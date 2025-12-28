

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSessionStore } from './stores/useSessionStore'
import { clearAuthSessions, getActiveAuthToken, getSessionChangedEventName, getTokenRemainingMs } from './utils/authSession'
import { authConfig } from './config/authConfig'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const session = useSessionStore()
session.init()

const { isAuthenticated, isAdmin, username, creditScore } = storeToRefs(session)

function creditLabel(score) {
  const s = Number(score || 0)
  if (s <= 0) return t('app.credit.lowest')
  if (s < 20) return t('app.credit.low')
  if (s < 50) return t('app.credit.medium')
  if (s < 80) return t('app.credit.high')
  return t('app.credit.veryHigh')
}

const creditText = computed(() => {
  if (!isAuthenticated.value) {
    return t('app.credit.visitor', { label: creditLabel(0) })
  }
  const score = creditScore.value ?? 0
  return t('app.credit.user', { username: username.value, label: creditLabel(score), score })
})

const resolvedAuthGuardIntervalMs = authConfig.guardIntervalMs
const resolvedExpiryWarningMinutes = authConfig.expiryWarningMinutes
const expiryWarningWindowMs = resolvedExpiryWarningMinutes * 60 * 1000
const resolvedExpiryWarningSnoozeMinutes = authConfig.expiryWarningSnoozeMinutes
const isCriticalPulseEnabled = authConfig.criticalPulseEnabled

const nowTickMs = ref(Date.now())
const tokenExpiryAtMs = ref<number | null>(null)
const currentToken = ref<string | null>(null)
const snoozedUntilMs = ref(0)

const tokenRemainingMs = computed(() => {
  if (tokenExpiryAtMs.value == null) {
    return null
  }
  return tokenExpiryAtMs.value - nowTickMs.value
})
const isWarningSnoozed = computed(() => snoozedUntilMs.value > nowTickMs.value)
const showSessionExpiryWarning = computed(
  () =>
    Boolean(
      isAuthenticated.value &&
      tokenRemainingMs.value != null &&
      tokenRemainingMs.value > 0 &&
      tokenRemainingMs.value <= expiryWarningWindowMs &&
      !isWarningSnoozed.value
    )
)
const sessionExpiryWarningText = computed(() => {
  if (!showSessionExpiryWarning.value) {
    return ''
  }
  const remainingMs = Math.max(0, tokenRemainingMs.value ?? 0)
  const minutes = Math.floor(remainingMs / 60000)
  const seconds = Math.max(0, Math.floor((remainingMs % 60000) / 1000))
  const timeLabel =
    minutes > 0
      ? t('app.sessionRemainingMinutesSeconds', { minutes, seconds })
      : t('app.sessionRemainingSeconds', { seconds })
  return t('app.sessionExpiringCountdown', { timeLabel })
})
const sessionWarningLevel = computed<'soft' | 'warn' | 'critical'>(() => {
  const remainingMs = tokenRemainingMs.value ?? Number.POSITIVE_INFINITY
  if (remainingMs <= 60 * 1000) {
    return 'critical'
  }
  if (remainingMs <= 5 * 60 * 1000) {
    return 'warn'
  }
  return 'soft'
})
const isCriticalSessionWarning = computed(() => sessionWarningLevel.value === 'critical')
const shouldPulseCriticalWarning = computed(() => isCriticalPulseEnabled && sessionWarningLevel.value === 'critical')

const sessionChangedEventName = getSessionChangedEventName()
let authGuardTimer: number | null = null
let countdownTimer: number | null = null

function ensureAuthForProtectedRoute(): void {
  const { token } = getActiveAuthToken()
  if (token !== currentToken.value) {
    currentToken.value = token
    snoozedUntilMs.value = 0
  }

  const remainingMs = token ? getTokenRemainingMs(token) : null
  tokenExpiryAtMs.value = remainingMs == null ? null : Date.now() + remainingMs

  const requiresAuth = route.matched.some((record) => Boolean(record.meta?.requiresAuth))
  if (!requiresAuth) {
    return
  }

  if (!token) {
    router.replace({ name: 'login', query: { as: 'admin', redirect: route.fullPath } })
  }
}

function goToLoginFromWarning(): void {
  const query = {
    ...(isAdmin.value ? { as: 'admin' } : {}),
    redirect: route.fullPath,
  }
  router.push({ name: 'login', query })
}

function snoozeSessionExpiryWarning(): void {
  snoozedUntilMs.value = Date.now() + resolvedExpiryWarningSnoozeMinutes * 60 * 1000
}

function handleWindowFocus(): void {
  ensureAuthForProtectedRoute()
}

function handleVisibilityChange(): void {
  if (document.visibilityState === 'visible') {
    ensureAuthForProtectedRoute()
  }
}

onMounted(() => {
  ensureAuthForProtectedRoute()
  authGuardTimer = window.setInterval(ensureAuthForProtectedRoute, resolvedAuthGuardIntervalMs)
  countdownTimer = window.setInterval(() => {
    nowTickMs.value = Date.now()
  }, 1000)
  window.addEventListener('focus', handleWindowFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener(sessionChangedEventName, ensureAuthForProtectedRoute)
})

watch(
  () => route.fullPath,
  () => {
    ensureAuthForProtectedRoute()
  }
)

watch(
  () => isAuthenticated.value,
  () => {
    ensureAuthForProtectedRoute()
  }
)

onBeforeUnmount(() => {
  if (authGuardTimer != null) {
    window.clearInterval(authGuardTimer)
    authGuardTimer = null
  }
  if (countdownTimer != null) {
    window.clearInterval(countdownTimer)
    countdownTimer = null
  }
  window.removeEventListener('focus', handleWindowFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener(sessionChangedEventName, ensureAuthForProtectedRoute)
})

function logout() {
  clearAuthSessions('all', true)
  router.replace({ name: 'user' })
}
</script>

<template>
  <div class="app">
    <nav class="global-nav">
      <div
        v-if="showSessionExpiryWarning"
        class="session-warning"
        :class="[`level-${sessionWarningLevel}`, { 'pulse-critical': shouldPulseCriticalWarning }]"
        role="status"
      >
        <span class="session-warning-text">{{ sessionExpiryWarningText }}</span>
        <button
          type="button"
          class="session-warning-action"
          :class="{
            'critical-primary': isCriticalSessionWarning,
            'critical-primary-pulse': shouldPulseCriticalWarning,
          }"
          @click="goToLoginFromWarning"
        >
          <span v-if="isCriticalSessionWarning" class="session-warning-icon" aria-hidden="true">!</span>
          {{ t('app.sessionExpiringLogin') }}
        </button>
        <button type="button" class="session-warning-action ghost" @click="snoozeSessionExpiryWarning">{{ t('app.sessionExpiringSnooze', { minutes: resolvedExpiryWarningSnoozeMinutes }) }}</button>
      </div>
      <div class="badge" :title="t('app.creditTooltip')">{{ creditText }}</div>
      <RouterLink data-testid="top-nav-user" class="nav-link" to="/">{{ t('nav.user') }}</RouterLink>
      <RouterLink v-if="isAdmin" data-testid="top-nav-admin" class="nav-link" :to="{ name: 'admin' }">{{ t('nav.admin') }}</RouterLink>
      <RouterLink v-if="!isAuthenticated" data-testid="top-nav-login" class="nav-link" :to="{ name: 'login' }">{{ t('nav.login') }}</RouterLink>
      <RouterLink
        v-if="!isAuthenticated"
        data-testid="top-nav-admin-login"
        class="nav-link"
        :to="{ name: 'login', query: { as: 'admin' } }"
      >
        {{ t('nav.adminLogin') }}
      </RouterLink>
      <RouterLink v-if="!isAuthenticated" data-testid="top-nav-register" class="nav-link" to="/register">{{ t('nav.register') }}</RouterLink>
      <button v-if="isAuthenticated" data-testid="top-nav-logout" type="button" class="nav-link btn-link" @click="logout">{{ t('nav.logout') }}</button>
    </nav>
    <RouterView />
  </div>
</template>

<style scoped>
.app {
  height: 100%;
  width: 100%;
  box-sizing: border-box;
  padding-top: 48px;
}

.global-nav {
  position: fixed;
  top: 8px;
  right: 12px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-link {
  font-size: 12px;
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.18);
  background: rgba(255, 255, 255, 0.9);
  color: #111;
}

.badge {
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.75);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-warning {
  --warning-border: rgba(180, 83, 9, 0.35);
  --warning-bg: rgba(255, 237, 213, 0.92);
  --warning-ink: #7c2d12;
  --warning-button-bg: rgba(255, 255, 255, 0.95);
  --warning-button-ghost-bg: rgba(255, 237, 213, 0.6);
  --warning-button-border: rgba(180, 83, 9, 0.45);

  font-size: 12px;
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px solid var(--warning-border);
  background: var(--warning-bg);
  color: var(--warning-ink);
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.session-warning.level-warn {
  --warning-border: rgba(194, 65, 12, 0.38);
  --warning-bg: rgba(255, 237, 213, 0.98);
  --warning-ink: #9a3412;
  --warning-button-border: rgba(194, 65, 12, 0.48);
}

.session-warning.level-critical {
  --warning-border: rgba(185, 28, 28, 0.42);
  --warning-bg: rgba(254, 226, 226, 0.96);
  --warning-ink: #991b1b;
  --warning-button-bg: rgba(255, 255, 255, 0.98);
  --warning-button-ghost-bg: rgba(254, 226, 226, 0.68);
  --warning-button-border: rgba(185, 28, 28, 0.5);
}

.session-warning.pulse-critical {
  animation: session-warning-pulse 1.6s ease-in-out infinite;
}

@keyframes session-warning-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
    transform: translateZ(0);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.2);
    transform: translateY(-1px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .session-warning.pulse-critical {
    animation: none;
  }
}

.session-warning-text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-warning-action {
  border: 1px solid var(--warning-button-border);
  background: var(--warning-button-bg);
  color: var(--warning-ink);
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  line-height: 1.1;
  padding: 2px 6px;
  cursor: pointer;
  white-space: nowrap;
}

.session-warning-icon {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: currentColor;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
}

.session-warning-action.ghost {
  background: var(--warning-button-ghost-bg);
}

.session-warning-action.critical-primary {
  border-color: rgba(185, 28, 28, 0.7);
  box-shadow: 0 0 0 1px rgba(185, 28, 28, 0.14);
}

.session-warning-action.critical-primary-pulse {
  animation: session-warning-login-glow 1.2s ease-in-out infinite;
}

@keyframes session-warning-login-glow {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.12);
  }
  50% {
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.28);
  }
}

@media (prefers-reduced-motion: reduce) {
  .session-warning-action.critical-primary-pulse {
    animation: none;
  }
}

.btn-link {
  cursor: pointer;
}

.nav-link.router-link-active {
  font-weight: 700;
}
</style>
