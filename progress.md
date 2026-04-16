# 进度日志

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
