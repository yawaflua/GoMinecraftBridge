package api

import (
	"context"
	"encoding/base64"
	"errors"
	"net/mail"
	"regexp"
	"strconv"
	"strings"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/fieldmaskpb"
)

const (
	defaultPageSize = 50
	maxPageSize     = 200
	maxArchiveSize  = 64 << 20
	maxAvatarSize   = 1 << 20
)

var (
	usernamePattern = regexp.MustCompile(`^[A-Za-z0-9_.-]{3,32}$`)
	slugPattern     = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*$`)
)

func parseUUID(value, field string) (uuid.UUID, error) {
	parsed, err := uuid.Parse(value)
	if err != nil || parsed == uuid.Nil {
		return uuid.Nil, status.Errorf(codes.InvalidArgument, "%s must be a UUID", field)
	}
	return parsed, nil
}

func validateEmail(value string) error {
	trimmed := strings.TrimSpace(value)
	address, err := mail.ParseAddress(trimmed)
	if err != nil || len(trimmed) > 254 || !strings.EqualFold(address.Address, trimmed) {
		return status.Error(codes.InvalidArgument, "email is invalid")
	}
	return nil
}

func validateUsername(value string) error {
	if !usernamePattern.MatchString(value) {
		return status.Error(
			codes.InvalidArgument,
			"username must contain 3-32 letters, digits, dots, underscores, or hyphens",
		)
	}
	return nil
}

func validateSlug(value string) error {
	if len(value) < 2 || len(value) > 64 || !slugPattern.MatchString(value) {
		return status.Error(codes.InvalidArgument, "slug must be 2-64 lowercase URL-safe characters")
	}
	return nil
}

func validatePassword(value string) error {
	if len(value) < 8 || len(value) > 72 {
		return status.Error(codes.InvalidArgument, "password must contain 8-72 bytes")
	}
	return nil
}

func databaseError(operation string, err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return status.Errorf(codes.NotFound, "%s: resource not found", operation)
	}
	var pgError *pgconn.PgError
	if errors.As(err, &pgError) {
		switch pgError.Code {
		case "23505":
			return status.Errorf(codes.AlreadyExists, "%s: resource already exists", operation)
		case "23503":
			return status.Errorf(codes.FailedPrecondition, "%s: referenced resource does not exist", operation)
		case "23514", "22001":
			return status.Errorf(codes.InvalidArgument, "%s: invalid data", operation)
		}
	}
	return status.Errorf(codes.Internal, "%s failed", operation)
}

func hasRole(user models.User, roles ...string) bool {
	for _, actual := range user.Roles {
		for _, expected := range roles {
			if actual == expected {
				return true
			}
		}
	}
	return false
}

func requireRoles(user models.User, roles ...string) error {
	if !hasRole(user, roles...) {
		return status.Error(codes.PermissionDenied, "insufficient permissions")
	}
	return nil
}

func requireProjectOwnerOrAdmin(user models.User, project models.Project) error {
	if user.Id != project.AuthorId && !hasRole(user, "admin") {
		return status.Error(codes.PermissionDenied, "project can only be changed by its owner or an admin")
	}
	return nil
}

func authenticatedUser(ctx context.Context) (models.User, error) {
	user, ok := auth.UserFromContext(ctx)
	if !ok {
		return models.User{}, status.Error(codes.Unauthenticated, "authenticated user is missing from context")
	}
	return user, nil
}

func normalizedPageSize(requested int32) int {
	if requested <= 0 {
		return defaultPageSize
	}
	return min(int(requested), maxPageSize)
}

func paginate[T any](items []T, pageSize int32, pageToken string) ([]T, string, error) {
	offset := 0
	if pageToken != "" {
		decoded, err := base64.RawURLEncoding.DecodeString(pageToken)
		if err != nil {
			return nil, "", status.Error(codes.InvalidArgument, "page_token is invalid")
		}
		offset, err = strconv.Atoi(string(decoded))
		if err != nil || offset < 0 {
			return nil, "", status.Error(codes.InvalidArgument, "page_token is invalid")
		}
	}
	if offset > len(items) {
		return nil, "", status.Error(codes.InvalidArgument, "page_token is outside the result set")
	}

	end := min(offset+normalizedPageSize(pageSize), len(items))
	nextToken := ""
	if end < len(items) {
		nextToken = base64.RawURLEncoding.EncodeToString([]byte(strconv.Itoa(end)))
	}
	return items[offset:end], nextToken, nil
}

func maskContains(mask *fieldmaskpb.FieldMask, field string) bool {
	if mask == nil || len(mask.Paths) == 0 {
		return true
	}
	for _, path := range mask.Paths {
		if path == field {
			return true
		}
	}
	return false
}

func validateMask(mask *fieldmaskpb.FieldMask, allowed ...string) error {
	if mask == nil {
		return nil
	}
	allowedSet := make(map[string]struct{}, len(allowed))
	for _, field := range allowed {
		allowedSet[field] = struct{}{}
	}
	for _, path := range mask.Paths {
		if _, ok := allowedSet[path]; !ok {
			return status.Errorf(codes.InvalidArgument, "update_mask contains unsupported field %q", path)
		}
	}
	return nil
}

func trigramSimilarity(left, right string) float32 {
	leftSet := trigrams(strings.ToLower(left))
	rightSet := trigrams(strings.ToLower(right))
	if len(leftSet) == 0 && len(rightSet) == 0 {
		return 1
	}
	common := 0
	for item := range leftSet {
		if _, ok := rightSet[item]; ok {
			common++
		}
	}
	return float32(2*common) / float32(len(leftSet)+len(rightSet))
}

func trigrams(value string) map[string]struct{} {
	value = "  " + strings.TrimSpace(value) + " "
	result := make(map[string]struct{})
	for index := 0; index+3 <= len(value); index++ {
		result[value[index:index+3]] = struct{}{}
	}
	return result
}

func optionalUUID(value, field string) (*uuid.UUID, error) {
	if value == "" {
		return nil, nil
	}
	parsed, err := parseUUID(value, field)
	if err != nil {
		return nil, err
	}
	return &parsed, nil
}
