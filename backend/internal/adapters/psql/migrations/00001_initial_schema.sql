-- +goose Up
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    nickname TEXT NOT NULL,
    minecraft_uuid UUID NOT NULL,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    avatar_url TEXT NOT NULL DEFAULT '',
    roles TEXT[] NOT NULL DEFAULT ARRAY['user']::TEXT[],
    status TEXT NOT NULL DEFAULT 'active',
    ban_reason TEXT NOT NULL DEFAULT '',
    banned_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX users_email_unique
    ON users (LOWER(email))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX users_nickname_unique
    ON users (LOWER(nickname))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX users_minecraft_uuid_unique
    ON users (minecraft_uuid)
    WHERE deleted_at IS NULL;

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    slug TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'draft',
    status_reason TEXT NOT NULL DEFAULT '',
    author_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX projects_slug_unique
    ON projects (LOWER(slug))
    WHERE deleted_at IS NULL;

CREATE INDEX projects_author_id_idx
    ON projects (author_id)
    WHERE deleted_at IS NULL;

CREATE INDEX projects_search_trgm_idx
    ON projects USING GIN ((name || ' ' || slug || ' ' || description) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE TABLE project_versions (
    id UUID PRIMARY KEY,
    version TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    changelog TEXT NOT NULL DEFAULT '',
    readme TEXT NOT NULL DEFAULT '',
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    tag TEXT NOT NULL DEFAULT 'release',
    size_bytes BIGINT NOT NULL DEFAULT 0,
    sha256 TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, version)
);

CREATE INDEX project_versions_project_id_idx
    ON project_versions (project_id, created_at DESC);

CREATE TABLE version_metadata (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL UNIQUE REFERENCES project_versions(id) ON DELETE CASCADE,
    slug TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    licence TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    authors TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    abi_version TEXT NOT NULL DEFAULT '',
    api_version TEXT NOT NULL DEFAULT '',
    environment TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE project_review_requests (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    submitted_by UUID NOT NULL REFERENCES users(id),
    reviewed_by UUID REFERENCES users(id),
    comment TEXT NOT NULL DEFAULT '',
    review_comment TEXT NOT NULL DEFAULT '',
    request_status SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX project_review_requests_active_unique
    ON project_review_requests (project_id)
    WHERE request_status IN (0, 3);

CREATE INDEX project_review_requests_status_idx
    ON project_review_requests (request_status, created_at);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    request_id UUID REFERENCES project_review_requests(id) ON DELETE SET NULL,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users(id),
    text TEXT NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ
);

CREATE INDEX notifications_user_id_idx
    ON notifications (user_id, created_at DESC);

CREATE INDEX notifications_user_unread_idx
    ON notifications (user_id, created_at DESC)
    WHERE is_read = FALSE;

-- +goose Down
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS project_review_requests;
DROP TABLE IF EXISTS version_metadata;
DROP TABLE IF EXISTS project_versions;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS users;
DROP EXTENSION IF EXISTS pg_trgm;
