# 架构

## 运行时基线

AUBB-Server 是一个运行在 Java 25 上的 Spring Boot 4 后端服务。当前服务基线包含 Spring Security、Actuator、Web MVC、WebSocket、Flyway、MyBatis-Plus、PostgreSQL、RabbitMQ、Redis、Prometheus 指标以及 OpenAPI 支持。

## 当前状态

仓库仍处于平台初始化阶段，尚未实现真实的生产业务模块。当前代码库主要提供应用启动能力、安全默认配置、依赖脚手架、健康检查暴露，以及一套面向 harness 的文档支持系统。

由于还没有真正的持久化模块，应用启动过程里有意延后了 DataSource 和 Flyway 的自动配置。这保证了在第一个依赖数据库的功能落地前，服务仍然可以启动和验证。

## 目标包结构

- `config`：安全、序列化等跨领域框架配置
- `api`：控制器、请求 DTO、响应 DTO 和 WebSocket 入口
- `application`：用例编排、事务控制和服务门面
- `domain`：聚合、策略、领域服务和仓储端口
- `infrastructure`：MyBatis 映射、Flyway 迁移、消息适配器、缓存适配器和外部客户端

## 基础设施边界

- PostgreSQL 是关系型数据的唯一事实来源
- Redis 用于缓存、会话和短生命周期的协调状态
- RabbitMQ 用于异步流程和集成事件
- Flyway 负责数据库模式演进
- MyBatis-Plus 负责应用层或领域层端口之后的持久化实现细节

## 请求流向

HTTP 或 WebSocket 请求应沿着 `api -> application -> domain -> infrastructure` 向内流动，避免让框架代码和传输层细节渗入领域层。

## Harness 约束

- 仓库文档属于运行时支持系统的一部分，不是可选说明
- 架构变更应同步反映到 [docs/quality-score.md](docs/quality-score.md)
- 较大改动应在 [docs/exec-plans](docs/exec-plans/active/README.md) 下留下执行轨迹
