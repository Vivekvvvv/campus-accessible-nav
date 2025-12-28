import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/services/apiService', () => ({
  apiJson: vi.fn(),
}))

import { apiJson } from '@/services/apiService'
import { useAccessibilityProfileStore } from '@/stores/useAccessibilityProfileStore'

describe('useAccessibilityProfileStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('resets to default profile', () => {
    const store = useAccessibilityProfileStore()
    store.profile = {
      mobilityMode: 'WHEELCHAIR',
      avoidStairs: true,
      avoidSlope: true,
      avoidConstruction: false,
      maxSlopePercent: 5,
    }
    store.loading = true
    store.saving = true
    store.error = 'x'

    store.resetProfile()

    expect(store.profile).toEqual({
      mobilityMode: 'WALK',
      avoidStairs: false,
      avoidSlope: false,
      avoidConstruction: true,
      maxSlopePercent: 12,
    })
    expect(store.loading).toBe(false)
    expect(store.saving).toBe(false)
    expect(store.error).toBeNull()
  })

  it('fetches and normalizes profile payload', async () => {
    const store = useAccessibilityProfileStore()
    vi.mocked(apiJson).mockResolvedValueOnce({
      data: {
        mobilityMode: 'wheelchair',
        avoidStairs: 1,
        avoidSlope: '',
        avoidConstruction: 0,
        maxSlopePercent: 99,
      },
    })

    const profile = await store.fetchProfile()

    expect(profile).toEqual({
      mobilityMode: 'WHEELCHAIR',
      avoidStairs: true,
      avoidSlope: false,
      avoidConstruction: false,
      maxSlopePercent: 45,
    })
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('saves profile and keeps errors on failure', async () => {
    const store = useAccessibilityProfileStore()
    vi.mocked(apiJson).mockRejectedValueOnce(new Error('save failed'))

    await expect(store.saveProfile({ mobilityMode: 'WALK' })).rejects.toThrow('save failed')
    expect(store.saving).toBe(false)
    expect(store.error).toBe('save failed')
  })
})
