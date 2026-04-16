CREATE TABLE question_bank_questions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    created_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    title TEXT NOT NULL CHECK (length(title) <= 128),
    prompt_text TEXT NOT NULL,
    question_type TEXT NOT NULL CHECK (question_type IN (
        'SINGLE_CHOICE',
        'MULTIPLE_CHOICE',
        'SHORT_ANSWER',
        'FILE_UPLOAD',
        'PROGRAMMING'
    )),
    default_score INTEGER NOT NULL CHECK (default_score > 0),
    config_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_question_bank_questions_offering_type
    ON question_bank_questions (offering_id, question_type);

CREATE TABLE question_bank_question_options (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES question_bank_questions (id) ON DELETE CASCADE,
    option_order INTEGER NOT NULL CHECK (option_order > 0),
    option_key TEXT NOT NULL CHECK (length(option_key) <= 16),
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_question_bank_question_options_order UNIQUE (question_id, option_order),
    CONSTRAINT uk_question_bank_question_options_key UNIQUE (question_id, option_key)
);

CREATE INDEX ix_question_bank_question_options_question_id
    ON question_bank_question_options (question_id, option_order);

CREATE TABLE assignment_sections (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    section_order INTEGER NOT NULL CHECK (section_order > 0),
    title TEXT NOT NULL CHECK (length(title) <= 128),
    description TEXT,
    total_score INTEGER NOT NULL CHECK (total_score > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_assignment_sections_order UNIQUE (assignment_id, section_order)
);

CREATE INDEX ix_assignment_sections_assignment_id
    ON assignment_sections (assignment_id, section_order);

CREATE TABLE assignment_questions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    assignment_section_id BIGINT NOT NULL REFERENCES assignment_sections (id) ON DELETE CASCADE,
    source_question_id BIGINT REFERENCES question_bank_questions (id) ON DELETE SET NULL,
    question_order INTEGER NOT NULL CHECK (question_order > 0),
    title TEXT NOT NULL CHECK (length(title) <= 128),
    prompt_text TEXT NOT NULL,
    question_type TEXT NOT NULL CHECK (question_type IN (
        'SINGLE_CHOICE',
        'MULTIPLE_CHOICE',
        'SHORT_ANSWER',
        'FILE_UPLOAD',
        'PROGRAMMING'
    )),
    score INTEGER NOT NULL CHECK (score > 0),
    config_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_assignment_questions_order UNIQUE (assignment_section_id, question_order)
);

CREATE INDEX ix_assignment_questions_assignment_id
    ON assignment_questions (assignment_id, assignment_section_id, question_order);

CREATE INDEX ix_assignment_questions_source_question_id
    ON assignment_questions (source_question_id);

CREATE TABLE assignment_question_options (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_question_id BIGINT NOT NULL REFERENCES assignment_questions (id) ON DELETE CASCADE,
    option_order INTEGER NOT NULL CHECK (option_order > 0),
    option_key TEXT NOT NULL CHECK (length(option_key) <= 16),
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_assignment_question_options_order UNIQUE (assignment_question_id, option_order),
    CONSTRAINT uk_assignment_question_options_key UNIQUE (assignment_question_id, option_key)
);

CREATE INDEX ix_assignment_question_options_question_id
    ON assignment_question_options (assignment_question_id, option_order);

CREATE TABLE submission_answers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    assignment_question_id BIGINT NOT NULL REFERENCES assignment_questions (id) ON DELETE CASCADE,
    answer_text TEXT,
    answer_payload_json TEXT NOT NULL DEFAULT '{}',
    auto_score INTEGER CHECK (auto_score IS NULL OR auto_score >= 0),
    manual_score INTEGER CHECK (manual_score IS NULL OR manual_score >= 0),
    final_score INTEGER CHECK (final_score IS NULL OR final_score >= 0),
    grading_status TEXT NOT NULL CHECK (grading_status IN (
        'AUTO_GRADED',
        'PENDING_MANUAL',
        'PENDING_PROGRAMMING_JUDGE'
    )),
    feedback_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_submission_answers_submission_question UNIQUE (submission_id, assignment_question_id),
    CONSTRAINT ck_submission_answers_score_consistency CHECK (
        final_score IS NULL
        OR (
            auto_score IS NOT NULL
            OR manual_score IS NOT NULL
        )
    )
);

CREATE INDEX ix_submission_answers_submission_id
    ON submission_answers (submission_id);

CREATE INDEX ix_submission_answers_assignment_question_id
    ON submission_answers (assignment_question_id);
