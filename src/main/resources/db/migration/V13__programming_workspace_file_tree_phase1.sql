ALTER TABLE programming_workspaces
    ADD COLUMN entry_file_path TEXT,
    ADD COLUMN source_files_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE programming_sample_runs
    ADD COLUMN entry_file_path TEXT,
    ADD COLUMN source_files_json TEXT NOT NULL DEFAULT '[]';
