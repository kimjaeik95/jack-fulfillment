<script setup>
/**
 * 입고정정 요청 (INB-PG-008).
 *
 * 완료된 입고의 수량이 틀렸을 때 고친다. 재고조정과 헷갈리기 쉬운데 되감는
 * 범위가 다르다 — 재고조정은 재고 숫자만 고치고, 정정은 <b>재고 · 입고 ·
 * 발주 기입고</b>를 함께 되감는다.
 *
 * 그 차이가 중요한 이유는 하나다. 재고조정으로 때우면 재고는 맞아도 발주가
 * 다 들어온 것으로 닫혀 있어, <b>공급처에 다시 보내 달라고 할 잔량이 없다.</b>
 * 누구 잘못이었는지도 남지 않는다.
 *
 * 화면이 둘로 나뉜다. 위는 '무엇을 고칠까' 이고 아래는 '내가 올린 것' 이다.
 * 한 목록에 섞으면 완료된 입고 수백 건 사이에서 자기 요청을 찾게 된다.
 *
 * 정정은 <b>적치 행</b>에 건다. 한 줄을 여러 자리에 나눠 놓는 일이 흔한데,
 * 입고 라인에 걸면 어느 자리에서 뺄지를 시스템이 멋대로 정하게 되고 창고에
 * 가 보면 없는 자리에서 뺀 것이 된다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as inboundApi from '@/api/inbound.js'
import * as correctApi from '@/api/inboundCorrect.js'
import { codeOptions } from '@/api/codes.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const signed = (v) => (v > 0 ? `+${nf.format(v)}` : nf.format(v ?? 0))

const canCreate = computed(() => session.can('INB_CORRECTION', 'C'))
const denyReason = computed(() => session.denyReason('INB_CORRECTION', 'C'))

/* ── 위: 정정할 입고 고르기 ──────────────────────────────────── */

const doneRows = ref([])
const doneLoading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', plantId: '' })

async function fetchDone() {
  doneLoading.value = true
  loadError.value = ''
  try {
    // 완료된 것만. 진행 중인 입고는 정정할 이유가 없다 — 검수를 다시 하거나
    // 적치를 더 하면 된다.
    const page = await inboundApi.list({
      ...filters,
      inboundStatus: 'DONE',
      sortBy: 'plannedDate',
      sortDir: 'desc',
      size: inboundApi.PAGE_SIZE,
    })
    doneRows.value = page.rows
  } catch (e) {
    doneRows.value = []
    loadError.value = e.message
  } finally {
    doneLoading.value = false
  }
}

const doneColumns = [
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '120px' },
  { key: '_where', label: '받은 곳', width: '140px' },
  { key: '_qty', label: '예정 / 받음', width: '110px', align: 'right' },
  { key: 'orderNo', label: '발주', width: '140px', cls: 'code' },
  { key: '_act', label: '', width: '90px', align: 'right' },
]

/* ── 아래: 내가 올린 정정 ────────────────────────────────────── */

const myRows = ref([])
const myLoading = ref(false)
const myFilters = reactive({ correctStatus: '' })

async function fetchMine() {
  myLoading.value = true
  try {
    const page = await correctApi.list({
      correctStatus: myFilters.correctStatus || undefined,
      size: correctApi.PAGE_SIZE,
    })
    myRows.value = page.rows
  } catch (e) {
    myRows.value = []
    loadError.value = e.message
  } finally {
    myLoading.value = false
  }
}

const myColumns = [
  { key: 'correctNo', label: '정정번호', width: '150px', cls: 'code' },
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: 'reasonName', label: '사유', width: '110px' },
  { key: 'lineCount', label: '줄', width: '50px', align: 'right' },
  { key: '_delta', label: '순변동', width: '80px', align: 'right' },
  { key: 'correctStatus', label: '상태', width: '84px', align: 'center' },
  { key: 'requestedAt', label: '요청', width: '140px' },
]

const reload = async () => {
  await Promise.all([fetchDone(), fetchMine()])
}

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await reload()
})

/* ── 정정 작성 ───────────────────────────────────────────────── */

const target = ref(null)
const targetLines = ref([])
const busy = ref(false)
const serverError = ref('')
const editing = ref(null)
const head = reactive({ reasonCode: '', remark: '' })

/**
 * 줄별 입력.
 *
 * 부호를 직접 치게 하지 않는다. 창고에서는 "10 개 덜 왔다" 라고 말하지
 * "마이너스 10" 이라고 말하지 않고, 부호를 손으로 넣게 하면 방향을 반대로
 * 적는 사고가 반드시 난다. 방향과 개수를 따로 받아 서버에 보낼 때 합친다.
 */
const draft = reactive({})

function blankDraft() {
  return { dir: 'MINUS', qty: 0, reasonCode: '', remark: '' }
}

async function openCreate(row) {
  serverError.value = ''
  editing.value = null
  head.reasonCode = ''
  head.remark = ''
  try {
    const [full, lines] = await Promise.all([
      inboundApi.detail(row.inboundSeq),
      correctApi.targets(row.inboundSeq),
    ])
    for (const k of Object.keys(draft)) delete draft[k]
    for (const l of lines) draft[l.putawaySeq] = blankDraft()
    targetLines.value = lines
    target.value = full
  } catch (e) {
    loadError.value = e.message
  }
}

/** 이미 올린 요청을 다시 연다 — 승인 전이면 고칠 수 있다 */
async function openEdit(row) {
  serverError.value = ''
  try {
    const correct = await correctApi.detail(row.correctSeq)
    const [full, lines] = await Promise.all([
      inboundApi.detail(correct.inboundSeq),
      correctApi.targets(correct.inboundSeq),
    ])
    for (const k of Object.keys(draft)) delete draft[k]
    for (const l of lines) draft[l.putawaySeq] = blankDraft()
    for (const l of correct.lines) {
      draft[l.putawaySeq] = {
        dir: l.qtyDelta < 0 ? 'MINUS' : 'PLUS',
        qty: Math.abs(l.qtyDelta),
        reasonCode: l.reasonCode ?? '',
        remark: l.remark ?? '',
      }
    }
    head.reasonCode = correct.reasonCode
    head.remark = correct.remark ?? ''
    targetLines.value = lines
    target.value = full
    editing.value = correct
  } catch (e) {
    loadError.value = e.message
  }
}

const deltaOf = (d) => (d.dir === 'MINUS' ? -Math.abs(d.qty || 0) : Math.abs(d.qty || 0))

/** 개수를 적은 줄만 보낸다. 0 인 줄은 고치는 것이 없다. */
const filledLines = computed(() =>
  targetLines.value
    .map((l) => ({ line: l, d: draft[l.putawaySeq] }))
    .filter(({ d }) => d && Math.abs(d.qty || 0) > 0),
)

const totalDelta = computed(() =>
  filledLines.value.reduce((s, { d }) => s + deltaOf(d), 0),
)

/** 놓은 것보다 많이 빼려는 줄 — 서버도 막지만 누르기 전에 알아야 한다 */
const overLimit = computed(() =>
  filledLines.value.filter(({ line, d }) => deltaOf(d) < 0 && -deltaOf(d) > line.remainingQty),
)

const canSubmit = computed(
  () => canCreate.value && head.reasonCode && filledLines.value.length > 0 && !overLimit.value.length,
)

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const lines = filledLines.value.map(({ line, d }) => ({
      putawaySeq: line.putawaySeq,
      qtyDelta: deltaOf(d),
      reasonCode: d.reasonCode || null,
      remark: d.remark || null,
    }))
    const payload = { reasonCode: head.reasonCode, remark: head.remark || null, lines }
    const saved = editing.value
      ? await correctApi.update(editing.value.correctSeq, payload)
      : await correctApi.create(target.value.inboundSeq, payload)
    toast.success(`${saved.correctNo} 정정 요청을 올렸습니다. 승인되면 반영됩니다.`)
    target.value = null
    await reload()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

/* ── 요청 거두기 ─────────────────────────────────────────────── */

const askCancel = ref(null)

async function doCancel() {
  const row = askCancel.value
  askCancel.value = null
  try {
    await correctApi.cancel(row.correctSeq, '요청자가 거둠')
    toast.success(`${row.correctNo} 요청을 거뒀습니다.`)
    await reload()
  } catch (e) {
    loadError.value = e.message
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">입고정정 요청</h1>
        <p class="page-desc">
          완료된 입고의 수량이 틀렸을 때 고칩니다. 재고조정과 다릅니다 —
          정정은 <strong>재고 · 입고 · 발주 기입고를 함께 되감습니다.</strong>
          재고만 고치면 발주는 다 들어온 것으로 닫혀 있어
          <strong>공급처에 다시 보내 달라고 할 잔량이 없습니다.</strong>
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="denyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ denyReason }}</span>
    </div>

    <!-- ── 정정할 입고 고르기 ───────────────────────────────── -->
    <div class="card">
      <div class="card-head">
        <h2 class="card-title">정정할 입고</h2>
        <span class="small dim">완료된 입고만 나옵니다. 진행 중이면 검수·적치로 고치세요.</span>
      </div>
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="입고번호 / 발주번호 / 공급처"
          @enter="fetchDone()"
        />
        <FormField
          v-model="filters.plantId"
          label="센터"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="fetchDone()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="doneLoading" @click="fetchDone()">
            <span v-if="doneLoading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <DataTable
        :columns="doneColumns"
        :rows="doneRows"
        row-key="inboundSeq"
        :loading="doneLoading"
        :page-size="0"
        :show-pager="false"
        clickable
        empty-text="완료된 입고가 없습니다."
        @row-click="openCreate"
      >
        <template #cell-_where="{ row }">
          {{ row.plantName }}
          <div class="small dim">{{ row.warehouseName }}</div>
        </template>
        <template #cell-_qty="{ row }">
          {{ num(row.totalPlannedQty) }}
          <span class="dim"> / </span>
          <strong>{{ num(row.totalReceivedQty) }}</strong>
        </template>
        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              class="btn btn-sm"
              :disabled="!canCreate"
              :title="denyReason ?? '정정 요청'"
              @click.stop="openCreate(row)"
            >
              정정
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ── 내가 올린 정정 ───────────────────────────────────── -->
    <div class="card mt-2">
      <div class="card-head">
        <h2 class="card-title">올린 정정</h2>
        <span class="small dim">승인 전까지만 고치거나 거둘 수 있습니다.</span>
      </div>
      <div class="toolbar">
        <FormField
          v-model="myFilters.correctStatus"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('CORRECT_STATUS')"
          @change="fetchMine()"
        />
        <div class="toolbar-actions">
          <button class="btn" :disabled="myLoading" @click="fetchMine()">새로고침</button>
        </div>
      </div>

      <DataTable
        :columns="myColumns"
        :rows="myRows"
        row-key="correctSeq"
        :loading="myLoading"
        :page-size="0"
        :show-pager="false"
        clickable
        empty-text="올린 정정이 없습니다."
        @row-click="openEdit"
      >
        <template #cell-_delta="{ row }">
          <strong :class="row.totalDelta < 0 ? 'danger' : 'warn'">
            {{ signed(row.totalDelta) }}
          </strong>
        </template>
        <template #cell-correctStatus="{ value }">
          <CodeBadge group="CORRECT_STATUS" :code="value" />
        </template>
        <template #cell-requestedAt="{ value, row }">
          <span class="small">{{ (value ?? '').replace('T', ' ').slice(0, 16) }}</span>
          <div v-if="row.pending" class="btn-row">
            <button class="btn btn-sm btn-danger" @click.stop="askCancel = row">거두기</button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ── 정정 작성 ────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      :title="editing ? '정정 요청 수정' : '입고정정 요청'"
      :subtitle="`${target.inboundNo} · ${target.supplierName ?? '공급처 없음'}`"
      size="wide"
      @close="target = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="detail-head">
        <span v-if="target.orderNo">발주 <strong class="code">{{ target.orderNo }}</strong></span>
        <span>예정 <strong>{{ num(target.totalPlannedQty) }}</strong></span>
        <span>받음 <strong>{{ num(target.totalReceivedQty) }}</strong></span>
        <span>적치 <strong>{{ num(target.totalPutawayQty) }}</strong></span>
      </div>

      <div class="alert alert-warn mt-1">
        <span class="alert-icon">⚠</span>
        <span>
          승인되면 <strong>재고 · 입고 기입고 · 발주 기입고가 함께</strong> 움직입니다.
          발주 잔량이 되살아나 공급처에 다시 요청할 수 있게 됩니다.
          승인 뒤에는 되돌릴 수 없습니다 — 반대 방향으로 한 번 더 올려야 합니다.
        </span>
      </div>

      <div class="form-grid mt-2">
        <FormField
          v-model="head.reasonCode"
          label="정정 사유"
          type="select"
          required
          empty-option="고르세요"
          :options="codeOptions('REASON_CORRECT')"
          help="누구 잘못인지가 기준입니다. 이 구분이 없으면 공급처와 다툴 때 쓸 수 없습니다."
        />
        <FormField v-model="head.remark" label="비고" placeholder="예: 공급처 확인 완료" />
      </div>

      <!--
        적치 행마다 한 줄. 자리와 놓은 수량을 그대로 보여 주고 그 옆에서
        고치게 한다 — 창고에서 보는 것과 같은 단위여야 옮겨 적다 틀리지 않는다.
      -->
      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 150px">자리</th>
            <th style="width: 170px">SKU</th>
            <th style="width: 64px" class="right">놓음</th>
            <th style="width: 64px" class="right">남음</th>
            <th style="width: 64px" class="right">현재고</th>
            <th style="width: 92px">방향</th>
            <th style="width: 80px" class="right">개수</th>
            <th style="width: 120px">줄 사유</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="l in targetLines"
            :key="l.putawaySeq"
            :class="{ filled: Math.abs(draft[l.putawaySeq]?.qty || 0) > 0 }"
          >
            <td><span class="code">{{ l.locationId }}</span></td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td class="num">{{ num(l.putawayQty) }}</td>
            <td class="num">
              <strong :class="{ danger: l.remainingQty === 0 }">{{ num(l.remainingQty) }}</strong>
              <div v-if="l.correctedQty" class="small dim">정정 {{ signed(l.correctedQty) }}</div>
            </td>
            <td class="num dim">{{ num(l.qtyOnHand) }}</td>
            <td>
              <select v-model="draft[l.putawaySeq].dir" class="input input-sm">
                <option value="MINUS">덜 받음 −</option>
                <option value="PLUS">더 받음 +</option>
              </select>
            </td>
            <td>
              <input
                v-model.number="draft[l.putawaySeq].qty"
                class="input input-sm right"
                type="number"
                min="0"
                :max="draft[l.putawaySeq].dir === 'MINUS' ? l.remainingQty : undefined"
              />
            </td>
            <td>
              <select v-model="draft[l.putawaySeq].reasonCode" class="input input-sm">
                <option value="">헤더 사유</option>
                <option v-for="o in codeOptions('REASON_CORRECT')" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="overLimit.length" class="alert alert-danger mt-2">
        <span class="alert-icon">⛔</span>
        <span>
          놓은 적 없는 수량은 뺄 수 없습니다 —
          {{ overLimit.map((o) => `${o.line.locationId} (남음 ${o.line.remainingQty})`).join(', ') }}.
          다른 입고 것이면 그 입고를 정정하고, 원인을 모르면 재고조정으로 맞추세요.
        </span>
      </div>

      <template #footer>
        <span class="left small">
          <template v-if="filledLines.length">
            {{ filledLines.length }} 줄 · 순변동
            <strong :class="totalDelta < 0 ? 'danger' : 'warn'">{{ signed(totalDelta) }}</strong>
          </template>
          <span v-else class="dim">고칠 줄에 개수를 적으세요.</span>
        </span>
        <button class="btn" :disabled="busy" @click="target = null">닫기</button>
        <button class="btn btn-primary" :disabled="!canSubmit || busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          {{ editing ? '수정' : '정정 요청' }}
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askCancel"
      title="정정 요청 거두기"
      :message="`${askCancel.correctNo} 을(를) 거둡니까?`"
      detail="지우지 않고 '취소' 로 남습니다. 올렸다 거둔 사실 자체가 정보입니다. 재고는 바뀌지 않습니다."
      confirm-label="거두기"
      danger
      @cancel="askCancel = null"
      @confirm="doCancel()"
    />
  </div>
</template>

<style scoped>
.card-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 12px 14px 0;
  flex-wrap: wrap;
}
.card-title {
  font-size: 15px;
  margin: 0;
}
.lines th,
.lines td {
  padding: 5px 8px;
  vertical-align: middle;
}
.lines tr.filled td {
  background: var(--c-amber-soft, #fffbeb);
}
.input-sm {
  padding: 3px 6px;
  font-size: 12px;
  width: 100%;
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.mt-1 {
  margin-top: 6px;
}
.mt-2 {
  margin-top: 12px;
}
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
