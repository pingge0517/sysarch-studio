# M2 主后端只读浏览部署验收

本报告记录首次只读浏览部署阶段；最新主后端及历史需求能力见 [2026-11-19 验收](M2-requirement-history-2026-11-19.md)，当前 PID、产物散列及完整回归数量以最新报告为准。

日期：2026-11-18。独立模型浏览入口已在本机测试部署可用：<http://localhost:3000/polarion-models>。主界面 `/sysarch` 的视图菜单也提供“Polarion 模型（只读）”。

## 使用

输入 Polarion 项目（sysarch_probe_a 或 sysarch_probe_b）和对应账号 PAT，读取模型列表，选择模型后浏览元素属性及引用。独立入口不要求用户目录登录权限；数据始终由 SDK 验证当前 PAT 后，再使用同一 PAT 读取。令牌不写入浏览器存储，清除、切换凭据或项目时取消请求并清空已有结果。

验收工作项已全部清理，测试项目没有其他正式模型时列表为空。当前不会把 LOCAL 模型自动迁移到 Polarion，也没有正式创建/保存模型的入口。本页不是 Sirius 图形编辑器。

## 接口

仅当 `SYSARCH_POLARION_STORE_ENABLED=true` 时注册控制器，默认关闭；部署清单显式开启只读模式。

- `GET /api/v1/model-store/projects/{polarionProject}/models?page=1&pageSize=25`
- `GET /api/v1/model-store/projects/{polarionProject}/models/{root}/snapshot?modelUuid=<UUID>`

Authorization 使用调用者 Bearer PAT，不接受 cookie 或声明用户名替代。列表返回分页与 mayHaveMore；快照返回已校验、重建的对象图、真实主体和版本。成功与控制器拒绝响应均禁止缓存。权限拒绝返回 403，远端不可用返回 503，完整性或协议问题返回 502；不会用空模型掩盖加载失败。

控制器不发布到 Sirius 编辑上下文，不切换存储模式，不提供写入方法。现有本地模型权限保持生效。这里的 polarionProject 路由变量明确表示远端作用域，不能使用本地 Sirius project 的解释方式。

## 验收证据

| 验证 | 结果 |
|---|---|
| 完整离线 Java check | backend 530、VSM 51、SSO 6，共 587 项；无失败、错误或跳过 |
| 性能门槛 | 既有 500/10,000 元素与视图切换通过；不代表大模型远端读取性能已验收 |
| 前端单元测试及构建 | 52 项通过，TypeScript/Vite 构建成功 |
| 本地模拟服务无头浏览器 | 6/6：文本转义、引用导航、分页、凭据更换拒绝及清除等 |
| 真实 3000→8080 浏览器 | 7/7：独立入口、A 读 A、实际模型打开并查看属性、引用导航、B 拒读 A、B 读 B、无 Web Storage 凭据 |
| 真实模型专项 | 26/26：六类型字段、64 KiB text、分页、重建、MockMvc/实际 8080 控制器、真实浏览器模型及权限拒绝 |
| 部署核验 | 9/9：候选、回退及数据库备份散列；019 迁移；健康；双账号与匿名权限 |

最新报告：`build/polarion-store/m2-live-03fdc944-bf5b-4023-a5ea-e3bdd90df764.json`、`build/model-store-ui-report.json`、`build/model-store-live-ui-report.json`。上一轮 25 项报告 `m2-live-f40732a9-ff12-4b5a-a727-96adf5702440.json` 保留。最新专项在两个项目共创建 18 个本轮工作项，浏览器在清理前打开真实模型、查看属性并点击 Component 引用；之后逐个删除工作项并 GET 确认不存在，附件随之清理，审计和历史保留。

复现完整已部署验收时，在运行专项 Gradle 任务前设置 `SYSARCH_M2_DEPLOYED_HTTP=true`、`SYSARCH_M2_DEPLOYED_UI=true`；需要本机 Chrome 和既有两份凭据文件。普通 test/check 不执行真实远端验收。

首次实际部署测试发现通用项目拦截器把远端项目当成本地项目，返回 403。已修正路由变量作用域，并增加包含通用拦截器和严格登录过滤器的回归用例。修复前失败报告保留，修复后实际 8080 双账号及浏览器验收通过。

## 部署与回退

当前主后端 PID 16348；候选 JAR SHA-256：`df3fd25eccaf4a96f83076dd0c39d77ae203cbb182ec36bc76db9274d5b2b415`。

受限部署目录：`build/deployments/20261118T110958Z`，清单 phase=VERIFIED。保留原候选、精确旧版 rollback.jar、数据库快照、前端快照和会话密钥。运行 `tools/finalize-model-read-deployment.mjs` 已验证产物及备份散列，并确认 `sysarch-019-model-storage` 实际应用。登记表是新增表，未迁移已有模型。

回退主后端时先核对监听 PID 和运行 JAR，停止当前候选，再执行 `node tools/start-local-backend.mjs build/deployments/20261118T110958Z rollback`。回退模式关闭只读开关；不要同时运行两个连接同一数据库的主后端。追加的登记表无需因应用回退而删除；恢复数据库快照会回退其他数据，不能作为普通应用回退的自动步骤。本次未实际执行回退。

只读身份扩展仍独立部署在 Polarion；其部署/回退见 [说明](../../polarion-model-store/README.md)。此次主后端升级未再次重启 Polarion。

## 后续范围

独立只读浏览闭环已完成。Sirius 图形投影及布局恢复、历史外部需求的真实权限验收、统一模型附件 ACL、原生修改保护/漂移的产品接入、正式命令写入、迁移及大模型并发性能仍待实现或验收，不应将本报告视为 M2—M8 全部完成。
