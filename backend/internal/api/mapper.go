package api

import (
	"time"

	projectv1 "github.com/yawaflua/GoMinecraftBridge/backend/gen/project/v1"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/auth"
	"github.com/yawaflua/GoMinecraftBridge/backend/internal/models"
	"github.com/yawaflua/GoMinecraftBridge/sdk"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func userToProto(user models.User) *projectv1.User {
	roles := make([]projectv1.UserRole, 0, len(user.Roles))
	for _, role := range user.Roles {
		switch role {
		case "admin":
			roles = append(roles, projectv1.UserRole_USER_ROLE_ADMIN)
		case "moderator":
			roles = append(roles, projectv1.UserRole_USER_ROLE_MODERATOR)
		case "user":
			roles = append(roles, projectv1.UserRole_USER_ROLE_USER)
		}
	}

	status := projectv1.UserStatus_USER_STATUS_ACTIVE
	if user.Status == models.UserStatusBanned {
		status = projectv1.UserStatus_USER_STATUS_BANNED
	}

	result := &projectv1.User{
		Id:            user.Id.String(),
		Email:         user.EMail,
		Username:      user.Nickname,
		MinecraftUuid: user.MinecraftUUID.String(),
		AvatarUrl:     user.AvatarURL,
		Roles:         roles,
		Status:        status,
		BanReason:     user.BanReason,
		CreatedAt:     timeToProto(user.CreatedAt),
		UpdatedAt:     timeToProto(user.UpdatedAt),
	}
	if user.BannedUntil != nil {
		result.BannedUntil = timeToProto(*user.BannedUntil)
	}
	return result
}

func tokenPairToProto(pair auth.TokenPair) *projectv1.TokenPair {
	return &projectv1.TokenPair{
		AccessToken:           pair.AccessToken,
		RefreshToken:          pair.RefreshToken,
		AccessTokenExpiresAt:  timeToProto(pair.AccessTokenExpiresAt),
		RefreshTokenExpiresAt: timeToProto(pair.RefreshTokenExpiresAt),
	}
}

func projectToProto(project models.Project) *projectv1.Project {
	latestVersion := ""
	if len(project.Versions) > 0 {
		latestVersion = project.Versions[0].Version
	}
	return &projectv1.Project{
		Id:            project.Id.String(),
		Slug:          project.Slug,
		Name:          project.Name,
		Description:   project.Description,
		GitUrl:        project.GitURL,
		OwnerId:       project.AuthorId.String(),
		Status:        projectStatusToProto(project.Status),
		StatusReason:  project.StatusReason,
		LatestVersion: latestVersion,
		CreatedAt:     timeToProto(project.CreatedAt),
		UpdatedAt:     timeToProto(project.UpdatedAt),
	}
}

func projectStatusToProto(status models.ProjectStatus) projectv1.ProjectStatus {
	switch status {
	case models.ProjectStatusDraft:
		return projectv1.ProjectStatus_PROJECT_STATUS_DRAFT
	case models.ProjectStatusPendingReview:
		return projectv1.ProjectStatus_PROJECT_STATUS_PENDING_REVIEW
	case models.ProjectStatusPublished:
		return projectv1.ProjectStatus_PROJECT_STATUS_PUBLISHED
	case models.ProjectStatusRejected:
		return projectv1.ProjectStatus_PROJECT_STATUS_REJECTED
	case models.ProjectStatusBanned:
		return projectv1.ProjectStatus_PROJECT_STATUS_BANNED
	default:
		return projectv1.ProjectStatus_PROJECT_STATUS_UNSPECIFIED
	}
}

func projectStatusFromProto(status projectv1.ProjectStatus) (models.ProjectStatus, bool) {
	switch status {
	case projectv1.ProjectStatus_PROJECT_STATUS_DRAFT:
		return models.ProjectStatusDraft, true
	case projectv1.ProjectStatus_PROJECT_STATUS_PENDING_REVIEW:
		return models.ProjectStatusPendingReview, true
	case projectv1.ProjectStatus_PROJECT_STATUS_PUBLISHED:
		return models.ProjectStatusPublished, true
	case projectv1.ProjectStatus_PROJECT_STATUS_REJECTED:
		return models.ProjectStatusRejected, true
	case projectv1.ProjectStatus_PROJECT_STATUS_BANNED:
		return models.ProjectStatusBanned, true
	default:
		return "", false
	}
}

func versionToProto(version models.Version) *projectv1.ProjectVersion {
	return &projectv1.ProjectVersion{
		Id:          version.Id.String(),
		ProjectId:   version.ProjectId.String(),
		Version:     version.Version,
		Description: version.Description,
		Changelog:   version.Changelog,
		Readme:      version.Readme,
		Tag:         versionTagToProto(version.Tag),
		Metadata:    metadataToProto(version.Metadata),
		SizeBytes:   version.SizeBytes,
		Sha256:      version.SHA256,
		CreatedAt:   timeToProto(version.CreatedAt),
		UpdatedAt:   timeToProto(version.UpdatedAt),
	}
}

func versionTagToProto(tag models.VersionTag) projectv1.VersionTag {
	switch tag {
	case models.Release:
		return projectv1.VersionTag_VERSION_TAG_RELEASE
	case models.Beta:
		return projectv1.VersionTag_VERSION_TAG_BETA
	case models.Alpha:
		return projectv1.VersionTag_VERSION_TAG_ALPHA
	default:
		return projectv1.VersionTag_VERSION_TAG_UNSPECIFIED
	}
}

func versionTagFromProto(tag projectv1.VersionTag) (models.VersionTag, bool) {
	switch tag {
	case projectv1.VersionTag_VERSION_TAG_RELEASE:
		return models.Release, true
	case projectv1.VersionTag_VERSION_TAG_BETA:
		return models.Beta, true
	case projectv1.VersionTag_VERSION_TAG_ALPHA:
		return models.Alpha, true
	default:
		return "", false
	}
}

func metadataToProto(metadata models.VersionMeta) *projectv1.VersionMetadata {
	return &projectv1.VersionMetadata{
		Slug:        metadata.Slug,
		Description: metadata.Description,
		Licenses:    append([]string(nil), metadata.Licence...),
		Authors:     append([]string(nil), metadata.Authors...),
		AbiVersion:  metadata.ABIVersion,
		ApiVersion:  metadata.APIVersion,
		Environment: environmentToProto(metadata.Environment),
	}
}

func metadataFromProto(metadata *projectv1.VersionMetadata) (models.VersionMeta, bool) {
	if metadata == nil {
		return models.VersionMeta{}, false
	}
	environment, ok := environmentFromProto(metadata.Environment)
	if !ok {
		return models.VersionMeta{}, false
	}
	return models.VersionMeta{
		Slug:        metadata.Slug,
		Description: metadata.Description,
		Licence:     append([]string(nil), metadata.Licenses...),
		Authors:     append([]string(nil), metadata.Authors...),
		ABIVersion:  metadata.AbiVersion,
		APIVersion:  metadata.ApiVersion,
		Environment: environment,
	}, true
}

func environmentToProto(environment sdk.PluginEnvironment) projectv1.PluginEnvironment {
	switch environment {
	case sdk.PluginEnvironmentServer:
		return projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_SERVER
	case sdk.PluginEnvironmentClient:
		return projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_CLIENT
	case sdk.PluginEnvironmentBoth:
		return projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_BOTH
	default:
		return projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_UNSPECIFIED
	}
}

func environmentFromProto(environment projectv1.PluginEnvironment) (sdk.PluginEnvironment, bool) {
	switch environment {
	case projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_SERVER:
		return sdk.PluginEnvironmentServer, true
	case projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_CLIENT:
		return sdk.PluginEnvironmentClient, true
	case projectv1.PluginEnvironment_PLUGIN_ENVIRONMENT_BOTH:
		return sdk.PluginEnvironmentBoth, true
	default:
		return "", false
	}
}

func reviewRequestToProto(request models.Request) *projectv1.ProjectReviewRequest {
	status := projectv1.ReviewStatus_REVIEW_STATUS_UNSPECIFIED
	switch request.RequestStatus {
	case models.RequestStatusOpen, models.RequestStatusSubmitted:
		status = projectv1.ReviewStatus_REVIEW_STATUS_PENDING
	case models.RequestStatusClosed:
		status = projectv1.ReviewStatus_REVIEW_STATUS_APPROVED
	case models.RequestStatusRejected:
		status = projectv1.ReviewStatus_REVIEW_STATUS_REJECTED
	case models.RequestStatusCancelled:
		status = projectv1.ReviewStatus_REVIEW_STATUS_CANCELLED
	}
	result := &projectv1.ProjectReviewRequest{
		Id:            request.Id.String(),
		ProjectId:     request.ProjectId.String(),
		SubmittedBy:   request.SubmittedBy.String(),
		Status:        status,
		ReviewComment: request.ReviewComment,
		CreatedAt:     timeToProto(request.CreatedAt),
		ReviewedAt:    timeToProto(request.ClosedAt),
	}
	if request.ReviewedBy != [16]byte{} {
		result.ReviewedBy = request.ReviewedBy.String()
	}
	return result
}

func reviewStatusFromProto(status projectv1.ReviewStatus) (*models.RequestStatus, bool) {
	var result models.RequestStatus
	switch status {
	case projectv1.ReviewStatus_REVIEW_STATUS_UNSPECIFIED:
		return nil, true
	case projectv1.ReviewStatus_REVIEW_STATUS_PENDING:
		result = models.RequestStatusSubmitted
	case projectv1.ReviewStatus_REVIEW_STATUS_APPROVED:
		result = models.RequestStatusClosed
	case projectv1.ReviewStatus_REVIEW_STATUS_REJECTED:
		result = models.RequestStatusRejected
	case projectv1.ReviewStatus_REVIEW_STATUS_CANCELLED:
		result = models.RequestStatusCancelled
	default:
		return nil, false
	}
	return &result, true
}

func notificationToProto(notification models.Notification) *projectv1.Notification {
	result := &projectv1.Notification{
		Id:              notification.Id.String(),
		RecipientUserId: notification.UserId.String(),
		Text:            notification.Text,
		System:          notification.IsSystem,
		Read:            notification.IsRead,
		CreatedAt:       timeToProto(notification.CreatedAt),
	}
	if notification.ProjectId != nil {
		result.ProjectId = notification.ProjectId.String()
	}
	if notification.RequestId != nil {
		result.ReviewRequestId = notification.RequestId.String()
	}
	if notification.ReadAt != nil {
		result.ReadAt = timeToProto(*notification.ReadAt)
	}
	return result
}

func timeToProto(value time.Time) *timestamppb.Timestamp {
	if value.IsZero() {
		return nil
	}
	return timestamppb.New(value)
}
