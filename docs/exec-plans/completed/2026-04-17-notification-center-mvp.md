# 2026-04-17 通知 / 消息中心 MVP

## 目标

基于 `todo.md` 优先级 8，在不引入新消息基础设施的前提下补齐站内通知最小闭环，覆盖通知入库、收件人 fan-out、未读 / 已读、未读数和关键教学事件接入。

## 范围

- 仅做站内通知 v1
- 仅做数据库持久化、列表查询、已读状态和未读数
- 不做 WebSocket、Redis 推送、多渠道通知、公告中心

## 设计决策

1. 使用 `notifications + notification_receipts` 两表：
   - `notifications` 保存通知内容、类型、目标对象和上下文元数据
   - `notification_receipts` 保存收件人和 `read_at`
2. 触发点只接在 application service 的稳定终态上，不在 controller 或草稿状态挂接：
   - 作业发布
   - 评测完成
   - 成绩发布
   - 申诉处理完成
   - 实验发布
   - 实验报告评语发布
3. 额外接入“实验报告提交后通知教师 / 助教”，让教师侧也有真实站内通知闭环。
4. 当前前端集成基线固定为“轮询列表 + 未读数”，后续若要做 v1.1 WebSocket，不能改变当前持久化模型和 API 语义。

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -q -DskipTests compile`
- `bash ./mvnw -Dtest=NotificationReceiptLifecyclePolicyTests,NotificationCenterIntegrationTests,AssignmentIntegrationTests#studentSeesOnlyPublishedAssignmentsForOwnCourseAndClass,GradingIntegrationTests#teacherAndTaGradeStructuredSubmissionAndPublishGrades+studentCreatesAppealAndTeacherResolvesWithScoreRevision,LabReportIntegrationTests#teacherAndStudentCompleteLabReportFlowWithObjectStorageReplay,StructuredProgrammingJudgeIntegrationTests#programmingAnswerRunsQuestionLevelJudgeAndSupportsAnswerScopedRequeue test`

## 收口结果

- 通知模块已落地到 `com.aubb.server.modules.notification`
- 站内通知 API 已提供：
  - `GET /api/v1/me/notifications`
  - `GET /api/v1/me/notifications/unread-count`
  - `POST /api/v1/me/notifications/{id}/read`
  - `POST /api/v1/me/notifications/read-all`
- 文档已同步到 README、产品规格索引、通知中心规格、可靠性说明和数据库结构说明
