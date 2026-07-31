package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strings"

	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

var authenticationRPCMethods = map[string]struct{}{
	projectv1.GBMBackend_Register_FullMethodName:     {},
	projectv1.GBMBackend_Login_FullMethodName:        {},
	projectv1.GBMBackend_RefreshToken_FullMethodName: {},
	projectv1.GBMBackend_Logout_FullMethodName:       {},
}

var publicRPCMethods = map[string]struct{}{
	projectv1.GBMBackend_CheckSlugAvailability_FullMethodName:  {},
	projectv1.GBMBackend_GetProject_FullMethodName:             {},
	projectv1.GBMBackend_SearchProjects_FullMethodName:         {},
	projectv1.GBMBackend_ListProjectVersions_FullMethodName:    {},
	projectv1.GBMBackend_GetProjectVersion_FullMethodName:      {},
	projectv1.GBMBackend_DownloadProjectVersion_FullMethodName: {},
	projectv1.GBMBackend_CheckNewVersions_FullMethodName:       {},
}

func (authenticator *Authenticator) UnaryServerInterceptor(
	ctx context.Context,
	request any,
	info *grpc.UnaryServerInfo,
	handler grpc.UnaryHandler,
) (any, error) {
	if _, public := authenticationRPCMethods[info.FullMethod]; public {
		return handler(ctx, request)
	}
	_, public := publicRPCMethods[info.FullMethod]
	authorization, hasAuthorization := authorizationFromIncomingContext(ctx)
	if public && !hasAuthorization {
		return handler(ctx, request)
	}
	if !hasAuthorization {
		return nil, status.Error(codes.Unauthenticated, "authorization token is required")
	}

	authenticatedContext, err := authenticator.AuthenticateAuthorization(ctx, authorization)
	if err != nil {
		return nil, authenticationStatus(err)
	}

	return handler(authenticatedContext, request)
}

func (authenticator *Authenticator) HTTPMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		authorization := request.Header.Get("Authorization")
		if strings.HasPrefix(request.URL.Path, "/v1/auth/") {
			next.ServeHTTP(writer, request)
			return
		}
		if isPublicHTTPRequest(request) && authorization == "" {
			next.ServeHTTP(writer, request)
			return
		}
		if authorization == "" {
			writeAuthenticationError(writer, status.Error(codes.Unauthenticated, "authorization token is required"))
			return
		}

		authenticatedContext, err := authenticator.AuthenticateAuthorization(request.Context(), authorization)
		if err != nil {
			writeAuthenticationError(writer, authenticationStatus(err))
			return
		}

		next.ServeHTTP(writer, request.WithContext(authenticatedContext))
	})
}

func authorizationFromIncomingContext(ctx context.Context) (string, bool) {
	incomingMetadata, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return "", false
	}

	values := incomingMetadata.Get("authorization")
	if len(values) == 0 || strings.TrimSpace(values[0]) == "" {
		return "", false
	}

	return values[0], true
}

func isPublicHTTPRequest(request *http.Request) bool {
	if request.Method == http.MethodGet && strings.HasPrefix(request.URL.Path, "/v1/projects") {
		return true
	}
	return request.Method == http.MethodPost &&
		request.URL.Path == "/v1/projects/@all/versions/check"
}

func authenticationStatus(err error) error {
	if errors.Is(err, ErrUserBanned) {
		return status.Error(codes.PermissionDenied, "user is banned")
	}

	return status.Error(codes.Unauthenticated, "invalid or expired authorization token")
}

func writeAuthenticationError(writer http.ResponseWriter, err error) {
	code := status.Code(err)
	httpStatus := http.StatusUnauthorized
	if code == codes.PermissionDenied {
		httpStatus = http.StatusForbidden
	}

	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(httpStatus)
	if encodeErr := json.NewEncoder(writer).Encode(map[string]string{
		"code":    strings.ToLower(code.String()),
		"message": status.Convert(err).Message(),
	}); encodeErr != nil {
		slog.Error("failed to encode HTTP authentication error", "error", fmt.Errorf("encode response: %w", encodeErr))
	}
}
