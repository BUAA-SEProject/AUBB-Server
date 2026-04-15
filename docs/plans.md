# 计划

## 计划模型

计划本身是仓库资产。小任务可以直接执行，但多步骤或高风险工作应写入 [exec-plans/active/README.md](exec-plans/active/README.md) 所在目录。

## 当前总计划

基于 SRS 的完整开发总计划见 [plan.md](plan.md)。该文件覆盖 V1 基线、V1.1 增强、阶段拆分、横切保障、里程碑和验收门槛。

## 预期流程

1. 写清目标、范围、风险和验证路径。
2. 执行过程中，把关键决策和偏差记录进计划文件。
3. 完成后，将计划移入 `docs/exec-plans/completed/`。

过程中的临时发现、进度日志和工作记忆应保留在仓库根目录的 `task_plan.md`、`findings.md`、`progress.md`，不进入 `docs/` 根目录。

## 当前基线

引入这套流程的 harness 初始化记录见 [exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md](exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md)。
