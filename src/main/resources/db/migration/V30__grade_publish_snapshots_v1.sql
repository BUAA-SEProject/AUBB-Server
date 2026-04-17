CREATE TABLE grade_publish_snapshot_batches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE SET NULL,
    publish_sequence INTEGER NOT NULL,
    snapshot_count INTEGER NOT NULL DEFAULT 0,
    initial_publication BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ NOT NULL,
    published_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_grade_publish_snapshot_batches_sequence CHECK (publish_sequence >= 1),
    CONSTRAINT ck_grade_publish_snapshot_batches_count CHECK (snapshot_count >= 0),
    CONSTRAINT ux_grade_publish_snapshot_batches_assignment_sequence UNIQUE (assignment_id, publish_sequence)
);

CREATE INDEX idx_grade_publish_snapshot_batches_assignment_published_at
    ON grade_publish_snapshot_batches (assignment_id, published_at DESC, id DESC);

CREATE INDEX idx_grade_publish_snapshot_batches_offering_published_at
    ON grade_publish_snapshot_batches (offering_id, published_at DESC, id DESC);

CREATE TABLE grade_publish_snapshots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publish_batch_id BIGINT NOT NULL REFERENCES grade_publish_snapshot_batches (id) ON DELETE CASCADE,
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    teaching_class_id BIGINT REFERENCES teaching_classes (id) ON DELETE SET NULL,
    student_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    submission_id BIGINT REFERENCES submissions (id) ON DELETE SET NULL,
    submission_no TEXT,
    attempt_no INTEGER,
    submitted_at TIMESTAMPTZ,
    total_final_score INTEGER NOT NULL DEFAULT 0,
    total_max_score INTEGER NOT NULL DEFAULT 0,
    auto_scored_score INTEGER NOT NULL DEFAULT 0,
    manual_scored_score INTEGER,
    fully_graded BOOLEAN NOT NULL DEFAULT FALSE,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_grade_publish_snapshots_attempt_no CHECK (attempt_no IS NULL OR attempt_no >= 1),
    CONSTRAINT ck_grade_publish_snapshots_total_final_score CHECK (total_final_score >= 0),
    CONSTRAINT ck_grade_publish_snapshots_total_max_score CHECK (total_max_score >= 0),
    CONSTRAINT ck_grade_publish_snapshots_auto_scored_score CHECK (auto_scored_score >= 0),
    CONSTRAINT ux_grade_publish_snapshots_batch_student UNIQUE (publish_batch_id, student_user_id)
);

CREATE INDEX idx_grade_publish_snapshots_assignment_batch
    ON grade_publish_snapshots (assignment_id, publish_batch_id, id);

CREATE INDEX idx_grade_publish_snapshots_student_batch
    ON grade_publish_snapshots (student_user_id, publish_batch_id, id);
