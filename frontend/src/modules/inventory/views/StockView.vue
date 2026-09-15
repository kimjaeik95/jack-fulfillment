<script setup>
/**
 * 재고 현황 · 상세 (INV-PG-001, INV-PG-002).
 *
 * 재고 한 줄은 '로케이션 × SKU × 거래처' 다. 같은 SKU 라도 빈이 다르면 다른
 * 줄이고, 위탁이면 거래처까지 갈라진다 — 남의 물건과 우리 물건을 한 줄에
 * 섞으면 정산이 안 된다.
 *
 * 수량이 넷이다. 그중 하나만 사람이 못 고친다.
 *   보유      창고에 실제로 있는 수량
 *   할당      주문이 잡아 둔 수량
 *   판매불가  불량 · 검수대기 등 팔 수 없는 수량
 *   판매가능  보유 − 할당 − 판매불가. DB 가 계산한다 (P-01)
 *
 * 판매가능을 따로 저장하지 않는 이유가 이 화면의 전부다. 저장하면 언젠가
 * 셋의 합과 어긋나고, 그때 어느 쪽이 맞는지 아무도 모른다.
 *
 * 등록 · 수정 · 삭제 버튼이 없다. 빠뜨린 것이 아니라 재고를 화면에서 직접
 * 고치지 않기 때문이다 (P-02) — 수량은 입고 · 출고 · 조정 · 실사의 결과로만
 * 바뀐다.
 *
 * SKU 수 × 로케이션 수만큼 늘어나 센터 하나에도 수만 행이 생기므로 서버
 * 페이징을 쓴다. 합계도 서버가 따로 집계한다 — 100건씩 보는 화면에서 전체
 * 합을 알 방법이 없다.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as stockApi from '@/api/stock.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const route = useRoute()
const router = useRouter()
const hierarchy = useHierarchyStore()
const session = useSessionStore()

const size = stockApi.PAGE_SIZE

const rows = ref([])
const summary = ref(null)
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: route.query.plantId ?? '',
  warehouseId: '',
  locationId: '',
  skuId: route.query.skuId ?? '',
  productId: '',
  vendorId: '',
  onHandOnly: '',
  lockedOnly: '',
  unsellableOnly: '',
  neverCountedOnly: '',
})

/**
 * 빠른 필터.
 *
 * 재고 화면에 오는 이유는 대개 넷 중 하나다. 조건을 매번 조합하게 하는 대신
 * 질문 그대로 버튼을 둔다. 한 번에 하나만 걸린다 — 겹쳐 걸면 무엇을 보고
 * 있는지 스스로도 알기 어렵다.
 */
const QUICK = [
  { key: '', label: '전체', desc: '조건 없이 전부' },
  { key: 'onHandOnly', label: '재고 있음', desc: '보유수량이 0 보다 큰 것만' },
  { key: 'lockedOnly', label: '묶여 있음', desc: '보유는 있는데 판매가능이 0 인 것' },
  { key: 'unsellableOnly', label: '판매불가 보유', desc: '불량 · 검수대기 수량이 있는 것' },
  { key: 'neverCountedOnly', label: '실사 이력 없음', desc: '한 번도 실사하지 않은 것' },
]

const quick = ref('')

watch(quick, (key) => {
  for (const q of QUICK) if (q.key) filters[q.key] = ''
  if (key) filters[key] = 'Y'
  search()
})

function resetFilters() {
  Object.assign(filters, {
    keyword: '', plantId: '', warehouseId: '', locationId: '',
    skuId: '', productId: '', vendorId: '',
    onHandOnly: '', lockedOnly: '', unsellableOnly: '', neverCountedOnly: '',
  })
  quick.value = ''
}

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await stockApi.list({ ...filters, page: page.value, size })
    rows.value = data.page.rows
    total.value = data.page.total
    summary.value = data.summary
  } catch (e) {
    rows.value = []
    total.value = 0
    summary.value = null
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

async function search() {
  page.value = 1
  await fetchPage()
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

const warehouseOptions = computed(() =>
  filters.plantId ? hierarchy.warehouseOptionsOf(filters.plantId) : [],
)

/** 플랜트를 바꾸면 창고를 푼다 — 다른 플랜트의 창고가 걸린 채 남으면 늘 0건이다 */
watch(
  () => filters.plantId,
  () => {
    filters.warehouseId = ''
    search()
  },
)

onMounted(async () => {
  // 창고까지 받는다. 창고 드롭다운(warehouseOptionsOf)이 이 목록에서 골라내므로
  // 플랜트만 받으면 플랜트를 골라도 창고가 비어 보인다.
  await Promise.all([hierarchy.loadPlants(false), hierarchy.loadWarehouses(false)])
  await fetchPage()
})

/* ── 표 ─────────────────────────────────────────────────────── */

const columns = [
  { key: 'locationFullCode', label: '재고주소', width: '180px', cls: 'code' },
  { key: 'skuId', label: 'SKU', width: '165px', cls: 'code' },
  { key: 'productName', label: '제품', width: '180px' },
  { key: 'vendorName', label: '거래처', width: '110px' },
  { key: 'qtyOnHand', label: '보유', width: '82px', align: 'right' },
  { key: 'qtyAllocated', label: '할당', width: '82px', align: 'right' },
  { key: 'qtyUnsellable', label: '판매불가', width: '88px', align: 'right' },
  { key: 'qtyAvailable', label: '판매가능', width: '92px', align: 'right' },
  { key: 'lastCountedAt', label: '최근 실사', width: '104px', align: 'center' },
  { key: '_act', label: '', width: '86px', align: 'right' },
]

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const day = (v) => (v ? String(v).slice(0, 10) : null)

/* ── 상세 (INV-PG-002) ──────────────────────────────────────── */

const picked = ref(null)

/**
 * 목록 행을 그대로 쓰지 않고 단건으로 다시 읽는다.
 *
 * 목록을 받은 뒤 시간이 지나 있을 수 있고, 상세는 '지금 몇 개인가' 에
 * 답해야 하는 화면이다. 서버가 단건 조회에서 데이터 범위를 다시 판정하기도
 * 한다 — 목록에 안 보이는 재고를 순번으로 넘겨보는 경로를 막는 검증이라,
 * 한 번 더 태워 두는 편이 맞다.
 */
async function openDetail(row) {
  try {
    picked.value = await stockApi.detail(row.stockSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/** 이 재고 한 줄의 이력으로 넘어간다 */
function goHistory(stockSeq, tab) {
  router.push({ name: 'stock-history', query: { stockSeq, tab } })
}

const readDenyReason = computed(() => session.denyReason('QRY_STOCK', 'R'))

/** 목록에 묶인 재고가 섞여 있는지 — 있으면 결품의 원인이 된다 */
const lockedCount = computed(() => rows.value.filter((r) => r.locked).length)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">재고 현황</h1>
        <p class="page-desc">
          로케이션 · SKU · 거래처별 보유 수량입니다.
          <strong>판매가능 = 보유 − 할당 − 판매불가</strong> 이며 이 값은 서버가 계산합니다.
          재고를 이 화면에서 직접 고칠 수는 없습니다 — 수량은 입고 · 출고 · 조정 · 실사의
          결과로만 바뀝니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!-- ── 합계 ─────────────────────────────────────────────── -->
    <!-- 목록이 페이징되므로 화면에서 더한 값이 아니다. 같은 조건으로 서버가 집계한다 -->
    <div v-if="summary" class="sum-row">
      <div class="sum-card">
        <span class="sum-label">재고 행</span>
        <strong class="sum-value">{{ num(summary.rowCount) }}</strong>
      </div>
      <div class="sum-card">
        <span class="sum-label">보유</span>
        <strong class="sum-value">{{ num(summary.qtyOnHand) }}</strong>
      </div>
      <div class="sum-card">
        <span class="sum-label">할당</span>
        <strong class="sum-value">{{ num(summary.qtyAllocated) }}</strong>
      </div>
      <div class="sum-card">
        <span class="sum-label">판매불가</span>
        <strong class="sum-value warn">{{ num(summary.qtyUnsellable) }}</strong>
      </div>
      <div class="sum-card strong">
        <span class="sum-label">판매가능</span>
        <strong class="sum-value ok">{{ num(summary.qtyAvailable) }}</strong>
      </div>
      <span class="small dim sum-note">조회 조건 전체의 합계입니다 (현재 페이지 합이 아닙니다).</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="SKU / 제품명 / 빈코드 / 바코드"
          @enter="search()"
        />
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
        />
        <FormField
          v-model="filters.warehouseId"
          label="창고"
          type="select"
          empty-option="전체"
          :options="warehouseOptions"
          :disabled="!filters.plantId"
          @change="search()"
        />
        <FormField
          v-model="filters.locationId"
          label="빈코드"
          mono
          placeholder="1A-01-01"
          @enter="search()"
        />
        <FormField
          v-model="filters.vendorId"
          label="거래처"
          mono
          placeholder="위탁 재고만"
          @enter="search()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
          <button class="btn" @click="resetFilters(); search()">초기화</button>
        </div>
      </div>

      <div class="quick">
        <button
          v-for="q in QUICK"
          :key="q.key || 'all'"
          :class="['chip', { on: quick === q.key }]"
          :title="q.desc"
          @click="quick = q.key"
        >
          {{ q.label }}
        </button>
      </div>

      <!-- 보유는 있는데 팔 수 없는 재고. 있는 줄 알고 주문을 받으면 결품이 난다 -->
      <div v-if="lockedCount && quick !== 'lockedOnly'" class="alert alert-warn m-2">
        <span class="alert-icon">⚠</span>
        <span>
          이 페이지에 <strong>묶인 재고 {{ lockedCount }}건</strong>이 있습니다 — 보유는 있지만
          전량이 할당·판매불가라 팔 수 없습니다. '묶여 있음' 으로 전체를 확인하세요.
        </span>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="stockSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        :selected-key="picked?.stockSeq ?? null"
        :muted-when="(s) => s.qtyOnHand === 0"
        empty-text="조건에 맞는 재고가 없습니다."
        @row-click="openDetail"
      >
        <template #cell-locationFullCode="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.plantName }} · {{ row.warehouseName }}</div>
        </template>

        <template #cell-skuId="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.colorCode }} / {{ row.sizeCode }}</div>
        </template>

        <template #cell-productName="{ row, value }">
          {{ value }}
          <div v-if="row.brandName" class="small dim">{{ row.brandName }}</div>
        </template>

        <template #cell-vendorName="{ row, value }">
          <span v-if="value">{{ value }}</span>
          <span v-else class="dim" title="자사 재고입니다">자사</span>
        </template>

        <template #cell-qtyOnHand="{ value }">{{ num(value) }}</template>
        <template #cell-qtyAllocated="{ value }">
          <span :class="{ dim: !value }">{{ num(value) }}</span>
        </template>
        <template #cell-qtyUnsellable="{ value }">
          <span :class="value ? 'warn' : 'dim'">{{ num(value) }}</span>
        </template>

        <template #cell-qtyAvailable="{ row, value }">
          <strong :class="value > 0 ? 'ok' : 'danger'">{{ num(value) }}</strong>
          <span v-if="row.locked" class="lock-mark" title="보유는 있지만 전량 묶여 있습니다">묶임</span>
        </template>

        <template #cell-lastCountedAt="{ row, value }">
          <span v-if="value" class="small">{{ day(value) }}</span>
          <span v-else class="small warn" title="한 번도 실사하지 않았습니다">미실사</span>
        </template>

        <template #cell-_act="{ row }">
          <button class="btn btn-sm" @click.stop="goHistory(row.stockSeq, 'history')">이력</button>
        </template>
      </DataTable>

      <!-- 서버 페이징이라 DataTable 의 내장 페이저를 쓰지 않는다 -->
      <div class="pager">
        <span class="small dim">총 {{ num(total) }}건 · {{ page }} / {{ totalPages }} 페이지</span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(1)">« 처음</button>
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">‹ 이전</button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">다음 ›</button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(totalPages)">마지막 »</button>
        </div>
      </div>
    </div>

    <!-- ── 재고 상세 (INV-PG-002) ───────────────────────────── -->
    <ModalDialog
      v-if="picked"
      title="재고 상세"
      :subtitle="`${picked.locationFullCode} · ${picked.skuId}`"
      @close="picked = null"
    >
      <div class="qty-grid">
        <div class="qty-cell">
          <span class="qty-label">보유</span>
          <strong class="qty-value">{{ num(picked.qtyOnHand) }}</strong>
          <span class="small dim">창고에 실제로 있는 수량</span>
        </div>
        <div class="qty-op">−</div>
        <div class="qty-cell">
          <span class="qty-label">할당</span>
          <strong class="qty-value">{{ num(picked.qtyAllocated) }}</strong>
          <span class="small dim">주문이 잡아 둔 수량</span>
        </div>
        <div class="qty-op">−</div>
        <div class="qty-cell">
          <span class="qty-label">판매불가</span>
          <strong class="qty-value warn">{{ num(picked.qtyUnsellable) }}</strong>
          <span class="small dim">불량 · 검수대기</span>
        </div>
        <div class="qty-op">=</div>
        <div class="qty-cell strong">
          <span class="qty-label">판매가능</span>
          <strong class="qty-value" :class="picked.qtyAvailable > 0 ? 'ok' : 'danger'">
            {{ num(picked.qtyAvailable) }}
          </strong>
          <span class="small dim">서버가 계산합니다</span>
        </div>
      </div>

      <div v-if="picked.locked" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span>
        <span>
          보유는 <strong>{{ num(picked.qtyOnHand) }}</strong> 개지만 전량이 할당·판매불가라
          팔 수 없습니다. 주문 취소로 할당이 풀리거나 판매불가가 정상으로 돌아와야 다시 팔립니다.
        </span>
      </div>
      <div v-if="picked.neverCounted" class="alert alert-info mt-2">
        <span class="alert-icon">ℹ</span>
        <span>
          실사 이력이 없습니다. 장부 수량과 실물이 같은지 아직 확인한 적이 없다는 뜻입니다.
        </span>
      </div>

      <div class="detail-grid mt-2">
        <div><span class="dt">재고주소</span><span class="dd code">{{ picked.locationFullCode }}</span></div>
        <div><span class="dt">플랜트</span><span class="dd">{{ picked.plantName }} ({{ picked.plantId }})</span></div>
        <div>
          <span class="dt">창고</span>
          <span class="dd">
            {{ picked.warehouseName }}
            <CodeBadge group="WH_TYPE" :code="picked.warehouseType" />
          </span>
        </div>
        <div><span class="dt">빈</span><span class="dd code">{{ picked.locationId }}</span></div>
        <div><span class="dt">SKU</span><span class="dd code">{{ picked.skuId }}</span></div>
        <div><span class="dt">제품</span><span class="dd">{{ picked.productName }} ({{ picked.productId }})</span></div>
        <div>
          <span class="dt">색상 / 사이즈</span>
          <span class="dd">
            <CodeBadge group="COLOR" :code="picked.colorCode" />
            <span class="badge">{{ picked.sizeCode }}</span>
          </span>
        </div>
        <div><span class="dt">브랜드</span><span class="dd">{{ picked.brandName ?? '-' }}</span></div>
        <div>
          <span class="dt">거래처</span>
          <span class="dd">
            <template v-if="picked.vendorId">{{ picked.vendorName }} ({{ picked.vendorId }})</template>
            <span v-else class="dim">자사 재고</span>
          </span>
        </div>
        <div>
          <span class="dt">최근 실사</span>
          <span class="dd">{{ day(picked.lastCountedAt) ?? '없음' }}</span>
        </div>
        <div><span class="dt">최초 생성</span><span class="dd small">{{ picked.createdAt }} · {{ picked.createdBy }}</span></div>
        <div><span class="dt">최근 변경</span><span class="dd small">{{ picked.updatedAt ?? '-' }} · {{ picked.updatedBy ?? '-' }}</span></div>
      </div>

      <template #footer>
        <span class="left small dim">
          수량은 이 화면에서 고칠 수 없습니다. 입고 · 출고 · 조정 · 실사로만 바뀝니다.
        </span>
        <button class="btn" @click="goHistory(picked.stockSeq, 'alloc')">할당 이력</button>
        <button class="btn btn-primary" @click="goHistory(picked.stockSeq, 'history')">이동 이력</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.sum-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.sum-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 108px;
  padding: 8px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--bg-card, #fff);
}
.sum-card.strong {
  border-color: var(--c-blue, #2563eb);
}
.sum-label {
  font-size: 12px;
  color: var(--fg-dim, #6b7280);
}
.sum-value {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}
.sum-note {
  align-self: center;
  flex: 1 1 200px;
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

.lock-mark {
  margin-left: 5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--c-amber, #f59e0b);
  color: #fff;
  font-size: 11px;
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

.qty-grid {
  display: flex;
  align-items: stretch;
  gap: 6px;
  flex-wrap: wrap;
}
.qty-cell {
  flex: 1 1 96px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
}
.qty-cell.strong {
  border-color: var(--c-blue, #2563eb);
}
.qty-label {
  font-size: 12px;
  color: var(--fg-dim, #6b7280);
}
.qty-value {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}
.qty-op {
  align-self: center;
  color: var(--fg-dim, #6b7280);
  font-size: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 18px;
}
.detail-grid > div {
  display: flex;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--line, #e5e7eb);
}
.dt {
  flex: 0 0 92px;
  color: var(--fg-dim, #6b7280);
  font-size: 12px;
}
.dd {
  flex: 1;
  min-width: 0;
  word-break: break-all;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.m-2 {
  margin: 10px 14px;
}

@media (max-width: 720px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
