CREATE TABLE programming_workspaces (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    assignment_question_id BIGINT NOT NULL REFERENCES assignment_questions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    programming_language VARCHAR(32) NOT NULL,
    code_text TEXT,
    artifact_ids_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_programming_workspaces_question_user UNIQUE (assignment_question_id, user_id)
);

CREATE INDEX ix_programming_workspaces_assignment_user_updated_at
    ON programming_workspaces (assignment_id, user_id, updated_at DESC);

CREATE TABLE programming_sample_runs (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    assignment_question_id BIGINT NOT NULL REFERENCES assignment_questions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    programming_language VARCHAR(32) NOT NULL,
    code_text TEXT,
    artifact_ids_json TEXT NOT NULL DEFAULT '[]',
    stdin_text TEXT NOT NULL,
    expected_stdout TEXT,
    status VARCHAR(32) NOT NULL,
    verdict VARCHAR(64),
    stdout_text TEXT,
    stderr_text TEXT,
    result_summary TEXT,
    error_message TEXT,
    time_millis BIGINT,
    memory_bytes BIGINT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_programming_sample_runs_question_user_created_at
    ON programming_sample_runs (assignment_question_id, user_id, created_at DESC);
