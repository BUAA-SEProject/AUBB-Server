CREATE TABLE judge_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE SET NULL,
    submitter_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requested_by_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    trigger_type TEXT NOT NULL CHECK (trigger_type IN ('AUTO', 'MANUAL_REJUDGE')),
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    engine_code TEXT NOT NULL DEFAULT 'GO_JUDGE' CHECK (engine_code IN ('GO_JUDGE')),
    engine_job_ref TEXT CHECK (engine_job_ref IS NULL OR length(engine_job_ref) <= 128),
    result_summary TEXT,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_judge_jobs_time_order CHECK (
        (started_at IS NULL OR started_at >= queued_at)
        AND (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
    )
);

CREATE INDEX ix_judge_jobs_submission_id_queued_at ON judge_jobs (submission_id, queued_at DESC);
CREATE INDEX ix_judge_jobs_status_queued_at ON judge_jobs (status, queued_at DESC);
CREATE INDEX ix_judge_jobs_assignment_submitter_queued_at
    ON judge_jobs (assignment_id, submitter_user_id, queued_at DESC);
