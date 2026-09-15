<script setup>
/**
 * 구매요청 결재 (PUR-PG-002).
 *
 * 결재자가 하는 일은 하나다 — <b>줄마다 승인수량을 정하는 것</b>. 그래서
 * 이 화면에는 '승인/반려' 버튼이 먼저 있는 것이 아니라 수량 칸이 먼저 있다.
 *
 * 상태는 결과를 읽어 서버가 정한다.
 *   전 줄이 요청수량 그대로  → 승인
 *   일부가 깎였다            → 부분승인
 *   전 줄이 0                → 반려 (사유 필수)
 *
 * 화면에서도 같은 판정을 미리 보여 준다. 누르기 전에 "이게 승인인가
 * 부분승인인가" 를 알 수 있어야 하기 때문이다 — 다만 판정의 주인은 서버고,
 * 여기 것은 미리보기다.
 *
 * 깎는 것이 결재의 일이라 막지 않는다. 대신 왜 깎았는지를 적게 한다 —
 * 안 적으면 요청자는 다음에도 같은 수량을 올린다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as purchaseApi from '@/api/purchase.js'
import * as stockApi from '@/api/stock.js'
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

const size = purchaseApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: '',
  // 결재함이라 기본이 '승인 대기' 다.
  pendingOnly: 'Y',
  overdueOnly: '',
  fromDate: stockApi.daysAgo(180),
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

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await fetchPage()
})

const columns = [
  { key: 'requestNo', label: '요청번호', width: '150px', cls: 'code' },
  { key: 'plantName', label: '센터', width: '116px' },
  { key: 'reasonName', label: '사유', width: '96px' },
  { key: 'lineCount', label: '줄', width: '48px', align: 'right' },
  { key: 'totalRequestQty', label: '요청수량', width: '80px', align: 'right' },
  { key: 'requiredDate', label: '필요일', width: '98px', align: 'center' },
  { key: 'requestedByName', label: '요청자', width: '86px' },
  { key: 'requestStatus', label: '상태', width: '80px', align: 'center' },
  { key: '_act', label: '', width: '72px', align: 'right' },
]

/* ── 결재 ───────────────────────────────────────────────────── */

const detail = ref(null)
const busy = ref(false)
const decideError = ref('')
const remark = ref('')
/** lineSeq -> 승인수량. 열 때 요청수량으로 채운다. */
const approved = reactive({})

async function open(row) {
  decideError.value = ''
  remark.value = ''
  for (const k of Object.keys(approved)) delete approved[k]
  try {
    const full = await purchaseApi.detail(row.requestSeq)
    detail.value = full
    // 요청수량으로 채워 둔다. 결재자가 아무것도 안 고치면 전량 승인이고,
    // 0 으로 채워 두면 '아무것도 안 했는데 반려' 가 되어 위험하다.
    for (const l of full.lines) {
      approved[l.lineSeq] = l.requestQty
    }
  } catch (e) {
    loadError.value = e.message
  }
}

/** 자기가 올린 요청은 자기가 결재할 수 없다. 서버도 막지만 먼저 말해 준다. */
const isMine = computed(
  () => detail.value && detail.value.requestedBy === session.currentUserId,
)

/** 눌렀을 때 어떤 결론이 되는지 — 서버와 같은 규칙으로 미리 본다 */
const verdict = computed(() => {
  if (!detail.value) return null
  let anyApproved = false
  let anyCut = false
  let sum = 0
  for (const l of detail.value.lines) {
    const q = Number(approved[l.lineSeq] ?? 0)
    sum += q
    if (q > 0) anyApproved = true
    if (q < l.requestQty) anyCut = true
  }
  const status = !anyApproved ? 'REJECTED' : anyCut ? 'PARTIAL' : 'APPROVED'
  return { status, sum, anyCut }
})

const overQty = (line) => Number(approved[line.lineSeq] ?? 0) > line.requestQty

const canDecide = computed(() => {
  if (!detail.value || busy.value || isMine.value) return false
  if (detail.value.lines.some(overQty)) return false
  // 반려(전 줄 0)에는 사유가 필수다
  if (verdict.value?.status === 'REJECTED' && !remark.value.trim()) return false
  return true
})

/** 전 줄을 0 으로 — 반려의 지름길 */
function dropAll() {
  for (const l of detail.value.lines) approved[l.lineSeq] = 0
}

/** 요청수량 그대로 되돌린다 */
function restoreAll() {
  for (const l of detail.value.lines) approved[l.lineSeq] = l.requestQty
}

async function decide() {
  busy.value = true
  decideError.value = ''
  try {
    const lines = detail.value.lines.map((l) => ({
      lineSeq: l.lineSeq,
      approvedQty: Number(approved[l.lineSeq] ?? 0),
    }))
    const { request, warning } = await purchaseApi.decide(
      detail.value.requestSeq,
      lines,
      remark.value.trim() || null,
    )
    toast.success(`${request.requestNo} — ${statusLabel(request.requestStatus)} 처리했습니다.`)
    if (warning) toast.warn(warning)
    detail.value = null
    await fetchPage()
  } catch (e) {
    decideError.value = e.message
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const statusLabel = (s) =>
  ({ APPROVED: '승인', PARTIAL: '부분승인', REJECTED: '반려' })[s] ?? s

const readDenyReason = computed(() => session.denyReason('PUR_REQ_APPROVE', 'R'))
const canApprove = computed(() => session.can('PUR_REQ_APPROVE', 'A'))
const approveDenyReason = computed(() => session.denyReason('PUR_REQ_APPROVE', 'A'))

const pendingCount = computed(() => rows.value.filter((r) => r.pending).length)
const overdueCount = computed(() => rows.value.filter((r) => r.overdue).length)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">구매요청 결재</h1>
        <p class="page-desc">
          센터가 올린 요청을 결재합니다. <strong>줄마다 승인수량을 정하면 상태는 서버가
          판정합니다</strong> — 전 줄 그대로면 승인, 일부를 깎으면 부분승인, 전 줄이 0 이면
          반려입니다. 깎는 것은 결재의 일이지만, 왜 깎았는지 적지 않으면 요청자는 다음에도
          같은 수량을 올립니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="approveDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ approveDenyReason }}</span>
    </div>

    <div v-if="overdueCount" class="alert alert-danger mb-2">
      <span class="alert-icon">⏰</span>
      <span>
        필요일이 지난 요청이 <strong>{{ overdueCount }}건</strong> 있습니다.
        지금 승인해도 그만큼 납기가 밀립니다.
      </span>
    </div>
    <div v-else-if="pendingCount" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>결재를 기다리는 요청이 <strong>{{ pendingCount }}건</strong> 있습니다.</span>
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
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <div class="quick">
        <button
          :class="['chip', { on: filters.pendingOnly === 'Y' && !filters.overdueOnly }]"
          @click="filters.pendingOnly = 'Y'; filters.overdueOnly = ''; search()"
        >
          승인 대기
        </button>
        <button
          :class="['chip', { on: filters.overdueOnly === 'Y' }]"
          title="필요일이 지났는데 아직 결재 안 된 것"
          @click="filters.pendingOnly = ''; filters.overdueOnly = 'Y'; search()"
        >
          납기 지남
        </button>
        <button
          :class="['chip', { on: !filters.pendingOnly && !filters.overdueOnly }]"
          @click="filters.pendingOnly = ''; filters.overdueOnly = ''; search()"
        >
          처리 완료 포함
        </button>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="requestSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        :muted-when="(r) => !r.pending"
        empty-text="결재할 요청이 없습니다."
        @row-click="open"
      >
        <template #cell-lineCount="{ value }">{{ num(value) }}</template>
        <template #cell-totalRequestQty="{ value }">{{ num(value) }}</template>
        <template #cell-requiredDate="{ row, value }">
          <span :class="{ danger: row.overdue }">{{ value }}</span>
          <div v-if="row.overdue" class="small danger">지남</div>
        </template>
        <template #cell-requestStatus="{ value }">
          <CodeBadge group="REQUEST_STATUS" :code="value" />
        </template>
        <template #cell-_act="{ row }">
          <button v-if="row.pending" class="btn btn-sm btn-primary" @click.stop="open(row)">
            결재
          </button>
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

    <!-- ── 결재 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="detail"
      :title="detail.requestNo"
      :subtitle="`${detail.plantName} · ${detail.requestedByName} 요청 · 필요일 ${detail.requiredDate}`"
      size="wide"
      @close="detail = null"
    >
      <div v-if="decideError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ decideError }}</span>
      </div>

      <div class="detail-head">
        <CodeBadge group="REQUEST_STATUS" :code="detail.requestStatus" />
        <span>사유 <strong>{{ detail.reasonName }}</strong></span>
        <span v-if="detail.overdue" class="danger">⏰ 필요일이 지났습니다</span>
      </div>
      <p v-if="detail.remark" class="small">{{ detail.remark }}</p>

      <div v-if="isMine" class="alert alert-danger mt-2">
        <span class="alert-icon">⛔</span>
        <span>
          자기가 올린 요청은 자기가 결재할 수 없습니다. 혼자 올리고 혼자 승인하면 발주가
          통제 없이 나갑니다 — 다른 결재자에게 요청하세요.
        </span>
      </div>

      <div v-if="detail.pending" class="bulk-row">
        <span class="small dim">한 번에</span>
        <button class="btn btn-sm" :disabled="isMine" @click="restoreAll()">전량 승인</button>
        <button class="btn btn-sm btn-danger" :disabled="isMine" @click="dropAll()">전량 0 (반려)</button>
        <span class="small dim">
          줄별로 고치면 부분승인이 됩니다. 가용 재고를 보고 정하세요.
        </span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 36px" class="right">#</th>
            <th style="width: 175px">SKU</th>
            <th style="width: 160px">제품</th>
            <th style="width: 76px" class="right">현재 가용</th>
            <th style="width: 70px" class="right">요청</th>
            <th style="width: 92px">승인수량</th>
            <th style="width: 70px" class="right">깎임</th>
            <th style="width: 120px">희망 공급처</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="l in detail.lines"
            :key="l.lineSeq"
            :class="{ cut: Number(approved[l.lineSeq] ?? 0) < l.requestQty }"
          >
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.skuId }}</span></td>
            <td>
              {{ l.productName }}
              <div class="small dim">{{ l.colorCode }} / {{ l.sizeCode }}</div>
            </td>
            <!-- 결재자가 '정말 모자란가' 를 판단하는 값. 0 이면 빨갛게. -->
            <td class="num">
              <span :class="l.currentAvailable ? '' : 'danger'">{{ num(l.currentAvailable) }}</span>
            </td>
            <td class="num">{{ num(l.requestQty) }}</td>
            <td>
              <template v-if="detail.pending">
                <input
                  v-model.number="approved[l.lineSeq]"
                  type="number"
                  class="input"
                  min="0"
                  :max="l.requestQty"
                  :disabled="isMine"
                  :class="{ invalid: overQty(l) }"
                />
                <div v-if="overQty(l)" class="small danger">요청수량까지만</div>
              </template>
              <strong v-else :class="l.full ? 'ok' : l.dropped ? 'danger' : 'warn'">
                {{ num(l.approvedQty) }}
              </strong>
            </td>
            <td class="num">
              <span
                v-if="detail.pending && Number(approved[l.lineSeq] ?? 0) < l.requestQty"
                class="warn"
              >
                −{{ num(l.requestQty - Number(approved[l.lineSeq] ?? 0)) }}
              </span>
              <span v-else-if="!detail.pending && l.cutQty" class="warn">−{{ num(l.cutQty) }}</span>
              <span v-else class="dim">-</span>
            </td>
            <td class="small">{{ l.prefSupplierName ?? '-' }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 누르기 전에 무엇이 되는지 보여 준다. 판정의 주인은 서버고 이건 미리보기다. -->
      <div v-if="detail.pending && verdict" :class="['verdict', verdict.status.toLowerCase()]">
        <strong>{{ statusLabel(verdict.status) }}</strong>
        <span class="small">
          승인 합계 {{ num(verdict.sum) }} / 요청 {{ num(detail.totalRequestQty) }}
        </span>
        <span v-if="verdict.status === 'REJECTED'" class="small">
          한 줄도 승인하지 않으므로 반려입니다. 사유가 필요합니다.
        </span>
      </div>

      <FormField
        v-if="detail.pending"
        v-model="remark"
        label="결재 의견"
        type="textarea"
        :rows="2"
        class="mt-2"
        :required="verdict?.status === 'REJECTED'"
        :placeholder="verdict?.anyCut ? '왜 깎았는지 적어 주세요' : '필요하면 적습니다'"
        :help="
          verdict?.status === 'REJECTED'
            ? '반려에는 사유가 필수입니다. 없으면 같은 요청이 그대로 다시 올라옵니다.'
            : '깎았으면 이유를 적으세요. 없으면 요청자는 다음에도 같은 수량을 올립니다.'
        "
      />
      <div v-else-if="detail.decideRemark" class="alert alert-warn mt-2">
        <span class="alert-icon">💬</span><span>{{ detail.decideRemark }}</span>
      </div>

      <template #footer>
        <span class="left small dim">
          결재하면 되돌릴 수 없습니다. 승인수량이 그대로 발주 대상이 됩니다.
        </span>
        <button class="btn" :disabled="busy" @click="detail = null">닫기</button>
        <button
          v-if="detail.pending"
          class="btn btn-primary"
          :disabled="!canDecide || !canApprove"
          :title="approveDenyReason ?? '결재'"
          @click="decide()"
        >
          <span v-if="busy" class="spinner"></span>
          {{ verdict ? statusLabel(verdict.status) : '결재' }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
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
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.bulk-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 10px;
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
.cut td {
  background: color-mix(in srgb, var(--c-amber, #f59e0b) 8%, transparent);
}
.verdict {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--line, #e5e7eb);
}
.verdict.approved {
  border-color: var(--c-green, #16a34a);
  background: color-mix(in srgb, var(--c-green, #16a34a) 6%, transparent);
}
.verdict.partial {
  border-color: var(--c-amber, #f59e0b);
  background: color-mix(in srgb, var(--c-amber, #f59e0b) 6%, transparent);
}
.verdict.rejected {
  border-color: var(--c-red, #dc2626);
  background: color-mix(in srgb, var(--c-red, #dc2626) 6%, transparent);
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
