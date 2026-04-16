ALTER TABLE question_bank_questions
    ADD COLUMN archived_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX ix_question_bank_questions_offering_type_active
    ON question_bank_questions (offering_id, question_type, created_at DESC, id DESC)
    WHERE archived_at IS NULL;

CREATE INDEX ix_question_bank_questions_offering_archived_at
    ON question_bank_questions (offering_id, archived_at DESC, id DESC);
