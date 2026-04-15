CREATE TABLE org_units (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id BIGINT REFERENCES org_units (id) ON DELETE RESTRICT,
    code TEXT NOT NULL CHECK (length(code) <= 64),
    name TEXT NOT NULL CHECK (length(name) <= 128),
    type TEXT NOT NULL CHECK (type IN ('SCHOOL', 'COLLEGE', 'COURSE', 'CLASS')),
    level INTEGER NOT NULL CHECK (level BETWEEN 1 AND 4),
    sort_order INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_org_units_code_lower ON org_units (lower(code));
CREATE INDEX ix_org_units_parent_id_sort_order ON org_units (parent_id, sort_order, id);

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    primary_org_unit_id BIGINT REFERENCES org_units (id) ON DELETE SET NULL,
    username TEXT NOT NULL CHECK (length(username) <= 64),
    display_name TEXT NOT NULL CHECK (length(display_name) <= 128),
    email TEXT NOT NULL CHECK (length(email) <= 128),
    phone TEXT,
    password_hash TEXT NOT NULL,
    account_status TEXT NOT NULL CHECK (account_status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'EXPIRED')),
    failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
CREATE INDEX ix_users_primary_org_unit_id ON users (primary_org_unit_id);
CREATE INDEX ix_users_account_status ON users (account_status);

CREATE TABLE platform_configs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform_name TEXT NOT NULL CHECK (length(platform_name) <= 128),
    platform_short_name TEXT NOT NULL CHECK (length(platform_short_name) <= 64),
    logo_url TEXT,
    footer_text TEXT,
    default_home_path TEXT NOT NULL CHECK (length(default_home_path) <= 128),
    theme_key TEXT NOT NULL CHECK (length(theme_key) <= 64),
    login_notice TEXT,
    module_flags JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_platform_configs_singleton ON platform_configs ((true));

CREATE TABLE user_scope_roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope_org_unit_id BIGINT NOT NULL REFERENCES org_units (id) ON DELETE CASCADE,
    role_code TEXT NOT NULL CHECK (role_code IN ('SCHOOL_ADMIN', 'COLLEGE_ADMIN', 'COURSE_ADMIN', 'CLASS_ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_user_scope_roles_user_scope_role ON user_scope_roles (user_id, scope_org_unit_id, role_code);
CREATE INDEX ix_user_scope_roles_user_id ON user_scope_roles (user_id);
CREATE INDEX ix_user_scope_roles_scope_org_unit_id ON user_scope_roles (scope_org_unit_id);

CREATE TABLE audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    action TEXT NOT NULL CHECK (length(action) <= 64),
    target_type TEXT NOT NULL CHECK (length(target_type) <= 64),
    target_id TEXT,
    result TEXT NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE')),
    request_id TEXT NOT NULL CHECK (length(request_id) <= 128),
    ip TEXT NOT NULL CHECK (length(ip) <= 64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_actor_user_id_created_at ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_logs_action_created_at ON audit_logs (action, created_at DESC);
CREATE INDEX ix_audit_logs_target_type_created_at ON audit_logs (target_type, created_at DESC);
