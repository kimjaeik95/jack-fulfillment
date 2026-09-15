<script setup>
/**
 * 재고 이력 — 이동이력 · 할당이력 (INV-PG-003, INV-PG-004).
 *
 * 두 탭이 서로 다른 질문에 답한다.
 *   이동이력  수량이 왜 이렇게 됐나          — 언제 · 무엇이 · 얼마나 · 왜
 *   할당이력  이 재고를 누가 잡고 있나       — 어느 주문이 · 몇 개를 · 아직도
 *
 * 한 화면에 둔 이유는 현장에서 이 둘을 번갈아 보기 때문이다. "재고가 있다는데
 * 왜 못 보내나" 는 할당이력이 답하고, "어제까지 100개였는데" 는 이동이력이
 * 답한다. 화면을 나누면 재고 하나를 두고 두 화면을 오가게 된다.
 *
 * 이력은 지우지 않으므로 무한히 쌓인다. 기간을 반드시 걸고 들어간다 — 최근
 * 한 달이 기본이고, 조건 없이 전체를 훑는 경로는 두지 않는다.
 *
 * 재고 현황에서 한 줄을 눌러 넘어오면 stockSeq 가 걸린 채 열린다. 그때는
 * 기간을 넓게 잡는다 — 특정 재고 한 줄의 내력을 보러 온 것이라 한 달로
 * 자르면 대개 아무것도 안 나온다.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as stockApi from '@/api/stock.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const route = useRoute()
const hierarchy = useHierarchyStore()
const session = useSessionStore()

const size = stockApi.PAGE_SIZE

/** 'history' = 이동이력, 'alloc' = 할당이력 */
const tab = ref(route.query.tab === 'alloc' ? 'alloc' : 'history')

/**
 * 재고 현황에서 넘어온 한 줄.
 *
 * 걸려 있으면 두 탭 모두 그 재고만 본다. 서버는 이 순번이 내 데이터 범위
 * 안인지 먼저 확인한다 — 목록에 안 보이는 재고의 이력만 훔쳐보는 경로를
 * 막기 위해서다.
 */
const stockSeq = ref(route.query.stockSeq ? Number(route.query.stockSeq) : null)

/** 한 줄의 내력을 보러 왔으면 1년, 전체를 훑으러 왔으면 한 달 */
const defaultFrom = () => stockApi.daysAgo(stockSeq.value ? 365 : 30)

const loading = ref(false)
const loadError = ref('')

/* ── 이동이력 (INV-PG-003) ──────────────────────────────────── */

const hRows = ref([])
const hTotal = ref(0)
const hPage = ref(1)
const hFilters = reactive({
  keyword: '',
  plantId: '',
  warehouseId: '',
  moveType: '',
  qtyField: '',
  refType: '',
  refNo: '',
  fromDate: defaultFrom(),
  toDate: stockApi.daysAgo(0),
})

async function fetchHistory() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await stockApi.history({
      ...hFilters,
      stockSeq: stockSeq.value,
      page: hPage.value,
      size,
    })
    hRows.value = data.rows
    hTotal.value = data.total
  } catch (e) {
    hRows.value = []
    hTotal.value = 0
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

async function searchHistory() {
  hPage.value = 1
  await fetchHistory()
}

const hTotalPages = computed(() => Math.max(1, Math.ceil(hTotal.value / size)))

async function goHistoryPage(n) {
  if (n < 1 || n > hTotalPages.value || n === hPage.value) return
  hPage.value = n
  await fetchHistory()
}

/* ── 할당이력 (INV-PG-004) ──────────────────────────────────── */

const aRows = ref([])
const aTotal = ref(0)
const aPage = ref(1)
const aFilters = reactive({
  keyword: '',
  plantId: '',
  warehouseId: '',
  orderNo: '',
  allocStatus: '',
  heldOnly: '',
  fromDate: defaultFrom(),
  toDate: stockApi.daysAgo(0),
})

async function fetchAllocs() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await stockApi.allocs({
      ...aFilters,
      stockSeq: stockSeq.value,
      page: aPage.value,
      size,
    })
    aRows.value = data.rows
    aTotal.value = data.total
  } catch (e) {
    aRows.value = []
    aTotal.value = 0
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

async function searchAllocs() {
  aPage.value = 1
  await fetchAllocs()
}

const aTotalPages = computed(() => Math.max(1, Math.ceil(aTotal.value / size)))

async function goAllocPage(n) {
  if (n < 1 || n > aTotalPages.value || n === aPage.value) return
  aPage.value = n
  await fetchAllocs()
}

/* ── 공통 ───────────────────────────────────────────────────── */

const refresh = () => (tab.value === 'alloc' ? searchAllocs() : searchHistory())

watch(tab, () => {
  loadError.value = ''
  refresh()
})

/**
 * 재고 한 줄 걸기를 푼다.
 *
 * 걸린 줄 모르고 "이 센터 이력이 이것뿐인가" 로 읽는 일을 막으려고, 걸려
 * 있을 때는 배너로 알리고 여기서만 풀 수 있게 둔다.
 */
function clearStock() {
  stockSeq.value = null
  hFilters.fromDate = defaultFrom()
  aFilters.fromDate = defaultFrom()
  refresh()
}

/**
 * 라우트의 stockSeq 를 따라간다.
 *
 * 해시 라우터는 쿼리만 바뀌면 컴포넌트를 다시 만들지 않는다. 재고 현황에서
 * 한 줄을 걸어 넘어온 뒤 사이드바로 이 화면을 다시 열면, 걸어 둔 줄이 그대로
 * 남아 전체를 보는 줄 알고 한 줄만 보게 된다.
 */
watch(
  () => [route.query.stockSeq, route.query.tab],
  ([seq, t]) => {
    const next = seq ? Number(seq) : null
    if (t === 'alloc' || t === 'history') tab.value = t
    if (next === stockSeq.value) return
    stockSeq.value = next
    hFilters.fromDate = defaultFrom()
    aFilters.fromDate = defaultFrom()
    refresh()
  },
)

onMounted(async () => {
  await Promise.all([hierarchy.loadPlants(false), hierarchy.loadWarehouses(false)])
  await refresh()
})

const hWarehouseOptions = computed(() =>
  hFilters.plantId ? hierarchy.warehouseOptionsOf(hFilters.plantId) : [],
)
const aWarehouseOptions = computed(() =>
  aFilters.plantId ? hierarchy.warehouseOptionsOf(aFilters.plantId) : [],
)

watch(() => hFilters.plantId, () => { hFilters.warehouseId = ''; searchHistory() })
watch(() => aFilters.plantId, () => { aFilters.warehouseId = ''; searchAllocs() })

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
/** +12 / −3 — 부호를 붙여 늘었는지 줄었는지를 숫자 자체로 보여 준다 */
const signed = (v) => (v > 0 ? `+${nf.format(v)}` : nf.format(v))
const stamp = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const readDenyReason = computed(() => session.denyReason('QRY_STOCK', 'R'))

/* ── 표 ─────────────────────────────────────────────────────── */

const historyColumns = [
  { key: 'occurredAt', label: '발생시각', width: '130px' },
  { key: 'locationFullCode', label: '재고주소', width: '170px', cls: 'code' },
  { key: 'skuId', label: 'SKU', width: '160px', cls: 'code' },
  { key: 'moveType', label: '이동유형', width: '90px', align: 'center' },
  { key: 'qtyField', label: '대상수량', width: '86px', align: 'center' },
  { key: 'qtyDelta', label: '변동', width: '80px', align: 'right' },
  { key: 'qtyAfter', label: '변경 후', width: '116px', align: 'right' },
  { key: 'reasonName', label: '사유', width: '150px' },
  { key: 'refNo', label: '전표', width: '130px' },
  { key: 'createdBy', label: '처리자', width: '90px' },
]

const allocColumns = [
  { key: 'allocatedAt', label: '할당시각', width: '130px' },
  { key: 'orderNo', label: '주문번호', width: '140px', cls: 'code' },
  { key: 'locationFullCode', label: '재고주소', width: '170px', cls: 'code' },
  { key: 'skuId', label: 'SKU', width: '160px', cls: 'code' },
  { key: 'qtyAllocated', label: '할당', width: '76px', align: 'right' },
  { key: 'qtyReleased', label: '해제', width: '76px', align: 'right' },
  { key: 'qtyHeld', label: '잡힌 수량', width: '90px', align: 'right' },
  { key: 'allocStatus', label: '상태', width: '86px', align: 'center' },
  { key: 'releaseReasonName', label: '해제사유', width: '140px' },
  { key: 'releasedAt', label: '해제시각', width: '130px' },
]

/** '대상수량' 은 코드그룹이 아니라 세 값 중 하나라 여기서 이름을 붙인다 */
const QTY_FIELD = { ON_HAND: '보유', ALLOCATED: '할당', UNSELLABLE: '판매불가' }
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">재고 이력</h1>
        <p class="page-desc">
          <strong>이동이력</strong>은 수량이 왜 지금 값이 됐는지를,
          <strong>할당이력</strong>은 이 재고를 어느 주문이 잡고 있는지를 답합니다.
          이력은 지우지 않으므로 기간을 걸고 봅니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!-- 재고 한 줄이 걸려 있다는 것을 숨기지 않는다. 모르면 전체로 오해한다 -->
    <div v-if="stockSeq" class="alert alert-info mb-2">
      <span class="alert-icon">🔎</span>
      <span>
        재고 <strong>#{{ stockSeq }}</strong> 한 줄만 보고 있습니다. 기간도 최근 1년으로
        넓혀 두었습니다.
      </span>
      <button class="btn btn-sm" style="margin-left: auto" @click="clearStock()">전체 보기</button>
    </div>

    <div class="tabs">
      <button :class="['tab', { on: tab === 'history' }]" @click="tab = 'history'">
        이동이력
      </button>
      <button :class="['tab', { on: tab === 'alloc' }]" @click="tab = 'alloc'">
        할당이력
      </button>
    </div>

    <!-- ── 이동이력 (INV-PG-003) ─────────────────────────────── -->
    <div v-show="tab === 'history'" class="card">
      <div class="toolbar">
        <FormField
          v-model="hFilters.keyword"
          class="grow"
          label="검색어"
          placeholder="SKU / 제품명 / 빈코드 / 전표번호"
          @enter="searchHistory()"
        />
        <FormField v-model="hFilters.fromDate" label="시작일" type="date" @change="searchHistory()" />
        <FormField v-model="hFilters.toDate" label="종료일" type="date" @change="searchHistory()" />
        <FormField
          v-model="hFilters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
        />
        <FormField
          v-model="hFilters.warehouseId"
          label="창고"
          type="select"
          empty-option="전체"
          :options="hWarehouseOptions"
          :disabled="!hFilters.plantId"
          @change="searchHistory()"
        />
        <FormField
          v-model="hFilters.moveType"
          label="이동유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('STOCK_MOVE')"
          @change="searchHistory()"
        />
        <FormField
          v-model="hFilters.refType"
          label="전표유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('STOCK_REF')"
          @change="searchHistory()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="searchHistory()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <DataTable
        :columns="historyColumns"
        :rows="hRows"
        row-key="historySeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        empty-text="이 기간에 재고 이동이 없습니다."
      >
        <template #cell-occurredAt="{ value }">
          <span class="small">{{ stamp(value) }}</span>
        </template>

        <template #cell-locationFullCode="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.plantName }}</div>
        </template>

        <template #cell-skuId="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.productName }}</div>
        </template>

        <template #cell-moveType="{ value }">
          <CodeBadge group="STOCK_MOVE" :code="value" />
        </template>

        <template #cell-qtyField="{ value }">
          <span class="small">{{ QTY_FIELD[value] ?? value }}</span>
        </template>

        <!-- 부호를 숫자에 붙인다. 색만으로 구분하면 흑백 출력에서 사라진다 -->
        <template #cell-qtyDelta="{ row, value }">
          <strong :class="row.increase ? 'ok' : 'danger'">{{ signed(value) }}</strong>
        </template>

        <!-- 변경 전/후를 함께 보여 준다 (P-04). 변동만으로는 합이 안 맞을 때
             어느 줄부터 틀어졌는지 찾을 수 없다 -->
        <template #cell-qtyAfter="{ row, value }">
          <span class="dim small">{{ num(row.qtyBefore) }} →</span>
          <strong> {{ num(value) }}</strong>
        </template>

        <template #cell-reasonName="{ row, value }">
          <span v-if="value">{{ value }}</span>
          <span v-else class="dim">-</span>
          <div v-if="row.remark" class="small dim" :title="row.remark">{{ row.remark }}</div>
        </template>

        <template #cell-refNo="{ row, value }">
          <template v-if="value">
            <CodeBadge group="STOCK_REF" :code="row.refType" plain />
            <div class="small code">{{ value }}</div>
          </template>
          <span v-else class="dim">-</span>
        </template>
      </DataTable>

      <div class="pager">
        <span class="small dim">총 {{ num(hTotal) }}건 · {{ hPage }} / {{ hTotalPages }} 페이지</span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="hPage <= 1 || loading" @click="goHistoryPage(1)">« 처음</button>
          <button class="btn btn-sm" :disabled="hPage <= 1 || loading" @click="goHistoryPage(hPage - 1)">‹ 이전</button>
          <button class="btn btn-sm" :disabled="hPage >= hTotalPages || loading" @click="goHistoryPage(hPage + 1)">다음 ›</button>
          <button class="btn btn-sm" :disabled="hPage >= hTotalPages || loading" @click="goHistoryPage(hTotalPages)">마지막 »</button>
        </div>
      </div>
    </div>

    <!-- ── 할당이력 (INV-PG-004) ─────────────────────────────── -->
    <div v-show="tab === 'alloc'" class="card">
      <div class="toolbar">
        <FormField
          v-model="aFilters.keyword"
          class="grow"
          label="검색어"
          placeholder="SKU / 제품명 / 빈코드 / 주문번호"
          @enter="searchAllocs()"
        />
        <FormField v-model="aFilters.orderNo" label="주문번호" mono @enter="searchAllocs()" />
        <FormField v-model="aFilters.fromDate" label="시작일" type="date" @change="searchAllocs()" />
        <FormField v-model="aFilters.toDate" label="종료일" type="date" @change="searchAllocs()" />
        <FormField
          v-model="aFilters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
        />
        <FormField
          v-model="aFilters.warehouseId"
          label="창고"
          type="select"
          empty-option="전체"
          :options="aWarehouseOptions"
          :disabled="!aFilters.plantId"
          @change="searchAllocs()"
        />
        <FormField
          v-model="aFilters.allocStatus"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('ALLOC_STATUS')"
          @change="searchAllocs()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="searchAllocs()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <div class="quick">
        <button
          :class="['chip', { on: aFilters.heldOnly !== 'Y' }]"
          title="해제된 것까지 모두"
          @click="aFilters.heldOnly = ''; searchAllocs()"
        >
          전체
        </button>
        <button
          :class="['chip', { on: aFilters.heldOnly === 'Y' }]"
          title="아직 풀리지 않고 재고를 잡고 있는 것만"
          @click="aFilters.heldOnly = 'Y'; searchAllocs()"
        >
          아직 잡혀 있는 것만
        </button>
      </div>

      <DataTable
        :columns="allocColumns"
        :rows="aRows"
        row-key="allocSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        :muted-when="(a) => a.qtyHeld === 0"
        empty-text="이 기간에 할당 내역이 없습니다."
      >
        <template #cell-allocatedAt="{ value }">
          <span class="small">{{ stamp(value) }}</span>
        </template>

        <template #cell-orderNo="{ row, value }">
          <span class="code">{{ value }}</span>
          <div v-if="row.orderLineNo" class="small dim">{{ row.orderLineNo }}행</div>
        </template>

        <template #cell-locationFullCode="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.plantName }}</div>
        </template>

        <template #cell-skuId="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.productName }}</div>
        </template>

        <template #cell-qtyAllocated="{ value }">{{ num(value) }}</template>
        <template #cell-qtyReleased="{ value }">
          <span :class="{ dim: !value }">{{ num(value) }}</span>
        </template>

        <!-- 할당 − 해제. 일부만 푼 경우가 있어 두 수만으로는 한눈에 안 보인다 -->
        <template #cell-qtyHeld="{ row, value }">
          <strong :class="value > 0 ? 'warn' : 'dim'">{{ num(value) }}</strong>
          <div v-if="row.partiallyReleased" class="small dim">일부해제</div>
        </template>

        <template #cell-allocStatus="{ value }">
          <CodeBadge group="ALLOC_STATUS" :code="value" />
        </template>

        <template #cell-releaseReasonName="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
        </template>

        <template #cell-releasedAt="{ value }">
          <span class="small" :class="{ dim: !value }">{{ stamp(value) }}</span>
        </template>
      </DataTable>

      <div class="pager">
        <span class="small dim">총 {{ num(aTotal) }}건 · {{ aPage }} / {{ aTotalPages }} 페이지</span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="aPage <= 1 || loading" @click="goAllocPage(1)">« 처음</button>
          <button class="btn btn-sm" :disabled="aPage <= 1 || loading" @click="goAllocPage(aPage - 1)">‹ 이전</button>
          <button class="btn btn-sm" :disabled="aPage >= aTotalPages || loading" @click="goAllocPage(aPage + 1)">다음 ›</button>
          <button class="btn btn-sm" :disabled="aPage >= aTotalPages || loading" @click="goAllocPage(aTotalPages)">마지막 »</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.tab {
  padding: 7px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
}
.tab.on {
  background: var(--c-blue, #2563eb);
  border-color: var(--c-blue, #2563eb);
  color: #fff;
}
.quick {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 0 14px 10px;
}
.chip {
  padding: 5px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
}
.chip.on {
  background: var(--c-blue, #2563eb);
  border-color: var(--c-blue, #2563eb);
  color: #fff;
}
.ok {
  color: var(--c-green, #16a34a);
}
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
</style>
