<script setup>
/**
 * 구매요청 (PUR-PG-001).
 *
 * "이게 모자라니 사 주세요" 를 센터가 올린다. 아직 발주가 아니다 —
 * 본사 구매 담당이 결재해야 발주로 넘어간다.
 *
 * 한 요청에 여러 SKU 를 담는다. SKU 단위인 이유는 발주가 색상 · 사이즈별로
 * 나가기 때문이다 (PUR-002). 제품 단위로 올리면 그 아래 무엇을 얼마나
 * 사야 할지는 아무도 모른다.
 *
 * 담을 때 그 SKU 의 현재 판매가능 수량을 함께 보여 준다. 얼마나 부족한지를
 * 보고 요청수량을 정하는 것이 실제 순서라, 재고 화면을 따로 열게 하지 않는다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
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

const size = purchaseApi.PAGE_SIZE

/* ── 목록 ───────────────────────────────────────────────────── */

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: '',
  requestStatus: '',
  fromDate: stockApi.daysAgo(90),
  toDate: stockApi.daysAgo(0),
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await purchaseApi.list({ ...filters, page: page.value, size })
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

/** 공급처 드롭다운. 희망 공급처를 고를 때 쓴다. */
const suppliers = ref([])

onMounted(async () => {
  await hierarchy.loadPlants(false)
  try {
    // 거래중인 곳만. 거래중지된 공급처를 희망으로 적으면 발주가 안 나간다.
    const data = await supplierApi.list({ status: 'ACTIVE', useYn: 'Y', size: 0 })
    suppliers.value = data.rows
  } catch {
    // 공급처를 못 읽어도 요청은 올릴 수 있다 — 희망 공급처는 참고값이다
    suppliers.value = []
  }
  await fetchPage()
})

const supplierOptions = computed(() =>
  suppliers.value.map((s) => ({ value: s.supplierId, label: s.supplierName })),
)

const columns = [
  { key: 'requestNo', label: '요청번호', width: '150px', cls: 'code' },
  { key: 'plantName', label: '센터', width: '120px' },
  { key: 'reasonName', label: '사유', width: '100px' },
  { key: 'lineCount', label: '줄', width: '50px', align: 'right' },
  { key: '_qty', label: '요청 / 승인', width: '110px', align: 'right' },
  { key: 'requiredDate', label: '필요일', width: '100px', align: 'center' },
  { key: 'requestStatus', label: '상태', width: '84px', align: 'center' },
  { key: 'requestedByName', label: '요청자', width: '86px' },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

/* ── 작성 ───────────────────────────────────────────────────── */

const editing = ref(false)
const editSeq = ref(null)
const picking = ref(false)
const busy = ref(false)
const serverError = ref('')

/** 기본 필요일은 2주 뒤. 발주 → 생산 → 납품에 그만큼은 걸린다. */
const defaultRequiredDate = () => stockApi.daysAgo(-14)

const form = reactive({
  plantId: '',
  reasonCode: '',
  requiredDate: defaultRequiredDate(),
  remark: '',
})
/** 담은 줄 — { sku, requestQty, prefSupplierId, remark } */
const lines = ref([])

const pickedIds = computed(() => lines.value.map((l) => l.sku.skuId))

function openCreate() {
  editSeq.value = null
  Object.assign(form, {
    plantId: '',
    reasonCode: '',
    requiredDate: defaultRequiredDate(),
    remark: '',
  })
  lines.value = []
  serverError.value = ''
  editing.value = true
}

function addLine(sku) {
  picking.value = false
  lines.value = [
    ...lines.value,
    { sku, requestQty: 1, prefSupplierId: '', remark: '' },
  ]
}

function removeLine(i) {
  lines.value = lines.value.filter((_, idx) => idx !== i)
}

const lineError = (line) => {
  if (line.requestQty === '' || line.requestQty === null) return '요청수량을 입력하세요.'
  if (Number(line.requestQty) < 1) return '1 이상이어야 합니다.'
  return ''
}

const formValid = computed(
  () =>
    form.plantId &&
    form.reasonCode &&
    form.requiredDate &&
    lines.value.length > 0 &&
    lines.value.every((l) => !lineError(l)) &&
    !busy.value,
)

const totalQty = computed(() =>
  lines.value.reduce((sum, l) => sum + (lineError(l) ? 0 : Number(l.requestQty)), 0),
)

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const payload = {
      plantId: form.plantId,
      reasonCode: form.reasonCode,
      requiredDate: form.requiredDate,
      remark: form.remark || null,
      lines: lines.value.map((l) => ({
        skuId: l.sku.skuId,
        requestQty: Number(l.requestQty),
        prefSupplierId: l.prefSupplierId || null,
        remark: l.remark || null,
      })),
    }
    const { request, warning } = editSeq.value
      ? await purchaseApi.update(editSeq.value, payload)
      : await purchaseApi.create(payload)
    toast.success(`${request.requestNo} — ${request.lineCount} 줄을 올렸습니다.`)
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
const canceling = ref(false)

async function openDetail(row) {
  try {
    detail.value = await purchaseApi.detail(row.requestSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/** 결재 전이면 담긴 줄을 그대로 꺼내 고칠 수 있다 */
async function openEdit(row) {
  try {
    const full = await purchaseApi.detail(row.requestSeq)
    editSeq.value = full.requestSeq
    Object.assign(form, {
      plantId: full.plantId,
      reasonCode: full.reasonCode,
      requiredDate: full.requiredDate,
      remark: full.remark ?? '',
    })
    lines.value = full.lines.map((l) => ({
      sku: {
        skuId: l.skuId,
        productName: l.productName,
        colorCode: l.colorCode,
        sizeCode: l.sizeCode,
        currentAvailable: l.currentAvailable,
      },
      requestQty: l.requestQty,
      prefSupplierId: l.prefSupplierId ?? '',
      remark: l.remark ?? '',
    }))
    serverError.value = ''
    editing.value = true
  } catch (e) {
    loadError.value = e.message
  }
}

async function doCancel() {
  canceling.value = true
  try {
    await purchaseApi.cancel(askCancel.value.requestSeq, '요청 철회')
    toast.success(`${askCancel.value.requestNo} 을(를) 거둬들였습니다.`)
    askCancel.value = null
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
    askCancel.value = null
  } finally {
    canceling.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const canCreate = computed(() => session.can('PUR_REQUEST', 'C'))
const createDenyReason = computed(() => session.denyReason('PUR_REQUEST', 'C'))
const myId = computed(() => session.currentUserId ?? '')
/** 남이 올린 요청은 고치거나 거둘 수 없다. 결재자는 반려로 돌려보낸다. */
const mine = (row) => row.requestedBy === myId.value
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">구매요청</h1>
        <p class="page-desc">
          모자란 물건을 본사에 올립니다. <strong>아직 발주가 아닙니다</strong> —
          구매 담당이 결재해야 발주로 넘어가고, 그때 수량이 깎일 수 있습니다.
          색상 · 사이즈별로 발주가 나가므로 SKU 단위로 담습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '구매요청'"
          @click="openCreate()"
        >
          + 구매요청
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
          placeholder="요청번호 / 사유 / 비고"
          @enter="search()"
        />
        <FormField v-model="filters.fromDate" label="시작일" type="date" @change="search()" />
        <FormField v-model="filters.toDate" label="종료일" type="date" @change="search()" />
        <FormField
          v-model="filters.plantId"
          label="센터"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.requestStatus"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('REQUEST_STATUS')"
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
        row-key="requestSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        :muted-when="(r) => r.requestStatus === 'CANCELED' || r.requestStatus === 'REJECTED'"
        empty-text="조건에 맞는 구매요청이 없습니다."
        @row-click="openDetail"
      >
        <template #cell-lineCount="{ value }">{{ num(value) }}</template>

        <!-- 요청수량과 승인수량을 한 칸에 둔다. 둘을 떼어 놓으면 '얼마나
             깎였나' 를 눈으로 빼야 한다. -->
        <template #cell-_qty="{ row }">
          <strong>{{ num(row.totalRequestQty) }}</strong>
          <template v-if="row.totalApprovedQty !== null">
            <span class="dim"> / </span>
            <strong :class="row.fullyApproved ? 'ok' : 'warn'">
              {{ num(row.totalApprovedQty) }}
            </strong>
          </template>
          <span v-else class="dim"> / -</span>
        </template>

        <template #cell-requiredDate="{ row, value }">
          <span :class="{ danger: row.overdue }">{{ value }}</span>
          <div v-if="row.overdue" class="small danger">지남</div>
        </template>

        <template #cell-requestStatus="{ value }">
          <CodeBadge group="REQUEST_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <template v-if="row.pending && mine(row)">
              <button class="btn btn-sm" @click.stop="openEdit(row)">수정</button>
              <button class="btn btn-sm btn-danger" @click.stop="askCancel = row">취소</button>
            </template>
            <span
              v-else-if="row.pending"
              class="small dim"
              title="남이 올린 요청은 결재자가 반려로 돌려보냅니다"
            >
              {{ row.requestedByName }} 요청
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
      :title="editSeq ? '구매요청 수정' : '구매요청'"
      size="wide"
      @close="editing = false"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.plantId"
          label="받을 센터"
          type="select"
          required
          empty-option="선택하세요"
          :options="hierarchy.plantOptions"
          help="소속 센터에 대해서만 요청할 수 있습니다."
        />
        <FormField
          v-model="form.reasonCode"
          label="요청 사유"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('REASON_PURCHASE')"
        />
        <FormField
          v-model="form.requiredDate"
          label="필요일"
          type="date"
          required
          help="언제까지 필요한지. 구매 담당이 무엇부터 발주할지 정하는 근거입니다."
        />
        <FormField v-model="form.remark" label="비고" placeholder="사유코드로 설명되지 않는 부분" />
      </div>

      <div class="lines-head">
        <strong>요청할 SKU {{ lines.length }} 줄 · 합계 {{ num(totalQty) }}</strong>
        <button class="btn btn-sm btn-primary" @click="picking = true">+ SKU 담기</button>
      </div>

      <div v-if="!lines.length" class="empty-note">
        담은 SKU 가 없습니다. 'SKU 담기' 로 한 줄 이상 담으세요.
      </div>

      <table v-else class="table lines">
        <thead>
          <tr>
            <th style="width: 175px">SKU</th>
            <th style="width: 170px">제품</th>
            <th style="width: 80px" class="right">현재 가용</th>
            <th style="width: 92px">요청수량</th>
            <th style="width: 140px">희망 공급처</th>
            <th style="width: 40px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in lines" :key="l.sku.skuId">
            <td><span class="code">{{ l.sku.skuId }}</span></td>
            <td>
              {{ l.sku.productName }}
              <div class="small dim">{{ l.sku.colorCode }} / {{ l.sku.sizeCode }}</div>
            </td>
            <!-- 지금 팔 수 있는 수량. 얼마나 부족한지를 보고 요청수량을 정한다. -->
            <td class="num">
              <span v-if="l.sku.currentAvailable !== undefined" :class="l.sku.currentAvailable ? '' : 'danger'">
                {{ num(l.sku.currentAvailable) }}
              </span>
              <span v-else class="dim">-</span>
            </td>
            <td><input v-model.number="l.requestQty" type="number" class="input" min="1" /></td>
            <td>
              <select v-model="l.prefSupplierId" class="select">
                <option value="">지정 안 함</option>
                <option v-for="o in supplierOptions" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </td>
            <td><button class="btn btn-sm btn-danger" @click="removeLine(i)">×</button></td>
          </tr>
          <tr v-for="(l, i) in lines.filter((x) => lineError(x))" :key="`e${i}`">
            <td colspan="6" class="small danger">{{ l.sku.skuId }} — {{ lineError(l) }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          올려도 재고는 변하지 않습니다. 결재가 나야 발주로 넘어갑니다.
        </span>
        <button class="btn" :disabled="busy" @click="editing = false">취소</button>
        <button class="btn btn-primary" :disabled="!formValid" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          {{ editSeq ? '저장' : '요청 올리기' }}
        </button>
      </template>
    </ModalDialog>

    <SkuPicker
      v-if="picking"
      title="요청할 SKU 담기"
      :picked-ids="pickedIds"
      @pick="addLine"
      @close="picking = false"
    />

    <!-- ── 상세 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="detail"
      :title="detail.requestNo"
      :subtitle="`${detail.plantName} · 필요일 ${detail.requiredDate}`"
      size="wide"
      @close="detail = null"
    >
      <div class="detail-head">
        <CodeBadge group="REQUEST_STATUS" :code="detail.requestStatus" />
        <span>사유 <strong>{{ detail.reasonName }}</strong></span>
        <span>요청 <strong>{{ detail.requestedByName }}</strong></span>
        <span v-if="detail.decidedBy">결재 <strong>{{ detail.decidedByName }}</strong></span>
        <span>
          요청 <strong>{{ num(detail.totalRequestQty) }}</strong>
          <template v-if="detail.totalApprovedQty !== null">
            / 승인 <strong :class="detail.fullyApproved ? 'ok' : 'warn'">
              {{ num(detail.totalApprovedQty) }}
            </strong>
          </template>
        </span>
      </div>
      <p v-if="detail.remark" class="small">{{ detail.remark }}</p>
      <div v-if="detail.decideRemark" class="alert alert-warn mt-1">
        <span class="alert-icon">💬</span><span>{{ detail.decideRemark }}</span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 36px" class="right">#</th>
            <th style="width: 175px">SKU</th>
            <th style="width: 170px">제품</th>
            <th style="width: 76px" class="right">현재 가용</th>
            <th style="width: 76px" class="right">요청</th>
            <th style="width: 76px" class="right">승인</th>
            <th style="width: 130px">희망 공급처</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in detail.lines" :key="l.lineSeq" :class="{ dropped: l.dropped }">
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.skuId }}</span></td>
            <td>
              {{ l.productName }}
              <div class="small dim">{{ l.colorCode }} / {{ l.sizeCode }}</div>
            </td>
            <td class="num">{{ num(l.currentAvailable) }}</td>
            <td class="num">{{ num(l.requestQty) }}</td>
            <td class="num">
              <template v-if="!l.pending">
                <strong :class="l.full ? 'ok' : l.dropped ? 'danger' : 'warn'">
                  {{ num(l.approvedQty) }}
                </strong>
                <div v-if="l.cutQty" class="small warn">−{{ num(l.cutQty) }}</div>
              </template>
              <span v-else class="dim">대기</span>
            </td>
            <td class="small">{{ l.prefSupplierName ?? '-' }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          {{ detail.orderable
            ? '승인되었습니다. 구매오더로 넘어갈 수 있습니다.'
            : detail.pending
              ? '아직 결재되지 않았습니다.'
              : '발주 대상이 아닙니다.' }}
        </span>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askCancel"
      title="구매요청 취소"
      :message="`${askCancel.requestNo} 을(를) 거둬들입니까?`"
      detail="지우지 않고 '취소' 로 남습니다. 올렸다가 거둔 사실도 기록입니다 — 같은 SKU 를 올렸다 거두기를 반복하는 것이 보이면 수요 예측이 흔들리고 있다는 뜻입니다."
      confirm-label="거둬들이기"
      danger
      :busy="canceling"
      @cancel="askCancel = null"
      @confirm="doCancel()"
    />
  </div>
</template>

<style scoped>
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
.lines .select,
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
.dropped td {
  opacity: 0.55;
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
