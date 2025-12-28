import { authConfig } from '../config/authConfig'

const ADMIN_TOKEN_KEY = 'accessiblenav_admin_token'
const ADMIN_PROFILE_KEY = 'accessiblenav_admin_profile'
const USER_TOKEN_KEY = 'accessiblenav_user_token'
const USER_PROFILE_KEY = 'accessiblenav_user_profile'
const SESSION_CHANGED_EVENT = 'accessiblenav-session-changed'

export type AuthTokenSource = 'admin' | 'user'

function isBrowser(): boolean {
  return typeof window !== 'undefined' && typeof localStorage !== 'undefined'
}

function decodeBase64Url(input: string): string | null {
  if (!input) return null
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/')
  const padding = '='.repeat((4 - (normalized.length % 4)) % 4)
  try {
    return atob(normalized + padding)
  } catch {
    return null
  }
}

function getTokenExpMs(token: string): number | null {
  const parts = String(token || '').split('.')
  if (parts.length < 2) return null
  const payloadRaw = decodeBase64Url(parts[1])
  if (!payloadRaw) return null
  try {
    const payload = JSON.parse(payloadRaw) as { exp?: unknown }
    const exp = Number(payload.exp)
    if (!Number.isFinite(exp) || exp <= 0) return null
    return exp * 1000
  } catch {
    return null
  }
}

export function isTokenExpired(token: string, skewSeconds = 30): boolean {
  const expMs = getTokenExpMs(token)
  if (!expMs) return false
  return Date.now() >= expMs - skewSeconds * 1000
}

export function getTokenRemainingMs(token: string): number | null {
  const expMs = getTokenExpMs(token)
  if (!expMs) return null
  return expMs - Date.now()
}

function emitSessionChanged(): void {
  if (!isBrowser()) return
  window.dispatchEvent(new Event(SESSION_CHANGED_EVENT))
}

function clearAdminSessionSilently(): void {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
  localStorage.removeItem(ADMIN_PROFILE_KEY)
}

function clearUserSessionSilently(): void {
  localStorage.removeItem(USER_TOKEN_KEY)
  localStorage.removeItem(USER_PROFILE_KEY)
}

export function clearAuthSessions(scope: AuthTokenSource | 'all' = 'all', notify = true): void {
  if (!isBrowser()) return
  if (scope === 'all' || scope === 'admin') {
    clearAdminSessionSilently()
  }
  if (scope === 'all' || scope === 'user') {
    clearUserSessionSilently()
  }
  if (notify) {
    emitSessionChanged()
  }
}

export function pruneExpiredAuthTokens(notify = true): { adminExpired: boolean; userExpired: boolean } {
  if (!isBrowser()) {
    return { adminExpired: false, userExpired: false }
  }

  let adminExpired = false
  let userExpired = false

  const skewSeconds = authConfig.tokenSkewSeconds

  const adminToken = localStorage.getItem(ADMIN_TOKEN_KEY)
  if (adminToken && isTokenExpired(adminToken, skewSeconds)) {
    clearAdminSessionSilently()
    adminExpired = true
  }

  const userToken = localStorage.getItem(USER_TOKEN_KEY)
  if (userToken && isTokenExpired(userToken, skewSeconds)) {
    clearUserSessionSilently()
    userExpired = true
  }

  if ((adminExpired || userExpired) && notify) {
    emitSessionChanged()
  }

  return { adminExpired, userExpired }
}

export function getActiveAuthToken(): { token: string | null; source: AuthTokenSource | null } {
  if (!isBrowser()) {
    return { token: null, source: null }
  }

  pruneExpiredAuthTokens(true)

  const adminToken = localStorage.getItem(ADMIN_TOKEN_KEY)
  if (adminToken) {
    return { token: adminToken, source: 'admin' }
  }

  const userToken = localStorage.getItem(USER_TOKEN_KEY)
  if (userToken) {
    return { token: userToken, source: 'user' }
  }

  return { token: null, source: null }
}

export function getSessionChangedEventName(): string {
  return SESSION_CHANGED_EVENT
}
