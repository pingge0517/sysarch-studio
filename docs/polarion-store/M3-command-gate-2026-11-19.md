# M3 持久命令协调基础（2026-11-19）

后续已加入规范命令、隔离候选及回执匹配；最新回归与 12 项数据库专项见 [命令协议验收](M3-command-protocol-2026-11-19.md)。下文保留本阶段证据。

## 当前实现

从已合并提交 `e128c07` 继续实现 M3 的跨进程协调基础。`DurableCommandGate` 是内部库，未注册 Spring bean、未开放 HTTP 接口、未接入正式 SDK 命令扩展。远端模型写入仍关闭。

新增 Liquibase 020：`model_command_coordinator` 保存协调库状态与 epoch，`model_command_gate` 保存模型 UUID、命令 ID、规范化请求 hash、已验证主体、独立执行 attempt、epoch 及 ACTIVE/UNKNOWN。不保存 PAT、模型快照或需求正文。

新建或重建协调表默认 `LOCKED`，没有自动启用入口。缺少状态行、RECOVERY_REQUIRED、无效 epoch 或数据库失败均拒绝执行；不能把重建为空表当作恢复成功。未来启用需要独立、可审计的远端记录核对和恢复流程，本轮不提供绕过该流程的管理开关。

取得资格使用独立、短 JDBC 事务，先锁定协调状态行，再以模型 UUID 唯一约束仲裁；不同服务实例共享同一协调库。远端调用前提交并释放数据库锁，不跨网络持有数据库事务；不使用 JVM 内存锁替代数据库，也不加入现有 Spring 线程事务。

ACTIVE 和 UNKNOWN 均持续阻止后续命令，无自动过期或进程重启删除。相同 commandId 的重复请求也不能直接当作已提交；幂等命中必须由未来 Polarion 命令记录查询确认。

恢复先校验完整 ticket，再在数据库事务之外调用 `RemoteVerifier`，最后重新核对 attempt、主体、hash 和 epoch 才能释放资格。只有 COMMITTED 或明确 ROLLED_BACK 可以释放；NOT_FOUND、UNKNOWN、查询异常和取消保留阻断。取消保留线程中断标志。旧 ticket 或旧 epoch 不能释放后续 attempt。

## 证据与复现

最终完整离线 `check` 通过：backend 558、VSM 51、SSO 6，共 615 项，零失败、错误或跳过，性能门槛通过。前端本轮未修改，沿用已通过的 64 项及生产构建结果。

- 新增 12 项 H2/JDBC 测试：默认锁定、缺失状态行、恢复状态、重建实例、四实例竞争、不同模型并行资格、远端记录缺失和错误、取消、旧 attempt/主体/hash/epoch 隔离、远端查询无数据库锁、数据库故障及迁移重复执行。
- 真实 SysMLTool PostgreSQL 10/10：四个独立 Java 进程仅一个成功；UNKNOWN 跨连接保留；NOT_FOUND 不释放；强杀持有资格的 JVM 后依然拒绝新命令；fixture 确认结果后恢复；旧 attempt 和旧 epoch 被隔离。最后删除本轮随机 schema。
- 原始记录：`build/polarion-store/m3-command-gate-live.json`。测试只连接 `localhost:5432/sysarch` 的随机 `sysarch_gate_test_<UUID>` schema，不连接 Polarion 数据库，不激活生产协调器。
- 本轮“确认结果”由测试回调模拟，用于验证协调状态机；它不证明新的正式 Polarion 提交/回滚或真实远端恢复已经完成。

普通 `check` 不运行数据库专项。显式专项命令（先设置本机 JAVA_HOME 与 GRADLE_USER_HOME）：

```powershell
node tools/verify-command-gate.mjs
```

脚本从被忽略的本地 `.env` 读取 SysMLTool 数据库凭据，仅放入子进程环境；不写 argv、报告或 JAR。工作进程的 Java 参数文件只保存 classpath，结束后删除。schema 名称本地生成并校验，只清理本轮新建的 schema。

## 仍未实现

正式 `RemoteVerifier` 及调用者再授权、持久提交记录、命令规范化和幂等协议、SDK 原子命令入口、expectedVersion/对象修订及权限复核、协调库重建恢复程序、全编辑入口保护和产品错误映射仍未完成。本库不能替代其中任何一项，也不能将外部手工修改自动纳入协调。

Liquibase 020 尚未应用到正在运行的主后端数据库；当前部署继续使用原只读候选 JAR，未重启服务。本轮新增代码尚未提交。M3 整体尚未完成，M2 的实际 ACL/PAT 失效验收及 M4—M8 仍待后续实施。
