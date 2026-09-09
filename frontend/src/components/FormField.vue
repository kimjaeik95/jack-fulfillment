<script setup>
/**
 * 라벨 + 컨트롤 + 도움말/오류 를 묶은 폼 필드.
 * type 별로 input / select / textarea / checks(다중 체크박스) 를 렌더한다.
 */
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, default: '' },
  modelValue: { type: [String, Number, Array, Boolean, null], default: '' },
  type: { type: String, default: 'text' }, // text | number | select | textarea | checks | switch
  options: { type: Array, default: () => [] }, // [{ value, label, disabled }]
  placeholder: { type: String, default: '' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  readonly: { type: Boolean, default: false },
  help: { type: String, default: '' },
  error: { type: String, default: '' },
  mono: { type: Boolean, default: false },
  rows: { type: Number, default: 3 },
  span: { type: Boolean, default: false },
  emptyOption: { type: String, default: '' }, // select 의 '전체'/'선택' 옵션 라벨
})

const emit = defineEmits(['update:modelValue'])

const cls = computed(() => ({ invalid: !!props.error, 'input-mono': props.mono }))

function onCheck(value, checked) {
  const cur = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  const i = cur.indexOf(value)
  if (checked && i < 0) cur.push(value)
  if (!checked && i >= 0) cur.splice(i, 1)
  emit('update:modelValue', cur)
}

const checked = (v) => Array.isArray(props.modelValue) && props.modelValue.includes(v)
</script>

<template>
  <div class="field" :class="{ 'span-2': span }">
    <label v-if="label" class="field-label">
      {{ label }}<span v-if="required" class="req">*</span>
    </label>

    <select
      v-if="type === 'select'"
      class="select"
      :class="cls"
      :value="modelValue"
      :disabled="disabled"
      @change="emit('update:modelValue', $event.target.value)"
    >
      <option v-if="emptyOption" value="">{{ emptyOption }}</option>
      <option v-for="o in options" :key="o.value" :value="o.value" :disabled="o.disabled">
        {{ o.label }}
      </option>
    </select>

    <textarea
      v-else-if="type === 'textarea'"
      class="textarea"
      :class="cls"
      :rows="rows"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      @input="emit('update:modelValue', $event.target.value)"
    ></textarea>

    <div v-else-if="type === 'checks'" class="check-group">
      <label v-for="o in options" :key="o.value" class="check-line" :title="o.title">
        <input
          type="checkbox"
          :checked="checked(o.value)"
          :disabled="disabled || o.disabled"
          @change="onCheck(o.value, $event.target.checked)"
        />
        {{ o.label }}
      </label>
      <span v-if="!options.length" class="dim small" style="padding: 3px 6px">선택 가능한 항목이 없습니다.</span>
    </div>

    <label v-else-if="type === 'switch'" class="check-line" style="padding-left: 0">
      <input
        type="checkbox"
        :checked="modelValue === 'Y' || modelValue === true"
        :disabled="disabled"
        @change="emit('update:modelValue', $event.target.checked ? 'Y' : 'N')"
      />
      <span>{{ modelValue === 'Y' || modelValue === true ? '사용' : '미사용' }}</span>
    </label>

    <input
      v-else
      class="input"
      :class="cls"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      @input="emit('update:modelValue', type === 'number' ? Number($event.target.value) : $event.target.value)"
    />

    <span v-if="error" class="field-error">{{ error }}</span>
    <span v-else-if="help" class="field-help">{{ help }}</span>
  </div>
</template>
