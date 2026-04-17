# AUBB-Server

AUBB-Server 是 AUBB 平台的后端仓库。当前仓库已经完成平台治理基线，并继续沿模块化单体路线推进课程、assignment、submission、grading、judge、lab/report 与 notification。当前真实代码已覆盖平台配置、学校/学院/课程/班级组织、用户与多身份治理、账号状态、JWT 登录、refresh token 轮换、会话撤销与强制失效、首个学校 / 管理员 bootstrap 初始化闭环、基础审计、站内通知与未读状态，以及课程模板、开课实例、教学班、课程成员、班级功能开关、作业创建 / 发布 / 关闭、开课实例内题库、结构化试卷快照、文本与附件正式提交、分题答案与客观题自动评分摘要、教师 / 助教人工批改、assignment 级成绩发布、教师与学生成绩册第一阶段、教师侧成绩册 CSV 导出与统计报告第一阶段、多作业权重与加权总评第一阶段、学生侧成绩册 CSV 导出第一阶段、成绩册排名与通过率第一阶段、成绩申诉与复核第一阶段、assignment 级批量成绩调整与 CSV 导入导出第一阶段、基于 go-judge 的 assignment 级脚本评测、结构化编程题题目级自动评测、编程题模板工作区、目录树工作区快照、工作区修订历史与恢复、样例试运行历史与日志回放、RabbitMQ 驱动的评测队列第一阶段、共享 MinIO 对象存储接入能力、judge 详细产物对象化存储 phase 2（正式评测报告下载、源码快照归档与产物追踪）、教学班级实验与实验报告 MVP，以及应用 Dockerfile、compose 本地联调入口、GHCR 镜像构建与最小 SSH deploy 流水线基线。

运行时基线为 Spring Boot 4 + Java 25，基础设施目标包括 PostgreSQL、RabbitMQ、Redis、Flyway 与 MyBatis-Plus。

## 当前状态

当前仓库已经打通“课程 -> 作业 -> 提交 -> 评测 -> 批改 -> 成绩”主链路的第一阶段，现状可以直接概括为：

- 平台治理、课程、assignment、submission、grading、judge、lab/report、notification 十一个核心能力切片已经落地并有自动化测试覆盖。
- 认证链路当前采用“JWT access token + 数据库锚定的 opaque refresh token”最小模型，支持 `/api/v1/auth/refresh`、`/api/v1/auth/revoke`、`logout` 即时失效，以及管理员和账号状态变更触发的旧会话失效。
- 新环境当前可通过受配置开关控制的启动期 bootstrap 自动创建首个学校根节点、首个学校管理员和必要平台配置，不再依赖手工插数。
- 工程化交付当前已补齐应用自身 `Dockerfile`、根 `compose.yaml` 的 `app` profile、本地容器联调入口，以及 GitHub Actions `verify -> image -> deploy` 最小流水线。
- 结构化作业已支持按课程或班级发布、草稿编辑、五种题型建模、题库更新 / 归档 / 标签 / 分类、分题提交、客观题自动评分、人工批改、成绩发布。
- lab/report 当前已支持教学班级实验草稿 / 发布 / 关闭、学生实验报告草稿 / 提交、实验报告附件上传、教师批注 / 评语 / 发布，以及 `labEnabled=false` 的后端真实拦截；当前每个学生每个实验只保留一份当前报告，不做历史版本。
- 通知中心当前已支持站内通知列表、未读数、单条已读、全部已读，以及作业发布、评测完成、成绩发布、申诉处理完成、实验发布、实验报告提交 / 评语发布等关键教学事件入箱；当前前端集成基线为轮询 + 未读数，不强依赖 WebSocket。
- 编程题已支持模板工作区、目录树工作区快照、工作区修订历史 / 恢复、自定义标准输入试运行、题目级自动评测、`CUSTOM_SCRIPT` 第一阶段、`compileArgs / runArgs` 参数化编译运行、开课实例级 judge environment profiles、题目级 `languageExecutionEnvironments / executionEnvironment` 运行环境快照，以及基于真实 go-judge 验证的 `PYTHON3 / JAVA21 / CPP17 / GO122` 最小执行链路；`JAVA17` 当前仅作为兼容输入保留。
- judge 详细产物当前已进入对象化存储 phase 2：`judge_jobs` 详细报告、正式评测源码快照、归档清单以及 `programming_sample_runs` 的详细报告 / 源码快照优先落到 MinIO，对外查询 / 下载 API 保持兼容，数据库只保留状态、摘要和对象引用。
- 评测链路已支持 RabbitMQ 队列第一阶段、详细评测报告 API、测试点级完整日志和教师可见的隐藏测试输入输出；学生侧默认脱敏隐藏测试数据，编程题基础设施失败时 answer 也会显式进入 `PROGRAMMING_JUDGE_FAILED` 终态，便于区分“失败”与“仍在等待”。
- 成绩能力已覆盖教师侧开课实例 / 教学班 / 单学生成绩册第一阶段、教师侧课程 / 班级成绩册 CSV 导出与统计报告第一阶段、统计报告五档成绩分布与通过率第一阶段、多作业权重与加权总评第一阶段、成绩册排名第一阶段、学生侧开课实例个人成绩册与 CSV 导出第一阶段，以及成绩申诉 / 复核、assignment 级批量成绩调整与 CSV 导入导出第一阶段；assignment 级成绩发布当前还会生成可追踪的发布快照批次 v1，供教师后续追溯发布时的学生成绩视图。
- OpenAPI 当前通过运行时 `GET /v3/api-docs` 暴露，`GET /swagger-ui/index.html` 提供开发联调入口；稳定接口范围已经固定到 [docs/stable-api.md](docs/stable-api.md)，并由 OpenAPI 回归测试兜底。
- 当前验证仍以仓库标准入口 `bash ./mvnw test` / `bash ./mvnw verify` 为准；数据库分页权限过滤、通知中心、lab/report、对象存储与 OpenAPI 发现路径都已有专项测试覆盖。

## 下一步开发优先级

1. 在线 IDE 第二阶段：前端目录树、编辑器能力、更实时自动保存与断线恢复体验。
2. 多语言运行时稳定化：继续加固 `PYTHON3 / JAVA21 / CPP17 / GO122` 的复杂工程布局与日志一致性。
3. 题库与组卷第二阶段：继续推进更稳定的组卷编辑体验、更强搜索与分类治理。
4. 成绩与评测第二阶段：继续推进更复杂总评、更完整统计与更强的评测结果可复现性。

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
- Stable API surface: [docs/stable-api.md](docs/stable-api.md)
- Quality dashboard: [docs/quality-score.md](docs/quality-score.md)
- Deployment guide: [docs/deployment.md](docs/deployment.md)
- Object storage: [docs/object-storage.md](docs/object-storage.md)
- Completed bootstrap plan: [docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md](docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md)

## 接手路径

继续开发时，建议按下面顺序进入，先建立现状认知，再进入具体功能规格和活动计划：

1. [README.md](README.md)
2. [docs/repository-structure.md](docs/repository-structure.md)
3. [docs/product-specs/index.md](docs/product-specs/index.md)
4. [docs/stable-api.md](docs/stable-api.md)
5. [docs/exec-plans/active/README.md](docs/exec-plans/active/README.md)
6. [todo.md](todo.md)

## Local Validation

- 代码格式化：`bash ./mvnw spotless:apply`
- 快速测试：`bash ./mvnw test` 或 `mvnd test`
- 全量验证：`mvnd verify`；若本机未安装 `mvnd`，使用 `bash ./mvnw verify`
- 应用镜像构建：`docker build -t aubb-server:local .`
- Compose 配置校验：`docker compose --profile app config`
- OpenAPI 契约 smoke：`bash ./mvnw -Dtest=OpenApiContractIntegrationTests,HarnessHealthSmokeTests test`
- 若本机尚未安装 `mvnd`，Windows 下可执行 `.\mvnw.cmd verify`；当前仓库在 Unix 环境中若 wrapper 无执行位，统一使用 `bash ./mvnw verify`
- 仓库 wrapper 已切换为 `maven-mvnd` 分发，会按当前平台自动引导对应的 `mvnd`

仓库验证当前主要覆盖：

- 平台治理、用户系统与课程系统相关测试
- 公开 `/actuator/health`
- 代码结构约束

## 必需配置

JWT 签名密钥不再提供仓库内默认值。应用启动时必须通过外部配置注入 `AUBB_JWT_SECRET`；缺失、空白或长度不足时，Spring 上下文会在启动阶段直接失败，应用也不会暴露 `/actuator/health`。

认证令牌当前采用以下安全默认值：

- access token：JWT Bearer，默认有效期 `PT2H`
- refresh token：opaque token，默认有效期 `P14D`
- refresh 时会轮换 refresh token；旧 refresh token 会立即失效
- `POST /api/v1/auth/logout`、`POST /api/v1/auth/revoke`、管理员强制失效、用户被禁用都会让该会话绑定的旧 access token 与 refresh token 立即不可用

本地运行示例：

```bash
export AUBB_JWT_SECRET='replace-with-at-least-32-characters'
bash ./mvnw spring-boot:run
```

打包后运行示例：

```bash
AUBB_JWT_SECRET='replace-with-at-least-32-characters' java -jar target/server-0.0.1-SNAPSHOT.jar
```

`compose.yaml` 当前默认仍用于宿主机运行模式下的基础设施编排；若需要让 compose 一并拉起应用，请显式启用 `app` profile，并仍然通过外部环境变量注入 `AUBB_JWT_SECRET`。

## OpenAPI 与稳定接口

- 运行时 OpenAPI JSON 入口：`GET /v3/api-docs`
- Swagger UI 入口：`GET /swagger-ui/index.html`
- 生产环境若不需要公开文档发现端点，可通过 `AUBB_API_DOCS_ENABLED=false` 和 `AUBB_SWAGGER_UI_ENABLED=false` 关闭
- 当前稳定业务接口范围见 [docs/stable-api.md](docs/stable-api.md)

本地导出 OpenAPI 示例：

```bash
curl -fsS http://localhost:8080/v3/api-docs -o openapi.json
```

## Docker 与本地联调

应用镜像当前由仓库根目录 `Dockerfile` 构建，固定使用 Java 25 运行时。常见本地路径如下：

- 只拉起基础设施，供宿主机 `spring-boot:run` 或 `java -jar` 使用：

```bash
docker compose up -d
```

- 一并拉起应用与基础设施，进行本地容器联调：

```bash
AUBB_JWT_SECRET='replace-with-at-least-32-characters' \
docker compose --profile app up --build
```

- 只构建应用镜像：

```bash
docker build -t aubb-server:local .
```

本地 compose 约定：

- 根 `compose.yaml` 的 `app` 服务默认不启用，避免破坏现有宿主机 `spring-boot-docker-compose` 使用方式。
- `app` profile 启用后，会自动连到 compose 内的 PostgreSQL、RabbitMQ、Redis、MinIO 和 go-judge。
- `app` profile 会在应用容器启动入口检查 `AUBB_JWT_SECRET`；缺失时直接失败，不提供弱默认值，同时不影响默认 infra 模式的 `compose up/down/config`。
- 若本机已有其他服务占用 `5050`、`5432`、`5672` 等端口，可通过 `AUBB_GO_JUDGE_PORT`、`AUBB_POSTGRES_PORT`、`AUBB_RABBITMQ_PORT` 等环境变量覆盖宿主机映射端口。
- 如需首启初始化，可在上述命令上额外注入 `AUBB_BOOTSTRAP_*` 环境变量。

最小远程部署和 GitHub Actions 环境变量、镜像版本与回滚方式，见 [docs/deployment.md](docs/deployment.md)。

## 首次初始化

新环境若需要一次性完成首个学校、学校管理员和基础平台配置初始化，可在启动应用时临时打开 bootstrap 开关：

```bash
export AUBB_JWT_SECRET='replace-with-at-least-32-characters'
export AUBB_BOOTSTRAP_ENABLED=true
export AUBB_BOOTSTRAP_SCHOOL_CODE='SCH-1'
export AUBB_BOOTSTRAP_SCHOOL_NAME='AUBB School'
export AUBB_BOOTSTRAP_ADMIN_USERNAME='school-admin'
export AUBB_BOOTSTRAP_ADMIN_DISPLAY_NAME='School Admin'
export AUBB_BOOTSTRAP_ADMIN_EMAIL='school-admin@example.com'
export AUBB_BOOTSTRAP_ADMIN_PASSWORD='Password123'
export AUBB_BOOTSTRAP_ADMIN_ACADEMIC_ID='AUBB-ADMIN-001'
export AUBB_BOOTSTRAP_ADMIN_REAL_NAME='学校管理员'
bash ./mvnw spring-boot:run
```

打包后也可以使用同一组环境变量：

```bash
AUBB_JWT_SECRET='replace-with-at-least-32-characters' \
AUBB_BOOTSTRAP_ENABLED=true \
AUBB_BOOTSTRAP_SCHOOL_CODE='SCH-1' \
AUBB_BOOTSTRAP_SCHOOL_NAME='AUBB School' \
AUBB_BOOTSTRAP_ADMIN_USERNAME='school-admin' \
AUBB_BOOTSTRAP_ADMIN_DISPLAY_NAME='School Admin' \
AUBB_BOOTSTRAP_ADMIN_EMAIL='school-admin@example.com' \
AUBB_BOOTSTRAP_ADMIN_PASSWORD='Password123' \
AUBB_BOOTSTRAP_ADMIN_ACADEMIC_ID='AUBB-ADMIN-001' \
AUBB_BOOTSTRAP_ADMIN_REAL_NAME='学校管理员' \
java -jar target/server-0.0.1-SNAPSHOT.jar
```

Bootstrap 语义：

- 默认关闭；只有 `AUBB_BOOTSTRAP_ENABLED=true` 时才执行。
- 首次运行会创建学校根节点、管理员、`SCHOOL_ADMIN` 作用域身份、管理员画像和单份平台配置。
- 重复执行相同配置不会重复建数据，也不会重置已有管理员密码。
- 如果数据库里已经存在不同编码的学校根节点，或者存在多个学校根节点，启动会 fail-fast。
- 完成初始化后，应关闭 `AUBB_BOOTSTRAP_ENABLED`，按正常方式重启应用。

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
- [docs/product-specs/lab-system.md](docs/product-specs/lab-system.md)
- [docs/product-specs/notification-center.md](docs/product-specs/notification-center.md)
- [docs/stable-api.md](docs/stable-api.md)
- [docs/reliability.md](docs/reliability.md)
- [docs/security.md](docs/security.md)
- [docs/object-storage.md](docs/object-storage.md)

项目级 skill 清单和能力覆盖见 [docs/project-skills.md](docs/project-skills.md)。

若要在本地启用 MinIO 或 go-judge，请额外参考 [docs/object-storage.md](docs/object-storage.md)、[docs/product-specs/judge-system.md](docs/product-specs/judge-system.md) 中的环境变量与 `compose.yaml` 说明。结构化编程题当前已经具备题目级自动评测、模板工作区、目录树快照、工作区修订历史、自定义标准输入试运行，以及基于固定 Python checker 的 `CUSTOM_SCRIPT` 真实执行；`JudgeIntegrationTests`、`StructuredProgrammingJudgeIntegrationTests`、`ProgrammingWorkspaceIntegrationTests` 当前都已切换到真实 go-judge + RabbitMQ Testcontainers 验证。judge 详细报告与样例试运行源码快照当前已开始优先写入 MinIO，对外报告查询链路保持兼容。lab/report 当前已补齐教学班级实验、学生实验报告、附件上传、教师评阅和 `labEnabled` 后端拦截 MVP。通知中心当前已补齐站内通知列表、未读数、已读状态和关键教学事件入箱。用户治理和“我的作业”两个热点列表当前已切换为数据库侧分页与权限过滤，避免继续依赖全量内存过滤。仓库入口文档、OpenAPI 路径与稳定接口清单现已收敛到代码事实；后续继续开发时，优先沿在线 IDE、多语言稳定化、题库组卷与更复杂总评四条线推进。
