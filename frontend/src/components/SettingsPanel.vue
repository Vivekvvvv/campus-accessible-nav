<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, setLocale, getLocale } from '../locales'
import { useThemeStore } from '../stores/useThemeStore'
import {
  buildDefaultAccessibilityProfile,
  buildDefaultVoicePolicy,
  normalizeAccessibilityProfile,
  normalizeVoicePolicy,
} from './settingsPanelHelpers'

const { t } = useI18n()
const themeStore = useThemeStore()

// 主题选项
const themeOptions = [
  { value: 'light', labelKey: 'settings.themeLight' },
  { value: 'dark', labelKey: 'settings.themeDark' },
  { value: 'auto', labelKey: 'settings.themeAuto' }
]

const props = defineProps({
  baseMapState: {
    type: Object,
    required: true,
  },
  baseMapOptions: {
    type: Array,
    required: true,
  },
  shareQrVisible: {
    type: Boolean,
    default: false,
  },
  shareQrSrc: {
    type: String,
    default: '',
  },
  campusOnly: {
    type: Boolean,
    default: false,
  },
  accessibilityProfile: {
    type: Object,
    default: () => null,
  },
  accessibilityLoading: {
    type: Boolean,
    default: false,
  },
  accessibilitySaving: {
    type: Boolean,
    default: false,
  },
  voicePolicy: {
    type: Object,
    default: () => null,
  },
  voiceSaving: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits([
  'change-basemap',
  'copy-link',
  'open-qr',
  'toggle-campus-only',
  'save-accessibility-profile',
  'save-voice-policy',
])

const mobilityOptions = [
  { value: 'WALK', labelKey: 'settings.mobilityWalk' },
  { value: 'WHEELCHAIR', labelKey: 'settings.mobilityWheelchair' },
  { value: 'VISUAL_IMPAIRMENT', labelKey: 'settings.mobilityVisualImpairment' },
  { value: 'STROLLER', labelKey: 'settings.mobilityStroller' },
]

const accessibilityForm = ref(buildDefaultAccessibilityProfile())
const voicePolicyForm = ref(buildDefaultVoicePolicy())

watch(
  () => props.accessibilityProfile,
  (value) => {
    accessibilityForm.value = normalizeAccessibilityProfile(value)
  },
  { immediate: true, deep: true },
)

watch(
  () => props.voicePolicy,
  (value) => {
    voicePolicyForm.value = normalizeVoicePolicy(value)
  },
  { immediate: true, deep: true },
)

function changeBasemap(event) {
  emit('change-basemap', event.target.value)
}

function toggleCampusOnly(event) {
  emit('toggle-campus-only', event.target.checked)
}

function changeLocale(event) {
  setLocale(event.target.value)
}

function saveAccessibilityProfile() {
  emit('save-accessibility-profile', { ...accessibilityForm.value })
}

function saveVoicePolicy() {
  emit('save-voice-policy', { ...voicePolicyForm.value })
}
</script>

<template>
  <!-- 设置面板：外观、地图、分享、无障碍档案、语音策略 -->
  <details data-testid="panel-settings" class="settings-panel">
    <summary class="panel-summary">
      <span class="summary-icon">⚙</span>
      <span class="summary-label">{{ t('settings.title') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">
<!-- 外观设置区 -->
      <div class="settings-group">
        <div class="group-title">外观</div>
        <!-- 语言 -->
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.language') }}</span>
          <select data-testid="locale-select" class="settings-select" :value="getLocale()" @change="changeLocale">
            <option v-for="locale in SUPPORTED_LOCALES" :key="locale.code" :value="locale.code">
              {{ locale.flag }} {{ locale.name }}
            </option>
          </select>
        </div>
        <!-- 主题 -->
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.theme') }}</span>
          <div class="theme-tabs">
            <button
              v-for="opt in themeOptions"
              :key="opt.value"
              type="button"
              class="theme-tab"
              :class="{ active: themeStore.mode === opt.value }"
              @click="themeStore.setTheme(opt.value)"
            >
              {{ t(opt.labelKey) }}
            </button>
          </div>
        </div>
      </div>

      <!-- 地图设置区 -->
      <div class="settings-group">
        <div class="group-title">地图</div>
        <!-- 底图 -->
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.baseMap') }}</span>
          <select data-testid="basemap-select" class="settings-select" :value="baseMapState.current" @change="changeBasemap">
            <option v-for="opt in baseMapOptions" :key="opt.key" :value="opt.key">{{ opt.label }}</option>
          </select>
        </div>
        <div v-if="baseMapState.lastError" data-testid="basemap-error" class="error-tip">
          <span class="error-icon">!</span>{{ baseMapState.lastError }}
        </div>
        <!-- 校园范围 -->
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.campusOnly') }}</span>
          <label class="toggle-item">
            <input data-testid="route-campus-only" type="checkbox" class="toggle-check" :checked="campusOnly" @change="toggleCampusOnly" />
            <span class="toggle-track"></span>
          </label>
        </div>
      </div>

      <!-- 分享区 -->
      <div class="settings-group">
        <div class="group-title">分享</div>
        <div class="share-btns">
          <button data-testid="tool-copy-link" type="button" class="share-btn" @click="$emit('copy-link')">
            <span class="share-icon">⎘</span>
            {{ t('common.copy') }}
          </button>
          <button data-testid="tool-open-qr" type="button" class="share-btn" @click="$emit('open-qr')">
            <span class="share-icon">⊞</span>
            {{ t('common.share') }}
          </button>
        </div>
        <img v-if="shareQrVisible" :src="shareQrSrc" alt="share qr" class="qr-img" />
      </div>

      <!-- 无障碍档案区 -->
      <div class="settings-group">
        <div class="group-title">{{ t('settings.accessibilityProfile') }}</div>
        <div v-if="accessibilityLoading" class="loading-hint">
          <span class="spin"></span>{{ t('settings.profileLoading') }}
        </div>
        <!-- 出行模式 -->
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.mobilityMode') }}</span>
          <select
            data-testid="accessibility-mobility-mode"
            class="settings-select"
            :disabled="accessibilityLoading || accessibilitySaving"
            v-model="accessibilityForm.mobilityMode"
          >
            <option v-for="opt in mobilityOptions" :key="opt.value" :value="opt.value">{{ t(opt.labelKey) }}</option>
          </select>
        </div>
        <!-- 避障选项 -->
        <div class="checkbox-group">
          <label class="check-item">
            <input data-testid="accessibility-avoid-stairs" type="checkbox" class="check-input" :disabled="accessibilityLoading || accessibilitySaving" v-model="accessibilityForm.avoidStairs" />
            <span class="check-box"></span>
            <span class="check-label">{{ t('settings.avoidStairs') }}</span>
          </label>
          <label class="check-item">
            <input data-testid="accessibility-avoid-slope" type="checkbox" class="check-input" :disabled="accessibilityLoading || accessibilitySaving" v-model="accessibilityForm.avoidSlope" />
            <span class="check-box"></span>
            <span class="check-label">{{ t('settings.avoidSlope') }}</span>
          </label>
          <label class="check-item">
            <input data-testid="accessibility-avoid-construction" type="checkbox" class="check-input" :disabled="accessibilityLoading || accessibilitySaving" v-model="accessibilityForm.avoidConstruction" />
            <span class="check-box"></span>
            <span class="check-label">{{ t('settings.avoidConstruction') }}</span>
          </label>
        </div>
        <!-- 最大坡度 -->
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.maxSlopePercent') }}</span>
          <input
            data-testid="accessibility-max-slope"
            class="settings-number"
            type="number" min="0" max="45" step="0.5"
            :disabled="accessibilityLoading || accessibilitySaving"
            v-model.number="accessibilityForm.maxSlopePercent"
          />
        </div>
        <button data-testid="accessibility-save" type="button" class="save-btn" :disabled="accessibilityLoading || accessibilitySaving" @click="saveAccessibilityProfile">
          {{ accessibilitySaving ? '...' : t('common.save') }}
        </button>
      </div>

      <!-- 语音策略区 -->
      <div class="settings-group">
        <div class="group-title">{{ t('settings.voicePolicy') }}</div>
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.preTurnM') }}</span>
          <input data-testid="voice-policy-pre-turn" class="settings-number" type="number" min="0" max="500" step="1" :disabled="voiceSaving" v-model.number="voicePolicyForm.preTurnM" />
        </div>
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.preArrivalM') }}</span>
          <input data-testid="voice-policy-pre-arrival" class="settings-number" type="number" min="0" max="1000" step="1" :disabled="voiceSaving" v-model.number="voicePolicyForm.preArrivalM" />
        </div>
        <div class="settings-row">
          <span class="settings-label">{{ t('settings.announceIntervalM') }}</span>
          <input data-testid="voice-policy-announce-interval" class="settings-number" type="number" min="0" max="500" step="1" :disabled="voiceSaving" v-model.number="voicePolicyForm.announceIntervalM" />
        </div>
        <div class="time-row">
          <div class="settings-row">
            <span class="settings-label">{{ t('settings.quietHoursStart') }}</span>
            <input data-testid="voice-policy-quiet-start" class="settings-time" type="time" :disabled="voiceSaving" v-model="voicePolicyForm.quietHoursStart" />
          </div>
          <div class="settings-row">
            <span class="settings-label">{{ t('settings.quietHoursEnd') }}</span>
            <input data-testid="voice-policy-quiet-end" class="settings-time" type="time" :disabled="voiceSaving" v-model="voicePolicyForm.quietHoursEnd" />
          </div>
        </div>
        <label class="check-item">
          <input data-testid="voice-policy-vibrate-enabled" type="checkbox" class="check-input" :disabled="voiceSaving" v-model="voicePolicyForm.vibrateEnabled" />
          <span class="check-box"></span>
          <span class="check-label">{{ t('settings.vibrateEnabled') }}</span>
        </label>
        <button data-testid="voice-policy-save" type="button" class="save-btn" :disabled="voiceSaving" @click="saveVoicePolicy">
          {{ voiceSaving ? '...' : t('common.save') }}
        </button>
      </div>
    </div>
  </details>
</template>

<style scoped>
/* ===== 面板整体 ===== */
.settings-panel {
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
.summary-icon { font-size: 15px; color: var(--ui-accent, #0ea5a4); }
.summary-label { flex: 1; }
.summary-arrow {
  width: 6px; height: 6px;
  border-right: 2px solid var(--ui-muted, #9ca3af);
  border-bottom: 2px solid var(--ui-muted, #9ca3af);
  transform: rotate(45deg);
  transition: transform 0.2s;
}
.settings-panel[open] .summary-arrow { transform: rotate(-135deg); }

/* ===== 内容区 ===== */
.panel-body {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 8px 0;
}

/* ===== 设置分组 ===== */
.settings-group {
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.settings-group:last-child { border-bottom: none; }

.group-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--ui-muted, #9ca3af);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 2px;
}

/* ===== 设置行 ===== */
.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
}

.settings-label {
  font-size: 13px;
  color: var(--ui-ink);
  flex-shrink: 0;
}

/* ===== 选择框 ===== */
.settings-select {
  padding: 6px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 8px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 13px;
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s;
  flex-shrink: 0;
}
.settings-select:focus { border-color: var(--ui-accent, #0ea5a4); }

/* ===== 数字输入 ===== */
.settings-number {
  width: 80px;
  padding: 6px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 8px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 13px;
  outline: none;
  text-align: right;
  transition: border-color 0.15s;
}
.settings-number:focus { border-color: var(--ui-accent, #0ea5a4); }
.settings-number:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 时间输入 ===== */
.settings-time {
  padding: 6px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 8px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}
.settings-time:focus { border-color: var(--ui-accent, #0ea5a4); }

/* ===== 主题 Tab ===== */
.theme-tabs {
  display: flex;
  gap: 4px;
  background: var(--ui-bg, #f9fafb);
  border-radius: 9px;
  padding: 3px;
  border: 1px solid var(--ui-line, #e5e7eb);
}
.theme-tab {
  flex: 1;
  padding: 5px 8px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--ui-muted, #6b7280);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.theme-tab.active {
  background: var(--ui-card, #fff);
  color: var(--ui-accent, #0ea5a4);
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

/* ===== 开关 Toggle ===== */
.toggle-item {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.toggle-check { display: none; }
.toggle-track {
  width: 36px; height: 20px;
  border-radius: 999px;
  background: var(--ui-line, #e5e7eb);
  position: relative;
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-track::after {
  content: '';
  position: absolute;
  width: 14px; height: 14px;
  border-radius: 50%;
  background: #fff;
  top: 3px; left: 3px;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}
.toggle-check:checked + .toggle-track { background: var(--ui-accent, #0ea5a4); }
.toggle-check:checked + .toggle-track::after { transform: translateX(16px); }

/* ===== 分享按钮 ===== */
.share-btns { display: flex; gap: 8px; }
.share-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 10px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.share-btn:hover {
  border-color: var(--ui-accent, #0ea5a4);
  background: rgba(14,165,164,0.06);
  color: var(--ui-accent, #0ea5a4);
}
.share-icon { font-size: 15px; }
.qr-img {
  width: 120px; height: 120px;
  border-radius: 8px;
  border: 1px solid var(--ui-line, #e5e7eb);
  margin-top: 4px;
}

/* ===== 复选框组 ===== */
.checkbox-group { display: flex; flex-direction: column; gap: 8px; }
.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.check-input { display: none; }
.check-box {
  width: 16px; height: 16px;
  border-radius: 4px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
  position: relative;
}
.check-input:checked + .check-box {
  background: var(--ui-accent, #0ea5a4);
  border-color: var(--ui-accent, #0ea5a4);
}
.check-input:checked + .check-box::after {
  content: '';
  width: 4px; height: 7px;
  border-right: 2px solid #fff;
  border-bottom: 2px solid #fff;
  transform: rotate(45deg);
  margin-bottom: 2px;
}
.check-input:disabled + .check-box { opacity: 0.4; cursor: not-allowed; }
.check-label { font-size: 13px; color: var(--ui-ink); }

/* ===== 时间行 ===== */
.time-row { display: flex; gap: 8px; flex-wrap: wrap; }
.time-row .settings-row { flex: 1; }

/* ===== 保存按钮 ===== */
.save-btn {
  align-self: flex-start;
  padding: 7px 18px;
  border-radius: 9px;
  border: 1.5px solid rgba(14,165,164,0.3);
  background: rgba(14,165,164,0.08);
  color: var(--ui-accent, #0ea5a4);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}
.save-btn:hover:not(:disabled) { background: rgba(14,165,164,0.15); }
.save-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* ===== 加载提示 ===== */
.loading-hint {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: var(--ui-muted, #9ca3af);
}
.spin {
  width: 12px; height: 12px;
  border: 2px solid var(--ui-line, #e5e7eb);
  border-top-color: var(--ui-accent, #0ea5a4);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 错误提示 ===== */
.error-tip {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; border-radius: 8px;
  background: rgba(239,68,68,0.06);
  border: 1px solid rgba(239,68,68,0.2);
  color: #dc2626; font-size: 12px;
}
.error-icon {
  width: 15px; height: 15px; border-radius: 50%;
  background: rgba(239,68,68,0.15);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 800; flex-shrink: 0;
}
</style>
