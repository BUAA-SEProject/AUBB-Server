# 评测系统

## 目标

交付 judge 当前切片，使平台既能继续支持 assignment 级 legacy 脚本评测，也能在结构化编程题提交后按 `submission_answer_id` 自动创建题目级评测作业、通过 RabbitMQ 队列第一阶段异步调度 go-judge 执行、回写结果，并补齐学生侧样例试运行、运行日志、详细评测报告和 `CUSTOM_SCRIPT` 第一阶段闭环。当前样例试运行与正式评测都已复用目录树源码快照装配，为后续前端在线 IDE 和更复杂实验环境提供稳定链路。

## 覆盖范围

### 功能范围

- assignment 已配置自动评测时，学生正式提交后自动创建一条 `PENDING` 评测作业
- 评测作业在事务提交后异步进入 `RUNNING -> terminal`
- 当前通过 go-judge `/run` 同步 HTTP API 执行，再由服务端异步回写结果；自动化测试当前已切到真实 go-judge Testcontainers
- 当前已支持 RabbitMQ 队列第一阶段：
  - 入队时在事务提交后发布 `judgeJobId`
  - consumer 拉取后执行 go-judge
  - 队列关闭时自动回退到应用内本地异步监听
- 当前支持 legacy assignment 级 `PYTHON3 + submissions.content_text` 脚本型评测
- 当前支持结构化编程题 question-level judge 第一阶段：
  - 编程题隐藏测试点
  - `submission_answer_id` 级 job 关联
  - 目录树源码快照 + 附件装配为多文件输入，并兼容 legacy `codeText`
  - 逐测试点结果明细回写到 `judge_jobs.case_results_json`
  - 编程题配置 `compileArgs / runArgs`
  - 开课实例级 judge environment profiles 管理与复用
  - 编程题配置题目级 `languageExecutionEnvironments`
  - 编程题配置题目级 `executionEnvironment`：
    - 环境标签 / 语言版本标签
    - 编译命令 / 运行命令模板
    - 环境变量
    - 工作目录
    - 初始化脚本
    - 支持文件
    - CPU 速率限制
  - 详细评测报告回写到 `judge_jobs.detail_report_json`
- 当前支持结构化编程题样例试运行：
  - 独立的 `programming_sample_runs` 历史
  - 单样例或自定义标准输入运行
  - 可直接使用显式源码快照、当前工作区或历史工作区修订
  - 完整 `stdout / stderr` 记录和详细报告
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
- 分布式 worker 横向扩展、重试编排和死信恢复
- 人工批改和成绩发布（当前已由 grading 模块承担）

## 核心业务规则

1. 只有 assignment 已配置自动评测时，正式提交后才会自动创建 `AUTO` 类型的评测作业。
2. legacy assignment 级自动评测只消费文本提交体。
3. 结构化编程题题目级自动评测会从 `submission_answers` 读取语言、入口文件、文件树快照和附件列表，并按答案维度归属；若只有 legacy `codeText`，则按单入口文件模式兼容。
4. 教师手动重新排队不会修改历史评测作业，只会创建新的 `MANUAL_REJUDGE` 作业。
5. 学生只能查看自己的评测作业。
6. 教师只能查看和重排队自己课程范围内的评测作业。
7. 当前固定使用 `GO_JUDGE` 作为引擎代码；默认执行方式为 AFTER_COMMIT 发布事件，经 RabbitMQ consumer 拉起 go-judge `/run`，关闭队列时回退到应用内本地异步执行。
8. `SUCCEEDED` 表示评测流程执行成功并拿到了结论，最终判定由 `verdict` 表达；`FAILED` 表示评测基础设施或配置失败。
9. 结构化编程题 answer 的评测回写状态当前区分：
  - `PENDING_PROGRAMMING_JUDGE`：已受理但尚未得到最终结果
  - `PROGRAMMING_JUDGED`：评测完成并已回写得分
  - `PROGRAMMING_JUDGE_FAILED`：评测流程已终止但未得到可用分数，需依据 `feedbackText / judge_jobs.error_message` 排障或重评
10. 结构化编程题当前支持 `STANDARD_IO` 和 `CUSTOM_SCRIPT` 两种真实执行模式；`CUSTOM_SCRIPT` 当前固定使用 Python checker，不支持教师自定义命令串。
11. checker 只能返回 JSON 裁决；checker 自身的 `stdout / stderr` 不覆盖学生程序日志，学生界面看到的仍是学生程序的输出。
12. 样例试运行与正式评测分开建模：样例试运行不写入 `judge_jobs`，也不影响正式成绩与提交次数。
13. 样例试运行与正式评测的源码装配优先消费 `entryFilePath + files + directories`；旧 `codeText` 仅作为兼容路径保留。
14. 样例试运行当前区分 `SAMPLE / CUSTOM` 两种输入模式；若请求带 `workspaceRevisionId`，则优先复用该修订快照，否则可按 `useWorkspaceSnapshot=true` 读取当前工作区。
15. 编译失败、运行失败和资源超限当前统一视为“评测成功但结论非通过”：
  - 编译失败当前落成 `SUCCEEDED + RUNTIME_ERROR`，并在摘要中明确标注“编译失败”
  - 运行时异常当前落成 `SUCCEEDED + RUNTIME_ERROR`，并在摘要中明确标注“程序运行失败”
  - 超时 / 超内存 / 超输出当前分别落成 `TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED / OUTPUT_LIMIT_EXCEEDED`
16. `result_summary` 当前要求是稳定的人类可读摘要；legacy job、question-level judge 和样例试运行都必须对同一类失败给出一致中文描述。
17. `detail_report_json` 保存测试点级完整日志、执行命令和执行元数据；学生侧报告默认隐藏 `stdinText / expectedStdout`，教师侧保留。
18. 为降低终态卡在 `RUNNING` 的风险，当前实现会先独立提交 `judge_jobs` 终态，再回写 `submission_answers` 与审计日志；后续同步失败时保留终态和错误日志，避免“已执行但看不到终态”。
19. 对于存在编译阶段的语言，当前实现会拆成“编译 -> 运行”两个真实 go-judge `/run` 调用，并通过 `copyOut / copyIn` 回传编译产物，避免编译结果在第二阶段沙箱中丢失。
20. 当前“支持文件”仅表示题目配置中的受控辅助文件，通过 go-judge `copyIn` 注入运行目录，不表示动态宿主目录挂载。
21. 开课实例级 `judge_environment_profiles` 当前作为可复用模板存在；教师在题库题目或 assignment question 中通过 `profileId / profileCode` 引用时，平台会先解析模板，再把结果快照固化进题目配置。
22. 编程题当前支持两种环境选择方式：
  - `languageExecutionEnvironments`：按语言命中独立环境，优先级最高
  - `executionEnvironment`：旧单环境字段，作为未命中语言时的共享回退

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
- `judge_environment_profiles`
  - `offering_id`：模板所属开课实例
  - `profile_code / normalized_code`：模板编码与大小写无关查找键
  - `programming_language`：模板绑定语言
  - `language_version / working_directory / init_script / compile_command / run_command`：模板化执行环境
  - `environment_variables_json / support_files_json`：环境变量与支持文件
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
  - `detail_report_json`：详细评测报告 JSON，包含执行元数据与完整测试点日志
  - `result_summary`：用户可读摘要
  - `queued_at / started_at / finished_at`：状态时间戳
- `programming_sample_runs`
  - `assignment_id + assignment_question_id + user_id`：运行范围与归属
  - `programming_language / code_text / entry_file_path / source_files_json / source_directories_json / artifact_ids_json`：本次样例运行的代码快照
  - `stdin_text / expected_stdout`：样例输入输出快照
  - `workspace_revision_id`：可空，引用本次运行所基于的工作区修订
  - `input_mode`：`SAMPLE / CUSTOM`
  - `status`：`RUNNING / SUCCEEDED / FAILED`
  - `verdict`：样例运行的最终判定
  - `stdout_text / stderr_text`：完整运行日志
  - `result_summary / error_message`：摘要与失败信息
  - `detail_report_json`：样例运行详细报告
  - `time_millis / memory_bytes`：资源指标
  - `started_at / finished_at`：执行时间戳
- `audit_logs`
  - 记录 `JUDGE_JOB_ENQUEUED / JUDGE_JOB_STARTED / JUDGE_JOB_COMPLETED / JUDGE_JOB_FAILED`
  - 记录 `PROGRAMMING_SAMPLE_RUN_CREATED`
  - 记录 `JUDGE_ENVIRONMENT_PROFILE_CREATED / UPDATED / ARCHIVED`

详细字段以 [../generated/db-schema.md](../generated/db-schema.md) 为准。

## API 边界

### 学生侧

- `GET /api/v1/me/submissions/{submissionId}/judge-jobs`
- `GET /api/v1/me/submission-answers/{answerId}/judge-jobs`
- `GET /api/v1/me/judge-jobs/{judgeJobId}/report`
- `POST /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs`
- `GET /api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs/{sampleRunId}`

### 教师侧

- `POST /api/v1/teacher/course-offerings/{offeringId}/judge-environment-profiles`
- `GET /api/v1/teacher/course-offerings/{offeringId}/judge-environment-profiles`
- `GET /api/v1/teacher/judge-environment-profiles/{profileId}`
- `PUT /api/v1/teacher/judge-environment-profiles/{profileId}`
- `POST /api/v1/teacher/judge-environment-profiles/{profileId}/archive`
- `GET /api/v1/teacher/submissions/{submissionId}/judge-jobs`
- `POST /api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue`
- `GET /api/v1/teacher/submission-answers/{answerId}/judge-jobs`
- `POST /api/v1/teacher/submission-answers/{answerId}/judge-jobs/requeue`
- `GET /api/v1/teacher/judge-jobs/{judgeJobId}/report`

## 当前实现边界

- 当前仍保留 assignment 级 legacy 模型；结构化编程题则已下沉到 question-level judge 第一阶段。
- 结构化编程题当前按语言装配 `PYTHON3 / JAVA21 / CPP17 / GO122` 运行命令，并支持把目录树源码快照、附件和题目级支持文件一起写入运行目录；自动化验证当前已覆盖这四种语言的样例试运行与正式评测最小链路。
- 结构化编程题当前支持 `compileArgs / runArgs`：
  - `PYTHON3`：解释器参数走 `compileArgs`，脚本参数走 `runArgs`
  - `JAVA21 / JAVA17`：编译参数追加到 `javac`，运行参数追加到 `java`
  - `CPP17`：编译阶段会收集目录树中的全部 `.cpp / .cc / .cxx / .c`，并追加 `compileArgs` 与 `runArgs`
  - `GO122`：编译参数追加到 `go build`，运行参数追加到二进制入口
- 结构化编程题当前支持开课实例级 `judge_environment_profiles` 与题目级 `languageExecutionEnvironments / executionEnvironment`：
  - 教师可先在开课实例下维护 `profileCode / profileName / programmingLanguage` 唯一的环境模板
  - 题目配置可按 `programmingLanguage` 绑定模板并做题目级覆盖，最终仍保存为 assignment question snapshot
  - `profileId / profileCode / profileName / profileScope / languageVersion` 作为快照元数据随 assignment question 固化
  - `workingDirectory / initScript / environmentVariables / supportFiles` 会真实映射到 go-judge 执行目录和环境变量
  - `compileCommand / runCommand` 当前以模板化 shell 命令执行，可引用入口文件、工作目录和参数占位符
- 对于 `JAVA21 / JAVA17 / CPP17 / GO122` 这类需要编译的语言，当前实现会先在真实 go-judge 沙箱内编译，再通过 `copyOut / copyIn` 回传编译产物到第二阶段执行，保证编译资源窗口和运行资源窗口可以独立控制。
- `JAVA21` 当前已固定为“编译全部 `.java` 源文件到当前工作目录，再按入口文件 package + 类名启动”的运行模板，因此已支持目录树场景下的多文件、嵌套路径和 package 化入口；`JAVA17` 仅作为兼容输入保留。
- `GO122` 当前默认以入口文件所在目录作为工作目录，并通过 `GOCACHE=/tmp/go-build`、`CGO_ENABLED=0`、`GOFLAGS=-p=1`、`GOMAXPROCS=1` 收敛真实 go-judge 沙箱内的编译并发和资源抖动。
- `CUSTOM_SCRIPT` 当前通过固定的 Python checker 执行，checker 读取保留文件：
  - `_aubb_stdin.txt`
  - `_aubb_expected_stdout.txt`
  - `_aubb_actual_stdout.txt`
  - `_aubb_actual_stderr.txt`
  - `_aubb_judge_context.json`
- `CUSTOM_SCRIPT` 当前约定 checker 输出一段 JSON，例如 `{\"verdict\":\"ACCEPTED\",\"score\":60,\"message\":\"样例通过\"}`；非法 JSON、未知 verdict、越界分数或 checker 执行异常会落成 `SYSTEM_ERROR`。
- 当前逐测试点明细已经挂到 `judge_jobs.case_results_json` 并通过 API 返回；同时补充 `detail_report_json` 保存完整测试点日志、执行命令和执行元数据，但正式评测产物尚未持久化到对象存储。
- 样例试运行当前不入队异步 `judge_jobs`，而是同步调用 go-judge 后把结果、详细报告和源码快照落到 `programming_sample_runs`；输入可来自题目样例或学生自定义标准输入。
- 样例试运行当前可直接运行当前工作区或历史工作区修订，用来保证“断线恢复后的再次试运行”和“正式评测前最后一次自测”共享同一份源码快照。
- 当前已支持 RabbitMQ 队列第一阶段，并保留本地异步回退路径；尚未拆分独立评测 worker 与重试编排。
- 为避免真实 RabbitMQ 集成测试与残留评测事务互相干扰，judge 相关 Testcontainers 集成测试当前会在清理前先 drain 运行中 job 并 purge 测试队列，再执行 `TRUNCATE`。
- 当前 `STANDARD_IO` 继续使用严格输出匹配（规范化行尾后比较），更复杂容错判定通过 `CUSTOM_SCRIPT` 扩展。
- 当前失败态摘要已经做了第一阶段规范化：
  - legacy assignment 级评测、question-level judge 和样例试运行会统一输出“编译失败 / 程序运行失败 / 超出时间限制 / 超出内存限制 / 超出输出限制”等中文摘要
  - 编译失败暂不单独新增 verdict，而是继续映射到 `RUNTIME_ERROR`，避免打破既有 API 枚举
- 当前四种语言的 V1 运行模板约束为：
  - `PYTHON3`：直接执行入口脚本
  - `JAVA21`：编译全部 `.java` 文件，支持嵌套目录与 package 入口
  - `CPP17`：编译目录树内全部翻译单元，并按入口文件启动
  - `GO122`：按工作目录执行 `go build`，支持带 `go.mod` 的多文件工程

## 验收标准

- assignment 已配置自动评测时，学生正式提交后能够看到评测作业从 `PENDING` 进入终态。
- 正确提交会得到 `SUCCEEDED + ACCEPTED` 和正确得分。
- 错误提交会得到 `SUCCEEDED + 非 ACCEPTED verdict`。
- go-judge 不可用或配置异常时，提交受理不被阻断，但评测作业会回写 `FAILED + SYSTEM_ERROR`。
- 结构化编程题自动评测成功后，会把结果回写到 `submission_answers.PROGRAMMING_JUDGED`，使 grading 可继续发布成绩。
- 结构化编程题自动评测若基础设施失败，会把 answer 标记为 `PROGRAMMING_JUDGE_FAILED` 并保留失败反馈，便于教师区分“尚未评测”和“评测已失败”。
- 教师重新排队后会新增一条新的评测作业历史。
- 学生和教师都可以查询详细评测报告；学生侧默认看不到隐藏测试输入输出，教师侧可见。
- RabbitMQ 队列开启时，legacy judge、question-level judge 和详细报告回归都能通过真实 go-judge + RabbitMQ Testcontainers 验证。
- 学生样例试运行不会创建正式提交，也不会创建 `judge_jobs`，但会保留目录树快照、工作区修订引用和详细日志。
- `mvnd verify` 或 `bash ./mvnw verify` 提供自动化测试证据。
