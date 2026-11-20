# M3 Polarion 回执只读查询验收

2026-11-19，在 Polarion 2512 专用实例的 `sysarch_probe_a`、`sysarch_probe_b` 验证了 `PolarionCommandReceiptLookup`。这是面向服务端调用的内部组件。查询结果的认证级别固定为 `UNATTESTED`，对应的命令闸门结果为 `UNKNOWN`。

回执保存为 `sysarch-command-receipt.json`，格式沿用命令协议的 `Receipt`，字节上限为 64 KiB。真实实例将测试工作项的纯文本描述返回为 HTML，因此选择二进制附件保存机器数据。附件查询先用固定文件名匹配，再通过同一工作项下的实际附件 ID 下载。附件 ID 由服务端分配，下载路径由本地代码根据已核对的项目、工作项和附件身份构造。[Siemens Polarion 2512 REST 文档](https://developer.siemens.com/polarion/2512/basic-concepts.html)

查询使用调用者 PAT，通过现有身份扩展取得真实主体，先验证当前模型和需求依赖的读取权限，再按项目、模型 UUID 和命令 UUID 查找托管 `sysarchCommit`。SDK 返回的修订用于读取工作项字段、列举附件和下载内容。查询拒绝重复记录、重复附件、无法完整检查的分页结果、模型或请求不匹配。读取后重新核对工作项修订和模型头，检查期间出现漂移则拒绝结果。

回执必须匹配当前模型头的版本、提交 ID 和清单摘要。历史修订用于固定本次读取的数据，已被后续模型提交替换的回执仍需独立的历史查询路径。搜索未命中仅表示没有观察到匹配记录。正式回放还需验证事务来源与记录保护，再接入 `CommittedCommandVerifier.ReceiptReader`。

真实双账号专项 **31/31** 通过，其中回执新增 **6/6**，包括实际修订读取、确认测试回执仍为 `UNKNOWN`、同命令不同请求冲突、跨项目拒绝、重复记录拒绝、字段变更后拒绝当前回执且原修订可读。20 个临时工作项全部清理。原始记录位于 [专项报告](../../build/polarion-store/m2-live-c435564c-c63e-4f4d-a841-6b8897bc4526.json)。

本地新增 15 项测试，覆盖回执严格解析、权限检查、固定修订、附件归属、重复记录和读取期间漂移。完整离线 `check` 通过 **649 项**（backend 592、VSM 51、SSO 6），零失败或跳过，性能门槛通过。专用回执测试通过 `SYSARCH_M3_RECEIPTS=true` 启用，执行 `:backend:polarionLiveReadAcceptance`。普通 `check` 不创建远端测试数据。

验收在专用项目进行。M3 代码待部署，正式 SDK 命令入口、原子事务回执及其认证仍待实现。
