import { beforeEach, describe, expect, it, vi } from 'vitest'

function createJwt(expSecondsFromNow: number): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
  const payload = btoa(JSON.stringify({
    exp: Math.floor(Date.now() / 1000) + expSecondsFromNow,
  }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
  return `${header}.${payload}.signature`
}

describe('authSession', () => {
  beforeEach(() => {
    vi.resetModules()
    localStorage.clear()
  })

  it('detects token expiry and remaining time', async () => {
    const { isTokenExpired, getTokenRemainingMs } = await import('./authSession')
    const freshToken = createJwt(300)
    const expiredToken = createJwt(-10)

    expect(isTokenExpired(freshToken, 30)).toBe(false)
    expect(isTokenExpired(expiredToken, 0)).toBe(true)
    expect(getTokenRemainingMs(freshToken)).toBeGreaterThan(0)
    expect(getTokenRemainingMs('bad.token')).toBeNull()
  })

  it('clears targeted sessions and dispatches session-changed event', async () => {
    const { clearAuthSessions, getSessionChangedEventName } = await import('./authSession')
    const eventSpy = vi.fn()
    window.addEventListener(getSessionChangedEventName(), eventSpy)

    localStorage.setItem('accessiblenav_admin_token', 'admin-token')
    localStorage.setItem('accessiblenav_admin_profile', '{}')
    localStorage.setItem('accessiblenav_user_token', 'user-token')
    localStorage.setItem('accessiblenav_user_profile', '{}')

    clearAuthSessions('admin')

    expect(localStorage.getItem('accessiblenav_admin_token')).toBeNull()
    expect(localStorage.getItem('accessiblenav_user_token')).toBe('user-token')
    expect(eventSpy).toHaveBeenCalledTimes(1)
  })

  it('prunes expired tokens and reports which scopes expired', async () => {
    const { pruneExpiredAuthTokens } = await import('./authSession')

    localStorage.setItem('accessiblenav_admin_token', createJwt(-10))
    localStorage.setItem('accessiblenav_admin_profile', '{}')
    localStorage.setItem('accessiblenav_user_token', createJwt(300))
    localStorage.setItem('accessiblenav_user_profile', '{}')

    const result = pruneExpiredAuthTokens(false)

    expect(result).toEqual({ adminExpired: true, userExpired: false })
    expect(localStorage.getItem('accessiblenav_admin_token')).toBeNull()
    expect(localStorage.getItem('accessiblenav_user_token')).not.toBeNull()
  })

  it('returns active auth token with admin precedence and prunes expired entries', async () => {
    const { getActiveAuthToken } = await import('./authSession')

    localStorage.setItem('accessiblenav_admin_token', createJwt(300))
    localStorage.setItem('accessiblenav_user_token', createJwt(300))
    expect(getActiveAuthToken().source).toBe('admin')

    localStorage.setItem('accessiblenav_admin_token', createJwt(-10))
    const active = getActiveAuthToken()

    expect(active.source).toBe('user')
    expect(localStorage.getItem('accessiblenav_admin_token')).toBeNull()
  })
})
