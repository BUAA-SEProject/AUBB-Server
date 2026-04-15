# 可靠性

## 基线保证

- `/actuator/health` 必须保持可公开访问，用于部署检查和 harness 验证。
- PostgreSQL 已成为首个真实业务切片的必需依赖，数据库迁移由 Flyway 管理。
- RabbitMQ、Redis 仍是目标集成组件，但当前 Phase 2 不以它们作为默认启动阻塞条件。
- 本地开发优先通过 Docker Compose 提供可重复依赖，测试通过 Testcontainers 保证独立验证。

## 验证策略

- 快速路径：仓库测试验证配置、领域规则、平台治理接口、公开健康检查、必需文档和 Markdown 链接。
- 集成路径：通过 PostgreSQL Testcontainers 验证 Flyway 迁移、持久化与登录/治理链路。
- 本地运行路径：使用 `compose.yaml` 提供 PostgreSQL、RabbitMQ、Redis 开发依赖。
- CI 路径：[../.github/workflows/harness.yml](../.github/workflows/harness.yml) 会在每次 push 和 pull request 时执行 harness 验证。

## 可靠性规则

1. 新的基础设施依赖一旦进入运行时，就必须同时提供本地和 CI 都可重复执行的验证路径。
2. 数据库迁移必须在测试中真实执行，不能只依赖文档假定结构存在。
3. 优先使用稳定镜像标签和可复现的默认配置，而不是图方便的临时方案。
4. 健康检查公开能力不能被认证逻辑或数据库初始化错误意外破坏。
