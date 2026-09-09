<script setup>
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()
const icons = { success: '✓', error: '⛔', warn: '⚠', info: 'ℹ' }
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <div v-for="t in toast.items" :key="t.id" class="toast" :class="`toast-${t.type}`">
      <span>{{ icons[t.type] }}</span>
      <span style="white-space: pre-line">{{ t.message }}</span>
      <button class="toast-close" title="닫기" @click="toast.dismiss(t.id)">×</button>
    </div>
  </div>
</template>
