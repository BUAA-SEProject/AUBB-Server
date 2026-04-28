-- 缓解 legacy 数据源同步 role_bindings 时的瞬时未生效窗口。
-- 新建治理/成员/授权组绑定后，登录态和紧随其后的鉴权请求应立即命中新权限快照，
-- 避免 created_at/joined_at 精度或应用/数据库时钟抖动导致短暂 fallback 到 legacy 视图。

CREATE OR REPLACE FUNCTION legacy_binding_effective_from(
    binding_from TIMESTAMPTZ,
    binding_status TEXT
) RETURNS TIMESTAMPTZ AS $$
DECLARE
    activation_cutoff TIMESTAMPTZ;
BEGIN
    IF binding_from IS NULL THEN
        RETURN NULL;
    END IF;

    IF binding_status IS DISTINCT FROM 'ACTIVE' THEN
        RETURN binding_from;
    END IF;

    activation_cutoff := clock_timestamp() - interval '1 second';
    IF binding_from > activation_cutoff THEN
        RETURN activation_cutoff;
    END IF;

    RETURN binding_from;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_role_bindings_from_user_scope_roles() RETURNS TRIGGER AS $$
DECLARE
    binding_role_code TEXT;
    binding_scope_type TEXT;
    binding_created_at TIMESTAMPTZ;
BEGIN
    DELETE FROM role_bindings
    WHERE source_type = 'LEGACY_GOVERNANCE'
      AND source_ref_id = COALESCE(NEW.id, OLD.id);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    binding_role_code := CASE NEW.role_code
        WHEN 'SCHOOL_ADMIN' THEN 'school_admin'
        WHEN 'COLLEGE_ADMIN' THEN 'college_admin'
        WHEN 'COURSE_ADMIN' THEN 'course_manager'
        WHEN 'CLASS_ADMIN' THEN 'class_admin'
    END;
    binding_created_at := NEW.created_at;

    SELECT lower(org_units.type)
    INTO binding_scope_type
    FROM org_units
    WHERE org_units.id = NEW.scope_org_unit_id;

    IF binding_role_code IS NOT NULL AND binding_scope_type IS NOT NULL THEN
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
            NEW.user_id,
            roles.id,
            binding_scope_type,
            NEW.scope_org_unit_id,
            '{}'::jsonb,
            'ACTIVE',
            legacy_binding_effective_from(binding_created_at, 'ACTIVE'),
            NULL,
            NULL,
            'LEGACY_GOVERNANCE',
            NEW.id,
            binding_created_at,
            binding_created_at
        FROM roles
        WHERE roles.code = binding_role_code;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_role_bindings_from_course_members() RETURNS TRIGGER AS $$
DECLARE
    binding_role_code TEXT;
    binding_scope_type TEXT;
    binding_scope_id BIGINT;
    binding_status TEXT;
BEGIN
    DELETE FROM role_bindings
    WHERE source_type = 'LEGACY_COURSE_MEMBER'
      AND source_ref_id = COALESCE(NEW.id, OLD.id);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    binding_role_code := CASE NEW.member_role
        WHEN 'INSTRUCTOR' THEN 'offering_teacher'
        WHEN 'CLASS_INSTRUCTOR' THEN 'class_teacher'
        WHEN 'OFFERING_TA' THEN 'offering_ta'
        WHEN 'TA' THEN 'class_ta'
        WHEN 'STUDENT' THEN 'student'
        WHEN 'OBSERVER' THEN 'observer'
    END;

    binding_scope_type := CASE
        WHEN NEW.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN 'offering'
        ELSE 'class'
    END;

    binding_scope_id := CASE
        WHEN NEW.member_role IN ('INSTRUCTOR', 'OFFERING_TA', 'OBSERVER') THEN NEW.offering_id
        ELSE NEW.teaching_class_id
    END;

    binding_status := CASE WHEN NEW.member_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END;

    IF binding_role_code IS NOT NULL AND binding_scope_id IS NOT NULL THEN
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
            NEW.user_id,
            roles.id,
            binding_scope_type,
            binding_scope_id,
            '{}'::jsonb,
            binding_status,
            legacy_binding_effective_from(NEW.joined_at, binding_status),
            NEW.left_at,
            NULL,
            'LEGACY_COURSE_MEMBER',
            NEW.id,
            NEW.created_at,
            NEW.updated_at
        FROM roles
        WHERE roles.code = binding_role_code;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_role_bindings_from_auth_group_members() RETURNS TRIGGER AS $$
DECLARE
    binding_role_code TEXT;
    binding_scope_type TEXT;
    binding_scope_id BIGINT;
    binding_status TEXT;
BEGIN
    DELETE FROM role_bindings
    WHERE source_type = 'LEGACY_AUTHZ_GROUP'
      AND source_ref_id = COALESCE(NEW.id, OLD.id);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    SELECT
        CASE auth_group_templates.code
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
        END,
        lower(auth_groups.scope_type),
        auth_groups.scope_ref_id,
        CASE
            WHEN auth_groups.status = 'ACTIVE'
                AND (NEW.expires_at IS NULL OR NEW.expires_at > now())
                THEN 'ACTIVE'
            ELSE 'INACTIVE'
        END
    INTO binding_role_code, binding_scope_type, binding_scope_id, binding_status
    FROM auth_groups
    JOIN auth_group_templates ON auth_group_templates.id = auth_groups.template_id
    WHERE auth_groups.id = NEW.group_id;

    IF binding_role_code IS NOT NULL AND binding_scope_id IS NOT NULL THEN
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
            NEW.user_id,
            roles.id,
            binding_scope_type,
            binding_scope_id,
            '{}'::jsonb,
            binding_status,
            legacy_binding_effective_from(NEW.joined_at, binding_status),
            NEW.expires_at,
            NULL,
            'LEGACY_AUTHZ_GROUP',
            NEW.id,
            NEW.created_at,
            NEW.updated_at
        FROM roles
        WHERE roles.code = binding_role_code;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
