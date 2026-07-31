package httpapi

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"google.golang.org/genproto/googleapis/api/httpbody"
	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
)

type gatewayAuthDatabase struct {
	adapters.DB
	user models.User
}

func (database gatewayAuthDatabase) GetUserById(
	_ context.Context,
	_ uuid.UUID,
) (models.User, error) {
	return database.user, nil
}

type gatewayAuthService struct {
	projectv1.UnimplementedGBMBackendServer
}

func (gatewayAuthService) GetCurrentUser(
	ctx context.Context,
	_ *projectv1.GetCurrentUserRequest,
) (*projectv1.User, error) {
	user, err := auth.RequireUser(ctx)
	if err != nil {
		return nil, err
	}
	return &projectv1.User{Id: user.Id.String(), Username: user.Nickname}, nil
}

func (gatewayAuthService) DownloadProjectVersion(
	ctx context.Context,
	_ *projectv1.DownloadProjectVersionRequest,
) (*httpbody.HttpBody, error) {
	if err := grpc.SetHeader(ctx, metadata.Pairs(
		"content-disposition",
		"attachment; filename=example-plugin-1.2.3.so",
	)); err != nil {
		return nil, err
	}
	return &httpbody.HttpBody{
		ContentType: "application/octet-stream",
		Data:        []byte("shared object"),
	}, nil
}

func (gatewayAuthService) CheckNewVersions(
	_ context.Context,
	request *projectv1.CheckNewVersionsRequest,
) (*projectv1.CheckNewVersionsResponse, error) {
	updates := make([]*projectv1.VersionUpdate, 0, len(request.GetPackages()))
	for _, installed := range request.GetPackages() {
		updates = append(updates, &projectv1.VersionUpdate{
			ProjectId:       installed.GetProjectId(),
			CurrentVersion:  installed.GetVersion(),
			LatestVersion:   "2.0.0",
			UpdateAvailable: true,
		})
	}
	return &projectv1.CheckNewVersionsResponse{Updates: updates}, nil
}

func TestGatewayReceivesAuthenticatedUserThroughContext(t *testing.T) {
	t.Parallel()

	user := models.User{
		Id:       uuid.New(),
		Nickname: "gateway-user",
		Status:   models.UserStatusActive,
	}
	authenticator, err := auth.New(auth.Config{
		Secret:          "gateway-integration-secret-at-least-32-bytes",
		Issuer:          "test-issuer",
		Audience:        "test-audience",
		AccessTokenTTL:  15 * time.Minute,
		RefreshTokenTTL: 24 * time.Hour,
	}, gatewayAuthDatabase{user: user})
	if err != nil {
		t.Fatalf("auth.New() error = %v", err)
	}

	token, _, err := authenticator.IssueAccessToken(user.Id)
	if err != nil {
		t.Fatalf("IssueAccessToken() error = %v", err)
	}

	request := httptest.NewRequest(http.MethodGet, "/v1/users/me", nil)
	request.Header.Set("Authorization", "Bearer "+token)
	response := httptest.NewRecorder()

	NewHandler(gatewayAuthService{}, authenticator).ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("gateway status = %d, body = %s", response.Code, response.Body.String())
	}
	if !strings.Contains(response.Body.String(), user.Id.String()) {
		t.Fatalf("gateway response %q does not contain user ID %s", response.Body.String(), user.Id)
	}
}

func TestGatewayForwardsDownloadFilename(t *testing.T) {
	t.Parallel()

	request := httptest.NewRequest(
		http.MethodGet,
		"/v1/projects/slug/example-plugin/versions/latest:download",
		nil,
	)
	response := httptest.NewRecorder()

	NewHandler(gatewayAuthService{}, &auth.Authenticator{}).ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("gateway status = %d, body = %s", response.Code, response.Body.String())
	}
	if disposition := response.Header().Get("Content-Disposition"); disposition != "attachment; filename=example-plugin-1.2.3.so" {
		t.Fatalf("Content-Disposition = %q, want attachment filename", disposition)
	}
	if response.Body.String() != "shared object" {
		t.Fatalf("gateway body = %q, want shared object", response.Body.String())
	}
}

func TestGatewayAcceptsVersionChecksAsPostBody(t *testing.T) {
	t.Parallel()

	request := httptest.NewRequest(
		http.MethodPost,
		"/v1/projects/@all/versions/check",
		strings.NewReader(`{"packages":[{"project_id":"project-id","version":"1.0.0"}]}`),
	)
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()

	NewHandler(gatewayAuthService{}, &auth.Authenticator{}).ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("gateway status = %d, body = %s", response.Code, response.Body.String())
	}
	if !strings.Contains(response.Body.String(), `"project_id":"project-id"`) {
		t.Fatalf("gateway response %q does not contain the checked project", response.Body.String())
	}
	if !strings.Contains(response.Body.String(), `"update_available":true`) {
		t.Fatalf("gateway response %q does not report an available update", response.Body.String())
	}
}
