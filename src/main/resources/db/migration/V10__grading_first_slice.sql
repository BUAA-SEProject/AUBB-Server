ALTER TABLE assignments
    ADD COLUMN IF NOT EXISTS grade_published_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS grade_published_by_user_id BIGINT;

ALTER TABLE submission_answers
    ADD COLUMN IF NOT EXISTS graded_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS graded_at TIMESTAMPTZ;

ALTER TABLE submission_answers
    DROP CONSTRAINT IF EXISTS submission_answers_grading_status_check;

ALTER TABLE submission_answers
    ADD CONSTRAINT submission_answers_grading_status_check CHECK (grading_status IN (
        'AUTO_GRADED',
        'MANUALLY_GRADED',
        'PROGRAMMING_JUDGED',
        'PENDING_MANUAL',
        'PENDING_PROGRAMMING_JUDGE'
    ));

CREATE INDEX IF NOT EXISTS idx_assignments_grade_published_at ON assignments (grade_published_at);
CREATE INDEX IF NOT EXISTS idx_submission_answers_graded_by_user_id ON submission_answers (graded_by_user_id);
