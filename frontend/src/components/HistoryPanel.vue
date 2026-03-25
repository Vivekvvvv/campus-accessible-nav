<script setup>
import { useI18n } from 'vue-i18n'
import { useHistoryStore } from '../stores/useHistoryStore'

const { t } = useI18n()
const historyStore = useHistoryStore()

const emit = defineEmits(['select-history'])

function formatDistance(meters) {
  if (meters < 1000) return `${Math.round(meters)}m`
  return `${(meters / 1000).toFixed(1)}km`
}

function formatDuration(seconds) {
  if (seconds < 60) return `${Math.round(seconds)}s`
  return `${Math.round(seconds / 60)}min`
}

function handleSelect(item) {
  emit('select-history', item)
}

function handleRemove(item) {
  historyStore.removeHistory(item.id)
}
</script>

<template>
  <!-- 历史面板：路线历史记录，支持过滤、复用、删除 -->
  <details data-testid="panel-history" class="hist-panel">
    <summary class="panel-summary">
      <span class="summary-icon">⊘</span>
      <span class="summary-label">{{ t('nav.history') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">
      <!-- 搜索过滤 -->
      <div class="search-wrap">
        <span class="search-icon">⌕</span>
        <input
          type="text"
          class="search-input"
          :placeholder="t('search.placeholder')"
          :value="historyStore.filterKeyword"
          @input="historyStore.setFilterKeyword($event.target.value)"
        />
      </div>

      <!-- 历史列表 -->
      <div v-if="historyStore.filteredHistory.length > 0" class="hist-list">
        <div
          v-for="item in historyStore.filteredHistory"
          :key="item.id"
          class="hist-item"
        >
          <div class="hist-main" @click="handleSelect(item)">
            <div class="hist-route">
              <span class="route-point start-point">{{ item.start.name }}</span>
              <span class="route-arrow">→</span>
              <span class="route-point end-point">{{ item.end.name }}</span>
            </div>
            <div class="hist-meta">
              <span class="meta-mode">{{ item.mode === 'wheel' ? t('route.wheelchair') : t('route.walk') }}</span>
              <span class="sep">·</span>
              <span>{{ formatDistance(item.distanceM) }}</span>
              <span class="sep">·</span>
              <span>{{ formatDuration(item.durationSec) }}</span>
              <span class="sep">·</span>
              <span class="meta-time">{{ historyStore.formatTime(item.timestamp) }}</span>
            </div>
          </div>
          <button
            type="button"
            class="remove-btn"
            :title="t('common.delete')"
            @click.stop="handleRemove(item)"
          >
            ✕
          </button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="hist-empty">
        {{ historyStore.filterKeyword ? t('search.noResults') : t('history.empty') }}
      </div>

      <!-- 清空按钮 -->
      <div v-if="historyStore.historyList.length > 0" class="hist-actions">
        <button type="button" class="clear-btn" @click="historyStore.clearHistory">
          {{ t('search.clearHistory') }}
        </button>
      </div>
    </div>
  </details>
</template>

<style scoped>
.hist-panel {
  background: var(--ui-card, #fff);
  border-radius: 16px;
  box-shadow: 0 4px 24px var(--ui-shadow, rgba(0,0,0,0.10));
  overflow: hidden;
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
}

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
.summary-icon { font-size: 14px; color: var(--ui-accent, #0ea5a4); }
.summary-label { flex: 1; }
.summary-arrow {
  width: 6px; height: 6px;
  border-right: 2px solid var(--ui-muted, #9ca3af);
  border-bottom: 2px solid var(--ui-muted, #9ca3af);
  transform: rotate(45deg); transition: transform 0.2s;
}
.hist-panel[open] .summary-arrow { transform: rotate(-135deg); }

.panel-body {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 12px 18px;
  gap: 10px;
}

/* ===== 搜索框 ===== */
.search-wrap {
  display: flex; align-items: center; gap: 6px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px;
  padding: 0 10px;
  background: var(--ui-bg, #f9fafb);
  transition: border-color 0.15s;
}
.search-wrap:focus-within { border-color: var(--ui-accent, #0ea5a4); }
.search-icon { font-size: 14px; color: var(--ui-muted, #9ca3af); flex-shrink: 0; }
.search-input {
  flex: 1; border: none; outline: none;
  background: transparent;
  padding: 8px 0;
  font-size: 13px; color: var(--ui-ink);
}
.search-input::placeholder { color: var(--ui-muted, #9ca3af); }

/* ===== 历史列表 ===== */
.hist-list { display: flex; flex-direction: column; gap: 6px; }

.hist-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 10px;
  background: var(--ui-bg, #fff);
  transition: all 0.15s;
}
.hist-item:hover {
  border-color: var(--ui-accent, #0ea5a4);
  box-shadow: 0 2px 8px var(--ui-shadow, rgba(0,0,0,0.06));
}

.hist-main { flex: 1; cursor: pointer; min-width: 0; }

.hist-route {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-ink);
  margin-bottom: 4px;
}
.route-point {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 38%;
}
.start-point { color: #16a34a; }
.end-point   { color: #dc2626; }
.route-arrow { color: var(--ui-muted, #9ca3af); font-size: 12px; flex-shrink: 0; }

.hist-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 3px;
  font-size: 11px;
  color: var(--ui-muted, #9ca3af);
}
.meta-mode {
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(14,165,164,0.08);
  color: var(--ui-accent, #0ea5a4);
  font-weight: 600;
}
.sep { opacity: 0.4; }
.meta-time { opacity: 0.8; }

.remove-btn {
  width: 22px; height: 22px; border-radius: 50%;
  border: none; background: var(--ui-line, #e5e7eb);
  color: var(--ui-muted, #6b7280); font-size: 11px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: all 0.15s;
}
.remove-btn:hover { background: rgba(239,68,68,0.15); color: #dc2626; }

/* ===== 空状态 ===== */
.hist-empty {
  text-align: center;
  padding: 20px 0;
  font-size: 13px;
  color: var(--ui-muted, #9ca3af);
}

/* ===== 清空按钮 ===== */
.hist-actions { display: flex; justify-content: flex-end; }
.clear-btn {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1.5px solid rgba(239,68,68,0.25);
  background: rgba(239,68,68,0.05);
  color: #dc2626;
  font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
}
.clear-btn:hover { background: rgba(239,68,68,0.12); }
</style>
