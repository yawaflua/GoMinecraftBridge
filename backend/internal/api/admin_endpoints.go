package api

import (
	"context"
	"strings"
	"time"

	"github.com/google/uuid"
	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"
)

func (service *Service) ListNotifications(
	ctx context.Context,
	request *projectv1.ListNotificationsRequest,
) (*projectv1.ListNotificationsResponse, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil {
		request = &projectv1.ListNotificationsRequest{}
	}
	notifications, err := service.db.GetNotifications(ctx, user.Id)
	if err != nil {
		return nil, databaseError("list notifications", err)
	}

	unreadCount := 0
	filtered := make([]models.Notification, 0, len(notifications))
	for _, notification := range notifications {
		if !notification.IsRead {
			unreadCount++
		}
		if !request.UnreadOnly || !notification.IsRead {
			filtered = append(filtered, notification)
		}
	}
	page, nextToken, err := paginate(filtered, request.PageSize, request.PageToken)
	if err != nil {
		return nil, err
	}
	result := make([]*projectv1.Notification, 0, len(page))
	for _, notification := range page {
		result = append(result, notificationToProto(notification))
	}
	return &projectv1.ListNotificationsResponse{
		Notifications: result,
		NextPageToken: nextToken,
		UnreadCount:   int32(unreadCount),
	}, nil
}

func (service *Service) MarkNotificationsRead(
	ctx context.Context,
	request *projectv1.MarkNotificationsReadRequest,
) (*projectv1.MarkNotificationsReadResponse, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if request == nil {
		request = &projectv1.MarkNotificationsReadRequest{}
	}
	ids := make([]uuid.UUID, 0, len(request.NotificationIds))
	for _, rawID := range request.NotificationIds {
		id, parseErr := parseUUID(rawID, "notification_ids")
		if parseErr != nil {
			return nil, parseErr
		}
		ids = append(ids, id)
	}
	notifications, err := service.db.ReadNotifications(ctx, user.Id, ids)
	if err != nil {
		return nil, databaseError("mark notifications read", err)
	}
	result := make([]*projectv1.Notification, 0, len(notifications))
	for _, notification := range notifications {
		result = append(result, notificationToProto(notification))
	}
	return &projectv1.MarkNotificationsReadResponse{Notifications: result}, nil
}

func (service *Service) ListProjectReviewRequests(
	ctx context.Context,
	request *projectv1.ListProjectReviewRequestsRequest,
) (*projectv1.ListProjectReviewRequestsResponse, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if err = requireRoles(user, "admin", "moderator"); err != nil {
		return nil, err
	}
	if request == nil {
		request = &projectv1.ListProjectReviewRequestsRequest{}
	}
	filter, ok := reviewStatusFromProto(request.Status)
	if !ok {
		return nil, status.Error(codes.InvalidArgument, "review status is invalid")
	}
	requests, err := service.db.GetProjectReviewRequests(ctx, filter)
	if err != nil {
		return nil, databaseError("list project review requests", err)
	}
	page, nextToken, err := paginate(requests, request.PageSize, request.PageToken)
	if err != nil {
		return nil, err
	}
	result := make([]*projectv1.ProjectReviewRequest, 0, len(page))
	for _, reviewRequest := range page {
		result = append(result, reviewRequestToProto(reviewRequest))
	}
	return &projectv1.ListProjectReviewRequestsResponse{
		ReviewRequests: result,
		NextPageToken:  nextToken,
	}, nil
}

func (service *Service) ReviewProject(
	ctx context.Context,
	request *projectv1.ReviewProjectRequest,
) (*projectv1.ProjectReviewRequest, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if err = requireRoles(user, "admin", "moderator"); err != nil {
		return nil, err
	}
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	requestID, err := parseUUID(request.ReviewRequestId, "review_request_id")
	if err != nil {
		return nil, err
	}
	var terminalStatus models.RequestStatus
	switch request.Decision {
	case projectv1.ReviewDecision_REVIEW_DECISION_APPROVE:
		terminalStatus = models.RequestStatusClosed
	case projectv1.ReviewDecision_REVIEW_DECISION_REJECT:
		if strings.TrimSpace(request.Comment) == "" {
			return nil, status.Error(codes.InvalidArgument, "rejection comment is required")
		}
		terminalStatus = models.RequestStatusRejected
	default:
		return nil, status.Error(codes.InvalidArgument, "review decision is required")
	}
	reviewed, err := service.db.ReviewProject(ctx, requestID, user.Id, terminalStatus, request.Comment)
	if err != nil {
		return nil, databaseError("review project", err)
	}
	return reviewRequestToProto(reviewed), nil
}

func (service *Service) UpdateUserRoles(
	ctx context.Context,
	request *projectv1.UpdateUserRolesRequest,
) (*projectv1.User, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if err = requireRoles(user, "admin"); err != nil {
		return nil, err
	}
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	userID, err := parseUUID(request.UserId, "user_id")
	if err != nil {
		return nil, err
	}

	roles := make([]string, 0, len(request.Roles))
	seen := make(map[string]struct{})
	for _, protoRole := range request.Roles {
		var role string
		switch protoRole {
		case projectv1.UserRole_USER_ROLE_USER:
			role = "user"
		case projectv1.UserRole_USER_ROLE_MODERATOR:
			role = "moderator"
		case projectv1.UserRole_USER_ROLE_ADMIN:
			role = "admin"
		default:
			return nil, status.Error(codes.InvalidArgument, "roles contain an unspecified value")
		}
		if _, exists := seen[role]; !exists {
			seen[role] = struct{}{}
			roles = append(roles, role)
		}
	}
	if len(roles) == 0 {
		return nil, status.Error(codes.InvalidArgument, "at least one role is required")
	}
	if err = service.db.ChangeUserRole(ctx, userID, roles); err != nil {
		return nil, databaseError("update user roles", err)
	}
	updated, err := service.db.GetUserById(ctx, userID)
	if err != nil {
		return nil, databaseError("get user after role update", err)
	}
	return userToProto(updated), nil
}

func (service *Service) SetUserBan(
	ctx context.Context,
	request *projectv1.SetUserBanRequest,
) (*projectv1.User, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if err = requireRoles(user, "admin"); err != nil {
		return nil, err
	}
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	userID, err := parseUUID(request.UserId, "user_id")
	if err != nil {
		return nil, err
	}
	if request.Banned && strings.TrimSpace(request.Reason) == "" {
		return nil, status.Error(codes.InvalidArgument, "ban reason is required")
	}
	var bannedUntil *time.Time
	if request.BannedUntil != nil {
		if err = request.BannedUntil.CheckValid(); err != nil {
			return nil, status.Error(codes.InvalidArgument, "banned_until is invalid")
		}
		value := request.BannedUntil.AsTime()
		if request.Banned && !value.After(time.Now()) {
			return nil, status.Error(codes.InvalidArgument, "banned_until must be in the future")
		}
		bannedUntil = &value
	}
	updated, err := service.db.SetUserBan(
		ctx,
		userID,
		request.Banned,
		request.Reason,
		bannedUntil,
	)
	if err != nil {
		return nil, databaseError("set user ban", err)
	}
	return userToProto(updated), nil
}

func (service *Service) SetProjectStatus(
	ctx context.Context,
	request *projectv1.SetProjectStatusRequest,
) (*projectv1.Project, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if err = requireRoles(user, "admin"); err != nil {
		return nil, err
	}
	if request == nil {
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	projectID, err := parseUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	projectStatus, ok := projectStatusFromProto(request.Status)
	if !ok {
		return nil, status.Error(codes.InvalidArgument, "project status is invalid")
	}
	if (projectStatus == models.ProjectStatusRejected || projectStatus == models.ProjectStatusBanned) &&
		strings.TrimSpace(request.Reason) == "" {
		return nil, status.Error(codes.InvalidArgument, "status reason is required")
	}
	project, err := service.db.SetProjectStatus(ctx, projectID, projectStatus, request.Reason)
	if err != nil {
		return nil, databaseError("set project status", err)
	}
	return projectToProto(project), nil
}

func (service *Service) DeleteProjectVersion(
	ctx context.Context,
	request *projectv1.DeleteProjectVersionRequest,
) (*emptypb.Empty, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if err = requireRoles(user, "admin"); err != nil {
		return nil, err
	}
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
	if strings.TrimSpace(request.Reason) == "" {
		return nil, status.Error(codes.InvalidArgument, "deletion reason is required")
	}
	if err = service.db.DeleteProjectVersion(ctx, projectID, versionID); err != nil {
		return nil, databaseError("delete project version", err)
	}
	return &emptypb.Empty{}, nil
}

func (service *Service) CreateNotification(
	ctx context.Context,
	request *projectv1.CreateNotificationRequest,
) (*projectv1.CreateNotificationResponse, error) {
	user, err := authenticatedUser(ctx)
	if err != nil {
		return nil, err
	}
	if err = requireRoles(user, "admin", "moderator"); err != nil {
		return nil, err
	}
	if request == nil || strings.TrimSpace(request.Text) == "" {
		return nil, status.Error(codes.InvalidArgument, "notification text is required")
	}
	if len(request.Text) > 10_000 {
		return nil, status.Error(codes.InvalidArgument, "notification text exceeds 10000 characters")
	}

	projectID, err := optionalUUID(request.ProjectId, "project_id")
	if err != nil {
		return nil, err
	}
	reviewRequestID, err := optionalUUID(request.ReviewRequestId, "review_request_id")
	if err != nil {
		return nil, err
	}

	var notifications []models.Notification
	switch request.Audience {
	case projectv1.NotificationAudience_NOTIFICATION_AUDIENCE_ALL_USERS:
		notifications, err = service.db.CreateGlobalNotification(ctx, user.Id, request.Text)
	case projectv1.NotificationAudience_NOTIFICATION_AUDIENCE_USER:
		userID, parseErr := parseUUID(request.UserId, "user_id")
		if parseErr != nil {
			return nil, parseErr
		}
		notification, createErr := service.db.CreateUserNotification(
			ctx,
			user.Id,
			userID,
			projectID,
			reviewRequestID,
			request.Text,
		)
		err = createErr
		if createErr == nil {
			notifications = []models.Notification{notification}
		}
	case projectv1.NotificationAudience_NOTIFICATION_AUDIENCE_PROJECT_OWNER:
		if projectID == nil {
			return nil, status.Error(codes.InvalidArgument, "project_id is required for project owner audience")
		}
		notification, createErr := service.db.CreateProjectNotification(
			ctx,
			user.Id,
			*projectID,
			reviewRequestID,
			request.Text,
		)
		err = createErr
		if createErr == nil {
			notifications = []models.Notification{notification}
		}
	default:
		return nil, status.Error(codes.InvalidArgument, "notification audience is required")
	}
	if err != nil {
		return nil, databaseError("create notification", err)
	}

	result := make([]*projectv1.Notification, 0, len(notifications))
	for _, notification := range notifications {
		result = append(result, notificationToProto(notification))
	}
	return &projectv1.CreateNotificationResponse{Notifications: result}, nil
}
