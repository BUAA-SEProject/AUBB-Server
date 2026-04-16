CREATE TABLE grade_appeals (
    id BIGSERIAL PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id),
    teaching_class_id BIGINT NULL REFERENCES teaching_classes (id),
    assignment_id BIGINT NOT NULL REFERENCES assignments (id),
    submission_id BIGINT NOT NULL REFERENCES submissions (id),
    submission_answer_id BIGINT NOT NULL REFERENCES submission_answers (id),
    student_user_id BIGINT NOT NULL REFERENCES users (id),
    status VARCHAR(32) NOT NULL,
    appeal_reason TEXT NOT NULL,
    response_text TEXT NULL,
    resolved_score INTEGER NULL,
    responded_by_user_id BIGINT NULL REFERENCES users (id),
    responded_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_grade_appeals_assignment_created ON grade_appeals (assignment_id, created_at DESC);
CREATE INDEX idx_grade_appeals_student_created ON grade_appeals (student_user_id, created_at DESC);
CREATE INDEX idx_grade_appeals_submission_answer ON grade_appeals (submission_answer_id);

CREATE UNIQUE INDEX uq_grade_appeals_active_answer
    ON grade_appeals (submission_answer_id)
    WHERE status IN ('PENDING', 'IN_REVIEW');
