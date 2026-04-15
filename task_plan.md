# 任务计划：Submission 第一切片

## 目标

落地 `submission` 第一切片，打通“学生查看已发布作业 -> 正式提交 -> 学生查看本人提交 -> 教师按作业查看提交”的最小闭环，并保持与当前 assignment、课程成员权限和文档体系一致。

## 当前阶段

Completed

## Skills 选择

- `planning-with-files`：本任务跨测试、迁移、模块代码、文档与验证，需要持续记录阶段和决策。
- `springboot-patterns`：用于保持模块优先、控制器薄、应用层承载规则、持久化边界清晰。
- `springboot-verification`：用于约束格式化和全量验证。
- `documentation-writer`：用于同步产品规格、数据库说明和架构文档。

## 阶段

### Phase 1：范围收敛与提交建模

- [x] 对齐 assignment、课程成员与权限模型
- [x] 定义 submission 第一切片的数据模型、状态和 API 边界
- [x] 建立正式执行计划与工作记忆
- **Status:** completed

### Phase 2：测试先行固定行为

- [x] 增加学生正式提交集成测试
- [x] 增加提交次数限制和时间窗口边界测试
- [x] 增加教师查看提交与学生越权测试
- **Status:** completed

### Phase 3：数据库与模块实现

- [x] 新增 Flyway 迁移与实体/Mapper
- [x] 新增 `modules.submission` 下的 `api / application / domain / infrastructure`
- [x] 接入 assignment 校验、课程授权、审计与必要查询模型
- **Status:** completed

### Phase 4：文档同步与验证

- [x] 更新 `docs/generated/db-schema.md`
- [x] 更新 assignment / submission 规格、架构和目录文档
- [x] 执行 `./mvnw spotless:apply`
- [x] 执行 `./mvnw clean verify`
- **Status:** completed

## 已做决策

| Decision | Rationale |
|----------|-----------|
| 提交模块独立建为 `modules.submission` | 与 `assignment / judge / grading` 保持边界清晰 |
| 第一切片只做正式提交，不做工作区和试运行 | 先打通主链路，不提前耦合 IDE 和运行环境 |
| 提交内容先采用文本正文承载 | 避免提前引入文件存储和工程快照复杂度 |
| 第一切片只支持学生提交，教师侧仅查看 | 控制范围，先建立稳定受理模型 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| 新增 `SubmissionIntegrationTests` 后，因 `submissions` 表尚未存在导致测试初始化阶段失败 | 先复现 Flyway 后的 TRUNCATE 失败，再回查缺失迁移和模块边界 | 新增 `V5__submission_first_slice.sql` 和 `modules.submission`，专项测试恢复通过 |
