# M2 历史需求引用与权限验收

此文件保留历史需求阶段记录；后续新增只读 Sirius 图形数据与矩阵，当前部署及回归数量见 [投影验收](M2-read-projections-2026-11-19.md)。

记录日期：2026-11-19。专用实例 `http://localhost/polarion`，项目 sysarch_probe_a、sysarch_probe_b。此轮能力已部署，入口仍为 <http://localhost:3000/polarion-models>。

## 结果

综合专项 **37/37 通过**：`build/polarion-store/m2-live-8ac7616b-7705-45d8-97f9-24b67e34ad25.json`。本轮创建 24 个临时工作项；其中一个需求用于删除依赖测试，其余在 finally 中清理，所有删除均以当前 GET 不存在确认。附件随工作项清理，审计和历史保留。

| 新增验证 | 结果 |
|---|---|
| SDK 获取真实当前修订 | 通过，不使用更新时间推断 |
| 原需求 V1 固定后更新为 V2 | 旧修订仍返回 V1，新修订返回 V2 |
| 模型加载需求正文 | 按清单所选 V1 修订叠加到独立读取投影 |
| 原生快照内容检查 | 无 V1/V2 正文及外部需求标题 |
| 原生 REST 附件权限 | B 可下载自身项目快照；A 无法下载 B 项目快照 |
| 部署接口历史正文 | 返回所选 V1，非当前 V2 |
| 历史需求真实浏览器 | 7/7，包含 V1 正文、来源项目及真实固定修订号 |
| 跨项目默认策略 | 未显式允许时拒绝 |
| 服务端允许跨项目后的对象权限 | 不会代替或绕过目标工作项权限 |
| 当前依赖删除 | 内部加载器及实际 8080 接口均拒绝，不返回此前可读正文 |

保留原模型字段、分页、重建、双账号和浏览器回归；原模型真实浏览器同样 7/7。报告分别为 `build/model-store-history-ui-report.json`、`build/model-store-live-ui-report.json`。

首次附件负向检查未通过预期状态断言。检查后修正了验收请求：二进制内容接口应使用 application/octet-stream，并选用确实没有 B 项目读取权的账号 A。B 原本具有 A 项目的 SDK 只读权，不能仅根据工作项 REST 列表拒绝就假设其所有原生访问均被拒绝。失败记录保留；修正后使用同一 B 快照的拥有者正向下载和无权账号负向下载，检查通过。没有修改账号权限来使测试通过。

## 实现

### 修订元数据

只读扩展新增 `GET /polarion/sysarch-model/revision?project=<id>&workItem=<id>`。验证 PAT，在该 SDK Subject 下开启只读事务，检查项目及工作项当前可读性，再返回真实 `getLastRevision()`。只返回协议、主体、资源身份和修订号，不返回正文，不提供写入方法。

后端 `pinCurrentRevision` 先检查当前 REST 读取权限，再请求同一用户的 SDK 元数据；核对主体、项目和工作项身份，要求数字修订号。获取 HEAD 对象的准确修订号仍是 [2512 REST 的已知限制](https://developer.siemens.com/polarion/2512/limitations-and-known-issues.html)，这里没有用时间戳替代该能力。

### 引用策略

同项目需求默认允许；跨项目默认拒绝。服务端可配置单向项目组合，例如：

```yaml
sysarch:
  polarion-store:
    requirement-projects:
      sysarch_probe_a:
        - sysarch_probe_b
```

这只是范围许可，不授予 Polarion 读取权限。实际依赖权限仍使用调用者 PAT 检查。当前测试部署没有开启该跨项目许可；明确允许但权限仍拒绝的分支使用真实账号及内部服务端策略完成验收。客户端无法通过请求参数或用户名改变策略。

### 正文与展示

持久语义快照中的外部需求节点保持中性占位；模型清单保存目标项目、工作项及选定修订。加载时先检查当前依赖权限，再读取选定修订正文，叠加到新建的 EMF 读取投影。读取接口返回的投影可以含已授权正文，不应直接当作可持久化提交包；后续写入发布流程仍须剥离外部正文。

界面显示“需求来源”和“固定修订”，不把历史正文误标为最新正文。响应禁止缓存。删除依赖测试验证了新的读取请求不会返回此前正文，不代表能撤回用户已经取得的副本。

## 部署与验证

主后端 PID 17500，JAR SHA-256 `a02c840f3f8f4b0e233ce98fabbc543f67c9adf5212ab6d79d573545f23101b8`。部署目录沿用 `build/deployments/20261118T110958Z`，保留候选历史、旧版 rollback.jar、会话密钥及数据库备份；部署核验 9/9 通过，phase=VERIFIED。此轮没有新增数据库迁移。

Polarion 只读扩展 JAR SHA-256 `7A51EDD6004E8CF3D35EB4421BC409BBD074424461E49C4D4E35B0F8F13C76B3`，回退备份 `build/private/model-read-deployment-20261118-193016`。身份接口原有 6 项真实检查仍通过。

完整离线 check：backend 537、VSM 51、SSO 6，共 **594 项通过**，无失败、错误或跳过，性能门槛通过。前端 **52 项通过**，构建成功；这些性能门槛仍是既有本地模型基准，不代表远端大模型性能已验收。

复现真实专项需显式设置三个环境变量为 true：`SYSARCH_M2_REQUIREMENTS`、`SYSARCH_M2_DEPLOYED_HTTP`、`SYSARCH_M2_DEPLOYED_UI`，再执行 `gradlew.bat --offline :backend:polarionLiveReadAcceptance`。会连接固定本机专用实例，使用既有私有凭据文件和隐藏 Chrome；普通 check 不执行此专项。

## 验收映射与剩余范围

- AC14：选定修订策略及需求正文更新后历史读取已验证；正式写入链路尚未实现。
- AC09：本轮覆盖列表、快照、历史依赖及原生 REST 附件的一部分权限路径；订阅、报告、全部原生入口及实际 ACL 变更仍需后续专项。
- M2：独立只读浏览和历史需求分支已验证。Sirius 图形投影、BDD/IBD/分层图和矩阵重建、统一托管附件 ACL 仍未完成。
- M3—M8：正式命令写入、编辑入口统一、布局、迁移和生产验收仍未完成。LOCAL 模型未迁移，正式远端模型写入保持关闭。
