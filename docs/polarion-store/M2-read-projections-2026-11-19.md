# M2 请求内 Sirius 投影与只读矩阵验收

阶段历史：后续已接入原生只读画布，最新结果见 [原生画布验收](M2-native-canvas-2026-11-19.md)。下文记录本阶段边界。

日期：2026-11-19。专用实例和两个测试项目不变。已部署：<http://localhost:3000/polarion-models>，打开模型后可选择分配矩阵或需求追溯矩阵。后端新增五种 Sirius 图形数据投影；原生图形画布尚未接入，不能把本轮结果视为完整 Sirius 编辑上下文或历史布局恢复已完成。

## 实现与边界

- 图形数据复用生成的 BDD、IBD、SA、LA、PA 描述、原有投影器及原版 Sirius DiagramRenderer，不另写语义投影规则。
- 矩阵复用原有 MatrixProjector 和 MatrixView；服务端清除动作和单元格操作链接，页面显式禁用编辑单元格。
- 每个投影请求重新验证 PAT、模型、所有元素和需求依赖，再从 Polarion 重建对象；不共享已授权对象或请求间投影缓存。
- 仅为请求内解码对象绑定 UUID 适配器，投影标识使用持久 UUID，业务 id 属性不变；重复业务编号不会使节点或矩阵行列合并。原 LOCAL 对象没有此适配器时保留既有标识行为。
- EMF ChangeRecorder 监测投影过程；发现语义变化时撤销并失败。普通模型投影前后的规范化快照完全一致。
- 专用只读 Sirius 描述没有编辑工具栏，改名、删除回调直接返回 Failure，不创建编辑工具所用的 OCL 环境。没有注册可写编辑上下文或保存投影。

图形结果是请求内生成的 Sirius Diagram 数据，representation id 与几何结果未作为持久图/布局管理。页面目前只展示矩阵及原有元素属性，不提供原生图形画布；历史位置、尺寸、路由和图 UUID 接入仍属后续范围。

## 接口

新增 GET：

```text
/api/v1/model-store/projects/{polarionProject}/models/{root}/views/{view}?modelUuid=<UUID>&elementUuid=<UUID>
```

view 为 bdd、ibd、sa-function-chain、la-architecture、pa-architecture 时，elementUuid 必须是该模型中的有效 PartDefinition UUID；矩阵 allocation-matrix、requirement-trace 不带 elementUuid。未知视图、错误根 UUID/类型均明确拒绝。

响应包含真实主体、模型版本、commitId 和 representation；representation 的 diagram 或 matrix 二者之一有值。模型快照接口同时给出支持的视图描述。只读页面核对矩阵与已打开模型的版本、提交和主体，发生变化时要求重新打开。权限失败不会返回空矩阵掩盖错误；响应禁止缓存。新接口保持只读，无 POST 保存方法。

## 真实验收

综合专项 **55/55 通过**，原始报告：`build/polarion-store/m2-live-a3d243ed-e845-4c18-a795-5fd8b49f3116.json`。

新增模型包含三个分层定义、部件、端口、接口、连接、两条分配及一项需求追溯，并故意使用重复业务编号测试 UUID 投影。分别在内部调用与真实 8080 接口生成七种视图；检查矩阵两条分配/一条追溯、无编辑动作、禁止缓存和账号隔离。新建解码对象再次重建 BDD 成功，不依赖上次内存投影；该检查不代替删除原生 Sirius 编辑上下文后的完整 AC01 验收。

本轮共 36 个临时工作项全部清理，删除后逐项 GET 确认不存在；附件随之清理，审计和历史保留。原字段、历史需求、模型浏览和双账号检查均保留。

真实矩阵浏览器 **7/7**：打开真实模型，分配矩阵两条标记及禁用编辑单元格，需求追溯矩阵一条标记，账号隔离及清除凭据。报告 `build/model-store-matrix-ui-report.json`。历史需求和普通模型浏览器各 7/7 同时通过。

完整离线 check：backend 546、VSM 51、SSO 6，共 **603 项通过**，无失败、错误或跳过，性能门槛通过。前端 **53 项通过**，TypeScript/Vite 构建成功。既有性能门槛仍是本地模型基准，不代表远端大模型并发性能已验收。

复现专项在原有三个开关基础上增加 `SYSARCH_M2_VIEWS=true`，即 REQUIREMENTS、VIEWS、DEPLOYED_HTTP、DEPLOYED_UI 四个 SYSARCH_M2 开关均为 true，再执行 `gradlew.bat --offline :backend:polarionLiveReadAcceptance`。普通 check 不运行真实远端专项。

## 回归中发现的问题

首次图形投影重复创建了供编辑工具使用的 ConstraintValidator/OCL 环境。专项能通过，但完整回归出现 94 项既有 OCL 断言失败，并有 Concurrent EnvironmentFactory 警告。通过 [Eclipse OCL 说明](https://wiki.eclipse.org/OCL/FAQ#Concurrent_EnvironmentFactory_instances) 和本机依赖诊断核对后，改为不依赖编辑工具/OCL 的只读描述构建路径；新增现有校验器与只读渲染共存测试，完整回归恢复通过。该失败版本未部署。

另核对了 Sirius operationValidator 的实际用途：它会检查读取提供器（例如 Node#semanticCandidates），不能将其简单当作编辑操作拦截器。当前只读保护来自无编辑上下文、无工具栏、拒绝修改回调和语义变更检测。

## 部署及剩余工作

当前主后端 PID **2504**，JAR SHA-256 `eb7ec7cb2674f521d6c0bbf9eb62c9431370a8d137a199795775d2165daa39c8`。受限部署目录仍为 `build/deployments/20261118T110958Z`，核验 9/9，phase=VERIFIED；保留候选历史、精确旧 JAR、数据库备份及会话密钥。本轮无新增数据库迁移，也未重启 Polarion。

M2 后续仍需原生 Sirius 只读画布/会话接入、原生编辑上下文生命周期与布局恢复、统一附件 ACL 及完整权限路径验收。M3 正式命令写入和后续编辑入口接入仍未开启；已有 LOCAL 模型未迁移。
