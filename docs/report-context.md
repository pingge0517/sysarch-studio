# 报告模板变量契约（ReportContext）

P2 规格书 §4.3 的契约，原文照录：

```text
project:      { id, name, baselineLabel, generatedAt }
system:       { name, description, owner }
layers[]:     { layer, name, elements[] }
elements[]:   { id, name, type, layer, stereotypes[], ports[], connections[] }
interfaces[]: { id, name, protocol, dataType, sourceElement, targetElement, layer }
requirements[]: { reqId, name, text, satisfiedBy[], verifiedBy[], status }
stats:        { elementCount, connectionCount, requirementCount, coverage }
```

模板里禁止出现业务判断（如"if 不满足 then 标红"），判断在抽取器里算好，模板只管排版。

---

## 实现说明（P2 T5）

以下是对上面原文的说明和补充，原文本身不变。

抽取器是 `backend/.../report/ReportExtractor.java`，三份模板在 `backend/src/main/resources/reports/`：`srs.ftl`、`icd.ftl`、`trace-report.ftl`。

### 字段含义

| 字段 | 取值 |
|---|---|
| `project.id` / `name` | Sirius 项目 id 和名称 |
| `project.baselineLabel` | 用哪个基线生成，就是那个基线的名称；用当前模型生成时为"当前模型" |
| `project.generatedAt` | 生成时间（服务器时区，`yyyy-MM-dd HH:mm`） |
| `system` | 带 `system` 构造型的块（有多个时取 id 最小的）。`owner` 是基线的创建人，用当前模型时是提交报告的用户 |
| `layers[]` | 按 OPERATIONAL → SYSTEM → LOGICAL → PHYSICAL → EPBS 排序，只列出有元素的层。`name` 是层的中文名，例如"LA 逻辑架构" |
| `elements[]` | 结构元素，即块（PartDefinition）和部件（Part），按层再按 id 排序。`type` 是"块"或"部件"。`ports[]` 是元素自己的端口：`{ id, name, direction, interface }`。`connections[]` 是连到这些端口的连接；块还包括连到它所含部件端口的连接，以及连到以它为类型的部件端口的连接：`{ id, name, source, target, interface }` |
| `interfaces[]` | **每条连接一行**，即一处点对点的接口。`name` 是连接名称；`protocol` / `dataType` 取自连接引用的接口定义；`sourceElement` / `targetElement` 是两端端口所属的部件或块；`layer` 是连接所在的层 |
| `requirements[]` | 全部需求，按需求 id 排序。`satisfiedBy[]` / `verifiedBy[]` 是元素名称。`status` 取"已验证"、"已满足"或"未满足" |
| `stats.coverage` | 已满足的需求数除以需求总数，写成百分比文本（例如 `60%`）；没有需求时为"—" |

### 补充字段

原契约里没有、但规格 §4.2 要求报告里必须有的内容，由抽取器另外算好，作为补充字段提供：

| 补充字段 | 用于 |
|---|---|
| `interfaces[].interface`、`interfaces[].requirements[]` | ICD 的"关联需求"：满足或验证两端元素（部件及其类型块）的需求 id |
| `interfaceDefs[]` | ICD 的接口定义表：`{ id, name, protocol, dataType, layer, usages }`，其中 `usages` 是使用它的连接数 |
| `elements[].description`、`elements[].typedBy`、`elements[].details[]` | SRS 附录的元素明细。`details[]` 是 `{ key, value }`，列出元素的全部字段 |
| `requirements[].id`、`layer`、`priority`、`verifyMethod`、`statusClass` | 追溯表的样式类和明细。`statusClass` 取 `ok`、`partial` 或 `missing`，由抽取器决定 |
| `stats.satisfied`、`stats.verified`、`stats.unsatisfied`、`stats.interfaceCount` | 追溯报告和 ICD 的统计 |
| `gaps[]` | "待补充"一节：`{ title, items[] }`，例如"2 条需求无 satisfiedBy"及其清单 |
| `unsatisfied[]` | 追溯报告的未满足清单：未满足需求的子集，字段同 `requirements[]` |

所有字段都不为 null：空值给空字符串，没有内容的列表给空列表，所以模板里不需要写默认值或判断。

### 模板规则

- 模板里只允许出现 `<#list>`（含 `<#else>`，用于空列表）和 `${...}`。
- 不允许 `<#if>`、`<#switch>`、`?then`、默认值运算符 `!`。`ReportTemplatesTest` 会检查这几条，出现即失败。
- 输出格式是 XHTML，值都会自动转义。同一份 HTML 再转成 Word（docx4j）和 PDF（openhtmltopdf，A4）。

### 什么情况下生成失败

模型不完整时照常生成，缺口列在"待补充"一节和任务的 `gaps` 里。只有根本无法生成时才失败，任务的 `missing` 会列出具体缺什么：

| 模板 | 失败条件 |
|---|---|
| `srs` | 没有带 `system` 构造型的块 |
| `icd` | 既没有连接也没有接口定义 |
| `trace-report` | 没有需求 |

另外，P2 之前建的基线没有记录元素的层级关系，无法还原成模型，用它们生成报告也会失败。
