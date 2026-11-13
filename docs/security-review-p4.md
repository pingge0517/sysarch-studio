# 安全评审（P4 T8）

> 2026-11-13 ｜ 范围：本仓库的后端、前端、Polarion 扩展和部署配置 ｜ 方法：逐个核对写接口的权限检查，以 Polarion 模式（不登录）实际调用验证，依赖漏洞扫描，复核已有的安全设计

## 1. 结论

| 编号 | 发现 | 严重度 | 状态 |
|---|---|---|---|
| F1 | `POST /api/v1/sync/status/resume` 没有权限检查：任何人（包括未登录者）都能恢复一个因连续失败而暂停的 Polarion 定时拉取 | 中 | **已修复**：需要该 Polarion 项目的同步权限；实测未登录返回 403，并写入审计 `ACCESS_DENIED` |
| F2 | 响应缺少 `X-Content-Type-Options: nosniff` 和 `Referrer-Policy` | 低 | **已修复**：后端每个响应都带上；nginx 和 Helm 的前端配置也加上了 |
| F3 | 前端依赖 React Router 6.26.0（Sirius 固定的版本）有已公开的开放重定向和 XSS 漏洞（npm audit：3 个 high） | 高 | **已修复**：升级到 6.30.6（仍是 6.x，Sirius 只把它声明为 peer，因此只有一份）；构建和测试通过，浏览器中验证路由正常（`/`→项目列表、`/sysarch`、编辑器） |
| F4 | **读取不需要登录**：未登录者可以通过 Sirius 的 GraphQL 读取所有模型，也可以调用导出、表格、追溯、时序图等读取接口 | 高（取决于部署环境） | **已修复**（方案 1，按推荐）：Polarion 模式下读取需要登录，见第 3 节 |
| F5 | `uuid` < 11.1.1（经 `@ObeoNetwork/react-trello`）：v3/v5/v6 在传入缓冲区时缺少边界检查 | 中 | 接受：没有可用的修复版本；该组件（看板视图）我们不使用，也不向它传入缓冲区。Sirius 升级后再看 |
| F6 | Java 依赖还没有做漏洞扫描 | — | **部分完成**：已加入 `.github/dependabot.yml`；安全警报需要仓库所有者在设置中开启，见第 5 节 |

此外确认没有问题的方面：CSRF（第 4 节）、写接口的权限（第 2 节，F1 修复之后）、文件路径、XML 外部实体、SVG 与报告的转义、单点登录凭证、求解沙箱、审计只追加。

## 2. 写接口与权限（全部）

| 接口 | 要求 | 结果 |
|---|---|---|
| Sirius GraphQL 的编辑类 mutation | 建模权限，由编辑守卫检查；未记录编辑者的输入按匿名处理并拒绝 | ✓（P2 T8） |
| Sirius `createProject` / `deleteProject` / `renameProject` | 已登录 / 管理员 / 管理员 | ✓ |
| Sirius 文档上传 `/api/graphql/upload` | 记录编辑者，按上面的规则检查 | ✓ |
| `POST /api/v1/projects/{p}/tables/{t}/actions` | 建模权限（`ProjectModels.write` 中的准入检查） | ✓ |
| `POST/PUT/DELETE /api/v1/locks…` | 建模权限；强制解锁需要强制解锁权限 | ✓ |
| `POST /api/v1/sessions`、`DELETE /sessions/{id}` | 已登录，只能关闭自己的会话 | ✓ |
| `POST /api/v1/baselines`、`DELETE /baselines/{id}` | 提交基线 / 删除基线（管理员） | ✓ |
| `POST /api/v1/branches`、`DELETE /branches/{id}` | 建分支 / 删除分支（管理员） | ✓ |
| `POST /api/v1/branches/{id}/leave` | 已登录，只释放自己的锁 | ✓ |
| `POST /api/v1/branches/{id}/merge` | 合并权限 | ✓ |
| `POST /api/v1/comments` | 评论权限（评审人及以上） | ✓ |
| `POST /api/v1/reports` | 已登录 | ✓ |
| `POST /api/v1/imports/sysmlv2`、`/imports/xmi` | 导入权限 | ✓ |
| `POST /api/v1/solve` | 建模权限（结果以用户身份写回，锁和人数上限照常检查） | ✓ |
| `POST /api/v1/sync/push`、`/sync/pull` | 该 Polarion 项目的同步权限 | ✓ |
| `POST /api/v1/links`、`DELETE /links/{id}` | 同步权限（v0.5.2 补上） | ✓ |
| `POST /api/v1/sync/status/resume` | 同步权限 | **F1，已修复** |
| `POST /api/v1/consistency/repair` | 同步权限 | ✓ |
| `POST/DELETE /api/v1/role-mappings…` | 管理角色（全局管理员） | ✓ |
| `PUT/DELETE /api/v1/project-bindings/{p}` | 管理角色 | ✓（P4 T1） |
| `POST /api/v1/webhooks/polarion` | 原始请求体的 HMAC 签名；未配置密钥时 503 | ✓ |
| `POST /api/v1/auth/login`、`/logout`、`/polarion-sso` | 登录接口本身；单点登录凭证需要签名、60 秒有效、只能用一次，只接受 Polarion 源 | ✓ |
| `POST /api/v1/export/sysmlv2`、`/export/xmi` | 只读，没有检查 | 归入 F4 |
| `GET /actuator/prometheus` | 指标令牌 | ✓（P4 T5） |

## 3. F4：读取不需要登录（已按方案 1 修复）

**实现**（`identity.ReadAccessFilter`）：Polarion 模式下，未登录访问 `/api/**` 和 GraphQL 订阅 `/subscriptions` 返回 401（JSON，提示登录方式）。例外：登录接口 `/api/v1/auth/**`、Polarion webhook（有签名校验）、Sirius 的图标 `/api/images/**`、CORS 预检；`/actuator` 不在 `/api` 下（健康检查开放，指标需要令牌）。静态页面本身不含模型数据：应用加载前先问 `/api/v1/auth/me`，未登录就显示登录页（`SignInGate`），从 Polarion 页面或工作项面板进入时经单点登录自动登录。开关 `SYSARCH_ANONYMOUS_READ`：不设置时 Polarion 模式关闭匿名读取，dev 模式开启（本机测试和压测脚本不受影响）；设为 `true` 恢复原来的行为。

**实测**（Polarion 模式，本机 Polarion 2512）：未登录时 GraphQL 401、SysML 导出 401、WebSocket 握手 401；健康检查 200、图标 200、未签名的 webhook 400（签名校验照旧）；带 Polarion 令牌（Bearer）GraphQL 200；浏览器未登录打开 `/projects` 显示登录页而不是 Sirius 的应用，经 `/polarion/sysarch-sso/start?return=/projects` 自动登录后显示项目列表和导航链接。

修复前的情况和当时的方案如下。

现状：Sirius Web 本身没有读取权限的概念，P2 T8 只对写操作做了检查。因此任何能访问 SysArch 地址的人，不登录也能读取所有模型：项目列表、图、属性、导出文件、表格、追溯和报告列表等。实测：未登录时 GraphQL 能返回项目列表，SysML 导出返回 200 和模型文本。

影响取决于部署环境：只在内网、而且所有能访问的人本来就可以看模型时，影响有限；如果模型属于保密范围，这就是数据泄露。

可选方案：

1. **Polarion 模式下所有读取都要求登录**（推荐）：未登录访问 `/api/**`（登录和健康检查除外）以及 Sirius 的 GraphQL 订阅时返回 401，前端跳到登录页（Polarion 单点登录或令牌登录）。工作量约 1–2 天：后端过滤器、前端在 Sirius 应用外加一个登录入口、验收脚本带上身份。
2. 按项目限制读取（观察者及以上才能读）：更细，但 Sirius 的 GraphQL 查询没有按项目拦截的统一入口，工作量大得多。
3. 维持现状，在部署文档中写明“只能部署在可信网络中，网络内的人都能读取所有模型”。

## 4. 已确认没有问题的方面

- **CSRF**：浏览器跨站发来的写请求会带上 `Origin`，Spring 的 CORS 检查拒绝不在白名单里的源。实测：从外部源发出、不需要预检的 `text/plain` 导入和 multipart 上传都返回 403。会话 Cookie 默认 `SameSite=Lax`；跨站部署改用 `None` 时，这一层检查仍然有效。
- **文件路径**：报告下载先按任务 ID 查库，格式在白名单内，不会拼出任意路径；图片服务只注册了一个类路径前缀。
- **XML**：XMI 导入拒绝 DOCTYPE，不解析外部 DTD 和 Schema，也不处理 XInclude（P3 T8）。报告模板不含 DOCTYPE。
- **转义**：报告由 FreeMarker 以 XHTML 输出格式自动转义；时序图 SVG 对文本做了转义；嵌入页和 Polarion 面板对 ID 先做 URL 编码再做 HTML 转义。
- **单点登录**：HMAC 签名、60 秒有效、按 nonce 只能用一次、`aud` 校验；密钥放在文件里，不出现在 Polarion 的属性日志中；只有登录接口接受 Polarion 的源。
- **框架嵌入**：`frame-ancestors` 只允许本站和 Polarion 的源，配置时只接受明确的源，不接受通配符或注入的指令。
- **求解**：表达式经过扫描、执行时命名空间为空、在独立进程中运行；生产环境使用无出站网络、根文件系统只读的 Pod（P3 T4 安全评审）。
- **审计**：PostgreSQL 触发器拒绝 UPDATE、DELETE 和 TRUNCATE，只有归档脚本能删除一年以前的记录（P2 T8）。
- **密钥**：每次提交前都检查过暂存的改动里没有令牌和密钥；`.env` 不进入版本库；`.npmrc` 只引用环境变量。

## 5. 待做：Java 依赖扫描

前端已用 `npm audit` 扫描（第 1 节 F3、F5）。Java 依赖（Spring Boot、Sirius、EMF、docx4j、openhtmltopdf 等）还没有扫描。可选：

- 启用 GitHub 仓库的 Dependabot 警报（在仓库设置中开启，不需要下载）；
- 在 CI 中加 OWASP Dependency-Check（需要下载工具和 NVD 漏洞库，最好申请一个 NVD API 密钥）；
- 用 OSV-Scanner 扫描 Gradle 依赖（下载一个约 20 MB 的工具）。

建议先启用 Dependabot，再在 CI 中加一项扫描，发现高危漏洞时让构建失败（P4 规格 §5.2）。

**已做**：`.github/dependabot.yml` 每周检查 Gradle 和 npm 依赖、每月检查 GitHub Actions；Sirius Web、Spring Boot 只建议补丁版本（升级走单独的升级任务，见 `docs/sirius-upgrade-2026.11.md`），React 不建议大版本。**仍需仓库所有者**：在 GitHub 仓库 Settings → Code security 中开启 Dependabot alerts 和 security updates；Dependabot 访问 GitHub Packages 上的 Sirius 包需要在仓库的 Dependabot secrets 中配置令牌，否则只会跳过这些包。CI 中的扫描（会让构建失败的那一种）还没有加：需要下载工具，等你决定。
