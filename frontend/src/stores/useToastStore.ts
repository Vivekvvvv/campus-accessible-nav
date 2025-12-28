import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
  const text = ref('')

  function push(msg: string): void {
    text.value = msg
  }

  return {
    text,
    push,
  }
})
