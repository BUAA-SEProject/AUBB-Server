CREATE TABLE academic_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    academic_id TEXT NOT NULL CHECK (length(academic_id) <= 64),
    real_name TEXT NOT NULL CHECK (length(real_name) <= 64),
    identity_type TEXT NOT NULL CHECK (identity_type IN ('TEACHER', 'STUDENT', 'ADMIN')),
    profile_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (profile_status IN ('ACTIVE', 'SUSPENDED', 'GRADUATED', 'LEFT')),
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_academic_profiles_user_id ON academic_profiles (user_id);
CREATE UNIQUE INDEX ux_academic_profiles_academic_id_lower ON academic_profiles (lower(academic_id));
CREATE INDEX ix_academic_profiles_identity_type_status ON academic_profiles (identity_type, profile_status);

CREATE TABLE user_org_memberships (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    org_unit_id BIGINT NOT NULL REFERENCES org_units (id) ON DELETE CASCADE,
    membership_type TEXT NOT NULL CHECK (membership_type IN ('ENROLLED', 'TEACHES', 'ASSISTS', 'MANAGES', 'BELONGS_TO_GROUP')),
    membership_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (membership_status IN ('ACTIVE', 'INACTIVE', 'COMPLETED', 'REMOVED')),
    source_type TEXT NOT NULL DEFAULT 'MANUAL' CHECK (source_type IN ('MANUAL', 'IMPORT', 'SYNC', 'SSO_BIND')),
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_user_org_memberships_unique_binding
    ON user_org_memberships (user_id, org_unit_id, membership_type);
CREATE INDEX ix_user_org_memberships_user_id ON user_org_memberships (user_id);
CREATE INDEX ix_user_org_memberships_org_unit_id ON user_org_memberships (org_unit_id);
