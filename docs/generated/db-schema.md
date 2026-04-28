# 数据库结构

基线迁移：

- `src/main/resources/db/migration/V1__phase2_platform_governance_and_iam.sql`
- `src/main/resources/db/migration/V2__user_profile_and_membership_extension.sql`
- `src/main/resources/db/migration/V3__course_system_first_slice.sql`
- `src/main/resources/db/migration/V4__assignment_first_slice.sql`
- `src/main/resources/db/migration/V5__submission_first_slice.sql`
- `src/main/resources/db/migration/V6__submission_artifact_slice.sql`
- `src/main/resources/db/migration/V7__judge_first_slice.sql`
- `src/main/resources/db/migration/V8__judge_go_judge_execution.sql`
- `src/main/resources/db/migration/V9__structured_assignment_foundation.sql`
- `src/main/resources/db/migration/V10__grading_first_slice.sql`
- `src/main/resources/db/migration/V11__structured_programming_judge_phase1.sql`
- `src/main/resources/db/migration/V12__programming_workspace_and_sample_runs.sql`
- `src/main/resources/db/migration/V13__programming_workspace_file_tree_phase1.sql`
- `src/main/resources/db/migration/V14__question_bank_lifecycle_phase2.sql`
- `src/main/resources/db/migration/V15__question_bank_tags_phase1.sql`
- `src/main/resources/db/migration/V16__judge_queue_and_reports_phase1.sql`
- `src/main/resources/db/migration/V17__online_ide_phase2.sql`
- `src/main/resources/db/migration/V18__question_bank_categories_phase2.sql`
- `src/main/resources/db/migration/V19__judge_environment_profiles_phase1.sql`
- `src/main/resources/db/migration/V20__assignment_grade_weights_phase1.sql`
- `src/main/resources/db/migration/V21__grade_appeals_phase1.sql`
- `src/main/resources/db/migration/V22__programming_judge_failed_status.sql`
- `src/main/resources/db/migration/V23__auth_sessions_refresh_tokens.sql`
- `src/main/resources/db/migration/V24__single_school_root_guard.sql`
- `src/main/resources/db/migration/V25__judge_artifact_object_storage_phase1.sql`
- `src/main/resources/db/migration/V26__lab_report_mvp.sql`
- `src/main/resources/db/migration/V27__notification_center_mvp.sql`
- `src/main/resources/db/migration/V28__db_paginated_permission_filter_indexes.sql`
- `src/main/resources/db/migration/V29__judge_artifact_tracking_phase2.sql`
- `src/main/resources/db/migration/V30__grade_publish_snapshots_v1.sql`
- `src/main/resources/db/migration/V31__course_announcements_mvp.sql`
- `src/main/resources/db/migration/V32__course_resources_mvp.sql`
- `src/main/resources/db/migration/V33__course_discussions_mvp.sql`
- `src/main/resources/db/migration/V34__authz_group_rbac_abac_foundation.sql`
- `src/main/resources/db/migration/V35__course_member_extended_roles.sql`
- `src/main/resources/db/migration/V36__submission_and_member_perf_indexes.sql`
- `src/main/resources/db/migration/V37__permission_system_data_layer_foundation.sql`
- `src/main/resources/db/migration/V38__class_ta_grade_export_compat.sql`
- `src/main/resources/db/migration/V39__member_status_transferred_compat.sql`
- `src/main/resources/db/migration/V40__legacy_role_bindings_backfill.sql`
- `src/main/resources/db/migration/V41__course_member_transfer_history_support.sql`
- `src/main/resources/db/migration/V42__discussion_and_lab_permission_compat.sql`
- `src/main/resources/db/migration/V43__authz_builtin_role_permission_compat.sql`
- `src/main/resources/db/migration/V44__offering_teacher_member_manage_compat.sql`
- `src/main/resources/db/migration/V45__question_bank_manage_role_compat.sql`
- `src/main/resources/db/migration/V46__legacy_binding_activation_tolerance.sql`
- `src/main/resources/db/migration/V47__expand_legacy_binding_activation_tolerance_window.sql`

## 总览

当前治理模型的核心表为：

- `platform_configs`：单份即时生效的平台配置
- `org_units`：学校 / 学院 / 课程 / 班级组织树
- `users`：平台用户账号
- `auth_sessions`：refresh token 与会话撤销状态
- `notifications`：站内通知内容主表
- `notification_receipts`：按用户展开的通知收件箱与已读状态
- `academic_profiles`：用户教务画像
- `user_org_memberships`：用户组织成员关系
- `user_scope_roles`：用户作用域身份分配
- `roles`：权限系统角色模板定义
- `permissions`：权限点定义
- `role_permissions`：角色与权限点映射
- `role_bindings`：用户在作用域上的角色绑定
- `academic_terms`：学期主数据
- `course_catalogs`：课程模板
- `course_offerings`：开课实例
- `course_offering_college_maps`：课程跨学院共同管理映射
- `teaching_classes`：教学班
- `course_members`：课程成员
- `offering_members`：开课级业务成员
- `class_members`：班级级业务成员
- `assignments`：作业主数据
- `labs`：教学班级实验主数据
- `lab_reports`：学生当前实验报告
- `lab_report_attachments`：实验报告附件元数据
- `question_bank_questions`：开课实例内题库题目
- `question_bank_question_options`：题库客观题选项
- `question_bank_categories`：开课实例内题库分类字典
- `question_bank_tags`：开课实例内题库标签字典
- `question_bank_question_tags`：题库题目与标签的关联关系
- `assignment_sections`：结构化试卷大题快照
- `assignment_questions`：结构化试卷题目快照
- `assignment_question_options`：试卷题目选项快照
- `assignment_judge_profiles`：作业自动评测配置
- `assignment_judge_cases`：作业自动评测测试用例
- `judge_environment_profiles`：开课实例级编程题评测环境模板
- `submissions`：正式提交记录
- `submission_artifacts`：提交附件元数据
- `submission_answers`：分题答案、人工批改与题目级评测回写状态
- `grade_appeals`：成绩申诉与复核记录
- `grade_publish_snapshot_batches`：assignment 成绩发布批次头
- `grade_publish_snapshots`：按学生展开的成绩发布快照
- `programming_workspaces`：编程题工作区草稿
- `programming_workspace_revisions`：工作区历史修订与恢复点
- `judge_jobs`：submission 级与 answer 级评测作业元数据
- `programming_sample_runs`：样例试运行日志
- `audit_logs`：关键治理与认证审计日志

## 权限系统与兼容迁移增量（V37-V47）

V37 在保留 `user_scope_roles`、`course_members`、`auth_group_*` 兼容链路的同时，引入新的统一权限基础结构：

- `roles`：角色模板主表，`scope_type` 支持 `platform / school / college / course / offering / class`
- `permissions`：规范化权限点字典，按 `resource.action` 组织
- `role_permissions`：角色默认权限模板映射
- `role_bindings`：用户角色绑定，支持 `constraints_json`、生效时间和来源追踪
- `offering_members` / `class_members`：面向业务查询的成员表，与 `role_bindings` 分层协作
- `audit_logs`：补充 `user_id`、`resource_type`、`resource_id`、`scope_type`、`scope_id`、`decision` 等兼容列与索引

V38-V46 在不新增核心业务表的前提下，继续把权限与成员链路收口到生产口径：

- V38：为 `class_ta` 增补 `grade.export` 兼容权限映射
- V39：扩展 `course_members / offering_members / class_members` 的 `member_status` 约束，纳入 `TRANSFERRED / INACTIVE`
- V40：对历史库执行幂等 `role_bindings` backfill，补齐由旧治理身份、课程成员和授权组推导出的绑定
- V41：把学生唯一索引收口为 `ux_course_members_student_active_unique`，允许保留转班 / 退课后的历史成员记录
- V42：为 discussion / lab 读写路径补齐最小权限兼容映射
- V43：收口内建角色默认权限模板，补齐 RBAC 基线兼容映射
- V44：为 `offering_teacher` 显式补齐 `member.manage` 兼容权限
- V45：为题库管理路径补齐 `question_bank.manage` 兼容权限
- V46：引入 `legacy_binding_effective_from(...)` 与三类 legacy -> `role_bindings` 同步函数，缓解新绑定刚创建后的瞬时未生效窗口
- V47：将 legacy binding 激活容忍窗口从 `1 second` 扩展到 `3 seconds`，并与应用层/授权查询容忍逻辑保持一致，避免教师建班后紧邻请求误判 `DENY_NO_ROLE_BINDING`
- V42：补齐 `discussion.*`、`lab.*` 权限点及角色映射
- V43：补齐 `auth.group.manage`、`auth.explain.read` 以及 judge / grade import 兼容角色映射
- V44：为 `offering_teacher` 增补 `member.manage / member.import`
- V45：为 `offering_coordinator / offering_teacher` 增补 `question_bank.manage`
- V46：重写 legacy -> `role_bindings` 同步触发器，引入 `legacy_binding_effective_from(...)` 1 秒容忍窗口，避免创建成员 / 身份后立即登录时因为时间精度抖动错过新权限快照

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
- `ck_org_units_root_is_school`
- `ux_org_units_single_school_root`

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
- `idx_users_primary_org_unit_created_at_id`

### `auth_sessions`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `session_id` | `text` | 必填，唯一，最长 64 |
| `user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `refresh_token_hash` | `text` | 必填，refresh token 的 SHA-256 哈希 |
| `refresh_token_expires_at` | `timestamptz` | 必填，refresh token 过期时间 |
| `revoked_at` | `timestamptz` | 会话撤销时间 |
| `revoked_reason` | `text` | 撤销原因，最长 256 |
| `revoked_by_user_id` | `bigint` | 操作人，外键到 `users.id`，删除时置空 |
| `last_access_issued_at` | `timestamptz` | 最近签发 access token 的时间 |
| `last_refreshed_at` | `timestamptz` | 最近 refresh 成功时间 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_auth_sessions_session_id`
- `ix_auth_sessions_user_id_revoked_at`
- `ix_auth_sessions_refresh_token_expires_at`

### `notifications`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `type` | `varchar(64)` | 必填，通知类型 |
| `title` | `varchar(200)` | 必填，通知标题 |
| `body` | `text` | 必填，通知正文 |
| `actor_user_id` | `bigint` | 可空，通知发起人，外键到 `users.id`，删除时置空 |
| `target_type` | `varchar(64)` | 可空，目标资源类型 |
| `target_id` | `varchar(64)` | 可空，目标资源编号 |
| `offering_id` | `bigint` | 可空，外键到 `course_offerings.id`，删除时置空 |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id`，删除时置空 |
| `metadata` | `jsonb` | 必填，默认 `{}`，保存通知上下文摘要 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `idx_notifications_created_at`
- `idx_notifications_target`

### `notification_receipts`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `notification_id` | `bigint` | 必填，外键到 `notifications.id`，级联删除 |
| `recipient_user_id` | `bigint` | 必填，收件人，外键到 `users.id`，级联删除 |
| `read_at` | `timestamptz` | 可空，已读时间 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_notification_receipts_notification_recipient`
- `idx_notification_receipts_recipient_created`
- `idx_notification_receipts_recipient_unread`

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
- `idx_user_scope_roles_role_code_user_id`

### `academic_terms`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `term_code` | `text` | 必填，最长 32，大小写不敏感唯一 |
| `term_name` | `text` | 必填，最长 64 |
| `school_year` | `text` | 必填，最长 16 |
| `semester` | `text` | 必填，`SPRING / SUMMER / AUTUMN / WINTER` |
| `start_date` | `date` | 必填 |
| `end_date` | `date` | 必填，且不早于开始日期 |
| `status` | `text` | 必填，默认 `PLANNING` |

### `course_catalogs`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `course_code` | `text` | 必填，最长 64，大小写不敏感唯一 |
| `course_name` | `text` | 必填，最长 128 |
| `course_type` | `text` | 必填，`REQUIRED / ELECTIVE / GENERAL / PRACTICE` |
| `credit` | `numeric(4,1)` | 必填，`>= 0` |
| `total_hours` | `integer` | 必填，`>= 0` |
| `department_unit_id` | `bigint` | 必填，外键到 `org_units.id` |
| `description` | `text` | 课程描述 |
| `status` | `text` | 必填，默认 `ACTIVE` |

### `course_offerings`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `catalog_id` | `bigint` | 必填，外键到 `course_catalogs.id` |
| `term_id` | `bigint` | 必填，外键到 `academic_terms.id` |
| `offering_code` | `text` | 必填，最长 64，大小写不敏感唯一 |
| `offering_name` | `text` | 必填，最长 128 |
| `primary_college_unit_id` | `bigint` | 必填，主学院 |
| `org_course_unit_id` | `bigint` | 必填，对应组织树 COURSE 节点 |
| `delivery_mode` | `text` | 必填，`ONLINE / OFFLINE / HYBRID` |
| `language` | `text` | 必填，`ZH / EN / BILINGUAL` |
| `capacity` | `integer` | 必填，`> 0` |
| `selected_count` | `integer` | 必填，默认 `0` |
| `status` | `text` | 必填，默认 `DRAFT` |
| `start_at` / `end_at` | `timestamptz` | 开课起止时间 |

### `course_offering_college_maps`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id` |
| `college_unit_id` | `bigint` | 必填，外键到 `org_units.id` |
| `relation_type` | `text` | 必填，`PRIMARY / SECONDARY / CROSS_LISTED` |

### `teaching_classes`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id` |
| `class_code` | `text` | 必填，课程内大小写不敏感唯一 |
| `class_name` | `text` | 必填，最长 128 |
| `entry_year` | `integer` | 必填，用于表达 2024 级、2025 级等教学班 |
| `org_class_unit_id` | `bigint` | 必填，对应组织树 CLASS 节点 |
| `capacity` | `integer` | 必填，`> 0` |
| `announcement_enabled` | `boolean` | 班级公告开关 |
| `discussion_enabled` | `boolean` | 班级讨论区开关 |
| `resource_enabled` | `boolean` | 资源功能开关 |
| `lab_enabled` | `boolean` | 实验功能开关 |
| `assignment_enabled` | `boolean` | 作业功能开关 |

### `course_members`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id` |
| `teaching_class_id` | `bigint` | 可空，绑定教学班 |
| `user_id` | `bigint` | 必填，外键到 `users.id` |
| `member_role` | `text` | 必填，`INSTRUCTOR / CLASS_INSTRUCTOR / OFFERING_TA / TA / STUDENT / OBSERVER` |
| `member_status` | `text` | 必填，默认 `ACTIVE`，`PENDING / ACTIVE / DROPPED / TRANSFERRED / COMPLETED / REMOVED` |
| `source_type` | `text` | 必填，`MANUAL / IMPORT / SYNC` |
| `remark` | `text` | 备注 |
| `joined_at` / `left_at` | `timestamptz` | 加入与离开时间 |

索引与约束：

- `ix_course_members_offering_id`
- `ix_course_members_teaching_class_id`
- `ix_course_members_user_id`
- `ix_course_members_member_role_status`
- `idx_course_members_user_offering_status_class`
- `idx_course_members_offering_class_role_status_user`
- `ux_course_members_instructor_unique`
- `ux_course_members_class_instructor_unique`
- `ux_course_members_offering_ta_unique`
- `ux_course_members_student_active_unique`
- `ux_course_members_ta_unique`

### `assignments`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id`，级联删除 |
| `title` | `text` | 必填，最长 128 |
| `description` | `text` | 作业说明 |
| `status` | `text` | 必填，默认 `DRAFT`，`DRAFT / PUBLISHED / CLOSED` |
| `open_at` | `timestamptz` | 必填，开放时间 |
| `due_at` | `timestamptz` | 必填，截止时间，且不早于 `open_at` |
| `max_submissions` | `integer` | 必填，`> 0` |
| `grade_weight` | `integer` | 必填，默认 `100`，`> 0` |
| `published_at` | `timestamptz` | 发布时间 |
| `closed_at` | `timestamptz` | 关闭时间，若存在则不早于 `published_at` |
| `grade_published_at` | `timestamptz` | assignment 级成绩发布时间 |
| `grade_published_by_user_id` | `bigint` | 成绩发布人，外键到 `users.id`，删除置空 |
| `created_by_user_id` | `bigint` | 创建人，外键到 `users.id`，删除置空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ix_assignments_offering_id_status`
- `ix_assignments_teaching_class_id_status`
- `ix_assignments_open_at_due_at`
- `idx_assignments_grade_published_at`
- `idx_assignments_visible_open_at_id`
- `ck_assignments_grade_weight_positive`

### `labs`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id` |
| `teaching_class_id` | `bigint` | 必填，外键到 `teaching_classes.id` |
| `title` | `text` | 必填，最长 `200` |
| `description` | `text` | 实验说明 |
| `status` | `text` | 必填，`DRAFT / PUBLISHED / CLOSED` |
| `published_at` | `timestamptz` | 发布时间 |
| `closed_at` | `timestamptz` | 关闭时间 |
| `created_by_user_id` | `bigint` | 必填，外键到 `users.id` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ix_labs_offering_id_status_created_at`
- `ix_labs_teaching_class_id_status_created_at`

### `lab_reports`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `lab_id` | `bigint` | 必填，外键到 `labs.id` |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id` |
| `teaching_class_id` | `bigint` | 必填，外键到 `teaching_classes.id` |
| `student_user_id` | `bigint` | 必填，外键到 `users.id` |
| `status` | `text` | 必填，`DRAFT / SUBMITTED / REVIEWED / PUBLISHED` |
| `report_content_text` | `text` | 正文，可空，最长 `20000` |
| `teacher_annotation_text` | `text` | 教师批注，可空，最长 `5000` |
| `teacher_comment_text` | `text` | 教师评语，可空，最长 `5000` |
| `submitted_at` | `timestamptz` | 学生正式提交时间 |
| `reviewed_at` | `timestamptz` | 教师评阅时间 |
| `published_at` | `timestamptz` | 教师评语发布时间 |
| `reviewer_user_id` | `bigint` | 评阅人，外键到 `users.id` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_lab_reports_lab_student`
- `ix_lab_reports_lab_id_status_updated_at`
- `ix_lab_reports_student_user_id_updated_at`

### `lab_report_attachments`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `lab_id` | `bigint` | 必填，外键到 `labs.id` |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id` |
| `teaching_class_id` | `bigint` | 必填，外键到 `teaching_classes.id` |
| `lab_report_id` | `bigint` | 外键到 `lab_reports.id`，可空 |
| `uploader_user_id` | `bigint` | 必填，外键到 `users.id` |
| `object_key` | `text` | 必填，唯一，最长 `255` |
| `original_filename` | `text` | 必填，最长 `255` |
| `content_type` | `text` | 必填，最长 `128` |
| `size_bytes` | `bigint` | 必填，`> 0` 且 `<= 20971520` |
| `uploaded_at` | `timestamptz` | 必填，默认 `now()` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ix_lab_report_attachments_lab_id_uploader_uploaded_at`
- `ix_lab_report_attachments_lab_report_id`

### `question_bank_questions`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `category_id` | `bigint` | 可空，外键到 `question_bank_categories.id`，删除置空 |
| `created_by_user_id` | `bigint` | 创建人，外键到 `users.id`，删除置空 |
| `archived_by_user_id` | `bigint` | 归档人，外键到 `users.id`，删除置空 |
| `title` | `text` | 必填，最长 128 |
| `prompt_text` | `text` | 必填，题面正文 |
| `question_type` | `text` | 必填，`SINGLE_CHOICE / MULTIPLE_CHOICE / SHORT_ANSWER / FILE_UPLOAD / PROGRAMMING` |
| `default_score` | `integer` | 必填，`> 0` |
| `config_json` | `text` | 必填，默认 `{}` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |
| `archived_at` | `timestamptz` | 软归档时间；为空表示仍可被引用 |

索引与约束：

- `ix_question_bank_questions_offering_type`
- `ix_question_bank_questions_offering_type_active`
- `ix_question_bank_questions_offering_archived_at`
- `ix_question_bank_questions_offering_category_active`

### `question_bank_categories`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `category_name` | `text` | 必填，最长 64 |
| `normalized_name` | `text` | 必填，最长 64，按开课实例内大小写不敏感去重 |
| `created_by_user_id` | `bigint` | 创建人，外键到 `users.id`，删除置空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_question_bank_categories_offering_normalized_name`
- `ix_question_bank_categories_offering_name`

### `question_bank_question_options`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `question_id` | `bigint` | 必填，外键到 `question_bank_questions.id`，级联删除 |
| `option_order` | `integer` | 必填，`> 0` |
| `option_key` | `text` | 必填，最长 16 |
| `content` | `text` | 必填，选项内容 |
| `is_correct` | `boolean` | 必填，是否正确 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_question_bank_question_options_order`
- `uk_question_bank_question_options_key`
- `ix_question_bank_question_options_question_id`

### `question_bank_tags`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `tag_name` | `text` | 必填，最长 32，当前保存归一化后的标签名 |
| `created_by_user_id` | `bigint` | 创建人，外键到 `users.id`，删除置空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_question_bank_tags_offering_name`
- `ix_question_bank_tags_offering_name`

### `question_bank_question_tags`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `question_id` | `bigint` | 必填，外键到 `question_bank_questions.id`，级联删除 |
| `tag_id` | `bigint` | 必填，外键到 `question_bank_tags.id`，级联删除 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_question_bank_question_tags`
- `ix_question_bank_question_tags_tag_id`
- `ix_question_bank_question_tags_question_id`

### `assignment_sections`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `section_order` | `integer` | 必填，`> 0` |
| `title` | `text` | 必填，最长 128 |
| `description` | `text` | 大题说明 |
| `total_score` | `integer` | 必填，`> 0` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_assignment_sections_order`
- `ix_assignment_sections_assignment_id`

### `assignment_questions`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `assignment_section_id` | `bigint` | 必填，外键到 `assignment_sections.id`，级联删除 |
| `source_question_id` | `bigint` | 可空，外键到 `question_bank_questions.id`，删除置空 |
| `question_order` | `integer` | 必填，`> 0` |
| `title` | `text` | 必填，最长 128 |
| `prompt_text` | `text` | 必填，题面正文 |
| `question_type` | `text` | 必填，`SINGLE_CHOICE / MULTIPLE_CHOICE / SHORT_ANSWER / FILE_UPLOAD / PROGRAMMING` |
| `score` | `integer` | 必填，`> 0` |
| `config_json` | `text` | 必填，默认 `{}` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_assignment_questions_order`
- `ix_assignment_questions_assignment_id`
- `ix_assignment_questions_source_question_id`

### `assignment_question_options`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_question_id` | `bigint` | 必填，外键到 `assignment_questions.id`，级联删除 |
| `option_order` | `integer` | 必填，`> 0` |
| `option_key` | `text` | 必填，最长 16 |
| `content` | `text` | 必填，选项内容 |
| `is_correct` | `boolean` | 必填，是否正确 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_assignment_question_options_order`
- `uk_assignment_question_options_key`
- `ix_assignment_question_options_question_id`

### `assignment_judge_profiles`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `assignment_id` | `bigint` | 主键，同时外键到 `assignments.id`，级联删除 |
| `source_type` | `text` | 必填，当前固定 `TEXT_BODY` |
| `language` | `text` | 必填，当前固定 `PYTHON3` |
| `entry_file_name` | `text` | 必填，最长 64，当前固定 `main.py` |
| `time_limit_ms` | `integer` | 必填，`> 0` |
| `memory_limit_mb` | `integer` | 必填，`> 0` |
| `output_limit_kb` | `integer` | 必填，`> 0` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

### `assignment_judge_cases`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_id` | `bigint` | 必填，外键到 `assignment_judge_profiles.assignment_id`，级联删除 |
| `case_order` | `integer` | 必填，`> 0` |
| `stdin_text` | `text` | 必填，标准输入 |
| `expected_stdout` | `text` | 必填，预期标准输出 |
| `score` | `integer` | 必填，`>= 0` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_assignment_judge_cases_assignment_order`
- `ix_assignment_judge_cases_assignment_order`

### `judge_environment_profiles`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `profile_code` | `text` | 必填，最长 64 |
| `normalized_code` | `text` | 必填，最长 64，开课实例内唯一 |
| `profile_name` | `text` | 必填，最长 128 |
| `description` | `text` | 可空，最长 500 |
| `programming_language` | `text` | 必填，当前支持 `PYTHON3 / JAVA21 / JAVA17 / CPP17 / GO122` |
| `language_version` | `text` | 可空，最长 64 |
| `working_directory` | `text` | 可空，最长 200 |
| `init_script` | `text` | 可空，初始化脚本 |
| `compile_command` | `text` | 可空，模板化编译命令 |
| `run_command` | `text` | 可空，模板化运行命令 |
| `environment_variables_json` | `text` | 可空，环境变量 JSON |
| `cpu_rate_limit` | `integer` | 可空，`> 0` |
| `support_files_json` | `text` | 可空，支持文件 JSON |
| `created_by_user_id` | `bigint` | 可空，外键到 `users.id`，删除置空 |
| `archived_by_user_id` | `bigint` | 可空，外键到 `users.id`，删除置空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |
| `archived_at` | `timestamptz` | 可空，归档时间 |

索引与约束：

- `uk_judge_environment_profiles_offering_code`
- `ix_judge_environment_profiles_offering_language_active`

### `submissions`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `submission_no` | `text` | 必填，唯一，最长 64 |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id`，删除置空 |
| `submitter_user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `attempt_no` | `integer` | 必填，`> 0` |
| `status` | `text` | 必填，默认 `SUBMITTED` |
| `content_text` | `text` | 可空，最长 20000 |
| `submitted_at` | `timestamptz` | 必填，默认 `now()` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_submissions_assignment_submitter_attempt`
- `ix_submissions_assignment_id_submitted_at`
- `ix_submissions_submitter_user_id_submitted_at`
- `ix_submissions_offering_id_submitted_at`
- `idx_submissions_assignment_submitter_submitted_id`
- `idx_submissions_assignment_submitted_id`

### `submission_artifacts`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id`，删除置空 |
| `submission_id` | `bigint` | 可空，外键到 `submissions.id`，级联删除 |
| `uploader_user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `object_key` | `text` | 必填，唯一，最长 255 |
| `original_filename` | `text` | 必填，最长 255 |
| `content_type` | `text` | 必填，最长 128 |
| `size_bytes` | `bigint` | 必填，`> 0` 且当前不超过 20MB |
| `uploaded_at` | `timestamptz` | 必填，默认 `now()` |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ix_submission_artifacts_assignment_user_uploaded_at`
- `ix_submission_artifacts_submission_id`
- `ix_submission_artifacts_offering_id_uploaded_at`

### `submission_answers`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `submission_id` | `bigint` | 必填，外键到 `submissions.id`，级联删除 |
| `assignment_question_id` | `bigint` | 必填，外键到 `assignment_questions.id`，级联删除 |
| `answer_text` | `text` | 可空，文本答案或代码正文 |
| `answer_payload_json` | `text` | 必填，默认 `{}`，保存选项、附件、语言、入口文件和目录树源码快照等结构化载荷 |
| `auto_score` | `integer` | 可空，`>= 0` |
| `manual_score` | `integer` | 可空，`>= 0` |
| `final_score` | `integer` | 可空，`>= 0` |
| `grading_status` | `text` | 必填，`AUTO_GRADED / MANUALLY_GRADED / PROGRAMMING_JUDGED / PROGRAMMING_JUDGE_FAILED / PENDING_MANUAL / PENDING_PROGRAMMING_JUDGE` |
| `feedback_text` | `text` | 可空，评分反馈 |
| `graded_by_user_id` | `bigint` | 可空，最近人工批改人，外键到 `users.id`，删除置空 |
| `graded_at` | `timestamptz` | 可空，最近人工批改时间 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_submission_answers_submission_question`
- `ix_submission_answers_submission_id`
- `ix_submission_answers_assignment_question_id`
- `idx_submission_answers_graded_by_user_id`
- `ck_submission_answers_score_consistency`

### `grade_appeals`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id` |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id` |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id` |
| `submission_id` | `bigint` | 必填，外键到 `submissions.id` |
| `submission_answer_id` | `bigint` | 必填，外键到 `submission_answers.id` |
| `student_user_id` | `bigint` | 必填，申诉发起学生，外键到 `users.id` |
| `status` | `varchar(32)` | 必填，`PENDING / IN_REVIEW / ACCEPTED / REJECTED` |
| `appeal_reason` | `text` | 必填，申诉原因 |
| `response_text` | `text` | 可空，教师 / 助教复核回复 |
| `resolved_score` | `integer` | 可空，申诉处理后的分数 |
| `responded_by_user_id` | `bigint` | 可空，复核人，外键到 `users.id` |
| `responded_at` | `timestamptz` | 可空，复核时间 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `idx_grade_appeals_assignment_created`
- `idx_grade_appeals_student_created`
- `idx_grade_appeals_submission_answer`
- `uq_grade_appeals_active_answer`

### `grade_publish_snapshot_batches`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id`，删除置空 |
| `publish_sequence` | `integer` | 必填，作业内发布序号，`>= 1` |
| `snapshot_count` | `integer` | 必填，默认 `0`，`>= 0` |
| `initial_publication` | `boolean` | 必填，是否首次发布 |
| `published_at` | `timestamptz` | 必填，本批次快照采集时间 |
| `published_by_user_id` | `bigint` | 可空，发布人，外键到 `users.id`，删除置空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_grade_publish_snapshot_batches_assignment_sequence`
- `idx_grade_publish_snapshot_batches_assignment_published_at`
- `idx_grade_publish_snapshot_batches_offering_published_at`
- `ck_grade_publish_snapshot_batches_sequence`
- `ck_grade_publish_snapshot_batches_count`

### `grade_publish_snapshots`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `publish_batch_id` | `bigint` | 必填，外键到 `grade_publish_snapshot_batches.id`，级联删除 |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id`，删除置空 |
| `student_user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `submission_id` | `bigint` | 可空，外键到 `submissions.id`，删除置空 |
| `submission_no` | `text` | 可空，提交编号快照 |
| `attempt_no` | `integer` | 可空，若存在则 `>= 1` |
| `submitted_at` | `timestamptz` | 可空，提交时间快照 |
| `total_final_score` | `integer` | 必填，默认 `0`，`>= 0` |
| `total_max_score` | `integer` | 必填，默认 `0`，`>= 0` |
| `auto_scored_score` | `integer` | 必填，默认 `0`，`>= 0` |
| `manual_scored_score` | `integer` | 可空，人工部分总分 |
| `fully_graded` | `boolean` | 必填，默认 `false` |
| `snapshot_json` | `text` | 必填，保存发布时的学生 / 提交 / 成绩摘要 / 分题批改快照 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_grade_publish_snapshots_batch_student`
- `idx_grade_publish_snapshots_assignment_batch`
- `idx_grade_publish_snapshots_student_batch`
- `ck_grade_publish_snapshots_attempt_no`
- `ck_grade_publish_snapshots_total_final_score`
- `ck_grade_publish_snapshots_total_max_score`
- `ck_grade_publish_snapshots_auto_scored_score`

### `programming_workspaces`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `assignment_question_id` | `bigint` | 必填，外键到 `assignment_questions.id`，级联删除 |
| `user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `programming_language` | `text` | 必填，`PYTHON3 / JAVA21 / CPP17 / GO122`（兼容接受 `JAVA17`） |
| `code_text` | `text` | 可空，兼容 legacy 单文件模式的入口文件正文 |
| `entry_file_path` | `text` | 可空，当前入口文件路径 |
| `source_files_json` | `text` | 必填，默认 `[]`，保存目录树源码快照 |
| `source_directories_json` | `text` | 必填，默认 `[]`，保存目录树目录列表 |
| `artifact_ids_json` | `text` | 必填，默认 `[]`，保存附件引用列表 |
| `last_stdin_text` | `text` | 可空，最近一次试运行输入 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_programming_workspaces_question_user`
- `ix_programming_workspaces_assignment_user_updated_at`

### `programming_workspace_revisions`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `workspace_id` | `bigint` | 必填，外键到 `programming_workspaces.id`，级联删除 |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `assignment_question_id` | `bigint` | 必填，外键到 `assignment_questions.id`，级联删除 |
| `user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `revision_no` | `bigint` | 必填，工作区内递增版本号 |
| `revision_kind` | `text` | 必填，`MANUAL_SAVE / AUTO_SAVE / FILE_OPERATION / RESET_TO_TEMPLATE / RESTORE_REVISION` |
| `revision_message` | `text` | 可空，最长 255 |
| `programming_language` | `text` | 必填，`PYTHON3 / JAVA21 / CPP17 / GO122`（兼容接受 `JAVA17`） |
| `code_text` | `text` | 可空，兼容 legacy 单文件模式的入口文件正文 |
| `artifact_ids_json` | `text` | 必填，默认 `[]`，保存附件引用列表 |
| `entry_file_path` | `text` | 可空，当前入口文件路径 |
| `source_files_json` | `text` | 必填，默认 `[]`，保存目录树源码快照 |
| `source_directories_json` | `text` | 必填，默认 `[]`，保存目录树目录列表 |
| `last_stdin_text` | `text` | 可空，最近一次试运行输入 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `uk_programming_workspace_revisions_workspace_revision_no`
- `ix_programming_workspace_revisions_question_user_created_at`

### `judge_jobs`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `submission_id` | `bigint` | 必填，外键到 `submissions.id`，级联删除 |
| `submission_answer_id` | `bigint` | 可空，题目级评测时外键到 `submission_answers.id`，级联删除 |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `assignment_question_id` | `bigint` | 可空，题目级评测时外键到 `assignment_questions.id`，级联删除 |
| `offering_id` | `bigint` | 必填，外键到 `course_offerings.id`，级联删除 |
| `teaching_class_id` | `bigint` | 可空，外键到 `teaching_classes.id`，删除置空 |
| `submitter_user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `requested_by_user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `trigger_type` | `text` | 必填，`AUTO / MANUAL_REJUDGE` |
| `status` | `text` | 必填，默认 `PENDING`，`PENDING / RUNNING / SUCCEEDED / FAILED` |
| `engine_code` | `text` | 必填，当前固定为 `GO_JUDGE` |
| `engine_job_ref` | `text` | 可空，最长 128 |
| `result_summary` | `text` | 结果摘要 |
| `verdict` | `text` | 可空，`ACCEPTED / WRONG_ANSWER / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED / OUTPUT_LIMIT_EXCEEDED / RUNTIME_ERROR / SYSTEM_ERROR` |
| `total_case_count` | `integer` | 可空，`>= 0` |
| `passed_case_count` | `integer` | 可空，`>= 0`，且不大于 `total_case_count` |
| `score` | `integer` | 可空，`>= 0` |
| `max_score` | `integer` | 可空，`>= 0`，且不小于 `score` |
| `stdout_excerpt` | `text` | 可空，输出摘要 |
| `stderr_excerpt` | `text` | 可空，错误输出摘要 |
| `time_millis` | `bigint` | 可空，聚合运行时长，`>= 0` |
| `memory_bytes` | `bigint` | 可空，聚合内存峰值，`>= 0` |
| `error_message` | `text` | 可空，基础设施失败信息 |
| `case_results_json` | `text` | 可空，逐测试点摘要 JSON |
| `detail_report_json` | `text` | 可空，旧详细评测报告 JSON，保留兼容回退 |
| `detail_report_object_key` | `text` | 可空，详细评测报告对象引用 |
| `source_snapshot_object_key` | `text` | 可空，正式评测源码快照对象引用 |
| `artifact_manifest_object_key` | `text` | 可空，正式评测归档清单对象引用 |
| `artifact_trace_json` | `text` | 可空，submission / answer / judge job 三维产物追踪摘要 |
| `queued_at` | `timestamptz` | 必填，默认 `now()` |
| `started_at` | `timestamptz` | 可空 |
| `finished_at` | `timestamptz` | 可空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ix_judge_jobs_submission_id_queued_at`
- `ix_judge_jobs_submission_answer_id_queued_at`
- `ix_judge_jobs_assignment_question_id_queued_at`
- `ix_judge_jobs_status_queued_at`
- `ix_judge_jobs_assignment_submitter_queued_at`
- `ck_judge_jobs_time_order`
- `ck_judge_jobs_case_progress`
- `ck_judge_jobs_score_progress`
- `ck_judge_jobs_answer_scope`

### `programming_sample_runs`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `assignment_id` | `bigint` | 必填，外键到 `assignments.id`，级联删除 |
| `assignment_question_id` | `bigint` | 必填，外键到 `assignment_questions.id`，级联删除 |
| `user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `programming_language` | `text` | 必填，`PYTHON3 / JAVA21 / CPP17 / GO122`（兼容接受 `JAVA17`） |
| `code_text` | `text` | 可空，兼容 legacy 单文件模式的入口文件正文 |
| `entry_file_path` | `text` | 可空，本次样例运行入口文件路径 |
| `source_files_json` | `text` | 必填，默认 `[]`，旧目录树源码快照字段，保留兼容回退 |
| `source_directories_json` | `text` | 必填，默认 `[]`，旧目录树目录列表字段，保留兼容回退 |
| `source_snapshot_object_key` | `text` | 可空，样例试运行源码快照对象引用 |
| `artifact_ids_json` | `text` | 必填，默认 `[]`，保存附件引用列表 |
| `stdin_text` | `text` | 必填，样例输入快照 |
| `expected_stdout` | `text` | 可空，样例预期输出快照 |
| `workspace_revision_id` | `bigint` | 可空，外键到 `programming_workspace_revisions.id`，删除置空 |
| `input_mode` | `text` | 必填，默认 `SAMPLE`，`SAMPLE / CUSTOM` |
| `status` | `text` | 必填，`RUNNING / SUCCEEDED / FAILED` |
| `verdict` | `text` | 可空，沿用 judge verdict 语义 |
| `stdout_text` | `text` | 可空，完整标准输出 |
| `stderr_text` | `text` | 可空，完整标准错误 |
| `result_summary` | `text` | 可空，用户可读摘要 |
| `error_message` | `text` | 可空，执行失败信息 |
| `detail_report_json` | `text` | 可空，旧样例试运行详细报告 JSON，保留兼容回退 |
| `detail_report_object_key` | `text` | 可空，样例试运行详细报告对象引用 |
| `time_millis` | `bigint` | 可空，`>= 0` |
| `memory_bytes` | `bigint` | 可空，`>= 0` |
| `started_at` | `timestamptz` | 可空 |
| `finished_at` | `timestamptz` | 可空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ix_programming_sample_runs_question_user_created_at`

### `academic_profiles`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `academic_id` | `text` | 必填，最长 64，大小写不敏感唯一 |
| `real_name` | `text` | 必填，最长 64 |
| `identity_type` | `text` | 必填，`TEACHER / STUDENT / ADMIN` |
| `profile_status` | `text` | 必填，默认 `ACTIVE`，`ACTIVE / SUSPENDED / GRADUATED / LEFT` |
| `phone` | `text` | 画像手机号，可空 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_academic_profiles_user_id`
- `ux_academic_profiles_academic_id_lower`
- `ix_academic_profiles_identity_type_status`

### `user_org_memberships`

| 列名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `bigint` | 主键，identity |
| `user_id` | `bigint` | 必填，外键到 `users.id`，级联删除 |
| `org_unit_id` | `bigint` | 必填，外键到 `org_units.id`，级联删除 |
| `membership_type` | `text` | 必填，`ENROLLED / TEACHES / ASSISTS / MANAGES / BELONGS_TO_GROUP` |
| `membership_status` | `text` | 必填，默认 `ACTIVE`，`ACTIVE / INACTIVE / COMPLETED / REMOVED` |
| `source_type` | `text` | 必填，默认 `MANUAL`，`MANUAL / IMPORT / SYNC / SSO_BIND` |
| `start_at` | `timestamptz` | 生效开始时间 |
| `end_at` | `timestamptz` | 生效结束时间 |
| `created_at` | `timestamptz` | 必填，默认 `now()` |
| `updated_at` | `timestamptz` | 必填，默认 `now()` |

索引与约束：

- `ux_user_org_memberships_unique_binding`
- `ix_user_org_memberships_user_id`
- `ix_user_org_memberships_org_unit_id`

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
- `academic_profiles.user_id -> users.id`
- `user_org_memberships.user_id -> users.id`
- `user_org_memberships.org_unit_id -> org_units.id`
- `user_scope_roles.user_id -> users.id`
- `user_scope_roles.scope_org_unit_id -> org_units.id`
- `course_catalogs.department_unit_id -> org_units.id`
- `course_offerings.catalog_id -> course_catalogs.id`
- `course_offerings.term_id -> academic_terms.id`
- `course_offerings.primary_college_unit_id -> org_units.id`
- `course_offerings.org_course_unit_id -> org_units.id`
- `course_offering_college_maps.offering_id -> course_offerings.id`
- `course_offering_college_maps.college_unit_id -> org_units.id`
- `teaching_classes.offering_id -> course_offerings.id`
- `teaching_classes.org_class_unit_id -> org_units.id`
- `course_members.offering_id -> course_offerings.id`
- `course_members.teaching_class_id -> teaching_classes.id`
- `course_members.user_id -> users.id`
- `assignments.offering_id -> course_offerings.id`
- `assignments.teaching_class_id -> teaching_classes.id`
- `assignments.grade_published_by_user_id -> users.id`
- `assignments.created_by_user_id -> users.id`
- `grade_publish_snapshot_batches.assignment_id -> assignments.id`
- `grade_publish_snapshot_batches.offering_id -> course_offerings.id`
- `grade_publish_snapshot_batches.teaching_class_id -> teaching_classes.id`
- `grade_publish_snapshot_batches.published_by_user_id -> users.id`
- `grade_publish_snapshots.publish_batch_id -> grade_publish_snapshot_batches.id`
- `grade_publish_snapshots.assignment_id -> assignments.id`
- `grade_publish_snapshots.offering_id -> course_offerings.id`
- `grade_publish_snapshots.teaching_class_id -> teaching_classes.id`
- `grade_publish_snapshots.student_user_id -> users.id`
- `grade_publish_snapshots.submission_id -> submissions.id`
- `question_bank_questions.offering_id -> course_offerings.id`
- `question_bank_questions.category_id -> question_bank_categories.id`
- `question_bank_questions.created_by_user_id -> users.id`
- `question_bank_questions.archived_by_user_id -> users.id`
- `question_bank_question_options.question_id -> question_bank_questions.id`
- `question_bank_categories.offering_id -> course_offerings.id`
- `question_bank_categories.created_by_user_id -> users.id`
- `question_bank_tags.offering_id -> course_offerings.id`
- `question_bank_tags.created_by_user_id -> users.id`
- `question_bank_question_tags.question_id -> question_bank_questions.id`
- `question_bank_question_tags.tag_id -> question_bank_tags.id`
- `assignment_sections.assignment_id -> assignments.id`
- `assignment_questions.assignment_id -> assignments.id`
- `assignment_questions.assignment_section_id -> assignment_sections.id`
- `assignment_questions.source_question_id -> question_bank_questions.id`
- `assignment_question_options.assignment_question_id -> assignment_questions.id`
- `assignment_judge_profiles.assignment_id -> assignments.id`
- `assignment_judge_cases.assignment_id -> assignment_judge_profiles.assignment_id`
- `judge_environment_profiles.offering_id -> course_offerings.id`
- `judge_environment_profiles.created_by_user_id -> users.id`
- `judge_environment_profiles.archived_by_user_id -> users.id`
- `submissions.assignment_id -> assignments.id`
- `submissions.offering_id -> course_offerings.id`
- `submissions.teaching_class_id -> teaching_classes.id`
- `submissions.submitter_user_id -> users.id`
- `submission_artifacts.assignment_id -> assignments.id`
- `submission_artifacts.offering_id -> course_offerings.id`
- `submission_artifacts.teaching_class_id -> teaching_classes.id`
- `submission_artifacts.submission_id -> submissions.id`
- `submission_artifacts.uploader_user_id -> users.id`
- `submission_answers.submission_id -> submissions.id`
- `submission_answers.assignment_question_id -> assignment_questions.id`
- `submission_answers.graded_by_user_id -> users.id`
- `grade_appeals.offering_id -> course_offerings.id`
- `grade_appeals.teaching_class_id -> teaching_classes.id`
- `grade_appeals.assignment_id -> assignments.id`
- `grade_appeals.submission_id -> submissions.id`
- `grade_appeals.submission_answer_id -> submission_answers.id`
- `grade_appeals.student_user_id -> users.id`
- `grade_appeals.responded_by_user_id -> users.id`
- `programming_workspaces.assignment_id -> assignments.id`
- `programming_workspaces.assignment_question_id -> assignment_questions.id`
- `programming_workspaces.user_id -> users.id`
- `programming_workspace_revisions.workspace_id -> programming_workspaces.id`
- `programming_workspace_revisions.assignment_id -> assignments.id`
- `programming_workspace_revisions.assignment_question_id -> assignment_questions.id`
- `programming_workspace_revisions.user_id -> users.id`
- `judge_jobs.submission_id -> submissions.id`
- `judge_jobs.submission_answer_id -> submission_answers.id`
- `judge_jobs.assignment_id -> assignments.id`
- `judge_jobs.assignment_question_id -> assignment_questions.id`
- `judge_jobs.offering_id -> course_offerings.id`
- `judge_jobs.teaching_class_id -> teaching_classes.id`
- `judge_jobs.submitter_user_id -> users.id`
- `judge_jobs.requested_by_user_id -> users.id`
- `programming_sample_runs.assignment_id -> assignments.id`
- `programming_sample_runs.assignment_question_id -> assignment_questions.id`
- `programming_sample_runs.user_id -> users.id`
- `programming_sample_runs.workspace_revision_id -> programming_workspace_revisions.id`
- `audit_logs.actor_user_id -> users.id`

## 设计说明

- `user_scope_roles` 用于表达“一个用户在多个组织节点上承担不同治理身份”的场景。
- `academic_profiles` 用于表达学号/工号、真实姓名和教务身份类型，不替代账号基础资料。
- `user_org_memberships` 用于表达用户在课程/班级等组织下的业务成员关系，不替代治理身份。
- `course_offerings` 是课程系统的业务核心，教学班、成员和后续任务/实验都应围绕它挂接。
- `assignments` 当前表达“课程公共作业”与“教学班专属作业”两种范围，并承载 assignment 级成绩发布时间与发布人。
- `question_bank_questions` 与 `assignment_questions` 分离建模，确保题库复用与已发布作业快照互不污染；题库题目归档后也不会反向修改既有快照。
- `question_bank_categories` 作为开课实例内题库分类字典存在，题目当前只支持一个主分类，分类名按 `trim + lower-case` 做查找与复用。
- 题库标签拆成 `question_bank_tags` 与 `question_bank_question_tags`，避免直接把标签数组塞进题目主表，便于后续扩展标签运营和按标签精确过滤。
- `assignment_sections / assignment_questions / assignment_question_options` 用于表达结构化试卷的快照，不再把题目结构塞进 assignment 单列字段。
- `submissions` 当前表达正式提交受理，并允许文本内容为空以支持附件型提交。
- `submission_artifacts` 采用“先上传元数据，再在正式提交时绑定 submission”的两阶段模型。
- `submission_answers` 当前承载分题答案、客观题自动得分、人工批改结果、批改反馈与批改人留痕；编程题自动评测失败时会把 `grading_status` 标记为 `PROGRAMMING_JUDGE_FAILED`，便于与“仍在等待评测”的 `PENDING_PROGRAMMING_JUDGE` 区分。成绩册排名、通过率和 batch-adjust 第一阶段都继续复用该表，不新增独立成绩明细表。
- `grade_appeals` 当前保存学生围绕非客观题答案发起的成绩申诉和复核结果，约束“同一答案同一时间最多一个活动申诉”。
- `grade_publish_snapshot_batches / grade_publish_snapshots` 当前作为成绩发布快照 v1 的最小追踪模型存在；每次 assignment 级成绩发布都会生成一个新批次，快照按“每个学生最新正式提交”写入，不直接替代现有成绩读取链路。
- `programming_workspaces` 用于保存学生在单道编程题上的目录树工作区快照，不改变正式提交版本号和成绩语义，并兼容 legacy `codeText`；当前还会记录目录列表与最近一次标准输入，便于断线恢复。
- `programming_workspace_revisions` 以追加写方式保存工作区历史版本，用于模板重置、历史恢复和试运行复用，不单独引入复杂的增量补丁协议。
- `assignment_judge_profiles` 当前只表达 `PYTHON3 + TEXT_BODY` 的脚本型自动评测配置。
- `assignment_judge_cases` 当前保存标准输入、预期输出和分值，不包含更复杂的断言规则。
- `judge_environment_profiles` 当前作为开课实例内可复用的编程题评测环境模板存在；题库题目和 assignment question 通过 `profileId / profileCode` 引用后，会先解析模板再固化到 `assignment_questions.config_json` 的环境快照中。
- `assignment_questions.config_json` 当前已承载结构化编程题的隐藏测试点、资源限制和语言配置。
- `judge_jobs` 当前已同时表达 submission 级 legacy job 和 `submission_answer_id` 级 question-level job；逐测试点摘要继续保留在数据库，完整详细报告优先写入对象存储并通过 `detail_report_object_key` 回放，同时补充 `source_snapshot_object_key / artifact_manifest_object_key / artifact_trace_json` 用于正式评测归档与追踪。
- `programming_sample_runs` 与 `judge_jobs` 分开建模，确保样例试运行不会污染正式评测历史、提交次数与成绩；当前样例试运行会把详细报告和源码快照优先对象化存储，并通过 `detail_report_object_key / source_snapshot_object_key` 回放，可继续回指工作区修订。
- `assignment_questions.config_json.customJudgeScript` 当前按“脚本内容”语义保存，由 judge 模块固定落盘为 Python checker 执行，不额外引入新表。
- `course_members` 用于表达教师、助教、学生的课程角色，并与 `user_org_memberships` 做同步，不回写为平台治理身份。
- `org_units` 仍使用邻接表，当前实现通过父链回溯完成作用域判定；若后续规模扩大，可引入路径列或 `ltree` 优化。
- 平台配置移除了版本化能力，若未来需要配置历史，可通过审计快照补充。
