package adapters

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
)

type DB interface {
	GetPool() *pgxpool.Pool

	CreateUser(ctx context.Context, email, username, passwordHash string, minecraftUUID uuid.UUID) (models.User, error)
	EditUser(ctx context.Context, userId uuid.UUID, email, username, passwordHash string, minecraftUUID uuid.UUID, avatarURL string) (models.User, error)
	GetUserById(ctx context.Context, uuid uuid.UUID) (models.User, error)
	GetUserByEmail(ctx context.Context, email string) (models.User, error)
	GetUserByUsername(ctx context.Context, username string) (models.User, error)
	GetUsersByRole(ctx context.Context, role string) ([]models.User, error)
	ChangeUserRole(ctx context.Context, userId uuid.UUID, roles []string) error
	SetUserBan(ctx context.Context, userId uuid.UUID, banned bool, reason string, bannedUntil *time.Time) (models.User, error)
	CreateRefreshSession(ctx context.Context, session models.RefreshSession) error
	GetRefreshSession(ctx context.Context, sessionId uuid.UUID) (models.RefreshSession, error)
	RotateRefreshSession(ctx context.Context, oldSessionId uuid.UUID, session models.RefreshSession) error
	RevokeRefreshSession(ctx context.Context, sessionId uuid.UUID) error

	CreateProject(ctx context.Context, authorId uuid.UUID, name, description, slug string) (models.Project, error)
	CheckSlugAvailability(ctx context.Context, slug string, excludeProjectId *uuid.UUID) (bool, error)
	EditProject(ctx context.Context, projectId uuid.UUID, name, description, slug string) (models.Project, error)
	GetProjectById(ctx context.Context, uuid uuid.UUID) (models.Project, error)
	GetProjectBySlug(ctx context.Context, slug string) (models.Project, error)
	GetUserProjects(ctx context.Context, uuid uuid.UUID) ([]models.Project, error)
	SearchProjects(ctx context.Context, query string, limit int, minSimilarity float32) ([]models.Project, error)
	SetProjectStatus(ctx context.Context, projectId uuid.UUID, status models.ProjectStatus, reason string) (models.Project, error)

	GetProjectVersions(ctx context.Context, projectId uuid.UUID) ([]models.Version, error)
	GetProjectVersionsWithMeta(ctx context.Context, projectId uuid.UUID) ([]models.Version, error)
	GetProjectVersionById(ctx context.Context, projectId, versionId uuid.UUID) (models.Version, error)
	GetProjectVersionByReference(ctx context.Context, projectId uuid.UUID, reference string) (models.Version, error)
	GetProjectByVersionId(ctx context.Context, versionId uuid.UUID) (models.Project, error)
	UploadNewVersion(ctx context.Context, projectId uuid.UUID, version models.Version) (models.Version, error)
	EditMetadata(ctx context.Context, projectId, versionId uuid.UUID, metadata models.VersionMeta) (models.Version, error)
	DeleteProjectVersion(ctx context.Context, projectId, versionId uuid.UUID) error

	SubmitProject(ctx context.Context, projectId, submittedBy uuid.UUID, comment string) (models.Request, error)
	GetProjectReviewRequest(ctx context.Context, requestId uuid.UUID) (models.Request, error)
	GetProjectReviewRequests(ctx context.Context, status *models.RequestStatus) ([]models.Request, error)
	// ReviewProject must only be called by a moderator/admin after authorization.
	ReviewProject(ctx context.Context, requestId, reviewedBy uuid.UUID, status models.RequestStatus, comment string) (models.Request, error)

	GetNotifications(ctx context.Context, userId uuid.UUID) ([]models.Notification, error)
	ReadNotifications(ctx context.Context, userId uuid.UUID, notificationIds []uuid.UUID) ([]models.Notification, error)
	// Notification creation must only be called by a moderator/admin after authorization.
	CreateGlobalNotification(ctx context.Context, createdBy uuid.UUID, text string) ([]models.Notification, error)
	CreateProjectNotification(ctx context.Context, createdBy, projectId uuid.UUID, requestId *uuid.UUID, text string) (models.Notification, error)
	CreateUserNotification(ctx context.Context, createdBy, userId uuid.UUID, projectId, requestId *uuid.UUID, text string) (models.Notification, error)
}
