import { beforeEach, describe, expect, it, vi } from 'vitest'

const clearAuthSessions = vi.fn()
const getActiveAuthToken = vi.fn()
const loggerError = vi.fn()
const loggerDebug = vi.fn()

vi.mock('@/utils/authSession', () => ({
  clearAuthSessions,
  getActiveAuthToken,
}))

vi.mock('@/utils/logger', () => ({
  logger: {
    error: loggerError,
    debug: loggerDebug,
    warn: vi.fn(),
    info: vi.fn(),
    log: vi.fn(),
  },
}))

describe('fetchUtils', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    localStorage.clear()
    getActiveAuthToken.mockReturnValue({ token: null, source: null })
  })

  it('creates and reuses a trace id', async () => {
    const { getCurrentTraceId } = await import('./fetchUtils')

    const first = getCurrentTraceId()
    const second = getCurrentTraceId()

    expect(first).toHaveLength(16)
    expect(second).toBe(first)
  })

  it('sends auth and trace headers on requests', async () => {
    getActiveAuthToken.mockReturnValue({ token: 'token-1', source: 'user' })
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: {
          'x-trace-id': 'trace-123',
        },
      })
    )

    const { postJson } = await import('./fetchUtils')
    await postJson('/api/test', { a: 1 })

    const [, init] = vi.mocked(fetch).mock.calls[0]
    const headers = init?.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-1')
    expect(headers['x-trace-id']).toBeDefined()
    expect(localStorage.getItem('accessiblenav_trace_id')).toBe('trace-123')
  })

  it('refreshes token from response headers', async () => {
    getActiveAuthToken.mockReturnValue({ token: 'old-token', source: 'admin' })
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: {
          'x-new-access-token': 'new-token',
          'x-token-expires-in': '60',
        },
      })
    )

    const { getJson } = await import('./fetchUtils')
    await getJson('/api/test')

    expect(localStorage.getItem('accessiblenav_admin_token')).toBe('new-token')
    expect(loggerDebug).toHaveBeenCalled()
  })

  it('clears auth sessions on authentication failures and throws HttpError', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ message: 'token expired', code: 'AUTH_001' }), {
        status: 401,
        statusText: 'Unauthorized',
      })
    )

    const { getJson } = await import('./fetchUtils')

    await expect(getJson('/api/test')).rejects.toMatchObject({
      message: 'token expired',
      status: 401,
      code: 'AUTH_001',
    })
    expect(clearAuthSessions).toHaveBeenCalledWith('all', true)
    expect(loggerError).toHaveBeenCalled()
  })

  it('reads plain-text error messages and response message fields', async () => {
    const { readResponseText } = await import('./fetchUtils')

    const plainText = await readResponseText(new Response('plain message', { status: 500 }))
    const jsonText = await readResponseText(
      new Response(JSON.stringify({ message: 'json message' }), { status: 500 })
    )

    expect(plainText).toBe('plain message')
    expect(jsonText).toBe('json message')
  })
})
