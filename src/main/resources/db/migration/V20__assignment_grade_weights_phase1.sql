ALTER TABLE assignments
    ADD COLUMN grade_weight INTEGER NOT NULL DEFAULT 100;

ALTER TABLE assignments
    ADD CONSTRAINT chk_assignments_grade_weight_positive
        CHECK (grade_weight > 0);
