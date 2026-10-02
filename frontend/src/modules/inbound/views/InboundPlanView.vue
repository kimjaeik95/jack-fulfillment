<script setup>
/**
 * 입고예정 (PUR-PG-006, INB-PG-001).
 *
 * 발주가 "공급처와의 약속" 이라면 입고예정은 <b>우리 창고가 받을 준비</b>다.
 * 언제 · 어디로 · 무엇이 몇 개 오는지 미리 적어 둔다.
 *
 * 만드는 길이 둘이다.
 *   발주에서  발주번호를 넣고 불러오면 잔량이 남은 줄을 그대로 담는다.
 *             수량을 사람이 옮겨 적으면 틀리고, 틀린 줄은 물건이 도착한
 *             다음에야 드러난다.
 *   직접      반품 · 이동입고는 발주가 없다. 줄을 직접 담는다.
 *
 * 여기까지는 재고가 움직이지 않는다. 물건이 도착한 것과 우리 재고가 된
 * 것은 다르다 — 검수와 적치가 끝나야 팔 수 있는 재고다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as inboundApi from '@/api/inbound.js'
import * as stockApi from '@/api/stock.js'
import * as partnerApi from '@/api/partner.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import SkuPicker from '@/components/SkuPicker.vue'
import OrderPicker from '../components/OrderPicker.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const size = inboundApi.PAGE_SIZE

/* ── 목록 ───────────────────────────────────────────────────── */

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
  inboundStatus: '',
  fromDate: '',
  toDate: '',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await inboundApi.list({ ...filters, page: page.value, size })
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
  // 창고까지 불러야 한다. warehouseOptionsOf() 가 이 목록을 거르는 것이라,
  // 안 부르면 센터를 골라도 창고 드롭다운이 비어 있다.
  await hierarchy.loadPlants(false)
  await hierarchy.loadWarehouses(false)
  try {
    const data = await partnerApi.suppliers({ useYn: 'Y', size: 0 })
    suppliers.value = data.rows
  } catch {
    // 공급처를 못 읽어도 예정은 세울 수 있다 — 발주에서 오면 자동으로 붙는다
    suppliers.value = []
  }
  await fetchPage()
})

const supplierOptions = computed(() =>
  suppliers.value.map((s) => ({ value: s.partnerId, label: s.partnerName })),
)

const columns = [
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: 'inboundType', label: '종류', width: '84px', align: 'center' },
  { key: 'orderNo', label: '발주번호', width: '145px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '120px' },
  { key: '_where', label: '받을 곳', width: '150px' },
  { key: 'plannedDate', label: '예정일', width: '100px', align: 'center' },
  { key: '_qty', label: '예정 / 입하', width: '110px', align: 'right' },
  { key: 'inboundStatus', label: '상태', width: '80px', align: 'center' },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

/* ── 작성 ───────────────────────────────────────────────────── */

const editing = ref(false)
const editSeq = ref(null)
const picking = ref(false)
const busy = ref(false)
const serverError = ref('')

/** 기본 예정일은 사흘 뒤. 오늘 시켜서 오늘 오는 물건은 없다. */
const defaultPlannedDate = () => stockApi.daysAgo(-3)

const form = reactive({
  inboundType: 'PURCHASE',
  orderNo: '',
  plantId: '',
  warehouseId: '',
  supplierId: '',
  plannedDate: defaultPlannedDate(),
  remark: '',
})
/** 담은 줄 — { sku, plannedQty, orderLineSeq, orderRemainQty, remark } */
const lines = ref([])

const pickedIds = computed(() => lines.value.map((l) => l.sku.skuId))
/** 구매입고는 발주가 근거다 — 없으면 만들 수 없다 */
const needsOrder = computed(() => form.inboundType === 'PURCHASE')

function openCreate() {
  editSeq.value = null
  Object.assign(form, {
    inboundType: 'PURCHASE',
    orderNo: '',
    plantId: '',
    warehouseId: '',
    supplierId: '',
    plannedDate: defaultPlannedDate(),
    remark: '',
  })
  lines.value = []
  serverError.value = ''
  editing.value = true
}

/* ── 발주에서 불러오기 (PUR-PG-006) ─────────────────────────── */

const loadingOrder = ref(false)

/**
 * 발주번호로 잔량이 남은 줄을 담아 온다.
 *
 * 이미 다른 예정에 잡힌 수량을 뺀 '지금 더 예정할 수 있는 수량' 이 온다.
 * 발주 잔량만 보여 주면 두 번 예정하고 나서야 초과를 안다.
 */
/** 담았으면 true. 고르기에서 실패하면 번호를 되돌려야 해서 결과를 돌려준다. */
async function pullFromOrder() {
  const no = form.orderNo.trim()
  if (!no) return false
  loadingOrder.value = true
  serverError.value = ''
  try {
    const picked = await inboundApi.fromOrder(no)
    lines.value = picked.map((l) => ({
      sku: {
        skuId: l.skuId,
        productName: l.productName,
        colorCode: l.colorCode,
        sizeCode: l.sizeCode,
      },
      plannedQty: l.plannedQty,
      orderLineSeq: l.orderLineSeq,
      orderRemainQty: l.plannedQty,
      remark: '',
    }))
    toast.success(`${no} 에서 ${lines.value.length} 품목을 담았습니다.`)
    return true
  } catch (e) {
    serverError.value = e.message
    return false
  } finally {
    loadingOrder.value = false
  }
}

/**
  * 발주 고르기.
  *
  * 발주번호를 손으로 적게 하면 어딘가에서 옮겨 적어야 하고, 한 글자만 틀려도
  * '없는 발주' 로 막힌다. 목록에서 고르면 그 일이 없고, 아직 안 들어온 발주만
  * 보여 주므로 고른 것에는 반드시 담을 것이 있다.
  */
const pickingOrder = ref(false)

/**
 * 고른 발주를 반영한다.
 *
 * 번호만 채우고 끝내지 않는다. 센터와 공급처도 그 발주의 것으로 맞춘다 —
 * 발주가 근거인데 센터가 다르면 물건이 엉뚱한 곳으로 예정되고, 그 어긋남은
 * 저장할 때야 드러난다.
 */
async function pickOrder(order) {
  pickingOrder.value = false

  /*
   * 못 담으면 되돌린다.
   *
   * 전에는 번호를 먼저 박고 담았는데, 담기가 실패해도 그 번호가 입력칸에
   * 남았다. 화면에는 발주번호가 있고 품목은 비어 있는, 무엇이 일어났는지
   * 알 수 없는 상태가 된다 — 저장을 눌러도 '줄이 없다' 로 막히고, 번호를
   * 지워야 한다는 것을 아무도 알려 주지 않는다.
   */
  const before = { orderNo: form.orderNo, plantId: form.plantId, supplierId: form.supplierId }
  form.orderNo = order.orderNo
  if (order.plantId) form.plantId = order.plantId
  if (order.supplierId) form.supplierId = order.supplierId

  const ok = await pullFromOrder()
  if (!ok) Object.assign(form, before)
}

function addLine(sku) {
  picking.value = false
  lines.value = [
    ...lines.value,
    { sku, plannedQty: 1, orderLineSeq: null, orderRemainQty: null, remark: '' },
  ]
}

function removeLine(i) {
  lines.value = lines.value.filter((_, idx) => idx !== i)
}

const lineError = (line) => {
  if (line.plannedQty === '' || line.plannedQty === null) return '예정수량을 입력하세요.'
  if (Number(line.plannedQty) < 1) return '1 이상이어야 합니다.'
  // 발주에서 온 줄은 잔량을 넘을 수 없다 (INB-002). 서버도 막지만
  // 저장 버튼을 눌러 본 뒤에 알면 늦다.
  if (line.orderRemainQty != null && Number(line.plannedQty) > line.orderRemainQty) {
    return `발주 잔량 ${line.orderRemainQty} 개를 넘습니다.`
  }
  return ''
}

const formValid = computed(
  () =>
    form.plantId &&
    form.warehouseId &&
    form.plannedDate &&
    (!needsOrder.value || form.orderNo.trim()) &&
    lines.value.length > 0 &&
    lines.value.every((l) => !lineError(l)) &&
    !busy.value,
)

const totalQty = computed(() =>
  lines.value.reduce((sum, l) => sum + (lineError(l) ? 0 : Number(l.plannedQty)), 0),
)

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const payload = {
      inboundType: form.inboundType,
      orderNo: form.orderNo.trim() || null,
      plantId: form.plantId,
      warehouseId: form.warehouseId,
      supplierId: form.supplierId || null,
      plannedDate: form.plannedDate,
      remark: form.remark || null,
      lines: lines.value.map((l) => ({
        skuId: l.sku.skuId,
        plannedQty: Number(l.plannedQty),
        orderLineSeq: l.orderLineSeq,
        remark: l.remark || null,
      })),
    }
    const { inbound, warning } = editSeq.value
      ? await inboundApi.update(editSeq.value, payload)
      : await inboundApi.create(payload)
    toast.success(`${inbound.inboundNo} — ${inbound.lineCount} 줄, ${inbound.plannedDate} 예정`)
    if (warning) toast.warn(warning)
    editing.value = false
    await search()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

/* ── 상세 · 취소 ────────────────────────────────────────────── */

const detail = ref(null)
const askCancel = ref(null)
const acting = ref(false)

async function openDetail(row) {
  try {
    detail.value = await inboundApi.detail(row.inboundSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/** 예정 상태면 담긴 줄을 그대로 꺼내 고칠 수 있다 */
async function openEdit(row) {
  try {
    const full = await inboundApi.detail(row.inboundSeq)
    editSeq.value = full.inboundSeq
    Object.assign(form, {
      inboundType: full.inboundType,
      orderNo: full.orderNo ?? '',
      plantId: full.plantId,
      warehouseId: full.warehouseId,
      supplierId: full.supplierId ?? '',
      plannedDate: full.plannedDate,
      remark: full.remark ?? '',
    })
    lines.value = full.lines.map((l) => ({
      sku: {
        skuId: l.skuId,
        productName: l.productName,
        colorCode: l.colorCode,
        sizeCode: l.sizeCode,
      },
      plannedQty: l.plannedQty,
      orderLineSeq: l.orderLineSeq,
      orderRemainQty: l.orderRemainQty,
      remark: l.remark ?? '',
    }))
    serverError.value = ''
    editing.value = true
  } catch (e) {
    loadError.value = e.message
  }
}

async function doCancel() {
  acting.value = true
  try {
    await inboundApi.cancel(askCancel.value.inboundSeq, '입고예정 취소')
    toast.success(`${askCancel.value.inboundNo} 을(를) 거둬들였습니다.`)
    askCancel.value = null
    detail.value = null
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
    askCancel.value = null
  } finally {
    acting.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const stamp = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const canCreate = computed(() => session.can('INB_PLAN', 'C'))
const canUpdate = computed(() => session.can('INB_PLAN', 'U'))
const canDelete = computed(() => session.can('INB_PLAN', 'D'))
const createDenyReason = computed(() => session.denyReason('INB_PLAN', 'C'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">입고 관리</h1>
        <p class="page-desc">
          <strong>입고예정을 세우고, 지난 입고를 되짚는 자리</strong>입니다.
          예정은 언제 · 어디로 · 무엇이 몇 개 오는지 미리 적어 두는 것이고,
          구매입고는 <strong>발주가 근거</strong>입니다. 예정수량은 발주 잔량을
          넘을 수 없습니다 — 실제로 더 들어오면 검수에서 초과입고로 판정합니다.
          여기까지는 재고가 움직이지 않습니다.
          <br />
          끝난 건도 모두 남아 있습니다. <strong>차량번호나 기사명으로도</strong>
          찾을 수 있습니다 — 나중에 문제가 생겼을 때 손에 있는 것은 대개 그쪽입니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '입고예정 등록'"
          @click="openCreate()"
        >
          + 입고예정
        </button>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="입고번호 / 발주번호 / 공급처 / 차량번호 / 기사명"
          @enter="search()"
        />
        <FormField v-model="filters.fromDate" label="예정일 시작" type="date" @change="search()" />
        <FormField v-model="filters.toDate" label="예정일 끝" type="date" @change="search()" />
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
          v-model="filters.inboundStatus"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('INBOUND_STATUS')"
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
        :muted-when="(r) => r.canceled"
        empty-text="조건에 맞는 입고예정이 없습니다."
        @row-click="openDetail"
      >
        <template #cell-inboundType="{ value }">
          <CodeBadge group="INBOUND_TYPE" :code="value" />
        </template>

        <template #cell-orderNo="{ value }">
          <span v-if="value" class="code">{{ value }}</span>
          <span v-else class="dim small">직접 등록</span>
        </template>

        <template #cell-_where="{ row }">
          {{ row.plantName }}
          <div class="small dim">{{ row.warehouseName }}</div>
        </template>

        <template #cell-plannedDate="{ row, value }">
          <span :class="{ danger: row.overdue }">{{ value }}</span>
          <div v-if="row.overdue" class="small danger">지남</div>
        </template>

        <!-- 예정과 입하를 한 칸에 둔다. 떼어 놓으면 '얼마나 왔나' 를 눈으로 빼야 한다. -->
        <template #cell-_qty="{ row }">
          <strong>{{ num(row.totalPlannedQty) }}</strong>
          <template v-if="row.hasArrived">
            <span class="dim"> / </span>
            <strong :class="row.arrivalDiffers ? 'warn' : 'ok'">
              {{ num(row.totalArrivedQty) }}
            </strong>
            <div v-if="row.arrivalDiffers" class="small warn">
              {{ row.arrivalDiff > 0 ? '+' : '' }}{{ row.arrivalDiff }}
            </div>
          </template>
          <span v-else class="dim"> / -</span>
        </template>

        <template #cell-inboundStatus="{ value }">
          <CodeBadge group="INBOUND_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <template v-if="row.planned">
              <button class="btn btn-sm" :disabled="!canUpdate" @click.stop="openEdit(row)">
                수정
              </button>
              <button
                class="btn btn-sm btn-danger"
                :disabled="!canDelete"
                @click.stop="askCancel = row"
              >
                취소
              </button>
            </template>
            <span v-else-if="row.arrived" class="small dim" title="검수 단계로 넘어갑니다">
              입하됨
            </span>
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

    <!-- ── 작성 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="editing"
      :title="editSeq ? '입고예정 수정' : '입고예정 등록'"
      size="wide"
      @close="editing = false"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.inboundType"
          label="입고 종류"
          type="select"
          required
          :options="codeOptions('INBOUND_TYPE')"
          :help="
            needsOrder
              ? '구매입고는 발주가 근거입니다. 발주번호가 필요합니다.'
              : '발주 없이 받는 물건입니다. 품목을 직접 담으세요.'
          "
        />
        <FormField
          v-model="form.plannedDate"
          label="입고 예정일"
          type="date"
          required
          help="언제 오는지. 창고가 인력과 자리를 잡는 근거입니다."
        />
        <FormField
          v-model="form.plantId"
          label="받을 센터"
          type="select"
          required
          empty-option="선택하세요"
          :options="hierarchy.plantOptions"
        />
        <FormField
          v-model="form.warehouseId"
          label="받을 창고"
          type="select"
          required
          empty-option="선택하세요"
          :options="form.plantId ? hierarchy.warehouseOptionsOf(form.plantId) : []"
          :disabled="!form.plantId"
          help="같은 센터에도 양품·불량 창고가 따로 있습니다."
        />
        <FormField
          v-model="form.supplierId"
          label="공급처"
          type="select"
          empty-option="발주에서 가져옴"
          :options="supplierOptions"
          help="발주가 있으면 비워 두세요 — 발주의 공급처를 씁니다."
        />
        <FormField v-model="form.remark" label="비고" />
      </div>

      <!--
        발주에서 불러오기 (PUR-PG-006).

        고르는 것이 기본이다. 번호를 손으로 적는 길도 남긴다 — 전화로 번호를
        받아 적는 경우가 있고, 목록이 길어지면 아는 번호를 치는 편이 빠르다.
      -->
      <div class="from-order">
        <FormField
          v-model="form.orderNo"
          :label="needsOrder ? '발주번호 *' : '발주번호'"
          mono
          placeholder="고르거나 번호를 직접 입력"
          help="고르면 그 발주의 센터 · 공급처까지 맞추고 잔량이 남은 줄을 담습니다."
          @enter="pullFromOrder()"
        />
        <button class="btn btn-primary" :disabled="loadingOrder" @click="pickingOrder = true">
          발주 고르기
        </button>
        <button class="btn" :disabled="!form.orderNo || loadingOrder" @click="pullFromOrder()">
          <span v-if="loadingOrder" class="spinner"></span>
          불러오기
        </button>
      </div>

      <div class="lines-head">
        <strong>받을 SKU {{ lines.length }} 품목 · 합계 {{ num(totalQty) }}</strong>
        <button class="btn btn-sm btn-primary" @click="picking = true">+ SKU 담기</button>
      </div>

      <div v-if="!lines.length" class="empty-note">
        담은 SKU 가 없습니다. 발주번호로 불러오거나 'SKU 담기' 로 한 품목 이상 담으세요.
      </div>

      <table v-else class="table lines">
        <thead>
          <tr>
            <th style="width: 175px">SKU</th>
            <th style="width: 170px">제품</th>
            <th style="width: 92px">예정수량</th>
            <th style="width: 84px" class="right">발주 잔량</th>
            <th>비고</th>
            <th style="width: 40px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in lines" :key="l.sku.skuId">
            <td>
              <span class="code">{{ l.sku.skuId }}</span>
              <div v-if="l.orderLineSeq" class="small dim">발주에서</div>
            </td>
            <td>
              {{ l.sku.productName }}
              <div class="small dim">{{ l.sku.colorCode }} / {{ l.sku.sizeCode }}</div>
            </td>
            <td><input v-model.number="l.plannedQty" type="number" class="input" min="1" /></td>
            <!-- 잔량이 보여야 '이만큼 예정해도 되나' 를 저장 전에 안다 -->
            <td class="num">
              <span v-if="l.orderRemainQty != null">{{ num(l.orderRemainQty) }}</span>
              <span v-else class="dim">-</span>
            </td>
            <td><input v-model="l.remark" class="input" placeholder="선택" /></td>
            <td><button class="btn btn-sm btn-danger" @click="removeLine(i)">×</button></td>
          </tr>
          <tr v-for="(l, i) in lines.filter((x) => lineError(x))" :key="`e${i}`">
            <td colspan="6" class="small danger">{{ l.sku.skuId }} — {{ lineError(l) }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          예정을 세워도 재고는 변하지 않습니다. 물건이 와서 검수를 통과해야 재고가 됩니다.
        </span>
        <button class="btn" :disabled="busy" @click="editing = false">취소</button>
        <button class="btn btn-primary" :disabled="!formValid" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          {{ editSeq ? '저장' : '예정 등록' }}
        </button>
      </template>
    </ModalDialog>

    <!--
      아직 안 들어온 발주만 보여 준다. 위에서 고른 센터 · 공급처가 있으면
      그 조건으로 좁힌다 — 고를 때마다 눈으로 대조하게 두지 않는다.
    -->
    <OrderPicker
      v-if="pickingOrder"
      :plant-id="form.plantId"
      :supplier-id="form.supplierId"
      @pick="pickOrder"
      @close="pickingOrder = false"
    />

    <SkuPicker
      v-if="picking"
      title="받을 SKU 담기"
      :picked-ids="pickedIds"
      @pick="addLine"
      @close="picking = false"
    />

    <!-- ── 상세 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="detail"
      :title="detail.inboundNo"
      :subtitle="`${detail.plantName} · ${detail.warehouseName} · ${detail.plannedDate} 예정`"
      size="wide"
      @close="detail = null"
    >
      <div class="detail-head">
        <CodeBadge group="INBOUND_STATUS" :code="detail.inboundStatus" />
        <CodeBadge group="INBOUND_TYPE" :code="detail.inboundType" />
        <span v-if="detail.orderNo">발주 <strong class="code">{{ detail.orderNo }}</strong></span>
        <span v-if="detail.supplierName">공급처 <strong>{{ detail.supplierName }}</strong></span>
        <span>
          예정 <strong>{{ num(detail.totalPlannedQty) }}</strong>
          <template v-if="detail.hasArrived">
            / 입하 <strong :class="detail.arrivalDiffers ? 'warn' : 'ok'">
              {{ num(detail.totalArrivedQty) }}
            </strong>
          </template>
        </span>
      </div>

      <!--
        입하 기록을 따로 뗀다.

        전에는 상태 뱃지들 사이에 '입하 홍길동 · 12가3456' 으로 작게 끼어
        있었다. 그런데 나중에 문제가 생겨 이 화면을 여는 사람은 <b>그것을
        보러</b> 온다 — 누가 가져왔고 몇 박스였나. 수량 옆에 흐리게 붙어
        있으면 찾느라 훑게 된다.

        기사명 · 박스 · 파렛트는 아예 빠져 있었다. 차량번호만으로는 전화를
        걸 수가 없다.
      -->
      <div v-if="detail.arrivedAt" class="arrive-box">
        <div class="arrive-title">입하 기록</div>
        <div class="arrive-grid">
          <div><span class="k">받은 때</span><strong>{{ stamp(detail.arrivedAt) }}</strong></div>
          <div><span class="k">받은 사람</span><strong>{{ detail.arrivedByName ?? detail.arrivedBy }}</strong></div>
          <div>
            <span class="k">차량번호</span>
            <strong :class="detail.vehicleNo ? 'code' : 'dim'">{{ detail.vehicleNo ?? '안 적음' }}</strong>
          </div>
          <div>
            <span class="k">기사</span>
            <strong :class="detail.driverName ? '' : 'dim'">{{ detail.driverName ?? '안 적음' }}</strong>
          </div>
          <div>
            <span class="k">파렛트</span>
            <strong :class="detail.palletCount != null ? '' : 'dim'">
              {{ detail.palletCount != null ? num(detail.palletCount) : '안 적음' }}
            </strong>
          </div>
          <div>
            <span class="k">박스</span>
            <strong :class="detail.boxCount != null ? '' : 'dim'">
              {{ detail.boxCount != null ? num(detail.boxCount) : '안 적음' }}
            </strong>
          </div>
        </div>
        <p v-if="detail.arriveRemark" class="arrive-remark small">
          💬 {{ detail.arriveRemark }}
        </p>
      </div>

      <!--
        초과입고.

        예정보다 많이 받은 건이다. 공급처 허용 오차를 넘으면 승인을 받아야
        완료되는데, 승인 화면은 검수중 · 적치중만 보여 줘서 <b>완료되는
        순간 어디서도 안 보이게</b> 된다.

        그런데 끝났다고 없던 일이 되지 않는다 — 예정보다 더 받았다는 것은
        공급처와 정산할 때 다시 꺼내야 하는 사실이다. 누가 언제 승인했는지도
        같이 남아야 한다.
      -->
      <div v-if="detail.overQty > 0" class="alert mt-2"
           :class="detail.overApproved ? 'alert-info' : 'alert-warn'">
        <span class="alert-icon">{{ detail.overApproved ? '✅' : '⚠' }}</span>
        <span>
          예정보다 <strong>{{ num(detail.overQty) }}개</strong> 많이 받았습니다
          (공급처 허용 오차 {{ num(detail.allowedOverQty) }}개).
          <template v-if="detail.overApproved">
            <br />
            <strong>{{ detail.overApprovedByName ?? detail.overApprovedBy }}</strong> 승인 ·
            {{ stamp(detail.overApprovedAt) }}
            <span v-if="detail.overApproveRemark"> · {{ detail.overApproveRemark }}</span>
          </template>
          <template v-else-if="detail.needsOverApproval">
            <br /><strong>아직 승인받지 않았습니다.</strong> 승인 전에는 입고를 완료할 수 없습니다.
          </template>
          <template v-else>
            <br />허용 오차 안이라 승인 없이 받았습니다.
          </template>
        </span>
      </div>

      <!--
        검수 · 적치 내역.

        작업 화면(검수 · 적치)에도 같은 표가 있지만, 그 화면들은 일이 끝나면
        목록에서 빠진다 — 끝난 뒤에 되짚을 수 있는 자리는 여기뿐이다.

        거부는 특히 돈과 직결된다. 공급처 청구서에 100개가 찍혀 왔을 때
        "3개는 파손이라 안 받았습니다" 를 대려면 언제 · 누가 · 왜 거부했는지가
        남아 있어야 한다.

        서버는 전부터 inspects · putaways 를 함께 보내고 있었다. 화면이
        안 그렸을 뿐이다.
      -->
      <div v-if="detail.inspects?.length" class="trail-box">
        <div class="arrive-title">검수 내역 — {{ detail.inspects.length }} 회</div>
        <table class="table trail">
          <thead>
            <tr>
              <th style="width: 44px" class="right">회차</th>
              <th style="width: 118px">언제</th>
              <th style="width: 86px">누가</th>
              <th style="width: 150px">SKU</th>
              <th style="width: 58px" class="right">받음</th>
              <th style="width: 58px" class="right">거부</th>
              <th>거부 사유</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in detail.inspects" :key="r.inspectSeq">
              <td class="num">{{ r.roundNo }}</td>
              <td class="small">{{ stamp(r.inspectedAt) }}</td>
              <td class="small">{{ r.inspectedByName ?? r.inspectedBy }}</td>
              <td><span class="code small">{{ r.skuId }}</span></td>
              <td class="num">{{ num(r.passedQty) }}</td>
              <td class="num">
                <strong v-if="r.rejectedQty" class="danger">{{ num(r.rejectedQty) }}</strong>
                <span v-else class="dim">-</span>
              </td>
              <td class="small">
                <template v-if="r.hasRejected">
                  {{ r.reasonName ?? r.reasonCode }}
                  <span v-if="r.remark" class="dim"> · {{ r.remark }}</span>
                </template>
                <span v-else class="dim">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="detail.putaways?.length" class="trail-box">
        <div class="arrive-title">적치 내역 — {{ detail.putaways.length }} 곳</div>
        <table class="table trail">
          <thead>
            <tr>
              <th style="width: 160px">자리</th>
              <th style="width: 150px">SKU</th>
              <th style="width: 58px" class="right">개수</th>
              <th style="width: 118px">언제</th>
              <th style="width: 86px">누가</th>
              <th style="width: 72px" class="center">재고 반영</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in detail.putaways" :key="p.putawaySeq">
              <td><span class="code">{{ p.locationFullCode ?? p.locationId }}</span></td>
              <td><span class="code small">{{ p.skuId }}</span></td>
              <td class="num">{{ num(p.qty) }}</td>
              <td class="small">{{ stamp(p.putawayAt) }}</td>
              <td class="small">{{ p.putawayByName ?? p.putawayBy }}</td>
              <!-- 재고가 되는 것은 입고완료다. 적치만으로는 아직 아니다 (INB-008). -->
              <td class="center small">
                <span v-if="p.applied" class="ok">됨</span>
                <span v-else class="dim">아직</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="detail.remark" class="small">{{ detail.remark }}</p>
      <div v-if="detail.cancelReason" class="alert alert-warn mt-1">
        <span class="alert-icon">💬</span><span>취소 — {{ detail.cancelReason }}</span>
      </div>
      <div v-if="detail.arrivalDiffers" class="alert alert-warn mt-1">
        <span class="alert-icon">⚠</span>
        <span>
          예정과 {{ Math.abs(detail.arrivalDiff) }}개 다르게 내려왔습니다. 검수에서
          확인합니다 — 아직 재고에는 반영되지 않았습니다.
        </span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 36px" class="right">#</th>
            <th style="width: 175px">SKU</th>
            <th style="width: 170px">제품</th>
            <th style="width: 76px" class="right">예정</th>
            <th style="width: 76px" class="right">입하</th>
            <th style="width: 76px" class="right">차이</th>
            <th>비고</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in detail.lines" :key="l.lineSeq" :class="{ diff: l.differs }">
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.skuId }}</span></td>
            <td>
              {{ l.productName }}
              <div class="small dim">{{ l.colorCode }} / {{ l.sizeCode }}</div>
            </td>
            <td class="num">{{ num(l.plannedQty) }}</td>
            <td class="num">
              <span :class="{ dim: l.notArrived }">{{ num(l.arrivedQty) }}</span>
            </td>
            <td class="num">
              <strong v-if="!l.notArrived && l.differs" class="warn">
                {{ l.diffQty > 0 ? '+' : '' }}{{ l.diffQty }}
              </strong>
              <span v-else class="dim">-</span>
            </td>
            <td class="small">{{ l.remark ?? '-' }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          {{
            detail.planned
              ? '아직 도착하지 않았습니다. 입하 등록 화면에서 받습니다.'
              : detail.canceled
                ? '취소된 예정입니다.'
                : '입하했습니다. 다음은 검수입니다 — 아직 재고가 아닙니다.'
          }}
        </span>
        <button
          v-if="detail.planned"
          class="btn btn-danger"
          :disabled="!canDelete"
          @click="askCancel = detail"
        >
          예정 취소
        </button>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askCancel"
      title="입고예정 취소"
      :message="`${askCancel.inboundNo} 을(를) 거둬들입니까?`"
      detail="지우지 않고 '취소' 로 남습니다. 잡았다 거둔 사실도 정보입니다 — 같은 발주의 예정을 잡았다 거두기를 반복하면 공급처 납기가 흔들리고 있다는 뜻입니다. 발주 잔량은 다시 예정할 수 있게 풀립니다."
      confirm-label="거둬들이기"
      danger
      :busy="acting"
      @cancel="askCancel = null"
      @confirm="doCancel()"
    />
  </div>
</template>

<style scoped>
/* 입하 기록 — 나중에 문제가 생겼을 때 보러 오는 자리라 따로 뗀다 */
.arrive-box {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
}
.arrive-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--text-2);
  margin-bottom: 6px;
}
.arrive-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 6px 16px;
}
.arrive-grid > div {
  display: flex;
  gap: 6px;
  align-items: baseline;
}
.arrive-grid .k {
  flex: 0 0 62px;
  font-size: 12px;
  color: var(--text-3);
}
/* 검수 · 적치 내역 — 끝난 뒤에 되짚는 자리 */.trail-box {  margin-top: 10px;  padding: 10px 12px;  border: 1px solid var(--border);  border-radius: var(--radius);  background: var(--surface-2);}.trail-box .table.trail {  margin-top: 4px;}.trail-box .table.trail th,.trail-box .table.trail td {  padding: 4px 6px;}.center {  text-align: center;}
.arrive-remark {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}
.from-order {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.from-order > :first-child {
  flex: 1;
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
.empty-note {
  padding: 24px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
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
.mt-1 {
  margin-top: 6px;
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
