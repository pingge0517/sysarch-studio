# M0 核心事务与故障恢复验收

2026 年 11 月 18 日更新。专用测试实例：`http://localhost/polarion`，项目 `sysarch_probe_a`、`sysarch_probe_b`。

核心最小探针 21／21 通过，后续加入字段漂移、UUID 注册和容量专项后 **34／34 通过**，共享 PostgreSQL 执行闸门检查 **4／4 通过**。正式模型存储尚未启用；这些结果不代替 M2—M8 的产品验收。

## 已证实的行为

- SDK 验证两个真实 PAT 主体为 sysarch_test_a／sysarch_test_b；无服务账号代写。
- 跨项目读取、只读账号修改均受到对象权限限制。
- 模型头、两个工作项和链接的注入失败可整体回滚。
- 正常提交、同命令重试、不同请求 hash 拒绝和旧版本拒绝均通过。
- 远端提交后注入确认失败，UNKNOWN 闸门保留并阻断后续写入；原账号核验持久 Polarion 命令记录后可显式恢复，重试不重复提交。
- 两个独立 Node 进程竞争同一版本，一方 200（约 887 ms），一方 409（约 210 ms），模型头仅前进一次。
- 四个独立 PostgreSQL 客户端竞争同一键，仅一方取得资格；UNKNOWN 跨连接保留，不自动过期，也不能由普通释放语句删除。

原始核心记录：`build/polarion-store/live-0d5075ef-fc32-4d7a-8bac-1392c15fdb3b.json`。后续 34 项记录：`build/polarion-store/live-edaa6fe8-e765-42e3-85f6-e0f88b64b006.json`。新增专项验证 REST 标题修改检测并拒绝覆盖、两个独立进程同 UUID 注册一胜一拒、重复及跨项目注册拒绝、64 KiB description 和 1/20 MiB SDK 附件完整往返及散列一致。容量验证不代表自定义 sysarchPayload 或 REST 上传容量已验收。

闸门检查：`coordinator-verification.json`。七轮运行 UUID、两个项目的独立清理检查均无剩余当前工作项；命令资格及 UUID 登记无残留。审计与历史修订保留。

## 并发修复的准确范围

裸 SDK 并发事务曾停在 `JavaSvnDriver.delayBeforeCommitRestart`。修复使用 **SysMLTool 自身数据库**中的持久执行资格，在进入 Polarion 写事务前拒绝竞争者。它不保存模型，也不访问 Polarion 数据库；模型原子性仍由 SDK 事务提供。见 [ADR 002](ADR-002-durable-command-coordination.md)。

这不是修复了 Polarion 原生 SVN 驱动，也不覆盖绕过命令服务的其他写入口。完整产品仍须统一编辑路径、保护或检测托管字段、处理协调库重建和服务进程异常退出。

UNKNOWN 测试注入点为服务器已经提交、确认流程随后失败；尚不代表完成所有网络分区、进程强杀和 ACTIVE 孤儿资格恢复场景。

## 当前部署

测试探针仍在专用实例运行，限于上述两个项目。最新部署备份：`build/private/m0-deployment-20261118-100744`；JAR SHA-256：`41A13ADDA28CA61803B6A5C01DE9DB003FD38BB45E5AC149158BF5A992352179`。连接配置仅在受限 `build/private/m0-command-gate.properties` 中，未放入 JAR 或启动参数。本轮未保留可重建 OSGi 缓存副本，以控制磁盘占用。

现有 SysMLTool 后端未升级，LOCAL 模型未切换。早期失败与清理记录保留在 [11 月 17 日报告](M0-live-acceptance-2026-11-17.md)，其结论属于修复前历史。
