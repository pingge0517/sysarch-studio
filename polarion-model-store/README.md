# Polarion 模型只读身份扩展

独立生产用途身份桥接，不依赖 M0 测试探针，也不提供模型写入能力。已在 Polarion 2512 专用测试实例完成 SDK 编译、部署及双账号验证，尚未进行生产环境验收。

`GET /polarion/sysarch-model/identity` 仅接受 Authorization Bearer PAT。使用 SDK AccessToken 登录，在返回的 Subject 下读取当前主体并 logout。返回 protocol、authentication 和 actor；不查询用户目录，不接受浏览器 cookie/用户名作为身份，不使用系统账号。GET 以外方法返回 405，身份失败返回 401，验证后的内部错误返回 503。响应禁止缓存。

后续新增 `GET /polarion/sysarch-model/revision?project=<id>&workItem=<id>`：在同一调用者 Subject 的只读事务中检查当前项目/对象权限，返回实际 getLastRevision，不返回标题或正文。客户端先执行当前 REST 读取权限检查，再固定该修订，不能用更新时间推断修订。历史需求与跨项目策略见 [验收报告](../docs/polarion-store/M2-requirement-history-2026-11-19.md)。

后端 `PolarionRestReadSession.open(server, token)` 先调用该接口，再以同一 PAT 读取 REST 数据。仅允许配置的同一服务器，远端需 HTTPS，禁止跟随跳转；身份响应有长度和超时限制。每次会话重新验证，不缓存 PAT。保留带 CredentialVerifier 的构造入口用于测试或其他可信服务端身份提供者，不能让 HTTP 客户端指定 verifier 或主体。

## 构建及部署

从仓库根目录执行 `polarion-model-store/build-extension.ps1`。SDK 只用于编译，不打包发行。产物在 `build/polarion-model-store/extension/com.example.sysarch.model`，安装脚本要求明确传入构建输出的 SHA-256。

```powershell
.\polarion-model-store\build-extension.ps1
.\polarion-model-store\install-extension.ps1 -ExpectedSha256 <构建输出的SHA256> -DiscardRebuildableCache
```

安装会短暂重启指定 Polarion 服务；验证安装路径、服务所属目录及目标散列，并在受限 build/private 保存原扩展和配置。显式选择 DiscardRebuildableCache 时，只在服务停止且验证精确路径后删除可重建 OSGi 缓存，不备份缓存以控制磁盘占用；项目数据不在该缓存内。

最新测试部署：JAR `7A51EDD6004E8CF3D35EB4421BC409BBD074424461E49C4D4E35B0F8F13C76B3`；备份 `build/private/model-read-deployment-20261118-193016`。初次仅身份版本 `050509079FC429868B977DAB8CF546A5627C99D1DF964CA3561F9FF2027EF0A5` 及其备份 `model-read-deployment-20261118-152320` 为历史。身份检查 `node tools/verify-polarion-model-identity.mjs`，真实模型验收 `gradlew.bat --offline :backend:polarionLiveReadAcceptance`。

回退使用 `remove-extension.ps1 -DeploymentBackup <对应备份目录>`：核对 manifest 和已安装散列，停止服务，把本扩展移入备份，恢复先前扩展（若有）并重启。首次安装没有先前扩展时，回退结果为移除本扩展；可重建缓存允许重新生成。回退操作本身尚未在此次部署上执行。

身份扩展与主后端分别部署。2026-11-18 后续主后端及独立只读界面已升级并完成真实端到端验收，见 [报告](../docs/polarion-store/M2-readonly-browser-2026-11-18.md)。Sirius 投影接入、正式模型写入及权限工作流保护仍属后续范围。
