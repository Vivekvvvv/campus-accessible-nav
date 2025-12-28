import { type Ref } from 'vue'
import { getCurrentTraceId } from '../utils/fetchUtils'

export interface ClientErrorState {
  lastSentAt: number
}

export interface ClientErrorReportingOptions {
  clientErrorState: Ref<ClientErrorState>
  clientErrorUrl: string
}

export interface ClientErrorPayload {
  type: string
  message: string
  stack?: string
  url?: string
  meta?: string | null
}

function safeTruncate(s: unknown, maxLen: number): string {
  const v = String(s || '')
  if (v.length <= maxLen) return v
  return v.slice(0, maxLen) + '...'
}

export function useClientErrorReporting({ clientErrorState, clientErrorUrl }: ClientErrorReportingOptions) {
  async function reportClientError(payload: ClientErrorPayload): Promise<void> {
    const now = Date.now()
    if (now - clientErrorState.value.lastSentAt < 10_000) return
    clientErrorState.value.lastSentAt = now

    try {
      const traceId = getCurrentTraceId()
      await fetch(clientErrorUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Trace-Id': traceId,
        },
        body: JSON.stringify({
          ...payload,
          meta: safeTruncate(
            payload?.meta ? `${payload.meta} traceId=${traceId}` : `traceId=${traceId}`,
            400
          ),
        }),
      })
    } catch {
      // ignore
    }
  }

  const onWindowError = (ev: ErrorEvent): void => {
    const message = ev?.message || 'Unknown error'
    const filename = ev?.filename || ''
    const lineno = ev?.lineno || 0
    const colno = ev?.colno || 0

    reportClientError({
      type: 'error',
      message: safeTruncate(message, 800),
      stack: safeTruncate(ev?.error?.stack || '', 2000),
      url: window.location.href,
      meta: safeTruncate(`${filename}:${lineno}:${colno}`, 400),
    })
  }

  const onUnhandledRejection = (ev: PromiseRejectionEvent): void => {
    const reason = ev?.reason
    reportClientError({
      type: 'unhandledrejection',
      message: safeTruncate(reason?.message || String(reason || 'Unhandled rejection'), 800),
      stack: safeTruncate(reason?.stack || '', 2000),
      url: window.location.href,
      meta: null,
    })
  }

  function installClientErrorHandlers(): void {
    window.addEventListener('error', onWindowError)
    window.addEventListener('unhandledrejection', onUnhandledRejection)
  }

  function uninstallClientErrorHandlers(): void {
    window.removeEventListener('error', onWindowError)
    window.removeEventListener('unhandledrejection', onUnhandledRejection)
  }

  return {
    reportClientError,
    onWindowError,
    onUnhandledRejection,
    installClientErrorHandlers,
    uninstallClientErrorHandlers,
  }
}
