<script setup>
/**
 * 이 줄이 지나온 길 (COM-PG-013).
 *
 * 목록의 줄 옆에 붙는 작은 버튼이다. 누르면 그 자리에서 길이 펼쳐진다.
 *
 * <b>왜 통합검색 화면만으로는 부족한가.</b> 주문 화면에 있는 사람이 그
 * 주문의 길을 보려면 번호를 복사해서 검색 화면으로 가서 붙여넣고 눌러야
 * 한다 — 화면 왕복을 줄이려고 만든 것이 왕복을 하나 더 늘린다.
 *
 * 검색 화면이 맞는 경우는 <b>손에 번호만 있고 아무 화면에도 없을 때</b>다.
 * CS 가 전화를 받아 "쿠팡 주문번호가 이거예요" 를 들었을 때. 이미 그
 * 문서를 보고 있는 사람에게는 이 버튼이 맞다.
 *
 * 화면을 옮기지 않고 겹쳐 띄우는 이유도 같다. 길을 보고 나서 하던 일로
 * 돌아가는 것이 대부분이라, 옮겨 버리면 뒤로 가기를 눌러야 한다.
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import * as searchApi from '@/api/search.js'
import { useSessionStore } from '@/stores/session.js'
import ModalDialog from '@/components/ModalDialog.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const props = defineProps({
  /** PUR_REQUEST · PUR_ORDER · INBOUND · ORDER · OUTBOUND · WAYBILL */
  kind: { type: String, required: true },
  seq: { type: [Number, String], required: true },
  /** 창 제목에 쓸 번호 */
  no: { type: String, default: '' },
  /** 버튼 글자. 좁은 목록에서는 '길' 하나로 줄인다 */
  label: { type: String, default: '길' },
})

const router = useRouter()
const session = useSessionStore()

const open = ref(false)
const chain = ref(null)
const loading = ref(false)
const error = ref('')

const denyReason = () => session.denyReason('SYS_SEARCH', 'R')

async function show() {
  open.value = true
  loading.value = true
  error.value = ''
  chain.value = null
  try {
    chain.value = await searchApi.chain(props.kind, props.seq)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

/** 길의 한 칸을 누르면 그 문서 화면으로 */
function goTo(step) {
  if (!step.route) return
  open.value = false
  router.push({ name: step.route, query: { keyword: step.no } })
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '')
</script>

<template>
  <button
    class="btn btn-sm"
    :disabled="!!denyReason()"
    :title="denyReason() ?? '이 건이 지나온 길을 봅니다'"
    @click.stop="show()"
  >
    {{ label }}
  </button>

  <ModalDialog
    v-if="open"
    title="지나온 길"
    :subtitle="no"
    @close="open = false"
  >
    <div v-if="loading" class="dim">불러오는 중…</div>

    <div v-else-if="error" class="alert alert-danger">
      <span class="alert-icon">⛔</span><span>{{ error }}</span>
    </div>

    <div v-else-if="!chain" class="alert">
      <span class="alert-icon">🔍</span>
      <span>
        이어지는 문서가 없습니다. 근거 요청 없이 낸 발주이거나, 아직 다음
        단계로 넘어가지 않았습니다.
      </span>
    </div>

    <template v-else>
      <p class="small dim mb-2">{{ chain.kindLabel }}</p>

      <ol class="chain">
        <li v-for="(s, i) in chain.steps" :key="s.kind + s.seq" @click="goTo(s)">
          <div class="dot" :class="{ last: i === chain.steps.length - 1 }"></div>
          <div class="step">
            <div class="small dim">{{ s.kindLabel }}</div>
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
        재고인데 재고는 수량이지 문서가 아니라, '그 물건' 을 가리킬
        방법이 없다.
      -->
      <p class="small dim note">
        <template v-if="chain.kind === 'SALES'">
          이 물건이 언제 어느 발주로 들어왔는지는 이어지지 않습니다 — 재고는
          수량이지 문서가 아니라서 '그 물건' 을 가리킬 방법이 없습니다.
        </template>
        <template v-else>
          들어온 물건이 어느 주문으로 나갔는지는 이어지지 않습니다 — 재고는
          수량이지 문서가 아니라서 '그 물건' 을 가리킬 방법이 없습니다.
        </template>
      </p>
    </template>

    <template #footer>
      <span class="left small dim">문서를 누르면 그 화면으로 갑니다.</span>
      <button class="btn" @click="open = false">닫기</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.chain {
  list-style: none;
  margin: 0;
  padding: 0;
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
.note {
  margin-top: 10px;
  line-height: 1.6;
}
</style>
