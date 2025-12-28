import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/utils/fetchUtils', () => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
}))

import { getJson, postJson } from '@/utils/fetchUtils'
import { useObstacleStore } from '@/stores/useObstacleStore'

describe('useObstacleStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetches my reports and normalizes status', async () => {
    const store = useObstacleStore()
    vi.mocked(getJson).mockResolvedValueOnce([{ id: 1 }, { id: 2 }])

    await store.fetchMyReports(' pending ')

    expect(getJson).toHaveBeenCalledWith('/api/obstacles/reports/me?status=PENDING')
    expect(store.myReportsState.items).toHaveLength(2)
    expect(store.myReportsState.status).toBe('PENDING')
    expect(store.myReportsState.loading).toBe(false)
  })

  it('captures fetch errors and clears items', async () => {
    const store = useObstacleStore()
    vi.mocked(getJson).mockRejectedValueOnce(new Error('load failed'))

    await store.fetchAdminReports('approved')

    expect(store.adminReportsState.error).toBe('load failed')
    expect(store.adminReportsState.items).toEqual([])
    expect(store.adminReportsState.status).toBe('APPROVED')
    expect(store.adminReportsState.loading).toBe(false)
  })

  it('uses default pending status for admin fetch and proxies review actions', async () => {
    const store = useObstacleStore()
    vi.mocked(getJson).mockResolvedValueOnce([])

    await store.fetchAdminReports()
    await store.approveReport(10, { reviewNote: 'ok' })
    await store.rejectReport(11)
    await store.revokeReport(12, { reason: 'expired' })

    expect(getJson).toHaveBeenCalledWith('/api/admin/obstacles/reports?status=PENDING')
    expect(postJson).toHaveBeenCalledWith('/api/admin/obstacles/reports/10/approve', { reviewNote: 'ok' })
    expect(postJson).toHaveBeenCalledWith('/api/admin/obstacles/reports/11/reject', {})
    expect(postJson).toHaveBeenCalledWith('/api/admin/obstacles/reports/12/revoke', { reason: 'expired' })
  })
})
