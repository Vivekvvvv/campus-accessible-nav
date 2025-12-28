<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { readResponseText } from '../utils/fetchUtils'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref(null)

const API_BASE_URL = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

const isAdminLogin = computed(() => String(route.query.as || '').toLowerCase() === 'admin')
const ADMIN_ROLES = new Set(['ADMIN', 'REVIEWER', 'EDITOR', 'VIEWER'])

function sessionNotify() {
  window.dispatchEvent(new Event('accessiblenav-session-changed'))
}

function saveUserSession(data) {
  localStorage.setItem('accessiblenav_user_token', data.token)
  localStorage.setItem(
    'accessiblenav_user_profile',
    JSON.stringify({ username: data.username, role: data.role, creditScore: data.creditScore ?? 0 })
  )
  sessionNotify()
}

function saveAdminSession(data) {
  localStorage.setItem('accessiblenav_admin_token', data.token)
  localStorage.setItem(
    'accessiblenav_admin_profile',
    JSON.stringify({ username: data.username, role: data.role, creditScore: data.creditScore ?? 0 })
  )
  sessionNotify()
}

async function login() {
  error.value = null
  if (!username.value || !password.value) {
    error.value = t('auth.inputRequired')
    return
  }

  loading.value = true
  try {
    const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })

    if (!res.ok) {
      const text = await readResponseText(res)
      throw new Error(text || `HTTP ${res.status}`)
    }

    const data = await res.json()
    const role = String(data.role || '').toUpperCase()
    const hasAdminRole = ADMIN_ROLES.has(role)

    if (isAdminLogin.value) {
      if (!hasAdminRole) {
        throw new Error(t('auth.adminOnly'))
      }
      saveAdminSession(data)
      await router.replace({ name: 'admin' })
    } else {
      if (hasAdminRole) {
        saveAdminSession(data)
        await router.replace({ name: 'admin' })
      } else {
        saveUserSession(data)
        const redirect = route.query.redirect ? String(route.query.redirect) : '/'
        await router.replace(redirect)
      }
    }
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const prefill = route.query.u ? String(route.query.u) : ''
  if (prefill) username.value = prefill
})
</script>

<template>
  <div class="auth-page" :class="{ admin: isAdminLogin }">
    <div class="auth-shell">
      <section class="brand">
        <div class="brand-chip">{{ t('brand.name') }}</div>
        <h1>{{ t('brand.slogan') }}</h1>
        <p class="brand-sub">{{ t('brand.campus') }}</p>
        <div class="brand-grid">
          <div class="brand-card">
            <div class="brand-title">{{ t('brand.featureRoute') }}</div>
            <div class="brand-desc">{{ t('brand.featureRouteDesc') }}</div>
          </div>
          <div class="brand-card">
            <div class="brand-title">{{ t('brand.featureFavorites') }}</div>
            <div class="brand-desc">{{ t('brand.featureFavoritesDesc') }}</div>
          </div>
          <div class="brand-card">
            <div class="brand-title">{{ t('brand.featureReport') }}</div>
            <div class="brand-desc">{{ t('brand.featureReportDesc') }}</div>
          </div>
          <div class="brand-card">
            <div class="brand-title">{{ t('brand.featureMap') }}</div>
            <div class="brand-desc">{{ t('brand.featureMapDesc') }}</div>
          </div>
        </div>
        <div class="brand-foot">{{ t('brand.footer') }}</div>
      </section>

      <section class="panel">
        <div class="panel-tag">{{ isAdminLogin ? t('auth.adminLogin') : t('auth.userLogin') }}</div>
        <div class="panel-title">{{ isAdminLogin ? t('auth.adminLogin') : t('auth.loginTitle') }}</div>
        <div class="panel-sub">{{ isAdminLogin ? t('auth.adminLoginHint') : t('auth.userLoginHint') }}</div>

        <form class="form" @submit.prevent="login">
          <div class="field">
            <label class="label">{{ t('auth.username') }}</label>
            <input
              data-testid="login-username"
              class="input"
              type="text"
              v-model="username"
              autocomplete="username"
              :placeholder="t('auth.usernamePlaceholder')"
              :disabled="loading"
            />
          </div>
          <div class="field">
            <label class="label">{{ t('auth.password') }}</label>
            <input
              data-testid="login-password"
              class="input"
              type="password"
              v-model="password"
              autocomplete="current-password"
              :placeholder="t('auth.passwordPlaceholder')"
              :disabled="loading"
            />
          </div>

          <div class="actions">
            <button data-testid="login-submit" type="submit" class="btn primary" :disabled="loading">
              {{ loading ? t('auth.loginLoading') : (isAdminLogin ? t('auth.enterAdmin') : t('auth.loginButton')) }}
            </button>
            <button
              v-if="!isAdminLogin"
              type="button"
              class="btn ghost"
              :disabled="loading"
              @click="$router.push('/register')"
            >
              {{ t('auth.createAccount') }}
            </button>
          </div>

          <div class="assist">
            <button
              v-if="!isAdminLogin"
              type="button"
              class="link"
              :disabled="loading"
              @click="router.push({ name: 'login', query: { as: 'admin' } })"
            >
              {{ t('auth.adminLogin') }}
            </button>
            <button v-else type="button" class="link" :disabled="loading" @click="router.push('/login')">
              {{ t('auth.userLogin') }}
            </button>
          </div>
        </form>

        <div v-if="error" class="info-card status err">{{ error }}</div>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@300;400;500;600;700;800&family=Noto+Sans+SC:wght@300;400;500;600;700&display=swap');

.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  font-family: 'Manrope', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--ink);
  background:
    radial-gradient(900px 500px at 8% 10%, rgba(14, 165, 164, 0.18), transparent 60%),
    radial-gradient(900px 480px at 92% 20%, rgba(249, 115, 22, 0.18), transparent 62%),
    linear-gradient(135deg, #fff7ed 0%, #f0f9ff 55%, #ecfeff 100%);
  position: relative;
  overflow: hidden;
  --ink: #0f172a;
  --muted: #475569;
  --card: rgba(255, 255, 255, 0.88);
  --line: rgba(15, 23, 42, 0.12);
  --accent: #0ea5a4;
  --accent-2: #f97316;
  --shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
}

.auth-shell {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 18px;
}

@media (max-width: 920px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }
}

.brand {
  padding: 28px 26px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--card);
  box-shadow: var(--shadow);
}

.brand-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(14, 165, 164, 0.35);
  background: rgba(14, 165, 164, 0.08);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.brand h1 {
  margin: 14px 0 8px;
  font-size: 34px;
  line-height: 1.1;
}

.brand-sub {
  margin: 0 0 18px;
  color: var(--muted);
}

.brand-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 560px) {
  .brand-grid {
    grid-template-columns: 1fr;
  }
}

.brand-card {
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 14px;
  padding: 12px 12px;
  background: rgba(255, 255, 255, 0.75);
}

.brand-title {
  font-weight: 800;
  margin-bottom: 4px;
}

.brand-desc {
  color: var(--muted);
  font-size: 13px;
}

.brand-foot {
  margin-top: 16px;
  color: var(--muted);
  font-size: 13px;
}

.panel {
  padding: 22px 20px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
  align-self: start;
}

.panel-tag {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.12);
  border: 1px solid rgba(249, 115, 22, 0.25);
  font-size: 12px;
  font-weight: 700;
}

.panel-title {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 800;
}

.panel-sub {
  margin-top: 8px;
  color: var(--muted);
  font-size: 13px;
}

.form {
  margin-top: 16px;
  display: grid;
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
}

.label {
  font-size: 12px;
  font-weight: 700;
}

.input {
  padding: 10px 12px;
  border: 1px solid rgba(15, 23, 42, 0.16);
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  background: white;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.btn {
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  background: white;
  cursor: pointer;
  font-weight: 700;
}

.btn.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: white;
}

.btn.ghost {
  background: rgba(15, 23, 42, 0.04);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.assist {
  margin-top: 12px;
}

.link {
  background: none;
  border: none;
  padding: 0;
  color: var(--accent);
  font-weight: 700;
  cursor: pointer;
}

.info-card.status {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  font-size: 13px;
}

.info-card.status.err {
  border-color: rgba(220, 38, 38, 0.35);
  background: rgba(220, 38, 38, 0.08);
  color: rgb(153, 27, 27);
}
</style>
