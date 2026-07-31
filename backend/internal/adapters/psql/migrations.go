package psql

import (
	"context"
	"database/sql"
	"embed"
	"errors"
	"fmt"
	"io/fs"

	_ "github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"
)

//go:embed migrations/*.sql
var migrationFiles embed.FS

func runMigrations(ctx context.Context, connectionString string) error {
	database, err := sql.Open("pgx", connectionString)
	if err != nil {
		return fmt.Errorf("open PostgreSQL connection for Goose migrations: %w", err)
	}

	migrations, err := fs.Sub(migrationFiles, "migrations")
	if err != nil {
		closeErr := database.Close()
		filesystemErr := fmt.Errorf("open embedded Goose migrations directory: %w", err)
		if closeErr != nil {
			return errors.Join(filesystemErr, fmt.Errorf("close migration database after filesystem failure: %w", closeErr))
		}
		return filesystemErr
	}

	provider, err := goose.NewProvider(goose.DialectPostgres, database, migrations)
	if err != nil {
		closeErr := database.Close()
		providerErr := fmt.Errorf("create Goose migration provider: %w", err)
		if closeErr != nil {
			return errors.Join(providerErr, fmt.Errorf("close migration database after provider failure: %w", closeErr))
		}
		return providerErr
	}

	if _, err = provider.Up(ctx); err != nil {
		closeErr := database.Close()
		migrationErr := fmt.Errorf("apply Goose migrations: %w", err)
		if closeErr != nil {
			return errors.Join(migrationErr, fmt.Errorf("close migration database after migration failure: %w", closeErr))
		}
		return migrationErr
	}

	if err = database.Close(); err != nil {
		return fmt.Errorf("close migration database: %w", err)
	}

	return nil
}
