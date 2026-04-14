# AGENTS.md

## 使命

将此仓库作为一个对代理友好的 Java 后端来维护。在进行非简单改动前，先阅读 [ARCHITECTURE.md](ARCHITECTURE.md)、[docs/plans.md](docs/plans.md)、[docs/project-skills.md](docs/project-skills.md) 和 [docs/product-specs/index.md](docs/product-specs/index.md)。

## 语言要求

- 默认使用中文进行沟通、计划编写、文档更新和实现说明。
- 除非用户明确要求英文，或涉及 API、配置项、类名、协议名等必须保留原文的技术标识，否则不要切换为英文叙述。

## 项目概览

- 运行时：Spring Boot 4 + Java 25
- 基础设施目标：PostgreSQL、RabbitMQ、Redis
- 持久化基线：Flyway + MyBatis-Plus
- 公共健康检查端点：`/actuator/health`
- 验证入口：`.\mvnw.cmd verify` 或 `./mvnw verify`
- 项目级 skills 清单： [docs/project-skills.md](docs/project-skills.md)

## 工作规则

1. 优先使用 Spring Boot 自动配置和共享配置类，不要零散堆积临时 wiring。
2. 所有数据库结构变更都必须通过 Flyway，并同步更新 [docs/generated/db-schema.md](docs/generated/db-schema.md)。
3. 公共 API、WebSocket 或认证授权相关变更，必须同步更新对应的产品文档和安全文档。
4. 新的基础设施集成必须提供确定性的验证路径，优先增加 smoke test 或 integration test。
5. 基础设施镜像和运行时假设必须固定版本，不要引入浮动的 `latest` 标签。
6. 出现歧义时，要把决定写进仓库文档，而不是只停留在聊天上下文里。

## 变更流程

- 小变更：直接修改代码，并同步更新最近的相关文档。
- 中大型变更：在 [docs/exec-plans/active/README.md](docs/exec-plans/active/README.md) 创建或更新执行计划，完成后移动到 `completed/`。
- 架构变更：同步更新 [ARCHITECTURE.md](ARCHITECTURE.md) 和 [docs/quality-score.md](docs/quality-score.md)。
- 产品范围变更：同步更新 [docs/product-sense.md](docs/product-sense.md) 以及 [docs/product-specs/index.md](docs/product-specs/index.md) 下相关文件。

## 包结构方向

随着业务模块落地，优先采用以下包结构：

- `com.aubb.server.api`
- `com.aubb.server.application`
- `com.aubb.server.domain`
- `com.aubb.server.infrastructure`
- `com.aubb.server.config`

## 验证清单

- 执行 `.\mvnw.cmd verify` 或 `./mvnw verify`
- 保持 `/actuator/health` 可被公开读取
- 保持文档链接有效、结构清晰
- 将非显而易见的决策记录到仓库中
