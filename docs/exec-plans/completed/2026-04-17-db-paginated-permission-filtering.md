# 2026-04-17 数据库分页权限过滤收口

## 背景

`todo.md` 优先级 9 明确要求把已确认热点列表从“内存过滤 + 内存分页”收敛为“数据库可分页权限过滤”。本轮只处理两个已经确认的热点：

- `GET /api/v1/admin/users`
- `GET /api/v1/me/assignments`

## 本轮范围

- 不泛化到所有列表接口
- 不重构权限模型
- 不把平台级治理权限和课程级资源权限混成一套 SQL
- 只补与这两个热点直接相关的 mapper、服务层权限集合解析和索引

## 现状问题

- `listUsers` 先按很宽的候选条件查询 `users`，再在 Java 侧逐条做组织作用域、画像、身份和分页过滤。
- `listMyAssignments` 先扫 assignment 候选集，再对每条作业调用 `canViewAssignment(...)`，形成 per-row 课程成员 / offering 查询放大。
- 两个接口的 `total` 都依赖过滤后的内存集合大小，而不是数据库 `count`。

## 实现决策

### 1. `listUsers`

- 组织树祖先规则继续由 `GovernanceAuthorizationService` 维护。
- 服务层先一次性解析“可管理组织集合”，再交给 `UserMapper` 做数据库侧 `count + page`。
- 用户画像、治理身份、组织成员关系只对当前页用户补全，避免继续对全量候选做批量加载。

### 2. `listMyAssignments`

- 课程级可见性继续由 `CourseAuthorizationService` 统一收敛。
- 服务层先解析“全量可见 offering 集合”，数据库侧再用 `EXISTS course_members` 区分课程公共作业和班级作业可见性。
- `total` 与 `items` 共享同一组 SQL 谓词。

### 3. 索引

新增索引只围绕新查询路径：

- `users (primary_org_unit_id, created_at DESC, id DESC)`
- `user_scope_roles (role_code, user_id)`
- `course_members (user_id, offering_id, member_status, teaching_class_id)`
- `assignments (open_at ASC, id ASC) WHERE status <> 'DRAFT'`

## 验证

- `bash ./mvnw spotless:apply`
- `bash ./mvnw -q -DskipTests compile`
- `bash ./mvnw -Dtest=GovernanceAuthorizationServiceTests,CourseAuthorizationServiceTests,PlatformGovernanceApiIntegrationTests#listsUsersByScopeAndStatusFilters+listsUsersByAcademicProfileAndRoleFilters+paginatesUsersAfterScopeAndProfileFiltersWithoutLeakingOutOfScopeUsers,AssignmentIntegrationTests#studentSeesOnlyPublishedAssignmentsForOwnCourseAndClass+paginatesMyAssignmentsAndKeepsOfferingScopeAccurate test`

结果：`BUILD SUCCESS`

## 后续

- 继续进入优先级 10：清理文档漂移并固化 OpenAPI / 稳定接口清单。
