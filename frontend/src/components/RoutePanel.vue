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
  <details data-testid="panel-route" class="panel" open>
    <summary>{{ t('route.title') }}</summary>
    <div class="panel-body">
      <div v-if="hasRoute" class="row">
        <button
          data-testid="route-mode-walk"
          type="button"
          class="btn"
          :class="{ active: activeMode === 'walk' }"
          @click="selectMode('walk')"
        >
          {{ t('route.walk') }}
        </button>
        <button
          data-testid="route-mode-wheel"
          type="button"
          class="btn"
          :class="{ active: activeMode === 'wheel' }"
          @click="selectMode('wheel')"
        >
          {{ t('route.wheelchair') }}
        </button>
      </div>

      <!-- Strategy selector -->
      <fieldset v-if="hasRoute" class="strategy-fieldset">
        <legend class="hint">{{ t('route.strategyTitle') }}</legend>
        <div class="row">
          <button
            v-for="s in ['SHORTEST', 'BALANCED', 'SAFEST']"
            :key="s"
            type="button"
            class="btn btn-sm"
            :class="{ active: strategy === s }"
            @click="selectStrategy(s)"
          >
            {{ t('route.strategy_' + s) }}
          </button>
        </div>
        <details class="advanced-weights">
          <summary class="hint">{{ t('route.advancedWeights') }}</summary>
          <div class="weight-slider">
            <label>{{ t('route.stairsPenalty') }}: {{ strategyWeights.stairsPenalty }}</label>
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
            <label>{{ t('route.slopePenalty') }}: {{ strategyWeights.slopePenalty }}</label>
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
            <label>{{ t('route.constructionPenalty') }}: {{ strategyWeights.constructionPenalty }}</label>
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

      <div v-if="loading" class="hint">{{ t('common.loading') }}</div>
      <div v-else-if="error" class="hint danger">{{ error }}</div>

      <div v-if="walkSummary || wheelSummary" class="list">
        <div v-if="walkSummary" class="card">
          <div class="card-title">{{ t('route.walk') }}</div>
          <div class="card-sub">
            {{ formatDistance(walkSummary.distanceM) }} · {{ formatDuration(walkSummary.durationSec) }}
          </div>
          <div class="hint">{{ t('route.riskCount') }}：{{ walkSummary.riskCount }}</div>
        </div>
        <div v-if="wheelSummary" class="card">
          <div class="card-title">{{ t('route.wheelchair') }}</div>
          <div class="card-sub">
            {{ formatDistance(wheelSummary.distanceM) }} · {{ formatDuration(wheelSummary.durationSec) }}
          </div>
          <div class="hint">{{ t('route.riskCount') }}：{{ wheelSummary.riskCount }}</div>
        </div>
      </div>
    </div>
  </details>
</template>
