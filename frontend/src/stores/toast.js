import { defineStore } from 'pinia'
import { ref } from 'vue'

let seq = 0

export const useToastStore = defineStore('toast', () => {
  const items = ref([])

  function push(message, type = 'info', ttl = 3800) {
    const id = ++seq
    items.value.push({ id, message, type })
    setTimeout(() => dismiss(id), ttl)
    return id
  }

  function dismiss(id) {
    const i = items.value.findIndex((t) => t.id === id)
    if (i >= 0) items.value.splice(i, 1)
  }

  return {
    items,
    push,
    dismiss,
    success: (m) => push(m, 'success'),
    error: (m) => push(m, 'error', 6000),
    warn: (m) => push(m, 'warn', 5000),
    info: (m) => push(m, 'info'),
  }
})
