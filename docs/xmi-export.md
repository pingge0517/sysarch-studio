# XMI 导出（P3 T7）

SysArch Studio 可以把整个项目导出为 **XMI 2.5.1**：元素用标准 UML 2.5.1 表达，SysML 含义用 SysML 1.6 构造型表达。导出文件可以导入 Cameo Systems Modeler（MagicDraw）、Papyrus 等工具。

> **XMI 往返不保证分层无损。** 分层（`layer`）和自定义构造型（`stereotypes`）在标准 XMI 里没有对应项，导出时写在各元素的 `xmi:Extension`（`extender="SysArch Studio"`）里，标准工具看不到。P3 只保证"元素与关系不丢"，不保证"分层不丢"。需要连分层一起无损交换时，请用 **SysML v2 文本导出**（`POST /api/v1/export/sysmlv2`），分层以元数据保留。

## 1. 怎么导出

```bash
curl -X POST http://<host>/api/v1/export/xmi \
     -H 'Content-Type: application/json' \
     -d '{"projectId": "<Sirius 项目 id 或名称，或已映射的 Polarion 项目 id>"}' \
     -o project.xmi
```

返回 `application/xml`（UTF-8），文件名为"项目名.xmi"。

可选参数 `"umlVersion": "2.5"`：文件声明 UML 2.5 命名空间（`http://www.omg.org/spec/UML/20131001`），而不是默认的 UML 2.5.1（`…/20161101`，SysML 1.6 的基础）。只有命名空间声明和文件头说明不同，元素完全相同，供只认识 UML 2.5 的导入器使用（例如 Eclipse UML2 5.5）。如果 Cameo 导入时报告不认识 UML 2.5.1，就改用这个参数。其他取值返回 400。本工具的 XMI 导入两种命名空间都接受。

可选参数 `"flavour": "papyrus"`：按 Eclipse Papyrus（SysML 1.6）自己保存模型的格式输出，下载文件名为"项目名.uml"。这个格式是让 Papyrus 自己的 API 保存一个小模型后对照出来的：
- 使用 Eclipse UML2 的 UML 命名空间；
- 构造型放在 Papyrus 按包拆分的命名空间里（`Blocks:Block`、`PortsAndFlows:InterfaceBlock`、`Requirements:Requirement/Satisfy/Verify`、`Allocations:Allocate`），并带上 Papyrus 需要的额外基引用；
- 每个 SysML 子配置文件各有一个 profileApplication，指向 Papyrus 的 `pathmap://SysML16_PROFILES/SysML.profile.uml`。

元素、id 和扩展与默认格式完全相同。默认的 `omg` 格式给 Cameo 和其他遵循 OMG 标准的工具使用。其他取值返回 400。本工具的 XMI 导入只接受 OMG 格式。运维可以用 `SYSARCH_FEATURE_XMI=false` 关闭导出入口，此时返回 503。

## 2. 映射

| SysArch | XMI | Cameo 中显示为 |
|---|---|---|
| 块（PartDefinition） | `uml:Class` + `sysml:Block`；继承为 `uml:Generalization` | Block |
| 部件（Part） | 块的 `uml:Property`，类型为部件的块，`aggregation="composite"`，多重性写成上下限 | Part Property |
| 端口（Port） | `uml:Port`，类型为接口；`isConjugated` 照写 | Port |
| 连接（Connection） | 同时包含两端的块下的 `uml:Connector`，两端为端口加 `partWithPort` | Connector |
| 接口（InterfaceDef） | `uml:Class` + `sysml:InterfaceBlock` | Interface Block |
| 需求（Requirement） | `uml:Class` + `sysml:Requirement`（`id` = 需求编号，`text` = 需求文本） | Requirement |
| 满足、验证、分配 | `uml:Abstraction` + `sysml:Satisfy` / `sysml:Verify` / `sysml:Allocate` | Satisfy / Verify / Allocate |
| 描述 | 元素自带的 `uml:Comment` | Comment |
| 交互（v1.4） | `uml:Interaction`：生命线 `uml:Lifeline`；每条消息一对按时间顺序排列的 `uml:MessageOccurrenceSpecification`（发送、接收），以及 `uml:Message`（messageSort 为 synchCall、asynchCall 或 reply）。生命线代表块时，`represents` 指向交互中以该块为类型的属性 | Interaction / Sequence Diagram |

文件开头有一段 XML 注释和一个 `xmi:Documentation`，注明"分层信息为扩展注释，标准工具不可见"，并列出各类元素各导出了多少个、哪些没有导出。

## 3. 写在扩展里的内容（标准工具看不到）

| 元素 | 扩展项 |
|---|---|
| 所有元素 | `layer`、`stereotype`（每个一项）；元素 id 不是合法 XML 名称时，另记原始 `id` |
| 端口 | `direction`（IN/OUT/INOUT）；部件上的端口另记 `partOwner` |
| 接口 | `protocol`、`dataType` |
| 需求 | `verifyMethod`、`priority` |
| 连接 | `kind`、`interfaceType`、`itemFlow`（id、名称、条目名、条目类型、分层）；两端不在同一个块中时记 `crossBlock` |
| 分配 | `rationale` |
| 生命线 | `represents`：代表的元素不是块时（例如部件），记录它的 id |
| 部件 | 写不成上下限的多重性，原样记在 `multiplicity` |

## 4. 已知限制

- **部件上的端口**：UML 的端口只能属于类型，不能属于某个部件。所以部件上的端口会写到部件的类型块上（部件没有类型时，写到部件所在的块），扩展里记下它原本属于哪个部件。同一个块类型的多个部件，在 Cameo 里会共享这些端口。
- **跨块连接**：两端不在同一个块中的连接，放在源端所在的块下，扩展里标记 `crossBlock`。
- **不导出**：参数图元素（约束块、参数、绑定），以及状态机、活动、用例、参与者，文件头会列出数量。交互（v1.4）会导出。需要时请用 SysML v2 文本导出，或在原工具里查看。
- **导入**：见第 5 节。

## 5. 导入（P3 T8）

`POST /api/v1/imports/xmi`（请求体为 XMI 文本，可选查询参数 `projectName`、`defaultLayer`、`fileName`），也可以在网页"导入"里把格式选为 XMI。导入总是新建一个项目，和 SysML v2 导入共用同一个任务与报告。

- **只接受两种来源**：本工具导出的 XMI；结构极简的 Cameo 导出（带 «Block» 的 `uml:Class`、以块为类型的组合属性、端口、连接器、«InterfaceBlock»、«Requirement»，以及 «Satisfy»/«Verify»/«Allocate» 的 `uml:Abstraction`，可以放在包里）。
- **其他内容整批失败**：活动、状态机、交互、值属性、操作、普通类、其他 SysML 构造型等，会逐个列出类型、名称和 id，并且不建任何东西。
- **会被忽略、不导致失败的**：其他工具的 `xmi:Extension`，以及其他配置文件（例如 MagicDraw 自带的）的构造型应用。它们列在报告的"未导入"中。
- **分层**：只能从本工具写的扩展里还原。Cameo 文件里的元素放在默认层，并在报告中列出。
- **往返**：本工具的导出经"导出 → 导入 → 再导出"后文本完全相同，由 `XmiRoundTripTest` 的三组 golden 用例保证。

## 6. 人工验证（P3-AC-6）

本机没有 Cameo，P3-AC-6 需要人工验证一次，步骤如下：

1. 用第 1 节的命令导出演示项目，或直接用测试生成的 `backend/build/xmi-export/domain-controller.xmi`（运行 `./gradlew :backend:test --tests "*XmiExportTest*"` 即可得到）。
2. 在 Cameo 里新建一个 SysML 项目，用 *File → Import From → UML XMI 2.5 File* 导入（菜单名称因版本略有不同）。
3. 检查：
   - 块"域控制器"带 «Block»；
   - 它的三个部件和四个连接器都在；
   - 需求带 «Requirement»，编号和文本正确；
   - 有三条 «Satisfy»、一条 «Verify»、三条 «Allocate»。

   给"域控制器"建一张 IBD，并选择显示已有的部件和连接器，应能看到部件、端口和连线。

### 代替验证：Eclipse UML2（2026-11-11）

在 Cameo 验证之前，先用 **Eclipse UML2 5.5**（UML 元模型的参考实现，Papyrus 的基础）做了一次自动检查。它不能代替 Cameo。工具在 `tools/uml2-check/Uml2Check.java`（不参与构建，运行方式写在文件头），所需 jar 已从 Eclipse 官方 p2 仓库和 Maven Central 下载，并核对了校验和。

**结果**：演示项目和三组 golden 用例全部通过。
- **能被识别**：UML2 用它的 OMG XMI 加载器读入，没有加载错误，所有引用都能解析。
- **数量与原模型一致**。以演示项目为例：12 个 Class、3 个部件 Property、6 个 Port、4 个 Connector（8 个端点都指向部件类型上的端口）、7 个 Abstraction。
- **构造型**：19 个 SysML 构造型应用都有基元素。
- **UML 校验**：UML2 的校验器没有发现任何问题。唯一的报告是 SysML 配置文件引用无法解析，这是预期的：UML2 本身不带 SysML 配置文件，Cameo 和 Papyrus 会把这个标准 URI 解析到各自内置的 SysML 1.6。

**一项需要在 Cameo 中留意的差异**：本工具按 SysML 1.6 的要求，写的是 UML 2.5.1 命名空间 `http://www.omg.org/spec/UML/20161101`。UML2 5.5 只认识到 UML 2.5（`…/20131001`），所以检查时只把这一个命名空间改成了 2.5。我们用到的类和属性在两个版本中完全相同。这个选项现在已经提供（第 1 节的 `umlVersion: "2.5"`）：用它导出的文件，UML2 不经任何改写即可直接读入（2026-11-11 已实测）。如果 Cameo 不接受 2.5.1，请改用它。

### 代替验证：Papyrus（SysML 1.6，2026-11-11）

工具：Eclipse Papyrus for SysML 1.6 独立版（2.2.0_c，2023-01；Windows zip 410,561,399 字节，与服务器大小一致，解压校验无错误），放在 `C:\PapyrusCheck`，不在项目内。本机工具只能操作浏览器，不能操作桌面程序，所以没有在 Papyrus 界面里点选。改用 Papyrus 自带的 jar（它的 UML2 5.5 和 SysML 1.6 模型包，也就是编辑器底层用的代码）无界面加载演示文件，结果如下：

| 文件 | Papyrus 看到的内容 |
|---|---|
| 默认（UML 2.5.1 命名空间） | 模型**不被识别为 UML**：Papyrus 的 UML2 只认识到 UML 2.5 |
| `umlVersion: "2.5"` | 模型完整读入为 UML：类、部件、端口、连接器、关系都在。但 **19 个 SysML 构造型应用不被识别** |

**原因**：Papyrus 6.x 的 SysML 1.6 使用自己的命名空间（`http://www.eclipse.org/papyrus/sysml/1.6/SysML/Blocks`、`…/Requirements` 等，按包拆分），而本工具和 OMG 标准 XMI 写的是统一的 `http://www.omg.org/spec/SysML/20181001/SysML`。在 Papyrus 中打开时，内容会显示为普通 UML（类、端口、连接器），没有 «Block»、«Requirement» 等构造型。

**结论**：
- **Papyrus 部分通过**：结构可以读入（需用 `umlVersion: "2.5"`），SysML 构造型需要 Papyrus 专用命名空间才能识别。
- **Cameo 仍待验证**：Cameo 读写的是 OMG 标准命名空间，所以本工具当前的写法正是为 Cameo 准备的。
- **后续已补上 `"flavour": "papyrus"`（第 1 节）**。用 Papyrus 自己的 UML2 和 SysML 1.6 配置文件加载演示项目的 Papyrus 格式导出（`tools/uml2-check/PapyrusCheck.java`），结果：
  - SysML 及 4 个子配置文件都已应用；
  - 12 个类、6 个端口、4 个连接器、7 个关系；
  - **每个元素都带有它的构造型**：6 个 «Block»、1 个 «InterfaceBlock»、5 个 «Requirement»（编号和文本都能读出）、3 个 «Satisfy»、1 个 «Verify»、3 个 «Allocate»。

  因此 Papyrus 一项通过。

如果要在 Papyrus 界面里亲自看：运行 `C:\PapyrusCheck\app\papyrusSysML16\papyrusSysML.exe`，新建一个工程，把 `C:\PapyrusCheck\files\domain-controller.uml`（Papyrus 格式）复制进去，在新建 Papyrus 模型时选择已有的 .uml 文件（或用导入 Papyrus 模型的向导）生成图文件，然后就能在模型浏览器中看到各元素及其构造型。
