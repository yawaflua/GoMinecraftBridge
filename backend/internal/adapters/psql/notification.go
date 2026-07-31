package psql

import (
	"context"
	"fmt"
	"strings"

	sq "github.com/Masterminds/squirrel"
	"github.com/google/uuid"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
)

func (psql *psql) GetNotifications(
	ctx context.Context,
	userID uuid.UUID,
) ([]models.Notification, error) {
	builder := statement.
		Select(notificationColumns...).
		From("notifications").
		Where(sq.Eq{"user_id": userID}).
		OrderBy("created_at DESC")

	query, args, err := buildQuery(builder, "get user notifications")
	if err != nil {
		return nil, fmt.Errorf("prepare get notifications for user %s query: %w", userID, err)
	}

	rows, err := psql.GetConnection(ctx).Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query notifications for user %s: %w", userID, err)
	}
	defer rows.Close()

	notifications := make([]models.Notification, 0)
	for rows.Next() {
		notification, scanErr := scanNotification(rows)
		if scanErr != nil {
			return nil, fmt.Errorf("scan notification for user %s: %w", userID, scanErr)
		}
		notifications = append(notifications, notification)
	}
	if err = rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate notifications for user %s: %w", userID, err)
	}

	return notifications, nil
}

func (psql *psql) ReadNotifications(
	ctx context.Context,
	userID uuid.UUID,
	notificationIDs []uuid.UUID,
) ([]models.Notification, error) {
	builder := statement.
		Update("notifications").
		Set("is_read", true).
		Set("read_at", sq.Expr("NOW()")).
		Set("updated_at", sq.Expr("NOW()")).
		Where(sq.Eq{"user_id": userID, "is_read": false})

	if len(notificationIDs) > 0 {
		builder = builder.Where(sq.Eq{"id": notificationIDs})
	}
	builder = builder.Suffix("RETURNING " + strings.Join(notificationColumns, ", "))

	query, args, err := buildQuery(builder, "mark notifications read")
	if err != nil {
		return nil, fmt.Errorf("prepare mark notifications read for user %s query: %w", userID, err)
	}

	rows, err := psql.GetConnection(ctx).Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("mark notifications read for user %s: %w", userID, err)
	}
	defer rows.Close()

	notifications := make([]models.Notification, 0)
	for rows.Next() {
		notification, scanErr := scanNotification(rows)
		if scanErr != nil {
			return nil, fmt.Errorf("scan notification marked read for user %s: %w", userID, scanErr)
		}
		notifications = append(notifications, notification)
	}
	if err = rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate notifications marked read for user %s: %w", userID, err)
	}

	return notifications, nil
}

func (psql *psql) CreateGlobalNotification(
	ctx context.Context,
	createdBy uuid.UUID,
	text string,
) ([]models.Notification, error) {
	usersBuilder := statement.
		Select("id").
		From("users").
		Where(sq.Eq{"status": models.UserStatusActive, "deleted_at": nil}).
		OrderBy("created_at ASC")

	query, args, err := buildQuery(usersBuilder, "get recipients for global notification")
	if err != nil {
		return nil, fmt.Errorf("prepare global notification recipients query: %w", err)
	}

	rows, err := psql.GetConnection(ctx).Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query recipients for global notification: %w", err)
	}

	userIDs := make([]uuid.UUID, 0)
	for rows.Next() {
		var userID uuid.UUID
		if scanErr := rows.Scan(&userID); scanErr != nil {
			rows.Close()
			return nil, fmt.Errorf("scan recipient for global notification: %w", scanErr)
		}
		userIDs = append(userIDs, userID)
	}
	if err = rows.Err(); err != nil {
		rows.Close()
		return nil, fmt.Errorf("iterate recipients for global notification: %w", err)
	}
	rows.Close()

	notifications := make([]models.Notification, 0, len(userIDs))
	err = psql.WithinTransaction(ctx, func(txCtx context.Context) error {
		for _, userID := range userIDs {
			notification, createErr := psql.createUserNotification(
				txCtx,
				createdBy,
				userID,
				nil,
				nil,
				text,
			)
			if createErr != nil {
				return fmt.Errorf("create global notification for user %s: %w", userID, createErr)
			}
			notifications = append(notifications, notification)
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("create global notification transaction: %w", err)
	}

	return notifications, nil
}

func (psql *psql) CreateProjectNotification(
	ctx context.Context,
	createdBy, projectID uuid.UUID,
	requestID *uuid.UUID,
	text string,
) (models.Notification, error) {
	builder := statement.
		Select("author_id").
		From("projects").
		Where(sq.Eq{"id": projectID, "deleted_at": nil})

	query, args, err := buildQuery(builder, "get project notification recipient")
	if err != nil {
		return models.Notification{}, fmt.Errorf(
			"prepare get notification recipient for project %s query: %w",
			projectID,
			err,
		)
	}

	var userID uuid.UUID
	if err = psql.GetConnection(ctx).QueryRow(ctx, query, args...).Scan(&userID); err != nil {
		return models.Notification{}, fmt.Errorf(
			"get notification recipient for project %s: %w",
			projectID,
			err,
		)
	}

	notification, err := psql.createUserNotification(
		ctx,
		createdBy,
		userID,
		&projectID,
		requestID,
		text,
	)
	if err != nil {
		return models.Notification{}, fmt.Errorf("create notification for project %s: %w", projectID, err)
	}

	return notification, nil
}

func (psql *psql) CreateUserNotification(
	ctx context.Context,
	createdBy, userID uuid.UUID,
	projectID, requestID *uuid.UUID,
	text string,
) (models.Notification, error) {
	notification, err := psql.createUserNotification(
		ctx,
		createdBy,
		userID,
		projectID,
		requestID,
		text,
	)
	if err != nil {
		return models.Notification{}, fmt.Errorf("create notification for user %s: %w", userID, err)
	}

	return notification, nil
}

func (psql *psql) createUserNotification(
	ctx context.Context,
	createdBy, userID uuid.UUID,
	projectID, requestID *uuid.UUID,
	text string,
) (models.Notification, error) {
	builder := statement.
		Insert("notifications").
		Columns(
			"id", "request_id", "project_id", "user_id",
			"created_by", "text", "is_system",
		).
		Values(uuid.New(), requestID, projectID, userID, createdBy, text, true).
		Suffix("RETURNING " + strings.Join(notificationColumns, ", "))

	query, args, err := buildQuery(builder, "create user notification")
	if err != nil {
		return models.Notification{}, fmt.Errorf("prepare create notification for user %s query: %w", userID, err)
	}

	notification, err := scanNotification(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Notification{}, fmt.Errorf("insert notification for user %s: %w", userID, err)
	}

	return notification, nil
}
