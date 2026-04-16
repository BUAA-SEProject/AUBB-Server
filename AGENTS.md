# AGENTS.md

## 使命

此仓库是一个 AUBB（Academic Unified Builder Bench）的一体化在线教学与实验平台后端。平台目标包括课程管理、在线实验、自动评测、作业布置与批改、成绩统计等能力，同时强调教学场景真实性、评测准确性与系统稳定性。仓库应继续作为一个对代理友好的 Java 后端维护，并围绕 AUBB 的教学主链路持续交付真实业务能力。

## 语言要求

- 默认使用中文进行沟通、计划编写、文档更新和实现说明。
- 除非用户明确要求英文，或涉及 API、配置项、类名、协议名等必须保留原文的技术标识，否则不要切换为英文叙述。

## 项目概览

- 运行时：Spring Boot 4 + Java 25
- 基础设施目标：PostgreSQL、RabbitMQ、Redis
- 持久化基线：Flyway + MyBatis-Plus
- 当前真实业务进度：平台治理、课程、assignment、submission、grading、judge 第一阶段已打通，并已补齐开课实例级评测环境模板、题目级运行环境快照、教师侧成绩册 CSV 导出 / 统计报告第一阶段与 `GO122` 真实评测链路；当前优先级是在线 IDE 前端、多语言运行时稳定化、题库与组卷第二阶段、多作业总评与评测可复现性
- 公共健康检查端点：`/actuator/health`
- 默认验证入口：`mvnd verify`
- 未预装 `mvnd` 时，可使用 `.\mvnw.cmd verify`；当前仓库在 Unix 环境中若 wrapper 无执行位，统一使用 `bash ./mvnw verify` 通过仓库 wrapper 自动引导对应平台的 `mvnd`
- 项目级 skills 清单： [docs/project-skills.md](docs/project-skills.md)

## 需求与设计输入

- 仓库级总计划：[`docs/plan.md`](docs/plan.md)
- 当前执行计划：[`docs/exec-plans/active/README.md`](docs/exec-plans/active/README.md)
- 系统级需求与设计来源：`../docs/02-process-docs/`、`../docs/03-product/`、`../docs/04-development/`、`../docs/05-api/`
- Phase 2 重点输入：
  - `../docs/02-process-docs/software-requirements-specification.md`
  - `../docs/02-process-docs/high-level-design.md`
  - `../docs/02-process-docs/detailed-design.md`
  - `../docs/05-api/platform-admin-api.md`

## 工作规则

1. 优先使用 Spring Boot 自动配置和共享配置类，不要零散堆积临时 wiring。
2. 默认使用 TDD：先写失败测试，再做最小实现，最后在测试通过的前提下重构。
3. 所有数据库结构变更都必须通过 Flyway，并同步更新 [docs/generated/db-schema.md](docs/generated/db-schema.md)。
4. 公共 API、WebSocket 或认证授权相关变更，必须同步更新对应的产品文档和安全文档。
5. 新的基础设施集成必须提供确定性的验证路径，优先增加 smoke test 或 integration test。
6. 基础设施镜像和运行时假设必须固定版本，不要引入浮动的 `latest` 标签。
7. 出现歧义时，要把决定写进仓库文档，而不是只停留在聊天上下文里。
8. 先锁定范围，再实现代码：V1 仅做 SRS `MUST`，不要把 `SHOULD` / `COULD` 混入当前切片。
9. 任何偏离上级系统文档的实现，都必须在仓库文档中写明“当前实现”和“后续扩展位”。
10. 默认将平台级权限和课程级权限分开建模，避免在平台治理阶段提前耦合课程域。
11. 复杂或不直观的业务逻辑应补充简洁中文注释，注释说明“为什么”，不要复述显而易见的代码动作。
12. 实现前后都要合理使用项目级 Skills，并在计划、执行说明或相关文档中体现技能选择依据。
13. Java 代码格式化统一使用 `spotless`；提交前至少执行一次 `bash ./mvnw spotless:apply`，验证时由 `mvnd verify` 或 `bash ./mvnw verify` 兜底。
14. `docs/` 目录只保留长期有效的规范、设计、规格和说明；过程性内容只允许放在仓库根目录工作记忆文件和 `docs/exec-plans/` 中。

## 变更流程

- 小变更：直接修改代码，并同步更新最近的相关文档。
- 中大型变更：在 [docs/exec-plans/active/README.md](docs/exec-plans/active/README.md) 创建或更新执行计划，完成后移动到 `completed/`。
- 架构变更：同步更新 [ARCHITECTURE.md](ARCHITECTURE.md) 和 [docs/quality-score.md](docs/quality-score.md)。
- 产品范围变更：同步更新 [docs/product-sense.md](docs/product-sense.md) 以及 [docs/product-specs/index.md](docs/product-specs/index.md) 下相关文件。
- 多步骤任务：同步维护仓库根目录的 `task_plan.md`、`findings.md`、`progress.md` 作为工作记忆，不要把这些过程性记录写入 `docs/` 根目录。

## 包结构方向

随着业务模块落地，优先采用“模块优先、模块内分层”的包结构：

- `com.aubb.server.modules.identityaccess`
- `com.aubb.server.modules.organization`
- `com.aubb.server.modules.platformconfig`
- `com.aubb.server.modules.audit`
- `com.aubb.server.common`
- `com.aubb.server.config`
- `com.aubb.server.infrastructure.persistence`

每个业务模块内部再组织以下层次：

- `api`
- `application`
- `domain`
- `infrastructure`

后续新增业务模块时，优先继续挂到 `com.aubb.server.modules.<module>` 下，例如：

- `course`
- `coursemember`
- `lab`
- `assignment`
- `submission`

## 验证清单

- 执行 `bash ./mvnw spotless:apply` 或等效格式化命令
- 执行 `mvnd verify`，或在 Unix 环境下使用 `bash ./mvnw verify`
- 保持 `/actuator/health` 可被公开读取
- 保持文档链接有效、结构清晰
- 将非显而易见的决策记录到仓库中
