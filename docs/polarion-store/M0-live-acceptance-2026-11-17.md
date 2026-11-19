# M0 真实双账号验收记录

环境：用户指定的专用测试 Polarion `http://localhost/polarion`，项目 `sysarch_probe_a`、`sysarch_probe_b`。凭据仅从 `build/private/polarion-test-a.env` 与 `polarion-test-b.env` 读入内存，未进入文档、命令行或提交文件。

**结论：15 项检查通过，1 项并发检查未通过，M0 尚未通过。正式模型存储写入仍关闭。** 字段保护、负载上限和唯一性等其他 M0 项也未完成，不能把本次最小探针等同完整模型存储实现。

## 环境与部署

目标 SDK：平台 bundle 3.25.12、JavaSE-21。独立 Servlet 扩展使用公开 SDK `AccessToken` 登录和 `doAsUser`，每个请求均验证调用者 PAT；不调用 `doAsSystemUser`，不使用管理员令牌回退，也不依赖浏览器 Cookie。

构建脚本 `tools/polarion-store-probe/build-extension.ps1 -EnableForTest`。只打包自有类和非敏感配置，不分发 Polarion SDK JAR。部署 JAR SHA-256：`251B931AAF1D2457668DDB80BD793E7FE90B8A6B32F0B3E27BD9040967150D76`。

扩展只允许两个指定项目，操作对象必须带当前测试 UUID 前缀；仅用于 M0，不是生产保存 API。利用 task 工作项、description 状态、relates_to 链接验证事务，尚未创建正式 sysarch 类型及字段。

安装前配置及 OSGi 缓存备份：`build/private/m0-deployment-20261117-231212`，已限制目录 ACL。本次不修改 Polarion 配置，不升级 SysMLTool 后端。安装过程中重启 Polarion 以加载插件。

## 真实身份和权限

SDK 确认两个身份分别为 `sysarch_test_a`、`sysarch_test_b`。REST `/user`、`/users` 端点均返回 403，但不表示 PAT 无效；两个账号分别可通过自己的项目端点读取数据。

A 不能读取 B 的探针对象；B 可以读取 A 的对象，但 `IWorkItem.can().modify()` 为否，写请求被 `item-not-writable` 拒绝。B 的原生对象读取权限与其 REST 项目端点权限不同，验收以实际对象权限分别判断。

## 检查结果

| 检查 | 结果 |
|---|---|
| 匿名调用拒绝 | 通过，401 |
| 两个独立 PAT 身份 | 通过，SDK 验证为两个不同账号 |
| A 在自己的项目创建探针数据 | 通过 |
| B 在自己的项目创建探针数据 | 通过 |
| A 不能读取 B 项目对象 | 通过，403 |
| B 对 A 的读取按实际配置执行 | 通过，只读可见 |
| B 不能修改 A 的对象 | 通过，SDK 对象权限拒绝 |
| 注入事务失败 | 通过，422 |
| 模型头、两个元素及链接整体回滚 | 通过，对比前后状态和链接数量 |
| 回滚命令无成功记录 | 通过，按 commandId 查询为 NOT_FOUND |
| 多对象正常提交 | 通过，模型版本增加一次 |
| 相同命令和请求重复执行 | 通过，返回同一持久结果，无重复创建 |
| 相同命令但不同请求 | 通过，409 |
| 旧 expectedVersion | 通过，409 |
| 主动丢弃响应后查询持久命令结果 | 通过；这是客户端丢弃响应，不是网络故障注入 |
| 两个独立进程竞争同一版本 | **未通过**，一方成功，另一方超时，未按期返回冲突 |

## 并发复现和边界

脚本：`tests/acceptance/polarion-store/live-probe.mjs`。父进程创建两个独立 Node 子进程，各自读取本机凭据，向同一模型头提交不同 commandId，但 expectedVersion 相同。插件没有 JVM 内模型锁。

30 秒与 60 秒两轮均出现一方成功、另一方超时。60 秒一轮成功请求约 734 ms，超时请求约 59664 ms，模型头从 2 变为 3。该结果没有观察到双成功或两次版本增加，但不能证明并发机制已满足有界冲突返回和未知结果恢复要求。

不能把增加客户端超时、仅检查头版本或加一个本地 Java 锁作为修复。需继续定位目标 SDK 的并发事务等待，选择受支持的服务器端并发控制并验证取消／超时后结果；未解决前，禁止开放远端多人写入。

本次查验公开 SDK 的事务和 IPObject 接口，没有据此确认可用的公开集群锁 API。没有采用未公开内部锁类，也没有直接访问 Polarion 数据库。

## 清理与证据

脚本 finally 块使用原调用者身份，在单独事务中只删除本次记录的 UUID 前缀对象。三轮已记录对象均完成清理。

独立只读检查 `tools/check-polarion-store-cleanup.mjs` 按三个运行 UUID、两个项目查询剩余当前工作项，并检查完整标题前缀。首次检查六组均无剩余；随后移除临时扩展、恢复原 OSGi 缓存并重启，服务恢复后再次检查六组，仍无剩余，`allClean=true`。SysMLTool 健康接口 200，原探针地址返回普通 HTML，不再提供探针 JSON API。审计和历史修订保留。

原始脱敏报告位于 `build/polarion-store/live-*.json`；60 秒复测为 `live-97ddd0d3-96b0-4c7a-99f8-fa306f4cc7ed.json`。独立检查为 `cleanup-verification.json`。这些运行产物均由 Git 忽略。

## 后续任务

1. 已完成：停用本轮临时扩展并恢复缓存，服务恢复后再次只读核对残留。
2. 对并发等待增加不含凭据的服务端阶段跟踪，明确等待位于登录、save、commit、rollback 还是 logout。
3. 构建真正的客户端网络故障注入和提交状态恢复测试，不用主动丢弃响应替代全部故障模型。
4. 继续 M0 的 UUID 并发唯一性、字段保护、附件及字段长度上限验证。
5. M0 全部通过后，再启用正式存储扩展和模型迁移。
