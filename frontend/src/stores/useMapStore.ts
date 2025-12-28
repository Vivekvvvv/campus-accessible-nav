import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface BaseMapState {
  current: string
  lastError: string | null
}

export const useMapStore = defineStore('map', () => {
  const campusOnly = ref(false)
  const baseMapState = ref<BaseMapState>({
    current: 'osm',
    lastError: null,
  })
  const currentFloor = ref<number>(1)

  function setCampusOnly(val: boolean): void {
    campusOnly.value = val
  }

  function setFloor(floor: number): void {
    currentFloor.value = floor
  }

  return {
    campusOnly,
    baseMapState,
    currentFloor,
    setCampusOnly,
    setFloor,
  }
})
