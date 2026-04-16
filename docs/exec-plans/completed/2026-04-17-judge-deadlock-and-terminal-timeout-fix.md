# 2026-04-17 judge 死锁与终态超时修复

## 目标

修复 `StructuredProgrammingJudgeIntegrationTests` 暴露的 `TRUNCATE` 死锁和 answer 级 judge job 终态超时，让真实 RabbitMQ + go-judge 链路下的集成测试稳定可回归，并在失败时提供明确可诊断状态。

## 范围

- judge 相关集成测试的清理与等待策略
- `judge` / `submission` 模块中编程题评测失败的状态回写与日志
- 与本次改动直接相关的文档、工作记忆和验证脚本

## 非目标

- 不重构 judge 架构
- 不改动 RabbitMQ / go-judge 的整体接入方式
- 不提前进入 JWT、bootstrap、CI/CD 等后续优先级事项

## 根因假设

1. `TRUNCATE ... RESTART IDENTITY CASCADE` 与异步评测事务并发，导致 `judge_jobs / submission_answers / audit_logs` 锁冲突。
2. 8 秒固定轮询默认假定队列中不存在前序残留 work，在真实 Rabbit consumer 下这个假设过于乐观。
3. 编程题失败后 answer 仍停留在 `PENDING_PROGRAMMING_JUDGE`，削弱了“已失败”与“未消费”的区分度。

## 最小实现方案

1. 在 `AbstractRealJudgeIntegrationTest` 增加统一清理 helper：
   - 等待非终态 judge job 清空
   - purge judge 队列
   - 再执行调用方传入的 `TRUNCATE`
2. 让 judge 相关集成测试复用该 helper，而不是裸跑 `TRUNCATE`
3. 为 `submission_answers.grading_status` 增加 `PROGRAMMING_JUDGE_FAILED`
4. 在 `JudgeExecutionService` 失败分支写回失败终态和诊断日志
5. 增加回归测试，覆盖成功终态、失败终态和清理回归

## 风险

- 新增失败状态会触及 DB check constraint、枚举和相关文档
- 如果等待策略只靠固定 sleep，仍可能留下隐性竞态；需要基于 DB/queue 状态轮询
- purge 队列只能用于测试路径，不能误用到生产代码

## 验证路径

- `bash ./mvnw -Dtest=JudgeIntegrationTests,StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests test`
- 如有必要，再执行 `bash ./mvnw -Dtest=StructuredProgrammingJudgeIntegrationTests test`
- 提交前执行 `bash ./mvnw spotless:apply`

## 决策记录

- 当前优先先做“测试清理闭环 + 最小业务诊断增强”，不重构执行模型
- Rabbit 队列开启时本地 `@Async` listener 不生效，因此不以“双消费”作为主修复方向
- 2026-04-17 实际落地结果：
  - 测试侧通过 `resetJudgeTables(...)` 统一在 `TRUNCATE` 前 drain 运行中 judge job，并 purge 测试队列
  - 业务侧新增 `PROGRAMMING_JUDGE_FAILED` answer 终态，并将 `judge_jobs` 终态提交与 answer / audit side effects 拆分，避免终态长时间卡在 `RUNNING`
  - 已执行 `bash ./mvnw spotless:apply`
  - 已执行 `bash ./mvnw -Dtest=SubmissionAnswerGradingStatusTests,JudgeIntegrationTests,StructuredProgrammingJudgeIntegrationTests,ProgrammingWorkspaceIntegrationTests test`
  - 当前结果：`BUILD SUCCESS`，定向 `23` 个测试通过
