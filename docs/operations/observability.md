# 可观测性运行手册

## tracing

- 依赖：`spring-boot-starter-opentelemetry`
- 配置入口：
  - `AUBB_TRACING_SAMPLING_PROBABILITY`
  - `AUBB_OTLP_TRACING_ENDPOINT`
- 日志已通过 `logging.pattern.correlation` 输出 `traceId / spanId` 关联字段。

## Prometheus

- 配置文件：[monitoring/prometheus/prometheus.yml](../../monitoring/prometheus/prometheus.yml)
- 告警规则：[monitoring/prometheus/alerts.yml](../../monitoring/prometheus/alerts.yml)
- 主要抓取端点：`GET /actuator/prometheus`

重点指标：

- `aubb_judge_queue_depth`
- `aubb_judge_job_executions_total`
- `aubb_judge_job_execution_seconds_*`
- `aubb_grading_grade_publications_total`
- `aubb_grading_appeal_*`

## Grafana

- 仪表盘：[monitoring/grafana/dashboards/aubb-platform-overview.json](../../monitoring/grafana/dashboards/aubb-platform-overview.json)
- 推荐面板：
  - API 吞吐
  - readiness / uptime
  - judge 队列深度
  - judge 时延与失败率

## 日志聚合

- Loki：[monitoring/loki/loki-config.yml](../../monitoring/loki/loki-config.yml)
- Promtail：[monitoring/promtail/promtail-config.yml](../../monitoring/promtail/promtail-config.yml)

建议为 `app` 与 `judge-worker` 打不同 label，并结合 `traceId` 进行链路排障。
