# M2 真实只读模型重建验收

日期：2026-11-18。仅针对授权专用实例 `http://localhost/polarion`，项目 sysarch_probe_a、sysarch_probe_b。

最新主后端及浏览器端到端验收已扩展为 26/26，真实浏览器 7/7（包含模型打开、属性与引用导航）；见 [只读浏览部署验收](M2-readonly-browser-2026-11-18.md)。下文保留早期内部读取阶段证据。

## 结果

**内部读取阶段扩展验收：19/19 通过。** 阶段报告 `build/polarion-store/m2-live-496e44f9-f744-4311-88e0-36d802f0e90f.json` 使用独立生产用途身份扩展；此前字段/分页报告 `m2-live-8cd57341-d5e3-40ab-91c0-f6961ca684bc.json` 仍使用 M0 验证，仅作为历史证据。

新增 12 项检查：两个项目各六种类型的全部契约字段实际往返，逐字段比较；三个 payload 类型写入 64 KiB text/plain 并完整读回。另新增两项分页检查：pageSize=1 遍历模型根至最后一页，检查模型身份且拒绝跨页 UUID 重复。保留原先重建、越权与清理五项。本轮共 18 个工作项及附件清理成功。

独立身份接口真实验收 6/6：两个账号正确主体、匿名 401、无效 PAT 401、POST 405、仅 cookie 401。报告 `build/polarion-store/production-identity-acceptance.json`。扩展部署及回退说明见 [README](../../polarion-model-store/README.md)。

以下五项为首次最小重建的历史记录。

原始记录：`build/polarion-store/m2-live-f5bc68da-22e4-4671-8b84-355e64b8c18d.json`，Gradle 专项任务成功。

| 检查 | 结果 |
|---|---|
| A 读取项目 A 清单/附件并重建 | 通过，重新编码与上传快照逐字节一致 |
| B 读取项目 B 清单/附件并重建 | 通过，重新编码与上传快照逐字节一致 |
| B 使用自身 PAT 读取项目 A 模型 | DENIED，通过 |
| 项目 A 三个临时工作项及附件清理 | 删除后逐项 GET 404 |
| 项目 B 三个临时工作项及附件清理 | 删除后逐项 GET 404 |

模型含 PartDefinition、其包含的 Part、业务编号、名称和 typedBy 引用。每个 EObject 均有 UUID 和独立托管工作项绑定。实际读取器执行 HEAD、清单和附件读取、长度/SHA-256 校验及对象重建，没有模拟传输。

建模写入仅用于构造验收 fixture，不代表正式 M3 写入服务完成。元素绑定使用当前修订，本轮不验证历史需求修订或完整 Sirius 图形布局。

## 配置核验

两个账号均可从真实 REST 枚举接口读到六种模型类型，原 task、requirement 等类型仍在，必需字段全部存在。

首次核验曾发现项目 B 的 `sysarchRelation.sysarchPayload` 为 string，要求 text。用户已修正；后续预检全部兼容，该字段的 64 KiB 文本真实往返也已通过。没有自动覆盖用户配置。

## 联调发现

Java multipart 的 resource 分段指定 application/json 时返回 500；使用浏览器 FormData 等效的字符串分段后，进一步发现附件元数据依赖字段顺序。

对同一临时工作项发送其他内容一致的请求，type 在 attributes 前返回 201，顺序交换返回 400（Required member 'type' was not found）。工具改用有序资源映射并固定 type 在前。此结论来自当前实例实测，不能泛化为 JSON 标准要求。各失败轮次均清理临时工作项。

接口协议参考 [Siemens 2512 OpenAPI](https://developer.siemens.com/polarion/2512/polarion-rest-apispec.json) 和 [附件操作说明](https://developer.siemens.com/polarion/2512/advanced-concepts.html)。

## 复现与边界

在仓库根目录使用既有 Java 21 和离线缓存执行：

```powershell
$env:JAVA_HOME='C:/jdk-21.0.11.10-hotspot'
$env:GRADLE_USER_HOME='C:/Users/Administrator/.gradle'
.\gradlew.bat --offline :backend:polarionLiveReadAcceptance
```

凭据仅从既有 `build/private/polarion-test-a.env`、`polarion-test-b.env` 读取，不传入命令行。任务固定 localhost 和两个专用项目；报告记录 fixture ID，便于异常退出后定向核查。普通 check/test 不运行此任务。

最新验收程序使用独立 `/polarion/sysarch-model/identity` SDK 身份扩展确认 PAT 主体，不依赖 M0 探针。生产读取会话默认使用此协议；主后端、只读界面、统一 ACL 和历史外部需求仍待接入/验收。

## 回归

最新 REST 传输/发现/身份 19 项、加载器 16 项专项通过。Node 配置预检/GET 探针 10 项通过。最新完整离线 check 成功，耗时 2 分 33 秒：backend 520、VSM 51、SSO 6，共 577 项，零失败、错误和跳过；500 元素同步、视图切换和 10,000 元素性能门槛均通过。
