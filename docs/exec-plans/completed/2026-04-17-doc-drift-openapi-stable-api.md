# 2026-04-17 文档漂移与 OpenAPI / 稳定接口清单收口

## 背景

`todo.md` 优先级 10 要求清理仓库入口文档、能力边界和接口契约漂移，并固化 OpenAPI / 稳定接口清单。本轮目标不是补一句说明，而是让文档和代码事实重新对齐。

## 本轮范围

- 清理入口文档、架构文档和长期索引中的明显过时表述
- 固化 OpenAPI 事实入口和稳定接口范围
- 不重写历史完成计划，不额外引入静态 OpenAPI 生成插件或新的发布基础设施

## 现状问题

- `ARCHITECTURE.md` 仍写“JWT Bearer Token，服务端无状态校验”，与当前 `auth_sessions` 会话校验实现冲突。
- `docs/exec-plans/tech-debt-tracker.md` 仍把仓库描述为“无真实业务模块 / 无 Flyway / 认证占位 / 无业务 OpenAPI”。
- 仓库已经真实公开 `/v3/api-docs` 和 `/swagger-ui/index.html`，但没有把“哪个路径是契约入口、哪些接口进入稳定面”固化成长期文档。

## 实现决策

### 1. 以代码事实为准

- `pom.xml` 已接入 `springdoc-openapi-starter-webmvc-ui`
- `application.yaml` 已显式配置 `springdoc.api-docs` 与 `springdoc.swagger-ui`
- `SecurityConfig` 已公开放行 `/v3/api-docs/**`、`/swagger-ui/**` 与 `/swagger-ui.html`

因此本轮直接把运行时 `/v3/api-docs` 认定为事实契约入口，而不是再创造第二套文档来源。

### 2. 新增稳定接口清单

- 新增 `docs/stable-api.md`
- 明确区分：
  - 运行时 OpenAPI / Swagger 入口
  - 当前纳入稳定维护范围的业务接口
  - 当前不纳入稳定承诺的接口范围

### 3. 只修长期文档与索引

- 更新 `README.md`、`ARCHITECTURE.md`、`docs/index.md`、`docs/repository-structure.md`
- 更新 `docs/security.md`、`docs/reliability.md`
- 修正 `docs/product-specs/platform-governance-and-iam.md`
- 重写明显过时的 `docs/exec-plans/tech-debt-tracker.md`
- 历史 `completed/` 计划保留其历史上下文，不直接改写为“当前状态”

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -Dtest=OpenApiContractIntegrationTests,AubbServerApplicationTests,HarnessHealthSmokeTests,DeliveryPipelineAssetsTests test`

结果：`BUILD SUCCESS`，定向 `7` 个测试通过

## 后续

- `todo.md` 优先级 1-10 已全部收口。
- 下一阶段进入在线 IDE 第二阶段、多语言运行时稳定化、题库组卷增强与更复杂总评 / 可复现性。
