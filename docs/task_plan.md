# 任务计划：基于 SRS 编写 AUBB 全流程开发计划

## 目标

基于 `software-requirements-specification.md` 为当前仓库产出一份完整的 `docs/plan.md`，覆盖 AUBB V1 的开发流程、阶段划分、交付门槛、跨阶段保障措施和后续增强路线。

## 当前阶段

Phase 5

## 阶段

### Phase 1：需求与现状梳理

- [x] 理解用户意图
- [x] 读取 SRS 和仓库现有规划文档
- [x] 将关键信息写入 `docs/findings.md`
- **Status:** complete

### Phase 2：计划结构设计

- [x] 明确 `docs/plan.md` 的定位与读者
- [x] 确定阶段拆分、交付层次和验证结构
- [x] 确定需要同步更新的仓库文档与测试
- **Status:** complete

### Phase 3：文档编写与接入

- [x] 编写 `docs/plan.md`
- [x] 更新 `docs/plans.md`
- [x] 让 harness 校验覆盖 `docs/plan.md`
- **Status:** complete

### Phase 4：验证

- [x] 运行 `.\mvnw.cmd verify`
- [x] 记录验证结果
- [x] 修正可能的文档或链接问题
- **Status:** complete

### Phase 5：交付

- [x] 复核输出文件
- [x] 更新 `docs/progress.md`
- [ ] 向用户交付结果
- **Status:** in_progress

## 关键问题

1. `docs/plan.md` 应如何既覆盖系统级 SRS，又对当前后端仓库可执行？
2. 如何把 V1、V1.1 和后续版本的需求优先级映射成实际开发顺序？
3. 如何把测试、文档、CI/CD、可观测性、安全和验收要求纳入每个阶段，而不是放到最后补？

## 已做决策

| Decision | Rationale |
|----------|-----------|
| 将 `docs/plan.md` 放在 `docs/` 目录 | 用户要求计划类文档统一进入 `docs/`，同时保留仓库级总计划入口定位 |
| 在 `docs/plans.md` 中链接 `docs/plan.md` | 保持现有文档入口不变，同时把详细计划接入文档树 |
| 将 `docs/plan.md` 纳入 harness 链接校验 | 既然它是关键计划文档，就应进入仓库级校验 |

## 错误记录

| Error | Attempt | Resolution |
|-------|---------|------------|
|       | 1       |            |

## 备注

- 计划采用“阶段 + 工作流 + 验收门槛”的结构，而不是纯任务清单。
- 计划以当前 `AUBB-Server` 后端仓库为主，前端和外部系统只描述协同点，不代替其它仓库的实施计划。
