CREATE TABLE assignment_judge_profiles (
    assignment_id BIGINT PRIMARY KEY REFERENCES assignments (id) ON DELETE CASCADE,
    source_type TEXT NOT NULL CHECK (source_type IN ('TEXT_BODY')),
    language TEXT NOT NULL CHECK (language IN ('PYTHON3')),
    entry_file_name TEXT NOT NULL CHECK (length(entry_file_name) <= 64),
    time_limit_ms INTEGER NOT NULL CHECK (time_limit_ms > 0),
    memory_limit_mb INTEGER NOT NULL CHECK (memory_limit_mb > 0),
    output_limit_kb INTEGER NOT NULL CHECK (output_limit_kb > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE assignment_judge_cases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignment_judge_profiles (assignment_id) ON DELETE CASCADE,
    case_order INTEGER NOT NULL CHECK (case_order > 0),
    stdin_text TEXT NOT NULL,
    expected_stdout TEXT NOT NULL,
    score INTEGER NOT NULL CHECK (score >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_assignment_judge_cases_assignment_order UNIQUE (assignment_id, case_order)
);

CREATE INDEX ix_assignment_judge_cases_assignment_order
    ON assignment_judge_cases (assignment_id, case_order);

ALTER TABLE judge_jobs
    ADD COLUMN verdict TEXT
        CHECK (verdict IS NULL OR verdict IN (
            'ACCEPTED',
            'WRONG_ANSWER',
            'TIME_LIMIT_EXCEEDED',
            'MEMORY_LIMIT_EXCEEDED',
            'OUTPUT_LIMIT_EXCEEDED',
            'RUNTIME_ERROR',
            'SYSTEM_ERROR'
        )),
    ADD COLUMN total_case_count INTEGER CHECK (total_case_count IS NULL OR total_case_count >= 0),
    ADD COLUMN passed_case_count INTEGER CHECK (passed_case_count IS NULL OR passed_case_count >= 0),
    ADD COLUMN score INTEGER CHECK (score IS NULL OR score >= 0),
    ADD COLUMN max_score INTEGER CHECK (max_score IS NULL OR max_score >= 0),
    ADD COLUMN stdout_excerpt TEXT,
    ADD COLUMN stderr_excerpt TEXT,
    ADD COLUMN time_millis BIGINT CHECK (time_millis IS NULL OR time_millis >= 0),
    ADD COLUMN memory_bytes BIGINT CHECK (memory_bytes IS NULL OR memory_bytes >= 0),
    ADD COLUMN error_message TEXT,
    ADD CONSTRAINT ck_judge_jobs_case_progress CHECK (
        (total_case_count IS NULL AND passed_case_count IS NULL)
        OR (
            total_case_count IS NOT NULL
            AND passed_case_count IS NOT NULL
            AND passed_case_count <= total_case_count
        )
    ),
    ADD CONSTRAINT ck_judge_jobs_score_progress CHECK (
        (score IS NULL AND max_score IS NULL)
        OR (
            score IS NOT NULL
            AND max_score IS NOT NULL
            AND score <= max_score
        )
    );
