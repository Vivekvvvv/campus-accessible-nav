// Small geo helpers for navigation/off-route detection.
// Keep it lightweight (no external deps) and OK for short campus-scale routes.

export type LineCoordinate = [number, number]

export interface ProjectionResult {
  distanceToLineM: number
  alongM: number
  totalM: number
}

function toMetersXY(lng: number, lat: number, refLatRad: number): { x: number; y: number } {
  // Equirectangular approximation.
  const x = (lng * 111320) * Math.cos(refLatRad)
  const y = lat * 110540
  return { x, y }
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v))
}

/**
 * Compute minimum distance from a point to a LineString (meters).
 */
export function distanceToLineStringMeters(p: { lng: number; lat: number }, coordinates: LineCoordinate[]): number {
  if (!p || !Array.isArray(coordinates) || coordinates.length < 2) return Infinity
  const lng = Number(p.lng)
  const lat = Number(p.lat)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return Infinity

  const refLatRad = (lat * Math.PI) / 180
  const P = toMetersXY(lng, lat, refLatRad)

  let best = Infinity
  for (let i = 0; i < coordinates.length - 1; i++) {
    const a = coordinates[i]
    const b = coordinates[i + 1]
    if (!a || !b) continue
    const ax = Number(a[0])
    const ay = Number(a[1])
    const bx = Number(b[0])
    const by = Number(b[1])
    if (![ax, ay, bx, by].every(Number.isFinite)) continue

    const A = toMetersXY(ax, ay, refLatRad)
    const B = toMetersXY(bx, by, refLatRad)

    const vx = B.x - A.x
    const vy = B.y - A.y
    const wx = P.x - A.x
    const wy = P.y - A.y

    const vv = vx * vx + vy * vy
    const t = vv === 0 ? 0 : clamp((wx * vx + wy * vy) / vv, 0, 1)
    const projX = A.x + t * vx
    const projY = A.y + t * vy
    const dx = P.x - projX
    const dy = P.y - projY
    const d = Math.sqrt(dx * dx + dy * dy)
    if (d < best) best = d
  }

  return best
}

/**
 * Project a point onto a LineString and return along-route distance (meters).
 */
export function projectToLineStringMeters(p: { lng: number; lat: number }, coordinates: LineCoordinate[]): ProjectionResult | null {
  if (!p || !Array.isArray(coordinates) || coordinates.length < 2) return null
  const lng = Number(p.lng)
  const lat = Number(p.lat)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null

  const refLatRad = (lat * Math.PI) / 180
  const P = toMetersXY(lng, lat, refLatRad)

  let best: ProjectionResult = { distanceToLineM: Infinity, alongM: 0, totalM: 0 }
  let cum = 0

  for (let i = 0; i < coordinates.length - 1; i++) {
    const a = coordinates[i]
    const b = coordinates[i + 1]
    if (!a || !b) continue
    const ax = Number(a[0])
    const ay = Number(a[1])
    const bx = Number(b[0])
    const by = Number(b[1])
    if (![ax, ay, bx, by].every(Number.isFinite)) continue

    const A = toMetersXY(ax, ay, refLatRad)
    const B = toMetersXY(bx, by, refLatRad)

    const vx = B.x - A.x
    const vy = B.y - A.y
    const segLen = Math.sqrt(vx * vx + vy * vy)
    if (segLen === 0) continue

    const wx = P.x - A.x
    const wy = P.y - A.y
    const t = clamp((wx * vx + wy * vy) / (segLen * segLen), 0, 1)

    const projX = A.x + t * vx
    const projY = A.y + t * vy
    const dx = P.x - projX
    const dy = P.y - projY
    const d = Math.sqrt(dx * dx + dy * dy)

    if (d < best.distanceToLineM) {
      best = { distanceToLineM: d, alongM: cum + t * segLen, totalM: 0 }
    }
    cum += segLen
  }

  best.totalM = cum
  return best.distanceToLineM === Infinity ? null : best
}
