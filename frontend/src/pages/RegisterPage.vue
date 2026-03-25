<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { readResponseText } from '../utils/fetchUtils'

const { t } = useI18n()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref(null)
const success = ref(null)

const API_BASE_URL = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

async function register() {
  error.value = null
  success.value = null
  if (!username.value || !password.value) {
    error.value = t('auth.inputRequired')
    return
  }
  loading.value = true
  try {
    const res = await fetch(`${API_BASE_URL}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    if (!res.ok) {
      const text = await readResponseText(res)
      throw new Error(text || `HTTP ${res.status}`)
    }
    success.value = t('auth.registerSuccess')
    username.value = ''
    password.value = ''
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- 注册页：全屏两栏布局，与登录页风格一致 -->
  <div class="auth-page">
    <!-- 左侧品牌展示区 -->
    <div class="brand-side">
      <div class="brand-glow"></div>
      <div class="brand-content">
        <div class="brand-logo">
          <span class="brand-icon">♿</span>
        </div>
        <h1 class="brand-name">{{ t('brand.name') }}</h1>
        <p class="brand-slogan">{{ t('brand.slogan') }}</p>
        <p class="brand-sub">{{ t('brand.registerSub') }}</p>
        <!-- 功能特性卡片网格 -->
        <div class="brand-grid">
          <div class="brand-card">
            <div class="card-title">{{ t('brand.featureRoute') }}</div>
            <div class="card-desc">{{ t('brand.featureRouteDesc') }}</div>
          </div>
          <div class="brand-card">
            <div class="card-title">{{ t('brand.featureFavorites') }}</div>
            <div class="card-desc">{{ t('brand.featureFavoritesDesc') }}</div>
          </div>
          <div class="brand-card">
            <div class="card-title">{{ t('obstacle.report') }}</div>
            <div class="card-desc">{{ t('obstacle.submitSuccess') }}</div>
          </div>
          <div class="brand-card">
            <div class="card-title">{{ t('common.share') }}</div>
            <div class="card-desc">{{ t('toast.linkCopied') }}</div>
          </div>
        </div>
        <p class="brand-foot">{{ t('brand.registerFoot') }}</p>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-side">
      <div class="form-card">
        <!-- 身份标签 -->
        <div class="role-badge">
          {{ t('auth.newUserTag') }}
        </div>

        <h2 class="form-title">{{ t('auth.registerTitle') }}</h2>
        <p class="form-sub">{{ t('auth.userLoginHint') }}</p>

        <!-- 注册表单 -->
        <form class="form" @submit.prevent="register">
          <div class="field">
            <label class="field-label">{{ t('auth.username') }}</label>
            <input
              data-testid="register-username"
              class="field-input"
              type="text"
              v-model="username"
              autocomplete="username"
              :placeholder="t('auth.usernamePlaceholder')"
              :disabled="loading"
            />
          </div>

          <div class="field">
            <label class="field-label">{{ t('auth.password') }}</label>
            <input
              data-testid="register-password"
              class="field-input"
              type="password"
              v-model="password"
              autocomplete="new-password"
              :placeholder="t('auth.passwordPlaceholder')"
              :disabled="loading"
            />
          </div>

          <!-- 错误/成功提示 -->
          <div v-if="error" class="status-bar err">
            <span class="status-icon">!</span>
            {{ error }}
          </div>
          <div v-if="success" class="status-bar ok">
            <span class="status-icon">✓</span>
            {{ success }}
          </div>

          <!-- 操作按钮 -->
          <div class="form-actions">
            <button
              data-testid="register-submit"
              class="btn-primary"
              type="submit"
              :disabled="loading"
            >
              <span v-if="loading" class="btn-spinner"></span>
              {{ loading ? t('common.loading') : t('nav.register') }}
            </button>
          </div>
        </form>

        <!-- 辅助链接 -->
        <div class="form-assist">
          <span class="assist-text">{{ t('auth.hasAccount') }}</span>
          <RouterLink class="assist-link" to="/login">{{ t('nav.login') }}</RouterLink>
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
  max-width: 400px;
  width: 100%;
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
  font-size: 26px;
  font-weight: 800;
  line-height: 1.3;
  margin-bottom: 8px;
  letter-spacing: -0.02em;
}

.brand-sub {
  font-size: 14px;
  opacity: 0.75;
  margin-bottom: 28px;
  line-height: 1.6;
}

/* 功能特性卡片网格 */
.brand-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 28px;
}

.brand-card {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  padding: 12px 14px;
  backdrop-filter: blur(4px);
}

.card-title {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 4px;
  opacity: 0.9;
}

.card-desc {
  font-size: 11px;
  opacity: 0.6;
  line-height: 1.4;
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
  background: rgba(14, 165, 164, 0.1);
  color: #0ea5a4;
  border: 1px solid rgba(14, 165, 164, 0.2);
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

.field-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 状态提示条 */
.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}

.status-bar.err {
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
}

.status-bar.ok {
  background: rgba(34, 197, 94, 0.06);
  border: 1px solid rgba(34, 197, 94, 0.25);
  color: #16a34a;
}

.status-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  flex-shrink: 0;
  background: currentColor;
  color: #fff;
}

.status-bar.err .status-icon {
  background: rgba(239, 68, 68, 0.15);
  color: #dc2626;
}

.status-bar.ok .status-icon {
  background: rgba(34, 197, 94, 0.15);
  color: #16a34a;
}

/* 操作按钮 */
.form-actions {
  margin-top: 4px;
}

.btn-primary {
  width: 100%;
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

/* ===== 响应式：移动端单栏 ===== */
@media (max-width: 768px) {
  .auth-page {
    flex-direction: column;
  }

  .brand-side {
    flex: none;
    padding: 32px 24px;
    min-height: 220px;
  }

  .brand-slogan {
    font-size: 20px;
  }

  .brand-grid {
    grid-template-columns: 1fr 1fr;
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
