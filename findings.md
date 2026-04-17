# 发现与决策

## 2026-04-17 文档漂移与 OpenAPI / 稳定接口清单发现

- 当前仓库的主要漂移已经不在业务功能本体，而在“入口文档与索引文档更新速度不一致”：`README.md`、`docs/index.md`、`docs/reliability.md` 基本跟上了最近改动，但 `ARCHITECTURE.md` 和 `docs/exec-plans/tech-debt-tracker.md` 仍保留明显早期表述。
- `springdoc-openapi-starter-webmvc-ui` 已真实接入，`application.yaml` 已显式暴露 `springdoc.api-docs` / `swagger-ui` 开关，`SecurityConfig` 也已放行 `/v3/api-docs/**` 与 `/swagger-ui/**`；因此“OpenAPI 只有概念，没有运行时入口”已经不是事实。
- 当前最缺的不是再造一套 API 设计，而是把“运行时 `/v3/api-docs` 是事实契约入口”与“哪些接口已经进入稳定维护范围”写清楚；否则 README 即使更新，也仍会让新接手的人误判接口边界。
- `ARCHITECTURE.md` 中“JWT Bearer Token，服务端无状态校验”已和当前 `auth_sessions + sid` 会话校验实现冲突，这类冲突比功能缺失更危险，因为它会误导后续安全设计。
- `docs/product-specs/platform-governance-and-iam.md` 里“judge / grading 等后续课程子域仍未实现”同样已经滞后；当前更准确的表述应是：这些子域已经开始复用 `course_members` 边界，但更细粒度筛选仍待增强。
- `docs/exec-plans/completed/` 中的历史计划应保留历史事实，不宜整体重写；真正需要修的是仍被当成“当前状态”的索引文档和长期说明文件。
- 本轮最稳的闭环是：
  - 新增一份 `docs/stable-api.md`
  - 以 `/v3/api-docs` 和 `/swagger-ui/index.html` 作为事实入口
  - 用一个轻量集成测试固定 OpenAPI 发现路径和关键稳定接口族
  - 同步入口文档、架构文档、仓库结构说明与安全 / 可靠性文档

## 2026-04-17 judge 详细产物对象化存储 phase 2 发现

- phase 1 已解决“详细报告和样例试运行源码快照不要继续把大 JSON 塞进库里”，但正式评测仍缺下载、正式源码快照归档和统一追踪摘要；这会让对象化看起来“能存”，却还不够可交接。
- 正式评测最小闭环不需要另起新表；在 `judge_jobs` 上补 `source_snapshot_object_key / artifact_manifest_object_key / artifact_trace_json`，就能和现有 `submission_id / submission_answer_id / assignment_question_id` 形成三维追踪。
- 下载能力最稳的实现不是直出原始 MinIO 对象，而是复用现有 `JudgeJobReportView` 组装逻辑，再按当前调用者权限导出 JSON：
  - 学生下载继续脱敏隐藏测试点
  - 教师下载继续保留隐藏测试点与追踪摘要
- 旧数据兼容不应做回填迁移：旧 `detail_report_json` 已足以支撑查询和下载；本轮最稳策略是“旧数据保留 JSON、读取时对象优先、无对象时回退 JSON、无批量 backfill”。
- 真正需要对象化的“源码快照”不是再把 `submission_answers.answer_payload_json` 原样复制一份，而是提炼出正式评测复现所需的最小快照：语言、入口文件、文件树、附件引用和题目上下文。
- 归档清单最小应包含：
  - 产物类型
  - 对象键
  - content type
  - size / sha256
  - 是否真正落到对象存储
  - 三维关联键与归档时间
- 这轮只补正式评测 phase 2 闭环，不扩展编译产物 bundle、镜像 digest、对象版本化和大规模历史回填。

## 2026-04-17 关键列表数据库分页权限过滤发现

- `listUsers` 和 `listMyAssignments` 当前共同的性能根因不是“少一个索引”，而是“候选集先全量拉入内存，再做权限过滤和分页”；只补索引不会自动修复分页准确性。
- `listUsers` 的平台治理可见性仍然依赖组织树祖先规则，因此最稳的改法不是把组织树逻辑硬塞进 SQL，而是先在 `GovernanceAuthorizationService` 中一次性解析出可管理组织集合，再把用户筛选、角色筛选、画像筛选和分页交给数据库。
- `listMyAssignments` 的课程侧权限也不适合继续 per-row 调 `canViewAssignment(...)`；更稳的切法是先在 `CourseAuthorizationService` 中一次性解析“全量可见 offering”，再由 SQL 用 `EXISTS course_members` 处理课程公共作业和班级作业的成员可见性。
- `total` 必须和 `items` 共用同一套 SQL 谓词；否则即使页面数据看起来正确，也会出现翻页数和真实结果不一致的问题。
- 这轮最小索引闭环应只围绕新查询路径补齐：
  - `users(primary_org_unit_id, created_at desc, id desc)`
  - `user_scope_roles(role_code, user_id)`
  - `course_members(user_id, offering_id, member_status, teaching_class_id)`
  - `assignments(open_at, id) WHERE status <> 'DRAFT'`
- 课程级权限和平台级权限必须继续分开建模：前者只服务 assignment 可见 offering / 成员边界，后者只服务治理组织树作用域，不能为了“写一条大 SQL”把两套授权体系混成一层。

## 2026-04-17 通知 / 消息中心 MVP 发现

- `todo.md` M4 的真实目标不是“先补实时推送”，而是先建立“通知内容 + 用户已读状态”的持久化模型；当前缺的是通知域本身，不是 WebSocket 通道。
- 现有 `audit_logs` 是 actor 视角的治理留痕，不具备 recipient 展开和已读 / 未读语义，不能直接替代通知中心。
- M4 当前最稳的实现是 `notifications + notification_receipts`：
  - `notifications` 保存通知内容、类型、目标对象、上下文摘要
  - `notification_receipts` 保存面向具体用户的收件箱视图和 `read_at`
- 当前仓库还没有稳定的 Redis / WebSocket 运行基线；如果先做推送，会把最核心的“持久化、已读状态、补拉列表、失败恢复”问题后置，因此 v1 仍应固定为“轮询 + 未读数接口”。
- 事件接入点必须放在 application service 的终态写入后，而不是 controller：
  - `AssignmentApplicationService.publishAssignment`
  - `GradingApplicationService.publishAssignmentGrades`
  - `GradeAppealApplicationService.reviewAppeal`
  - `LabApplicationService.publishLab`
  - `LabApplicationService.publishReport`
  - `JudgeExecutionService.finalizeJobSideEffects`
- `LAB_REPORT_REVIEWED` 当前不是学生可见边界，因为学生在 `REVIEWED` 阶段看不到教师批注 / 评语；实验报告通知必须挂在 `publishReport` 而不是 `reviewReport`。
- `JUDGE_JOB_ENQUEUED / STARTED` 过于偏内部执行链路，通知噪音高；本轮只通知终态完成，且优先面向正式提交的 submitter。
- 为了让教师侧也有真实未读通知闭环，除了用户明确要求的五类事件外，第一阶段额外接入了“实验报告提交后通知教师 / 助教”，把它作为教师待办入口，而不是等待后续 WebSocket。

## 2026-04-17 lab / report MVP 发现

- `todo.md` 的 M3 已明确 `labEnabled` 只是 feature 开关，而 `lab / report` 模块本身尚不存在；真实缺口不是权限体系，而是业务域缺席。
- 课程粒度权限目前都集中在 `CourseAuthorizationService`；controller 普遍只做 `isAuthenticated()`，因此 `labEnabled` 和课程成员访问控制最稳的接线点就是课程授权服务，而不是新模块内零散查表。
- `TeachingClassEntity.labEnabled` 当前只有配置与回显，没有任何业务真正消费它；如果直接在 controller 上做条件判断，后续一定会漂移。
- assignment/submission 已经提供两条可复用能力：
  - 教师侧 `create / update / publish / close / list / detail` 状态流转骨架
  - 学生侧“对象存储附件 + 数据库元数据引用”的写法
- lab/report 不能简单复用 assignment/submission：
  - 实验报告需要草稿、教师评阅、评语发布等独立状态
  - 把实验报告塞进 submission 会把作业次数、评测、成绩发布等错误语义带进实验域
- 为了让 `labEnabled` 有唯一、确定的判断来源，MVP 最稳的实现是先收敛为“教学班级级实验”，让 `teachingClassId` 成为必填；开课实例级公共实验留到后续扩展。
- 当前最小模型足以闭环：
  - `labs`
  - `lab_reports`
  - `lab_report_attachments`
  - 状态流转：`DRAFT / PUBLISHED / CLOSED` 与 `DRAFT / SUBMITTED / REVIEWED / PUBLISHED`
- 实验报告第一阶段先固定为“每学生每实验一份当前报告”，这样能最小化 API、状态和权限复杂度；如果后续需要历史版本，应新增版本化结构而不是污染当前表语义。
- 学生视角下，`REVIEWED` 阶段先只暴露状态，不暴露教师批注 / 评语；只有 `PUBLISHED` 后才回放给学生。这能保留教师“先内部评阅、再发布结果”的最小语义。
- 实验报告附件最稳的模型仍是“先上传附件对象，再在保存 / 提交报告时绑定到报告”；这样可以直接复用现有对象存储能力并支持失败回滚。

## 2026-04-17 judge 详细产物对象化存储发现

- 当前仓库已经有共享 `ObjectStorageService` / `MinioObjectStorageService`，并且 `submission_artifacts` 已经把附件元数据和对象内容分离；judge 域尚未复用这条能力，仍把大体积评测产物直接塞进 `judge_jobs` 和 `programming_sample_runs`。
- 当前正式评测的主要大字段是：
  - `judge_jobs.detail_report_json`：完整详细报告，已经包含执行元数据、逐测试点完整 stdout/stderr、命令、根级 stdout/stderr
  - `judge_jobs.case_results_json`：列表摘要 JSON，体积相对小，且直接服务 `/judge-jobs` 列表接口
- 当前样例试运行的主要大字段是：
  - `programming_sample_runs.detail_report_json`
  - `programming_sample_runs.stdout_text / stderr_text`
  - `programming_sample_runs.code_text / source_files_json / source_directories_json`
- 当前正式评测报告查询链路完全耦合旧列：
  - `JudgeApplicationService.toView(...)` 通过 `detail_report_json` 判定 `detailReportAvailable`
  - `JudgeApplicationService.toReportView(...)` 直接反序列化 `detail_report_json`
- 当前样例试运行查询链路也完全耦合旧列：
  - `ProgrammingSampleRunApplicationService.toView(...)` 直接读取 `code_text / source_files_json / source_directories_json / stdout_text / stderr_text / detail_report_json`
- `JudgeJobStoredReport` 已经天然是一个“归档对象”：
  - 根级 `stdoutText / stderrText`
  - `caseReports[*].stdoutText / stderrText / compileCommand / runCommand`
  - `executionMetadata`
  因此第一阶段没有必要再把“case outputs”和“运行日志”拆成独立对象；把完整 `detailReport` 对象落到对象存储，就已经覆盖了这两类大体积内容。
- 正式评测与样例试运行对“源码快照”的处理边界不同：
  - 正式评测已经有 `submission_answer_id` 这个稳定锚点，源码快照可以通过 `submission_answers.answer_payload_json + submission_artifacts + assignment_questions` 复原，第一阶段更稳的是补清晰引用链，而不是再次复制一份源码正文
  - 样例试运行没有 `submission_answer_id` 这种正式提交锚点，因此需要把本次运行的源码快照对象化存下来，避免继续依赖 `programming_sample_runs.code_text / source_files_json / source_directories_json`
- 最小兼容层放在 judge application service 最稳：
  - 新增 judge 专用 artifact persistence/lookup service
  - `JudgeExecutionService` / `ProgrammingSampleRunApplicationService` 只负责生成语义对象
  - `JudgeApplicationService` / `ProgrammingSampleRunApplicationService` 读路径优先读对象引用，旧列只作为兼容回退
- 第一阶段不适合直接删除旧列；更稳的做法是：
  - 新增对象引用字段
  - 新写入走对象存储
  - 读取时优先对象、回退旧列
  - 让旧 API 无感知切换
- 可复现链路第一阶段至少依赖这些元数据：
  - `submission_id / submission_answer_id / assignment_question_id`
  - `programmingLanguage / entryFilePath / artifactIds / sourceFiles` 或样例试运行源码快照对象
  - `compileArgs / runArgs / executionEnvironment`
  - `judgeMode / judgeCaseCount / timeLimit / memoryLimit / outputLimit`
  - 正式评测或样例试运行对应的详细报告对象
- 第一阶段先不保留这些更重的元数据：
  - go-judge 容器镜像 digest
  - 编译阶段二进制 bundle
  - 远程对象版本化/归档策略
  - 更细粒度的对象表分层或冷热分层策略
- 已按上述结论落地最小模型：
  - `judge_jobs.detail_report_object_key`
  - `programming_sample_runs.detail_report_object_key`
  - `programming_sample_runs.source_snapshot_object_key`
- 当前读取策略已固定为“对象优先、旧列兼容回退”，因此已有报告 API 和样例试运行查询链路不需要改接口即可继续工作。

## 2026-04-17 Dockerfile、CI 与发布流水线基线发现

- 当前仓库只有基础设施级 `compose.yaml` 和 `docker/go-judge/Dockerfile`，没有应用自身的 `Dockerfile`、`.dockerignore`、`.github/workflows` 或部署编排文件，因此“本地容器联调”“镜像构建”“最小 deploy”都缺少仓库内标准入口。
- 当前 `compose.yaml` 只拉起 PostgreSQL、RabbitMQ、Redis、MinIO 和 go-judge，`README.md` 也明确写了“不包含应用服务本身”；这意味着本地要么手工启动应用，要么自行拼装容器参数，无法一条命令编排 app + 基础设施。
- 运行时依赖里已经加入 `spring-boot-docker-compose`。因此如果直接把 `app` 服务无条件加入根 `compose.yaml`，宿主机执行 `spring-boot:run` 时可能连带拉起应用容器自己，形成端口冲突或递归。最稳的最小方案是：
  - 根 `compose.yaml` 默认仍服务于宿主机运行的基础设施依赖
  - 应用容器通过 `profiles: [app]` 挂入，显式 `docker compose --profile app up --build` 时才参与编排
- 根 `compose.yaml` 里如果直接使用 `${AUBB_JWT_SECRET:?…}` 这种强制插值，`docker compose up -d`、`down`、`config` 即使在 infra-only 场景也会先于 profile 解析失败。更稳的做法是：
  - 保持 compose 级可解析
  - 在 `app` 容器入口检查 `AUBB_JWT_SECRET`，缺失时立刻退出
  - 继续由 Spring 配置绑定层保证应用启动期 fail-fast
- 本地联调最小环境变量集合已经很清晰：
  - 强制项：`AUBB_JWT_SECRET`
  - 应用连库：`SPRING_DATASOURCE_URL / USERNAME / PASSWORD`
  - 队列与缓存：`SPRING_RABBITMQ_*`、`SPRING_DATA_REDIS_*`
  - MinIO：`AUBB_MINIO_*`
  - judge：`AUBB_GO_JUDGE_ENABLED`、`AUBB_GO_JUDGE_BASE_URL`、`AUBB_JUDGE_QUEUE_ENABLED`
- 本机真实 smoke 验证暴露了另一个工程边界：`go-judge` 默认宿主机端口 `5050` 很容易被已有本地服务占用，因此根 `compose.yaml` 必须支持通过 `AUBB_GO_JUDGE_PORT` 等环境变量覆盖宿主机映射端口，不能把端口假设写死在文档里。
- CI/CD 当前的真实缺口不是“测试命令不存在”，而是仓库没有把现有 `bash ./mvnw verify`、容器镜像构建和最小远程部署入口固化到工作流中，也没有在失败时保留 `surefire/failsafe` 报告。
- deploy 在这一轮不需要扩展到 Helm / K8s；最小可交付方案可以是：
  - GitHub Actions 手动触发
  - 基于 GHCR 版本化镜像
  - 通过 SSH 把部署 compose 和 `.env` 推到目标主机
  - 目标主机执行 `docker compose pull && up -d`
  - 回滚入口就是重新执行同一工作流并指定旧的 `image tag`

## 2026-04-17 首个学校 / 管理员 bootstrap 初始化闭环发现

- 当前仓库已经具备三段核心业务能力，但都建立在“系统里已经存在学校管理员”的前提上：
  - `OrganizationApplicationService.createOrgUnit(...)` 能创建根 `SCHOOL` 节点
  - `UserAdministrationApplicationService.createUser(...)` 能创建学校管理员并分配 `SCHOOL_ADMIN`
  - `PlatformConfigApplicationService.upsertCurrent(...)` 能写入单份平台配置
- 真正缺失的是首启入口：现有 HTTP 管理接口都要求 `SCHOOL_ADMIN` 认证，代码库里也没有业务级 `CommandLineRunner / ApplicationRunner / seed`，因此新环境无法从空库自举出第一位管理员。
- 当前唯一启动期自动初始化模式是 `MinioStorageConfiguration` 中受开关控制的 `ApplicationRunner`。这为本任务提供了最合适的实现模板：
  - 默认关闭
  - 启用时 fail-fast 校验配置
  - 启动时执行
  - 逻辑本身必须幂等
- 现有 schema 基本够用，不需要为了 bootstrap 另起一套初始化表；最小闭环可以直接复用：
  - `org_units`
  - `users`
  - `academic_profiles`
  - `user_scope_roles`
  - `platform_configs`
- 但当前 `org_units` 只通过应用层约束根节点必须是 `SCHOOL`，数据库层没有“单一学校根节点”约束；这会削弱“首个学校 bootstrap”语义，并放大重复初始化和并发初始化的脏数据风险。
- 如果 bootstrap 要做到真正幂等，不能直接盲调 `createOrgUnit()` / `createUser()`；必须先按自然键查询后再 create / reuse / patch：
  - 学校根节点：按 `code` 查找，且要防止不同 code 的第二个 `SCHOOL` 根节点
  - 管理员：按 `username` 复用，并补齐 `SCHOOL_ADMIN@schoolId`、学工号画像和主组织
  - 平台配置：复用现有 `upsertCurrent(...)` 单例语义
- 管理员密码是 bootstrap 的敏感点。最小安全语义应是：
  - 首次创建时使用外部配置密码
  - 重复执行默认不覆盖既有密码，避免把正常运行环境变成“每次启动重置管理员密码”
- 在非 HTTP 启动场景下，审计服务会把 `requestId/ip` 记为 `unknown`。这不会阻塞 bootstrap，但文档要明确这是启动期初始化行为，而不是普通接口调用。
- 当前已按上述路径落地默认关闭的 `aubb.bootstrap.*` 启动期 bootstrap runner，并补齐：
  - 首个学校根节点
  - 首个学校管理员与 `SCHOOL_ADMIN` 作用域角色
  - 管理员学工画像
  - 单份平台配置
- 当前幂等语义已固定为“只创建缺失项，不重置既有管理员密码，不覆盖既有平台配置”；若环境里已存在冲突学校根节点或不同 code 的根学校，启动直接 fail-fast。
- 为了把“首个学校 bootstrap”从应用层约束下沉到 schema，已新增数据库保护：
  - 根节点必须是 `SCHOOL`
  - 全库只允许一个学校根节点

## 2026-04-17 refresh token / revoke / 强制失效发现

- 当前登录链路只有 access token：`AuthController.login` 直接调用 `AuthenticationApplicationService.login(...)` + `JwtTokenService.issueToken(...)`，`LoginResultView` 也只返回 access token，没有 refresh token 或 session 标识。
- 当前 `logout` 只是写审计，不会修改任何服务端状态；因此现有 access token 在过期前仍然可用。
- 当前 Bearer 请求在 JWT 验签后，由 `JwtPrincipalAuthenticationConverter` 直接信任 claim 构造 `AuthenticatedUserPrincipal`，不会回查用户状态或会话状态，所以“用户被禁用后旧 token 立刻失效”“管理员强制失效后旧 token 立刻失效”现在都做不到。
- 持久化层目前没有 `refresh token / auth session / revocation` 相关表。最小闭环不需要一开始拆成多张表，单张 `auth_sessions` 足够同时承载 refresh token、revoke 状态和 access token 的服务端锚点。
- 最小实现路径应保持现有 JWT Bearer 主链路不变，只补三件事：
  - 登录时创建 `auth_sessions` 并返回 opaque refresh token
  - access token 带 `sid`，服务端在每次 Bearer 请求时按 `sid` 校验会话仍有效
  - refresh/revoke/禁用/管理员强制失效都通过 `auth_sessions` 状态完成
- 对“用户被禁用”场景，不必先改 `users` 表增加 token version；只要请求链路在 session 校验时回查当前用户状态，并在管理员禁用时批量 revoke 活跃 session，就能满足当前 todo 的最小闭环。
- 实现中遇到的关键工程问题不是业务规则，而是 Bean 依赖环：`SecurityConfig -> AccessTokenSessionValidator -> AuthSessionApplicationService -> AuthenticationApplicationService -> PasswordEncoder(SecurityConfig)`。最小修复是抽出只读 `AuthenticatedPrincipalLoader`，承接 principal 装配和账号状态回查，让安全配置不再反向依赖登录服务。
- 最终落地的最小模型是：
  - access token 继续使用 JWT Bearer，并新增 `sid` 与 `tokenType=access`
  - refresh token 使用 opaque token，不把 token 明文落库，只存 `SHA-256` 哈希
  - `auth_sessions` 单表同时承载 refresh token 过期时间、revoke 原因、最近签发时间
  - 每次受保护请求都按 `sid` 校验 `auth_sessions` 与当前用户状态，以换取 logout / revoke / disable 的立即生效
- 当前闭环已经覆盖：
  - 登录后刷新 access token，并轮换 refresh token
  - `logout` / `POST /api/v1/auth/revoke` 后旧 access token 和 refresh token 不再可用
  - 用户被禁用或管理员强制失效后旧会话立刻失效

## 2026-04-17 JWT 默认密钥治理发现

- 当前 JWT 弱点不是“默认值太弱”，而是 `application.yaml` 和 `SecurityConfig` 仍允许在缺失密钥时使用默认回退启动；这会让部署遗漏环境变量时仍带着可预测签名密钥上线。
- `SecurityConfig` 当前通过 `@Value("${aubb.security.jwt.secret:...}")` 直接创建 `SecretKey`，`JwtTokenService` 也独立通过 `@Value` 读取 `issuer/ttl`；配置分散在多个 bean 创建点，容易再次产生漂移。
- 启动期校验最稳的放置点是独立 `@ConfigurationProperties` 绑定层，而不是继续散落在 `@Value` 或运行期调用里：
  - 单一配置源，`SecurityConfig` 与 `JwtTokenService` 共用同一份已校验配置
  - 缺失或非法时会在上下文创建早期直接 fail-fast
  - 对现有测试和部署的影响面清晰，只需要为测试提供显式 test secret，并在 README / 安全文档中要求外部注入 `AUBB_JWT_SECRET`
- 这次改动应顺手建立最小密钥治理基线：除了去掉默认值，还应至少校验 `secret` 非空且长度不低于 HS256 的最低实用门槛。
- 已实现的最小闭环是：
  - 新增 `JwtSecurityProperties`，集中校验 `issuer / ttl / secret`
  - `SecurityConfig` 和 `JwtTokenService` 不再各自维护 JWT 默认值
  - 主配置改为 `${AUBB_JWT_SECRET}` 无默认回退，测试作用域通过 `src/test/resources/application.properties` 集中注入 `AUBB_JWT_SECRET`
  - 新增 `JwtSecurityPropertiesValidationTests` 覆盖“未配置密钥时启动失败”，并用 `AuthApiIntegrationTests` 回归“已配置密钥时登录与鉴权正常”
- 当前 `compose.yaml` 仍只负责基础设施，不包含应用容器；因此这次变更不会破坏 `docker compose up` 的基础设施用途，但真实启动应用时必须额外注入 `AUBB_JWT_SECRET`。

## 2026-04-17 judge 死锁与终态超时修复发现

- `StructuredProgrammingJudgeIntegrationTests`、`JudgeIntegrationTests` 和 `ProgrammingWorkspaceIntegrationTests` 都在 `@BeforeEach` 中直接执行大范围 `TRUNCATE ... RESTART IDENTITY CASCADE`，但真实评测执行走 RabbitMQ consumer + 独立事务，不受测试线程生命周期约束。
- 队列开启时只会启用 `JudgeQueueConsumer`，`JudgeExecutionLocalListener` 带有 `@ConditionalOnProperty(... havingValue = "false")`，因此这次问题不是 Rabbit 和本地 `@Async` 同时消费同一条 job。
- 真正的死锁风险来自测试清理与异步评测事务并发：`JudgeExecutionService.startJob/finishJob` 会在独立事务里读写 `judge_jobs`、`submission_answers`、`audit_logs`；而 `TRUNCATE ... CASCADE` 需要对这些表拿 `ACCESS EXCLUSIVE` 锁，若测试在前一条评测事务未完全收尾时直接清库，就可能形成锁环。
- answer 级 job 的 8 秒轮询超时本质上是测试假设过强：没有先排空上一轮残留的 running / queued judge work，就假定当前 job 会在固定时间窗内自然收口，容易被真实 Rabbit consumer 的异步调度放大成偶发超时。
- 失败分支当前只写 `submission_answers.feedback_text`，不会把 `grading_status` 从 `PENDING_PROGRAMMING_JUDGE` 切换到明确失败终态；这会让教师侧答案看起来仍像“还没判完”，削弱排障信号。
- 最小闭环修复应同时覆盖测试和业务两侧：
  - 测试侧先等待 in-flight judge work 结束并 purge 队列，再执行 `TRUNCATE`
  - 业务侧给编程题失败增加显式失败终态和日志，便于判断是执行失败还是尚未消费

## 2026-04-16 成绩系统第二阶段补充发现

- 成绩册排名和通过率最稳的实现边界是继续作为 `grading` 读模型派生结果，不新增独立成绩统计表；这样可以保持页面、导出和统计报告的一致性。
- 通过率继续挂在既有 report 结构上比新开统计接口更稳，既不会放大权限边界，也不会引入第二套缓存 / 聚合语义。
- 成绩申诉第一阶段的最小可用闭环是“已发布成绩 + 非客观题 + 单答案唯一活动申诉 + assignment 维度复核”，先不引入更复杂的仲裁、回滚和附件化申诉材料。
- 申诉接受时复用 `gradeAnswer(...)` 写回最终分数和反馈，是当前最稳且最少分叉的实现；这样人工批改、batch-adjust 和申诉复核都共用同一套评分规则。
- assignment 级 batch-adjust 第一阶段的正确边界是 API 级 JSON 调整 + CSV 模板导入导出，而不是直接引入独立的批量批改工作台；这样能先满足教师真实使用，同时避免 UI 工作台在后端里过早定型。

## 2026-04-16 入口文档四次收口补充发现

- 当前主入口文档整体已基本同步，但活动计划 `2026-04-16-assignment-module-replan.md` 仍保留“题库仍缺分类”的旧表述，需要纠正为“分类第一阶段已完成，剩余缺口是更强的组卷编辑体验”。
- `README.md`、`docs/index.md`、`docs/product-sense.md` 和 `docs/quality-score.md` 需要显式提到“题库标签与分类”“结构化作业草稿编辑”以及最近一次 `BUILD SUCCESS / 78` 项测试通过的验证基线，避免下一位开发者先读到落后状态。

## 2026-04-16 入口文档五次收口补充发现

- `docs/product-sense.md` 和 `docs/quality-score.md` 仍停留在 `BUILD SUCCESS / 78` 项测试通过的旧基线，需要更新到最近一次 `82` 项测试通过。
- `README.md`、`todo.md` 和 `docs/product-sense.md` 的“多语言运行时稳定化”优先级表述仍写成 `PYTHON3 / JAVA21 / CPP17`，需要同步到当前四语言矩阵 `PYTHON3 / JAVA21 / CPP17 / GO122`。
- `docs/index.md`、`docs/repository-structure.md`、`docs/product-specs/index.md` 和 `AGENTS.md` 需要把“开课实例级评测环境模板 + 题目级运行环境快照”纳入接手入口，否则开发者很容易只看到 go-judge 执行链路，看不到课程内环境复用边界。

## 2026-04-16 成绩册导出与统计报告第一阶段补充发现

- 教师侧成绩册第一阶段如果只停留在页面浏览，实际教学管理价值不够；CSV 导出和统计报告是让该能力从“能看”推进到“能用”的最小收口。
- offering / class 导出与统计必须严格复用既有成绩册最新正式提交矩阵，否则页面与下载结果不一致会直接破坏教师信任。
- 完成教师侧课程 / 班级导出与统计后，成绩域下一步最自然的缺口已经收敛为多作业加权总评、学生侧导出和更完整统计，而不再是“是否先补一个导出入口”。

## 2026-04-16 assignment 权重与加权总评第一阶段补充发现

- 当前成绩册、CSV 导出和统计报告都已经稳定建立在“每个学生每个作业最新一次正式提交”之上，继续补 assignment 级权重比直接引入更复杂的总评策略更稳。
- 把权重建模在 `assignments.grade_weight` 而不是额外的独立总评表，可以先保持作业定义、成绩展示和导出三者的一致性，避免在第一阶段引入版本漂移。
- 第一阶段最稳的加权语义是 `finalScore / assignmentMaxScore * gradeWeight`，并且只有在某作业存在最新正式提交时才计入当前 `totalWeight`；这能避免“未提交作业是否算零分”在总评里被过早锁死。

## 2026-04-16 学生侧成绩册 CSV 导出第一阶段补充发现

- 学生侧导出最稳的做法不是新写一套查询，而是直接复用 `getMyGradebook(...)` 的聚合逻辑，再单独渲染 CSV；这样页面和导出不会在可见性规则上漂移。
- 学生个人导出第一阶段只需要“个人汇总 + 按作业明细”两段即可满足留存、申诉准备和课后复盘，不必过早引入更多报表维度。
- 未发布人工分的隐藏规则必须和页面完全一致；如果导出比页面多暴露任何人工分、人工反馈或人工部分总分，就会直接破坏当前成绩发布边界。

## 2026-04-16 统计报告五档成绩分布第一阶段补充发现

- 教师侧 report 已经有总体、作业和班级维度，如果继续拆新接口做分布统计，会平白增加权限、缓存和文档复杂度；最稳的做法是在现有 report 结构上追加派生统计字段。
- 五档分布先固定为 `EXCELLENT / GOOD / MEDIUM / PASS / FAIL`，足够覆盖教学场景里的快速诊断；更复杂的自定义分档和趋势分析可以延后。
- 总评分布优先按当前加权得分率统计，只有在学生尚无加权权重时才回退到总分得分率；这能和现有加权总评第一阶段保持一致。

## 2026-04-16 重规划结论

### 需求覆盖矩阵

#### 已完成

- 作业已支持按课程实例或教学班发布，并具备 `openAt / dueAt / maxSubmissions`
- 结构化试卷已支持多个大题与以下题型：
  - `SINGLE_CHOICE`
  - `MULTIPLE_CHOICE`
  - `SHORT_ANSWER`
  - `FILE_UPLOAD`
  - `PROGRAMMING`
- 已支持开课实例内题库创建、列表、详情与引用组卷
- 已支持正式提交、多次提交版本记录、附件上传、分题答案和客观题自动评分摘要
- 已支持教师 / 助教人工批改、assignment 级成绩发布与教师侧成绩册第一阶段
- 已支持 go-judge 驱动的样例试运行、question-level judge、`STANDARD_IO`、第一阶段 `CUSTOM_SCRIPT`、RabbitMQ 队列第一阶段和详细评测报告 API

#### 部分完成

- 题库管理已补齐更新、归档、标签、标签精确检索，以及分类 / 分类过滤第一阶段；结构化作业也已补齐草稿编辑第一阶段，但仍缺更完整组卷体验
- 编程题后端已支持 `entryFilePath + files + directories + artifactIds` 的目录树快照、模板工作区、工作区目录操作、历史修订、模板重置、最近标准输入回填，以及样例试运行 / 正式评测复用；当前剩余缺口主要是前端目录树交互、编辑器能力和更实时同步协议
- 多语言运行时已有 `PYTHON3 / JAVA21 / CPP17 / GO122` 的模型与正式 / 样例两条执行链路，自动化验证已覆盖这四种语言，并已支持 `compileArgs / runArgs`、题目级 `executionEnvironment`、开课实例级 `judge_environment_profiles`、按语言 `languageExecutionEnvironments` 与 C++ / Go 多文件工程，但日志一致性与更复杂工程布局仍不足；`JAVA17` 当前仅作为兼容输入保留
- 编译失败、运行失败和资源超限的摘要口径已在 legacy judge、question-level judge 与样例试运行三条链路上完成第一阶段统一，但更复杂工程布局下的完整执行日志仍不足
- `JAVA21` 运行模板已补齐为“编译全部 `.java` 文件 + 按 package 解析启动类”，目录树中的嵌套路径和 package 化入口已不再退化成 `WRONG_ANSWER`
- 成绩发布、教师侧成绩册和学生侧成绩册已完成第一阶段，但成绩导出和多作业聚合未完成
- 成绩发布、教师侧成绩册和学生侧成绩册已完成第一阶段；教师侧课程 / 班级成绩册现已补齐 CSV 导出与统计报告第一阶段，但多作业聚合与加权总评仍未完成
- 成绩发布、教师侧成绩册和学生侧成绩册当前都已补齐第一阶段导出能力：教师侧支持课程 / 班级 CSV 与统计，学生侧支持个人成绩册 CSV；剩余缺口是更复杂的总评策略和更完整统计
- 成绩统计当前已补齐五档成绩分布第一阶段；剩余缺口进一步收敛为更复杂总评策略、更深入学习分析和学生侧更丰富报表
- 评测结果现已补齐测试点级完整日志、执行命令、`compileArgs / runArgs` 和执行元数据持久化，但评测产物对象化与完整重放仍不足

#### 当前缺口

- 浏览器侧目录树 IDE、语法高亮 / 自动补全 / 格式化，以及更实时的自动保存协议
- `PYTHON3 / JAVA21 / CPP17 / GO122` 四语言完整验证矩阵
- 更稳定的组卷编辑能力与题库选题体验
- 成绩导出和后续多作业总评
- 更可回放的评测日志、执行元数据与产物对象

### 最佳实现拆分

- `assignment`
  - 负责作业头、题库、试卷快照和题目配置
  - 保持题目快照不可变，不直接依赖运行中的题库题目实体
- `submission`
  - 负责提交版本头、分题答案、附件和工作区状态
  - 继续维持 `submissionNo / attemptNo` 的版本语义
- `judge`
  - 负责样例试运行、正式评测、队列状态和日志产物
  - 不承载题库或人工批改语义
- `grading`
  - 负责人工批改、成绩发布、教师 / 学生成绩册和后续统计能力
  - 不直接承接代码执行逻辑

### 重新排序后的优先级

1. 在线 IDE 第二阶段
2. 多语言运行时稳定化
3. 题库与组卷第二阶段
4. 成绩与反馈第二阶段
5. 判题日志与可复现性第二阶段

## 当前仓库真实边界

### 已落地能力

- 平台治理、课程、assignment、submission、grading、judge 以及 MinIO / go-judge 基础设施已形成最小主链路。
- assignment 仍是“单体作业头”模型：
  - `assignments` 只描述发布范围、状态、时间窗、最大提交次数
  - 可选 assignment 级 `judgeConfig` 只支持 `PYTHON3 + TEXT_BODY`
- submission 仍是“整份提交头”模型：
  - `submissions` 表示一个学生对某作业的一次正式提交版本
  - `submission_artifacts` 负责附件资产
- grading 已作为独立逻辑模块落地：
  - `assignments` 挂 assignment 级成绩发布时间
  - `submission_answers` 挂人工评分、反馈、批改人和批改时间
- judge 当前同时包含两条执行链：
  - legacy assignment 级执行任务继续依赖 `assignment_judge_profiles / cases`
  - 结构化编程题 question-level judge 通过 `judge_jobs.submission_answer_id` 下沉到答案级
- judge 当前已支持 RabbitMQ 队列第一阶段与本地异步回退，详细报告先持久化到 `judge_jobs.detail_report_json`
- 题库题目当前已支持更新与软归档：
  - 默认列表只展示未归档题目
  - 已归档题目仍可按详情回溯，但不能继续被新的 assignment 引用
- 题库标签当前已支持：
  - 开课实例内标签去重与复用
  - 创建 / 更新题目时写入标签
  - 题库列表按重复 `tag` 参数执行“全部命中”过滤
- 题库分类当前已支持：
  - 开课实例内分类字典自动创建与列表读取
  - 创建 / 更新题目时设置主分类
  - 题库列表按 `category` 参数过滤
- 草稿作业当前已支持：
  - 教师在发布前更新标题、范围、时间窗和最大提交次数
  - 整体替换结构化试卷或 legacy assignment 级 judge 配置
  - 发布后保持不可编辑，避免污染提交与批改基线

### 当前最关键的缺口

- 已补上 `CUSTOM_SCRIPT` 第一阶段真实执行；当前仍缺更完整的目录树 IDE
- 没有成绩导出与多作业总评
- 教师侧与学生侧成绩册第一阶段已补齐，但仍没有导出和多作业加权总评
- 教师侧成绩册已补齐导出与统计报告第一阶段，但学生侧仍无导出能力，且多作业加权总评仍未建模

## 子代理结论汇总

### 最佳演进路径

- `assignment` 继续负责题库、组卷和作业快照，不把这些职责提前拆到平级新模块。
- `submission` 继续负责提交版本头和分题作答，不推翻现有 `submissionNo / attemptNo` 语义。
- `judge` 继续只承接执行型评测，客观题自动判分不先走 go-judge。
- 工作区状态应放在 `submission`，样例试运行历史应放在 `judge`，避免正式提交与试运行混模。

### 需要严格保持兼容的契约

- 教师侧 assignment API：
  - `POST /api/v1/teacher/course-offerings/{offeringId}/assignments`
  - `GET /api/v1/teacher/course-offerings/{offeringId}/assignments`
  - `GET /api/v1/teacher/assignments/{assignmentId}`
- 学生侧 assignment API：
  - `GET /api/v1/me/assignments`
  - `GET /api/v1/me/assignments/{assignmentId}`
- 学生侧 submission API：
  - `POST /api/v1/me/assignments/{assignmentId}/submissions`
  - 现有 `contentText` / `artifactIds` 语义不能被破坏，只能追加结构化字段
- 编程题工作区、样例试运行和正式编程答案当前已追加 `entryFilePath / files`，旧 `codeText` 仍保留兼容语义
- 教师侧 submission / judge 查询 API 继续按 `assignmentId` 和 `submissionId` 聚合，不打散已有查询模型

## 第一阶段最合理的交付边界

- 题库最小管理：
  - 题目增删查列表中的“增 / 查 / 列表”先落地
  - 题型先覆盖 `SINGLE_CHOICE / MULTIPLE_CHOICE / SHORT_ANSWER / FILE_UPLOAD / PROGRAMMING`
- 结构化作业：
  - assignment 继续保留头信息
  - 新增大题和题目快照，不把整份结构塞进 `description` 或单个 assignment JSON 字段
- 分题提交：
  - 在保持 `submissions` 为整份提交头的前提下，新增 `submission_answers`
  - 单选 / 多选题应用内自动判分
  - 简答 / 文件上传 / 编程题先进入待后续处理状态
- legacy 兼容：
  - 旧版简单作业继续可用
  - 结构化作业走新增字段和新子模型，不回写破坏旧数据

## 本轮数据模型草案

- 题库：
  - `question_bank_questions`
  - `question_bank_question_options`
- 结构化作业快照：
  - `assignment_sections`
  - `assignment_questions`
  - `assignment_question_options`
- 分题作答：
  - `submission_answers`
- 批改与发布：
  - `assignments.grade_published_at / grade_published_by_user_id`
  - `submission_answers.manual_score / feedback_text / graded_by_user_id / graded_at`

## 风险记录

- 现有 `assignment_judge_profiles` 是 legacy assignment 级配置；结构化编程题当前已通过 `assignment_questions.config_json` 挂题目级隐藏测试点。
- `judge_jobs` 已能同时表达 submission 级 legacy job 和 `submission_answer_id` 级 question-level job，并保存逐测试点摘要、详细报告和执行元数据；完整评测产物对象仍未持久化。
- `programming_workspaces` 已支持 `entryFilePath + sourceFilesJson + sourceDirectoriesJson + artifactIdsJson` 的目录树快照，并通过 `programming_workspace_revisions` 保留工作区历史版本、模板重置和恢复点；当前仍未覆盖前端目录树交互和协同协议。
- `programming_sample_runs` 已支持保存样例试运行时的目录树快照、目录列表、输入模式、详细报告和工作区修订引用，并已支持从当前工作区或历史修订发起自定义标准输入试运行；正式评测与试运行的评测产物对象存储仍未落地。
- 真实 go-judge 集成测试已经替换 fake judge：legacy judge、question-level judge 和样例试运行都改为通过 Testcontainers 启动真实引擎验证。
- 真实 go-judge 集成测试当前已同时覆盖 RabbitMQ 队列路径，不再只验证应用内事件直连执行。
- 真实 go-judge `/run` 会返回 `Nonzero Exit Status`，不能再沿用 fake judge 时代的 `Non Zero Exit Status` 字符串。
- 真实 go-judge 对 `files` 使用联合类型校验，stdin/stdout/stderr 需要按真实对象类型序列化，不能发送带 `null` 字段的混合描述符。
- 正式评测与样例试运行现在都支持 `compileArgs / runArgs`，并已经由真实 go-judge Testcontainers 覆盖到 C++ 多文件工程。
- 真实 go-judge 的每次 `/run` 都是全新沙箱；若要拆成“编译 -> 运行”两阶段，必须通过 `copyOut / copyIn` 回传编译产物，不能假设第一次执行生成的二进制会自动保留到第二次调用。
- 题目级 `executionEnvironment` 目前最稳的边界是 assignment question snapshot：通过题库题目复用、在作业发布时固化，并在执行时只允许映射到受控的 `workingDirectory / initScript / environmentVariables / supportFiles / compileCommand / runCommand / cpuRateLimit`，不额外引入独立环境管理中心。
- 课程内最稳的环境复用边界是开课实例级 `judge_environment_profiles`：教师可先维护语言模板，再由题库题目或作业题目按 `profileId / profileCode` 引用；平台解析后只保留环境快照，不在正式评测阶段回查运行中的模板实体。
- `judge_jobs.detail_report_json` 当前包含测试点级 `stdin / expectedStdout / stdout / stderr / compileCommand / runCommand` 和执行元数据；学生侧报告会脱敏隐藏测试输入输出。
- 编译失败当前继续映射到 `RUNTIME_ERROR`，通过稳定中文摘要区分“编译失败”和“程序运行失败”；如后续要新增独立 verdict，需要评估 API 兼容和历史数据迁移。
- 结构化作业一旦落地，旧版“整份文本提交”不能误用于新型作业，必须在业务层显式区分。
- 学生详情接口不能泄露题库正确答案或 assignment 快照中的 `isCorrect` 信息。
- assignment 级成绩发布当前是全局开关，后续若需要按班级或按学生分批发布，需要单独建模。
- Serena 目录级 symbol overview 在当前仓库环境下不稳定，继续以文件级符号查询和 `rg` 为主。
- 教师侧成绩册第一阶段的最稳边界是：
  - 放在 `grading` 模块
  - offering 级与单学生视图只对教师 / 管理员开放
  - class 级视图额外允许具备班级责任的 TA
  - 默认按每个学生每个作业最新正式提交聚合
  - 第一阶段只覆盖结构化作业

## 2026-04-16 仓库整理补充发现

- `README.md`、`docs/index.md`、`docs/repository-structure.md` 是当前最关键的接手入口；其中任一口径漂移，后续开发者都很容易先读到过期状态。
- `docs/repository-structure.md` 原先的模块列表少了 `grading / judge`，不利于快速定位作业主链路的完整代码入口。
- `docs/index.md` 原先存在重复入口，且对当前状态的概括少了 `grading` 与学生侧成绩能力。
- `docs/exec-plans/active/` 中保留已完成计划会误导下一轮开发优先级；活动计划目录应只保留仍在推进的路线图。
- 当前仓库 Unix 环境下 `./mvnw` 没有稳定执行位，主入口文档应统一显式给出 `bash ./mvnw ...`，否则接手者会在第一步验证上遇到 `Permission denied`。
- 已完成的 `docs/exec-plans/completed/` 历史计划保留原命令记录即可；真正需要对齐的是 README、AGENTS、开发流程、当前规格和 active 计划这类“面向下一位开发者”的入口文档。
- `ARCHITECTURE.md` 在上一轮评测队列落地后仍把 RabbitMQ 描述为“未来异步扩展位”，这是当前最容易误导下一位开发者的口径漂移。
- `docs/repository-structure.md` 需要明确 `application.yaml` 已包含 go-judge 与 RabbitMQ 队列开关，以及真实 judge 集成测试会拉起 go-judge / MinIO / RabbitMQ 三类容器。
- `docs/product-specs/index.md`、`docs/product-sense.md` 和 `docs/quality-score.md` 应继续只做入口层概括，不复制细节，但必须覆盖学生侧成绩册 / 导出、评测队列和详细评测报告这类会影响“下一步开发从哪里开始看”的变化。

## 2026-04-16 仓库状态复核补充发现

- `AGENTS.md` 中“当前真实业务进度”若继续停留在“平台治理已完成，并已进入课程系统第一切片”，会直接误导后续任务的范围判断；它需要同步到当前主链路状态和下一步优先级。
- `docs/product-sense.md` 和 `docs/quality-score.md` 是最容易被忽略、但又最容易在接手时先扫一眼的入口页；它们必须至少覆盖模板工作区、修订历史、自定义试运行和真实 go-judge / RabbitMQ 基线。
- 全仓 `git diff --check` 在当前工作区并不适合作为收尾信号，因为 `.agents/skills/**`、`design/**` 和 `pom.xml` 已有与本任务无关的既有脏改动；仓库整理任务应改为对“本轮改动文件”做定向 diff 检查。
- 当前最合适的接手顺序已经比较稳定：
  1. `README.md`
  2. `docs/repository-structure.md`
  3. `docs/product-specs/index.md`
  4. `docs/exec-plans/active/README.md`
  5. `todo.md`
