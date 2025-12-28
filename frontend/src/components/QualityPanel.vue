<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiJson } from '../services/apiService'

const { t } = useI18n()

defineProps({
  qualityState: {
    type: Object,
    required: true,
  },
  issueFilterOptions: {
    type: Array,
    default: () => [],
  },
  hasAdmin: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits([
  'refresh-report',
  'refresh-history',
  'change-filter',
  'select-history',
  'export-report-json',
  'export-report-image',
])

const diffState = ref({ loading: false, result: null, error: null })
const diffIds = ref({ id1: '', id2: '' })

function formatTime(value) {
  if (!value) return '--'
  try {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return String(value)
    return date.toLocaleString()
  } catch {
    return String(value)
  }
}

function selectHistory(item) {
  emit('select-history', item)
}

async function compareDiff() {
  const id1 = diffIds.value.id1
  const id2 = diffIds.value.id2
  if (!id1 || !id2) return
  diffState.value = { loading: true, result: null, error: null }
  try {
    const data = await apiJson(`/api/admin/graph/snapshots/${id1}/diff/${id2}`, { method: 'GET' })
    diffState.value = { loading: false, result: data, error: null }
  } catch (e) {
    diffState.value = { loading: false, result: null, error: e?.message || String(e) }
  }
}

async function rollbackToSnapshot(snapshotId) {
  if (!window.confirm(t('quality.rollbackConfirm'))) return
  try {
    await apiJson(`/api/admin/graph/rollback/${snapshotId}`, { method: 'POST' })
    emit('refresh-report')
    emit('refresh-history')
  } catch (e) {
    alert(e?.message || String(e))
  }
}
</script>

<template>
  <details data-testid="panel-quality" class="panel">
    <summary>{{ t('quality.title') }}</summary>
    <div class="panel-body">
      <div v-if="!hasAdmin" class="hint">{{ t('quality.adminLoginHint') }}</div>
      <template v-else>
        <div class="row">
          <button type="button" class="btn" @click="$emit('refresh-report')">{{ t('quality.refreshReport') }}</button>
          <button type="button" class="btn" @click="$emit('refresh-history')">{{ t('quality.refreshHistory') }}</button>
        </div>

        <div v-if="qualityState.loading" class="hint">{{ t('quality.reportLoading') }}</div>
        <div v-else-if="qualityState.error" class="hint danger">{{ qualityState.error }}</div>

        <div v-if="qualityState.activeReport" class="card">
          <div class="card-title">
            {{ t('quality.currentDisplay', { label: qualityState.activeLabel || t('quality.currentReportFallback') }) }}
          </div>
          <div class="card-sub">
            {{ t('quality.qualityScore') }}：{{ qualityState.activeReport.qualityScore }} · {{ t('quality.componentCount') }}：{{
              qualityState.activeReport.componentCount
            }}
          </div>
          <div class="hint">
            {{ t('quality.nodes') }}：{{ qualityState.activeReport.nodeCount }} · {{ t('quality.edges') }}：{{ qualityState.activeReport.edgeCount }}
          </div>
          <div class="hint">
            {{ t('quality.disconnected') }}：{{ qualityState.activeReport.disconnectedNodes }} · {{ t('quality.isolated') }}：{{
              qualityState.activeReport.isolatedNodes
            }}
            · {{ t('quality.deadEnds') }}：{{ qualityState.activeReport.deadEndNodes }}
          </div>
          <div class="list" v-if="qualityState.activeReport.suggestions?.length">
            <div v-for="item in qualityState.activeReport.suggestions" :key="item" class="hint">
              {{ item }}
            </div>
          </div>
          <div class="row">
            <button type="button" class="btn" @click="$emit('export-report-json')">{{ t('quality.exportJson') }}</button>
            <button type="button" class="btn" @click="$emit('export-report-image')">{{ t('quality.exportImage') }}</button>
          </div>
        </div>

        <div class="label" v-if="issueFilterOptions.length">
          <span class="hint">{{ t('quality.issueFilter') }}</span>
          <div class="row">
            <button
              v-for="opt in issueFilterOptions"
              :key="opt.key"
              type="button"
              class="btn"
              :class="{ active: qualityState.issueFilter === opt.key }"
              @click="$emit('change-filter', opt.key)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="label">
          <span class="hint">{{ t('quality.historyTitle') }}</span>
          <div v-if="qualityState.historyLoading" class="hint">{{ t('quality.historyLoading') }}</div>
          <div v-else-if="qualityState.historyError" class="hint danger">{{ qualityState.historyError }}</div>
          <div v-else-if="!qualityState.history?.length" class="hint">{{ t('quality.historyEmpty') }}</div>
          <div v-else class="list">
            <div v-for="item in qualityState.history" :key="item.id" class="card">
              <div class="card-title">#{{ item.id }} · {{ item.qualityScore }}</div>
              <div class="card-sub">{{ formatTime(item.appliedAt || item.createdAt) }}</div>
              <div class="hint">
                {{ t('quality.nodes') }}：{{ item.nodeCount }} · {{ t('quality.edges') }}：{{ item.edgeCount }}
              </div>
              <button type="button" class="btn" @click="selectHistory(item)">{{ t('quality.viewReport') }}</button>
              <button type="button" class="btn danger" @click="rollbackToSnapshot(item.id)">{{ t('quality.rollback') }}</button>
            </div>
          </div>
        </div>

        <div class="label">
          <span class="hint">{{ t('quality.diffTitle') }}</span>
          <div class="row">
            <input type="number" v-model="diffIds.id1" placeholder="ID 1" class="input input-sm" />
            <input type="number" v-model="diffIds.id2" placeholder="ID 2" class="input input-sm" />
            <button type="button" class="btn" :disabled="diffState.loading" @click="compareDiff">{{ t('quality.compare') }}</button>
          </div>
          <div v-if="diffState.loading" class="hint">{{ t('common.loading') }}</div>
          <div v-else-if="diffState.error" class="hint danger">{{ diffState.error }}</div>
          <div v-else-if="diffState.result" class="card">
            <div class="card-sub">v{{ diffState.result.fromVersion }} → v{{ diffState.result.toVersion }}</div>
            <div class="hint">+{{ diffState.result.nodesAdded }} / -{{ diffState.result.nodesRemoved }} nodes</div>
            <div class="hint">+{{ diffState.result.edgesAdded }} / -{{ diffState.result.edgesRemoved }} edges</div>
            <div v-for="(e, i) in diffState.result.diffEntries" :key="i" class="hint">{{ e }}</div>
          </div>
        </div>
      </template>
    </div>
  </details>
</template>

