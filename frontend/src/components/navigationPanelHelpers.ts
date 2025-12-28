export interface LevelHintInput {
  currentLevel: unknown
  nextLevel: unknown
  levelTransitionVia?: unknown
}

export function formatCoordsText(location: unknown): string {
  const loc = location as Record<string, unknown> | null
  if (!loc) return '--'
  const lat = Number(loc.lat)
  const lng = Number(loc.lng)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return '--'
  return `${lat.toFixed(6)}, ${lng.toFixed(6)}`
}

export function formatOffRouteText(distance: unknown): string {
  const value = Number(distance)
  if (!Number.isFinite(value)) return '--'
  return `${Math.round(value)}m`
}

export function formatHazardText(hazard: unknown): string {
  const value = hazard as Record<string, unknown> | null
  if (!value) return ''
  const reason = String(value.reason || '').trim() || '--'
  const remaining = Number(value.remainingM)
  const distText = Number.isFinite(remaining) ? `${Math.max(0, Math.round(remaining))}m` : '--'
  return `${distText} | ${reason}`
}

export function buildLevelHintText(
  input: LevelHintInput,
  translate: (key: string, params?: Record<string, unknown>) => string,
): string {
  const current = Number(input.currentLevel)
  const next = Number(input.nextLevel)
  if (!Number.isFinite(current) || !Number.isFinite(next) || current === next) {
    return ''
  }

  const via = String(input.levelTransitionVia || '').trim()
  return translate('navigation.levelTransitionHint', {
    from: Math.round(current),
    to: Math.round(next),
    via: via || translate('navigation.levelTransitionDefaultVia'),
  })
}
