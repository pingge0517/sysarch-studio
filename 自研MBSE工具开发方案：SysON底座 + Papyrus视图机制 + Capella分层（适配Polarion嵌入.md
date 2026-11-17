# 自研MBSE工具开发方案：SysON底座 + Papyrus视图机制 + Capella分层（适配Polarion嵌入）
> 定位说明：
> - **SysON底座**：Web运行基座、SysML v2/KerML元模型内核、Sirius Web底层（SpringBoot+React+GraphQL+PostgreSQL）、REST接口、模型持久化、Polarion嵌入能力；
> - **Papyrus视图机制**：采用**VSM（Viewpoint Specification Model）声明式视图规范**，实现【模型为唯一真相源、视图是投影、一模型多视图自动同步】，解耦图形定义和元模型，新增图无需改核心代码；
> - **Capella分层**：借鉴Arcadia五层方法论思想，但不强制锁死Arcadia DSL，在SysML v2之上用**Stereotype（构造型）扩展**实现分层建模（Operational/System/Logical/Physical/EPBS），同时保留标准SysML语义，兼容BDD/IBD等标准视图；
> - 业务目标：内嵌Polarion，架构WorkItem、接口WorkItem双向同步；几十人项目并发、模型基线、追溯矩阵；对标Cameo/Papyrus，避开从零自研元模型、画布引擎的巨大工作量。
> - 重要前提：本方案基于 **Sirius Web**（三者共同底层），不是三套代码堆砌；Obeo官方已有 `Capella for SysON` 扩展原型，证明这条技术路线原生可行，不是空想路线。

## 一、整体架构总览（四层）
```
┌─────────────────────────────────────────────────────┐
│ 【接入层】 Polarion 嵌入、单点登录、权限代理、REST/OSLC
│  - iframe嵌入、模型元素 ↔ Polarion WorkItem双向同步
│  - 架构WI、接口WI、需求WI映射
└───────────────────────────┬─────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────┐
│ 【视图层】 Papyrus式VSM声明式视图（核心借鉴点）
│  VSM定义：BDD、IBD、功能链图、接口图、需求追溯图、Arcadia分层专用视图
│  规则：视图只定义“怎么展示模型元素”，不存储模型；改模型，所有视图自动刷新
│  图形校验、连线规则、端口类型匹配、样式规范全部在VSM配置
└───────────────────────────┬─────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────┐
│ 【模型语义层】SysON SysML v2元模型 + Capella Arcadia构造型扩展
│  基础：SysML v2 Block、Port、Connector、ItemFlow、Requirement
│  扩展构造型：OperationalActivity、SystemFunction、LogicalComponent、PhysicalComponent等（Arcadia分层标记）
│  校验规则：OCL子集、端口兼容校验、追溯完整性校验、分层分配一致性校验
└───────────────────────────┬─────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────┐
│ 【底座层】SysON + Sirius Web Runtime
│  EMF/Ecore模型仓库、版本基线、模型Diff、模型存储、GraphQL服务、前端画布组件、模型导入导出（SysML v2文本）
└─────────────────────────────────────────────────────┘
```

### 三者在架构里各自承担的角色，不混淆
| 参考源 | 拿来什么 | 不拿来什么 |
|---|---|---|
| **SysON** | 整套Sirius Web工程骨架、SysML v2元模型、Web前端、持久化、REST API、部署方案 | 不沿用它默认“非同步General View”，替换成Papyrus风格自动同步视图 |
| **Papyrus** | VSM声明式视图范式、视图-模型分离、DSML扩展思路、多视图联动、图形语义规则 | 不引入Eclipse RCP桌面遗产、GMF旧notation格式、庞大UML1.x遗留代码 |
| **Capella（Arcadia）** | 五层分层方法论、功能-组件分配、功能链、逐层抽象、自顶向下验证思路 | 不直接使用Capella专属Ecore元模型，不用强制Arcadia专属DSL；全部用SysML v2构造型扩展实现分层 |

> 关键优势：模型文件是标准SysML v2文本（KerML），可以和原生SysON互通；分层只是**标记构造型**，不是私有模型，未来可以导出给Cameo、EA，不会锁死。

## 二、元模型设计：SysML v2 + Arcadia构造型扩展（Capella分层落地）
Arcadia原生五层：OA 运行分析 → SA 系统分析 → LA 逻辑架构 → PA 物理架构 → EPBS 最终产品分解
我们在SysML v2基础上，新增一组轻量构造型（Stereotype），不修改原生SysML元模型：
1. **OA（Operational Analysis 运行层）**
   - 构造型：`«OperationalEntity»`、`«OperationalActivity»`、`«OperationalExchange»`
   - 用途：用户、任务、作战/运行场景，不涉及系统内部实现
2. **SA（System Analysis 系统分析层）**
   - 构造型：`«SystemFunction»`、`«SystemInterface»`
   - 用途：系统黑盒，定义系统需要实现的功能、外部接口，对接Polarion需求WI
3. **LA（Logical Architecture 逻辑架构层）**
   - 构造型：`«LogicalComponent»`、`«LogicalPort»`、`«LogicalConnector»`
   - 用途：无工艺、无硬件约束的逻辑组件，功能分配到逻辑块（对标Capella LA）
4. **PA（Physical Architecture 物理架构层）**
   - 构造型：`«PhysicalComponent»`、`«PhysicalPort»`
   - 用途：硬件、软件、Die、基板、控制器，和你Xpedition IC Packaging线束/封装链路打通
5. **EPBS（End Product Breakdown）**
   - 构造型：`«EndProductElement»`
   - 用途：BOM、交付物分解，输出到PLM

**分配关系（核心约束，OCL校验）**
- OperationalActivity → allocate → SystemFunction
- SystemFunction → allocate → LogicalComponent
- LogicalComponent → allocate → PhysicalComponent
- PhysicalComponent → allocate → EPBS元素
> 校验规则：不允许跨层直接分配；删除上层元素，下层分配标记异常（对标你之前需求：连线WI标红告警）

## 三、视图体系：Papyrus VSM视图机制落地（重点改造SysON原生短板）
SysON原生视图短板：General View是**非同步视图**，元素需要手动拖入画布，模型更新不会自动在画布体现。
Papyrus的核心思想：**视图是声明式过滤，不是用户手绘图形集合**，VSM定义一套视图规则：
> 给定一个模型元素，视图自动展示它内部所有子元素、端口、连接器；模型修改，视图自动同步。

### 规划VSM视图清单（分两类：标准SysML视图 + Arcadia分层视图）
1. 标准SysML视图（对标Cameo/Papyrus）
   - BDD（Block Definition Diagram）：块定义、继承、组合
   - IBD（Internal Block Diagram）：内部部件、端口、连接器、ItemFlow（重点，SiP/基板架构使用）
   - Requirement Diagram：需求、Satisfy、Verify追溯关系，双向绑定Polarion需求WI
2. Arcadia分层视图（借鉴Capella）
   - Operational Architecture Diagram（OA）
   - System Analysis Diagram（SA，功能链图）
   - Logical Architecture Diagram（LA）
   - Physical Architecture Diagram（PA）
   - Allocation Table（分配矩阵，自动生成：功能→逻辑→物理）

### VSM开发规范（Papyrus范式）
- 一个VSM = Viewpoint（视图集），包含多个Representation（图定义）
- 在Sirius Web Studio中定义：节点样式、连线样式、工具、自动展开规则、过滤条件
- 视图不存储坐标以外的业务信息；坐标单独存布局信息，模型本体独立
- 新增视图，只新增VSM配置文件，不需要修改Java/React核心代码，实现Papyrus式可扩展

> 对比：原生SysON是代码硬编码视图；本方案改为VSM声明式，后续新增参数图、时序图只需要增加VSM，架构不用重构。

## 四、与Polarion深度集成（承接你最初业务目标）
### 同步对象
1. **架构WorkItem ↔ SysML Block（Component）**
2. **接口WorkItem ↔ SysML Connector / Port**
3. **需求WorkItem ↔ SysML Requirement**
### 同步能力
- 新建Block → 自动在Polarion创建架构WI，携带模型ID；WI删除 → 模型内元素标记失效、视图标红告警
- 在画布修改接口两端Port → 自动更新Polarion接口WI的源/目标字段
- 导出追溯矩阵（需求→功能→逻辑组件→物理组件）直接生成Polarion报表
- 鉴权：复用Polarion账号，权限由Polarion控制，工具本身不做独立权限
- 部署：容器化，和Polarion同K8s集群，iframe嵌入，用户无感知，就是Polarion里面的架构标签页

## 五、项目阶段划分、交付物、人力与周期（5~6人团队）
> 团队组成：Java后端（Sirius Web）2人、前端React 1人、MBSE建模专家（VSM+Arcadia）1人、测试+集成（Polarion）1人、架构负责人1人

### P0：验证阶段（6周，可行性验证，验收门槛，不通过止损）
目标：验证三件核心技术可行性
1. Fork SysON，跑通基础Sirius Web扩展
2. 完成第一个VSM：自动同步IBD视图（Papyrus视图机制验证，解决SysON非同步痛点）
3. 实现单方向同步：Block ↔ Polarion架构WI，接口连线标红告警
交付物：
- 可运行Demo：IBD画布、模型树、属性面板、Polarion单向同步
- 元模型Ecore定义（SysML v2 + Arcadia构造型）
- 第一版VSM视图定义文件
**止损标准：自动同步视图延迟>2s、模型Diff冲突无法处理，停止自研，转向采购EA或商用方案**

### P1：最小可用版本（12周，面向项目试用）
目标：完整分层视图、双向同步、基础校验
1. VSM补齐：BDD、SA/LA/PA分层架构图、分配矩阵表
2. 完善OCL校验：端口类型匹配、跨层分配约束、孤立元素告警
3. Polarion双向同步：架构WI、接口WI、需求WI
4. 基础基线、模型diff、模型导出为SysML v2文本
交付物：内部试用版，支持小项目（10人以内）做系统架构、接口建模

### P2：正式版本（12周，全功能对标EA，满足几十人并发）
1. 完善协同：项目锁、元素级编辑锁、版本分支（对标Cameo Teamwork Cloud简化版，不做复杂CRDT协同）
2. 文档生成模板：从模型导出规格书、接口清单、追溯报告（Acceleo模板，Papyrus方案）
3. 导入导出：支持导入SysML v2、兼容SysON模型
4. 性能优化：支持上千模型元素的大模型
交付物：正式上线版本，在DAF这类ASPICE项目试点使用

### P3：扩展迭代（长期，可选，不纳入首期预算）
- 参数图基础建模（不做求解，求解对接外部OpenModelica/Python）
- 状态机、活动图VSM视图
- 导出XMI，和Cameo、Papyrus互导
- 仿真桥接（fUML，非首期重点）

> 首期P0+P1合计18周，约22~24人月；首期不碰仿真、复杂OCL、实时协同，控制风险。

## 六、风险清单 & 规避方案
| 风险 | 影响 | 规避方案 |
|---|---|---|
| Sirius Web、SysON社区迭代快，API变更 | 版本升级导致定制代码失效 | 锁定SysON/Sirius Web版本，内部fork，不直接跟随上游滚动升级；分层隔离扩展代码和原生代码 |
| VSM声明式视图复杂，Arcadia分层视图配置工作量大 | 视图样式、自动布局效果差 | 参考Papyrus、Capella for SysON官方VSM样例，优先做结构视图，行为视图后置 |
| Polarion同步量大，并发更新冲突 | WI和模型数据不一致 | 引入版本ETag、乐观锁；变更队列，禁止双向同时写同一个元素；变更日志留痕 |
| 工程师习惯Cameo，学习成本高 | 落地阻力 | 视图符号、术语对齐Cameo；保留导出SysML v2，允许Cameo作为备选评审工具 |
| 大模型性能（上万元素） | 画布卡顿 | 视图懒加载、按分层过滤，默认只打开当前层视图 |

## 七、和采购方案对比（管理层汇报口径）
1. **对比Cameo**：
   ✅ 优势：原生Web、深度嵌入Polarion、自主可控、可以按业务定制Arcadia分层流程、无高额年费、信创适配空间大
   ❌ 劣势：**无内置参数求解、仿真能力弱、报告模板需要自建**，复杂权衡仿真场景仍需要Cameo
2. **对比Papyrus**：
   ✅ 优势：Web原生、内嵌ALM、模型是标准SysML v2，不是桌面EMF文件；
   ❌ 劣势：生态成熟度、现成插件少于Papyrus
3. **对比Capella**：
   ✅ 优势：不锁死Arcadia，可以自由切换标准SysML建模；原生对接Polarion；Web化
   ❌ 劣势：没有Capella几十年工业沉淀的全套校验、报告模板库

> 一句话定位：**这个自研工具不是替换Cameo做仿真权衡，而是面向Polarion为中心的系统架构、接口定义、需求追溯链路，提供一套Web原生、分层化、模型驱动的MBSE底座，填补当前draw.io画图工具的语义短板**

## 八、技术栈清单
- 后端：Spring Boot、Sirius Web Runtime、EMF/Ecore、Eclipse OCL、GraphQL、PostgreSQL
- 前端：React、TypeScript、Sirius Web画布组件
- 建模定义：VSM（Viewpoint Specification Model）、SysML v2 / KerML
- 集成：Polarion REST API、OSLC桥接、Docker容器、K8s部署
- 模型交换：SysML v2文本，可选扩展XMI导入导出

如果你需要，我下一步输出：
1. P0阶段 Claude Code 可直接执行的规格文档（含VSM样例、Ecore元模型定义、Polarion同步接口定义）；
2. 或者整理一份PPT精简版，直接给管理层评审。