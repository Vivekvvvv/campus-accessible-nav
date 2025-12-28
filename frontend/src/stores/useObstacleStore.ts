import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getJson, postJson } from '../utils/fetchUtils'

interface ReportItem {
  [key: string]: unknown
}

interface ReportsState {
  items: ReportItem[]
  loading: boolean
  error: string
  status: string
}

function normalizeStatus(v: unknown): string {
  const s = String(v ?? '').trim().toUpperCase()
  return s || ''
}

export const useObstacleStore = defineStore('obstacle', () => {
  const myReportsState = ref<ReportsState>({
    items: [],
    loading: false,
    error: '',
    status: '',
  })

  const adminReportsState = ref<ReportsState>({
    items: [],
    loading: false,
    error: '',
    status: 'PENDING',
  })

  async function fetchMyReports(status?: string): Promise<void> {
    const st = normalizeStatus(status)
    myReportsState.value.loading = true
    myReportsState.value.error = ''
    myReportsState.value.status = st
    try {
      const qs = st ? `?status=${encodeURIComponent(st)}` : ''
      const data = await getJson<ReportItem[]>(`/api/obstacles/reports/me${qs}`)
      myReportsState.value.items = Array.isArray(data) ? data : []
    } catch (e: unknown) {
      myReportsState.value.error = (e as Error)?.message || String(e)
      myReportsState.value.items = []
    } finally {
      myReportsState.value.loading = false
    }
  }

  async function fetchAdminReports(status?: string): Promise<void> {
    const st = normalizeStatus(status) || 'PENDING'
    adminReportsState.value.loading = true
    adminReportsState.value.error = ''
    adminReportsState.value.status = st
    try {
      const data = await getJson<ReportItem[]>(`/api/admin/obstacles/reports?status=${encodeURIComponent(st)}`)
      adminReportsState.value.items = Array.isArray(data) ? data : []
    } catch (e: unknown) {
      adminReportsState.value.error = (e as Error)?.message || String(e)
      adminReportsState.value.items = []
    } finally {
      adminReportsState.value.loading = false
    }
  }

  async function approveReport(id: number | string, payload?: unknown): Promise<void> {
    await postJson(`/api/admin/obstacles/reports/${id}/approve`, payload ?? {})
  }

  async function rejectReport(id: number | string, payload?: unknown): Promise<void> {
    await postJson(`/api/admin/obstacles/reports/${id}/reject`, payload ?? {})
  }

  async function revokeReport(id: number | string, payload?: unknown): Promise<void> {
    await postJson(`/api/admin/obstacles/reports/${id}/revoke`, payload ?? {})
  }

  return {
    myReportsState,
    adminReportsState,
    fetchMyReports,
    fetchAdminReports,
    approveReport,
    rejectReport,
    revokeReport,
  }
})
