export type ProjectStatus =
  | 'PROJECT_STATUS_UNSPECIFIED'
  | 'PROJECT_STATUS_DRAFT'
  | 'PROJECT_STATUS_PENDING_REVIEW'
  | 'PROJECT_STATUS_PUBLISHED'
  | 'PROJECT_STATUS_REJECTED'
  | 'PROJECT_STATUS_BANNED'

export type UserRole =
  | 'USER_ROLE_UNSPECIFIED'
  | 'USER_ROLE_USER'
  | 'USER_ROLE_MODERATOR'
  | 'USER_ROLE_ADMIN'

export type VersionTag =
  | 'VERSION_TAG_UNSPECIFIED'
  | 'VERSION_TAG_RELEASE'
  | 'VERSION_TAG_BETA'
  | 'VERSION_TAG_ALPHA'

export type PluginEnvironment =
  | 'PLUGIN_ENVIRONMENT_UNSPECIFIED'
  | 'PLUGIN_ENVIRONMENT_SERVER'
  | 'PLUGIN_ENVIRONMENT_CLIENT'
  | 'PLUGIN_ENVIRONMENT_BOTH'

export interface User {
  id: string
  email: string
  username: string
  minecraft_uuid: string
  avatar_url: string
  roles: UserRole[]
  status: 'USER_STATUS_UNSPECIFIED' | 'USER_STATUS_ACTIVE' | 'USER_STATUS_BANNED'
  ban_reason: string
  banned_until?: string
  created_at: string
  updated_at: string
}

export interface TokenPair {
  access_token: string
  refresh_token: string
  access_token_expires_at: string
  refresh_token_expires_at: string
}

export interface AuthResponse {
  user: User
  tokens: TokenPair
}

export interface Project {
  id: string
  slug: string
  name: string
  description: string
  git_url: string
  owner_id: string
  status: ProjectStatus
  status_reason: string
  latest_version: string
  created_at: string
  updated_at: string
}

export interface VersionMetadata {
  slug: string
  description: string
  licenses: string[]
  authors: string[]
  abi_version: string
  api_version: string
  environment: PluginEnvironment
}

export interface ProjectVersion {
  id: string
  project_id: string
  version: string
  description: string
  changelog: string
  readme: string
  tag: VersionTag
  metadata: VersionMetadata
  size_bytes: string
  sha256: string
  created_at: string
  updated_at: string
}

export interface Notification {
  id: string
  recipient_user_id: string
  project_id: string
  review_request_id: string
  text: string
  system: boolean
  read: boolean
  created_at: string
  read_at?: string
}

export interface ProjectReviewRequest {
  id: string
  project_id: string
  submitted_by: string
  status:
    | 'REVIEW_STATUS_UNSPECIFIED'
    | 'REVIEW_STATUS_PENDING'
    | 'REVIEW_STATUS_APPROVED'
    | 'REVIEW_STATUS_REJECTED'
    | 'REVIEW_STATUS_CANCELLED'
  review_comment: string
  reviewed_by: string
  created_at: string
  reviewed_at?: string
}

export interface SearchProjectResult {
  project: Project
  similarity: number
}

export interface ApiErrorBody {
  code?: number
  message?: string
  details?: unknown[]
}

export interface SessionState {
  user: User
  tokens: TokenPair
}
