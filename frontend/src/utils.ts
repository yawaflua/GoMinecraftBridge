import type { PluginEnvironment, ProjectStatus, VersionTag } from './types'
import { intlLocale } from './lib/i18n'

export const projectStatus: Record<ProjectStatus, { label: string; tone: string; detail: string }> = {
  PROJECT_STATUS_UNSPECIFIED: { label: 'Неизвестно', tone: 'neutral', detail: 'Статус проекта не определён' },
  PROJECT_STATUS_DRAFT: { label: 'Черновик', tone: 'neutral', detail: 'Добавьте версию и отправьте проект на ревью' },
  PROJECT_STATUS_PENDING_REVIEW: { label: 'На ревью', tone: 'warning', detail: 'Модерация проверяет проект' },
  PROJECT_STATUS_PUBLISHED: { label: 'Опубликован', tone: 'success', detail: 'Проект доступен в каталоге' },
  PROJECT_STATUS_REJECTED: { label: 'Нужны изменения', tone: 'error', detail: 'Исправьте замечания и отправьте снова' },
  PROJECT_STATUS_BANNED: { label: 'Заблокирован', tone: 'error', detail: 'Публикация и обновления ограничены' },
}

export const tagLabel: Record<VersionTag, string> = {
  VERSION_TAG_UNSPECIFIED: 'Не указан',
  VERSION_TAG_RELEASE: 'Release',
  VERSION_TAG_BETA: 'Beta',
  VERSION_TAG_ALPHA: 'Alpha',
}

export const environmentLabel: Record<PluginEnvironment, string> = {
  PLUGIN_ENVIRONMENT_UNSPECIFIED: 'Не указано',
  PLUGIN_ENVIRONMENT_SERVER: 'Сервер',
  PLUGIN_ENVIRONMENT_CLIENT: 'Клиент',
  PLUGIN_ENVIRONMENT_BOTH: 'Клиент и сервер',
}

export function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(intlLocale(), { day: 'numeric', month: 'short', year: 'numeric' }).format(date)
}

export function formatDateTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(intlLocale(), {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatBytes(value: string | number) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes)) return '—'
  const english = intlLocale() === 'en-US'
  if (bytes < 1024) return `${bytes} ${english ? 'B' : 'Б'}`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} ${english ? 'KB' : 'КБ'}`
  return `${(bytes / 1024 ** 2).toFixed(1)} ${english ? 'MB' : 'МБ'}`
}

export function initials(value: string) {
  return value.trim().slice(0, 2).toUpperCase() || 'BM'
}

export function slugify(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64)
}

export function isModerator(roles: string[]) {
  return roles.includes('USER_ROLE_MODERATOR') || roles.includes('USER_ROLE_ADMIN')
}
