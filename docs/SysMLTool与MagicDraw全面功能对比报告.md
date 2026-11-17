# SysMLTool 与 MagicDraw 全面功能对比报告

评估日期：2026 年 11 月 17 日。评估对象：当前 `C:\SysMLTool` 工作区及已部署候选版本，产品界面名称为 SysArch Studio。

**结论：SysMLTool 已具备围绕 Polarion 开展结构架构、接口设计、分层分配、需求追溯和报告交付的可运行能力，可以进入限定范围的替代试点；目前不能作为 MagicDraw／Cameo 的全面替代产品。** 最主要的差距是标准语言语义、复杂行为及执行仿真、模型交换保真度、大型模型编辑体验和企业级协作运维。SysMLTool 的优势集中在浏览器操作、专用分层规则、Polarion 业务流程和源码可定制性。

这里的“可以试点”不等于已完成生产验收。当前完成了本机部署和真实 Polarion 单管理员账号的接口联调，真实双账号跨项目隔离、Cameo 实际模型往返和四周业务试点仍待完成。

## 1 对比范围与判定方法

### 1.1 对比版本

SysMLTool 按当前工作区评价，包括尚未提交到 Git 的浏览器同步入口和项目读取隔离改动。已部署候选 JAR 的 SHA-256 为 `3dd1e3095eec3e04cc7167893eff49b71ed7736b2319136875f5de7fc87f83a9`。不能只检出旧提交就复现本报告全部能力。部署和测试记录见 [本机联调记录](e2e-test-report.md#11-本机部署与真实单账号联调2026-11-17)。

商业产品以官方可核实的 **2026x Refresh1** 产品线为主要基准，该版本于 2026 年 6 月 26 日发布；个别长期功能引用 2024x 文档，并在来源中注明。此处不宣称它是所有客户当前安装版本，也不把产品文档视为本机实测结果。[官方版本公告](https://3dswym.3dexperience.3ds.com/post/catia-mbse-cyber-systems/now-released-2026x-refresh1_nS0Ya-VaT9OtOoj6dnPSRg)

### 1.2 MagicDraw 需要分产品比较

| 对比对象 | 在本报告中的含义 | 采购及实施边界 |
|---|---|---|
| MagicDraw 基础平台 | UML、软件及数据库建模、通用图形与扩展平台 | 不把所有系统工程插件和服务器功能计入基础产品 |
| SysML v1 建模组合 | MagicDraw 加相应 SysML 功能，或 Cameo Systems Modeler／Magic Cyber Systems Engineer | 用于比较 BDD、IBD、需求、参数和行为建模；具体版本与授权按合同核对 |
| SysML v2 组合 | 支持的建模产品加 SysML v2 Plugin，按任务增加 Evaluation 等插件 | 官方兼容表对 Cameo Systems Modeler 标明 Enterprise 版本条件；不能假定任意 MagicDraw 版本均可安装 |
| 仿真组合 | Cameo Simulation Toolkit／Magic Model Analyst；v2 执行还需相应 v2 Simulation 配套 | 与静态建模分开评价，插件依赖及许可证必须核对 |
| 团队与评审组合 | Teamwork Cloud／Magic Collaboration Studio，以及 Collaborator／MagicLab 等服务 | 服务器、评审端和建模客户端属于不同部署及许可项 |
| 外部工具与行业组合 | DataHub、特定连接器、UAF、行业插件等 | 有生态方案不代表基础产品原生提供，更不代表已覆盖本项目的特定 Polarion 配置 |

产品定位见 [MagicDraw](https://www.3ds.com/products/catia/no-magic/magicdraw)、[Cameo Systems Modeler](https://www.3ds.com/products/catia/no-magic/cameo-systems-modeler)。SysML v2 的版本条件见 [官方兼容表](https://docs.nomagic.com/PC/latest/catia-no-magic-products-254414890.html)，仿真依赖见 [v2 仿真前置条件](https://docs.nomagic.com/SYSML2SP/2026x-refresh1/prerequisites-308314148.html)。

### 1.3 实现与验证等级

下文“已实现”表示存在可调用的实现及相应测试或操作路径；“部分实现”表示对象、语义、流程或规模有明确限制；“未实现”表示当前实现范围内没有相应能力；“待实测”表示相关代码或接口存在，但目标环境验收尚未完成。

测试等级分别为代码及自动化、模拟浏览器、真实本机接口联调、真实业务验收。它们不互相替代。商业产品一侧主要依据官方功能文档，不给出未经同环境测试的速度、稳定性或易用性排名。

## 2 当前 SysMLTool 已实现的能力

### 2.1 底座和模型

当前实现是 **Sirius Web 运行时加自定义 EMF Ecore 元模型**，命名空间为 `http://example.com/sysarch/1.0`，不是完整 SysON／KerML 内核。项目沿用了原方案的视图配置和分层思路，实际语义范围以 `sysarch.ecore` 为准。

主要对象包括块定义、部件、端口、接口、连接、条目流、需求、分配、约束块、参数、绑定连接、状态机、状态与迁移、活动与动作、用例与参与者、交互与消息。公共字段包括业务编号、名称、说明、层级、字符串构造型、Polarion 工作项编号和修订号。

五层字段为 OA、SA、LA、PA、EPBS；主要图形能力落在 SA、LA、PA。OA 与 EPBS 的存在主要体现在层级标识及分配约束，不能据此称为完整 Arcadia 实现。来源：[元模型](../metamodel/sysarch.ecore)、[模型与视图说明](metamodel-and-views.md)。

### 2.2 可操作的功能清单

| 功能域 | 当前能力 | 主要边界 |
|---|---|---|
| 结构建模 | BDD、IBD、继承、组合、部件、端口、接口、连接及流标签 | 自定义结构语义子集，不是完整 UML／SysML 元模型 |
| 分层架构 | SA、LA、PA 视图和层级过滤，相邻层分配约束 | 没有完整 OA、EPBS 专用建模流程；SA 图不等于 Capella 完整功能链语义 |
| 表格追溯 | 分配矩阵、需求满足及验证矩阵 | 专用表格；部分刷新为定时轮询 |
| 行为 | 状态机、活动、用例的图形建模及静态检查 | 不执行；复杂标准构造覆盖有限 |
| 时序 | 交互、生命线、消息模型及 SVG／PNG 输出 | 图形只读，在模型树维护内容 |
| 参数 | 约束块、参数、绑定和外接求解 | 同单位绑定，无自动换算；受限表达式 |
| 版本协作 | 基线、结构差异、分支、三方合并预演及冲突处理 | 分支单写者；单后端实例，不是任意实时协同算法 |
| 日常协作 | 元素租约锁、会话上限、评论、审计 | 真实多人生产身份负载尚待验证 |
| Polarion | 嵌入入口、SSO、工作项绑定、推送预检与确认、拉取、一致性报告及修复 | 手动同步需本人 PAT；按字段归属同步，不是任意字段双向合并 |
| 权限 | 项目成员读取隔离、角色授权、REST／GraphQL／订阅保护 | 模型粒度；未绑定模型保留显式共享策略，身份角色有缓存边界 |
| 交付 | SRS、ICD、追溯报告的 HTML、DOCX、PDF 输出 | 固定领域数据契约，不是全模型任意查询报告平台 |
| 交换 | 自定义 SysML v2 文本子集、XMI 结构映射、Papyrus 输出变体 | 非全语义无损；实际 Cameo 往返未验收 |
| 运维 | 本机部署、Docker／Helm 资产、指标、备份和恢复脚本 | 真实 K8s、故障恢复时间与长期业务试点尚未形成完整验收 |

视图目录目前有 **9 类图形表示和 2 类表格表示**。时序图另走只读图片路径。这一数量包含三种业务分层图，不能与“SysML v1 九种标准图全部覆盖”画等号。来源：[视图配置目录](../views)、[参数和行为说明](param-behavior.md)、[操作说明](layers-and-trace.md)。

## 3 标准语言与模型语义对比

| 能力 | SysMLTool 当前状态 | MagicDraw／Cameo 对照能力 | 判断及影响 |
|---|---|---|---|
| UML 通用语义 | 部分映射结构和行为对象，没有完整 UML 元模型 | MagicDraw 的主要基础能力是通用 UML 建模 | 软件详细设计、复杂 UML 模型不能直接迁入替代 |
| SysML v1 | 借用块、端口、需求、参数等概念，XMI 按部分 SysML 1.6 映射 | 对应 SysML 产品／插件提供标准图和语言支持 | 对象名称相似不能证明标准语义等价 |
| SysML v2／KerML | 自定义文本导入导出子集；内部不是 KerML | 2026x 产品线已有专门 v2 插件及图形、文本能力 | 当前不具备完整原生 v2 建模替代能力 |
| 定义和使用 | PartDefinition、Part、InterfaceDef 等专用关系 | 商业语言实现覆盖更多类型、特征及语言关系 | 复杂特征重定义、子集化等应逐项验证，不能推定支持 |
| 构造型和领域扩展 | `stereotypes` 是字符串集合，层级是枚举 | UML Profile、构造型及平台扩展机制 | 字符串标签不具备完整 Profile 的类型约束和标签值语义 |
| 包和模型组织 | 导入解析及工作区组织服务于受限模型 | 通用模型组织、复用和项目使用机制 | 大型模型库和跨项目依赖管理差距明显 |
| 数值与单位 | 单位白名单、同单位校验，不换算 | 参数与执行分析功能可由相应配套提供 | 现有单位功能不能当成完整量纲与工程计算系统 |
| 模型约束 | Ecore／OCL 和业务规则，部分错误阻止写入 | 平台验证工具与可扩展验证机制；v2 新版本也有自定义验证规则 | 对固定业务规则有实用性，规则覆盖范围仍较窄 |

MagicDraw 的通用软件和数据库能力见 [基础产品说明](https://www.3ds.com/products/catia/no-magic/magicdraw)。v2 图文建模及插件划分见 [SysML v2 文档](https://docs.nomagic.com/SYSML2P/latest/sysml-v2-plugin-documentation-304004594.html)，v2 验证扩展见 [2026x Refresh1 公告](https://3dswym.3dexperience.3ds.com/post/catia-mbse-cyber-systems/now-released-2026x-refresh1_nS0Ya-VaT9OtOoj6dnPSRg)。本节未宣称商业实现通过所有标准条目的形式认证。

**实际影响：**如果要求“从 Cameo 迁出任意工程模型，再继续标准化编辑”，当前不能承诺；如果要求“管理系统组成、端口连接和需求映射”，当前元模型可满足限定对象范围内的工作流。

## 4 图形编辑能力逐项对比

官方 SysML v1 文档列出需求、BDD、IBD、包、参数、时序、状态机、活动、用例九类图。以下据此对照，不用 SysMLTool 自有视图数量代替标准覆盖度。[官方图类型说明，2024x](https://docs.nomagic.com/spaces/SYSMLP2024x/pages/136724703/Diagrams%2Bownership)

| 图或编辑能力 | SysMLTool | MagicDraw／Cameo 对照 | 差距 |
|---|---|---|---|
| BDD | 已实现定义、继承和组合的核心操作 | SysML 建模组合提供对应标准图 | 高级属性、完整类型及复用语义仍有差距 |
| IBD | 已实现部件、端口、连接及流显示 | 提供标准 IBD 及内部结构建模 | 应以真实复杂嵌套端口案例评估，而非仅比较图形外观 |
| 需求图 | 有需求对象和追溯矩阵，未识别到独立标准需求图编辑器 | 提供 Requirement Diagram | 矩阵不能替代需求层次和全部关系的图形表达 |
| 包图 | 有模型浏览及受限包处理，无独立标准包图编辑器 | 提供 Package Diagram | 模型组织的可视化表达不足 |
| 参数图 | 已实现约束块、参数、绑定 | 提供 Parametric Diagram；计算另看分析配套 | 支持范围及执行分析能力不同 |
| 状态机 | 已实现基本状态、初终态、迁移、entry／do／exit 显示 | 标准建模；配仿真工具可执行支持的行为 | 当前图不能执行，复杂层次和并发语义不能推定支持 |
| 活动图 | 动作、判定、控制流和对象流等基本表达 | 标准建模；配套支持执行分析 | 当前是静态子集，不能当作完整可执行活动模型 |
| 用例图 | 用例、参与者及关联 | 提供标准 Use Case Diagram | include／extend 等复杂关系需补齐或明确排除 |
| 时序图 | 模型树维护，SVG／PNG 只读 | 标准 Sequence Diagram 编辑 | 缺少画布编辑；片段、时间约束等不能推定支持 |
| SA／LA／PA | 已实现专用投影和层级规则 | 可用 SysML 及方法模板组织，不必使用相同层名 | 当前专用流程较直接，但语义不能等同 Arcadia |
| 分配和追溯矩阵 | 两种专用矩阵，可操作关系 | 商业平台提供表格／矩阵工作流 | 当前更贴合既定业务，通用配置范围较小 |
| 多视图一致性 | 模型驱动投影，布局与模型分离 | 模型与图形分离是同类建模平台的核心方式 | 有共同理念，不代表所有标准关系都已实现 |
| 大行为图 | 每区最多 200 节点，跨区连线不绘制而计数 | 商业产品具备更广的图形工具体系；本次没有同机对测 | 分区改善可用性，但损失同屏完整关系上下文 |
| 编辑体验 | 基于 Sirius 外壳、工具面板、模型树和菜单 | 成熟桌面建模工作流 | 不能用后端投影速度证明用户编辑效率相当 |

当前时序图降级和大图分区均是有意的范围选择，见 [行为说明](param-behavior.md) 与 [时序图探针](probes/p3-probe-a-sequence.md)。实际新建图布局曾存在重叠问题，后续做过修正；仍应以工程师完成真实任务的耗时、出错率和可读性验收，不只看演示截图。

## 5 方法与系统工程流程对比

SysMLTool 把分层约束做进了数据和操作：相邻层分配、接口一致性、需求满足及验证、交付报告。这适合组织已明确采用固定分层流程的场景。其“Capella 分层”应理解为方法启发，不能称为嵌入 Capella 或实现完整 Arcadia。

MagicGrid 是商业体系中的方法框架，与 SysML 语言和工具层分开；官方 2026x 课程也分别讲解语言、工具与方法。它不是与 OA／SA／LA／PA 一一对应的同义名称。[官方 SysML v2 与 MagicGrid 课程](https://akamai.3ds.com/edu/catia-magic-training/mbse-sysml-v2-and-magicgrid)

| 流程项 | SysMLTool 当前能力 | 对标判断 |
|---|---|---|
| 需求到结构追溯 | Requirement 的 satisfiedBy／verifiedBy、矩阵及报告 | 已形成基础闭环，验证关联本身不等于试验通过 |
| 功能分析 | SA 结构投影，另有基本活动模型 | 未形成完整功能、交换、场景、分配的统一分析链 |
| 逻辑到物理分配 | LA→PA 等相邻层分配和检查 | 可以承担限定分配任务 |
| OA 和 EPBS | 层级标识和约束 | 不足以承接完整运营分析及产品分解流程 |
| 全链路影响分析 | 局部关系、差异和一致性报告 | 未证明完整需求→功能→逻辑→物理的传递分析 |
| 备选方案权衡 | 可利用分支和参数求解支撑部分工作 | 不是专用的方案评价、优化和实验设计系统 |
| 行业架构框架 | 没有完整 UAF 等实现 | Cameo Enterprise Architecture／Magic Systems of Systems Architect 等属于另一产品组合，不能算作 MagicDraw 基础版能力 |
| 安全及合规方法 | 有权限和技术安全工作，但没有行业认证验收 | ISO 26262、ASPICE 等应落实到流程及证据，不能由产品名称推定合规 |

行业框架产品范围见 [官方 2026x Refresh1 企业架构版本说明](https://docs.nomagic.com/VN/2026x-refresh1/magic-systems-of-systems-architect-cameo-enterprise-architecture-314179598.html)。

## 6 参数求解与行为仿真对比

### 6.1 SysMLTool 已做到的范围

参数求解通过 Python／SciPy、HTTP 远端服务或 OpenModelica 适配器调用外部后端。表达式限于方程及白名单算术运算；支持欠定／超定检查、30 秒超时、残差判断、锁冲突和结果写回。单位必须完全匹配，不做转换。本机 Python 数值案例已验证，OpenModelica 未安装时的 503 分支通过，不等于实际 Modelica 求解通过。[参数功能说明](param-behavior.md#1-参数图与求解)

状态机、活动和用例只支持建模及静态检查，包括初始态数量、不可达状态、死迁移和 CALL 动作缺少表达式。**没有状态执行、活动执行、fUML 仿真或行为调试器。**

### 6.2 与商业分析组合的差距

| 能力 | SysMLTool | Cameo 分析组合 |
|---|---|---|
| 参数方程求解 | 外接、受限表达式和单位规则 | CST／Magic Model Analyst 有数学与脚本分析配套，外部数学引擎另有条件 |
| 动态行为执行 | 未实现 | 相应工具可执行支持的活动与状态行为 |
| 仿真过程动画和实例观察 | 未实现 | 2026x Refresh1 提供行为图实例动画筛选等能力 |
| 运行时变量与时间序列 | 没有统一行为运行时 | v2 Simulation 文档列有运行变量及时间序列能力 |
| 基于执行的自动需求判定 | 当前只有追溯关系与静态状态 | 相应执行组合支持自动验证场景；覆盖取决于模型和工具能力 |
| 脚本及外部数学引擎 | 自有服务适配器 | CST 支持多种脚本与数学引擎扩展 |
| 服务端仿真 | 无统一调度执行平台 | 有单独服务端仿真能力，存在许可证和功能限制 |

仿真能力分别参见 [2026x Refresh1 建模与仿真变化](https://docs.nomagic.com/VN/2026x-refresh1/magic-cyber-systems-engineer-cameo-systems-modeler-314179731.html)、[支持的脚本语言](https://docs.nomagic.com/MMA/2026x/supported-scripting-languages-255626228.html)、[服务端仿真条件](https://docs.nomagic.com/MMA/2026x-refresh1/server-side-simulation-304009990.html)。Alf 编译与 fUML 执行是额外配套关系，见 [Alf 插件说明](https://docs.nomagic.com/MAA/2026x/magic-alf-analyst-alf-plugin-documentation-254417242.html)。

**版本成熟度必须区分：**2026x Refresh1 的 **SysML v2 Simulation Plugin 标注为 Technology Preview**，不能把它与既有 v1 仿真能力一起统称为已经全面成熟的 v2 执行平台。[官方 v2 仿真版本说明](https://docs.nomagic.com/VN/2026x-refresh1/sysml-v2-simulation-plugin-314180796.html)

对需要模型可执行、场景回放和执行驱动验证的项目，SysMLTool 当前差距是核心能力缺失，不能只通过补几个按钮弥补。

## 7 需求管理与 Polarion 集成对比

### 7.1 当前可用的 Polarion 工作流

SysMLTool 已实现项目绑定、元素与 Work Item 绑定、推送预检、显式确认推送、拉取、在线一致性报告、逐项修复及嵌入入口。架构名称、分层、端点和追溯等由模型侧维护，需求标题、编号和正文等按字段归属由 Polarion 侧维护。

浏览器同步页面会核验 PAT 所属账号，验证请求不携带 SSO Cookie；PAT 仅保存在当前页面内存，切换账号或模型等操作会清除。选择变化使预检失效，字段冲突阻断确认，分支不静默写入主干。当前列表显示上限为 200 项，批量业务规模需要专门验收。[同步操作说明](layers-and-trace.md)、[新增入口与验证](e2e-test-report.md#9-b1-浏览器同步入口补齐2026-11-16)

**SSO 登录不等于无需再次提供同步授权。** 手动写 Polarion 仍需要本人 PAT，没有采用服务账号冒充用户写入。本机当前定时拉取关闭；存在调度实现不代表当前正在自动拉取，更没有实现模型修改后的自动推送。

### 7.2 与 MagicDraw／Cameo 的区别

| 需求及集成项 | SysMLTool | MagicDraw／Cameo 生态 | 判断 |
|---|---|---|---|
| 需求对象与结构追溯 | 已实现基本满足、验证及矩阵 | SysML／需求配套提供需求建模工作流 | 语义覆盖和高级分析深度不同 |
| Polarion 内嵌架构操作 | 本项目的直接业务目标，已有实际入口 | 基础产品文档不能证明存在完全相同的内嵌编辑流程 | SysMLTool 在本场景具有定制优势 |
| 特定 WI 字段及接口绑定 | 已有本项目映射和真实创建验证 | 通常需连接器或定制，并核对支持版本 | 不应把商业生态直接等同于本项目配置已经可用 |
| ReqIF 文件交换 | 当前未实现通用 ReqIF 导入导出 | 官方需求文档列出与 Polarion 等工具的 ReqIF 交换 | 不能把 REST 定制同步当成 ReqIF 标准交换 |
| 多来源需求管理 | 主要针对 Polarion | DataHub 等扩展服务多来源集成 | 商业生态覆盖面更广，实施和授权需单独评估 |
| 冲突处理 | 字段归属、预检、显式确认及专用修复 | 取决于连接器及同步方式 | 应对同一冲突用例实测，不能只按“支持双向同步”判断 |
| OSLC | 当前没有通用 OSLC 桥接实现 | 部分配套有 OSLC 场景 | 必须确认服务端、连接器、配置与目标系统版本 |
| Polarion 工作流权限 | 用调用者真实 PAT 写入 | 取决于连接器身份及部署配置 | SysMLTool 有明确身份边界，但真实双账号仍待验收 |

官方需求说明列出 Polarion 的 **ReqIF 交换**；这不能证明 DataHub 对当前 Polarion 配置提供原生实时双向同步。本报告不作该推断。DataHub 新版支持范围应按连接器核对。[2026x 需求管理](https://docs.nomagic.com/MT/2026x/magic-cyber-systems-engineer---cameo-systems-modeler/requirements-management-272728870.html)、[DataHub 2026x Refresh1 变化](https://docs.nomagic.com/VN/2026x-refresh1/cameo-datahub-314180175.html)

“更贴合 Polarion”在这里指已有专用流程，而非已证明在任何组织都比商业连接方案便宜、稳定或完整。

## 8 协作 版本和权限对比

| 能力 | SysMLTool 当前实现 | 商业组合对照 | 当前限制 |
|---|---|---|---|
| 共享模型编辑 | Sirius Web 服务端模型与订阅更新 | Teamwork Cloud 支持多人并行编辑模型和图 | 服务器运行架构及并发模型不同 |
| 元素锁 | 租约锁及冲突拒绝 | 商业协作有相应模型治理机制 | 锁争用等待和异常恢复要业务验收 |
| 基线与差异 | 不可变快照与结构差异 | 商业协作提供变更和版本管理 | 完整配置项、发布流和复用项目治理尚有距离 |
| 分支与合并 | 独立工作区、三方合并、预演及冲突决策 | 商业协作组合支持分支与合并 | 当前分支单写者，不能当成多人自由并发分支编辑 |
| 评论与审计 | 有实现 | 商业配套提供团队评审和浏览器协作 | 审批工作流、评审发布和审计治理深度不同 |
| 项目读取隔离 | 绑定项目成员或显式全局授权；分支继承主干归属 | Teamwork Cloud 有资源角色和作用域控制 | 当前主要是模型粒度，无 WI 字段级隔离 |
| 身份来源 | Polarion PAT、签名会话、SSO 对接 | 商业协作有独立身份与角色体系 | 当前角色缓存到重新登录的边界需接受或优化 |
| 未绑定模型 | 采用显式共享映射 | 商业资源按其授权配置 | 当前不是“所有未绑定模型默认私有” |
| 浏览器评审 | 当前网页与评论可用 | Collaborator、MagicLab 等属于专门配套 | 商业浏览器评审不等于全部桌面编辑器已搬到浏览器 |
| 横向扩展与高可用 | 当前不支持多后端实例协同 | 商业平台有相应企业部署体系，规格随版本核对 | 现有内存会话状态和锁／编辑上下文需要重新设计 |

商业侧依据：[Teamwork Cloud 产品说明](https://www.3ds.com/products/catia/no-magic/teamwork-cloud)、[角色和权限](https://docs.nomagic.com/MCS/latest/magic-collaboration-studio/user-roles-and-permissions-304001666.html)、[CATIA Magic 模型治理概览](https://www.3ds.com/products/catia/catia-magic)、[2026x Refresh1 浏览器协作变化](https://docs.nomagic.com/VN/2026x-refresh1/magic-collaboration-studio-teamwork-cloud-and-services-314179937.html)。这些资料不构成对本项目规模的性能承诺。

SysMLTool 的读取隔离覆盖 MVC、请求体、原生项目／文档接口、GraphQL 及 WebSocket 订阅事件。全局观察者回退不再自动取得所有已绑定项目的读取权。当前另有两个实际边界：原生分页经权限过滤后可能不足一页；Polarion 成员变更不能保证现有 Cookie 立即感知。详见 [读取隔离回归](e2e-test-report.md#10-项目读取隔离回归2026-11-16) 和 [运行手册](runbook.md)。

## 9 导入导出和迁移保真度

这是现阶段全面替代 MagicDraw 的关键阻断项。

| 格式或迁移任务 | 当前能力 | 保真边界 |
|---|---|---|
| SysML v2 文本 | 自定义解析和导出结构子集，保留业务编号及部分元数据 | 不是任意标准 SysML v2 文件都能导入 |
| v2 关系表达 | 部分结构使用原生文本表达 | 分配、满足、验证、ItemFlow 等大量信息借助文档注释保存；外部工具不一定建立相应语义关系 |
| v2 全模型导出 | 顶层导出主要覆盖 PartDefinition、InterfaceDef、Requirement、Interaction | 不能保证参数、状态机、活动等全部模型导出；“项目导出”不能解释为所有语义无损 |
| XMI 导出 | XMI 2.5.1／UML 2.5.1／部分 SysML 1.6 映射，支持 UML 2.5 声明选项 | XMI 是交换容器，不能自动解决工具间语义差异 |
| Papyrus 变体 | 提供特定命名空间、Profile 应用及基引用 | 特定验证不代表任意工具兼容；自身导入也不接受所有变体 |
| XMI 参数及行为 | 当前未完整导出参数、状态机、活动、用例等；交互另有实现 | 存在明确范围外对象，迁移会受限 |
| XMI 导入 | 自身输出及极简外部结构模型子集 | 复杂内容可能整批拒绝；没有直接导入完整 Cameo 工程包的能力 |
| 分层和自定义构造型 | 写入自有扩展／元数据 | 外部工具可能忽略或丢弃，不能保证跨工具保留 |
| 部件端口 | 导出时映射到 UML 类型所有的端口 | 同类型多个部件会共享，存在语义转换风险 |
| 图形布局交换 | 没有完整 Cameo／Papyrus 图形往返承诺 | 元素存在不代表原图可自动重建或布局保留 |
| 真实 Cameo 往返 | 待实测 | Eclipse UML2／Papyrus 检查不能代替 Cameo 实际验收 |

关键代码证据：[SysML v2 导出器](../backend/src/main/java/com/example/sysarch/export/SysmlV2Exporter.java)，尤其 `topLevel()` 和关系注释映射；详细格式边界见 [XMI 说明](xmi-export.md)。部分旧说明中“需要完整信息可用 v2 导出”的表述应按上述代码范围收窄，不能作为全模型无损承诺。

商业产品虽提供标准建模与交换能力，也不能因此承诺所有厂商扩展和图形无损互换。对本项目而言，最有效的验收是选择真实 Cameo 工程样本，逐项核对元素、关系、构造型、单位、分层、图形和再次编辑结果。

建议迁移验证样本至少包含：嵌套部件、同类型多实例端口、跨块连接、需求满足和验证、相邻层分配、参数模型、复杂行为、交互消息以及自定义 Profile。每个对象输出“原生保留／扩展保留／转换／遗漏／拒绝”的清单，不能只以文件可打开判定通过。

## 10 报告 扩展和软件工程能力

| 能力 | SysMLTool | MagicDraw／Cameo 对照 | 结论 |
|---|---|---|---|
| SRS／ICD／追溯报告 | 三类领域报告，HTML、DOCX、PDF | Report Wizard 等提供模板报告机制 | 当前满足专用输出，通用报告生态较窄 |
| 报告业务规则 | 在抽取器中计算，模板负责排版 | 商业报告平台提供模板和模型访问工具 | 当前结构清晰，但新增数据域通常需开发 |
| 覆盖率 | 由需求是否具备满足关联计算 | 商业工具可配置不同分析与指标 | 有关系不等于工程验证完成，指标口径必须公开 |
| 声明式视图 | YAML 经校验及代码生成，适配 Sirius | 商业平台通过 Profile、插件和开放 API 扩展 | 对已有图形原语扩展较方便，不是任意视图免开发 |
| 运行时无代码配置 | 当前需生成、构建、部署 | 商业配置与扩展也存在不同层次 | 不应对当前方案宣传“任意视图运行时配置即生效” |
| 开放 API | 自定义 REST、GraphQL 和外部适配器 | MagicDraw／Cameo 有公开 Java API 和插件机制 | 双方均可扩展，不能把商业产品表述为完全封闭 |
| 软件代码工程 | 没有通用代码生成和逆向工程 | MagicDraw 基础平台提供代码和数据库工程功能 | 软件详细设计工具替代差距较大 |
| ALM／PLM／ECAD | 已实现专用 Polarion 集成，其他未形成完整产品能力 | 商业生态有多种配套，逐项核对 | 当前不能承诺完整数字主线、BOM 或电气设计联通 |
| AI 建模 | 没有已交付、已验证的通用 AI 建模能力 | 本报告不将未核实或未采购的 AI 功能计入对手优势 | AI 不纳入现有功能替代结论 |

报告实现依据：[报告变量契约](report-context.md)。商业报告依据：[DOCX 等模板支持](https://docs.nomagic.com/MT/2026x/magic-software-architect---magicdraw/creating-a-template-for-a-rich-text-format-or-opendocument-text-document-or-microsoft-word-document-docx-272739736.html)。开放接口依据：[2026x 开发者指南](https://docs.nomagic.com/DEVG/2026x/modeling-tools-developer-guide-254437091.html)。代码工程依据：[MagicDraw 产品页](https://www.3ds.com/products/catia/no-magic/magicdraw)。

## 11 部署 运维 安全和成本

### 11.1 架构与交付状态

SysMLTool 采用 Java 21、Spring Boot 3.5.16、Sirius Web 2025.8.11、PostgreSQL 和 React 前端。版本以 [Gradle 版本锁定文件](../gradle/libs.versions.toml) 为准，部分较早 README 中的版本号不是当前实际依赖。

| 项目 | SysMLTool 当前状态 | 与商业方案比较时的含义 |
|---|---|---|
| 用户端 | 浏览器使用，支持 Polarion 嵌入 | 减少专用建模客户端分发；商业侧也有浏览器评审服务，应按角色比较 |
| 后端部署 | 本机实际运行；有 Docker／Helm 文件 | 部署文件存在不等于真实集群通过验收 |
| 服务可用性 | 当前单实例，编辑上下文和部分状态在内存 | 不具备多副本无缝故障切换；商业侧也应核对具体部署规格 |
| 开机恢复 | 本次是本机后台进程部署 | 不是已配置完整 Windows 服务及无人值守自动恢复 |
| 备份 | 有逻辑备份、WAL 和恢复相关脚本，部署前后有数据库备份 | 仍需从故障到恢复的实际演练验证 RTO／RPO |
| 监控 | 健康检查及受保护 Prometheus 指标 | 指标可访问不等于告警闭环与值班能力完成 |
| 安全措施 | 生产身份、匿名关闭、项目读取隔离、用户 PAT 写入、审计和扫描工作 | 不等于完成渗透测试、行业认证或生产安全评审 |
| 升级维护 | 定制适配层可控，但需要持续跟随 Sirius API | 2026.11 升级演练出现约 70 个编译错误，当前未完成升级 |
| 技术支持 | 依赖自研维护团队及上游社区 | 需建立可持续缺陷响应、版本维护和知识传递安排 |

来源：[运行手册](runbook.md)、[安全评审记录](security-review-p4.md)、[Sirius 升级演练](sirius-upgrade-2026.11.md)。升级问题集中在适配层说明架构隔离有价值，但不能据此忽略未来升级成本。

### 11.2 成本比较应采用总拥有成本

本报告不编造 MagicDraw 单席价格、折扣或节省百分比。商业成本应按建模席位、SysML／仿真等插件、协作服务器、评审端、连接器、维护、部署和培训逐项询价，并以合同确认许可范围。[官方 2026x 许可条款](https://www.3ds.com/assets/Terms/LicensedProgramTerms/CATIA/NoMagic_2026x.pdf)

自研总成本应至少包括：语言语义与图形功能开发、Sirius 升级、测试及安全维护、Polarion 版本适配、数据迁移验证、运维支持、培训和关键人员替补。源码可控及免去部分商业席位支出，并不等于零成本，也不意味着所有依赖均可不经许可审查直接再分发。

建议以同一三年业务范围估算：

`总拥有成本 = 许可与订阅 + 开发与定制 + 集成迁移 + 基础设施 + 运维安全 + 培训支持 + 升级和停机成本`

如果范围仅是 Polarion 内的结构架构与追溯，自研可能更容易控制需求；若范围扩展到完整标准语言、仿真、大型团队和全量历史模型迁移，成本结构将显著变化。这里是基于工程范围的判断，不是已经完成的财务测算。

## 12 已有验证能证明什么

| 记录 | 已有结果 | 可支持的结论 | 不能据此证明 |
|---|---|---|---|
| 当前 Java 检查 | 后端 457、VSM 51、SSO 6，共 514 项通过 | 已覆盖的逻辑和回归检查通过 | 全部产品需求完成或生产无缺陷 |
| 前端检查 | 49 项测试及生产构建通过 | 覆盖的接口和界面逻辑可构建 | 所有工程师任务的体验已合格 |
| 同步模拟浏览器 | 无头 Chrome 12 项通过 | PAT 核验、预检、冲突和清理等页面流程 | 新同步页面已完成真实 Polarion 浏览器全流程验收 |
| 读取隔离专项 | 26 项自动测试 | REST、GraphQL、订阅等已覆盖路径的授权逻辑 | 两个真实 Polarion 项目成员账号的正反向隔离已验收 |
| 真实本机接口联调 | 单管理员账号 15／15 通过，使用真实 Java、数据库和 Polarion | 登录、实际 WI 创建及清理、同步、基线、导出、订阅等可运行 | 真实双账号联调或完整交互式 SSO 入口验收 |
| P2 历史负载 | 最终一轮 30 个 dev 身份、30 分钟、110460 请求，0 意外错误及丢失修改 | 该版本与本机负载条件下协作链路可运行 | 30 个真实 Polarion 身份、当前鉴权版本、跨网生产负载通过 |
| 历史交换检查 | UML2／Papyrus 及受限 SysON 案例验证 | 特定结构样本能被相应环境识别 | Cameo 全工程往返、全部语义和原图无损 |
| 四周业务试点 | 报告仍有待填写的验收项 | 已有试点计划 | 已完成生产用户的持续使用验收 |

统计来源：[当前回归和部署记录](e2e-test-report.md)、[P2 历史压测](p2-load-test-report.md)、[P4 试点报告](p4-pilot-report.md)、[XMI 验证记录](xmi-export.md)。这些测试数量来自已有记录，本次报告工作没有重新运行完整产品测试。

历史压测的编辑 P99 为 91 ms，锁等待 P95 为 4.1 s、最大 20.7 s，完整 GC 后堆约 169–183 MB。它们有明确的本机、单实例、dev 身份和版本条件。当前进程内万元素 IBD 投影约 186.6 ms、矩阵 43.9 ms、报告 30.2 s；这些也不代表浏览器加载、网络和完整权限链路的端到端耗时。

**没有在同一硬件、同一模型、同一任务上测试 MagicDraw／Cameo，因此没有依据声称 SysMLTool 更快、并发更高或内存更省。**

## 13 按使用场景给出的替代结论

| 使用场景 | 当前建议 | 必须满足的条件 |
|---|---|---|
| Polarion 内查看和维护系统组成、接口及追溯 | 可以作为首批限定替代试点 | 使用支持的结构对象；明确字段归属和同步授权 |
| 固定 SA／LA／PA 分层架构与分配矩阵 | 可以试点 | 不把图名等同完整 Arcadia；业务规则经架构师确认 |
| 基于当前对象生成 SRS／ICD／追溯报告 | 可以试点 | 模板、编号和覆盖率口径通过交付评审 |
| 简单参数计算 | 可在配置外部求解器后试点 | 验证单位、方程范围、收敛标准和责任归属 |
| 简单静态状态机、活动、用例表达 | 可以辅助使用 | 接受当前语义子集，不要求执行 |
| 可交互编辑复杂时序 | 暂不替代 | 需要新编辑器或其他专业工具 |
| 执行模型、动态仿真、运行验证 | 暂不替代 | 需保留／采购对应商业分析组合或建立独立执行平台 |
| 完整 Cameo 模型资产迁移 | 暂不承诺 | 完成真实模型逐类迁移保真验收 |
| 原生 SysML v2／KerML 工程平台 | 暂不替代 | 需要语言内核及标准语义路线调整 |
| UML 软件设计、代码及数据库逆向工程 | 暂不替代 | 当前没有相应通用实现 |
| 跨部门大规模及高可用模型库 | 暂不替代 | 先完成多实例架构、身份负载和故障演练 |
| UAF 或完整行业架构工程 | 暂不替代 | 需要相应方法、元模型和行业配套 |

更可行的近期组合是：SysMLTool 承担 Polarion 中的结构架构与追溯工作，专业建模工具承担复杂标准模型和执行分析；二者交换严格限定在经验证的对象清单内。双工具并行时必须指定每类数据的唯一维护源，避免两边都可编辑而没有冲突规则。

## 14 差距优先级和可执行验收

### 14.1 进入生产试点前应完成的事项

| 优先级 | 工作 | 具体验收方式 |
|---|---|---|
| P0 | 真实双账号项目隔离 | 两个真实项目成员分别访问自己的及对方模型；覆盖 REST、GraphQL、订阅、报告、基线、分支及原生文档下载；验证缓存边界 |
| P0 | 新同步页真实浏览器验收 | 从 Polarion 入口进入，用本人 PAT 完成预检、创建、更新、拉取、冲突阻断、解绑、账号切换及精确清理 |
| P0 | 交换承诺收敛 | UI、API 和文档一致列出对象范围；对未导出内容给出数量及原因，禁止静默遗漏 |
| P0 | Cameo 实际结构互通 | 对固定复杂度样本在目标 Cameo 版本中双向操作，输出对象与关系级差异，不以文件打开为完成 |
| P0 | 部署和恢复演练 | 从备份恢复到隔离实例，验证模型、绑定、报告和审计；记录实际 RTO／RPO；配置自动启动和日志治理 |
| P0 | 当前生产身份负载 | 用真实或等价认证路径重新测试，覆盖并发编辑、锁争用、权限查询及订阅，不沿用 dev 压测作验收 |

这些 P0 是本报告提出的验收建议，不是重新把旧开发阶段编号改为未完成。

### 14.2 扩大替代范围时的建设顺序

| 优先级 | 能力建设 | 完成标志 |
|---|---|---|
| P1 | 需求图、包图和追溯影响分析 | 可视化管理支持范围内的层次和关系，报告能解释传递路径 |
| P1 | 大图编辑和布局体验 | 用真实工程模型测首次打开、操作响应、跨区上下文和任务完成时间 |
| P1 | 端口、类型和单位语义完善 | 覆盖同类型多实例、嵌套接口、量纲转换和错误反馈的工程用例 |
| P1 | 报告及模板产品化 | 模板配置、版本、审批、归档和结果复现具备可操作流程 |
| P1 | Sirius 升级及回归 | 完成适配层迁移，通过生成一致性、功能、浏览器和身份回归 |
| P2 | 原生 SysML v2 路线 | 明确采用标准语言内核或继续专用 DSL；实现定义／使用与关系的标准化持久化和交换 |
| P2 | 行为与时序深化 | 明确支持语义，增加画布时序及复杂行为；执行引擎作为独立项目验收 |
| P2 | 企业协作和高可用 | 外置会话与锁状态，解决编辑上下文一致性，并验证多节点故障恢复 |
| P2 | 标准连接生态 | 根据客户需求补 ReqIF／OSLC、PLM 等连接，而不是只增加接口名称 |

在标准语言路线未确定前，继续大量扩充自定义行为对象可能增加后续迁移负担。应先决定产品目标是“Polarion 专用架构应用”还是“通用 SysML 工程平台”，再确定投入顺序。

## 15 选型建议

如果主要业务目标是把分散的架构图变成 Polarion 内可追溯、可生成报告的模型资产，建议继续 SysMLTool 路线，以一个边界明确的真实项目完成双账号、交换、恢复和四周试点验收后，再逐步扩大使用。

如果目标是统一替代现有 MagicDraw／Cameo 中的复杂 SysML、UML、仿真和企业模型库，建议保留商业平台作为主工程工具。当前 SysMLTool 可作为专用业务前端或结构架构子系统，全面替换需要语言内核、执行分析和企业协作三个层面的持续建设。

不建议给出“已达到 MagicDraw 的某个百分比”。标准语义、图形种类、用户任务和生产成熟度的权重不同，缺失执行引擎或交换语义可能直接使某类任务不可用，不能用其他小功能数量抵消。

## 附录 A 仓库证据索引

以下链接相对于本报告所在目录，便于评审时直接核对。

| 证据 | 对应问题 |
|---|---|
| [原始自研方案](../自研MBSE工具开发方案：SysON底座%20+%20Papyrus视图机制%20+%20Capella分层（适配Polarion嵌入.md) | 原目标及完整平台愿景 |
| [Ecore 元模型](../metamodel/sysarch.ecore) | 实际对象、关系、层级和字符串构造型 |
| [视图配置](../views) | 9 图形与 2 表格配置 |
| [元模型及视图说明](metamodel-and-views.md) | 生成、投影和表格机制 |
| [分层与追溯操作](layers-and-trace.md) | 分配、同步和日常操作 |
| [参数与行为说明](param-behavior.md) | 求解器依赖、行为不执行、分区及只读时序 |
| [时序图探针](probes/p3-probe-a-sequence.md) | 图形编辑降级原因 |
| [SysML v2 导出器](../backend/src/main/java/com/example/sysarch/export/SysmlV2Exporter.java) | 顶层导出范围及注释关系映射 |
| [XMI 说明](xmi-export.md) | 格式变体、遗漏、端口转换及外部验证边界 |
| [报告数据契约](report-context.md) | SRS／ICD／追溯及覆盖率口径 |
| [当前测试与部署记录](e2e-test-report.md) | 514 项 Java、模拟浏览器、隔离及真实单账号验证 |
| [历史并发压测](p2-load-test-report.md) | dev 身份与单机性能条件 |
| [试点报告](p4-pilot-report.md) | 业务验收仍待完成的范围 |
| [运行手册](runbook.md) | 部署、权限缓存、备份和单实例限制 |
| [安全评审](security-review-p4.md) | 扫描及安全工作边界 |
| [版本锁定](../gradle/libs.versions.toml) | 当前 Java／Spring Boot／Sirius 依赖 |
| [升级演练](sirius-upgrade-2026.11.md) | 上游升级的实际成本 |

## 附录 B 官方资料及核查边界

检索日期为 2026 年 11 月 17 日。正文已在相关结论处直接引用官方页面。部分 `docs.nomagic.com` 页面在直接打开时返回 403，对这些条目仅采用官方页面可检索到的明确内容，不推断隐藏的细节、许可价格或完整标准符合性。实际选型需按客户采购版本核对安装介质、插件依赖和合同。

| 官方资料 | 用途 |
|---|---|
| [MagicDraw 产品页](https://www.3ds.com/products/catia/no-magic/magicdraw) | UML、代码工程和基础平台定位 |
| [Cameo Systems Modeler 产品页](https://www.3ds.com/products/catia/no-magic/cameo-systems-modeler) | 系统建模产品范围 |
| [2026x Refresh1 发布公告](https://3dswym.3dexperience.3ds.com/post/catia-mbse-cyber-systems/now-released-2026x-refresh1_nS0Ya-VaT9OtOoj6dnPSRg) | 发布日期和产品线变化 |
| [产品兼容表](https://docs.nomagic.com/PC/latest/catia-no-magic-products-254414890.html) | v2 插件的产品和版本条件 |
| [SysML v2 插件文档](https://docs.nomagic.com/SYSML2P/latest/sysml-v2-plugin-documentation-304004594.html) | v2 图形、文本和分析插件划分 |
| [SysML v1 图类型，2024x](https://docs.nomagic.com/spaces/SYSMLP2024x/pages/136724703/Diagrams%2Bownership) | 九类标准图的对照基准 |
| [v2 Simulation 版本说明](https://docs.nomagic.com/VN/2026x-refresh1/sysml-v2-simulation-plugin-314180796.html) | v2 执行能力及 Technology Preview 状态 |
| [仿真前置条件](https://docs.nomagic.com/SYSML2SP/2026x-refresh1/prerequisites-308314148.html) | 组合依赖 |
| [Teamwork Cloud](https://www.3ds.com/products/catia/no-magic/teamwork-cloud) | 共享模型及团队协作 |
| [协作角色与权限](https://docs.nomagic.com/MCS/latest/magic-collaboration-studio/user-roles-and-permissions-304001666.html) | 资源授权和作用域 |
| [2026x 需求管理](https://docs.nomagic.com/MT/2026x/magic-cyber-systems-engineer---cameo-systems-modeler/requirements-management-272728870.html) | ReqIF 与需求工具范围 |
| [DataHub 新版说明](https://docs.nomagic.com/VN/2026x-refresh1/cameo-datahub-314180175.html) | 连接器及 v2 支持变化 |
| [2026x 开发者指南](https://docs.nomagic.com/DEVG/2026x/modeling-tools-developer-guide-254437091.html) | Java API 与插件机制 |
| [官方许可条款](https://www.3ds.com/assets/Terms/LicensedProgramTerms/CATIA/NoMagic_2026x.pdf) | 商业许可核对入口；不作为报价 |

