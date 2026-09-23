<script setup>
/**
 * 주문 관리 (ORD-PG-001, 002, 009, 010, 011).
 *
 * 한 화면이 네 가지를 한다 — 목록 · 상세 · 등록 · 확정. 주문은 등록하고
 * 바로 확정하는 흐름이라, 화면을 나누면 같은 주문을 찾아 다시 들어가야 한다.
 *
 * 채널주문만 다룬다. 판매오더(B2B)는 11차다.
 *
 * 수령인과 배송지는 직접 적는다. 오픈마켓에서 산 개인은 거래처로 등록하지
 * 않아 고를 목록이 없고, 서버도 이 값을 스냅샷으로 저장한다 — 나중에
 * 무엇이 바뀌어도 과거 주문의 배송지는 그때 보낸 곳이어야 한다.
 *
 * SKU 를 못 찾은 줄이 있어도 저장은 된다 (ORD-005). 그 주문은 목록에서
 * 'SKU 미지정' 배지가 붙고 확정 버튼이 잠긴다 — 무엇을 보낼지 모르는 줄을
 * 할당에 넘길 수 없기 때문이다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as orderApi from '@/api/order.js'
import * as channelApi from '@/api/channel.js'
import { codeOptions } from '@/api/codes.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import SkuPicker from '@/components/SkuPicker.vue'

const session = useSessionStore()
const router = useRouter()
const toast = useToastStore()

const size = orderApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const channels = ref([])

const filters = reactive({
  keyword: '',
  orderStatus: '',
  channelId: '',
  unmappedOnly: '',
  fromDate: '',
  toDate: '',
})

/**
 * 빠른 필터.
 *
 * 주문 화면에 오는 이유는 대개 셋이다 — 새로 들어온 것을 확정하거나,
 * 할당할 것을 고르거나, 막힌 것을 고치거나. 조건을 매번 조합하게 하는 대신
 * 질문 그대로 버튼을 둔다.
 */
const QUICK = [
  { key: '', label: '전체', desc: '조건 없이 전부' },
  { key: 'draftOnly', label: '확정 대기', desc: '접수됐고 아직 확정 안 한 것' },
  { key: 'allocatableOnly', label: '할당 대상', desc: '확정됐고 아직 할당 안 된 것' },
  { key: 'unmappedOnly', label: 'SKU 미지정', desc: 'SKU 가 안 붙은 줄이 있는 것' },
]
const quick = ref('')

function applyQuick(key) {
  quick.value = key
  filters.draftOnly = ''
  filters.allocatableOnly = ''
  filters.unmappedOnly = ''
  if (key) filters[key] = 'Y'
  search()
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '', orderStatus: '', channelId: '',
    draftOnly: '', allocatableOnly: '', unmappedOnly: '', fromDate: '', toDate: '',
  })
  quick.value = ''
  search()
}

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

onMounted(async () => {
  // 채널 목록을 미리 받는다. 등록 모달과 필터가 같이 쓴다.
  try {
    const ch = await channelApi.list({ size: 0 })
    channels.value = ch.rows ?? ch
  } catch {
    // 목록을 못 받아도 주문 조회는 되어야 한다. 드롭다운만 비게 둔다.
  }
  await fetchPage()
})

const channelOptions = computed(() =>
  channels.value.map((c) => ({ value: c.channelId, label: c.channelName })),
)
/* ── 표 ─────────────────────────────────────────────────────── */

const columns = [
  { key: 'orderNo', label: '주문번호', width: '155px', cls: 'code' },
  { key: 'origin', label: '채널', width: '140px' },
  { key: 'receiverName', label: '수령인', width: '150px' },
  { key: 'lineCount', label: '품목', width: '60px', align: 'right' },
  { key: 'totalQty', label: '수량', width: '76px', align: 'right' },
  { key: 'orderStatus', label: '상태', width: '100px' },
  { key: 'orderedAt', label: '주문일시', width: '132px' },
  { key: '_act', label: '', width: '150px', align: 'right' },
]

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).slice(0, 16).replace('T', ' ') : '-')

const canCreate = computed(() => session.can('ORD_ORDER', 'C'))

/**
 * 일괄 업로드로 보낸다 (ORD-PG-012).
 *
 * 올리는 화면은 공용이다(COM-PG-010) — 파일 파싱 · 행별 검증 · 부분성공 ·
 * 오류 파일을 대상마다 다시 만들지 않는다. 다만 주문을 여럿 받으려는
 * 사람이 시스템 메뉴를 뒤져 대상을 고르게 둘 이유는 없어서, 여기서
 * 대상을 지정해 보낸다.
 *
 * 등록 권한이 있어야 의미가 있다. 권한이 없으면 서버가 대상 목록에서
 * 주문을 빼므로 가 봐야 올릴 수 없다.
 */
function goBulkUpload() {
  router.push({ name: 'uploads', query: { type: 'ORDER' } })
}
const canUpdate = computed(() => session.can('ORD_ORDER', 'U'))
const createDenyReason = computed(() => session.denyReason('ORD_ORDER', 'C'))
const updateDenyReason = computed(() => session.denyReason('ORD_ORDER', 'U'))
const readDenyReason = computed(() => session.denyReason('ORD_ORDER', 'R'))

/* ── 상세 (ORD-PG-002) ──────────────────────────────────────── */

const picked = ref(null)

async function openDetail(row) {
  try {
    picked.value = await orderApi.detail(row.orderSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/* ── 확정 (ORD-PG-011) ──────────────────────────────────────── */

const confirming = ref(false)

async function doConfirm(order) {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  confirming.value = true
  try {
    const { order: after, warning } = await orderApi.confirm(order.orderSeq)
    toast.success(`${after.orderNo} 을(를) 확정했습니다. 이제 할당할 수 있습니다.`)
    if (warning) toast.warn(warning)
    if (picked.value?.orderSeq === after.orderSeq) picked.value = after
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  } finally {
    confirming.value = false
  }
}

/* ── 취소 · 변경 (ORD-PG-007, ORD-PG-008) ───────────────────── */

const canDelete = computed(() => session.can('ORD_ORDER', 'D'))
const deleteDenyReason = computed(() => session.denyReason('ORD_ORDER', 'D'))

/** 아직 손댈 수 있는 주문인가 — 취소됐거나 출고가 시작되면 끝이다 */
const OPEN = ['RECEIVED', 'CONFIRMED', 'ALLOCATED']
const isOpen = (o) => o && OPEN.includes(o.orderStatus)

/* 주문취소 */
const canceling = ref(null)
const cancelForm = reactive({ reasonCode: '', remark: '' })

function openCancel(order) {
  if (!canDelete.value) return toast.error(deleteDenyReason.value)
  canceling.value = order
  cancelForm.reasonCode = ''
  cancelForm.remark = ''
}

async function doCancel() {
  if (!cancelForm.reasonCode) return toast.warn('취소 사유를 고르세요.')
  busy.value = true
  try {
    const { order, warning } = await orderApi.cancel(canceling.value.orderSeq, {
      reasonCode: cancelForm.reasonCode,
      remark: cancelForm.remark || null,
    })
    toast.success(warning ?? `${order.orderNo} 을(를) 취소했습니다.`)
    canceling.value = null
    if (picked.value?.orderSeq === order.orderSeq) picked.value = order
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* 줄 취소 — 결품 줄을 접는다 */
const cancelingLine = ref(null)

function openCancelLine(line) {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  cancelingLine.value = line
  cancelForm.reasonCode = ''
  cancelForm.remark = ''
}

async function doCancelLine() {
  if (!cancelForm.reasonCode) return toast.warn('취소 사유를 고르세요.')
  busy.value = true
  try {
    const { order, warning } = await orderApi.cancelLine(
      picked.value.orderSeq,
      cancelingLine.value.lineSeq,
      { reasonCode: cancelForm.reasonCode, remark: cancelForm.remark || null },
    )
    toast.success(warning)
    cancelingLine.value = null
    picked.value = order
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* 배송지 변경 */
const editingAddress = ref(null)
const addressForm = reactive({
  receiverName: '',
  receiverPhone: '',
  zipCode: '',
  address: '',
  addressDetail: '',
  deliveryMemo: '',
  remark: '',
  reason: '',
})

function openAddress(order) {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  editingAddress.value = order
  Object.assign(addressForm, {
    receiverName: order.receiverName ?? '',
    receiverPhone: order.receiverPhone ?? '',
    zipCode: order.zipCode ?? '',
    address: order.address ?? '',
    addressDetail: order.addressDetail ?? '',
    deliveryMemo: order.deliveryMemo ?? '',
    remark: order.remark ?? '',
    reason: '',
  })
}

async function doUpdateAddress() {
  if (!addressForm.receiverName?.trim()) return toast.warn('수령인은 필수입니다.')
  if (!addressForm.address?.trim()) return toast.warn('주소는 필수입니다.')
  busy.value = true
  try {
    const { order, warning } = await orderApi.updateAddress(
      editingAddress.value.orderSeq,
      { ...addressForm },
    )
    toast.success(warning ?? '주문정보를 바꿨습니다.')
    editingAddress.value = null
    if (picked.value?.orderSeq === order.orderSeq) picked.value = order
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* ── 등록 (ORD-PG-009, 010) ─────────────────────────────────── */

const dlg = ref(false)
const busy = ref(false)
const serverError = ref('')
const errors = reactive({})
const picking = ref(false)

const form = reactive({
  channelId: '',
  extOrderNo: '',
  receiverName: '',
  receiverPhone: '',
  zipCode: '',
  address: '',
  addressDetail: '',
  deliveryMemo: '',
  remark: '',
})
const lines = ref([])

function openCreate() {
  if (!canCreate.value) return toast.error(createDenyReason.value)
  Object.assign(form, {
    channelId: '', extOrderNo: '',
    receiverName: '', receiverPhone: '', zipCode: '', address: '',
    addressDetail: '', deliveryMemo: '', remark: '',
  })
  lines.value = []
  Object.keys(errors).forEach((k) => delete errors[k])
  serverError.value = ''
  dlg.value = true
}

function addLine(sku) {
  picking.value = false
  if (lines.value.some((l) => l.sku.skuId === sku.skuId)) {
    return toast.warn('이미 담긴 SKU 입니다. 수량을 고치세요.')
  }
  lines.value = [...lines.value, { sku, orderQty: 1, unitPrice: '', remark: '' }]
}

function removeLine(i) {
  lines.value = lines.value.filter((_, idx) => idx !== i)
}

const pickedIds = computed(() => lines.value.map((l) => l.sku.skuId))

const lineError = (line) => {
  if (line.orderQty === '' || line.orderQty === null) return '주문수량을 입력하세요.'
  if (Number(line.orderQty) < 1) return '1 이상이어야 합니다.'
  return ''
}

const totalAmount = computed(() =>
  lines.value.reduce((sum, l) => {
    const p = Number(l.unitPrice)
    return sum + (Number.isFinite(p) ? p * Number(l.orderQty || 0) : 0)
  }, 0),
)

async function submit() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.channelId) errors.channelId = '채널을 고르세요.'
  if (!form.receiverName?.trim()) errors.receiverName = '수령인은 필수입니다.'
  if (!form.address?.trim()) errors.address = '주소는 필수입니다.'
  if (!lines.value.length) {
    serverError.value = '주문 라인을 한 품목 이상 담으세요.'
    return toast.warn('주문할 SKU 를 담으세요.')
  }
  if (lines.value.some((l) => lineError(l))) return toast.warn('라인 수량을 확인하세요.')
  if (Object.keys(errors).length) return toast.warn('입력값을 확인하세요.')

  busy.value = true
  serverError.value = ''
  try {
    const { order, warning } = await orderApi.create({
      channelId: form.channelId,
      extOrderNo: form.extOrderNo || null,
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone || null,
      zipCode: form.zipCode || null,
      address: form.address,
      addressDetail: form.addressDetail || null,
      deliveryMemo: form.deliveryMemo || null,
      remark: form.remark || null,
      lines: lines.value.map((l) => ({
        skuId: l.sku.skuId,
        orderQty: Number(l.orderQty),
        unitPrice: l.unitPrice === '' ? null : Number(l.unitPrice),
        remark: l.remark || null,
      })),
    })
    toast.success(`${order.orderNo} 을(를) 등록했습니다.`)
    if (warning) toast.warn(warning)
    dlg.value = false
    await search()
  } catch (e) {
    // 중복 주문번호 · 유형 불일치 같은 업무 규칙은 모달을 닫지 않고 보여 준다
    serverError.value = e.message
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">주문 관리</h1>
        <p class="page-desc">
          채널에서 들어온 주문을 다룹니다.
          <strong>확정해야 할당 대상</strong>이 되며, SKU 가 정해지지 않은 줄이 있으면
          확정할 수 없습니다 — 무엇을 보낼지 모르는 줄은 재고를 잡을 수 없습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <!--
          채널이 준 파일로 한꺼번에 받는 길. 한 건씩 치는 것이 기본이지만
          채널 주문은 원래 여러 건이 묶여 온다.
        -->
        <button
          class="btn"
          :disabled="!canCreate"
          :title="createDenyReason ?? '엑셀 · CSV 로 한꺼번에 받습니다'"
          @click="goBulkUpload()"
        >
          일괄 업로드
        </button>
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '주문 등록'"
          @click="openCreate()"
        >
          + 주문 등록
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!-- ── 빠른 필터 ────────────────────────────────────────── -->
    <div class="quick-row">
      <button
        v-for="q in QUICK"
        :key="q.key"
        class="btn btn-sm"
        :class="{ 'btn-primary': quick === q.key }"
        :title="q.desc"
        @click="applyQuick(q.key)"
      >
        {{ q.label }}
      </button>
    </div>

    <!-- ── 조회 조건 ────────────────────────────────────────── -->
    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색"
        placeholder="주문번호 / 채널주문번호 / 수령인"
        @enter="search()"
      />
      <FormField
        v-model="filters.orderStatus"
        label="상태"
        type="select"
        placeholder="전체"
        :options="codeOptions('SALES_ORDER_STATUS')"
        @change="search()"
      />
      <FormField
        v-model="filters.channelId"
        label="채널"
        type="select"
        placeholder="전체"
        :options="channelOptions"
        @change="search()"
      />
      <FormField v-model="filters.fromDate" label="주문일 시작" type="date" @change="search()" />
      <FormField v-model="filters.toDate" label="주문일 끝" type="date" @change="search()" />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
        <button class="btn" @click="resetFilters()">초기화</button>
      </div>
    </div>

    <!-- ── 목록 ─────────────────────────────────────────────── -->
    <DataTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      row-key="orderSeq"
      clickable
      :selected-key="picked?.orderSeq ?? null"
      empty-text="조건에 맞는 주문이 없습니다."
      @row-click="openDetail"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.extOrderNo" class="small dim mono">{{ row.extOrderNo }}</div>
      </template>

      <!-- 채널이 지워진 주문도 목록에는 남는다. 빈 칸으로 둔다. -->
      <template #cell-origin="{ row }">
        <span v-if="row.channelName">{{ row.channelName }}</span>
        <span v-else class="dim">-</span>
      </template>

      <template #cell-receiverName="{ row, value }">
        <span>{{ value }}</span>
        <div class="small dim ellipsis">{{ row.address }}</div>
      </template>

      <template #cell-lineCount="{ row, value }">
        {{ num(value) }}
        <span v-if="row.hasUnmapped" class="badge badge-amber" title="SKU 가 안 붙은 줄이 있습니다">
          !
        </span>
      </template>

      <template #cell-totalQty="{ value }">{{ num(value) }}</template>

      <template #cell-orderStatus="{ value }">
        <CodeBadge group="SALES_ORDER_STATUS" :code="value" />
      </template>

      <template #cell-orderedAt="{ value }">
        <span class="small">{{ dt(value) }}</span>
      </template>

      <template #cell-_act="{ row }">
        <div class="btn-row" style="justify-content: flex-end">
          <button
            v-if="row.orderStatus === 'RECEIVED'"
            class="btn btn-sm btn-primary"
            :disabled="!canUpdate || row.hasUnmapped || confirming"
            :title="row.hasUnmapped ? 'SKU 가 안 붙은 줄이 있어 확정할 수 없습니다' : '확정'"
            @click.stop="doConfirm(row)"
          >
            확정
          </button>
        </div>
      </template>
    </DataTable>

    <div v-if="totalPages > 1" class="pager">
      <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">이전</button>
      <span class="small dim">{{ page }} / {{ totalPages }} (총 {{ num(total) }}건)</span>
      <button class="btn btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">
        다음
      </button>
    </div>

    <!-- ── 상세 (ORD-PG-002) ────────────────────────────────── -->
    <ModalDialog
      v-if="picked"
      :title="picked.orderNo"
      :subtitle="`${picked.channelName ?? ''} · ${dt(picked.orderedAt)}`"
      size="wide"
      @close="picked = null"
    >
      <div class="detail-grid">
        <div>
          <span class="dt">상태</span>
          <span class="dd"><CodeBadge group="SALES_ORDER_STATUS" :code="picked.orderStatus" /></span>
        </div>
        <div v-if="picked.extOrderNo">
          <span class="dt">채널주문번호</span><span class="dd mono">{{ picked.extOrderNo }}</span>
        </div>
        <div>
          <span class="dt">수령인</span>
          <span class="dd">{{ picked.receiverName }} {{ picked.receiverPhone ?? '' }}</span>
        </div>
        <div style="grid-column: 1 / -1">
          <span class="dt">배송지</span>
          <span class="dd">
            {{ picked.zipCode ? `(${picked.zipCode}) ` : '' }}{{ picked.fullAddress }}
          </span>
        </div>
        <div v-if="picked.deliveryMemo" style="grid-column: 1 / -1">
          <span class="dt">요청사항</span><span class="dd">{{ picked.deliveryMemo }}</span>
        </div>
      </div>

      <div v-if="picked.hasUnmapped" class="alert alert-warn" style="margin: 12px 0">
        <span class="alert-icon">⚠</span>
        <span>
          SKU 가 정해지지 않은 줄이 있습니다. 채널 SKU 매핑을 등록하면 다음 주문부터
          자동으로 붙습니다.
        </span>
      </div>

      <table class="table">
        <thead>
          <tr>
            <th style="width: 46px">#</th>
            <th style="width: 165px">SKU</th>
            <th>상품</th>
            <th class="right" style="width: 80px">수량</th>
            <th class="right" style="width: 90px">판매가능</th>
            <th style="width: 96px">상태</th>
            <th style="width: 60px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in picked.lines" :key="l.lineSeq">
            <td>{{ l.lineNo }}</td>
            <td>
              <span v-if="l.skuId" class="code">{{ l.skuId }}</span>
              <span v-else class="badge badge-amber">미지정</span>
            </td>
            <td>
              {{ l.displayName ?? '-' }}
              <div v-if="l.extProductCode" class="small dim mono">
                {{ l.extProductCode }}{{ l.extOptionCode ? ` / ${l.extOptionCode}` : '' }}
              </div>
            </td>
            <td class="num">{{ num(l.orderQty) }}</td>
            <td class="num">{{ num(l.qtyAvailable) }}</td>
            <td><CodeBadge group="SALES_LINE_STATUS" :code="l.lineStatus" /></td>
            <td>
              <!--
                줄 하나만 접는다. 결품 줄 때문에 나머지 줄까지 묶어 둘 수는
                없다 — 나갈 수 있는 것은 내보내고 못 채운 줄만 접는다.
              -->
              <button
                v-if="isOpen(picked) && l.lineStatus !== 'CANCELED'"
                class="btn btn-sm"
                :disabled="!canUpdate || busy"
                title="이 줄만 접습니다. 잡아 둔 재고는 풀립니다."
                @click="openCancelLine(l)"
              >
                접기
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <button class="btn" @click="picked = null">닫기</button>
        <button
          v-if="isOpen(picked)"
          class="btn"
          :disabled="!canUpdate || busy"
          title="출고 전까지 수령인 · 배송지 · 요청사항을 바꿀 수 있습니다"
          @click="openAddress(picked)"
        >
          배송지 변경
        </button>
        <button
          v-if="isOpen(picked)"
          class="btn btn-danger"
          :disabled="!canDelete || busy"
          :title="deleteDenyReason ?? '잡아 둔 재고를 풀고 주문을 접습니다'"
          @click="openCancel(picked)"
        >
          주문취소
        </button>
        <button
          v-if="picked.orderStatus === 'RECEIVED'"
          class="btn btn-primary"
          :disabled="!canUpdate || picked.hasUnmapped || confirming"
          :title="picked.hasUnmapped ? 'SKU 가 안 붙은 줄이 있어 확정할 수 없습니다' : '확정'"
          @click="doConfirm(picked)"
        >
          확정
        </button>
      </template>
    </ModalDialog>

    <!-- ── 등록 (ORD-PG-009, 010) ───────────────────────────── -->
    <ModalDialog
      v-if="dlg"
      title="주문 등록"
      subtitle="주문번호는 저장할 때 자동으로 매겨집니다."
      size="wide"
      @close="dlg = false"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.channelId"
          label="채널"
          type="select"
          required
          :options="channelOptions"
          :error="errors.channelId"
        />
        <FormField
          v-model="form.extOrderNo"
          label="채널주문번호"
          mono
          placeholder="MSS-20260921-88123"
          hint="채널이 준 번호입니다. 같은 채널에 같은 번호는 한 번만 등록됩니다. 모르면 비워 두세요."
        />
      </div>

      <div class="section-title">배송지</div>
      <div class="form-grid">
        <FormField
          v-model="form.receiverName"
          label="수령인"
          required
          :error="errors.receiverName"
        />
        <FormField v-model="form.receiverPhone" label="연락처" placeholder="010-0000-0000" />
        <FormField v-model="form.zipCode" label="우편번호" mono />
        <FormField
          v-model="form.address"
          label="주소"
          required
          class="span2"
          :error="errors.address"
        />
        <FormField v-model="form.addressDetail" label="상세주소" class="span2" />
        <FormField v-model="form.deliveryMemo" label="배송 요청사항" class="span2" />
        <FormField v-model="form.remark" label="비고" class="span2" />
      </div>

      <div class="section-title">
        주문 라인
        <button class="btn btn-sm" @click="picking = true">+ SKU 담기</button>
      </div>

      <div v-if="!lines.length" class="table-empty">
        담은 SKU 가 없습니다. 'SKU 담기' 로 주문할 상품을 고르세요.
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th style="width: 46px">#</th>
            <th style="width: 165px">SKU</th>
            <th>상품</th>
            <th class="right" style="width: 110px">주문수량</th>
            <th class="right" style="width: 130px">단가</th>
            <th class="right" style="width: 120px">금액</th>
            <th style="width: 60px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in lines" :key="l.sku.skuId">
            <td>{{ i + 1 }}</td>
            <td><span class="code">{{ l.sku.skuId }}</span></td>
            <td>
              {{ l.sku.productName }}
              <div class="small dim">{{ l.sku.colorCode }} / {{ l.sku.sizeCode }}</div>
            </td>
            <td class="num">
              <input v-model="l.orderQty" class="input input-sm right" type="number" min="1" />
              <div v-if="lineError(l)" class="field-error">{{ lineError(l) }}</div>
            </td>
            <td class="num">
              <input v-model="l.unitPrice" class="input input-sm right" type="number" min="0" />
            </td>
            <td class="num">
              {{ l.unitPrice === '' ? '-' : num(Number(l.unitPrice) * Number(l.orderQty || 0)) }}
            </td>
            <td>
              <button class="btn btn-sm btn-danger" @click="removeLine(i)">삭제</button>
            </td>
          </tr>
        </tbody>
        <tfoot v-if="totalAmount > 0">
          <tr>
            <td colspan="5" class="right bold">합계</td>
            <td class="num bold">{{ num(totalAmount) }}</td>
            <td></td>
          </tr>
        </tfoot>
      </table>

      <template #footer>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
        <button class="btn" :disabled="busy" @click="dlg = false">취소</button>
      </template>
    </ModalDialog>

    <!-- ── 주문취소 (ORD-PG-007) ────────────────────────────── -->
    <ModalDialog
      v-if="canceling"
      :title="`${canceling.orderNo} 취소`"
      :subtitle="canceling.receiverName"
      @close="canceling = null"
    >
      <div class="alert alert-warn mb-2">
        <span class="alert-icon">⚠️</span>
        <span>
          <strong>되돌릴 수 없습니다.</strong>
          잡아 둔 재고가 있으면 함께 풀려 <strong>판매가능수량으로 돌아갑니다</strong> —
          다른 주문이 먼저 가져갈 수 있습니다. 줄 하나만 접으려면 상세에서
          그 줄의 '접기' 를 쓰세요.
        </span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="cancelForm.reasonCode"
          label="취소 사유"
          type="select"
          required
          placeholder="고르세요"
          :options="codeOptions('REASON_CANCEL')"
          hint="주문에 남고 감사로그에도 기록됩니다. 고객이 물으면 이것으로 답합니다."
        />
        <FormField v-model="cancelForm.remark" label="비고" class="span-2" />
      </div>

      <template #footer>
        <button class="btn" @click="canceling = null">닫기</button>
        <button
          class="btn btn-danger"
          :disabled="busy || !cancelForm.reasonCode"
          @click="doCancel()"
        >
          <span v-if="busy" class="spinner"></span>
          취소하기
        </button>
      </template>
    </ModalDialog>

    <!-- ── 줄 접기 ──────────────────────────────────────────── -->
    <ModalDialog
      v-if="cancelingLine"
      :title="`${cancelingLine.lineNo} 번 품목 접기`"
      :subtitle="cancelingLine.skuId ?? cancelingLine.displayName"
      @close="cancelingLine = null"
    >
      <p class="small">
        이 줄만 접습니다. 잡아 둔 재고가 있으면 풀립니다.
        <strong>남은 줄이 하나도 없으면 주문도 함께 취소됩니다.</strong>
      </p>

      <div class="form-grid mt-2">
        <FormField
          v-model="cancelForm.reasonCode"
          label="사유"
          type="select"
          required
          placeholder="고르세요"
          :options="codeOptions('REASON_CANCEL')"
        />
        <FormField v-model="cancelForm.remark" label="비고" class="span-2" />
      </div>

      <template #footer>
        <button class="btn" @click="cancelingLine = null">닫기</button>
        <button
          class="btn btn-danger"
          :disabled="busy || !cancelForm.reasonCode"
          @click="doCancelLine()"
        >
          <span v-if="busy" class="spinner"></span>
          접기
        </button>
      </template>
    </ModalDialog>

    <!-- ── 주문정보 변경 (ORD-PG-008) ───────────────────────── -->
    <ModalDialog
      v-if="editingAddress"
      :title="`${editingAddress.orderNo} 배송지 변경`"
      subtitle="출고 전까지만 바꿀 수 있습니다"
      size="wide"
      @close="editingAddress = null"
    >
      <p class="small dim">
        무엇을 몇 개 보내는지는 여기서 못 바꿉니다 — 그건 이미 재고를 잡아 둔 값이라
        취소하고 다시 받아야 합니다. 여기서 고치는 것은
        <strong>이 주문의 배송지</strong>이고, 거래처 주소는 그대로입니다.
      </p>

      <div class="form-grid mt-2">
        <FormField v-model="addressForm.receiverName" label="수령인" required />
        <FormField v-model="addressForm.receiverPhone" label="연락처" />
        <FormField v-model="addressForm.zipCode" label="우편번호" mono />
        <FormField v-model="addressForm.address" label="주소" required class="span-2" />
        <FormField v-model="addressForm.addressDetail" label="상세주소" class="span-2" />
        <FormField v-model="addressForm.deliveryMemo" label="배송 요청사항" class="span-2" />
        <FormField v-model="addressForm.remark" label="비고" class="span-2" />
        <FormField
          v-model="addressForm.reason"
          label="변경 사유"
          class="span-2"
          placeholder="예) 고객이 이사 — 전화로 변경 요청"
          hint="감사로그에 바뀐 칸의 전·후와 함께 남습니다. 배송지가 바뀐 주문은 설명이 필요합니다."
        />
      </div>

      <template #footer>
        <button class="btn" @click="editingAddress = null">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="doUpdateAddress()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <SkuPicker
      v-if="picking"
      title="주문할 SKU 담기"
      :picked-ids="pickedIds"
      @pick="addLine"
      @close="picking = false"
    />
  </div>
</template>

<style scoped>
/*
  상세 모달의 항목 나열.

  StockView 와 같은 모양이다. 전역 CSS 가 아니라 화면마다 들고 있는 것은
  detail-grid 가 아직 공통으로 승격되지 않아서다 — 쓰는 화면이 늘면 그때
  styles.css 로 옮기는 편이 낫다.
*/
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

/* 빠른 필터 줄 — 조회 조건 위에 붙는다 */
.quick-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

/* 등록 모달의 구획 제목. 배송지 · 주문 라인을 가른다 */
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg-dim, #6b7280);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
