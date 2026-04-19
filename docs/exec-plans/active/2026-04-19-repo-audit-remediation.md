# 2026-04-19 仓库级审查缺陷修复执行计划

## 目标

基于 [todo.md](/data/code/AUBB-Server/todo.md) 中的真实缺陷与阻塞项，完成仓库级生产收口修复，关闭所有 `P0 / P1` 与“阻塞生产: yes”的问题，并同步代码、配置、compose、deploy、workflow、OpenAPI、文档与验证资产。

## 约束

- 代码事实优先，不以历史完成记录为准。
- 按依赖顺序执行，优先处理全局基线、共享能力与会导致返工的授权入口。
- 先失败测试/精确复现，再做最小实现，再同步文档，再执行验证。
- 所有数据库结构改动必须通过 Flyway。

## 阶段

### Phase 1：全局基线与共享能力

- Redis 能力边界收口
- 限流 fail-open 改为安全降级
- deploy healthcheck / readiness / workflow / 文档统一
- Maven Java 基线与 Docker runtime 基线清理
- 统一客户端 IP 解析
- 清理未接线 Redis realtime 残留或正式降级

### Phase 2：权限入口收口

- `AuthenticatedPrincipalLoader`
- `AuthzExplainApplicationService`
- `AuthzGroupApplicationService`
- `CourseAuthorizationService`
- 相关兼容测试与迁移说明同步

### Phase 3：读路径历史边界与敏感字段

- `assignment` 列表/详情权限源统一
- `submission` 历史提交列表/详情一致性
- `grading` roster 历史状态正确性
- `judge` 隐藏字段授权与敏感审计

### Phase 4：剩余模块与资产

- `lab` 列表分页下推
- `notification` SSE 契约收口
- `docs/generated/db-schema.md`
- README / ARCHITECTURE / security / reliability / deployment / stable-api / product-spec

### Phase 5：最终验证与交付

- `bash ./mvnw spotless:apply`
- 定向单元/集成测试
- `bash ./mvnw test`
- `bash ./mvnw verify`
- `docker compose config`
- `docker compose -f deploy/compose.yaml --env-file deploy/.env.production.example config`
- `actionlint`
- `todo.md` 状态与修复证据回填

## 当前结论

- 第一批真实修复对象仍是 Redis / rate limit / readiness / build/runtime / request-context。
- Redis 不是纯残留，因为缓存与限流仍在真实使用；本轮按“真实增强能力”收口，而不是直接删除。
- `AUDIT-P0-019` 已完成第一轮收口：
  - teacher judge report 的隐藏字段已改为显式依赖 `judge.view_hidden`
  - `JUDGE_HIDDEN_READ` 敏感审计已落库
  - `JudgeIntegrationTests` / `StructuredProgrammingJudgeIntegrationTests` 的新契约回归已通过
- 下一优先级保持不变：
  1. `AUDIT-P1-011` 课程模板列表治理范围过滤
  2. `AUDIT-P1-016` 我的作业列表与详情授权源统一
  3. 核实 `AUDIT-P1-017` / `AUDIT-P1-018`
  4. 收缩 `AUDIT-P1-020` / `AUDIT-P1-012`
- 额外验证风险：
  - `StructuredProgrammingJudgeIntegrationTests` 整类运行会命中真实登录限流，后续全量 `test/verify` 前需要补一轮非限流测试基线收口

## 2026-04-19 最新进展

- 已用更严格回归测试稳定复现课程成员同步后的 `role binding` 激活窗口不足问题：
  - `CourseSystemIntegrationTests#initialInstructorGrantShouldAuthorizeWithinClockSkewTolerance`
  - 旧实现下稳定报错 `DENY_NO_ROLE_BINDING`
- 已完成最小修复：
  - `CourseMemberRoleBindingSyncService` 的应用层容忍窗口提升到 `3 seconds`
  - `RoleBindingGrantQueryMapper` 查询容忍窗口同步提升到 `3 seconds`
  - 新增 `V47__expand_legacy_binding_activation_tolerance_window.sql`，让数据库函数口径与应用层一致
- 已完成定向验证：
  - `bash ./mvnw -Dtest='CourseSystemIntegrationTests#initialInstructorGrantShouldAuthorizeWithinClockSkewTolerance' test`
  - `bash ./mvnw -Dtest='StructuredProgrammingJudgeIntegrationTests#programmingAnswerSupportsCompileAndRunArgsAndExposesDetailedReport' test`
- 已完成 fresh 全量验证第一轮：
  - `bash ./mvnw test`
  - 结果：`326/326` 通过，`BUILD SUCCESS`
- 已完成最终交付验证：
  - `bash ./mvnw verify`
  - `docker build -f Dockerfile .`
  - `docker compose config`
  - `docker compose -f deploy/compose.yaml --env-file deploy/.env.production.example config`
  - `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:1.7.7`
  - 当前结果：全部通过，可作为本轮生产就绪证据
