/**
 * Common type aliases extracted from schema.d.ts for convenient usage.
 */

export interface NavigationSessionDto {
  sessionId?: string
  session_id?: string
  status?: string
  mode?: string
  destinationLng?: number
  destinationLat?: number
  destinationName?: string
  destination_lng?: number
  destination_lat?: number
  destination_name?: string
  deviationCount?: number
  deviation_count?: number
  rerouteCount?: number
  reroute_count?: number
  resumeToken?: string
  resume_token?: string
  route?: RouteResponse | null
  waypoints?: WaypointDto[]
  currentLeg?: number
  current_leg?: number
  totalLegs?: number
  total_legs?: number
  currentLevel?: number
  current_level?: number
  nextLevel?: number
  next_level?: number
  levelTransitionVia?: string
  level_transition_via?: string
}

export interface WaypointDto {
  lat: number
  lng: number
  name?: string
  reached?: boolean
}

export interface RouteResponse {
  type?: string
  features?: RouteFeature[]
  mode?: string
  distanceM?: number
  durationSec?: number
  riskCount?: number
  instructions?: RouteInstruction[]
  explain?: string[]
  debug?: unknown
  startSnap?: unknown
  endSnap?: unknown
  pathWithLevel?: unknown[]
  levelTransitions?: unknown[]
  modeDiff?: unknown
  routingPolicy?: unknown
  summary?: RouteSummary
  path?: Array<{ lng: number; lat: number }>
}

export interface RouteFeature {
  type: string
  geometry: {
    type: string
    coordinates: [number, number][]
  }
  properties?: Record<string, unknown>
}

export interface RouteInstruction {
  action?: string
  text?: string
  distanceM?: number
}

export interface RouteSummary {
  distanceM: number
  durationSec: number
  riskCount: number
}

export interface LocationUpdate {
  lat: number
  lng: number
  accuracyM?: number | null
  observedAt?: string
}

export interface ClientEventPayload {
  type: string
  payload?: string | null
  observedAt?: string
}

export interface HazardEffect {
  effectId?: number
  edgeId?: number
  reason?: string
  endAt?: string
  fromLat?: number
  fromLng?: number
  toLat?: number
  toLng?: number
  distanceToRouteM?: number
  routeAtM?: number
}

export interface UserLocation {
  lng: number
  lat: number
  heading?: number
  accuracy?: number
}

export interface RoutePoint {
  lng: number
  lat: number
  name?: string
  label?: string
}

export interface FavoritePlaceDto {
  id?: number
  name?: string
  lng?: number
  lat?: number
  groupName?: string
  groupId?: number | null
  tags?: string[]
  createdAt?: string | null
  updatedAt?: string | null
}

export interface AccessibilityProfile {
  mobilityMode: string
  avoidStairs: boolean
  avoidSlope: boolean
  avoidConstruction: boolean
  maxSlopePercent: number
}

export interface ApiResponse<T = unknown> {
  data?: T
  message?: string
  error?: string
  code?: string
}
