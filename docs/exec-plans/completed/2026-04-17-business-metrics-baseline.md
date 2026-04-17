# 2026-04-17 关键业务指标基线

## 背景

Step 4 已经把 readiness 健康检查收口为交付口径上的依赖就绪事实入口，但 M5 仍缺一组最小可用的业务指标。当前仓库虽然已经接入 actuator 与 Prometheus registry 依赖，也公开了 `prometheus` exposure 配置，但在本轮之前并没有 judge / grading 相关业务指标采集点。

## 本轮目标

- 为 judge 与 grading 补齐最关键的一小撮业务指标
- 保持实现最小化，不引入新的监控基础设施
- 让 `/actuator/prometheus` 成为可抓取、可验证、可文档化的运行时事实入口

## 指标范围

### judge

- `aubb_judge_queue_depth`
  - 当前 judge RabbitMQ 队列深度
- `aubb_judge_job_executions_total{result="succeeded|failed"}`
  - judge 执行次数
- `aubb_judge_job_execution_seconds_*{result="succeeded|failed"}`
  - judge 执行耗时

### grading

- `aubb_grading_grade_publications_total{publish_type="initial|republish"}`
  - 成绩发布次数
- `aubb_grading_appeal_creations_total`
  - 学生发起申诉数量
- `aubb_grading_appeal_reviews_total{result="pending|in_review|accepted|rejected"}`
  - 申诉处理结果数量

## 设计决策

1. judge 失败率不单独落地为 gauge；运维侧通过 `failed / total` 计算。
2. judge 主耗时从 `JudgeExecutionService.executeJudgeJob(...)` 的评测执行段计时，不把终态 side effects 混进主 timer。
3. 队列长度直接读取 RabbitMQ 队列属性，而不是维护应用内缓存。
4. 成绩发布和申诉计数通过事务 `afterCommit` 记录，避免事务回滚后脏计数。
5. `/actuator/prometheus` 作为公开运维面暴露，但只提供低基数系统 / 业务指标，不返回用户级、作业级或提交级高基数标签。

## 实现摘要

- 新增 `JudgeMetricsRecorder`
  - 注册 queue depth gauge
  - 注册 judge 成功 / 失败计数与耗时 timer
- 新增 `GradingMetricsRecorder`
  - 注册成绩发布次数
  - 注册申诉创建数量
  - 注册申诉处理结果
- 新增 `PrometheusMetricsConfiguration`
  - 显式提供 `PrometheusMeterRegistry`
  - 固化 `GET /actuator/prometheus` 抓取入口
- 在以下服务中接入采集点：
  - `JudgeExecutionService`
  - `GradingApplicationService`
  - `GradeAppealApplicationService`

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=JudgeMetricsRecorderTests,GradingMetricsRecorderTests,HarnessHealthSmokeTests,JudgeIntegrationTests,GradingIntegrationTests test`
- `git diff --check`

结果：通过。

## 当前边界

- 本轮不引入告警规则、Grafana 面板或远程 Prometheus 部署。
- 当前 failure rate 仍由 PromQL 计算，不单独持久化预聚合指标。
- `/actuator/prometheus` 当前保持公开，如生产环境需要进一步收口，应由反向代理、内网策略或 API Gateway 处理。
