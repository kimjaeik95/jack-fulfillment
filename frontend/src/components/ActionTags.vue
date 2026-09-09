<script setup>
import { codeColor, codeLabel } from '@/api/codes.js'

defineProps({
  actions: { type: Array, default: () => [] },
})
</script>

<template>
  <span v-if="actions?.length" class="nowrap">
    <span
      v-for="a in actions"
      :key="a"
      class="action-tag"
      :class="`badge-${codeColor('PERM_ACTION', a)}`"
      :title="codeLabel('PERM_ACTION', a)"
      >{{ a }}</span
    >
  </span>
  <span v-else class="dim">-</span>
</template>
