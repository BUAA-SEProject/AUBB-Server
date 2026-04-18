# 任务计划：作业模块能力重规划与后续推进

## 当前目标

基于当前代码基线，继续执行最终收尾步骤。Step 1 最终统一验收、Step 2 judge 产物对象化 phase 2、Step 3 成绩发布快照 v1、Step 4 健康检查收口、Step 5 关键业务指标、Step 6 Redis 去留收口与 Step 7 状态台账 / 最终交付结论已通过；本轮收尾开发完成，后续进入交接与新阶段规划。

## 当前阶段

Phase 53 completed，Phase 52 completed，Phase 51 completed，Phase 50 completed，Phase 49 completed，Phase 48 completed，Phase 47 completed，Phase 46 completed，Phase 45 completed，Phase 44 completed，Phase 43 completed，Phase 42 completed，Phase 41 completed，Phase 40 completed，Phase 39 completed，Phase 38 completed，Phases 15 / 16 / 17 / 18 / 19 in progress，Phases 20 / 21 / 22 / 23 / 24 / 29 / 30 / 31 / 32 / 33 / 34 / 35 / 36 / 37 completed

### Phase 53：状态台账与最终交付结论

- [x] 复核 `todo.md`、`docs/exec-plans/completed/`、`README.md`、`ARCHITECTURE.md`、`docs/reliability.md`、`docs/deployment.md` 与最近完成步骤的代码 / 测试 / 文档证据
- [x] 将优先级 1-10 与里程碑 M0-M5 统一汇总为状态台账，明确状态、证据文件、剩余缺口、是否阻塞上线和下一动作
- [x] 输出最终交付结论，明确 V1 当前是否达到“可继续联调 / 可内测 / 可进入上线准备”的判断
- [x] 明确区分“当前计划已完成”与“后续增强项”，避免把未来优化误判为当前阻塞
- [x] 同步工作记忆与完成执行计划
- **Status:** completed

### Phase 52：Redis 去留收口

- [x] 审计 `pom.xml`、`application.yaml`、`compose.yaml`、`deploy/compose.yaml`、`.github/workflows/deploy.yml` 与仓库全文引用，确认 Redis 只有依赖、compose、部署变量和文档残留，没有真实业务使用
- [x] 明确保留 Redis 的理由不足：通知、认证、缓存、消息、健康检查和指标都未落到 Redis
- [x] 选择“移除 Redis”方案，避免继续维持误导性的运行时依赖与部署前提
- [x] 从 `pom.xml` 移除 `spring-boot-starter-data-redis` 与 `spring-boot-starter-data-redis-test`
- [x] 从 `application.yaml` 移除 `management.health.redis` 配置
- [x] 从根 `compose.yaml` 移除 Redis 服务、端口、volume、`depends_on` 和应用容器内的 `SPRING_DATA_REDIS_*` 注入
- [x] 从 `deploy/compose.yaml`、`deploy/.env.production.example` 与 `.github/workflows/deploy.yml` 中移除 Redis 相关环境变量
- [x] 扩展 `DeliveryPipelineAssetsTests`，固定“根 compose 与 deploy compose 不再包含 Redis wiring”的收口约束
- [x] 同步 README、ARCHITECTURE、AGENTS、`docs/reliability.md`、`docs/deployment.md`、`docs/product-specs/platform-baseline.md` 与完成执行计划
- [x] 执行 `bash ./mvnw spotless:apply`、`bash ./mvnw -q -DskipTests compile`、`bash ./mvnw -Dtest=HarnessHealthSmokeTests,DeliveryPipelineAssetsTests,AubbServerApplicationTests test`、compose config、actionlint 与 `git diff --check`
- **Status:** completed

### Phase 51：关键业务 metrics 基线

- [x] 审计 `pom.xml`、`application.yaml`、`SecurityConfig` 与现有 actuator / prometheus 暴露基线，确认 Micrometer 依赖已在仓库中但还没有任何业务指标采集点
- [x] 明确最小指标范围：judge 队列长度、judge 执行次数 / 耗时 / 失败率基础计数、成绩发布次数、申诉创建与处理结果
- [x] 新增 `JudgeMetricsRecorder`，收口 RabbitMQ 队列深度 gauge 与 judge 成功 / 失败计数、耗时 timer
- [x] 在 `JudgeExecutionService` 中接入 judge 执行耗时与成功 / 失败计数，保持主耗时不包含终态 side effects
- [x] 新增 `GradingMetricsRecorder`，收口成绩发布次数、申诉创建数量与申诉处理结果数量
- [x] 在 `GradingApplicationService`、`GradeAppealApplicationService` 中接入业务指标，并通过事务 `afterCommit` 避免回滚脏计数
- [x] 补齐 `PrometheusMetricsConfiguration`，固化 `/actuator/prometheus` 抓取入口，并在 `SecurityConfig` 中明确公开放行
- [x] 补齐 `JudgeMetricsRecorderTests`、`GradingMetricsRecorderTests`、`HarnessHealthSmokeTests` 与 judge / grading 集成测试，验证指标暴露与关键路径计数
- [x] 同步 README、`docs/reliability.md`、`docs/deployment.md`、`docs/security.md`、`docs/stable-api.md` 与完成执行计划
- [x] 执行 `bash ./mvnw spotless:apply`、指标专项测试与 `git diff --check`
- **Status:** completed

### Phase 50：健康检查收口

- [x] 审计 `application.yaml`、`MinioStorageConfiguration`、`GoJudgeConfiguration`、`JudgeQueueConfiguration`、部署 / 可靠性文档与 compose 当前健康检查基线
- [x] 确认 MinIO 已有条件化健康指示器，RabbitMQ 被显式关闭、go-judge 尚未进入 actuator、Redis 仍无真实业务使用
- [x] 设计最小分级方案：`db` 全局必需；`minioStorage`、`goJudge`、`judgeQueue` 按开关条件进入 readiness；Redis 暂按 optional 保留且不纳入 readiness
- [x] 新增 `GoJudgeHealthIndicator`、`JudgeQueueHealthIndicator`，并在配置层按 `aubb.judge.go-judge.enabled / aubb.judge.queue.enabled` 条件装配
- [x] 收口 actuator readiness：固定包含 `db`，按条件纳入 `minioStorage / goJudge / judgeQueue`，并公开返回组件级故障提示
- [x] 将本地 compose 与远程 deploy smoke 从 `/actuator/health` 切换到 `/actuator/health/readiness`
- [x] 补齐默认健康 smoke、MinIO readiness 集成测试、真实 judge 依赖 readiness 集成测试，以及 go-judge / judgeQueue 健康指示器单元测试
- [x] 同步更新 `docs/reliability.md`、`docs/deployment.md`、`docs/security.md`、`docs/stable-api.md` 与完成执行计划
- [x] 执行 `bash ./mvnw spotless:apply`、`bash ./mvnw -q -DskipTests compile`、健康专项测试、`docker compose --profile app config`、`actionlint` 与 `git diff --check`
- **Status:** completed

### Phase 49：成绩发布快照 v1

- [x] 审计 grading / gradebook 当前成绩发布链路，确认当前只靠 `assignments.grade_published_at` 控制学生可见性
- [x] 明确快照 v1 只做“发布时生成快照 + 教师可追踪批次”，不混入完整回滚或读模型重写
- [x] 新增 `grade_publish_snapshot_batches / grade_publish_snapshots` migration 与对应 MyBatis 实体 / mapper
- [x] 在 `publishAssignmentGrades` 中接入批次快照生成逻辑，并保持“仅首次发布写入 assignment 首发时间 / 首次通知”的兼容语义
- [x] 新增教师侧快照批次列表 / 详情 API，支持按作业追溯已发布成绩快照
- [x] 补齐发布生成快照、查询追踪快照、重复发布行为和 gradebook 回归测试
- [x] 同步 grading 规格、README、稳定接口清单、数据库结构、OpenAPI 回归测试与完成执行计划
- [x] 执行 `git diff --check` 并完成最终最小测试回归
- **Status:** completed

### Phase 48：judge 详细产物对象化存储 phase 2

- [x] 审计 `V25__judge_artifact_object_storage_phase1.sql`、`JudgeArtifactStorageService`、`JudgeExecutionService`、judge controller / API 与文档
- [x] 明确 phase 1 已实现能力、当前缺口和“旧数据保留 JSON、对象优先读取、不做批量回填”的兼容策略
- [x] 新增 `judge_jobs` 的正式评测源码快照、归档清单与产物追踪摘要字段
- [x] 补齐学生 / 教师侧详细评测报告下载能力
- [x] 让正式评测在终态落库时同步写入 `detailReport / sourceSnapshot / artifactManifest / artifactTrace`
- [x] 补齐对象化存储单元测试、真实 judge / MinIO 集成测试、报告下载测试和旧数据兼容测试
- [x] 同步 judge 规格、README、稳定接口清单、数据库结构和完成执行计划
- [x] 执行 `bash ./mvnw spotless:apply`、`bash ./mvnw -q -DskipTests compile`、最小必要 judge/MinIO 回归并确认结果
- **Status:** completed

### Phase 47：文档漂移与 OpenAPI / 稳定接口清单收口

- [x] 复核 `README.md`、`ARCHITECTURE.md`、`docs/security.md`、`docs/reliability.md`、`docs/product-specs/`、`docs/exec-plans/` 与 springdoc / swagger 相关代码事实
- [x] 识别并清理“骨架系统 / 无 Flyway / 认证占位 / JWT 无状态校验”等明显过时口径
- [x] 新增 `docs/stable-api.md`，固化 `/v3/api-docs`、`/swagger-ui/index.html` 与当前稳定业务接口范围
- [x] 同步更新入口文档、架构说明、仓库结构说明、IAM 规格和过时 tech debt 索引
- [x] 新增 OpenAPI 集成测试，验证公开访问与关键稳定路径仍在运行时契约中暴露
- [x] 补齐工作记忆与完成执行计划
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要 OpenAPI / 启动测试
- **Status:** completed

### Phase 46：关键列表数据库分页权限过滤

- [x] 复核 `todo.md`、`listUsers`、`listMyAssignments` 现状实现与现有权限服务边界，确认本轮只处理两个已确认热点
- [x] 识别当前“全量候选 + Java 过滤 + 内存分页”的根因，并拆分出可安全下推到 SQL 的谓词与必须保留在服务层的作用域预解析
- [x] 为 `UserMapper`、`AssignmentMapper` 增加定向 count/page 查询，让 `listUsers`、`listMyAssignments` 切换到数据库侧分页
- [x] 在 `GovernanceAuthorizationService`、`CourseAuthorizationService` 中补齐最小作用域集合解析，保持平台级权限和课程级权限边界不混用
- [x] 新增贴合查询路径的索引 migration，补齐 `users / user_scope_roles / course_members / assignments` 热点访问路径
- [x] 补齐治理与课程授权单元测试，以及 `listUsers`、`listMyAssignments` 的分页正确性 / 越权边界集成测试
- [x] 同步 README、可靠性说明、产品规格、数据库结构和执行计划
- [x] 执行 `bash ./mvnw spotless:apply`、`bash ./mvnw -q -DskipTests compile` 与最小必要专项测试
- **Status:** completed

### Phase 45：通知 / 消息中心 MVP

- [x] 复核 `todo.md` M4、`docs/plan.md` 与现有模块边界，确认本轮只做站内通知 v1，不把 WebSocket / Redis 前置为必选依赖
- [x] 识别 assignment / grading / judge / lab 里的稳定业务事件触发点，并明确只接“状态真正对用户可见”的终态
- [x] 新增 `notifications / notification_receipts` 最小模型与 Flyway migration
- [x] 新增通知模块，补齐我的通知列表、未读数、单条已读、全部已读 API
- [x] 接入首批关键教学事件：作业发布、评测完成、成绩发布、申诉处理完成、实验发布、实验报告评语发布
- [x] 额外接入实验报告提交通知，确保教师 / 助教也有真实站内通知闭环
- [x] 补齐领域单元测试、通知 API 集成测试，以及 assignment / grading / judge / lab 关键链路事件触发回归测试
- [x] 同步 README、产品规格、可靠性说明、数据库结构与执行计划
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要通知专项测试
- **Status:** completed

### Phase 44：lab / report MVP

- [x] 复核 `todo.md` M3、course/assignment/submission 现状与 `labEnabled` 功能开关边界
- [x] 确认实验先按教学班级级别落地，避免在 MVP 阶段引入 offering 级实验与不确定的功能开关语义
- [x] 新增 `labs / lab_reports / lab_report_attachments` 最小模型与 Flyway migration
- [x] 落地教师侧实验创建 / 更新 / 发布 / 关闭、报告列表 / 详情、批注 / 评语 / 发布 API
- [x] 落地学生侧实验列表 / 详情、附件上传、实验报告草稿 / 提交、我的报告与附件下载 API
- [x] 将 `labEnabled=false` 接入 `CourseAuthorizationService`，实现真实后端拦截
- [x] 补齐领域单元测试与 MinIO 真实链路集成测试
- [x] 同步 README、产品规格、可靠性说明、数据库结构与执行计划
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要 lab/report 测试
- **Status:** completed

### Phase 43：judge 详细产物对象化存储第一阶段

- [x] 复核 `JudgeExecutionService`、`JudgeApplicationService`、`ProgrammingSampleRunApplicationService`、`submission` 相关源码快照与附件链路、MinIO 接入点和 judge 规格
- [x] 明确正式评测与样例试运行当前哪些大体积字段直接落库，哪些可继续保留为摘要或引用
- [x] 设计最小对象化模型，保持现有报告 API 兼容，并通过 Flyway 引入必要对象引用字段或元数据表
- [x] 实现 judge job 详细报告对象化存储与兼容读取；样例试运行同步对象化详细报告和源码快照
- [x] 保留 `judge_jobs` / `programming_sample_runs` 的摘要、状态、索引字段，避免打断列表查询和已有权限链路
- [x] 补齐 MinIO 真实链路集成测试，覆盖对象写入、报告查询回放和旧字段兼容回退
- [x] 同步 README、对象存储文档、judge 规格、可靠性说明、数据库结构和执行计划
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要 judge / MinIO 测试
- **Status:** completed

### Phase 42：Dockerfile、CI 与发布流水线基线

- [x] 复核 `pom.xml`、`compose.yaml`、`.github/`、`README.md` 与现有运行配置，确认应用级容器化和流水线缺口
- [x] 为应用补齐根目录 `Dockerfile` 与 `.dockerignore`，并保持 Java 25 / Spring Boot 打包链路兼容
- [x] 让根目录 `compose.yaml` 能编排 app + 基础设施用于本地联调，同时不破坏现有宿主机 `spring-boot-docker-compose` 用法
- [x] 补齐 GitHub Actions `verify -> image -> deploy` 最小闭环，并在失败时保留测试报告或日志产物
- [x] 为最小 deploy 提供清晰的版本、环境变量、回滚入口和部署编排文件，但不扩展到 Helm / K8s
- [x] 补充工程化回归测试或仓库资产校验，并执行 `bash ./mvnw spotless:apply`、最小必要测试、`docker build`、`docker compose config` 与本地 compose smoke
- [x] 同步 README、部署文档、环境变量说明、可靠性文档与执行计划
- **Status:** completed

### Phase 41：首个学校 / 管理员 bootstrap 初始化闭环

- [x] 复核 `platform-governance-and-iam` 规格、identityaccess / organization / platformconfig 模块与现有 migration，确认初始化缺口
- [x] 确认启动期最小入口采用受配置开关控制的 `ApplicationRunner`，而不是额外引入独立初始化系统
- [x] 设计并实现幂等 bootstrap 服务，覆盖学校根节点、首个学校管理员、必要平台配置
- [x] 补齐配置校验与重复执行幂等保护，新增单一学校根节点约束 migration
- [x] 补齐启动参数与集成测试，覆盖首次初始化、重复执行不脏写、管理员登录与平台配置可读取
- [x] 同步 README、部署 / 初始化说明、安全 / 可靠性文档、IAM 规格与执行计划
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要 bootstrap 测试
- **Status:** completed

### Phase 40：refresh token / revoke / 强制失效机制

- [x] 复核 `AuthController`、`application/auth`、JWT 解码链路、现有认证集成测试和 `docs/security.md`
- [x] 确认最小数据模型为 `auth_sessions`，避免过早拆成多张 token 表
- [x] 新增 refresh token、session revoke 和 access token 会话校验能力
- [x] 让 logout、`/api/v1/auth/revoke`、用户禁用和管理员强制失效都能让旧会话不可继续使用
- [x] 补充 `POST /api/v1/auth/refresh`、`POST /api/v1/auth/revoke` 及必要的管理员强制失效接口
- [x] 补齐单元测试和认证 / 平台治理集成测试，覆盖 refresh、revoke、禁用、强制失效回归
- [x] 同步 README、安全文档、IAM 规格、数据库结构和执行计划
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要认证 / 治理测试
- **Status:** completed

### Phase 39：JWT 默认密钥移除与密钥治理基线

- [x] 复核 `application.yaml`、`SecurityConfig`、`JwtTokenService`、认证 API 与 `docs/security.md`
- [x] 确认启动期校验放在独立 `@ConfigurationProperties` 绑定层的最稳路径
- [x] 移除 JWT secret 默认回退，并在缺失或过弱时 fail-fast
- [x] 为测试入口显式注入 test secret，避免继续隐式依赖主配置弱默认值
- [x] 补齐启动失败测试，以及“已配置密钥时登录与鉴权正常”的回归验证
- [x] 同步更新 README、安全文档、执行计划与工作记忆
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要认证/启动测试
- **Status:** completed

### Phase 38：judge 死锁与终态超时修复

- [x] 复核 `StructuredProgrammingJudgeIntegrationTests`、`JudgeIntegrationTests`、`ProgrammingWorkspaceIntegrationTests` 的清理策略与 RabbitMQ 队列路径
- [x] 确认 `TRUNCATE` 死锁由测试清理与异步评测事务并发引起，而不是本地 `@Async` 与 Rabbit 双消费
- [x] 用最小改动修复 judge 相关测试清理策略，避免残留队列消息或运行中事务与 `TRUNCATE` 冲突
- [x] 为编程题评测失败补齐明确可诊断终态，避免 answer 长时间停留在 `PENDING_PROGRAMMING_JUDGE`
- [x] 补齐回归测试，覆盖死锁不再复现、answer 级 job 成功/失败终态，以及失败诊断信息
- [x] 同步更新 judge 规格、README / 可靠性说明、active 执行计划与工作记忆
- [x] 执行 `bash ./mvnw spotless:apply` 与最小必要 judge 集成测试
- **Status:** completed

### Phase 37：assignment 级批量成绩调整与 CSV 导入导出第一阶段

- [x] 为教师侧新增 assignment 级批量成绩 CSV 模板导出接口
- [x] 为教师侧新增 assignment 级批量成绩 CSV 导入接口，并允许逐行部分成功
- [x] 复用既有单题批改语义，保持批量调整结果与手工批改一致
- [x] 补齐专项集成测试，覆盖模板导出、成功导入与逐行错误反馈
- [x] 同步 grading 规格、README、todo、数据库结构与工作记忆
- **Status:** completed

### Phase 36：成绩申诉与复核第一阶段

- [x] 新增学生侧成绩申诉创建与列表接口
- [x] 新增教师 / 责任助教 assignment 维度的申诉列表与复核接口
- [x] 保持“仅非客观题、仅已发布成绩、同一答案唯一活动申诉”边界
- [x] 在申诉接受时复用单题人工批改写回最终分数与反馈
- [x] 补齐集成测试、数据库迁移、审计动作和文档同步
- **Status:** completed

### Phase 35：成绩册排名与通过率第一阶段

- [x] 为教师侧成绩册和单学生视图补齐开课实例排名与教学班排名
- [x] 为统计报告补齐总体 / 作业 / 班级三层通过人数与通过率
- [x] 保持排名和通过率都为读模型派生结果，不新增持久化列
- [x] 补齐集成测试、grading 规格、README、todo 与工作记忆
- **Status:** completed

### Phase 34：统计报告五档成绩分布第一阶段

- [x] 在现有成绩册 report API 中补齐总体五档成绩分布
- [x] 在按作业统计中补齐已提交学生的五档成绩分布
- [x] 在按班级统计中补齐班级总评五档成绩分布
- [x] 保持既有授权边界和导出接口不变
- [x] 补齐集成测试、grading 规格、README、todo 与工作记忆
- **Status:** completed

### Phase 33：学生侧成绩册 CSV 导出第一阶段

- [x] 为学生侧新增个人成绩册 CSV 导出接口
- [x] 复用既有“我的成绩册”聚合结果，保持导出与页面语义一致
- [x] 保持未发布人工分、人工反馈和人工部分总分的现有隐藏边界
- [x] 补齐集成测试，覆盖学生正常导出与教师误用 `/me` 导出接口被拒绝
- [x] 同步 grading 规格、README、todo 与工作记忆
- **Status:** completed

### Phase 32：assignment 权重与加权总评第一阶段

- [x] 为 `assignments` 新增 assignment 级 `gradeWeight`
- [x] 允许教师在创建 / 编辑草稿作业时设置成绩权重，并保持发布后不可变
- [x] 在教师 / 学生成绩册中补齐加权总分、权重合计和加权得分率
- [x] 在课程 / 班级 CSV 导出中补齐作业权重和每格加权得分
- [x] 在统计报告中补齐加权总分和作业权重第一阶段
- [x] 补齐集成测试、数据库结构文档、产品规格与工作记忆
- **Status:** completed

### Phase 31：成绩册导出与统计报告第一阶段

- [x] 为教师侧 offering / class 成绩册新增 CSV 导出接口
- [x] 复用现有成绩册聚合结果，保证导出语义与页面视图一致
- [x] 保持教师 / 管理员 / 班级助教的既有授权边界
- [x] 为成绩册补齐课程 / 班级统计报告第一阶段，覆盖作业统计与班级对比
- [x] 补齐集成测试，覆盖 offering 导出、class 导出、统计报告与 TA 作用域
- [x] 同步 grading 规格、README、todo 与工作记忆
- **Status:** completed

### Phase 30：接手入口文档五次收口

- [x] 复核 `README.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/product-specs/index.md`、`docs/quality-score.md` 和 `AGENTS.md`
- [x] 修正 `78` 项测试通过和三语言矩阵等滞后表述，同步到 `82` 项测试与 `GO122`
- [x] 把开课实例级评测环境模板和题目级运行环境快照同步到接手入口
- [x] 将本轮结论写回工作记忆，方便下一轮直接继续开发
- **Status:** completed

### Phase 29：接手入口文档四次收口

- [x] 复核 `README.md`、`docs/index.md`、`docs/product-sense.md`、`docs/quality-score.md` 与当前活动计划
- [x] 明确题库分类、草稿作业编辑和最近一次 `78` 项测试通过的验证基线
- [x] 修正活动计划中“题库仍缺分类”的滞后表述
- [x] 将本轮结论写回工作记忆，方便下一轮直接继续开发
- **Status:** completed

### Phase 24：接手文档与仓库入口二次收口

- [x] 复核 `ARCHITECTURE.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/product-specs/index.md`、`docs/quality-score.md`
- [x] 修正 RabbitMQ 仍被描述为“未来扩展位”等口径漂移
- [x] 把评测队列、详细评测报告、学生侧成绩册和当前下一步优先级同步到入口文档
- [x] 保持 active 路线图只描述仍在推进的长期计划
- **Status:** completed

### Phase 26：仓库状态复核与入口文档三次收口

- [x] 复核 `README.md`、`AGENTS.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/quality-score.md`
- [x] 修正“仍停留在课程第一切片 / 最小工作区”的旧表述
- [x] 明确在线 IDE 后端契约已具备模板工作区、修订历史、历史恢复和自定义试运行
- [x] 明确当前开发者应从 README、仓库结构说明、产品规格索引和 active 计划进入
- 后续如继续做后端开发，应把当前总计划进一步下钻到“多语言稳定化 / 题库分类 / 成绩导出”的下一张执行计划
- **Status:** completed

## Skills 选择

- `planning-with-files`：持续维护多阶段计划、发现和进度记录。
- `springboot-patterns`：保持 `assignment / submission / grading / judge` 的职责稳定。
- `documentation-writer`：把重规划结论同步到仓库文档与执行计划。
- `springboot-tdd`：后续每个切片继续用集成测试驱动实现。
- `springboot-verification`：维持专项测试与全量验证闭环。
- `postgresql-table-design`：后续目录树工作区和日志产物建模时约束表结构。
- `api-design-principles`：保持既有 REST API 兼容，只做追加式演进。

## 阶段

### 基线能力：结构化作业主链路

- [x] 课程级与班级级发布
- [x] 结构化试卷与多题型配置
- [x] 多次提交与版本记录
- [x] 客观题自动评分与人工批改
- [x] assignment 级成绩发布
- [x] question-level judge、样例试运行与最小工作区
- [x] 教师侧成绩册第一阶段
- **Status:** completed

### Phase 14：现状核对与路线重排

- [x] 对照最新需求盘点 `assignment / submission / grading / judge` 的真实边界
- [x] 划分“已完成 / 部分完成 / 缺口”
- [x] 冻结最佳模块职责拆分和兼容约束
- [x] 同步 `todo.md`、执行计划和工作记忆
- **Status:** completed

### Phase 15：在线 IDE 第二阶段

- [x] 把编程工作区升级为目录树快照与多文件编辑的数据模型
- [x] 支持入口文件选择，并让样例试运行与正式评测复用同一份工作区快照
- [x] 为工作区补充更稳定的项目快照语义
- [x] 提供工作区模板快照、模板重置和最近一次标准输入回填
- [x] 提供后端文件 / 目录操作、工作区修订历史、修订详情与恢复接口
- [x] 支持按当前工作区快照或历史修订发起自定义标准输入试运行
- [ ] 提供前端目录树体验、编辑器能力和更实时的草稿同步协议
- **Status:** in_progress

### Phase 16：多语言运行时稳定化

- [x] 补齐四种语言的样例试运行与正式评测集成测试
- [x] 固化 `PYTHON3 / JAVA21 / CPP17 / GO122` 的源码装配与运行模板
- [x] 保留 `JAVA17` 作为兼容输入，统一映射到现有 Java 运行模板
- [x] 统一编译失败、运行失败与资源超限的日志摘要格式
- [ ] 明确当前 V1 支持矩阵与后续扩展语言入口
- **Status:** in_progress

### Phase 17：题库与组卷第二阶段

- [x] 为题库补齐更新、归档与更清晰的引用约束
- [x] 增加标签与标签检索第一阶段
- [x] 增加分类、分类列表与分类过滤第一阶段
- [x] 支持教师编辑草稿作业并整体替换结构化试卷
- [ ] 增强组卷能力，支持更稳定的题库选题与试卷编辑
- [ ] 保持 assignment 快照不可变，不直接引用运行中的题库实体
- **Status:** in_progress

### Phase 18：成绩与反馈第二阶段

- [x] 补齐学生侧成绩视图与已发布成绩面板
- [x] 增加课程 / 班级成绩导出与报表第一阶段
- [x] 为多作业聚合与加权总评补齐 assignment 权重第一阶段
- [ ] 保持“发布前隐藏人工评分”的现有可见性边界
- **Status:** in_progress

### Phase 19：判题可复现性与日志第二阶段

- [x] 持久化测试点级详细评测日志与执行元数据到 `judge_jobs.detail_report_json`
- [x] 提供学生 / 教师详细评测报告 API，并按角色脱敏隐藏测试数据
- [x] 为正式评测补充 `compileArgs / runArgs` 与执行命令可见性
- [x] 为样例试运行补充 `detail_report_json`、输入模式与工作区修订引用
- [ ] 为正式评测和样例试运行补充更可回放的结果模型
- [ ] 在受控边界内扩展 `CUSTOM_SCRIPT` 的脚本打包与执行上下文
- **Status:** in_progress

### Phase 23：评测队列与详细报告第一阶段

- [x] 新增 RabbitMQ 驱动的评测入队与 consumer 执行链路
- [x] 保留队列关闭时的应用内异步回退路径
- [x] 为 legacy judge 和 question-level judge 持久化详细评测报告
- [x] 新增学生 / 教师详细评测报告接口与真实集成测试
- [x] 补齐 `compileArgs / runArgs` 与 C++ 多文件正式评测、样例试运行验证
- **Status:** completed

### Phase 20：仓库状态检查与文档整理

- [x] 盘点 README、文档索引、仓库结构说明和 active 计划是否与当前代码一致
- [x] 修正模块列表、成绩能力口径和下一步开发优先级
- [x] 归档已完成但仍留在 `docs/exec-plans/active/` 的旧计划
- [x] 同步根目录接手入口，方便后续直接继续开发
- **Status:** completed

### Phase 21：真实 go-judge 验证与 fake judge 清理

- [x] 用真实 go-judge Testcontainers 运行 legacy judge、question-level judge 和样例试运行
- [x] 清理测试中的 fake go-judge 服务器与旧标记断言
- [x] 修正真实 go-judge 状态映射与 HTTP 请求模型兼容问题
- [x] 将仓库文档和计划统一到 `JAVA21` 与真实引擎验证口径
- **Status:** completed

### Phase 22：接手入口与验证路径收口

- [x] 复核 `README.md`、`docs/index.md`、`docs/repository-structure.md` 与 active 计划入口是否仍反映当前状态
- [x] 统一主入口文档中的验证命令，显式说明当前 Unix 环境使用 `bash ./mvnw ...`
- [x] 把仓库状态检查与文档整理结论写回工作记忆，便于下一轮继续开发
- **Status:** completed

### Phase 24：评测运行环境与语言模板第一阶段

- [x] 为结构化编程题补齐题目级 `executionEnvironment` 快照模型与校验
- [x] 为教师侧题库 / 作业接口补齐运行环境请求模型
- [x] 扩展 `ProgrammingLanguage`，新增 `GO122`
- [x] 通过真实 go-judge `/run` 支持 Go 多文件工程正式评测与样例试运行
- [x] 用 `copyOut / copyIn` 复用编译阶段产物，保证两阶段执行时编译结果不丢失
- [x] 新增开课实例级 `judge_environment_profiles`，供教师按课程维护可复用的语言环境模板
- [x] 支持编程题通过 `languageExecutionEnvironments` 按语言引用环境模板，并在题库题目 / assignment 快照中固化解析结果
- [x] 用真实 go-judge 集成测试验证“模板引用 -> assignment 快照 -> 正式评测 / 样例试运行”的完整链路
- **Status:** completed

## 已做决策

| Decision | Rationale |
|----------|-----------|
| `assignment` 继续负责作业头、题库和试卷快照 | 这些内容共同定义了“作业长什么样”，不应拆散到执行或提交域 |
| `submission` 继续负责提交版本、分题答案、附件和工作区状态 | 这些都属于“学生提交了什么”的事实模型 |
| `judge` 只负责样例试运行、正式评测和日志产物 | 执行型系统应与题库、人工批改和成绩发布解耦 |
| `grading` 继续负责人工批改、成绩发布和成绩册 | 评分可见性、反馈和统计应集中在一个域内演进 |
| 下一优先级先补在线 IDE，再补多语言稳定化 | 当前最显著的产品缺口是“能评测但不像真正 IDE”，先补工作区模型最稳 |
| 多语言 V1 先收敛为 `PYTHON3 / JAVA21 / CPP17 / GO122` | 当前代码、运行镜像和真实 go-judge 验证已覆盖这四种语言；`JAVA17` 仅作为兼容输入保留，更复杂版本矩阵延后 |
| 编译失败继续映射到 `RUNTIME_ERROR` 而不是新增 verdict | 现有 API 和存储枚举已被学生侧、教师侧与样例试运行复用，先稳定摘要口径比扩枚举更稳 |
| 题库生命周期与成绩导出排在 IDE / 运行时之后 | 这两块重要，但不会阻断学生完成编程题主链路 |
| 文档入口优先集中到 README / docs/index / repository-structure 三处 | 继续开发时应先解决入口失真，而不是继续新增重复说明文档 |
| 评测详细报告先落库到 `judge_jobs.detail_report_json`，暂不直接落对象存储 | 先稳定 API 与权限脱敏边界，再决定日志产物对象化策略 |
| RabbitMQ 队列先做单队列单 consumer 入口，并保留本地异步回退 | 先验证真实引擎与消息链路闭环，避免一次性引入独立 worker 与重试编排 |
| 在线 IDE 第二阶段先补后端工作区与试运行契约，不在当前仓库内实现浏览器编辑器能力 | 当前仓库是后端服务，优先保证模板、目录树、版本恢复、试运行与评测环境一致性 |
| 评测环境模板当前先收敛到开课实例级 `judge_environment_profiles` | 教师最常见的复用边界是同一门课内的多题共享环境；先避免过早引入平台级环境中心 |
| 题目引用环境模板时必须在发布前解析并固化为 assignment question snapshot | 题库题目和环境模板后续仍可变，正式评测必须绑定创建作业时的稳定环境快照 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| 暂无本轮实现错误 | 当前阶段以重规划与文档同步为主 | 后续进入实现切片时继续追加 |
| `./mvnw` 在当前环境返回 `Permission denied` | 1 | 改用 `bash ./mvnw ...` 完成格式化与验证，无需修改仓库权限位 |
| 真实 go-judge 返回 `Nonzero Exit Status` 未被正确映射 | 1 | 扩展 `mapEngineVerdict(...)` 兼容官方状态值，避免把用户代码失败误判为 `SYSTEM_ERROR` |
| go-judge 对 `files` 数组执行联合类型校验，`null` 字段会触发 400 | 1 | 将 `GoJudgeClient` 的 stdin/stdout/stderr 文件描述符拆成真实联合模型，并为空 stdin 发送空内容而不是 `null` |
| 评测任务在 RabbitMQ 路径下可能出现 `finished_at < started_at` 约束冲突 | 1 | 在 `JudgeExecutionService` 中将 `startedAt / finishedAt` 归一到不早于排队时间与开始时间，避免时序抖动触发表约束 |
| 工作区从历史修订恢复时，`last_stdin_text` 无法被清空 | 1 | 为 `ProgrammingWorkspaceEntity.lastStdinText` 增加 `FieldStrategy.ALWAYS`，确保恢复空值时也会写回数据库 |
| 真实 go-judge 的每次 `/run` 都是全新沙箱，直接拆成两次调用会丢失编译产物 | 1 | 通过 `copyOut / copyIn` 回传编译阶段产物，再在第二阶段恢复执行，保证 Go 多文件工程等场景稳定运行 |

## Session: 2026-04-17 真实运行与 API 联调验证

### Phase 54：真实依赖联调、OpenAPI 契约提取与 API 验证

- [ ] 确认启动主类、Maven/Wrapper 命令、Compose 编排、必要环境变量与 bootstrap 初始化路径
- [ ] 真实拉起 PostgreSQL、RabbitMQ、MinIO、go-judge，并记录依赖健康状态与关键端口
- [ ] 真实启动 Spring Boot 应用与 judge worker，确认 Flyway、readiness、`/v3/api-docs`、Swagger UI
- [ ] 从运行时 `/v3/api-docs` 自动提取全部 API 并按模块分类统计
- [ ] 获取管理员真实 token，并通过真实接口准备教师、学生、课程与业务测试数据
- [ ] 对公开 REST API 执行正向、权限、越权与非法参数验证，重点覆盖认证、课程、作业、提交、评测、批改、成绩册、实验、通知
- [ ] 汇总通过、失败、阻塞、风险与证据，输出《AUBB Server 真实 API 验证报告》
- **Status:** in_progress
