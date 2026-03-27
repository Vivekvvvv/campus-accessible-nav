<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  favoriteState: {
    type: Object,
    required: true,
  },
  filteredFavorites: {
    type: Array,
    required: true,
  },
})

const emit = defineEmits([
  'update-field',
  'add-favorite',
  'export-favorites',
  'import-favorites',
  'remove-favorite',
])

const nameModel = computed({
  get: () => props.favoriteState.name,
  set: (value) => emit('update-field', { key: 'name', value }),
})

const groupModel = computed({
  get: () => props.favoriteState.group,
  set: (value) => emit('update-field', { key: 'group', value }),
})

const tagsModel = computed({
  get: () => props.favoriteState.tagsText,
  set: (value) => emit('update-field', { key: 'tagsText', value }),
})

const filterModel = computed({
  get: () => props.favoriteState.filterText,
  set: (value) => emit('update-field', { key: 'filterText', value }),
})

const importModel = computed({
  get: () => props.favoriteState.importText,
  set: (value) => emit('update-field', { key: 'importText', value }),
})

function addFavorite(which) {
  emit('add-favorite', which)
}

function exportFavorites() {
  emit('export-favorites')
}

function importFavorites() {
  emit('import-favorites')
}

function removeFavorite(id) {
  emit('remove-favorite', id)
}
</script>

<template>
  <!-- 收藏面板：添加/过滤/导入导出/删除收藏地点 -->
  <details data-testid="panel-favorites" class="fav-panel">
    <summary class="panel-summary">
      <span class="summary-icon">★</span>
      <span class="summary-label">{{ t('favorites.title') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">
<!-- 添加收藏表单 -->
      <div class="fav-form">
        <div class="form-title">添加收藏</div>
        <input data-testid="favorite-name" class="fav-input" :placeholder="t('favorites.namePlaceholder')" v-model="nameModel" />
        <input data-testid="favorite-group" class="fav-input" :placeholder="t('favorites.groupPlaceholder')" v-model="groupModel" />
        <input data-testid="favorite-tags" class="fav-input" :placeholder="t('favorites.tagsPlaceholder')" v-model="tagsModel" />
        <button data-testid="favorite-add-start" type="button" class="add-btn" @click="addFavorite('start')">
          <span>＋</span> {{ t('favorites.favoriteStart') }}
        </button>
      </div>

      <!-- 过滤 & 导出 -->
      <div class="fav-toolbar">
        <div class="search-wrap">
          <span class="search-icon">⌕</span>
          <input data-testid="favorite-filter" class="filter-input" :placeholder="t('favorites.filterPlaceholder')" v-model="filterModel" />
        </div>
        <button data-testid="favorite-export" type="button" class="tool-btn" @click="exportFavorites">
          ↑ {{ t('favorites.export') }}
        </button>
      </div>

      <!-- 收藏列表 -->
      <div class="fav-list">
        <div v-for="fav in filteredFavorites" :key="fav.id" data-testid="favorite-card" class="fav-card">
          <div class="fav-info">
            <div class="fav-name">{{ fav.name }}</div>
            <div v-if="fav.group" class="fav-group">{{ fav.group }}</div>
          </div>
          <button data-testid="favorite-delete" type="button" class="del-btn" @click="removeFavorite(fav.id)">✕</button>
        </div>
        <div v-if="filteredFavorites.length === 0" class="fav-empty">暂无收藏</div>
      </div>

      <!-- 导入区 -->
      <details class="import-section">
        <summary class="import-summary">↓ {{ t('favorites.import') }}</summary>
        <textarea
          data-testid="favorite-import-text"
          class="import-textarea"
          rows="3"
          :placeholder="t('favorites.pasteJsonPlaceholder')"
          v-model="importModel"
        />
        <button data-testid="favorite-import" type="button" class="add-btn" @click="importFavorites">
          {{ t('favorites.import') }}
        </button>
      </details>
    </div>
  </details>
</template>

<style scoped>
.fav-panel {
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
.summary-icon { font-size: 14px; color: var(--ui-accent-2, #f97316); }
.summary-label { flex: 1; }
.summary-arrow {
  width: 6px; height: 6px;
  border-right: 2px solid var(--ui-muted, #9ca3af);
  border-bottom: 2px solid var(--ui-muted, #9ca3af);
  transform: rotate(45deg); transition: transform 0.2s;
}
.fav-panel[open] .summary-arrow { transform: rotate(-135deg); }

.panel-body {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 8px 0;
}

/* ===== 添加表单 ===== */
.fav-form {
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.form-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--ui-muted, #9ca3af);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 2px;
}
.fav-input {
  padding: 8px 12px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}
.fav-input:focus { border-color: var(--ui-accent-2, #f97316); }
.fav-input::placeholder { color: var(--ui-muted, #9ca3af); }
.add-btn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 14px;
  border-radius: 9px;
  border: 1.5px solid rgba(249,115,22,0.3);
  background: rgba(249,115,22,0.08);
  color: var(--ui-accent-2, #f97316);
  font-size: 13px; font-weight: 700;
  cursor: pointer; transition: all 0.15s;
}
.add-btn:hover { background: rgba(249,115,22,0.15); }

/* ===== 工具栏 ===== */
.fav-toolbar {
  padding: 10px 18px;
  display: flex;
  gap: 8px;
  align-items: center;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.search-wrap {
  flex: 1;
  display: flex; align-items: center; gap: 6px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px;
  padding: 0 10px;
  background: var(--ui-bg, #f9fafb);
  transition: border-color 0.15s;
}
.search-wrap:focus-within { border-color: var(--ui-accent-2, #f97316); }
.search-icon { font-size: 14px; color: var(--ui-muted, #9ca3af); flex-shrink: 0; }
.filter-input {
  flex: 1; border: none; outline: none;
  background: transparent;
  padding: 7px 0;
  font-size: 13px; color: var(--ui-ink);
}
.filter-input::placeholder { color: var(--ui-muted, #9ca3af); }
.tool-btn {
  padding: 7px 12px;
  border-radius: 9px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.15s; white-space: nowrap;
}
.tool-btn:hover {
  border-color: var(--ui-accent-2, #f97316);
  color: var(--ui-accent-2, #f97316);
}

/* ===== 收藏列表 ===== */
.fav-list {
  padding: 10px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.fav-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #fff);
  transition: box-shadow 0.15s;
}
.fav-card:hover { box-shadow: 0 2px 8px var(--ui-shadow, rgba(0,0,0,0.06)); }
.fav-info { flex: 1; min-width: 0; }
.fav-name {
  font-size: 13px; font-weight: 700;
  color: var(--ui-ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.fav-group {
  font-size: 11px; color: var(--ui-muted, #9ca3af); margin-top: 2px;
}
.del-btn {
  width: 22px; height: 22px; border-radius: 50%;
  border: none; background: var(--ui-line, #e5e7eb);
  color: var(--ui-muted, #6b7280); font-size: 11px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: all 0.15s;
}
.del-btn:hover { background: rgba(239,68,68,0.15); color: #dc2626; }
.fav-empty {
  text-align: center; padding: 16px 0;
  font-size: 13px; color: var(--ui-muted, #9ca3af);
}

/* ===== 导入区 ===== */
.import-section { padding: 10px 18px; }
.import-summary {
  font-size: 12px; color: var(--ui-muted, #9ca3af);
  cursor: pointer; user-select: none; list-style: none;
  padding: 4px 0;
}
.import-summary::-webkit-details-marker { display: none; }
.import-textarea {
  width: 100%; margin-top: 8px;
  padding: 10px 12px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 12px;
  font-family: monospace;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
}
.import-textarea:focus { border-color: var(--ui-accent-2, #f97316); }
</style>
