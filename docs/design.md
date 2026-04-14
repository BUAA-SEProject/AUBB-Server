# 设计

## 设计意图

这个后端应当保持显式、可预测：边界清晰、契约有类型约束、配置来源可追踪，避免依赖仓库之外的隐性假设。

## 当前重点

- 将框架配置集中管理
- 将传输模型与持久化模型分离
- 将业务规则与框架代码解耦
- 通过健康检查、指标和日志保证运行行为可观测

## 关联阅读

- 架构细节：[../ARCHITECTURE.md](../ARCHITECTURE.md)
- 核心信念：[design-docs/core-beliefs.md](design-docs/core-beliefs.md)
- 可靠性规则：[reliability.md](reliability.md)
