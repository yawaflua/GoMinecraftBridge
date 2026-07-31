package psql

import (
	"context"
	"errors"
	"fmt"
	"strings"

	sq "github.com/Masterminds/squirrel"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"github.com/yawaflua/GoMinecraftBridge/sdk"
)

func (psql *psql) CreateProject(
	ctx context.Context,
	authorID uuid.UUID,
	name, description, slug, gitURL string,
) (models.Project, error) {
	builder := statement.
		Insert("projects").
		Columns("id", "author_id", "name", "description", "slug", "git_url", "status").
		Values(uuid.New(), authorID, name, description, slug, gitURL, models.ProjectStatusDraft).
		Suffix("RETURNING " + strings.Join(projectColumns, ", "))

	query, args, err := buildQuery(builder, "create project")
	if err != nil {
		return models.Project{}, fmt.Errorf("prepare create project %q query: %w", name, err)
	}

	project, err := scanProject(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Project{}, fmt.Errorf("create project %q with slug %q: %w", name, slug, err)
	}

	return project, nil
}

func (psql *psql) CheckSlugAvailability(
	ctx context.Context,
	slug string,
	excludeProjectID *uuid.UUID,
) (bool, error) {
	builder := statement.
		Select("COUNT(*)").
		From("projects").
		Where(sq.Expr("LOWER(slug) = LOWER(?)", slug)).
		Where(sq.Eq{"deleted_at": nil})

	if excludeProjectID != nil {
		builder = builder.Where(sq.NotEq{"id": *excludeProjectID})
	}

	query, args, err := buildQuery(builder, "check project slug availability")
	if err != nil {
		return false, fmt.Errorf("prepare slug %q availability query: %w", slug, err)
	}

	var count int
	if err = psql.GetConnection(ctx).QueryRow(ctx, query, args...).Scan(&count); err != nil {
		return false, fmt.Errorf("check availability of project slug %q: %w", slug, err)
	}

	return count == 0, nil
}

func (psql *psql) EditProject(
	ctx context.Context,
	projectID uuid.UUID,
	name, description, slug, gitURL string,
) (models.Project, error) {
	builder := statement.
		Update("projects").
		Set("name", name).
		Set("description", description).
		Set("slug", slug).
		Set("git_url", gitURL).
		Set("updated_at", sq.Expr("NOW()")).
		Where(sq.Eq{"id": projectID, "deleted_at": nil}).
		Suffix("RETURNING " + strings.Join(projectColumns, ", "))

	query, args, err := buildQuery(builder, "edit project")
	if err != nil {
		return models.Project{}, fmt.Errorf("prepare edit project %s query: %w", projectID, err)
	}

	project, err := scanProject(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Project{}, fmt.Errorf("edit project %s: %w", projectID, err)
	}

	return project, nil
}

func (psql *psql) GetProjectById(ctx context.Context, projectID uuid.UUID) (models.Project, error) {
	builder := statement.
		Select(projectColumns...).
		From("projects").
		Where(sq.Eq{"id": projectID, "deleted_at": nil})

	return psql.getProject(ctx, builder, fmt.Sprintf("get project by id %s", projectID))
}

func (psql *psql) GetProjectBySlug(ctx context.Context, slug string) (models.Project, error) {
	builder := statement.
		Select(projectColumns...).
		From("projects").
		Where(sq.Expr("LOWER(slug) = LOWER(?)", slug)).
		Where(sq.Eq{"deleted_at": nil})

	return psql.getProject(ctx, builder, fmt.Sprintf("get project by slug %q", slug))
}

func (psql *psql) GetUserProjects(ctx context.Context, userID uuid.UUID) ([]models.Project, error) {
	builder := statement.
		Select(projectColumns...).
		From("projects").
		Where(sq.Eq{"author_id": userID, "deleted_at": nil}).
		OrderBy("created_at DESC")

	return psql.getProjects(ctx, builder, fmt.Sprintf("get projects of user %s", userID))
}

func (psql *psql) SearchProjects(
	ctx context.Context,
	search string,
	limit int,
	minSimilarity float32,
) ([]models.Project, error) {
	if limit <= 0 {
		limit = 50
	}
	if limit > 200 {
		limit = 200
	}
	if minSimilarity < 0 {
		minSimilarity = 0
	}
	if minSimilarity > 1 {
		minSimilarity = 1
	}

	searchDocument := "name || ' ' || slug || ' ' || description"
	builder := statement.
		Select(projectColumns...).
		From("projects").
		Where(sq.Eq{"status": models.ProjectStatusPublished, "deleted_at": nil}).
		Where(sq.Expr("similarity("+searchDocument+", ?) >= ?", search, minSimilarity)).
		OrderByClause("similarity("+searchDocument+", ?) DESC", search).
		Limit(uint64(limit))

	return psql.getProjects(ctx, builder, fmt.Sprintf("search published projects for %q", search))
}

func (psql *psql) SetProjectStatus(
	ctx context.Context,
	projectID uuid.UUID,
	status models.ProjectStatus,
	reason string,
) (models.Project, error) {
	builder := statement.
		Update("projects").
		Set("status", status).
		Set("status_reason", reason).
		Set("updated_at", sq.Expr("NOW()")).
		Where(sq.Eq{"id": projectID, "deleted_at": nil}).
		Suffix("RETURNING " + strings.Join(projectColumns, ", "))

	query, args, err := buildQuery(builder, "set project status")
	if err != nil {
		return models.Project{}, fmt.Errorf("prepare set status for project %s query: %w", projectID, err)
	}

	project, err := scanProject(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Project{}, fmt.Errorf("set status %q for project %s: %w", status, projectID, err)
	}

	return project, nil
}

func (psql *psql) GetProjectVersions(
	ctx context.Context,
	projectID uuid.UUID,
) ([]models.Version, error) {
	builder := statement.
		Select(versionColumns...).
		From("project_versions").
		Where(sq.Eq{"project_id": projectID}).
		OrderBy("created_at DESC")

	return psql.getVersions(ctx, builder, fmt.Sprintf("get versions of project %s", projectID))
}

func (psql *psql) GetProjectVersionsWithMeta(
	ctx context.Context,
	projectID uuid.UUID,
) ([]models.Version, error) {
	versions, err := psql.GetProjectVersions(ctx, projectID)
	if err != nil {
		return nil, fmt.Errorf("get project %s versions before loading metadata: %w", projectID, err)
	}

	for index := range versions {
		metadata, metadataErr := psql.getVersionMetadata(ctx, versions[index].Id)
		if metadataErr != nil {
			return nil, fmt.Errorf("get metadata of version %s in project %s: %w", versions[index].Id, projectID, metadataErr)
		}
		versions[index].Metadata = metadata
	}

	return versions, nil
}

func (psql *psql) GetProjectVersionById(
	ctx context.Context,
	projectID, versionID uuid.UUID,
) (models.Version, error) {
	builder := statement.
		Select(versionColumns...).
		From("project_versions").
		Where(sq.Eq{"project_id": projectID, "id": versionID})

	query, args, err := buildQuery(builder, "get project version by id")
	if err != nil {
		return models.Version{}, fmt.Errorf("prepare get version %s of project %s query: %w", versionID, projectID, err)
	}

	version, err := scanVersion(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Version{}, fmt.Errorf("get version %s of project %s: %w", versionID, projectID, err)
	}

	version.Metadata, err = psql.getVersionMetadata(ctx, versionID)
	if err != nil {
		return models.Version{}, fmt.Errorf("get metadata for version %s of project %s: %w", versionID, projectID, err)
	}

	return version, nil
}

func (psql *psql) GetProjectVersionByReference(
	ctx context.Context,
	projectID uuid.UUID,
	reference string,
) (models.Version, error) {
	columns := append(append([]string{}, versionColumns...), "archive", "archive_content_type")
	builder := statement.
		Select(columns...).
		From("project_versions").
		Where(sq.Eq{"project_id": projectID})

	switch {
	case reference == "" || reference == "latest":
		builder = builder.OrderBy("created_at DESC").Limit(1)
	default:
		if versionID, parseErr := uuid.Parse(reference); parseErr == nil {
			builder = builder.Where(sq.Eq{"id": versionID})
		} else {
			builder = builder.Where(sq.Eq{"version": reference})
		}
	}

	query, args, err := buildQuery(builder, "get project version by reference")
	if err != nil {
		return models.Version{}, fmt.Errorf(
			"prepare version reference %q of project %s query: %w",
			reference,
			projectID,
			err,
		)
	}

	version, err := scanVersionArchive(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Version{}, fmt.Errorf(
			"get version reference %q of project %s: %w",
			reference,
			projectID,
			err,
		)
	}
	version.Metadata, err = psql.getVersionMetadata(ctx, version.Id)
	if err != nil {
		return models.Version{}, fmt.Errorf("get metadata for version %s: %w", version.Id, err)
	}
	return version, nil
}

func (psql *psql) GetProjectByVersionId(
	ctx context.Context,
	versionID uuid.UUID,
) (models.Project, error) {
	builder := statement.
		Select(qualifiedColumns("p", projectColumns)...).
		From("projects p").
		Join("project_versions v ON v.project_id = p.id").
		Where(sq.Eq{"v.id": versionID, "p.deleted_at": nil})

	return psql.getProject(ctx, builder, fmt.Sprintf("get project by version id %s", versionID))
}

func (psql *psql) UploadNewVersion(
	ctx context.Context,
	projectID uuid.UUID,
	version models.Version,
) (models.Version, error) {
	if version.Id == uuid.Nil {
		version.Id = uuid.New()
	}
	if version.Metadata.Id == uuid.Nil {
		version.Metadata.Id = uuid.New()
	}
	version.ProjectId = projectID

	var created models.Version
	err := psql.WithinTransaction(ctx, func(txCtx context.Context) error {
		projectBuilder := statement.
			Update("projects").
			Set("updated_at", sq.Expr("NOW()")).
			Where(sq.Eq{"id": projectID, "deleted_at": nil})
		projectQuery, projectArgs, buildErr := buildQuery(projectBuilder, "touch project before version upload")
		if buildErr != nil {
			return fmt.Errorf("prepare update timestamp of project %s query: %w", projectID, buildErr)
		}
		projectTag, execErr := psql.GetConnection(txCtx).Exec(txCtx, projectQuery, projectArgs...)
		if execErr != nil {
			return fmt.Errorf("update timestamp of project %s before version upload: %w", projectID, execErr)
		}
		if projectTag.RowsAffected() == 0 {
			return fmt.Errorf("upload version %q: project %s not found", version.Version, projectID)
		}

		builder := statement.
			Insert("project_versions").
			Columns(
				"id", "version", "description", "changelog", "readme",
				"project_id", "tag", "size_bytes", "sha256", "archive", "archive_content_type",
			).
			Values(
				version.Id, version.Version, version.Description, version.Changelog, version.Readme,
				projectID, version.Tag, version.SizeBytes, version.SHA256, version.Archive, version.ContentType,
			).
			Suffix("RETURNING " + strings.Join(versionColumns, ", "))

		query, args, buildErr := buildQuery(builder, "upload new project version")
		if buildErr != nil {
			return fmt.Errorf("prepare upload version %q for project %s query: %w", version.Version, projectID, buildErr)
		}

		createdVersion, scanErr := scanVersion(psql.GetConnection(txCtx).QueryRow(txCtx, query, args...))
		if scanErr != nil {
			return fmt.Errorf("insert version %q for project %s: %w", version.Version, projectID, scanErr)
		}

		metadata, metadataErr := psql.insertVersionMetadata(txCtx, createdVersion.Id, version.Metadata)
		if metadataErr != nil {
			return fmt.Errorf("insert metadata for version %q of project %s: %w", version.Version, projectID, metadataErr)
		}

		createdVersion.Metadata = metadata
		created = createdVersion
		return nil
	})
	if err != nil {
		return models.Version{}, fmt.Errorf("upload version %q for project %s transaction: %w", version.Version, projectID, err)
	}

	return created, nil
}

func (psql *psql) EditMetadata(
	ctx context.Context,
	projectID, versionID uuid.UUID,
	metadata models.VersionMeta,
) (models.Version, error) {
	if metadata.Id == uuid.Nil {
		metadata.Id = uuid.New()
	}

	var version models.Version
	err := psql.WithinTransaction(ctx, func(txCtx context.Context) error {
		var exists bool
		existsBuilder := statement.
			Select().
			Column(
				"EXISTS(SELECT 1 FROM project_versions WHERE id = ? AND project_id = ?)",
				versionID,
				projectID,
			)
		existsQuery, existsArgs, buildErr := buildQuery(existsBuilder, "verify project version before metadata edit")
		if buildErr != nil {
			return fmt.Errorf("prepare verify version %s of project %s query: %w", versionID, projectID, buildErr)
		}
		if scanErr := psql.GetConnection(txCtx).QueryRow(txCtx, existsQuery, existsArgs...).Scan(&exists); scanErr != nil {
			return fmt.Errorf("verify version %s belongs to project %s: %w", versionID, projectID, scanErr)
		}
		if !exists {
			return fmt.Errorf("edit metadata: version %s does not belong to project %s", versionID, projectID)
		}

		builder := statement.
			Insert("version_metadata").
			Columns(
				"id", "version_id", "slug", "description", "licence",
				"authors", "abi_version", "api_version", "environment",
			).
			Values(
				metadata.Id, versionID, metadata.Slug, metadata.Description, metadata.Licence,
				metadata.Authors, metadata.ABIVersion, metadata.APIVersion, metadata.Environment,
			).
			Suffix(`
				ON CONFLICT (version_id) DO UPDATE SET
					slug = EXCLUDED.slug,
					description = EXCLUDED.description,
					licence = EXCLUDED.licence,
					authors = EXCLUDED.authors,
					abi_version = EXCLUDED.abi_version,
					api_version = EXCLUDED.api_version,
					environment = EXCLUDED.environment,
					updated_at = NOW()
			`)

		query, args, buildErr := buildQuery(builder, "edit project version metadata")
		if buildErr != nil {
			return fmt.Errorf("prepare edit metadata for version %s query: %w", versionID, buildErr)
		}

		tag, execErr := psql.GetConnection(txCtx).Exec(txCtx, query, args...)
		if execErr != nil {
			return fmt.Errorf("edit metadata for version %s of project %s: %w", versionID, projectID, execErr)
		}
		if tag.RowsAffected() == 0 {
			return fmt.Errorf("edit metadata for version %s of project %s: no metadata row changed", versionID, projectID)
		}

		updated, getErr := psql.GetProjectVersionById(txCtx, projectID, versionID)
		if getErr != nil {
			return fmt.Errorf("get version %s after metadata edit: %w", versionID, getErr)
		}
		version = updated
		return nil
	})
	if err != nil {
		return models.Version{}, fmt.Errorf("edit metadata for version %s transaction: %w", versionID, err)
	}

	return version, nil
}

func (psql *psql) DeleteProjectVersion(
	ctx context.Context,
	projectID, versionID uuid.UUID,
) error {
	err := psql.WithinTransaction(ctx, func(txCtx context.Context) error {
		builder := statement.
			Delete("project_versions").
			Where(sq.Eq{"id": versionID, "project_id": projectID})

		query, args, buildErr := buildQuery(builder, "delete project version")
		if buildErr != nil {
			return fmt.Errorf("prepare delete version %s of project %s query: %w", versionID, projectID, buildErr)
		}

		tag, execErr := psql.GetConnection(txCtx).Exec(txCtx, query, args...)
		if execErr != nil {
			return fmt.Errorf("delete version %s of project %s: %w", versionID, projectID, execErr)
		}
		if tag.RowsAffected() == 0 {
			return fmt.Errorf("delete version %s of project %s: version not found", versionID, projectID)
		}

		projectBuilder := statement.
			Update("projects").
			Set("updated_at", sq.Expr("NOW()")).
			Where(sq.Eq{"id": projectID, "deleted_at": nil})
		projectQuery, projectArgs, buildErr := buildQuery(projectBuilder, "touch project after version deletion")
		if buildErr != nil {
			return fmt.Errorf("prepare update project %s after version deletion query: %w", projectID, buildErr)
		}
		projectTag, execErr := psql.GetConnection(txCtx).Exec(txCtx, projectQuery, projectArgs...)
		if execErr != nil {
			return fmt.Errorf("update project %s after deleting version %s: %w", projectID, versionID, execErr)
		}
		if projectTag.RowsAffected() == 0 {
			return fmt.Errorf("update project %s after deleting version %s: project not found", projectID, versionID)
		}

		return nil
	})
	if err != nil {
		return fmt.Errorf("delete version %s of project %s transaction: %w", versionID, projectID, err)
	}

	return nil
}

func (psql *psql) SubmitProject(
	ctx context.Context,
	projectID, submittedBy uuid.UUID,
	comment string,
) (models.Request, error) {
	var request models.Request
	err := psql.WithinTransaction(ctx, func(txCtx context.Context) error {
		var versionCount int
		countBuilder := statement.
			Select("COUNT(*)").
			From("project_versions").
			Where(sq.Eq{"project_id": projectID})
		countQuery, countArgs, buildErr := buildQuery(countBuilder, "count project versions before submission")
		if buildErr != nil {
			return fmt.Errorf("prepare count versions for project %s query: %w", projectID, buildErr)
		}
		if scanErr := psql.GetConnection(txCtx).QueryRow(txCtx, countQuery, countArgs...).Scan(&versionCount); scanErr != nil {
			return fmt.Errorf("count versions for project %s: %w", projectID, scanErr)
		}
		if versionCount == 0 {
			return fmt.Errorf("submit project %s: project has no versions", projectID)
		}

		builder := statement.
			Insert("project_review_requests").
			Columns("id", "project_id", "submitted_by", "comment", "request_status").
			Values(uuid.New(), projectID, submittedBy, comment, models.RequestStatusSubmitted).
			Suffix("RETURNING " + strings.Join(reviewRequestColumns, ", "))
		query, args, buildErr := buildQuery(builder, "submit project for review")
		if buildErr != nil {
			return fmt.Errorf("prepare submit project %s query: %w", projectID, buildErr)
		}

		created, scanErr := scanRequest(psql.GetConnection(txCtx).QueryRow(txCtx, query, args...))
		if scanErr != nil {
			return fmt.Errorf("create review request for project %s: %w", projectID, scanErr)
		}

		updateBuilder := statement.
			Update("projects").
			Set("status", models.ProjectStatusPendingReview).
			Set("status_reason", "").
			Set("updated_at", sq.Expr("NOW()")).
			Where(sq.Eq{"id": projectID, "author_id": submittedBy, "deleted_at": nil})
		updateQuery, updateArgs, buildErr := buildQuery(updateBuilder, "mark submitted project pending review")
		if buildErr != nil {
			return fmt.Errorf("prepare mark project %s pending review query: %w", projectID, buildErr)
		}

		tag, execErr := psql.GetConnection(txCtx).Exec(txCtx, updateQuery, updateArgs...)
		if execErr != nil {
			return fmt.Errorf("mark project %s pending review: %w", projectID, execErr)
		}
		if tag.RowsAffected() == 0 {
			return fmt.Errorf("mark project %s pending review: project not found or user %s is not its owner", projectID, submittedBy)
		}

		request = created
		return nil
	})
	if err != nil {
		return models.Request{}, fmt.Errorf("submit project %s transaction: %w", projectID, err)
	}

	return request, nil
}

func (psql *psql) GetProjectReviewRequest(
	ctx context.Context,
	requestID uuid.UUID,
) (models.Request, error) {
	builder := statement.
		Select(reviewRequestColumns...).
		From("project_review_requests").
		Where(sq.Eq{"id": requestID})

	query, args, err := buildQuery(builder, "get project review request")
	if err != nil {
		return models.Request{}, fmt.Errorf("prepare get review request %s query: %w", requestID, err)
	}

	request, err := scanRequest(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Request{}, fmt.Errorf("get project review request %s: %w", requestID, err)
	}

	return request, nil
}

func (psql *psql) GetProjectReviewRequests(
	ctx context.Context,
	status *models.RequestStatus,
) ([]models.Request, error) {
	builder := statement.
		Select(reviewRequestColumns...).
		From("project_review_requests").
		OrderBy("created_at ASC")
	if status != nil {
		builder = builder.Where(sq.Eq{"request_status": *status})
	}

	query, args, err := buildQuery(builder, "get project review requests")
	if err != nil {
		return nil, fmt.Errorf("prepare get project review requests query: %w", err)
	}

	rows, err := psql.GetConnection(ctx).Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query project review requests: %w", err)
	}
	defer rows.Close()

	requests := make([]models.Request, 0)
	for rows.Next() {
		request, scanErr := scanRequest(rows)
		if scanErr != nil {
			return nil, fmt.Errorf("scan project review request: %w", scanErr)
		}
		requests = append(requests, request)
	}
	if err = rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate project review requests: %w", err)
	}

	return requests, nil
}

func (psql *psql) ReviewProject(
	ctx context.Context,
	requestID, reviewedBy uuid.UUID,
	status models.RequestStatus,
	comment string,
) (models.Request, error) {
	var projectStatus models.ProjectStatus
	switch status {
	case models.RequestStatusClosed:
		projectStatus = models.ProjectStatusPublished
	case models.RequestStatusRejected:
		projectStatus = models.ProjectStatusRejected
	default:
		return models.Request{}, fmt.Errorf(
			"review request %s: unsupported terminal request status %d",
			requestID,
			status,
		)
	}

	var reviewed models.Request
	err := psql.WithinTransaction(ctx, func(txCtx context.Context) error {
		builder := statement.
			Update("project_review_requests").
			Set("reviewed_by", reviewedBy).
			Set("review_comment", comment).
			Set("request_status", status).
			Set("updated_at", sq.Expr("NOW()")).
			Set("closed_at", sq.Expr("NOW()")).
			Where(sq.Eq{
				"id":             requestID,
				"request_status": []models.RequestStatus{models.RequestStatusOpen, models.RequestStatusSubmitted},
			}).
			Suffix("RETURNING " + strings.Join(reviewRequestColumns, ", "))

		query, args, buildErr := buildQuery(builder, "review project request")
		if buildErr != nil {
			return fmt.Errorf("prepare review request %s query: %w", requestID, buildErr)
		}

		request, scanErr := scanRequest(psql.GetConnection(txCtx).QueryRow(txCtx, query, args...))
		if scanErr != nil {
			return fmt.Errorf("close project review request %s: %w", requestID, scanErr)
		}

		projectBuilder := statement.
			Update("projects").
			Set("status", projectStatus).
			Set("status_reason", comment).
			Set("updated_at", sq.Expr("NOW()")).
			Where(sq.Eq{"id": request.ProjectId, "deleted_at": nil})
		projectQuery, projectArgs, buildErr := buildQuery(projectBuilder, "update reviewed project status")
		if buildErr != nil {
			return fmt.Errorf("prepare update reviewed project %s query: %w", request.ProjectId, buildErr)
		}

		tag, execErr := psql.GetConnection(txCtx).Exec(txCtx, projectQuery, projectArgs...)
		if execErr != nil {
			return fmt.Errorf("set reviewed project %s status to %q: %w", request.ProjectId, projectStatus, execErr)
		}
		if tag.RowsAffected() == 0 {
			return fmt.Errorf("set reviewed project %s status: project not found", request.ProjectId)
		}

		reviewed = request
		return nil
	})
	if err != nil {
		return models.Request{}, fmt.Errorf("review project request %s transaction: %w", requestID, err)
	}

	return reviewed, nil
}

func (psql *psql) getProject(
	ctx context.Context,
	builder sq.SelectBuilder,
	operation string,
) (models.Project, error) {
	query, args, err := buildQuery(builder, operation)
	if err != nil {
		return models.Project{}, fmt.Errorf("prepare %s query: %w", operation, err)
	}

	project, err := scanProject(psql.GetConnection(ctx).QueryRow(ctx, query, args...))
	if err != nil {
		return models.Project{}, fmt.Errorf("%s: %w", operation, err)
	}

	return project, nil
}

func (psql *psql) getProjects(
	ctx context.Context,
	builder sq.SelectBuilder,
	operation string,
) ([]models.Project, error) {
	query, args, err := buildQuery(builder, operation)
	if err != nil {
		return nil, fmt.Errorf("prepare %s query: %w", operation, err)
	}

	rows, err := psql.GetConnection(ctx).Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", operation, err)
	}
	defer rows.Close()

	projects := make([]models.Project, 0)
	for rows.Next() {
		project, scanErr := scanProject(rows)
		if scanErr != nil {
			return nil, fmt.Errorf("scan row while trying to %s: %w", operation, scanErr)
		}
		projects = append(projects, project)
	}
	if err = rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate rows while trying to %s: %w", operation, err)
	}

	return projects, nil
}

func (psql *psql) getVersions(
	ctx context.Context,
	builder sq.SelectBuilder,
	operation string,
) ([]models.Version, error) {
	query, args, err := buildQuery(builder, operation)
	if err != nil {
		return nil, fmt.Errorf("prepare %s query: %w", operation, err)
	}

	rows, err := psql.GetConnection(ctx).Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", operation, err)
	}
	defer rows.Close()

	versions := make([]models.Version, 0)
	for rows.Next() {
		version, scanErr := scanVersion(rows)
		if scanErr != nil {
			return nil, fmt.Errorf("scan row while trying to %s: %w", operation, scanErr)
		}
		versions = append(versions, version)
	}
	if err = rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate rows while trying to %s: %w", operation, err)
	}

	return versions, nil
}

func (psql *psql) insertVersionMetadata(
	ctx context.Context,
	versionID uuid.UUID,
	metadata models.VersionMeta,
) (models.VersionMeta, error) {
	builder := statement.
		Insert("version_metadata").
		Columns(
			"id", "version_id", "slug", "description", "licence",
			"authors", "abi_version", "api_version", "environment",
		).
		Values(
			metadata.Id, versionID, metadata.Slug, metadata.Description, metadata.Licence,
			metadata.Authors, metadata.ABIVersion, metadata.APIVersion, metadata.Environment,
		).
		Suffix(`
			RETURNING id, slug, description, licence, authors,
			          abi_version, api_version, environment
		`)

	query, args, err := buildQuery(builder, "insert project version metadata")
	if err != nil {
		return models.VersionMeta{}, fmt.Errorf("prepare insert metadata for version %s query: %w", versionID, err)
	}

	var environment string
	err = psql.GetConnection(ctx).QueryRow(ctx, query, args...).Scan(
		&metadata.Id,
		&metadata.Slug,
		&metadata.Description,
		&metadata.Licence,
		&metadata.Authors,
		&metadata.ABIVersion,
		&metadata.APIVersion,
		&environment,
	)
	if err != nil {
		return models.VersionMeta{}, fmt.Errorf("insert metadata for version %s: %w", versionID, err)
	}
	metadata.Environment = sdk.PluginEnvironment(environment)

	return metadata, nil
}

func (psql *psql) getVersionMetadata(
	ctx context.Context,
	versionID uuid.UUID,
) (models.VersionMeta, error) {
	builder := statement.
		Select(
			"id", "slug", "description", "licence", "authors",
			"abi_version", "api_version", "environment",
		).
		From("version_metadata").
		Where(sq.Eq{"version_id": versionID})

	query, args, err := buildQuery(builder, "get project version metadata")
	if err != nil {
		return models.VersionMeta{}, fmt.Errorf("prepare get metadata for version %s query: %w", versionID, err)
	}

	var metadata models.VersionMeta
	var environment string
	err = psql.GetConnection(ctx).QueryRow(ctx, query, args...).Scan(
		&metadata.Id,
		&metadata.Slug,
		&metadata.Description,
		&metadata.Licence,
		&metadata.Authors,
		&metadata.ABIVersion,
		&metadata.APIVersion,
		&environment,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return models.VersionMeta{}, fmt.Errorf("get metadata for version %s: %w", versionID, err)
	}
	if err != nil {
		return models.VersionMeta{}, fmt.Errorf("scan metadata for version %s: %w", versionID, err)
	}
	metadata.Environment = sdk.PluginEnvironment(environment)

	return metadata, nil
}
