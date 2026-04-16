# AUBB-Server

AUBB-Server 是 AUBB 平台的后端仓库。当前仓库已经完成平台治理基线、课程系统第一切片，并继续推进 assignment、submission、grading 与 judge，已落地平台配置、学校/学院/课程/班级组织、用户与多身份治理、账号状态、JWT 登录、基础审计，以及课程模板、开课实例、教学班、课程成员、班级功能开关、作业创建/发布/关闭、开课实例内题库、结构化试卷快照、文本与附件正式提交、分题答案与客观题自动评分摘要、教师 / 助教人工批改、assignment 级成绩发布、教师与学生成绩册第一阶段、教师侧成绩册 CSV 导出与统计报告第一阶段、多作业权重与加权总评第一阶段、学生侧成绩册 CSV 导出第一阶段、基于 go-judge 的 assignment 级脚本评测、结构化编程题题目级自动评测、编程题模板工作区、目录树工作区快照、工作区修订历史与恢复、样例试运行历史与日志回放、RabbitMQ 驱动的评测队列第一阶段，以及共享 MinIO 对象存储接入能力。

运行时基线为 Spring Boot 4 + Java 25，基础设施目标包括 PostgreSQL、RabbitMQ、Redis、Flyway 与 MyBatis-Plus。

## 当前状态

当前仓库已经打通“课程 -> 作业 -> 提交 -> 评测 -> 批改 -> 成绩”主链路的第一阶段，现状可以直接概括为：

- 平台治理、课程、assignment、submission、grading、judge 九个核心模块已经落地并有自动化测试覆盖。
- 结构化作业已支持按课程或班级发布、草稿编辑、五种题型建模、题库更新 / 归档 / 标签 / 分类、分题提交、客观题自动评分、人工批改、成绩发布。
- 编程题已支持模板工作区、目录树工作区快照、工作区修订历史 / 恢复、自定义标准输入试运行、题目级自动评测、`CUSTOM_SCRIPT` 第一阶段、`compileArgs / runArgs` 参数化编译运行、开课实例级 judge environment profiles、题目级 `languageExecutionEnvironments / executionEnvironment` 运行环境快照，以及基于真实 go-judge 验证的 `PYTHON3 / JAVA21 / CPP17 / GO122` 最小执行链路；`JAVA17` 当前仅作为兼容输入保留。
- 评测链路已支持 RabbitMQ 队列第一阶段、详细评测报告 API、测试点级完整日志和教师可见的隐藏测试输入输出；学生侧默认脱敏隐藏测试数据。
- 成绩能力已覆盖教师侧开课实例 / 教学班 / 单学生成绩册第一阶段、教师侧课程 / 班级成绩册 CSV 导出与统计报告第一阶段、统计报告五档成绩分布第一阶段、多作业权重与加权总评第一阶段，以及学生侧开课实例个人成绩册与 CSV 导出第一阶段。
- 当前最近一次全量验证结果为 `BUILD SUCCESS`，共 `87` 个测试通过。

## 下一步开发优先级

1. 在线 IDE 第二阶段：前端目录树、编辑器能力、更实时自动保存与断线恢复体验。
2. 多语言运行时稳定化：继续加固 `PYTHON3 / JAVA21 / CPP17 / GO122` 的复杂工程布局与日志一致性。
3. 题库与组卷第二阶段：分类、试卷编辑体验、快照约束。
4. 成绩与评测第二阶段：更复杂的总评策略、更完整统计、学生侧更丰富报表，以及评测结果可复现性与队列稳定化。

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
- Object storage: [docs/object-storage.md](docs/object-storage.md)
- Completed bootstrap plan: [docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md](docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md)

## 接手路径

继续开发时，建议按下面顺序进入，先建立现状认知，再进入具体功能规格和活动计划：

1. [README.md](README.md)
2. [docs/repository-structure.md](docs/repository-structure.md)
3. [docs/product-specs/index.md](docs/product-specs/index.md)
4. [docs/exec-plans/active/README.md](docs/exec-plans/active/README.md)
5. [todo.md](todo.md)

## Local Validation

- 代码格式化：`bash ./mvnw spotless:apply`
- 快速测试：`mvnd test`
- 全量验证：`mvnd verify`
- 若本机尚未安装 `mvnd`，Windows 下可执行 `.\mvnw.cmd verify`；当前仓库在 Unix 环境中若 wrapper 无执行位，统一使用 `bash ./mvnw verify`
- 仓库 wrapper 已切换为 `maven-mvnd` 分发，会按当前平台自动引导对应的 `mvnd`

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
- [docs/product-specs/grading-system.md](docs/product-specs/grading-system.md)
- [docs/product-specs/judge-system.md](docs/product-specs/judge-system.md)
- [docs/reliability.md](docs/reliability.md)
- [docs/security.md](docs/security.md)
- [docs/object-storage.md](docs/object-storage.md)

项目级 skill 清单和能力覆盖见 [docs/project-skills.md](docs/project-skills.md)。

若要在本地启用 MinIO 或 go-judge，请额外参考 [docs/object-storage.md](docs/object-storage.md)、[docs/product-specs/judge-system.md](docs/product-specs/judge-system.md) 中的环境变量与 `compose.yaml` 说明。结构化编程题当前已经具备题目级自动评测、模板工作区、目录树快照、工作区修订历史、自定义标准输入试运行，以及基于固定 Python checker 的 `CUSTOM_SCRIPT` 真实执行；`JudgeIntegrationTests`、`StructuredProgrammingJudgeIntegrationTests`、`ProgrammingWorkspaceIntegrationTests` 当前都已切换到真实 go-judge + RabbitMQ Testcontainers 验证。成绩册当前已提供教师侧与学生侧第一阶段视图，并补齐教师侧 CSV 导出、统计报告、多作业权重、统计报告五档成绩分布和学生侧 CSV 导出第一阶段。接下来应优先继续补强在线 IDE 前端、多语言稳定化、更稳定的题库组卷、更复杂总评策略，以及评测结果可复现性。
