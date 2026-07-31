package api

import (
	"context"
	"testing"
	"time"

	"github.com/google/uuid"
	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"golang.org/x/crypto/bcrypt"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type endpointAuthDatabase struct {
	adapters.DB
	user     models.User
	sessions map[uuid.UUID]models.RefreshSession
}

func (database *endpointAuthDatabase) CreateUser(
	_ context.Context,
	email, username, passwordHash string,
	minecraftUUID uuid.UUID,
) (models.User, error) {
	database.user = models.User{
		Id:            uuid.New(),
		EMail:         email,
		Nickname:      username,
		PasswordHash:  passwordHash,
		MinecraftUUID: minecraftUUID,
		Roles:         []string{"user"},
		Status:        models.UserStatusActive,
		CreatedAt:     time.Now(),
		UpdatedAt:     time.Now(),
	}
	return database.user, nil
}

func (database *endpointAuthDatabase) GetUserById(
	_ context.Context,
	userID uuid.UUID,
) (models.User, error) {
	if database.user.Id != userID {
		return models.User{}, context.Canceled
	}
	return database.user, nil
}

func (database *endpointAuthDatabase) GetUserByEmail(
	_ context.Context,
	email string,
) (models.User, error) {
	if database.user.EMail != email {
		return models.User{}, context.Canceled
	}
	return database.user, nil
}

func (database *endpointAuthDatabase) GetUserByUsername(
	_ context.Context,
	username string,
) (models.User, error) {
	if database.user.Nickname != username {
		return models.User{}, context.Canceled
	}
	return database.user, nil
}

func (database *endpointAuthDatabase) CreateRefreshSession(
	_ context.Context,
	session models.RefreshSession,
) error {
	database.sessions[session.Id] = session
	return nil
}

func TestRegisterHashesPasswordAndReturnsTokenPair(t *testing.T) {
	t.Parallel()

	database := &endpointAuthDatabase{sessions: make(map[uuid.UUID]models.RefreshSession)}
	authenticator := newEndpointAuthenticator(t, database)
	service := NewService(ServiceDependencies{DB: database, Authenticator: authenticator})
	minecraftUUID := uuid.New()

	response, err := service.Register(context.Background(), &projectv1.RegisterRequest{
		Email:         "player@example.com",
		Username:      "player_one",
		Password:      "correct horse battery staple",
		MinecraftUuid: minecraftUUID.String(),
	})
	if err != nil {
		t.Fatalf("Register() error = %v", err)
	}
	if response.GetUser().GetId() == "" {
		t.Fatal("Register() returned an empty user ID")
	}
	if response.GetTokens().GetAccessToken() == "" || response.GetTokens().GetRefreshToken() == "" {
		t.Fatal("Register() returned an incomplete token pair")
	}
	if database.user.PasswordHash == "correct horse battery staple" {
		t.Fatal("Register() stored the plaintext password")
	}
	if err = bcrypt.CompareHashAndPassword(
		[]byte(database.user.PasswordHash),
		[]byte("correct horse battery staple"),
	); err != nil {
		t.Fatalf("stored password hash does not match: %v", err)
	}
	if len(database.sessions) != 1 {
		t.Fatalf("refresh session count = %d, want 1", len(database.sessions))
	}
}

func TestLoginRejectsWrongPasswordAndActiveBan(t *testing.T) {
	t.Parallel()

	hash, err := bcrypt.GenerateFromPassword([]byte("correct-password"), bcrypt.MinCost)
	if err != nil {
		t.Fatalf("GenerateFromPassword() error = %v", err)
	}
	database := &endpointAuthDatabase{
		user: models.User{
			Id:           uuid.New(),
			EMail:        "player@example.com",
			Nickname:     "player_one",
			PasswordHash: string(hash),
			Status:       models.UserStatusActive,
		},
		sessions: make(map[uuid.UUID]models.RefreshSession),
	}
	service := NewService(ServiceDependencies{
		DB:            database,
		Authenticator: newEndpointAuthenticator(t, database),
	})

	_, err = service.Login(context.Background(), &projectv1.LoginRequest{
		Login:    &projectv1.LoginRequest_Email{Email: database.user.EMail},
		Password: "wrong-password",
	})
	if status.Code(err) != codes.Unauthenticated {
		t.Fatalf("Login() wrong-password code = %s, want %s", status.Code(err), codes.Unauthenticated)
	}

	database.user.Status = models.UserStatusBanned
	_, err = service.Login(context.Background(), &projectv1.LoginRequest{
		Login:    &projectv1.LoginRequest_Username{Username: database.user.Nickname},
		Password: "correct-password",
	})
	if status.Code(err) != codes.PermissionDenied {
		t.Fatalf("Login() banned-user code = %s, want %s", status.Code(err), codes.PermissionDenied)
	}
	if len(database.sessions) != 0 {
		t.Fatalf("banned login created %d refresh sessions, want 0", len(database.sessions))
	}
}

func newEndpointAuthenticator(
	t *testing.T,
	database adapters.DB,
) *auth.Authenticator {
	t.Helper()

	authenticator, err := auth.New(auth.Config{
		Secret:          "endpoint-test-secret-at-least-32-bytes-long",
		Issuer:          "endpoint-test",
		Audience:        "endpoint-test",
		AccessTokenTTL:  15 * time.Minute,
		RefreshTokenTTL: 24 * time.Hour,
	}, database)
	if err != nil {
		t.Fatalf("auth.New() error = %v", err)
	}
	return authenticator
}
