import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiJson } from '../services/apiService'
import type { AccessibilityProfile } from '../types/api'

const ALLOWED_MOBILITY_MODES = new Set(['WALK', 'WHEELCHAIR', 'VISUAL_IMPAIRMENT', 'STROLLER'])

function createDefaultProfile(): AccessibilityProfile {
  return {
    mobilityMode: 'WALK',
    avoidStairs: false,
    avoidSlope: false,
    avoidConstruction: true,
    maxSlopePercent: 12,
  }
}

function normalizeProfile(raw: unknown): AccessibilityProfile {
  const defaults = createDefaultProfile()
  if (!raw || typeof raw !== 'object') {
    return defaults
  }

  const r = raw as Record<string, unknown>
  const modeRaw = String(r.mobilityMode || '').trim().toUpperCase()
  const mobilityMode = ALLOWED_MOBILITY_MODES.has(modeRaw) ? modeRaw : defaults.mobilityMode
  const maxSlope = Number(r.maxSlopePercent)

  return {
    mobilityMode,
    avoidStairs: Boolean(r.avoidStairs),
    avoidSlope: Boolean(r.avoidSlope),
    avoidConstruction: Boolean(r.avoidConstruction),
    maxSlopePercent: Number.isFinite(maxSlope) ? Math.max(0, Math.min(45, maxSlope)) : defaults.maxSlopePercent,
  }
}

function unwrapResponsePayload(resp: unknown): unknown {
  if (!resp || typeof resp !== 'object') return null
  const r = resp as Record<string, unknown>
  if ('data' in r && r.data && typeof r.data === 'object') {
    return r.data
  }
  return resp
}

export const useAccessibilityProfileStore = defineStore('accessibilityProfile', () => {
  const profile = ref<AccessibilityProfile>(createDefaultProfile())
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string | null>(null)

  function resetProfile(): void {
    profile.value = createDefaultProfile()
    error.value = null
    loading.value = false
    saving.value = false
  }

  async function fetchProfile(): Promise<AccessibilityProfile> {
    loading.value = true
    error.value = null
    try {
      const resp = await apiJson('/api/profile/accessibility', { method: 'GET' })
      profile.value = normalizeProfile(unwrapResponsePayload(resp))
      return profile.value
    } catch (e: unknown) {
      error.value = (e as Error)?.message || String(e)
      throw e
    } finally {
      loading.value = false
    }
  }

  async function saveProfile(nextProfile: unknown): Promise<AccessibilityProfile> {
    saving.value = true
    error.value = null
    try {
      const resp = await apiJson('/api/profile/accessibility', {
        method: 'PUT',
        body: JSON.stringify(nextProfile || {}),
      })
      profile.value = normalizeProfile(unwrapResponsePayload(resp))
      return profile.value
    } catch (e: unknown) {
      error.value = (e as Error)?.message || String(e)
      throw e
    } finally {
      saving.value = false
    }
  }

  return {
    profile,
    loading,
    saving,
    error,
    resetProfile,
    fetchProfile,
    saveProfile,
  }
})
