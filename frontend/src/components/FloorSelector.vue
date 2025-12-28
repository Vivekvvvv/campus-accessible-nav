<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMapStore } from '../stores/useMapStore'

const { t } = useI18n()
const mapStore = useMapStore()

const props = defineProps({
  minLevel: {
    type: Number,
    default: 1,
  },
  maxLevel: {
    type: Number,
    default: 1,
  },
  visible: {
    type: Boolean,
    default: false,
  },
})

const floors = computed(() => {
  const list = []
  for (let i = props.minLevel; i <= props.maxLevel; i++) {
    list.push(i)
  }
  return list
})
</script>

<template>
  <div v-if="visible && floors.length > 1" class="floor-selector">
    <label class="floor-label">{{ t('map.floor') }}</label>
    <select
      :value="mapStore.currentFloor"
      class="floor-select"
      @change="mapStore.setFloor(Number(($event.target).value))"
    >
      <option v-for="f in floors" :key="f" :value="f">
        {{ f }}F
      </option>
    </select>
  </div>
</template>

<style scoped>
.floor-selector {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
  font-size: 13px;
}

.floor-label {
  font-weight: 500;
}

.floor-select {
  padding: 2px 6px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
}
</style>
