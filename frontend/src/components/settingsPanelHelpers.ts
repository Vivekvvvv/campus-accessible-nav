export interface AccessibilityProfileForm {
  mobilityMode: string
  avoidStairs: boolean
  avoidSlope: boolean
  avoidConstruction: boolean
  maxSlopePercent: number
}

export interface VoicePolicyForm {
  preTurnM: number
  preArrivalM: number
  announceIntervalM: number
  quietHoursStart: string
  quietHoursEnd: string
  vibrateEnabled: boolean
}

export function buildDefaultAccessibilityProfile(): AccessibilityProfileForm {
  return {
    mobilityMode: 'WALK',
    avoidStairs: false,
    avoidSlope: false,
    avoidConstruction: true,
    maxSlopePercent: 12,
  }
}

export function normalizeAccessibilityProfile(value: unknown): AccessibilityProfileForm {
  const defaults = buildDefaultAccessibilityProfile()
  if (!value || typeof value !== 'object') return defaults

  const source = value as Record<string, unknown>
  const maxSlope = Number(source.maxSlopePercent)
  return {
    mobilityMode: String(source.mobilityMode || defaults.mobilityMode).toUpperCase(),
    avoidStairs: Boolean(source.avoidStairs),
    avoidSlope: Boolean(source.avoidSlope),
    avoidConstruction: Boolean(source.avoidConstruction),
    maxSlopePercent: Number.isFinite(maxSlope) ? Math.max(0, Math.min(45, maxSlope)) : defaults.maxSlopePercent,
  }
}

export function buildDefaultVoicePolicy(): VoicePolicyForm {
  return {
    preTurnM: 12,
    preArrivalM: 20,
    announceIntervalM: 20,
    quietHoursStart: '',
    quietHoursEnd: '',
    vibrateEnabled: true,
  }
}

function clamp(value: unknown, min: number, max: number, fallback: number): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return fallback
  if (parsed < min) return min
  if (parsed > max) return max
  return parsed
}

export function normalizeVoicePolicy(value: unknown): VoicePolicyForm {
  const defaults = buildDefaultVoicePolicy()
  if (!value || typeof value !== 'object') return defaults

  const source = value as Record<string, unknown>
  return {
    preTurnM: clamp(source.preTurnM, 0, 500, defaults.preTurnM),
    preArrivalM: clamp(source.preArrivalM, 0, 1000, defaults.preArrivalM),
    announceIntervalM: clamp(source.announceIntervalM, 0, 500, defaults.announceIntervalM),
    quietHoursStart: String(source.quietHoursStart || '').trim(),
    quietHoursEnd: String(source.quietHoursEnd || '').trim(),
    vibrateEnabled: Boolean(source.vibrateEnabled),
  }
}
