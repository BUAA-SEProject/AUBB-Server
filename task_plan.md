# 任务计划：取消 Harness Verify 设计

## 目标

取消仓库中的 harness verify 设计，使文档不再进入自动工作流检查，同时保留必要的代码结构约束。

## 当前阶段

Completed

## 阶段

### Phase 1：定位自动校验入口

- [x] 检查 GitHub Actions 中的 harness 工作流
- [x] 检查仓库内与文档校验相关的测试与说明
- **Status:** complete

### Phase 2：移除自动校验设计

- [x] 删除 `.github/workflows/harness.yml`
- [x] 移除 `RepositoryHarnessTests` 中的文档自动校验
- [x] 保留最小的代码结构约束测试
- **Status:** complete

### Phase 3：同步文档说明

- [x] 更新 README 中的验证说明
- [x] 更新可靠性与计划文档中的 CI/文档校验口径
- **Status:** complete

### Phase 4：验证与收尾

- [x] 执行针对性测试验证仓库仍可通过
- [x] 记录当前修改结果
- **Status:** complete

## 已做决策

| Decision | Rationale |
|----------|-----------|
| 删除专门的 harness GitHub Actions 工作流 | 文档不再需要进入自动工作流检查 |
| 用 `RepositoryStructureTests` 替代 `RepositoryHarnessTests` | 保留代码结构约束，但移除文档自动校验 |
| 已完成的历史执行计划与归档文档保持不追溯修改 | 这些是历史记录，不代表当前自动化策略 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
| 暂无 | - | - |
