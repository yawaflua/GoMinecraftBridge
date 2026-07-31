package psql

import (
	"database/sql"
	"fmt"

	sq "github.com/Masterminds/squirrel"
	"github.com/google/uuid"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
)

var statement = sq.StatementBuilder.PlaceholderFormat(sq.Dollar)

type scanner interface {
	Scan(dest ...any) error
}

var userColumns = []string{
	"id",
	"nickname",
	"minecraft_uuid",
	"email",
	"password_hash",
	"avatar_url",
	"roles",
	"status",
	"ban_reason",
	"banned_until",
	"created_at",
	"updated_at",
	"deleted_at",
}

var projectColumns = []string{
	"id",
	"slug",
	"name",
	"description",
	"git_url",
	"status",
	"status_reason",
	"author_id",
	"created_at",
	"updated_at",
	"deleted_at",
}

var versionColumns = []string{
	"id",
	"version",
	"description",
	"changelog",
	"readme",
	"project_id",
	"tag",
	"size_bytes",
	"sha256",
	"created_at",
	"updated_at",
}

var reviewRequestColumns = []string{
	"id",
	"project_id",
	"submitted_by",
	"reviewed_by",
	"comment",
	"review_comment",
	"request_status",
	"created_at",
	"updated_at",
	"closed_at",
}

var notificationColumns = []string{
	"id",
	"request_id",
	"project_id",
	"user_id",
	"created_by",
	"text",
	"is_system",
	"is_read",
	"created_at",
	"updated_at",
	"read_at",
}

func buildQuery(builder sq.Sqlizer, operation string) (string, []any, error) {
	query, args, err := builder.ToSql()
	if err != nil {
		return "", nil, fmt.Errorf("build SQL query for %s: %w", operation, err)
	}

	return query, args, nil
}

func scanUser(row scanner) (models.User, error) {
	var user models.User
	var status string
	var bannedUntil sql.NullTime
	var deletedAt sql.NullTime

	err := row.Scan(
		&user.Id,
		&user.Nickname,
		&user.MinecraftUUID,
		&user.EMail,
		&user.PasswordHash,
		&user.AvatarURL,
		&user.Roles,
		&status,
		&user.BanReason,
		&bannedUntil,
		&user.CreatedAt,
		&user.UpdatedAt,
		&deletedAt,
	)
	if err != nil {
		return models.User{}, fmt.Errorf("scan user row: %w", err)
	}

	user.Status = models.UserStatus(status)
	if bannedUntil.Valid {
		user.BannedUntil = &bannedUntil.Time
	}
	if deletedAt.Valid {
		user.DeletedAt = deletedAt.Time
	}

	return user, nil
}

func scanProject(row scanner) (models.Project, error) {
	var project models.Project
	var status string
	var deletedAt sql.NullTime

	err := row.Scan(
		&project.Id,
		&project.Slug,
		&project.Name,
		&project.Description,
		&project.GitURL,
		&status,
		&project.StatusReason,
		&project.AuthorId,
		&project.CreatedAt,
		&project.UpdatedAt,
		&deletedAt,
	)
	if err != nil {
		return models.Project{}, fmt.Errorf("scan project row: %w", err)
	}

	project.Status = models.ProjectStatus(status)
	if deletedAt.Valid {
		project.DeletedAt = deletedAt.Time
	}

	return project, nil
}

func scanVersion(row scanner) (models.Version, error) {
	var version models.Version
	var tag string

	err := row.Scan(
		&version.Id,
		&version.Version,
		&version.Description,
		&version.Changelog,
		&version.Readme,
		&version.ProjectId,
		&tag,
		&version.SizeBytes,
		&version.SHA256,
		&version.CreatedAt,
		&version.UpdatedAt,
	)
	if err != nil {
		return models.Version{}, fmt.Errorf("scan project version row: %w", err)
	}

	version.Tag = models.VersionTag(tag)
	return version, nil
}

func scanVersionArchive(row scanner) (models.Version, error) {
	var version models.Version
	var tag string

	err := row.Scan(
		&version.Id,
		&version.Version,
		&version.Description,
		&version.Changelog,
		&version.Readme,
		&version.ProjectId,
		&tag,
		&version.SizeBytes,
		&version.SHA256,
		&version.CreatedAt,
		&version.UpdatedAt,
		&version.Archive,
		&version.ContentType,
	)
	if err != nil {
		return models.Version{}, fmt.Errorf("scan project version archive row: %w", err)
	}

	version.Tag = models.VersionTag(tag)
	return version, nil
}

func scanRequest(row scanner) (models.Request, error) {
	var request models.Request
	var reviewedBy uuid.NullUUID
	var closedAt sql.NullTime

	err := row.Scan(
		&request.Id,
		&request.ProjectId,
		&request.SubmittedBy,
		&reviewedBy,
		&request.Comment,
		&request.ReviewComment,
		&request.RequestStatus,
		&request.CreatedAt,
		&request.UpdatedAt,
		&closedAt,
	)
	if err != nil {
		return models.Request{}, fmt.Errorf("scan project review request row: %w", err)
	}

	if reviewedBy.Valid {
		request.ReviewedBy = reviewedBy.UUID
	}
	if closedAt.Valid {
		request.ClosedAt = closedAt.Time
	}

	return request, nil
}

func qualifiedColumns(table string, columns []string) []string {
	qualified := make([]string, len(columns))
	for index, column := range columns {
		qualified[index] = table + "." + column
	}
	return qualified
}

func scanNotification(row scanner) (models.Notification, error) {
	var notification models.Notification
	var requestID uuid.NullUUID
	var projectID uuid.NullUUID
	var readAt sql.NullTime

	err := row.Scan(
		&notification.Id,
		&requestID,
		&projectID,
		&notification.UserId,
		&notification.CreatedBy,
		&notification.Text,
		&notification.IsSystem,
		&notification.IsRead,
		&notification.CreatedAt,
		&notification.UpdatedAt,
		&readAt,
	)
	if err != nil {
		return models.Notification{}, fmt.Errorf("scan notification row: %w", err)
	}

	if requestID.Valid {
		notification.RequestId = &requestID.UUID
	}
	if projectID.Valid {
		notification.ProjectId = &projectID.UUID
	}
	if readAt.Valid {
		notification.ReadAt = &readAt.Time
	}

	return notification, nil
}
