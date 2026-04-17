# 可靠性

## 基线保证

- `/actuator/health` 必须保持可公开访问，用于部署检查和服务活性验证。
- `/actuator/health/readiness` 当前作为外部依赖就绪检查入口，公开返回数据库与条件化外部依赖的组件状态和故障提示。
- PostgreSQL 已成为当前真实业务切片的必需依赖，数据库迁移由 Flyway 管理。
- MinIO 已具备正式接入路径；默认关闭，启用后 bucket 可用性进入健康检查。
- RabbitMQ 当前在 `aubb.judge.queue.enabled=true` 时属于条件化硬依赖；readiness 会暴露 `judgeQueue` 组件。
- go-judge 当前在 `aubb.judge.go-judge.enabled=true` 时属于条件化硬依赖；readiness 会暴露 `goJudge` 组件。
- Redis 当前作为可选增强组件引入，默认关闭；关闭时不得影响主链路可用性。
- readiness 当前固定暴露 `redisEnhancement` 组件：
  - 关闭时返回 `UP + mode=disabled`
  - 启用但不可用时返回 `UNKNOWN + mode=degraded`
- 本地开发优先通过 Docker Compose 提供可重复依赖，测试通过 Testcontainers 保证独立验证。
- 应用当前已具备根目录 `Dockerfile`、本地 compose `app` profile 和最小 GitHub Actions `verify -> image -> deploy` 闭环；本地联调与远程部署都必须走仓库内标准入口，而不是依赖临时命令拼装。
- OpenAPI 当前以运行时 `/v3/api-docs` 作为事实契约入口，`/swagger-ui/index.html` 作为联调入口；稳定接口范围通过 `docs/stable-api.md` 固化，并由集成测试回归兜底。
- judge 真实链路集成测试当前默认走 RabbitMQ consumer；测试清理必须先等待运行中评测结束并 purge 测试队列，再执行数据库 `TRUNCATE`，避免与异步回写事务形成死锁。
- JWT 签名密钥当前属于启动阻塞配置；缺失、空白或长度不足时必须在 Spring 上下文刷新阶段直接失败，避免服务以不可认证状态半启动。
- 认证会话当前落库到 `auth_sessions`；受保护请求除 JWT 验签外还会校验会话与用户状态，用于支撑 refresh/revoke/强制失效。
- 首个学校 / 管理员 bootstrap 当前采用默认关闭的启动期 `ApplicationRunner`；启用时若配置缺失或检测到多个学校根节点，会在启动阶段直接失败。
- judge 详细产物当前采用“对象存储优先 + 数据库兼容回退”策略；启用 MinIO 后，详细报告和样例试运行源码快照必须写入成功，或者明确回退到旧列，不能出现“状态成功但报告丢失”。
- lab/report 当前采用“数据库元数据 + 对象存储附件”策略；启用 MinIO 后，实验报告附件写入失败必须同步报错，不能出现“报告已保存但附件丢失”。
- 通知中心当前采用“数据库通知内容 + 收件状态”模型；未读角标与列表回放依赖 `notifications / notification_receipts`，当前轮询策略不应被 WebSocket 或 Redis 可用性阻塞。
- `labEnabled` 当前已进入真实后端拦截链路；教学班关闭实验功能后，实验定义、实验列表、实验详情、附件上传、报告提交和教师评阅都必须被统一拒绝。
- 热点列表查询当前以数据库分页和权限过滤为基线；`total` 与 `items` 必须来自同一组 SQL 谓词，不能再通过“先全量拉取再 Java 侧过滤 / skip / limit”维持分页语义。

## 验证策略

- 快速路径：仓库测试验证配置、领域规则、平台治理接口、课程系统、公开健康检查和代码结构约束。
- 集成路径：通过 PostgreSQL Testcontainers 验证 Flyway 迁移、持久化与登录/治理链路。
- 本地运行路径：使用 `compose.yaml` 提供 PostgreSQL、RabbitMQ、MinIO、go-judge 开发依赖。
- 容器路径：使用根目录 `Dockerfile` 构建应用镜像，通过 `docker compose --profile app config/up` 验证本地联调编排。
- 健康检查路径：
  - 顶层活性：`GET /actuator/health`
  - 依赖就绪：`GET /actuator/health/readiness`
  - 指标抓取：`GET /actuator/prometheus`
  - 当前 readiness 固定包含 `db`，并按开关条件纳入 `minioStorage`、`goJudge`、`judgeQueue`
- 文档同步由开发流程约束和人工评审负责，不再放入自动工作流校验。

## 指标与抓取边界

- `/actuator/prometheus` 当前作为公开 Prometheus 抓取入口，与 `/actuator/health`、`/actuator/health/readiness` 分工不同：
  - `/actuator/health`：只看应用是否存活
  - `/actuator/health/readiness`：只看依赖是否就绪
  - `/actuator/prometheus`：暴露运行时与业务指标，供 Prometheus 周期抓取
- 当前最小业务指标集如下：
  - `aubb_cache_operations_total{cache,operation,result}`：Redis 缓存命中 / 未命中 / 驱逐 / 异常计数
  - `aubb_rate_limit_decisions_total{policy,result}`：Redis 限流放行 / 拒绝 / 降级计数
  - `aubb_redis_available`：Redis 增强链路当前可用性，1 为可用
  - `aubb_redis_enabled`：Redis 增强链路是否启用，1 为启用
  - `aubb_judge_queue_depth`：judge 队列长度；当 judge queue 未启用时返回 `NaN`
  - `aubb_judge_job_executions_total{result=\"succeeded|failed\"}`：judge 任务执行次数；失败率通过 `failed / total` 计算
  - `aubb_judge_job_execution_seconds_count|sum|max{result=\"succeeded|failed\"}`：judge 任务执行耗时
  - `aubb_grading_grade_publications_total{publish_type=\"initial|republish\"}`：成绩发布次数
  - `aubb_grading_appeal_creations_total`：学生发起申诉数量
  - `aubb_grading_appeal_reviews_total{result=\"pending|in_review|accepted|rejected\"}`：申诉处理结果数量
- 当前指标只使用低基数标签；不得把 `assignmentId`、`submissionId`、`userId` 等高基数字段直接写入指标标签。
- 当前 v1 只暴露最小业务指标，不在仓库内引入额外的 recording rules、告警平台或独立 metrics 服务。

## 可靠性规则

1. 新的基础设施依赖一旦进入运行时，就必须同时提供本地和 CI 都可重复执行的验证路径。
2. 数据库迁移必须在测试中真实执行，不能只依赖文档假定结构存在。
3. 优先使用稳定镜像标签和可复现的默认配置，而不是图方便的临时方案。
4. 健康检查公开能力不能被认证逻辑或数据库初始化错误意外破坏。
5. 顶层 `/actuator/health` 只用于轻量活性检查；依赖组件状态、失败原因和运维排障应统一看 `/actuator/health/readiness`。
6. 涉及认证根密钥的配置错误应在应用启动时暴露；不允许依赖“第一次登录时才报错”的延迟失败模式。
7. 启用对象存储后，bucket 缺失或不可访问必须能通过健康检查及时暴露，而不是在业务首次写入时才发现。
8. `aubb.judge.queue.enabled=true` 时，RabbitMQ 必须进入 readiness；若 broker 不可达或评测队列缺失，不能继续返回“应用已就绪”。
9. `aubb.judge.go-judge.enabled=true` 时，go-judge 必须进入 readiness；若 `/version` 不可达或返回异常响应，必须能从健康检查直接看出故障原因。
10. Redis 只能作为增强组件，不得承载评测结果、提交记录、最终成绩、成绩发布快照等核心业务真相。
11. 涉及异步评测的测试或运维脚本，不得在存在运行中 judge job 时直接批量清库；至少要先 drain 运行中任务，避免 `judge_jobs / submission_answers / audit_logs` 锁顺序反转。
12. 涉及令牌撤销的改动，必须同时验证 access token 即时失效、refresh token 轮换和用户状态变更触发的旧会话失效，避免只实现半条链路。
13. 涉及新环境初始化的改动，必须提供标准启动参数、幂等重复执行语义和自动化验证，不能继续依赖手工 SQL 插数。
14. 根 `compose.yaml` 既要保留宿主机 `spring-boot-docker-compose` 的基础设施模式，也要通过显式 profile 支持 app + 基础设施联调，不能让两种运行方式互相冲突。
15. CI 中任何 `verify` 失败都必须保留 `surefire/failsafe` 报告或关键日志，避免流水线失败后无法定位。
16. 最小 deploy 至少要能表达“部署哪个镜像版本、用哪些环境变量、失败后如何回滚”，即使暂不引入更复杂编排。
17. 评测产物对象化后，列表查询继续只走数据库摘要字段；完整详细报告与样例试运行源码快照走对象引用回放，避免把大对象读取放到热点列表路径。
18. 实验报告当前只保留“每学生每实验一份当前报告”；后续若要引入历史版本，必须新增版本化结构，而不是直接覆盖现有表语义。
19. 通知中心 v1 必须先保证“持久化 + 已读状态 + 未读数 + 列表补拉”闭环，再考虑 WebSocket 推送；实时通道故障不能影响通知入库和已读状态正确性。
20. 涉及热点列表优化时，优先把权限过滤和分页下推到数据库；若组织树或课程成员边界仍需服务层预解析，也应先收敛成有限作用域集合，再交给 SQL 做 count/page，避免全量候选集进入内存。
21. 稳定 API 发生变更时，必须在同一轮提交中同步更新 `docs/stable-api.md`，并至少验证 `/v3/api-docs` 仍可访问且包含当前承诺路径。
22. `/actuator/prometheus` 与 `/actuator/health/readiness` 不能混用；监控系统抓 metrics，部署 smoke 与探活继续看 health/readiness。
23. 所有 Redis 接入都必须同时给出 key 设计、TTL、失效点、一致性边界与降级策略；若做不到，宁可不缓存。
