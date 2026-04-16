CREATE TABLE question_bank_categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    category_name TEXT NOT NULL CHECK (length(category_name) <= 64),
    normalized_name TEXT NOT NULL CHECK (length(normalized_name) <= 64),
    created_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_question_bank_categories_offering_normalized_name UNIQUE (offering_id, normalized_name)
);

CREATE INDEX ix_question_bank_categories_offering_name
    ON question_bank_categories (offering_id, category_name);

ALTER TABLE question_bank_questions
    ADD COLUMN category_id BIGINT REFERENCES question_bank_categories (id) ON DELETE SET NULL;

CREATE INDEX ix_question_bank_questions_offering_category_active
    ON question_bank_questions (offering_id, category_id, created_at DESC, id DESC)
    WHERE archived_at IS NULL;
