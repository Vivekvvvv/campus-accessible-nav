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
  <details data-testid="panel-favorites" class="panel">
    <summary>{{ t('favorites.title') }}</summary>
    <div class="panel-body">
      <input data-testid="favorite-name" class="input" :placeholder="t('favorites.namePlaceholder')" v-model="nameModel" />
      <input
        data-testid="favorite-group"
        class="input"
        :placeholder="t('favorites.groupPlaceholder')"
        v-model="groupModel"
      />
      <input data-testid="favorite-tags" class="input" :placeholder="t('favorites.tagsPlaceholder')" v-model="tagsModel" />

      <div class="row">
        <button data-testid="favorite-add-start" type="button" class="btn" @click="addFavorite('start')">
          {{ t('favorites.favoriteStart') }}
        </button>
      </div>

      <input data-testid="favorite-filter" class="input" :placeholder="t('favorites.filterPlaceholder')" v-model="filterModel" />

      <div class="row">
        <button data-testid="favorite-export" type="button" class="btn" @click="exportFavorites">
          {{ t('favorites.export') }}
        </button>
      </div>

      <textarea
        data-testid="favorite-import-text"
        class="textarea"
        rows="4"
        :placeholder="t('favorites.pasteJsonPlaceholder')"
        v-model="importModel"
      />
      <button data-testid="favorite-import" type="button" class="btn" @click="importFavorites">
        {{ t('favorites.import') }}
      </button>

      <div class="list">
        <div v-for="fav in filteredFavorites" :key="fav.id" data-testid="favorite-card" class="card">
          <div class="card-title">{{ fav.name }}</div>
          <div class="card-sub">{{ fav.group }}</div>
          <button data-testid="favorite-delete" type="button" class="btn danger" @click="removeFavorite(fav.id)">
            {{ t('common.delete') }}
          </button>
        </div>
      </div>
    </div>
  </details>
</template>

