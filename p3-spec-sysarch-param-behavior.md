# P3 规格书：参数图、行为视图与 XMI 互导（Claude Code 可执行版）

> 版本 v0.4.0 ｜ 2026-11-05 ｜ 周期 **16 周（含 2 个限时探针）** ｜ 定位：**能力补齐 + 对外交换**
>
> 前置：P2 的 P2-AC-1 至 P2-AC-9 全部通过，tag `v0.3.0`。
>
> **P3 与前三期的性质不同：它不再是"建一个工具"，而是"补两块硬能力 + 开一扇对外的门"。** 因此每条能力都必须带一个明确的退出条件——做不到就承认做不到，不要把半成品留在主干上。

---

## 0. 执行前置

```bash
git checkout -b feature/p3 release/p2
./gradlew build && ./gradlew :backend:test
./tests/acceptance/run-p0.sh && ./tests/acceptance/run-p1.sh && ./tests/acceptance/run-p2.sh
```

---

## 1. 范围：两主两辅

| 编号 | 内容 | 性质 | 周期 |
|---|---|---|---|
| P3-1 | 参数图：建模 + **外接求解**（不自研求解器） | 主 | 6 周 |
| P3-2 | 行为视图：状态机、活动图、用例图 | 主 | 5 周 |
| P3-3 | XMI 导出与导入，与 Cameo / Papyrus 互导 | 主 | 4 周 |
| P3-4 | 探针 A：Sirius Web 上的 Sequence 视图可行性 | 辅（限时） | 2 周 |
| P3-5 | 探针 B：几何视图（Geometry）是否需要 | 辅（限时） | 1 周 |

### 1.1 一条硬边界：不写求解器

参数图的价值分配很清楚：**建模归我们，求解归外部**。

- 我们负责：约束块的元模型、参数绑定、表达式管理、输入数据集、结果回填与展示。
- 外部负责：数值求解。支持三种后端——Python（SciPy）、OpenModelica、HTTP 求解服务。
- **禁止**在 Java 里实现方程求解、符号微分、优化算法。这不是保守，是能力边界：商业工具这一层投入了二十年。

### 1.2 不做

- 不做 fUML 语义执行（状态机的"跑起来"不在本期，只做建模与静态检查）。
- 不做 UI 模拟、动画播放（Cameo 的 Simulation Toolkit 能力，明确放弃）。
- 不做几何视图的真实三维渲染，探针 B 只判断"要不要排期"。
- 不做 SysML v2 全量语法导入，范围仍限定在 P2 第 5.1 节。

---

## 2. 元模型 v1.3（Ecore diff）

### 2.1 新增类

| 类 | 关键属性 / 引用 | 说明 |
|---|---|---|
| `ConstraintBlock` | `parameters[1..*]`、`expression: ConstraintExpr` | 约束块，替代 P0 的"自由文本约束" |
| `Parameter` | `name`、`unit`、`direction`、`defaultValue` | 参数，带单位 |
| `ConstraintExpr` | `language`、`body`、`declaredVars` | 表达式容器；`language` ∈ `PYTHON` / `MODELICA` / `MATH` |
| `BindingConnector` | `source: Parameter`、`target: Parameter` | 参数绑定（参数图里的连线） |
| `StateMachine` | `states[]`、`transitions[]` | 状态机 |
| `State` | `isInitial`、`isFinal`、`entry/do/exit` | 状态 |
| `Transition` | `trigger`、`guard`、`effect`、`source`、`target` | 迁移 |
| `Activity` | `actions[]`、`flows[]` | 活动 |
| `Action` | `kind`（`CALL`/`ASSIGN`/`DECIDE`）、`expression` | 动作 |
| `UseCase` | `actors[]`、`subject` | 用例 |
| `Actor` | `name` | 参与者 |

### 2.2 Ecore 片段 —— `[COPY]` 合并进 `sysarch.ecore`

```xml
  <eClassifiers xsi:type="ecore:EClass" name="ConstraintBlock" eSuperTypes="#//ArchElement">
    <eAnnotations source="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot">
      <details key="HasExpression" value="not expression.oclIsUndefined()"/>
      <details key="ParamsHaveUnit" value="parameters->forAll(p | not p.unit.oclIsUndefined() and p.unit.size() &gt; 0)"/>
    </eAnnotations>
    <eStructuralFeatures xsi:type="ecore:EReference" name="parameters" lowerBound="1" upperBound="-1"
        eType="#//Parameter" containment="true"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="expression" lowerBound="1" eType="#//ConstraintExpr" containment="true"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="Parameter" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="unit" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="direction" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString" defaultValueLiteral="IN"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="defaultValue" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EDouble"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="ConstraintExpr">
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="language" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString" defaultValueLiteral="PYTHON"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="body" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="declaredVars" upperBound="-1" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="BindingConnector" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EReference" name="source" lowerBound="1" eType="#//Parameter"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="target" lowerBound="1" eType="#//Parameter"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="StateMachine" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EReference" name="states" upperBound="-1" eType="#//State" containment="true"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="transitions" upperBound="-1" eType="#//Transition" containment="true"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="State" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="isInitial" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EBoolean" defaultValueLiteral="false"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="isFinal" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EBoolean" defaultValueLiteral="false"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="doBehavior" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
  </eClassifiers>

  <eClassifiers xsi:type="ecore:EClass" name="Transition" eSuperTypes="#//ArchElement">
    <eStructuralFeatures xsi:type="ecore:EReference" name="source" lowerBound="1" eType="#//State"/>
    <eStructuralFeatures xsi:type="ecore:EReference" name="target" lowerBound="1" eType="#//State"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="trigger" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="guard" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
    <eStructuralFeatures xsi:type="ecore:EAttribute" name="effect" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
  </eClassifiers>
```

> `Activity` / `Action` / `UseCase` / `Actor` 按上表同构实现，不再全文列出；生成时保证 `Action.kind` 用枚举而非字符串。

### 2.3 约束（参数与状态机）

```text
ConstraintBlock::UnitConsistent       参数单位必须可被求解器识别（见第 3.2 节单位白名单）
ConstraintBlock::NoDuplicateParamName  同名参数拒绝
StateMachine::SingleInitial            有且仅有一个 isInitial 状态
StateMachine::Reachability             每个状态必须从初始态可达（图算法，非 OCL）
Transition::GuardNotEmpty              有 guard 必须有 trigger
```

`Reachability` 用图遍历实现而不是 OCL——OCL 上跑可达性在大状态机上会超时，这是 P0 就定下的原则（复杂算法放 Java）。

---

## 3. 参数图与外接求解

### 3.1 视图 `views/parametric.vsm.yaml` —— `[COPY]`

```yaml
vsm: "1.0"
viewpoint:
  id: "sysarch.core"
  label: "系统架构建模"
  metamodelUri: "http://example.com/sysarch/1.0"
  priority: 10

representations:
  - id: "parametric"
    label: "参数图（Parametric）"
    kind: "diagram"
    domainClass: "sysarch:ConstraintBlock"
    synchronization: "auto"
    refresh:
      mode: "incremental"
      budgetMs: 2000
    autoExpand:
      - feature: "parameters"
        maxDepth: 1
    nodeStyles:
      - id: "constraintBlock"
        appliesTo: "sysarch:ConstraintBlock"
        shape: "rectangle"
        color: "#FFFFFF"
        borderColor: "#252525"
        labelExpression: "{name}"
        compartments:
          - feature: "parameters"
            label: "parameters"
            itemExpression: "{direction} {name} : {unit}"
    nodeStylesExtra:
      - id: "parameter"
        appliesTo: "sysarch:Parameter"
        shape: "square"
        width: 8
        height: 8
        isBorderNode: true
        affiliationFeature: "eContainer"
        labelPosition: "outside"
        labelExpression: "{name}"
    edgeStyles:
      - id: "binding"
        domainClass: "sysarch:BindingConnector"
        sourceEnd: "source"
        targetEnd: "target"
        routing: "straight"
        lineStyle: "solid"
        color: "#252525"
        labelExpression: ""
    tools:
      - id: "createConstraint"
        label: "新建约束块"
        action: "create-child"
        targetClass: "sysarch:ConstraintBlock"
      - id: "bind"
        label: "绑定参数"
        action: "create-edge"
        targetClass: "sysarch:BindingConnector"
        validateOnDrop: true
```

### 3.2 求解适配层（不自研求解器）

**统一接口**：`ConstraintSolver` 三个实现——`PythonSolver`、`ModelicaSolver`、`RemoteHttpSolver`。

```java
public interface ConstraintSolver {
    String id();                                   // python | modelica | remote
    SolveResult solve(SolveRequest request);       // 无状态，可并行
    boolean supports(String unit);                 // 单位白名单校验
}
```

**单位白名单**（P3 只认 SI 前缀，不做单位换算）：`m, kg, s, A, K, mol, cd, W, V, Hz, N, Pa, J, C, rad, 1`，以及加前缀的 `mm, cm, km, g, ms, us, kHz, MHz`。白名单外的单位在建模阶段就报错，不允许带进求解器。

### 3.3 OpenAPI 追加 —— `[COPY]`

```yaml
  /solve:
    post:
      summary: 对一个约束块集合求解
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [projectId, constraintBlockIds, inputs, targets]
              properties:
                projectId: { type: string }
                constraintBlockIds:
                  type: array
                  items: { type: string }
                solver: { type: string, enum: [python, modelica, remote], default: python }
                inputs:
                  type: object
                  additionalProperties: { type: number }
                targets:
                  type: array
                  items: { type: string }
                options:
                  type: object
                  properties:
                    tolerance: { type: number, default: 1e-6 }
                    maxIterations: { type: integer, default: 200 }
      responses:
        "200":
          description: 求解结果
          content:
            application/json:
              schema:
                type: object
                properties:
                  converged: { type: boolean }
                  residuals:
                    type: object
                    additionalProperties: { type: number }
                  outputs:
                    type: object
                    additionalProperties: { type: number }
                  iterations: { type: integer }
                  solverLog: { type: string }
        "422":
          description: 约束无法求解（欠定、超定、单位不一致），响应体列出原因
        "504":
          description: 求解超时（默认 30 秒）
```

### 3.4 Python 求解脚本 `solver/python_solver.py` —— `[COPY]`

```python
"""约束块求解：输入 JSON（stdin），输出 JSON（stdout）。只用 SciPy，不引入其它依赖。"""
import json, sys
import numpy as np
from scipy.optimize import least_squares

def main():
    req = json.load(sys.stdin)
    body = req["expression"]           # 例如 "P_out = P_in * eff"
    env = dict(req["inputs"])
    targets = req["targets"]

    def residual(x):
        local = dict(env)
        for i, t in enumerate(targets):
            local[t] = float(x[i])
        exec(body, {"__builtins__": {}}, local)   # 受限命名空间，禁止 import / open
        return np.array([float(local[t]) for t in targets])

    x0 = np.array([float(env.get(t, 1.0)) for t in targets])
    res = least_squares(residual, x0, xtol=req["options"]["tolerance"],
                        max_nfev=req["options"]["maxIterations"])
    print(json.dumps({
        "converged": bool(res.success),
        "outputs": {t: float(v) for t, v in zip(targets, res.x)},
        "residuals": {t: float(r) for t, r in zip(targets, res.fun)},
        "iterations": int(res.nfev),
        "solverLog": str(res.message)
    }))

if __name__ == "__main__":
    main()
```

**安全要求**：`exec` 的命名空间必须为空且禁用内置函数，脚本运行在独立进程、无网络、只读文件系统的容器里。这是 P3 唯一必须做安全评审的组件。

---

## 4. 行为视图

### 4.1 三张视图

| 视图 id | 根元素 | 关键渲染 |
|---|---|---|
| `state-machine` | `sysarch:StateMachine` | 初始伪状态、圆角状态、迁移带 `trigger[guard]/effect` |
| `activity` | `sysarch:Activity` | 动作矩形、判定菱形、对象流为虚线 |
| `use-case` | `sysarch:UseCase` | 椭圆用例、火柴人参与者、关联实线 |

三张都沿用 P1 的模板化写法，**共用同一个 `layer-architecture` 之外的行为模板**，不各写一套。

### 4.2 静态检查（本期只做建模，不做执行）

| 检查 | 规则 | 输出 |
|---|---|---|
| 初始态唯一 | `SingleInitial` | BLOCKER |
| 可达性 | 每状态从初始态可达 | MAJOR |
| 死迁移 | transition 的 source 无出边且非 final | MINOR |
| 动作完整性 | `Action.kind = CALL` 必须有 `expression` | MAJOR |

四项并入一致性报告，作为 R7–R10。

---

## 5. XMI 互导

### 5.1 导出

用 EMF 原生 `XMIResource`，`sysarch.ecore` 映射到 `xmi:Extension` 中的构造型，主流工具能识别元素与关系，构造型以注释保留。

| 我们 | XMI 输出 | Cameo 侧对应 |
|---|---|---|
| `PartDefinition` | `uml:Class` + `«block»` | Block |
| `Part` | `uml:Property` + `«part»` | Part |
| `Port` | `uml:Port` | Port |
| `Connection` | `uml:Connector` | Connector |
| `Requirement` | `uml:Class` + `«requirement»` | Requirement |

**必须承认的损失**：`layer`、`stereotypes` 在标准 XMI 里没有等价物，导出写进 `xmi:Documentation` 注释块；导回时不保证还原（P3 只保证"元素与关系不丢"，不保证"分层不丢"）。这句话要写进对外文档，避免被当成双向无损。

### 5.2 导入

只接受由我们导出的 XMI，或结构极简的 Cameo 导出（`uml:Class` + `«block»` + `uml:Property`）。**不接受任意第三方 XMI**——变体太多，做兼容是无底洞。

### 5.3 往返测试（golden file）

```text
fixtures/xmi/roundtrip/
  case-01-basic/        3 块 4 端口 2 连接
  case-02-layers/       三层各 2 元素
  case-03-requirement/  5 需求 + 满足关系
验收：export → import → 再 export，两次导出文本归一化后 diff 为空（忽略 id 与时间戳）。
```

---

## 6. 两个限时探针（到点即停，不做延期）

### 6.1 探针 A：Sequence 视图（2 周）

```text
目标：判断 Sirius Web 能否在 2 周内做出可用的时序图（含生命线、消息、激活条）。
做法：只用 Sirius Web 现有能力做最小实现，不写自研渲染器。
判定：
  - Go：能画出 3 条生命线、6 条消息，拖动不丢布局 → 排入 P4。
  - No-Go：需要自研渲染或依赖未发布的 API → 明确放弃，时序图降级为"只读导出 PNG"。
产出：1 页结论 + 截图，不提交主干代码。
```

### 6.2 探针 B：几何视图（1 周）

```text
目标：判断"物理布局三维/几何视图"是否是真需求。
做法：访谈 3 位架构师，问"现在用什么画物理布置，为什么要在这里画"。
判定：若答案集中在"给评审看"，则用 2D 布置图 + 导出即可，不做三维。
产出：需求结论，不写代码。
```

---

## 7. 任务分解（T1–T9）

### T1 元模型 v1.3｜2 人 × 8 天

```text
1) 按第 2.2 节扩展 Ecore，Activity/Action/UseCase/Actor 同构补齐，枚举化 Action.kind。
2) EMF 重新生成；确认不影响 P0–P2 已有类的序列化兼容。
3) 写迁移：无（新增类，无破坏性变更），但需验证旧 XMI 仍可读。
验收：旧模型文件加载后元素数不变；新类各建一个实例并可保存。
```

### T2 四类约束与静态检查｜1 人 × 6 天

```text
1) 实现 ConstraintBlock 三条、StateMachine 两条、Transition 一条约束。
2) 用图算法实现 Reachability，节点 > 200 时加超时（5 秒）并降级为 WARNING。
3) 四项状态机检查并入一致性报告 R7–R10。
验收：构造 5 个反例（双初始态、不可达态、空 guard、无表达式 CALL），全部命中。
```

### T3 参数图视图｜1 人 × 5 天

```text
1) 落盘 parametric YAML，确认参数渲染为 borderNode，compartment 显示 direction/unit。
2) 绑定工具 validateOnDrop=true，指到不同单位参数时拦截。
验收：3 个约束块、6 个参数、4 条绑定可完整绘制并保存。
```

### T4 求解适配层｜2 人 × 8 天

```text
1) 实现 ConstraintSolver 接口与三个实现；PythonSolver 用独立进程调用第 3.4 节脚本。
2) 单位白名单校验前置：不合法单位直接 422，不进入求解。
3) 求解结果回填到 Parameter.defaultValue，并在参数图上以只读徽标显示。
4) 超时 30 秒，超时返回 504 且不写回任何值。
5) 安全：容器无网络、只读根文件系统、执行前扫描脚本中的 import/open/eval。
验收：三个用例（单方程求解、超定无解、欠定多解）结果符合预期；注入恶意脚本被拒绝执行。
```

### T5 状态机视图｜1 人 × 6 天

```text
1) 落盘 state-machine YAML：初始伪状态用实心圆，final 用同心圆，迁移标签三段式。
2) 迁移标签模板：{trigger}[{guard}]/{effect}，空段自动省略，不留空方括号。
验收：5 状态 7 迁移的图可完整渲染，标签无空段。
```

### T6 活动图与用例图｜1 人 × 6 天

```text
1) 两张视图沿用行为模板，只改节点形状与边语义。
2) 活动图的对象流（虚线）与判定菱形必须与动作矩形区分明显（形状，不用颜色堆）。
验收：各画一张，元素类型与 YAML 映射一致。
```

### T7 XMI 导出｜1 人 × 5 天

```text
1) 实现 /export/xmi，用 XMIResource 输出，映射按第 5.1 节表。
2) layer 与 stereotypes 写入 xmi:Documentation，并在文件头注明"分层信息为扩展注释，标准工具不可见"。
验收：导出文件用 Cameo 打开（人工验证一次），块与连接可见。
```

### T8 XMI 导入与往返｜1 人 × 5 天

```text
1) 只接受第 5.2 节限定范围，其余元素类型整批失败并列出。
2) 实现 golden file 往返测试（第 5.3 节三组用例），二次导出归一化后 diff 为空。
验收：./gradlew :backend:test --tests "*XmiRoundTripTest*" 三组全绿。
```

### T9 探针与收口｜1 人 × 6 天

```text
1) 执行探针 A（2 周）与探针 B（1 周），各出 1 页结论。
2) 更新对外文档：明确"XMI 往返不保证分层无损"、"参数求解依赖外部后端"、"行为图不执行"。
3) 跑 P0–P2 全量回归。
验收：探针结论落档；回归全绿；对外文档三条限制均有明文。
```

---

## 8. 验收清单

| 编号 | 验收动作 | 通过标准 |
|---|---|---|
| P3-AC-1 | 建 3 个约束块并绑定参数 | 图可保存，单位不一致被拦 |
| P3-AC-2 | 调用 /solve 求解 | 收敛结果与手算一致（误差 < 1e-6） |
| P3-AC-3 | 提交超定方程组 | 返回 422 并说明原因 |
| P3-AC-4 | 注入 `import os` 脚本 | 被拒绝，容器未受影响 |
| P3-AC-5 | 画 5 状态状态机 | 双初始态、不可达态被 R7/R8 命中 |
| P3-AC-6 | 导出 XMI 用 Cameo 打开 | 块与连接可见 |
| P3-AC-7 | 往返三组用例 | diff 为空 |
| P3-AC-8 | 探针 A | 2 周内出 Go/No-Go 结论 |
| P3-AC-9 | 回归 P0–P2 | 全绿 |

---

## 9. 风险与回滚

| 风险 | 判据 | 处置 |
|---|---|---|
| 求解器被当成"我们的能力"对外承诺 | 客户以为参数求解是自研 | 对外口径统一为"外接 Python/Modelica 求解"，文档与销售材料同步 |
| 脚本执行成为安全漏洞 | 出现 import / 文件读取 | 三重防线：静态扫描 + 空命名空间 + 无网络只读容器；任一缺失则下线求解功能 |
| XMI 被要求"无损往返" | 对方要求分层也还原 | 明确拒绝并给替代方案（导出 SysML v2 文本，分层以注释保留） |
| Sequence 探针做成半成品 | 2 周后仍不可用但代码已合入 | 探针代码不进主干，放 `spike/` 分支，结论优先 |
| 行为视图拖慢整体性能 | 大状态机渲染超 3s | 状态机节点上限 200，超出启用分区视图 |

**回滚点**：参数求解（关掉 /solve 与求解菜单即可）、行为视图（删 YAML 与生成代码）、XMI（关导出入口）。三者互不耦合。

---

## 10. 交付物与路线收口

| 交付物 | 形态 |
|---|---|
| 元模型 v1.3 + 四张新视图 | 源码 |
| 求解适配层 + Python 脚本 + 安全评审记录 | 源码 + 文档 |
| XMI 导出/导入 + golden files | 源码 + 测试 |
| 探针 A/B 结论 | 各 1 页 |
| 对外限制说明 | 文档（三条） |

### 10.1 四年路线的一句话总结

P0 证明"能跑"，P1 证明"能按层用"，P2 证明"能多人用、能出文档"，P3 证明"能算一点、能对外交换一点"。**四期做完，能力大致对齐 EA 的结构建模与追溯，仍明显落后于 Cameo 的仿真与协同治理**——这不是失败，是当初定 L2 档位时就接受的结果。

### 10.2 什么时候该停下来买 Cameo

出现以下任一情况，P3 之后不再继续自研扩展：验收要求模型可执行（fUML 语义）、要求方案权衡的自动化 trade study、要求跨企业模型协同与基线治理。这三件事各自都是一个独立工程量级，不是"再加一个迭代"。

---

**文档结束。执行入口：第 7 章 T1。**
