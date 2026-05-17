# AUBB Server 后端 — Agent 规则

> **共享规则**：全局 Agent 规则（指令优先级、默认原则、任务分流、验证规则、工程实践、沟通模式、并行协作、技能引用）定义在工作区根目录的 [`AGENTS-shared.md`](../AGENTS-shared.md) 中。本文件仅包含 server 仓库特有的规则。

## 使命

此仓库是一个 AUBB（Academic Unified Builder Bench）的一体化在线教学与实验平台后端。平台目标包括课程管理、在线实验、自动评测、作业布置与批改、成绩统计等能力，同时强调教学场景真实性、评测准确性与系统稳定性。仓库应继续作为一个对代理友好的 Java 后端维护，并围绕 AUBB 的教学主链路持续交付真实业务能力。

## 语言要求

- 默认使用中文进行沟通、计划编写、文档更新和实现说明。
- 除非用户明确要求英文，或涉及 API、配置项、类名、协议名等必须保留原文的技术标识，否则不要切换为英文叙述。

## 项目概览

- 运行时：Spring Boot 4 + Java 25
- 基础设施目标：PostgreSQL、RabbitMQ、MinIO、go-judge
- 持久化基线：Flyway + MyBatis-Plus
- 安全：Spring Security（OAuth2 Resource Server）
- API 文档：SpringDoc OpenAPI（`/v3/api-docs`）
- 代码格式化：Spotless（Palantir Java format），提交前自动检查
- 模块化单体架构：`modules/<module>/{api,application,domain,infrastructure}/`
- 稳定 API 契约见 `docs/stable-api.md`
- 系统架构见 `ARCHITECTURE.md`

## 测试规范

### 测试分类
- **单元测试**：纯逻辑测试，不依赖 Spring 上下文
- **集成测试**：继承 `AbstractIntegrationTest`，使用 Testcontainers（PostgreSQL、Redis、MinIO、RabbitMQ、go-judge）

### 共享测试基础设施
- `IntegrationTestData`：共享数据工具类（resetDatabase、login、createTerm、createCatalog、createOffering、createTeachingClass、addMember、createAssignment、publishAssignment）
- `IntegrationTestConstants`：集中测试常量（JWT_SECRET、MINIO 凭据、REDIS_PASSWORD）
- `AbstractIntegrationTest`：基类，提供 mockMvc、jdbcTemplate 字段和 baseSetUp()

### 验证命令
```bash
bash ./mvnw test        # 快速：单元测试
bash ./mvnw verify      # 完整：含集成测试
bash ./mvnw spotless:check  # 代码格式检查
```
