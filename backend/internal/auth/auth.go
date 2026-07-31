package auth

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/adapters"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const (
	accessTokenType  = "access"
	refreshTokenType = "refresh"
)

var (
	ErrAuthorizationMissing = errors.New("authorization token is missing")
	ErrAuthorizationInvalid = errors.New("authorization token is invalid")
	ErrUserBanned           = errors.New("user is banned")
)

type Config struct {
	Secret          string
	Issuer          string
	Audience        string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
	Leeway          time.Duration
}

type TokenPair struct {
	AccessToken           string
	RefreshToken          string
	AccessTokenExpiresAt  time.Time
	RefreshTokenExpiresAt time.Time
}

type Claims struct {
	TokenType string `json:"token_type"`
	jwt.RegisteredClaims
}

type Authenticator struct {
	db              adapters.DB
	secret          []byte
	issuer          string
	audience        string
	accessTokenTTL  time.Duration
	refreshTokenTTL time.Duration
	leeway          time.Duration
	now             func() time.Time
}

func New(config Config, database adapters.DB) (*Authenticator, error) {
	if database == nil {
		return nil, fmt.Errorf("create authenticator: database is nil")
	}
	if len(config.Secret) < 32 {
		return nil, fmt.Errorf("create authenticator: JWT secret must contain at least 32 bytes")
	}
	if config.AccessTokenTTL <= 0 {
		return nil, fmt.Errorf("create authenticator: access token TTL must be positive")
	}
	if config.RefreshTokenTTL <= 0 {
		return nil, fmt.Errorf("create authenticator: refresh token TTL must be positive")
	}
	if config.Leeway < 0 {
		return nil, fmt.Errorf("create authenticator: JWT leeway cannot be negative")
	}

	return &Authenticator{
		db:              database,
		secret:          []byte(config.Secret),
		issuer:          config.Issuer,
		audience:        config.Audience,
		accessTokenTTL:  config.AccessTokenTTL,
		refreshTokenTTL: config.RefreshTokenTTL,
		leeway:          config.Leeway,
		now:             time.Now,
	}, nil
}

func (authenticator *Authenticator) IssueAccessToken(userID uuid.UUID) (string, time.Time, error) {
	return authenticator.issueToken(userID, accessTokenType, uuid.New(), authenticator.accessTokenTTL)
}

func (authenticator *Authenticator) IssueTokenPair(
	ctx context.Context,
	userID uuid.UUID,
) (TokenPair, error) {
	accessToken, accessExpiresAt, err := authenticator.IssueAccessToken(userID)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue access token for user %s: %w", userID, err)
	}

	refreshID := uuid.New()
	refreshToken, refreshExpiresAt, err := authenticator.issueToken(
		userID,
		refreshTokenType,
		refreshID,
		authenticator.refreshTokenTTL,
	)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue refresh token for user %s: %w", userID, err)
	}

	err = authenticator.db.CreateRefreshSession(ctx, models.RefreshSession{
		Id:        refreshID,
		UserId:    userID,
		TokenHash: tokenHash(refreshToken),
		ExpiresAt: refreshExpiresAt,
	})
	if err != nil {
		return TokenPair{}, fmt.Errorf("persist refresh token %s for user %s: %w", refreshID, userID, err)
	}

	return TokenPair{
		AccessToken:           accessToken,
		RefreshToken:          refreshToken,
		AccessTokenExpiresAt:  accessExpiresAt,
		RefreshTokenExpiresAt: refreshExpiresAt,
	}, nil
}

func (authenticator *Authenticator) Refresh(
	ctx context.Context,
	rawRefreshToken string,
) (TokenPair, error) {
	claims, err := authenticator.parseToken(rawRefreshToken, refreshTokenType)
	if err != nil {
		return TokenPair{}, fmt.Errorf("validate refresh token: %w", err)
	}

	sessionID, userID, err := authenticator.validateRefreshSession(ctx, rawRefreshToken, claims)
	if err != nil {
		return TokenPair{}, fmt.Errorf("validate refresh session: %w", err)
	}

	if _, err = authenticator.loadActiveUser(ctx, userID); err != nil {
		return TokenPair{}, fmt.Errorf("load refresh token user %s: %w", userID, err)
	}

	accessToken, accessExpiresAt, err := authenticator.IssueAccessToken(userID)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue rotated access token for user %s: %w", userID, err)
	}
	newRefreshID := uuid.New()
	newRefreshToken, refreshExpiresAt, err := authenticator.issueToken(
		userID,
		refreshTokenType,
		newRefreshID,
		authenticator.refreshTokenTTL,
	)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue rotated refresh token for user %s: %w", userID, err)
	}

	err = authenticator.db.RotateRefreshSession(ctx, sessionID, models.RefreshSession{
		Id:        newRefreshID,
		UserId:    userID,
		TokenHash: tokenHash(newRefreshToken),
		ExpiresAt: refreshExpiresAt,
	})
	if err != nil {
		return TokenPair{}, fmt.Errorf("rotate refresh session %s: %w", sessionID, err)
	}

	return TokenPair{
		AccessToken:           accessToken,
		RefreshToken:          newRefreshToken,
		AccessTokenExpiresAt:  accessExpiresAt,
		RefreshTokenExpiresAt: refreshExpiresAt,
	}, nil
}

func (authenticator *Authenticator) RevokeRefreshToken(
	ctx context.Context,
	rawRefreshToken string,
) error {
	claims, err := authenticator.parseToken(rawRefreshToken, refreshTokenType)
	if err != nil {
		return fmt.Errorf("validate refresh token before revocation: %w", err)
	}
	sessionID, _, err := authenticator.validateRefreshSession(ctx, rawRefreshToken, claims)
	if err != nil {
		return fmt.Errorf("validate refresh session before revocation: %w", err)
	}
	if err = authenticator.db.RevokeRefreshSession(ctx, sessionID); err != nil {
		return fmt.Errorf("revoke refresh session %s: %w", sessionID, err)
	}
	return nil
}

func (authenticator *Authenticator) issueToken(
	userID uuid.UUID,
	tokenType string,
	tokenID uuid.UUID,
	ttl time.Duration,
) (string, time.Time, error) {
	now := authenticator.now()
	expiresAt := now.Add(ttl)
	var audience jwt.ClaimStrings
	if authenticator.audience != "" {
		audience = jwt.ClaimStrings{authenticator.audience}
	}
	claims := Claims{
		TokenType: tokenType,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    authenticator.issuer,
			Subject:   userID.String(),
			Audience:  audience,
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			NotBefore: jwt.NewNumericDate(now),
			IssuedAt:  jwt.NewNumericDate(now),
			ID:        tokenID.String(),
		},
	}

	signedToken, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(authenticator.secret)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("sign %s token for user %s: %w", tokenType, userID, err)
	}

	return signedToken, expiresAt, nil
}

func (authenticator *Authenticator) AuthenticateAuthorization(
	ctx context.Context,
	authorization string,
) (context.Context, error) {
	rawToken, err := bearerToken(authorization)
	if err != nil {
		return nil, fmt.Errorf("extract bearer token: %w", err)
	}

	claims, err := authenticator.parseToken(rawToken, accessTokenType)
	if err != nil {
		return nil, fmt.Errorf("%w: validate access JWT: %w", ErrAuthorizationInvalid, err)
	}

	userID, err := uuid.Parse(claims.Subject)
	if err != nil {
		return nil, fmt.Errorf("%w: parse subject as user ID: %w", ErrAuthorizationInvalid, err)
	}

	user, err := authenticator.loadActiveUser(ctx, userID)
	if err != nil {
		return nil, fmt.Errorf("%w: authenticate user %s: %w", ErrAuthorizationInvalid, userID, err)
	}

	return ContextWithUser(ctx, user), nil
}

func (authenticator *Authenticator) parseToken(rawToken, expectedType string) (*Claims, error) {
	claims := &Claims{}
	options := []jwt.ParserOption{
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		jwt.WithExpirationRequired(),
		jwt.WithIssuedAt(),
		jwt.WithLeeway(authenticator.leeway),
		jwt.WithTimeFunc(authenticator.now),
	}
	if authenticator.issuer != "" {
		options = append(options, jwt.WithIssuer(authenticator.issuer))
	}
	if authenticator.audience != "" {
		options = append(options, jwt.WithAudience(authenticator.audience))
	}

	token, err := jwt.ParseWithClaims(
		rawToken,
		claims,
		func(token *jwt.Token) (any, error) {
			if token.Method != jwt.SigningMethodHS256 {
				return nil, fmt.Errorf(
					"validate JWT signing method: expected %s, got %s",
					jwt.SigningMethodHS256.Alg(),
					token.Method.Alg(),
				)
			}
			return authenticator.secret, nil
		},
		options...,
	)
	if err != nil {
		return nil, fmt.Errorf("parse and validate JWT: %w", err)
	}
	if token == nil || !token.Valid {
		return nil, fmt.Errorf("JWT is not valid")
	}
	if claims.TokenType != expectedType {
		return nil, fmt.Errorf(
			"expected token type %q, got %q",
			expectedType,
			claims.TokenType,
		)
	}
	return claims, nil
}

func (authenticator *Authenticator) validateRefreshSession(
	ctx context.Context,
	rawToken string,
	claims *Claims,
) (uuid.UUID, uuid.UUID, error) {
	sessionID, err := uuid.Parse(claims.ID)
	if err != nil {
		return uuid.Nil, uuid.Nil, fmt.Errorf("parse refresh session ID: %w", err)
	}
	userID, err := uuid.Parse(claims.Subject)
	if err != nil {
		return uuid.Nil, uuid.Nil, fmt.Errorf("parse refresh user ID: %w", err)
	}

	session, err := authenticator.db.GetRefreshSession(ctx, sessionID)
	if err != nil {
		return uuid.Nil, uuid.Nil, fmt.Errorf("load refresh session %s: %w", sessionID, err)
	}
	if session.UserId != userID {
		return uuid.Nil, uuid.Nil, fmt.Errorf("refresh session user does not match token subject")
	}
	if session.RevokedAt != nil {
		return uuid.Nil, uuid.Nil, fmt.Errorf("refresh session %s is revoked", sessionID)
	}
	if !session.ExpiresAt.After(authenticator.now()) {
		return uuid.Nil, uuid.Nil, fmt.Errorf("refresh session %s is expired", sessionID)
	}
	actualHash := tokenHash(rawToken)
	if subtle.ConstantTimeCompare([]byte(actualHash), []byte(session.TokenHash)) != 1 {
		return uuid.Nil, uuid.Nil, fmt.Errorf("refresh token hash does not match session %s", sessionID)
	}

	return sessionID, userID, nil
}

func (authenticator *Authenticator) loadActiveUser(
	ctx context.Context,
	userID uuid.UUID,
) (models.User, error) {
	user, err := authenticator.db.GetUserById(ctx, userID)
	if err != nil {
		return models.User{}, fmt.Errorf("load user %s from database: %w", userID, err)
	}
	if user.Status == models.UserStatusBanned &&
		(user.BannedUntil == nil || user.BannedUntil.After(authenticator.now())) {
		return models.User{}, fmt.Errorf("%w: user %s", ErrUserBanned, userID)
	}
	return user, nil
}

func tokenHash(token string) string {
	hash := sha256.Sum256([]byte(token))
	return hex.EncodeToString(hash[:])
}

func bearerToken(authorization string) (string, error) {
	parts := strings.Fields(authorization)
	if len(parts) == 0 {
		return "", fmt.Errorf("%w", ErrAuthorizationMissing)
	}
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") || parts[1] == "" {
		return "", fmt.Errorf("%w: expected Authorization: Bearer <token>", ErrAuthorizationInvalid)
	}

	return parts[1], nil
}

type userContextKey struct{}

func ContextWithUser(ctx context.Context, user models.User) context.Context {
	return context.WithValue(ctx, userContextKey{}, user)
}

func UserFromContext(ctx context.Context) (models.User, bool) {
	user, ok := ctx.Value(userContextKey{}).(models.User)
	return user, ok
}

func RequireUser(ctx context.Context) (models.User, error) {
	user, ok := UserFromContext(ctx)
	if !ok {
		return models.User{}, status.Error(codes.Unauthenticated, "authenticated user is missing from context")
	}

	return user, nil
}
