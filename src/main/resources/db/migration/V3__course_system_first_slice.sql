CREATE TABLE academic_terms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    term_code TEXT NOT NULL CHECK (length(term_code) <= 32),
    term_name TEXT NOT NULL CHECK (length(term_name) <= 64),
    school_year TEXT NOT NULL CHECK (length(school_year) <= 16),
    semester TEXT NOT NULL CHECK (semester IN ('SPRING', 'SUMMER', 'AUTUMN', 'WINTER')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'PLANNING' CHECK (status IN ('PLANNING', 'ONGOING', 'ENDED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);

CREATE UNIQUE INDEX ux_academic_terms_term_code_lower ON academic_terms (lower(term_code));

CREATE TABLE course_catalogs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_code TEXT NOT NULL CHECK (length(course_code) <= 64),
    course_name TEXT NOT NULL CHECK (length(course_name) <= 128),
    course_type TEXT NOT NULL CHECK (course_type IN ('REQUIRED', 'ELECTIVE', 'GENERAL', 'PRACTICE')),
    credit NUMERIC(4, 1) NOT NULL CHECK (credit >= 0),
    total_hours INTEGER NOT NULL CHECK (total_hours >= 0),
    department_unit_id BIGINT NOT NULL REFERENCES org_units (id) ON DELETE RESTRICT,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_course_catalogs_course_code_lower ON course_catalogs (lower(course_code));
CREATE INDEX ix_course_catalogs_department_unit_id ON course_catalogs (department_unit_id);

CREATE TABLE course_offerings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_id BIGINT NOT NULL REFERENCES course_catalogs (id) ON DELETE RESTRICT,
    term_id BIGINT NOT NULL REFERENCES academic_terms (id) ON DELETE RESTRICT,
    offering_code TEXT NOT NULL CHECK (length(offering_code) <= 64),
    offering_name TEXT NOT NULL CHECK (length(offering_name) <= 128),
    primary_college_unit_id BIGINT NOT NULL REFERENCES org_units (id) ON DELETE RESTRICT,
    org_course_unit_id BIGINT NOT NULL REFERENCES org_units (id) ON DELETE RESTRICT,
    delivery_mode TEXT NOT NULL CHECK (delivery_mode IN ('ONLINE', 'OFFLINE', 'HYBRID')),
    language TEXT NOT NULL CHECK (language IN ('ZH', 'EN', 'BILINGUAL')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    selected_count INTEGER NOT NULL DEFAULT 0 CHECK (selected_count >= 0),
    intro TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ONGOING', 'FROZEN', 'ENDED', 'ARCHIVED')),
    publish_at TIMESTAMPTZ,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (selected_count <= capacity),
    CHECK (end_at IS NULL OR start_at IS NULL OR end_at >= start_at)
);

CREATE UNIQUE INDEX ux_course_offerings_offering_code_lower ON course_offerings (lower(offering_code));
CREATE UNIQUE INDEX ux_course_offerings_org_course_unit_id ON course_offerings (org_course_unit_id);
CREATE INDEX ix_course_offerings_catalog_id ON course_offerings (catalog_id);
CREATE INDEX ix_course_offerings_term_id ON course_offerings (term_id);
CREATE INDEX ix_course_offerings_primary_college_unit_id ON course_offerings (primary_college_unit_id);
CREATE INDEX ix_course_offerings_status ON course_offerings (status);

CREATE TABLE course_offering_college_maps (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    college_unit_id BIGINT NOT NULL REFERENCES org_units (id) ON DELETE RESTRICT,
    relation_type TEXT NOT NULL CHECK (relation_type IN ('PRIMARY', 'SECONDARY', 'CROSS_LISTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_course_offering_college_maps_unique ON course_offering_college_maps (offering_id, college_unit_id);
CREATE INDEX ix_course_offering_college_maps_college_unit_id ON course_offering_college_maps (college_unit_id);

CREATE TABLE teaching_classes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    class_code TEXT NOT NULL CHECK (length(class_code) <= 64),
    class_name TEXT NOT NULL CHECK (length(class_name) <= 128),
    entry_year INTEGER NOT NULL CHECK (entry_year BETWEEN 2000 AND 2100),
    org_class_unit_id BIGINT NOT NULL REFERENCES org_units (id) ON DELETE RESTRICT,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'ARCHIVED')),
    schedule_summary TEXT,
    announcement_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    discussion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    resource_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    lab_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    assignment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_teaching_classes_offering_class_code_lower ON teaching_classes (offering_id, lower(class_code));
CREATE UNIQUE INDEX ux_teaching_classes_org_class_unit_id ON teaching_classes (org_class_unit_id);
CREATE INDEX ix_teaching_classes_offering_id ON teaching_classes (offering_id);

CREATE TABLE course_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    member_role TEXT NOT NULL CHECK (member_role IN ('INSTRUCTOR', 'TA', 'STUDENT', 'OBSERVER')),
    member_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (member_status IN ('PENDING', 'ACTIVE', 'DROPPED', 'COMPLETED', 'REMOVED')),
    source_type TEXT NOT NULL DEFAULT 'MANUAL' CHECK (source_type IN ('MANUAL', 'IMPORT', 'SYNC')),
    remark TEXT,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (left_at IS NULL OR left_at >= joined_at)
);

CREATE INDEX ix_course_members_offering_id ON course_members (offering_id);
CREATE INDEX ix_course_members_teaching_class_id ON course_members (teaching_class_id);
CREATE INDEX ix_course_members_user_id ON course_members (user_id);
CREATE INDEX ix_course_members_member_role_status ON course_members (member_role, member_status);
CREATE UNIQUE INDEX ux_course_members_instructor_unique
    ON course_members (offering_id, user_id)
    WHERE member_role = 'INSTRUCTOR';
CREATE UNIQUE INDEX ux_course_members_student_unique
    ON course_members (offering_id, user_id)
    WHERE member_role = 'STUDENT';
CREATE UNIQUE INDEX ux_course_members_ta_unique
    ON course_members (offering_id, user_id, teaching_class_id)
    WHERE member_role = 'TA';
