import { beforeEach, describe, expect, it, vi } from 'vitest'

describe('logger', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('does not report production errors in vitest mode', async () => {
    const debugSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    const { logger } = await import('./logger')
    logger.debug('hidden')
    logger.error('hidden error')

    expect(debugSpy).not.toHaveBeenCalled()
    expect(errorSpy).not.toHaveBeenCalled()
  })

  it('remains side-effect free for error logging in vitest mode', async () => {
    const fetchSpy = vi.mocked(fetch)
    const { logger } = await import('./logger')

    logger.error('boom', { huge: 'x'.repeat(1200) }, new Error('kaput'))

    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
