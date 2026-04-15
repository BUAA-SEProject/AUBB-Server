# 进度日志

## Session: 2026-04-15 Submission 第一切片

### Phase 1：范围收敛与提交建模

- **Status:** completed
- **Started:** 2026-04-15
- Actions taken:
  - 读取当前 `task_plan.md`、`docs/plan.md`、`docs/product-specs/assignment-system.md`
  - 检查 assignment 权限、教师侧控制器、学生聚合查询与现有 Flyway 迁移
  - 确认 `submission` 第一切片采用独立模块，并挂在 `assignment` 下
  - 新建本轮任务计划和发现记录
- Files created/modified:
  - `task_plan.md`
  - `findings.md`
  - `progress.md`

### Phase 2：测试先行固定行为

- **Status:** completed
- Actions taken:
  - 新增 `SubmissionIntegrationTests`
  - 固定学生正式提交、提交次数限制、开放时间窗口、教师查看和学生越权场景
- Files created/modified:
  - `src/test/java/com/aubb/server/integration/SubmissionIntegrationTests.java`

### Phase 3：数据库与模块实现

- **Status:** completed
- Actions taken:
  - 新增 `V5__submission_first_slice.sql`
  - 新建 `modules.submission` 模块并接入 assignment、课程授权和审计
  - 扩展仓库结构测试，要求 `submission` 模块根目录必须存在
- Files created/modified:
  - `src/main/resources/db/migration/V5__submission_first_slice.sql`
  - `src/main/java/com/aubb/server/modules/submission/*`
  - `src/main/java/com/aubb/server/modules/course/application/CourseAuthorizationService.java`
  - `src/main/java/com/aubb/server/modules/audit/domain/AuditAction.java`
  - `src/test/java/com/aubb/server/RepositoryStructureTests.java`

### Phase 4：文档同步与验证

- **Status:** completed
- Actions taken:
  - 新增 submission 产品规格
  - 更新 assignment 规格、README、架构、数据库结构、目录说明、ADR 和质量评分
  - 执行 `./mvnw spotless:apply`
  - 执行 `./mvnw clean verify`，确认 41 个测试全部通过
- Files created/modified:
  - `docs/product-specs/submission-system.md`
  - `docs/product-specs/assignment-system.md`
  - `docs/generated/db-schema.md`
  - `README.md`
  - `ARCHITECTURE.md`
