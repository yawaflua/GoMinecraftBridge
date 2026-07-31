const configuredSiteUrl = import.meta.env.VITE_SITE_URL ?? 'https://gbm.ywfl.dev'

export const SITE_URL = configuredSiteUrl.replace(/\/$/, '')
export const SITE_HOST = new URL(SITE_URL).host
