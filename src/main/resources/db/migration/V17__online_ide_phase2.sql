ALTER TABLE programming_workspaces
    ADD COLUMN source_directories_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN last_stdin_text TEXT;

CREATE TABLE programming_workspace_revisions (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES programming_workspaces(id) ON DELETE CASCADE,
    assignment_id BIGINT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    assignment_question_id BIGINT NOT NULL REFERENCES assignment_questions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revision_no BIGINT NOT NULL,
    revision_kind VARCHAR(32) NOT NULL,
    revision_message VARCHAR(255),
    programming_language VARCHAR(32) NOT NULL,
    code_text TEXT,
    artifact_ids_json TEXT NOT NULL DEFAULT '[]',
    entry_file_path TEXT,
    source_files_json TEXT NOT NULL DEFAULT '[]',
    source_directories_json TEXT NOT NULL DEFAULT '[]',
    last_stdin_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_programming_workspace_revisions_workspace_revision_no UNIQUE (workspace_id, revision_no)
);

CREATE INDEX ix_programming_workspace_revisions_question_user_created_at
    ON programming_workspace_revisions (assignment_question_id, user_id, created_at DESC);

ALTER TABLE programming_sample_runs
    ADD COLUMN source_directories_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN workspace_revision_id BIGINT REFERENCES programming_workspace_revisions(id) ON DELETE SET NULL,
    ADD COLUMN input_mode VARCHAR(32) NOT NULL DEFAULT 'SAMPLE',
    ADD COLUMN detail_report_json TEXT;
