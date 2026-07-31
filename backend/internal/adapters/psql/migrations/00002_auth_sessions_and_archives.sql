-- +goose Up
ALTER TABLE project_versions
    ADD COLUMN archive BYTEA NOT NULL DEFAULT '\x'::BYTEA,
    ADD COLUMN archive_content_type TEXT NOT NULL DEFAULT 'application/octet-stream';

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX refresh_sessions_user_id_idx
    ON refresh_sessions (user_id, expires_at DESC);

CREATE INDEX refresh_sessions_active_idx
    ON refresh_sessions (id, expires_at)
    WHERE revoked_at IS NULL;

-- +goose Down
DROP TABLE IF EXISTS refresh_sessions;

ALTER TABLE project_versions
    DROP COLUMN IF EXISTS archive_content_type,
    DROP COLUMN IF EXISTS archive;
