<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  reportVisible: {
    type: Boolean,
    default: false,
  },
  points: {
    type: Object,
    required: true,
  },
  activeRoute: {
    type: Object,
    default: null,
  },
  activeMode: {
    type: String,
    default: 'walk',
  },
  canExport: {
    type: Boolean,
    default: true,
  },
  canReport: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['toggle-report', 'export-image', 'toggle-report-issue'])

const modeLabel = computed(() => (props.activeMode === 'wheel' ? t('route.wheelchair') : t('route.walk')))

function toggleReport() {
  emit('toggle-report')
}

function startReportIssue() {
  emit('toggle-report-issue')
}

function exportImage() {
  emit('export-image')
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

const unlockHint = computed(() => {
  if (!props.canReport && !props.canExport) return t('report.loginToUnlockBoth')
  if (!props.canReport) return t('report.loginToUnlockReport')
  if (!props.canExport) return t('report.loginToUnlockExport')
  return ''
})
</script>

<template>
  <details data-testid="panel-report" class="panel">
    <summary>{{ t('report.title') }}</summary>
    <div class="panel-body">
      <button data-testid="report-toggle" type="button" class="btn" @click="toggleReport">
        {{ t('report.toggle') }}
      </button>
      <button
        type="button"
        class="btn"
        :disabled="!canReport"
        :title="!canReport ? t('toast.loginRequired') : ''"
        @click="startReportIssue"
      >
        {{ t('report.reportIssue') }}
      </button>

      <div v-if="unlockHint" class="hint">{{ unlockHint }}</div>

      <div v-if="reportVisible" data-testid="route-report-card" id="route-report-card" class="report-card">
        <div class="card-title">{{ t('report.routeReportTitle', { mode: modeLabel }) }}</div>
        <div v-if="points.start">{{ t('report.start') }}：{{ points.start.name || t('map.startPoint') }}</div>
        <div v-if="points.end">{{ t('report.end') }}：{{ points.end.name || t('map.endPoint') }}</div>

        <div v-if="activeRoute">
          <div class="card-sub">
            {{ formatDistance(activeRoute.distanceM) }} · {{ formatDuration(activeRoute.durationSec) }} · {{ t('route.riskCount') }}:
            {{ activeRoute.riskCount ?? 0 }}
          </div>

          <div v-if="activeRoute.instructions && activeRoute.instructions.length" class="list">
            <div class="card-title">{{ t('report.steps') }}</div>
            <ol>
              <li v-for="(step, idx) in activeRoute.instructions" :key="`${step.action}-${idx}`">
                {{ step.text }}
              </li>
            </ol>
          </div>

          <div v-if="activeRoute.explain && activeRoute.explain.length" class="list">
            <div class="card-title">{{ t('report.explain') }}</div>
            <ul>
              <li v-for="(item, idx) in activeRoute.explain" :key="`${item}-${idx}`">
                {{ item }}
              </li>
            </ul>
          </div>
        </div>
        <div v-else class="hint">{{ t('report.computeFirst') }}</div>
      </div>

      <button
        data-testid="report-export-image"
        type="button"
        class="btn"
        :disabled="!canExport"
        :title="!canExport ? t('toast.loginRequired') : ''"
        @click="exportImage"
      >
        {{ t('report.exportImage') }}
      </button>
    </div>
  </details>
</template>

