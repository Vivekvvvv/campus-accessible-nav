import type { LineCoordinate } from '../utils/polylineUtils'

export interface SessionDestination {
  lng: number
  lat: number
  name: string
}

export interface SessionSnapshot {
  sessionId: string
  resumeToken: string
  status: string
  mode: string
  destination: SessionDestination
  updatedAt: number
}

export function nowIso(): string {
  return new Date().toISOString()
}

export function pickActiveMode(activeMode: string): string {
  return activeMode === 'wheel' ? 'WHEELCHAIR' : 'WALK'
}

export function extractLineCoordinates(activeRoute: unknown): LineCoordinate[] | null {
  if (!activeRoute) return null
  const route = activeRoute as Record<string, unknown>
  const features = route.features as Record<string, unknown>[] | undefined
  const firstFeature = Array.isArray(features) ? features[0] : null
  const coords = (firstFeature?.geometry as Record<string, unknown>)?.coordinates
  return Array.isArray(coords) ? (coords as LineCoordinate[]) : null
}

export function extractLineEndCoordinate(activeRoute: unknown): { lng: number; lat: number } | null {
  const coords = extractLineCoordinates(activeRoute)
  if (!coords || coords.length === 0) return null

  const end = coords[coords.length - 1]
  const lng = Number(end?.[0])
  const lat = Number(end?.[1])
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null

  return { lng, lat }
}

export function formatDistanceM(distanceM: number): string {
  const value = Number(distanceM)
  if (!Number.isFinite(value)) return '--'
  if (value >= 1000) return `${(value / 1000).toFixed(2)} km`
  return `${Math.max(0, Math.round(value))} m`
}

export function toSessionDestination(data: Record<string, unknown> | null, current: SessionDestination | null): SessionDestination | null {
  if (!data) return current

  const lng = Number(data.destinationLng ?? data.destination_lng)
  const lat = Number(data.destinationLat ?? data.destination_lat)
  const name = String(data.destinationName ?? data.destination_name ?? '')

  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    return current
  }

  return { lng, lat, name }
}
