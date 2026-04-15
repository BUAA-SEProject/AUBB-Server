ALTER TABLE submissions
    ALTER COLUMN content_text DROP NOT NULL;

CREATE TABLE submission_artifacts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE SET NULL,
    submission_id BIGINT REFERENCES submissions (id) ON DELETE CASCADE,
    uploader_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE CHECK (length(object_key) <= 255),
    original_filename TEXT NOT NULL CHECK (length(original_filename) <= 255),
    content_type TEXT NOT NULL CHECK (length(content_type) <= 128),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 20971520),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_submission_artifacts_assignment_user_uploaded_at
    ON submission_artifacts (assignment_id, uploader_user_id, uploaded_at DESC);
CREATE INDEX ix_submission_artifacts_submission_id
    ON submission_artifacts (submission_id);
CREATE INDEX ix_submission_artifacts_offering_id_uploaded_at
    ON submission_artifacts (offering_id, uploaded_at DESC);
