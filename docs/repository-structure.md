# 仓库结构说明

## 目标

本说明用于帮助后续开发者快速理解当前仓库应从哪里开始阅读、哪些目录承载长期资产、哪些目录属于执行过程或构建产物，以及新增代码应放到哪里。

## 阅读顺序

1. 先看 [README.md](../README.md) 了解当前阶段与验证入口。
2. 再看 [ARCHITECTURE.md](../ARCHITECTURE.md) 和 [design.md](design.md) 了解模块边界。
3. 接着看 [product-specs/index.md](product-specs/index.md) 与 [generated/db-schema.md](generated/db-schema.md) 了解当前业务与数据模型。
4. 开始开发前，再看 [development-workflow.md](development-workflow.md) 和 `AGENTS.md`。

## 顶层目录

### `src/main/java/com/aubb/server`

Java 生产代码根目录。

- `modules/`
  - 业务模块目录，新增业务优先放在这里。
  - 当前模块：
    - `identityaccess`
    - `organization`
    - `platformconfig`
    - `audit`
    - `course`
- `common/`
  - 跨模块共享的稳定公共能力，例如错误模型、分页响应、请求上下文。
- `config/`
  - Spring Boot、Security、MyBatis-Plus 等跨模块配置。
- `infrastructure/persistence/`
  - 跨模块复用的持久化适配，不承载业务逻辑。

### `src/main/resources`

- `application.yaml`
  - 默认运行配置。
- `db/migration/`
  - Flyway 迁移脚本，任何数据库结构调整都必须在这里新增版本脚本。

### `src/test/java/com/aubb/server`

测试代码根目录。

- `integration/`
  - 面向 HTTP API 和跨模块链路的集成测试。
  - 这里承载登录、平台治理、课程系统等真实业务回归。
- `modules/<module>/domain/`
  - 模块内部的领域规则测试。
- 根目录测试
  - `HarnessHealthSmokeTests`：公共健康检查验证。
  - `RepositoryStructureTests`：仓库结构约束。
  - `AubbServerApplicationTests`：基础上下文加载验证。

测试目录不再使用生产 `api` 包名，避免把“测试分类”与“生产分层”混淆。

## 文档目录

### `docs/`

只保留长期有效、能直接指导后续开发的仓库文档。

- `index.md`
  - 文档总入口。
- `product-specs/`
  - 当前业务规格。
- `design-docs/`
  - ADR、核心设计信条等长期设计资产。
- `generated/`
  - 当前数据库结构等可生成参考。
- `exec-plans/`
  - 多步骤任务的执行计划与归档记录。

### `design/`

本目录保留较长周期的专题设计草稿，例如用户系统、课程系统扩展设计。它们可以作为后续模块输入，但不应替代 `docs/` 中反映“当前实现状态”的正式文档。

### 仓库根目录工作记忆文件

- `task_plan.md`
- `findings.md`
- `progress.md`

这三个文件只服务当前或最近一次任务执行，不属于长期规范，因此不进入 `docs/` 根目录。

## 新增代码放置规则

### 新增业务模块

放在：

- `src/main/java/com/aubb/server/modules/<module>/api`
- `src/main/java/com/aubb/server/modules/<module>/application`
- `src/main/java/com/aubb/server/modules/<module>/domain`
- `src/main/java/com/aubb/server/modules/<module>/infrastructure`

### 新增共享能力

只有在明确跨模块复用且不承载特定业务语义时，才允许放入：

- `common`
- `config`
- `infrastructure/persistence`

### 新增测试

- 跨模块或接口链路测试：`src/test/java/com/aubb/server/integration`
- 模块领域规则测试：`src/test/java/com/aubb/server/modules/<module>/domain`
- 仓库级结构或启动基线测试：`src/test/java/com/aubb/server`

## 不建议继续做的事

- 不要回退到顶层 `api / application / domain` 的按层堆目录方式。
- 不要把过程性文档直接写进 `docs/` 根目录。
- 不要把未来愿景文档当成当前实现说明。
- 不要把跨模块公共目录当成“临时兜底目录”塞业务代码。
