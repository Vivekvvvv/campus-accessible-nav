import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiJson, apiRequest } from '../services/apiService'
import { distanceToLineStringMeters, projectToLineStringMeters } from '../utils/polylineUtils'
import { getDistance } from '../utils/coordTransform'
import {
  extractLineCoordinates,
  extractLineEndCoordinate,
  formatDistanceM,
  nowIso,
  pickActiveMode,
  type SessionDestination,
  type SessionSnapshot,
  toSessionDestination,
} from './navigationSessionHelpers'
import { useRouteStore } from './useRouteStore'
import { useSessionStore } from './useSessionStore'
import { useToastStore } from './useToastStore'
import i18n from '../locales'
import type { HazardEffect, NavigationSessionDto, UserLocation, WaypointDto } from '../types/api'

const OFF_ROUTE_THRESHOLD_M = 30
const OFF_ROUTE_HITS_TO_REROUTE = 3
const REROUTE_COOLDOWN_MS = 20_000
const LOCATION_PUSH_MIN_INTERVAL_MS = 1_000
const ARRIVE_THRESHOLD_M = 15
const ARRIVE_HITS_TO_END = 2
const OFF_ROUTE_WARN_COOLDOWN_MS = 20_000
const HAZARD_QUERY_INTERVAL_MS = 15_000
const HAZARD_ANNOUNCE_WITHIN_M = 25
const HAZARD_WARN_COOLDOWN_MS = 20_000
const NAV_SESSION_CACHE_KEY = 'accessiblenav_nav_session_cache_v1'

interface NavEvent {
  type: string
  message: string
  ts: number
}

function tr(key: string, params?: Record<string, unknown>): string {
  return i18n.global.t(key, params as Record<string, string>)
}

export const useNavigationSessionStore = defineStore('navigationSession', () => {
  const toast = useToastStore()
  const routeStore = useRouteStore()
  const sessionStore = useSessionStore()
  let rerouteInFlight: Promise<void> | null = null

  const sessionId = ref<string | null>(null)
  const status = ref<string>('IDLE') // IDLE | ACTIVE | PAUSED | ENDED
  const mode = ref<string>('WALK')

  const destination = ref<SessionDestination | null>(null)
  const deviationCount = ref(0)
  const rerouteCount = ref(0)

  const lastEvent = ref<NavEvent | null>(null)

  const offRouteDistanceM = ref<number | null>(null)
  const offRouteHits = ref(0)
  const lastRerouteAtMs = ref(0)
  const lastOffRouteWarnAtMs = ref(0)

  const arriveHits = ref(0)

  const lastLocationPushAtMs = ref(0)
  const lastLocationPushErrorAtMs = ref(0)

  const hazards = ref<HazardEffect[]>([])
  const hazardWarning = ref<(HazardEffect & { remainingM: number }) | null>(null)
  const lastHazardFetchAtMs = ref(0)
  const lastHazardWarnAtMs = ref(0)
  const warnedHazardEffectIds = ref(new Set<number>())
  const clientEventLastAtMs = ref(new Map<string, number>())
  const resumeToken = ref<string | null>(null)
  const waypoints = ref<WaypointDto[]>([])
  const currentLeg = ref(0)
  const totalLegs = ref(1)
  const currentLevel = ref<number | null>(null)
  const nextLevel = ref<number | null>(null)
  const levelTransitionVia = ref<string>('')

  const isIdle = computed(() => status.value === 'IDLE')
  const isActive = computed(() => status.value === 'ACTIVE')
  const isPaused = computed(() => status.value === 'PAUSED')
  const isEnded = computed(() => status.value === 'ENDED')

  function emitEvent(type: string, message: string): void {
    lastEvent.value = {
      type: String(type || 'UNKNOWN'),
      message: String(message || ''),
      ts: Date.now(),
    }
  }

  function persistSessionSnapshot(): void {
    if (typeof localStorage === 'undefined') return

    if (!sessionId.value || !resumeToken.value || !destination.value) {
      localStorage.removeItem(NAV_SESSION_CACHE_KEY)
      return
    }

    const payload: SessionSnapshot = {
      sessionId: sessionId.value,
      resumeToken: resumeToken.value,
      status: status.value,
      mode: mode.value,
      destination: destination.value,
      updatedAt: Date.now(),
    }
    localStorage.setItem(NAV_SESSION_CACHE_KEY, JSON.stringify(payload))
  }

  function clearPersistedSessionSnapshot(): void {
    if (typeof localStorage === 'undefined') return
    localStorage.removeItem(NAV_SESSION_CACHE_KEY)
  }

  function readPersistedSessionSnapshot(): SessionSnapshot | null {
    if (typeof localStorage === 'undefined') return null
    try {
      const raw = localStorage.getItem(NAV_SESSION_CACHE_KEY)
      if (!raw) return null
      const parsed = JSON.parse(raw)
      if (!parsed || typeof parsed !== 'object') return null
      if (!parsed.resumeToken || typeof parsed.resumeToken !== 'string') return null
      return parsed as SessionSnapshot
    } catch {
      return null
    }
  }

  function resetLocalState(): void {
    sessionId.value = null
    status.value = 'IDLE'
    mode.value = 'WALK'
    destination.value = null
    deviationCount.value = 0
    rerouteCount.value = 0
    lastEvent.value = null
    offRouteDistanceM.value = null
    offRouteHits.value = 0
    lastRerouteAtMs.value = 0
    lastOffRouteWarnAtMs.value = 0
    arriveHits.value = 0
    lastLocationPushAtMs.value = 0
    lastLocationPushErrorAtMs.value = 0
    hazards.value = []
    hazardWarning.value = null
    lastHazardFetchAtMs.value = 0
    lastHazardWarnAtMs.value = 0
    warnedHazardEffectIds.value = new Set()
    clientEventLastAtMs.value = new Map()
    resumeToken.value = null
    waypoints.value = []
    currentLeg.value = 0
    totalLegs.value = 1
    currentLevel.value = null
    nextLevel.value = null
    levelTransitionVia.value = ''
    clearPersistedSessionSnapshot()
  }

  function applyResponse(data: NavigationSessionDto | null): void {
    if (!data) return
    sessionId.value = data.sessionId || data.session_id || sessionId.value
    status.value = String(data.status || status.value || 'IDLE')
    mode.value = String(data.mode || mode.value || 'WALK')
    destination.value = toSessionDestination(data as Record<string, unknown>, destination.value)
    deviationCount.value = Number(data.deviationCount ?? data.deviation_count ?? deviationCount.value ?? 0)
    rerouteCount.value = Number(data.rerouteCount ?? data.reroute_count ?? rerouteCount.value ?? 0)
    resumeToken.value = data.resumeToken ?? data.resume_token ?? resumeToken.value

    // Waypoints
    if (Array.isArray(data.waypoints)) {
      waypoints.value = data.waypoints
    }
    currentLeg.value = Number(data.currentLeg ?? data.current_leg ?? currentLeg.value ?? 0)
    totalLegs.value = Number(data.totalLegs ?? data.total_legs ?? totalLegs.value ?? 1)
    currentLevel.value = Number.isFinite(Number(data.currentLevel ?? data.current_level))
      ? Number(data.currentLevel ?? data.current_level)
      : null
    nextLevel.value = Number.isFinite(Number(data.nextLevel ?? data.next_level))
      ? Number(data.nextLevel ?? data.next_level)
      : null
    levelTransitionVia.value = String(data.levelTransitionVia ?? data.level_transition_via ?? '')

    if (data.route) {
      const active = pickActiveMode(routeStore.routeState.activeMode)
      routeStore.setActiveRouteFromBackend(active, data.route)
    }

    persistSessionSnapshot()
  }

  async function restoreByResumeToken(): Promise<NavigationSessionDto | null> {
    if (!sessionStore.isAuthenticated) return null
    if (sessionId.value) return null

    const snapshot = readPersistedSessionSnapshot()
    if (!snapshot?.resumeToken) return null

    try {
      const data = await apiJson<NavigationSessionDto>(`/api/navigation/session/resume-token/${encodeURIComponent(snapshot.resumeToken)}`, {
        method: 'GET',
      })
      applyResponse(data)
      emitEvent('SESSION_RESUMED', tr('navigation.resume'))
      return data
    } catch {
      clearPersistedSessionSnapshot()
      return null
    }
  }

  async function startFromCurrentRoute(): Promise<void> {
    if (!sessionStore.isAuthenticated) {
      toast.push(tr('toast.loginRequired'))
      return
    }

    const start = routeStore.points.start
    const end = routeStore.points.end
    if (!start || !end) {
      toast.push(tr('toast.startEndFirst'))
      return
    }

    const activeMode = pickActiveMode(routeStore.routeState.activeMode)
    const payload: Record<string, unknown> = {
      startLat: Number(start.lat),
      startLng: Number(start.lng),
      endLat: Number(end.lat),
      endLng: Number(end.lng),
      destinationName: String(end.name || end.label || ''),
      mode: activeMode,
    }
    if (waypoints.value.length > 0) {
      payload.waypoints = waypoints.value.map(w => ({
        lat: w.lat,
        lng: w.lng,
        name: w.name || '',
        reached: false,
      }))
    }

    const data = await apiJson<NavigationSessionDto>('/api/navigation/session', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    applyResponse(data)
    emitEvent('SESSION_STARTED', tr('toast.navSessionStarted'))
    toast.push(tr('toast.navSessionStarted'))
  }

  async function pause(): Promise<void> {
    if (!sessionId.value) return
    const data = await apiJson<NavigationSessionDto>(`/api/navigation/session/${sessionId.value}/pause`, { method: 'POST' })
    applyResponse(data)
    emitEvent('SESSION_PAUSED', tr('navigation.pause'))
  }

  async function resume(): Promise<void> {
    if (!sessionId.value) return
    const data = await apiJson<NavigationSessionDto>(`/api/navigation/session/${sessionId.value}/resume`, { method: 'POST' })
    applyResponse(data)
    emitEvent('SESSION_RESUMED', tr('navigation.resume'))
  }

  async function end(reason?: string): Promise<void> {
    if (!sessionId.value) return
    const qs = reason ? `?reason=${encodeURIComponent(String(reason))}` : ''
    const data = await apiJson<NavigationSessionDto>(`/api/navigation/session/${sessionId.value}/end${qs}`, { method: 'POST' })
    applyResponse(data)
    emitEvent('SESSION_ENDED', reason === 'ARRIVED' ? tr('navigation.arrived') : tr('navigation.stop'))
    if (reason === 'ARRIVED') {
      toast.push(tr('navigation.arrived'))
    }
  }

  async function rerouteFromLocation(loc: UserLocation, reason: string = 'DEVIATION'): Promise<void> {
    if (!sessionId.value) return
    if (rerouteInFlight) {
      await rerouteInFlight
      return
    }
    const payload = {
      lat: Number(loc?.lat),
      lng: Number(loc?.lng),
      reason,
    }
    lastRerouteAtMs.value = Date.now()
    rerouteInFlight = (async () => {
      const data = await apiJson<NavigationSessionDto>(`/api/navigation/session/${sessionId.value}/reroute`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      applyResponse(data)
      offRouteHits.value = 0
      arriveHits.value = 0
      hazardWarning.value = null
      emitEvent('REROUTED', tr('toast.navRerouted'))
      toast.push(tr('toast.navRerouted'))
    })()

    try {
      await rerouteInFlight
    } finally {
      rerouteInFlight = null
    }
  }

  async function fetchHazards(): Promise<void> {
    if (!sessionId.value || !isActive.value) return
    const now = Date.now()
    if (now - lastHazardFetchAtMs.value < HAZARD_QUERY_INTERVAL_MS) return
    lastHazardFetchAtMs.value = now

    try {
      const data = await apiJson<HazardEffect[]>(`/api/navigation/session/${sessionId.value}/hazards?radiusM=30&limit=30`, {
        method: 'GET',
      })
      hazards.value = Array.isArray(data) ? data : []
    } catch {
      // Non-critical; ignore.
    }
  }

  async function reportClientEvent(type: string, payload?: string | null): Promise<void> {
    if (!sessionId.value || !isActive.value) return
    const t = String(type || '').trim().toUpperCase()
    if (!t) return

    const now = Date.now()
    const last = clientEventLastAtMs.value.get(t) || 0
    if (now - last < 2000) return
    clientEventLastAtMs.value.set(t, now)

    const body = JSON.stringify({
      type: t,
      payload: payload == null ? null : String(payload).slice(0, 180),
      observedAt: nowIso(),
    })

    try {
      const res = await apiRequest(`/api/navigation/session/${sessionId.value}/client-event`, {
        method: 'POST',
        body,
      })
      // Best-effort; ignore errors.
      void res
    } catch {
      // ignore
    }
  }

  function clearHazardWarning(): void {
    hazardWarning.value = null
  }

  function addWaypoint(point: WaypointDto): void {
    waypoints.value = [...waypoints.value, { ...point, reached: false }]
  }

  function removeWaypoint(idx: number): void {
    waypoints.value = waypoints.value.filter((_, i) => i !== idx)
  }

  async function advanceLeg(): Promise<void> {
    if (!sessionId.value) return
    const data = await apiJson<NavigationSessionDto>(`/api/navigation/session/${sessionId.value}/advance-leg`, {
      method: 'POST',
    })
    applyResponse(data)
    emitEvent('WAYPOINT_REACHED', tr('navigation.waypointReached'))
    toast.push(tr('navigation.waypointReached'))
  }

  async function pushLocation(loc: UserLocation): Promise<void> {
    if (!sessionId.value) return
    if (!isActive.value) return
    const now = Date.now()
    if (now - lastLocationPushAtMs.value < LOCATION_PUSH_MIN_INTERVAL_MS) return
    lastLocationPushAtMs.value = now

    try {
      await apiJson(`/api/navigation/session/${sessionId.value}/location`, {
        method: 'POST',
        body: JSON.stringify({
          lat: Number(loc?.lat),
          lng: Number(loc?.lng),
          accuracyM: Number.isFinite(Number(loc?.accuracy)) ? Number(loc.accuracy) : null,
          observedAt: nowIso(),
        }),
      })
    } catch (e: unknown) {
      const err = e as { code?: string; message?: string }
      // If backend ended/expired the session, close locally to avoid repeated errors.
      if (err?.code === 'NAV_001' || err?.code === 'NAV_002') {
        toast.push(tr('navigation.sessionExpired'))
        resetLocalState()
        return
      }

      // Avoid toast spam; show at most once per 15s.
      if (now - lastLocationPushErrorAtMs.value > 15_000) {
        lastLocationPushErrorAtMs.value = now
        toast.push(tr('toast.locationPushFailed', { message: err?.message || String(e) }))
      }
    }
  }

  async function handleLocationUpdate(loc: UserLocation): Promise<void> {
    if (!loc) return
    if (!sessionId.value || !isActive.value) return

    await pushLocation(loc)
    if (!sessionId.value || !isActive.value) return

    // Arrival detection (2 consecutive hits under threshold to avoid GPS jitter).
    // For multi-leg navigation, arrival means current-leg endpoint reached -> auto advance to next leg.
    // For final leg, reaching destination auto-ends the session.
    const currentLegEnd = extractLineEndCoordinate(routeStore.activeRoute)
    if (currentLegEnd && Number.isFinite(currentLegEnd.lat) && Number.isFinite(currentLegEnd.lng)) {
      const accurateEnough = !Number.isFinite(Number(loc?.accuracy)) || Number(loc.accuracy) <= 50
      if (accurateEnough) {
        const dToLegEnd = getDistance(Number(loc.lng), Number(loc.lat), currentLegEnd.lng, currentLegEnd.lat)
        if (Number.isFinite(dToLegEnd) && dToLegEnd <= ARRIVE_THRESHOLD_M) {
          arriveHits.value += 1
        } else {
          arriveHits.value = 0
        }
        if (arriveHits.value >= ARRIVE_HITS_TO_END) {
          const hasNextLeg = totalLegs.value > 1 && currentLeg.value < totalLegs.value - 1
          if (hasNextLeg) {
            await advanceLeg()
            arriveHits.value = 0
          } else {
            await end('ARRIVED')
          }
          return
        }
      }
    } else if (destination.value && Number.isFinite(destination.value.lat) && Number.isFinite(destination.value.lng)) {
      // Fallback for unusual cases where route geometry is unavailable.
      const accurateEnough = !Number.isFinite(Number(loc?.accuracy)) || Number(loc.accuracy) <= 50
      if (accurateEnough) {
        const dToDest = getDistance(Number(loc.lng), Number(loc.lat), destination.value.lng, destination.value.lat)
        if (Number.isFinite(dToDest) && dToDest <= ARRIVE_THRESHOLD_M) {
          arriveHits.value += 1
        } else {
          arriveHits.value = 0
        }
        if (arriveHits.value >= ARRIVE_HITS_TO_END) {
          await end('ARRIVED')
          return
        }
      }
    }

    // Off-route detection based on current active route line.
    const coords = extractLineCoordinates(routeStore.activeRoute)
    const dist = coords ? distanceToLineStringMeters(loc, coords) : Infinity
    offRouteDistanceM.value = Number.isFinite(dist) ? dist : null

    const accurateEnough = !Number.isFinite(Number(loc?.accuracy)) || Number(loc.accuracy) <= 50
    const offRoute = accurateEnough && Number.isFinite(dist) && dist > OFF_ROUTE_THRESHOLD_M
    offRouteHits.value = offRoute ? offRouteHits.value + 1 : 0
    if (!offRoute) {
      arriveHits.value = 0
    }

    const now = Date.now()
    if (offRoute && offRouteHits.value === 1 && now - lastOffRouteWarnAtMs.value >= OFF_ROUTE_WARN_COOLDOWN_MS) {
      lastOffRouteWarnAtMs.value = now
      emitEvent('OFF_ROUTE', tr('navigation.offRoute'))
      toast.push(tr('navigation.offRoute'))
      void reportClientEvent('OFF_ROUTE_WARNED', formatDistanceM(dist))
    }
    const canReroute = now - lastRerouteAtMs.value >= REROUTE_COOLDOWN_MS
    if (offRouteHits.value >= OFF_ROUTE_HITS_TO_REROUTE && canReroute) {
      emitEvent('REROUTING', tr('navigation.rerouting'))
      toast.push(tr('navigation.rerouting'))
      await rerouteFromLocation(loc, 'DEVIATION')
    }

    // Hazard detection
    await fetchHazards()

    if (coords && hazards.value.length) {
      const proj = projectToLineStringMeters(loc, coords)
      const alongM = Number(proj?.alongM)
      if (Number.isFinite(alongM)) {
        let best: HazardEffect | null = null
        let bestRemaining = Infinity
        for (const h of hazards.value) {
          const atM = Number(h?.routeAtM)
          if (!Number.isFinite(atM)) continue
          const remaining = atM - alongM
          if (remaining < 0) continue
          if (remaining > HAZARD_ANNOUNCE_WITHIN_M) continue
          if (remaining < bestRemaining) {
            bestRemaining = remaining
            best = h
          }
        }

        if (
          best
          && best?.effectId != null
          && !warnedHazardEffectIds.value.has(best.effectId)
          && now - lastHazardWarnAtMs.value >= HAZARD_WARN_COOLDOWN_MS
        ) {
          lastHazardWarnAtMs.value = now
          warnedHazardEffectIds.value.add(best.effectId)
          hazardWarning.value = { ...best, remainingM: bestRemaining }
          const reasonText = String(best.reason || '').trim() || tr('navigation.hazard')
          const msg = tr('navigation.hazardAhead', { distance: formatDistanceM(bestRemaining), reason: reasonText })
          emitEvent('HAZARD_AHEAD', msg)
          toast.push(msg)
          void reportClientEvent('HAZARD_WARNED', reasonText)
        }
      }
    }
  }

  return {
    sessionId,
    status,
    mode,
    destination,
    deviationCount,
    rerouteCount,
    lastEvent,
    offRouteDistanceM,
    offRouteHits,
    hazards,
    hazardWarning,
    resumeToken,
    waypoints,
    currentLeg,
    totalLegs,
    currentLevel,
    nextLevel,
    levelTransitionVia,
    isIdle,
    isActive,
    isPaused,
    isEnded,
    resetLocalState,
    startFromCurrentRoute,
    pause,
    resume,
    end,
    clearHazardWarning,
    addWaypoint,
    removeWaypoint,
    advanceLeg,
    reportClientEvent,
    restoreByResumeToken,
    handleLocationUpdate,
    rerouteFromLocation,
  }
})
