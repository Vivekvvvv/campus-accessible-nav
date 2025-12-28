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
  <details data-testid="panel-search" class="panel" open>
    <summary>{{ t('search.title') }}</summary>
    <div class="panel-body">
      <div class="label">
        <span class="hint">{{ t('search.mapClickTarget') }}</span>
        <div class="row">
          <button
            data-testid="search-target-start"
            type="button"
            class="btn"
            :class="{ active: which === 'start' }"
            @click="which = 'start'"
          >
            {{ t('map.startPoint') }}
          </button>
          <button
            data-testid="search-target-end"
            type="button"
            class="btn"
            :class="{ active: which === 'end' }"
            @click="which = 'end'"
          >
            {{ t('map.endPoint') }}
          </button>
        </div>
      </div>

      <div class="card">
        <div class="card-title">{{ t('search.selectedPoints') }}</div>
        <div class="row" style="justify-content: space-between; align-items: center;">
          <span class="hint" style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            {{ t('map.startPoint') }}：{{ points.start?.name || t('search.notSet') }}
          </span>
          <button v-if="points.start" data-testid="clear-start" type="button" class="btn danger" @click="clearPoint('start')">
            {{ t('common.clear') }}
          </button>
        </div>
        <div class="row" style="justify-content: space-between; align-items: center;">
          <span class="hint" style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            {{ t('map.endPoint') }}：{{ points.end?.name || t('search.notSet') }}
          </span>
          <button v-if="points.end" data-testid="clear-end" type="button" class="btn danger" @click="clearPoint('end')">
            {{ t('common.clear') }}
          </button>
        </div>
      </div>

      <input
        data-testid="search-input"
        class="input"
        :value="query"
        :placeholder="t('search.placeholder')"
        @input="query = $event.target.value"
        @keydown.enter.prevent="commitQuery"
        @blur="commitQuery"
      />

      <div v-if="loading" class="hint">{{ t('search.loadingCampusData') }}</div>
      <div v-else-if="error" class="hint danger">{{ t('search.loadError') }}：{{ error }}</div>

      <div v-if="categoryOptions.length" class="label">
        <span class="hint">{{ t('search.filterCategory') }}</span>
        <div class="row">
          <button
            v-for="opt in categoryOptions"
            :key="opt.key"
            type="button"
            class="btn"
            :class="{ active: (searchState.categories || []).includes(opt.key) }"
            @click="toggleCategory(opt.key)"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <div v-if="searchHistory.length" class="card">
        <div class="card-title">{{ t('search.recentSearches') }}</div>
        <div class="row">
          <button v-for="item in searchHistory" :key="item" type="button" class="btn" @click="selectHistory(item)">
            {{ item }}
          </button>
        </div>
        <button type="button" class="btn danger" @click="clearHistory">{{ t('search.clearHistory') }}</button>
      </div>

      <div v-if="searchResults.length" class="list">
        <div v-for="it in searchResults" :key="it.id" data-testid="search-result-card" class="card">
          <div class="card-title" v-html="highlightName(it.name)"></div>
          <div class="tag-list" v-if="it.group || (it.categories && it.categories.length) || (it.tags && it.tags.length)">
            <span v-if="it.group" class="tag tag-group">{{ it.group }}</span>
            <span v-for="cat in formatCategoryList(it.categories)" :key="`cat-${it.id}-${cat}`" class="tag tag-category">
              {{ cat }}
            </span>
            <span v-for="(tag, idx) in (it.tags || []).slice(0, 4)" :key="`tag-${it.id}-${idx}`" class="tag">
              {{ tag }}
            </span>
          </div>
          <div class="row">
            <button data-testid="search-result-set-start" type="button" class="btn" @click="applyResult(it, 'start')">
              {{ t('map.setStart') }}
            </button>
            <button data-testid="search-result-set-end" type="button" class="btn" @click="applyResult(it, 'end')">
              {{ t('map.setEnd') }}
            </button>
            <button data-testid="search-result-add-waypoint" type="button" class="btn" @click="addWaypoint(it)">
              {{ t('navigation.addWaypoint') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </details>
</template>
