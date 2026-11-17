结合你前面的 **“Polarion 内嵌 diagrams.net 系统架构绘图插件”**规格书，我的核心判断是：

> **当前方案适合定位为“架构可视化与需求追溯工具”，但还不能等同于完整的 SysML/UML 建模工具。**
>
> 如果目标只是绘制架构、管理元素和关系、关联需求与变更，现有方向合理；如果目标进一步包括端口类型检查、行为建模、跨图一致性、模型分析和标准模型交换，就需要引入明确的模型语义层，或者集成专业建模工具。

你的规格书已经明确了节点绑定架构元素、连线绑定连接关系、图纸作为附件存储、复用 Polarion 权限和版本管理等能力。这构成了良好的轻量级架构管理基础，但规格书尚未定义完整的 SysML/UML 元模型与语义规则。粘贴的 Markdown (1)

下面分别分析语言、工具，以及对你现有方案的影响。产品与标准状态以 **2026 年 11 月 5 日查询到的官方资料**为依据；方案取舍属于针对当前项目的建议。

---

## 一、先区分：绘图工具、建模工具和生命周期管理工具

这三类工具可以协同，但不能相互简单替代。

| 类型 | 核心管理对象 | 主要解决的问题 | 在你项目中的对应 |
|---|---|---|---|
| **绘图工具** | 图形、连线、标签、布局 | 如何直观表达系统 | diagrams.net / draw.io |
| **建模工具** | 有类型、有规则、有身份的模型元素及关系 | 系统结构、行为和约束是否一致 | Cameo、Enterprise Architect、Rhapsody、SysON 等 |
| **生命周期管理工具** | 需求、任务、测试、变更、审批和基线 | 工程过程如何追溯与受控 | Polarion |

draw.io 官方确实提供 UML、SysML 图形库；而专业建模工具进一步管理模型关系、执行一致性检查，并可能支持模型分析或执行。**图形库提供的是表示手段，模型语义才决定工具能否理解你画的内容。**[drawio.com](https://www.drawio.com/docs/diagram-types/uml/profile-diagrams/?utm_source=chatgpt.com)

例如，画出：

```text
温度传感器 ── 温度数据 ──> 控制器
```

对于绘图工具，这主要是两个图形和一条箭头。

对于你要设计的建模工具，还应能回答：

- 这两个对象是可复用的类型，还是某个系统中的具体使用角色？
- 连线连接的是组件，还是组件的某个端口？
- 两端的数据类型、单位和方向是否兼容？
- 同一个控制器出现在另一张图中时，是否仍是同一个模型元素？
- 修改接口后，哪些需求、行为和测试受到影响？

**因此，选型的关键不是“能画多少种图”，而是“需要让工具理解和检查多少工程语义”。**

---

## 二、UML、SysML v1、SysML v2 分别适合什么

### 1. UML 与 SysML 的定位差异

| 维度 | UML | SysML v1 | SysML v2 |
|---|---|---|---|
| 主要侧重 | 软件与面向对象系统的结构、交互、行为设计 | 多学科系统的需求、结构、行为和参数约束 | 更统一、精确、可计算的系统模型 |
| 常见关注对象 | 类、组件、接口、对象、状态、消息 | Block、部件、端口、需求、约束、分配关系 | 定义与使用、结构与行为、需求与验证等系统模型元素 |
| 语言基础 | UML 元模型 | 基于 UML 的扩展机制 | 基于 KerML，不是简单增加几个 UML 构造型 |
| 表达方式 | 图形化建模为主 | 图形化建模为主 | 同时提供标准化图形与文本语法 |
| 对工具集成的意义 | 关注模型 API、XMI、工具扩展 | 关注模型 API、Profile 和交换兼容性 | 还应评估 Systems Modeling API and Services 支持情况 |

UML 与 SysML v1 的联系，可以从两者共享并扩展的图类型理解；SysML v2 则采用 KerML，并引入标准文本语法和配套标准 API。[drawio.com](https://www.drawio.com/docs/diagram-types/sysml-vs-uml/?utm_source=chatgpt.com)

当前 OMG 正式规范页面列出的 UML 版本为 **2.5.1**，SysML 正式版本为 **2.0**；SysML v1 系列的最新正式版本为 **1.7**。不能把“某个工具支持 SysML 1.5”直接理解为支持全部 SysML v1.7 或 v2 能力。[OMG](https://www.omg.org/spec/UML/)

### 2. 对你项目的实际意义

**做软件架构设计：**重点评估组件、接口、部署、时序、状态和活动等建模能力，不必因为项目名称里有“系统架构”就强制采用完整 SysML。

**做跨软硬件的系统工程：**需要同时处理需求、功能、结构、接口、行为、约束和验证关系，SysML 或 Arcadia 这样的建模语言／方法组合更值得评估。OMG 对 SysML 的范围明确包含硬件、软件、信息、人员、过程和设施。[OMG](https://www.omg.org/sysml/)

**做下一代自研建模平台：**建议评估 SysML v2，但不要把“生成 `.sysml` 文本”当成已经具备完整建模能力。还需要解析、类型解析、引用解析、语义校验和受控的模型更新。

这里也要区分：**SysML 是语言，不会自动给出完整的工程实施方法。**例如应该先做场景分析还是功能分解、怎样形成逻辑与物理架构，仍需要企业自己的建模方法或 Arcadia 等方法指导。[mbse-capella.org](https://mbse-capella.org/arcadia_capella_sysml_tool.html?utm_source=chatgpt.com)

---

## 三、主流工具对比

下表中的“官方能力”来自产品资料，“适用判断”是针对你当前 Polarion 项目的分析，不是统一排名。

| 工具 | 官方资料体现的核心能力 | 对你项目的适用判断 | 必须重点验证的事项 |
|---|---|---|---|
| **CATIA Magic / Cameo 产品族** | SysML/UML 建模、一致性检查、模型分析；协作产品提供版本、分支、合并与访问控制。[Dassault Systèmes](https://www.3ds.com/products/catia/catia-magic) | 适合作为专业系统建模环境，与 Polarion 分工协同 | 产品角色与许可证组合；所选版本的 v1/v2 能力；Polarion 集成粒度 |
| **Sparx Enterprise Architect** | SysML 1.5 结构、行为、需求建模及参数分析；官方文档列出九类 SysML 图支持。[sparxsystems.com](https://www.sparxsystems.com/enterprise_architect_user_guide/17.2/modeling_languages/sysml.html) | 适合同时开展软件设计和系统建模的团队，值得纳入通用工具 PoC | 协作部署、模型交换、自动化接口，以及现有版本是否满足所需语义 |
| **IBM Engineering Rhapsody** | UML/SysML/AUTOSAR 建模、仿真、静态模型检查及嵌入式代码生成。[IBM](https://www.ibm.com/products/engineering-rhapsody-developer) | 当架构设计要延伸到嵌入式软件行为和代码实现时，优先级较高 | 建模、仿真、代码生成所需产品版本与附加组件 |
| **Capella / Arcadia** | 围绕运行需求、系统分析、逻辑架构和物理架构组织工程模型。[mbse-capella.org](https://mbse-capella.org/arcadia_capella_sysml_tool.html?utm_source=chatgpt.com) | 适合重点解决“系统架构如何逐层设计”，而不是要求所有内容必须使用标准 SysML 表达的场景 | 企业是否接受 Arcadia 方法；与现有模型、需求和专业工具的衔接 |
| **Eclipse Papyrus** | 可定制的 UML/SysML 建模环境；官方列出 UML、SysML 1.6、模型执行及领域扩展能力。[eclipse.dev](https://eclipse.dev/papyrus/?L=EN) | 适合具有建模平台研发能力、需要深度定制的团队 | 经典版与 Web 版的功能差异、插件兼容和维护责任 |
| **Eclipse SysON** | 开源、Web 化、面向 SysML v2 的模型编辑环境，基于 Sirius Web。[projects.eclipse.org](https://projects.eclipse.org/projects/modeling.syson?utm_source=chatgpt.com) | 很适合作为“Polarion＋Web 建模”方向的技术底座候选 | 标准 API 覆盖、模型配置管理、权限、性能及工程化成熟度 |
| **MATLAB System Composer** | 组件、端口、连接器、接口等架构建模，并与 MATLAB/Simulink 的分析和详细模型衔接。[MathWorks](https://www.mathworks.com/help/systemcomposer/?utm_source=chatgpt.com) | 对已经使用 MATLAB/Simulink 的团队，应单独纳入评估 | 版本兼容、需求追溯工具链、模型交换范围；不能默认等同于通用完整 SysML 工具 |
| **draw.io＋Polarion 自研插件** | draw.io 提供 UML/SysML 绘图表示；你的规格书增加 WI 绑定、附件持久化与一致性告警。[drawio.com](https://www.drawio.com/docs/diagram-types/uml/profile-diagrams/?utm_source=chatgpt.com) 粘贴的 Markdown (1) | 最适合当前轻量级、Polarion 内嵌、流程复用的目标 | 模型语义、跨图一致性、关系规则等都需要自行定义和实现 |

### 几个会直接影响选型的版本问题

**Enterprise Architect 与 Trechoro 不能混为一谈。**  
Sparx 已公布面向 SysML v2 的 Trechoro，但其公开页面仍包含开发进展与初始发布路线图。应单独核实可交付版本和功能，不能据此认定现有 Enterprise Architect 安装已经具备同等的 v2 能力。[sparxsystems.com](https://sparxsystems.com/mbse/sysml2/)

**传统 Rhapsody 与 Rhapsody Systems Engineering 也要分开评估。**  
IBM 将 Rhapsody Systems Engineering 描述为云原生、Web 化、面向 SysML v2 的产品；这不等于传统 Rhapsody Developer 的所有能力、接口和部署方式可以直接照搬。[IBM](https://www.ibm.com/products/rhapsody-systems-engineering?utm_source=chatgpt.com)

**Capella 不应笼统称为“标准 SysML 工具”。**  
现有 Capella 的核心是 Arcadia 方法及其专用建模语言。另一个项目 **Capella for SysON** 正在把 Arcadia 引入 SysML v2 和 Web 建模，其公开路线图将首个集成 MVP 安排在 2027 年，不能当作现有 Capella 已全面迁移完成。[mbse-capella.org](https://mbse-capella.org/arcadia_capella_sysml_tool.html?utm_source=chatgpt.com)

**SysON 开源，不代表企业平台能力已经齐全。**  
其 v2026.9.0 官方 FAQ 明确说明，没有内置完整的模型版本、分支和访问控制功能，需要结合配置管理等基础设施。因此，选择 SysON 的主要收益是复用建模基础，而不是免除平台工程工作。[MBSE Syson](https://doc.mbse-syson.org/syson/v2026.9.0/user-manual/faq/faq.html)

**你前面日志中的 MATLAB 是 R2024b。**  
MathWorks 当前文档中的 `systemcomposer.sysml.exportFromMLProject` 标注为 **Since R2026a**。评估 System Composer 的 SysML v2 能力时，不能直接把新版能力套用到现有 R2024b 环境。[MathWorks](https://www.mathworks.com/help/systemcomposer/ref/systemcomposer.sysml.exportfrommlproject.html)

---

## 四、你的规格书距离 SysML/UML 建模工具还差什么

### 1. 目前已经具备的是“可追溯架构图”的基础

规格书将节点和连线分别绑定到 `architecture_element`、`architecture_connection`，并用 `architecture_diagram` 管理图纸；连接关系包含起点、终点、类型、协议和描述。这能够表达通用组件拓扑，但尚未明确端口、定义与使用、行为、参数约束等对象。粘贴的 Markdown (1) 粘贴的 Markdown (1)

建议按下面的能力差距评估，而不是直接给界面增加一组 SysML 图形。

| 能力 | 当前规格书的基础 | 向建模工具演进时需要明确的内容 |
|---|---|---|
| 元素身份 | 节点绑定 WI ID | 模型元素与图元是否分离；同一元素如何出现在多张图 |
| 类型复用 | 通用元素分类、父级关系 | 类型定义与具体使用角色如何区分 |
| 接口建模 | `protocol` 和起止元素 | 端口、接口类型、方向、数据类型、单位、多重性 |
| 关系语义 | 依赖、调用、数据流、组成等枚举 | 不同关系允许连接什么对象，各自如何校验 |
| 行为建模 | 未定义 | 状态、转换、事件、动作、活动、消息等 |
| 参数分析 | 未定义 | 参数、量纲、约束表达式和分析结果 |
| 跨图一致性 | 打开时刷新 WI 属性 | 同一元素在所有视图中的一致性和冲突处理 |
| 模型交换 | `.drawio` XML | 与 UML/SysML 模型的映射、交换范围及往返保真测试 |
| 配置管理 | 复用附件和 WI 历史 | 图纸、模型元素、关系和需求的版本如何形成一致快照 |

这里的“未定义”是指**规格书没有规定**，不是说 Polarion 永远无法承载。

### 2. 第一项关键设计：模型元素与图元分离

建议明确：

> **模型元素是工程对象；图元是该对象在某张图中的一次展示。**

例如，同一个控制器可以出现在系统上下文图、内部结构图和部署图中。不能因为画了三个矩形，就创建三个不同的控制器对象。

建议采用类似下面的身份结构：

```text
模型元素：
  modelElementId
  repositoryId / projectId
  elementType
  revision

图元：
  diagramId
  shapeId
  modelElementId
  布局、样式、显示配置
```

这是建议的数据设计，不是 SysML 标准字段定义。

由此需要明确两种不同操作：

**从当前图移除：**只删除图元。  
**从模型删除：**删除工程对象，必须检查其他视图、接口和追溯关系。

这比增加多少种图标更重要。

### 3. 第二项关键设计：区分“定义”和“使用”

以一个温度采集系统为例，建议不要把下面三个概念混在一起：

```text
可复用类型：TemperatureSensor

系统中的使用角色：
  inletSensor  : TemperatureSensor
  outletSensor : TemperatureSensor
```

这里的两个使用角色共享类型，但可能具有不同的位置、连接和配置。它们也不必直接等同于制造阶段带序列号的实物实例。

如果自研数据模型只保留一个 `architecture_element` 加一个 `parent` 字段，后续就必须回答：这个元素表示类型，还是某个组合结构中的部件角色？否则复用和接口继承会产生歧义。

### 4. 第三项关键设计：不要把所有关系都解释成同一种箭头

你当前的连接关系把起点和终点定义为架构元素，并通过 `conn_type` 区分类型。粘贴的 Markdown (1)

向建模工具演进时，建议至少区分：

| 关系类别 | 示例 | 建议校验重点 |
|---|---|---|
| 组成关系 | 系统包含控制器 | 所有权、层级及循环引用 |
| 依赖关系 | 服务 A 依赖服务 B | 合法元素类型、依赖方向 |
| 接口连接 | 传感器输出端口连接控制器输入端口 | 类型、方向、连接上下文 |
| 分配关系 | 功能分配给逻辑组件 | 分配对象类型、完整性 |
| 需求满足／验证关系 | 组件满足需求、测试验证需求 | 关系角色、对象类型、版本 |

**并不是所有连线都必须连接端口。**依赖可以连接元素，接口连接才需要进一步考虑端口；需求满足和验证又是另外的语义。

在轻量级工具中可以先定义企业自己的受限规则，但应标注为“企业架构关系模型”，不要未经映射验证就宣称完整遵循 SysML。

### 5. 第四项关键设计：历史基线不能被“刷新最新属性”破坏

规格书要求打开图纸时拉取 WI 最新标题，同时又希望复用 Polarion 的版本与基线能力。粘贴的 Markdown (1) 粘贴的 Markdown (1)

这需要增加明确的模式区分：

| 模式 | 建议行为 |
|---|---|
| **当前工作版本** | 可以刷新最新属性，但检查冲突并提示变更 |
| **历史版本／发布基线** | 按固定版本读取，不自动替换为最新名称、端口或关系 |
| **差异分析** | 将两个版本显式比较，而不是混合展示 |

否则可能出现：**图纸是上个月的版本，标题是今天的，连接关系又来自另一个修订。**虽然每条数据都“有历史”，整张图却不是一个一致的工程快照。

---

## 五、三条可行实施路线

### 路线 A：继续 draw.io＋Polarion，做轻量级架构管理

**适用目标：**架构表达、元素管理、需求追溯、评审、变更和发布。

建议先形成一个有明确边界的模型子集，例如：

```text
系统／子系统／组件
＋
端口／接口
＋
组成／依赖／连接
＋
需求与测试追溯
＋
受控图纸与基线
```

这里需要特别说明：**端口／接口是建议新增的后续范围，不是原规格书已经要求的 MVP。**

这条路线的优势是沿用当前产品方向，不必立即引入完整专业建模环境；代价是你们要自行维护语义规则和一致性机制。

我的建议是将其命名为：

> **Polarion 轻量级架构建模与追溯插件**

比“完整 SysML/UML 工具”更准确，也更容易控制研发范围。

### 路线 B：专业建模工具＋Polarion，采用分工集成

**适用目标：**需要正式建模、行为分析、复杂接口、模型复用和标准交换。

建议职责划分如下：

| 数据／能力 | 建议权威来源 |
|---|---|
| 需求正文、审批、测试、缺陷、变更 | Polarion |
| 结构、端口、接口、行为、约束 | 专业模型仓库 |
| 视图布局 | 建模工具 |
| 模型与需求之间的对应关系 | 集成层维护的稳定引用 |
| 发布基线 | 同时记录需求版本和模型版本的发布清单 |

市场上已有可参考的集成实现：Siemens 产品页面列出了 **HCLTech Cameo Connector for Polarion**；Obeo 的 **Publication for Capella** 则提供面向 Polarion 等 ALM 工具的 OSLC 集成。它们说明不必从零解决所有集成问题，但仍需验证具体版本、同步范围和权限行为。[Siemens](https://www.siemens.com/en-us/products/hcltech-sysml-connector-for-polarion/?utm_source=chatgpt.com)

**不建议默认把模型中的每个细粒度对象都复制成一个 Work Item。**

更合理的映射可以是：

- 需要独立审批、责任人或变更管理的架构对象，映射为 WI；
- 状态、转换、端口、参数等细粒度元素，视管理需求选择映射或仅维护外部引用；
- 图元布局、折线拐点等显示数据，不映射为工程 WI。

这是一项管理粒度决策，而不是越细越好。

### 路线 C：SysON／其他模型内核＋Polarion，建设 Web 建模平台

**适用目标：**希望最终形成可二次开发、浏览器访问、面向 SysML v2 的平台。

这条路线与 A 的本质区别是：

> draw.io 方案以图纸和 WI 绑定为中心；模型内核方案以语义模型为中心，图纸是模型的视图。

建议的职责结构是：

```text
Polarion：需求、测试、流程、变更
模型服务：元素、类型、关系、语义校验
Web 编辑器：模型编辑与视图表达
集成服务：身份映射、版本关联、同步与错误恢复
```

SysON 的项目定位与这条路线较吻合，但它不能被当成可直接替换 draw.io 的“另一个 iframe”。需要重新设计模型所有权、保存事务、权限和配置管理。其官方资料明确把部分模型管理能力留给外部基础设施。[projects.eclipse.org](https://projects.eclipse.org/projects/modeling.syson?utm_source=chatgpt.com)

**对你的长期自研方向，我建议优先对这条路线做技术验证，而不是自己从零编写完整 SysML 元模型和语义引擎。**

---

## 六、对当前项目的具体建议

### 1. 不要立即推翻已经确定的 MVP

当前需求首先是“在 Polarion 中画架构、绑定已有工程对象并受控保存”。因此我建议：

**近期继续路线 A，确保绑定、保存、权限、锁、版本和错误恢复可靠；同时把模型身份与图元身份分开，为后续路线 B 或 C 保留接口。**

不要在同一期加入完整状态机、参数求解、代码生成、SysML v2 全语言支持。这些能力会改变项目性质，而不只是增加页面功能。

### 2. 在继续让 Claude Code 开发前，先解决一个规格冲突

原规格书的 MVP 范围写了“只绑定已有 WI、不自动创建 Work Item”；但 US-3 又要求新连线保存后自动创建 `architecture_connection`。粘贴的 Markdown (1) 粘贴的 Markdown (1)

这必须成为明确的产品决策：

**一种选择是节点和连线都只绑定已有 WI；另一种选择是节点只绑定已有 WI，但允许自动创建连接 WI。**

两种都可以实现，但不能让 Claude Code 自行猜测，也不能在升级为 SysML/UML 工具时把这种不一致继续带入模型服务。

### 3. 工具短名单按目标选择，不做统一排名

| 你的首要目标 | 建议优先验证 |
|---|---|
| 快速交付 Polarion 内嵌架构管理 | 现有 draw.io 插件 |
| 采用专业 SysML 建模并与 Polarion 集成 | Cameo／CATIA Magic、Enterprise Architect |
| 架构进一步连接嵌入式行为和代码生成 | Rhapsody |
| 强调系统分析到逻辑、物理架构的方法闭环 | Capella／Arcadia |
| 自研 Web 化 SysML v2 平台 | SysON；同时评估企业配置管理补充方案 |
| 已有大量 MATLAB／Simulink 资产 | System Composer，与前述工具按需要协同 |

这是基于前述官方能力的应用建议，不代表这些工具在所有场景下可以直接互换。

### 4. 用同一个小模型做 PoC，比看功能宣传更有价值

建议用一个通用的“温度采集与告警系统”验证以下场景：

| 验证场景 | 通过标准 |
|---|---|
| 同一个组件出现在三张图中 | 仍然是一个模型对象，修改可一致反映 |
| 一个传感器类型被使用两次 | 类型与使用角色能够区分，不靠复制图形冒充复用 |
| 错误接口连接 | 能识别并解释类型或方向问题，而不只是允许画线 |
| 删除某张图中的组件 | 不会意外删除其他图或模型中的对象 |
| 打开历史基线 | 不会混入最新元素属性和关系 |
| 修改接口后分析影响 | 能定位相关组件、需求和验证对象 |
| 同步中途失败后重试 | 不重复创建对象，不把部分完成误报为成功 |
| 模型导出再导入 | 能报告保留、丢失和转换的内容，不只验证文件能打开 |

总成本也应按 **许可证／协作组件＋集成开发＋培训＋迁移＋运维**评估，而不是仅比较单个建模客户端的价格。

---

## 最终判断

**对于你现在的规格书，draw.io＋Polarion 是合理的轻量级架构管理方案，但“增加 SysML/UML 图形库”不应被当成升级为专业建模工具的主要路径。**

真正决定后续能力的是四件事：

**模型元素与图元分离、明确的类型和关系语义、跨视图一致性、可重现的版本基线。**

需要专业建模能力时，优先考虑与成熟工具集成；需要建设长期 Web 平台时，优先评估可复用的模型内核。**不要让绘图引擎承担它并未提供的语义职责，也不要让 Work Item 管理机制未经设计就兼任完整模型仓库。**

下一步最值得做的是用上述同一个小模型，对“现有插件、一个成熟建模工具、SysON”进行对照验证，再据结果冻结下一版 `CLAUDE.md` 的能力边界和数据模型。