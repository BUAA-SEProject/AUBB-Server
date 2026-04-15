# 评测系统

## 目标

交付 judge 第一切片，使平台在正式提交之后能够自动生成评测作业，提供评测作业状态持久化、学生/教师查询，以及教师手动重新排队入口，为后续 go-judge 执行器接入、结果回写和成绩链路提供稳定骨架。

## 覆盖范围

### 功能范围

- 学生正式提交后自动创建一条 `PENDING` 评测作业
- 学生按提交查看自己的评测作业列表
- 教师按提交查看评测作业列表
- 教师手动重新排队，生成新的 `PENDING` 评测作业
- 评测作业写入审计日志

### 不在范围

- 直接调用 go-judge 或任何真实执行器
- `RUNNING -> terminal` 的执行与结果回写
- 测试用例明细、日志摘要、资源使用指标
- 人工批改、成绩计算、成绩发布

## 核心业务规则

1. 每次正式提交受理后，会自动创建一条 `AUTO` 类型的 `PENDING` 评测作业。
2. 评测作业按提交维度归属，一个提交可以拥有多次评测作业历史。
3. 教师手动重新排队不会修改历史评测作业，只会创建新的 `MANUAL_REJUDGE` 作业。
4. 学生只能查看自己的评测作业。
5. 教师只能查看和重排队自己课程范围内的评测作业。
6. 当前固定使用 `GO_JUDGE` 作为引擎代码，但只是预留执行器方向，不代表当前已经调用真实评测引擎。

## 核心数据模型

- `judge_jobs`
  - `submission_id`：所属正式提交
  - `assignment_id`：所属作业
  - `offering_id` / `teaching_class_id`：冗余课程范围
  - `submitter_user_id`：提交人
  - `requested_by_user_id`：本次入队发起人
  - `trigger_type`：`AUTO / MANUAL_REJUDGE`
  - `status`：当前支持 `PENDING / RUNNING / SUCCEEDED / FAILED`
  - `engine_code`：当前固定为 `GO_JUDGE`
  - `engine_job_ref`：后续执行器返回的外部任务标识
  - `result_summary`：后续结果摘要扩展位
  - `queued_at` / `started_at` / `finished_at`：状态时间戳
- `submissions`
  - 正式提交受理后作为评测作业的上游输入
- `audit_logs`
  - 记录 `JUDGE_JOB_ENQUEUED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## API 边界

### 学生侧

- `GET /api/v1/me/submissions/{submissionId}/judge-jobs`

### 教师侧

- `GET /api/v1/teacher/submissions/{submissionId}/judge-jobs`
- `POST /api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue`

## 当前实现边界

- 当前只打通“提交 -> 评测作业入队 -> 查询”的骨架，不执行真实评测。
- 评测作业当前只会停留在 `PENDING`，`RUNNING / SUCCEEDED / FAILED` 作为后续执行器回写扩展位。
- 当前没有消息队列和执行 worker；评测作业只是先落数据库并留审计。
- 当前仍未暴露测试用例明细、日志摘要和资源指标。

## 验收标准

- 学生正式提交后能够自动看到一条 `PENDING` 评测作业。
- 教师能够查看某次提交的评测作业列表。
- 教师重新排队后会新增一条 `MANUAL_REJUDGE` 类型的 `PENDING` 评测作业。
- `./mvnw verify` 提供自动化测试证据。
