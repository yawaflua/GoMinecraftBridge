import { writable } from 'svelte/store'
import { api, saveStoredSession } from './api'
import type { SessionState } from '../types'

const SESSION_KEY = 'bridgemods.session'

function initialSession(): SessionState | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as SessionState) : null
  } catch {
    return null
  }
}

export const session = writable<SessionState | null>(initialSession())
export const sessionReady = writable(false)

session.subscribe((value) => saveStoredSession(value))

window.addEventListener('bridgemods:session', ((event: CustomEvent<SessionState | null>) => {
  session.set(event.detail)
}) as EventListener)

export async function hydrateSession() {
  let current: SessionState | null = null
  const unsubscribe = session.subscribe((value) => (current = value))
  unsubscribe()
  if (!current) {
    sessionReady.set(true)
    return
  }
  try {
    const user = await api.currentUser()
    session.update((value) => (value ? { ...value, user } : null))
  } catch {
    session.set(null)
  } finally {
    sessionReady.set(true)
  }
}

export function setSession(value: SessionState) {
  session.set(value)
}

export async function endSession() {
  let current: SessionState | null = null
  const unsubscribe = session.subscribe((value) => (current = value))
  unsubscribe()
  session.set(null)
  if (current) {
    try {
      await api.logout((current as SessionState).tokens.refresh_token)
    } catch {
      // The local session is already cleared; server revocation can safely fail.
    }
  }
}

