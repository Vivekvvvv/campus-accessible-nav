import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const toastPush = vi.fn()

vi.mock('@/services/apiService', () => ({
  apiJson: vi.fn(),
  apiRequest: vi.fn(),
}))

vi.mock('@/locales', () => ({
  default: {
    global: {
      t: (key: string, params?: Record<string, unknown>) => {
        if (!params) return key
        const extra = Object.entries(params)
          .map(([k, v]) => `${k}=${String(v)}`)
          .join(',')
        return `${key}:${extra}`
      },
    },
  },
}))

vi.mock('@/stores/useToastStore', () => ({
  useToastStore: () => ({
    push: toastPush,
  }),
}))

import { apiJson } from '@/services/apiService'
import { useNavigationSessionStore } from '@/stores/useNavigationSessionStore'
import { useRouteStore } from '@/stores/useRouteStore'
import { useSessionStore } from '@/stores/useSessionStore'
import type { NavigationSessionDto, UserLocation } from '@/types/api'

describe('useNavigationSessionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  function authenticate() {
    const sessionStore = useSessionStore()
    sessionStore.userProfile = { username: 'user-a', role: 'USER' }
  }

  function primeRoutePoints() {
    const routeStore = useRouteStore()
    routeStore.setPoint('start', { lat: 23.275, lng: 113.2, name: 'S' })
    routeStore.setPoint('end', { lat: 23.276, lng: 113.201, name: 'E' })
    return routeStore
  }

  function buildSession(overrides: Partial<NavigationSessionDto> = {}): NavigationSessionDto {
    return {
      sessionId: 'sid-1',
      status: 'ACTIVE',
      mode: 'WALK',
      destinationLat: 23.276,
      destinationLng: 113.201,
      destinationName: 'Library',
      deviationCount: 0,
      rerouteCount: 0,
      resumeToken: 'resume-1',
      route: {
        type: 'FeatureCollection',
        mode: 'WALK',
        distanceM: 100,
        durationSec: 60,
        features: [
          {
            type: 'Feature',
            geometry: {
              type: 'LineString',
              coordinates: [
                [113.2, 23.275],
                [113.201, 23.276],
              ],
            },
          },
        ],
      },
      ...overrides,
    }
  }

  it('blocks session start when user is unauthenticated', async () => {
    const store = useNavigationSessionStore()
    primeRoutePoints()

    await store.startFromCurrentRoute()

    expect(apiJson).not.toHaveBeenCalled()
    expect(toastPush).toHaveBeenCalledWith('toast.loginRequired')
    expect(store.sessionId).toBeNull()
  })

  it('starts session and persists resume snapshot', async () => {
    authenticate()
    primeRoutePoints()
    const store = useNavigationSessionStore()
    vi.mocked(apiJson).mockResolvedValueOnce(buildSession())

    await store.startFromCurrentRoute()

    expect(apiJson).toHaveBeenCalledWith('/api/navigation/session', expect.objectContaining({
      method: 'POST',
    }))
    expect(store.sessionId).toBe('sid-1')
    expect(store.status).toBe('ACTIVE')
    expect(store.destination).toEqual({ lng: 113.201, lat: 23.276, name: 'Library' })
    expect(store.resumeToken).toBe('resume-1')
    expect(localStorage.getItem('accessiblenav_nav_session_cache_v1')).toContain('resume-1')
  })

  it('restores session from persisted resume token and clears snapshot on failure', async () => {
    authenticate()
    const store = useNavigationSessionStore()
    localStorage.setItem('accessiblenav_nav_session_cache_v1', JSON.stringify({
      sessionId: 'sid-old',
      resumeToken: 'resume-old',
      status: 'ACTIVE',
      mode: 'WALK',
      destination: { lng: 113.2, lat: 23.27, name: 'Old' },
      updatedAt: Date.now(),
    }))

    vi.mocked(apiJson).mockResolvedValueOnce(buildSession({ sessionId: 'sid-restored', resumeToken: 'resume-restored' }))
    const restored = await store.restoreByResumeToken()

    expect(restored?.sessionId).toBe('sid-restored')
    expect(store.sessionId).toBe('sid-restored')
    expect(store.lastEvent?.type).toBe('SESSION_RESUMED')

    vi.mocked(apiJson).mockRejectedValueOnce(new Error('resume failed'))
    store.resetLocalState()
    localStorage.setItem('accessiblenav_nav_session_cache_v1', JSON.stringify({
      sessionId: 'sid-old',
      resumeToken: 'resume-old',
      status: 'ACTIVE',
      mode: 'WALK',
      destination: { lng: 113.2, lat: 23.27, name: 'Old' },
      updatedAt: Date.now(),
    }))

    const failed = await store.restoreByResumeToken()

    expect(failed).toBeNull()
    expect(localStorage.getItem('accessiblenav_nav_session_cache_v1')).toBeNull()
  })

  it('deduplicates in-flight reroute requests', async () => {
    const store = useNavigationSessionStore()
    store.sessionId = 'sid-1'
    store.status = 'ACTIVE'
    store.hazardWarning = {
      effectId: 1,
      edgeId: 2,
      reason: 'construction',
      remainingM: 10,
    }
    store.offRouteHits = 3

    let resolveReroute: (() => void) | null = null
    vi.mocked(apiJson).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveReroute = () => resolve(undefined)
        }) as Promise<NavigationSessionDto>
    )

    const loc: UserLocation = { lng: 113.2, lat: 23.27 }
    const p1 = store.rerouteFromLocation(loc, 'DEVIATION')
    const p2 = store.rerouteFromLocation(loc, 'DEVIATION')
    resolveReroute?.()
    await Promise.all([p1, p2])

    expect(apiJson).toHaveBeenCalledTimes(1)
    expect(store.hazardWarning).toBeNull()
    expect(store.offRouteHits).toBe(0)
    expect(store.lastEvent?.type).toBe('REROUTED')
  })

  it('stops processing location updates after session-expired push failure', async () => {
    const routeStore = useRouteStore()
    routeStore.routeState.walk = {
      type: 'FeatureCollection',
      mode: 'WALK',
      features: [
        {
          type: 'Feature',
          geometry: {
            type: 'LineString',
            coordinates: [
              [113.2, 23.27],
              [113.201, 23.271],
            ],
          },
        },
      ],
    }
    const store = useNavigationSessionStore()
    store.sessionId = 'sid-1'
    store.status = 'ACTIVE'
    store.destination = { lng: 113.201, lat: 23.276, name: 'Library' }

    vi.mocked(apiJson).mockRejectedValueOnce({ code: 'NAV_001', message: 'expired' })

    await store.handleLocationUpdate({ lng: 113.2, lat: 23.27 })

    expect(apiJson).toHaveBeenCalledTimes(1)
    expect(store.sessionId).toBeNull()
    expect(store.status).toBe('IDLE')
    expect(toastPush).toHaveBeenCalledWith('navigation.sessionExpired')
  })
})
