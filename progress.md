# 进度日志

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
