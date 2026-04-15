# 开发流程

## 目标

为 AUBB-Server 提供一套稳定、可重复、可验证的默认开发流程，使代理与开发者在同一套约束下推进代码、测试和文档。

## 默认流程

1. 先锁定范围：读取 `AGENTS.md`、`ARCHITECTURE.md`、本文件、`docs/product-specs/` 与相关 `../docs`。
2. 判断任务规模：小改动直接执行；多步骤任务必须创建或更新 `docs/exec-plans/active/` 中的执行计划。
3. 选择合适 Skills：至少明确当前任务为何使用 `springboot-tdd`、`springboot-patterns`、`documentation-writer` 等项目级技能。
4. 先写失败测试：优先覆盖领域规则、接口契约、权限边界、审计行为和回归风险。
5. 做最小实现：优先在 `modules.<module>.{api,application,domain,infrastructure}` 中补齐实现，不把复杂逻辑堆进 Controller。
6. 只补必要注释：仅在业务规则、授权边界、事务选择或不直观实现处添加简洁中文注释。
7. 同步文档：涉及架构、API、数据库、安全、产品规格或上级系统文档的改动，必须在同一轮提交中同步更新。
8. 统一格式化与验证：先执行 `./mvnw spotless:apply`，再执行 `./mvnw test` 与 `./mvnw verify`。

## Skills 使用原则

| 场景 | 推荐 Skills |
| --- | --- |
| 多步骤任务与执行追踪 | `planning-with-files` |
| Spring Boot 功能实现 | `springboot-patterns` |
| 默认测试策略 | `springboot-tdd` |
| 认证授权与安全边界 | `springboot-security` |
| 文档更新与说明编写 | `documentation-writer` |
| 架构取舍记录 | `architecture-decision-records` |
| API 契约梳理 | `api-design-principles` |
| 数据库结构设计 | `postgresql-table-design` |
| 发布前验证闭环 | `springboot-verification` |

## 代码规则

1. Java 代码默认使用 `spotless` 管理格式，禁止长期依赖手工对齐。
2. 中文注释只解释业务意图、边界条件和设计原因，不复述语法动作。
3. 新增接口默认要求：
   - 输入 DTO 与输出 DTO 分离
   - 权限粗拦截放在接口层，数据作用域校验放在应用层
   - 审计要求明确时，同步补齐审计写入
4. 所有数据库变更必须通过 Flyway。
5. 业务代码默认进入 `com.aubb.server.modules.<module>`；只有跨模块共享能力才允许留在顶层 `common`、`config` 和 `infrastructure.persistence`。
6. 任何会影响上级系统口径的实现，都要在仓库文档中写清“当前实现”和“后续扩展位”。

## 文档治理规则

### 长期文档

- `docs/`：只保存长期有效的仓库资产，例如架构、开发规范、设计决策、产品规格和数据库说明。
- `../docs/`：保存系统级需求、设计、API、工程与用户文档。

### 过程性文档

- 仓库根目录：`task_plan.md`、`findings.md`、`progress.md`
- `docs/exec-plans/active/` 与 `docs/exec-plans/completed/`

禁止在 `docs/` 根目录保留单次任务的过程记录、操作日志或临时分析草稿。

## 变更同步矩阵

| 变更类型 | 至少同步的文档 |
| --- | --- |
| 架构边界调整 | `ARCHITECTURE.md`、`docs/design.md`、相关 ADR |
| 认证授权或安全策略 | `docs/security.md`、相关产品/API 文档 |
| API 契约变化 | `docs/product-specs/`、`../docs/05-api/` |
| 数据库结构变化 | Flyway、`docs/generated/db-schema.md`、`../docs/04-development/database.md` |
| 工程流程变化 | `AGENTS.md`、本文件、`../docs/04-development/engineering-standards.md` |

## 默认命令

- 格式化：`./mvnw spotless:apply`
- 快速测试：`./mvnw test`
- 全量验证：`./mvnw verify`
- 如本机已安装 `mvnd`，可使用 `mvnd test` / `mvnd verify` 提升速度
