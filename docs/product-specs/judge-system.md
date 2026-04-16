# 评测系统

## 目标

交付 judge 当前切片，使平台在 assignment 已配置自动评测时，能够在正式提交之后自动创建评测作业、异步调用 go-judge 执行脚本型评测、回写聚合结果，并保留教师手动重新评测入口，为后续 grading 和更复杂实验环境提供稳定链路。

## 覆盖范围

### 功能范围

- assignment 已配置自动评测时，学生正式提交后自动创建一条 `PENDING` 评测作业
- 评测作业在事务提交后异步进入 `RUNNING -> terminal`
- 当前通过 go-judge `/run` 同步 HTTP API 执行，再由服务端异步回写结果
- 当前支持 `PYTHON3` + 文本提交体（`submissions.content_text`）的脚本型评测
- 学生按提交查看自己的评测作业列表
- 教师按提交查看评测作业列表
- 教师手动重新排队，生成新的评测作业历史
- 评测作业写入审计日志

### 不在范围

- 多语言编译链路和多文件工程评测
- 测试用例逐条明细 API
- 评测产物对象存储留存
- RabbitMQ worker、分布式调度和重试编排
- 人工批改、成绩计算、成绩发布

## 核心业务规则

1. 只有 assignment 已配置自动评测时，正式提交后才会自动创建 `AUTO` 类型的评测作业。
2. 当前自动评测只消费文本提交体；附件和目录工程不会进入 go-judge。
3. 评测作业按提交维度归属，一个提交可以拥有多次评测历史。
4. 教师手动重新排队不会修改历史评测作业，只会创建新的 `MANUAL_REJUDGE` 作业。
5. 学生只能查看自己的评测作业。
6. 教师只能查看和重排队自己课程范围内的评测作业。
7. 当前固定使用 `GO_JUDGE` 作为引擎代码，当前执行方式是服务端 AFTER_COMMIT 异步触发 + go-judge `/run`。
8. `SUCCEEDED` 表示评测流程执行成功并拿到了结论，最终判定由 `verdict` 表达；`FAILED` 表示评测基础设施或配置失败。

## 核心数据模型

- `assignment_judge_profiles`
  - `assignment_id`：一对一绑定作业
  - `source_type`：当前固定 `TEXT_BODY`
  - `language`：当前固定 `PYTHON3`
  - `entry_file_name`：当前固定 `main.py`
  - `time_limit_ms / memory_limit_mb / output_limit_kb`：资源限制
- `assignment_judge_cases`
  - `assignment_id`：所属作业
  - `case_order`：测试用例顺序
  - `stdin_text / expected_stdout`：输入输出
  - `score`：用例分值
- `judge_jobs`
  - `submission_id`：所属正式提交
  - `assignment_id`：所属作业
  - `offering_id / teaching_class_id`：冗余课程范围
  - `submitter_user_id`：提交人
  - `requested_by_user_id`：本次入队发起人
  - `trigger_type`：`AUTO / MANUAL_REJUDGE`
  - `status`：`PENDING / RUNNING / SUCCEEDED / FAILED`
  - `engine_code`：当前固定为 `GO_JUDGE`
  - `engine_job_ref`：当前固定记录同步运行标识
  - `verdict`：`ACCEPTED / WRONG_ANSWER / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED / OUTPUT_LIMIT_EXCEEDED / RUNTIME_ERROR / SYSTEM_ERROR`
  - `total_case_count / passed_case_count / score / max_score`：聚合得分
  - `stdout_excerpt / stderr_excerpt`：摘要输出
  - `time_millis / memory_bytes`：聚合资源指标
  - `error_message`：基础设施失败信息
  - `result_summary`：用户可读摘要
  - `queued_at / started_at / finished_at`：状态时间戳
- `audit_logs`
  - 记录 `JUDGE_JOB_ENQUEUED / JUDGE_JOB_STARTED / JUDGE_JOB_COMPLETED / JUDGE_JOB_FAILED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## API 边界

### 学生侧

- `GET /api/v1/me/submissions/{submissionId}/judge-jobs`

### 教师侧

- `GET /api/v1/teacher/submissions/{submissionId}/judge-jobs`
- `POST /api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue`

## 当前实现边界

- 当前只支持 `PYTHON3` + 文本提交体 + 标准输入 / 标准输出对比，不支持附件代码包、编译型语言和多文件工程。
- 当前只返回作业级聚合结果，不返回测试用例逐条详情。
- 当前采用应用内异步执行，不走 RabbitMQ worker。
- 当前没有把评测日志和产物持久化到对象存储。
- 当前只支持严格输出匹配（规范化行尾后比较），更复杂容错判定留待后续扩展。

## 验收标准

- assignment 已配置自动评测时，学生正式提交后能够看到评测作业从 `PENDING` 进入终态。
- 正确提交会得到 `SUCCEEDED + ACCEPTED` 和正确得分。
- 错误提交会得到 `SUCCEEDED + 非 ACCEPTED verdict`。
- go-judge 不可用或配置异常时，提交受理不被阻断，但评测作业会回写 `FAILED + SYSTEM_ERROR`。
- 教师重新排队后会新增一条新的评测作业历史。
- `./mvnw verify` 提供自动化测试证据。
