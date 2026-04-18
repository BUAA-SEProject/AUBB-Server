# 权限系统改造差距分析与实施草案

> 依据：`docs/product-specs/permission-system.md`
> 范围：本草案仅用于当前步骤的仓库盘点、差距分析与实施排序，不包含数据库或业务大改。

## 1. 当前仓库中的相关模块

### 1.1 身份、认证、授权、审计

- `src/main/java/com/aubb/server/modules/identityaccess`
  - `application/auth`：登录、JWT、refresh token、session 校验、当前用户快照
  - `application/authz`：权限码、作用域解析、授权决策、policy guard、授权组、授权解释
  - `application/iam`：治理身份（`user_scope_roles`）分配与治理范围校验
  - `application/user`：用户、档案、组织成员关系维护
  - `infrastructure/auth*` / `role` / `user` / `membership` / `profile`
- `src/main/java/com/aubb/server/modules/audit`
  - 审计日志写入与后台查询
- `src/main/java/com/aubb/server/config/SecurityConfig.java`
  - Spring Security 入口、JWT 资源服务器配置、未授权/拒绝处理
- `src/main/java/com/aubb/server/config/JwtPrincipalAuthenticationConverter.java`
  - JWT -> `AuthenticatedUserPrincipal`

### 1.2 组织、课程、开课、班级、成员

- `src/main/java/com/aubb/server/modules/organization`
  - `org_units` 组织树
- `src/main/java/com/aubb/server/modules/course`
  - `course_catalogs`
  - `course_offerings`
  - `teaching_classes`
  - `course_members`
  - `CourseAuthorizationService`

### 1.3 与权限强耦合的业务模块

- `src/main/java/com/aubb/server/modules/assignment`
- `src/main/java/com/aubb/server/modules/submission`
- `src/main/java/com/aubb/server/modules/grading`
- `src/main/java/com/aubb/server/modules/judge`
- `src/main/java/com/aubb/server/modules/lab`
- `src/main/java/com/aubb/server/modules/course/application/announcement`
- `src/main/java/com/aubb/server/modules/course/application/resource`
- `src/main/java/com/aubb/server/modules/course/application/discussion`

### 1.4 数据库迁移与测试

- `src/main/resources/db/migration/V1__phase2_platform_governance_and_iam.sql`
- `src/main/resources/db/migration/V3__course_system_first_slice.sql`
- `src/main/resources/db/migration/V24__single_school_root_guard.sql`
- `src/main/resources/db/migration/V34__authz_group_rbac_abac_foundation.sql`
- `src/main/resources/db/migration/V35__course_member_extended_roles.sql`
- `src/test/java/com/aubb/server/integration/Authz*.java`

## 2. 当前已有实现

### 2.1 用户与身份模型

- 用户主表：`users`
- 学术档案：`academic_profiles`
- 组织成员关系：`user_org_memberships`
- 旧治理身份：`user_scope_roles`
- 旧平台角色：`user_platform_roles`（实体仍在，已非主路径）

### 2.2 组织与作用域模型

- 组织树当前为 `SCHOOL -> COLLEGE -> COURSE -> CLASS`
- 授权作用域枚举已支持 `SCHOOL/COLLEGE/COURSE/OFFERING/CLASS`
- `offering` 不是组织树节点，而是通过 `AuthzScopeResolutionService` 动态补链

### 2.3 当前认证方式

- 表单用户名密码登录
- JWT access token + refresh token
- `auth_sessions` 维护会话与 refresh token 撤销
- access token 每次请求都会校验 session 是否仍有效

### 2.4 当前权限/角色实现

- 已有统一权限枚举：`PermissionCode`
- 已有统一授权服务：`AuthorizationService`
- 已有两类 grant 来源：
  - 旧治理身份 -> `LegacyGovernanceGrantResolver`
  - 课程成员关系 -> `LegacyCourseMemberGrantResolver`
  - 新授权组 -> `PersistedAuthzGroupGrantResolver`
- 已有内建授权组模板：`school-admin / college-admin / course-admin / class-admin / offering-instructor / class-instructor / offering-ta / class-ta / student / observer / audit-readonly / grade-corrector`
- 已有基础 ABAC guard：
  - 账号状态不可用拦截
  - 归档资源默认只读
  - 禁止自批改/自改分/自处理申诉/自审实验报告

### 2.5 当前路由与中间件风格

- 路由普遍只有 `@PreAuthorize("isAuthenticated()")`
- 细粒度权限多数在应用服务里通过 `CourseAuthorizationService` 手工断言
- 少数后台治理接口仍直接依赖 `hasAuthority('SCHOOL_ADMIN')` 等旧 authority

### 2.6 当前审计

- 已有 `audit_logs`
- 业务操作已有大量审计埋点
- 拒绝访问会走 `AuthzAuditService`
- 但审计字段仍偏“操作日志”，不是完整的“授权决策日志”

## 3. 与 spec 的主要差距

### 3.1 组织与作用域结构差距

1. 当前组织树缺少 `offering` 层级节点，spec 明确要求 `school -> college -> course -> offering -> class`。
2. 当前 `OrgUnitType` 只有 `SCHOOL/COLLEGE/COURSE/CLASS`，与 spec 不一致。
3. 当前 `OrganizationPolicy` 强约束 `COURSE` 下只能建 `CLASS`，不支持 `offering` 成为正式层级。
4. `V24__single_school_root_guard.sql` 强制单学校根节点，当前实现与 spec 的后续多学校扩展目标存在结构耦合。

### 3.2 角色模型差距

1. 现有内建角色名称与 spec 不一致：
   - `course-admin` vs `course_manager`
   - `offering-instructor` vs `offering_teacher`
   - `class-instructor` vs `class_teacher`
2. spec 要求的 `offering_coordinator`、`judge_admin`、`auditor`、`grader` 尚未完整落地。
3. 当前仍保留旧治理身份模型 `user_scope_roles`，与 spec 中“用户不直接存单一全局角色、统一走 role binding”不一致。
4. 当前没有独立的 `roles / role_permissions / role_bindings` 标准表，使用的是 `auth_group_templates / auth_groups / auth_group_template_permissions / auth_group_members` 的过渡形态。

### 3.3 权限点模型差距

1. 当前权限命名与 spec 存在系统性偏差：
   - `assignment.*` 代替 spec 的 `task.*`
   - `judge.profile.manage` / `judge.hidden.read` 代替 `judge.config` / `judge.view_hidden`
   - `submission.code.read_sensitive` 代替 `submission.read_source`
   - `grade.export.class/offering` 代替 spec 的统一 `grade.export`
2. spec 中以下权限尚未完整建模：
   - `member.import` / `member.export`
   - `role_binding.read` / `role_binding.manage`
   - `submission.download` / `submission.export` / `submission.comment`
   - `ranking.read`
   - `ide.read/save/run/submit`
   - `judge.run/rejudge/read_log/config/view_hidden/manage_environment`
   - `announcement.publish`
   - `resource.read/upload/manage`
   - `report.read/export`
   - `analytics.read`
   - `appeal.create`
   - `audit.export`

### 3.4 成员关系与角色绑定分层差距

1. spec 要求 `offering_members` / `class_members` 与 `role_bindings` 分层共存。
2. 当前只有 `course_members` 一张业务+授权混合表，同时还被直接映射为 group binding。
3. 这导致业务成员关系与授权模型耦合，后续引入显式 role binding 时迁移复杂度较高。

### 3.5 ABAC 与状态驱动差距

已落地：

- 归档资源只读
- 自操作禁止
- 学生仅看自己提交/成绩
- 作业草稿对学生不可见

未完整落地或不够统一：

1. “成绩仅在 published 后学生可见”是业务内逻辑，不是统一授权规则。
2. “退课学生不可继续提交，仅可只读历史”未作为统一规则沉淀。
3. “考试窗口 / 截止时间 / 迟交策略”仍主要在业务服务判断，未纳入统一授权上下文。
4. “组织管理员默认不能看源码全文 / 隐藏测试 / 评测脚本”未完全收敛：
   - 当前教师/助教模板默认含 `submission.code.read_sensitive`
   - `JudgeApplicationService#getTeacherJudgeJobReport` 直接给出敏感评测报告，不校验 `JUDGE_HIDDEN_READ`
5. `judge_admin` 专项敏感权限边界尚未形成独立闭环。

### 3.6 路由与统一授权入口差距

1. 当前已有 `AuthorizationService`，但未真正成为全仓唯一授权入口。
2. 治理后台仍有部分 `@PreAuthorize("hasAuthority(...)")` 旧式 authority 判断。
3. 大量业务授权仍通过 `CourseAuthorizationService` 中的场景化断言方法展开，后续新增角色/权限点仍需改业务代码。

### 3.7 列表过滤与查询下推差距

已落地：

- 部分查询具备 SQL 层过滤基础，例如成绩册查询、部分分页索引与最新提交查询。

未达 spec：

1. 还没有通用的 Repository/Query Builder 级授权过滤注入机制。
2. 很多列表接口仍是“先验证父资源权限，再按业务条件查询”，而不是统一权限过滤框架。
3. 导出接口也没有统一复用过滤注入层。
4. 批量授权判断能力没有标准接口。

### 3.8 字段级访问控制差距

已落地：

- 作业 paper 对学生隐藏敏感判题配置
- 评测报告支持 `revealSensitiveFields`

未达 spec：

1. 字段级控制未形成统一 DTO/序列化安全层。
2. 提交对象仍直接返回 `contentText`、附件等字段，缺少统一敏感字段裁剪策略。
3. 成绩册导出/详情中的“草稿成绩、内部备注、未发布评语”没有统一字段策略框架。

### 3.9 审计差距

1. 当前审计日志缺少 spec 要求的 `scope_type/scope_id/decision/reason/user_agent/命中角色/命中作用域` 等核心字段。
2. 高风险操作虽然大量已记录，但记录内容更偏业务事件，不是权限可审计模型。
3. “查看隐藏测试、查看源码全文、导出成绩”等敏感读操作审计不完整。

### 3.10 权限变更实时生效差距

1. `AuthzGroupApplicationService#addMember` 已主动失效用户会话。
2. 但 `UserAdministrationApplicationService#updateIdentities` 和 `replaceMemberships` 变更后没有同步失效会话。
3. 这与 spec 的“角色变更后及时生效、旧 token 不应长期保留已移除权限”不一致。

## 4. 推荐改造顺序

1. 先统一模型名与边界，不先改业务接口
   - 明确“标准角色/权限/作用域/资源归属链”词汇表
2. 再建设标准角色绑定层
   - 在现有 authz group 之上或旁路引入标准 `role_bindings`
3. 再做资源归属解析与统一授权上下文
   - Submission / Grade / Assignment / Judge / Workspace
4. 再替换业务授权入口
   - 先 teaching 主链路：assignment / submission / grading / judge / IDE
5. 再补列表过滤与字段级裁剪
6. 最后清理旧 authority、legacy resolver、硬编码角色映射

## 5. 预计需要新增/修改的核心文件或模块

### 5.1 高优先级新增

- `modules/identityaccess/domain/role` 或同等目录
  - 标准角色定义
  - 标准权限定义
  - 角色-权限模板装配
- `modules/identityaccess/application/authz/resource`
  - 统一资源归属链解析器
- `modules/identityaccess/application/authz/filter`
  - 列表过滤/SQL 条件下推能力
- 新迁移脚本
  - 角色、权限、角色绑定、审计增强

### 5.2 高优先级修改

- `PermissionCode`
- `BuiltInGroupTemplate`
- `AuthorizationService`
- `AuthorizationRequest`
- `CourseAuthorizationService`
- `AuthenticatedPrincipalLoader`
- `JwtTokenService`
- `UserAdministrationApplicationService`
- `AuthzGroupApplicationService`
- `AuditLogEntity` 与审计写入服务

### 5.3 需要逐步接入统一授权的业务模块

- `assignment`
- `submission`
- `grading`
- `judge`
- `lab`
- `course` 下 announcement/resource/discussion/member
- IDE / workspace 相关服务

## 6. 主要迁移风险

1. 现有 `course_members` 同时承担业务成员与授权来源，拆分为 `offering_members/class_members + role_bindings` 时容易造成权限回归。
2. 角色命名和权限命名一旦替换，会波及 JWT claim、`@PreAuthorize`、测试用例、OpenAPI 覆盖测试。
3. `offering` 正式入链会影响组织树校验、scope 解析、数据初始化与现有根组织策略。
4. 列表过滤若一次性改全仓，容易带来 SQL 回归和性能回退。
5. 审计字段扩展会影响历史查询接口与已有审计消费端。
6. 身份变更实时生效若采用“全部会话失效”，会影响管理后台使用体验，需要清晰灰度策略。

## 7. 分阶段实施计划

### Phase 1：模型收敛与兼容层

- 建立 spec 对齐的角色、权限、作用域字典
- 明确旧名到新名映射
- 补齐缺失权限点枚举
- 设计资源归属链统一接口
- 先修复“身份变更未失效会话”的明显安全缺口

### Phase 2：角色绑定标准化

- 引入标准 `role_bindings`（或在现有授权组上做兼容映射层）
- 保留旧 `user_scope_roles/course_members/auth_groups` 作为迁移来源
- 完成治理身份、教学身份到标准绑定模型的同步

### Phase 3：资源归属解析与统一授权上下文

- 为 Assignment / Submission / Grade / JudgeJob / Workspace 建立统一归属链解析
- 将 ABAC 所需的 `owner/status/published/archive/time-window` 全部纳入上下文

### Phase 4：教学主链路接入

- 先改 assignment / submission / grading / judge / IDE
- 用统一授权服务替换场景化断言
- 收敛敏感资源规则：隐藏测试、源码全文、评测脚本

### Phase 5：列表过滤与字段级裁剪

- 为成绩册、提交列表、导出、搜索接口补 SQL 层过滤
- 建立 DTO 裁剪层，统一处理源码、隐藏测试、草稿成绩、内部备注等敏感字段

### Phase 6：审计增强与旧逻辑下线

- 扩展审计字段为授权决策模型
- 覆盖高风险读写操作
- 下线旧 authority、legacy resolver、硬编码角色判断

## 8. 当前步骤建议

下一步应优先做两件事：

1. 产出“spec 对齐字典”
   - 角色代码映射
   - 权限点映射
   - 旧表 -> 新角色绑定来源映射
2. 做最小安全修复
   - 身份/成员关系变更后强制失效会话
   - 补齐 judge 敏感报告读取权限边界
