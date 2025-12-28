import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getSessionChangedEventName, pruneExpiredAuthTokens } from '../utils/authSession'

export interface UserProfile {
  username?: string
  role?: string
  creditScore?: number
  [key: string]: unknown
}

const ADMIN_ROLES = new Set(['ADMIN', 'REVIEWER', 'EDITOR', 'VIEWER'])

function readProfile(key: string): UserProfile | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useSessionStore = defineStore('session', () => {
  const userProfile = ref<UserProfile | null>(null)
  const adminProfile = ref<UserProfile | null>(null)
  let initialized = false

  function refresh(): void {
    pruneExpiredAuthTokens(false)
    userProfile.value = readProfile('accessiblenav_user_profile')
    adminProfile.value = readProfile('accessiblenav_admin_profile')
  }

  function init(): void {
    if (initialized) return
    initialized = true
    refresh()
    if (typeof window !== 'undefined') {
      window.addEventListener(getSessionChangedEventName(), refresh)
      window.addEventListener('storage', refresh)
    }
  }

  const profile = computed(() => adminProfile.value || userProfile.value || null)
  const role = computed<string | null>(() => {
    const raw = profile.value?.role
    return raw ? String(raw).toUpperCase() : null
  })
  const isAuthenticated = computed(() => Boolean(role.value))
  const isAdmin = computed(() => role.value != null && ADMIN_ROLES.has(role.value))
  const isUser = computed(() => isAuthenticated.value && !isAdmin.value)
  const username = computed(() => profile.value?.username || '')
  const creditScore = computed(() => {
    const value = profile.value?.creditScore
    return Number.isFinite(Number(value)) ? Number(value) : 0
  })

  return {
    userProfile,
    adminProfile,
    profile,
    role,
    isAuthenticated,
    isAdmin,
    isUser,
    username,
    creditScore,
    refresh,
    init,
  }
})
