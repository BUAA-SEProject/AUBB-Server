# 评测系统

## 目标

交付 judge 当前切片，使平台既能继续支持 assignment 级 legacy 脚本评测，也能在结构化编程题提交后按 `submission_answer_id` 自动创建题目级评测作业、异步调用 go-judge 执行、回写结果，并补齐学生侧样例试运行、运行日志和 `CUSTOM_SCRIPT` 第一阶段闭环。当前样例试运行与正式评测都已复用目录树源码快照装配，为后续前端在线 IDE 和更复杂实验环境提供稳定链路。

## 覆盖范围

### 功能范围

- assignment 已配置自动评测时，学生正式提交后自动创建一条 `PENDING` 评测作业
- 评测作业在事务提交后异步进入 `RUNNING -> terminal`
- 当前通过 go-judge `/run` 同步 HTTP API 执行，再由服务端异步回写结果
- 当前支持 legacy assignment 级 `PYTHON3 + submissions.content_text` 脚本型评测
- 当前支持结构化编程题 question-level judge 第一阶段：
  - 编程题隐藏测试点
  - `submission_answer_id` 级 job 关联
  - 目录树源码快照 + 附件装配为多文件输入，并兼容 legacy `codeText`
  - 逐测试点结果明细回写到 `judge_jobs.case_results_json`
- 当前支持结构化编程题样例试运行：
  - 独立的 `programming_sample_runs` 历史
  - 单样例输入输出比对
  - 完整 `stdout / stderr` 记录
- 当前支持结构化编程题 `CUSTOM_SCRIPT`：
  - 教师配置脚本内容
  - 平台固定落盘为 Python checker 执行
  - checker 通过保留文件名读取学生程序输出、期望输出与运行上下文
  - checker 以 JSON 返回 `verdict / score / message`
- 学生按提交查看自己的评测作业列表
- 学生按答案查看自己的题目级评测作业列表
- 学生按题目查看自己的样例试运行历史
- 教师按提交查看评测作业列表
- 教师按答案查看和重排题目级评测作业
- 教师手动重新排队，生成新的评测作业历史
- 评测作业写入审计日志

### 不在范围

- 在线 IDE 工作区
- 更复杂的 checker 断言库与评测产物对象存储
- 评测产物对象存储留存
- RabbitMQ worker、分布式调度和重试编排
- 人工批改和成绩发布（当前已由 grading 模块承担）

## 核心业务规则

1. 只有 assignment 已配置自动评测时，正式提交后才会自动创建 `AUTO` 类型的评测作业。
2. legacy assignment 级自动评测只消费文本提交体。
3. 结构化编程题题目级自动评测会从 `submission_answers` 读取语言、入口文件、文件树快照和附件列表，并按答案维度归属；若只有 legacy `codeText`，则按单入口文件模式兼容。
4. 教师手动重新排队不会修改历史评测作业，只会创建新的 `MANUAL_REJUDGE` 作业。
5. 学生只能查看自己的评测作业。
6. 教师只能查看和重排队自己课程范围内的评测作业。
7. 当前固定使用 `GO_JUDGE` 作为引擎代码，当前执行方式是服务端 AFTER_COMMIT 异步触发 + go-judge `/run`。
8. `SUCCEEDED` 表示评测流程执行成功并拿到了结论，最终判定由 `verdict` 表达；`FAILED` 表示评测基础设施或配置失败。
9. 结构化编程题当前支持 `STANDARD_IO` 和 `CUSTOM_SCRIPT` 两种真实执行模式；`CUSTOM_SCRIPT` 当前固定使用 Python checker，不支持教师自定义命令串。
10. checker 只能返回 JSON 裁决；checker 自身的 `stdout / stderr` 不覆盖学生程序日志，学生界面看到的仍是学生程序的输出。
11. 样例试运行与正式评测分开建模：样例试运行不写入 `judge_jobs`，也不影响正式成绩与提交次数。
12. 样例试运行与正式评测的源码装配优先消费 `entryFilePath + files`；旧 `codeText` 仅作为兼容路径保留。

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
  - `submission_answer_id`：可选，题目级评测时指向分题答案
  - `assignment_id`：所属作业
  - `assignment_question_id`：可选，题目级评测时指向题目快照
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
  - `case_results_json`：逐测试点明细摘要
  - `result_summary`：用户可读摘要
  - `queued_at / started_at / finished_at`：状态时间戳
- `programming_sample_runs`
  - `assignment_id + assignment_question_id + user_id`：运行范围与归属
  - `programming_language / code_text / entry_file_path / source_files_json / artifact_ids_json`：本次样例运行的代码快照
  - `stdin_text / expected_stdout`：样例输入输出快照
  - `status`：`RUNNING / SUCCEEDED / FAILED`
  - `verdict`：样例运行的最终判定
  - `stdout_text / stderr_text`：完整运行日志
  - `result_summary / error_message`：摘要与失败信息
  - `time_millis / memory_bytes`：资源指标
  - `started_at / finished_at`：执行时间戳
- `audit_logs`
  - 记录 `JUDGE_JOB_ENQUEUED / JUDGE_JOB_STARTED / JUDGE_JOB_COMPLETED / JUDGE_JOB_FAILED`
  - 记录 `PROGRAMMING_SAMPLE_RUN_CREATED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## API 边界

### 学生侧

- `GET /api/v1/me/submissions/{submissionId}/judge-jobs`
- `GET /api/v1/me/submission-answers/{answerId}/judge-jobs`
- `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`

### 教师侧

- `GET /api/v1/teacher/submissions/{submissionId}/judge-jobs`
- `POST /api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue`
- `GET /api/v1/teacher/submission-answers/{answerId}/judge-jobs`
- `POST /api/v1/teacher/submission-answers/{answerId}/judge-jobs/requeue`

## 当前实现边界

- 当前仍保留 assignment 级 legacy 模型；结构化编程题则已下沉到 question-level judge 第一阶段。
- 结构化编程题当前按语言装配 `PYTHON3 / JAVA17 / CPP17` 运行命令，并支持把目录树源码快照和附件一起写入运行目录；自动化验证当前已覆盖这三种语言的样例试运行与正式评测最小链路。
- `CUSTOM_SCRIPT` 当前通过固定的 Python checker 执行，checker 读取保留文件：
  - `_aubb_stdin.txt`
  - `_aubb_expected_stdout.txt`
  - `_aubb_actual_stdout.txt`
  - `_aubb_actual_stderr.txt`
  - `_aubb_judge_context.json`
- `CUSTOM_SCRIPT` 当前约定 checker 输出一段 JSON，例如 `{\"verdict\":\"ACCEPTED\",\"score\":60,\"message\":\"样例通过\"}`；非法 JSON、未知 verdict、越界分数或 checker 执行异常会落成 `SYSTEM_ERROR`。
- 当前逐测试点明细已经挂到 `judge_jobs.case_results_json` 并通过 API 返回，但尚未把完整正式评测日志和产物持久化到对象存储。
- 样例试运行当前只执行单个样例输入输出，不入队异步 `judge_jobs`，而是同步调用 go-judge 后把结果和源码快照落到 `programming_sample_runs`。
- 当前采用应用内异步执行，不走 RabbitMQ worker。
- 当前 `STANDARD_IO` 继续使用严格输出匹配（规范化行尾后比较），更复杂容错判定通过 `CUSTOM_SCRIPT` 扩展。

## 验收标准

- assignment 已配置自动评测时，学生正式提交后能够看到评测作业从 `PENDING` 进入终态。
- 正确提交会得到 `SUCCEEDED + ACCEPTED` 和正确得分。
- 错误提交会得到 `SUCCEEDED + 非 ACCEPTED verdict`。
- go-judge 不可用或配置异常时，提交受理不被阻断，但评测作业会回写 `FAILED + SYSTEM_ERROR`。
- 结构化编程题自动评测成功后，会把结果回写到 `submission_answers`，使 grading 可继续发布成绩。
- 教师重新排队后会新增一条新的评测作业历史。
- 学生样例试运行不会创建正式提交，也不会创建 `judge_jobs`。
- `./mvnw verify` 提供自动化测试证据。
