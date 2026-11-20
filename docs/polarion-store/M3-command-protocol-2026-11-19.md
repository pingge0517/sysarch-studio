# M3 命令协议、候选副本与幂等回执

后续已验证当前模型头对应的真实 Polarion 回执查询，见 [回执查询验收](M3-receipt-lookup-2026-11-19.md)。下文记录命令协议阶段的证据。

日期：2026-11-19。继 [持久协调基础](M3-command-gate-2026-11-19.md) 继续实现内部提交协议。本轮未开放写入接口、未安装新的远端命令扩展。

## 请求契约

`ModelCommandProtocol.bind(modelUuid, verifiedActor, bytes)` 接收服务端已经验证的调用者和模型作用域。请求 JSON 只包含以下四个必需字段：

```json
{
  "commandId": "a74604a6-2d65-48e4-8aab-5a41973f9af0",
  "expectedVersion": 17,
  "schemaVersion": "1.0",
  "operations": [
    {
      "kind": "RenameElement",
      "elementUuid": "2b31f827-f8bd-4fa9-8bc1-b808567f9bb1",
      "name": "左前驱动电机"
    }
  ]
}
```

当前白名单仅 `RenameElement` 与 `SetAttribute`；后者使用 `attribute` 与 `values` 字符串数组，值遵循快照的 EMF 规范词法格式，空数组表示取消设置。其他操作，包括创建、连接、分配和删除，明确拒绝。

输入上限 1 MiB、操作数量 1—256、JSON 嵌套深度 20。拒绝重复键、尾随文档、未知字段、客户端 actor/PAT 字段、未知类型、缺失或 null 必需字段、不规范 UUID 形状、负版本、版本溢出及标量隐式转换。改名最多 4096 字符；属性最多 1000 个值、单值最多 65536 字符。

规范摘要绑定协议标识、模型 UUID、已验证主体及完整请求。JSON 字段顺序、空白和等价 Unicode 转义不影响 hash；操作顺序、字段值、expectedVersion 或作用域变化会影响 hash。字符串保留原样，不做 trim 或 Unicode 字符替换。`BoundCommand` 只能由协议绑定方法构造，内容与列表不可变；可直接生成持久闸门所需 Request。不记录请求正文，错误不回显解析片段或原始异常。

## 候选修改

`CommandCandidate` 仅接收已经授权的原始持久快照及服务端确定的外部需求 UUID 集合。首先检查模型与预期版本，然后按请求顺序修改独立 DTO，使用 `ModelCodec` 验证属性、类型、基数、引用和归属，解码到独立 EMF 对象并调用现有 `ConstraintValidator` 执行 OCL。

业务编号、持久 UUID、文档顺序和引用保留。失败不改变输入，不更新 Sirius 投影，也不提交远端。未知元素、未知属性、把引用当属性、无效枚举、空名称约束及直接修改布局均拒绝。布局需后续独立协议。

外部需求只允许引用占位，不能改名或改属性；候选准备前检查整个外部需求集合，防止把界面叠加的受限标题/正文重新打包。正文边界由 `ExternalRequirementContent` 与原 M2 读取器共享。外部占位对象不作为可编辑对象参与 OCL；实际内容和权限仍由读取路径单独验证。

## 回执与协调

`CommittedCommandVerifier.Receipt` 包含 schema、模型、命令、主体、请求 hash、expectedVersion、已提交 version、commitId 和 manifestHash。成功版本必须严格为 expectedVersion+1。

回放前后均检查 `ReceiptReader` 的真实主体；返回回执必须匹配模型、命令、主体、hash 和预期版本。同一 commandId 对应不同请求时拒绝回放。记录缺失仅为 NOT_FOUND，永远不等于已回滚。与持久闸门配合时，错误或缺失回执保留 UNKNOWN，只有一致的已确认结果才能释放原 ticket。

当前 `ReceiptReader` 是受信任服务端接口：未来实现必须以原调用者重查模型和需求权限，并验证不可变提交记录、清单及来源。**本轮尚无真实 Polarion reader 实现；匹配回执元数据不等于已证明远端原子提交。** 测试回执仅验证内部协议及闸门状态机。

## 验证与边界

新增 19 项测试覆盖规范摘要、严格解析、候选隔离、失败批次不残留修改、外部正文拒绝、OCL、回执冲突和身份变化。最终完整离线 `check`：backend 577、VSM 51、SSO 6，共 **634 项通过，零失败、错误或跳过**；性能门槛通过。

真实 SysMLTool PostgreSQL 专项 **12/12**，原有跨 JVM 竞争、强杀、旧 attempt/epoch 隔离仍通过；新增两项将规范请求和回执匹配接入真实数据库闸门：不匹配回执保留 UNKNOWN，匹配 fixture 回执释放精确 ticket。远端回执仍由 fixture 提供，不是实际 Polarion 命令提交证据。测试随机 schema 及临时参数文件已清理，生产协调器未启用。

专项运行 `node tools/verify-command-gate.mjs`（先设置 JAVA_HOME、GRADLE_USER_HOME）；原始记录 `build/polarion-store/m3-command-gate-live.json`。普通 `check` 不运行真实数据库专项。本轮前端未改，沿用原 64 项测试及成功构建。

未完成：正式命令 SDK 入口、当前对象权限/修订的事务内复核、持久 Polarion 命令记录、真实 reader、创建/连线/分配/逻辑退役、协调库重建恢复流程和所有编辑入口接入。不能将本轮称为 M3 完成或开放安全多人编辑。

运行中的后端及前端均未升级，仍是既有只读部署。Liquibase 020 未应用到主部署。当前 M3 代码尚未提交或推送。
