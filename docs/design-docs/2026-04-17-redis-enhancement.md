# 2026-04-17 Redis 增强接入设计

## 目标

在不改变 PostgreSQL 真相源地位的前提下，为 AUBB 增量引入 Redis，用于限流、高频读缓存与未来多实例实时协调的基础能力，并保证 Redis 关闭或异常时系统可明确降级。

## 边界

- Redis 不是成绩、评测、提交、成绩快照、权限真相库。
- Redis 默认属于增强组件，不是启动必备依赖。
- 只有在 `aubb.redis.enabled=true` 时才创建 Redis 连接与增强实现。
- Redis 不可用时：
  - 限流回退为 no-op，主业务继续执行
  - 缓存回退为直接查库
  - 实时协调回退为 no-op

## 本轮能力

1. Redis 配置、自动装配、健康检查、指标与 deploy / compose 资产
2. `RateLimitService` 抽象与 Redis 固定窗口限流
3. `CacheService` 基础能力与三个高收益缓存：
   - 未读通知数
   - 我的课程列表摘要
   - 题库分类 / 标签字典
4. `RealtimeCoordinationService` 预留接口与最小 Redis 发布实现
5. 自动化测试、预检脚本与运维文档

## 设计决策

### 1. 限流

- 使用 `@RateLimited` + AOP，不把 Redis 逻辑写进 Controller。
- key 统一走 `aubb:{env}:ratelimit:{action}:...` 规范。
- 对匿名接口保留 `ip + 请求主体` 维度；对登录后接口加入 `userId + ip + endpoint + resource` 维度。
- 限流策略走配置驱动，不把阈值硬编码在业务代码。

### 2. 缓存

- 统一采用 cache-aside。
- 业务模块只依赖缓存门面，不直接依赖 `StringRedisTemplate`。
- TTL 保守：
  - 未读通知数：60s
  - 我的课程列表：120s
  - 题库分类 / 标签字典：15min
- 写路径显式失效，不在事务边界内引入额外强耦合。

### 3. 健康检查与降级

- 增加 `redisEnhancement` 健康组件：
  - 禁用时返回 `UP(enabled=false)`
  - 启用且可连通时返回 `UP(available=true)`
  - 启用但不可连通时：
    - `requiredForReadiness=false` 时返回 `UP(available=false,degraded=true)`
    - `requiredForReadiness=true` 时返回 `DOWN`
- 指标补：
  - `aubb_cache_operations_total`
  - `aubb_rate_limit_total`
  - `aubb_redis_available`

## 风险

- 若把课程列表缓存失效点做漏，会出现短 TTL 内数据陈旧。
- 若限流 key 设计过粗，会误伤正常请求；过细则无法抑制刷接口。
- Redis 异常日志若不降噪，会污染主业务日志。

## 验证

- 单元测试：
  - Redis 限流 allow/reject/fallback
  - Redis 缓存 hit/miss/evict/fallback
- 集成测试：
  - 登录 / refresh / sample run / 提交 / 上传限流
  - 通知未读数缓存命中与失效
  - 课程列表缓存命中
  - 题库字典缓存与失效
- 全量验证：`bash ./mvnw verify`
