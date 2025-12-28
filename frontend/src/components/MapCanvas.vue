<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['ready'])
const container = ref(null)

onMounted(() => {
  emit('ready', container.value)
})

onBeforeUnmount(() => {
  emit('ready', null)
})
</script>

<template>
  <div v-if="!disabled" class="map-area">
    <div ref="container" class="map"></div>
    <slot />
  </div>
</template>

<style scoped>
.map-area {
  height: 100%;
  width: 100%;
  position: absolute;
  inset: 0;
}

.map {
  height: 100%;
  width: 100%;
}
</style>
