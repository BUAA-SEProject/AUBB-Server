# 2026-04-19 仓库级逐文件代码审查

## 目标

以当前 `dev` 分支代码、配置、compose、deploy、workflow、migration 和测试事实为准，完成一次仓库级逐文件代码审查，持续重建 `todo.md`，形成可信、可执行、可排序的缺陷清单，并明确哪些问题阻塞生产、哪些只是后续增强。

## 审查原则

- 代码事实优先于历史过程文档。
- 每审完一个模块，立即把问题写入 `todo.md`。
- 重点关注生产可用性，不以“能跑通 happy path”替代生产质量。
- 发现文档/代码/部署口径漂移时，直接记为正式缺陷。

## 阶段划分

### Phase A：全局工程基线

- [x] 读取仓库级入口文档与约束文件
- [x] 审计 `pom.xml`、`application.yaml`、`compose.yaml`、`deploy/compose.yaml`、`.github/workflows/*.yml`
- [x] 重建 `todo.md` 结构
- [x] 复核 Dockerfile、`.dockerignore`、deploy env examples、OpenAPI/actuator/security 暴露
- [x] 形成首批全局阻塞项与修复顺序

### Phase B：共享层与配置层

- [x] 审查 `common/**`
- [x] 审查 `config/**`
- [x] 审查 `infrastructure/persistence/**`

### Phase C：平台治理域

- [x] 审查 `identityaccess`
- [x] 审查 `organization`
- [x] 审查 `platformconfig`
- [x] 审查 `audit`

### Phase D：教学主链路

- [x] 审查 `course`
- [x] 审查 `assignment`
- [x] 审查 `submission`
- [x] 审查 `grading`
- [x] 审查 `judge`

### Phase E：扩展模块与横切收口

- [x] 审查 `lab`
- [x] 审查 `notification`
- [x] 串联登录、权限、课程成员、assignment、submission、judge、grading、lab、notification 的真实主链路
- [x] 收口 Docs / Deploy / Observability / Security / Performance 结论
- [x] 输出最终审查摘要与下一步修复顺序

## 当前已确认问题

1. Redis 去留口径严重漂移：文档声称已移除，但代码、compose、deploy、workflow 仍保留完整接线。
2. `docs/generated/db-schema.md` 只列到 V37，实际 migration 已到 V46。
3. 原 `todo.md` 被旧任务内容污染，不具备当前审查可执行性；本轮已重建。
4. `deploy/compose.yaml` 的容器 healthcheck 仍调用 `/actuator/health`，与根 compose、workflow 和文档使用 `/actuator/health/readiness` 的口径不一致。
5. Redis 限流在故障时 `fallback -> allowed()`，登录、refresh、sample-run、submission create/upload、lab upload 等真实接口会在 Redis 异常时整体失去限流保护。
6. `pom.xml` 顶层声明 Java 25，但 `maven-compiler-plugin` 仍显式保留 `source/target=8`。
7. `Dockerfile` runtime stage 仍使用 Maven 镜像，生产镜像基线未最小化。
8. `RequestContextSupport.clientIp()` 与 `RateLimitAspect` 的代理 IP 解析口径不一致，审计日志和限流主体在反向代理场景会分裂。
9. `RealtimeCoordinationService` 当前没有任何业务接线，属于预留未落地能力。
10. `course` 模块已确认两类正式缺陷：
   - 学院管理员调用 `GET /api/v1/admin/course-catalogs` 时，若未带 `departmentUnitId`，当前实现会返回全量课程模板，缺少组织范围过滤。
   - `CourseAuthorizationService` 仍在运行态混用 deprecated `AuthorizationService` 与新 `PermissionAuthorizationService`，兼容测试还在显式保留 legacy instructor 读提交行为。
11. `course` 模块还存在明显的生产性能债：
   - `listTerms/listCatalogs` 先全表拉取再做内存过滤和分页。
   - `listOfferings -> toOfferingView` 仍有 catalog/term/college/member/instructor 级 `N+1` 组装。
12. `createOffering(...)` 缺少 `startAt/endAt` 合法性校验，可把反向时间窗口写入数据库。
13. `assignment` 模块已确认“我的作业”读路径权限源分裂：
   - `listMyAssignments(...)` 仍通过 `AssignmentMapper` 直接按 `course_members` 中 `ACTIVE / DROPPED / TRANSFERRED / COMPLETED` 过滤。
   - `getMyAssignment(...)` 已依赖 `ReadPathAuthorizationService.canReadMyAssignmentHistory(...)`。
   - 现有测试已证明删除 `role_bindings` 后详情会 `403`，因此当前实现可能出现“列表还能看到任务标题，详情却 403”的权限收敛裂缝。
14. `submission` 模块已确认教师列表与详情的历史边界不一致：
   - 教师详情 `getTeacherSubmission(...)` 走 `ReadPathAuthorizationService.canReadSubmission(...)`，class-scoped 提交按 `submission.teaching_class_id` 判定。
   - 教师列表与 `latestOnly` 统计仍通过 `SubmissionMapper` 直接按 submitter 当前 `course_members.member_status='ACTIVE'` 和当前班级过滤。
   - 结果是学生退课或转班后，旧提交可能仍能按详情读取，却从教师列表、统计和后续导出入口中消失。
15. `grading` 模块已确认成绩册读模型仍按“当前 active roster”近似：
   - `GradebookQuerySql.rosterCtes()` 只纳入 `course_members.member_status='ACTIVE'` 的学生，教师成绩册 / 报表 / 导出会在退课或转班后直接漏掉历史成绩。
   - 学生自助成绩册虽然允许历史状态，但 `loadRoster(...)` 只是按班级 id / 记录 id 选第一条非空班级，转班学生可能被错误挂到旧班。
   - 结果是 rank、applicable 和导出数据在成员状态迁移后不再可信。
16. `judge` 模块已确认隐藏评测报告字段存在越权泄露面：
   - 教师评测报告 `FULL/MASKED` 的切换仍由 `submission.read_source` 决定，没有实际使用 `judge.view_hidden`。
   - migration 中内建教学角色默认不带 `judge.view_hidden`，但现有结构化 judge 集成测试已经证明普通教师仍可看到 `expectedStdout`、`runCommand` 等隐藏测试细节。
   - 查看 / 下载隐藏评测报告链路当前也没有专门的敏感审计。
17. `identityaccess` 模块已确认 deprecated 兼容授权链仍在 principal 快照和 admin authz 工具运行态存活：
   - `AuthenticatedPrincipalLoader` 在缺失 `role_bindings` 时仍回退 legacy `PermissionGrantResolver`。
   - `AuthzExplainApplicationService` / `AuthzGroupApplicationService` 仍在 `DENY_NO_ROLE_BINDING` 条件下通过 `CompatibilityPermissionGrantService` 放行。
   - `AuthzLegacyCompatibilityIntegrationTests` 仍把 legacy instructor / governance 行为锁为当前契约。
18. `lab` 模块已确认实验列表与报告列表仍通过 `selectList + stream/filter/slice` 做内存过滤和分页：
   - `listTeacherLabs(...)`、`listMyLabs(...)`、`listTeacherReports(...)` 都还没有数据库侧 count/page/filter。
   - `isLabFeatureEnabled(...)` 还会为列表中的每条实验再次查询 `teaching_classes`。
19. `notification` 模块已确认 `/api/v1/me/notifications/stream` 是单节点内存 SSE best-effort 能力，但产品规格、稳定接口清单和部署口径未完全一致：
   - 代码已暴露 SSE stream
   - `docs/product-specs/notification-center.md` 仍写“当前不承载实时推送协议”
   - `docs/stable-api.md` 又把 `/stream` 列为当前稳定接口

## 当前状态

- `todo.md` 已重建并完成全模块落账，当前 `course / assignment / submission / grading / judge / identityaccess / lab / notification` 都有明确审查结论。
- 全局、共享层、平台治理域、教学主链路和横切能力均已完成首轮逐文件审查。
- 当前最高优先级阻塞项为：
  - Redis 运行基线与限流 fail-open
  - deploy readiness / health 口径分裂
  - `identityaccess` / `course` 的旧授权兼容运行态残留
  - assignment / submission / grading 的历史状态读路径分裂
  - judge 隐藏字段越权泄露
- 下一阶段应转入按 `todo.md` 优先级顺序的修复闭环，而不是继续扩展审查范围。
