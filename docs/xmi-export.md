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

返回 `application/xml`（UTF-8），文件名为"项目名.xmi"。运维可以用 `SYSARCH_FEATURE_XMI=false` 关闭导出入口，此时返回 503。

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
| 部件 | 写不成上下限的多重性，原样记在 `multiplicity` |

## 4. 已知限制

- **部件上的端口**：UML 的端口只能属于类型，不能属于某个部件。所以部件上的端口会写到部件的类型块上（部件没有类型时，写到部件所在的块），扩展里记下它原本属于哪个部件。同一个块类型的多个部件，在 Cameo 里会共享这些端口。
- **跨块连接**：两端不在同一个块中的连接，放在源端所在的块下，扩展里标记 `crossBlock`。
- **不导出**：参数图元素（约束块、参数、绑定）和行为元素（状态机、活动、用例、参与者），文件头会列出数量。需要时请用 SysML v2 文本导出，或在原工具里查看。
- **导入**：只接受本工具导出的 XMI，或结构极简的 Cameo 导出（P3 T8），不接受任意第三方 XMI。

## 5. 人工验证（P3-AC-6）

本机没有 Cameo，P3-AC-6 需要人工验证一次，步骤如下：

1. 用第 1 节的命令导出演示项目，或直接用测试生成的 `backend/build/xmi-export/domain-controller.xmi`（运行 `./gradlew :backend:test --tests "*XmiExportTest*"` 即可得到）。
2. 在 Cameo 里新建一个 SysML 项目，用 *File → Import From → UML XMI 2.5 File* 导入（菜单名称因版本略有不同）。
3. 检查：
   - 块"域控制器"带 «Block»；
   - 它的三个部件和四个连接器都在；
   - 需求带 «Requirement»，编号和文本正确；
   - 有三条 «Satisfy»、一条 «Verify»、三条 «Allocate»。

   给"域控制器"建一张 IBD，并选择显示已有的部件和连接器，应能看到部件、端口和连线。
