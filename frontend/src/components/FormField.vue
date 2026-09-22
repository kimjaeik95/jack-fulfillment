<script setup>
/**
 * 라벨 + 컨트롤 + 도움말/오류 를 묶은 폼 필드.
 * type 별로 input / select / textarea / checks(다중 체크박스) 를 렌더한다.
 */
import { computed, ref } from 'vue'

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

  /*
   * switch 의 양쪽 이름.
   *
   * 대부분은 사용/미사용이지만 전부는 아니다 — '블라인드 카운트' 는 숨김/보임이고
   * '기본 배송지' 는 예/아니오다. 기본값을 두되 화면이 바꿀 수 있게 한다.
   */
  onLabel: { type: String, default: '사용' },
  offLabel: { type: String, default: '미사용' },
})

/** switch 는 Y/N 문자열과 boolean 을 둘 다 받는다 (화면마다 다르게 쓰고 있다) */
const switchOn = computed(() => props.modelValue === 'Y' || props.modelValue === true)
/** 들어온 모양대로 돌려준다 — boolean 을 넣었는데 'Y' 가 나가면 저장할 때 어긋난다 */
const switchOut = (on) => (typeof props.modelValue === 'boolean' ? on : on ? 'Y' : 'N')

// enter — 서버 조회 화면에서 검색을 실행하는 데 쓴다.
// 검색어를 치고 Enter 를 누르는 것은 당연한 기대이고, 버튼만 두면 매번 마우스를 잡아야 한다.
const emit = defineEmits(['update:modelValue', 'enter'])

const cls = computed(() => ({ invalid: !!props.error, 'input-mono': props.mono }))

/**
 * 밖에서 포커스를 옮길 수 있게 연다.
 *
 * 스캔으로 이어 넣는 화면이 필요로 한다. 적치는 SKU 를 찍고 바로 로케이션을
 * 찍는데, 그 사이에 사람이 칸을 옮겨 주지 않는다 — 스캐너는 글자와 Enter 만
 * 보내고 기다리지 않는다. 포커스가 안 옮겨지면 두 번째 스캔이 첫 칸을
 * 덮어써서, 화면에는 값이 들어간 것처럼 보이는데 다른 칸은 비어 있게 된다.
 *
 * select · textarea · 단일 input 을 한 ref 로 받는다. type 에 따라 렌더되는
 * 것이 다르고, 그중 하나만 존재한다.
 */
const el = ref(null)

defineExpose({
  focus: () => el.value?.focus?.(),
  select: () => el.value?.select?.(),
})

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
      ref="el"
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
      ref="el"
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

    <!--
      켜고 끄는 값은 두 쪽을 다 보여 준다.

      전에는 체크박스 하나에 글자가 붙어 있었고, 그 글자가 상태에 따라
      '사용' ↔ '미사용' 으로 바뀌었다. 체크박스 옆 글자는 보통 '체크하면
      이렇게 된다' 를 뜻하는데 여기서는 '지금 이렇다' 를 뜻해서, 미사용이라고
      쓰여 있을 때 그것이 현재 상태인지 누르면 될 일인지 알 수 없었다.

      둘을 나란히 놓고 하나를 켜면 그 물음이 생기지 않는다 — 고를 것이
      무엇이고 지금 무엇인지가 한눈에 같이 보인다.
    -->
    <div v-else-if="type === 'switch'" class="segmented" role="radiogroup" :aria-label="label">
      <label class="seg" :class="{ on: switchOn }">
        <input
          type="radio"
          :checked="switchOn"
          :disabled="disabled"
          @change="emit('update:modelValue', switchOut(true))"
        />
        <span>{{ onLabel }}</span>
      </label>
      <label class="seg" :class="{ on: !switchOn, off: !switchOn }">
        <input
          type="radio"
          :checked="!switchOn"
          :disabled="disabled"
          @change="emit('update:modelValue', switchOut(false))"
        />
        <span>{{ offLabel }}</span>
      </label>
    </div>

    <input
      v-else
      ref="el"
      class="input"
      :class="cls"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      @input="emit('update:modelValue', type === 'number' ? Number($event.target.value) : $event.target.value)"
      @keyup.enter="emit('enter')"
    />

    <span v-if="error" class="field-error">{{ error }}</span>
    <span v-else-if="help" class="field-help">{{ help }}</span>
  </div>
</template>
