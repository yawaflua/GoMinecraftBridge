package models

import (
	"time"

	"github.com/google/uuid"
)

type User struct {
	Id            uuid.UUID `json:"id"`
	Nickname      string    `json:"nickname"`
	MinecraftUUID uuid.UUID `json:"minecraft_uuid"`
	EMail         string    `json:"email"`
	PasswordHash  string    `json:"-"`

	AvatarURL string    `json:"avatar_url"`
	Projects  []Project `json:"projects,omitempty"`
	//Likes     []uuid.UUID `json:"likes,omitempty"`

	Roles       []string   `json:"roles"`
	Status      UserStatus `json:"status"`
	BanReason   string     `json:"ban_reason,omitempty"`
	BannedUntil *time.Time `json:"banned_until,omitempty"`

	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
	DeletedAt time.Time `json:"deleted_at"`
}

type UserStatus string

const (
	UserStatusActive UserStatus = "active"
	UserStatusBanned UserStatus = "banned"
)

type RefreshSession struct {
	Id        uuid.UUID  `json:"id"`
	UserId    uuid.UUID  `json:"user_id"`
	TokenHash string     `json:"-"`
	ExpiresAt time.Time  `json:"expires_at"`
	CreatedAt time.Time  `json:"created_at"`
	RevokedAt *time.Time `json:"revoked_at,omitempty"`
}
