# 2026-04-17 Redis 增强接入执行计划

## 目标

把 Redis 以“增强组件”方式接入 AUBB，完成配置、限流、缓存、健康检查、测试与文档闭环。

## 范围

- Redis 基础配置与条件化自动装配
- RateLimitService / CacheService / RealtimeCoordinationService
- 登录、refresh、样例试运行、提交、上传限流
- 通知未读数、我的课程列表、题库分类/标签缓存
- 健康检查、指标、降级、preflight、compose / deploy 资产
- 单元测试、集成测试、文档

## 风险

- Redis 绝不能成为默认硬依赖。
- 课程缓存与题库字典缓存的失效点必须完整。
- 限流必须覆盖目标接口，但不能把正常链路误伤到不可用。

## 实施步骤

1. 增加 Redis 依赖、配置属性、自动装配、health 与 metrics
2. 先写 RateLimitService / CacheService 的失败测试，再实现 Redis/no-op 双实现
3. 用 `@RateLimited` 接入认证、样例试运行、提交、上传接口
4. 接入通知未读数缓存与失效
5. 接入我的课程列表缓存与失效
6. 接入题库分类 / 标签字典缓存与失效，并补标签字典接口
7. 接入 realtime coordination 预留接口与 Redis 发布实现
8. 更新 compose / deploy / env / preflight
9. 完成单元测试、集成测试、全量验证
10. 更新 `docs/reliability.md`、`docs/deployment.md`、`docs/redis.md`

## 验证路径

- 定向：
  - `bash ./mvnw -Dtest=RedisRateLimitServiceTests,RedisCacheServiceTests test`
  - `bash ./mvnw -Dtest=RedisRateLimitIntegrationTests,NotificationCenterIntegrationTests,StructuredAssignmentIntegrationTests,CourseSystemIntegrationTests test`
- 全量：
  - `bash ./mvnw verify`
