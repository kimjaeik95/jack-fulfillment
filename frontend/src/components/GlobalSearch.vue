<script setup>
/**
 * 어디서든 여는 검색창 (COM-PG-013).
 *
 * 상단 막대에 붙어 <b>Ctrl+K</b> 로 열린다. CS 가 전화를 받아 "쿠팡 주문번호가
 * 이거예요" 를 들었을 때, 지금 무슨 화면에 있든 바로 찍어 넣는 자리다.
 *
 * 검색 화면을 따로 두고도 이것을 붙이는 이유는, 검색 화면으로 <b>가는 것</b>
 * 자체가 왕복이라서다. 메뉴를 찾아 누르고 화면이 바뀌고 나서야 입력할 수
 * 있으면, 화면 왕복을 줄이려고 만든 것이 왕복을 하나 더 늘린다.
 *
 * 결과를 여기서 다 보여 주지 않는다. 한 건이면 그 길을 바로 펼치고, 여럿이면
 * 고르게 한다 — 목록을 여기 그리기 시작하면 이 작은 창이 또 하나의 화면이
 * 된다. '자세히' 로 검색 화면에 넘길 수 있다.
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as searchApi from '@/api/search.js'
import { useSessionStore } from '@/stores/session.js'
import CodeBadge from '@/components/CodeBadge.vue'

const router = useRouter()
const session = useSessionStore()

const allowed = computed(() => !session.denyReason('SYS_SEARCH', 'R'))

const open = ref(false)
const q = ref('')
const input = ref(null)
const result = ref(null)
const chain = ref(null)
const loading = ref(false)
const error = ref('')

async function show() {
  if (!allowed.value) return
  open.value = true
  await nextTick()
  input.value?.focus()
  input.value?.select()
}

function close() {
  open.value = false
  error.value = ''
}

async function run() {
  const kw = q.value.trim()
  if (kw.length < 2) {
    error.value = '두 글자 이상 입력하세요.'
    return
  }
  loading.value = true
  error.value = ''
  chain.value = null
  try {
    result.value = await searchApi.search(kw)
    chain.value = result.value.chain
  } catch (e) {
    error.value = e.message
    result.value = null
  } finally {
    loading.value = false
  }
}

async function pick(hit) {
  loading.value = true
  try {
    chain.value = await searchApi.chain(hit.kind, hit.seq)
    // 길이 없는 것(SKU)은 그 화면으로 바로 보낸다
    if (!chain.value) goTo(hit)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function goTo(step) {
  if (!step.route) return
  close()
  router.push({ name: step.route, query: { keyword: step.no } })
}

function goFull() {
  close()
  router.push({ name: 'search' })
}

/**
 * Ctrl+K 로 연다.
 *
 * 입력칸 안에서 누른 것은 무시한다 — 다른 화면에서 검색어를 치다가
 * 실수로 이 창이 뜨면 치던 것이 사라진다.
 */
function onKey(e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    show()
    return
  }
  if (e.key === 'Escape' && open.value) close()
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))

const hits = computed(() => result.value?.hits ?? [])
const hidden = computed(() => result.value?.hiddenKinds ?? [])

const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '')
</script>

<template>
  <button
    v-if="allowed"
    class="btn btn-ghost btn-sm gs-trigger"
    title="통합검색 (Ctrl+K)"
    @click="show()"
  >
    🔍 <span class="gs-hint">검색</span>
    <kbd>Ctrl K</kbd>
  </button>

  <!-- 화면 위에 겹쳐 띄운다. 검색은 하던 일을 잠깐 멈추는 것이지
       다른 데로 가는 것이 아니다 -->
  <div v-if="open" class="gs-back" @click.self="close()">
    <div class="gs-panel">
      <div class="gs-input">
        <span>🔍</span>
        <input
          ref="input"
          v-model="q"
          type="text"
          placeholder="주문번호 · 채널 주문번호 · 송장번호 · 지시번호 · SKU · 수령인 · 연락처"
          @keydown.enter="run()"
        />
        <button class="btn btn-sm btn-primary" :disabled="loading" @click="run()">
          <span v-if="loading" class="spinner"></span>
          찾기
        </button>
      </div>

      <div v-if="error" class="gs-msg danger">{{ error }}</div>

      <div v-if="hidden.length" class="gs-msg warn">
        조회 권한이 없어 <strong>{{ hidden.join(' · ') }}</strong> 은(는) 빠졌습니다.
      </div>

      <div v-if="result && !hits.length && !error" class="gs-msg dim">
        '{{ result.keyword }}' 로 찾은 것이 없습니다.
      </div>

      <!-- 여럿이면 고르게 둔다. 아무거나 골라 펼치면 그것을 답으로 읽는다 -->
      <div v-if="hits.length > 1 && !chain" class="gs-list">
        <div v-for="h in hits" :key="h.kind + h.seq" class="gs-row" @click="pick(h)">
          <span class="gs-kind">{{ h.kindLabel }}</span>
          <span class="code">{{ h.no }}</span>
          <CodeBadge v-if="h.status" :group="h.statusGroup" :code="h.status" />
          <span class="small dim gs-label">{{ h.label }}</span>
        </div>
      </div>

      <!-- 길 -->
      <div v-if="chain" class="gs-chain">
        <div class="small dim mb-1">{{ chain.kindLabel }}</div>
        <div
          v-for="s in chain.steps"
          :key="s.kind + s.seq"
          class="gs-row"
          @click="goTo(s)"
        >
          <span class="gs-kind">{{ s.kindLabel }}</span>
          <span class="code">{{ s.no }}</span>
          <CodeBadge v-if="s.status" :group="s.statusGroup" :code="s.status" />
          <span class="small dim gs-label">{{ s.label }} {{ dt(s.when) }}</span>
        </div>
      </div>

      <div class="gs-foot">
        <span class="small dim">Enter 찾기 · Esc 닫기 · 문서를 누르면 그 화면으로</span>
        <button class="btn btn-sm" @click="goFull()">검색 화면에서 보기</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gs-trigger {
  gap: 6px;
}
.gs-hint {
  opacity: 0.8;
}
kbd {
  font-size: 11px;
  padding: 1px 5px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 4px;
  opacity: 0.7;
}
.gs-back {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  justify-content: center;
  padding-top: 12vh;
}
.gs-panel {
  width: min(720px, 92vw);
  max-height: 70vh;
  overflow-y: auto;
  background: var(--c-surface, #fff);
  border-radius: 12px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3);
}
.gs-input {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}
.gs-input input {
  flex: 1 1 auto;
  min-width: 0;
  border: 0;
  outline: none;
  font-size: 15px;
  background: transparent;
  color: inherit;
}
.gs-msg {
  padding: 10px 16px;
  font-size: 13px;
}
.gs-msg.danger {
  color: var(--c-red, #dc2626);
}
.gs-msg.warn {
  color: var(--c-amber, #b45309);
}
.gs-list,
.gs-chain {
  padding: 8px 8px 4px;
}
.gs-chain {
  border-top: 1px solid var(--line, #e5e7eb);
}
.gs-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
}
.gs-row:hover {
  background: var(--c-hover, #f3f4f6);
}
.gs-kind {
  flex: 0 0 76px;
  font-size: 12px;
  color: var(--c-dim, #6b7280);
}
.gs-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gs-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid var(--line, #e5e7eb);
}
</style>
