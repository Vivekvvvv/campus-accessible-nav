import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { apiJson } from './apiService'

describe('apiJson', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('returns parsed JSON for normal success responses', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true, value: 1 }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
        },
      })
    )

    const data = await apiJson<{ ok: boolean; value: number }>('/api/test')

    expect(data).toEqual({ ok: true, value: 1 })
  })

  it('returns undefined for 204 responses', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(null, {
        status: 204,
      })
    )

    const data = await apiJson('/api/test')

    expect(data).toBeUndefined()
  })

  it('returns undefined for empty successful response bodies', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response('', {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
        },
      })
    )

    const data = await apiJson('/api/test')

    expect(data).toBeUndefined()
  })
})
