<script setup>
import { computed } from 'vue'
import { codeColor, codeLabel } from '@/api/codes.js'

const props = defineProps({
  group: { type: String, required: true },
  code: { type: [String, Number], default: null },
  plain: { type: Boolean, default: false },
})

const label = computed(() => codeLabel(props.group, props.code))
const color = computed(() => codeColor(props.group, props.code))
</script>

<template>
  <span v-if="code !== null && code !== ''" class="badge" :class="[`badge-${color}`, { plain }]">{{ label }}</span>
  <span v-else class="dim">-</span>
</template>
