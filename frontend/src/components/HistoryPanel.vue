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
  <details data-testid="panel-history" class="panel">
    <summary>{{ t('nav.history') }}</summary>
    <div class="panel-body">
      <!-- 过滤搜索 -->
      <input
        type="text"
        class="input"
        :placeholder="t('search.placeholder')"
        :value="historyStore.filterKeyword"
        @input="historyStore.setFilterKeyword($event.target.value)"
      />

      <!-- 历史列表 -->
      <div v-if="historyStore.filteredHistory.length > 0" class="history-items">
        <div
          v-for="item in historyStore.filteredHistory"
          :key="item.id"
          class="history-item"
        >
          <div class="history-main" @click="handleSelect(item)">
            <div class="history-route">
              <span class="history-point start">{{ item.start.name }}</span>
              <span class="history-arrow">→</span>
              <span class="history-point end">{{ item.end.name }}</span>
            </div>
            <div class="history-meta">
              <span class="history-mode">{{ item.mode === 'wheel' ? t('route.wheelchair') : t('route.walk') }}</span>
              <span class="history-sep">·</span>
              <span>{{ formatDistance(item.distanceM) }}</span>
              <span class="history-sep">·</span>
              <span>{{ formatDuration(item.durationSec) }}</span>
              <span class="history-sep">·</span>
              <span class="history-time">{{ historyStore.formatTime(item.timestamp) }}</span>
            </div>
          </div>
          <button
            type="button"
            class="btn history-remove"
            :title="t('common.delete')"
            @click.stop="handleRemove(item)"
          >
            ×
          </button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="history-empty">
        {{ historyStore.filterKeyword ? t('search.noResults') : t('history.empty') }}
      </div>

      <!-- 清空按钮 -->
      <div v-if="historyStore.historyList.length > 0" class="history-actions">
        <button type="button" class="btn danger" @click="historyStore.clearHistory">
          {{ t('search.clearHistory') }}
        </button>
      </div>
    </div>
  </details>
</template>

<style scoped>
.history-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--ui-line);
  border-radius: 10px;
  background: var(--ui-btn-bg);
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.history-item:hover {
  background: var(--ui-btn-hover);
  border-color: var(--ui-accent);
}

.history-main {
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.history-route {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ui-ink);
}

.history-point {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-point.start {
  color: #16a34a;
}

.history-point.end {
  color: #dc2626;
}

.history-arrow {
  color: var(--ui-muted);
  flex-shrink: 0;
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--ui-muted);
  margin-top: 4px;
}

.history-sep {
  color: var(--ui-line);
}

.history-mode {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(14, 165, 164, 0.1);
  color: var(--ui-accent);
}

.history-time {
  opacity: 0.8;
}

.history-remove {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  padding: 0;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;
  opacity: 0.6;
}

.history-remove:hover {
  opacity: 1;
}

.history-empty {
  text-align: center;
  padding: 20px;
  color: var(--ui-muted);
  font-size: 13px;
}

.history-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
