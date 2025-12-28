import { isRef, type Ref } from 'vue'
import i18n from '../locales'

export interface BaseMapState {
  current: string
  lastError: string | null
}

export interface BaseMapStyleControllerOptions {
  baseMapState: Ref<BaseMapState> | BaseMapState
  getMap: (() => { setStyle: (style: unknown, opts?: { diff: boolean }) => void } | null) | null
  apiBaseUrl: string
  maptilerKey: string
  maptilerStyleAliases?: Record<string, string>
  maptilerBaseKeys?: string[]
}

export interface BaseMapStyleController {
  getBaseMapStyle: (key: string) => unknown
  setBaseMapStyle: (key: string) => void
  handleBaseMapError: (ev: unknown) => void
  resolveSafeBaseMap: () => string
}

export function normalizeStyleKey(key: unknown): string {
  return String(key || '').trim().toLowerCase()
}

function osmRasterStyle() {
  return {
    version: 8,
    sources: {
      osm: {
        type: 'raster',
        tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
        tileSize: 256,
        maxzoom: 19,
        attribution: '© OpenStreetMap contributors',
      },
    },
    layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
  }
}

function gaodeRasterStyle({ apiBaseUrl, kind }: { apiBaseUrl: string; kind: string }) {
  if (kind === 'satellite') {
    return {
      version: 8,
      sources: {
        gaodeSatellite: {
          type: 'raster',
          tiles: [`${apiBaseUrl}/api/tiles/gaode?style=6&scale=1&size=1&x={x}&y={y}&z={z}`],
          tileSize: 256,
          maxzoom: 18,
          attribution: 'Gaode',
        },
        gaodeLabel: {
          type: 'raster',
          tiles: [`${apiBaseUrl}/api/tiles/gaode?style=8&lang=zh_cn&scale=1&size=1&x={x}&y={y}&z={z}`],
          tileSize: 256,
          maxzoom: 18,
        },
      },
      layers: [
        { id: 'gaode-satellite', type: 'raster', source: 'gaodeSatellite' },
        { id: 'gaode-label', type: 'raster', source: 'gaodeLabel' },
      ],
    }
  }

  return {
    version: 8,
    sources: {
      gaode: {
        type: 'raster',
        tiles: [`${apiBaseUrl}/api/tiles/gaode?style=7&lang=zh_cn&scale=1&size=1&x={x}&y={y}&z={z}`],
        tileSize: 256,
        maxzoom: 18,
        attribution: 'Gaode',
      },
    },
    layers: [{ id: 'gaode', type: 'raster', source: 'gaode' }],
  }
}

function resolveMapTilerStyleId({ key, maptilerStyleAliases }: { key: string; maptilerStyleAliases?: Record<string, string> }): string {
  const normalized = normalizeStyleKey(key)
  return maptilerStyleAliases?.[normalized] || normalized
}

function buildMapTilerStyleUrl({ key, maptilerKey, maptilerStyleAliases }: { key: string; maptilerKey: string; maptilerStyleAliases?: Record<string, string> }): string | null {
  if (!maptilerKey) return null
  const styleId = resolveMapTilerStyleId({ key, maptilerStyleAliases })
  if (!styleId) return null
  return `https://api.maptiler.com/maps/${styleId}/style.json?key=${maptilerKey}`
}

function resolveSafeBaseMap({ maptilerKey }: { maptilerKey: string }): string {
  return maptilerKey ? 'streets' : 'osm'
}

export function createBaseMapStyleController({
  baseMapState,
  getMap,
  apiBaseUrl,
  maptilerKey,
  maptilerStyleAliases,
  maptilerBaseKeys,
}: BaseMapStyleControllerOptions): BaseMapStyleController {
  let baseMapFallbackUsed = false
  const resolveState = (): BaseMapState | null => (isRef(baseMapState) ? baseMapState.value : baseMapState)

  function getBaseMapStyle(key: string): unknown {
    const normalized = normalizeStyleKey(key)
    if (normalized === 'gaode') return gaodeRasterStyle({ apiBaseUrl, kind: 'vector' })
    if (normalized === 'gaode-satellite') return gaodeRasterStyle({ apiBaseUrl, kind: 'satellite' })
    if (normalized === 'osm') return osmRasterStyle()
    if (!maptilerKey) return osmRasterStyle()
    return (
      buildMapTilerStyleUrl({ key: normalized, maptilerKey, maptilerStyleAliases }) || osmRasterStyle()
    )
  }

  function setBaseMapStyle(key: string): void {
    const state = resolveState()
    if (!state) return
    const normalized = normalizeStyleKey(key)
    baseMapFallbackUsed = false
    state.current = normalized
    state.lastError = null

    if (!maptilerKey && (maptilerBaseKeys || []).includes(normalized)) {
      state.lastError = tr('settings.maptilerMissingKeyFallbackOsm')
      state.current = 'osm'
    }

    const map = getMap?.() || null
    if (!map) return
    map.setStyle(getBaseMapStyle(state.current), { diff: false })
  }

  function applyBaseMapFallback(message: string): void {
    const map = getMap?.() || null
    if (baseMapFallbackUsed || !map) return
    baseMapFallbackUsed = true

    const fallback = resolveSafeBaseMap({ maptilerKey })
    const state = resolveState()
    if (state) {
      state.lastError = message
      state.current = fallback
    }
    map.setStyle(getBaseMapStyle(fallback), { diff: false })
  }

  function handleBaseMapError(ev: unknown): void {
    const map = getMap?.() || null
    if (baseMapFallbackUsed || !map) return

    const evObj = ev as Record<string, unknown> | null
    const errorObj = evObj?.error as Record<string, unknown> | undefined
    const message = String(errorObj?.message || '')
    const sourceId = String(evObj?.sourceId || (evObj?.source as Record<string, unknown>)?.id || '')
    const isCorsError = message.includes('CORS') || message.includes('Access-Control-Allow-Origin')
    const isGaode = sourceId.startsWith('gaode') || message.includes('autonavi') || message.includes('amap')
    const isMapTilerError = message.includes('maptiler')
    const isOsmError = sourceId === 'osm' || message.includes('openstreetmap')
    const isNetworkError =
      message.includes('Failed to fetch') ||
      message.includes('NetworkError') ||
      message.includes('401') ||
      message.includes('403')

    if (isGaode && (isCorsError || isNetworkError)) {
      applyBaseMapFallback(tr('settings.gaodeMapLoadFailedFallback'))
      return
    }

    if (!isMapTilerError && !isOsmError && !isNetworkError) return
    applyBaseMapFallback(tr('settings.baseMapLoadFailedFallback'))
  }

  return {
    getBaseMapStyle,
    setBaseMapStyle,
    handleBaseMapError,
    resolveSafeBaseMap: () => resolveSafeBaseMap({ maptilerKey }),
  }
}

function tr(key: string, params?: Record<string, unknown>): string {
  return i18n.global.t(key, params as Record<string, string>)
}
