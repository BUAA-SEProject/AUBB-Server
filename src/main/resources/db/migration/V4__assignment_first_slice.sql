CREATE TABLE assignments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (length(title) <= 128),
    description TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    open_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    max_submissions INTEGER NOT NULL CHECK (max_submissions > 0),
    published_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (due_at >= open_at),
    CHECK (closed_at IS NULL OR published_at IS NULL OR closed_at >= published_at)
);

CREATE INDEX ix_assignments_offering_id_status ON assignments (offering_id, status);
CREATE INDEX ix_assignments_teaching_class_id_status ON assignments (teaching_class_id, status);
CREATE INDEX ix_assignments_open_at_due_at ON assignments (open_at, due_at);
