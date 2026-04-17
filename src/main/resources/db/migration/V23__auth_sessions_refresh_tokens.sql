CREATE TABLE auth_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id TEXT NOT NULL CHECK (length(session_id) <= 64),
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash TEXT NOT NULL,
    refresh_token_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason TEXT CHECK (revoked_reason IS NULL OR length(revoked_reason) <= 256),
    revoked_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    last_access_issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_refreshed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_auth_sessions_session_id ON auth_sessions (session_id);
CREATE INDEX ix_auth_sessions_user_id_revoked_at ON auth_sessions (user_id, revoked_at);
CREATE INDEX ix_auth_sessions_refresh_token_expires_at ON auth_sessions (refresh_token_expires_at);
