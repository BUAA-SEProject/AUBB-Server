ALTER TABLE judge_jobs
    ADD COLUMN submission_answer_id BIGINT REFERENCES submission_answers (id) ON DELETE CASCADE,
    ADD COLUMN assignment_question_id BIGINT REFERENCES assignment_questions (id) ON DELETE CASCADE,
    ADD COLUMN case_results_json TEXT;

ALTER TABLE judge_jobs
    ADD CONSTRAINT ck_judge_jobs_answer_scope CHECK (
        (submission_answer_id IS NULL AND assignment_question_id IS NULL)
        OR (
            submission_answer_id IS NOT NULL
            AND assignment_question_id IS NOT NULL
        )
    );

CREATE INDEX ix_judge_jobs_submission_answer_id_queued_at
    ON judge_jobs (submission_answer_id, queued_at DESC);

CREATE INDEX ix_judge_jobs_assignment_question_id_queued_at
    ON judge_jobs (assignment_question_id, queued_at DESC);
