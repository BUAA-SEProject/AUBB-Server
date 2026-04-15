# 数据库结构

基线迁移：

- `src/main/resources/db/migration/V1__phase2_platform_governance_and_iam.sql`
- `src/main/resources/db/migration/V2__jwt_scope_governance_refactor.sql`

## 总览

当前治理模型的核心表为：

- `platform_configs`：单份即时生效的平台配置
- `org_units`：学校 / 学院 / 课程 / 班级组织树
- `users`：平台用户账号
- `user_scope_roles`：用户作用域身份分配
- `audit_logs`：关键治理与认证审计日志

## 表结构

### `platform_configs`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `platform_name` | `text` | 必填，最长 128 |
| `platform_short_name` | `text` | 必填，最长 64 |
| `logo_url` | `text` | Logo 地址 |
| `footer_text` | `text` | 页脚文案 |
| `default_home_path` | `text` | 必填，最长 128 |
| `theme_key` | `text` | 必填，最长 64 |
| `login_notice` | `text` | 登录页提示 |
| `module_flags` | `jsonb` | 必填，默认 `{}`，仅允许对象 |
| `updated_by_user_id` | `bigint` | 最近更新人，外键到 `users.id` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- 当前实现以应用层保证单份配置语义，初始化后应只保留一行数据

### `org_units`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `parent_id` | `bigint` | 父节点，外键到 `org_units.id` |
| `code` | `text` | 必填，唯一，最长 64 |
| `name` | `text` | 必填，最长 128 |
| `type` | `text` | 必填，`SCHOOL / COLLEGE / COURSE / CLASS` |
| `level` | `integer` | 必填，`1 ~ 4` |
| `sort_order` | `integer` | 必填，默认 `0` |
| `status` | `text` | 必填，默认 `ACTIVE`，`ACTIVE / DISABLED` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_org_units_code`
- `ix_org_units_parent_id_sort_order`

### `users`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `primary_org_unit_id` | `bigint` | 主组织，外键到 `org_units.id` |
| `username` | `text` | 必填，最长 64 |
| `display_name` | `text` | 必填，最长 128 |
| `email` | `text` | 必填，最长 128 |
| `phone` | `text` | 手机号，可空 |
| `password_hash` | `text` | 必填，BCrypt 哈希 |
| `account_status` | `text` | 必填，`ACTIVE / DISABLED / LOCKED / EXPIRED` |
| `failed_login_attempts` | `integer` | 必填，默认 `0`，`>= 0` |
| `locked_until` | `timestamptz` | 锁定截止时间 |
| `expires_at` | `timestamptz` | 账号失效时间 |
| `last_login_at` | `timestamptz` | 最近登录时间 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_users_username_lower`
- `ux_users_email_lower`
- `ix_users_primary_org_unit_id`
- `ix_users_account_status`

### `user_scope_roles`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `scope_org_unit_id` | `bigint` | 必填，外键到 `org_units.id`，级联删除 |
| `role_code` | `text` | 必填，`SCHOOL_ADMIN / COLLEGE_ADMIN / COURSE_ADMIN / CLASS_ADMIN` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_user_scope_roles_user_scope_role`
- `ix_user_scope_roles_user_id`
- `ix_user_scope_roles_scope_org_unit_id`

### `audit_logs`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `actor_user_id` | `bigint` | 操作者，外键到 `users.id` |
| `action` | `text` | 必填，最长 64 |
| `target_type` | `text` | 必填，最长 64 |
| `target_id` | `text` | 目标对象标识 |
| `result` | `text` | 必填，`SUCCESS / FAILURE` |
| `request_id` | `text` | 必填，最长 128 |
| `ip` | `text` | 必填，最长 64 |
| `metadata` | `jsonb` | 必填，默认 `{}`，仅允许对象 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ix_audit_logs_actor_user_id_created_at`
- `ix_audit_logs_action_created_at`
- `ix_audit_logs_target_type_created_at`

## 关系

- `platform_configs.updated_by_user_id -> users.id`
- `org_units.parent_id -> org_units.id`
- `users.primary_org_unit_id -> org_units.id`
- `user_scope_roles.user_id -> users.id`
- `user_scope_roles.scope_org_unit_id -> org_units.id`
- `audit_logs.actor_user_id -> users.id`

## 设计说明

- `user_scope_roles` 用于表达“一个用户在多个组织节点上承担不同治理身份”的场景。
- `org_units` 仍使用邻接表，当前实现通过父链回溯完成作用域判定；若后续规模扩大，可引入路径列或 `ltree` 优化。
- 平台配置移除了版本化能力，若未来需要配置历史，可通过审计快照补充。
