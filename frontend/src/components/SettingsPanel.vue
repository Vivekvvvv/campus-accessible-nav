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

function changeTheme(event) {
  themeStore.setTheme(event.target.value)
}

function saveAccessibilityProfile() {
  emit('save-accessibility-profile', { ...accessibilityForm.value })
}

function saveVoicePolicy() {
  emit('save-voice-policy', { ...voicePolicyForm.value })
}
</script>

<template>
  <details data-testid="panel-settings" class="panel">
    <summary>{{ t('settings.title') }}</summary>
    <div class="panel-body">
      <!-- 语言设置 -->
      <label class="label">
        <span>{{ t('settings.language') }}</span>
        <select
          data-testid="locale-select"
          class="input"
          :value="getLocale()"
          @change="changeLocale"
        >
          <option v-for="locale in SUPPORTED_LOCALES" :key="locale.code" :value="locale.code">
            {{ locale.flag }} {{ locale.name }}
          </option>
        </select>
      </label>

      <!-- 主题设置 -->
      <label class="label">
        <span>{{ t('settings.theme') }}</span>
        <select
          data-testid="theme-select"
          class="input"
          :value="themeStore.mode"
          @change="changeTheme"
        >
          <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">
            {{ t(opt.labelKey) }}
          </option>
        </select>
      </label>

      <!-- 底图设置 -->
      <label class="label">
        <span>{{ t('settings.baseMap') }}</span>
        <select
          data-testid="basemap-select"
          class="input"
          :value="baseMapState.current"
          @change="changeBasemap"
        >
          <option v-for="opt in baseMapOptions" :key="opt.key" :value="opt.key">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <div v-if="baseMapState.lastError" data-testid="basemap-error" class="hint danger">
        {{ baseMapState.lastError }}
      </div>

      <!-- 分享 -->
      <div class="row">
        <button data-testid="tool-copy-link" type="button" class="btn" @click="$emit('copy-link')">
          {{ t('common.copy') }}
        </button>
        <button data-testid="tool-open-qr" type="button" class="btn" @click="$emit('open-qr')">
          {{ t('common.share') }}
        </button>
      </div>
      <img v-if="shareQrVisible" :src="shareQrSrc" alt="share qr" />

      <!-- 路线范围 -->
      <div class="label">
        <span>{{ t('settings.campusOnly') }}</span>
        <div class="row">
          <input
            data-testid="route-campus-only"
            type="checkbox"
            :checked="campusOnly"
            @change="toggleCampusOnly"
          />
          <span>{{ t('settings.campusOnly') }}</span>
        </div>
      </div>

      <div class="label">
        <span>{{ t('settings.accessibilityProfile') }}</span>
        <div v-if="accessibilityLoading" class="hint">
          {{ t('settings.profileLoading') }}
        </div>
        <label class="label">
          <span>{{ t('settings.mobilityMode') }}</span>
          <select
            data-testid="accessibility-mobility-mode"
            class="input"
            :disabled="accessibilityLoading || accessibilitySaving"
            v-model="accessibilityForm.mobilityMode"
          >
            <option v-for="opt in mobilityOptions" :key="opt.value" :value="opt.value">
              {{ t(opt.labelKey) }}
            </option>
          </select>
        </label>
        <div class="row">
          <label>
            <input
              data-testid="accessibility-avoid-stairs"
              type="checkbox"
              :disabled="accessibilityLoading || accessibilitySaving"
              v-model="accessibilityForm.avoidStairs"
            />
            {{ t('settings.avoidStairs') }}
          </label>
          <label>
            <input
              data-testid="accessibility-avoid-slope"
              type="checkbox"
              :disabled="accessibilityLoading || accessibilitySaving"
              v-model="accessibilityForm.avoidSlope"
            />
            {{ t('settings.avoidSlope') }}
          </label>
        </div>
        <div class="row">
          <label>
            <input
              data-testid="accessibility-avoid-construction"
              type="checkbox"
              :disabled="accessibilityLoading || accessibilitySaving"
              v-model="accessibilityForm.avoidConstruction"
            />
            {{ t('settings.avoidConstruction') }}
          </label>
        </div>
        <label class="label">
          <span>{{ t('settings.maxSlopePercent') }}</span>
          <input
            data-testid="accessibility-max-slope"
            class="input"
            type="number"
            min="0"
            max="45"
            step="0.5"
            :disabled="accessibilityLoading || accessibilitySaving"
            v-model.number="accessibilityForm.maxSlopePercent"
          />
        </label>
        <button
          data-testid="accessibility-save"
          type="button"
          class="btn"
          :disabled="accessibilityLoading || accessibilitySaving"
          @click="saveAccessibilityProfile"
        >
          {{ t('common.save') }}
        </button>
      </div>

      <div class="label">
        <span>{{ t('settings.voicePolicy') }}</span>

        <label class="label">
          <span>{{ t('settings.preTurnM') }}</span>
          <input
            data-testid="voice-policy-pre-turn"
            class="input"
            type="number"
            min="0"
            max="500"
            step="1"
            :disabled="voiceSaving"
            v-model.number="voicePolicyForm.preTurnM"
          />
        </label>

        <label class="label">
          <span>{{ t('settings.preArrivalM') }}</span>
          <input
            data-testid="voice-policy-pre-arrival"
            class="input"
            type="number"
            min="0"
            max="1000"
            step="1"
            :disabled="voiceSaving"
            v-model.number="voicePolicyForm.preArrivalM"
          />
        </label>

        <label class="label">
          <span>{{ t('settings.announceIntervalM') }}</span>
          <input
            data-testid="voice-policy-announce-interval"
            class="input"
            type="number"
            min="0"
            max="500"
            step="1"
            :disabled="voiceSaving"
            v-model.number="voicePolicyForm.announceIntervalM"
          />
        </label>

        <div class="row">
          <label class="label">
            <span>{{ t('settings.quietHoursStart') }}</span>
            <input
              data-testid="voice-policy-quiet-start"
              class="input"
              type="time"
              :disabled="voiceSaving"
              v-model="voicePolicyForm.quietHoursStart"
            />
          </label>

          <label class="label">
            <span>{{ t('settings.quietHoursEnd') }}</span>
            <input
              data-testid="voice-policy-quiet-end"
              class="input"
              type="time"
              :disabled="voiceSaving"
              v-model="voicePolicyForm.quietHoursEnd"
            />
          </label>
        </div>

        <label>
          <input
            data-testid="voice-policy-vibrate-enabled"
            type="checkbox"
            :disabled="voiceSaving"
            v-model="voicePolicyForm.vibrateEnabled"
          />
          {{ t('settings.vibrateEnabled') }}
        </label>

        <button
          data-testid="voice-policy-save"
          type="button"
          class="btn"
          :disabled="voiceSaving"
          @click="saveVoicePolicy"
        >
          {{ t('common.save') }}
        </button>
      </div>
    </div>
  </details>
</template>
