CREATE TABLE course_announcements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (length(title) <= 200),
    body TEXT NOT NULL CHECK (length(body) <= 5000),
    created_by_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_course_announcements_offering_published_at
    ON course_announcements (offering_id, published_at DESC, id DESC);
CREATE INDEX ix_course_announcements_teaching_class_published_at
    ON course_announcements (teaching_class_id, published_at DESC, id DESC);
