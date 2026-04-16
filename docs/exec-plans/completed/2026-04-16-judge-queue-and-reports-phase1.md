# 2026-04-16 评测队列与详细报告第一阶段

## 目标

在保持现有 judge API 兼容的前提下，把真实 go-judge 执行链路从“应用内事件直连”推进到“RabbitMQ 队列第一阶段”，并补齐详细评测报告、参数化编译运行与真实集成测试闭环。

## 范围

- 为 `judge_jobs` 增加详细评测报告持久化字段
- 新增 RabbitMQ 队列配置、publisher、consumer 和本地异步回退
- 为结构化编程题补齐 `compileArgs / runArgs`
- 扩展详细评测报告 API，并区分学生 / 教师可见范围
- 用真实 go-judge + RabbitMQ Testcontainers 补齐集成测试

## 风险

- 队列路径会引入新的时序问题，数据库时间戳约束可能被放大
- 详细报告如果直接向学生暴露隐藏测试点，会造成判题数据泄露
- 多语言命令模板一旦写死具体数组位置，测试会变得脆弱

## 实现摘要

- 新增 `V16__judge_queue_and_reports_phase1.sql`，给 `judge_jobs` 增加 `detail_report_json`
- 新增 `JudgeQueueConfiguration`、`JudgeExecutionQueuePublisher`、`JudgeQueueConsumer`、`JudgeExecutionLocalListener`
- `JudgeExecutionService` 现在会持久化：
  - 测试点级完整 `stdout / stderr`
  - `stdinText / expectedStdout`
  - `compileCommand / runCommand`
  - `compileArgs / runArgs`
  - 执行元数据
- 结构化编程题当前支持 `compileArgs / runArgs`
- `CPP17` 现在会编译目录树中的全部翻译单元
- 新增：
  - `GET /api/v1/me/judge-jobs/{judgeJobId}/report`
  - `GET /api/v1/teacher/judge-jobs/{judgeJobId}/report`

## 决策记录

- 详细报告先落数据库 JSON，而不是直接落对象存储
- RabbitMQ 队列先做单队列第一阶段，并保留队列关闭时的本地异步回退
- 学生侧详细报告默认脱敏 `stdinText / expectedStdout`，教师侧保留完整隐藏测试数据
- `compileCommand` 的测试断言只验证“包含关键参数”，不写死命令数组位置

## 验证路径

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=JudgeIntegrationTests,StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests test`
- `bash ./mvnw clean verify`

## 结果

- 真实 go-judge + RabbitMQ 路径已纳入自动化测试
- legacy judge、question-level judge、样例试运行的正式评测链路保持兼容
- 详细评测报告和参数化编译运行已对外可见，便于后续继续做评测可复现性与日志产物对象化
