# AUBB-Server

AUBB-Server 是 AUBB 平台的后端仓库。当前仓库已经完成平台治理基线、课程系统第一切片，并进入 assignment 与 submission 第一切片，已落地平台配置、学校/学院/课程/班级组织、用户与多身份治理、账号状态、JWT 登录、基础审计，以及课程模板、开课实例、教学班、课程成员、班级功能开关、作业创建/发布/关闭和正式提交能力。

运行时基线为 Spring Boot 4 + Java 25，基础设施目标包括 PostgreSQL、RabbitMQ、Redis、Flyway 与 MyBatis-Plus。

## Repository Baseline

本仓库保留一套面向代理和开发者协作的工程化约定，用来围绕统一文档、计划和验证路径推进功能。

- Agent workflow: [AGENTS.md](AGENTS.md)
- Architecture map: [ARCHITECTURE.md](ARCHITECTURE.md)
- Docs index: [docs/index.md](docs/index.md)
- Repository structure: [docs/repository-structure.md](docs/repository-structure.md)
- Development workflow: [docs/development-workflow.md](docs/development-workflow.md)
- Project skills: [docs/project-skills.md](docs/project-skills.md)
- Design rules: [docs/design-docs/index.md](docs/design-docs/index.md)
- Product baseline: [docs/product-specs/index.md](docs/product-specs/index.md)
- Quality dashboard: [docs/quality-score.md](docs/quality-score.md)
- Completed bootstrap plan: [docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md](docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md)

## Local Validation

- 代码格式化：`./mvnw spotless:apply`
- 快速测试：`mvnd test`
- 全量验证：`mvnd verify`
- 若本机尚未安装 `mvnd`，可先执行 `.\mvnw.cmd verify` 或 `./mvnw verify`；仓库 wrapper 已切换为 `maven-mvnd` 分发，会按当前平台自动引导对应的 `mvnd`

仓库验证当前主要覆盖：

- 平台治理、用户系统与课程系统相关测试
- 公开 `/actuator/health`
- 代码结构约束

## Current Scope

当前范围请阅读：

- [docs/product-sense.md](docs/product-sense.md)
- [docs/repository-structure.md](docs/repository-structure.md)
- [docs/product-specs/platform-governance-and-iam.md](docs/product-specs/platform-governance-and-iam.md)
- [docs/product-specs/course-system.md](docs/product-specs/course-system.md)
- [docs/product-specs/assignment-system.md](docs/product-specs/assignment-system.md)
- [docs/product-specs/submission-system.md](docs/product-specs/submission-system.md)
- [docs/reliability.md](docs/reliability.md)
- [docs/security.md](docs/security.md)

项目级 skill 清单和能力覆盖见 [docs/project-skills.md](docs/project-skills.md)。
