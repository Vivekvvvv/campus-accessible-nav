import type { LineCoordinate } from '../utils/polylineUtils'

import type { RouteResponse } from '../types/api'
import type { NavInfo, TimelineStep, VoicePolicy } from './types/navigation'

export const DEFAULT_VOICE_POLICY: Readonly<VoicePolicy> = Object.freeze({
  preTurnM: 12,
  preArrivalM: 20,
  announceIntervalM: 20,
  quietHoursStart: '',
  quietHoursEnd: '',
  vibrateEnabled: true,
})

export function clamp(value: unknown, min: number, max: number, fallback: number): number {
  const num = Number(value)
  if (!Number.isFinite(num)) return fallback
  if (num < min) return min
  if (num > max) return max
  return num
}

export function parseTimeToMinutes(value: unknown): number | null {
  if (typeof value !== 'string') return null
  const text = value.trim()
  if (!text) return null
  const match = text.match(/^([01]\d|2[0-3]):([0-5]\d)$/)
  if (!match) return null
  return Number(match[1]) * 60 + Number(match[2])
}

export function isInQuietHours(policy: VoicePolicy | null): boolean {
  const start = parseTimeToMinutes(policy?.quietHoursStart)
  const end = parseTimeToMinutes(policy?.quietHoursEnd)
  if (!Number.isFinite(start) || !Number.isFinite(end) || start === end) {
    return false
  }

  const now = new Date()
  const current = now.getHours() * 60 + now.getMinutes()
  if (start! < end!) {
    return current >= start! && current < end!
  }
  return current >= start! || current < end!
}

export function normalizeVoicePolicy(raw: unknown): VoicePolicy {
  if (!raw || typeof raw !== 'object') {
    return { ...DEFAULT_VOICE_POLICY }
  }

  const source = raw as Record<string, unknown>
  return {
    preTurnM: clamp(source.preTurnM, 0, 500, DEFAULT_VOICE_POLICY.preTurnM),
    preArrivalM: clamp(source.preArrivalM, 0, 1000, DEFAULT_VOICE_POLICY.preArrivalM),
    announceIntervalM: clamp(source.announceIntervalM, 0, 500, DEFAULT_VOICE_POLICY.announceIntervalM),
    quietHoursStart: String(source.quietHoursStart || '').trim(),
    quietHoursEnd: String(source.quietHoursEnd || '').trim(),
    vibrateEnabled: typeof source.vibrateEnabled === 'boolean' ? source.vibrateEnabled : DEFAULT_VOICE_POLICY.vibrateEnabled,
  }
}

export function unwrapApiPayload(resp: unknown): Record<string, unknown> | null {
  if (!resp || typeof resp !== 'object') return null
  const payload = resp as Record<string, unknown>
  if ('data' in payload && payload.data && typeof payload.data === 'object') {
    return payload.data as Record<string, unknown>
  }
  return payload
}

export function formatDurationSec(durationSec: number): string {
  const value = Number(durationSec)
  if (!Number.isFinite(value)) return '--'
  if (value >= 3600) {
    const hours = Math.floor(value / 3600)
    const minutes = Math.round((value % 3600) / 60)
    return `${hours}h ${minutes}m`
  }
  return `${Math.max(1, Math.round(value / 60))} min`
}

export function extractLineCoordinates(activeRoute: unknown): LineCoordinate[] | null {
  const route = activeRoute as Record<string, unknown> | null
  const firstFeature = Array.isArray(route?.features) ? (route!.features as Record<string, unknown>[])[0] : null
  const coords = (firstFeature?.geometry as Record<string, unknown>)?.coordinates
  return Array.isArray(coords) ? (coords as LineCoordinate[]) : null
}

export function isTurnAction(action: string): boolean {
  return action === 'TURN_LEFT' || action === 'TURN_RIGHT'
}

export function buildInstructionTimeline(instructions: unknown, totalLineM: number): TimelineStep[] {
  const steps = Array.isArray(instructions) ? (instructions as Record<string, unknown>[]) : []
  const total = Number(totalLineM)
  if (!steps.length || !Number.isFinite(total) || total <= 0) return []

  const sumGo = steps
    .filter((step) => String(step?.action || '') === 'GO')
    .reduce((acc, step) => acc + Math.max(0, Number(step?.distanceM || 0)), 0)

  const scale = sumGo > 0 ? total / sumGo : 1
  let cum = 0

  return steps.map((step, idx) => {
    const action = String(step?.action || '')
    const text = String(step?.text || '').trim()
    const dist = Math.max(0, Number(step?.distanceM || 0)) * scale

    if (action === 'GO') {
      const startM = cum
      const endM = Math.min(total, cum + dist)
      cum = endM
      return { idx, action, text, startM, endM, atM: endM }
    }

    return { idx, action, text, startM: cum, endM: cum, atM: cum }
  })
}

export function defaultNavInfo(): NavInfo {
  return {
    currentText: '--',
    nextText: '--',
    remainingDistanceM: null,
    remainingDurationSec: null,
    remainingDistanceText: '--',
    remainingDurationText: '--',
    remainingToNextM: null,
    remainingToNextText: '--',
    _meta: null,
  }
}

export function routeSignature(route: RouteResponse | null): string {
  const current = route as Record<string, unknown> | null
  return `${current?.mode || ''}:${current?.distanceM || ''}:${current?.durationSec || ''}:${
    (current?.features as unknown[])?.length || 0
  }:${(current?.instructions as unknown[])?.length || 0}`
}
