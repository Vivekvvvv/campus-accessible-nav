interface IntegerEnvOptions {
  min?: number
  max?: number
}

export interface AuthConfig {
  tokenSkewSeconds: number
  guardIntervalMs: number
  expiryWarningMinutes: number
  expiryWarningSnoozeMinutes: number
  criticalPulseEnabled: boolean
}

const DEFAULT_AUTH_CONFIG: AuthConfig = {
  tokenSkewSeconds: 90,
  guardIntervalMs: 15000,
  expiryWarningMinutes: 10,
  expiryWarningSnoozeMinutes: 5,
  criticalPulseEnabled: true,
}

function parseIntegerEnv(value: unknown, fallback: number, options: IntegerEnvOptions = {}): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return fallback
  }

  const normalized = Math.floor(parsed)
  if (options.min != null && normalized < options.min) {
    return fallback
  }
  if (options.max != null && normalized > options.max) {
    return fallback
  }
  return normalized
}

function parseBooleanEnv(value: unknown, fallback: boolean): boolean {
  if (value == null || value === '') {
    return fallback
  }
  const normalized = String(value).trim().toLowerCase()
  if (['1', 'true', 'yes', 'on'].includes(normalized)) {
    return true
  }
  if (['0', 'false', 'no', 'off'].includes(normalized)) {
    return false
  }
  return fallback
}

function resolveAuthConfig(): AuthConfig {
  return {
    tokenSkewSeconds: parseIntegerEnv(import.meta.env.VITE_AUTH_TOKEN_SKEW_SECONDS, DEFAULT_AUTH_CONFIG.tokenSkewSeconds, {
      min: 0,
      max: 3600,
    }),
    guardIntervalMs: parseIntegerEnv(import.meta.env.VITE_AUTH_GUARD_INTERVAL_MS, DEFAULT_AUTH_CONFIG.guardIntervalMs, {
      min: 5000,
      max: 300000,
    }),
    expiryWarningMinutes: parseIntegerEnv(
      import.meta.env.VITE_AUTH_EXPIRY_WARNING_MINUTES,
      DEFAULT_AUTH_CONFIG.expiryWarningMinutes,
      {
        min: 1,
        max: 180,
      }
    ),
    expiryWarningSnoozeMinutes: parseIntegerEnv(
      import.meta.env.VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES,
      DEFAULT_AUTH_CONFIG.expiryWarningSnoozeMinutes,
      {
        min: 1,
        max: 120,
      }
    ),
    criticalPulseEnabled: parseBooleanEnv(
      import.meta.env.VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED,
      DEFAULT_AUTH_CONFIG.criticalPulseEnabled
    ),
  }
}

export const authConfig: AuthConfig = resolveAuthConfig()

