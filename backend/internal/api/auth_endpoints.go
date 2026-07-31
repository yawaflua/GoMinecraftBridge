package api

import (
	"context"
	"encoding/base64"
	"fmt"
	"strings"
	"time"

	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"golang.org/x/crypto/bcrypt"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"
)

func (service *Service) Register(
	ctx context.Context,
	request *projectv1.RegisterRequest,
) (*projectv1.AuthResponse, error) {
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	if err := validateEmail(request.Email); err != nil {
		return nil, err
	}
	if err := validateUsername(request.Username); err != nil {
		return nil, err
	}
	if err := validatePassword(request.Password); err != nil {
		return nil, err
	}
	minecraftUUID, err := parseUUID(request.MinecraftUuid, "minecraft_uuid")
	if err != nil {
		return nil, err
	}

	passwordHash, err := bcrypt.GenerateFromPassword([]byte(request.Password), bcrypt.DefaultCost)
	if err != nil {
		return nil, status.Error(codes.Internal, "password hashing failed")
	}
	user, err := service.db.CreateUser(
		ctx,
		strings.TrimSpace(request.Email),
		request.Username,
		string(passwordHash),
		minecraftUUID,
	)
	if err != nil {
		return nil, databaseError("create user", err)
	}

	tokens, err := service.authenticator.IssueTokenPair(ctx, user.Id)
	if err != nil {
		return nil, status.Error(codes.Internal, "token issuance failed")
	}
	return &projectv1.AuthResponse{
		User:   userToProto(user),
		Tokens: tokenPairToProto(tokens),
	}, nil
}

func (service *Service) Login(
	ctx context.Context,
	request *projectv1.LoginRequest,
) (*projectv1.AuthResponse, error) {
	if request == nil || request.Password == "" {
		return nil, status.Error(codes.InvalidArgument, "login and password are required")
	}

	var (
		userErr error
		user    models.User
	)
	switch login := request.Login.(type) {
	case *projectv1.LoginRequest_Email:
		if err := validateEmail(login.Email); err != nil {
			return nil, err
		}
		user, userErr = service.db.GetUserByEmail(ctx, login.Email)
	case *projectv1.LoginRequest_Username:
		if err := validateUsername(login.Username); err != nil {
			return nil, err
		}
		user, userErr = service.db.GetUserByUsername(ctx, login.Username)
	default:
		return nil, status.Error(codes.InvalidArgument, "email or username login is required")
	}
	if userErr != nil || bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(request.Password)) != nil {
		return nil, status.Error(codes.Unauthenticated, "invalid login or password")
	}
	if user.Status == models.UserStatusBanned &&
		(user.BannedUntil == nil || user.BannedUntil.After(time.Now())) {
		return nil, status.Error(codes.PermissionDenied, "user is banned")
	}

	tokens, err := service.authenticator.IssueTokenPair(ctx, user.Id)
	if err != nil {
		return nil, status.Error(codes.Internal, "token issuance failed")
	}
	return &projectv1.AuthResponse{
		User:   userToProto(user),
		Tokens: tokenPairToProto(tokens),
	}, nil
}

func (service *Service) RefreshToken(
	ctx context.Context,
	request *projectv1.RefreshTokenRequest,
) (*projectv1.TokenPair, error) {
	if request == nil || request.RefreshToken == "" {
		return nil, status.Error(codes.InvalidArgument, "refresh_token is required")
	}
	pair, err := service.authenticator.Refresh(ctx, request.RefreshToken)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "refresh token is invalid or expired")
	}
	return tokenPairToProto(pair), nil
}

func (service *Service) Logout(
	ctx context.Context,
	request *projectv1.LogoutRequest,
) (*emptypb.Empty, error) {
	if request == nil || request.RefreshToken == "" {
		return nil, status.Error(codes.InvalidArgument, "refresh_token is required")
	}
	if err := service.authenticator.RevokeRefreshToken(ctx, request.RefreshToken); err != nil {
		return nil, status.Error(codes.Unauthenticated, "refresh token is invalid or expired")
	}
	return &emptypb.Empty{}, nil
}

func (service *Service) GetCurrentUser(
	ctx context.Context,
	_ *projectv1.GetCurrentUserRequest,
) (*projectv1.User, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	return userToProto(user), nil
}

func (service *Service) UpdateCurrentUser(
	ctx context.Context,
	request *projectv1.UpdateCurrentUserRequest,
) (*projectv1.User, error) {
	current, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil || request.User == nil {
		return nil, status.Error(codes.InvalidArgument, "user update is required")
	}
	if request.UpdateMask == nil || len(request.UpdateMask.Paths) == 0 {
		return nil, status.Error(codes.InvalidArgument, "update_mask is required")
	}
	if err = validateMask(
		request.UpdateMask,
		"email", "username", "password", "minecraft_uuid", "avatar", "avatar_content_type",
	); err != nil {
		return nil, err
	}

	email := current.EMail
	username := current.Nickname
	minecraftUUID := current.MinecraftUUID
	avatarURL := current.AvatarURL
	passwordHash := ""

	if maskContains(request.UpdateMask, "email") {
		if err = validateEmail(request.User.Email); err != nil {
			return nil, err
		}
		email = strings.TrimSpace(request.User.Email)
	}
	if maskContains(request.UpdateMask, "username") {
		if err = validateUsername(request.User.Username); err != nil {
			return nil, err
		}
		username = request.User.Username
	}
	if maskContains(request.UpdateMask, "minecraft_uuid") {
		minecraftUUID, err = parseUUID(request.User.MinecraftUuid, "minecraft_uuid")
		if err != nil {
			return nil, err
		}
	}
	if maskContains(request.UpdateMask, "password") {
		if err = validatePassword(request.User.Password); err != nil {
			return nil, err
		}
		hash, hashErr := bcrypt.GenerateFromPassword([]byte(request.User.Password), bcrypt.DefaultCost)
		if hashErr != nil {
			return nil, status.Error(codes.Internal, "password hashing failed")
		}
		passwordHash = string(hash)
	}
	if maskContains(request.UpdateMask, "avatar") || maskContains(request.UpdateMask, "avatar_content_type") {
		if len(request.User.Avatar) == 0 || len(request.User.Avatar) > maxAvatarSize {
			return nil, status.Error(codes.InvalidArgument, "avatar must contain 1 byte to 1 MiB")
		}
		contentType := strings.ToLower(request.User.AvatarContentType)
		switch contentType {
		case "image/png", "image/jpeg", "image/webp":
		default:
			return nil, status.Error(codes.InvalidArgument, "avatar_content_type must be PNG, JPEG, or WebP")
		}
		avatarURL = fmt.Sprintf(
			"data:%s;base64,%s",
			contentType,
			base64.StdEncoding.EncodeToString(request.User.Avatar),
		)
	}

	updated, err := service.db.EditUser(
		ctx,
		current.Id,
		email,
		username,
		passwordHash,
		minecraftUUID,
		avatarURL,
	)
	if err != nil {
		return nil, databaseError("update user", err)
	}
	return userToProto(updated), nil
}
