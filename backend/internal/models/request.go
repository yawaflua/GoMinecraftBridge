package models

import (
	"time"

	"github.com/google/uuid"
)

type Request struct {
	Id            uuid.UUID `json:"id"`
	ProjectId     uuid.UUID `json:"project_id"`
	Project       Project   `json:"project,omitempty"`
	SubmittedBy   uuid.UUID `json:"submitted_by"`
	ReviewedBy    uuid.UUID `json:"reviewed_by,omitempty"`
	Comment       string    `json:"comment,omitempty"`
	ReviewComment string    `json:"review_comment,omitempty"`

	RequestStatus RequestStatus `json:"request_status"`

	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
	ClosedAt  time.Time `json:"closed_at"`
}

type RequestStatus int8

const (
	RequestStatusOpen      RequestStatus = 0
	RequestStatusClosed    RequestStatus = 1
	RequestStatusRejected  RequestStatus = 2
	RequestStatusSubmitted RequestStatus = 3
	RequestStatusCancelled RequestStatus = 4
)

type RequestMessage struct {
	Id        uuid.UUID `json:"id"`
	RequestId uuid.UUID `json:"request_id"`
	UserId    uuid.UUID `json:"user_id"`
	User      User      `json:"user,omitempty"`

	IsSystem         bool `json:"is_system"`
	IsClosingMessage bool `json:"is_closing_message"`

	// List of Attachments is []string with urls
	Attachments []string  `json:"attachments"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type Notification struct {
	Id        uuid.UUID  `json:"id"`
	RequestId *uuid.UUID `json:"request_id,omitempty"`
	ProjectId *uuid.UUID `json:"project_id,omitempty"`
	UserId    uuid.UUID  `json:"user_id"`
	CreatedBy uuid.UUID  `json:"created_by"`
	Text      string     `json:"text"`
	IsSystem  bool       `json:"is_system"`
	IsRead    bool       `json:"is_read"`
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
	ReadAt    *time.Time `json:"read_at,omitempty"`
}
