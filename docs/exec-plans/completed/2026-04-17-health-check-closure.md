# 2026-04-17 健康检查收口

## 目标

收口 M5 第一段最影响交付判断的治理缺口：

- 明确 hard dependency / optional dependency
- 让 RabbitMQ、MinIO、go-judge 的健康状态进入 actuator readiness
- 明确 Redis 当前不进入 readiness，避免误报
- 统一本地 compose 与远程 deploy 的探活入口

## 审计结论

1. `application.yaml` 当前显式关闭了 `management.health.rabbit` 与 `management.health.redis`。
2. MinIO 已有 `minioStorage` 健康指示器，但 go-judge 还没有 actuator 健康组件。
3. RabbitMQ 在 `aubb.judge.queue.enabled=true` 时是条件化硬依赖；简单打开 Spring 内建 `rabbit` 健康检查会把“队列未启用”的场景误伤成 DOWN。
4. Redis 当前没有任何真实业务使用，继续把它算进 readiness 会制造 false negative。

## 最小方案

1. 保持顶层 `/actuator/health` 作为公开活性检查。
2. 把 `/actuator/health/readiness` 作为依赖就绪检查入口。
3. 固定把 `db` 纳入 readiness。
4. 按开关条件纳入：
   - `minioStorage`
   - `goJudge`
   - `judgeQueue`
5. Redis 暂不纳入 readiness，直到后续明确保留或移除。

## 实现结果

- 新增 `GoJudgeHealthIndicator`
  - 通过 go-judge `/version` 探测服务状态
  - 返回 `baseUrl / buildVersion / goVersion / platform / os`
- 新增 `JudgeQueueHealthIndicator`
  - 仅在 `aubb.judge.queue.enabled=true` 时装配
  - 返回 `queueName / broker / messageCount / consumerCount`
  - 若 broker 不可达或队列缺失，直接把 readiness 打成 DOWN
- 根 `compose.yaml` 的 app healthcheck 改为调用 `/actuator/health/readiness`
- `deploy.yml` 远程部署 smoke 改为调用 `/actuator/health/readiness`

## Redis 当前结论

- 当前代码事实：无真实业务使用。
- 当前治理结论：Step 4 先按 optional 保留，不纳入 readiness。
- 后续动作：Step 6 单独收口 Redis 去留。

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -q -DskipTests compile`
- `bash ./mvnw -Dtest=HarnessHealthSmokeTests,MinioStorageIntegrationTests,JudgeDependencyHealthIntegrationTests,GoJudgeHealthIndicatorTests,JudgeQueueHealthIndicatorTests test`
- `docker compose --profile app config`
- `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.7`
- `git diff --check`

结果：通过。

## 当前边界

- 本轮不实现业务 metrics、告警规则或 Redis 最终去留。
- readiness 暂只覆盖最影响当前交付口径的外部依赖，不做更大范围的第三方系统编排。
