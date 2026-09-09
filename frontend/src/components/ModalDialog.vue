<script setup>
import { onMounted, onUnmounted } from 'vue'

defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  size: { type: String, default: 'normal' }, // narrow | normal | wide
  closeOnBackdrop: { type: Boolean, default: true },
})

const emit = defineEmits(['close'])

function onKey(e) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="closeOnBackdrop && emit('close')">
      <div
        class="modal"
        :class="{ wide: size === 'wide', narrow: size === 'narrow' }"
        role="dialog"
        aria-modal="true"
      >
        <div class="modal-head">
          <div>
            <div class="modal-title">{{ title }}</div>
            <div v-if="subtitle" class="modal-sub">{{ subtitle }}</div>
          </div>
          <div class="spacer"></div>
          <button class="btn btn-ghost btn-icon" title="닫기 (Esc)" @click="emit('close')">×</button>
        </div>

        <div class="modal-body">
          <slot />
        </div>

        <div v-if="$slots.footer" class="modal-foot">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
