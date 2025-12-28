import { clearAuthSessions, getActiveAuthToken, type AuthTokenSource } from '../utils/authSession'
import { logger } from '../utils/logger'

const API_BASE_URL: string = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const NEW_TOKEN_HEADER = 'x-new-access-token'
const TOKEN_EXPIRES_HEADER = 'x-token-expires-in'

export class ApiError extends Error {
  status?: number
  code?: string | null
  details?: unknown

  constructor(message: string, { status, code, details }: { status?: number; code?: string | null; details?: unknown } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

function getToken(): string | null {
  return getActiveAuthToken().token
}

function updateToken(newToken: string, source: AuthTokenSource | null): void {
  if (source === 'admin') {
    localStorage.setItem('accessiblenav_admin_token', newToken)
    logger.debug('[API] admin token refreshed')
    return
  }
  if (source === 'user') {
    localStorage.setItem('accessiblenav_user_token', newToken)
    logger.debug('[API] user token refreshed')
    return
  }

  const adminToken = localStorage.getItem('accessiblenav_admin_token')
  if (adminToken) {
    localStorage.setItem('accessiblenav_admin_token', newToken)
    logger.debug('[API] admin token refreshed')
  } else {
    localStorage.setItem('accessiblenav_user_token', newToken)
    logger.debug('[API] user token refreshed')
  }
}

function handleTokenRefresh(response: Response, source: AuthTokenSource | null): void {
  const newToken = response.headers.get(NEW_TOKEN_HEADER)
  if (!newToken) return
  updateToken(newToken, source)
  const expiresIn = response.headers.get(TOKEN_EXPIRES_HEADER)
  if (expiresIn) {
    logger.debug(`[API] token expires in ${expiresIn}s`)
  }
}

function shouldClearAuthSession(status: number, message: string, code: string | null): boolean {
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

export async function apiRequest(endpoint: string, options: RequestInit = {}): Promise<Response> {
  const url = `${API_BASE_URL}${endpoint}`
  const headers = new Headers(options.headers || {})

  const { token, source } = getActiveAuthToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (options.body && typeof options.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(url, {
    ...options,
    headers,
  })

  handleTokenRefresh(response, source)
  return response
}

export async function apiGet(endpoint: string, options: RequestInit = {}): Promise<Response> {
  return apiRequest(endpoint, { ...options, method: 'GET' })
}

export async function apiPost(endpoint: string, data?: unknown, options: RequestInit = {}): Promise<Response> {
  return apiRequest(endpoint, {
    ...options,
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function apiPut(endpoint: string, data?: unknown, options: RequestInit = {}): Promise<Response> {
  return apiRequest(endpoint, {
    ...options,
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export async function apiDelete(endpoint: string, options: RequestInit = {}): Promise<Response> {
  return apiRequest(endpoint, { ...options, method: 'DELETE' })
}

export async function apiJson<T = unknown>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const response = await apiRequest(endpoint, options)

  if (!response.ok) {
    const text = await response.text()
    let message: string = text
    let code: string | null = null
    let details: unknown = null
    try {
      const json = JSON.parse(text)
      message = json.message || json.error || text
      code = json.code || null
      details = json.details || null
    } catch {
      // noop
    }

    if (shouldClearAuthSession(response.status, message, code)) {
      clearAuthSessions('all', true)
    }

    throw new ApiError(message || `HTTP ${response.status}`, {
      status: response.status,
      code,
      details,
    })
  }

  if (response.status === 204 || response.status === 205 || response.status === 304) {
    return undefined as T
  }

  const text = await response.text()
  if (!text.trim()) {
    return undefined as T
  }

  return JSON.parse(text) as T
}

export default {
  request: apiRequest,
  get: apiGet,
  post: apiPost,
  put: apiPut,
  delete: apiDelete,
  json: apiJson,
  getToken,
  API_BASE_URL,
}
