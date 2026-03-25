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
  <!-- 障碍审核面板：管理员专用 -->
  <details data-testid="panel-obstacle-review" class="review-panel">
    <summary class="panel-summary">
      <span class="summary-icon">⊛</span>
      <span class="summary-label">{{ t('obstacle.adminReviewTitle') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">
      <div v-if="!enabled" class="empty-hint">{{ t('obstacle.adminLoginHint') }}</div>
      <template v-else>
        <!-- 状态过滤 + 刷新 -->
        <div class="filter-row">
          <select
            class="filter-select"
            :value="obstacleStore.adminReportsState.status"
            @change="(e) => { obstacleStore.adminReportsState.status = e.target.value; refresh() }"
          >
            <option v-for="opt in statusOptions" :key="opt.key" :value="opt.key">{{ opt.label }}</option>
          </select>
          <button type="button" class="refresh-btn" @click="refresh" :disabled="obstacleStore.adminReportsState.loading">
            {{ obstacleStore.adminReportsState.loading ? t('obstacle.refreshLoading') : t('common.refresh') }}
          </button>
        </div>

        <!-- 错误/空状态 -->
        <div v-if="obstacleStore.adminReportsState.error" class="error-bar">
          <span class="error-icon">!</span> {{ obstacleStore.adminReportsState.error }}
        </div>
        <div v-else-if="!obstacleStore.adminReportsState.items.length" class="empty-hint">
          {{ t('obstacle.recordsEmpty') }}
        </div>

        <!-- 障碍报告列表 -->
        <div v-else class="report-list">
          <div
            v-for="r in obstacleStore.adminReportsState.items"
            :key="r.id"
            class="report-card"
            :class="{ escalated: r.escalated }"
          >
            <!-- 卡片头部 -->
            <div class="card-header">
              <div class="card-id">#{{ r.id }}</div>
              <span class="status-chip">{{ statusLabel(r.status) }}</span>
              <span v-if="r.confidenceScore != null" class="conf-badge" :class="confidenceClass(r.confidenceScore)">
                {{ confidenceLabel(r.confidenceScore) }}
              </span>
            </div>
            <!-- 元信息 -->
            <div class="card-meta">
              <span>{{ formatTime(r.createdAt) }}</span>
              <span class="sep">·</span>
              <span>{{ t('obstacle.typeLabel') }}: {{ typeLabel(r.type) }}</span>
              <span class="sep">·</span>
              <span>边 {{ r.edgeId ?? '--' }}</span>
            </div>
            <div class="card-meta">
              <span>{{ t('obstacle.submitter') }}: {{ r.submitterName || r.submitterId || '--' }}</span>
            </div>
            <div v-if="r.reason" class="card-reason">{{ r.reason }}</div>

            <!-- 操作按钮 -->
            <div class="card-actions">
              <button v-if="r.status === 'PENDING'" type="button" class="action-btn review-btn" @click="openReview(r)">
                {{ t('obstacle.reviewAction') }}
              </button>
              <button v-if="r.status === 'APPROVED'" type="button" class="action-btn danger-btn" :disabled="reviewState.submitting" @click="revoke(r)">
                {{ t('obstacle.revokeEffect') }}
              </button>
            </div>

            <!-- 审核展开表单 -->
            <div v-if="reviewState.activeId === r.id" class="review-form">
              <div class="review-fields">
                <div class="field-row">
                  <label class="field-label">{{ t('obstacle.effectiveMinutes') }}</label>
                  <input class="field-input" type="number" min="1" step="1" v-model="reviewState.durationMinutes" />
                </div>
                <div class="field-row">
                  <label class="field-label">{{ t('obstacle.verification') }}</label>
                  <select class="field-select" v-model="reviewState.verificationStatus">
                    <option value="VERIFIED">{{ t('obstacle.verified') }}</option>
                    <option value="NEED_FIELD_CHECK">{{ t('obstacle.needFieldCheck') }}</option>
                    <option value="UNVERIFIED">{{ t('obstacle.unverified') }}</option>
                  </select>
                </div>
                <div class="field-row">
                  <label class="field-label">{{ t('obstacle.reasonEditable') }}</label>
                  <input class="field-input grow" type="text" v-model="reviewState.overrideReason" />
                </div>
                <div class="field-row">
                  <label class="field-label">{{ t('obstacle.note') }}</label>
                  <input class="field-input grow" type="text" v-model="reviewState.reviewNote" :placeholder="t('obstacle.noteOptional')" />
                </div>
              </div>
              <div class="review-actions">
                <button type="button" class="action-btn approve-btn" :disabled="reviewState.submitting" @click="approve(r)">
                  {{ reviewState.submitting ? t('obstacle.submitLoading') : t('obstacle.approveAndApply') }}
                </button>
                <button type="button" class="action-btn danger-btn" :disabled="reviewState.submitting" @click="reject(r)">
                  {{ t('obstacle.rejectAction') }}
                </button>
                <button type="button" class="action-btn cancel-btn" :disabled="reviewState.submitting" @click="closeReview">
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
.review-panel {
  background: var(--ui-card, #fff);
  border-radius: 16px;
  box-shadow: 0 4px 24px var(--ui-shadow, rgba(0,0,0,0.10));
  overflow: hidden;
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
}
.panel-summary {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 18px; cursor: pointer; user-select: none;
  list-style: none; font-size: 14px; font-weight: 700; color: var(--ui-ink);
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.panel-summary::-webkit-details-marker { display: none; }
.summary-icon { font-size: 15px; color: #f97316; }
.summary-label { flex: 1; }
.summary-arrow {
  width: 6px; height: 6px;
  border-right: 2px solid var(--ui-muted, #9ca3af);
  border-bottom: 2px solid var(--ui-muted, #9ca3af);
  transform: rotate(45deg); transition: transform 0.2s;
}
.review-panel[open] .summary-arrow { transform: rotate(-135deg); }

.panel-body { display: flex; flex-direction: column; gap: 10px; padding: 14px 16px; }

.filter-row { display: flex; gap: 8px; align-items: center; }
.filter-select {
  flex: 1; padding: 7px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px; background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink); font-size: 13px; outline: none; cursor: pointer;
}
.refresh-btn {
  padding: 7px 14px; border-radius: 9px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb); color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s; white-space: nowrap;
}
.refresh-btn:hover:not(:disabled) { border-color: var(--ui-accent, #0ea5a4); color: var(--ui-accent, #0ea5a4); }
.refresh-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.error-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border-radius: 8px;
  background: rgba(239,68,68,0.06); border: 1px solid rgba(239,68,68,0.2);
  color: #dc2626; font-size: 12px;
}
.error-icon {
  width: 16px; height: 16px; border-radius: 50%;
  background: rgba(239,68,68,0.15);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 800; flex-shrink: 0;
}
.empty-hint { font-size: 13px; color: var(--ui-muted, #9ca3af); text-align: center; padding: 8px 0; }

.report-list { display: flex; flex-direction: column; gap: 8px; }
.report-card {
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 12px; padding: 12px 14px;
  background: var(--ui-bg, #fff);
  display: flex; flex-direction: column; gap: 6px;
  transition: box-shadow 0.15s;
}
.report-card:hover { box-shadow: 0 2px 10px var(--ui-shadow, rgba(0,0,0,0.07)); }
.report-card.escalated { border-left: 3px solid #dc2626; background: rgba(220,38,38,0.03); }

.card-header { display: flex; align-items: center; gap: 8px; }
.card-id { font-size: 13px; font-weight: 800; color: var(--ui-ink); }

.status-chip {
  padding: 2px 8px; border-radius: 999px;
  font-size: 11px; font-weight: 700;
  background: var(--ui-line, #e5e7eb); color: var(--ui-muted, #6b7280);
}

.conf-badge {
  display: inline-block;
  font-size: 11px; padding: 1px 6px;
  border-radius: 999px; font-weight: 700;
}
.badge-green { background: #16a34a; color: #fff; }
.badge-yellow { background: #ca8a04; color: #fff; }
.badge-red { background: #dc2626; color: #fff; }
.badge-neutral { background: #9ca3af; color: #fff; }

.card-meta {
  display: flex; align-items: center; flex-wrap: wrap; gap: 4px;
  font-size: 11px; color: var(--ui-muted, #9ca3af);
}
.sep { opacity: 0.4; }
.card-reason { font-size: 12px; color: var(--ui-ink); font-style: italic; }

.card-actions { display: flex; gap: 6px; }

.action-btn {
  padding: 6px 12px; border-radius: 8px;
  font-size: 12px; font-weight: 600;
  cursor: pointer; border: 1.5px solid; transition: all 0.15s;
}
.action-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.review-btn { border-color: rgba(14,165,164,0.3); background: rgba(14,165,164,0.08); color: var(--ui-accent, #0ea5a4); }
.review-btn:hover { background: rgba(14,165,164,0.15); }
.approve-btn { border-color: rgba(34,197,94,0.3); background: rgba(34,197,94,0.08); color: #16a34a; }
.approve-btn:hover:not(:disabled) { background: rgba(34,197,94,0.15); }
.danger-btn { border-color: rgba(239,68,68,0.3); background: rgba(239,68,68,0.08); color: #dc2626; }
.danger-btn:hover:not(:disabled) { background: rgba(239,68,68,0.15); }
.cancel-btn { border-color: var(--ui-line,#e5e7eb); background: var(--ui-bg,#f9fafb); color: var(--ui-muted,#6b7280); }
.cancel-btn:hover { border-color: var(--ui-muted); color: var(--ui-ink); }

.review-form {
  margin-top: 8px; padding-top: 10px;
  border-top: 1px dashed var(--ui-line, #e5e7eb);
  display: flex; flex-direction: column; gap: 8px;
}
.review-fields { display: flex; flex-direction: column; gap: 6px; }
.field-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.field-label { font-size: 11px; color: var(--ui-muted, #9ca3af); white-space: nowrap; }
.field-input {
  padding: 6px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 8px; background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink); font-size: 13px; outline: none; transition: border-color 0.15s;
}
.field-input:focus { border-color: var(--ui-accent, #0ea5a4); }
.field-input.grow { flex: 1; }
.field-select {
  padding: 6px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 8px; background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink); font-size: 13px; outline: none; cursor: pointer;
}
.review-actions { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
