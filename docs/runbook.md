# SysArch Studio 运维手册（P2）

本文是 P2 规格书 §8 T8 第 3 步要求的 runbook，面向运维人员，分启动、备份、恢复、回滚四部分，另附灰度发布和审计归档。部署资产在 [`deploy/helm/sysarch-studio`](../deploy/helm/sysarch-studio)：Helm Chart 和它用到的脚本（`files/`），脚本也可以脱离 Kubernetes 单独运行。

## 1. 组成与依赖

| 部件 | 说明 |
|---|---|
| backend | Spring Boot 应用（Sirius Web + sysarch），端口 8080。Sirius Web 自带的建模界面也由它提供 |
| frontend | nginx 提供的外壳页面，端口 3000，把 `/api`、`/graphql`、`/subscriptions`、`/actuator` 转给 backend |
| PostgreSQL 16 | 模型文档、锁、会话、分支、基线、审计都在这里。Chart 可以自带一个，也可以用集群已有的 |
| Polarion | 身份和角色来源（用户用 Polarion 个人访问令牌登录）、工作项同步 |
| 报告目录 | 生成的报告文件，后端自己清理 90 天前的文件（`SYSARCH_REPORT_DIR`） |

P2 只跑一个 backend 实例：编辑会话的开启在 JVM 内串行（见 CLAUDE.md P2 T2）。Chart 的 `strategy: Recreate` 保证升级时不会出现两个实例。

## 2. 启动

### 2.1 首次部署（Kubernetes）

1. 准备 Secret，**所有口令只放 Secret，不写进 values**：

   ```sh
   kubectl create secret generic sysarch-backend \
     --from-literal=POSTGRES_USER=sysarch \
     --from-literal=POSTGRES_PASSWORD="$(openssl rand -base64 24)" \
     --from-literal=SYSARCH_SESSION_SECRET="$(openssl rand -base64 48)" \
     --from-literal=POLARION_SYNC_TOKEN=<只读服务账号令牌，可省> \
     --from-literal=POLARION_WEBHOOK_SECRET=<可省>
   ```

   `SYSARCH_SESSION_SECRET` 是会话 cookie 的签名密钥。不设时每次启动随机生成，重启即全员掉线。

2. 按环境改一份 values（镜像、域名、`POLARION_BASE_URL`、`SYSARCH_POLARION_PROJECTS_<ID>` 项目绑定、`SYSARCH_COOKIE_DOMAIN`），然后：

   ```sh
   helm upgrade --install sysarch deploy/helm/sysarch-studio -f values-prod.yaml
   ```

3. 检查：`kubectl get pods`，backend 就绪探针 `/actuator/health/readiness` 变绿（首次启动 Liquibase 建表，约 30 秒）；打开外壳页面，用 Polarion 令牌登录，右上角应显示"名字（角色）"。

前端镜像的 `VITE_SIRIUS_URL` 要在构建时设成 Sirius 的域名（`ingress.siriusHost`），外壳里"在 Sirius 中打开"才能跳对。外壳和 Sirius 是同一域名下的两个主机，所以要设 `SYSARCH_COOKIE_DOMAIN`，登录 cookie 才能带到 Sirius。

### 2.2 不用 Kubernetes（本机或虚拟机）

见 CLAUDE.md"Running locally without Docker"：PostgreSQL 16 作为系统服务，`java -jar backend-*.jar`，环境变量从 `.env` 来。

### 2.3 身份与权限

- **生产只用 `SYSARCH_AUTH_MODE=polarion`（默认）。** 用户粘贴自己的 Polarion 个人访问令牌，后端向 Polarion 查令牌主人和他的角色，签发 8 小时的 HttpOnly cookie。令牌用完即丢，服务端和 cookie 里都不保存。`dev` 模式按名字登录，只用于本机测试和压测。**读取同样需要登录**（P4 T8）：Polarion 模式下，未登录访问 `/api/**` 和 `/subscriptions` 返回 401（登录接口、Polarion webhook、图标和 `/actuator` 除外），界面先显示登录页；`SYSARCH_ANONYMOUS_READ=true` 恢复匿名读取，只应在可信网络中使用。
- 工具角色由表 `role_mapping` 决定，不写在代码里。初始行：Polarion 全局 `admin` 和项目 `project_admin` 对应项目管理员，`project_user` 对应架构师，`project_reviewer` 对应评审人，`project_observer` 对应观察者；未绑定 Polarion 的项目里，所有已登录用户都是架构师；其余情况为观察者。管理员在外壳的"角色与审计"页或 `/api/v1/role-mappings` 修改，每次修改都记审计。
- 用户在 Polarion 里的角色变了，要重新登录才生效（cookie 里带的是登录时的角色）。映射表的修改立即生效。

## 3. 备份

规格 §7.3：每日全量 + WAL 归档，保留 30 天；报告文件保留 90 天。

| 内容 | 怎么做 | 保留 |
|---|---|---|
| 每日全量 | CronJob `<release>-backup`，02:30 运行 `files/pg-backup.sh`：`pg_dump -Fc` 加 SHA-256，写入 PVC `<release>-backups` | 30 天，脚本自己清理 |
| WAL 归档 | 自带的 PostgreSQL 以 `archive_mode=on` 启动，把 WAL 复制到 PVC `wal-archive-…`；外部 PostgreSQL 照 `files/postgresql-archive.conf` 配置 | 37 天（30 天加两次基础备份的间隔），数据库 Pod 的 `wal-pruner` 边车清理 |
| 每周基础备份 | CronJob `<release>-basebackup`，周日 03:00 运行 `files/pg-basebackup.sh`（`pg_basebackup`） | 30 天 |
| 报告文件 | 后端每晚清理 90 天前的报告，任务状态改为 EXPIRED | 90 天 |
| 审计日志 | 见第 6 节 | 365 天，之后导出归档 |

手工执行一次备份：

```sh
kubectl create job --from=cronjob/sysarch-backup sysarch-backup-manual
# 不用 Kubernetes：PGHOST=… PGUSER=… PGPASSWORD=… BACKUP_DIR=/backups sh deploy/helm/sysarch-studio/files/pg-backup.sh
```

每周确认一次 `<release>-backups` 里有最近 7 份 `.dump` 和 `.sha256`，CronJob 没有失败记录。

## 4. 恢复

### 4.1 从每日全量恢复（演练过的流程）

原则：**恢复到一个新数据库，确认可用之后再切换**，不在原库上覆盖。

1. 选一份备份并校验：`sha256sum -c sysarch-<时间>.dump.sha256`（`pg-restore.sh` 也会先校验）。
2. 恢复到新库：

   ```sh
   PGHOST=… PGUSER=<能建库的角色> PGPASSWORD=… \
     sh deploy/helm/sysarch-studio/files/pg-restore.sh /backups/sysarch-<时间>.dump sysarch_restored sysarch
   ```

   脚本最后打印项目数、文档数、基线数和审计行数。
3. 用一个临时 backend 指向新库验证：`POSTGRES_URL=jdbc:postgresql://…/sysarch_restored`，换一个端口启动，检查健康检查为 UP、项目列表齐全、抽一个项目导出 SysML v2，和事故前的导出比较。
4. 切换：停掉 backend（`kubectl scale deploy/sysarch-backend --replicas=0`），把 `postgres.external.url`（或自带库的数据库名）指向新库，`helm upgrade`，启动后再验证一次。旧库保留到确认无误为止。

2026-11-10 在本机做过演练：从每日备份恢复到新库，再用第二个 backend 实例在 8081 端口拉起，项目、模型和导出与原库一致（结果见 CLAUDE.md P2 T8）。

**每月演练一次**（P4 T6），按上面的第 1–3 步做，并记录下表。目标：**RTO ≤ 30 分钟**（从开始恢复到可用），**RPO ≤ 24 小时**（只有每日全量时）或 **≤ 15 分钟**（开启 WAL 归档时，见 4.2）。

| 日期 | 备份大小 | 恢复用时 | 第二个实例启动 | 恢复到可用 | 校验 | 执行人 |
|---|---|---|---|---|---|---|
| 2026-11-12 | 194 KB（2 个项目、4 个文档、150 行审计） | 2.2 s | 24.5 s | **26.6 s** | 两个项目的 SysML v2 和 XMI 导出与原实例逐字节一致；在恢复出的实例上建基线成功（201），原实例不受影响；日志无 ERROR | Claude（本机，PostgreSQL 16） |

演练时注意：

- 恢复用的角色必须能建库（应用自己的角色不行），本机用 `postgres` 超级用户。
- 第二个实例要换端口（`SERVER_PORT=8081`），报告目录也要单独设置（`SYSARCH_REPORT_DIR`），不要和正在运行的实例共用。
- 演练结束后删除演练库（`DROP DATABASE`）和临时转储文件。

### 4.1a 单实例的限制

后端只运行一个实例（Helm 的 `Recreate` 部署，副本数 1）：

- 编辑会话和锁的部分状态在进程内存中（30 人上限的计数、会话活动时间的缓存），多个实例会互相看不到，所以不能横向扩展。
- 后端停机期间所有人都不能编辑；Polarion 的定时拉取也会暂停，恢复后从上次成功的时间点继续，不会丢失变更。
- 升级或恢复时先停旧实例再起新实例（`Recreate`），中间有约半分钟不可用，应安排在维护时间。
- 发生故障时的恢复手段是按本节快速恢复（上表约半分钟，数据量大时以实际演练为准），而不是故障切换。

### 4.2 时间点恢复（误删、坏数据）

需要恢复到某个时刻（例如一次错误合并之前）时：取该时刻之前最近的基础备份 `base-<时间>`，解压到新的数据目录；在数据目录里建 `recovery.signal`，并设置 `restore_command = 'cp /var/lib/postgresql/wal-archive/%f %p'` 和 `recovery_target_time = '<时刻>'`；启动这个实例，它回放 WAL 到目标时刻后停下。之后按 4.1 的第 3、4 步验证、切换。

只是一次合并出了问题时，先看有没有更轻的办法：合并前系统会自动打一个"合并前（分支 X）"基线，可以用基线差异找出改动，逐项改回。

## 5. 回滚

### 5.1 功能开关（规格 §10 的三个回滚点，加导入）

四个开关互相独立，改环境变量后重启 backend 生效，不必下架整个版本：

| 开关 | 关掉后 | 什么时候关 |
|---|---|---|
| `SYSARCH_FEATURE_LOCKS=false` | 不再检查和授予元素锁，退回单人编辑；申请锁返回 503 | 锁表持续膨胀（`edit_lock` 行数长期超过在线人数 × 20）或锁误拦编辑 |
| `SYSARCH_FEATURE_BRANCHES=false` | 不能新建分支、不能合并，退回主干直接编辑；已有分支保留 | 合并产生脏数据（APPLY 后元素数与预期不符） |
| `SYSARCH_FEATURE_REPORTS=false` | 不能生成新报告，已生成的仍可下载 | 报告错误或拖慢服务 |
| `SYSARCH_FEATURE_IMPORTS=false` | SysML v2 导入返回 503 | 导入后元素数少于预期且没有报错（规格 §10："立即禁用导入入口"） |
| `SYSARCH_FEATURE_XMI=false` | XMI 导出 `/export/xmi` 返回 503（P3） | 对方把 XMI 当作无损往返使用，或导出文件在目标工具里出错（P3 规格 §9） |
| `SYSARCH_FEATURE_SOLVER=false` | 参数求解 `/solve` 返回 503（P3） | 求解的任一安全防线缺失（P3 规格 §9，见 `docs/solver-security-review.md`） |

### 5.2 版本回滚

1. `helm history sysarch` 找到上一个版本，`helm rollback sysarch <版本>`。
2. **数据库结构只增不改**：所有变更集都是新增表或列，旧版本能直接跑在新结构上，回滚版本不用回滚数据库。例外要在发布说明里写明，例如 P1 的接口迁移有专门的离线回滚任务 `./gradlew :backend:rollbackInterfaceMigration`（见 CLAUDE.md）。
3. 回滚后的验证同 2.1 第 3 步。

## 5a. 参数求解服务（P3）

求解在单独的 Pod 里运行（`templates/solver.yaml`，镜像由 `solver/Dockerfile` 构建）：只读根文件系统、非 root、出站网络全部禁止，只允许 backend 连入。`solver.enabled: true` 时 backend 自动通过 `SYSARCH_SOLVER_REMOTE_URL` 调用它。生产环境不要给 backend 配置 `SYSARCH_SOLVER_PYTHON`（那是本机开发用的、没有容器隔离的方式）。安全评审记录见 `docs/solver-security-review.md`。

## 6. 审计日志

- 记录谁、何时、对哪个元素、做了什么：强制解锁、合并、删除基线、生成报告这四类敏感动作，以及每一次被拒绝的操作（`ACCESS_DENIED`，例如评审人尝试建模）、放弃分支、导入、修改角色映射。管理员在"角色与审计"页查看。
- **只增不删由数据库保证**：变更集 016 的触发器拒绝对 `audit_log` 的 UPDATE、TRUNCATE，以及 DELETE；唯一的例外是删除超过 365 天的行，并且会话里设置了 `sysarch.audit_archive = 'on'`。应用代码不做这件事。
- **超期归档**：CronJob `<release>-audit-archive` 每月 1 日运行 `files/audit-archive.sh`。脚本先把 365 天前的行导出为 CSV（加 SHA-256），写入备份卷的 `audit/` 目录，再在同一事务里删除这些行；如果要删的行数和导出的行数不一致，整个事务回滚，什么也不删。归档文件跟着备份卷保存；如有合规要求，另行转存到不可改写的存储。
- 规格要求"按项目分区"，目前用 `(project_id, created_at)` 索引实现。365 天的数据量用不着物理分区。

## 7. 灰度发布

规格 §7.3：新版本先对 1 个项目开放一周，观察错误率与性能，再全量。

P2 是单实例、共用一个数据库，不能让两个版本同时写同一个项目，所以按项目灰度这样做：

1. 选一个试点项目，通知它的用户。
2. 用 4.1 的方法把当天的备份恢复成新库，部署第二个 release（例如 `sysarch-canary`，新版本镜像，独立域名，指向新库），只开放给试点项目的用户。
3. 在正式 release 的 `role_mapping` 里，把试点项目对应的 Polarion 项目映射为观察者（例如 `polarion_project=<试点 ID>`、`polarion_role=*`、`tool_role=OBSERVER`，并临时删掉该项目更高角色的行）。这一周里试点项目只在灰度 release 里编辑，正式 release 里只读，两边不会分叉。
4. 观察一周：灰度 release 的错误日志（`ERROR` 行）、`/actuator/health`、编辑和矩阵的响应时间（可以对灰度库跑 `tests/perf/concurrent-writes.mjs`）。
5. 通过：备份灰度库，正式 release 升级到新版本；用 `pg_dump -t` 和恢复脚本，把试点项目的文档从灰度库迁回正式库（Sirius 的 `project`、`document`、`representation_*` 表，以及我们的 `baseline*`、`branch`、`changeset`、`element_comment` 表里属于该项目的行）；恢复 `role_mapping`；下线灰度 release。不通过：下线灰度 release，恢复 `role_mapping`，试点项目回到正式 release 继续用旧版本，这一周在灰度里的修改要人工补录。

## 8. 日常检查

监控指标、告警规则、日志和报告目录的上限，以及 Polarion 日志的保留建议见 [monitoring.md](monitoring.md)（P4 T5）。

| 频率 | 检查 |
|---|---|
| 每天 | backend 日志没有持续的 `ERROR`；`Rejected edit` 告警和 `ACCESS_DENIED` 数量正常；Polarion 定时拉取没有暂停（Sirius 的 Validation 视图里没有红色"已暂停"条目） |
| 每周 | 备份齐全（第 3 节）；`edit_lock` 行数没有超过在线人数 × 20 |
| 每月 | 审计归档任务成功；报告目录的磁盘用量 |
| 每季度 | 按 4.1 做一次恢复演练 |
