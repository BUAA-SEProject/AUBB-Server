CREATE TABLE submissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_no TEXT NOT NULL UNIQUE CHECK (length(submission_no) <= 64),
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE SET NULL,
    submitter_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    status TEXT NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED')),
    content_text TEXT NOT NULL CHECK (length(content_text) <= 20000),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_submissions_assignment_submitter_attempt UNIQUE (assignment_id, submitter_user_id, attempt_no)
);

CREATE INDEX ix_submissions_assignment_id_submitted_at ON submissions (assignment_id, submitted_at DESC);
CREATE INDEX ix_submissions_submitter_user_id_submitted_at ON submissions (submitter_user_id, submitted_at DESC);
CREATE INDEX ix_submissions_offering_id_submitted_at ON submissions (offering_id, submitted_at DESC);
