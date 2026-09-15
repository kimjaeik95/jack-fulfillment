<script setup>
/**
 * 발주 진행현황 (PUR-PG-005).
 *
 * 구매오더 목록과 같은 데이터를 본다. 다른 것은 <b>무엇부터 보느냐</b>다.
 *
 *   목록      최근에 만든 것부터. "내가 뭘 작성했더라"
 *   진행현황  급한 납기부터, 아직 덜 들어온 것만. "뭘 독촉해야 하나"
 *
 * 그래서 작성중 · 취소 · 입고완료는 여기 없다. 작성중은 아직 나가지
 * 않아 기다릴 물건이 없고, 나머지 둘은 끝난 건이다. 끝난 건을 섞으면
 * 정작 독촉할 것이 묻힌다.
 *
 * 기입고수량을 올리는 것은 입고 검수(INB-004)다. 그 기능이 붙기 전까지
 * 여기 진행률은 전부 0% 이고, 그것이 맞는 표시다 — 아직 아무것도 들어온
 * 적이 없다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as orderApi from '@/api/purchaseOrder.js'
import * as supplierApi from '@/api/supplier.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()

const size = orderApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: '',
  supplierId: '',
  /** 납기가 지난 것만 — 지금 전화를 돌려야 할 대상 */
  overdueOnly: '',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await orderApi.progress({ ...filters, page: page.value, size })
    rows.value = data.rows
    total.value = data.total
  } catch (e) {
    rows.value = []
    total.value = 0
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

const suppliers = ref([])

onMounted(async () => {
  await hierarchy.loadPlants(false)
  try {
    const data = await supplierApi.list({ useYn: 'Y', size: 0 })
    suppliers.value = data.rows
  } catch {
    suppliers.value = []
  }
  await fetchPage()
})

const supplierOptions = computed(() =>
  suppliers.value.map((s) => ({ value: s.supplierId, label: s.supplierName })),
)

/* 지금 화면에 뜬 것들의 요약. 목록을 세지 않고도 규모가 보여야 한다. */
const overdueCount = computed(() => rows.value.filter((r) => r.overdue).length)
const openQty = computed(() => rows.value.reduce((s, r) => s + (r.remainQty ?? 0), 0))

const columns = [
  { key: 'dueDate', label: '납기', width: '96px', align: 'center' },
  { key: 'orderNo', label: '발주번호', width: '148px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '130px' },
  { key: 'plantName', label: '입고 센터', width: '108px' },
  { key: '_progress', label: '진행', width: '150px' },
  { key: 'remainQty', label: '잔량', width: '72px', align: 'right' },
  { key: 'orderDate', label: '발주일', width: '96px', align: 'center' },
  { key: 'orderStatus', label: '상태', width: '84px', align: 'center' },
]

const detail = ref(null)

async function openDetail(row) {
  try {
    detail.value = await orderApi.detail(row.orderSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const won = (v) => (v === null || v === undefined ? '-' : nf.format(Math.round(Number(v))))

/** 남은 날짜. 음수면 이미 지났다. */
function daysLeft(dueDate) {
  if (!dueDate) return null
  const due = new Date(`${dueDate}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((due - today) / 86400000)
}

const denyReason = computed(() => session.denyReason('PUR_PO_ISSUE', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">발주 진행현황</h1>
        <p class="page-desc">
          아직 물건이 덜 들어온 발주만, <strong>급한 납기부터</strong> 봅니다.
          작성중 · 취소 · 입고완료는 여기 없습니다 — 끝난 건을 섞으면 정작
          독촉할 것이 묻힙니다.
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="denyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ denyReason }}</span>
    </div>

    <!-- 목록을 세지 않고도 규모가 보여야 한다 -->
    <div class="summary">
      <div class="summary-item">
        <span class="summary-label">기다리는 발주</span>
        <strong class="summary-value">{{ num(total) }}</strong>
      </div>
      <div class="summary-item" :class="{ alarm: overdueCount > 0 }">
        <span class="summary-label">납기 지남</span>
        <strong class="summary-value">{{ num(overdueCount) }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">안 들어온 수량</span>
        <strong class="summary-value">{{ num(openQty) }}</strong>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="발주번호 / 공급처 / 비고"
          @enter="search()"
        />
        <FormField
          v-model="filters.supplierId"
          label="공급처"
          type="select"
          empty-option="전체"
          :options="supplierOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.plantId"
          label="입고 센터"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.overdueOnly"
          label="납기"
          type="select"
          empty-option="전체"
          :options="[{ value: 'Y', label: '지난 것만' }]"
          @change="search()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="orderSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        empty-text="기다리는 발주가 없습니다. 모두 들어왔거나, 아직 낸 발주가 없습니다."
        @row-click="openDetail"
      >
        <!-- 날짜만으로는 급한지 모른다. 며칠 남았는지가 판단의 단위다. -->
        <template #cell-dueDate="{ row, value }">
          <span :class="{ danger: row.overdue }">{{ value }}</span>
          <div class="small" :class="row.overdue ? 'danger' : 'dim'">
            <template v-if="daysLeft(value) === null">-</template>
            <template v-else-if="daysLeft(value) < 0">{{ -daysLeft(value) }}일 지남</template>
            <template v-else-if="daysLeft(value) === 0">오늘</template>
            <template v-else>{{ daysLeft(value) }}일 남음</template>
          </div>
        </template>

        <template #cell-_progress="{ row }">
          <div class="bar">
            <div
              class="bar-fill"
              :class="{ started: row.totalReceivedQty > 0 }"
              :style="{ width: `${Math.min(100, row.progressPercent)}%` }"
            ></div>
          </div>
          <div class="small dim">
            {{ num(row.totalReceivedQty) }} / {{ num(row.totalOrderQty) }} ·
            {{ row.progressPercent }}%
          </div>
        </template>

        <template #cell-remainQty="{ value }">
          <strong>{{ num(value) }}</strong>
        </template>

        <template #cell-orderStatus="{ value }">
          <CodeBadge group="ORDER_STATUS" :code="value" />
        </template>
      </DataTable>

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

    <!-- 줄별로 무엇이 얼마나 남았는지. 독촉은 발주 단위가 아니라 품목 단위다. -->
    <ModalDialog
      v-if="detail"
      :title="detail.orderNo"
      :subtitle="`${detail.supplierName} → ${detail.plantName} · 납기 ${detail.dueDate}`"
      size="wide"
      @close="detail = null"
    >
      <div class="detail-head">
        <CodeBadge group="ORDER_STATUS" :code="detail.orderStatus" />
        <span v-if="detail.orderDate">발주일 <strong>{{ detail.orderDate }}</strong></span>
        <span v-if="detail.issuedByName">발주자 <strong>{{ detail.issuedByName }}</strong></span>
        <span>
          발주 <strong>{{ num(detail.totalOrderQty) }}</strong>
          / 입고 <strong :class="detail.totalReceivedQty ? 'ok' : 'dim'">
            {{ num(detail.totalReceivedQty) }}
          </strong>
          <span class="dim"> · 잔량 {{ num(detail.remainQty) }}</span>
        </span>
        <span v-if="detail.overdue" class="danger">
          <strong>납기 {{ -daysLeft(detail.dueDate) }}일 지남</strong>
        </span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 32px" class="right">#</th>
            <th style="width: 170px">SKU</th>
            <th style="width: 160px">제품</th>
            <th style="width: 64px" class="right">발주</th>
            <th style="width: 64px" class="right">입고</th>
            <th style="width: 64px" class="right">잔량</th>
            <th style="width: 120px">진행</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in detail.lines" :key="l.lineSeq">
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.skuId }}</span></td>
            <td>
              {{ l.productName }}
              <div class="small dim">{{ l.colorCode }} / {{ l.sizeCode }}</div>
            </td>
            <td class="num">{{ num(l.orderQty) }}</td>
            <td class="num">
              <span :class="l.overReceived ? 'warn' : l.received ? 'ok' : ''">
                {{ num(l.receivedQty) }}
              </span>
            </td>
            <td class="num">
              <span :class="{ warn: l.remainQty < 0 }">{{ num(l.remainQty) }}</span>
            </td>
            <td>
              <div class="bar">
                <div
                  class="bar-fill"
                  :class="{ started: l.receivedQty > 0, over: l.overReceived }"
                  :style="{ width: `${Math.min(100, l.progressPercent)}%` }"
                ></div>
              </div>
              <div v-if="l.overReceived" class="small warn">초과입고</div>
            </td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          금액 {{ won(detail.totalAmount) }} 원 · 결제 {{ detail.payTerm }}
        </span>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.summary {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.summary-item {
  flex: 1 1 140px;
  padding: 10px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--bg-card, #fff);
}
.summary-item.alarm {
  border-color: var(--c-red, #dc2626);
}
.summary-item.alarm .summary-value {
  color: var(--c-red, #dc2626);
}
.summary-label {
  display: block;
  font-size: 12px;
  color: var(--fg-dim, #6b7280);
}
.summary-value {
  font-size: 20px;
}
.bar {
  height: 6px;
  border-radius: 3px;
  background: var(--line, #e5e7eb);
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: var(--line-strong, #9ca3af);
}
.bar-fill.started {
  background: var(--c-green, #16a34a);
}
.bar-fill.over {
  background: var(--c-amber, #b45309);
}
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
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
</style>
