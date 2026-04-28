CREATE TABLE course_resources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE CASCADE,
    uploader_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (length(title) <= 200),
    object_key TEXT NOT NULL UNIQUE CHECK (length(object_key) <= 255),
    original_filename TEXT NOT NULL CHECK (length(original_filename) <= 255),
    content_type TEXT NOT NULL CHECK (length(content_type) <= 128),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 52428800),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_course_resources_offering_created_at
    ON course_resources (offering_id, created_at DESC, id DESC);
CREATE INDEX ix_course_resources_teaching_class_created_at
    ON course_resources (teaching_class_id, created_at DESC, id DESC);
