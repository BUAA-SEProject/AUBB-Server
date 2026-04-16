# 仓库结构说明

## 目标

本说明用于帮助后续开发者快速理解当前仓库应从哪里开始阅读、哪些目录承载长期资产、哪些目录属于执行过程或构建产物，以及新增代码应放到哪里。

## 阅读顺序

1. 先看 [README.md](../README.md) 了解当前阶段与验证入口。
2. 再看 [ARCHITECTURE.md](../ARCHITECTURE.md) 和 [design.md](design.md) 了解模块边界。
3. 接着看 [product-specs/index.md](product-specs/index.md) 与 [generated/db-schema.md](generated/db-schema.md) 了解当前业务与数据模型。
4. 如果要继续做功能开发，再看 [exec-plans/active/README.md](exec-plans/active/README.md) 和 [../todo.md](../todo.md) 了解当前推进优先级。
5. 开始开发前，再看 [development-workflow.md](development-workflow.md) 和 `AGENTS.md`。

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
    - `assignment`
    - `submission`
    - `grading`
    - `judge`
  - 模块内先维持 `api / application / domain / infrastructure` 四层；当某一层开始混入大量不同职责文件时，再继续按职责细分子目录。
  - 当前已采用的细分模式包括：
    - `application/view`、`application/command`、`application/result`
    - `domain/<子场景>`，例如 `term`、`catalog`、`membership`
    - `infrastructure/<聚合>`，例如 `user`、`profile`、`offering`
- `common/`
  - 跨模块共享的稳定公共能力，例如错误模型、分页响应、请求上下文、对象存储。
- `config/`
  - Spring Boot、Security、MyBatis-Plus 等跨模块配置。
- `infrastructure/persistence/`
  - 跨模块复用的持久化适配，不承载业务逻辑。

### `src/main/resources`

- `application.yaml`
  - 默认运行配置，包含 JWT、OpenAPI、MinIO、go-judge 和 RabbitMQ 队列开关。
- `db/migration/`
  - Flyway 迁移脚本，任何数据库结构调整都必须在这里新增版本脚本。

### `src/test/java/com/aubb/server`

测试代码根目录。

- `integration/`
  - 面向 HTTP API 和跨模块链路的集成测试。
  - 这里承载登录、平台治理、课程系统、assignment、submission 等真实业务回归。
  - 这里也承载编程题工作区、样例试运行、成绩册和对象存储等跨模块回归。
  - 其中 `AbstractRealJudgeIntegrationTest` 会拉起 go-judge、MinIO 和 RabbitMQ Testcontainers，覆盖真实评测、队列、工作区试运行和详细日志链路。
- `domain/`
  - 早期平台治理阶段遗留的仓库级领域测试目录。
  - 当前仍保留 `iam / organization / platformconfig` 等子目录，后续新增领域测试优先按模块继续放到 `modules/<module>/domain/`。
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
  - `active/` 只放当前仍在推进的计划；已完成计划应及时移到 `completed/`。
- `object-storage.md`
  - 共享对象存储与 MinIO 接入说明。

### `design/`

本目录保留较长周期的专题设计草稿，例如用户系统、课程系统扩展设计。它们可以作为后续模块输入，但不应替代 `docs/` 中反映“当前实现状态”的正式文档。

### 仓库根目录工作记忆文件

- `task_plan.md`
- `findings.md`
- `progress.md`
- `todo.md`

这些文件服务当前或最近一次任务执行，不属于长期规范，因此不进入 `docs/` 根目录。

## 新增代码放置规则

### 新增业务模块

放在：

- `src/main/java/com/aubb/server/modules/<module>/api`
- `src/main/java/com/aubb/server/modules/<module>/application`
- `src/main/java/com/aubb/server/modules/<module>/domain`
- `src/main/java/com/aubb/server/modules/<module>/infrastructure`

如果某一层开始出现下面的信号，应继续拆到职责子目录：

- 同一目录同时出现服务类和大量 `View / Command / Result`
- 多个子场景的枚举或策略类平铺在同一 `domain` 目录
- 多个聚合的 `Entity / Mapper` 平铺在同一 `infrastructure` 目录
- 单一目录直接文件数明显高于同模块其他目录，阅读和跳转成本开始上升

当前仓库里，`course` 和 `identityaccess` 已按这套规则收敛，后续新增模块应优先沿用，而不是重新回到平铺方式。

当前编程题在线 IDE 后端能力的代码入口主要在：

- `src/main/java/com/aubb/server/modules/assignment`
  - 编程题模板工作区、题目配置快照与题目级运行环境快照
- `src/main/java/com/aubb/server/modules/submission`
  - 工作区目录树、目录操作、修订历史和模板重置
- `src/main/java/com/aubb/server/modules/judge`
  - 样例试运行、自定义标准输入试运行、详细评测报告、开课实例级评测环境模板与真实 go-judge 执行

### 新增共享能力

只有在明确跨模块复用且不承载特定业务语义时，才允许放入：

- `common`
- `config`
- `infrastructure/persistence`

其中共享对象存储当前放在：

- `src/main/java/com/aubb/server/common/storage`
- `src/main/java/com/aubb/server/config/MinioStorageConfiguration.java`

### 新增测试

- 跨模块或接口链路测试：`src/test/java/com/aubb/server/integration`
- 模块领域规则测试：`src/test/java/com/aubb/server/modules/<module>/domain`
- 仓库级结构或启动基线测试：`src/test/java/com/aubb/server`

## 不建议继续做的事

- 不要回退到顶层 `api / application / domain` 的按层堆目录方式。
- 不要把过程性文档直接写进 `docs/` 根目录。
- 不要把未来愿景文档当成当前实现说明。
- 不要把跨模块公共目录当成“临时兜底目录”塞业务代码。
