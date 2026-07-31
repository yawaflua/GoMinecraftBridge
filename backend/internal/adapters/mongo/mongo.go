package mongo

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
)

type Config struct {
	Host     string `env:"MONGO_HOST" env-default:"localhost"`
	Port     string `env:"MONGO_PORT" env-default:"27017"`
	Database string `env:"MONGO_DATABASE" env-default:"gmb"`
}

func (M Config) ConnectionString() string {
	return fmt.Sprintf("mongo://%s:%s/%s", M.Host, M.Port, M.Database)
}

type mongo struct {
}

func (m mongo) SubmitProject(ctx context.Context, projectId, submittedBy uuid.UUID, comment string) models.Request {
	//TODO implement me
	panic("implement me")
}

func (m mongo) CreateUser(ctx context.Context, email, username, password string, minecraftUUID uuid.UUID) models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) EditUser(ctx context.Context, userId uuid.UUID, email, username, password string, minecraftUUID uuid.UUID) models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetUserByEmail(ctx context.Context, email string) models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetUsersByRole(ctx context.Context, role string) []models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) ChangeUserRole(ctx context.Context, userId uuid.UUID, roles []string) error {
	//TODO implement me
	panic("implement me")
}

func (m mongo) SetUserBan(ctx context.Context, userId uuid.UUID, banned bool, reason string, bannedUntil *time.Time) models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) CreateProject(ctx context.Context, authorId uuid.UUID, name, description, slug, gitURL string) models.Project {
	//TODO implement me
	panic("implement me")
}

func (m mongo) CheckSlugAvailability(ctx context.Context, slug string, excludeProjectId *uuid.UUID) bool {
	//TODO implement me
	panic("implement me")
}

func (m mongo) EditProject(ctx context.Context, projectId uuid.UUID, name, description, slug, gitURL string) models.Project {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectBySlug(ctx context.Context, slug string) models.Project {
	//TODO implement me
	panic("implement me")
}

func (m mongo) SearchProjects(ctx context.Context, query string, limit int, minSimilarity float32) []models.Project {
	//TODO implement me
	panic("implement me")
}

func (m mongo) SetProjectStatus(ctx context.Context, projectId uuid.UUID, status models.ProjectStatus, reason string) models.Project {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectVersionById(ctx context.Context, projectId, versionId uuid.UUID) models.Version {
	//TODO implement me
	panic("implement me")
}

func (m mongo) UploadNewVersion(ctx context.Context, projectId uuid.UUID, version models.Version) models.Version {
	//TODO implement me
	panic("implement me")
}

func (m mongo) EditMetadata(ctx context.Context, projectId, versionId uuid.UUID, metadata models.VersionMeta) models.Version {
	//TODO implement me
	panic("implement me")
}

func (m mongo) DeleteProjectVersion(ctx context.Context, projectId, versionId uuid.UUID) error {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectReviewRequest(ctx context.Context, requestId uuid.UUID) models.Request {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectReviewRequests(ctx context.Context, status *models.RequestStatus) []models.Request {
	//TODO implement me
	panic("implement me")
}

func (m mongo) ReviewProject(ctx context.Context, requestId, reviewedBy uuid.UUID, status models.RequestStatus, comment string) models.Request {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetNotifications(ctx context.Context, userId uuid.UUID) []models.Notification {
	//TODO implement me
	panic("implement me")
}

func (m mongo) ReadNotifications(ctx context.Context, userId uuid.UUID, notificationIds []uuid.UUID) []models.Notification {
	//TODO implement me
	panic("implement me")
}

func (m mongo) CreateGlobalNotification(ctx context.Context, createdBy uuid.UUID, text string) []models.Notification {
	//TODO implement me
	panic("implement me")
}

func (m mongo) CreateProjectNotification(ctx context.Context, createdBy, projectId uuid.UUID, requestId *uuid.UUID, text string) models.Notification {
	//TODO implement me
	panic("implement me")
}

func (m mongo) CreateUserNotification(ctx context.Context, createdBy, userId uuid.UUID, projectId, requestId *uuid.UUID, text string) models.Notification {
	//TODO implement me
	panic("implement me")
}

func New(ctx context.Context, cfg Config) (adapters.DB, error) {
	return nil, fmt.Errorf("create MongoDB adapter: %w", errors.New("not implemented"))
}

func (m mongo) GetPool() *pgxpool.Pool {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetUserById(ctx context.Context, uuid uuid.UUID) models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetUserByUsername(ctx context.Context, username string) models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetUserByRole(ctx context.Context, role string) models.User {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectById(ctx context.Context, uuid uuid.UUID) models.Project {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetUserProjects(ctx context.Context, uuid uuid.UUID) []models.Project {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectVersions(ctx context.Context, projectId uuid.UUID) []models.Version {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectVersionsWithMeta(ctx context.Context, projectId uuid.UUID) []models.Version {
	//TODO implement me
	panic("implement me")
}

func (m mongo) GetProjectByVersionId(ctx context.Context, versionId uuid.UUID) models.Project {
	//TODO implement me
	panic("implement me")
}
