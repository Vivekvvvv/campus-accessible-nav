import { logger } from './logger'
import { clearAuthSessions, getActiveAuthToken, type AuthTokenSource } from './authSession'

const NEW_TOKEN_HEADER = 'x-new-access-token'
const TOKEN_EXPIRES_HEADER = 'x-token-expires-in'
const TRACE_ID_HEADER = 'x-trace-id'
const TRACE_ID_STORAGE_KEY = 'accessiblenav_trace_id'

export interface HttpError extends Error {
  status?: number
  traceId?: string
  code?: string
}

function generateTraceId(): string {
  const random = Math.random().toString(16).slice(2)
  const timestamp = Date.now().toString(16)
  return `${timestamp}${random}`.slice(0, 16).padEnd(16, '0')
}

function ensureTraceId(): string {
  let traceId = localStorage.getItem(TRACE_ID_STORAGE_KEY)
  if (!traceId) {
    traceId = generateTraceId()
    localStorage.setItem(TRACE_ID_STORAGE_KEY, traceId)
  }
  return traceId
}

function syncTraceIdFromResponse(response: Response): string {
  const traceId = response.headers.get(TRACE_ID_HEADER)
  if (traceId) {
    localStorage.setItem(TRACE_ID_STORAGE_KEY, traceId)
    return traceId
  }
  return localStorage.getItem(TRACE_ID_STORAGE_KEY) || ensureTraceId()
}

function buildAuthHeaders(): Record<string, string> {
  const { token } = getActiveAuthToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function buildBaseHeaders(): Record<string, string> {
  return {
    [TRACE_ID_HEADER]: ensureTraceId(),
    ...buildAuthHeaders(),
  }
}

function handleTokenRefresh(response: Response, source: AuthTokenSource | null): void {
  const newToken = response.headers.get(NEW_TOKEN_HEADER)
  if (!newToken) return

  if (source === 'admin') {
    localStorage.setItem('accessiblenav_admin_token', newToken)
    logger.debug('[FetchUtils] admin token refreshed')
  } else if (source === 'user') {
    localStorage.setItem('accessiblenav_user_token', newToken)
    logger.debug('[FetchUtils] user token refreshed')
  } else {
    const adminToken = localStorage.getItem('accessiblenav_admin_token')
    if (adminToken) {
      localStorage.setItem('accessiblenav_admin_token', newToken)
      logger.debug('[FetchUtils] admin token refreshed')
    } else {
      localStorage.setItem('accessiblenav_user_token', newToken)
      logger.debug('[FetchUtils] user token refreshed')
    }
  }

  const expiresIn = response.headers.get(TOKEN_EXPIRES_HEADER)
  if (expiresIn) {
    logger.debug(`[FetchUtils] token expires in ${expiresIn}s`)
  }
}

function shouldClearAuthSession(status: number, message: string, code?: string): boolean {
  if (status !== 401 && status !== 403) {
    return false
  }
  if (status === 401) {
    return true
  }
  const summary = `${code || ''} ${message || ''}`.toLowerCase()
  return ['expired', 'token', 'jwt', 'unauthorized', 'authentication', 'invalid', 'forbidden'].some((keyword) =>
    summary.includes(keyword)
  )
}

async function safeReadText(res: Response): Promise<string> {
  try {
    const buffer = await res.arrayBuffer()
    return new TextDecoder('utf-8').decode(buffer).trim()
  } catch {
    return ''
  }
}

async function parseResponseData(res: Response): Promise<unknown> {
  const text = await safeReadText(res)
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function buildHttpError(res: Response, data: unknown, traceId: string): HttpError {
  const errorMessage =
    (data && typeof data === 'object' && ((data as Record<string, unknown>).message || (data as Record<string, unknown>).error)) ||
    res.statusText ||
    `HTTP ${res.status}`
  const error = new Error(String(errorMessage)) as HttpError
  error.status = res.status
  error.traceId = (data && typeof data === 'object' && (data as Record<string, unknown>).traceId as string) || traceId
  error.code = data && typeof data === 'object' ? (data as Record<string, unknown>).code as string : undefined
  return error
}

async function requestJson<T = unknown>(url: string, method: string, body?: unknown): Promise<T> {
  const { source } = getActiveAuthToken()
  const headers: Record<string, string> = buildBaseHeaders()
  if (body !== undefined && body !== null) {
    headers['Content-Type'] = 'application/json'
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body !== undefined && body !== null ? JSON.stringify(body) : undefined,
  })

  handleTokenRefresh(res, source)
  const traceId = syncTraceIdFromResponse(res)
  const data = await parseResponseData(res)

  if (!res.ok) {
    const error = buildHttpError(res, data, traceId)
    if (shouldClearAuthSession(res.status, error.message, error.code)) {
      clearAuthSessions('all', true)
    }
    logger.error(
      `[FetchUtils] request failed method=${method} url=${url} status=${res.status} traceId=${error.traceId || '-'}`
    )
    throw error
  }

  return data as T
}

export async function readResponseText(res: Response): Promise<string> {
  const data = await parseResponseData(res)
  if (typeof data === 'string') return data
  if (data && typeof data === 'object' && typeof (data as Record<string, unknown>).message === 'string') return (data as Record<string, string>).message
  if (data && typeof data === 'object' && typeof (data as Record<string, unknown>).error === 'string') return (data as Record<string, string>).error
  return ''
}

export async function postJson<T = unknown>(url: string, body?: unknown): Promise<T> {
  return requestJson<T>(url, 'POST', body)
}

export async function getJson<T = unknown>(url: string): Promise<T> {
  return requestJson<T>(url, 'GET')
}

export async function putJson<T = unknown>(url: string, body?: unknown): Promise<T> {
  return requestJson<T>(url, 'PUT', body)
}

export async function deleteJson<T = unknown>(url: string): Promise<T> {
  return requestJson<T>(url, 'DELETE')
}

export function getCurrentTraceId(): string {
  return localStorage.getItem(TRACE_ID_STORAGE_KEY) || ensureTraceId()
}
