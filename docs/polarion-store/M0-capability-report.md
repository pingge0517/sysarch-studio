# M0 能力探针计划与记录

## 只读检查

执行 `node tools/probe-polarion-store.mjs --output build/polarion-store/m0-readonly.json`。
默认读取 `.env` 中的 `POLARION_BASE_URL`、`POLARION_TOKEN`（或显式配置的探针凭据文件），仅调用可信目标的元数据和现有项目读取端点，不输出令牌、账号正文或响应正文。未配置 URL 时使用当前本机开发环境 `http://localhost/polarion`，报告注明此回退。

探针不能证明事务、锁、字段上限或实际写权限。无 SDK 时记录不可用，不当成通过。

## 本机只读结果

2026-11-17：REST `/metadata` 和 `/projects?page[size]=1` 返回 200 及 JSON Content-Type。脚本仅保存状态，不保存响应正文，因此不把它当作字段配置、完整 JSON 内容或写能力检查。

本机 SDK 目录为 `C:/Polarion/polarion`。平台 bundle 为 `com.polarion.platform_3.25.12`，manifest 要求 `JavaSE-21`。已用目标 `platform.jar` 与 core.util JAR 编译 `SdkCompileProbe.java`，确认 `execute().write().callable(...)` 签名可用；没有执行该方法，没有安装插件。

复现命令：

```powershell
node --test tests/acceptance/polarion-store/probe.test.mjs
node tools/probe-polarion-store.mjs --output build/polarion-store/m0-readonly.json
./tools/polarion-store-probe/compile-sdk-probe.ps1
```

结果文件：`build/polarion-store/m0-readonly.json` 和 `build/polarion-store/sdk-probe/result.json`，均不提交。Node 探针 4 项测试通过；测试使用模拟响应，真实请求结果单独记录如上。

## 待执行的真实探针

| 项 | 方法 | 判定 |
|---|---|---|
| SDK 与插件入口 | 目标安装编译最小扩展，确认 Java／Servlet／OSGi 版本 | 必须在目标版本加载成功 |
| 多对象回滚 | 两个 WI、一个链接、提交记录和根修改中途抛错 | 全部未变 |
| 并发版本 | 两个独立客户端／进程提交相同 expectedVersion | 仅一方成功，另一方冲突 |
| 调用者身份 | 有写权与无写权账号调用相同扩展 | 不提权，审计记录真实主体 |
| 幂等与超时 | 提交成功后丢弃响应，用原 commandId 查询及重试 | 单次提交，结果稳定 |
| 对象唯一性 | 并发创建相同模型 UUID／元素 UUID | 只创建一个或明确拒绝冲突 |
| 托管字段保护 | 原生 UI／REST 修改受管字段与链接 | 被阻止或产生可检测漂移 |
| 长度与附件 | 在专用项目逐步增加数据，记录上限并精确清理 | 有实际保护值和错误行为 |

## 当前写入闸门

正式写入默认关闭；即使配置 enabled=true，也不能绕过未通过的能力验证。后续已使用用户提供的两个专用账号和项目执行真实最小事务探针：身份、权限、回滚和幂等通过，并发失败，M0 未通过。见 [真实验收](M0-live-acceptance-2026-11-17.md)。临时插件已移除并恢复服务，清理复核通过。凭据已提供，当前阻断不是缺少账号。
