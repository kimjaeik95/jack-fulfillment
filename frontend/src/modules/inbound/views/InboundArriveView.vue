<script setup>
/**
 * 입하 등록 (INB-PG-002).
 *
 * 차가 왔다. 여기서 기록하는 것은 <b>차에서 내린 개수</b>이지 우리가 받은
 * 수량이 아니다. 세어 보면 달라질 수 있고, 그 차이를 찾는 것이 검수다.
 *
 * 그래서 재고는 여기서 움직이지 않는다. 물건이 마당에 있는 것과 팔 수
 * 있는 재고가 된 것은 다르다.
 *
 * 화면이 아직 안 온 것만 보여 준다. 이미 받은 것은 찍을 일이 없고, 오늘
 * 받을 것이 맨 위에 와야 한다 — 그래서 예정일이 가까운 순이다.
 *
 * 수량은 예정수량으로 미리 채워 둔다. 대부분은 맞게 오고, 줄마다 같은
 * 숫자를 다시 치게 하면 오타만 는다. 다른 줄만 고치면 된다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as inboundApi from '@/api/inbound.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const size = inboundApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: '',
  warehouseId: '',
  inboundType: '',
  /** 예정일이 지났는데 아직 안 온 것만 — 공급처에 전화할 대상 */
  overdueOnly: '',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await inboundApi.pending({ ...filters, page: page.value, size })
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

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await fetchPage()
})

/* 오늘 받을 것과 지난 것 — 목록을 세지 않고도 규모가 보여야 한다 */
const todayCount = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return rows.value.filter((r) => r.plannedDate === today).length
})
const overdueCount = computed(() => rows.value.filter((r) => r.overdue).length)

const columns = [
  { key: 'plannedDate', label: '예정일', width: '100px', align: 'center' },
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: 'inboundType', label: '종류', width: '84px', align: 'center' },
  { key: 'supplierName', label: '공급처', width: '124px' },
  { key: '_where', label: '받을 곳', width: '150px' },
  { key: 'lineCount', label: '줄', width: '48px', align: 'right' },
  { key: 'totalPlannedQty', label: '예정수량', width: '86px', align: 'right' },
  { key: '_act', label: '', width: '92px', align: 'right' },
]

/* ── 입하 입력 ──────────────────────────────────────────────── */

const arriving = ref(null)
const busy = ref(false)
const serverError = ref('')
const arrive = reactive({ vehicleNo: '', driverName: '', remark: '' })
/** 줄별 입하수량 — 예정수량으로 미리 채운다 */
const qty = reactive({})

async function openArrive(row) {
  serverError.value = ''
  Object.assign(arrive, { vehicleNo: '', driverName: '', remark: '' })
  for (const k of Object.keys(qty)) delete qty[k]
  try {
    const full = await inboundApi.detail(row.inboundSeq)
    for (const l of full.lines) qty[l.lineSeq] = l.plannedQty
    arriving.value = full
  } catch (e) {
    loadError.value = e.message
  }
}

/** 예정과 다르게 친 줄 — 저장 전에 몇 줄인지 보여 준다 */
const changed = computed(() =>
  (arriving.value?.lines ?? []).filter((l) => Number(qty[l.lineSeq]) !== l.plannedQty),
)

const totalArrived = computed(() =>
  (arriving.value?.lines ?? []).reduce((sum, l) => sum + (Number(qty[l.lineSeq]) || 0), 0),
)

const diff = computed(() => totalArrived.value - (arriving.value?.totalPlannedQty ?? 0))

const arriveValid = computed(
  () =>
    !busy.value &&
    (arriving.value?.lines ?? []).every((l) => {
      const v = qty[l.lineSeq]
      return v !== '' && v !== null && v !== undefined && Number(v) >= 0
    }),
)

async function submitArrive() {
  busy.value = true
  serverError.value = ''
  try {
    const { inbound, warning } = await inboundApi.arrive(arriving.value.inboundSeq, {
      vehicleNo: arrive.vehicleNo || null,
      driverName: arrive.driverName || null,
      remark: arrive.remark || null,
      // 바꾼 줄만 보낸다. 안 보낸 줄은 예정대로 내린 것으로 본다.
      lines: changed.value.map((l) => ({
        lineSeq: l.lineSeq,
        arrivedQty: Number(qty[l.lineSeq]),
      })),
    })
    toast.success(`${inbound.inboundNo} 입하 — ${inbound.totalArrivedQty} 개를 받았습니다.`)
    if (warning) toast.warn(warning)
    arriving.value = null
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const canArrive = computed(() => session.can('INB_ARRIVE', 'C'))
const denyReason = computed(() => session.denyReason('INB_ARRIVE', 'C'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">입하 등록</h1>
        <p class="page-desc">
          차가 도착하면 <strong>내린 개수</strong>를 적습니다. 이건 우리가 받은 수량이
          아닙니다 — 세어 보면 달라질 수 있고, 그 차이를 찾는 것이 검수입니다.
          <strong>재고는 아직 늘지 않습니다.</strong> 아직 안 온 예정만, 급한 날짜부터
          보여 줍니다.
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="denyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ denyReason }}</span>
    </div>

    <div class="summary">
      <div class="summary-item">
        <span class="summary-label">받을 예정</span>
        <strong class="summary-value">{{ num(total) }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">오늘 도착</span>
        <strong class="summary-value">{{ num(todayCount) }}</strong>
      </div>
      <div class="summary-item" :class="{ alarm: overdueCount > 0 }">
        <span class="summary-label">예정일 지남</span>
        <strong class="summary-value">{{ num(overdueCount) }}</strong>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="입고번호 / 발주번호 / 공급처"
          @enter="search()"
        />
        <FormField
          v-model="filters.plantId"
          label="센터"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.inboundType"
          label="종류"
          type="select"
          empty-option="전체"
          :options="codeOptions('INBOUND_TYPE')"
          @change="search()"
        />
        <FormField
          v-model="filters.overdueOnly"
          label="예정일"
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
        row-key="inboundSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        empty-text="받을 예정이 없습니다. 모두 입하했거나, 아직 예정을 세우지 않았습니다."
        @row-click="openArrive"
      >
        <template #cell-plannedDate="{ row, value }">
          <span :class="{ danger: row.overdue }">{{ value }}</span>
          <div v-if="row.overdue" class="small danger">지남</div>
        </template>

        <template #cell-inboundType="{ value }">
          <CodeBadge group="INBOUND_TYPE" :code="value" />
        </template>

        <template #cell-_where="{ row }">
          {{ row.plantName }}
          <div class="small dim">{{ row.warehouseName }}</div>
        </template>

        <template #cell-lineCount="{ value }">{{ num(value) }}</template>
        <template #cell-totalPlannedQty="{ value }">
          <strong>{{ num(value) }}</strong>
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              class="btn btn-sm btn-primary"
              :disabled="!canArrive"
              :title="denyReason ?? '입하 등록'"
              @click.stop="openArrive(row)"
            >
              입하
            </button>
          </div>
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

    <!-- ── 입하 입력 ────────────────────────────────────────── -->
    <ModalDialog
      v-if="arriving"
      title="입하 등록"
      :subtitle="`${arriving.inboundNo} · ${arriving.supplierName ?? '공급처 없음'} → ${arriving.warehouseName}`"
      size="wide"
      @close="arriving = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="arrive.vehicleNo"
          label="차량번호"
          placeholder="12가3456"
          help="수량이 안 맞거나 파손이 나오면 이게 첫 번째 단서입니다."
        />
        <FormField v-model="arrive.driverName" label="기사명" placeholder="선택" />
        <FormField
          v-model="arrive.remark"
          label="비고"
          class="span-2"
          placeholder="파손 외관 · 지연 사유 등"
        />
      </div>

      <div class="lines-head">
        <strong>
          내린 개수 — 예정 {{ num(arriving.totalPlannedQty) }} / 입하 {{ num(totalArrived) }}
          <span v-if="diff !== 0" class="warn">({{ diff > 0 ? '+' : '' }}{{ diff }})</span>
        </strong>
        <span class="small dim">예정수량으로 채워 뒀습니다. 다른 줄만 고치세요.</span>
      </div>

      <table class="table lines">
        <thead>
          <tr>
            <th style="width: 36px" class="right">#</th>
            <th style="width: 175px">SKU</th>
            <th style="width: 170px">제품</th>
            <th style="width: 76px" class="right">예정</th>
            <th style="width: 96px">내린 개수</th>
            <th style="width: 76px" class="right">차이</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="l in arriving.lines"
            :key="l.lineSeq"
            :class="{ diff: Number(qty[l.lineSeq]) !== l.plannedQty }"
          >
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.skuId }}</span></td>
            <td>
              {{ l.productName }}
              <div class="small dim">{{ l.colorCode }} / {{ l.sizeCode }}</div>
            </td>
            <td class="num">{{ num(l.plannedQty) }}</td>
            <!-- 0 도 정당하다 — 그 SKU 는 아예 안 온 것이다 -->
            <td><input v-model.number="qty[l.lineSeq]" type="number" class="input" min="0" /></td>
            <td class="num">
              <strong
                v-if="Number(qty[l.lineSeq]) !== l.plannedQty"
                class="warn"
              >
                {{ Number(qty[l.lineSeq]) - l.plannedQty > 0 ? '+' : ''
                }}{{ Number(qty[l.lineSeq]) - l.plannedQty }}
              </strong>
              <span v-else class="dim">-</span>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="changed.length" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span>
        <span>
          예정과 다른 줄이 <strong>{{ changed.length }}</strong> 개입니다. 막지 않습니다 —
          덜 오거나 더 오는 일은 실제로 있고, <strong>검수에서 판정합니다.</strong>
        </span>
      </div>

      <template #footer>
        <span class="left small dim">
          입하해도 재고는 늘지 않습니다. 검수와 적치가 끝나야 팔 수 있는 재고가 됩니다.
        </span>
        <button class="btn" :disabled="busy" @click="arriving = null">닫기</button>
        <button
          class="btn btn-primary"
          :disabled="!arriveValid || !canArrive"
          @click="submitArrive()"
        >
          <span v-if="busy" class="spinner"></span>
          입하 등록
        </button>
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
.lines-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 14px 0 8px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.lines .input {
  min-height: 28px;
  padding: 3px 6px;
  font-size: 13px;
  width: 100%;
}
.diff td {
  background: var(--c-amber-soft, #fffbeb);
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
