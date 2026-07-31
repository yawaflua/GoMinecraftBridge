import type {
  AuthResponse,
  Notification,
  PluginEnvironment,
  Project,
  ProjectReviewRequest,
  ProjectVersion,
  SearchProjectResult,
  SessionState,
  User,
  VersionTag,
} from '../types'
import { translate } from './i18n'

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const SESSION_KEY = 'bridgemods.session'

export class ApiError extends Error {
  status: number
  code?: number

  constructor(message: string, status: number, code?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

function readSession(): SessionState | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as SessionState) : null
  } catch {
    return null
  }
}

export function saveStoredSession(value: SessionState | null) {
  if (value) localStorage.setItem(SESSION_KEY, JSON.stringify(value))
  else localStorage.removeItem(SESSION_KEY)
}

let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  if (refreshing) return refreshing
  const current = readSession()
  if (!current?.tokens.refresh_token) return null

  refreshing = fetch(`${API_BASE}/v1/auth/token:refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refresh_token: current.tokens.refresh_token }),
  })
    .then(async (response) => {
      if (!response.ok) throw new Error('refresh failed')
      const tokens = await response.json()
      saveStoredSession({ ...current, tokens })
      window.dispatchEvent(new CustomEvent('bridgemods:session', { detail: { ...current, tokens } }))
      return tokens.access_token as string
    })
    .catch(() => {
      saveStoredSession(null)
      window.dispatchEvent(new CustomEvent('bridgemods:session', { detail: null }))
      return null
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers)
  const session = readSession()
  if (session?.tokens.access_token) headers.set('Authorization', `Bearer ${session.tokens.access_token}`)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(translate('Не удалось связаться с сервером. Проверьте, запущен ли бэкенд.'), 0)
  }

  if (response.status === 401 && retry && !path.includes('/auth/token:refresh')) {
    const accessToken = await refreshAccessToken()
    if (accessToken) return request<T>(path, init, false)
  }

  if (!response.ok) {
    let message = `${translate('Сервер вернул ошибку')} ${response.status}`
    let code: number | undefined
    try {
      const body = await response.json()
      message = body.message || message
      code = body.code
    } catch {
      // Keep the generic response message for non-JSON errors.
    }
    throw new ApiError(message, response.status, code)
  }

  if (response.status === 204 || response.headers.get('content-length') === '0') return {} as T
  return (await response.json()) as T
}

function query(params: Record<string, string | number | boolean | undefined>) {
  const values = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') values.set(key, String(value))
  })
  const text = values.toString()
  return text ? `?${text}` : ''
}

export const api = {
  register(input: { email: string; username: string; password: string; minecraft_uuid: string }) {
    return request<AuthResponse>('/v1/auth/register', { method: 'POST', body: JSON.stringify(input) })
  },
  login(login: string, password: string) {
    const identity = login.includes('@') ? { email: login } : { username: login }
    return request<AuthResponse>('/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ ...identity, password }),
    })
  },
  currentUser() {
    return request<User>('/v1/users/me')
  },
  async logout(refreshToken: string) {
    return request<Record<string, never>>('/v1/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refresh_token: refreshToken }),
    })
  },
  updateUser(user: Partial<User> & { password?: string; avatar?: string; avatar_content_type?: string }, fields: string[]) {
    return request<User>(`/v1/users/me?${new URLSearchParams(fields.map((field) => ['updateMask.paths', field]))}`, {
      method: 'PATCH',
      body: JSON.stringify(user),
    })
  },
  searchProjects(search = '', pageToken = '') {
    return request<{ projects: SearchProjectResult[]; next_page_token: string }>(
      `/v1/projects:search${query({ query: search, pageSize: 24, pageToken, minSimilarity: search ? 0.08 : 0 })}`,
    )
  },
  projectBySlug(slug: string) {
    return request<Project>(`/v1/projects/slug/${encodeURIComponent(slug)}`)
  },
  projectById(id: string) {
    return request<Project>(`/v1/projects/id/${encodeURIComponent(id)}`)
  },
  myProjects(includeBanned = false) {
    return request<{ projects: Project[]; next_page_token: string }>(
      `/v1/users/me/projects${query({ pageSize: 100, includeBanned })}`,
    )
  },
  checkSlug(slug: string, excludeProjectId = '') {
    return request<{ available: boolean }>(
      `/v1/projects/slug/${encodeURIComponent(slug)}:check${query({ excludeProjectId })}`,
    )
  },
  createProject(project: { slug: string; name: string; description: string; git_url: string }) {
    return request<Project>('/v1/projects', { method: 'POST', body: JSON.stringify(project) })
  },
  updateProject(id: string, project: { slug: string; name: string; description: string; git_url: string }, fields: string[]) {
    const params = new URLSearchParams(fields.map((field) => ['updateMask.paths', field]))
    return request<Project>(`/v1/projects/${encodeURIComponent(id)}?${params}`, {
      method: 'PATCH',
      body: JSON.stringify(project),
    })
  },
  versions(projectId: string) {
    return request<{ versions: ProjectVersion[]; next_page_token: string }>(
      `/v1/projects/${encodeURIComponent(projectId)}/versions${query({ pageSize: 100 })}`,
    )
  },
  version(projectId: string, versionId: string) {
    return request<ProjectVersion>(
      `/v1/projects/${encodeURIComponent(projectId)}/versions/${encodeURIComponent(versionId)}`,
    )
  },
  uploadVersion(
    projectId: string,
    input: {
      version: string
      description: string
      changelog: string
      readme: string
      tag: VersionTag
      metadata: {
        slug: string
        description: string
        licenses: string[]
        authors: string[]
        abi_version: string
        api_version: string
        environment: PluginEnvironment
      }
      archive: { content_type: string; data: string }
    },
  ) {
    return request<ProjectVersion>(`/v1/projects/${encodeURIComponent(projectId)}/versions`, {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },
  submitProject(projectId: string, comment: string) {
    return request<ProjectReviewRequest>(`/v1/projects/${encodeURIComponent(projectId)}:submit`, {
      method: 'POST',
      body: JSON.stringify({ comment }),
    })
  },
  notifications(unreadOnly = false) {
    return request<{ notifications: Notification[]; next_page_token: string; unread_count: number }>(
      `/v1/users/me/notifications${query({ pageSize: 100, unreadOnly })}`,
    )
  },
  markNotificationsRead(ids: string[]) {
    return request<{ notifications: Notification[] }>('/v1/users/me/notifications:mark-read', {
      method: 'POST',
      body: JSON.stringify({ notification_ids: ids }),
    })
  },
  reviewRequests(status = 'REVIEW_STATUS_PENDING') {
    return request<{ review_requests: ProjectReviewRequest[]; next_page_token: string }>(
      `/v1/admin/project-review-requests${query({ status, pageSize: 100 })}`,
    )
  },
  reviewProject(id: string, decision: 'REVIEW_DECISION_APPROVE' | 'REVIEW_DECISION_REJECT', comment: string) {
    return request<ProjectReviewRequest>(`/v1/admin/project-review-requests/${encodeURIComponent(id)}:review`, {
      method: 'POST',
      body: JSON.stringify({ decision, comment }),
    })
  },
  notifyProjectOwner(projectId: string, reviewRequestId: string, text: string) {
    return request<{ notifications: Notification[] }>('/v1/admin/notifications', {
      method: 'POST',
      body: JSON.stringify({
        audience: 'NOTIFICATION_AUDIENCE_PROJECT_OWNER',
        project_id: projectId,
        review_request_id: reviewRequestId,
        text,
      }),
    })
  },
  downloadUrl(slug: string, version = 'latest') {
    return `${API_BASE}/v1/projects/slug/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}:download`
  },
}

export function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('Не удалось прочитать архив'))
    reader.onload = () => resolve(String(reader.result).split(',')[1] ?? '')
    reader.readAsDataURL(file)
  })
}
