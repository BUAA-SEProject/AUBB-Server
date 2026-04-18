# 稳定接口清单

## 目标

本清单用于固化当前仓库已经进入真实联调与测试回归范围的对外接口面。它不替代运行时 OpenAPI，也不描述未来规划中的接口，只回答两个问题：

- 当前哪个路径是 OpenAPI / Swagger 的事实入口
- 当前哪些外部接口被视为仓库承诺维护的稳定面

若本文与代码冲突，以代码和运行时 OpenAPI 为准；变更代码时必须同步更新本文。

## 契约来源

- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`GET /swagger-ui/index.html`
- 业务接口基线：`/api/v1/**`

当前仓库会在发布演练或 deploy workflow 中通过 `bash ops/openapi/export-static.sh` 导出静态 `docs/openapi/aubb-openapi.json` 产物，但它只作为发布辅助快照，不替代运行时 `/v3/api-docs` 这一事实来源。`docs/product-specs/*.md` 负责解释业务语义，本文件负责约束“哪些接口已经进入稳定范围”。

## 兼容边界

- 已列入本清单的路径，不应在无文档同步的情况下改名、删除或改变权限语义。
- 已列入本清单的响应允许增加向后兼容的可选字段，但不应移除现有字段或改变已存在字段的核心含义。
- 未列入本清单的路径，默认视为内部实现细节、过渡接口或后续扩展位，不承诺稳定性。
- `/actuator/health`、`/actuator/health/readiness`、`/actuator/info` 与 `/actuator/prometheus` 属于公开运维检查面；其他 `actuator` 端点不属于稳定业务 API。
- 当前通知中心承诺轮询式 HTTP 接口与当前 HTTP SSE stream 入口；未来 WebSocket / Redis 推送不纳入本轮稳定承诺。
- 稳定业务接口一旦新增、改名或调整权限语义，必须同步更新本文、`AuthzOpenApiAccessRegistry` 与 `scripts/realrun/verify_authz_matrix.*`。

## 当前稳定接口范围

### 公开发现与健康检查

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/info`
- `GET /actuator/prometheus`
- `GET /v3/api-docs`
- `GET /swagger-ui/index.html`

### 认证与当前用户

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/revoke`
- `GET /api/v1/auth/me`

### 平台治理与 IAM

- `GET /api/v1/admin/platform-config/current`，`PUT /api/v1/admin/platform-config/current`
- `GET /api/v1/admin/org-units/tree`，`POST /api/v1/admin/org-units`
- `POST /api/v1/admin/auth/groups`
- `POST /api/v1/admin/auth/groups/{groupId}/members`
- `GET /api/v1/admin/auth/explain`
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/users/{userId}`
- `POST /api/v1/admin/users`
- `POST /api/v1/admin/users/import`
- `PUT /api/v1/admin/users/{userId}/identities`
- `PATCH /api/v1/admin/users/{userId}/status`
- `PUT /api/v1/admin/users/{userId}/profile`
- `PUT /api/v1/admin/users/{userId}/memberships`
- `POST /api/v1/admin/users/{userId}/sessions/revoke`
- `GET /api/v1/admin/audit-logs`

### 课程主数据与教学组织

- `POST /api/v1/admin/academic-terms`，`GET /api/v1/admin/academic-terms`
- `POST /api/v1/admin/course-catalogs`，`GET /api/v1/admin/course-catalogs`
- `POST /api/v1/admin/course-offerings`，`GET /api/v1/admin/course-offerings`，`GET /api/v1/admin/course-offerings/{offeringId}`
- `GET /api/v1/me/courses`
- `POST /api/v1/teacher/course-offerings/{offeringId}/classes`
- `GET /api/v1/teacher/course-offerings/{offeringId}/classes`
- `PUT /api/v1/teacher/course-classes/{teachingClassId}/features`
- `POST /api/v1/teacher/course-offerings/{offeringId}/members/batch`
- `POST /api/v1/teacher/course-offerings/{offeringId}/members/import`
- `GET /api/v1/teacher/course-offerings/{offeringId}/members`

### 作业与题库

- `POST /api/v1/teacher/course-offerings/{offeringId}/assignments`
- `GET /api/v1/teacher/course-offerings/{offeringId}/assignments`
- `GET /api/v1/teacher/assignments/{assignmentId}`
- `PUT /api/v1/teacher/assignments/{assignmentId}`
- `PUT /api/v1/teacher/assignments/{assignmentId}/paper`
- `POST /api/v1/teacher/assignments/{assignmentId}/publish`
- `POST /api/v1/teacher/assignments/{assignmentId}/close`
- `POST /api/v1/teacher/course-offerings/{offeringId}/question-bank/questions`
- `GET /api/v1/teacher/course-offerings/{offeringId}/question-bank/questions`
- `GET /api/v1/teacher/course-offerings/{offeringId}/question-bank/categories`
- `GET /api/v1/teacher/question-bank/questions/{questionId}`
- `PUT /api/v1/teacher/question-bank/questions/{questionId}`
- `POST /api/v1/teacher/question-bank/questions/{questionId}/archive`
- `GET /api/v1/me/assignments`
- `GET /api/v1/me/assignments/{assignmentId}`

### 提交、工作区与评测

- `POST /api/v1/me/assignments/{assignmentId}/submissions`
- `POST /api/v1/me/assignments/{assignmentId}/submission-artifacts`
- `GET /api/v1/me/assignments/{assignmentId}/submissions`
- `GET /api/v1/me/submissions/{submissionId}`
- `GET /api/v1/me/submission-artifacts/{artifactId}/download`
- `GET /api/v1/teacher/assignments/{assignmentId}/submissions`
- `GET /api/v1/teacher/submissions/{submissionId}`
- `GET /api/v1/teacher/submission-artifacts/{artifactId}/download`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace`
- `PUT /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace`
- `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/operations`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/revisions`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/revisions/{revisionId}`
- `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/revisions/{revisionId}/restore`
- `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace/reset-to-template`
- `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs/{sampleRunId}`
- `GET /api/v1/me/submissions/{submissionId}/judge-jobs`
- `GET /api/v1/me/submission-answers/{answerId}/judge-jobs`
- `GET /api/v1/me/judge-jobs/{judgeJobId}/report`
- `GET /api/v1/me/judge-jobs/{judgeJobId}/report/download`
- `GET /api/v1/teacher/submissions/{submissionId}/judge-jobs`
- `GET /api/v1/teacher/submission-answers/{answerId}/judge-jobs`
- `GET /api/v1/teacher/judge-jobs/{judgeJobId}/report`
- `GET /api/v1/teacher/judge-jobs/{judgeJobId}/report/download`
- `POST /api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue`
- `POST /api/v1/teacher/submission-answers/{answerId}/judge-jobs/requeue`
- `POST /api/v1/teacher/course-offerings/{offeringId}/judge-environment-profiles`
- `GET /api/v1/teacher/course-offerings/{offeringId}/judge-environment-profiles`
- `GET /api/v1/teacher/judge-environment-profiles/{profileId}`
- `PUT /api/v1/teacher/judge-environment-profiles/{profileId}`
- `POST /api/v1/teacher/judge-environment-profiles/{profileId}/archive`

### 批改、成绩册与申诉

- `POST /api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade`
- `POST /api/v1/teacher/assignments/{assignmentId}/grades/batch-adjust`
- `GET /api/v1/teacher/assignments/{assignmentId}/grades/import-template`
- `POST /api/v1/teacher/assignments/{assignmentId}/grades/import`
- `POST /api/v1/teacher/assignments/{assignmentId}/grades/publish`
- `GET /api/v1/teacher/assignments/{assignmentId}/grade-publish-batches`
- `GET /api/v1/teacher/assignments/{assignmentId}/grade-publish-batches/{batchId}`
- `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook`
- `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook/export`
- `GET /api/v1/teacher/course-offerings/{offeringId}/gradebook/report`
- `GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook`
- `GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/export`
- `GET /api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/report`
- `GET /api/v1/teacher/course-offerings/{offeringId}/students/{studentUserId}/gradebook`
- `GET /api/v1/me/course-offerings/{offeringId}/gradebook`
- `GET /api/v1/me/course-offerings/{offeringId}/gradebook/export`
- `POST /api/v1/me/submissions/{submissionId}/answers/{answerId}/appeals`
- `GET /api/v1/me/course-offerings/{offeringId}/grade-appeals`
- `GET /api/v1/teacher/assignments/{assignmentId}/grade-appeals`
- `POST /api/v1/teacher/grade-appeals/{appealId}/review`

### 实验与实验报告

- `POST /api/v1/teacher/course-offerings/{offeringId}/labs`
- `PUT /api/v1/teacher/labs/{labId}`
- `POST /api/v1/teacher/labs/{labId}/publish`
- `POST /api/v1/teacher/labs/{labId}/close`
- `GET /api/v1/teacher/course-offerings/{offeringId}/labs`
- `GET /api/v1/teacher/labs/{labId}`
- `GET /api/v1/teacher/labs/{labId}/reports`
- `GET /api/v1/teacher/lab-reports/{reportId}`
- `PUT /api/v1/teacher/lab-reports/{reportId}/review`
- `POST /api/v1/teacher/lab-reports/{reportId}/publish`
- `GET /api/v1/teacher/lab-report-attachments/{attachmentId}/download`
- `GET /api/v1/me/course-classes/{teachingClassId}/labs`
- `GET /api/v1/me/labs/{labId}`
- `POST /api/v1/me/labs/{labId}/attachments`
- `PUT /api/v1/me/labs/{labId}/report`
- `GET /api/v1/me/labs/{labId}/report`
- `GET /api/v1/me/lab-report-attachments/{attachmentId}/download`

### 站内通知

- `GET /api/v1/me/notifications`
- `GET /api/v1/me/notifications/unread-count`
- `GET /api/v1/me/notifications/stream`
- `POST /api/v1/me/notifications/{notificationId}/read`
- `POST /api/v1/me/notifications/read-all`

## 当前未纳入稳定承诺的范围

- 未来可能引入的 WebSocket、Redis 推送或其他非当前 HTTP SSE 形态的通知通道
- 更细粒度的课程成员查询、设备会话管理、自助登录终端管理
- 更复杂的题库组卷、实验报告版本历史和高级统计策略
- 除 `health` / `info` / `prometheus` 之外的其他 `actuator` 端点
