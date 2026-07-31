package psql

import (
	"context"
	"fmt"
	"strings"
	"time"

	sq "github.com/Masterminds/squirrel"
	"github.com/google/uuid"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
)

func (psql *psql) CreateUser(
	ctx context.Context,
	email, username, passwordHash string,
	minecraftUUID uuid.UUID,
) (models.User, error) {
	builder := statement.
		Insert("users").
		Columns("id", "email", "nickname", "password_hash", "minecraft_uuid", "roles", "status").
		Values(uuid.New(), email, username, passwordHash, minecraftUUID, []string{"user"}, models.UserStatusActive).
		Suffix("RETURNING " + strings.Join(userColumns, ", "))

	query, args, err := buildQuery(builder, "create user")
	if err != nil {
		return models.User{}, fmt.Errorf("prepare create user query: %w", err)
	}

	user, err := scanUser(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.User{}, fmt.Errorf("create user %q: %w", username, err)
	}

	return user, nil
}

func (psql *psql) EditUser(
	ctx context.Context,
	userID uuid.UUID,
	email, username, passwordHash string,
	minecraftUUID uuid.UUID,
	avatarURL string,
) (models.User, error) {
	builder := statement.
		Update("users").
		Set("email", email).
		Set("nickname", username).
		Set("minecraft_uuid", minecraftUUID).
		Set("avatar_url", avatarURL).
		Set("updated_at", sq.Expr("NOW()")).
		Where(sq.Eq{"id": userID, "deleted_at": nil})

	if passwordHash != "" {
		builder = builder.Set("password_hash", passwordHash)
	}

	builder = builder.Suffix("RETURNING " + strings.Join(userColumns, ", "))
	query, args, err := buildQuery(builder, "edit user")
	if err != nil {
		return models.User{}, fmt.Errorf("prepare edit user %s query: %w", userID, err)
	}

	user, err := scanUser(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.User{}, fmt.Errorf("edit user %s: %w", userID, err)
	}

	return user, nil
}

func (psql *psql) GetUserById(ctx context.Context, userID uuid.UUID) (models.User, error) {
	builder := statement.
		Select(userColumns...).
		From("users").
		Where(sq.Eq{"id": userID, "deleted_at": nil})

	return psql.getUser(ctx, builder, fmt.Sprintf("get user by id %s", userID))
}

func (psql *psql) GetUserByEmail(ctx context.Context, email string) (models.User, error) {
	builder := statement.
		Select(userColumns...).
		From("users").
		Where(sq.Expr("LOWER(email) = LOWER(?)", email)).
		Where(sq.Eq{"deleted_at": nil})

	return psql.getUser(ctx, builder, fmt.Sprintf("get user by email %q", email))
}

func (psql *psql) GetUserByUsername(ctx context.Context, username string) (models.User, error) {
	builder := statement.
		Select(userColumns...).
		From("users").
		Where(sq.Expr("LOWER(nickname) = LOWER(?)", username)).
		Where(sq.Eq{"deleted_at": nil})

	return psql.getUser(ctx, builder, fmt.Sprintf("get user by username %q", username))
}

func (psql *psql) GetUsersByRole(ctx context.Context, role string) ([]models.User, error) {
	builder := statement.
		Select(userColumns...).
		From("users").
		Where(sq.Expr("? = ANY(roles)", role)).
		Where(sq.Eq{"deleted_at": nil}).
		OrderBy("created_at ASC")

	query, args, err := buildQuery(builder, "get users by role")
	if err != nil {
		return nil, fmt.Errorf("prepare get users by role %q query: %w", role, err)
	}

	rows, err := psql.GetConnection(ctx).Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query users with role %q: %w", role, err)
	}
	defer rows.Close()

	users := make([]models.User, 0)
	for rows.Next() {
		user, scanErr := scanUser(rows)
		if scanErr != nil {
			return nil, fmt.Errorf("scan user with role %q: %w", role, scanErr)
		}
		users = append(users, user)
	}
	if err = rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate users with role %q: %w", role, err)
	}

	return users, nil
}

func (psql *psql) ChangeUserRole(ctx context.Context, userID uuid.UUID, roles []string) error {
	builder := statement.
		Update("users").
		Set("roles", roles).
		Set("updated_at", sq.Expr("NOW()")).
		Where(sq.Eq{"id": userID, "deleted_at": nil})

	query, args, err := buildQuery(builder, "change user roles")
	if err != nil {
		return fmt.Errorf("prepare change roles for user %s query: %w", userID, err)
	}

	tag, err := psql.GetConnection(ctx).Exec(ctx, query, args...)
	if err != nil {
		return fmt.Errorf("change roles for user %s: %w", userID, err)
	}
	if tag.RowsAffected() == 0 {
		return fmt.Errorf("change roles for user %s: user not found", userID)
	}

	return nil
}

func (psql *psql) SetUserBan(
	ctx context.Context,
	userID uuid.UUID,
	banned bool,
	reason string,
	bannedUntil *time.Time,
) (models.User, error) {
	status := models.UserStatusActive
	if banned {
		status = models.UserStatusBanned
	} else {
		reason = ""
		bannedUntil = nil
	}

	builder := statement.
		Update("users").
		Set("status", status).
		Set("ban_reason", reason).
		Set("banned_until", bannedUntil).
		Set("updated_at", sq.Expr("NOW()")).
		Where(sq.Eq{"id": userID, "deleted_at": nil}).
		Suffix("RETURNING " + strings.Join(userColumns, ", "))

	query, args, err := buildQuery(builder, "set user ban")
	if err != nil {
		return models.User{}, fmt.Errorf("prepare set ban for user %s query: %w", userID, err)
	}

	user, err := scanUser(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.User{}, fmt.Errorf("set ban=%t for user %s: %w", banned, userID, err)
	}

	return user, nil
}

func (psql *psql) getUser(
	ctx context.Context,
	builder sq.SelectBuilder,
	operation string,
) (models.User, error) {
	query, args, err := buildQuery(builder, operation)
	if err != nil {
		return models.User{}, fmt.Errorf("prepare %s query: %w", operation, err)
	}

	user, err := scanUser(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.User{}, fmt.Errorf("%s: %w", operation, err)
	}

	return user, nil
}
