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
  { key: 'lineCount', label: '줄', width: '60px', align: 'right' },
  { key: 'totalQty', label: '수량', width: '76px', align: 'right' },
  { key: 'orderStatus', label: '상태', width: '100px' },
  { key: 'orderedAt', label: '주문일시', width: '132px' },
  { key: '_act', label: '', width: '150px', align: 'right' },
]

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).slice(0, 16).replace('T', ' ') : '-')

const canCreate = computed(() => session.can('ORD_ORDER', 'C'))
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
    serverError.value = '주문 라인을 한 줄 이상 담으세요.'
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
          </tr>
        </tbody>
      </table>

      <template #footer>
        <button
          v-if="picked.orderStatus === 'RECEIVED'"
          class="btn btn-primary"
          :disabled="!canUpdate || picked.hasUnmapped || confirming"
          :title="picked.hasUnmapped ? 'SKU 가 안 붙은 줄이 있어 확정할 수 없습니다' : '확정'"
          @click="doConfirm(picked)"
        >
          확정
        </button>
        <button class="btn" @click="picked = null">닫기</button>
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
