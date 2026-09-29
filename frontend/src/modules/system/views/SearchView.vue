<script setup>
/**
 * 통합검색 (COM-PG-013).
 *
 * 번호 하나를 받아 <b>그 건이 지나온 길</b>을 보여 준다.
 *
 * 지금은 주문번호를 받아 들면 주문 화면에서 찾고, 지시번호를 알아내 출고
 * 화면으로 가고, 송장번호를 알아내 배송 화면으로 간다. 한 건이 지나는
 * 문서가 여섯이라 CS 가 전화를 받으면 화면을 여섯 번 연다.
 *
 * <b>사슬은 둘로 끊겨 있다.</b> 구매(요청 → 발주 → 입고)와 판매(주문 →
 * 지시 → 송장 → 배송)를 잇는 것은 재고인데, 재고는 수량이지 문서가 아니다.
 * 잇는 척하면 없는 관계를 있다고 말하는 것이 된다.
 */
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as searchApi from '@/api/search.js'
import { useSessionStore } from '@/stores/session.js'
import CodeBadge from '@/components/CodeBadge.vue'
import FormField from '@/components/FormField.vue'

const router = useRouter()
const session = useSessionStore()

const readDenyReason = computed(() => session.denyReason('SYS_SEARCH', 'R'))

const keyword = ref('')
const result = ref(null)
const chain = ref(null)
const loading = ref(false)
const loadError = ref('')
const box = ref(null)

async function run() {
  const q = keyword.value.trim()
  if (q.length < 2) {
    loadError.value = '두 글자 이상 입력하세요. 한 글자로는 거의 모든 문서가 걸립니다.'
    return
  }
  loading.value = true
  loadError.value = ''
  chain.value = null
  try {
    result.value = await searchApi.search(q)
    chain.value = result.value.chain
  } catch (e) {
    loadError.value = e.message
    result.value = null
  } finally {
    loading.value = false
  }
}

/** 걸린 것이 여럿일 때 하나를 골라 길을 펼친다 */
async function openChain(hit) {
  loading.value = true
  try {
    chain.value = await searchApi.chain(hit.kind, hit.seq)
    await nextTick()
    box.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

/** 그 문서 화면으로. 번호를 검색어로 넘겨 바로 그 줄이 뜨게 한다 */
function goTo(step) {
  if (!step.route) return
  const param = searchApi.KIND_QUERY[step.kind] ?? 'keyword'
  router.push({ name: step.route, query: { [param]: step.no } })
}

const hits = computed(() => result.value?.hits ?? [])
const hidden = computed(() => result.value?.hiddenKinds ?? [])

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '')
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">통합검색</h1>
        <p class="page-desc">
          번호 하나로 <strong>그 건이 지나온 길</strong>을 봅니다. 주문번호 · 채널 주문번호 ·
          송장번호 · 지시번호 · 발주번호 · SKU · 수령인 · 연락처로 찾습니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="keyword"
          class="grow"
          label="검색어"
          placeholder="ORD-20260923-0010 · 880150888879 · PRD-23001-BE-FREE · 홍길동 · 010-…"
          @enter="run()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="run()">
            <span v-if="loading" class="spinner"></span>
            찾기
          </button>
        </div>
      </div>

      <div v-if="loadError" class="alert alert-danger m-2">
        <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
      </div>

      <!--
        못 보는 것과 없는 것을 구분해 준다. 조용히 빼면 '그런 문서 없다' 로
        잘못 답하게 된다.
      -->
      <div v-if="hidden.length" class="alert alert-warn m-2">
        <span class="alert-icon">⚠</span>
        <span>
          조회 권한이 없어 <strong>{{ hidden.join(' · ') }}</strong> 은(는) 결과에서 빠졌습니다.
          없는 것이 아니라 안 보이는 것입니다.
        </span>
      </div>

      <div v-if="result && !hits.length && !loadError" class="alert m-2">
        <span class="alert-icon">🔍</span>
        <span>'{{ result.keyword }}' 로 찾은 것이 없습니다.</span>
      </div>

      <!-- ── 걸린 것들 ────────────────────────────────────── -->
      <div v-if="hits.length" class="hits">
        <div
          v-for="h in hits"
          :key="h.kind + h.seq"
          class="hit"
          :class="{ picked: chain && hits.length === 1 }"
          @click="openChain(h)"
        >
          <div class="hit-kind">{{ h.kindLabel }}</div>
          <div class="hit-main">
            <span class="code">{{ h.no }}</span>
            <CodeBadge v-if="h.status" :group="h.statusGroup" :code="h.status" />
            <div class="small dim">
              {{ h.label }}
              <template v-if="h.plantName"> · {{ h.plantName }}</template>
              <template v-if="h.when"> · {{ dt(h.when) }}</template>
            </div>
          </div>
          <div class="hit-go small dim">길 보기 →</div>
        </div>
      </div>
    </div>

    <!-- ── 지나온 길 ──────────────────────────────────────── -->
    <div v-if="chain" ref="box" class="card mt-2">
      <div class="chain-head">
        <strong>{{ chain.kindLabel }}</strong>
        <span class="small dim">
          문서를 누르면 그 화면으로 갑니다
        </span>
      </div>

      <ol class="chain">
        <li v-for="(s, i) in chain.steps" :key="s.kind + s.seq" @click="goTo(s)">
          <div class="dot" :class="{ last: i === chain.steps.length - 1 }"></div>
          <div class="step">
            <div class="step-kind small dim">{{ s.kindLabel }}</div>
            <div>
              <span class="code">{{ s.no }}</span>
              <CodeBadge v-if="s.status" :group="s.statusGroup" :code="s.status" />
              <span v-if="s.qty" class="small dim"> · {{ num(s.qty) }}개</span>
            </div>
            <div class="small dim">
              {{ s.label }}<template v-if="s.when"> · {{ dt(s.when) }}</template>
            </div>
          </div>
        </li>
      </ol>

      <!--
        사슬이 둘로 끊긴 것을 숨기지 않는다. 구매와 판매를 잇는 것은
        재고인데 재고는 수량이지 문서가 아니다 — 입고된 그 물건이 이 주문으로
        나갔다는 고리가 없다. 로트를 안 쓰기로 했으니 당연하다.
      -->
      <p class="small dim chain-note">
        <template v-if="chain.kind === 'SALES'">
          여기까지가 판매 사슬입니다. 이 물건이 <strong>언제 어느 발주로 들어왔는지</strong>는
          이어지지 않습니다 — 구매와 판매를 잇는 것은 재고인데, 재고는 수량이지 문서가
          아니라서 '그 물건' 을 가리킬 방법이 없습니다.
        </template>
        <template v-else>
          여기까지가 구매 사슬입니다. 들어온 물건이 <strong>어느 주문으로 나갔는지</strong>는
          이어지지 않습니다 — 재고는 수량이지 문서가 아니라서 '그 물건' 을 가리킬 방법이
          없습니다.
        </template>
      </p>
    </div>
  </div>
</template>

<style scoped>
.hits {
  border-top: 1px solid var(--line, #e5e7eb);
}
.hit {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line, #e5e7eb);
  cursor: pointer;
}
.hit:hover {
  background: var(--c-hover, #f9fafb);
}
.hit-kind {
  flex: 0 0 84px;
  font-size: 12px;
  color: var(--c-dim, #6b7280);
}
.hit-main {
  flex: 1 1 auto;
  min-width: 0;
}
.hit-go {
  flex: 0 0 auto;
}
.chain-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}
.chain {
  list-style: none;
  margin: 0;
  padding: 8px 14px;
}
.chain li {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  cursor: pointer;
}
.chain li:hover .step {
  background: var(--c-hover, #f9fafb);
}
.dot {
  flex: 0 0 10px;
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--c-primary, #2563eb);
  position: relative;
}
.dot:not(.last)::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 12px;
  width: 2px;
  height: calc(100% + 18px);
  background: var(--line, #e5e7eb);
}
.step {
  flex: 1 1 auto;
  min-width: 0;
  padding: 2px 6px;
  border-radius: 6px;
}
.chain-note {
  padding: 0 14px 12px;
  line-height: 1.6;
}
</style>
