# Redis 增强组件说明

## 定位与边界

Redis 在 AUBB 中只作为增强组件使用，不是业务真相库。

- PostgreSQL 仍是唯一主数据源。
- Redis 只承接低一致性风险、高收益场景：
  - 限流 / 防刷
  - 高频读缓存
  - 未来实时推送与多实例协调的基础接口
- Redis 不存储以下内容作为真相：
  - judge 最终评测结果
  - 提交主记录
  - 成绩册最终成绩
  - 成绩发布快照
  - 长 TTL 的权限决策结果

## 当前接入点

### 1. 限流

由 `RateLimitService` 统一承接，默认使用 Redis 固定窗口计数器。

当前已覆盖接口：

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`
- `POST /api/v1/me/assignments/{assignmentId}/submissions`
- `POST /api/v1/me/assignments/{assignmentId}/submission-artifacts`
- `POST /api/v1/me/labs/{labId}/attachments`

### 2. 高频读缓存

由 `CacheService` 统一承接，使用 cache-aside 策略。

当前缓存场景：

- 未读通知数
- 我的课程列表摘要
- 题库分类字典
- 题库标签字典

### 3. 未来实时推送预留

`RealtimeCoordinationService` 目前只提供接口与基础 Redis 实现，不承担通知真相存储，也不阻塞现有通知入库链路。

## Key 设计

统一前缀：

```text
{namespace}:{environment}:{domain}:...
```

默认前缀示例：

```text
aubb:staging:cache:notificationunreadcount:user:42
aubb:staging:ratelimit:login:user:42:ip:10_0_0_1:school-admin
aubb:staging:realtime:notifications
```

## TTL / 失效 / 一致性策略

| 场景 | Key 示例 | 默认 TTL | 失效策略 | 一致性边界 |
| --- | --- | --- | --- | --- |
| 登录限流 | `aubb:{env}:ratelimit:login:{scope}` | `PT1M` | 窗口自动过期 | 只影响防刷，不影响认证真相 |
| refresh 限流 | `aubb:{env}:ratelimit:refresh:{scope}` | `PT1M` | 窗口自动过期 | 只影响接口速率 |
| 样例试运行限流 | `aubb:{env}:ratelimit:sample-run:{scope}` | `PT1M` | 窗口自动过期 | 只影响请求速率 |
| 提交限流 | `aubb:{env}:ratelimit:submission-create:{scope}` | `PT1M` | 窗口自动过期 | 只影响请求速率 |
| 上传限流 | `aubb:{env}:ratelimit:submission-artifact-upload:{scope}` / `lab-attachment-upload:{scope}` | `PT1M` | 窗口自动过期 | 只影响请求速率 |
| 未读通知数 | `aubb:{env}:cache:notificationunreadcount:user:{userId}` | `PT1M` | 通知 fan-out、已读、全部已读时驱逐 | 允许短时间读到旧角标 |
| 我的课程摘要 | `aubb:{env}:cache:mycoursessummary:user:{userId}` | `PT2M` | 开课教师初始化、成员变更时驱逐 | 允许短时间读到旧摘要 |
| 题库分类字典 | `aubb:{env}:cache:questionbankcategories:offering:{offeringId}` | `PT20M` | 题目创建、更新、归档时驱逐 | 允许短时间读到旧字典 |
| 题库标签字典 | `aubb:{env}:cache:questionbanktags:offering:{offeringId}` | `PT20M` | 题目创建、更新、归档时驱逐 | 允许短时间读到旧字典 |

## 降级策略

### Redis 关闭

- `aubb.redis.enabled=false` 时，不创建 Redis 连接。
- `RateLimitService` 自动退化为 no-op。
- `CacheService` 自动退化为直接回源数据库 / 应用 loader。
- `RealtimeCoordinationService` 自动退化为 no-op。

### Redis 不可用

- 限流：返回 `allow/fallback`，不阻断主业务。
- 缓存：直接回源，不因 Redis 异常导致接口失败。
- 实时协调：记录降噪日志并跳过，不影响通知持久化。

## 配置项

核心配置位于 `aubb.redis.*`：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `aubb.redis.enabled` | `false` | 是否启用 Redis 增强链路 |
| `aubb.redis.host` | `localhost` | Redis 主机 |
| `aubb.redis.port` | `6379` | Redis 端口 |
| `aubb.redis.password` | 空 | Redis 密码 |
| `aubb.redis.database` | `0` | 逻辑库 |
| `aubb.redis.namespace` | `aubb` | Key 命名空间 |
| `aubb.redis.environment` | `local` | 环境维度前缀 |
| `aubb.redis.cache.*` | 见 `application.yaml` | 各缓存 TTL |
| `aubb.redis.rate-limit.enabled` | `true` | 是否启用 Redis 限流实现 |
| `aubb.redis.rate-limit.policies.*` | 见 `application.yaml` | 各接口限流策略 |
| `aubb.redis.realtime.enabled` | `true` | 是否启用 Redis 实时协调实现 |

## 健康检查与指标

### 健康检查

- readiness 组件名：`redisEnhancement`
- Redis 关闭：
  - `status=UP`
  - `mode=disabled`
- Redis 启用但不可用：
  - `status=UNKNOWN`
  - `mode=degraded`

这意味着 Redis 故障会被显式暴露，但不会把主业务健康语义误判成“数据库不可用级别”的硬失败。

### 指标

- `aubb_cache_operations_total{cache,operation,result}`
- `aubb_rate_limit_decisions_total{policy,result}`
- `aubb_redis_available`
- `aubb_redis_enabled`

建议重点关注：

- `cache=get,result=hit|miss|error`
- `cache=*,operation=evict,result=error`
- `policy=*,result=rejected|fallback`
- `aubb_redis_available == 0`

## 上线注意事项

1. 先以 `AUBB_REDIS_ENABLED=false` 完成部署链路验证，再灰度打开。
2. 打开 Redis 后先观察：
   - readiness 中 `redisEnhancement`
   - `aubb_redis_available`
   - `aubb_rate_limit_decisions_total{result="fallback"}`
   - `aubb_cache_operations_total{result="error"}`
3. 不要把 Redis 当成“省 SQL 的万能替代品”；新增缓存前必须先定义失效点与降级行为。
4. 若需要扩展到组织范围短 TTL 缓存或 SSE/WebSocket 多实例协调，继续沿用 `CacheService` / `RealtimeCoordinationService` 抽象，不要在业务代码里散落 Redis 命令。
