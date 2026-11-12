# 在 Polarion 中嵌入 SysArch

目标：SysArch 作为 Polarion 里的一个页面出现。用户已经登录 Polarion，打开页面就直接进入架构模型，不用再输入令牌。

## 1. 工作方式

```
Polarion 页面 ── iframe ──▶ /polarion/sysarch-sso/start?return=/?embed=1&polarionProject=…   （Polarion 扩展，在 Polarion 的登录之后）
                                   │  页面自动提交表单：一条签名的、60 秒有效的“当前用户是 alice”
                                   ▼
                      SysArch  POST /api/v1/auth/polarion-sso
                                   │  校验签名、有效期、一次性；用服务账号向 Polarion 读取 alice 的角色
                                   │  设置会话 Cookie（与令牌登录相同），303 跳到 return
                                   ▼
                      SysArch  /?embed=1&polarionProject=…&workItem=…   （嵌入页：模型、绑定的元素、各页面入口）
```

- **不经手密码和令牌**：扩展只读取 Polarion 已登录的用户名并签名；SysArch 用服务账号（`POLARION_SYNC_TOKEN`）读取该用户的角色，权限仍由 `role_mapping` 决定（P2 T8）。
- **签名**：HMAC-SHA256，密钥由 Polarion 和 SysArch 双方共享；凭证 60 秒内有效，只能用一次，`aud` 必须为 `sysarch`。格式见 `polarion-sso/src/main/java/com/example/sysarch/polarion/sso/Assertions.java`，SysArch 端的校验见 `identity.PolarionSso`。
- **跨站限制**：只有登录接口接受来自 Polarion 源的表单提交（仅 POST，不带凭证）；Polarion 页面上的脚本不能借用户的 Cookie 调用 SysArch 的其他接口。
- **框架嵌入**：SysArch 的页面带 `Content-Security-Policy: frame-ancestors 'self' <Polarion 源>`，其他站点嵌入时得到空白框架（防点击劫持）。

## 2. 构建扩展

```bash
./gradlew :polarion-sso:polarionExtension
# 产物：polarion-sso/build/extension/com.example.sysarch.sso/ 和 polarion-sso/build/distributions/com.example.sysarch.sso.zip
```

扩展只用 JDK 和 Servlet API（由 Polarion 的 Tomcat 9 提供），不向 Polarion 带入任何第三方库。

## 3. 安装到 Polarion（由 Polarion 管理员操作）

**推荐用安装脚本**（P4 T2）：在构建好扩展后，以管理员身份在 PowerShell 中运行

```powershell
./polarion-sso/install.ps1 -SysArchUrl https://sysarch.example.com [-PolarionHome C:Polarion] [-Restart]
./polarion-sso/install.ps1 -Uninstall [-PolarionHome C:Polarion] [-Restart]   # 卸载
```

脚本先检查参数，再备份 `polarion.properties`（`.bak-sysarch-<时间>`），然后完成下面第 1、2、3、5 步：复制扩展（替换旧版本）、缺少时生成密钥文件、在一对标记注释之间写入两个属性（重复运行只会替换这一段，原有的 `sysarch.sso.*` 行会被清掉，文件的编码和换行符保持不变）、删除 `.config`。它**不会修改 Apache 配置**，只检查 `frame-src` 是否已允许 SysArch，没有时打印需要加的内容（第 4 步由管理员手工完成）。它也不会显示密钥，只提示密钥文件的位置。Polarion 运行时 `.config` 里的文件被占用，所以：Polarion 服务正在运行时，不加 `-Restart` 脚本会在改动任何文件之前拒绝；加 `-Restart` 时先停服务，改完再启动。只认程序路径在 `-PolarionHome` 下的那个 Polarion 服务。2026-11-12 在本机 Polarion 2512 上实测：首次运行时脚本还没有先停服务，删除 `.config` 失败（文件被占用），已按上述方式修正；修正后的一次运行完成全部步骤，Polarion 重启后扩展正常加载，整页 `/polarion/sysarch-sso/page?project=sysarch` 单点登录成功，非法项目 ID 返回 400。

手工安装的步骤如下：


1. 把 `com.example.sysarch.sso` 文件夹复制到 `<Polarion 安装目录>/polarion/extensions/`。
2. 把密钥（与 SysArch 的 `SYSARCH_POLARION_SSO_SECRET` 相同，至少 32 个随机字符，例如 `openssl rand -hex 32`）写进一个文件，例如 `<Polarion 安装目录>/polarion/configuration/sysarch-sso.key`，只让 Polarion 服务账号可读。
3. 在 `<Polarion 安装目录>/polarion/configuration/polarion.properties` 中加入：

   ```properties
   # SysArch 单点登录：存放共享密钥的文件（不要把密钥本身写在这里：Polarion 启动时会把所有属性写进日志）
   sysarch.sso.secretFile=C:/Polarion/polarion/configuration/sysarch-sso.key
   # 浏览器访问 SysArch 外壳的地址
   sysarch.sso.url=https://sysarch.example.com
   ```

   也可以用 Polarion 服务的环境变量 `SYSARCH_SSO_SECRET` 提供密钥。
4. **允许 Polarion 页面嵌入 SysArch**：Polarion 自带的 Apache 给每个页面都设置了 `Content-Security-Policy: default-src 'self' …`，没有 `frame-src` 时 Polarion 页面只能嵌入 Polarion 自己。在 `<Polarion 安装目录>/bundled/apache/conf/extra/polarion.conf` 的这条 `Header set Content-Security-Policy` 里，在 `frame-ancestors` 前加一段（换成 SysArch 外壳和 Sirius Web 的地址）：

   ```
   frame-src 'self' https://sysarch.example.com https://sirius.sysarch.example.com;
   ```

   修改前先备份，保留文件原来的换行符（CRLF）；`bin/httpd.exe -t` 检查语法后重启 Apache2Polarion 服务。其余策略不变。
5. 删除 `<Polarion 安装目录>/data/workspace/.config`（Polarion 新增或更换插件后需要重建 OSGi 缓存），然后重启 Polarion 服务。日志里应有 `added webapp 'WebappContribution[webappName: polarion/sysarch-sso` 且没有 `FATAL … /polarion/sysarch-sso`。
6. 验证：已登录 Polarion 时打开 `https://<polarion>/polarion/sysarch-sso/start?return=%2F%3Fembed%3D1%26polarionProject%3D<项目>`，页面应跳转到 SysArch 的嵌入页并显示已登录的用户名。未登录时先出现 Polarion 的登录页，登录后继续跳转。

卸载：删除扩展文件夹、两行属性、密钥文件和 `frame-src` 这一段，再删除 `.config` 并重启 Polarion 和 Apache2Polarion。

安装时踩到的坑（已在扩展中处理）：

- `MANIFEST.MF` 必须依赖 `com.polarion.platform`：web.xml 里的 `DoAsFilter` 在这个插件里，缺少时 Web 应用启动失败（`ClassNotFoundException: com.polarion.portal.tomcat.servlets.DoAsFilter`）。
- Polarion 的表单登录成功后，会以登录时的 POST 重放原请求，所以 `/start` 同时接受 GET 和 POST（否则登录后得到 405）。

## 4. SysArch 端配置

| 环境变量 | 说明 |
|---|---|
| `SYSARCH_POLARION_SSO_SECRET` | 与 Polarion 的 `sysarch.sso.secret` 相同；不设则单点登录关闭（503），令牌登录照常可用 |
| `POLARION_SYNC_TOKEN` | 服务账号令牌，用来读取登录用户的角色（也用于定时拉取） |
| `SYSARCH_SCHEDULED_PULL` | `false` 时不做定时拉取，即使设置了服务账号令牌（令牌只用于单点登录时） |
| `SYSARCH_SSO_ORIGINS` | 允许提交登录表单的 Polarion 源，例如 `https://polarion.example.com`（默认 `http://localhost`） |
| `SYSARCH_FRAME_ANCESTORS` | 允许嵌入 SysArch 的源；默认取 `POLARION_BASE_URL` 的源 |
| `SYSARCH_COOKIE_SAMESITE` | `Lax`（默认，Polarion 与 SysArch 同站，例如同一公司域名下的两个主机）；不同站时设 `None`，并同时设 `SYSARCH_SECURE_COOKIE=true` |
| `SYSARCH_COOKIE_PARTITIONED` | `true` 时会话 Cookie 带 `Partitioned`（CHIPS），按嵌入它的站点分区：浏览器拦截第三方 Cookie 时仍能在框架里登录。不同站部署时建议与 `None`、`Secure` 一起使用 |
| `SYSARCH_POLARION_PROJECTS_<ID>` | Polarion 项目对应的模型的默认值。P4 起管理员可在外壳的“角色与审计”页设置绑定，立即生效、不用重启，并覆盖这里的值（见下文） |

**Polarion 项目与模型的绑定**（P4 T1）：外壳“角色与审计”页的“Polarion 项目与模型”。管理员选择模型并绑定，立即生效（Polarion 页面、工作项面板、同步都按新绑定）；一个模型只能绑定一个 Polarion 项目；每次修改写审计 `PROJECT_BINDING_CHANGE`。删除模型时它的绑定一并删除。已经登录的用户在新绑定的 Polarion 项目中的角色要在下次登录后生效（会话只保存已绑定项目的角色）。接口：`GET/PUT/DELETE /api/v1/project-bindings[/{polarionProject}]`。

外壳的 nginx 也要发送相同的 `frame-ancestors`：本地配置见 `frontend/nginx.conf`，Helm 用 `embed.polarionOrigin`。

**同站部署**（推荐）：例如 `polarion.example.com` 与 `sysarch.example.com`。Cookie 用 `Lax` 即可在 iframe 中生效。

**不同站部署**（Polarion 与 SysArch 不在同一注册域下）：设 `SYSARCH_COOKIE_SAMESITE=None`、`SYSARCH_SECURE_COOKIE=true`（需要 HTTPS）、`SYSARCH_COOKIE_PARTITIONED=true`，并把 Polarion 的源加入 `SYSARCH_SSO_ORIGINS` 和 `SYSARCH_FRAME_ANCESTORS`。2026-11-12 在本机用 Chrome 实测（嵌入方为 `http://127.0.0.1:8099`，SysArch 为 `http://localhost:3000`，浏览器视为两个站点）：

| Cookie 设置 | 登录请求 | 框架内调用接口 | 结论 |
|---|---|---|---|
| `SameSite=Lax`（默认） | 303 | 401 | 跨站框架里没有 Cookie，显示未登录 |
| `None; Secure` | 303 | 已登录 | 浏览器允许第三方 Cookie 时可用 |
| `None; Secure; Partitioned` | 303 | 已登录 | 可用；同时在顶层打开 SysArch 时仍是未登录，说明 Cookie 按嵌入站点分区，拦截第三方 Cookie 的浏览器也能用 |

未测：在浏览器设置里关闭第三方 Cookie 后的情况（那是用户自己的浏览器设置），以及 Firefox、Safari。

## 5. 在 Polarion 中加入页面

**最简单的做法**（P4 T2）：扩展自带一个整页，地址是 `/polarion/sysarch-sso/page?project=<Polarion 项目 ID>`。页面顶部有一行“SysArch 架构 · 项目 · 在新标签页中打开”，下面是整页的 SysArch。把这个地址加到 Polarion 的项目快捷方式或导航里即可，不需要建页面。项目 ID 只能包含字母、数字和 `_ - .`，其他内容会被拒绝（400）。

也可以在项目里新建一个 LiveReport 页面（文档与页面 → 新建 → LiveReport Page），从部件目录加一个“Script - Block”部件，内容如下（把 `sysarch` 换成 Polarion 项目 ID），应用后保存：

```html
<iframe src="/polarion/sysarch-sso/start?return=%2F%3Fembed%3D1%26polarionProject%3Dsysarch"
        style="width:100%;height:85vh;border:0" title="SysArch 架构模型"></iframe>
```

指向某个 Work Item 时在 `return` 中加 `workItem`：`return=/?embed=1&polarionProject=sysarch&workItem=SYS-12`（URL 编码后放进 `return`）。嵌入页会打开与该 Work Item 绑定的模型元素；没有绑定时只打开模型。

### Work Item 表单上的“SysArch 架构”面板

扩展里还带一个 Work Item 表单扩展 `sysarchArchitecture`：在 Work Item 表单上显示该 Work Item 的嵌入页（框架高 260 px，下方有“在新标签页中打开 SysArch”）。未保存的 Work Item 只显示一行提示。

启用方法：Polarion 管理 → 工作项 → 表单配置 → 表单布局，为需要的类型（例如“架构元素”）新建或编辑布局，在自定义字段面板之后加一行：

```xml
<extension id="sysarchArchitecture" label="SysArch 架构" />
```

这个表单扩展实现的是 Polarion 自己的接口，只能用 Polarion 的 jar 编译：构建时找到 Polarion（`POLARION_HOME`、`-Ppolarion.home`，或本机的 `C:/Polarion/polarion`）才会把它编进扩展，并在清单里加上 `Guice-Modules`；找不到时（例如 CI）构建的扩展不带面板，其余功能不变。构建日志会写明 `with` 或 `without the Work Item form panel`。

## 6. 嵌入页

`/?embed=1&polarionProject=<Polarion 项目>[&workItem=<ID>]`：

- 顶部显示登录用户；
- 显示 Polarion 项目对应的模型；如果带了 Work Item，再显示它绑定的元素和绑定状态；
- 提供入口：打开模型元素（Sirius Web，自动选中该元素）、打开模型、分配矩阵、需求追踪、基线与差异、报告。这些入口都在框架内打开。

接口：`GET /api/v1/embed/target?polarionProject=&workItem=`，需要登录，并需要该 Polarion 项目的读取权限。

## 7. 已验证 / 未验证

- 已验证（2026-11-12）：单元测试（`PolarionSsoTest`、`AssertionsTest`、前端 `embed/api.test.ts`）；本机实际运行的后端，用扩展自己的签名代码生成凭证，Polarion 接口用本地桩代替：登录 303 并带上 Cookie 和角色、重放 401、其他源 403、Polarion 源调用其他接口 403、嵌入目标接口未登录 401、`frame-ancestors` 响应头。
- 已验证（2026-11-12，本机 Polarion 2512，按第 3 节安装）：已登录 Polarion 的浏览器打开 `/polarion/sysarch-sso/start` 后直接进入 SysArch 嵌入页，显示 System Administrator，没有输入任何令牌；在 Polarion 页面（`http://localhost`）中用 iframe 打开同一地址：Polarion 起始页 → 表单提交到 SysArch（`/api/v1/auth/polarion-sso`）→ 嵌入页加载并读取用户和目标（均 200），用户确认框架内显示嵌入页，点“打开模型（Sirius Web）”后 Sirius Web 也在框架内打开。测试时 SysArch 的服务账号令牌临时用了个人令牌，并映射到一个不存在的 Polarion 项目，避免定时拉取碰到已有的绑定；测试项目和记录事后已删除。
- 已验证（2026-11-12）Work Item 面板：在 sysarch 项目“架构元素”类型的表单布局中加入面板，新建一个测试工作项，并在 SysArch 中把它临时绑定到一个元素：打开该工作项，表单上出现“SysArch 架构”面板（865×262 的框架），框架内依次完成登录、加载嵌入页、读取用户和目标（均 200），目标为绑定的元素且已在模型中定位；用户确认面板显示正常。测试工作项、临时绑定和临时项目事后已删除，表单布局保留。
- 已验证（2026-11-12）不同站部署的 Cookie 行为：见第 4 节的表格。
- 已验证（2026-11-12）正式页面：在 sysarch 项目建了 LiveReport 页面“SysArch 架构”（Default Space），只含一个“Script - Block”部件，内容为第 5 节的 iframe；重新打开页面后框架显示 SysArch，SysArch 日志记录了单点登录。
- 未验证：关闭第三方 Cookie 的浏览器设置、Firefox 和 Safari。
