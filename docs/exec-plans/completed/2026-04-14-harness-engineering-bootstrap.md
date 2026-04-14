# Harness Engineering 初始化

## 目标

基于 OpenAI《Harness Engineering》文章中描述的仓库支持模式，为 AUBB-Server 初始化一套对代理友好的 harness 系统。

## 已完成工作

- 将运行时基线统一到 Java 25
- 增加仓库级 agent 文档和架构文档
- 增加产品、可靠性、安全和质量基线文档
- 暴露 actuator 健康检查用于 smoke 验证
- 在真实持久化切片出现前，延后 DataSource 和 Flyway 自动配置
- 为知识库结构与 Markdown 链接增加仓库级测试
- 增加在 push 和 pull request 时运行 harness 的 CI 工作流
- 将基础设施镜像版本从浮动 `latest` 固定下来

## 决策

- 默认 smoke 验证不依赖 Docker，确保仓库在纯 Java 环境下也可测试
- 保留已有测试配置中的容器化开发支持
- 将仓库文档视为可执行支持结构，而不是可有可无的备注

## 下一步

- 将第一个真实业务能力写成产品规格
- 在引入领域表时补充 Flyway 迁移
- 为第一个依赖基础设施的功能增加集成测试
