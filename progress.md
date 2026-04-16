# 进度日志

## Session: 2026-04-17 Dockerfile、CI 与发布流水线基线

### Phase 42：优先级 5 工程化交付收口

- **Status:** completed
- **Started:** 2026-04-17
- Actions taken:
  - 读取 `pom.xml`、`compose.yaml`、`.github/`、`README.md`、`application.yaml`、`docs/index.md`、`docs/reliability.md`、`docs/object-storage.md`
  - 确认当前仓库没有应用自身 `Dockerfile`、`.dockerignore`、GitHub Actions workflows 或部署编排文件，工程化资产只覆盖基础设施 `compose` 和 go-judge 运行镜像
  - 确认根 `compose.yaml` 当前只拉起基础设施，不包含应用服务本身；README 也明确写了这一点
  - 确认仓库运行时已经启用 `spring-boot-docker-compose` 依赖，因此根 `compose.yaml` 若直接无条件加入 `app` 服务，会破坏宿主机 `spring-boot:run` 的现有使用方式
  - 收敛最小方案为：根目录 `Dockerfile` + `.dockerignore`、根 `compose.yaml` 使用 `app` profile 承载应用容器、本地与部署各自独立的 compose 用途、GitHub Actions 固化 `verify -> image -> deploy`
  - 新增根目录 `Dockerfile`、`.dockerignore`、`deploy/compose.yaml` 和 `deploy/.env.production.example`
  - 新增 `.github/workflows/ci.yml` 与 `.github/workflows/deploy.yml`，落地 `verify -> image -> deploy` 最小基线
  - 将根 `compose.yaml` 扩展为“默认 infra + `app` profile 联调”模式，并为 PostgreSQL / RabbitMQ / Redis / MinIO 补齐健康检查和固定版本
  - 在真实本地 compose smoke 中发现 `${AUBB_JWT_SECRET:?…}` 会破坏 infra-only 的 `compose up/down/config`，因此改为 app 容器入口 fail-fast 校验
  - 补充 `DeliveryPipelineAssetsTests`，覆盖 Dockerfile、workflows、deploy compose 与本地 compose 关键约束
  - 同步更新 `README.md`、`docs/deployment.md`、`docs/index.md`、`docs/reliability.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `git diff --check`
  - `bash ./mvnw -Dtest=DeliveryPipelineAssetsTests,AubbServerApplicationTests,HarnessHealthSmokeTests test`
  - `docker build -t aubb-server:local .`
  - `docker compose --profile app config`
  - `docker compose -f deploy/compose.yaml --env-file deploy/.env.production.example config`
  - `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.7`
  - `AUBB_JWT_SECRET=compose-local-jwt-secret-with-at-least-32-chars AUBB_GO_JUDGE_PORT=15050 docker compose --profile app up -d --build`
  - `curl -fsS http://localhost:8080/actuator/health`
  - `docker compose --profile app down -v`
  - 当前结果：`BUILD SUCCESS`，定向 `5` 个测试通过；本地 compose app + 基础设施联调和部署 compose 配置校验通过

## Session: 2026-04-17 首个学校 / 管理员 bootstrap 初始化闭环

### Phase 41：优先级 4 初始化入口收口

- **Status:** completed
- **Started:** 2026-04-17
- Actions taken:
  - 读取 `docs/product-specs/platform-governance-and-iam.md`、`todo.md`、`docs/design-docs/adr-0002-jwt-and-scoped-governance.md`
  - 复核 `identityaccess / organization / platformconfig` 模块 application service、controller、domain policy 与 V1 / V2 / V23 migration
  - 确认当前已具备“建学校根节点 / 建学校管理员 / 写平台配置”三段业务能力，但都依赖先有 `SCHOOL_ADMIN`
  - 确认代码库里没有业务级 bootstrap command、startup runner、seed 文件或 Flyway 初始化数据；当前唯一启动期初始化模式是 `MinioStorageConfiguration` 的条件化 `ApplicationRunner`
  - 通过子代理复核现有可复用调用路径、幂等风险和潜在冲突点，收敛“受配置开关控制的启动期 bootstrap runner + 幂等 application service”为最小方案
  - 新增 `PlatformBootstrapProperties`、`PlatformBootstrapConfiguration` 与 `PlatformBootstrapApplicationService`，用默认关闭的启动期 runner 执行首个学校 / 管理员 / 平台配置初始化
  - 为 bootstrap 补齐“只创建缺失项、不重置既有管理员密码”的幂等语义，并补记角色、画像和审计
  - 新增 `V24__single_school_root_guard.sql`，在数据库层约束根节点必须是单一 `SCHOOL`
  - 扩展 `OrganizationApplicationService`，显式拒绝第二个学校根节点创建
  - 新增 `PlatformBootstrapPropertiesValidationTests` 和 `BootstrapInitializationIntegrationTests`，覆盖启动失败、首次初始化、重复执行不脏写、管理员登录和平台配置读取
  - 同步 `README.md`、`docs/security.md`、`docs/reliability.md`、`docs/product-specs/platform-governance-and-iam.md`、`docs/design-docs/adr-0002-jwt-and-scoped-governance.md` 与 `docs/generated/db-schema.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `git diff --check`
  - `bash ./mvnw -Dtest=PlatformBootstrapPropertiesValidationTests,BootstrapInitializationIntegrationTests test`
  - `bash ./mvnw -Dtest=PlatformBootstrapPropertiesValidationTests,BootstrapInitializationIntegrationTests,PlatformGovernanceApiIntegrationTests,AubbServerApplicationTests,HarnessHealthSmokeTests test`
  - 当前结果：`BUILD SUCCESS`，定向 `19` 个测试通过

## Session: 2026-04-17 refresh token / revoke / 强制失效

### Phase 40：优先级 3 认证会话闭环

- **Status:** completed
- **Started:** 2026-04-17
- Actions taken:
  - 读取 `AuthController`、`AuthenticationApplicationService`、`JwtTokenService`、`JwtPrincipalAuthenticationConverter`、`SecurityConfig`、`AuthApiIntegrationTests`、`PlatformGovernanceApiIntegrationTests`、`docs/security.md`、`docs/product-specs/platform-governance-and-iam.md` 和 `V1/V2` 迁移
  - 确认当前系统只有 access token，`logout` 仅写审计，没有 refresh token、session 持久化或 revoke 状态
  - 确认当前 Bearer 鉴权直接信任 JWT claim 构造 principal，不会回查会话或用户状态，这是“旧 token 不能立即失效”的根因
  - 收敛最小数据模型为 `auth_sessions`，计划通过 `sid` claim + 每请求 session 校验补齐 logout / revoke / 禁用 / 管理员强制失效
  - 新增 `V23__auth_sessions_refresh_tokens.sql`、`AuthSessionEntity`、`AuthSessionMapper` 和 `AuthSessionApplicationService`
  - 新增 `OpaqueRefreshTokenCodec`，实现 opaque refresh token 签发、解析、哈希匹配和 refresh token 轮换
  - 让 `AuthController` 补齐 `POST /api/v1/auth/refresh`、`POST /api/v1/auth/revoke`，并让 `logout` 真正撤销当前会话
  - 在 JWT access token 中新增 `sid` / `tokenType` claim，并通过 `AccessTokenSessionValidator` + `auth_sessions` 做每请求会话校验
  - 抽出 `AuthenticatedPrincipalLoader` 打破 `SecurityConfig` 与登录服务的 Bean 环，并复用到 refresh / access token 校验路径
  - 在 `UserAdministrationApplicationService` 和 `UserAdminController` 中补齐管理员强制失效接口，以及账号停用后的批量 session invalidation
  - 扩展 `AuthApiIntegrationTests`、`PlatformGovernanceApiIntegrationTests`，覆盖 refresh、logout、revoke、disable 和 admin invalidate；新增 `AccessTokenSessionValidatorTests`
  - 同步 `README.md`、`docs/security.md`、`docs/reliability.md`、`docs/product-specs/platform-governance-and-iam.md`、`docs/generated/db-schema.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=AccessTokenSessionValidatorTests,OpaqueRefreshTokenCodecTests,JwtTokenServiceTests,AuthApiIntegrationTests,PlatformGovernanceApiIntegrationTests test`
  - 当前结果：`BUILD SUCCESS`，定向 `27` 个测试通过

## Session: 2026-04-17 JWT 默认密钥治理

### Phase 39：优先级 2 认证密钥基线收口

- **Status:** completed
- **Started:** 2026-04-17
- Actions taken:
  - 读取 `application.yaml`、`SecurityConfig`、`JwtTokenService`、`AuthController`、`AuthApiIntegrationTests`、`AbstractIntegrationTest`、`AbstractRealJudgeIntegrationTest`、`AubbServerApplicationTests`、`HarnessHealthSmokeTests`、`MinioStorageIntegrationTests`、`docs/security.md`
  - 确认当前 JWT secret 仍通过 `${AUBB_JWT_SECRET:change-this-secret-key-change-this-secret-key}` 和 `@Value(...:default)` 提供弱默认值
  - 确认现有登录与鉴权链路本身已经稳定，真正需要收口的是“配置来源统一 + 缺失时 fail-fast + 测试显式注入”
  - 新增 `JwtSecurityProperties`，在配置绑定阶段校验 `issuer / ttl / secret`，并复用到 `SecurityConfig` 与 `JwtTokenService`
  - 将主配置中的 JWT secret 改为 `${AUBB_JWT_SECRET}` 无默认回退，建立缺失即失败的启动基线
  - 新增 `src/test/resources/application.properties`，集中为全部 `SpringBootTest` 注入测试专用 `AUBB_JWT_SECRET`
  - 新增 `JwtSecurityPropertiesValidationTests` 覆盖“未配置密钥时启动失败”，并执行 `AuthApiIntegrationTests`、`AubbServerApplicationTests`、`HarnessHealthSmokeTests`、`MinioStorageIntegrationTests`
  - 更新 `README.md`、`docs/security.md`、`docs/reliability.md` 与执行计划，补齐本地运行示例和部署说明
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=JwtSecurityPropertiesValidationTests,AuthApiIntegrationTests,AubbServerApplicationTests,HarnessHealthSmokeTests,MinioStorageIntegrationTests test`
  - 当前结果：`BUILD SUCCESS`，定向 `13` 个测试通过

## Session: 2026-04-17 judge 死锁与终态超时修复

### Phase 38：优先级 1 judge 稳定性收口

- **Status:** completed
- **Started:** 2026-04-17
- Actions taken:
  - 读取 `todo.md`、`StructuredProgrammingJudgeIntegrationTests`、`JudgeQueueConfiguration`、`JudgeApplicationService`、`JudgeExecutionService`、`SubmissionApplicationService`、`SubmissionAnswerApplicationService`
  - 复核 Rabbit 队列和本地 `@Async` 监听条件，确认队列开启时不会发生双 listener 同时消费
  - 定位当前风险为“测试清理策略与异步评测事务边界冲突 + 固定轮询窗口过强”，并识别失败分支缺少 answer 级显式失败终态
  - 在 `AbstractRealJudgeIntegrationTest` 中新增 `resetJudgeTables(...)`，先 purge 测试队列、等待运行中 job 收口，并在检测到死锁时重试 `TRUNCATE`
  - 将 `JudgeIntegrationTests`、`StructuredProgrammingJudgeIntegrationTests`、`ProgrammingWorkspaceIntegrationTests` 改为复用统一清理 helper，并增强超时时的诊断信息
  - 为 `submission_answers.grading_status` 新增 `PROGRAMMING_JUDGE_FAILED`，并在 `JudgeExecutionService` 中把 job 终态提交与 answer / audit side effects 拆成两个事务，补充失败日志
  - 新增 `SubmissionAnswerGradingStatusTests` 单元测试，以及 `judgeCleanupDrainsAsyncWorkBeforeTruncate` 回归集成测试
  - 更新 `README.md`、`docs/product-specs/judge-system.md`、`docs/reliability.md`、`docs/generated/db-schema.md`、`docs/quality-score.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=SubmissionAnswerGradingStatusTests,JudgeIntegrationTests,StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests test`
  - 当前结果：`BUILD SUCCESS`，定向 `23` 个测试通过

## Session: 2026-04-16 成绩系统第二阶段

### Phase 35：成绩册排名与通过率第一阶段

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 在 `GradebookApplicationService` 中为教师侧成绩册和单学生视图补齐 `offeringRank / teachingClassRank`
  - 在统计报告总体、按作业和按班级三层补齐 `passedStudentCount / passRate`
  - 保持排名和通过率都作为读模型派生结果，不新增数据库列
  - 扩展 `GradebookIntegrationTests`，固定成绩册排名和通过率统计语义
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookApplicationService.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookPageView.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookReportView.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/StudentGradebookView.java`
  - `src/test/java/com/aubb/server/integration/GradebookIntegrationTests.java`

### Phase 36：成绩申诉与复核第一阶段

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 新增 `V21__grade_appeals_phase1.sql`，引入 `grade_appeals`
  - 新增学生侧成绩申诉创建 / 列表接口，以及教师 / 责任助教的 assignment 维度申诉列表 / 复核接口
  - 保持“仅非客观题、仅已发布成绩、同一答案唯一活动申诉”边界，并在接受申诉时复用单题人工批改写回分数与反馈
  - 扩展 `GradingIntegrationTests`，覆盖申诉创建、重复申诉拦截、越权复核被拒绝和申诉接受后分数写回
- Files created/modified:
  - `src/main/resources/db/migration/V21__grade_appeals_phase1.sql`
  - `src/main/java/com/aubb/server/modules/grading/application/appeal/**`
  - `src/main/java/com/aubb/server/modules/grading/api/MyGradeAppealController.java`
  - `src/main/java/com/aubb/server/modules/grading/api/TeacherGradeAppealController.java`
  - `src/main/java/com/aubb/server/modules/grading/domain/appeal/GradeAppealStatus.java`
  - `src/main/java/com/aubb/server/modules/grading/infrastructure/appeal/**`
  - `src/main/java/com/aubb/server/modules/audit/domain/AuditAction.java`
  - `src/test/java/com/aubb/server/integration/GradingIntegrationTests.java`

### Phase 37：assignment 级批量成绩调整与 CSV 导入导出第一阶段

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 在 `GradingApplicationService` 中补齐 assignment 级批量成绩 CSV 模板导出和 CSV 导入能力
  - 在 `TeacherGradingController` 中新增模板导出、CSV 导入接口，并保留既有 JSON `batch-adjust`
  - 保持导入逐行处理和逐行错误反馈，成功行继续复用单题批改语义写回
  - 扩展 `GradingIntegrationTests`，覆盖模板导出、成功导入和部分失败时的逐行报错
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/application/GradingApplicationService.java`
  - `src/main/java/com/aubb/server/modules/grading/application/BatchGradeImportErrorView.java`
  - `src/main/java/com/aubb/server/modules/grading/application/BatchGradeImportResultView.java`
  - `src/main/java/com/aubb/server/modules/grading/application/GradeImportTemplateContent.java`
  - `src/main/java/com/aubb/server/modules/grading/api/TeacherGradingController.java`
  - `src/test/java/com/aubb/server/integration/GradingIntegrationTests.java`

### Phase 35 / 36 / 37 验证

- 已执行 `bash ./mvnw spotless:apply`
- 已执行 `bash ./mvnw -Dtest=GradingIntegrationTests test`
- 已执行 `bash ./mvnw clean verify`
- 当前结果：`BUILD SUCCESS`，全量 `92` 个测试通过

## Session: 2026-04-16 作业模块能力重规划

## Session: 2026-04-16 接手入口文档四次收口

### Phase 29：项目状态复核与入口同步

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 复核 `README.md`、`docs/index.md`、`docs/product-sense.md`、`docs/quality-score.md` 与活动计划 `2026-04-16-assignment-module-replan.md`
  - 把题库标签与分类、结构化作业草稿编辑，以及最近一次 `BUILD SUCCESS / 78` 项测试通过的验证基线同步到接手入口文档
  - 修正活动计划和发现记录中“题库仍缺分类”的滞后表述，明确剩余缺口是更稳定的组卷编辑体验
  - 追加 README 接手路径，方便下一位开发者直接从入口文档进入产品规格和 active 计划
- Files created/modified:
  - `README.md`
  - `docs/index.md`
  - `docs/product-sense.md`
  - `docs/quality-score.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

## Session: 2026-04-16 接手入口文档五次收口

### Phase 30：评测运行环境切片后的入口同步

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 复核 `README.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/product-specs/index.md`、`docs/quality-score.md` 和 `AGENTS.md`
  - 把最近一次验证基线从 `BUILD SUCCESS / 78` 项测试通过修正为 `BUILD SUCCESS / 82` 项测试通过
  - 把“多语言运行时稳定化”入口口径从三语言更新为 `PYTHON3 / JAVA21 / CPP17 / GO122`
  - 把开课实例级评测环境模板、题目级运行环境快照和 `GO122` 真实评测链路同步到接手入口与数据库参考文档
  - 同步更新 `task_plan.md`、`findings.md`、`progress.md` 和 `todo.md`，方便下一轮直接继续开发
- Files created/modified:
  - `README.md`
  - `docs/index.md`
  - `docs/repository-structure.md`
  - `docs/product-sense.md`
  - `docs/product-specs/index.md`
  - `docs/product-specs/judge-system.md`
  - `docs/generated/db-schema.md`
  - `docs/quality-score.md`
  - `AGENTS.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - 对本轮改动文件执行定向 `git diff --check`
  - 本轮仅整理文档与工作记忆，沿用最近一次代码基线：`bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `82` 个测试通过

## Session: 2026-04-16 成绩册导出与统计报告第一阶段

### Phase 31：教师侧成绩册从“可浏览”推进到“可导出 / 可统计”

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 在 `GradebookApplicationService` 中新增 offering / class 成绩册 CSV 导出能力，复用既有成绩册聚合结果，保持导出语义与页面视图一致
  - 在 `TeacherGradebookController` 中新增课程级 / 班级级成绩册导出接口，沿用现有教师 / 管理员 / 班级助教授权边界
  - 新增 `GradebookReportView`，为 offering / class 成绩册补齐统计报告第一阶段，覆盖总体摘要、按作业统计和按班级对比
  - 扩展 `GradebookIntegrationTests`，覆盖 offering 导出、class 导出、offering 报告和班级 TA 读取报告边界
  - 同步 `README.md`、`docs/product-specs/grading-system.md`、`docs/product-sense.md`、`docs/product-specs/index.md`、`todo.md` 和执行计划，统一成绩导出 / 统计报告口径
  - 已执行 `bash ./mvnw spotless:apply`
  - 已执行 `bash ./mvnw -Dtest=GradebookIntegrationTests test`
  - 已执行 `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests test`
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookApplicationService.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookExportContent.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookReportView.java`
  - `src/main/java/com/aubb/server/modules/grading/api/TeacherGradebookController.java`
  - `src/test/java/com/aubb/server/integration/GradebookIntegrationTests.java`
  - `README.md`
  - `docs/product-specs/index.md`
  - `docs/product-specs/grading-system.md`
  - `docs/product-sense.md`
  - `todo.md`
  - `docs/exec-plans/completed/2026-04-16-gradebook-export-and-report-phase1.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=GradebookIntegrationTests test`
  - `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests test`
  - `bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `87` 个测试通过

## Session: 2026-04-16 assignment 权重与加权总评第一阶段

### Phase 32：成绩能力从“可导出 / 可统计”推进到“可表达基础总评规则”

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 新增 `V20__assignment_grade_weights_phase1.sql`，为 `assignments` 补齐 assignment 级 `grade_weight`
  - 扩展教师侧创建 / 编辑草稿作业接口，允许设置 `gradeWeight`，并在发布后保持不可变
  - 在 `GradebookApplicationService` 中为教师 / 学生成绩册补齐加权总分、权重合计和加权得分率
  - 在课程 / 班级 CSV 导出中补齐作业权重和每格加权得分，在统计报告中补齐加权总分与作业权重第一阶段
  - 扩展 `AssignmentIntegrationTests`、`StructuredAssignmentIntegrationTests`、`GradebookIntegrationTests`，固定 assignment 权重、加权总分和报表导出语义
  - 同步 `README.md`、assignment / grading 规格、数据库结构、`todo.md` 与工作记忆
- Files created/modified:
  - `src/main/resources/db/migration/V20__assignment_grade_weights_phase1.sql`
  - `src/main/java/com/aubb/server/modules/assignment/**`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/**`
  - `src/test/java/com/aubb/server/integration/AssignmentIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/GradebookIntegrationTests.java`
  - `README.md`
  - `docs/product-specs/assignment-system.md`
  - `docs/product-specs/grading-system.md`
  - `docs/generated/db-schema.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=AssignmentIntegrationTests,StructuredAssignmentIntegrationTests,GradebookIntegrationTests,GradingIntegrationTests test`
  - `bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `87` 个测试通过

## Session: 2026-04-16 学生侧成绩册 CSV 导出第一阶段

### Phase 33：学生侧成绩能力从“可查看”推进到“可留存 / 可复核”

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 在 `MyGradebookController` 中新增学生侧个人成绩册 CSV 导出接口 `GET /api/v1/me/course-offerings/{offeringId}/gradebook/export`
  - 在 `GradebookApplicationService` 中复用既有 `buildStudentGradebook(...)` 聚合结果，补齐学生侧 CSV 渲染，保持导出与页面语义一致
  - 修复学生侧 CSV 渲染时 `List.of(...)` 遇到 `null` 字段触发 `NullPointerException` 的实现错误，改用可空行构造方式
  - 扩展 `GradebookIntegrationTests`，覆盖学生成功导出、教师误用 `/me` 导出被拒绝，以及未发布人工分在导出中继续隐藏
  - 同步 `README.md`、grading 规格、active 计划、`todo.md` 与工作记忆，统一“学生侧成绩册与 CSV 导出第一阶段”口径
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/api/MyGradebookController.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookApplicationService.java`
  - `src/test/java/com/aubb/server/integration/GradebookIntegrationTests.java`
  - `README.md`
  - `docs/product-sense.md`
  - `docs/product-specs/index.md`
  - `docs/product-specs/grading-system.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `docs/exec-plans/completed/2026-04-16-student-gradebook-export-phase1.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests test`
  - `bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `87` 个测试通过

## Session: 2026-04-16 统计报告五档成绩分布第一阶段

### Phase 34：成绩统计从“有均值”推进到“可快速诊断分布”

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 在 `GradebookReportView` 中为总体、按作业和按班级统计追加统一的五档成绩分布结构
  - 在 `GradebookApplicationService` 中基于现有 report 聚合结果计算 `EXCELLENT / GOOD / MEDIUM / PASS / FAIL` 五档分布
  - 保持既有 report API 路径、授权边界和导出接口不变，不新增统计表
  - 扩展 `GradebookIntegrationTests`，固定 offering / class report 的分布统计语义
  - 同步 README、grading 规格、todo、active 计划与工作记忆，统一“统计报告五档成绩分布第一阶段”口径
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookReportView.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookApplicationService.java`
  - `src/test/java/com/aubb/server/integration/GradebookIntegrationTests.java`
  - `README.md`
  - `docs/product-sense.md`
  - `docs/product-specs/index.md`
  - `docs/product-specs/grading-system.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `docs/exec-plans/completed/2026-04-16-gradebook-score-bands-phase1.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests test`
  - `bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `87` 个测试通过

## Session: 2026-04-16 仓库状态复核与入口文档三次收口

### Phase 26：接手入口与仓库口径再次校准

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 复核 `README.md`、`AGENTS.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/quality-score.md` 和 active 计划入口
  - 修正仍停留在“课程第一切片 / 最小工作区”的旧表述，把模板工作区、工作区修订历史、历史恢复和自定义标准输入试运行同步到入口文档
  - 把“下一位开发者从哪里开始看”统一到 README、仓库结构说明、产品规格索引和 active 计划四个入口
  - 记录当前工作区存在与本任务无关的既有脏改动，因此收口时改用“定向 `git diff --check`”而不是全仓 diff 检查
- Files created/modified:
  - `AGENTS.md`
  - `docs/index.md`
  - `docs/repository-structure.md`
  - `docs/product-sense.md`
  - `docs/quality-score.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

## Session: 2026-04-16 在线 IDE 第二阶段后端

## Session: 2026-04-16 题库分类与分类过滤第一阶段

### Phase 27：题库分类管理基础

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 新增 `V18__question_bank_categories_phase2.sql`，引入 `question_bank_categories`，并为题库题目补充 `category_id`
  - 在 `QuestionBankApplicationService` 中补齐题目分类写入、按分类过滤题库列表，以及教师侧分类列表读取
  - 扩展 `QuestionBankTeacherController`、题库题目视图与请求模型，支持 `categoryName` 和 `category` 过滤
  - 扩展 `StructuredAssignmentIntegrationTests`，覆盖分类创建、分类过滤、分类更新与分类列表计数
- Files created/modified:
  - `src/main/resources/db/migration/V18__question_bank_categories_phase2.sql`
  - `src/main/java/com/aubb/server/modules/assignment/application/bank/**`
  - `src/main/java/com/aubb/server/modules/assignment/infrastructure/bank/**`
  - `src/main/java/com/aubb/server/modules/assignment/api/QuestionBankTeacherController.java`
  - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
  - `README.md`
  - `docs/product-specs/assignment-system.md`
  - `docs/product-specs/index.md`
  - `docs/generated/db-schema.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
  - `todo.md`

## Session: 2026-04-16 草稿作业编辑第一阶段

### Phase 28：教师侧草稿作业更新

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 为教师侧作业管理新增 `PUT /api/v1/teacher/assignments/{assignmentId}`，允许仅在 `DRAFT` 状态下更新作业头
  - 在 `AssignmentApplicationService` 中补齐草稿编辑校验、试卷整体替换和 legacy judge 配置替换
  - 为结构化作业补充集成测试，固定“草稿可改、发布后不可改”的边界
  - 同步更新 README、assignment 产品规格、任务计划与工作记忆
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/assignment/api/AssignmentTeacherController.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/AssignmentApplicationService.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/AssignmentPaperApplicationService.java`
  - `src/main/java/com/aubb/server/modules/audit/domain/AuditAction.java`
  - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
  - `README.md`
  - `docs/product-specs/assignment-system.md`
  - `docs/product-specs/index.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
  - `todo.md`

### Phase 25：模板工作区、修订历史与自定义试运行

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 新增 `V17__online_ide_phase2.sql`，为工作区补充目录列表、最近标准输入、工作区修订表，以及样例试运行的输入模式 / 工作区修订引用 / 详细报告字段
  - 重写 `ProgrammingWorkspaceApplicationService`，补齐模板工作区加载、目录树操作、历史修订列表 / 详情 / 恢复、模板重置和保存种类
  - 重写 `ProgrammingSampleRunApplicationService`，支持从显式快照、当前工作区或历史修订发起样例 / 自定义标准输入试运行，并提供单次运行详情
  - 扩展题目配置，使编程题可携带模板入口文件、模板目录和模板源码文件
  - 扩展 `ProgrammingWorkspaceIntegrationTests`，覆盖模板工作区、历史恢复、自定义输入试运行与详细日志回读
  - 已执行 `bash ./mvnw spotless:apply`
  - 已执行 `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests,SubmissionIntegrationTests test`
- Files created/modified:
  - `src/main/resources/db/migration/V17__online_ide_phase2.sql`
  - `src/main/java/com/aubb/server/modules/assignment/**`
  - `src/main/java/com/aubb/server/modules/submission/**/workspace/**`
  - `src/main/java/com/aubb/server/modules/submission/domain/workspace/**`
  - `src/main/java/com/aubb/server/modules/judge/**/sample/**`
  - `src/main/java/com/aubb/server/modules/judge/application/JudgeExecutionService.java`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `README.md`
  - `ARCHITECTURE.md`
  - `docs/product-specs/*.md`
  - `docs/generated/db-schema.md`
  - `todo.md`

### Phase 14：现状核对与路线重排

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 重新读取 `todo.md`、`task_plan.md`、`findings.md`、`progress.md` 与当前产品规格
  - 使用 Serena、`rg` 和文件级符号检索核对 `assignment / submission / judge / grading` 的真实覆盖度
  - 并行等待两个子代理，分别收敛“结构化作业与成绩能力”和“编程题运行栈 / 工作区能力”的现状结论
  - 冻结新的阶段路线：在线 IDE 第二阶段 -> 多语言运行时稳定化 -> 题库与组卷第二阶段 -> 成绩与反馈第二阶段 -> 判题日志与可复现性第二阶段
  - 新增活动执行计划，并同步 `task_plan.md`、`findings.md`、`todo.md`
- Files created/modified:
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
  - `todo.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `docs/exec-plans/active/README.md`

## Session: 2026-04-16 结构化作业与题库第一阶段

### Phase 1：需求盘点与阶段边界冻结

- **Status:** completed
- Actions taken:
  - 读取 `todo.md`、assignment/submission/judge 产品规格与当前实现
  - 用 Serena 和 `rg` 盘点 assignment、submission、judge 的真实边界
  - 并行启动两个子代理，分别给出“最佳阶段方案”和“最稳扩展点/兼容边界”建议
  - 汇总后确认本轮方向为：`assignment` 管题库与试卷快照，`submission` 管分题作答，`judge` 继续只负责执行型评测
- Files created/modified:
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

### Phase 2：结构化作业数据模型与失败测试

- **Status:** in_progress
- Actions taken:
  - 已开始设计题库、试卷快照和分题答案的最小表结构
  - 已锁定“追加字段、不破坏旧 API 语义”的兼容策略
  - 下一步直接补 Flyway 迁移和集成测试失败用例

## Session: 2026-04-16 todo 驱动的开发推进与提交附件切片

### Phase 1：todo 进度盘点与计划更新

- **Status:** completed
- **Started:** 2026-04-16
- Actions taken:
  - 读取 `todo.md`、`AGENTS.md`、`README.md`、`ARCHITECTURE.md`、`docs/plan.md` 与当前产品规格
  - 恢复 `task_plan.md`、`findings.md`、`progress.md` 工作记忆
  - 盘点当前模块实现，确认仓库已覆盖平台治理、课程、assignment 和 submission 第一切片
  - 锁定当前主链路缺口为“提交附件 / 代码 / 文件 / 报告上传”
- Files created/modified:
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

### Phase 2：提交附件能力设计与实现

- **Status:** completed
- Actions taken:
  - 新增 `V6__submission_artifact_slice.sql`，引入 `submission_artifacts` 表并放宽 `submissions.content_text` 为可空
  - 在 `modules.submission` 中新增附件上传、正式提交关联、学生/教师下载能力
  - 复用 MinIO 共享对象存储，并在业务层显式保留附件元数据与授权边界
  - 新增 `SUBMISSION_ARTIFACT_UPLOADED` 审计动作
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/submission/**`
  - `src/main/resources/db/migration/V6__submission_artifact_slice.sql`
  - `src/main/java/com/aubb/server/modules/audit/domain/AuditAction.java`

### Phase 3：测试与文档同步

- **Status:** completed
- Actions taken:
  - 扩展 `SubmissionIntegrationTests`，覆盖附件上传、正式提交关联、教师下载和附件复用校验
  - 已执行 `bash ./mvnw -Dtest=SubmissionIntegrationTests test` 并通过
  - 已同步 `todo.md`、产品规格、对象存储和数据库结构文档

### Phase 4：judge 第一切片

- **Status:** completed
- Actions taken:
  - 新增 `V7__judge_first_slice.sql`，引入 `judge_jobs` 表
  - 新建 `modules.judge`，覆盖自动入队、学生/教师查询和教师重排队
  - 在 `SubmissionApplicationService` 中接入“提交后自动创建评测作业”
  - 扩展 `SubmissionIntegrationTests`，覆盖自动入队与重排队，并通过专项测试
  - 已同步 judge 产品规格、架构与数据库文档

### Phase 5：验证与提交

- **Status:** in_progress
- Actions taken:
  - 已执行 `bash ./mvnw spotless:apply`
  - 已执行 `bash ./mvnw clean verify`
  - 当前 `BUILD SUCCESS`，共 `48` 个测试通过
  - 正在整理本轮 git 提交范围，排除与本任务无关的既有改动

## Session: 2026-04-16 judge go-judge 执行切片

### Phase 6：go-judge 真实执行与结果回写

- **Status:** completed
- Actions taken:
  - 新增 `V8__judge_go_judge_execution.sql`，引入 `assignment_judge_profiles`、`assignment_judge_cases`，并扩展 `judge_jobs` 聚合结果字段
  - 为 assignment 增加脚本型自动评测配置摘要，当前支持 `PYTHON3 + TEXT_BODY`
  - 新增 go-judge 配置、客户端和 AFTER_COMMIT 异步执行服务
  - 让评测作业支持 `PENDING -> RUNNING -> SUCCEEDED/FAILED` 并回写 verdict、得分、日志摘要、资源指标和错误信息
  - 新增 `JudgeIntegrationTests`，覆盖正确提交、错误提交、go-judge 失败三类路径
  - 调整 `SubmissionIntegrationTests`，明确“未配置自动评测的作业不会自动入队”
- Files created/modified:
  - `src/main/resources/db/migration/V8__judge_go_judge_execution.sql`
  - `src/main/java/com/aubb/server/config/GoJudgeConfiguration.java`
  - `src/main/java/com/aubb/server/modules/assignment/**`
  - `src/main/java/com/aubb/server/modules/judge/**`
  - `src/test/java/com/aubb/server/integration/JudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/SubmissionIntegrationTests.java`
  - `compose.yaml`
  - `docker/go-judge/Dockerfile`
  - `README.md`
  - `ARCHITECTURE.md`
  - `docs/product-specs/*.md`
  - `docs/generated/db-schema.md`
  - `todo.md`

## Session: 2026-04-16 人工批改与成绩发布第一阶段

### Phase 7：grading 第一阶段闭环

- **Status:** completed
- Actions taken:
  - 新增 `V10__grading_first_slice.sql`，把成绩发布时间挂到 `assignments`，把批改人 / 批改时间挂到 `submission_answers`
  - 新建 `modules.grading`，新增人工批改与 assignment 级成绩发布 API
  - 扩展 `CourseAuthorizationService`，允许教师和班级助教按作用域批改
  - 调整学生侧 submission 视图：成绩发布前只展示客观题即时分，隐藏人工评分与反馈
  - 新增 `GradingIntegrationTests`，覆盖教师 / 助教批改、发布前后可见性和越权拦截
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/**`
  - `src/main/resources/db/migration/V10__grading_first_slice.sql`
  - `src/main/java/com/aubb/server/modules/submission/**`
  - `src/main/java/com/aubb/server/modules/course/application/CourseAuthorizationService.java`
  - `src/main/java/com/aubb/server/modules/audit/domain/AuditAction.java`
  - `src/test/java/com/aubb/server/integration/GradingIntegrationTests.java`
  - `README.md`
  - `ARCHITECTURE.md`
  - `docs/product-specs/*.md`
  - `docs/generated/db-schema.md`
  - `todo.md`

### Phase 8：question-level judge 下一阶段计划冻结

- **Status:** completed
- Actions taken:
  - 重新盘点结构化编程题配置，确认当前缺少题目级隐藏测试用例 / 脚本快照模型
  - 将“结构化编程题题目级评测第一阶段”写入活动执行计划
  - 归档“结构化作业与题库第一阶段”执行计划，明确当前下一优先级已切换到 question-level judge

## Session: 2026-04-16 结构化编程题题目级评测第一阶段

### Phase 9：题目级评测模型与执行闭环

- **Status:** completed
- Actions taken:
  - 扩展 `AssignmentQuestionConfigInput / View`，为编程题补充隐藏测试点、资源限制和题目级评测配置摘要
  - 让结构化提交在 `persistStructuredAnswers(...)` 之后返回已落库答案，并在 `SubmissionApplicationService` 中触发 question-level judge 入队
  - 扩展 `judge_jobs`，新增 `submission_answer_id / assignment_question_id / case_results_json`
  - 重写 `JudgeExecutionService`，让 legacy assignment 级 job 与结构化编程题题目级 job 共存
  - 新增答案级评测列表 / 重排队 API，并把成功结果回写到 `submission_answers`
  - 新增 `StructuredProgrammingJudgeIntegrationTests`，覆盖隐藏测试点自动入队、答案级查询、多文件附件装配和重排队
- Files created/modified:
  - `src/main/resources/db/migration/V11__structured_programming_judge_phase1.sql`
  - `src/main/java/com/aubb/server/modules/assignment/**`
  - `src/main/java/com/aubb/server/modules/judge/**`
  - `src/main/java/com/aubb/server/modules/submission/**`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `docker/go-judge/Dockerfile`

### Phase 10：下一阶段计划切换

- **Status:** completed
- Actions taken:
  - 将 question-level judge 第一阶段标记为已完成
  - 下一优先级切换为“样例试运行 / 在线 IDE / CUSTOM_SCRIPT 执行”

## Session: 2026-04-16 样例试运行与工作区最小闭环

### Phase 11：工作区与样例试运行

- **Status:** completed
- Actions taken:
  - 新增 `V12__programming_workspace_and_sample_runs.sql`，引入 `programming_workspaces` 与 `programming_sample_runs`
  - 在 `modules.submission` 中新增编程题工作区读写服务与学生侧 API
  - 在 `modules.judge` 中新增样例试运行服务与学生侧 API，并与正式 `judge_jobs` 分开建模
  - 调整附件上传语义，使附件上传不再受正式提交次数限制，便于工作区和样例试运行复用
  - 复用 `JudgeExecutionService` 的源码装配与标准输入输出比对能力，避免出现两套 go-judge 运行时
  - 新增 `ProgrammingWorkspaceIntegrationTests`，覆盖工作区保存、样例试运行、多文件装配、不生成正式提交和不生成 `judge_jobs`
- Files created/modified:
  - `src/main/resources/db/migration/V12__programming_workspace_and_sample_runs.sql`
  - `src/main/java/com/aubb/server/modules/submission/**/workspace/**`
  - `src/main/java/com/aubb/server/modules/judge/**/sample/**`
  - `src/main/java/com/aubb/server/modules/judge/application/JudgeExecutionService.java`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`

## Session: 2026-04-16 CUSTOM_SCRIPT 第一阶段

### Phase 12：自定义脚本真实执行

- **Status:** completed
- Actions taken:
  - 在 `JudgeExecutionService` 中新增统一的编程题 case 执行 helper，让样例试运行和正式题目级评测共用同一套运行链路
  - 为 `CUSTOM_SCRIPT` 增加固定 Python checker 执行模式，不再把教师输入当 shell 命令直接执行
  - 为 checker 注入保留文件名上下文，包括学生程序 stdout / stderr、期望输出、stdin 和运行元数据 JSON
  - 允许 checker 以 JSON 返回 `verdict / score / message`，并把分值回写到题目级评测结果和 `submission_answers`
  - 新增 `StructuredProgrammingJudgeIntegrationTests` 与 `ProgrammingWorkspaceIntegrationTests` 的 `CUSTOM_SCRIPT` 回归
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/judge/application/JudgeExecutionService.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`

## Session: 2026-04-16 judge 失败态摘要规范化

### Phase 13：失败态映射与摘要统一

- **Status:** completed
- Actions taken:
  - 先补失败测试，覆盖 legacy judge 运行时异常、question-level judge 超时和样例试运行编译失败三条路径
  - 调整 `JudgeExecutionService`，统一 legacy job、question-level judge 和样例试运行的失败态摘要
  - 保持现有 `JudgeVerdict` 枚举不变，把编译失败继续映射为 `RUNTIME_ERROR`，仅通过稳定中文摘要区分“编译失败”和“程序运行失败”
  - 扩展 fake go-judge，按源码标记模拟 `RUNTIME_ERROR / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED / OUTPUT_LIMIT_EXCEEDED / 编译失败`
  - 已执行 `bash ./mvnw -Dtest=JudgeIntegrationTests,StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests test` 并通过
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/judge/application/JudgeExecutionService.java`
  - `src/test/java/com/aubb/server/integration/JudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `docs/product-specs/judge-system.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

## Session: 2026-04-16 Java 运行模板稳定化

### Phase 14：目录树 Java 多文件与 package 入口

- **Status:** completed
- Actions taken:
  - 先扩展现有 Java 多语言集成测试，把样例试运行和正式题目级评测都覆盖到“嵌套目录 + package 化入口”场景
  - 重写 `JudgeExecutionService` 的 `JAVA17` 运行模板，改为编译全部 `.java` 文件，并根据入口文件源码解析 package + 启动类
  - 扩展 fake go-judge，对目录树中的 `solutions/Main.java`、`solutions/Calculator.java` 等路径做后缀匹配，避免测试桩只识别根目录文件
  - 已执行 `bash ./mvnw -o -Dtest=StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests test` 并通过
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/judge/application/JudgeExecutionService.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `docs/product-specs/judge-system.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

## Session: 2026-04-16 评测队列与详细报告第一阶段

### Phase 23：RabbitMQ 队列、详细报告与参数化运行

- **Status:** completed
- Actions taken:
  - 新增 `V16__judge_queue_and_reports_phase1.sql`，为 `judge_jobs` 补充 `detail_report_json`
  - 新增 `JudgeQueueConfiguration`、RabbitMQ publisher / consumer 与本地异步回退监听器
  - 调整 `JudgeExecutionService`，为 legacy judge 和 question-level judge 持久化测试点级详细报告、执行元数据、执行命令和 `compileArgs / runArgs`
  - 为结构化编程题补齐 `compileArgs / runArgs` 配置透传，并让 `CPP17` 编译目录树中的全部翻译单元
  - 新增学生 / 教师详细评测报告 API，并按角色脱敏隐藏测试数据
  - 扩展真实 go-judge 集成测试，覆盖 RabbitMQ 队列路径、详细报告端点，以及 C++ 多文件正式评测和样例试运行的参数化执行
- Files created/modified:
  - `src/main/resources/db/migration/V16__judge_queue_and_reports_phase1.sql`
  - `src/main/java/com/aubb/server/config/JudgeQueueConfiguration.java`
  - `src/main/java/com/aubb/server/modules/assignment/**`
  - `src/main/java/com/aubb/server/modules/judge/**`
  - `src/main/resources/application.yaml`
  - `src/test/java/com/aubb/server/integration/AbstractRealJudgeIntegrationTest.java`
  - `src/test/java/com/aubb/server/integration/JudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `README.md`
  - `docs/product-specs/judge-system.md`
  - `docs/generated/db-schema.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

## Session: 2026-04-16 仓库现状复核与接手文档二次收口

### Phase 24：入口文档口径纠偏

- **Status:** completed
- Actions taken:
  - 重新核对 `ARCHITECTURE.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/product-specs/index.md`、`docs/quality-score.md` 和 active 路线图
  - 修正 RabbitMQ 仍被描述为“后续异步扩展位”的过期口径
  - 补齐学生侧成绩册、详细评测报告、`compileArgs / runArgs` 和真实 judge 测试链路在入口文档中的概括
  - 保持 README / docs / active plan 三个接手入口在“当前能力 + 下一步优先级”上口径一致
- Files created/modified:
  - `ARCHITECTURE.md`
  - `docs/index.md`
  - `docs/repository-structure.md`
  - `docs/product-sense.md`
  - `docs/product-specs/index.md`
  - `docs/quality-score.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `git diff --check -- <targeted-files>`
  - 当前轮次只涉及文档和计划口径整理，代码验证沿用同日已完成的 `bash ./mvnw clean verify`
  - 当前代码基线结果：`BUILD SUCCESS`，全量 `74` 个测试通过

## Session: 2026-04-16 编程工作区目录树快照第一阶段

### Phase 15：工作区快照后端模型

- **Status:** completed
- Actions taken:
  - 新增 `V13__programming_workspace_file_tree_phase1.sql`，为 `programming_workspaces` 和 `programming_sample_runs` 增加 `entry_file_path / source_files_json`
  - 引入共享 `ProgrammingSourceSnapshot / ProgrammingSourceFile` 模型，统一工作区、样例试运行和正式编程答案的源码快照表示
  - 在学生侧工作区、样例试运行和正式提交接口中追加 `entryFilePath + files`，同时保留 legacy `codeText` 兼容语义
  - 调整 `JudgeExecutionService`，让样例试运行与正式评测复用同一套目录树源码装配逻辑
  - 扩展 `ProgrammingWorkspaceIntegrationTests` 与 `StructuredProgrammingJudgeIntegrationTests`，覆盖目录树快照持久化、样例试运行与正式评测的多文件装配
  - 同步 submission / judge 产品规格、数据库结构、路线图与工作记忆，明确当前边界为“后端快照已完成，前端目录树 IDE 仍待后续阶段”
- Files created/modified:
  - `src/main/java/com/aubb/server/common/programming/**`
  - `src/main/java/com/aubb/server/modules/submission/**`
  - `src/main/java/com/aubb/server/modules/judge/**`
  - `src/main/resources/db/migration/V13__programming_workspace_file_tree_phase1.sql`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `docs/exec-plans/completed/2026-04-16-programming-workspace-file-tree-phase1.md`
  - `docs/product-specs/submission-system.md`
  - `docs/product-specs/judge-system.md`
  - `docs/generated/db-schema.md`
  - `README.md`
  - `ARCHITECTURE.md`
  - `docs/product-sense.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests,SubmissionIntegrationTests,JudgeIntegrationTests test`
  - `bash ./mvnw clean verify`
  - `BUILD SUCCESS`，全量 `62` 个测试通过

## Session: 2026-04-16 多语言运行时验证第一阶段

### Phase 16：`JAVA17 / CPP17` 最小链路回归

- **Status:** completed
- Actions taken:
  - 扩展 `ProgrammingWorkspaceIntegrationTests`，新增 `JAVA17 / CPP17` 样例试运行回归，固定语言特定命令和多文件装配行为
  - 扩展 `StructuredProgrammingJudgeIntegrationTests`，新增 `JAVA17 / CPP17` 正式评测回归，确认题目级评测可写回 `submission_answers`
  - 调整两组 fake go-judge 服务器，使其按 `Main.java / main.cpp` 与辅助源文件模拟最小可运行结果
  - 同步 `task_plan.md`、`findings.md`、`todo.md` 和 judge 产品规格，明确三语言最小链路已纳入自动化验证
- Files created/modified:
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `docs/product-specs/judge-system.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
  - `todo.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests test`
  - `BUILD SUCCESS`，新增后专项共 `6` 个测试通过
  - `bash ./mvnw clean verify`
  - `BUILD SUCCESS`，全量 `64` 个测试通过

## Session: 2026-04-16 教师侧成绩册第一阶段

### Phase 13：gradebook 聚合与接口

- **Status:** completed
- Actions taken:
  - 新增 `modules.grading.application.gradebook`，补齐成绩册聚合服务与视图模型
  - 新增教师侧成绩册 API，覆盖开课实例、教学班和单学生三类视图
  - 复用 `SubmissionScoreSummaryView`、`CourseAuthorizationService` 和现有 assignment / submission / grading 读模型
  - 明确成绩册第一阶段默认按每个学生每个作业最新正式提交聚合，并只覆盖结构化作业
  - 新增 `GradebookIntegrationTests`，覆盖 offering / class / student 视图、TA 作用域和最新提交语义
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/**`
  - `src/main/java/com/aubb/server/modules/grading/api/TeacherGradebookController.java`
  - `src/test/java/com/aubb/server/integration/GradebookIntegrationTests.java`
  - `README.md`
  - `ARCHITECTURE.md`
  - `docs/product-sense.md`
  - `docs/product-specs/{assignment,submission,grading}-system.md`
  - `docs/exec-plans/completed/2026-04-16-gradebook-first-slice.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
- Verification:
  - `bash ./mvnw -Dtest=GradebookIntegrationTests test`
  - `bash ./mvnw -Dtest=GradebookIntegrationTests,GradingIntegrationTests,SubmissionIntegrationTests,StructuredAssignmentIntegrationTests test`
  - `bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `62` 个测试通过

## Session: 2026-04-16 学生侧成绩册第一阶段

### Phase 18：学生侧成绩视图最小闭环

- **Status:** completed
- Actions taken:
  - 新增 `MyGradebookController`，补齐学生侧 `GET /api/v1/me/course-offerings/{offeringId}/gradebook`
  - 在 `GradebookApplicationService` 中新增 `getMyGradebook(...)`，复用既有读模型，不新增成绩表
  - 调整学生侧成绩册的 score summary 可见性：未发布成绩时仅保留客观题即时分与提交状态，不提前泄露人工分
  - 扩展 `GradebookIntegrationTests`，覆盖“只看自己”和“未发布人工分隐藏”两条边界
  - 同步 grading 产品规格、路线图与工作记忆，明确学生侧成绩册第一阶段已完成
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/grading/api/MyGradebookController.java`
  - `src/main/java/com/aubb/server/modules/grading/application/gradebook/GradebookApplicationService.java`
  - `src/test/java/com/aubb/server/integration/GradebookIntegrationTests.java`
  - `docs/product-specs/grading-system.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
  - `todo.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=GradebookIntegrationTests test`
  - `bash ./mvnw clean verify`
  - `BUILD SUCCESS`，全量 `65` 个测试通过

## Session: 2026-04-16 题库生命周期第一阶段

### Phase 17：题库更新与归档

- **Status:** completed
- Actions taken:
  - 新增 `V14__question_bank_lifecycle_phase2.sql`，为题库题目补齐 `archived_by_user_id / archived_at` 与活跃题目索引
  - 为教师侧题库新增更新与归档接口，并在默认列表中过滤归档题目
  - 让 assignment 组卷时拒绝引用已归档题目，同时保持既有 assignment 快照不受题库后续更新影响
  - 扩展 `StructuredAssignmentIntegrationTests`，覆盖“更新不污染快照”和“归档后不可再引用”
  - 同步 assignment 产品规格、数据库结构、路线图与工作记忆
- Files created/modified:
  - `src/main/resources/db/migration/V14__question_bank_lifecycle_phase2.sql`
  - `src/main/java/com/aubb/server/modules/assignment/api/QuestionBankTeacherController.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/bank/QuestionBankApplicationService.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/bank/QuestionBankQuestionView.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/AssignmentPaperApplicationService.java`
  - `src/main/java/com/aubb/server/modules/assignment/infrastructure/bank/QuestionBankQuestionEntity.java`
  - `src/main/java/com/aubb/server/modules/audit/domain/AuditAction.java`
  - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
  - `docs/product-specs/assignment-system.md`
  - `docs/generated/db-schema.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
  - `todo.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=StructuredAssignmentIntegrationTests test`
  - `bash ./mvnw clean verify`
  - `BUILD SUCCESS`，全量 `66` 个测试通过

## Session: 2026-04-16 题库标签与标签检索第一阶段

### Phase 17：题库标签最小闭环

- **Status:** completed
- Actions taken:
  - 新增 `V15__question_bank_tags_phase1.sql`，引入 `question_bank_tags` 与 `question_bank_question_tags`
  - 为教师侧题库创建 / 更新接口补齐 `tags`，并为题库列表补齐重复 `tag` 参数过滤
  - 在题库服务中加入标签归一化、开课实例内标签复用与题目标签替换逻辑
  - 扩展 `StructuredAssignmentIntegrationTests`，覆盖标签去重、标签更新替换和多标签同时命中过滤
  - 同步 assignment 产品规格、数据库结构、路线图与工作记忆
- Files created/modified:
  - `src/main/resources/db/migration/V15__question_bank_tags_phase1.sql`
  - `src/main/java/com/aubb/server/modules/assignment/api/QuestionBankTeacherController.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/bank/QuestionBankApplicationService.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/bank/QuestionBankQuestionView.java`
  - `src/main/java/com/aubb/server/modules/assignment/infrastructure/bank/QuestionBankTagEntity.java`
  - `src/main/java/com/aubb/server/modules/assignment/infrastructure/bank/QuestionBankTagMapper.java`
  - `src/main/java/com/aubb/server/modules/assignment/infrastructure/bank/QuestionBankQuestionTagEntity.java`
  - `src/main/java/com/aubb/server/modules/assignment/infrastructure/bank/QuestionBankQuestionTagMapper.java`
  - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
  - `docs/product-specs/assignment-system.md`
  - `docs/generated/db-schema.md`
  - `docs/exec-plans/active/2026-04-16-assignment-module-replan.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
  - `todo.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=StructuredAssignmentIntegrationTests test`
  - `bash ./mvnw clean verify`
  - `BUILD SUCCESS`，全量 `67` 个测试通过

## Session: 2026-04-16 仓库状态检查与文档整理

### Phase 20：入口、结构说明与 active 计划收口

- **Status:** completed
- Actions taken:
  - 重新盘点 `README.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/product-sense.md`、`docs/product-specs/index.md`、`docs/quality-score.md`
  - 修正文档入口重复、模块列表缺项、成绩能力口径滞后和下一步开发优先级说明
  - 将已完成的 `2026-04-15-repository-audit-remediation.md` 从 `docs/exec-plans/active/` 归档到 `docs/exec-plans/completed/`
  - 保持当前进行中的计划目录只承载真实 active 路线
- Files created/modified:
  - `README.md`
  - `docs/index.md`
  - `docs/repository-structure.md`
  - `docs/product-sense.md`
  - `docs/product-specs/index.md`
  - `docs/quality-score.md`
  - `docs/exec-plans/active/README.md`
  - `docs/exec-plans/completed/2026-04-15-repository-audit-remediation.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=RepositoryStructureTests test`
  - 当前结果：`BUILD SUCCESS`

## Session: 2026-04-16 真实 go-judge 引擎验证与 fake judge 清理

### Phase 21：真实引擎专项回归

- **Status:** completed
- Actions taken:
  - 新增 `AbstractRealJudgeIntegrationTest`，统一用 Testcontainers 启动真实 go-judge 与 MinIO，不再使用内存假服务器
  - 清理 `JudgeIntegrationTests`、`ProgrammingWorkspaceIntegrationTests`、`StructuredProgrammingJudgeIntegrationTests` 中的 fake go-judge 服务器、请求形状断言与 `#FAIL_HTTP` 等旧标记
  - 将 Java 运行时主矩阵切到 `JAVA21`，保留 `JAVA17` 作为兼容输入
  - 更新 go-judge 运行镜像与挂载配置，实测 `PYTHON3 / JAVA21 / CPP17` 的真实 `/run` 可执行
  - 修复真实 go-judge 接入中暴露的问题：
    - 兼容官方状态值 `Nonzero Exit Status`
    - `files` 请求体改为真实联合模型，避免带 `null` 字段的伪描述符触发 400
    - custom judge 无 stdin 时改为空输入，避免协议层拒绝
    - 扩展编译失败摘要识别，覆盖真实 `javac / g++` 报错样式
- Files created/modified:
  - `docker/go-judge/Dockerfile`
  - `docker/go-judge/mount.yaml`
  - `src/main/java/com/aubb/server/common/programming/ProgrammingSourceSnapshot.java`
  - `src/main/java/com/aubb/server/modules/assignment/domain/question/ProgrammingLanguage.java`
  - `src/main/java/com/aubb/server/modules/judge/application/JudgeExecutionService.java`
  - `src/main/java/com/aubb/server/modules/judge/infrastructure/gojudge/GoJudgeClient.java`
  - `src/test/java/com/aubb/server/integration/AbstractRealJudgeIntegrationTest.java`
  - `src/test/java/com/aubb/server/integration/JudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `docker build -t aubb-go-judge-realtest -f docker/go-judge/Dockerfile .`
  - 真实 `/run` 冒烟：`PYTHON3 / JAVA21 / CPP17` 都返回正确输出
  - `bash ./mvnw -Dtest=JudgeIntegrationTests test`
  - `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests test`
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw clean verify`
  - 当前结果：专项均 `BUILD SUCCESS`，全量 `71` 个测试通过

## Session: 2026-04-16 仓库现状检查与接手入口整理

### Phase 22：接手入口与验证路径收口

- **Status:** completed
- Actions taken:
  - 重新核对 `README.md`、`docs/index.md`、`docs/repository-structure.md`、`docs/exec-plans/active/README.md`、`ARCHITECTURE.md` 与当前 active 计划是否仍反映真实代码状态
  - 复查 `todo.md`、`task_plan.md`、`findings.md` 与 `progress.md`，确认下一步优先级仍为在线 IDE、多语言稳定化、题库组卷和成绩导出
  - 统一主入口文档中的验证命令，显式写明当前 Unix 环境应使用 `bash ./mvnw ...`，避免接手者在 wrapper 执行位上卡住
  - 保持 `docs/exec-plans/completed/` 作为历史记录，不追溯重写旧计划中的命令示例
- Files created/modified:
  - `README.md`
  - `AGENTS.md`
  - `docs/development-workflow.md`
  - `docs/object-storage.md`
  - `docs/product-specs/platform-baseline.md`
  - `docs/product-specs/course-system.md`
  - `docs/product-specs/assignment-system.md`
  - `docs/product-specs/submission-system.md`
  - `docs/product-specs/grading-system.md`
  - `docs/product-specs/judge-system.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - 本轮代码状态复核沿用同日已完成的 `bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `71` 个测试通过

## Session: 2026-04-16 评测运行环境与语言模板第一阶段

### Phase 24：题目级运行环境快照、Go 多文件执行与开课实例环境模板

- **Status:** completed
- Actions taken:
  - 为结构化编程题新增 `ProgrammingExecutionEnvironmentInput/View`，并让题库题目与作业题目都支持 `executionEnvironment`
  - 为教师侧作业 / 题库接口补齐环境标签、语言版本、编译命令、运行命令、环境变量、工作目录、初始化脚本、支持文件和 CPU 速率限制的请求模型
  - 扩展 `ProgrammingLanguage` 与源码快照默认入口，新增 `GO122`
  - 更新 `JudgeExecutionService` 与 `GoJudgeClient`，将编译型语言执行拆成“编译 -> 运行”两阶段，并通过 go-judge `copyOut / copyIn` 回传编译产物
  - 为 `GO122` 固化第一阶段运行模板：多文件工程、`go.mod`、工作目录、环境变量覆盖、初始化脚本与支持文件都通过真实 go-judge 验证
  - 新增开课实例级 `judge_environment_profiles`，支持教师按课程维护可复用的语言环境模板
  - 为编程题新增 `languageExecutionEnvironments`，支持按 `programmingLanguage` 引用模板并做题目级覆盖，同时保留旧 `executionEnvironment` 作为回退环境
  - 在题库建题与 assignment 快照环节解析 `profileId / profileCode`，把模板化环境固化为不可变快照，避免后续模板修改污染既有作业
  - 扩展真实集成测试，覆盖正式题目级评测、样例试运行，以及“模板引用 -> assignment 快照 -> 真实 go-judge 执行”的完整链路
- Files created/modified:
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/ProgrammingExecutionEnvironmentInput.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/ProgrammingExecutionEnvironmentView.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/ProgrammingLanguageExecutionEnvironmentInput.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/ProgrammingLanguageExecutionEnvironmentView.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/AssignmentQuestionConfigInput.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/AssignmentQuestionConfigView.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/StructuredQuestionSupport.java`
  - `src/main/java/com/aubb/server/modules/assignment/api/AssignmentTeacherController.java`
  - `src/main/java/com/aubb/server/modules/assignment/api/QuestionBankTeacherController.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/bank/QuestionBankApplicationService.java`
  - `src/main/java/com/aubb/server/modules/assignment/application/paper/AssignmentPaperApplicationService.java`
  - `src/main/java/com/aubb/server/modules/assignment/domain/question/ProgrammingLanguage.java`
  - `src/main/java/com/aubb/server/common/programming/ProgrammingSourceSnapshot.java`
  - `src/main/java/com/aubb/server/modules/judge/infrastructure/gojudge/GoJudgeClient.java`
  - `src/main/java/com/aubb/server/modules/judge/application/JudgeExecutionService.java`
  - `src/main/java/com/aubb/server/modules/judge/application/environment/**`
  - `src/main/java/com/aubb/server/modules/judge/infrastructure/environment/**`
  - `src/main/java/com/aubb/server/modules/judge/api/TeacherJudgeEnvironmentProfileController.java`
  - `docker/go-judge/Dockerfile`
  - `docker/go-judge/mount.yaml`
  - `src/test/java/com/aubb/server/integration/AbstractRealJudgeIntegrationTest.java`
  - `src/test/java/com/aubb/server/integration/StructuredAssignmentIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/StructuredProgrammingJudgeIntegrationTests.java`
  - `src/test/java/com/aubb/server/integration/ProgrammingWorkspaceIntegrationTests.java`
  - `README.md`
  - `docs/product-specs/assignment-system.md`
  - `docs/product-specs/judge-system.md`
  - `docs/generated/db-schema.md`
  - `docs/exec-plans/completed/2026-04-16-judge-runtime-environments-phase1.md`
  - `todo.md`
  - `task_plan.md`
  - `findings.md`
  - `progress.md`
- Verification:
  - `bash ./mvnw spotless:apply`
  - `bash ./mvnw -Dtest=StructuredAssignmentIntegrationTests,StructuredProgrammingJudgeIntegrationTests test`
  - `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests test`
  - `bash ./mvnw clean verify`
  - 当前结果：`BUILD SUCCESS`，全量 `82` 个测试通过
