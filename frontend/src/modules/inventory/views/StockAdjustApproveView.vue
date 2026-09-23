<script setup>
/**
 * 재고조정 승인 (INV-PG-007).
 *
 * 요청 화면과 같은 데이터를 보지만 보는 이유가 다르다. 요청자는 자기가
 * 올린 것을 보고, 승인자는 결재할 것을 본다. 그래서 화면도 다르다 —
 * 여기는 '승인 대기' 가 기본이고, 전표를 열지 않고도 무엇을 승인하는지
 * 보여야 한다.
 *
 * 승인은 <b>목표수량이 아니라 변동량</b>을 반영한다. 요청과 승인 사이에
 * 재고가 움직였을 수 있어서, 요청자가 "3 개 모자라더라" 고 했으면 승인
 * 시점에도 3 개를 빼는 것이 맞다. 그 사이 입고된 것까지 없애는 것은
 * 요청한 적 없는 일이다.
 *
 * 그래서 '요청 뒤 장부가 움직인 줄' 을 눈에 띄게 표시한다. 막지는 않는다 —
 * 재고가 움직였다고 요청이 무효가 되는 것은 아니지만, 결과가 요청자의
 * 목표와 다를 수 있다는 것은 승인자가 알고 눌러야 한다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as opsApi from '@/api/stockOps.js'
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

const size = opsApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: '',
  warehouseId: '',
  // 결재함이라 기본이 '승인 대기' 다. 끝난 것을 보려면 풀면 된다.
  pendingOnly: 'Y',
  fromDate: stockApi.daysAgo(90),
  toDate: stockApi.daysAgo(0),
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await opsApi.listAdjusts({ ...filters, page: page.value, size })
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
  await Promise.all([hierarchy.loadPlants(false), hierarchy.loadWarehouses(false)])
  await fetchPage()
})

const columns = [
  { key: 'adjustNo', label: '전표번호', width: '150px', cls: 'code' },
  { key: 'warehouseName', label: '창고', width: '120px' },
  { key: 'reasonName', label: '사유', width: '100px' },
  { key: 'lineCount', label: '품목', width: '58px', align: 'right' },
  { key: 'totalDelta', label: '순변동', width: '76px', align: 'right' },
  { key: 'requestedByName', label: '요청자', width: '90px' },
  { key: 'requestedAt', label: '요청시각', width: '118px' },
  { key: 'adjustStatus', label: '상태', width: '80px', align: 'center' },
  { key: '_act', label: '', width: '78px', align: 'right' },
]

/* ── 결재 ───────────────────────────────────────────────────── */

const detail = ref(null)
const busy = ref(false)
const decideError = ref('')
const rejectRemark = ref('')
const rejecting = ref(false)

async function open(row) {
  decideError.value = ''
  rejectRemark.value = ''
  rejecting.value = false
  try {
    detail.value = await opsApi.detailAdjust(row.adjustSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/** 자기가 올린 요청은 자기가 승인할 수 없다. 서버도 막지만 먼저 말해 준다. */
const isMine = computed(
  () => detail.value && detail.value.requestedBy === session.currentUserId,
)

async function approve() {
  busy.value = true
  decideError.value = ''
  try {
    const { adjust, warning } = await opsApi.approveAdjust(detail.value.adjustSeq, null)
    toast.success(`${adjust.adjustNo} — 승인했습니다. 재고에 반영되었습니다.`)
    if (warning) toast.warn(warning)
    detail.value = null
    await fetchPage()
  } catch (e) {
    decideError.value = e.message
  } finally {
    busy.value = false
  }
}

async function reject() {
  if (!rejectRemark.value.trim()) {
    decideError.value =
      '반려 사유는 필수입니다. 무엇을 고쳐야 하는지 적지 않으면 같은 요청이 그대로 다시 올라옵니다.'
    return
  }
  busy.value = true
  decideError.value = ''
  try {
    const adjust = await opsApi.rejectAdjust(detail.value.adjustSeq, rejectRemark.value.trim())
    toast.success(`${adjust.adjustNo} — 반려했습니다.`)
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
const signed = (v) => (v > 0 ? `+${nf.format(v)}` : nf.format(v))
const stamp = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const readDenyReason = computed(() => session.denyReason('INV_ADJ_APPROVE', 'R'))
const canApprove = computed(() => session.can('INV_ADJ_APPROVE', 'A'))
const approveDenyReason = computed(() => session.denyReason('INV_ADJ_APPROVE', 'A'))

/** 대기 중인 건수 — 결재함에 얼마나 쌓여 있는지 */
const pendingCount = computed(() => rows.value.filter((r) => r.pending).length)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">재고조정 승인</h1>
        <p class="page-desc">
          올라온 조정 요청을 결재합니다. <strong>승인하면 그 자리에서 재고가 바뀌고 되돌릴 수
          없습니다</strong> — 잘못 승인했으면 반대 방향으로 한 번 더 올려야 합니다.
          자기가 올린 요청은 자기가 승인할 수 없습니다.
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

    <div v-if="pendingCount" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>
        결재를 기다리는 조정이 <strong>{{ pendingCount }}건</strong> 있습니다.
        결재가 멈춰 있는 동안은 "틀린 줄 알면서 고치지 않고 있는" 상태가 이어집니다.
      </span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="전표번호 / 사유 / 비고"
          @enter="search()"
        />
        <FormField v-model="filters.fromDate" label="시작일" type="date" @change="search()" />
        <FormField v-model="filters.toDate" label="종료일" type="date" @change="search()" />
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="filters.warehouseId = ''; search()"
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
          :class="['chip', { on: filters.pendingOnly === 'Y' }]"
          @click="filters.pendingOnly = 'Y'; search()"
        >
          승인 대기
        </button>
        <button
          :class="['chip', { on: filters.pendingOnly !== 'Y' }]"
          title="승인·반려·취소까지 모두"
          @click="filters.pendingOnly = ''; search()"
        >
          처리 완료 포함
        </button>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="adjustSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        :muted-when="(a) => !a.pending"
        empty-text="결재할 조정 요청이 없습니다."
        @row-click="open"
      >
        <template #cell-lineCount="{ value }">{{ num(value) }}</template>
        <template #cell-totalDelta="{ value }">
          <strong :class="value > 0 ? 'ok' : value < 0 ? 'danger' : 'dim'">
            {{ signed(value ?? 0) }}
          </strong>
        </template>
        <template #cell-requestedAt="{ value }">
          <span class="small">{{ stamp(value) }}</span>
        </template>
        <template #cell-adjustStatus="{ value }">
          <CodeBadge group="ADJUST_STATUS" :code="value" />
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
      :title="detail.adjustNo"
      :subtitle="`${detail.plantName} · ${detail.warehouseName} · ${detail.requestedByName} 요청`"
      size="wide"
      @close="detail = null"
    >
      <div v-if="decideError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ decideError }}</span>
      </div>

      <div class="detail-head">
        <CodeBadge group="ADJUST_STATUS" :code="detail.adjustStatus" />
        <span>사유 <strong>{{ detail.reasonName }}</strong></span>
        <span>{{ stamp(detail.requestedAt) }}</span>
        <span>
          순변동
          <strong :class="detail.totalDelta > 0 ? 'ok' : detail.totalDelta < 0 ? 'danger' : 'dim'">
            {{ signed(detail.totalDelta ?? 0) }}
          </strong>
        </span>
      </div>
      <p v-if="detail.remark" class="small">{{ detail.remark }}</p>

      <div v-if="isMine" class="alert alert-danger mt-2">
        <span class="alert-icon">⛔</span>
        <span>
          자기가 올린 요청은 자기가 승인할 수 없습니다. 혼자 올리고 혼자 승인하면 승인은
          통제가 아니라 절차가 됩니다 — 다른 승인자에게 요청하세요.
        </span>
      </div>

      <div v-if="detail.staleLineCount" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span>
        <span>
          요청한 뒤 장부가 움직인 품목이 <strong>{{ detail.staleLineCount }}개</strong> 있습니다
          (아래 표에 표시). 승인하면 반영되는 것은 <strong>변동량</strong>이라, 결과가
          요청자가 적은 목표와 다를 수 있습니다.
        </span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 36px" class="right">#</th>
            <th style="width: 165px">재고주소</th>
            <th style="width: 145px">SKU</th>
            <th style="width: 78px">수량항목</th>
            <th style="width: 76px" class="right">요청시점</th>
            <th style="width: 76px" class="right">현재</th>
            <th style="width: 68px" class="right">변동</th>
            <th style="width: 88px" class="right">승인 후</th>
            <th style="width: 100px">사유</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in detail.lines" :key="l.lineSeq" :class="{ stale: l.stale }">
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.locationFullCode }}</span></td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td class="small">{{ l.qtyField === 'ON_HAND' ? '보유' : '판매불가' }}</td>
            <td class="num">{{ num(l.qtyBefore) }}</td>
            <td class="num">
              <span :class="{ warn: l.stale }">{{ num(l.qtyCurrent) }}</span>
            </td>
            <td class="num">
              <strong :class="l.increase ? 'ok' : 'danger'">{{ signed(l.qtyDelta) }}</strong>
            </td>
            <!-- 승인하면 실제로 얼마가 되는지. 목표(qtyAfter)가 아니라
                 '현재 + 변동' 이다 — 그 차이가 이 화면의 핵심이다. -->
            <td class="num">
              <strong>{{ num(l.qtyCurrent + l.qtyDelta) }}</strong>
              <div v-if="l.stale" class="small warn">목표 {{ num(l.qtyAfter) }}</div>
            </td>
            <td class="small">{{ l.reasonName ?? detail.reasonName }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 반려는 사유를 받고 나서 보낸다 -->
      <div v-if="rejecting" class="reject-box mt-2">
        <FormField
          v-model="rejectRemark"
          label="반려 사유"
          type="textarea"
          required
          :rows="2"
          placeholder="무엇을 고쳐 다시 올려야 하는지"
          help="적지 않으면 같은 요청이 그대로 다시 올라옵니다."
        />
      </div>

      <template #footer>
        <span class="left small dim">
          승인하면 그 자리에서 재고가 바뀌고 되돌릴 수 없습니다.
        </span>
        <button class="btn" :disabled="busy" @click="detail = null">닫기</button>
        <template v-if="detail.pending && !isMine">
          <button
            v-if="!rejecting"
            class="btn btn-danger"
            :disabled="busy || !canApprove"
            :title="approveDenyReason ?? '반려'"
            @click="rejecting = true"
          >
            반려
          </button>
          <button
            v-else
            class="btn btn-danger"
            :disabled="busy || !rejectRemark.trim()"
            @click="reject()"
          >
            <span v-if="busy" class="spinner"></span>
            반려 확정
          </button>
          <button
            class="btn btn-primary"
            :disabled="busy || rejecting || !canApprove"
            :title="approveDenyReason ?? '승인'"
            @click="approve()"
          >
            <span v-if="busy" class="spinner"></span>
            승인
          </button>
        </template>
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
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.stale td {
  background: color-mix(in srgb, var(--c-amber, #f59e0b) 8%, transparent);
}
.reject-box {
  padding: 10px 12px;
  border: 1px solid var(--c-red, #dc2626);
  border-radius: 8px;
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
