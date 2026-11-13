# 端到端测试报告：已实现的全部功能（P0–P4）

> 2026-11-13 ｜ 版本：`feature/p4`（`v0.7.0` 之后）｜ 一个运行实例，生产配置，连接本机 Polarion 2512 ｜ 结论：**全部通过；测试中发现 3 个缺陷，均已修复并补了回归测试**

## 1. 结论

| 测试 | 方式 | 结果 |
|---|---|---|
| 接口端到端（`tests/e2e/full-e2e.mjs`） | 生产配置的实例，按功能分 14 节，95 项检查 | **95/95** |
| 界面端到端（`tests/e2e/ui-e2e.mjs`） | 无头 Chrome，DevTools 协议；同一实例 | **26/26** |
| P0–P4 验收回归（`tests/acceptance/run-p4.sh`） | dev 模式实例（多用户、按名字登录） | **全绿**（P4 11/11 + 依赖扫描，P3、P2、P1 7/7、P0 AC-1..6） |
| 30 人并发编辑（`tests/perf/concurrent-writes.mjs`，60 s） | dev 模式 | 3079 次写入全部 200，P99 284 ms，无丢失更新，第 31 人被拒 |
| 协作负载（`tests/perf/collab-load.mjs`，5 分钟） | dev 模式，30 人，10% 争用共享块 | 18,299 个请求，0 错误，0 个 5xx，编辑 P99 103 ms，锁等待 P95 3.7 s / 最长 14 s / 0 超时，无丢失更新，内存平稳，连接池最多用 3 个 |
| 构建 | `./gradlew build`，测试强制重跑 | 478 个测试 0 失败；前端 38 个测试通过 |

**发现并修复的缺陷**（都在本次测试中修好，并各有一个修复前失败、修复后通过的测试）：

| 编号 | 缺陷 | 严重度 | 修复 |
|---|---|---|---|
| E1 | **每调用一次 `GET /api/v1/audit` 就泄漏一个数据库连接**。`AuditLog.recent` 用了 `JdbcClient` 的 `.stream()`，流从未关闭，连接一直不还。10 次调用（"角色与审计"页每打开一次就调一次）就耗尽连接池，此后**所有**访问数据库的请求都卡 30 秒后失败：图打不开，页面空白 | 严重（BLOCKER） | 改为 SQL `LIMIT` + `.list()`。`AuditLogTest`：连接池 2 个、连续调用 20 次，修复前失败（"Failed to obtain JDBC Connection"），修复后通过。接口端到端测试最后检查 `hikaricp_connections_active`（测完为 0） |
| E2 | **统一界面（:3000）画不出椭圆节点**：状态机的初始状态、用例图的用例都不显示，连在它们上面的迁移和关联也不显示。椭圆节点的前端部分属于 Sirius Web 自己的应用，不在已发布的 npm 包里；Sirius 自带的前端（:8080）有，我们的外壳没有 | 高（MAJOR） | `frontend/src/application/EllipseNodeConverter.ts`：通过 Sirius 的 `DiagramRepresentationConfiguration` 扩展点注册一个转换器，把椭圆节点转成 Sirius 内置的自由节点（圆形边框），复用内置组件、布局和连线手柄。单元测试 3 个；界面测试：状态机 4/4 条迁移、用例图 1/1 条关联（修复前 3/4、0/1） |
| E3 | **我们自己导出的 XMI 导不回来**：模型里有没有类型的部件时（IBD 的"新建部件"会产生，标红但允许），导出为不带类型的组合属性，导入器却拒收 | 中（MINOR） | 文件由 SysArch 导出时（`xmi:Documentation exporter="SysArch Studio"`），不带类型的组合属性按无类型部件导入；其他工具的文件照旧拒收。`XmiRoundTripTest.anUntypedPartRoundTripsInOurOwnExportOnly` |

## 2. 测试环境

- **实例**：后端（Spring Boot 3.5.16 + Sirius Web 2025.8.11）在 :8080，`vite preview` 在 :3000（发送与 nginx 相同的安全头）；PostgreSQL 16（本机服务）。
- **生产配置**：Polarion 模式（读取需要登录）、完整 CSP、`POLARION_BASE_URL=http://localhost/polarion`、服务账号（用 `.env` 中的 PAT 临时充当，只在测试进程中使用）、单点登录密钥（`.env`）、随机生成的指标令牌和 webhook 密钥（只在任务临时目录中）、本机 Python 求解器、报告写到临时目录、定时拉取间隔改为 1 分钟（`SYSARCH_POLARION_SCHEDULE_INTERVAL=PT1M`）。
- **Polarion**：本机 Polarion 2512 的 `sysarch` 项目。测试在其中建立 Work Item（推送、未绑定池场景），测完按编号全部删除。
- **数据**：每次运行新建一个模型项目（演示模板"域控制器"+ 一份行为文档：两个绑定的约束块、一个正常和一个有问题的状态机、一个活动、一个用例、一个交互），测完删除；`project001` 和 `seq-export` 未被触碰。
- **多用户场景**用 dev 模式（按名字登录 30 个用户）；Polarion 模式下只有一个真实用户（PAT 的主人，全局管理员）。

## 3. 覆盖范围与结果

### 3.1 接口端到端（95 项，全部通过）

| 节 | 项数 | 检查内容 |
|---|---|---|
| 身份与访问（P2 T8、P4 T8） | 14 | 健康检查；未登录读取 401（GraphQL、导出）；登录状态接口开放；错误令牌 401；不信任输入的名字；令牌登录得到 HttpOnly Cookie（Cookie 中没有令牌）；全局管理员权限；单点登录：签名凭证 303 + Cookie、重放 401、伪造签名 401、其他来源 403、外站跳转地址改为 `/`、Polarion 来源调用其他接口 403 |
| 模型项目与绑定（P0、P4 T1） | 3 | 从演示模板建模型并上传文档；元素定位；绑定 Polarion 项目后不重启即生效 |
| 图（P0 AC-2..AC-5） | 5 | BDD；IBD 的部件、端口、连接；新建部件后图自动刷新（AC-4）；IN → IN 连线被拒且不建任何东西（AC-5）；OUT → IN 连线成功 |
| 分层视图（P1 T3） | 4 | SA、LA、PA 各只显示本层；在 LA 新建逻辑组件 |
| 行为与参数视图（P3） | 5 | 参数图（块、参数、绑定）；状态机图（迁移标签）；初始、终止状态的图形；活动图的判定节点和虚线对象流；用例图的椭圆用例和小人参与者 |
| 分配矩阵与追溯（P1 T4、T5） | 6 | 跨层分配被拒（"跳过 LA"）；同层分配被拒；建立与删除分配；建立与取消满足关系 |
| 基线、分支与合并（P1 T7、P2 T3、T4） | 10 | 建基线；改名后差异恰为 1 处；两个基线之间的差异；从基线建分支；分支中的修改记成变更集；合并预演发现冲突；未选择冲突不能应用；选择后应用；分支状态 MERGED；删除基线（管理员） |
| 锁、会话、评论（P2） | 6 | 取锁、列表、续约、替别人取锁 403、释放；编辑会话（上限 30）；评论 |
| 报告（P2 T5） | 6 | 三个模板；SRS 14 页、ICD 4 页、追溯报告 2 页，HTML / Word / PDF 都能下载；同一模型走缓存；未知模板 400 |
| 导出与导入（P1 T8、P2 T6、P3 T7、T8） | 9 | SysML v2 全量与按层导出；导入为新项目；导出 → 导入 → 导出一致；不支持的语法整体失败并指出行号；XMI（OMG 2.5.1、UML 2.5、Papyrus 三种）；XMI 导入为新项目（**E3**）；拒绝 DOCTYPE（XXE） |
| 求解、行为检查、时序图（P3） | 6 | Python 求解（P_in=100 → P_out=90、L=4.5）并写回；图上显示求解值；超定 422；未配置的求解器 503；行为检查 R7、R8、R10；时序图 SVG 与 PNG |
| Polarion 同步（P0 T7、P1 T6） | 7 | 推送建立 3 个 Work Item；同一幂等键重放；缺少幂等键 400；Polarion 中的自定义字段与 `implements` 链接；拉取后 reqId = Work Item 号；Polarion 改了模型拥有的字段 → 冲突、不合并；同步状态 |
| 一致性、未绑定池、Webhook、定时拉取 | 9 | 在线读取 Polarion 的一致性报告（R1、R5）；不允许"全部修复"；修复 R1（解除绑定进入待认领）；定时拉取（服务账号，1 分钟）；新 Work Item 进入未绑定池；认领；Webhook 签名正确 202、错误 401；嵌入页 Work Item → 元素；解除绑定 |
| 运维（P2 T8、P4 T5） | 5 | Prometheus 指标需要令牌（无 / 错 401，对 200，5 个 `sysarch_` 指标）；审计记录了本次的 IMPORT、MERGE、PROJECT_BINDING_CHANGE、REPORT_GENERATE、SOLVE；**连接都已归还（E1 的回归检查）**；角色映射；映射按优先级生效 |

### 3.2 界面端到端（26 项，全部通过）

- 未登录时先显示登录页，不加载 Sirius 应用；登录后的项目浏览器有"SysArch 表格与报告"导航链接；退出登录后回到登录页。
- 编辑器：BDD 画出 8 个节点、6 条边；资源管理器列出两份文档；拖动节点后重新打开，位置保留；在 LA 背景上**右键**打开画板 →"建模工具"→"新建逻辑组件"，节点加 1。
- 状态机图：迁移标签，初始状态及其迁移（**E2**）；用例图：用例与参与者的关联（**E2**）。
- 外壳：分配矩阵中点击空格建立分配、再点取消、跨层被拒并显示原因；需求追踪矩阵；基线、分支、报告、时序图、评论、编辑锁、导入、角色与审计八个页面都显示内容；嵌入页显示 Polarion 项目对应的模型。
- 每个页面都检查了控制台错误和 CSP 违规：均为 0（Sirius 打包的库取全局对象时那一条被拦下的 `eval` 除外，见安全评审 §5）。

## 4. 观察（未修复，记录在案）

| 编号 | 观察 | 级别 | 说明 |
|---|---|---|---|
| O1 | 新建的状态机图默认布局拥挤：Sirius 的自动布局把状态放进一个很小的框，标签相互重叠 | 轻微 | 用户可拖开并保存布局；用例图类似（小人与椭圆重叠） |
| O2 | Sirius 2025.8 的画板用**右键**打开，左键单击背景不弹出 | 提示 | 用户手册应写明；工具在"建模工具"分组里 |
| O3 | 手动拉取只更新已绑定的 Work Item；**新建的 Work Item 只由定时拉取放进未绑定池** | 提示 | 符合设计（P1 T6）；未配置服务账号时未绑定池不会更新 |
| O4 | 删除项目的同时还有编辑请求在途时，会为已删除的项目留下会话记录（协作负载测试结束时出现 2 条） | 轻微 | 无功能影响，30 分钟后被空闲清理关闭；测试遗留的 34 条记录已删除 |
| O5 | Windows 上 Tomcat 偶尔报 "Failed to delete temporary file used for part [operations]"（上传后临时文件仍被占用） | 提示 | 只是 WARN，不影响上传 |
| O6 | `/links` 不返回 Work Item 标题（标题只存在绑定记录里） | 提示 | 规格的 OpenAPI 就没有这个字段 |

## 5. 未覆盖

- **Polarion 里的页面和工作项面板**：无头浏览器无法登录 Polarion（不能代输密码），这部分在接口层验证（单点登录凭证、嵌入目标）；P4 T2 时已在真实浏览器中由用户确认过。
- **P4-AC-2**（在干净的 Polarion 上安装）、**P4-AC-4**（Helm 安装到集群）、**P4-AC-8/9**（试点、探针 B、Cameo）：按用户决定挂起。
- **键盘撤销（Ctrl+Z）**：合成的键盘事件在 Sirius 中不触发撤销（P4 T3 已记录）。
- **Modelica 求解器**：本机没有 OpenModelica，只验证了未配置时的 503。

## 6. 复现

```bash
# 生产配置的实例（见 §2 的环境变量），然后：
POLARION_TOKEN=... SSO_SECRET=... METRICS_TOKEN=... WEBHOOK_SECRET=... CONSISTENCY_STRATEGY=MODEL_WINS \
  node tests/e2e/full-e2e.mjs --keep                      # 保留项目给界面测试
POLARION_TOKEN=... node tests/e2e/ui-e2e.mjs "<chrome.exe>" <空的配置目录> <保留的项目 id> [截图目录]
# 多用户与回归（后端 dev 模式；协作负载还要 MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics）：
sh tests/acceptance/run-p4.sh
node tests/perf/concurrent-writes.mjs --duration 60
node tests/perf/collab-load.mjs --duration 300 --warmup 30
```

`full-e2e.mjs` 不加 `--keep` 时会删除自己建的全部数据（模型项目、绑定、链接、它在 Polarion 中建的 Work Item）；加 `--keep` 时保留模型项目和绑定，界面测试之后需要删除。
