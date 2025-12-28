/* eslint-disable no-console */
// Minimal logger wrapper to avoid noisy console output in production builds.
// - debug/log/info/warn: DEV only
// - error: DEV console, PROD reports to backend (/api/client/error)

export interface Logger {
  debug: (...args: unknown[]) => void
  log: (...args: unknown[]) => void
  info: (...args: unknown[]) => void
  warn: (...args: unknown[]) => void
  error: (...args: unknown[]) => void
}

const TRACE_ID_STORAGE_KEY = 'accessiblenav_trace_id'
const ERROR_REPORT_THROTTLE_MS = 10_000
let lastErrorReportedAt = 0

function shouldLog() {
  return import.meta.env.DEV && !import.meta.env.VITEST
}

function shouldReportErrorInProd() {
  if (import.meta.env.DEV || import.meta.env.VITEST) return false
  const reportingToggle = String(import.meta.env.VITE_ENABLE_CLIENT_ERROR_REPORTING ?? '1')
  return !['0', 'false', 'off', 'no'].includes(reportingToggle.trim().toLowerCase())
}

function buildClientErrorUrl() {
  const apiBaseUrl = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return apiBaseUrl ? `${apiBaseUrl}/api/client/error` : '/api/client/error'
}

function safeTruncate(s: unknown, maxLen: number): string {
  const v = String(s || '')
  if (v.length <= maxLen) return v
  return `${v.slice(0, maxLen)}...`
}

function argToString(arg: unknown): string {
  if (arg instanceof Error) {
    return `${arg.name}: ${arg.message}`
  }
  if (typeof arg === 'string') {
    return arg
  }
  try {
    return JSON.stringify(arg)
  } catch {
    return String(arg)
  }
}

function getErrorStack(args: unknown[]): string {
  const err = args.find((arg) => arg instanceof Error)
  return err instanceof Error ? safeTruncate(err.stack || '', 2000) : ''
}

function getCurrentTraceId(): string {
  try {
    return localStorage.getItem(TRACE_ID_STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

function reportErrorInProd(args: unknown[]) {
  if (!shouldReportErrorInProd()) return
  if (typeof fetch !== 'function') return

  const now = Date.now()
  if (now - lastErrorReportedAt < ERROR_REPORT_THROTTLE_MS) return
  lastErrorReportedAt = now

  const message = safeTruncate(args.map(argToString).join(' '), 800)
  const stack = getErrorStack(args)
  const traceId = getCurrentTraceId()
  const url =
    typeof location !== 'undefined' && typeof location.href === 'string'
      ? location.href
      : ''
  const meta = safeTruncate(traceId ? `traceId=${traceId}` : '', 400)

  void fetch(buildClientErrorUrl(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(traceId ? { 'X-Trace-Id': traceId } : {}),
    },
    body: JSON.stringify({
      type: 'logger.error',
      message,
      stack,
      url,
      meta,
    }),
    keepalive: true,
  }).catch(() => {
    // no-op: reporting must never affect runtime behavior.
  })
}

export const logger: Logger = {
  debug: (...args: unknown[]) => {
    if (shouldLog()) console.debug(...args)
  },
  log: (...args: unknown[]) => {
    if (shouldLog()) console.log(...args)
  },
  info: (...args: unknown[]) => {
    if (shouldLog()) console.info(...args)
  },
  warn: (...args: unknown[]) => {
    if (shouldLog()) console.warn(...args)
  },
  error: (...args: unknown[]) => {
    if (shouldLog()) {
      console.error(...args)
      return
    }
    reportErrorInProd(args)
  },
}
