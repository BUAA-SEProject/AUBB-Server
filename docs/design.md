# 设计

## 设计意图

这个后端应保持显式、可预测：边界清晰、契约有类型约束、配置来源可追踪，避免依赖仓库之外的隐性假设。

## 当前重点

- 将后端代码组织升级为“模块优先、模块内分层”的模块化单体
- 将认证切换为无状态 JWT
- 将平台治理角色改为按组织作用域分配的多身份模型
- 将用户系统拆分为“基础资料 + 平台治理身份 + 课程成员角色扩展位”三层
- 将课程系统第一切片收敛为 `course` 聚合模块，而不是一次性拆成多个课程子模块
- 将组织树固定为学校 / 学院 / 课程 / 班级四层
- 将平台配置简化为单份实时配置
- 将授权规则集中到应用层和领域层，而不是散落在 Controller

## 关联阅读

- 架构细节：[../ARCHITECTURE.md](../ARCHITECTURE.md)
- 开发流程：[development-workflow.md](development-workflow.md)
- 设计决策：[design-docs/index.md](design-docs/index.md)
- 安全规则：[security.md](security.md)
- 当前执行记录：[exec-plans/completed/2026-04-14-jwt-scope-governance-refactor.md](exec-plans/completed/2026-04-14-jwt-scope-governance-refactor.md)
