# 2026-04-16 编程工作区目录树快照第一阶段

## 目标

在不破坏现有 `codeText` 契约的前提下，把编程题工作区从“单入口正文 + 附件引用”升级为“入口文件 + 目录树源码快照 + 附件引用”的后端模型，并让样例试运行与正式评测复用同一套源码装配逻辑。

## 范围

- `submission` 中的编程题工作区读写
- `submission` 中结构化编程题正式答案的源码快照表示
- `judge` 中样例试运行与正式评测的源码装配
- `programming_workspaces / programming_sample_runs` 的 Flyway 增量迁移
- 工作记忆、产品规格、数据库结构和路线文档同步

## 不在范围

- 前端目录树 IDE 的具体交互
- 新建 / 重命名 / 删除文件的专门 API
- 实时协同与增量同步协议
- 多语言稳定化与更完整运行日志留存

## 风险

- 现有客户端仍可能只发送 `codeText`，因此不能用破坏式迁移替换旧字段。
- 工作区、样例试运行和正式评测若采用不同的源码装配方式，会导致“本地能跑、正式评测不能跑”的语义偏差。
- 目录树快照一旦允许不安全路径，可能引入覆盖保留文件或逃逸运行目录的风险。

## 验证路径

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests,SubmissionIntegrationTests,JudgeIntegrationTests test`
- `bash ./mvnw clean verify`

## 决策记录

- 使用 `entryFilePath + files + artifactIds` 作为新的源码快照契约，同时保留 `codeText` 兼容语义。
- `submission` 继续负责工作区和正式答案快照，`judge` 只消费快照并执行，不反向持有工作区状态。
- 样例试运行与正式评测复用同一套 `ProgrammingSourceSnapshot` 装配逻辑，避免两套运行时模型分叉。
- 当前只落后端快照能力，不提前设计前端目录树事件协议。
