# 2026-04-17 Redis 去留收口

## 背景

在 Step 4 健康检查收口之后，Redis 仍处于“依赖已引入、compose 已编排、部署变量已预留，但没有任何真实业务用途”的悬空状态。这会继续误导开发、部署和运维，把一个未落地的组件当成运行时基线的一部分。

## 审计结论

全仓库审计结果如下：

- `pom.xml` 仍保留 `spring-boot-starter-data-redis` 与 `spring-boot-starter-data-redis-test`
- 根 `compose.yaml` 仍编排 Redis，并向 `app` 容器注入 `SPRING_DATA_REDIS_*`
- `deploy/compose.yaml`、`deploy/.env.production.example`、`.github/workflows/deploy.yml` 仍保留 Redis 部署变量
- `application.yaml` 仍保留 `management.health.redis.enabled=false`
- 但生产代码中不存在：
  - `RedisTemplate`
  - `StringRedisTemplate`
  - Spring Cache Redis
  - Redis repository
  - 基于 Redis 的通知、认证、限流或会话实现

因此，Redis 当前没有真实用途。

## 决策

采用“移除 Redis”方案，而不是继续保留为 optional 基础设施。

理由：

1. 当前没有任何业务功能依赖 Redis。
2. 继续保留只会增加 compose、部署和文档复杂度。
3. “未来也许会用”不足以支撑当前运行时基线。
4. 真正需要 Redis 时，应以明确业务用途重新引入，而不是沿用遗留空壳。

## 实现摘要

- 从 `pom.xml` 移除 Redis 运行时与测试依赖
- 从 `application.yaml` 移除 Redis 健康检查配置
- 从根 `compose.yaml` 移除 Redis 服务、volume 和 app 容器内的 Redis 环境变量
- 从远程部署 compose、部署变量模板和 GitHub Actions deploy workflow 中移除 Redis 环境变量
- 扩展 `DeliveryPipelineAssetsTests`，固定“compose / deploy compose 不再包含 Redis wiring”
- 同步 README、AGENTS、ARCHITECTURE、可靠性说明、部署文档与平台基线说明

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -q -DskipTests compile`
- `bash ./mvnw -Dtest=HarnessHealthSmokeTests,DeliveryPipelineAssetsTests,AubbServerApplicationTests test`
- `docker compose --profile app config`
- `docker compose -f deploy/compose.yaml --env-file deploy/.env.production.example config`
- `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.7`
- `git diff --check`

## 当前边界

- 当前运行时基线不再包含 Redis。
- 若未来重新引入 Redis，必须在同一轮变更中同时提交：
  - 真实业务用途
  - 健康检查策略
  - 自动化验证
  - 部署与运维说明
