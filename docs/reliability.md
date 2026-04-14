# 可靠性

## 基线保证

- 为了仓库级 smoke 验证，应用上下文必须在没有外部服务的情况下也能启动。
- `/actuator/health` 必须保持可公开访问，用于部署检查和 harness 验证。
- PostgreSQL、RabbitMQ、Redis 是目标集成组件，但当前平台骨架启动时不依赖它们。
- 在这些集成尚未显式配置前，数据库和基础设施健康检查贡献者应保持静默。

## 验证策略

- 快速路径：仓库测试验证上下文启动、actuator 健康检查、必需文档和 Markdown 链接。
- 集成路径：[../src/test/java/com/aubb/server/TestAubbServerApplication.java](../src/test/java/com/aubb/server/TestAubbServerApplication.java) 与 [../src/test/java/com/aubb/server/TestcontainersConfiguration.java](../src/test/java/com/aubb/server/TestcontainersConfiguration.java) 仍可用于基于容器的开发验证。
- CI 路径：[../.github/workflows/harness.yml](../.github/workflows/harness.yml) 会在每次 push 和 pull request 时执行 harness 验证。

## 可靠性规则

1. 除非测试已覆盖，否则不要让基础应用在启动阶段依赖外部基础设施。
2. 如果某个功能必须在启动时依赖基础设施，就必须同时提供本地和 CI 都可重复执行的满足路径。
3. 优先使用稳定镜像标签和可复现实的默认配置，而不是图方便的临时方案。
