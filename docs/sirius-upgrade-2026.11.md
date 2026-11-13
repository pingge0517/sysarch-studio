# Sirius Web 升级演练：2025.8.11 → 2026.11.0（P4 T7）

> 2026-11-12/13 ｜ 分支 `spike/sirius-upgrade`（独立 worktree `C:\SysArchUpgrade`，**不合入主干**）｜ 结论：**本期不升级，继续锁定 2025.8.11**

## 1. 结论

升级可以做，但不是“改个版本号”：需要改写 Sirius 适配层，并迁移到 Spring Boot 4。预计约 **1–1.5 周**（见第 4 节），这超出了 P4 的范围。P4 规格 §5.1 的判定规则是：全部测试和 P0–P3 验收通过、性能不退化才升级；否则记录阻塞点，继续锁定版本。本次连编译都还没有通过，因此**继续使用 2025.8.11**，升级作为独立任务排期，建议放在试点之后。

## 2. 版本变化

| 项目 | 现在 | 2026.11.0 |
|---|---|---|
| Spring Boot | 3.5.5 | **4.1.1**（Spring Framework 7） |
| Java | 21 | 21 |
| AQL（Acceleo） | Acceleo 4.0 仓库 | AQL 8.1.2，需要 Acceleo **4.2** 仓库（`settings.gradle.kts` 中的地址要改） |
| 前端 React | 18.3.1 | 18.3.1（不变） |
| 前端其他依赖 | — | 41 个版本变化：MUI 7.0→7.3、xyflow 12.6→12.10、Apollo 3.10→3.14、Lexical 0.8→0.42、react-window 1→2、elkjs 0.8→0.11，新增 i18n（react-i18next、i18next-http-backend）等 |

## 3. 发现的破坏点

### 3.1 后端：70 个编译错误，全部集中在 9 个 Sirius 边界文件

没有一个编译错误来自 Spring Boot 4，也没有一个落在模型、校验、同步、报告、导入导出等业务代码里。这也印证了 P0 起“所有 Sirius 依赖都放在适配层”的做法。

| 原因 | 文件 | 新的位置或做法 |
|---|---|---|
| **工具执行方式改变**：工具 DTO 不再带 `handler`，执行改由 Spring Bean 负责（`ISingleClickOnOneDiagramElementHandler` / `ISingleClickOnTwoDiagramElementHandler`，按工具 ID 的 `canHandle` 选择处理者，`execute` 得到编辑上下文、图、元素 ID、工具 ID 和变量） | `ToolFactory`（16 个）、`SysarchPaletteProvider`（33 个） | 现有的 create / connect / link / 分区等处理逻辑可以保留，改为由一个分发 Bean 按工具 ID 调用。这是唯一需要设计的改动 |
| 调色板 DTO 移到新模块 | `SysarchPaletteProvider` | `org.eclipse.sirius.components.palette.dto`（`Palette` 改为 record：`id, quickAccessTools, paletteEntries`）；`IPaletteProvider.canHandle/handle` 新增参数 `List<String>` / `List<Object>` |
| 工具 DTO 换包 | `ToolFactory`、`SiriusModelGateway` | `org.eclipse.sirius.components.collaborative.diagrams.dto.*` |
| 建项目的输入改变 | `BranchService`、`ImportService`、`ImportController` | `CreateProjectInput(id, name, templateId, libraryIds)`（不再有 natures）；`IProjectApplicationService` 换包 |
| 项目领域换包 | `ProjectCleanup`、`SiriusModelGateway` | `org.eclipse.sirius.web.projects.domain.events.ProjectDeletedEvent`；`IProjectSearchService` 换包 |
| 表示描述列表 | `SysarchRepresentationDescriptionsLister` | `RepresentationDescriptionMetadata` 已改动 |
| 输入后处理 | `ConstraintEnforcement` | `getInput()` 已不存在（变更描述的取法改了） |

### 3.2 前端：我们的代码不用改

把依赖换成 2026.11.0 的版本后，`frontend/src/application`（宿主 Sirius 应用、`/sysarch` 路由、导航链接、合并策略）**无需任何修改就通过了类型检查和构建**（4.65 MB）。唯一要补的是新的可选依赖 `i18next`（≥ 25.5.2）。前端版本必须与后端一致，所以运行时要等后端也升级后才能验证。新增的 i18n 可能需要提供语言资源文件，届时检查。

## 4. 未做的部分与工作量估计

本次演练只做到“依赖能解析、错误全部列清”，下面这些还没有做：

| 工作 | 估计 |
|---|---|
| 改写适配层：工具分发 Bean、调色板、项目输入和事件、描述列表、输入后处理（第 3.1 节） | 2–3 天 |
| Spring Boot 4 的运行时问题：Spring Framework 7、Jackson 3 默认（我们的代码使用 `com.fasterxml.jackson`）、Actuator/Micrometer、Liquibase；Eclipse OCL 6.19（来自 p2）与新版 EMF、Guava、Xtext 的兼容性 | 1–3 天，运行前无法确定 |
| 回归：`./gradlew build` 三道 CI 关、`run-p3.sh`（含 P0–P2）、浏览器内的 P0 AC-4/AC-5、P3 的视图（形状、分区）、Polarion 嵌入和单点登录、性能基准（刷新、10k 元素） | 1–2 天 |
| 合计 | **约 1–1.5 周**，取决于 Spring Boot 4 的问题有多少 |

## 5. 建议

1. 本期（P4）继续锁定 2025.8.11。
2. 升级作为一个独立任务（P5 或维护期），按以下顺序做：分支上先改适配层，编译通过；然后运行全部测试，处理 Spring Boot 4 的问题；最后做完整回归。全部通过后，按本页更新 `CLAUDE.md` 中的版本说明（Spring Boot、AQL 仓库、前端依赖）。
3. 在此之前，Sirius 2025.8.x 的安全更新照常跟进（当前已是该系列最新的 2025.8.11）。

## 6. 复现

```bash
git worktree add -b spike/sirius-upgrade ../SysArchUpgrade feature/p4
# gradle/libs.versions.toml: spring-boot = "4.1.1", sirius-web = "2026.11.0"
# settings.gradle.kts: Acceleo 仓库改为 https://download.eclipse.org/acceleo/updates/releases/4.2/R202603201315
# backend/build.gradle.kts（只在演练中加）: -Xmaxerrs 2000，看到全部错误
./gradlew :backend:compileJava          # 70 个错误，9 个文件
# frontend: 依赖换成 2026.11.0 的 peerDependencies，外加 i18next 25.5.2
NPM_TOKEN=… npm install && npx tsc -b && npx vite build   # 通过
```
