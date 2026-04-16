ALTER TABLE submission_answers
    DROP CONSTRAINT IF EXISTS submission_answers_grading_status_check;

ALTER TABLE submission_answers
    ADD CONSTRAINT submission_answers_grading_status_check CHECK (grading_status IN (
        'AUTO_GRADED',
        'MANUALLY_GRADED',
        'PROGRAMMING_JUDGED',
        'PROGRAMMING_JUDGE_FAILED',
        'PENDING_MANUAL',
        'PENDING_PROGRAMMING_JUDGE'
    ));
