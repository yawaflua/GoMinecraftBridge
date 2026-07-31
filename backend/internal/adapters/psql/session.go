package psql

import (
	"context"
	"database/sql"
	"fmt"

	sq "github.com/Masterminds/squirrel"
	"github.com/google/uuid"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
)

func (psql *psql) CreateRefreshSession(
	ctx context.Context,
	session models.RefreshSession,
) error {
	builder := statement.
		Insert("refresh_sessions").
		Columns("id", "user_id", "token_hash", "expires_at").
		Values(session.Id, session.UserId, session.TokenHash, session.ExpiresAt)

	query, args, err := buildQuery(builder, "create refresh session")
	if err != nil {
		return fmt.Errorf("prepare refresh session %s insert query: %w", session.Id, err)
	}

	if _, err = psql.GetConnection(ctx).Exec(ctx, query, args...); err != nil {
		return fmt.Errorf("create refresh session %s for user %s: %w", session.Id, session.UserId, err)
	}
	return nil
}

func (psql *psql) GetRefreshSession(
	ctx context.Context,
	sessionID uuid.UUID,
) (models.RefreshSession, error) {
	builder := statement.
		Select("id", "user_id", "token_hash", "expires_at", "created_at", "revoked_at").
		From("refresh_sessions").
		Where(sq.Eq{"id": sessionID})

	query, args, err := buildQuery(builder, "get refresh session")
	if err != nil {
		return models.RefreshSession{}, fmt.Errorf("prepare refresh session %s query: %w", sessionID, err)
	}

	var session models.RefreshSession
	var revokedAt sql.NullTime
	err = psql.GetConnection(ctx).QueryRow(ctx, query, args...).Scan(
		&session.Id,
		&session.UserId,
		&session.TokenHash,
		&session.ExpiresAt,
		&session.CreatedAt,
		&revokedAt,
	)
	if err != nil {
		return models.RefreshSession{}, fmt.Errorf("get refresh session %s: %w", sessionID, err)
	}
	if revokedAt.Valid {
		session.RevokedAt = &revokedAt.Time
	}

	return session, nil
}

func (psql *psql) RotateRefreshSession(
	ctx context.Context,
	oldSessionID uuid.UUID,
	session models.RefreshSession,
) error {
	err := psql.WithinTransaction(ctx, func(txCtx context.Context) error {
		revokeBuilder := statement.
			Update("refresh_sessions").
			Set("revoked_at", sq.Expr("NOW()")).
			Where(sq.Eq{"id": oldSessionID, "revoked_at": nil}).
			Where(sq.Expr("expires_at > NOW()"))
		query, args, buildErr := buildQuery(revokeBuilder, "revoke rotated refresh session")
		if buildErr != nil {
			return fmt.Errorf("prepare old refresh session %s revocation query: %w", oldSessionID, buildErr)
		}

		tag, execErr := psql.GetConnection(txCtx).Exec(txCtx, query, args...)
		if execErr != nil {
			return fmt.Errorf("revoke old refresh session %s: %w", oldSessionID, execErr)
		}
		if tag.RowsAffected() == 0 {
			return fmt.Errorf("revoke old refresh session %s: session is missing, expired, or already revoked", oldSessionID)
		}

		if createErr := psql.CreateRefreshSession(txCtx, session); createErr != nil {
			return fmt.Errorf("create rotated refresh session %s: %w", session.Id, createErr)
		}
		return nil
	})
	if err != nil {
		return fmt.Errorf("rotate refresh session %s transaction: %w", oldSessionID, err)
	}
	return nil
}

func (psql *psql) RevokeRefreshSession(ctx context.Context, sessionID uuid.UUID) error {
	builder := statement.
		Update("refresh_sessions").
		Set("revoked_at", sq.Expr("NOW()")).
		Where(sq.Eq{"id": sessionID, "revoked_at": nil})

	query, args, err := buildQuery(builder, "revoke refresh session")
	if err != nil {
		return fmt.Errorf("prepare refresh session %s revocation query: %w", sessionID, err)
	}

	tag, err := psql.GetConnection(ctx).Exec(ctx, query, args...)
	if err != nil {
		return fmt.Errorf("revoke refresh session %s: %w", sessionID, err)
	}
	if tag.RowsAffected() == 0 {
		return fmt.Errorf("revoke refresh session %s: session not found or already revoked", sessionID)
	}
	return nil
}
