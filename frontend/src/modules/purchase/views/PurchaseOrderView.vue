<script setup>
/**
 * 구매오더 (PUR-PG-003 등록 · PUR-PG-004 발주).
 *
 * 구매요청이 "사 주세요" 라면 구매오더는 <b>공급처와의 약속</b>이다.
 * 여기서부터 돈이 나가고, 물건이 들어올 근거가 생긴다.
 *
 * 작성과 발주를 한 화면에 둔다. 만들어 두고 며칠 뒤에 내보내는 일이 흔해
 * 두 단계로 나누지만, 두 화면으로 나누면 "내가 만든 게 어디 갔지" 가
 * 된다 — 같은 목록에서 상태로 구분한다.
 *
 * 화면이 지키는 선은 하나다. <b>발주(ISSUED) 뒤에는 수정 버튼이 없다.</b>
 * 이미 공급처에 나간 문서라, 우리 화면만 바뀌면 공급처가 보고 있는 종이와
 * 달라진다. 서버도 같은 것을 막지만 버튼이 먼저 없어야 한다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as orderApi from '@/api/purchaseOrder.js'
import * as purchaseApi from '@/api/purchase.js'
import * as stockApi from '@/api/stock.js'
import * as supplierApi from '@/api/supplier.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import SkuPicker from '@/components/SkuPicker.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const size = orderApi.PAGE_SIZE

/* ── 목록 ───────────────────────────────────────────────────── */

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: '',
  supplierId: '',
  orderStatus: '',
  fromDate: '',
  toDate: '',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await orderApi.list({ ...filters, page: page.value, size })
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

/** 공급처 드롭다운 — 발주는 공급처가 있어야 성립한다 */
const suppliers = ref([])
/** 거래중인 곳만. 거래중지된 곳으로는 발주가 나가지 않는다 (MST-010). */
const activeSuppliers = computed(() => suppliers.value.filter((s) => s.status === 'ACTIVE'))

onMounted(async () => {
  await hierarchy.loadPlants(false)
  try {
    // 필터에서는 거래중지된 곳도 골라야 한다 — 예전 발주를 찾아야 하기 때문이다.
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
const activeSupplierOptions = computed(() =>
  activeSuppliers.value.map((s) => ({ value: s.supplierId, label: s.supplierName })),
)

const columns = [
  { key: 'orderNo', label: '발주번호', width: '150px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '130px' },
  { key: 'plantName', label: '입고 센터', width: '110px' },
  { key: 'lineCount', label: '줄', width: '44px', align: 'right' },
  { key: '_qty', label: '발주 / 기입고', width: '116px', align: 'right' },
  { key: 'totalAmount', label: '금액', width: '104px', align: 'right' },
  { key: 'dueDate', label: '납기', width: '96px', align: 'center' },
  { key: 'orderStatus', label: '상태', width: '84px', align: 'center' },
  { key: '_act', label: '', width: '150px', align: 'right' },
]

/* ── 작성 ───────────────────────────────────────────────────── */

const editing = ref(false)
const editSeq = ref(null)
const picking = ref(false)
const busy = ref(false)
const serverError = ref('')

/** 기본 납기는 2주 뒤. 발주 → 생산 → 납품에 그만큼은 걸린다. */
const defaultDueDate = () => stockApi.daysAgo(-14)

const form = reactive({
  supplierId: '',
  plantId: '',
  requestNo: '',
  dueDate: defaultDueDate(),
  payTerm: '',
  remark: '',
})
/** 담은 줄 — { sku, orderQty, unitPrice, requestLineSeq, remark } */
const lines = ref([])

const pickedIds = computed(() => lines.value.map((l) => l.sku.skuId))

/**
 * 고른 공급처의 기본 결제조건.
 *
 * 공급처마다 거의 고정이라 매번 고르게 하지 않는다. 비워 두면 서버가
 * 이 값을 쓴다 — 화면에는 무엇이 적용될지만 보여 준다.
 */
const pickedSupplier = computed(() =>
  suppliers.value.find((s) => s.supplierId === form.supplierId),
)
const effectivePayTerm = computed(() => form.payTerm || pickedSupplier.value?.payTerm || '')

function openCreate() {
  editSeq.value = null
  Object.assign(form, {
    supplierId: '',
    plantId: '',
    requestNo: '',
    dueDate: defaultDueDate(),
    payTerm: '',
    remark: '',
  })
  lines.value = []
  approvedRequest.value = null
  serverError.value = ''
  editing.value = true
}

/**
 * SKU 를 담을 때 단가를 미리 채운다.
 *
 * 비워 두면 서버가 제품 원가를 복사하지만, 화면에서 금액을 못 보면
 * 얼마짜리 발주를 내는지 모르는 채로 확정하게 된다.
 */
function addLine(sku) {
  picking.value = false
  const fromRequest = approvedLineOf(sku.skuId)
  lines.value = [
    ...lines.value,
    {
      sku,
      orderQty: fromRequest?.approvedQty ?? 1,
      unitPrice: sku.costAmount ?? '',
      requestLineSeq: fromRequest?.lineSeq ?? null,
      remark: '',
    },
  ]
}

function removeLine(i) {
  lines.value = lines.value.filter((_, idx) => idx !== i)
}

const lineError = (line) => {
  if (line.orderQty === '' || line.orderQty === null) return '발주수량을 입력하세요.'
  if (Number(line.orderQty) < 1) return '1 이상이어야 합니다.'
  if (line.unitPrice !== '' && Number(line.unitPrice) < 0) return '단가는 0 이상이어야 합니다.'
  return ''
}

const formValid = computed(
  () =>
    form.supplierId &&
    form.plantId &&
    form.dueDate &&
    lines.value.length > 0 &&
    lines.value.every((l) => !lineError(l)) &&
    !busy.value,
)

const totalQty = computed(() =>
  lines.value.reduce((sum, l) => sum + (lineError(l) ? 0 : Number(l.orderQty)), 0),
)
/** 화면에서 미리 보는 금액. 확정 금액은 서버가 저장한 단가로 다시 계산된다. */
const totalAmount = computed(() =>
  lines.value.reduce(
    (sum, l) => sum + (lineError(l) ? 0 : Number(l.orderQty) * Number(l.unitPrice || 0)),
    0,
  ),
)

/* ── 근거 구매요청 불러오기 (PUR-005 의 반대편) ─────────────── */

const approvedRequest = ref(null)
const loadingRequest = ref(false)

/**
 * 요청번호를 넣으면 승인된 줄을 그대로 담아 준다.
 *
 * 승인수량을 사람이 옮겨 적게 하면 틀린다. 틀린 줄은 발주가 나가고
 * 물건이 들어온 다음에야 드러난다.
 *
 * 요청 없이도 발주할 수 있다 (PUR-005). 신상품 초도물량처럼 센터의
 * 요청 없이 본사가 바로 내는 경우가 있다.
 */
async function pullFromRequest() {
  const no = form.requestNo.trim()
  if (!no) {
    approvedRequest.value = null
    return
  }
  loadingRequest.value = true
  serverError.value = ''
  try {
    const found = await purchaseApi.list({ keyword: no, size: 5 })
    const hit = found.rows.find((r) => r.requestNo === no)
    if (!hit) throw new Error(`${no} 을(를) 찾을 수 없습니다.`)
    const full = await purchaseApi.detail(hit.requestSeq)
    if (!full.orderable) {
      throw new Error(
        `${no} 은(는) 아직 결재가 끝나지 않았습니다. 승인 또는 부분승인이어야 발주할 수 있습니다.`,
      )
    }
    approvedRequest.value = full
    if (!form.plantId) form.plantId = full.plantId
    // 승인수량이 0 인 줄은 담지 않는다 — 결재가 "사지 말라" 고 한 줄이다.
    lines.value = full.lines
      .filter((l) => (l.approvedQty ?? 0) > 0)
      .map((l) => ({
        sku: {
          skuId: l.skuId,
          productName: l.productName,
          colorCode: l.colorCode,
          sizeCode: l.sizeCode,
        },
        orderQty: l.approvedQty,
        unitPrice: '',
        requestLineSeq: l.lineSeq,
        remark: '',
      }))
    toast.success(`${no} 의 승인된 ${lines.value.length} 줄을 담았습니다.`)
  } catch (e) {
    approvedRequest.value = null
    serverError.value = e.message
  } finally {
    loadingRequest.value = false
  }
}

const approvedLineOf = (skuId) =>
  approvedRequest.value?.lines.find((l) => l.skuId === skuId && (l.approvedQty ?? 0) > 0) ?? null

/** 승인수량을 넘겼나. 서버도 알리지만, 넣는 순간 보여야 고칠 수 있다. */
function overApproved(line) {
  if (!line.requestLineSeq) return 0
  const src = approvedRequest.value?.lines.find((l) => l.lineSeq === line.requestLineSeq)
  if (!src || src.approvedQty === null) return 0
  const over = Number(line.orderQty || 0) - src.approvedQty
  return over > 0 ? over : 0
}

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const payload = {
      supplierId: form.supplierId,
      plantId: form.plantId,
      requestNo: form.requestNo.trim() || null,
      dueDate: form.dueDate,
      payTerm: form.payTerm || null,
      remark: form.remark || null,
      lines: lines.value.map((l) => ({
        skuId: l.sku.skuId,
        orderQty: Number(l.orderQty),
        unitPrice: l.unitPrice === '' ? null : Number(l.unitPrice),
        requestLineSeq: l.requestLineSeq,
        remark: l.remark || null,
      })),
    }
    const { order, warning } = editSeq.value
      ? await orderApi.update(editSeq.value, payload)
      : await orderApi.create(payload)
    toast.success(`${order.orderNo} — ${order.lineCount} 줄을 작성했습니다. 아직 나가지 않았습니다.`)
    if (warning) toast.warn(warning)
    editing.value = false
    await search()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

/* ── 상세 · 발주 · 취소 (PUR-PG-004) ────────────────────────── */

const detail = ref(null)
const askIssue = ref(null)
const askCancel = ref(null)
const askDelete = ref(null)
const cancelReason = reactive({ reasonCode: '', remark: '' })
const acting = ref(false)

async function openDetail(row) {
  try {
    detail.value = await orderApi.detail(row.orderSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/** 작성중이면 담긴 줄을 그대로 꺼내 고칠 수 있다 */
async function openEdit(row) {
  try {
    const full = await orderApi.detail(row.orderSeq)
    editSeq.value = full.orderSeq
    Object.assign(form, {
      supplierId: full.supplierId,
      plantId: full.plantId,
      requestNo: full.requestNo ?? '',
      dueDate: full.dueDate,
      payTerm: full.payTerm ?? '',
      remark: full.remark ?? '',
    })
    lines.value = full.lines.map((l) => ({
      sku: {
        skuId: l.skuId,
        productName: l.productName,
        colorCode: l.colorCode,
        sizeCode: l.sizeCode,
      },
      orderQty: l.orderQty,
      unitPrice: l.unitPrice,
      requestLineSeq: l.requestLineSeq,
      remark: l.remark ?? '',
    }))
    approvedRequest.value = null
    serverError.value = ''
    editing.value = true
  } catch (e) {
    loadError.value = e.message
  }
}

async function doIssue() {
  acting.value = true
  try {
    const { order, warning } = await orderApi.issue(askIssue.value.orderSeq)
    toast.success(`${order.orderNo} 을(를) 발주했습니다. 납기 ${order.dueDate}`)
    if (warning) toast.warn(warning)
    askIssue.value = null
    if (detail.value) detail.value = await orderApi.detail(order.orderSeq)
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
    askIssue.value = null
  } finally {
    acting.value = false
  }
}

function openCancel(row) {
  cancelReason.reasonCode = ''
  cancelReason.remark = ''
  askCancel.value = row
}

async function doCancel() {
  acting.value = true
  try {
    const order = await orderApi.cancel(
      askCancel.value.orderSeq,
      cancelReason.reasonCode,
      cancelReason.remark || null,
    )
    toast.success(`${order.orderNo} 을(를) 취소했습니다.`)
    askCancel.value = null
    if (detail.value) detail.value = await orderApi.detail(order.orderSeq)
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
  } finally {
    acting.value = false
  }
}

async function doDelete() {
  acting.value = true
  try {
    await orderApi.remove(askDelete.value.orderSeq, '작성중인 발주 삭제')
    toast.success(`${askDelete.value.orderNo} 을(를) 지웠습니다.`)
    askDelete.value = null
    detail.value = null
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
    askDelete.value = null
  } finally {
    acting.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const won = (v) => (v === null || v === undefined ? '-' : nf.format(Math.round(Number(v))))

const canCreate = computed(() => session.can('PUR_PO_ISSUE', 'C'))
const canIssue = computed(() => session.can('PUR_PO_ISSUE', 'U'))
const canCancel = computed(() => session.can('PUR_PO_CANCEL', 'U'))
const createDenyReason = computed(() => session.denyReason('PUR_PO_ISSUE', 'C'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">구매오더</h1>
        <p class="page-desc">
          공급처에 보내는 <strong>발주서</strong>입니다. 작성해 두었다가 확정할 때
          나갑니다 — <strong>발주 뒤에는 고칠 수 없습니다.</strong> 바꾸려면 취소하고
          새로 냅니다. 단가는 발주 시점 값으로 박혀, 나중에 원가가 바뀌어도
          지난 발주 금액은 움직이지 않습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '구매오더 작성'"
          @click="openCreate()"
        >
          + 구매오더
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
          placeholder="발주번호 / 공급처 / 비고"
          @enter="search()"
        />
        <FormField v-model="filters.fromDate" label="발주일 시작" type="date" @change="search()" />
        <FormField v-model="filters.toDate" label="발주일 끝" type="date" @change="search()" />
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
          v-model="filters.orderStatus"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('ORDER_STATUS')"
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
        :muted-when="(r) => r.canceled"
        empty-text="조건에 맞는 구매오더가 없습니다."
        @row-click="openDetail"
      >
        <template #cell-lineCount="{ value }">{{ num(value) }}</template>

        <!-- 발주수량과 기입고를 한 칸에 둔다. 떼어 놓으면 '얼마나 들어왔나'
             를 눈으로 빼야 한다. -->
        <template #cell-_qty="{ row }">
          <strong>{{ num(row.totalOrderQty) }}</strong>
          <span class="dim"> / </span>
          <strong :class="row.totalReceivedQty ? 'ok' : 'dim'">
            {{ num(row.totalReceivedQty) }}
          </strong>
          <div v-if="row.issued || row.open" class="small dim">{{ row.progressPercent }}%</div>
        </template>

        <template #cell-totalAmount="{ value }">{{ won(value) }}</template>

        <template #cell-dueDate="{ row, value }">
          <span :class="{ danger: row.overdue }">{{ value }}</span>
          <div v-if="row.overdue" class="small danger">지남</div>
        </template>

        <template #cell-orderStatus="{ value }">
          <CodeBadge group="ORDER_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <!-- 작성중에만 고치고 지우고 낼 수 있다 -->
            <template v-if="row.draft">
              <button class="btn btn-sm" :disabled="!canCreate" @click.stop="openEdit(row)">
                수정
              </button>
              <button
                class="btn btn-sm btn-primary"
                :disabled="!canIssue"
                title="공급처에 보냅니다. 이후에는 고칠 수 없습니다."
                @click.stop="askIssue = row"
              >
                발주
              </button>
              <button class="btn btn-sm btn-danger" :disabled="!canCreate" @click.stop="askDelete = row">
                ×
              </button>
            </template>
            <!-- 나간 뒤에는 취소만 남는다. 입고가 시작되면 그것도 막힌다. -->
            <button
              v-else-if="row.open && !row.totalReceivedQty"
              class="btn btn-sm btn-danger"
              :disabled="!canCancel"
              @click.stop="openCancel(row)"
            >
              취소
            </button>
            <span v-else-if="row.open" class="small dim" title="입고가 시작되어 취소할 수 없습니다">
              입고중
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
      :title="editSeq ? '구매오더 수정' : '구매오더 작성'"
      size="wide"
      @close="editing = false"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.supplierId"
          label="공급처"
          type="select"
          required
          empty-option="선택하세요"
          :options="activeSupplierOptions"
          help="거래중인 곳만 나옵니다. 거래중지된 곳으로는 발주가 나가지 않습니다."
        />
        <FormField
          v-model="form.plantId"
          label="입고 센터"
          type="select"
          required
          empty-option="선택하세요"
          :options="hierarchy.plantOptions"
          help="물건이 들어올 센터입니다."
        />
        <FormField
          v-model="form.dueDate"
          label="납품예정일"
          type="date"
          required
          help="언제까지 받기로 했는지. 진행현황에서 독촉 기준이 됩니다."
        />
        <FormField
          v-model="form.payTerm"
          label="결제조건"
          type="select"
          empty-option="공급처 기본값"
          :options="codeOptions('PAY_TERM')"
          :help="
            effectivePayTerm
              ? `적용: ${effectivePayTerm}`
              : '공급처에 기본 결제조건이 없습니다. 직접 고르세요.'
          "
        />
      </div>

      <!-- 근거 구매요청. 없어도 된다 (PUR-005). -->
      <div class="from-request">
        <FormField
          v-model="form.requestNo"
          label="근거 구매요청"
          placeholder="REQ-20260915-0001 (없으면 비워 둡니다)"
          help="승인된 요청번호를 넣고 불러오면 승인수량대로 담습니다."
          @enter="pullFromRequest()"
        />
        <button class="btn" :disabled="!form.requestNo || loadingRequest" @click="pullFromRequest()">
          <span v-if="loadingRequest" class="spinner"></span>
          불러오기
        </button>
      </div>
      <div v-if="approvedRequest" class="alert alert-info mb-2">
        <span class="alert-icon">📋</span>
        <span>
          {{ approvedRequest.requestNo }} · {{ approvedRequest.plantName }} ·
          승인 <strong>{{ num(approvedRequest.totalApprovedQty) }}</strong> 개
        </span>
      </div>

      <div class="lines-head">
        <strong>
          발주할 SKU {{ lines.length }} 줄 · 수량 {{ num(totalQty) }} · 금액 {{ won(totalAmount) }} 원
        </strong>
        <button class="btn btn-sm btn-primary" @click="picking = true">+ SKU 담기</button>
      </div>

      <div v-if="!lines.length" class="empty-note">
        담은 SKU 가 없습니다. 요청번호로 불러오거나 'SKU 담기' 로 한 줄 이상 담으세요.
      </div>

      <table v-else class="table lines">
        <thead>
          <tr>
            <th style="width: 170px">SKU</th>
            <th style="width: 160px">제품</th>
            <th style="width: 88px">발주수량</th>
            <th style="width: 104px">단가</th>
            <th style="width: 96px" class="right">금액</th>
            <th style="width: 40px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in lines" :key="l.sku.skuId">
            <td>
              <span class="code">{{ l.sku.skuId }}</span>
              <div v-if="l.requestLineSeq" class="small dim">요청에서</div>
            </td>
            <td>
              {{ l.sku.productName }}
              <div class="small dim">{{ l.sku.colorCode }} / {{ l.sku.sizeCode }}</div>
            </td>
            <td>
              <input v-model.number="l.orderQty" type="number" class="input" min="1" />
              <!-- 결재가 정한 한도를 넘는 것은 넣는 순간 보여야 한다 -->
              <div v-if="overApproved(l)" class="small warn">승인 +{{ num(overApproved(l)) }}</div>
            </td>
            <td>
              <input
                v-model.number="l.unitPrice"
                type="number"
                class="input"
                min="0"
                placeholder="제품 원가"
              />
            </td>
            <td class="num">{{ won(Number(l.orderQty || 0) * Number(l.unitPrice || 0)) }}</td>
            <td><button class="btn btn-sm btn-danger" @click="removeLine(i)">×</button></td>
          </tr>
          <tr v-for="(l, i) in lines.filter((x) => lineError(x))" :key="`e${i}`">
            <td colspan="6" class="small danger">{{ l.sku.skuId }} — {{ lineError(l) }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          저장해도 아직 공급처에 나가지 않습니다. 목록에서 '발주' 를 눌러야 나갑니다.
        </span>
        <button class="btn" :disabled="busy" @click="editing = false">취소</button>
        <button class="btn btn-primary" :disabled="!formValid" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          {{ editSeq ? '저장' : '작성' }}
        </button>
      </template>
    </ModalDialog>

    <SkuPicker
      v-if="picking"
      title="발주할 SKU 담기"
      :picked-ids="pickedIds"
      @pick="addLine"
      @close="picking = false"
    />

    <!-- ── 상세 ─────────────────────────────────────────────── -->
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
        <span v-else class="dim">아직 나가지 않았습니다</span>
        <span v-if="detail.issuedByName">발주자 <strong>{{ detail.issuedByName }}</strong></span>
        <span v-if="detail.requestNo">근거 <strong class="code">{{ detail.requestNo }}</strong></span>
        <span>결제 <strong>{{ detail.payTerm }}</strong></span>
        <span>
          발주 <strong>{{ num(detail.totalOrderQty) }}</strong>
          / 입고 <strong :class="detail.totalReceivedQty ? 'ok' : 'dim'">
            {{ num(detail.totalReceivedQty) }}
          </strong>
          <span class="dim"> · 잔량 {{ num(detail.remainQty) }}</span>
        </span>
        <span>금액 <strong>{{ won(detail.totalAmount) }}</strong> 원</span>
      </div>
      <p v-if="detail.remark" class="small">{{ detail.remark }}</p>
      <div v-if="detail.cancelReason" class="alert alert-warn mt-1">
        <span class="alert-icon">💬</span><span>취소 — {{ detail.cancelReason }}</span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 32px" class="right">#</th>
            <th style="width: 170px">SKU</th>
            <th style="width: 150px">제품</th>
            <th style="width: 64px" class="right">발주</th>
            <th style="width: 64px" class="right">입고</th>
            <th style="width: 64px" class="right">잔량</th>
            <th style="width: 92px" class="right">단가</th>
            <th style="width: 96px" class="right">금액</th>
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
            <!-- 잔량이 음수면 초과입고다. 오류가 아니라 판정이 필요한 사건이다. -->
            <td class="num">
              <span :class="{ warn: l.remainQty < 0 }">{{ num(l.remainQty) }}</span>
            </td>
            <td class="num">
              {{ won(l.unitPrice) }}
              <!-- 발주 뒤 기준 원가가 바뀌었는지. 금액에는 영향이 없다. -->
              <div v-if="l.priceDrifted" class="small dim" title="지금 기준정보의 원가">
                현재 {{ won(l.currentCost) }}
              </div>
            </td>
            <td class="num">{{ won(l.lineAmount) }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          {{
            detail.draft
              ? '작성중입니다. 발주해야 공급처에 나갑니다.'
              : detail.canceled
                ? '취소된 발주입니다.'
                : detail.closed
                  ? '발주수량이 모두 들어왔습니다.'
                  : `입고를 기다리는 중입니다 — ${detail.progressPercent}% 진행.`
          }}
        </span>
        <button
          v-if="detail.draft"
          class="btn btn-primary"
          :disabled="!canIssue"
          @click="askIssue = detail"
        >
          발주
        </button>
        <button
          v-else-if="detail.open && !detail.totalReceivedQty"
          class="btn btn-danger"
          :disabled="!canCancel"
          @click="openCancel(detail)"
        >
          취소
        </button>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>

    <!-- ── 발주 확정 ────────────────────────────────────────── -->
    <ConfirmDialog
      v-if="askIssue"
      title="발주 확정"
      :message="`${askIssue.orderNo} 을(를) ${askIssue.supplierName} 에 발주합니까?`"
      detail="확정하면 공급처와의 약속이 됩니다. 이후에는 수정할 수 없고, 바꾸려면 취소하고 새로 내야 합니다 — 공급처가 보고 있는 종이와 우리 화면이 달라지는 것이 가장 나쁩니다."
      confirm-label="발주"
      :busy="acting"
      @cancel="askIssue = null"
      @confirm="doIssue()"
    />

    <!-- ── 취소 — 사유가 필수다 ─────────────────────────────── -->
    <ModalDialog
      v-if="askCancel"
      title="발주 취소"
      :subtitle="askCancel.orderNo"
      @close="askCancel = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>
      <p class="small">
        이미 나간 발주를 거둬들입니다. 공급처에도 알려야 합니다 — 화면에서
        지운다고 상대방이 아는 것은 아닙니다.
      </p>
      <div class="form-grid mt-2">
        <FormField
          v-model="cancelReason.reasonCode"
          label="취소 사유"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('REASON_PO_CANCEL')"
          help="나중에 '왜 취소됐나' 를 반드시 묻게 됩니다."
        />
        <FormField
          v-model="cancelReason.remark"
          label="설명"
          placeholder="사유코드로 설명되지 않는 사정"
        />
      </div>
      <template #footer>
        <button class="btn" :disabled="acting" @click="askCancel = null">닫기</button>
        <button
          class="btn btn-danger"
          :disabled="!cancelReason.reasonCode || acting"
          @click="doCancel()"
        >
          <span v-if="acting" class="spinner"></span>
          발주 취소
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="구매오더 삭제"
      :message="`${askDelete.orderNo} 을(를) 지웁니까?`"
      detail="아직 공급처에 나간 적 없는 작성중 문서라 흔적 없이 지웁니다. 이미 발주한 건은 지울 수 없고 '취소' 로 남깁니다."
      confirm-label="삭제"
      danger
      :busy="acting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>

<style scoped>
.from-request {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.from-request > :first-child {
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
