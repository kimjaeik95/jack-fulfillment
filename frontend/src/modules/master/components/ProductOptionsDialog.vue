<script setup>
/**
 * 스타일 옵션 — 이 스타일의 색상 · 사이즈.
 *
 * 색상 · 사이즈는 공통코드에서 끊어 스타일이 직접 들고 있다 (V43). 시즌 색이
 * 나올 때마다 기준정보 담당을 거치지 않으려는 것이었는데, 아무 도움도 없으면
 * 담당자가 'BK' 인지 'BLK' 인지 <b>기억해 적게</b> 된다. SKU 코드가 그 값으로
 * 조립되므로(PRD-24001-BK-M) 흔들리면 그대로 번진다.
 *
 * 그래서 <b>이미 쓰이고 있는 값을 추천</b>한다. 사전 테이블은 없다 — 쌓인
 * 것이 곧 목록이고, 없는 값은 그냥 치면 그 순간 목록에 합류한다. 고르게
 * 하되 막다른 길을 만들지 않는 방식이다.
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import * as api from '@/api/productOptions.js'
import ModalDialog from '@/components/ModalDialog.vue'

const props = defineProps({ product: { type: Object, required: true }, readonly: Boolean })
const emit = defineEmits(['close', 'saved'])

const rows = ref([])
const busy = ref(true)
const error = ref('')
const loaded = ref(false)
const types = [{ code: 'COLOR', label: '색상' }, { code: 'SIZE', label: '사이즈' }]

onMounted(async () => {
  try { rows.value = await api.list(props.product.productId); loaded.value = true }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
})

function add(type) {
  rows.value.push({
    optionType: type, optionCode: '', optionName: '',
    sortOrder: rows.value.filter((r) => r.optionType === type).length * 10,
  })
}

async function save() {
  busy.value = true; error.value = ''
  try { rows.value = await api.save(props.product.productId, rows.value); emit('saved'); emit('close') }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}

/* ── 같은 코드를 두 번 ───────────────────────────────────────── */

/**
 * 코드는 비교할 때도 대문자로 본다.
 *
 * 저장할 때 서버가 대문자로 맞추므로(ProductOption), 'bk' 와 'BK' 는 결국
 * 같은 줄이 된다. 화면에서 다르게 보면 '칠 때는 괜찮다가 저장만 막히는'
 * 상태가 되어, 왜 막혔는지가 안 보인다.
 */
const key = (row) => `${row.optionType}:${(row.optionCode || '').trim().toUpperCase()}`

/** 같은 종류 안에서 두 번 이상 나오는 코드 */
const dupKeys = computed(() => {
  const seen = new Set()
  const dup = new Set()
  for (const r of rows.value) {
    if (!r.optionCode?.trim()) continue
    const k = key(r)
    if (seen.has(k)) dup.add(k)
    seen.add(k)
  }
  return dup
})

const isDup = (row) => !!row.optionCode?.trim() && dupKeys.value.has(key(row))
const hasDup = computed(() => rows.value.some(isDup))

/** 그 종류에 몇 줄인가 — 머리글과 '없음' 안내에 쓴다 */
const countOf = (type) => rows.value.filter((r) => r.optionType === type).length

/** 이 종류에 이미 들어 있는 코드인가 — 추천 목록에서 '이미 추가됨' 으로 보인다 */
function already(type, code) {
  return rows.value.some((r) => r.optionType === type
      && (r.optionCode || '').trim().toUpperCase() === code.toUpperCase())
}

/* ── 다른 스타일과 이름이 다를 때 ────────────────────────────── */

/**
 * 코드는 같은데 이름이 다르면 알려 준다 — 막지는 않는다.
 *
 * BK 가 다른 스타일에서 '블랙' 인데 여기서 '찐블랙' 이면, 같은 코드가 두
 * 이름을 갖게 된다. 다른 색이라면 코드를 달리 해야 하고, 같은 색이라면
 * 이름을 맞춰야 한다 — 어느 쪽인지는 사람만 안다.
 *
 * 코드를 손으로 쳤을 때만 본다. 추천에서 고르면 이름이 함께 들어와 어긋날
 * 일이 없다.
 */
const nameWarn = ref({})

async function checkName(row, i) {
  const code = (row.optionCode || '').trim().toUpperCase()
  if (!code) { delete nameWarn.value[i]; return }
  try {
    const found = (await api.suggest(row.optionType, code))
        .find((h) => h.optionCode === code)
    if (found && row.optionName?.trim() && found.optionName !== row.optionName.trim()) {
      nameWarn.value[i] = `다른 스타일에서는 '${found.optionName}' 로 쓰입니다. `
          + `같은 색이면 이름을 맞추고, 다른 색이면 코드를 다르게 하세요.`
    } else {
      delete nameWarn.value[i]
    }
  } catch { delete nameWarn.value[i] }
}

/** 그 줄에 띄울 안내 한 줄 — 중복이 먼저다 */
function noteOf(row, i) {
  if (isDup(row)) return '이 코드는 이미 있습니다. 하나를 지우거나 코드를 바꾸세요.'
  return nameWarn.value[i] || ''
}

/* ── 추천 ───────────────────────────────────────────────────── */

/** 지금 추천을 펼친 줄의 인덱스. 한 번에 하나만 뜬다 */
const openAt = ref(null)
const hits = ref([])
const hitBusy = ref(false)
let timer = null

/**
 * 글자를 칠 때마다 부르지 않는다.
 *
 * 스캐너처럼 빠르게 치는 칸은 아니지만, 한 글자마다 왕복하면 느린 망에서
 * 목록이 덜컥거린다. 멈춘 뒤에 한 번만 묻는다.
 */
function ask(i, type, q) {
  openAt.value = i
  clearTimeout(timer)
  timer = setTimeout(async () => {
    hitBusy.value = true
    try { hits.value = await api.suggest(type, q) }
    catch { hits.value = [] }
    finally { hitBusy.value = false }
  }, 200)
}

/** 고르면 코드와 이름이 함께 들어간다 — 이름을 따로 치게 하지 않는다 */
function pick(row, hit) {
  row.optionCode = hit.optionCode
  row.optionName = hit.optionName
  close()
}

function close() {
  clearTimeout(timer)
  openAt.value = null
  hits.value = []
}

/**
 * 칸을 벗어나면 닫는다 — 다만 조금 기다린다.
 *
 * 추천 줄을 누르는 순간 blur 가 먼저 오므로, 바로 닫으면 클릭이 허공을
 * 친다. mousedown.prevent 로도 막고 있지만 둘 다 두는 편이 안전하다.
 */
function closeSoon() {
  setTimeout(close, 150)
}

/** 목록에 없을 때 — 친 글자를 이름으로 두고 코드 칸으로 보낸다 */
const codeInputs = ref([])
async function asNew(row, i, typed) {
  row.optionName = typed
  close()
  await nextTick()
  codeInputs.value[i]?.focus?.()
}
</script>

<template>
  <ModalDialog
    title="스타일 옵션"
    :subtitle="`${product.productName} · ${product.productId}`"
    size="wide"
    @close="!busy && emit('close')"
  >
    <p class="field-help mb-3">
      이 스타일의 SKU 에 쓸 색상과 사이즈입니다. 이름 칸에 치면
      <strong>이미 쓰이는 코드를 추천</strong>하고, 없으면 새로 적으면 됩니다.
      SKU 가 쓰고 있는 옵션은 지우거나 코드를 바꿀 수 없습니다.
    </p>

    <div v-if="error" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ error }}</span>
    </div>

    <section v-for="type in types" :key="type.code" class="opt-section">
      <div class="opt-head">
        <h3 class="opt-title">{{ type.label }}</h3>
        <span class="dim small">{{ countOf(type.code) }}개</span>
      </div>

      <table v-if="countOf(type.code)" class="table opt-table">
        <thead>
          <tr>
            <th style="width: 150px">코드</th>
            <th>이름</th>
            <th style="width: 92px" class="right">순서</th>
            <th style="width: 56px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in rows" v-show="row.optionType === type.code" :key="i">
            <td>
              <input
                :ref="(el) => (codeInputs[i] = el)"
                v-model="row.optionCode"
                class="input input-mono"
                :class="{ invalid: isDup(row) }"
                :aria-label="`${type.label} 코드`"
                maxlength="20"
                :disabled="busy || readonly"
                placeholder="BK"
                @blur="checkName(row, i)"
              />
            </td>

            <td class="name-cell">
              <input
                v-model="row.optionName"
                class="input"
                :aria-label="`${type.label} 이름`"
                maxlength="100"
                :disabled="busy || readonly"
                placeholder="블랙"
                autocomplete="off"
                @focus="ask(i, type.code, row.optionName)"
                @input="ask(i, type.code, row.optionName)"
                @keydown.esc="close()"
                @blur="closeSoon()"
              />
              <!--
                표기가 갈린 것이 고르는 순간 눈에 띈다 — 'BK 블랙 4곳' 옆에
                'BLK 블랙 1곳' 이 나란히 뜨면 어느 쪽이 쓰이는지 바로 보인다.
              -->
              <ul v-if="openAt === i && !readonly" class="hits">
                <li v-if="hitBusy" class="hit-msg">찾는 중…</li>
                <!--
                  이미 이 스타일에 든 코드는 고르게 하지 않는다. 고를 수 있게
                  두면 중복이 생기고, 그건 저장할 때가 되어야 막힌다.
                -->
                <li
                  v-for="h in hits"
                  :key="h.optionCode + h.optionName"
                  class="hit"
                  :class="{ taken: already(type.code, h.optionCode) }"
                  @mousedown.prevent="!already(type.code, h.optionCode) && pick(row, h)"
                >
                  <span class="hit-code">{{ h.optionCode }}</span>
                  <span class="hit-name">{{ h.optionName }}</span>
                  <span v-if="already(type.code, h.optionCode)" class="hit-tag">이미 추가됨</span>
                  <span v-else class="hit-tag">{{ h.usedCount }}곳</span>
                </li>
                <li
                  v-if="!hitBusy && row.optionName"
                  class="hit hit-new"
                  @mousedown.prevent="asNew(row, i, row.optionName)"
                >
                  <span class="hit-plus">+</span>
                  <span class="hit-name"><strong>{{ row.optionName }}</strong> 새로 만들기</span>
                </li>
                <li v-else-if="!hitBusy && !hits.length" class="hit-msg">
                  아직 쓰인 {{ type.label }}이 없습니다. 직접 적으세요.
                </li>
              </ul>
            </td>

            <td class="right">
              <input
                v-model.number="row.sortOrder"
                class="input right"
                :aria-label="`${type.label} 순서`"
                type="number"
                min="0"
                :disabled="busy || readonly"
              />
            </td>

            <td class="right">
              <button
                class="btn btn-sm btn-ghost"
                :disabled="busy || readonly"
                title="이 줄을 뺍니다"
                @click="rows.splice(i, 1)"
              >
                ×
              </button>
            </td>
          </tr>

          <!-- 줄 아래 한 칸에 모아 띄운다. 칸 밑에 붙이면 표가 들쭉날쭉해진다 -->
          <tr v-for="(row, i) in rows" v-show="row.optionType === type.code && noteOf(row, i)" :key="`n${i}`" class="note-row">
            <td colspan="4">
              <span :class="isDup(row) ? 'danger' : 'warn'">{{ noteOf(row, i) }}</span>
            </td>
          </tr>
        </tbody>
      </table>

      <p v-else class="empty-line">
        등록된 {{ type.label }}이 없습니다.
      </p>

      <button class="btn btn-sm" :disabled="busy || readonly" @click="add(type.code)">
        + {{ type.label }} 추가
      </button>
    </section>

    <template #footer>
      <span v-if="hasDup" class="left small danger">
        같은 코드가 두 번 있습니다. 하나를 지우거나 코드를 바꾸세요.
      </span>
      <button class="btn" :disabled="busy" @click="emit('close')">닫기</button>
      <button
        v-if="!readonly"
        class="btn btn-primary"
        :disabled="busy || !loaded || hasDup"
        @click="save"
      >
        <span v-if="busy" class="spinner"></span>
        옵션 저장
      </button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.opt-section {
  padding: 12px 14px;
  margin-bottom: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
}
.opt-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}
.opt-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
.opt-table {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  margin-bottom: 8px;
}
.opt-table th {
  font-size: 11.5px;
  color: var(--text-2);
  background: var(--surface-3);
}
.opt-table tbody tr:last-child td { border-bottom: none; }
.opt-table td { padding: 6px 10px; }

/* 안내 줄 — 테두리 없이 바로 위 줄에 붙는다 */
.note-row td {
  border-bottom: none;
  padding: 0 10px 6px;
  font-size: 11.5px;
}

.empty-line {
  margin: 0 0 8px;
  padding: 10px;
  text-align: center;
  color: var(--text-3);
  font-size: 12px;
  background: var(--surface);
  border: 1px dashed var(--border);
  border-radius: var(--radius);
}

/* 추천 — 이름 칸 바로 아래에 겹친다 */
.name-cell { position: relative; }
.hits {
  position: absolute;
  z-index: 30;
  left: 10px;
  right: 10px;
  margin: 3px 0 0;
  padding: 4px;
  list-style: none;
  max-height: 240px;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
}
.hit {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 9px;
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 12.5px;
}
.hit:hover { background: var(--primary-soft); }
.hit-code {
  flex: none;
  min-width: 54px;
  font-family: 'Cascadia Mono', 'D2Coding', Consolas, monospace;
  font-size: 12px;
  color: var(--text);
}
.hit-name { flex: 1; color: var(--text); }
.hit-tag { flex: none; font-size: 11px; color: var(--text-3); }
.hit-plus { flex: none; min-width: 54px; color: var(--primary); font-weight: 600; }

/* 이미 든 코드 — 보이되 못 고른다. 숨기면 '왜 안 뜨지' 가 된다 */
.hit.taken { opacity: 0.42; cursor: default; }
.hit.taken:hover { background: transparent; }

.hit-new { border-top: 1px solid var(--border); margin-top: 4px; padding-top: 8px; }
.hit-msg { padding: 8px 9px; font-size: 12px; color: var(--text-3); }
</style>
