<script setup>
defineProps({
  text: {
    type: String,
    default: '',
  },
})
</script>

<template>
  <div v-if="text" class="toast">{{ text }}</div>
</template>
