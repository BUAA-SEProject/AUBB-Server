CREATE TABLE course_discussions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE CASCADE,
    created_by_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (length(title) <= 200),
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE course_discussion_posts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    discussion_id BIGINT NOT NULL REFERENCES course_discussions (id) ON DELETE CASCADE,
    author_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reply_to_post_id BIGINT REFERENCES course_discussion_posts (id) ON DELETE SET NULL,
    body TEXT NOT NULL CHECK (length(body) <= 5000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_course_discussions_offering_last_activity
    ON course_discussions (offering_id, last_activity_at DESC, id DESC);
CREATE INDEX ix_course_discussions_teaching_class_last_activity
    ON course_discussions (teaching_class_id, last_activity_at DESC, id DESC);
CREATE INDEX ix_course_discussion_posts_discussion_created_at
    ON course_discussion_posts (discussion_id, created_at ASC, id ASC);
