# 2026-04-16 在线 IDE 第二阶段后端

## 目标

在保持后端职责边界稳定的前提下，把编程题工作区从“目录树快照第一阶段”推进到“可支撑浏览器在线 IDE 的后端契约”：支持模板工作区、目录树操作、工作区修订历史、历史恢复、自定义标准输入试运行，以及工作区快照与评测环境的一致性复用。

## 范围

- `assignment` 中编程题模板工作区配置
- `submission` 中工作区目录树、目录操作、修订历史与模板重置
- `judge` 中基于当前工作区或历史修订的样例 / 自定义标准输入试运行
- `programming_workspaces / programming_workspace_revisions / programming_sample_runs` 的增量迁移
- 产品规格、数据库结构、仓库入口和工作记忆同步

## 不在范围

- 浏览器编辑器本身的语法高亮、自动补全、格式化
- 前端目录树组件和实时协同协议
- 远程开发容器、断点调试或终端仿真
- 评测产物对象存储与完整重放

## 风险

- 工作区修订如果不能稳定记录空值字段，会导致“恢复成功但输入框仍保留旧值”的隐藏状态污染。
- 模板工作区若与正式评测使用不同源码装配逻辑，会出现“在线 IDE 能跑、正式评测不能跑”的偏差。
- 目录树操作一旦放开不安全路径，可能覆盖保留文件或逃逸运行目录。

## 验证路径

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=ProgrammingWorkspaceIntegrationTests,StructuredProgrammingJudgeIntegrationTests,SubmissionIntegrationTests test`
- `bash ./mvnw clean verify`

## 决策记录

- 工作区继续放在 `submission` 模块，不把 IDE 状态推到 `judge` 或前移到 `assignment`。
- 工作区修订采用“完整快照追加写”而不是增量 patch，优先保证恢复与试运行复用的确定性。
- 样例试运行继续放在 `judge` 模块，但允许显式引用当前工作区或历史修订，以保证与正式评测环境一致。
- 编程题模板快照保存在 assignment 题目配置中，避免运行中的题库更新污染学生初始工作区。
- 当前只补齐后端契约与版本恢复，不在本仓库内实现浏览器编辑器能力。
