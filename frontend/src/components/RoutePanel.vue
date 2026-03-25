<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  hasRoute: {
    type: Boolean,
    default: false,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: '',
  },
  walkSummary: {
    type: Object,
    default: null,
  },
  wheelSummary: {
    type: Object,
    default: null,
  },
  activeMode: {
    type: String,
    default: 'walk',
  },
  strategy: {
    type: String,
    default: 'BALANCED',
  },
  strategyWeights: {
    type: Object,
    default: () => ({ stairsPenalty: 0.3, slopePenalty: 0.2, constructionPenalty: 0.3 }),
  },
})

const emit = defineEmits(['select-mode', 'update:strategy', 'update:strategyWeights'])

function selectMode(mode) {
  emit('select-mode', mode)
}

function selectStrategy(s) {
  emit('update:strategy', s)
}

function updateWeight(key, value) {
  emit('update:strategyWeights', { ...props.strategyWeights, [key]: Number(value) })
}

function formatDistance(distanceM) {
  const value = Number(distanceM)
  if (!Number.isFinite(value)) return '--'
  if (value >= 1000) return `${(value / 1000).toFixed(2)} km`
  return `${Math.round(value)} m`
}

function formatDuration(seconds) {
  const value = Number(seconds)
  if (!Number.isFinite(value)) return '--'
  if (value >= 3600) {
    const hours = Math.floor(value / 3600)
    const minutes = Math.round((value % 3600) / 60)
    return `${hours}h ${minutes}m`
  }
  return `${Math.max(1, Math.round(value / 60))} min`
}
</script>

<template>
  <!-- 路线面板：模式选择、策略、权重调节、路线摘要 -->
  <details data-testid="panel-route" class="route-panel" open>
    <summary class="panel-summary">
      <span class="summary-icon">◎</span>
      <span class="summary-label">{{ t('route.title') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">
      <!-- 出行模式切换 -->
      <div v-if="hasRoute" class="mode-tabs">
        <button
          data-testid="route-mode-walk"
          type="button"
          class="mode-tab"
          :class="{ active: activeMode === 'walk' }"
          @click="selectMode('walk')"
        >
          <span class="mode-icon">🚶</span>
          {{ t('route.walk') }}
        </button>
        <button
          data-testid="route-mode-wheel"
          type="button"
          class="mode-tab"
          :class="{ active: activeMode === 'wheel' }"
          @click="selectMode('wheel')"
        >
          <span class="mode-icon">♿</span>
          {{ t('route.wheelchair') }}
        </button>
      </div>

      <!-- 策略选择器 -->
      <fieldset v-if="hasRoute" class="strategy-section">
        <legend class="section-legend">{{ t('route.strategyTitle') }}</legend>
        <div class="strategy-btns">
          <button
            v-for="s in ['SHORTEST', 'BALANCED', 'SAFEST']"
            :key="s"
            type="button"
            class="strategy-btn"
            :class="{ active: strategy === s }"
            @click="selectStrategy(s)"
          >
            {{ t('route.strategy_' + s) }}
          </button>
        </div>
        <!-- 高级权重调节 -->
        <details class="advanced-weights">
          <summary class="advanced-summary">{{ t('route.advancedWeights') }}</summary>
          <div class="weight-slider">
            <div class="weight-header">
              <label class="weight-label">{{ t('route.stairsPenalty') }}</label>
              <span class="weight-val">{{ strategyWeights.stairsPenalty }}</span>
            </div>
            <input
              type="range"
              min="0"
              max="3"
              step="0.1"
              :value="strategyWeights.stairsPenalty"
              @input="updateWeight('stairsPenalty', $event.target.value)"
            />
          </div>
          <div class="weight-slider">
            <div class="weight-header">
              <label class="weight-label">{{ t('route.slopePenalty') }}</label>
              <span class="weight-val">{{ strategyWeights.slopePenalty }}</span>
            </div>
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              :value="strategyWeights.slopePenalty"
              @input="updateWeight('slopePenalty', $event.target.value)"
            />
          </div>
          <div class="weight-slider">
            <div class="weight-header">
              <label class="weight-label">{{ t('route.constructionPenalty') }}</label>
              <span class="weight-val">{{ strategyWeights.constructionPenalty }}</span>
            </div>
            <input
              type="range"
              min="0"
              max="3"
              step="0.1"
              :value="strategyWeights.constructionPenalty"
              @input="updateWeight('constructionPenalty', $event.target.value)"
            />
          </div>
        </details>
      </fieldset>

      <!-- 加载/错误状态 -->
      <div v-if="loading" class="status-loading">
        <span class="loading-spinner"></span>
        {{ t('common.loading') }}
      </div>
      <div v-else-if="error" class="status-error">
        <span class="error-icon">!</span>
        {{ error }}
      </div>

      <!-- 路线摘要卡片 -->
      <div v-if="walkSummary || wheelSummary" class="summary-cards">
        <div v-if="walkSummary" class="summary-card walk-card">
          <div class="summary-card-header">
            <span class="summary-mode-icon">🚶</span>
            <span class="summary-mode-name">{{ t('route.walk') }}</span>
          </div>
          <div class="summary-stats">
            <div class="stat">
              <span class="stat-value">{{ formatDistance(walkSummary.distanceM) }}</span>
              <span class="stat-label">距离</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat">
              <span class="stat-value">{{ formatDuration(walkSummary.durationSec) }}</span>
              <span class="stat-label">时间</span>
            </div>
          </div>
          <div class="summary-risk">
            <span class="risk-dot"></span>
            {{ t('route.riskCount') }}：{{ walkSummary.riskCount }}
          </div>
        </div>
        <div v-if="wheelSummary" class="summary-card wheel-card">
          <div class="summary-card-header">
            <span class="summary-mode-icon">♿</span>
            <span class="summary-mode-name">{{ t('route.wheelchair') }}</span>
          </div>
          <div class="summary-stats">
            <div class="stat">
              <span class="stat-value">{{ formatDistance(wheelSummary.distanceM) }}</span>
              <span class="stat-label">距离</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat">
              <span class="stat-value">{{ formatDuration(wheelSummary.durationSec) }}</span>
              <span class="stat-label">时间</span>
            </div>
          </div>
          <div class="summary-risk">
            <span class="risk-dot"></span>
            {{ t('route.riskCount') }}：{{ wheelSummary.riskCount }}
          </div>
        </div>
      </div>
    </div>
  </details>
</template>

<style scoped>
/* ===== 面板整体 ===== */
.route-panel {
  background: var(--ui-card, #fff);
  border-radius: 16px;
  box-shadow: 0 4px 24px var(--ui-shadow, rgba(0,0,0,0.10));
  overflow: hidden;
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
}

/* ===== 折叠标题栏 ===== */
.panel-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  cursor: pointer;
  user-select: none;
  list-style: none;
  font-size: 14px;
  font-weight: 700;
  color: var(--ui-ink);
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}

.panel-summary::-webkit-details-marker { display: none; }

.summary-icon {
  font-size: 15px;
  color: var(--ui-accent, #0ea5a4);
}

.summary-label { flex: 1; }

.summary-arrow {
  width: 6px;
  height: 6px;
  border-right: 2px solid var(--ui-muted, #9ca3af);
  border-bottom: 2px solid var(--ui-muted, #9ca3af);
  transform: rotate(45deg);
  transition: transform 0.2s;
}

.route-panel[open] .summary-arrow {
  transform: rotate(-135deg);
}

/* ===== 面板内容区 ===== */
.panel-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

/* ===== 出行模式 Tab ===== */
.mode-tabs {
  display: flex;
  gap: 8px;
  background: var(--ui-bg, #f9fafb);
  border-radius: 12px;
  padding: 4px;
}

.mode-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--ui-muted, #6b7280);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.mode-tab.active {
  background: var(--ui-card, #fff);
  color: var(--ui-accent, #0ea5a4);
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.mode-icon { font-size: 15px; }

/* ===== 策略选择 ===== */
.strategy-section {
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 12px;
  padding: 12px 14px;
  margin: 0;
}

.section-legend {
  font-size: 11px;
  font-weight: 700;
  color: var(--ui-muted, #9ca3af);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding: 0 4px;
}

.strategy-btns {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.strategy-btn {
  flex: 1;
  padding: 6px 8px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 8px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.strategy-btn.active {
  border-color: var(--ui-accent, #0ea5a4);
  background: rgba(14, 165, 164, 0.08);
  color: var(--ui-accent, #0ea5a4);
}

.strategy-btn:hover:not(.active) {
  border-color: var(--ui-accent, #0ea5a4);
  color: var(--ui-ink);
}

/* ===== 高级权重 ===== */
.advanced-weights {
  margin-top: 10px;
}

.advanced-summary {
  font-size: 12px;
  color: var(--ui-muted, #9ca3af);
  cursor: pointer;
  user-select: none;
  list-style: none;
}

.advanced-summary::-webkit-details-marker { display: none; }

.weight-slider {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.weight-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.weight-label {
  font-size: 12px;
  color: var(--ui-ink);
}

.weight-val {
  font-size: 12px;
  font-weight: 700;
  color: var(--ui-accent, #0ea5a4);
  min-width: 28px;
  text-align: right;
}

input[type="range"] {
  width: 100%;
  accent-color: var(--ui-accent, #0ea5a4);
  cursor: pointer;
}

/* ===== 加载/错误状态 ===== */
.status-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ui-muted, #9ca3af);
  padding: 4px 0;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--ui-line, #e5e7eb);
  border-top-color: var(--ui-accent, #0ea5a4);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.status-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 12px;
}

.error-icon {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(239, 68, 68, 0.15);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 800;
  flex-shrink: 0;
}

/* ===== 路线摘要卡片 ===== */
.summary-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-card {
  border-radius: 12px;
  padding: 12px 14px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color 0.15s;
}

.walk-card { border-left: 3px solid #22c55e; }
.wheel-card { border-left: 3px solid var(--ui-accent, #0ea5a4); }

.summary-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.summary-mode-icon { font-size: 16px; }

.summary-mode-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--ui-ink);
}

.summary-stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 18px;
  font-weight: 800;
  color: var(--ui-ink);
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.stat-label {
  font-size: 11px;
  color: var(--ui-muted, #9ca3af);
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: var(--ui-line, #e5e7eb);
}

.summary-risk {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ui-muted, #9ca3af);
}

.risk-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f97316;
  flex-shrink: 0;
}
</style>
