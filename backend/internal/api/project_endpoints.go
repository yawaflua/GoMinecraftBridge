package api

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"mime"
	"strings"

	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"google.golang.org/genproto/googleapis/api/httpbody"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

func (service *Service) CreateProject(
	ctx context.Context,
	request *projectv1.CreateProjectRequest,
) (*projectv1.Project, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil || request.Project == nil {
		return nil, status.Error(codes.InvalidArgument, "project is required")
	}
	if strings.TrimSpace(request.Project.Name) == "" || len(request.Project.Name) > 100 {
		return nil, status.Error(codes.InvalidArgument, "project name must contain 1-100 characters")
	}
	if len(request.Project.Description) > 4096 {
		return nil, status.Error(codes.InvalidArgument, "project description exceeds 4096 characters")
	}
	if err = validateSlug(request.Project.Slug); err != nil {
		return nil, err
	}
	gitURL, err := normalizeGitURL(request.Project.GitUrl)
	if err != nil {
		return nil, err
	}

	project, err := service.db.CreateProject(
		ctx,
		user.Id,
		strings.TrimSpace(request.Project.Name),
		request.Project.Description,
		request.Project.Slug,
		gitURL,
	)
	if err != nil {
		return nil, databaseError("create project", err)
	}
	return projectToProto(project), nil
}

func (service *Service) CheckSlugAvailability(
	ctx context.Context,
	request *projectv1.CheckSlugAvailabilityRequest,
) (*projectv1.CheckSlugAvailabilityResponse, error) {
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	if err := validateSlug(request.Slug); err != nil {
		return nil, err
	}
	excluded, err := optionalUUID(request.ExcludeProjectId, "exclude_project_id")
	if err != nil {
		return nil, err
	}
	available, err := service.db.CheckSlugAvailability(ctx, request.Slug, excluded)
	if err != nil {
		return nil, databaseError("check slug availability", err)
	}
	return &projectv1.CheckSlugAvailabilityResponse{Available: available}, nil
}

func (service *Service) GetProject(
	ctx context.Context,
	request *projectv1.GetProjectRequest,
) (*projectv1.Project, error) {
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "project selector is required")
	}

	var (
		project models.Project
		err     error
	)
	switch selector := request.ProjectSelector.(type) {
	case *projectv1.GetProjectRequest_ProjectId:
		projectID, parseErr := parseUUID(selector.ProjectId, "project_id")
		if parseErr != nil {
			return nil, parseErr
		}
		project, err = service.db.GetProjectById(ctx, projectID)
	case *projectv1.GetProjectRequest_Slug:
		if validateErr := validateSlug(selector.Slug); validateErr != nil {
			return nil, validateErr
		}
		project, err = service.db.GetProjectBySlug(ctx, selector.Slug)
	default:
		return nil, status.Error(codes.InvalidArgument, "project_id or slug is required")
	}
	if err != nil {
		return nil, databaseError("get project", err)
	}
	if err = service.requireProjectVisible(ctx, project); err != nil {
		return nil, err
	}
	project, err = service.withLatestVersion(ctx, project)
	if err != nil {
		return nil, err
	}
	return projectToProto(project), nil
}

func (service *Service) ListMyProjects(
	ctx context.Context,
	request *projectv1.ListMyProjectsRequest,
) (*projectv1.ListProjectsResponse, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil {
		request = &projectv1.ListMyProjectsRequest{}
	}
	projects, err := service.db.GetUserProjects(ctx, user.Id)
	if err != nil {
		return nil, databaseError("list user projects", err)
	}
	if !request.IncludeBanned {
		filtered := projects[:0]
		for _, project := range projects {
			if project.Status != models.ProjectStatusBanned {
				filtered = append(filtered, project)
			}
		}
		projects = filtered
	}
	page, nextToken, err := paginate(projects, request.PageSize, request.PageToken)
	if err != nil {
		return nil, err
	}

	result := make([]*projectv1.Project, 0, len(page))
	for _, project := range page {
		project, err = service.withLatestVersion(ctx, project)
		if err != nil {
			return nil, err
		}
		result = append(result, projectToProto(project))
	}
	return &projectv1.ListProjectsResponse{Projects: result, NextPageToken: nextToken}, nil
}

func (service *Service) UpdateProject(
	ctx context.Context,
	request *projectv1.UpdateProjectRequest,
) (*projectv1.Project, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil || request.Project == nil {
		return nil, status.Error(codes.InvalidArgument, "project update is required")
	}
	if request.UpdateMask == nil || len(request.UpdateMask.Paths) == 0 {
		return nil, status.Error(codes.InvalidArgument, "update_mask is required")
	}
	if err = validateMask(request.UpdateMask, "name", "description", "slug", "git_url"); err != nil {
		return nil, err
	}
	projectID, err := parseUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	current, err := service.db.GetProjectById(ctx, projectID)
	if err != nil {
		return nil, databaseError("get project before update", err)
	}
	if err = requireProjectOwnerOrAdmin(user, current); err != nil {
		return nil, err
	}

	name, description, slug, gitURL := current.Name, current.Description, current.Slug, current.GitURL
	if maskContains(request.UpdateMask, "name") {
		name = strings.TrimSpace(request.Project.Name)
		if name == "" || len(name) > 100 {
			return nil, status.Error(codes.InvalidArgument, "project name must contain 1-100 characters")
		}
	}
	if maskContains(request.UpdateMask, "description") {
		description = request.Project.Description
		if len(description) > 4096 {
			return nil, status.Error(codes.InvalidArgument, "project description exceeds 4096 characters")
		}
	}
	if maskContains(request.UpdateMask, "slug") {
		slug = request.Project.Slug
		if err = validateSlug(slug); err != nil {
			return nil, err
		}
	}
	if maskContains(request.UpdateMask, "git_url") {
		gitURL, err = normalizeGitURL(request.Project.GitUrl)
		if err != nil {
			return nil, err
		}
	}

	updated, err := service.db.EditProject(ctx, projectID, name, description, slug, gitURL)
	if err != nil {
		return nil, databaseError("update project", err)
	}
	return projectToProto(updated), nil
}

func (service *Service) SearchProjects(
	ctx context.Context,
	request *projectv1.SearchProjectsRequest,
) (*projectv1.SearchProjectsResponse, error) {
	if request == nil {
		request = &projectv1.SearchProjectsRequest{}
	}
	query := strings.TrimSpace(request.Query)
	if len(query) > 200 {
		return nil, status.Error(codes.InvalidArgument, "search query exceeds 200 characters")
	}
	minSimilarity := request.MinSimilarity
	if minSimilarity < 0 || minSimilarity > 1 {
		return nil, status.Error(codes.InvalidArgument, "min_similarity must be between 0 and 1")
	}
	projects, err := service.db.SearchProjects(ctx, query, maxPageSize, minSimilarity)
	if err != nil {
		return nil, databaseError("search projects", err)
	}
	page, nextToken, err := paginate(projects, request.PageSize, request.PageToken)
	if err != nil {
		return nil, err
	}

	results := make([]*projectv1.SearchProjectResult, 0, len(page))
	for _, project := range page {
		project, err = service.withLatestVersion(ctx, project)
		if err != nil {
			return nil, err
		}
		score := trigramSimilarity(
			project.Name+" "+project.Slug+" "+project.Description,
			query,
		)
		results = append(results, &projectv1.SearchProjectResult{
			Project:    projectToProto(project),
			Similarity: score,
		})
	}
	return &projectv1.SearchProjectsResponse{Projects: results, NextPageToken: nextToken}, nil
}

func (service *Service) ListProjectVersions(
	ctx context.Context,
	request *projectv1.ListProjectVersionsRequest,
) (*projectv1.ListProjectVersionsResponse, error) {
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	projectID, err := parseUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	project, err := service.db.GetProjectById(ctx, projectID)
	if err != nil {
		return nil, databaseError("get project before listing versions", err)
	}
	if err = service.requireProjectVisible(ctx, project); err != nil {
		return nil, err
	}
	versions, err := service.db.GetProjectVersionsWithMeta(ctx, projectID)
	if err != nil {
		return nil, databaseError("list project versions", err)
	}
	page, nextToken, err := paginate(versions, request.PageSize, request.PageToken)
	if err != nil {
		return nil, err
	}
	result := make([]*projectv1.ProjectVersion, 0, len(page))
	for _, version := range page {
		result = append(result, versionToProto(version))
	}
	return &projectv1.ListProjectVersionsResponse{Versions: result, NextPageToken: nextToken}, nil
}

func (service *Service) GetProjectVersion(
	ctx context.Context,
	request *projectv1.GetProjectVersionRequest,
) (*projectv1.ProjectVersion, error) {
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	projectID, err := parseUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	versionID, err := parseUUID(request.VersionId, "version_id")
	if err != nil {
		return nil, err
	}
	project, err := service.db.GetProjectById(ctx, projectID)
	if err != nil {
		return nil, databaseError("get project before getting version", err)
	}
	if err = service.requireProjectVisible(ctx, project); err != nil {
		return nil, err
	}
	version, err := service.db.GetProjectVersionById(ctx, projectID, versionID)
	if err != nil {
		return nil, databaseError("get project version", err)
	}
	return versionToProto(version), nil
}

func (service *Service) UploadProjectVersion(
	ctx context.Context,
	request *projectv1.UploadProjectVersionRequest,
) (*projectv1.ProjectVersion, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil || request.Archive == nil {
		return nil, status.Error(codes.InvalidArgument, "version archive is required")
	}
	projectID, err := parseUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	project, err := service.db.GetProjectById(ctx, projectID)
	if err != nil {
		return nil, databaseError("get project before version upload", err)
	}
	if err = requireProjectOwnerOrAdmin(user, project); err != nil {
		return nil, err
	}
	if strings.TrimSpace(request.Version) == "" || len(request.Version) > 64 {
		return nil, status.Error(codes.InvalidArgument, "version must contain 1-64 characters")
	}
	if len(request.Description) > 4096 || len(request.Changelog) > 50_000 || len(request.Readme) > 1_000_000 {
		return nil, status.Error(codes.InvalidArgument, "version text fields exceed their limits")
	}
	if len(request.Archive.Data) == 0 || len(request.Archive.Data) > maxArchiveSize {
		return nil, status.Error(codes.InvalidArgument, "archive must contain 1 byte to 64 MiB")
	}
	tag, ok := versionTagFromProto(request.Tag)
	if !ok {
		return nil, status.Error(codes.InvalidArgument, "version tag is required")
	}
	metadata, ok := metadataFromProto(request.Metadata)
	if !ok {
		return nil, status.Error(codes.InvalidArgument, "valid version metadata is required")
	}
	contentType := request.Archive.ContentType
	if contentType == "" {
		contentType = "application/octet-stream"
	}
	if _, _, parseErr := mime.ParseMediaType(contentType); parseErr != nil || len(contentType) > 255 {
		return nil, status.Error(codes.InvalidArgument, "archive content_type is invalid")
	}
	hash := sha256.Sum256(request.Archive.Data)
	version, err := service.db.UploadNewVersion(ctx, projectID, models.Version{
		Version:     strings.TrimSpace(request.Version),
		Description: request.Description,
		Changelog:   request.Changelog,
		Readme:      request.Readme,
		Metadata:    metadata,
		Tag:         tag,
		SizeBytes:   int64(len(request.Archive.Data)),
		SHA256:      hex.EncodeToString(hash[:]),
		Archive:     append([]byte(nil), request.Archive.Data...),
		ContentType: contentType,
	})
	if err != nil {
		return nil, databaseError("upload project version", err)
	}
	return versionToProto(version), nil
}

func (service *Service) UpdateVersionMetadata(
	ctx context.Context,
	request *projectv1.UpdateVersionMetadataRequest,
) (*projectv1.ProjectVersion, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil || request.Metadata == nil {
		return nil, status.Error(codes.InvalidArgument, "metadata is required")
	}
	if request.UpdateMask == nil || len(request.UpdateMask.Paths) == 0 {
		return nil, status.Error(codes.InvalidArgument, "update_mask is required")
	}
	if err = validateMask(
		request.UpdateMask,
		"slug", "description", "licenses", "authors", "abi_version", "api_version", "environment",
	); err != nil {
		return nil, err
	}
	projectID, err := parseUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	versionID, err := parseUUID(request.VersionId, "version_id")
	if err != nil {
		return nil, err
	}
	project, err := service.db.GetProjectById(ctx, projectID)
	if err != nil {
		return nil, databaseError("get project before metadata update", err)
	}
	if err = requireProjectOwnerOrAdmin(user, project); err != nil {
		return nil, err
	}
	current, err := service.db.GetProjectVersionById(ctx, projectID, versionID)
	if err != nil {
		return nil, databaseError("get version before metadata update", err)
	}
	update, ok := metadataFromProto(request.Metadata)
	if !ok && maskContains(request.UpdateMask, "environment") {
		return nil, status.Error(codes.InvalidArgument, "valid environment is required")
	}
	metadata := current.Metadata
	if maskContains(request.UpdateMask, "slug") {
		metadata.Slug = request.Metadata.Slug
	}
	if maskContains(request.UpdateMask, "description") {
		metadata.Description = request.Metadata.Description
	}
	if maskContains(request.UpdateMask, "licenses") {
		metadata.Licence = append([]string(nil), request.Metadata.Licenses...)
	}
	if maskContains(request.UpdateMask, "authors") {
		metadata.Authors = append([]string(nil), request.Metadata.Authors...)
	}
	if maskContains(request.UpdateMask, "abi_version") {
		metadata.ABIVersion = request.Metadata.AbiVersion
	}
	if maskContains(request.UpdateMask, "api_version") {
		metadata.APIVersion = request.Metadata.ApiVersion
	}
	if maskContains(request.UpdateMask, "environment") {
		metadata.Environment = update.Environment
	}
	updated, err := service.db.EditMetadata(ctx, projectID, versionID, metadata)
	if err != nil {
		return nil, databaseError("update version metadata", err)
	}
	return versionToProto(updated), nil
}

func (service *Service) DownloadProjectVersion(
	ctx context.Context,
	request *projectv1.DownloadProjectVersionRequest,
) (*httpbody.HttpBody, error) {
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "project selector is required")
	}
	var (
		project models.Project
		err     error
	)
	switch selector := request.ProjectSelector.(type) {
	case *projectv1.DownloadProjectVersionRequest_ProjectId:
		projectID, parseErr := parseUUID(selector.ProjectId, "project_id")
		if parseErr != nil {
			return nil, parseErr
		}
		project, err = service.db.GetProjectById(ctx, projectID)
	case *projectv1.DownloadProjectVersionRequest_Slug:
		if validateErr := validateSlug(selector.Slug); validateErr != nil {
			return nil, validateErr
		}
		project, err = service.db.GetProjectBySlug(ctx, selector.Slug)
	default:
		return nil, status.Error(codes.InvalidArgument, "project_id or slug is required")
	}
	if err != nil {
		return nil, databaseError("get project before download", err)
	}
	if err = service.requireProjectVisible(ctx, project); err != nil {
		return nil, err
	}
	version, err := service.db.GetProjectVersionByReference(ctx, project.Id, request.Version)
	if err != nil {
		return nil, databaseError("download project version", err)
	}
	contentType := version.ContentType
	if contentType == "" {
		contentType = "application/octet-stream"
	}
	contentDisposition := mime.FormatMediaType("attachment", map[string]string{
		"filename": project.Slug + "-" + version.Version + ".so",
	})
	if err = grpc.SetHeader(ctx, metadata.Pairs("content-disposition", contentDisposition)); err != nil {
		return nil, status.Error(codes.Internal, "set download filename failed")
	}
	return &httpbody.HttpBody{ContentType: contentType, Data: version.Archive}, nil
}

func (service *Service) SubmitProject(
	ctx context.Context,
	request *projectv1.SubmitProjectRequest,
) (*projectv1.ProjectReviewRequest, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	projectID, err := parseUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	project, err := service.db.GetProjectById(ctx, projectID)
	if err != nil {
		return nil, databaseError("get project before submission", err)
	}
	if project.AuthorId != user.Id {
		return nil, status.Error(codes.PermissionDenied, "only the project owner can submit it")
	}
	if project.Status == models.ProjectStatusBanned {
		return nil, status.Error(codes.FailedPrecondition, "banned project cannot be submitted")
	}
	reviewRequest, err := service.db.SubmitProject(ctx, projectID, user.Id, request.Comment)
	if err != nil {
		return nil, databaseError("submit project", err)
	}
	return reviewRequestToProto(reviewRequest), nil
}

func (service *Service) CheckNewVersions(
	ctx context.Context,
	request *projectv1.CheckNewVersionsRequest,
) (*projectv1.CheckNewVersionsResponse, error) {
	if request == nil {
		return &projectv1.CheckNewVersionsResponse{}, nil
	}
	updates := make([]*projectv1.VersionUpdate, 0, len(request.Packages))
	for _, installed := range request.Packages {
		if installed == nil {
			continue
		}
		projectID, err := parseUUID(installed.ProjectId, "project_id")
		if err != nil {
			return nil, err
		}
		project, err := service.db.GetProjectById(ctx, projectID)
		if err != nil {
			return nil, databaseError("check project update", err)
		}
		if project.Status != models.ProjectStatusPublished {
			continue
		}
		versions, err := service.db.GetProjectVersions(ctx, projectID)
		if err != nil {
			return nil, databaseError("check latest project version", err)
		}
		latest := ""
		if len(versions) > 0 {
			latest = versions[0].Version
		}
		updates = append(updates, &projectv1.VersionUpdate{
			ProjectId:       projectID.String(),
			CurrentVersion:  installed.Version,
			LatestVersion:   latest,
			UpdateAvailable: latest != "" && latest != installed.Version,
		})
	}
	return &projectv1.CheckNewVersionsResponse{Updates: updates}, nil
}

func (service *Service) requireProjectVisible(ctx context.Context, project models.Project) error {
	if project.Status == models.ProjectStatusPublished {
		return nil
	}
	user, ok := auth.UserFromContext(ctx)
	if ok && (user.Id == project.AuthorId || hasRole(user, "admin", "moderator")) {
		return nil
	}
	return status.Error(codes.NotFound, "project not found")
}

func (service *Service) withLatestVersion(
	ctx context.Context,
	project models.Project,
) (models.Project, error) {
	versions, err := service.db.GetProjectVersions(ctx, project.Id)
	if err != nil {
		return models.Project{}, databaseError("get latest project version", err)
	}
	if len(versions) > 0 {
		project.Versions = versions[:1]
	}
	return project, nil
}
