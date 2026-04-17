ALTER TABLE judge_jobs
    ADD COLUMN detail_report_object_key TEXT;

ALTER TABLE programming_sample_runs
    ADD COLUMN source_snapshot_object_key TEXT,
    ADD COLUMN detail_report_object_key TEXT;
