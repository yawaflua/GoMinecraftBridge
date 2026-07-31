package psql

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
)

type Config struct {
	Host     string `env:"PSQL_HOST" env-default:"localhost"`
	Port     string `env:"PSQL_PORT" env-default:"5432"`
	Username string `env:"PSQL_USERNAME" env-default:"postgres"`
	Password string `env:"PSQL_PASSWORD" env-default:"postgres"`
	Database string `env:"PSQL_DATABASE" env-default:"postgres"`
	SSLMode  string `env:"PSQL_SSLMODE" env-default:"disable"`
}

func (p Config) ConnectionString() string {
	return fmt.Sprintf(
		"postgres://%s:%s@%s:%s/%s?sslmode=%s",
		p.Username,
		p.Password,
		p.Host,
		p.Port,
		p.Database,
		p.SSLMode,
	)
}

type psql struct {
	Pool *pgxpool.Pool
}

var _ adapters.DB = (*psql)(nil)

type txKey struct{}

type connector interface {
	Exec(ctx context.Context, sql string, arguments ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, optionsAndArgs ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, optionsAndArgs ...any) pgx.Row
}

func (psql *psql) GetPool() *pgxpool.Pool {
	return psql.Pool
}

func New(ctx context.Context, cfg Config) (adapters.DB, error) {
	connPool, err := pgxpool.New(ctx, cfg.ConnectionString())
	if err != nil {
		return nil, fmt.Errorf("failed to connect to database: %w", err)
	}

	connection, err := connPool.Acquire(ctx)
	if err != nil {
		connPool.Close()
		return nil, fmt.Errorf("can't acquire connection: %w", err)
	}

	err = connection.Conn().Ping(ctx)
	if err != nil {
		connection.Release()
		connPool.Close()
		return nil, fmt.Errorf("can't ping database: %w", err)
	}
	connection.Release()

	database := &psql{
		Pool: connPool,
	}

	if err = runMigrations(ctx, cfg.ConnectionString()); err != nil {
		connPool.Close()
		return nil, fmt.Errorf("migrate PostgreSQL schema: %w", err)
	}

	return database, nil
}

func (psql *psql) Close() {
	psql.Pool.Close()
}

func (psql *psql) WithinTransaction(
	ctx context.Context,
	tFunc func(ctx context.Context) error,
) error {
	incomingTx := extractTx(ctx)
	if incomingTx != nil {
		err := tFunc(ctx)
		if err != nil {
			return fmt.Errorf("execute nested transaction callback: %w", err)
		}

		return nil
	}

	conn, err := psql.Pool.Acquire(ctx)
	if err != nil {
		return fmt.Errorf("can't acquire connection: %w", err)
	}
	defer conn.Release()

	tx, err := conn.Begin(ctx)
	if err != nil {
		return fmt.Errorf("can't begin transaction: %w", err)
	}

	err = tFunc(injectTx(ctx, tx))
	if err != nil {
		callbackErr := fmt.Errorf("execute transaction callback: %w", err)
		if rollbackErr := tx.Rollback(ctx); rollbackErr != nil && !errors.Is(rollbackErr, pgx.ErrTxClosed) {
			return errors.Join(callbackErr, fmt.Errorf("rollback transaction: %w", rollbackErr))
		}
		return callbackErr
	}

	err = tx.Commit(ctx)
	if err != nil {
		commitErr := fmt.Errorf("commit transaction: %w", err)
		if rollbackErr := tx.Rollback(ctx); rollbackErr != nil && !errors.Is(rollbackErr, pgx.ErrTxClosed) {
			return errors.Join(commitErr, fmt.Errorf("rollback transaction after commit failure: %w", rollbackErr))
		}
		return commitErr
	}

	return nil
}

func (psql *psql) GetConnection(ctx context.Context) connector {
	conn := extractTx(ctx)
	if conn == nil {
		conn = psql.Pool
	}

	return conn
}

func injectTx(ctx context.Context, tx pgx.Tx) context.Context {
	return context.WithValue(ctx, txKey{}, tx)
}

func extractTx(ctx context.Context) connector {
	if tx, ok := ctx.Value(txKey{}).(pgx.Tx); ok {
		return tx
	}

	return nil
}
