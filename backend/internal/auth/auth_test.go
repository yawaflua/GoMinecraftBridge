package auth

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
)

type authTestDatabase struct {
	adapters.DB
	user models.User
	err  error
}

func (database authTestDatabase) GetUserById(
	_ context.Context,
	userID uuid.UUID,
) (models.User, error) {
	if database.err != nil {
		return models.User{}, database.err
	}
	if database.user.Id != userID {
		return models.User{}, errors.New("user not found")
	}
	return database.user, nil
}

func TestAuthenticateAuthorizationStoresDatabaseUserInContext(t *testing.T) {
	t.Parallel()

	user := models.User{
		Id:       uuid.New(),
		Nickname: "authenticated-user",
		Status:   models.UserStatusActive,
	}
	authenticator := newTestAuthenticator(t, "test-secret-at-least-32-bytes-long", authTestDatabase{user: user})

	token, _, err := authenticator.IssueAccessToken(user.Id)
	if err != nil {
		t.Fatalf("IssueAccessToken() error = %v", err)
	}

	ctx, err := authenticator.AuthenticateAuthorization(context.Background(), "Bearer "+token)
	if err != nil {
		t.Fatalf("AuthenticateAuthorization() error = %v", err)
	}

	got, ok := UserFromContext(ctx)
	if !ok {
		t.Fatal("UserFromContext() did not find authenticated user")
	}
	if got.Id != user.Id || got.Nickname != user.Nickname {
		t.Fatalf("UserFromContext() = %#v, want %#v", got, user)
	}
}

func TestAuthenticateAuthorizationRejectsWrongSignature(t *testing.T) {
	t.Parallel()

	user := models.User{Id: uuid.New(), Status: models.UserStatusActive}
	signer := newTestAuthenticator(t, "signing-secret-at-least-32-bytes-long", authTestDatabase{user: user})
	validator := newTestAuthenticator(t, "different-secret-at-least-32-bytes", authTestDatabase{user: user})

	token, _, err := signer.IssueAccessToken(user.Id)
	if err != nil {
		t.Fatalf("IssueAccessToken() error = %v", err)
	}

	_, err = validator.AuthenticateAuthorization(context.Background(), "Bearer "+token)
	if !errors.Is(err, ErrAuthorizationInvalid) {
		t.Fatalf("AuthenticateAuthorization() error = %v, want ErrAuthorizationInvalid", err)
	}
}

func TestAuthenticateAuthorizationRejectsActiveBan(t *testing.T) {
	t.Parallel()

	user := models.User{Id: uuid.New(), Status: models.UserStatusBanned}
	authenticator := newTestAuthenticator(t, "test-secret-at-least-32-bytes-long", authTestDatabase{user: user})

	token, _, err := authenticator.IssueAccessToken(user.Id)
	if err != nil {
		t.Fatalf("IssueAccessToken() error = %v", err)
	}

	_, err = authenticator.AuthenticateAuthorization(context.Background(), "Bearer "+token)
	if !errors.Is(err, ErrUserBanned) {
		t.Fatalf("AuthenticateAuthorization() error = %v, want ErrUserBanned", err)
	}
}

func TestHTTPMiddlewarePassesUserThroughRequestContext(t *testing.T) {
	t.Parallel()

	user := models.User{Id: uuid.New(), Status: models.UserStatusActive}
	authenticator := newTestAuthenticator(t, "test-secret-at-least-32-bytes-long", authTestDatabase{user: user})
	token, _, err := authenticator.IssueAccessToken(user.Id)
	if err != nil {
		t.Fatalf("IssueAccessToken() error = %v", err)
	}

	next := http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		authenticatedUser, ok := UserFromContext(request.Context())
		if !ok || authenticatedUser.Id != user.Id {
			t.Errorf("middleware context user = %#v, present = %t", authenticatedUser, ok)
		}
		writer.WriteHeader(http.StatusNoContent)
	})

	request := httptest.NewRequest(http.MethodGet, "/v1/users/me", nil)
	request.Header.Set("Authorization", "Bearer "+token)
	response := httptest.NewRecorder()
	authenticator.HTTPMiddleware(next).ServeHTTP(response, request)

	if response.Code != http.StatusNoContent {
		t.Fatalf("HTTP middleware status = %d, want %d", response.Code, http.StatusNoContent)
	}
}

func TestHTTPMiddlewareRequiresTokenForProtectedEndpoint(t *testing.T) {
	t.Parallel()

	authenticator := newTestAuthenticator(t, "test-secret-at-least-32-bytes-long", authTestDatabase{})
	request := httptest.NewRequest(http.MethodGet, "/v1/users/me", nil)
	response := httptest.NewRecorder()

	authenticator.HTTPMiddleware(http.NotFoundHandler()).ServeHTTP(response, request)

	if response.Code != http.StatusUnauthorized {
		t.Fatalf("HTTP middleware status = %d, want %d", response.Code, http.StatusUnauthorized)
	}
}

func TestHTTPMiddlewareAllowsPublicCatalogWithoutToken(t *testing.T) {
	t.Parallel()

	authenticator := newTestAuthenticator(t, "test-secret-at-least-32-bytes-long", authTestDatabase{})
	next := http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusNoContent)
	})
	request := httptest.NewRequest(http.MethodGet, "/v1/projects:search", nil)
	response := httptest.NewRecorder()

	authenticator.HTTPMiddleware(next).ServeHTTP(response, request)

	if response.Code != http.StatusNoContent {
		t.Fatalf("HTTP middleware status = %d, want %d", response.Code, http.StatusNoContent)
	}
}

func TestHTTPMiddlewareAllowsPublicVersionCheckPost(t *testing.T) {
	t.Parallel()

	authenticator := newTestAuthenticator(t, "test-secret-at-least-32-bytes-long", authTestDatabase{})
	next := http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusNoContent)
	})
	request := httptest.NewRequest(http.MethodPost, "/v1/projects/@all/versions/check", nil)
	response := httptest.NewRecorder()

	authenticator.HTTPMiddleware(next).ServeHTTP(response, request)

	if response.Code != http.StatusNoContent {
		t.Fatalf("HTTP middleware status = %d, want %d", response.Code, http.StatusNoContent)
	}
}

func TestHTTPMiddlewareIgnoresExpiredAccessTokenOnRefreshEndpoint(t *testing.T) {
	t.Parallel()

	authenticator := newTestAuthenticator(t, "test-secret-at-least-32-bytes-long", authTestDatabase{})
	next := http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusNoContent)
	})
	request := httptest.NewRequest(http.MethodPost, "/v1/auth/token:refresh", nil)
	request.Header.Set("Authorization", "Bearer expired-or-invalid")
	response := httptest.NewRecorder()

	authenticator.HTTPMiddleware(next).ServeHTTP(response, request)

	if response.Code != http.StatusNoContent {
		t.Fatalf("HTTP middleware status = %d, want %d", response.Code, http.StatusNoContent)
	}
}

type refreshTestDatabase struct {
	adapters.DB
	mu       sync.Mutex
	user     models.User
	sessions map[uuid.UUID]models.RefreshSession
}

func (database *refreshTestDatabase) GetUserById(
	_ context.Context,
	userID uuid.UUID,
) (models.User, error) {
	if database.user.Id != userID {
		return models.User{}, errors.New("user not found")
	}
	return database.user, nil
}

func (database *refreshTestDatabase) CreateRefreshSession(
	_ context.Context,
	session models.RefreshSession,
) error {
	database.mu.Lock()
	defer database.mu.Unlock()
	database.sessions[session.Id] = session
	return nil
}

func (database *refreshTestDatabase) GetRefreshSession(
	_ context.Context,
	sessionID uuid.UUID,
) (models.RefreshSession, error) {
	database.mu.Lock()
	defer database.mu.Unlock()
	session, ok := database.sessions[sessionID]
	if !ok {
		return models.RefreshSession{}, errors.New("session not found")
	}
	return session, nil
}

func (database *refreshTestDatabase) RotateRefreshSession(
	_ context.Context,
	oldSessionID uuid.UUID,
	session models.RefreshSession,
) error {
	database.mu.Lock()
	defer database.mu.Unlock()
	oldSession, ok := database.sessions[oldSessionID]
	if !ok || oldSession.RevokedAt != nil {
		return errors.New("session not active")
	}
	now := time.Now()
	oldSession.RevokedAt = &now
	database.sessions[oldSessionID] = oldSession
	database.sessions[session.Id] = session
	return nil
}

func (database *refreshTestDatabase) RevokeRefreshSession(
	_ context.Context,
	sessionID uuid.UUID,
) error {
	database.mu.Lock()
	defer database.mu.Unlock()
	session, ok := database.sessions[sessionID]
	if !ok || session.RevokedAt != nil {
		return errors.New("session not active")
	}
	now := time.Now()
	session.RevokedAt = &now
	database.sessions[sessionID] = session
	return nil
}

func TestRefreshTokenRotationPreventsReplay(t *testing.T) {
	t.Parallel()

	user := models.User{Id: uuid.New(), Status: models.UserStatusActive}
	database := &refreshTestDatabase{
		user:     user,
		sessions: make(map[uuid.UUID]models.RefreshSession),
	}
	authenticator := newTestAuthenticator(
		t,
		"test-secret-at-least-32-bytes-long",
		database,
	)

	original, err := authenticator.IssueTokenPair(context.Background(), user.Id)
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}
	rotated, err := authenticator.Refresh(context.Background(), original.RefreshToken)
	if err != nil {
		t.Fatalf("Refresh() error = %v", err)
	}
	if rotated.RefreshToken == original.RefreshToken || rotated.AccessToken == original.AccessToken {
		t.Fatal("Refresh() did not rotate both tokens")
	}

	if _, err = authenticator.Refresh(context.Background(), original.RefreshToken); err == nil {
		t.Fatal("Refresh() accepted a replayed refresh token")
	}
	if err = authenticator.RevokeRefreshToken(context.Background(), rotated.RefreshToken); err != nil {
		t.Fatalf("RevokeRefreshToken() error = %v", err)
	}
	if _, err = authenticator.Refresh(context.Background(), rotated.RefreshToken); err == nil {
		t.Fatal("Refresh() accepted a revoked refresh token")
	}
}

func newTestAuthenticator(
	t *testing.T,
	secret string,
	database adapters.DB,
) *Authenticator {
	t.Helper()

	authenticator, err := New(Config{
		Secret:          secret,
		Issuer:          "test-issuer",
		Audience:        "test-audience",
		AccessTokenTTL:  15 * time.Minute,
		RefreshTokenTTL: 24 * time.Hour,
		Leeway:          0,
	}, database)
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}

	return authenticator
}
