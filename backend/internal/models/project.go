package models

import (
	"time"

	"github.com/google/uuid"
	"github.com/yawaflua/GoMinecraftBridge/sdk"
)

type Project struct {
	Id           uuid.UUID     `json:"id"`
	Slug         string        `json:"slug"`
	Name         string        `json:"name"`
	Description  string        `json:"description"`
	Status       ProjectStatus `json:"status"`
	StatusReason string        `json:"status_reason,omitempty"`

	AuthorId uuid.UUID `json:"author_id"`
	Author   User      `json:"author,omitempty"`
	Versions []Version `json:"versions"`

	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
	DeletedAt time.Time `json:"deleted_at"`
}

type Version struct {
	Id          uuid.UUID   `json:"id"`
	Version     string      `json:"version"`
	Description string      `json:"description"`
	Changelog   string      `json:"changelog"`
	Readme      string      `json:"readme"`
	ProjectId   uuid.UUID   `json:"project_id"`
	Project     Project     `json:"project,omitempty"`
	Metadata    VersionMeta `json:"metadata,omitempty"`
	Tag         VersionTag  `json:"tag,omitempty"`
	SizeBytes   int64       `json:"size_bytes"`
	SHA256      string      `json:"sha256"`
	Archive     []byte      `json:"-"`
	ContentType string      `json:"content_type"`

	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type VersionMeta struct {
	Id          uuid.UUID `json:"id"`
	Slug        string    `json:"slug"`
	Description string    `json:"description"`
	Licence     []string  `json:"licence"`
	Authors     []string  `json:"authors"`

	ABIVersion string `json:"abi_version"`
	APIVersion string `json:"api_version"`

	Environment sdk.PluginEnvironment `json:"environment"`
}

type VersionTag string

const (
	Release VersionTag = "release"
	Beta    VersionTag = "beta"
	Alpha   VersionTag = "alpha"
)

type ProjectStatus string

const (
	ProjectStatusDraft         ProjectStatus = "draft"
	ProjectStatusPendingReview ProjectStatus = "pending_review"
	ProjectStatusPublished     ProjectStatus = "published"
	ProjectStatusRejected      ProjectStatus = "rejected"
	ProjectStatusBanned        ProjectStatus = "banned"
)
