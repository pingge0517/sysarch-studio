# 监控、日志与磁盘（P4 T5）

面向运维人员。内容：指标从哪里取、哪些指标要告警、日志和报告文件的上限、Polarion 侧的日志建议。2026-11-12 本机 C 盘一度被日志写满（构建失败、Polarion 日志 5 GB），本节的上限和告警就是为了不再出现这种情况。

## 1. 指标

后端在 `/actuator/prometheus` 提供 Prometheus 格式的指标，**只接受携带指标令牌的请求**：

```
Authorization: Bearer <SYSARCH_METRICS_TOKEN>
```

- 未设置 `SYSARCH_METRICS_TOKEN`：该地址返回 404（相当于不存在）。
- 令牌错误或缺失：401。
- `/actuator/health` 不受影响，探针和验收脚本照常使用。

这样做的原因：nginx 和 Sirius 的入口都会把 `/actuator` 转发给后端，不加令牌的话指标会对外公开；改用单独的管理端口又会把 `/actuator/health` 一起移走。

Helm：把 `SYSARCH_METRICS_TOKEN` 放进 `backend.existingSecret`，设置 `monitoring.enabled: true`，chart 会创建 ServiceMonitor（从同一个 Secret 读取令牌）和 PrometheusRule。这需要集群里装有 Prometheus Operator。

### 1.1 SysArch 自己的指标

每次抓取时从数据库读取，不在内存中保存状态：

| 指标 | 含义 |
|---|---|
| `sysarch_sync_schedules_paused` | 因连续失败而暂停的 Polarion 定时拉取数量 |
| `sysarch_edit_sessions_open` | 正在编辑的会话数 |
| `sysarch_locks_held` | 当前持有的元素锁数量（已过期的不算） |
| `sysarch_report_jobs_waiting` | 排队或正在生成的报告任务数 |
| `sysarch_import_jobs_failed_24h` | 最近 24 小时失败的导入任务数 |

查询失败时值为 NaN，不会让整次抓取失败。

### 1.2 常用的通用指标

- 请求延迟：`http_server_requests_seconds_bucket`（已开启直方图，可以算 P99）。
- JVM：`jvm_memory_used_bytes`、`jvm_gc_live_data_size_bytes`（GC 后的存活数据，判断内存时用它，不要看堆的瞬时占用）、`jvm_threads_live_threads`。
- 磁盘：`disk_free_bytes` 和 `disk_total_bytes`，统计的是报告目录（`SYSARCH_REPORT_DIR`）所在的卷。
- 数据库连接池：`hikaricp_connections_active`、`hikaricp_connections_pending`。

## 2. 告警

PrometheusRule 中的规则（阈值在 Helm 的 `monitoring.*` 中设置）：

| 告警 | 条件 | 级别 |
|---|---|---|
| SysArchSlowRequests | 请求延迟 P99 超过 0.5 s，持续 5 分钟（不含 `/actuator`） | warning |
| SysArchSyncPaused | 有定时拉取被暂停超过 1 分钟 | warning |
| SysArchHeapHigh | GC 后存活数据超过最大堆的 75%，持续 10 分钟 | warning |
| SysArchDiskLow | 报告卷剩余空间低于 15%，持续 10 分钟 | critical |
| SysArchReportsPiling | 等待中的报告任务超过 10 个，持续 15 分钟 | warning |

处理方式：

- 同步暂停：查看“验证”面板的说明，修复原因后调用 `POST /api/v1/sync/status/resume`，或手动拉取一次（成功后自动恢复）。
- 磁盘不足：先归档后清理（见第 4 节），不要直接删除正在写入的文件。
- 内存偏高：按 P2 T9 的经验，先 `jcmd <pid> GC.run` 再 `GC.heap_info` 确认 GC 后的实际占用，再决定是否扩容。

## 3. 后端日志和报告文件的上限

| 设置 | 默认值 | 说明 |
|---|---|---|
| `SYSARCH_LOG_FILE` | 空 | 为空时只输出到控制台（容器内的默认做法）；设置后同时写入该文件 |
| `SYSARCH_LOG_FILE_SIZE` | 50MB | 单个日志文件达到这个大小就滚动，旧文件压缩为 `.gz` |
| `SYSARCH_LOG_TOTAL_CAP` | 2GB | 所有日志文件的总上限，超过时删除最旧的 |
| `SYSARCH_LOG_DAYS` | 30 | 最多保留的天数 |
| `SYSARCH_REPORT_MAX_TOTAL` | 5GB | 报告目录的总上限，见下文 |

报告目录：报告文件保留 90 天（P2 T5）。此外，目录总大小超过 `SYSARCH_REPORT_MAX_TOTAL` 时，从最旧的已完成报告开始删除，直到降到上限以内。刚生成的那份报告不会被删除。被删除的任务状态变为 EXPIRED，审计日志记一条 `REPORT_EXPIRE`，写明删除了多少份、目录从多大降到多大。这个检查在每份报告生成后和每晚的过期清理时进行。

第三方库的噪声日志在 `application.yml` 中统一降级。例如 docx4j 的 XHTML 导入会为每个带样式的元素打印一条 WARN，大型 SRS 一次就能写出几百 MB。新增依赖时要检查它的日志量。

## 4. Polarion 侧的日志（建议，不在本工具中修改）

Polarion 的日志位于 `<Polarion 安装目录>/data/logs`：

- `apache/access.log.*`：按周滚动，**不会自动删除**，本机曾累积到 3.5 GB。
- `main/log4j-*.log`：每次启动生成一组新文件，同样不会自动删除。

建议 Polarion 管理员：

1. 定期归档并删除 30 天以前的日志，例如每月一次：先压缩为 zip（文本日志通常能压到约 1/40），校验无误后再删除原文件。不要删除 Apache 和 Polarion 正在写入的当前文件。
2. 在 Apache 配置中限制访问日志：`rotatelogs` 支持 `-n <数量>`（只保留固定数量的文件轮转），或者降低访问日志的详细程度。修改前先备份，并用 `httpd -t` 检查语法。
3. 把 Polarion 数据所在的磁盘也纳入监控（例如 node_exporter 的 `node_filesystem_avail_bytes`），使用与第 2 节相同的 15% 阈值。

## 5. 已验证（2026-11-12）

- 单元测试：`MetricsTokenFilterTest`（令牌正确 200、错误或缺失 401、未配置 404，`/actuator/health` 不受影响）；`SysarchMetricsTest`（各个计数和 NaN）；`ReportServiceTest.theReportDirectoryKeepsToItsSizeCap`。
- 本机实际运行的后端：无令牌 401、错误令牌 401、正确令牌 200，返回 478 行指标，包含报告卷的 `disk_free_bytes`；第一次测试时 401 被 Sirius 的单页路由改写成了 200 HTML，已改为直接设置状态码。日志按 10 KB、总量 60 KB 的测试配置滚动并压缩；报告上限设为 200 KB 时，生成 SRS、ICD 和追溯报告三份后，最旧的 SRS 被删除并记入审计。
- 未验证：PrometheusRule 在实际 Prometheus Operator 中的加载（本机没有 Kubernetes，见 T4）。
