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
  <!-- 质量面板：图谱报告、历史快照、版本比较 -->
  <details data-testid="panel-quality" class="quality-panel">
    <summary class="panel-summary">
      <span class="summary-icon">⊹</span>
      <span class="summary-label">{{ t('quality.title') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">

      <!-- 未登录管理员提示 -->
      <div v-if="!hasAdmin" class="no-admin-hint">
        <span class="hint-icon">⊘</span>
        {{ t('quality.adminLoginHint') }}
      </div>

      <template v-else>
        <!-- 刷新操作行 -->
        <div class="action-row">
          <button type="button" class="action-btn" @click="$emit('refresh-report')">
            <span class="btn-icon">↻</span> {{ t('quality.refreshReport') }}
          </button>
          <button type="button" class="action-btn" @click="$emit('refresh-history')">
            <span class="btn-icon">↻</span> {{ t('quality.refreshHistory') }}
          </button>
        </div>

        <!-- 加载/错误状态 -->
        <div v-if="qualityState.loading" class="state-row loading-row">
          <span class="spin"></span> {{ t('quality.reportLoading') }}
        </div>
        <div v-else-if="qualityState.error" class="state-row error-row">
          <span class="error-icon">!</span> {{ qualityState.error }}
        </div>

        <!-- 当前报告卡片 -->
        <div v-if="qualityState.activeReport" class="report-card">
          <div class="report-header">
            <span class="report-title">{{ t('quality.currentDisplay', { label: qualityState.activeLabel || t('quality.currentReportFallback') }) }}</span>
            <span class="score-badge">{{ qualityState.activeReport.qualityScore }}</span>
          </div>
          <div class="report-stats">
            <div class="stat-item">
              <span class="stat-val">{{ qualityState.activeReport.nodeCount }}</span>
              <span class="stat-lbl">{{ t('quality.nodes') }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-val">{{ qualityState.activeReport.edgeCount }}</span>
              <span class="stat-lbl">{{ t('quality.edges') }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-val">{{ qualityState.activeReport.componentCount }}</span>
              <span class="stat-lbl">{{ t('quality.componentCount') }}</span>
            </div>
            <div class="stat-item warn">
              <span class="stat-val">{{ qualityState.activeReport.disconnectedNodes }}</span>
              <span class="stat-lbl">{{ t('quality.disconnected') }}</span>
            </div>
            <div class="stat-item warn">
              <span class="stat-val">{{ qualityState.activeReport.isolatedNodes }}</span>
              <span class="stat-lbl">{{ t('quality.isolated') }}</span>
            </div>
            <div class="stat-item warn">
              <span class="stat-val">{{ qualityState.activeReport.deadEndNodes }}</span>
              <span class="stat-lbl">{{ t('quality.deadEnds') }}</span>
            </div>
          </div>
          <!-- 建议列表 -->
          <div v-if="qualityState.activeReport.suggestions?.length" class="suggestions">
            <div v-for="item in qualityState.activeReport.suggestions" :key="item" class="suggestion-item">
              <span class="suggestion-dot">·</span> {{ item }}
            </div>
          </div>
          <!-- 导出按钮 -->
          <div class="export-row">
            <button type="button" class="export-btn" @click="$emit('export-report-json')">
              ↓ {{ t('quality.exportJson') }}
            </button>
            <button type="button" class="export-btn" @click="$emit('export-report-image')">
              ↓ {{ t('quality.exportImage') }}
            </button>
          </div>
        </div>

        <!-- 问题过滤器 -->
        <div v-if="issueFilterOptions.length" class="section">
          <div class="section-title">{{ t('quality.issueFilter') }}</div>
          <div class="filter-chips">
            <button
              v-for="opt in issueFilterOptions"
              :key="opt.key"
              type="button"
              class="filter-chip"
              :class="{ active: qualityState.issueFilter === opt.key }"
              @click="$emit('change-filter', opt.key)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <!-- 历史快照列表 -->
        <div class="section">
          <div class="section-title">{{ t('quality.historyTitle') }}</div>
          <div v-if="qualityState.historyLoading" class="state-row loading-row">
            <span class="spin"></span> {{ t('quality.historyLoading') }}
          </div>
          <div v-else-if="qualityState.historyError" class="state-row error-row">
            <span class="error-icon">!</span> {{ qualityState.historyError }}
          </div>
          <div v-else-if="!qualityState.history?.length" class="empty-hint">{{ t('quality.historyEmpty') }}</div>
          <div v-else class="snapshot-list">
            <div v-for="item in qualityState.history" :key="item.id" class="snapshot-card">
              <div class="snapshot-header">
                <span class="snapshot-id">#{{ item.id }}</span>
                <span class="snapshot-score">{{ item.qualityScore }}</span>
                <span class="snapshot-time">{{ formatTime(item.appliedAt || item.createdAt) }}</span>
              </div>
              <div class="snapshot-meta">
                {{ t('quality.nodes') }}：{{ item.nodeCount }} · {{ t('quality.edges') }}：{{ item.edgeCount }}
              </div>
              <div class="snapshot-actions">
                <button type="button" class="snap-btn" @click="selectHistory(item)">{{ t('quality.viewReport') }}</button>
                <button type="button" class="snap-btn danger" @click="rollbackToSnapshot(item.id)">{{ t('quality.rollback') }}</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 版本比较区 -->
        <div class="section">
          <div class="section-title">{{ t('quality.diffTitle') }}</div>
          <div class="diff-inputs">
            <input type="number" v-model="diffIds.id1" placeholder="ID 1" class="diff-input" />
            <span class="diff-sep">→</span>
            <input type="number" v-model="diffIds.id2" placeholder="ID 2" class="diff-input" />
            <button type="button" class="compare-btn" :disabled="diffState.loading" @click="compareDiff">{{ t('quality.compare') }}</button>
          </div>
          <div v-if="diffState.loading" class="state-row loading-row">
            <span class="spin"></span> {{ t('common.loading') }}
          </div>
          <div v-else-if="diffState.error" class="state-row error-row">
            <span class="error-icon">!</span> {{ diffState.error }}
          </div>
          <div v-else-if="diffState.result" class="diff-result">
            <div class="diff-version">v{{ diffState.result.fromVersion }} → v{{ diffState.result.toVersion }}</div>
            <div class="diff-stats">
              <span class="diff-add">+{{ diffState.result.nodesAdded }} nodes</span>
              <span class="diff-del">-{{ diffState.result.nodesRemoved }} nodes</span>
              <span class="diff-add">+{{ diffState.result.edgesAdded }} edges</span>
              <span class="diff-del">-{{ diffState.result.edgesRemoved }} edges</span>
            </div>
            <div v-for="(e, i) in diffState.result.diffEntries" :key="i" class="diff-entry">{{ e }}</div>
          </div>
        </div>
      </template>
    </div>
  </details>
</template>

<style scoped>
/* ===== 面板整体 ===== */
.quality-panel {
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
  transform: rotate(45deg); transition: transform 0.2s;
}
.quality-panel[open] .summary-arrow { transform: rotate(-135deg); }

/* ===== 内容区 ===== */
.panel-body {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 14px 18px;
  gap: 14px;
}

/* ===== 无权限提示 ===== */
.no-admin-hint {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 16px; border-radius: 10px;
  background: rgba(156,163,175,0.08);
  border: 1px dashed var(--ui-line, #e5e7eb);
  font-size: 13px; color: var(--ui-muted, #9ca3af);
}
.hint-icon { font-size: 16px; }

/* ===== 刷新按钮行 ===== */
.action-row { display: flex; gap: 8px; }
.action-btn {
  display: flex; align-items: center; gap: 5px;
  padding: 7px 14px; border-radius: 9px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
}
.action-btn:hover { border-color: var(--ui-accent, #0ea5a4); color: var(--ui-accent, #0ea5a4); }
.btn-icon { font-size: 13px; }

/* ===== 状态行 ===== */
.state-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border-radius: 8px;
  font-size: 12px;
}
.loading-row { background: rgba(14,165,164,0.05); color: var(--ui-muted, #9ca3af); }
.error-row {
  background: rgba(239,68,68,0.06); border: 1px solid rgba(239,68,68,0.2);
  color: #dc2626;
}
.error-icon {
  width: 15px; height: 15px; border-radius: 50%;
  background: rgba(239,68,68,0.15);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 800; flex-shrink: 0;
}

/* ===== 当前报告卡片 ===== */
.report-card {
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 12px;
  overflow: hidden;
}
.report-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px;
  background: rgba(14,165,164,0.05);
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.report-title {
  font-size: 13px; font-weight: 700;
  color: var(--ui-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.score-badge {
  padding: 2px 10px; border-radius: 999px;
  background: rgba(14,165,164,0.12);
  color: var(--ui-accent, #0ea5a4);
  font-size: 12px; font-weight: 700;
  white-space: nowrap; flex-shrink: 0; margin-left: 8px;
}
.report-stats {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 0;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.stat-item {
  display: flex; flex-direction: column; align-items: center;
  padding: 10px 6px;
  border-right: 1px solid var(--ui-line, #e5e7eb);
  gap: 2px;
}
.stat-item:nth-child(3n) { border-right: none; }
.stat-val { font-size: 16px; font-weight: 800; color: var(--ui-ink); }
.stat-lbl { font-size: 10px; color: var(--ui-muted, #9ca3af); text-transform: uppercase; letter-spacing: 0.04em; text-align: center; }
.stat-item.warn .stat-val { color: #f97316; }

.suggestions {
  padding: 10px 14px;
  display: flex; flex-direction: column; gap: 4px;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.suggestion-item { font-size: 12px; color: var(--ui-muted, #6b7280); display: flex; gap: 6px; }
.suggestion-dot { color: var(--ui-accent, #0ea5a4); flex-shrink: 0; }

.export-row {
  display: flex; gap: 8px;
  padding: 10px 14px;
}
.export-btn {
  padding: 6px 12px; border-radius: 8px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
}
.export-btn:hover { border-color: var(--ui-accent, #0ea5a4); color: var(--ui-accent, #0ea5a4); }

/* ===== 分区 ===== */
.section {
  display: flex; flex-direction: column; gap: 8px;
}
.section-title {
  font-size: 11px; font-weight: 700;
  color: var(--ui-muted, #9ca3af);
  text-transform: uppercase; letter-spacing: 0.06em;
}

/* ===== 过滤 chips ===== */
.filter-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.filter-chip {
  padding: 4px 12px; border-radius: 999px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s;
}
.filter-chip.active { border-color: var(--ui-accent, #0ea5a4); background: rgba(14,165,164,0.08); color: var(--ui-accent, #0ea5a4); }
.filter-chip:hover:not(.active) { border-color: var(--ui-accent, #0ea5a4); }

/* ===== 历史快照列表 ===== */
.snapshot-list { display: flex; flex-direction: column; gap: 8px; }
.snapshot-card {
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 10px;
  overflow: hidden;
}
.snapshot-header {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  background: var(--ui-bg, #f9fafb);
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
  flex-wrap: wrap;
}
.snapshot-id { font-size: 12px; font-weight: 700; color: var(--ui-ink); }
.snapshot-score {
  padding: 1px 8px; border-radius: 999px;
  background: rgba(14,165,164,0.08); color: var(--ui-accent, #0ea5a4);
  font-size: 11px; font-weight: 700;
}
.snapshot-time { font-size: 11px; color: var(--ui-muted, #9ca3af); margin-left: auto; }
.snapshot-meta { padding: 6px 12px; font-size: 12px; color: var(--ui-muted, #6b7280); }
.snapshot-actions {
  display: flex; gap: 6px;
  padding: 8px 12px;
  border-top: 1px solid var(--ui-line, #e5e7eb);
}
.snap-btn {
  padding: 5px 12px; border-radius: 7px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s;
}
.snap-btn:hover { border-color: var(--ui-accent, #0ea5a4); color: var(--ui-accent, #0ea5a4); }
.snap-btn.danger { border-color: rgba(239,68,68,0.3); color: #dc2626; background: rgba(239,68,68,0.04); }
.snap-btn.danger:hover { background: rgba(239,68,68,0.10); }

/* ===== 空状态 ===== */
.empty-hint { text-align: center; padding: 14px 0; font-size: 13px; color: var(--ui-muted, #9ca3af); }

/* ===== 版本比较 ===== */
.diff-inputs {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}
.diff-input {
  width: 80px; padding: 6px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 8px; background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink); font-size: 13px; outline: none;
  transition: border-color 0.15s; text-align: center;
}
.diff-input:focus { border-color: var(--ui-accent, #0ea5a4); }
.diff-sep { font-size: 14px; color: var(--ui-muted, #9ca3af); }
.compare-btn {
  padding: 6px 14px; border-radius: 8px;
  border: 1.5px solid rgba(14,165,164,0.3);
  background: rgba(14,165,164,0.08); color: var(--ui-accent, #0ea5a4);
  font-size: 12px; font-weight: 700; cursor: pointer; transition: all 0.15s;
}
.compare-btn:hover:not(:disabled) { background: rgba(14,165,164,0.15); }
.compare-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.diff-result {
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 10px; overflow: hidden;
}
.diff-version {
  padding: 8px 12px;
  background: var(--ui-bg, #f9fafb);
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
  font-size: 12px; font-weight: 700; color: var(--ui-ink);
}
.diff-stats {
  display: flex; flex-wrap: wrap; gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.diff-add { font-size: 12px; color: #16a34a; font-weight: 600; }
.diff-del { font-size: 12px; color: #dc2626; font-weight: 600; }
.diff-entry { padding: 4px 12px; font-size: 11px; color: var(--ui-muted, #6b7280); border-bottom: 1px solid var(--ui-line, #e5e7eb); }
.diff-entry:last-child { border-bottom: none; }

/* ===== 旋转动画 ===== */
.spin {
  width: 12px; height: 12px;
  border: 2px solid var(--ui-line, #e5e7eb);
  border-top-color: var(--ui-accent, #0ea5a4);
  border-radius: 50%;
  animation: spin 0.7s linear infinite; flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
.stat-lbl { font-size: 10px; color: var(--ui-muted, #9ca3