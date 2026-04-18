# 权限系统实现说明

## 1. 文档范围

本文档说明当前仓库内已经落地的权限系统实现，用于收尾验收、联调和后续维护。唯一需求权威来源仍是 `docs/product-specs/permission-system.md`；本文档描述的是“当前代码如何实现 spec”，不是替代 spec。

## 2. 总体实现概览

当前实现采用“统一权限核心 + 业务兼容适配”的模式：

- 统一权限核心位于 `src/main/java/com/aubb/server/modules/identityaccess/application/authz/core/`
- 登录态快照优先从 `role_bindings` 生成 `groupBindings` 和 `permissionCodes`
- 历史治理身份、课程成员和旧 auth group 仍保留兼容读取与回填
- 读路径优先下推到查询层做过滤，详情/高风险写操作走对象级授权
- 敏感读写和拒绝访问写入 `audit_logs`

## 3. 表结构说明

权限系统基础表由以下 migration 建立：

- `src/main/resources/db/migration/V37__permission_system_data_layer_foundation.sql`
- `src/main/resources/db/migration/V38__class_ta_grade_export_compat.sql`
- `src/main/resources/db/migration/V39__member_status_transferred_compat.sql`
- `src/main/resources/db/migration/V40__legacy_role_bindings_backfill.sql`

核心表如下：

- `roles`
  - 定义角色编码、作用域类型、是否内建、状态
- `permissions`
  - 定义权限点编码、资源类型、动作、是否敏感
- `role_permissions`
  - 角色与权限点映射
- `role_bindings`
  - 用户在某个作用域上的角色绑定，包含约束、有效期、来源和兼容回填来源
- `audit_logs`
  - 兼容旧审计表，同时增加 `user_id`、`resource_type`、`resource_id`、`scope_type`、`scope_id`、`decision`、`reason`、`user_agent`
- `offering_members`
  - 开课级成员关系，保留与 `course_members` 的兼容引用
- `class_members`
  - 班级级成员关系，保留与 `course_members` 的兼容引用

关键索引：

- `role_bindings`
  - `ix_role_bindings_user_status`
  - `ix_role_bindings_scope_status`
  - `ix_role_bindings_role_status`
  - `ux_role_bindings_source_scope`
- `role_permissions`
  - `ux_role_permissions_role_permission`
  - `ix_role_permissions_permission_id`
- `audit_logs`
  - `ix_audit_logs_user_id_created_at`
  - `ix_audit_logs_scope_created_at`
  - `ix_audit_logs_resource_created_at`
  - `ix_audit_logs_decision_created_at`
- 资源归属链相关
  - `ix_class_members_offering_id`
  - `ix_offering_members_offering_status`
  - `ix_class_members_class_status`

## 4. 角色与权限点清单

当前内建角色以 `V37__permission_system_data_layer_foundation.sql` 为准，核心包括：

- `school_admin`
- `college_admin`
- `course_manager`
- `offering_coordinator`
- `offering_teacher`
- `class_teacher`
- `offering_ta`
- `class_ta`
- `student`
- `judge_admin`
- `auditor`
- `grader`
- 兼容角色：`class_admin`、`observer`

当前权限点已覆盖组织、成员、角色绑定、任务、提交、成绩、IDE、评测、报表、申诉、审计等域。高风险权限点包括：

- `role_binding.manage`
- `submission.export`
- `submission.read_source`
- `grade.export`
- `grade.publish`
- `grade.override`
- `judge.config`
- `judge.view_hidden`
- `judge.manage_environment`
- `report.export`
- `audit.export`

说明：

- 新权限核心使用的是 spec 风格权限编码，例如 `task.read`、`submission.read_source`、`judge.view_hidden`
- 历史兼容链路里仍存在一部分旧权限编码和旧模板编码，因此登录态和治理服务包含双向映射/兜底逻辑

## 5. 作用域继承规则

当前作用域模型为：

- `platform`
- `school`
- `college`
- `course`
- `offering`
- `class`

继承规则：

- 权限只向下继承，不向上提升
- `school` 可覆盖下属学院/课程/开课/班级
- `college` 可覆盖下属课程/开课/班级
- `course` 可覆盖下属开课/班级
- `offering` 可覆盖本开课全部班级
- `class` 仅覆盖本班级

实现位置：

- `AuthorizationScopePath`
- `ResourceOwnershipResolutionService`
- `PermissionAuthorizationService`

## 6. 授权流程说明

### 6.1 登录态构建

入口：

- `AuthenticatedPrincipalLoader`
- `AuthenticatedUserPrincipal`

流程：

1. 加载用户基本信息与身份信息
2. 优先从 `role_bindings` 读取活动绑定
3. 生成登录态中的 `groupBindings` 和 `permissionCodes`
4. 额外标记本次会话是否已由 `role_bindings` 构建快照，JWT 中同步写入 `roleBindingSnapshot`
5. 若没有新绑定，则 fallback 到旧治理身份、课程成员和旧 auth group
6. authority 优先来自 `groupBindings`，旧 `identities` 仅作兜底

### 6.2 读路径授权

入口：

- `ReadPathAuthorizationService`
- `PermissionAuthorizationService`

流程：

1. 根据资源解析归属链
2. 根据权限码加载可用绑定
3. 按作用域命中 + ABAC 规则判定
4. 列表/搜索路径优先下推为 scope filter，避免“先查全量再内存过滤”
5. `teacher` 读路径额外剔除 `student` grant，防止把“学生本人/本班可读”误放大成教师接口可读，避免提交详情与列表的 IDOR 风险

### 6.3 写路径授权

入口：

- `CourseAuthorizationService`
- 业务应用服务中的显式断言

流程：

1. 将业务资源映射为 `AuthorizationResourceRef`
2. 调用统一权限核心判定
3. 若命中敏感能力或拒绝决策，则写审计
4. `CourseAuthorizationService` 的旧权限矩阵兜底仅在当前会话没有 `role_bindings` 快照时启用，避免新旧授权链混用
5. `CourseAuthorizationService.hasPermission(...)` 已对成员、任务、题库、提交、成绩、申诉等高风险旧权限码做桥接，优先走新权限核心；当前新模型尚未落表的能力（如 `lab.read` / `lab.report.review`）继续保留旧逻辑兼容

## 7. 已实现的关键 ABAC 与字段级规则

已实现的固定 ABAC 规则包括：

- 是否本人
- 是否已发布
- 是否归档
- 是否在时间窗口内
- 是否属于授权班级/开课
- 是否为敏感资源访问

字段级控制当前已落地在以下链路：

- `submission`
  - 无 `submission.read_source` 时，源码正文、源码文件、入口文件、附件 ID/下载路径会被裁剪
- `grade`
  - 未发布成绩对学生隐藏未发布人工分和评语
- `task / judge config`
  - 普通读取链路对隐藏评测配置、隐藏测试和敏感 judge 字段做裁剪

## 8. 迁移与兼容说明

### 8.1 迁移来源

`V40__legacy_role_bindings_backfill.sql` 已将以下旧模型回填为 `role_bindings`：

- `user_scope_roles` -> 学校/学院/课程/班级治理角色
- `course_members` -> 开课教师、班级教师、开课助教、班级助教、学生
- `auth_group_members` -> 旧授权组成员

### 8.2 兼容策略

- 旧表暂不删除，继续作为兼容来源
- 登录态优先使用 `role_bindings`，旧逻辑仅在新绑定缺失时兜底
- 已有 `role_bindings` 快照的会话，不再允许写路径从 `AuthorizationService` 重新回落到旧治理/成员解析器
- 若短期内无法彻底移除旧授权入口，代码中已通过 `@Deprecated` 标出旧服务

## 9. 审计说明

审计相关核心实现：

- `AuditLogApplicationService`
- `SensitiveOperationAuditService`
- `AuthzAuditService`

当前审计至少覆盖：

- 操作者
- 动作
- 资源类型与资源 ID
- 作用域类型与作用域 ID
- 决策结果
- 必要上下文 metadata

高风险操作审计重点包括：

- `submission.read_source`
- `judge.config`
- `judge.view_hidden`
- `grade.override`
- `grade.export`
- `report.export`
- 授权拒绝事件

## 10. 测试说明

权限系统测试分为三层：

- 单元测试
  - 作用域匹配、ABAC 规则、治理作用域、登录快照、字段级脱敏、敏感审计
- 集成测试
  - 开课/班级隔离、学院隔离、学生仅看自己、助教边界、成绩发布状态、归档只读、退课/转班限制、敏感读取审计
- 回归测试
  - 课程、作业、提交、成绩册、评测、JWT 会话、旧权限兼容

本轮补充的测试重点：

- `AuthenticatedPrincipalLoaderTests`
- `SubmissionAnswerApplicationServiceTests`
- `SensitiveOperationAuditServiceTests`
- `CourseSystemIntegrationTests`
- `SubmissionIntegrationTests`

## 11. 当前已知限制与风险

- 当前仓库仍受 `V24__single_school_root_guard.sql` 影响，运行态是单学校模式，因此“跨学校资源隔离”无法像 spec 多学校模型那样完整做真实集成验证
- 旧 `auth_group_*` 与新 `role_*` 结构并存，属于迁移期技术债
- 部分“归档”仍通过状态与 `archived_at` 直接驱动，没有完整的独立归档工作流接口
- 历史权限编码与新权限编码并存，新增功能时必须优先接入新权限核心，避免继续扩散旧编码

## 12. 后续维护建议

- 新增业务接口时，优先接入 `PermissionAuthorizationService` / `ReadPathAuthorizationService`
- 新增敏感读取能力时，默认同时补 `SensitiveOperationAuditService` 审计
- 逐步收缩 `user_scope_roles`、`course_members`、`auth_group_*` 的授权职责，仅保留业务数据职责或迁移职责
- 等单学校约束解除后，再补齐跨学校隔离的真实集成测试
