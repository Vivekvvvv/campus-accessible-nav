<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useObstacleStore } from '../stores/useObstacleStore'
import { useToastStore } from '../stores/useToastStore'

const { t } = useI18n()

const props = defineProps({
  enabled: {
    type: Boolean,
    default: false,
  },
})

const toast = useToastStore()
const obstacleStore = useObstacleStore()

const statusOptions = computed(() => [
  { key: 'PENDING', label: t('obstacle.statusPending') },
  { key: 'APPROVED', label: t('obstacle.statusApproved') },
  { key: 'REJECTED', label: t('obstacle.statusRejected') },
  { key: 'EXPIRED', label: t('obstacle.statusExpired') },
  { key: 'REVOKED', label: t('obstacle.statusRevoked') },
])

const reviewState = ref({
  activeId: null,
  durationMinutes: 30,
  reviewNote: '',
  verificationStatus: 'VERIFIED',
  overrideReason: '',
  submitting: false,
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

function confidenceClass(score) {
  if (score == null) return 'badge-neutral'
  if (score >= 0.7) return 'badge-green'
  if (score >= 0.4) return 'badge-yellow'
  return 'badge-red'
}

function confidenceLabel(score) {
  if (score == null) return '--'
  return (score * 100).toFixed(0) + '%'
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
  await obstacleStore.fetchAdminReports(obstacleStore.adminReportsState.status)
}

function openReview(item) {
  reviewState.value.activeId = item.id
  reviewState.value.durationMinutes = 30
  reviewState.value.reviewNote = ''
  reviewState.value.verificationStatus = 'VERIFIED'
  reviewState.value.overrideReason = item.reason || ''
}

function closeReview() {
  reviewState.value.activeId = null
}

async function approve(item) {
  if (reviewState.value.submitting) return
  reviewState.value.submitting = true
  try {
    await obstacleStore.approveReport(item.id, {
      durationMinutes: Number(reviewState.value.durationMinutes) || null,
      reviewNote: reviewState.value.reviewNote || null,
      verificationStatus: reviewState.value.verificationStatus || null,
      reason: reviewState.value.overrideReason || null,
    })
    toast.push(t('toast.obstacleApproved', { id: item.id }))
    closeReview()
    await refresh()
  } catch (e) {
    toast.push(e?.message || String(e))
  } finally {
    reviewState.value.submitting = false
  }
}

async function reject(item) {
  if (reviewState.value.submitting) return
  reviewState.value.submitting = true
  try {
    await obstacleStore.rejectReport(item.id, {
      reviewNote: reviewState.value.reviewNote || null,
      verificationStatus: reviewState.value.verificationStatus || null,
      reason: reviewState.value.overrideReason || null,
    })
    toast.push(t('toast.obstacleRejected', { id: item.id }))
    closeReview()
    await refresh()
  } catch (e) {
    toast.push(e?.message || String(e))
  } finally {
    reviewState.value.submitting = false
  }
}

async function revoke(item) {
  if (reviewState.value.submitting) return
  reviewState.value.submitting = true
  try {
    const note = window?.prompt ? window.prompt(t('obstacle.revokePrompt'), '') : ''
    await obstacleStore.revokeReport(item.id, {
      reviewNote: note || null,
    })
    toast.push(t('toast.obstacleRevoked', { id: item.id }))
    closeReview()
    await refresh()
  } catch (e) {
    toast.push(e?.message || String(e))
  } finally {
    reviewState.value.submitting = false
  }
}

onMounted(() => {
  if (props.enabled) refresh()
})
</script>

<template>
  <details data-testid="panel-obstacle-review" class="panel">
    <summary>{{ t('obstacle.adminReviewTitle') }}</summary>
    <div class="panel-body">
      <div v-if="!enabled" class="hint">{{ t('obstacle.adminLoginHint') }}</div>
      <template v-else>
        <div class="row">
          <label class="hint">{{ t('obstacle.status') }}</label>
          <select
            class="input"
            :value="obstacleStore.adminReportsState.status"
            @change="
              (e) => {
                obstacleStore.adminReportsState.status = e.target.value
                refresh()
              }
            "
          >
            <option v-for="opt in statusOptions" :key="opt.key" :value="opt.key">
              {{ opt.label }}
            </option>
          </select>
          <button type="button" class="btn" @click="refresh" :disabled="obstacleStore.adminReportsState.loading">
            {{ obstacleStore.adminReportsState.loading ? t('obstacle.refreshLoading') : t('common.refresh') }}
          </button>
        </div>

        <div v-if="obstacleStore.adminReportsState.error" class="hint danger">
          {{ obstacleStore.adminReportsState.error }}
        </div>
        <div v-else-if="!obstacleStore.adminReportsState.items.length" class="hint">{{ t('obstacle.recordsEmpty') }}</div>

        <div v-else class="list">
          <div v-for="r in obstacleStore.adminReportsState.items" :key="r.id" class="card" :class="{ 'card-escalated': r.escalated }">
            <div class="card-title">
              #{{ r.id }} · {{ statusLabel(r.status) }}
              <span v-if="r.confidenceScore != null" class="badge-confidence" :class="confidenceClass(r.confidenceScore)">
                {{ confidenceLabel(r.confidenceScore) }}
              </span>
            </div>
            <div class="card-sub">{{ formatTime(r.createdAt) }} · {{ t('obstacle.edgeId') }}: {{ r.edgeId ?? '--' }}</div>
            <div class="hint">{{ t('obstacle.typeLabel') }}：{{ typeLabel(r.type) }}</div>
            <div class="hint">{{ t('obstacle.submitter') }}：{{ r.submitterName || r.submitterId || '--' }}</div>
            <div v-if="r.reason" class="hint">{{ t('obstacle.description') }}：{{ r.reason }}</div>

            <div v-if="r.status === 'PENDING'" class="row">
              <button type="button" class="btn" @click="openReview(r)">{{ t('obstacle.reviewAction') }}</button>
            </div>

            <div v-else-if="r.status === 'APPROVED'" class="row">
              <button type="button" class="btn danger" :disabled="reviewState.submitting" @click="revoke(r)">
                {{ t('obstacle.revokeEffect') }}
              </button>
            </div>

            <div v-if="reviewState.activeId === r.id" class="review-form">
              <div class="row">
                <label class="hint">{{ t('obstacle.effectiveMinutes') }}</label>
                <input class="input" type="number" min="1" step="1" v-model="reviewState.durationMinutes" />
                <label class="hint">{{ t('obstacle.verification') }}</label>
                <select class="input" v-model="reviewState.verificationStatus">
                  <option value="VERIFIED">{{ t('obstacle.verified') }}</option>
                  <option value="NEED_FIELD_CHECK">{{ t('obstacle.needFieldCheck') }}</option>
                  <option value="UNVERIFIED">{{ t('obstacle.unverified') }}</option>
                </select>
              </div>

              <div class="row">
                <label class="hint">{{ t('obstacle.reasonEditable') }}</label>
                <input class="input grow" type="text" v-model="reviewState.overrideReason" />
              </div>

              <div class="row">
                <label class="hint">{{ t('obstacle.note') }}</label>
                <input class="input grow" type="text" v-model="reviewState.reviewNote" :placeholder="t('obstacle.noteOptional')" />
              </div>

              <div class="row">
                <button type="button" class="btn primary" :disabled="reviewState.submitting" @click="approve(r)">
                  {{ reviewState.submitting ? t('obstacle.submitLoading') : t('obstacle.approveAndApply') }}
                </button>
                <button type="button" class="btn danger" :disabled="reviewState.submitting" @click="reject(r)">
                  {{ t('obstacle.rejectAction') }}
                </button>
                <button type="button" class="btn" :disabled="reviewState.submitting" @click="closeReview">
                  {{ t('common.cancel') }}
                </button>
              </div>
            </div>
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

.grow {
  flex: 1;
  min-width: 180px;
}

.review-form {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(148, 163, 184, 0.7);
}

.btn.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: white;
}

.btn.danger {
  background: #dc2626;
  border-color: #dc2626;
  color: white;
}

.badge-confidence {
  display: inline-block;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  margin-left: 6px;
  vertical-align: middle;
  font-weight: 500;
}

.badge-green {
  background: #16a34a;
  color: #fff;
}

.badge-yellow {
  background: #ca8a04;
  color: #fff;
}

.badge-red {
  background: #dc2626;
  color: #fff;
}

.badge-neutral {
  background: #9ca3af;
  color: #fff;
}

.card-escalated {
  border-left: 3px solid #dc2626;
  background: rgba(220, 38, 38, 0.05);
}
</style>

