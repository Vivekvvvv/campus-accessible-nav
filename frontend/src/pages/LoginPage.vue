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
  <!-- 登录页：全屏两栏布局 -->
  <div class="auth-page" :class="{ admin: isAdminLogin }">
    <!-- 左侧品牌展示区 -->
    <div class="brand-side">
      <div class="brand-glow"></div>
      <div class="brand-content">
        <div class="brand-logo">
          <span class="brand-icon">♿</span>
        </div>
        <h1 class="brand-name">{{ t('brand.name') }}</h1>
        <p class="brand-slogan">{{ t('brand.slogan') }}</p>
        <div class="brand-features">
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>{{ t('brand.featureRoute') }}</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>{{ t('brand.featureFavorites') }}</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>{{ t('obstacle.report') }}</span>
          </div>
        </div>
        <p class="brand-foot">{{ t('brand.loginFoot') }}</p>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-side">
      <div class="form-card">
        <!-- 身份标签 -->
        <div class="role-badge" :class="isAdminLogin ? 'admin' : 'user'">
          {{ isAdminLogin ? t('nav.adminLogin') : t('nav.login') }}
        </div>

        <h2 class="form-title">
          {{ isAdminLogin ? t('auth.adminWelcome') : t('auth.welcome') }}
        </h2>
        <p class="form-sub">
          {{ isAdminLogin ? t('auth.adminLoginHint') : t('auth.userLoginHint') }}
        </p>

        <!-- 登录表单 -->
        <form class="form" @submit.prevent="login">
          <div class="field">
            <label class="field-label">{{ t('auth.username') }}</label>
            <input
              data-testid="login-username"
              class="field-input"
              type="text"
              v-model="username"
              autocomplete="username"
              :placeholder="t('auth.usernamePlaceholder')"
              :disabled="loading"
              @keyup.enter="login"
            />
          </div>

          <div class="field">
            <label class="field-label">{{ t('auth.password') }}</label>
            <input
              data-testid="login-password"
              class="field-input"
              type="password"
              v-model="password"
              autocomplete="current-password"
              :placeholder="t('auth.passwordPlaceholder')"
              :disabled="loading"
              @keyup.enter="login"
            />
          </div>

          <!-- 错误提示 -->
          <div v-if="error" class="error-bar">
            <span class="error-icon">!</span>
            {{ error }}
          </div>

          <!-- 操作按钮 -->
          <div class="form-actions">
            <button
              data-testid="login-submit"
              class="btn-primary"
              type="submit"
              :disabled="loading"
            >
              <span v-if="loading" class="btn-spinner"></span>
              {{ loading ? t('common.loading') : t('nav.login') }}
            </button>
            <RouterLink class="btn-ghost" to="/register">
              {{ t('nav.register') }}
            </RouterLink>
          </div>
        </form>

        <!-- 辅助链接 -->
        <div class="form-assist">
          <span class="assist-text">{{ t('auth.noAccount') }}</span>
          <RouterLink class="assist-link" to="/register">{{ t('nav.register') }}</RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 页面整体布局 ===== */
.auth-page {
  min-height: 100vh;
  display: flex;
  background: var(--ui-bg);
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
}

/* ===== 左侧品牌区 ===== */
.brand-side {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0c4a4a 0%, #0ea5a4 60%, #14b8a6 100%);
  overflow: hidden;
  padding: 48px;
}

/* 管理员模式：橙色品牌区 */
.auth-page.admin .brand-side {
  background: linear-gradient(135deg, #7c2d12 0%, #f97316 60%, #fb923c 100%);
}

/* 背景光晕装饰 */
.brand-glow {
  position: absolute;
  width: 500px;
  height: 500px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  top: -100px;
  right: -100px;
  pointer-events: none;
}

.brand-glow::after {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  bottom: -80px;
  left: -60px;
}

.brand-content {
  position: relative;
  z-index: 1;
  color: #fff;
  max-width: 380px;
}

.brand-logo {
  width: 56px;
  height: 56px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.brand-icon {
  font-size: 28px;
}

.brand-name {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  opacity: 0.7;
  margin-bottom: 12px;
}

.brand-slogan {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.3;
  margin-bottom: 32px;
  letter-spacing: -0.02em;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 36px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  opacity: 0.85;
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.7);
  flex-shrink: 0;
}

.brand-foot {
  font-size: 12px;
  opacity: 0.5;
  line-height: 1.6;
}

/* ===== 右侧表单区 ===== */
.form-side {
  flex: 0 0 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: var(--ui-bg);
}

.form-card {
  width: 100%;
  max-width: 340px;
}

/* 身份标签 */
.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin-bottom: 20px;
}

.role-badge.user {
  background: rgba(14, 165, 164, 0.1);
  color: #0ea5a4;
  border: 1px solid rgba(14, 165, 164, 0.2);
}

.role-badge.admin {
  background: rgba(249, 115, 22, 0.1);
  color: #f97316;
  border: 1px solid rgba(249, 115, 22, 0.2);
}

.form-title {
  font-size: 26px;
  font-weight: 800;
  color: var(--ui-ink);
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}

.form-sub {
  font-size: 13px;
  color: var(--ui-muted);
  margin-bottom: 28px;
  line-height: 1.5;
}

/* 表单字段 */
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--ui-ink);
  letter-spacing: 0.02em;
}

.field-input {
  padding: 11px 14px;
  border: 1.5px solid var(--ui-line);
  border-radius: 10px;
  font-size: 14px;
  color: var(--ui-ink);
  background: var(--ui-bg);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  width: 100%;
  box-sizing: border-box;
}

.field-input:focus {
  border-color: #0ea5a4;
  box-shadow: 0 0 0 3px rgba(14, 165, 164, 0.1);
}

.auth-page.admin .field-input:focus {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.1);
}

.field-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 错误提示 */
.error-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 13px;
}

.error-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(239, 68, 68, 0.15);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  flex-shrink: 0;
}

/* 操作按钮 */
.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.btn-primary {
  flex: 1;
  padding: 11px 16px;
  background: #0ea5a4;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: opacity 0.15s, transform 0.1s;
}

.auth-page.admin .btn-primary {
  background: #f97316;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.btn-ghost {
  padding: 11px 16px;
  background: var(--ui-btn-bg, #fff);
  color: var(--ui-ink);
  border: 1.5px solid var(--ui-line);
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.btn-ghost:hover {
  background: var(--ui-btn-hover, rgba(15,23,42,0.04));
}

/* 辅助链接 */
.form-assist {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--ui-muted);
}

.assist-link {
  color: #0ea5a4;
  font-weight: 700;
  text-decoration: none;
  margin-left: 4px;
}

.auth-page.admin .assist-link {
  color: #f97316;
}

/* ===== 响应式：移动端单栏 ===== */
@media (max-width: 768px) {
  .auth-page {
    flex-direction: column;
  }

  .brand-side {
    flex: none;
    padding: 32px 24px;
    min-height: 200px;
  }

  .brand-slogan {
    font-size: 22px;
  }

  .form-side {
    flex: none;
    padding: 32px 24px;
  }

  .form-card {
    max-width: 100%;
  }
}
</style>
