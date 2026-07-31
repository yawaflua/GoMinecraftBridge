import { describe, expect, test } from 'bun:test'
import { formatBytes, initials, isModerator, projectStatus, slugify } from './utils'
import { setLocale, translate } from './lib/i18n'

setLocale('ru')

describe('domain presentation helpers', () => {
  test('creates backend-compatible slugs', () => {
    expect(slugify(' World Sync 2 ')).toBe('world-sync-2')
    expect(slugify('Привет Minecraft')).toBe('minecraft')
  })

  test('formats archive sizes', () => {
    expect(formatBytes(1024)).toBe('1.0 КБ')
    expect(formatBytes(2 * 1024 * 1024)).toBe('2.0 МБ')
  })

  test('switches interface copy and locale-sensitive units to English', () => {
    setLocale('en')
    expect(translate('Каталог')).toBe('Discover')
    expect(translate('Результаты для «{query}»', { query: 'sync' })).toBe('Results for “sync”')
    expect(formatBytes(1024)).toBe('1.0 KB')
    setLocale('ru')
  })

  test('recognises moderation access', () => {
    expect(isModerator(['USER_ROLE_USER'])).toBe(false)
    expect(isModerator(['USER_ROLE_USER', 'USER_ROLE_MODERATOR'])).toBe(true)
    expect(isModerator(['USER_ROLE_ADMIN'])).toBe(true)
  })

  test('covers every project lifecycle state used by the UI', () => {
    expect(projectStatus.PROJECT_STATUS_DRAFT.label).toBe('Черновик')
    expect(projectStatus.PROJECT_STATUS_PENDING_REVIEW.tone).toBe('warning')
    expect(projectStatus.PROJECT_STATUS_PUBLISHED.tone).toBe('success')
    expect(projectStatus.PROJECT_STATUS_REJECTED.tone).toBe('error')
  })

  test('builds compact project initials', () => {
    expect(initials('bridge')).toBe('BR')
    expect(initials('')).toBe('BM')
  })
})
