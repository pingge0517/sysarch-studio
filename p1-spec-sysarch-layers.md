# P1 规格书：分层视图、分配矩阵与需求追溯（Claude Code 可执行版）

> 版本 v0.2.0 ｜ 2026-11-05 ｜ 周期 **12 周** ｜ 目标：**10 人以内的项目试用**
>
> 前置：P0 的 AC-1 至 AC-6 全部通过，打 tag `v0.1.0`。P1 期间 AC-1 至 AC-6 必须保持绿，任何一条回归即阻塞发布。
>
> 本文档沿用 P0 的写法：`[COPY]` 表示原文落盘，`[GENERATE]` 表示由 Claude Code 生成，`[MIGRATE]` 表示需要数据迁移。

---

## 0. 执行前置（先做这四件事，否则后面全是返工）

```bash
git checkout -b feature/p1 release/p0        # 从 v0.1.0 切出
./gradlew build                              # 确认基线可编译
./gradlew :backend:test                      # 确认 P0 测试全绿
docker compose up -d && ./tests/acceptance/run-p0.sh   # 确认 AC-1~AC-6 仍通过
```

四条命令任一失败，**先修基线，不进入 P1**。P1 的工作量集中在元模型变更，基线不稳会把问题放大。

---

## 1. P1 范围与不做清单

### 1.1 P1 交付的五件事

| 编号 | 交付 | 对应业务价值 |
|---|---|---|
| P1-1 | 分层视图：SA 功能链、LA 逻辑架构、PA 物理架构 | Capella 分层真正落地，架构师能按层工作 |
| P1-2 | 分配矩阵（表格视图） | 功能→逻辑→物理的分配可查、可审 |
| P1-3 | 需求追溯：Satisfy / Verify 关系与矩阵 | 需求到组件到接口的链路闭合 |
| P1-4 | 双向同步补全：需求 WI、定时拉取、冲突清单 | 与 Polarion 从"能绑"变成"能协同" |
| P1-5 | 基线、模型 diff、SysML v2 文本导出 | 可评审、可交付、可对外交换 |

### 1.2 不做（推到 P2/P3）

- **不做**编辑锁与分支合并（P2）。
- **不做**文档/报告生成（P2）。
- **不做**参数图、状态机、活动图（P3）。
- **不做** XMI 与 Cameo 互导（P3）。
- **不做**元模型之外的治理字段（如审批状态）。P1 的元模型只加"接口类型实体化"与"需求追溯引用"两处。

---

## 2. 元模型 v1.1（Ecore diff）

### 2.1 变更清单

| 变更 | 类型 | 原因 | 影响面 |
|---|---|---|---|
| 新增 `InterfaceDef` | 新 EClass | 接口从字符串升级为实体，才能承载协议、数据类型、继承 | 端口、连接、视图、同步 |
| `Port.interfaceType` 由 `EString` 改为 `EReference → InterfaceDef` | 破坏性变更 | 支撑类型兼容校验 | **需要数据迁移** |
| 新增 `Requirement.satisfiedBy` / `verifiedBy` | 新 EReference | 承载 Satisfy / Verify | 追溯矩阵、同步 |
| 新增 `InterfaceDef.superTypes` | 新 EReference | 支撑接口继承下的兼容判定 | 校验 |

**刻意不加的**：`ArchElement.status`（审批态）、`Diagram.version`。这两个属于治理层，放在数据库与工作流里，进了元模型会让导出语义变脏。

### 2.2 `metamodel/sysarch.ecore` 的差异片段 —— `[COPY]` 合并进原文件

```xml
  <!-- 新增：接口定义 -->
  <eClassifiers xsi:type="ecore:EClass" name="InterfaceDef" eSuperTypes="#//ArchElement">
    <eAnnotations source="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot">
      <details key="ProtocolRequired" value="not protocol.oclIsUndefined() and protocol.size() &gt; 0"/>
    </eAnnotations>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="protocol" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="dataType" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="superTypes" upperBound="-1" eType="#//InterfaceDef"/>
  </eClassifiers>

  <!-- 变更：Port.interfaceType 改为引用 -->
  <eStructuralFeatures xsi:type="ecore:EReference" name="interfaceType" eType="#//InterfaceDef"/>
```

```xml
  <!-- 变更：Requirement 增加追溯引用 -->
  <eStructuralFeatures xsi:type="ecore:EReference" name="satisfiedBy" upperBound="-1" eType="#//ArchElement"/>
  <eStructuralFeatures xsi:type="ecore:EReference" name="verifiedBy" upperBound="-1" eType="#//ArchElement"/>
```

### 2.3 新增约束（替换 P0 的字符串比较）

```text
约束名：PortInterfaceAssignable
适用：Connection
表达：kind = ASSEMBLY 时，source 与 target 的 interfaceType 必须"可赋值"。
判定：可赋值 = 同一实体，或一方是另一方的祖先（superTypes 传递闭包）。
实现：OCL 不写闭包，注册 Java 侧 operation `InterfaceDef::compatibleWith(other: InterfaceDef): Boolean`，
      OCL 中直接调用。理由：闭包在 OCL Pivot 上对大模型开销高，Java 侧可加缓存。
```

这条约束替换 P0 的 `PortTypeCompatible`（字符串相等）。**迁移完成后必须删除旧约束**，否则会因字符串与新引用并存而重复报错。

### 2.4 数据迁移 `[MIGRATE]`

P0 的 `interfaceType` 是字符串（如 `"CAN_FD_500k"`）。迁移规则：

1. 扫描全部 `Port.interfaceType` 的非空字符串值，按值去重；
2. 每个唯一值生成一个 `InterfaceDef`（`name` = 原字符串，`protocol` 留空，`dataType` 留空），置于 `layer = LOGICAL` 的接口库中；
3. 把 `Port.interfaceType` 指向对应实体；
4. 无法解析的空值保持空，并在迁移报告中列出（这些端口本来就不满足 P0 的 `InterfaceTypeRequired`，属于历史脏数据）。

迁移必须**可回滚**：迁移前导出全部链接关系的 CSV，回滚时按 CSV 还原为字符串。

---

## 3. 新增视图（VSM）

### 3.1 五张视图的定位

| 视图 id | 名称 | kind | 根元素 | 过滤 |
|---|---|---|---|---|
| `sa-function-chain` | 系统功能链（SA） | diagram | `sysarch:PartDefinition` | `layer == 'SYSTEM'` |
| `la-architecture` | 逻辑架构（LA） | diagram | `sysarch:PartDefinition` | `layer == 'LOGICAL'` |
| `pa-architecture` | 物理架构（PA） | diagram | `sysarch:PartDefinition` | `layer == 'PHYSICAL'` |
| `allocation-matrix` | 分配矩阵 | table | `sysarch:Allocate` | — |
| `requirement-trace` | 需求追溯 | table | `sysarch:Requirement` | — |

LA 与 PA 是同一模板的两个实例，只差 `layer` 过滤与配色。**不要为它们各写一份完全独立的 YAML**，用 `views/_templates/layer-architecture.vsm.tpl.yaml` 加三个参数文件生成，避免三份配置漂移。

### 3.2 `views/sa-function-chain.vsm.yaml` —— `[COPY]`

```yaml
vsm: "1.0"
viewpoint:
  id: "sysarch.core"
  label: "系统架构建模"
  metamodelUri: "http://example.com/sysarch/1.0"
  priority: 10

representations:
  - id: "sa-function-chain"
    label: "系统功能链（SA）"
    kind: "diagram"
    domainClass: "sysarch:PartDefinition"
    synchronization: "auto"
    refresh:
      mode: "incremental"
      budgetMs: 2000
    filters:
      - id: "onlySystem"
        label: "仅系统层"
        expression: "layer == 'SYSTEM'"
    autoExpand:
      - feature: "ownedParts"
        maxDepth: 2
      - feature: "ownedPorts"
        maxDepth: 1
    nodeStyles:
      - id: "function"
        appliesTo: "sysarch:PartDefinition"
        shape: "rounded-rectangle"
        color: "#FFFFFF"
        borderColor: "#1C3D2E"
        labelExpression: "{name}"
        subLabelExpression: "{stereotypes}"
      - id: "role"
        appliesTo: "sysarch:PartDefinition"
        shape: "rectangle"
        color: "#F2F5F3"
        borderColor: "#778B82"
        labelExpression: "{name}"
    edgeStyles:
      - id: "exchange"
        domainClass: "sysarch:Connection"
        sourceEnd: "source"
        targetEnd: "target"
        routing: "spline"
        color: "#1C3D2E"
        arrow: "open"
        labelExpression: "{interfaceType.name}"
    tools:
      - id: "createFunction"
        label: "新建功能"
        action: "create-child"
        targetClass: "sysarch:Part"
        containmentFeature: "ownedParts"
        defaults:
          layer: "SYSTEM"
      - id: "connect"
        label: "建立功能交互"
        action: "create-edge"
        targetClass: "sysarch:Connection"
        validateOnDrop: true
```

### 3.3 `views/allocation-matrix.vsm.yaml` —— `[COPY]`

表格视图是 P1 唯一的新 `kind`，编译器要新增 `TableRepresentationGenerator`。

```yaml
vsm: "1.0"
viewpoint:
  id: "sysarch.core"
  label: "系统架构建模"
  metamodelUri: "http://example.com/sysarch/1.0"
  priority: 10

representations:
  - id: "allocation-matrix"
    label: "分配矩阵（系统→逻辑→物理）"
    kind: "table"
    domainClass: "sysarch:Allocate"
    rows:
      class: "sysarch:ArchElement"
      filter: "layer == 'SYSTEM' or layer == 'LOGICAL'"
      labelExpression: "{name}"
      groupBy: "layer"
    columns:
      class: "sysarch:ArchElement"
      filter: "layer == 'LOGICAL' or layer == 'PHYSICAL'"
      labelExpression: "{name}"
      groupBy: "layer"
    cells:
      existsExpression: "from == row and to == column"
      markSymbol: "●"
      color: "#1C3D2E"
    actions:
      - id: "createAllocate"
        label: "建立分配"
        action: "create-link"
        targetClass: "sysarch:Allocate"
        validateOnCreate: true
      - id: "deleteAllocate"
        label: "取消分配"
        action: "delete-link"
```

`LayerAdjacentOnly` 在这个视图上会高频触发，因此表格的 `create-link` 必须和画布一样走校验入口，**不能因为"表格里点一下就建了"而绕过约束**。

### 3.4 `views/requirement-trace.vsm.yaml` —— `[COPY]`

```yaml
vsm: "1.0"
viewpoint:
  id: "sysarch.core"
  label: "系统架构建模"
  metamodelUri: "http://example.com/sysarch/1.0"
  priority: 10

representations:
  - id: "requirement-trace"
    label: "需求追溯矩阵"
    kind: "table"
    domainClass: "sysarch:Requirement"
    rows:
      class: "sysarch:Requirement"
      labelExpression: "{reqId} {name}"
    columns:
      class: "sysarch:PartDefinition"
      filter: "layer == 'SYSTEM' or layer == 'LOGICAL' or layer == 'PHYSICAL'"
      labelExpression: "{name}"
    cells:
      existsExpression: "self.satisfiedBy->includes(column) or self.verifiedBy->includes(column)"
      markSymbol: "○"
      color: "#758092"
    actions:
      - id: "satisfy"
        label: "建立满足关系"
        action: "link"
        feature: "satisfiedBy"
      - id: "verify"
        label: "建立验证关系"
        action: "link"
        feature: "verifiedBy"
```

### 3.5 DSL 新增字段（编译器要支持）

| 字段 | 适用 kind | 语义 |
|---|---|---|
| `filters[].expression` | diagram / table | 布尔表达式，支持 `layer`、`isAbstract`、`stereotypes` 三个字段 |
| `rows` / `columns` / `cells` | table | 行列来源与单元格判定 |
| `groupBy` | table | 按字段分组表头（P1 支持 `layer`） |
| `actions[]` | table | 表格内可执行的建模动作 |
| `defaults` | diagram tool | 新建时预填属性 |

编译期规则不变：未知类、未知特征、未知表达式字段一律失败并输出 `file:line`。新增一条：**`expression` 只允许引用白名单字段**，出现别的标识符即失败（防止有人写 `self.getOwner().getLayer()` 这种绕过）。

---

## 4. 接口变更

### 4.1 OpenAPI 追加 —— `[COPY]` 合并进 `polarion-proxy.openapi.yaml`

```yaml
  /baselines:
    post:
      summary: 创建基线（对当前模型打快照）
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [projectId, label]
              properties:
                projectId: { type: string }
                label: { type: string }
                description: { type: string }
      responses:
        "201":
          description: 基线已创建
          content:
            application/json:
              schema: { $ref: "#/components/schemas/Baseline" }
    get:
      summary: 列出基线
      parameters:
        - name: projectId
          in: query
          required: true
          schema: { type: string }
      responses:
        "200":
          description: 基线列表

  /baselines/{baselineId}/diff:
    get:
      summary: 基线与当前模型（或另一基线）的差异
      parameters:
        - name: baselineId
          in: path
          required: true
          schema: { type: string }
        - name: against
          in: query
          schema: { type: string, enum: [CURRENT, BASELINE] }
        - name: otherBaselineId
          in: query
          schema: { type: string }
      responses:
        "200":
          description: 差异清单
          content:
            application/json:
              schema: { $ref: "#/components/schemas/ModelDiff" }

  /export/sysmlv2:
    post:
      summary: 导出 SysML v2 文本
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [projectId]
              properties:
                projectId: { type: string }
                scope: { type: string, enum: [PROJECT, LAYER, ELEMENT] }
                layer: { type: string }
                elementIds:
                  type: array
                  items: { type: string }
      responses:
        "200":
          description: 文本流
          content:
            text/plain:
              schema: { type: string }

  /trace/matrix:
    get:
      summary: 追溯矩阵数据（前端渲染用）
      parameters:
        - name: projectId
          in: query
          required: true
          schema: { type: string }
      responses:
        "200":
          description: 需求到元素的追溯关系
```

```yaml
components:
  schemas:
    Baseline:
      type: object
      properties:
        id: { type: string }
        projectId: { type: string }
        label: { type: string }
        createdAt: { type: string, format: date-time }
        elementCount: { type: integer }
        modelRev: { type: integer }
    ModelDiff:
      type: object
      properties:
        added:
          type: array
          items: { type: string }
        removed:
          type: array
          items: { type: string }
        modified:
          type: array
          items:
            type: object
            properties:
              elementId: { type: string }
              fields: { type: array, items: { type: string } }
```

### 4.2 DDL 迁移 `V2__baseline.sql` —— `[COPY]`

```sql
create table baseline (
  id            varchar(64) primary key,
  project_id    varchar(64) not null,
  label         varchar(200) not null,
  description   text,
  model_rev     integer not null,
  element_count integer not null default 0,
  created_by    varchar(128) not null,
  created_at    timestamptz not null default now()
);
create index idx_baseline_project on baseline(project_id, created_at desc);

-- 基线只存元素快照的引用与关键字段，不做全量拷贝
create table baseline_element (
  baseline_id  varchar(64) not null references baseline(id) on delete cascade,
  element_id   varchar(64) not null,
  element_type varchar(64) not null,
  name         varchar(500),
  layer        varchar(32),
  attributes   jsonb not null default '{}',
  primary key (baseline_id, element_id)
);
```

### 4.3 同步侧的两处收紧

1. **需求 WI 接入**：`LinkRequest.workItemType` 增加 `requirement`，模型侧对应 `sysarch:Requirement`，字段归属按 P0 第 6.2 节执行（`reqId` 由 Polarion 写，`satisfiedBy` 由模型写）。
2. **定时拉取**：新增调度任务，默认 15 分钟一次 `/sync/pull`，`since` 取上次成功时间；**只拉取增量，不拉全量**。拉取失败不阻塞用户操作，只在一致性面板显示"上次同步时间"。

---

## 5. 任务分解（T1–T9）

### T1 元模型 v1.1 与迁移｜2 人 × 6 天

```text
1) 按第 2.2 节修改 metamodel/sysarch.ecore，运行 EMF 重新生成；确认编译通过。
2) 编写 Flyway 迁移 V2__interface_entity.sql（如接口实体也需落库则加表，否则仅记迁移标记）。
3) 实现 InterfaceMigration：扫描 Port 的字符串 interfaceType，按第 2.4 节四步转换，
   输出 migration-report.csv（含无法解析的空值清单）。
4) 迁移必须支持 dry-run：参数 --dry-run 只输出报告不写库。
5) 写回滚脚本：从迁移前导出的 links.csv 还原为字符串。
验收：./gradlew :backend:test --tests "*InterfaceMigrationTest*"；在演示数据上 dry-run 报告与实跑结果一致。
```

### T2 接口兼容校验｜1 人 × 5 天

```text
1) 实现 Java 侧 InterfaceDef.compatibleWith(other)：同实体返回 true；否则沿 superTypes 求祖先集合，
   双向判断包含关系；结果按 (a,b) 缓存，模型变更时失效。
2) 用 OCL 注册该 operation，替换 P0 的 PortTypeCompatible 约束；删除旧约束，避免双重报错。
3) 补充 InterfaceDef 的 ProtocolRequired 约束。
4) 校验接入三条入口：画布连线落位、表格 create-link、批量导入。
验收：矩阵测试覆盖 同实体/子接口/父接口/无关接口/空值 五种情况。
```

### T3 分层视图（SA / LA / PA）｜2 人 × 10 天

```text
1) 落盘 views/_templates/layer-architecture.vsm.tpl.yaml，用 layer 参数生成三份 YAML
   （sa-function-chain / la-architecture / pa-architecture），模板中禁止出现硬编码颜色以外的分支逻辑。
2) 实现编译器对 filters.expression 的白名单求值（只允许 layer / isAbstract / stereotypes）。
3) 三个视图共享同一套 nodeStyles 结构，差异只在过滤与配色；配色按第 3.2 节的墨绿色系。
4) 校验：切到 LA 视图时，PHYSICAL 元素必须不可见且不可新建（工具需按 layer 限制 targetClass 的默认 layer）。
验收：三视图可打开；在 LA 视图新建元素，其 layer 自动为 LOGICAL；切到 PA 视图后新元素不可见。
```

### T4 分配矩阵表｜1 人 × 6 天

```text
1) 在编译器中新增 TableRepresentationGenerator，支持 rows/columns/cells/groupBy/actions。
2) 前端实现矩阵渲染：行列按 layer 分组表头，单元格内点击建连，标记符号与颜色按 YAML。
3) create-link 与 delete-link 必须走统一校验入口（T2 的校验 + LayerAdjacentOnly）。
4) 大矩阵性能：50×50 = 2500 格，首屏渲染 < 1.5s，只渲染可视区域。
验收：跨两层建分配被拒并提示"跳过 LA"；同层建分配被拒。
```

### T5 需求追溯｜1 人 × 5 天

```text
1) 落盘 requirement-trace 视图 YAML，实现 satisfiedBy / verifiedBy 两种 link 动作。
2) 追溯矩阵接口 /trace/matrix 返回结构化数据，供前端与 P2 的报告复用。
3) 实现"追溯完整性"检查：每个 SYSTEM 层功能至少有一条 satisfiedBy，否则在一致性报告中作为 R6（MINOR）输出。
验收：演示数据 5 条需求中，2 条故意不满足，报告命中 2 条。
```

### T6 双向同步补全｜2 人 × 8 天

```text
1) /links 支持 workItemType=requirement；字段归属按第 6.2 节矩阵扩展一行（reqId 由 Polarion 写）。
2) 实现 15 分钟增量拉取调度：since 取上次成功时间，失败退避 2 分钟后重试一次，连续 3 次失败暂停并告警。
3) 拉取只处理 workitem.updated 与 workitem.deleted，created 进"未绑定池"等认领。
4) 同步面板显示三个时间：上次推送、上次拉取、上次一致性校验。
验收：Mock Server 模拟"改标题""删 WI""改端点"三个场景，各自产生正确结果。
```

### T7 基线与模型 diff｜2 人 × 8 天

```text
1) 实现 /baselines 三个接口，基线落 baseline 与 baseline_element 两张表（第 4.2 节 DDL）。
2) diff 算法：按 element_id 对齐，输出 added / removed / modified（含字段清单）。
   只比 name、layer、attributes 三类；布局字段不参与 diff。
3) 前端提供"基线选择器 + diff 清单"，modified 行可跳转到对应元素。
4) 基线创建必须与模型版本号绑定，禁止在编辑事务中途创建。
验收：改 3 个元素（1 改名、1 删、1 加）后 diff 精确命中 3 项，无多余项。
```

### T8 SysML v2 文本导出｜1 人 × 6 天

```text
1) 实现 /export/sysmlv2，按 scope 导出 PROJECT / LAYER / ELEMENT。
2) 映射规则：PartDefinition → part def；Part → part；Port → port；Connection → connect；
   Requirement → requirement；ItemFlow → 注释化保留（SysML v2 无直接对应，导出为 doc 注释，不伪造语法）。
3) 输出必须能被 SysON 原生解析器接受；用 SysON 的文本导入做往返验证（导入后元素数量一致）。
4) 不支持的部分（stereotypes、layer）用 @ 注释块保留，避免信息丢失。
验收：演示数据导出 → 用 SysON 导入 → 元素数、名称、层级三项一致。
```

### T9 试用与打磨｜全员 × 10 天

```text
1) 在 1 个真实项目（≤10 人）试用两周，收集问题，只修 P0/P1 级缺陷。
2) 补齐文档：docs/layers-and-trace.md，写清三层怎么用、分配怎么建、追溯怎么查。
3) 性能基线：500 元素、3 张视图同时打开，切换视图 < 1s。
验收：试用问题清单关闭率 ≥ 80%，无 BLOCKER 遗留。
```

---

## 6. 验收清单

| 编号 | 验收动作 | 通过标准 |
|---|---|---|
| P1-AC-1 | 打开 LA 视图 | 只见 LOGICAL 元素，新建元素自动归 LOGICAL |
| P1-AC-2 | 在分配矩阵点格建连 | 跨两层被拒，提示含"跳过 LA" |
| P1-AC-3 | 建 Satisfy 关系 | 追溯矩阵出现标记，且写入 Polarion 关系 |
| P1-AC-4 | 改一个元素名后打基线 | diff 只报 1 项 modified |
| P1-AC-5 | 导出 SysML v2 再导入 SysON | 元素数、名称、层级一致 |
| P1-AC-6 | 断开 Polarion 网络 15 分钟 | 拉取任务告警但界面可继续编辑 |
| P1-AC-7 | 跑 P0 全部验收 | AC-1~AC-6 全绿（无回归） |

---

## 7. 风险与回滚

| 风险 | 判据 | 处置 |
|---|---|---|
| 接口实体化引发全量迁移失败 | 迁移后端口无法连线 | 用 dry-run 报告先验证；失败按 CSV 回滚到字符串版本，P1 其余功能不受影响 |
| 表格视图性能不达标 | 50×50 首屏 > 3s | 降级为"按 layer 分页查看"，每页 20×20 |
| SysML v2 导出被判定为"伪标准" | SysON 导入失败 | 先缩到 PROJECT 范围导出；仍失败则只导出结构注释版，并在文档标注"非严格交换格式" |
| 分层视图配置漂移 | LA 与 PA 行为不一致 | 强制用同一模板生成，CI 中比对三份 YAML 的结构差异，只允许 layer 与配色不同 |

**回滚点**：P1 有两个可独立回滚的模块——元模型 v1.1（回滚到 v1.0 + 字符串接口）与新增视图（删除 YAML 与生成代码即可）。两者互不依赖，不要做成一个整体发布。

---

## 8. 交付物

| 交付物 | 形态 | 验收人 |
|---|---|---|
| 元模型 v1.1 + 迁移脚本 + 迁移报告 | 源码 + CSV | 架构负责人 |
| 五张新视图（YAML + 生成代码） | 源码 | 架构负责人 |
| 基线 / diff / 导出接口 | OpenAPI + 实现 + 测试 | 集成 |
| 追溯矩阵 | 页面 + 接口 | 项目组 |
| `layers-and-trace.md` | 文档 | 全员 |
| 试用报告 | 文档 | 项目组 |

---

**下一步：P2 规格书（协同、报告、导入、性能）。P1 验收全绿后再启动。**
