CREATE TABLE question_bank_tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    tag_name TEXT NOT NULL CHECK (length(tag_name) <= 32),
    created_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_question_bank_tags_offering_name UNIQUE (offering_id, tag_name)
);

CREATE INDEX ix_question_bank_tags_offering_name
    ON question_bank_tags (offering_id, tag_name);

CREATE TABLE question_bank_question_tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES question_bank_questions (id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES question_bank_tags (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_question_bank_question_tags UNIQUE (question_id, tag_id)
);

CREATE INDEX ix_question_bank_question_tags_tag_id
    ON question_bank_question_tags (tag_id, question_id);

CREATE INDEX ix_question_bank_question_tags_question_id
    ON question_bank_question_tags (question_id);
