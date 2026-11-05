# P0 规格书：SysON 底座 + Papyrus 视图机制 + Capella 分层（Claude Code 可执行版）

> 版本 V1.0 ｜ 2026-11-05 ｜ 适用：Claude Code / 工程师直接执行
>
> **给执行者的读取顺序**：先读第 0 章（怎么用这份文档）→ 第 1 章（边界与不做清单）→ 第 2 章（仓库结构）→ 第 3、4、6 章（三个可执行资产：Ecore、VSM、OpenAPI）→ 第 8 章（任务分解，逐条执行）→ 第 9 章（验收）。
>
> 本文档中所有代码块均为**可直接落盘**的完整文件内容，不是示意。凡标注 `[GENERATE]` 的条目由 Claude Code 生成，标注 `[COPY]` 的条目按原文落盘。

---

## 0. 怎么用这份文档

本规格书面向一个具体动作：**在 6 周内做出一个能跑通的 P0 Demo，证明三件事**——

1. Sirius Web 上能承载我们自己的元模型（不是 SysON 原生 SysML v2 全量）；
2. 视图是"声明式配置生成"的（VSM 机制），不是手写 Java 画布代码；
3. 模型元素与 Polarion Work Item 能双向对上，并且能查出一致性差异。

执行方式：把第 8 章的 `T1` 到 `T9` 按顺序逐条交给 Claude Code，每条任务的开头都有一个**可直接粘贴的指令块**。任务之间有硬依赖，不要跳序执行（T3 依赖 T2，T4 依赖 T3）。

**三个判断标准贯穿全文**：

- 任何"看起来能跑但改一个字段就要改核心代码"的实现，判为不通过；
- 任何绕过 Polarion 权限模型自建账号体系的实现，判为不通过；
- 任何把 SysON 上游代码整体改写而不是分层叠加的实现，判为不通过。

---

## 1. 定位、边界与不做清单

### 1.1 一句话定位

在 Sirius Web 之上，做一个**Web 原生、分层化、与 Polarion 双向绑定的系统架构建模工具**，服务对象是单项目几十人的架构与接口定义场景。

### 1.2 P0 必须证明的能力（六条，缺一即失败）

| 编号 | 能力 | 判定方式 |
|---|---|---|
| AC-1 | 环境一键起：Spring Boot + PostgreSQL + React 跑通 | `docker compose up` 后访问首页 200 |
| AC-2 | 自有元模型可加载 | 能创建 PartDefinition / Part / Port / Connection / ItemFlow 五类实例 |
| AC-3 | 两张视图由 VSM 生成 | BDD 与 IBD 各一个 DiagramDescription，且改 YAML 不改 Java 即可换样式 |
| AC-4 | 视图自动同步（Papyrus 语义） | 在模型树新增一个 Part，已打开的 IBD 画布 2 秒内出现该节点 |
| AC-5 | 端口类型校验拦截 | 连接两个接口类型不同的 Port，保存被拒并给出定位 |
| AC-6 | Polarion 双向绑定 + 一致性报告 | 建/删 Work Item 后，一致性报告能列出孤儿绑定与悬空连线 |

### 1.3 明确不做（P0 红线）

- **不做**参数图求解、行为仿真、fUML。求解在 P3 之后外接 OpenModelica，P0 不碰。
- **不做**实时协同（CRDT / OT）。并发控制用编辑锁。
- **不做**自研元模型引擎、自研画布渲染。只允许在 Sirius Web 上做增量。
- **不做** Polarion 账号体系复制。鉴权一律透传 Polarion 登录态。
- **不做** UML 全图族。P0 只做 BDD、IBD 两张，加一张分配矩阵表（表格型）。
- **不做** XMI 导入导出、Report 模板、SysML v2 文本导出。P0 不出这些。

### 1.4 分层与构造型的落地约定（Capella 借鉴点的边界）

借用 Arcadia 的**分层思想**，但**不复用 Capella 的 Ecore 元模型**，也不引入 Arcadia 专属 DSL。做法是：在自有元模型上挂一个 `layer` 枚举，并用 `stereotypes` 多值属性表达角色。

| 层 | layer 枚举 | 推荐构造型 | 典型元素 |
|---|---|---|---|
| 运行分析 | `OPERATIONAL` | `«OperationalEntity»` `«OperationalActivity»` | 用户角色、任务场景 |
| 系统分析 | `SYSTEM` | `«SystemFunction»` `«SystemInterface»` | 系统黑盒功能、外部接口 |
| 逻辑架构 | `LOGICAL` | `«LogicalComponent»` `«LogicalPort»` | 无硬件约束的逻辑部件 |
| 物理架构 | `PHYSICAL` | `«PhysicalComponent»` `«PhysicalPort»` | 控制器、基板、封装、总线 |
| 产品分解 | `EPBS` | `«EndProductElement»` | BOM、交付物 |

约束：`Allocate` 关系只允许跨一层（`OPERATIONAL→SYSTEM→LOGICAL→PHYSICAL→EPBS`），跨两层及以上直接校验失败。这条约束是 Capella 分层能"防跑偏"的核心，P0 必须实现。

---

## 2. 技术栈与仓库结构

### 2.1 技术栈（锁定版本，P0 期间不升级）

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | Sirius Web 要求 17+，21 为 LTS |
| Spring Boot | 3.3.x | 与 Sirius Web 官方 starter 对齐 |
| Sirius Web | 2025.8.x（锁死） | fork 后不跟随上游滚动升级 |
| PostgreSQL | 16 | 模型与布局持久化 |
| React + TypeScript | 18 / 5.x | 前端壳，画布用 Sirius Web 组件 |
| GraphQL | Spring GraphQL | Sirius Web 原生协议 |
| EMF / Ecore | 2.35 | 元模型定义与代码生成 |
| Eclipse OCL | 6.x | 约束校验（Pivot） |
| Polarion | REST API v2 | 通过自建代理访问 |

### 2.2 仓库结构（monorepo，T1 落盘）

```text
sysarch-studio/
├── docker-compose.yml                    # AC-1
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── metamodel/
│   ├── sysarch.ecore                     # [COPY] 第 3.1 节全文
│   ├── sysarch.genmodel                  # [GENERATE] 由 Ecore 生成
│   └── src-gen/                          # [GENERATE] EMF 生成代码
├── views/
│   ├── sysarch-core.vsm.yaml             # [COPY] 第 4.3 节（BDD）
│   ├── sysarch-ibd.vsm.yaml              # [COPY] 第 4.4 节（IBD）
│   └── schema/vsm.schema.json            # [GENERATE] VSM DSL 的 JSON Schema
├── vsm-compiler/                         # [GENERATE] VSM → Java 的生成器
│   ├── src/main/java/com/example/vsm/
│   └── src/main/resources/templates/*.ftl
├── backend/                              # Sirius Web 应用 + Polarion 代理
│   ├── src/main/java/com/example/sysarch/
│   │   ├── SysarchApplication.java
│   │   ├── model/                        # 引用 metamodel/src-gen
│   │   ├── view/                         # [GENERATE] BDD/IBD DiagramDescription
│   │   ├── validation/                   # OCL 与端口类型校验
│   │   ├── link/                         # 元素 ↔ Work Item 绑定
│   │   └── sync/                         # 推送/拉取/一致性
│   └── src/main/resources/
│       ├── application.yml
│       └── api/polarion-proxy.openapi.yaml   # [COPY] 第 6.1 节
├── frontend/                             # React 壳 + 嵌入页
├── tests/
│   ├── acceptance/                       # 第 9 章验收脚本
│   └── fixtures/
└── docs/
    └── metamodel-and-views.md            # [GENERATE] 10 页说明
```

### 2.3 依赖边界（架构红线）

```text
frontend  →  backend(GraphQL)  →  Sirius Web 内核（只读依赖，不修改源码）
                                  ├── sysarch 元模型（我们的）
                                  ├── VSM 生成的视图（我们的）
                                  └── link/sync（我们的）
backend   →  polarion-proxy  →  Polarion REST v2
```

**允许**：新增 Java 包、注册新的 `DiagramDescription`、扩展 `IEditingContext`、加拦截器。
**禁止**：改 Sirius Web 源码、替换其图渲染器、绕过其 `EditingContext` 直接写数据库。

---

## 3. 领域元模型定义（Ecore）

### 3.1 `metamodel/sysarch.ecore` —— `[COPY]` 原样落盘

设计取舍：**不复制 SysML v2 全量元模型**，只保留架构场景够用的结构子集（块、部件、端口、连接、项流、需求、分配），分层用 `layer` 枚举而非独立包。这样做的直接好处是：EMF 生成的 Java 类不超过 15 个，P0 六周内可控；代价是导出到 Cameo 时需要一层映射，这在 P2 处理。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ecore:EPackage xmi:version="2.0"
    xmlns:xmi="http://www.omg.org/XMI"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
    name="sysarch" nsURI="http://example.com/sysarch/1.0" nsPrefix="sysarch">

  <eAnnotations source="http://www.eclipse.org/emf/2002/GenModel">
    <details key="documentation" value="系统架构建模领域元模型：SysML v2 结构子集 + Arcadia 分层标记"/>
  </eAnnotations>

  <eClassifiers xsi:type="ecore:EEnum" name="Layer">
    <eLiterals name="OPERATIONAL" value="0" literal="OPERATIONAL"/>
    <eLiterals name="SYSTEM" value="1" literal="SYSTEM"/>
    <eLiterals name="LOGICAL" value="2" literal="LOGICAL"/>
    <eLiterals name="PHYSICAL" value="3" literal="PHYSICAL"/>
    <eLiterals name="EPBS" value="4" literal="EPBS"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EEnum" name="PortDirection">
    <eLiterals name="IN" value="0" literal="IN"/>
    <eLiterals name="OUT" value="1" literal="OUT"/>
    <eLiterals name="INOUT" value="2" literal="INOUT"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EEnum" name="ConnectionKind">
    <eLiterals name="ASSEMBLY" value="0" literal="ASSEMBLY"/>
    <eLiterals name="DELEGATION" value="1" literal="DELEGATION"/>
    <eLiterals name="ITEM_FLOW" value="2" literal="ITEM_FLOW"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EEnum" name="VerifyMethod">
    <eLiterals name="ANALYSIS" value="0" literal="ANALYSIS"/>
    <eLiterals name="INSPECTION" value="1" literal="INSPECTION"/>
    <eLiterals name="TEST" value="2" literal="TEST"/>
    <eLiterals name="DEMONSTRATION" value="3" literal="DEMONSTRATION"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="ArchElement" abstract="true">
    <eAnnotations source="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot">
      <details key="NameNotEmpty" value="not name.oclIsUndefined() and name.size() &gt; 0"/>
    </eAnnotations>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="id" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString" iD="true"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="name" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="description" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="layer" eType="#//Layer" defaultValueLiteral="LOGICAL"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="stereotypes" upperBound="-1" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="workItemId" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="rev" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EInt"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="PartDefinition" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EReference" name="ownedParts" upperBound="-1"
        eType="#//Part" containment="true" eOpposite="#//Part/owner"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="ownedPorts" upperBound="-1"
        eType="#//Port" containment="true" eOpposite="#//Port/owner"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="superTypes" upperBound="-1" eType="#//PartDefinition"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="isAbstract" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EBoolean" defaultValueLiteral="false"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="Part" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EReference" name="owner" eType="#//PartDefinition" eOpposite="#//PartDefinition/ownedParts"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="typedBy" lowerBound="1" eType="#//PartDefinition"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="ports" upperBound="-1" eType="#//Port" containment="true" eOpposite="#//Port/partOwner"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="multiplicity" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString" defaultValueLiteral="1"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="Port" eSuperTypes="#//ArchElement">
    <eAnnotations source="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot">
      <details key="InterfaceTypeRequired" value="direction = PortDirection::INOUT or (not interfaceType.oclIsUndefined() and interfaceType.size() &gt; 0)"/>
    </eAnnotations>
    <eStructuralFeatures xsi:type="ecore:EReference" name="owner" eType="#//PartDefinition" eOpposite="#//PartDefinition/ownedPorts"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="partOwner" eType="#//Part" eOpposite="#//Part/ports"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="direction" eType="#//PortDirection" defaultValueLiteral="INOUT"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="interfaceType" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="isConjugated" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EBoolean" defaultValueLiteral="false"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="Connection" eSuperTypes="#//ArchElement">
    <eAnnotations source="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot">
      <details key="PortTypeCompatible" value="kind = ConnectionKind::ASSEMBLY implies source.interfaceType = target.interfaceType"/>
      <details key="NoSelfLoop" value="source &lt;&gt; target"/>
      <details key="DirectionCompatible" value="kind = ConnectionKind::ASSEMBLY implies (source.direction &lt;&gt; target.direction or source.direction = PortDirection::INOUT or target.direction = PortDirection::INOUT)"/>
    </eAnnotations>
    <eStructuralFeatures xsi:type="ecore:EReference" name="source" lowerBound="1" eType="#//Port"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="target" lowerBound="1" eType="#//Port"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="kind" eType="#//ConnectionKind" defaultValueLiteral="ASSEMBLY"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="interfaceType" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="ItemFlow" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EReference" name="connection" eType="#//Connection"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="itemName" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="itemType" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="Requirement" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="reqId" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="text" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="verifyMethod" eType="#//VerifyMethod" defaultValueLiteral="TEST"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="priority" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EInt" defaultValueLiteral="3"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="Allocate" eSuperTypes="#//ArchElement">
    <eAnnotations source="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot">
      <details key="LayerAdjacentOnly" value="(from.layer = Layer::OPERATIONAL and to.layer = Layer::SYSTEM) or (from.layer = Layer::SYSTEM and to.layer = Layer::LOGICAL) or (from.layer = Layer::LOGICAL and to.layer = Layer::PHYSICAL) or (from.layer = Layer::PHYSICAL and to.layer = Layer::EPBS)"/>
      <details key="NoSelfAllocate" value="from &lt;&gt; to"/>
    </eAnnotations>
    <eStructuralFeatures xsi:type="ecore:EReference" name="from" lowerBound="1" eType="#//ArchElement"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="to" lowerBound="1" eType="#//ArchElement"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="rationale" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="DiagramLayout" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="diagramKey" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="elementRef" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="x" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EInt" defaultValueLiteral="0"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="y" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EInt" defaultValueLiteral="0"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="width" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EInt" defaultValueLiteral="180"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="height" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EInt" defaultValueLiteral="90"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="waypoints" upperBound="-1" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
  </eClassifiers>

</ecore:EPackage>
```

### 3.2 约束说明（照这个口径实现，不要自己加码）

| 约束 | 归属类 | 触发时机 | 不通过的界面表现 |
|---|---|---|---|
| `NameNotEmpty` | `ArchElement` | 保存时 | 属性面板字段标红，禁止提交 |
| `InterfaceTypeRequired` | `Port` | 保存时 | 同上 |
| `PortTypeCompatible` | `Connection` | 连线落位时 | 连线不入库，弹出提示并高亮两端端口 |
| `NoSelfLoop` | `Connection` | 连线落位时 | 同上 |
| `DirectionCompatible` | `Connection` | 连线落位时 | 同上（同向 IN-IN 拒绝，INOUT 放行） |
| `LayerAdjacentOnly` | `Allocate` | 保存时 | 分配被拒，提示"跨层跳过：LA → EPBS" |

**注意**：`PortTypeCompatible` 用的是 `interfaceType` 字符串相等，不是类型继承推理。P0 不做继承链解析，这条限制要在文档里写死，避免后续误以为已支持接口兼容性推导。

### 3.3 生成流程（T2 执行）

```bash
# 1) 由 Ecore 生成 EMF Java 代码（包名 com.example.sysarch.model）
#    genmodel 中设置 Model Directory = backend/src/main/java，Base Package = com.example.sysarch
java -jar org.eclipse.emf.codegen.ecore.jar -model metamodel/sysarch.genmodel

# 2) 若目标 Sirius Web 版本不支持直接导入 Ecore，则转换为 Sirius Web 的 JSON 元模型描述
#    vsm-compiler 提供：compileEcoreToJson(ecorePath, outPath)
./gradlew :vsm-compiler:runEcoreConverter --args="metamodel/sysarch.ecore build/sysarch.metamodel.json"
```

生成物必须满足：`PartDefinition`、`Part`、`Port`、`Connection`、`ItemFlow`、`Requirement`、`Allocate` 七类可实例化，`ArchElement` 抽象不可实例化，四个枚举可取值。

---

## 4. 视图机制：VSM DSL 与样例

### 4.1 为什么不用 `.odesign`

Papyrus 的 VSM 是 Eclipse 的 `.odesign`（Sirius Desktop 专有格式），依赖 RCP 运行时，在 Web 端无法直接使用。本方案取其**语义**——"视图是声明式描述，不是手绘图形的集合"——落成一套 YAML DSL，由编译器生成 Sirius Web 的 `DiagramDescription`。

这样做的收益是三个可验证的事实：

1. 新增一张图 = 新增一个 YAML 文件 + 重跑生成，**不动 Java**；
2. 视图样式、过滤、自动展开规则全部可评审、可 diff；
3. 生成器是纯函数，同样的输入必得同样的输出（可进 CI 做快照测试）。

### 4.2 DSL 字段约定

| 字段 | 必填 | 语义 | 执行要求 |
|---|---|---|---|
| `viewpoint.id` | 是 | 视图集标识，全局唯一 | 与其他 YAML 冲突时构建失败 |
| `representation.kind` | 是 | `diagram` \| `table` | P0 只实现 diagram，table 产出占位 |
| `domainClass` | diagram 必填 | 图的根元素类型 | 必须是元模型中已存在的 EClass |
| `synchronization` | 是 | `auto` \| `manual` | **P0 全部为 auto**；manual 仅用于对照演示 |
| `autoExpand[].feature` | 否 | 自动展开的结构特征 | 必须是 domainClass 或其子类的 EReference |
| `nodeStyles[].appliesTo` | 是 | 节点映射的 EClass | 未知类 → 构建失败并输出 `file:line` |
| `edgeStyles[]` | 否 | 边映射 | 必须给出 `domainClass`、`sourceEnd`、`targetEnd` |
| `labelExpression` | 否 | 标签模板，`{feature}` 插值 | 未知 feature → 构建失败 |
| `tools[]` | 否 | 画布工具（新建、删除、绑定） | `containmentFeature` 必须是容器引用 |

编译期校验三条：**类不存在、特征不存在、引用端点不存在**，任一命中即失败，不允许降级为 warning。

### 4.3 `views/sysarch-core.vsm.yaml` —— BDD —— `[COPY]` 原样落盘

```yaml
vsm: "1.0"
viewpoint:
  id: "sysarch.core"
  label: "系统架构建模"
  metamodelUri: "http://example.com/sysarch/1.0"
  priority: 10

representations:
  - id: "bdd"
    label: "块定义图（BDD）"
    kind: "diagram"
    domainClass: "sysarch:PartDefinition"
    synchronization: "auto"
    refresh:
      mode: "incremental"
      budgetMs: 2000
    autoExpand:
      - feature: "ownedParts"
        maxDepth: 3
      - feature: "ownedPorts"
        maxDepth: 1
    filters:
      - id: "hideAbstract"
        label: "隐藏抽象块"
        expression: "not isAbstract"
    nodeStyles:
      - id: "block"
        appliesTo: "sysarch:PartDefinition"
        shape: "rectangle"
        color: "#FFFFFF"
        borderColor: "#1A2B4A"
        borderWidth: 1
        headerBold: true
        labelExpression: "{name}"
        subLabelExpression: "{layer}"
        compartments:
          - feature: "ownedPorts"
            label: "ports"
            itemExpression: "{name} : {interfaceType}"
      - id: "abstractBlock"
        appliesTo: "sysarch:PartDefinition"
        shape: "rectangle"
        color: "#F2F3F5"
        borderColor: "#758092"
        italic: true
        labelExpression: "{name}"
    edgeStyles:
      - id: "generalization"
        label: ""
        domainClass: "sysarch:PartDefinition"
        sourceEnd: "self"
        targetEnd: "superTypes"
        routing: "straight"
        arrow: "hollow-triangle"
        color: "#758092"
      - id: "composition"
        label: "{name}"
        domainClass: "sysarch:PartDefinition"
        sourceEnd: "self"
        targetEnd: "ownedParts"
        routing: "manhattan"
        arrow: "filled-diamond"
        color: "#1A2B4A"
    tools:
      - id: "createPart"
        label: "新建部件"
        action: "create-child"
        targetClass: "sysarch:Part"
        containmentFeature: "ownedParts"
      - id: "createPort"
        label: "新建端口"
        action: "create-child"
        targetClass: "sysarch:Port"
        containmentFeature: "ownedPorts"
      - id: "bindWorkItem"
        label: "绑定 Polarion 工作项"
        action: "external-link"
        linkType: "POLARION_WORKITEM"
```

### 4.4 `views/sysarch-ibd.vsm.yaml` —— IBD —— `[COPY]` 原样落盘

IBD 是这套机制的关键验证点：**Papyrus 的 IBD 是"模型驱动自动同步"的，而 SysON 原生 Interconnection View 是非同步的**。这里的 `synchronization: auto` 就是用来把前者搬过来的开关。

```yaml
vsm: "1.0"
viewpoint:
  id: "sysarch.core"
  label: "系统架构建模"
  metamodelUri: "http://example.com/sysarch/1.0"
  priority: 10

representations:
  - id: "ibd"
    label: "内部块图（IBD）"
    kind: "diagram"
    domainClass: "sysarch:PartDefinition"
    synchronization: "auto"
    refresh:
      mode: "incremental"
      budgetMs: 2000
    autoExpand:
      - feature: "ownedParts"
        maxDepth: 1
      - feature: "ownedPorts"
        maxDepth: 1
    nodeStyles:
      - id: "part"
        appliesTo: "sysarch:Part"
        shape: "rectangle"
        color: "#FFFFFF"
        borderColor: "#1A2B4A"
        labelExpression: "{name}"
        subLabelExpression: "{typedBy.name} [{multiplicity}]"
      - id: "portOnPart"
        appliesTo: "sysarch:Port"
        shape: "square"
        width: 10
        height: 10
        color: "#FFFFFF"
        borderColor: "#1A2B4A"
        isBorderNode: true
        affiliationFeature: "partOwner"
        labelPosition: "outside"
        labelExpression: "{name}"
      - id: "portOnDefinition"
        appliesTo: "sysarch:Port"
        shape: "square"
        width: 12
        height: 12
        color: "#FFFFFF"
        borderColor: "#1A2B4A"
        isBorderNode: true
        affiliationFeature: "owner"
        labelPosition: "outside"
        labelExpression: "{name}"
    edgeStyles:
      - id: "assembly"
        domainClass: "sysarch:Connection"
        sourceEnd: "source"
        targetEnd: "target"
        routing: "manhattan"
        color: "#1A2B4A"
        labelExpression: "{interfaceType}"
      - id: "delegation"
        domainClass: "sysarch:Connection"
        sourceEnd: "source"
        targetEnd: "target"
        routing: "straight"
        lineStyle: "dashed"
        color: "#758092"
        labelExpression: ""
      - id: "itemFlow"
        domainClass: "sysarch:ItemFlow"
        sourceEnd: "connection"
        targetEnd: "connection"
        routing: "straight"
        color: "#BABFC9"
        labelExpression: "{itemName} : {itemType}"
    tools:
      - id: "createPart"
        label: "新建部件"
        action: "create-child"
        targetClass: "sysarch:Part"
        containmentFeature: "ownedParts"
      - id: "connect"
        label: "建立连接"
        action: "create-edge"
        targetClass: "sysarch:Connection"
        validateOnDrop: true
```

> 两处容易踩错：`isBorderNode: true` 依赖 `affiliationFeature` 指到父部件，指错会导致端口浮在画布中央；`connect` 工具的 `validateOnDrop: true` 必须开，否则 AC-5 无法在落位阶段拦截。

### 4.5 编译器契约（T3 执行）

输入：`views/*.vsm.yaml`；输出：`backend/src/main/java/com/example/sysarch/view/generated/` 下的 `<RepId>DiagramDescriptionProvider.java` 与 `ViewpointRegistry.java`。

生成逻辑固定为四步：解析 YAML → 与元模型做交叉校验 → 按模板生成 Java → 写注册表。要求：

- **确定性**：同一输入两次生成的字节级一致（CI 中比对 hash）；
- **可失败**：任一校验错误输出 `views/xxx.vsm.yaml:37 unknown feature 'ownedPort' on sysarch:PartDefinition` 并退出码 1；
- **不热加载**：运行时只读生成产物，禁止在运行时解析 YAML（避免启动变慢与线上不一致）。

生成代码样例（`bdd` 的产物，节选，用来判断生成器该长什么样）：

```java
@Component
public class BddDiagramDescriptionProvider implements IDiagramDescriptionProvider {

    @Override
    public DiagramDescription build() {
        return DiagramDescription.newDiagramDescription("bdd")
            .label("块定义图（BDD）")
            .domainType("sysarch::PartDefinition")
            .synchronizationKind(SynchronizationKind.AUTO)
            .refreshPolicy(RefreshPolicy.incremental(2000))
            .autoExpand("ownedParts", 3)
            .autoExpand("ownedPorts", 1)
            .addNode(NodeStyle.builder("block")
                .appliesTo("sysarch::PartDefinition")
                .labelTemplate("{name}")
                .subLabelTemplate("{layer}")
                .borderColor("#1A2B4A")
                .compartment("ownedPorts", "ports", "{name} : {interfaceType}")
                .build())
            .addEdge(EdgeStyle.builder("composition")
                .domainType("sysarch::PartDefinition")
                .sourceEnd("self").targetEnd("ownedParts")
                .routing(Routing.MANHATTAN).arrow(Arrow.FILLED_DIAMOND)
                .labelTemplate("{name}").build())
            .addTool(Tool.createChild("新建部件", "sysarch::Part", "ownedParts"))
            .build();
    }
}
```

---

## 5. 视图同步语义：把 Papyrus 的"投影"搬到 Web

这一章定义**行为**，T4 与 T5 按此实现。判断标准只有一条：模型变了，图不用手改。

### 5.1 三条不变量

1. **模型是唯一真相源**：图中不允许出现模型里不存在的节点。用户在画布上画的每个节点，必须能反查到 `elementRef`。
2. **布局不是模型**：`DiagramLayout`（x/y/宽高/折点）单独存储，删除布局不影响模型；反过来，模型元素删除时其布局级联清理。
3. **自动同步不接受手动例外**：`synchronization: auto` 的视图，禁止"从图中移除但保留在模型中"这种操作。用户想隐藏，只能用 filter（如 `hideAbstract`），不能用删除。

### 5.2 刷新机制

| 事件 | 触发 | 行为 | 时限 |
|---|---|---|---|
| 模型元素新建 | 编辑命令提交 | 通过 GraphQL subscription 通知已打开的画布，按 `autoExpand` 深度增量插入节点 | 2s 内 |
| 元素重命名 | 同上 | 重算 `labelExpression`，只更新标签，不重排 | 1s 内 |
| 连接新建/删除 | 同上 | 增删边，保留既有折点 | 2s 内 |
| 元素删除 | 同上 | 节点与其相连的边一并消失，布局记录删除 | 2s 内 |
| 布局变更 | 拖拽结束 | 只写 `DiagramLayout`，不产生模型变更事件 | 500ms 内 |

实现要求：**增量刷新，禁止整图重绘**。整图重绘在 500 元素规模下会超过 2s，直接判 AC-4 不通过。判定方法是 T5 的性能测试：500 元素、连续 20 次新增，每次刷新耗时中位数 < 800ms、P95 < 2000ms。

### 5.3 与 SysON 原生行为的差异对照（评审时用）

| 行为 | SysON 原生 Interconnection View | 本方案 |
|---|---|---|
| 新建元素是否自动出现 | 否，需手动拖入 | 是 |
| 隐藏元素方式 | 从图中移除 | filter 表达式 |
| 视图定义位置 | Java 代码 | YAML + 生成 |
| 元素删除后图内残留 | 可能残留 | 级联清除 |

---

## 6. Polarion 同步接口定义

### 6.1 `backend/src/main/resources/api/polarion-proxy.openapi.yaml` —— `[COPY]`

设计前提：**浏览器不直连 Polarion**（跨域 + 鉴权），所有读写经由本服务代理，透传当前用户登录态。服务本身不持有独立账号，不存密码。

```yaml
openapi: 3.0.3
info:
  title: SysArch ↔ Polarion 同步代理
  version: "1.0.0"
  description: 模型元素与 Polarion Work Item 的绑定、单向推送、增量拉取与一致性校验
servers:
  - url: http://localhost:8080/api/v1

paths:
  /links:
    get:
      summary: 查询绑定关系
      parameters:
        - name: projectId
          in: query
          required: true
          schema: { type: string }
        - name: state
          in: query
          schema: { type: string, enum: [ACTIVE, ORPHAN, DANGLING, CONFLICT] }
      responses:
        "200":
          description: 绑定列表
          content:
            application/json:
              schema:
                type: array
                items: { $ref: "#/components/schemas/Link" }
    post:
      summary: 建立或更新绑定（幂等）
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: "#/components/schemas/LinkRequest" }
      responses:
        "201": { description: 已建立 }
        "409": { description: 该元素已绑定其他工作项 }

  /links/{elementId}:
    delete:
      summary: 解除绑定（不删除工作项）
      parameters:
        - name: elementId
          in: path
          required: true
          schema: { type: string }
      responses:
        "204": { description: 已解除 }

  /sync/push:
    post:
      summary: 模型到 Polarion 的增量推送
      parameters:
        - name: Idempotency-Key
          in: header
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                projectId: { type: string }
                elementIds:
                  type: array
                  items: { type: string }
                dryRun: { type: boolean, default: false }
      responses:
        "200":
          description: 推送结果
          content:
            application/json:
              schema: { $ref: "#/components/schemas/SyncResult" }
        "409":
          description: 版本冲突，响应体含冲突字段清单
        "429":
          description: 触发 Polarion 限流，响应头带 Retry-After

  /sync/pull:
    post:
      summary: 从 Polarion 拉取变更（按 workItemId 增量）
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                projectId: { type: string }
                since: { type: string, format: date-time }
      responses:
        "200":
          description: 拉取结果
          content:
            application/json:
              schema: { $ref: "#/components/schemas/SyncResult" }

  /consistency/report:
    get:
      summary: 生成一致性报告
      parameters:
        - name: projectId
          in: query
          required: true
          schema: { type: string }
      responses:
        "200":
          description: 报告
          content:
            application/json:
              schema: { $ref: "#/components/schemas/ConsistencyReport" }

  /consistency/repair:
    post:
      summary: 按策略修复
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                projectId: { type: string }
                issueIds:
                  type: array
                  items: { type: string }
                strategy:
                  type: string
                  enum: [MODEL_WINS, POLARION_WINS]
      responses:
        "200":
          description: 修复结果，含逐条处理动作

  /webhooks/polarion:
    post:
      summary: 接收 Polarion 变更事件
      parameters:
        - name: X-Polarion-Signature
          in: header
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                event: { type: string, enum: [workitem.created, workitem.updated, workitem.deleted] }
                projectId: { type: string }
                workItemId: { type: string }
                revision: { type: string }
      responses:
        "202": { description: 已入队 }

components:
  schemas:
    Link:
      type: object
      required: [elementId, elementType, workItemId, projectId, state]
      properties:
        elementId: { type: string }
        elementType:
          type: string
          enum: [PartDefinition, Part, Port, Connection, Requirement]
        workItemId: { type: string }
        workItemType:
          type: string
          enum: [architecture_element, architecture_connection, requirement]
        projectId: { type: string }
        state:
          type: string
          enum: [ACTIVE, ORPHAN, DANGLING, CONFLICT]
        modelRev: { type: integer }
        wiRev: { type: string }
        etag: { type: string }
        lastPushAt: { type: string, format: date-time }
        lastPullAt: { type: string, format: date-time }
    LinkRequest:
      type: object
      required: [elementId, elementType, workItemId, workItemType, projectId]
      properties:
        elementId: { type: string }
        elementType: { type: string }
        workItemId: { type: string }
        workItemType: { type: string }
        projectId: { type: string }
    SyncResult:
      type: object
      properties:
        pushed: { type: integer }
        pulled: { type: integer }
        skipped: { type: integer }
        conflicts:
          type: array
          items:
            type: object
            properties:
              elementId: { type: string }
              fields: { type: array, items: { type: string } }
        errors:
          type: array
          items:
            type: object
            properties:
              elementId: { type: string }
              code: { type: string }
              message: { type: string }
    ConsistencyReport:
      type: object
      properties:
        projectId: { type: string }
        generatedAt: { type: string, format: date-time }
        issues:
          type: array
          items:
            type: object
            properties:
              id: { type: string }
              rule: { type: string, enum: [R1, R2, R3, R4, R5] }
              severity: { type: string, enum: [BLOCKER, MAJOR, MINOR] }
              elementId: { type: string }
              workItemId: { type: string }
              message: { type: string }
              repairable: { type: boolean }
```

### 6.2 字段归属矩阵（冲突策略的唯一依据）

写代码前先定这张表，否则双向同步必然打架。**每个字段只能有一个写入方**。

| 数据 | 唯一写入方 | 另一侧的权限 | 冲突表现 |
|---|---|---|---|
| Work Item 标题 | Polarion | 模型只读展示 | 模型侧改名仅作本地标签，不推送 |
| 元素名称 / 层 / 构造型 | 模型 | Polarion 只读 | 同上 |
| 接口两端（source/target） | 模型 | Polarion 字段由模型回写 | Polarion 手改端点 → 记 CONFLICT，需人工确认 |
| 需求文本 `text` | Polarion | 模型只读 | 模型侧不改，只做 Satisfy 关系 |
| 连接类型 `kind` | 模型 | — | — |
| 绑定关系本身 | 用户显式操作 | — | 自动同步不改绑定 |

**允许自动合并的只有"新增"**：模型新增元素 → 建 Work Item；Polarion 新增 Work Item → 落入"未绑定池"等用户认领。**更新一律走上面矩阵**，矩阵没覆盖的字段视为冲突，停下来问人。

### 6.3 同步的工程约束（T7 必须实现）

- **幂等**：`/sync/push` 与 `/sync/pull` 都必须带 `Idempotency-Key`，服务端保留 24 小时去重表，重放返回首次结果。
- **不写半成品**：单次推送先做全量预检（字段归属、约束），全部通过才提交；任何一条失败则整批回滚，错误逐条返回。
- **限流**：对 Polarion 的调用令牌桶限速，默认 10 rps，429 时按 `Retry-After` 退避，最多 3 次。
- **审计**：每次推送/拉取写 `sync_audit` 表（elementId、方向、字段、旧值、新值、操作人、时间），保留 180 天。
- **不允许后台静默改数据**：定时任务只做"检测"，修复必须由人触发 `/consistency/repair`。

---

## 7. 一致性校验规则

五条规则，全部在 `/consistency/report` 中输出，每条给出可点击的定位信息。

| 规则 | 名称 | 判定 | 严重度 | 修复动作 |
|---|---|---|---|---|
| R1 | 孤儿绑定 | 模型元素存在，对应 Work Item 已删除 | BLOCKER | 解除绑定并标记元素"待认领" |
| R2 | 悬空连线 | 画布有连线，Polarion 无对应关系；或反之 | MAJOR | 按 `MODEL_WINS` 或 `POLARION_WINS` 二选一重建 |
| R3 | 重复绑定 | 同一 Work Item 被多个元素绑定 | MAJOR | 保留一个，其余置为 `CONFLICT` |
| R4 | 层违规 | `Allocate` 跨层超过一级 | BLOCKER | 不允许自动修，必须人工改模型 |
| R5 | 未绑定元素 | 元素已入模型但无 Work Item | MINOR | 批量创建或忽略 |

**R2 是深度绑定模式最大的坑**，处理口径必须写进产品文档：打开图纸时自动跑一次，保存时再跑一次，结果在右侧面板以红/黄两色列出；**不自动修复**，只提供"以模型为准/以 Polarion 为准"两个按钮，且该选择按项目固定，不允许逐条随意切换。

---

## 8. P0 任务分解（T1–T9）

每条任务的"指令块"可直接粘贴给 Claude Code。要求它在**同一仓库上下文**中连续执行，不要每次重开新会话（元模型与生成代码的引用关系会丢）。

### T1 仓库骨架与一键启动（对应 AC-1）｜1 人 × 3 天

```text
在空目录 sysarch-studio 中按第 2.2 节的目录结构创建 monorepo（Gradle Kotlin DSL）。
约束：
1) 只创建骨架与配置，不写业务代码；所有占位类只留 TODO 注释。
2) docker-compose.yml 必须包含 postgres:16、一个 backend 服务、一个 frontend 服务；
   backend 依赖 postgres 健康检查，frontend 依赖 backend。
3) backend 使用 Spring Boot 3.3 + Sirius Web 2025.8 starter，版本写死在 gradle/libs.versions.toml。
4) application.yml 中数据库连接从环境变量注入，禁止硬编码密码。
5) 提供 README 的"3 行启动"说明。
验收：docker compose up -d 后 60 秒内 curl -f http://localhost:8080/actuator/health 返回 UP。
```

产物：目录树、`docker-compose.yml`、`libs.versions.toml`、`README.md`。
验收命令：

```bash
docker compose up -d && sleep 60 && curl -fsS localhost:8080/actuator/health | grep UP
```

### T2 元模型生成（对应 AC-2）｜1 人 × 4 天

```text
1) 把第 3.1 节的 Ecore 原文落盘为 metamodel/sysarch.ecore，不做任何修改。
2) 生成 sysarch.genmodel：包名 com.example.sysarch.model，Model Directory 指向 backend/src/main/java，
   Base Package 为 com.example.sysarch，关闭所有 UI/Editor 生成项，只生成 model 与 edit 的接口层。
3) 运行 EMF 代码生成，得到 PartDefinition/Part/Port/Connection/ItemFlow/Requirement/Allocate 七类。
4) 若 Sirius Web 2025.8 不支持直接导入 Ecore，实现 vsm-compiler 中的 compileEcoreToJson，
   输出 build/sysarch.metamodel.json，并在 backend 启动时加载。
5) 写一个 ModelSmokeTest：用 EMF 工厂各创建一个实例，设置 layer/stereotypes，断言可序列化到 XMI 并反序列化回来。
验收：./gradlew :backend:test --tests "*ModelSmokeTest*" 全绿。
```

### T3 VSM 编译器（对应 AC-3 前半）｜1 人 × 6 天

```text
实现 vsm-compiler 模块：
1) 定义 views/schema/vsm.schema.json，覆盖第 4.2 节全部字段，additionalProperties=false。
2) 解析 views/*.vsm.yaml，与元模型做交叉校验：domainClass 是否存在、feature 是否存在、
   appliesTo 是否合法、edgeStyles 的 sourceEnd/targetEnd 是否为该类的引用、labelExpression 中的 {feature} 是否存在。
3) 任一校验失败，输出 "views/<file>:<line> <message>" 并以退出码 1 结束；禁止降级为警告。
4) 用 FreeMarker 模板生成 <RepId>DiagramDescriptionProvider.java 与 ViewpointRegistry.java，
   输出到 backend/src/main/java/com/example/sysarch/view/generated/。
5) 生成前先删除上一次的 generated 目录；生成结果必须可重复，两次运行 sha256 一致。
验收：./gradlew :vsm-compiler:test 通过；故意把 appliesTo 写成 sysarch:PartDefintion（拼错），
      构建失败且错误信息包含文件名与行号。
```

### T4 BDD 视图落地（对应 AC-3 后半）｜1 人 × 5 天

```text
1) 落盘 views/sysarch-core.vsm.yaml（第 4.3 节原文），跑 T3 生成器。
2) 在 frontend 注册该视图，模型树中"块定义图"入口可打开画布。
3) 验证三件事：新建 PartDefinition 出现在图中；superTypes 渲染为空心三角；ownedParts 渲染为实心菱形且带名称。
4) 实现 hideAbstract 过滤器，切换后抽象块从画布消失但模型不变。
5) 写快照测试：给定固定模型 fixture（3 个块 2 条继承），断言生成的节点/边数量与标签文本。
验收：UI 手动验证 + ./gradlew :backend:test --tests "*BddSnapshotTest*"。
```

### T5 IBD 视图与自动同步（对应 AC-4）｜2 人 × 8 天｜**P0 最关键的一条**

```text
1) 落盘 views/sysarch-ibd.vsm.yaml（第 4.4 节原文），生成 IBD 视图。
2) Port 必须渲染为父部件边界上的 borderNode，affiliationFeature 分别指向 partOwner 与 owner。
3) 实现 synchronization=auto 的增量刷新：订阅编辑上下文的模型变更事件，
   按 autoExpand 深度计算受影响节点集合，只对差集做插入/删除/标签更新，禁止整图重绘。
4) 实现布局持久化：拖拽结束写 DiagramLayout（x/y/width/height/waypoints），刷新时优先用布局坐标，
   布局缺失时走自动布局。
5) 实现级联删除：模型元素删除后，其布局记录与相连的边一并清理。
6) 性能测试：构造 500 元素 fixture，连续新增 20 次，记录刷新耗时；
   断言中位数 < 800ms、P95 < 2000ms，并把结果写入 build/perf-refresh.json。
验收：./gradlew :backend:test --tests "*IbdSyncTest*" 与 perf 报告同时通过。
```

### T6 约束校验（对应 AC-5）｜1 人 × 5 天

```text
1) 用 Eclipse OCL 加载第 3.1 节 Ecore 中的六条不变量（Pivot 环境），实现统一校验入口 validate(ArchElement)。
2) 把校验挂到两条路径：保存前（编辑器提交）与连线落位前（画布的 create-edge 工具）。
3) 错误返回结构固定为 {code, elementId, message, anchors[]}，anchors 给出需要高亮的元素 ID，
   前端据此把两端端口描红并弹出提示条。
4) PortTypeCompatible 只做 interfaceType 字符串相等比较，不实现继承推导；在代码注释中写明该限制。
5) 写测试矩阵：类型相同放行、类型不同拒绝、IN-IN 拒绝、INOUT 放行、自环拒绝、跨两层 Allocate 拒绝。
验收：./gradlew :backend:test --tests "*ValidationMatrixTest*" 覆盖上述 6 个用例。
```

### T7 Polarion 绑定与推送拉取（对应 AC-6 前半）｜2 人 × 8 天

```text
1) 落盘第 6.1 节 OpenAPI 文件，用 openapi-generator 只生成接口与 DTO（不生成 controller 实现）。
2) 实现 Polarion 客户端：透传用户 token，不落盘；令牌桶限速 10 rps；429 按 Retry-After 退避最多 3 次。
3) 实现 /links 的建、查、解绑，写 links 表；同一 elementId 重复绑定返回 409。
4) 实现 /sync/push 与 /sync/pull：
   - push 前做全量预检（字段归属矩阵），全部通过才提交，任一失败整批回滚；
   - 必须校验 Idempotency-Key，24 小时内重放返回首次结果；
   - 每次同步写 sync_audit。
5) 字段归属严格按第 6.2 节矩阵，矩阵未覆盖字段一律记 CONFLICT，不自动合并。
6) 提供 Polarion 的 Mock Server（WireMock），录制 5 个场景：建、改名、改端点、删除、限流。
验收：./gradlew :backend:test --tests "*PolarionSyncIT*" 使用 WireMock 跑通 5 个场景。
```

### T8 一致性报告与修复（对应 AC-6 后半）｜1 人 × 5 天

```text
1) 实现 R1–R5 五条规则，输出第 6.1 节 ConsistencyReport 结构，每条 issue 带可定位的 elementId/workItemId。
2) /consistency/repair 只支持 MODEL_WINS 与 POLARION_WINS 两种策略，按项目配置固定；
   修复逐条返回动作，不提供"全部自动修"。
3) 接入 /webhooks/polarion：校验签名，事件只入队不直接改数据；
   队列消费侧触发"标记待校验"，由 R1–R5 在下一次报告时判定。
4) 报告在打开图纸时与保存时各触发一次（前端调用），结果渲染在右侧面板，红/黄分色。
验收：造 5 个异常 fixture（删 WI、删关系、重复绑定、跨层分配、未绑定元素），报告命中 5/5。
```

### T9 文档、演示数据与 CI｜1 人 × 4 天

```text
1) 生成 docs/metamodel-and-views.md，10 页量级：元模型图、七类元素字段表、两张视图的映射说明、
   新增一张视图的完整步骤（改 YAML→跑生成→注册→验证）。
2) 造一套演示数据：一个"域控制器"系统，含 3 个逻辑部件、6 个端口、4 条连接、5 条需求，跨 LA/PA 两层。
3) CI：gradle test + vsm 生成确定性 hash 比对 + perf 门槛检查，三项全绿才算通过。
验收：./gradlew build 在干净容器中一次通过。
```

---

## 9. 验收与演示脚本

### 9.1 P0 验收清单（逐条打勾，缺一即不通过）

| 编号 | 验收动作 | 通过标准 |
|---|---|---|
| AC-1 | `docker compose up -d` | health 返回 UP，60 秒内 |
| AC-2 | 建 7 类元素各一个 | 保存后刷新仍在，无报错 |
| AC-3 | 改 YAML 中的 block 边框色为 `#1C3D2E`，只重跑生成器 | 画布边框变色，Java 文件无手工改动 |
| AC-4 | 打开 IBD，在模型树加一个 Part | 2 秒内出现在画布 |
| AC-5 | 连两个 interfaceType 不同的 Port | 拒绝落位，两端端口描红 |
| AC-6 | 在 Polarion 删掉一个已绑定的 WI | 报告出现 R1，元素进入"待认领" |

### 9.2 演示脚本（给评审现场用，5 分钟跑完）

```bash
# 0) 起服务
docker compose up -d

# 1) 建立绑定（把模型元素挂到一个 Polarion 需求）
curl -sS -X POST localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $POLARION_TOKEN" \
  -d '{"elementId":"PD-001","elementType":"PartDefinition","workItemId":"PROJ-123","workItemType":"architecture_element","projectId":"sysarch"}'

# 2) 推送
curl -sS -X POST localhost:8080/api/v1/sync/push \
  -H "Idempotency-Key: demo-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d '{"projectId":"sysarch","elementIds":["PD-001"],"dryRun":false}'

# 3) 一致性报告
curl -sS "localhost:8080/api/v1/consistency/report?projectId=sysarch" | jq '.issues[] | {rule, severity, message}'
```

演示顺序建议：**先改模型看图自动变（AC-4）→ 再改 YAML 看样式变（AC-3）→ 再连错端口看被拦（AC-5）→ 最后删 WI 看告警（AC-6）**。这个顺序能在一分钟内把"模型驱动、声明式、有约束、可治理"四件事说清。

### 9.3 环境变量

| 变量 | 用途 | 必填 |
|---|---|---|
| `POSTGRES_URL` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | 数据库 | 是 |
| `POLARION_BASE_URL` | Polarion 地址（内网） | 是 |
| `POLARION_TOKEN` | 仅演示用；生产走登录态透传 | 否 |
| `SYNC_RATE_LIMIT_RPS` | 对 Polarion 的限速，默认 10 | 否 |
| `VIEW_REFRESH_BUDGET_MS` | 刷新预算，默认 2000 | 否 |
| `CONSISTENCY_STRATEGY` | `MODEL_WINS` \| `POLARION_WINS` | 是 |

---

## 10. 风险、止损与交付物

### 10.1 已识别的五个风险与对应处置

| 风险 | 触发信号 | 处置 |
|---|---|---|
| Sirius Web 版本漂移 | 上游 API 变更导致生成代码编译失败 | 锁版本 + fork；升级单独开分支，用 T4/T5 快照测试做回归门槛 |
| 生成器变成第二套框架 | VSM 字段越加越多，模板逻辑膨胀 | 硬约束：DSL 字段不超过第 4.2 节所列；新增字段必须走评审，禁止直连 Java API |
| 自动同步性能不达标 | 500 元素 P95 > 2000ms | 先查是否整图重绘；仍不达标则降级为"视图级可见性分区"（按层过滤），不牺牲语义 |
| Polarion 双向写冲突 | 同元素一天内出现 3 次以上 CONFLICT | 收紧字段归属，把争议字段改为单向；必要时关闭 pull，只做 push |
| 用户仍要 Cameo 式仿真 | 评审提出"模型要能跑" | 明确答复：P0 不做，P3 后外接 OpenModelica；不接受则该场景直接采购 Cameo |

### 10.2 三个止损点（预先写死，避免沉没成本）

1. **第 6 周末**：AC-4 与 AC-5 任一不通过 → 停止自研，转入"采购 EA + 保留 Polarion 轻量层"。
2. **第 12 周末**：真实子系统（≥80 元素）无法在画布上顺畅编辑 → 冻结新功能，只修可用性一个迭代；仍无改善则止损。
3. **累计投入超过采购方案五年 TCO 的 60%，且活跃建模用户 < 20 人** → 回退采购路线，自研成果转为内部组件。

### 10.3 P0 交付物清单

| 交付物 | 形态 | 验收人 |
|---|---|---|
| 可运行 Demo | docker compose 一键起，含演示数据 | 项目组 |
| `sysarch.ecore` 与 EMF 生成代码 | 源码 | 架构负责人 |
| VSM DSL Schema + 两张视图 YAML | 源码 + Schema | 架构负责人 |
| VSM 生成器 | Gradle 模块，含快照测试 | 开发 |
| Polarion 代理服务 | OpenAPI + 实现 + WireMock 测试 | 集成 |
| 一致性报告 | 页面 + 接口 | 项目组 |
| `metamodel-and-views.md` | 10 页说明 | 全员 |

---

## 11. 附录

### 11.1 `docker-compose.yml` 关键片段（T1 参考）

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: sysarch
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d sysarch"]
      interval: 5s
      retries: 10

  backend:
    build: ./backend
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      POSTGRES_URL: jdbc:postgresql://postgres:5432/sysarch
      POLARION_BASE_URL: ${POLARION_BASE_URL}
      SYNC_RATE_LIMIT_RPS: "10"
    ports: ["8080:8080"]

  frontend:
    build: ./frontend
    depends_on: [backend]
    ports: ["3000:3000"]
```

### 11.2 六个常见坑（提前写进代码注释）

1. **Ecore 里的 `<>` 必须写成 `&lt;&gt;`**，否则 XML 解析失败，OCL 表达式静默失效。
2. **`isBorderNode` 依赖 `affiliationFeature`**，指错父引用会让端口跑到画布中央。
3. **`eOpposite` 必须成对**，只写一半会导致 EMF 通知链断裂，自动同步收不到事件。
4. **生成器不要读运行时配置**，否则同一份 YAML 在不同机器上产出不同代码，CI 必然红。
5. **GraphQL subscription 要带节流**，高频拖拽会打爆前端；布局变更不应触发模型事件。
6. **Polarion 的 429 不是异常而是常态**，必须按 Retry-After 退避，不能直接抛给用户。

### 11.3 与采购路线的对照口径（汇报时说这三句）

- 这套工具**不替代 Cameo 的仿真与参数权衡**，替代的是"用 draw.io 画架构图"这一段。
- 相对 EA，它买到的是**Web 原生 + 深度嵌入 Polarion + 分层约束**，不是更全的功能。
- 相对 Papyrus 与 SysON，它多的是**可声明的视图层与 ALM 绑定**，少的是生态与成熟度。

### 11.4 下一步（P0 通过后再启动，不在本规格范围）

P1：补齐 SA/LA/PA 分层视图与分配矩阵表；P2：文档生成模板、编辑锁、千元素级性能；P3：参数图建模（求解外接）、状态机与活动图视图、SysML v2 文本导出。

---

**文档结束。执行入口：第 8 章 T1。**




