-- +goose Up
ALTER TABLE projects
    ADD COLUMN git_url TEXT NOT NULL DEFAULT '';

-- +goose Down
ALTER TABLE projects
    DROP COLUMN IF EXISTS git_url;
