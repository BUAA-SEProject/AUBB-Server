CREATE TABLE labs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT NOT NULL REFERENCES teaching_classes (id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (length(title) <= 200),
    description TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    published_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_by_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_labs_offering_id_status_created_at ON labs (offering_id, status, created_at DESC);
CREATE INDEX ix_labs_teaching_class_id_status_created_at ON labs (teaching_class_id, status, created_at DESC);

CREATE TABLE lab_reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_id BIGINT NOT NULL REFERENCES labs (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT NOT NULL REFERENCES teaching_classes (id) ON DELETE CASCADE,
    student_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVIEWED', 'PUBLISHED')),
    report_content_text TEXT CHECK (report_content_text IS NULL OR length(report_content_text) <= 20000),
    teacher_annotation_text TEXT CHECK (teacher_annotation_text IS NULL OR length(teacher_annotation_text) <= 5000),
    teacher_comment_text TEXT CHECK (teacher_comment_text IS NULL OR length(teacher_comment_text) <= 5000),
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    reviewer_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_lab_reports_lab_student UNIQUE (lab_id, student_user_id)
);

CREATE INDEX ix_lab_reports_lab_id_status_updated_at ON lab_reports (lab_id, status, updated_at DESC);
CREATE INDEX ix_lab_reports_student_user_id_updated_at ON lab_reports (student_user_id, updated_at DESC);

CREATE TABLE lab_report_attachments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_id BIGINT NOT NULL REFERENCES labs (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT NOT NULL REFERENCES teaching_classes (id) ON DELETE CASCADE,
    lab_report_id BIGINT REFERENCES lab_reports (id) ON DELETE CASCADE,
    uploader_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE CHECK (length(object_key) <= 255),
    original_filename TEXT NOT NULL CHECK (length(original_filename) <= 255),
    content_type TEXT NOT NULL CHECK (length(content_type) <= 128),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 20971520),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_lab_report_attachments_lab_id_uploader_uploaded_at
    ON lab_report_attachments (lab_id, uploader_user_id, uploaded_at DESC);
CREATE INDEX ix_lab_report_attachments_lab_report_id ON lab_report_attachments (lab_report_id);
