<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useObstacleStore } from '../stores/useObstacleStore'
import { useSessionStore } from '../stores/useSessionStore'

const { t } = useI18n()

const props = defineProps({
  enabled: {
    type: Boolean,
    default: false,
  },
})

const obstacleStore = useObstacleStore()
const sessionStore = useSessionStore()

const statusFilter = ref('')
const statusOptions = computed(() => [
  { key: '', label: t('obstacle.statusAll') },
  { key: 'PENDING', label: t('obstacle.statusPending') },
  { key: 'APPROVED', label: t('obstacle.statusApproved') },
  { key: 'REJECTED', label: t('obstacle.statusRejected') },
  { key: 'EXPIRED', label: t('obstacle.statusExpired') },
  { key: 'REVOKED', label: t('obstacle.statusRevoked') },
])

const title = computed(() => {
  const name = sessionStore.username || ''
  return name ? `${t('obstacle.myReports')} · ${name}` : t('obstacle.myReports')
})

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

function typeLabel(type) {
  const k = String(type || '').toLowerCase()
  if (k === 'blocked') return t('obstacle.typeBlocked')
  if (k === 'construction') return t('obstacle.typeConstruction')
  if (k === 'stairs') return t('obstacle.typeStairs')
  if (k === 'steep') return t('obstacle.typeSteep')
  if (k === 'narrow') return t('obstacle.typeNarrow')
  if (k === 'damage' || k === 'damaged') return t('obstacle.typeDamaged')
  if (k === 'other') return t('obstacle.typeOther')
  return type || '--'
}

function statusLabel(status) {
  const s = String(status || '').toUpperCase()
  if (s === 'PENDING') return t('obstacle.statusPending')
  if (s === 'APPROVED') return t('obstacle.statusApproved')
  if (s === 'REJECTED') return t('obstacle.statusRejected')
  if (s === 'EXPIRED') return t('obstacle.statusExpired')
  if (s === 'REVOKED') return t('obstacle.statusRevoked')
  return status || '--'
}

async function refresh() {
  if (!props.enabled) return
  await obstacleStore.fetchMyReports(statusFilter.value)
}

onMounted(() => {
  if (props.enabled) {
    refresh()
  }
})
</script>

<template>
  <details data-testid="panel-my-reports" class="panel">
    <summary>{{ title }}</summary>
    <div class="panel-body">
      <div v-if="!enabled" class="hint">{{ t('toast.loginRequired') }}</div>
      <template v-else>
        <div class="row">
          <label class="hint">{{ t('obstacle.status') }}</label>
          <select v-model="statusFilter" class="input" @change="refresh">
            <option v-for="opt in statusOptions" :key="opt.key" :value="opt.key">
              {{ opt.label }}
            </option>
          </select>
          <button type="button" class="btn" @click="refresh" :disabled="obstacleStore.myReportsState.loading">
            {{ obstacleStore.myReportsState.loading ? t('obstacle.refreshLoading') : t('common.refresh') }}
          </button>
        </div>

        <div v-if="obstacleStore.myReportsState.error" class="hint danger">
          {{ obstacleStore.myReportsState.error }}
        </div>

        <div v-else-if="!obstacleStore.myReportsState.items.length" class="hint">
          {{ t('obstacle.emptyMyReports') }}
        </div>

        <div v-else class="list">
          <div v-for="r in obstacleStore.myReportsState.items" :key="r.id" class="card">
            <div class="card-title">
              #{{ r.id }} · {{ statusLabel(r.status) }}
              <span v-if="r.confirmCount > 1" class="badge-confirm" :title="t('obstacle.confirmCountTip', { count: r.confirmCount })">
                {{ r.confirmCount }}x
              </span>
            </div>
            <div class="card-sub">{{ typeLabel(r.type) }} · {{ formatTime(r.createdAt) }}</div>
            <div class="hint">{{ t('obstacle.edgeId') }}：{{ r.edgeId ?? '--' }}</div>
            <div v-if="r.reason" class="hint">{{ t('obstacle.description') }}：{{ r.reason }}</div>
            <div v-if="r.reviewNote" class="hint">{{ t('obstacle.reviewNote') }}：{{ r.reviewNote }}</div>
          </div>
        </div>
      </template>
    </div>
  </details>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.input {
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
}

.badge-confirm {
  display: inline-block;
  background: #3b82f6;
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  margin-left: 6px;
  vertical-align: middle;
}
</style>

