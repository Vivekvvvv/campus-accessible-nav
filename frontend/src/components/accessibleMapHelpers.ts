export interface WaypointInput {
  lat: unknown
  lng: unknown
  name?: unknown
}

export interface RouteSummaryLike {
  distanceM?: unknown
  durationSec?: unknown
  riskCount?: unknown
}

export interface RoutePointLike {
  lng: number
  lat: number
  name?: string
}

export interface RouteLike {
  summary?: RouteSummaryLike | null
  distanceM?: unknown
  durationSec?: unknown
  features?: Array<Record<string, unknown>>
  [key: string]: unknown
}

export function isEnvTruthy(value: unknown): boolean {
  const normalized = String(value ?? '').trim().toLowerCase()
  return normalized !== '' && normalized !== '0' && normalized !== 'false' && normalized !== 'off'
}

export function buildBaseMapOptions(
  translate: (key: string) => string,
  hasMapTilerKey: boolean,
): Array<{ key: string; label: string }> {
  const options = [
    { key: 'osm', label: translate('settings.osmMap') },
    { key: 'gaode', label: translate('settings.gaodeMap') },
    { key: 'gaode-satellite', label: translate('settings.gaodeSatellite') },
  ]

  if (hasMapTilerKey) {
    options.push(
      { key: 'streets', label: translate('settings.maptilerStreets') },
      { key: 'basic', label: translate('settings.maptilerBasic') },
      { key: 'satellite', label: translate('settings.maptilerSatellite') },
    )
  }

  return options
}

export function resolveRouteModeFromProfile(profile: unknown): 'walk' | 'wheel' {
  return String((profile as Record<string, unknown> | null)?.mobilityMode || '').toUpperCase() === 'WHEELCHAIR'
    ? 'wheel'
    : 'walk'
}

export function buildWaypointPayload(item: WaypointInput | null | undefined, fallbackName: string) {
  if (!item) return null
  const lat = Number(item.lat)
  const lng = Number(item.lng)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null

  const baseName = String(item.name || '').trim()
  const name = baseName || fallbackName
  const label = baseName || `${lat.toFixed(4)},${lng.toFixed(4)}`

  return {
    waypoint: {
      lat,
      lng,
      name,
      reached: false,
    },
    label,
  }
}

export function resolveObstacleSuccessToast(
  status: unknown,
  translate: (key: string, params?: Record<string, unknown>) => string,
): string {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'PENDING') {
    return translate('toast.obstaclePendingReview')
  }
  if (normalized) {
    return translate('toast.obstacleReportSuccessWithStatus', { status: normalized })
  }
  return translate('obstacle.submitSuccess')
}

export function formatRouteMetrics(distanceM: unknown, durationSec: unknown): { distanceText: string; durationText: string } {
  const distance = Number(distanceM)
  const duration = Number(durationSec)

  const distanceText = distance >= 1000 ? `${(distance / 1000).toFixed(2)} km` : `${Math.round(Number.isFinite(distance) ? distance : 0)} m`
  const durationText = `${Math.max(1, Math.round((Number.isFinite(duration) ? duration : 0) / 60))} min`

  return { distanceText, durationText }
}

export function extractRouteSummaryMetrics(summary: RouteSummaryLike | null | undefined): { distanceM: number; durationSec: number; riskCount: number } {
  return {
    distanceM: Number(summary?.distanceM ?? 0),
    durationSec: Number(summary?.durationSec ?? 0),
    riskCount: Number(summary?.riskCount ?? 0),
  }
}

export function buildHistoryPayload(
  route: RouteLike | null | undefined,
  points: { start: RoutePointLike | null; end: RoutePointLike | null },
  activeMode: string,
) {
  if (!route || !route.summary || !points.start || !points.end) {
    return null
  }

  return {
    start: points.start,
    end: points.end,
    mode: activeMode,
    distanceM: Number(route.summary.distanceM ?? route.distanceM ?? 0),
    durationSec: Number(route.summary.durationSec ?? route.durationSec ?? 0),
  }
}

function transformCoordsRecursively(
  coords: unknown,
  transformFn: (lng: number, lat: number) => { lng: number; lat: number },
): unknown {
  if (!Array.isArray(coords)) return coords
  if (typeof coords[0] === 'number') {
    const lng = Number(coords[0])
    const lat = Number(coords[1])
    const transformed = transformFn(lng, lat)
    return [transformed.lng, transformed.lat]
  }
  return coords.map((item) => transformCoordsRecursively(item, transformFn))
}

export function buildDisplayGeoJson(
  route: RouteLike | null | undefined,
  useGcj: boolean,
  transformFn: (lng: number, lat: number) => { lng: number; lat: number },
) {
  if (!route) return null
  const cloned = JSON.parse(JSON.stringify(route)) as RouteLike
  if (!useGcj || !Array.isArray(cloned.features)) {
    return cloned
  }

  cloned.features.forEach((feature) => {
    const geometry = feature.geometry as { coordinates?: unknown } | null | undefined
    if (geometry && geometry.coordinates) {
      geometry.coordinates = transformCoordsRecursively(geometry.coordinates, transformFn)
    }
  })

  return cloned
}

export function buildShareQrSrc(url: string): string {
  return `https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(url)}`
}

export function buildExportReportContent(input: {
  activeMode: string
  startName: string
  endName: string
  summary: RouteSummaryLike | null | undefined
  translate: (key: string, params?: Record<string, unknown>) => string
}) {
  const { distanceM, durationSec, riskCount } = extractRouteSummaryMetrics(input.summary)
  const { distanceText, durationText } = formatRouteMetrics(distanceM, durationSec)
  const modeLabel = input.translate(input.activeMode === 'wheel' ? 'route.wheelchair' : 'route.walk')

  return {
    title: input.translate('report.routeReportTitle', { mode: modeLabel }),
    startLine: `${input.translate('map.startPoint')}: ${input.startName}`,
    endLine: `${input.translate('map.endPoint')}: ${input.endName}`,
    distanceLine: `${input.translate('route.distance')}: ${distanceText}`,
    durationLine: `${input.translate('route.duration')}: ${durationText}`,
    riskLine: `${input.translate('route.riskCount')}: ${Number.isFinite(riskCount) ? riskCount : 0}`,
  }
}
