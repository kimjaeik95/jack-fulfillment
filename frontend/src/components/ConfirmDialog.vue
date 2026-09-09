<script setup>
import ModalDialog from './ModalDialog.vue'

defineProps({
  title: { type: String, default: '확인' },
  message: { type: String, default: '' },
  detail: { type: String, default: '' },
  confirmLabel: { type: String, default: '확인' },
  danger: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['confirm', 'cancel'])
</script>

<template>
  <ModalDialog :title="title" size="narrow" @close="emit('cancel')">
    <div class="alert" :class="danger ? 'alert-danger' : 'alert-info'">
      <span class="alert-icon">{{ danger ? '⚠' : 'ℹ' }}</span>
      <span style="white-space: pre-line">{{ message }}</span>
    </div>
    <p v-if="detail" class="small dim mt-2" style="white-space: pre-line">{{ detail }}</p>

    <template #footer>
      <button class="btn" :disabled="busy" @click="emit('cancel')">취소</button>
      <button
        class="btn"
        :class="danger ? 'btn-danger' : 'btn-primary'"
        :disabled="busy"
        @click="emit('confirm')"
      >
        <span v-if="busy" class="spinner"></span>
        {{ confirmLabel }}
      </button>
    </template>
  </ModalDialog>
</template>
