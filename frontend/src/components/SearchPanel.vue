<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { highlightMatch } from '../utils/searchUtils'

const { t } = useI18n()

const props = defineProps({
  searchState: {
    type: Object,
    required: true,
  },
  searchResults: {
    type: Array,
    required: true,
  },
  categoryOptions: {
    type: Array,
    default: () => [],
  },
  searchHistory: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: '',
  },
  points: {
    type: Object,
    default: () => ({ start: null, end: null }),
  },
})

const emit = defineEmits([
  'update-query',
  'commit-query',
  'update-which',
  'toggle-category',
  'select-result',
  'select-waypoint',
  'select-history',
  'clear-history',
  'clear-point',
])

const query = computed({
  get: () => props.searchState.query,
  set: (value) => emit('update-query', value),
})

function highlightName(name) {
  return highlightMatch(name, props.searchState.query || '')
}

const categoryLabelMap = computed(() => {
  const map = new Map()
  for (const opt of props.categoryOptions || []) {
    map.set(opt.key, opt.label)
  }
  return map
})

function formatCategoryList(categories) {
  return (categories || [])
    .map((key) => categoryLabelMap.value.get(key) || key)
    .filter(Boolean)
}

const which = computed({
  get: () => props.searchState.which,
  set: (value) => emit('update-which', value),
})

const points = computed(() => props.points || {})

function commitQuery() {
  emit('commit-query')
}

function applyResult(item, target) {
  emit('select-result', { item, which: target })
}

function addWaypoint(item) {
  emit('select-waypoint', item)
}

function toggleCategory(key) {
  emit('toggle-category', key)
}

function selectHistory(item) {
  emit('select-history', item)
}

function clearHistory() {
  emit('clear-history')
}

function clearPoint(whichValue) {
  emit('clear-point', whichValue)
}
</script>

<template>
  <!-- 搜索面板：悬浮卡片式，支持起终点选择、分类过滤、历史记录 -->
  <details data-testid="panel-search" class="search-panel" open>
    <summary class="panel-summary">
      <span class="summary-icon">⌕</span>
      <span class="summary-label">{{ t('search.title') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">
      <!-- 起终点选择器 -->
      <div class="which-selector">
        <p class="selector-hint">{{ t('search.mapClickTarget') }}</p>
        <div class="selector-btns">
          <button
            data-testid="search-target-start"
            type="button"
            class="selector-btn"
            :class="{ active: which === 'start' }"
            @click="which = 'start'"
          >
            <span class="btn-dot start-dot"></span>
            {{ t('map.startPoint') }}
          </button>
          <button
            data-testid="search-target-end"
            type="button"
            class="selector-btn"
            :class="{ active: which === 'end' }"
            @click="which = 'end'"
          >
            <span class="btn-dot end-dot"></span>
            {{ t('map.endPoint') }}
          </button>
        </div>
      </div>

      <!-- 已选起终点卡片 -->
      <div class="points-card">
        <div class="point-row">
          <span class="point-dot start-dot"></span>
          <span class="point-name">{{ points.start?.name || t('search.notSet') }}</span>
          <button v-if="points.start" data-testid="clear-start" type="button" class="clear-btn" @click="clearPoint('start')">
            ✕
          </button>
        </div>
        <div class="point-divider"></div>
        <div class="point-row">
          <span class="point-dot end-dot"></span>
          <span class="point-name">{{ points.end?.name || t('search.notSet') }}</span>
          <button v-if="points.end" data-testid="clear-end" type="button" class="clear-btn" @click="clearPoint('end')">
            ✕
          </button>
        </div>
      </div>

      <!-- 搜索输入框 -->
      <div class="search-input-wrap">
        <span class="search-icon">⌕</span>
        <input
          data-testid="search-input"
          class="search-input"
          :value="query"
          :placeholder="t('search.placeholder')"
          @input="query = $event.target.value"
          @keydown.enter.prevent="commitQuery"
          @blur="commitQuery"
        />
        <span v-if="loading" class="input-spinner"></span>
      </div>

      <!-- 错误提示 -->
      <div v-if="error && !loading" class="error-tip">
        <span class="error-icon">!</span>
        {{ t('search.loadError') }}：{{ error }}
      </div>

      <!-- 分类过滤芯片 -->
      <div v-if="categoryOptions.length" class="filter-section">
        <p class="filter-label">{{ t('search.filterCategory') }}</p>
        <div class="chip-list">
          <button
            v-for="opt in categoryOptions"
            :key="opt.key"
            type="button"
            class="chip"
            :class="{ active: (searchState.categories || []).includes(opt.key) }"
            @click="toggleCategory(opt.key)"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- 搜索历史 -->
      <div v-if="searchHistory.length" class="history-section">
        <div class="section-header">
          <span class="section-title">{{ t('search.recentSearches') }}</span>
          <button type="button" class="text-btn" @click="clearHistory">{{ t('search.clearHistory') }}</button>
        </div>
        <div class="history-list">
          <button v-for="item in searchHistory" :key="item" type="button" class="history-chip" @click="selectHistory(item)">
            <span class="history-icon">↩</span>
            {{ item }}
          </button>
        </div>
      </div>

      <!-- 搜索结果列表 -->
      <div v-if="searchResults.length" class="results-list">
        <div v-for="it in searchResults" :key="it.id" data-testid="search-result-card" class="result-card">
          <div class="result-name" v-html="highlightName(it.name)"></div>
          <div class="result-tags" v-if="it.group || (it.categories && it.categories.length) || (it.tags && it.tags.length)">
            <span v-if="it.group" class="tag tag-group">{{ it.group }}</span>
            <span v-for="cat in formatCategoryList(it.categories)" :key="`cat-${it.id}-${cat}`" class="tag tag-category">
              {{ cat }}
            </span>
            <span v-for="(tag, idx) in (it.tags || []).slice(0, 4)" :key="`tag-${it.id}-${idx}`" class="tag">
              {{ tag }}
            </span>
          </div>
          <div class="result-actions">
            <button data-testid="search-result-set-start" type="button" class="action-btn start" @click="applyResult(it, 'start')">
              {{ t('map.setStart') }}
            </button>
            <button data-testid="search-result-set-end" type="button" class="action-btn end" @click="applyResult(it, 'end')">
              {{ t('map.setEnd') }}
            </button>
            <button data-testid="search-result-add-waypoint" type="button" class="action-btn waypoint" @click="addWaypoint(it)">
              {{ t('navigation.addWaypoint') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </details>
</template>

<style scoped>
/* ===== 面板整体 ===== */
.search-panel {
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

.summary-icon {
  font-size: 16px;
  color: var(--ui-accent, #0ea5a4);
}

.summary-label { flex: 1; }

.summary-arrow {
  width: 6px;
  height: 6px;
  border-right: 2px solid var(--ui-muted, #9ca3af);
  border-bottom: 2px solid var(--ui-muted, #9ca3af);
  transform: rotate(45deg);
  transition: transform 0.2s;
}

.search-panel[open] .summary-arrow {
  transform: rotate(-135deg);
}

/* ===== 面板内容区 ===== */
.panel-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

/* ===== 起终点选择器 ===== */
.which-selector {}

.selector-hint {
  font-size: 11px;
  color: var(--ui-muted, #9ca3af);
  margin-bottom: 8px;
}

.selector-btns {
  display: flex;
  gap: 8px;
}

.selector-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 10px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.selector-btn.active {
  border-color: var(--ui-accent, #0ea5a4);
  background: rgba(14, 165, 164, 0.06);
  color: var(--ui-accent, #0ea5a4);
}

.selector-btn:hover:not(.active) {
  border-color: var(--ui-accent, #0ea5a4);
  color: var(--ui-ink);
}

/* ===== 圆点标记 ===== */
.btn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.start-dot { background: #22c55e; }
.end-dot   { background: #ef4444; }

/* ===== 起终点信息卡片 ===== */
.points-card {
  background: var(--ui-bg, #f9fafb);
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 12px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.point-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.point-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.point-name {
  flex: 1;
  font-size: 13px;
  color: var(--ui-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.clear-btn {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: var(--ui-line, #e5e7eb);
  color: var(--ui-muted, #6b7280);
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s;
}

.clear-btn:hover {
  background: rgba(239, 68, 68, 0.15);
  color: #dc2626;
}

.point-divider {
  height: 1px;
  background: var(--ui-line, #e5e7eb);
  margin: 0 2px;
}

/* ===== 搜索输入框 ===== */
.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 10px;
  padding: 0 12px;
  background: var(--ui-bg, #fff);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.search-input-wrap:focus-within {
  border-color: var(--ui-accent, #0ea5a4);
  box-shadow: 0 0 0 3px rgba(14, 165, 164, 0.1);
}

.search-icon {
  font-size: 16px;
  color: var(--ui-muted, #9ca3af);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  padding: 10px 0;
  font-size: 14px;
  color: var(--ui-ink);
}

.search-input::placeholder { color: var(--ui-muted, #9ca3af); }

.input-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--ui-line, #e5e7eb);
  border-top-color: var(--ui-accent, #0ea5a4);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 错误提示 ===== */
.error-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 12px;
}

.error-icon {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(239, 68, 68, 0.15);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 800;
  flex-shrink: 0;
}

/* ===== 分类过滤 ===== */
.filter-section {}

.filter-label {
  font-size: 11px;
  color: var(--ui-muted, #9ca3af);
  margin-bottom: 8px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.chip.active {
  border-color: var(--ui-accent, #0ea5a4);
  background: rgba(14, 165, 164, 0.08);
  color: var(--ui-accent, #0ea5a4);
}

.chip:hover:not(.active) {
  border-color: var(--ui-accent, #0ea5a4);
  color: var(--ui-ink);
}

/* ===== 搜索历史 ===== */
.history-section {}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--ui-muted, #9ca3af);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.text-btn {
  border: none;
  background: none;
  font-size: 11px;
  color: var(--ui-muted, #9ca3af);
  cursor: pointer;
  padding: 0;
  transition: color 0.15s;
}

.text-btn:hover { color: #dc2626; }

.history-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.history-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.history-chip:hover {
  background: rgba(14, 165, 164, 0.06);
  border-color: var(--ui-accent, #0ea5a4);
}

.history-icon {
  font-size: 11px;
  color: var(--ui-muted, #9ca3af);
}

/* ===== 搜索结果 ===== */
.results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-card {
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 12px;
  padding: 12px 14px;
  background: var(--ui-bg, #fff);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: box-shadow 0.15s;
}

.result-card:hover {
  box-shadow: 0 2px 12px var(--ui-shadow, rgba(0,0,0,0.08));
}

.result-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--ui-ink);
  line-height: 1.4;
}

/* 搜索关键词高亮 */
.result-name :deep(mark) {
  background: rgba(14, 165, 164, 0.15);
  color: var(--ui-accent, #0ea5a4);
  border-radius: 3px;
  padding: 0 2px;
  font-style: normal;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--ui-line, #e5e7eb);
  color: var(--ui-muted, #6b7280);
}

.tag-group {
  background: rgba(14, 165, 164, 0.1);
  color: var(--ui-accent, #0ea5a4);
}

.tag-category {
  background: rgba(249, 115, 22, 0.08);
  color: var(--ui-accent-2, #f97316);
}

.result-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 5px 10px;
  border-radius: 8px;
  border: 1.5px solid;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn.start {
  border-color: rgba(34, 197, 94, 0.3);
  background: rgba(34, 197, 94, 0.06);
  color: #16a34a;
}

.action-btn.start:hover {
  background: rgba(34, 197, 94, 0.12);
}

.action-btn.end {
  border-color: rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.06);
  color: #dc2626;
}

.action-btn.end:hover {
  background: rgba(239, 68, 68, 0.12);
}

.action-btn.waypoint {
  border-color: rgba(14, 165, 164, 0.3);
  background: rgba(14, 165, 164, 0.06);
  color: var(--ui-accent, #0ea5a4);
}

.action-btn.waypoint:hover {
  background: rgba(14, 165, 164, 0.12);
}
</style>
