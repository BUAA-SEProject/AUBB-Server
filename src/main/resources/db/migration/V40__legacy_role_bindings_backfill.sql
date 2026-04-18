-- 幂等补齐历史库中由旧治理/成员/授权组数据推导出的 role_bindings。
-- V37 已包含首轮 backfill 与触发器；本迁移仅用于修复早期库中可能缺失的绑定。

INSERT INTO role_bindings (
    user_id,
    role_id,
    scope_type,
    scope_id,
    constraints_json,
    status,
    effective_from,
    effective_to,
    granted_by,
    source_type,
    source_ref_id,
    created_at,
    updated_at
)
SELECT
    user_scope_roles.user_id,
    roles.id,
    lower(org_units.type),
    user_scope_roles.scope_org_unit_id,
    '{}'::jsonb,
    'ACTIVE',
    user_scope_roles.created_at,
    NULL,
    NULL,
    'LEGACY_GOVERNANCE',
    user_scope_roles.id,
    user_scope_roles.created_at,
    user_scope_roles.created_at
FROM user_scope_roles
JOIN org_units ON org_units.id = user_scope_roles.scope_org_unit_id
JOIN roles ON roles.code = CASE user_scope_roles.role_code
    WHEN 'SCHOOL_ADMIN' THEN 'school_admin'
    WHEN 'COLLEGE_ADMIN' THEN 'college_admin'
    WHEN 'COURSE_ADMIN' THEN 'course_manager'
    WHEN 'CLASS_ADMIN' THEN 'class_admin'
END
ON CONFLICT (user_id, role_id, scope_type, scope_id, source_type, source_ref_id) DO NOTHING;

INSERT INTO role_bindings (
    user_id,
    role_id,
    scope_type,
    scope_id,
    constraints_json,
    status,
    effective_from,
    effective_to,
    granted_by,
    source_type,
    source_ref_id,
    created_at,
    updated_at
)
SELECT
    course_members.user_id,
    roles.id,
    CASE
        WHEN course_members.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN 'offering'
        ELSE 'class'
    END,
    CASE
        WHEN course_members.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN course_members.offering_id
        ELSE course_members.teaching_class_id
    END,
    '{}'::jsonb,
    CASE WHEN course_members.member_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
    course_members.joined_at,
    course_members.left_at,
    NULL,
    'LEGACY_COURSE_MEMBER',
    course_members.id,
    course_members.created_at,
    course_members.updated_at
FROM course_members
JOIN roles ON roles.code = CASE course_members.member_role
    WHEN 'INSTRUCTOR' THEN 'offering_teacher'
    WHEN 'CLASS_INSTRUCTOR' THEN 'class_teacher'
    WHEN 'OFFERING_TA' THEN 'offering_ta'
    WHEN 'TA' THEN 'class_ta'
    WHEN 'STUDENT' THEN 'student'
    WHEN 'OBSERVER' THEN 'observer'
END
WHERE (
        course_members.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER')
        AND course_members.offering_id IS NOT NULL
    )
   OR (
        course_members.member_role IN ('CLASS_INSTRUCTOR', 'TA', 'STUDENT')
        AND course_members.teaching_class_id IS NOT NULL
    )
ON CONFLICT (user_id, role_id, scope_type, scope_id, source_type, source_ref_id) DO NOTHING;

INSERT INTO role_bindings (
    user_id,
    role_id,
    scope_type,
    scope_id,
    constraints_json,
    status,
    effective_from,
    effective_to,
    granted_by,
    source_type,
    source_ref_id,
    created_at,
    updated_at
)
SELECT
    auth_group_members.user_id,
    roles.id,
    lower(auth_groups.scope_type),
    auth_groups.scope_ref_id,
    '{}'::jsonb,
    CASE
        WHEN auth_groups.status = 'ACTIVE'
            AND (auth_group_members.expires_at IS NULL OR auth_group_members.expires_at > now())
            THEN 'ACTIVE'
        ELSE 'INACTIVE'
    END,
    auth_group_members.joined_at,
    auth_group_members.expires_at,
    NULL,
    'LEGACY_AUTHZ_GROUP',
    auth_group_members.id,
    auth_group_members.created_at,
    auth_group_members.updated_at
FROM auth_group_members
JOIN auth_groups ON auth_groups.id = auth_group_members.group_id
JOIN auth_group_templates ON auth_group_templates.id = auth_groups.template_id
JOIN roles ON roles.code = CASE auth_group_templates.code
    WHEN 'school-admin' THEN 'school_admin'
    WHEN 'college-admin' THEN 'college_admin'
    WHEN 'course-admin' THEN 'course_manager'
    WHEN 'class-admin' THEN 'class_admin'
    WHEN 'offering-instructor' THEN 'offering_teacher'
    WHEN 'class-instructor' THEN 'class_teacher'
    WHEN 'offering-ta' THEN 'offering_ta'
    WHEN 'class-ta' THEN 'class_ta'
    WHEN 'student' THEN 'student'
    WHEN 'observer' THEN 'observer'
    WHEN 'audit-readonly' THEN 'auditor'
    WHEN 'grade-corrector' THEN 'grader'
END
ON CONFLICT (user_id, role_id, scope_type, scope_id, source_type, source_ref_id) DO NOTHING;
