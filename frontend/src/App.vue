

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
  <!-- 应用根组件：全局顶部导航栏 + 路由视图 -->
  <div class="app">
    <nav class="global-nav">
      <!-- 会话过期警告横幅 -->
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

      <!-- 积分徽章 -->
      <div class="credit-badge" :title="t('app.creditTooltip')">
        <span class="credit-icon">◈</span>
        {{ creditText }}
      </div>

      <!-- 导航链接组 -->
      <div class="nav-links">
        <RouterLink data-testid="top-nav-user" class="nav-link" to="/">{{ t('nav.user') }}</RouterLink>
        <RouterLink v-if="isAdmin" data-testid="top-nav-admin" class="nav-link nav-link-admin" :to="{ name: 'admin' }">{{ t('nav.admin') }}</RouterLink>
        <RouterLink v-if="!isAuthenticated" data-testid="top-nav-login" class="nav-link" :to="{ name: 'login' }">{{ t('nav.login') }}</RouterLink>
        <RouterLink
          v-if="!isAuthenticated"
          data-testid="top-nav-admin-login"
          class="nav-link"
          :to="{ name: 'login', query: { as: 'admin' } }"
        >
          {{ t('nav.adminLogin') }}
        </RouterLink>
        <RouterLink v-if="!isAuthenticated" data-testid="top-nav-register" class="nav-link nav-link-accent" to="/register">{{ t('nav.register') }}</RouterLink>
        <button v-if="isAuthenticated" data-testid="top-nav-logout" type="button" class="nav-link nav-link-logout" @click="logout">{{ t('nav.logout') }}</button>
      </div>
    </nav>
    <RouterView />
  </div>
</template>

<style scoped>
/* ===== 应用根布局 ===== */
.app {
  height: 100%;
  width: 100%;
  box-sizing: border-box;
  padding-top: 48px;
}

/* ===== 全局顶部导航栏 ===== */
.global-nav {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 48px;
  z-index: 100;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 10px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.07);
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
}

/* 深色模式 */
:root[data-theme="dark"] .global-nav {
  background: rgba(17, 24, 39, 0.88);
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

/* ===== 积分徽章 ===== */
.credit-badge {
  display: flex; align-items: center; gap: 5px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(14, 165, 164, 0.08);
  border: 1px solid rgba(14, 165, 164, 0.2);
  color: var(--ui-accent, #0ea5a4);
  font-size: 12px; font-weight: 600;
  max-width: 200px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  flex-shrink: 0;
}
.credit-icon { font-size: 11px; flex-shrink: 0; }

/* ===== 导航链接组 ===== */
.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

/* ===== 导航链接通用 ===== */
.nav-link {
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  padding: 5px 12px;
  border-radius: 8px;
  border: 1.5px solid transparent;
  color: var(--ui-muted, #6b7280);
  background: transparent;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  font-family: inherit;
}
.nav-link:hover {
  background: rgba(14, 165, 164, 0.07);
  color: var(--ui-accent, #0ea5a4);
  border-color: rgba(14, 165, 164, 0.2);
}
.nav-link.router-link-active {
  font-weight: 700;
  color: var(--ui-accent, #0ea5a4);
  background: rgba(14, 165, 164, 0.08);
  border-color: rgba(14, 165, 164, 0.25);
}

/* 管理员链接：橙色强调 */
.nav-link-admin {
  color: #f97316;
}
.nav-link-admin:hover {
  background: rgba(249, 115, 22, 0.08);
  color: #f97316;
  border-color: rgba(249, 115, 22, 0.25);
}
.nav-link-admin.router-link-active {
  color: #f97316;
  background: rgba(249, 115, 22, 0.08);
  border-color: rgba(249, 115, 22, 0.3);
}

/* 注册链接：填充强调 */
.nav-link-accent {
  background: var(--ui-accent, #0ea5a4);
  color: #fff;
  border-color: var(--ui-accent, #0ea5a4);
}
.nav-link-accent:hover {
  filter: brightness(1.06);
  color: #fff;
  background: var(--ui-accent, #0ea5a4);
  border-color: var(--ui-accent, #0ea5a4);
}

/* 退出按钮 */
.nav-link-logout:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
  border-color: rgba(239, 68, 68, 0.25);
}

/* ===== 会话过期警告 ===== */
.session-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-radius: 8px;
  font-size: 12px;
  border: 1px solid;
  flex-shrink: 0;
  max-width: 480px;
}

.session-warning.level-low {
  background: rgba(251, 191, 36, 0.10);
  border-color: rgba(251, 191, 36, 0.35);
  color: #92400e;
}
.session-warning.level-medium {
  background: rgba(249, 115, 22, 0.10);
  border-color: rgba(249, 115, 22, 0.35);
  color: #9a3412;
}
.session-warning.level-high,
.session-warning.level-critical {
  background: rgba(239, 68, 68, 0.10);
  border-color: rgba(239, 68, 68, 0.35);
  color: #991b1b;
}
.session-warning.pulse-critical {
  animation: warning-pulse 2s ease-in-out infinite;
}

.session-warning-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.session-warning-action {
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid currentColor;
  background: transparent;
  color: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.15s;
  font-family: inherit;
}
.session-warning-action:hover { opacity: 0.8; }
.session-warning-action.ghost { opacity: 0.7; border-style: dashed; }
.session-warning-action.ghost:hover { opacity: 1; }
.session-warning-action.critical-primary {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}
.session-warning-action.critical-primary-pulse {
  animation: session-warning-login-glow 1.2s ease-in-out infinite;
}

.session-warning-icon {
  width: 14px; height: 14px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: currentColor;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
}

@keyframes warning-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.75; }
}

@keyframes session-warning-login-glow {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.12); }
  50% { box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.28); }
}

@media (prefers-reduced-motion: reduce) {
  .session-warning-action.critical-primary-pulse,
  .session-warning.pulse-critical {
    animation: none;
  }
}
</style>
