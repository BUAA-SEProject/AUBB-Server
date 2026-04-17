CREATE TABLE judge_environment_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id BIGINT NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    profile_code TEXT NOT NULL CHECK (length(profile_code) <= 64),
    normalized_code TEXT NOT NULL CHECK (length(normalized_code) <= 64),
    profile_name TEXT NOT NULL CHECK (length(profile_name) <= 128),
    description TEXT CHECK (description IS NULL OR length(description) <= 500),
    programming_language TEXT NOT NULL CHECK (length(programming_language) <= 32),
    language_version TEXT CHECK (language_version IS NULL OR length(language_version) <= 64),
    working_directory TEXT CHECK (working_directory IS NULL OR length(working_directory) <= 200),
    init_script TEXT,
    compile_command TEXT,
    run_command TEXT,
    environment_variables_json TEXT,
    cpu_rate_limit INTEGER CHECK (cpu_rate_limit IS NULL OR cpu_rate_limit > 0),
    support_files_json TEXT,
    created_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    archived_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_judge_environment_profiles_offering_code
    ON judge_environment_profiles (offering_id, normalized_code);

CREATE INDEX ix_judge_environment_profiles_offering_language_active
    ON judge_environment_profiles (offering_id, programming_language, created_at DESC, id DESC)
    WHERE archived_at IS NULL;
