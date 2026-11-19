# 模型快照格式 1.0

当前实现：`backend/.../storage/ModelCodec.java`、`StoredModel.java`、`ModelIdentityRegistry.java`。

## 覆盖范围

编码器遍历指定资源中的全部 SysArch EObject，而非只遍历 ArchElement。包括 ConstraintExpr、参数、行为、交互及 DiagramLayout。保留文档身份、根顺序、包含列表顺序、交叉引用、EMF 实际已设置的持久属性以及缺省值语义。业务编号可以相同，不据此合并对象。

所有对象使用独立 UUID。调用者在同一个编辑上下文内复用身份注册表；从快照解码会恢复同一 UUID 注册表。旧 Sirius 模型尚没有持久化这些 UUID，重新加载旧模型后另建注册表会生成新 UUID，因此正式迁移必须固定一次身份映射并保存快照，不能每次重新迁移重新编号。本阶段不把 UUID 写入现有模型，也不改变旧模式的编辑历史。

DiagramLayout 是 Ecore 中的布局对象。真实 Sirius representation 的坐标和样式不一定存储在这种对象中；本编码器不声称已保存整个 Sirius 图形文档。M5 必须另外实现 representation／图定义与布局的适配和完整恢复，再验收 AC01、AC16 和 AC21。

## 数据约定

- 根字段：schemaVersion、modelUuid、documents、nodes。
- Document：documentUuid、roots；roots 保持原始顺序。
- Node：uuid、type、attributes、references。
- attributes：特征名到 EMF EDataType 词法字符串列表的映射；单值必须恰好一个字符串。
- references：特征名到 UUID 列表；包含与交叉引用均保存，反向 container 引用由 EMF 恢复。
- 不设置的属性不写入；空字符串保留；setter 自动恢复缺省值时按当前 EMF 语义处理，不能恢复元模型本身无法区分的值。
- 固定排序 JSON 字段、特征键、节点 UUID 及文档 UUID；保留有序集合顺序和文本换行。此为应用格式，不宣称 RFC 8785。
- 不支持的 schema、类型、特征、外部引用、重复身份／根、多包含 owner、包含循环、无 owner 对象、非法枚举及数字均拒绝。
- 快照整体上限 20 MiB，先检查长度再解析；JSON 重复键、未知属性及尾随 JSON 均拒绝。
- 当前是持久化结构检查，不代替业务 OCL、端口兼容性或模型合法性检查；提交路径还需运行这些约束。

## 摘要

`complete` 为全部规范化内容的 SHA-256；`semantic` 排除 DiagramLayout；`layout` 仅包含 DiagramLayout。跨语义与布局的 EObject 引用被拒绝，避免语义 hash 依赖布局。DiagramLayout.elementRef 仍按现有元模型保存字符串；正式存储阶段需建立与稳定对象 UUID 的明确映射。

解码必须提供文件实际字节的 expectedSha256，先核验再解析。业务编码和引用类型检查全部完成后才构建资源，不触碰现有运行模型。完整编码不包含凭据、账号会话或机器导出路径。

## 仓储适配

LocalModelRepository 目前是内部、只读适配器。先解析项目，再以已验证 Principal 调用 ProjectReadAccess，最后在 ProjectModels.read 调度的线程中捕获快照。它没有新增公共 HTTP 入口，也不替换当前 LOCAL 保存链路。

ModelStorageRegistry 通过 Liquibase 019 的登记表保存模型 UUID 和模式。缺少登记时默认 LOCAL；登记操作校验读取及 MODEL_EDIT 权限，数据库唯一约束处理并发。当前只提供 LOCAL 登记，没有切换接口。

ModelRepositoryRouter 遇到 POLARION 登记即拒绝，不静默回退到本地。设置新功能开关只装配内部读取 bean，不改变任何现有工具 mutation。M4 仍需接入并检查所有编辑路径，不能把此内部路由当成已实现的全入口写保护。

模型头版本、命令提交、RequirementReference 及其修订固定属于后续阶段。本快照保留当前 Requirement 对象全部字段；它不是已经实现的需求引用权限模型，禁止直接把它当成对外可下载的 Polarion 快照 API。
