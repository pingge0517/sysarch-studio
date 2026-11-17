# P4 规格书：试点上线、单一界面与运维收口（Claude Code 可执行版）

> 版本 v0.1.0（草案，待确认）｜ 2026-11-12 ｜ 周期 **12 周（含 2 个限时任务）** ｜ 定位：**把已有能力交到真实用户手里**
>
> 前置：P3 的 P3-AC-1 至 P3-AC-9（AC-6 Cameo 除外，见 T7）通过；Polarion 嵌入已完成，tag `v0.6.0`。
>
> **P4 不再扩展建模能力。** P3 第 10 章已经说明四期之后的能力边界：结构建模与追溯对齐 EA，仿真与协同治理不追 Cameo。P4 只做三件事：让用户从 Polarion 一个入口用上它（单一界面、正式嵌入），让它能在生产环境里稳定运行（部署、运维、升级、安全），以及用真实试点决定还要不要继续投入（探针 B、Cameo 互导、试点反馈）。每项任务都有退出条件，做不到就记录结论，不留半成品。

---

## 0. 执行前置

```bash
git checkout -b feature/p4 feature/p3   # v0.6.0 + the partitioned cookie and docs (c9c16a8)
./gradlew build                                   # 三道 CI 关：测试、生成确定性、性能阈值
sh tests/acceptance/run-p3.sh                     # 含 run-p2.sh / run-p1.sh / run-p0.sh（后端以 dev 模式运行）
./gradlew :polarion-sso:polarionExtension         # 本机有 Polarion 时应输出 "with the Work Item form panel"
```

环境缺口（开始前确认）：本机无 Docker、无 kubectl/helm（T1 需要用户同意后下载 kind/helm，或在用户提供的集群上执行）；无 Cameo（T7 由用户执行）；C: 盘空间紧张（T8 第一项）。

---

## 1. 范围：三主两辅

| 编号 | 内容 | 性质 | 周期 |
|---|---|---|---|
| P4-1 | 正式嵌入 Polarion：项目映射可管理、页面与面板模板化、一键安装 | 主 | 2 周 |
| P4-2 | 单一界面：外壳内显示 Sirius 画布，不再在 :3000 与 :8080 之间跳转 | 主（限时 3 周） | 3 周 |
| P4-3 | 生产部署与运维：HTTPS、Helm 实测、监控告警、日志与磁盘、备份演练 | 主 | 3 周 |
| P4-4 | Sirius Web 升级演练 + 安全评审 | 辅 | 2 周 |
| P4-5 | 试点：一个真实项目组 4 周试用，探针 B 与 Cameo 互导收尾 | 辅（限时） | 4 周（与上面并行） |

### 1.1 硬边界

- **不改元模型**（冻结在 v1.4）。需要改元模型的需求记入试点问题清单，留给 P5 或判定为不做。
- **不替换 Sirius 的渲染器**（P0 §2.3 红线不变）。P4-2 只允许"把 Sirius 自己的前端组件挂进外壳"；做不到就保留两个入口，并把切换体验做顺。
- **不做独立账号体系**。所有登录仍经 Polarion（令牌或单点登录）。
- **不做多实例横向扩展**。后端仍为单实例 + 快速恢复（见 §5.3），只把单实例的限制写清楚。

### 1.2 不做

- 不做可执行模型（fUML）、trade study、跨企业协同（P3 §10.2 的三条"停下来买 Cameo"条件不变）。
- 不做可编辑时序图（探针 A 已判定 No-Go；保留只读 SVG/PNG）。
- 不做几何视图开发；探针 B 只给结论。

---

## 2. 正式嵌入 Polarion（P4-1）

### 2.1 项目映射从环境变量改为数据

现状：Polarion 项目 → Sirius 模型的映射只能用 `SYSARCH_POLARION_PROJECTS_<ID>` 环境变量配置，改一次要重启。P4 改为数据库表 + 管理页，环境变量保留为初始值。

`db/sysarch/018-project-binding.sql` —— `[COPY]`

```sql
create table project_binding (
  polarion_project varchar(255) primary key,
  sirius_project   varchar(64)  not null,
  created_by       varchar(128) not null,
  created_at       timestamptz  not null default now(),
  updated_by       varchar(128),
  updated_at       timestamptz
);
create index idx_project_binding_sirius on project_binding(sirius_project);
```

规则：

- 读取顺序：表中有记录用表；没有则用环境变量（兼容现有部署）；都没有则视为未绑定。
- 改动只能由全局管理员做，写 `audit_log`（动作 `PROJECT_BINDING_CHANGE`）。
- 一个 Sirius 模型只能绑定一个 Polarion 项目（唯一约束在应用层检查，返回 409）。
- 删除 Sirius 项目时（P3 后的 `ProjectCleanup`）同时删除指向它的绑定。

### 2.2 页面与面板模板化

- `polarion-sso` 扩展增加 `GET /polarion/sysarch-sso/page?project=<id>`：一个整页包装（Polarion 源下，含 iframe），可直接放进 Polarion 的项目导航或快捷方式，不再依赖手工建 LiveReport 页面。
- 文档给出 Work Item 表单布局的最小改动（一行 `<extension>`），以及按类型批量启用的步骤。
- 安装脚本 `polarion-sso/install.ps1`：复制扩展、写 `polarion.properties` 两行、生成密钥文件、提示 Apache `frame-src` 改动（只打印，不自动改 Apache 配置）、删除 `.config`。脚本先备份，失败可回滚。

### 2.3 验收要点

- 管理员在 SysArch 管理页把 Polarion 项目 `sysarch` 绑定到一个模型，**不重启**，Polarion 页面和 Work Item 面板立即显示该模型。
- 非管理员修改绑定被拒绝并写审计。

---

## 3. 单一界面（P4-2，限时 3 周）

### 3.1 目标

用户在 :3000 外壳里打开模型、切换图、编辑，不再跳到 :8080。外壳的矩阵、基线、报告等页面与画布并列。

### 3.2 做法（只允许这一种）

- 使用 Sirius Web 自己发布的前端包（`@eclipse-sirius/sirius-components-*`，与后端同版本 2025.8.11），在外壳里挂载其编辑器组件。
- 依赖来自 GitHub Packages，需要与后端相同的 classic PAT（`read:packages`）；CI 用现有 `GPR_TOKEN`。
- React 版本以 Sirius 前端包的要求为准；若与外壳的 React 18 冲突，外壳跟随 Sirius 升级，不反过来。

### 3.3 退出条件（第 3 周末判定）

| 结论 | 判据 | 处置 |
|---|---|---|
| Go | 外壳内能打开 BDD/IBD/LA，编辑、撤销、拖拽布局与 :8080 一致；首屏 < 3 s | 合入主干，:8080 仅作兼容入口 |
| No-Go | 版本冲突无法在 3 周内解决，或需要改 Sirius 源码 | 不合入；改为"同窗口切换"：外壳与 Sirius 同域部署（Ingress 按路径分流），外壳顶栏常驻，回退按钮回到外壳 |

无论 Go 或 No-Go，代码里唯一的 TODO（`frontend/src/App.tsx`）在 P4 结束时必须消失：要么实现，要么替换为指向结论文档的说明。

---

## 4. 生产部署与运维（P4-3）

### 4.1 部署基线

- HTTPS：Ingress 终止 TLS；`SYSARCH_SECURE_COOKIE=true`；同站部署用 `Lax`，跨站部署用 `None + Partitioned`（P3 后已实测，见 `docs/polarion-embedding.md`）。
- Helm chart 第一次真实安装：`helm lint` + 在 kind（或用户提供的集群）上 `helm install`，跑 `run-p0.sh` 的健康检查与 P1 验收脚本。此前 chart 从未被 lint 过。
- 镜像：后端、前端、求解器三个镜像在 CI 构建（不推送，除非用户提供仓库）。

### 4.2 监控与告警

- 暴露 `/actuator/prometheus`（仅集群内）；指标：请求延迟 P99、编辑会话数、锁等待、报告队列、导入失败数、同步定时任务状态、JVM 堆（full GC 后）。
- 告警规则（PrometheusRule 或文档化阈值）：P99 > 500 ms 持续 5 分钟；同步暂停；堆在 full GC 后 > 75%；**磁盘剩余 < 15%**。

### 4.3 日志与磁盘（P3 后的教训）

- 后端日志：滚动 + 总量上限（Logback `SizeAndTimeBasedRollingPolicy`，默认 30 天 / 2 GB）。
- 报告目录：已有 90 天过期；增加总量上限，超出时按最旧删除并写审计。
- Polarion 侧：文档给出 Apache `rotatelogs` 与 log4j 保留策略建议（**只给建议，不改 Polarion 配置**）。
- 第三方库的噪声日志统一在 `application.yml` 降级（docx4j 已做），新增依赖时检查。

### 4.4 备份与恢复演练

- 每月一次恢复演练（P2 T8 的 `pg-restore.sh` 流程），记录恢复时间；目标 RTO ≤ 30 分钟、RPO ≤ 24 小时（WAL 归档时 ≤ 15 分钟）。
- 单实例限制写进运行手册：后端宕机期间不可编辑；恢复依赖 `Recreate` 部署与数据库。

---

## 5. Sirius 升级演练与安全评审（P4-4）

### 5.1 升级演练（只演练，不强制合入）

- 在 `spike/sirius-upgrade` 分支把 Sirius Web 从 2025.8.11 升到当时最新的稳定版。
- 记录：编译错误、行为变化（适配层 `view.sirius`、迁移参与者、调色板、校验视图）、性能变化、前端包变化。
- 判定：全部测试与 P0–P3 验收通过且性能不退化 → 写升级手册，下期执行；否则记录阻塞点，继续锁定 2025.8.11。

### 5.2 安全评审

- 依赖漏洞扫描（OWASP Dependency-Check 或等价工具）进 CI，高危漏洞阻断构建。
- 对本机运行实例做一次被动扫描（ZAP baseline），只针对自己的服务。
- 复核清单：所有写接口都有权限检查（P3 后补上了 `/links`，逐个接口列表核对）、CSP 与 frame-ancestors、单点登录凭证（重放、过期、密钥存放）、求解沙箱三道防线、审计只追加。
- 输出：`docs/security-review-p4.md`，每条发现给处理状态。

---

## 6. 试点（P4-5，限时 4 周）

- 选一个真实项目组（5–10 人），从 Polarion 入口使用，覆盖：分层建模、分配矩阵、需求追踪、基线、报告、Work Item 面板。
- 问题记录沿用 `docs/p1-trial-report.md` 的格式；每周分级（BLOCKER/MAJOR/MINOR/TRIVIAL）。
- **探针 B 收尾**：用问卷结果（3 位架构师）按 P3 §6.2 的判定规则给结论，写入 `docs/probes/p3-probe-b-geometry.md`。
- **P3-AC-6 收尾**：用户在 Cameo 中打开导出的 XMI（OMG 版本），按 `docs/xmi-export.md` §6 的步骤记录结果。
- 退出条件：试点第 4 周末，BLOCKER 为 0、MAJOR ≤ 2 且都有处理计划；否则暂停推广，先修问题。

---

## 7. 任务分解（T1–T9）

### T1 项目映射数据化｜1 人 × 5 天

```text
1) 按 §2.1 建表（018-project-binding.sql 原样执行），读取顺序：表 → 环境变量 → 未绑定。
2) 管理接口 GET/PUT/DELETE /api/v1/project-bindings（全局管理员，审计 PROJECT_BINDING_CHANGE），外壳"角色与审计"页增加映射管理。
3) ProjectCleanup 删除指向被删模型的绑定；ProjectBindings 缓存随改动失效。
验收：不重启即可生效；非管理员 403 并审计；一个模型绑定两个 Polarion 项目返回 409。
```

### T2 嵌入模板化与安装脚本｜1 人 × 5 天

```text
1) 扩展增加整页包装 /polarion/sysarch-sso/page?project=。
2) install.ps1：备份、复制扩展、属性两行、密钥文件、删除 .config；打印 Apache frame-src 改动，不自动修改。
3) 文档：导航入口、Work Item 面板批量启用、卸载。
验收：在一台干净的 Polarion 上按文档 30 分钟内完成安装并单点登录成功；卸载后 Polarion 启动日志无本扩展痕迹。
```

### T3 单一界面（限时）｜2 人 × 15 天

```text
1) 引入 Sirius 前端包，确认版本与 React 要求；外壳内挂载编辑器组件。
2) 第 3 周末按 §3.3 判定 Go/No-Go；No-Go 时实现同域路径分流与常驻顶栏。
3) 移除或替换 App.tsx 中的 TODO。
验收：Go——外壳内完成 P0 AC-4（自动同步）与 AC-5（连线校验）；No-Go——结论文档 + 同域切换可用。
```

### T4 部署基线｜1 人 × 6 天

```text
1) helm lint；kind 或用户集群上 helm install，HTTPS（自签名即可）。
2) 跑健康检查与 p1-acceptance.mjs；记录安装时间。
3) Cookie：同站 Lax 与跨站 None+Partitioned 各验证一次。
验收：chart 首次安装成功，验收脚本全绿；文档记录实测步骤。
```

### T5 监控、日志与磁盘｜1 人 × 6 天

```text
1) /actuator/prometheus + 指标清单 + 告警阈值（含磁盘 < 15%）。
2) Logback 滚动与总量上限；报告目录总量上限。
3) Polarion 日志保留建议写入运行手册（不改 Polarion 配置）。
验收：人为填满报告目录触发清理与审计；日志总量不超过上限；告警规则可加载。
```

### T6 备份恢复演练｜1 人 × 3 天

```text
1) 按 runbook 做一次完整演练，记录 RTO/RPO。
2) 单实例限制与恢复步骤写入 runbook。
验收：恢复后的实例导出与原实例逐字节一致（同 P2 T8）；RTO ≤ 30 分钟。
```

### T7 Sirius 升级演练｜1 人 × 5 天

```text
1) spike/sirius-upgrade 分支升级，记录所有破坏点。
2) 全量测试 + run-p3.sh；性能基准对比。
验收：一页结论（升级 / 不升级及原因），分支不合入主干。
```

### T8 安全评审｜1 人 × 5 天

```text
1) 依赖扫描进 CI；ZAP baseline 扫本机实例。
2) 逐个写接口核对权限检查，产出接口 × 权限表。
3) docs/security-review-p4.md。
验收：无未处理的高危项；接口 × 权限表覆盖全部写接口。
```

### T9 试点与收口｜贯穿 4 周 + 收口 3 天

```text
1) 试点问题记录与每周分级；探针 B 结论；Cameo 打开 XMI（用户执行）。
2) tests/acceptance/run-p4.sh：P4 新测试 + run-p3.sh。
3) 发布 v0.7.0（或 v1.0.0，见 §10）。
验收：见第 8 章。
```

---

## 8. 验收清单

| 编号 | 验收动作 | 通过标准 |
|---|---|---|
| P4-AC-1 | 管理页绑定 Polarion 项目与模型 | 不重启生效；非管理员 403 且审计 |
| P4-AC-2 | 在干净的 Polarion 上安装扩展 | 按文档 30 分钟内完成，单点登录成功 |
| P4-AC-3 | 单一界面 | Go：外壳内通过 P0 AC-4/AC-5；No-Go：结论文档 + 同域切换 |
| P4-AC-4 | Helm 首次安装 | lint 通过，安装后验收脚本全绿 |
| P4-AC-5 | 磁盘与日志 | 日志与报告目录有总量上限；磁盘 < 15% 有告警 |
| P4-AC-6 | 恢复演练 | RTO ≤ 30 分钟，导出逐字节一致 |
| P4-AC-7 | 安全评审 | 无未处理高危；写接口全部有权限检查 |
| P4-AC-8 | 试点 | 4 周末 BLOCKER = 0，MAJOR ≤ 2 且有计划 |
| P4-AC-9 | 探针 B、Cameo 互导 | 各有书面结论 |
| P4-AC-10 | 回归 P0–P3 | run-p4.sh 全绿 |

---

## 9. 风险与回滚

| 风险 | 判据 | 处置 |
|---|---|---|
| 单一界面陷入前端版本泥潭 | 第 2 周仍无法编译挂载 | 提前按 No-Go 处理，不延期 |
| Polarion 管理员不同意改 Apache CSP | 无法 frame-src | 退回"新标签页打开 + 单点登录"，面板与页面改为链接 |
| 试点暴露元模型缺口 | 用户需要新类/新字段 | 记入问题清单，P4 内不改元模型 |
| 升级演练发现大量破坏 | 适配层大面积失效 | 继续锁版本，记录阻塞点，评估下一个窗口 |
| 磁盘或日志再次失控 | 剩余 < 15% | 告警触发；按运行手册清理，清理前先归档 |

**回滚点**：项目映射（表为空即回到环境变量）、单一界面（关闭外壳内画布入口）、监控（删除 ServiceMonitor 与规则）、扩展（删除扩展文件夹与两行属性）。

---

## 10. 交付物与路线收口

| 交付物 | 形态 |
|---|---|
| 项目映射管理 + 嵌入模板 + 安装脚本 | 源码 + 文档 |
| 单一界面（或结论 + 同域切换） | 源码或文档 |
| Helm 实测记录、监控与告警、日志与磁盘策略 | 文档 + 配置 |
| 恢复演练记录、Sirius 升级结论、安全评审 | 文档 |
| 试点报告、探针 B 结论、Cameo 互导结论 | 文档 |

### 10.1 什么时候叫 1.0

P4-AC-1 至 P4-AC-10 全部通过、试点项目组愿意继续使用，即发布 `v1.0.0`；否则发布 `v0.7.0`，把未通过的项列为 1.0 的前提。

### 10.2 P5 只在这种情况下开

试点提出的需求中，有三条以上无法在不改元模型的前提下满足，且都不属于 P3 §10.2 的"该买 Cameo"范围。否则 P4 之后进入维护：只修问题、跟随安全更新、按升级手册升级 Sirius。

---

**文档结束。执行入口：第 7 章 T1（确认本草案后开始）。**
