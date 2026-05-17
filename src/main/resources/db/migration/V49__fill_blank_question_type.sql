ALTER TABLE question_bank_questions
    DROP CONSTRAINT question_bank_questions_question_type_check;

ALTER TABLE question_bank_questions
    ADD CONSTRAINT question_bank_questions_question_type_check CHECK (question_type IN (
        'SINGLE_CHOICE',
        'MULTIPLE_CHOICE',
        'FILL_BLANK',
        'SHORT_ANSWER',
        'FILE_UPLOAD',
        'PROGRAMMING'
    ));

ALTER TABLE assignment_questions
    DROP CONSTRAINT assignment_questions_question_type_check;

ALTER TABLE assignment_questions
    ADD CONSTRAINT assignment_questions_question_type_check CHECK (question_type IN (
        'SINGLE_CHOICE',
        'MULTIPLE_CHOICE',
        'FILL_BLANK',
        'SHORT_ANSWER',
        'FILE_UPLOAD',
        'PROGRAMMING'
    ));
