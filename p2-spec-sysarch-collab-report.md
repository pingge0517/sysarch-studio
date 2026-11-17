# P2 规格书：协同、报告生成与千元素性能（Claude Code 可执行版）

> 版本 v0.3.0 ｜ 2026-11-05 ｜ 周期 **12 周** ｜ 目标：**单项目几十人正式使用**
>
> 前置：P1 的 P1-AC-1 至 P1-AC-7 全部通过，tag `v0.2.0`。
>
> **P2 的第一条纪律：元模型冻结。** 本期不许改 `sysarch.ecore`。所有新增能力都落在平台层（锁、分支、报告、导入、审计），一旦发现必须改元模型才能实现，说明需求超出 P2 边界，退回 P3 评估。

---

## 0. 执行前置

```bash
git checkout -b feature/p2 release/p1
./gradlew build && ./gradlew :backend:test
./tests/acceptance/run-p0.sh && ./tests/acceptance/run-p1.sh
```

三条验收脚本全绿再开工。P2 触碰的是并发与数据一致性，基线不干净会把问题伪装成"偶发 bug"。

---

## 1. P2 范围

### 1.1 六件事

| 编号 | 交付 | 判定价值 |
|---|---|---|
| P2-1 | 编辑锁（项目锁 + 元素租约锁） | 几十人同时在线不互相覆盖 |
| P2-2 | 分支与三方合并 | 变更可隔离、可评审、可回退 |
| P2-3 | 报告生成（规格书 / ICD / 追溯报告） | 模型能一键变交付文档 |
| P2-4 | SysML v2 / SysON 模型导入 | 外部模型能进来，不是只出不进 |
| P2-5 | 性能治理（1 万元素、并发 30 人） | 大项目不卡 |
| P2-6 | 权限映射、审计、部署与备份 | 能上生产 |

### 1.2 不做

- 不做实时协同（CRDT / OT / 字符级合并）。**分支 + 锁 + 行级合并**就是本期上限。
- 不做报告的可视化设计器（模板是文件，改模板发版）。
- 不做参数图、状态机、XMI（P3）。
- 不做多租户。一个部署服务一个项目群。

---

## 2. 并发模型（三把锁，各管一段）

### 2.1 分层设计

```text
项目层：编辑会话（Session）——同一项目同一时刻最多 N 个并发写会话，默认 30
元素层：租约锁（Lease）——粒度到 elementId，默认 90 秒，心跳续租
存储层：乐观锁（Version）——每次写带 baseRev，冲突返回 409
```

**为什么三层都要**：只用元素锁，改一棵子树要锁上百个元素，锁表爆炸；只用乐观锁，冲突率高到用户天天点"合并"。三层叠加后，正常编辑走元素锁，批量操作走会话锁，兜底靠 version。

### 2.2 租约锁规则

| 规则 | 说明 |
|---|---|
| 获取 | `POST /locks` 带 elementId 与 leaseSeconds（30–300，默认 90） |
| 续租 | 客户端每 45 秒 `PUT /locks/{id}`，超时未续自动释放 |
| 释放 | 保存成功或用户关闭编辑器时显式释放；异常退出靠超时回收 |
| 抢占 | 不允许抢占。他人持锁时返回 423，前端进入只读并显示持锁人 |
| 批量 | 一次最多锁 50 个元素，超过走会话锁，由服务端串行化 |

### 2.3 撤销"锁"的两个例外

1. **管理员强制释放**：`DELETE /locks/{id}?force=true`，必须写审计。
2. **元素已删除**：锁随元素删除自动失效，不额外处理。

### 2.4 DDL `V3__collab.sql` —— `[COPY]`

```sql
create table edit_lock (
  id            varchar(64) primary key,
  project_id    varchar(64) not null,
  element_id    varchar(64) not null,
  owner_user    varchar(128) not null,
  lease_seconds integer not null default 90,
  acquired_at   timestamptz not null default now(),
  expires_at    timestamptz not null,
  unique (project_id, element_id)
);
create index idx_lock_expire on edit_lock(expires_at);

create table edit_session (
  id          varchar(64) primary key,
  project_id  varchar(64) not null,
  user_id     varchar(128) not null,
  opened_at   timestamptz not null default now(),
  closed_at   timestamptz,
  client_info varchar(200)
);

-- 回收任务：每分钟清理过期锁
```

---

## 3. 分支与三方合并

### 3.1 分支模型（刻意做"轻"）

```text
trunk（主干，始终可编辑）
 └─ branch/<name>（从某个基线 fork 出来的变更线）
```

- 分支**不是**全量模型复制，而是"基线快照 + 变更集（changeset）"。存储上只记 `baseline_id` 与后续差异。
- 分支创建必须选一个已存在的基线（P1 的 `/baselines`），**不允许从"当前未提交状态"拉分支**。
- 同一用户可以有多条分支；同一分支同时只允许一个写会话。

### 3.2 合并算法（按 element_id 三方对齐）

```text
输入：base（分叉点基线）、theirs（分支）、ours（目标）
步骤：
1. 以 element_id 做外连接，得到 added / removed / both-present 三类
2. added：双方都加 → 若有相同 element_id 且内容不同，记 CONFLICT(DUPLICATE_ID)
3. removed：仅一方删 → 直接删；双方都删 → 删除幂等
4. both-present：逐字段比较
   - 只有一方改 → 取改动方
   - 双方改且值相同 → 取任意一方
   - 双方改且值不同 → 查字段归属矩阵（P0 第 6.2 节）：
       * 归属明确的字段（如 name）→ 按 POLARION_WINS / MODEL_WINS 策略裁决
       * 归属不明的字段 → 记 CONFLICT，交人处理
5. 输出 MergeResult{merged, conflicts[], stats}
```

**关键约束**：合并结果必须整体可回滚——先算完整 `MergeResult`，用户确认后才落库；落库失败则不动原数据。

### 3.3 冲突上限保护

单次合并冲突超过 **50 条**时，拒绝自动合并，改为"按元素逐条处理"界面。经验值：超过 50 条说明分支分叉太久，应该 rebase 而不是硬合。

### 3.4 DDL（接 2.4）

```sql
create table branch (
  id           varchar(64) primary key,
  project_id   varchar(64) not null,
  name         varchar(200) not null,
  base_baseline varchar(64) not null references baseline(id),
  created_by   varchar(128) not null,
  created_at   timestamptz not null default now(),
  status       varchar(16) not null default 'OPEN',  -- OPEN / MERGED / ABANDONED
  unique (project_id, name)
);

create table changeset (
  id          varchar(64) primary key,
  branch_id   varchar(64) not null references branch(id) on delete cascade,
  element_id  varchar(64) not null,
  op          varchar(16) not null,   -- ADD / MODIFY / DELETE
  payload     jsonb not null,
  created_at  timestamptz not null default now()
);
```

---

## 4. 报告生成

### 4.1 架构：模板 + 数据抽取 + 渲染器

```text
模板(.ftl) → 数据抽取器(ReportContext) → HTML → docx4j → .docx
                                     └→ 直接输出 .html
```

**不做**可视化报表设计器；模板是受版本管理的文件，改模板走发版流程。

### 4.2 三类模板（P2 必须交付）

| 模板 id | 输出 | 必备内容 |
|---|---|---|
| `srs` | 系统规格书 | 系统概述、分层架构（每层一节）、接口清单、需求追溯表、附录（元素明细） |
| `icd` | 接口数据清单 | 接口逐条：名称、协议、数据类型、源端、目标端、所属层、关联需求 |
| `trace-report` | 追溯报告 | 需求总数、已满足、已验证、未满足清单、覆盖率 |

### 4.3 模板变量契约 —— `[COPY]` 写入 `docs/report-context.md`

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

### 4.4 异步与幂等

- 生成是异步任务：`POST /reports` 返回 `jobId`，前端轮询 `GET /reports/{jobId}`。
- 同一 `(projectId, baselineId, templateId)` 的重复请求复用缓存结果（24 小时），文件存对象存储。
- 生成失败必须给出**具体缺什么**（如"3 条需求无 satisfiedBy"），不能只报 500。

### 4.5 OpenAPI 追加 —— `[COPY]`

```yaml
  /locks:
    post:
      summary: 申请元素锁
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [projectId, elementIds, ownerUser]
              properties:
                projectId: { type: string }
                elementIds:
                  type: array
                  items: { type: string }
                  maxItems: 50
                ownerUser: { type: string }
                leaseSeconds: { type: integer, default: 90, minimum: 30, maximum: 300 }
      responses:
        "201": { description: 全部获取成功 }
        "423": { description: 存在被他人持有的元素，响应体列出冲突 elementId 与持锁人 }
    delete:
      summary: 释放锁
      parameters:
        - name: elementId
          in: query
          required: true
          schema: { type: string }
        - name: force
          in: query
          schema: { type: boolean, default: false }

  /branches:
    post:
      summary: 从基线创建分支
      responses:
        "201": { description: 分支已创建 }
    get:
      summary: 列出项目分支

  /branches/{branchId}/merge:
    post:
      summary: 合并到主干（先返回预演结果）
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                mode: { type: string, enum: [PREVIEW, APPLY] }
                strategy: { type: string, enum: [MODEL_WINS, POLARION_WINS] }
      responses:
        "200":
          description: 预演结果或应用结果
          content:
            application/json:
              schema:
                type: object
                properties:
                  merged: { type: integer }
                  conflicts:
                    type: array
                    items:
                      type: object
                      properties:
                        elementId: { type: string }
                        field: { type: string }
                        base: { type: string }
                        theirs: { type: string }
                        ours: { type: string }

  /reports:
    post:
      summary: 提交报告生成任务
      responses:
        "202":
          description: jobId
    get:
      summary: 查询任务状态与下载地址

  /imports/sysmlv2:
    post:
      summary: 导入 SysML v2 文本或 SysON 模型
      requestBody:
        content:
          text/plain:
            schema: { type: string }
      responses:
        "202": { description: 导入任务已入队，返回 jobId }
```

---

## 5. 导入：只收结构，不收语义

### 5.1 范围

| 支持 | 不支持（明确报错） |
|---|---|
| `part def` / `part` / `port` / `connect` / `requirement` | 参数、状态机、动作、行为 |
| `doc` 注释映射为 description | 任意 fUML 语义 |
| 元素层级与名称 | 自定义 KerML 语法扩展 |

### 5.2 导入流程

```text
1) 解析文本 → 中间 AST（不直接建模型）
2) 预检：遇到不支持的语法，收集位置清单，直接失败（不做"跳过并继续"，避免半成品模型）
3) 映射到 sysarch 元模型，无法映射的标 UNMAPPED 并在报告中列出
4) 事务内批量建元素，全成功或全失败
5) 导入后自动跑一遍一致性校验（R1–R6）
```

**禁止**：导入时自动创建 Polarion Work Item。导入只进模型，绑定仍由人显式做。

---

## 6. 性能治理

### 6.1 指标（不达标不予发布）

| 场景 | 指标 |
|---|---|
| 1 万元素打开 IBD | < 3s |
| 单次增量刷新（500 元素图） | P95 < 2s（P0 已有） |
| 30 人并发编辑同一项目 | 无锁超时、无 5xx，P99 写入 < 500ms |
| 分配矩阵 50×50 | 首屏 < 1.5s |
| 报告生成（1 万元素 SRS） | < 60s |
| 合并预演（200 变更） | < 10s |

### 6.2 六个手段（按性价比排序）

1. **懒加载**：图只加载可视区节点，滚动时增量取。
2. **元素索引**：`(project_id, layer)`、`(project_id, element_type, name)` 两个复合索引。
3. **关系预计算**：连接关系在写入时维护邻接表，查询不做全表遍历。
4. **批量写**：保存时把同一事务的元素变更合并为一次批量 upsert。
5. **diff 异步**：基线 diff 与合并预演走异步任务，不阻塞请求。
6. **结果缓存**：报告与矩阵结果按 `(projectId, baselineId)` 缓存。

**不做**的优化：垂直分库、读写分离、Redis 缓存。P2 规模（1 万元素、30 人）用不上，加了只是增加运维负担。

---

## 7. 权限、审计与部署

### 7.1 权限映射（不透传业务角色，只透传身份）

| Polarion 角色 | 工具权限 |
|---|---|
| 项目管理员 | 全量 + 强制解锁 + 删除分支 |
| 架构师 | 建模、提交基线、建分支、合并 |
| 评审人 | 只读 + 评论（不建模） |
| 观察者 | 只读 |

映射表落库（`role_mapping`），**不写死在代码里**，因为各项目角色命名不一致。

### 7.2 审计

`audit_log` 记录：谁、何时、对哪个元素、做了什么（含强制解锁、合并、删除基线）。保留 365 天，按项目分区。审计日志**只增不删**。

### 7.3 部署

- Helm Chart 三件套：backend / frontend / postgres（或用集群已有 PG）。
- 备份：PG 每日全量 + WAL 归档，保留 30 天；对象存储中的报告文件保留 90 天。
- 灰度：新版本先对 1 个项目开放一周，观察错误率与性能，再全量。

---

## 8. 任务分解（T1–T9）

### T1 租约锁服务｜2 人 × 7 天

```text
1) 按第 2.4 节建表，实现 /locks 的申请、续租、释放、强制释放。
2) 过期回收用数据库定时任务（每分钟），不依赖应用内存。
3) 批量申请（≤50）在一个事务内处理：任一元素被占则整体失败并返回冲突清单。
4) 前端：进入编辑时申请锁，45 秒心跳，失锁自动切只读并提示持锁人。
验收：两个客户端同时编辑同一元素，第二个返回 423；停心跳 90 秒后锁自动释放。
```

### T2 编辑会话与并发上限｜1 人 × 4 天

```text
1) 实现 edit_session 的开启与关闭，超时 30 分钟无操作自动关闭。
2) 并发写会话上限默认 30，超出返回 429 并提示"当前项目编辑人数已满"。
3) 会话与锁解耦：会话只管人，锁只管元素。
验收：压测 30 并发写入，无 5xx，P99 < 500ms。
```

### T3 分支与变更集｜2 人 × 8 天

```text
1) 按第 3.4 节建 branch 与 changeset 表；分支只从基线 fork。
2) 所有写操作在分支上记录 changeset（ADD/MODIFY/DELETE + payload）。
3) 分支切换在前端表现为"工作区切换"，切走时强制释放该分支的全部元素锁。
验收：在分支改 5 个元素后回到主干，主干数据不变；changeset 恰好 5 条。
```

### T4 三方合并｜2 人 × 10 天

```text
1) 严格按第 3.2 节五步实现 merge，输出 MergeResult 结构。
2) PREVIEW 模式只算不写；APPLY 模式先落临时表，校验通过后单事务提交。
3) 字段冲突按字段归属矩阵裁决，矩阵外字段记 CONFLICT。
4) 冲突 > 50 条直接拒绝自动合并，返回需要逐条处理的清单。
验收：构造四类场景（双方改同字段、一方删一方改、双方加同 ID、无冲突），结果符合预期。
```

### T5 报告引擎与三模板｜2 人 × 10 天

```text
1) 实现 ReportContext 抽取器，严格按第 4.3 节变量契约输出；禁止模板内做业务判断。
2) 落盘三份 FreeMarker 模板（srs / icd / trace-report），HTML 版先做，再经 docx4j 转 Word。
3) 异步任务 + 24 小时缓存；生成失败必须列出缺失项。
4) 报告中的统计（覆盖率 = 已满足需求 / 总需求）由抽取器计算，模板只引用。
验收：演示数据生成三份报告，SRS 不少于 12 页，ICD 的接口条数与模型一致。
```

### T6 SysML v2 导入｜1 人 × 7 天

```text
1) 实现第 5.2 节五步流程，先落中间 AST 再建模型。
2) 不支持语法必须整批失败并给出 file:line 清单，禁止部分导入。
3) 导入后自动触发一致性校验，结果附在导入报告里。
验收：用 P1 导出的文本回灌，元素数与导出前一致；故意插入参数语法，导入失败并定位到行。
```

### T7 性能优化｜2 人 × 8 天

```text
1) 按第 6.2 节六手段逐项实施，每实施一项跑一次基准，记录在 build/perf/。
2) 建 perf 基准脚本：1 万元素 fixture 的打开、刷新、矩阵、报告四项耗时。
3) CI 加入阈值断言，超过 6.1 节指标即失败。
验收：四项指标全部达标，且比 P1 版本不劣化。
```

### T8 权限、审计与运维｜1 人 × 6 天

```text
1) 实现 role_mapping 与权限拦截器；四类角色按第 7.1 节授权。
2) audit_log 覆盖强制解锁、合并、删除基线、报告生成四类敏感动作。
3) 落盘 Helm Chart 与备份脚本；写 runbook（启动、备份、恢复、回滚）。
验收：用评审人账号尝试建模被拒且记审计；恢复演练从备份拉起一个可用实例。
```

### T9 三十人压测与回归｜全员 × 8 天

```text
1) 脚本模拟 30 人：各持不同元素编辑、偶发同元素冲突、周期性打基线。
2) 跑 P0、P1 全部验收脚本，确认无回归。
3) 输出压测报告：错误率、P99、锁等待时长、内存与连接池水位。
验收：错误率 < 0.5%，无死锁，内存无持续增长。
```

---

## 9. 验收清单

| 编号 | 验收动作 | 通过标准 |
|---|---|---|
| P2-AC-1 | 两人同时编辑同一元素 | 第二人进入只读，显示持锁人 |
| P2-AC-2 | 强杀客户端后 90 秒 | 锁自动释放，他人可编辑 |
| P2-AC-3 | 分支改 5 元素后合并 | 预演列出 5 项，应用后主干更新 |
| P2-AC-4 | 构造 1 个字段冲突 | 冲突列出 elementId 与字段名，不静默覆盖 |
| P2-AC-5 | 生成 SRS | 12 页以上，含三层架构与追溯表 |
| P2-AC-6 | 导出文本回灌 | 元素数一致 |
| P2-AC-7 | 1 万元素打开 IBD | < 3s |
| P2-AC-8 | 30 人压测 30 分钟 | 错误率 < 0.5%，无 5xx |
| P2-AC-9 | 回归 P0、P1 | 全部绿 |

---

## 10. 风险与回滚

| 风险 | 判据 | 处置 |
|---|---|---|
| 锁表膨胀 | `edit_lock` 行数持续超过在线人数 × 20 | 检查客户端是否漏释放；把续租改为服务端托管 |
| 合并产生脏数据 | APPLY 后元素数与预期不符 | 合并前自动打临时基线，回滚即恢复该基线 |
| 报告模板成为第二套业务逻辑 | 模板里出现 if/else 判断 | 强制评审：模板只允许 loop 与变量插值，判断逻辑退回抽取器 |
| 导入触发半成品模型 | 导入后元素数少于预期且无报错 | 已设计为整批失败；若线上出现，立即禁用导入入口 |
| 性能优化改坏一致性 | 缓存与索引导致读到旧值 | 每个优化项必须配一条一致性测试；缓存失效走模型版本号 |

**三个回滚点**：锁（可关，退回"单人编辑"）、分支（可关，退回主干直编）、报告（可关，退回手工整理）。三者独立，按需回退，不必整体下架。

---

## 11. 交付物

| 交付物 | 形态 |
|---|---|
| 锁 / 会话 / 分支 / 合并服务 | 源码 + 测试 |
| 三份报告模板 | `.ftl` 文件 + 渲染器 |
| SysML v2 导入器 | 源码 + 导入报告 |
| 性能基准与 CI 阈值 | 脚本 + 报告 |
| Helm Chart + runbook | 部署资产 |
| 压测报告 | 文档 |

---

**下一步：P3 规格书（参数图、行为视图、XMI 互导）。P2 验收全绿后再启动。**
