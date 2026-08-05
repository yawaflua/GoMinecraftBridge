package api

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"mime"
	"testing"

	"github.com/google/uuid"
	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"google.golang.org/genproto/googleapis/api/httpbody"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/fieldmaskpb"
)

type projectEndpointDatabase struct {
	adapters.DB
	project       models.Project
	createdGitURL string
	editedGitURL  string
}

func (database *projectEndpointDatabase) CreateProject(
	_ context.Context,
	authorID uuid.UUID,
	name, description, slug, gitURL string,
) (models.Project, error) {
	database.createdGitURL = gitURL
	database.project = models.Project{
		Id:          uuid.New(),
		AuthorId:    authorID,
		Name:        name,
		Description: description,
		Slug:        slug,
		GitURL:      gitURL,
		Status:      models.ProjectStatusDraft,
	}
	return database.project, nil
}

func (database *projectEndpointDatabase) GetProjectById(
	_ context.Context,
	projectID uuid.UUID,
) (models.Project, error) {
	if projectID != database.project.Id {
		return models.Project{}, context.Canceled
	}
	return database.project, nil
}

func (database *projectEndpointDatabase) EditProject(
	_ context.Context,
	projectID uuid.UUID,
	name, description, slug, gitURL string,
) (models.Project, error) {
	if projectID != database.project.Id {
		return models.Project{}, context.Canceled
	}
	database.editedGitURL = gitURL
	database.project.Name = name
	database.project.Description = description
	database.project.Slug = slug
	database.project.GitURL = gitURL
	return database.project, nil
}

func TestProjectGitURLCanBeCreatedAndUpdated(t *testing.T) {
	t.Parallel()

	owner := models.User{
		Id:     uuid.New(),
		Roles:  []string{"user"},
		Status: models.UserStatusActive,
	}
	database := &projectEndpointDatabase{}
	service := NewService(ServiceDependencies{DB: database})
	ctx := auth.ContextWithUser(context.Background(), owner)

	created, err := service.CreateProject(ctx, &projectv1.CreateProjectRequest{
		Project: &projectv1.ProjectInput{
			Slug:        "git-project",
			Name:        "Git project",
			Description: "Project with a repository",
			GitUrl:      " https://github.com/example/project.git ",
		},
	})
	if err != nil {
		t.Fatalf("CreateProject() error = %v", err)
	}
	if database.createdGitURL != "https://github.com/example/project.git" || created.GetGitUrl() != database.createdGitURL {
		t.Fatalf("created git URL = %q, response = %q", database.createdGitURL, created.GetGitUrl())
	}

	updated, err := service.UpdateProject(ctx, &projectv1.UpdateProjectRequest{
		ProjectId: database.project.Id.String(),
		Project: &projectv1.ProjectUpdate{
			GitUrl: "git@github.com:example/project.git",
		},
		UpdateMask: &fieldmaskpb.FieldMask{Paths: []string{"git_url"}},
	})
	if err != nil {
		t.Fatalf("UpdateProject() error = %v", err)
	}
	if database.editedGitURL != "git@github.com:example/project.git" || updated.GetGitUrl() != database.editedGitURL {
		t.Fatalf("updated git URL = %q, response = %q", database.editedGitURL, updated.GetGitUrl())
	}
}

func TestCreateProjectRejectsInvalidGitURL(t *testing.T) {
	t.Parallel()

	owner := models.User{Id: uuid.New(), Roles: []string{"user"}, Status: models.UserStatusActive}
	database := &projectEndpointDatabase{}
	service := NewService(ServiceDependencies{DB: database})
	ctx := auth.ContextWithUser(context.Background(), owner)

	_, err := service.CreateProject(ctx, &projectv1.CreateProjectRequest{
		Project: &projectv1.ProjectInput{
			Slug:   "git-project",
			Name:   "Git project",
			GitUrl: "not-a-url",
		},
	})
	if status.Code(err) != codes.InvalidArgument {
		t.Fatalf("CreateProject() code = %s, want %s", status.Code(err), codes.InvalidArgument)
	}
	if database.createdGitURL != "" {
		t.Fatalf("CreateProject() persisted invalid git URL %q", database.createdGitURL)
	}
}

type versionEndpointDatabase struct {
	adapters.DB
	project    models.Project
	uploaded   *models.Version
	downloaded models.Version
}

func (database *versionEndpointDatabase) GetProjectVersionByReference(
	_ context.Context,
	projectID uuid.UUID,
	_ string,
) (models.Version, error) {
	if projectID != database.project.Id {
		return models.Version{}, context.Canceled
	}
	return database.downloaded, nil
}

func (database *versionEndpointDatabase) GetProjectById(
	_ context.Context,
	projectID uuid.UUID,
) (models.Project, error) {
	if projectID != database.project.Id {
		return models.Project{}, context.Canceled
	}
	return database.project, nil
}

func (database *versionEndpointDatabase) UploadNewVersion(
	_ context.Context,
	projectID uuid.UUID,
	version models.Version,
) (models.Version, error) {
	version.Id = uuid.New()
	version.ProjectId = projectID
	database.uploaded = &version
	return version, nil
}

func TestUploadProjectVersionPersistsArchiveDigestAndMetadata(t *testing.T) {
	t.Parallel()

	owner := models.User{
		Id:     uuid.New(),
		Roles:  []string{"user"},
		Status: models.UserStatusActive,
	}
	database := &versionEndpointDatabase{
		project: models.Project{
			Id:       uuid.New(),
			AuthorId: owner.Id,
			Status:   models.ProjectStatusDraft,
		},
	}
	service := NewService(ServiceDependencies{DB: database})
	archive := []byte("compiled plugin archive")
	ctx := auth.ContextWithUser(context.Background(), owner)

	response, err := service.UploadProjectVersion(ctx, &projectv1.UploadProjectVersionRequest{
		ProjectId:   database.project.Id.String(),
		Version:     "1.0.0",
		Description: "first release",
		Changelog:   "initial release",
		Readme:      "# Plugin",
		Tag:         projectv1.VersionTag_VERSION_TAG_RELEASE,
		Metadata: &projectv1.VersionMetadata{
			Slug:        "plugin",
			Description: "plugin metadata",
			Licenses:    []string{"MIT"},
			Authors:     []string{"author"},
			AbiVersion:  supportedNativeProtocolVersion,
			ApiVersion:  supportedNativeProtocolVersion,
			Environment: projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_BOTH,
		},
		Archive: &httpbody.HttpBody{
			ContentType: "application/zip",
			Data:        archive,
		},
	})
	if err != nil {
		t.Fatalf("UploadProjectVersion() error = %v", err)
	}
	if response.GetVersion() != "1.0.0" || database.uploaded == nil {
		t.Fatalf("UploadProjectVersion() response = %#v, uploaded = %#v", response, database.uploaded)
	}
	expectedDigest := sha256.Sum256(archive)
	if database.uploaded.SHA256 != hex.EncodeToString(expectedDigest[:]) {
		t.Fatalf("archive SHA-256 = %q, want %q", database.uploaded.SHA256, hex.EncodeToString(expectedDigest[:]))
	}
	if database.uploaded.ContentType != "application/zip" {
		t.Fatalf("archive content type = %q, want application/zip", database.uploaded.ContentType)
	}
	if database.uploaded.Metadata.Environment != "both" {
		t.Fatalf("metadata environment = %q, want both", database.uploaded.Metadata.Environment)
	}
}

func TestUploadProjectVersionRejectsOldProtocol(t *testing.T) {
	t.Parallel()

	owner := models.User{Id: uuid.New(), Roles: []string{"user"}, Status: models.UserStatusActive}
	database := &versionEndpointDatabase{project: models.Project{
		Id: uuid.New(), AuthorId: owner.Id, Status: models.ProjectStatusDraft,
	}}
	service := NewService(ServiceDependencies{DB: database})
	ctx := auth.ContextWithUser(context.Background(), owner)

	_, err := service.UploadProjectVersion(ctx, &projectv1.UploadProjectVersionRequest{
		ProjectId: database.project.Id.String(),
		Version:   "1.0.0",
		Tag:       projectv1.VersionTag_VERSION_TAG_RELEASE,
		Metadata: &projectv1.VersionMetadata{
			Slug: "plugin", AbiVersion: "2", ApiVersion: "2",
			Environment: projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_SERVER,
		},
		Archive: &httpbody.HttpBody{ContentType: "application/zip", Data: []byte("archive")},
	})
	if status.Code(err) != codes.InvalidArgument {
		t.Fatalf("UploadProjectVersion() code = %s, want %s", status.Code(err), codes.InvalidArgument)
	}
	if database.uploaded != nil {
		t.Fatal("UploadProjectVersion() persisted an ABI v2 package")
	}
}

func TestDownloadProjectVersionSetsArchiveFilename(t *testing.T) {
	t.Parallel()

	database := &versionEndpointDatabase{
		project: models.Project{
			Id:     uuid.New(),
			Slug:   "example-plugin",
			Status: models.ProjectStatusPublished,
		},
		downloaded: models.Version{
			Version:     "1.2.3",
			Archive:     []byte("shared object"),
			ContentType: "application/octet-stream",
		},
	}
	service := NewService(ServiceDependencies{DB: database})
	var transport runtime.ServerTransportStream
	ctx := grpc.NewContextWithServerTransportStream(context.Background(), &transport)

	response, err := service.DownloadProjectVersion(ctx, &projectv1.DownloadProjectVersionRequest{
		ProjectSelector: &projectv1.DownloadProjectVersionRequest_ProjectId{
			ProjectId: database.project.Id.String(),
		},
		Version: "latest",
	})
	if err != nil {
		t.Fatalf("DownloadProjectVersion() error = %v", err)
	}
	if string(response.GetData()) != "shared object" {
		t.Fatalf("DownloadProjectVersion() data = %q, want shared object", response.GetData())
	}

	dispositions := transport.Header().Get("content-disposition")
	if len(dispositions) != 1 {
		t.Fatalf("Content-Disposition values = %q, want exactly one", dispositions)
	}
	disposition := dispositions[0]
	mediaType, parameters, err := mime.ParseMediaType(disposition)
	if err != nil {
		t.Fatalf("Content-Disposition %q is invalid: %v", disposition, err)
	}
	if mediaType != "attachment" || parameters["filename"] != "example-plugin-1.2.3.so" {
		t.Fatalf("Content-Disposition = %q, want attachment filename example-plugin-1.2.3.so", disposition)
	}
}

func TestUploadProjectVersionRejectsNonOwner(t *testing.T) {
	t.Parallel()

	database := &versionEndpointDatabase{
		project: models.Project{
			Id:       uuid.New(),
			AuthorId: uuid.New(),
			Status:   models.ProjectStatusDraft,
		},
	}
	service := NewService(ServiceDependencies{DB: database})
	ctx := auth.ContextWithUser(context.Background(), models.User{
		Id:     uuid.New(),
		Roles:  []string{"user"},
		Status: models.UserStatusActive,
	})

	_, err := service.UploadProjectVersion(ctx, &projectv1.UploadProjectVersionRequest{
		ProjectId: database.project.Id.String(),
		Archive:   &httpbody.HttpBody{Data: []byte("archive")},
	})
	if status.Code(err) != codes.PermissionDenied {
		t.Fatalf("UploadProjectVersion() code = %s, want %s", status.Code(err), codes.PermissionDenied)
	}
	if database.uploaded != nil {
		t.Fatal("UploadProjectVersion() persisted a version for a non-owner")
	}
}
