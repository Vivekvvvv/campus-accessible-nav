import { defineStore } from 'pinia'
import { ref, computed, toRaw } from 'vue'
import { useToastStore } from './useToastStore'
import { useMapStore } from './useMapStore'
import { readResponseText } from '../utils/fetchUtils'
import { logger } from '../utils/logger'
import { apiRequest } from '../services/apiService'
import type { RouteResponse, RouteInstruction, RoutePoint } from '../types/api'

interface RouteState {
  walk: RouteResponse | null
  wheel: RouteResponse | null
  loading: boolean
  error: string | null
  activeMode: 'walk' | 'wheel'
}

export const useRouteStore = defineStore('route', () => {
  const toast = useToastStore()
  const mapStore = useMapStore()

  const routeApiUrl = '/api/route'
  logger.debug('[RouteStore] routeApiUrl:', routeApiUrl)

  const points = ref<{ start: RoutePoint | null; end: RoutePoint | null }>({
    start: null,
    end: null,
  })

  const strategy = ref<'BALANCED' | 'SHORTEST' | 'SAFEST'>('BALANCED')
  const strategyWeights = ref<{ stairsPenalty: number; slopePenalty: number; constructionPenalty: number }>({
    stairsPenalty: 0.3,
    slopePenalty: 0.2,
    constructionPenalty: 0.3,
  })

  const routeState = ref<RouteState>({
    walk: null,
    wheel: null,
    loading: false,
    error: null,
    activeMode: 'walk',
  })

  const activeRoute = computed(() => {
    const route = routeState.value.activeMode === 'wheel'
      ? routeState.value.wheel
      : routeState.value.walk
    // Return raw value so callers/tests can do identity equality on the assigned object.
    return toRaw(route)
  })

  // Has at least one valid route?
  const hasRoute = computed(() => {
    return Boolean(routeState.value.walk || routeState.value.wheel)
  })

  let routeRequestId = 0

  function setPoint(which: 'start' | 'end', val: RoutePoint | null): void {
    if (which === 'start') points.value.start = val
    if (which === 'end') points.value.end = val
  }

  function clearPoints(): void {
    points.value.start = null
    points.value.end = null
    clearRouteState()
  }

  function clearRouteState(): void {
    routeState.value.loading = false
    routeState.value.error = null
    routeState.value.walk = null
    routeState.value.wheel = null
  }

  async function fetchRoute(mode: string): Promise<RouteResponse | null> {
    const start = points.value.start
    const end = points.value.end
    if (!start || !end) return null

    const payload = {
      startLat: Number(start.lat),
      startLng: Number(start.lng),
      endLat: Number(end.lat),
      endLng: Number(end.lng),
      mode,
      campusOnly: mapStore.campusOnly,
      strategy: strategy.value,
      strategyWeights: strategyWeights.value,
    }

    const res = await apiRequest(routeApiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })

    if (!res.ok) {
      let message = `Route request failed (${res.status})`
      try {
        const data = await res.json()
        if (data?.message) message = data.message
      } catch {
        try {
          const text = await readResponseText(res)
          if (text) message = text
        } catch {
          // ignore
        }
      }
      throw new Error(message)
    }

    const data = await res.json()
    return normalizeRouteResponse(data, mode)
  }

  async function computeRoutes(): Promise<void> {
    if (!points.value.start || !points.value.end) {
      clearRouteState()
      return
    }

    const currentId = ++routeRequestId
    routeState.value.loading = true
    routeState.value.error = null

    const [walkResult, wheelResult] = await Promise.allSettled([
      fetchRoute('WALK'),
      fetchRoute('WHEELCHAIR'),
    ])

    if (currentId !== routeRequestId) return

    routeState.value.walk = walkResult.status === 'fulfilled' ? walkResult.value : null
    routeState.value.wheel = wheelResult.status === 'fulfilled' ? wheelResult.value : null

    const errors: string[] = []
    if (walkResult.status === 'rejected') {
      errors.push(walkResult.reason?.message || 'Walk route failed')
    }
    if (wheelResult.status === 'rejected') {
      errors.push(wheelResult.reason?.message || 'Wheelchair route failed')
    }
    routeState.value.error = errors.length ? errors.join(' | ') : null
    routeState.value.loading = false

    // Auto-switch active mode if only one is available
    if (routeState.value.activeMode === 'walk' && !routeState.value.walk && routeState.value.wheel) {
      routeState.value.activeMode = 'wheel'
    }
    if (routeState.value.activeMode === 'wheel' && !routeState.value.wheel && routeState.value.walk) {
      routeState.value.activeMode = 'walk'
    }

    if (routeState.value.error) {
      toast.push(routeState.value.error)
    }
  }

  function normalizeRouteResponse(data: unknown, mode: string): RouteResponse | null {
    if (!data) return null
    const d = data as Record<string, unknown>
    if (d.type === 'FeatureCollection' && Array.isArray(d.features)) {
      if (!d.summary) {
        d.summary = {
          distanceM: Number(d.distanceM ?? 0),
          durationSec: Number(d.durationSec ?? 0),
          riskCount: Number(d.riskCount ?? 0),
        }
      }
      return d as unknown as RouteResponse
    }

    const path = Array.isArray(d.path) ? d.path : []
    const coordinates = path
      .map((p: Record<string, unknown>) => [Number(p?.lng), Number(p?.lat)] as [number, number])
      .filter((pair: [number, number]) => Number.isFinite(pair[0]) && Number.isFinite(pair[1]))

    const featureCollection: RouteResponse = {
      type: 'FeatureCollection',
      features: coordinates.length
        ? [
            {
              type: 'Feature',
              geometry: {
                type: 'LineString',
                coordinates,
              },
              properties: {
                mode: (d.mode as string) || mode,
              },
            },
          ]
        : [],
      mode: (d.mode as string) || mode,
      distanceM: Number(d.distanceM ?? 0),
      durationSec: Number(d.durationSec ?? 0),
      riskCount: Number(d.riskCount ?? 0),
      instructions: Array.isArray(d.instructions) ? d.instructions as RouteInstruction[] : [],
      explain: Array.isArray(d.explain) ? d.explain as string[] : [],
      debug: d.debug || null,
      startSnap: d.startSnap || null,
      endSnap: d.endSnap || null,
      pathWithLevel: Array.isArray(d.pathWithLevel) ? d.pathWithLevel : [],
      levelTransitions: Array.isArray(d.levelTransitions) ? d.levelTransitions : [],
      modeDiff: d.modeDiff || null,
      routingPolicy: d.routingPolicy || null,
    }
    featureCollection.summary = {
      distanceM: featureCollection.distanceM!,
      durationSec: featureCollection.durationSec!,
      riskCount: featureCollection.riskCount!,
    }
    return featureCollection
  }

  function setActiveRouteFromBackend(mode: string, data: unknown): void {
    const normalized = normalizeRouteResponse(data, mode)
    if (!normalized) return
    if (mode === 'WHEELCHAIR') {
      routeState.value.wheel = normalized
      routeState.value.activeMode = 'wheel'
    } else {
      routeState.value.walk = normalized
      routeState.value.activeMode = 'walk'
    }
    routeState.value.loading = false
    routeState.value.error = null
  }

  function setActiveMode(mode: 'walk' | 'wheel'): void {
    routeState.value.activeMode = mode
  }

  function setStrategy(s: 'BALANCED' | 'SHORTEST' | 'SAFEST'): void {
    strategy.value = s
  }

  function setStrategyWeights(w: { stairsPenalty: number; slopePenalty: number; constructionPenalty: number }): void {
    strategyWeights.value = w
  }

  return {
    points,
    routeState,
    strategy,
    strategyWeights,
    activeRoute,
    hasRoute,
    setPoint,
    clearPoints,
    computeRoutes,
    setActiveRouteFromBackend,
    setActiveMode,
    setStrategy,
    setStrategyWeights,
    clearRouteState
  }
})
