ALTER TABLE judge_jobs
    ADD COLUMN source_snapshot_object_key TEXT,
    ADD COLUMN artifact_manifest_object_key TEXT,
    ADD COLUMN artifact_trace_json TEXT;
