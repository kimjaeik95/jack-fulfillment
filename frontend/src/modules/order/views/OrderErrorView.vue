<script setup>
/**
 * 주문 오류대기 · 재처리 (ORD-PG-003, ORD-PG-004).
 *
 * 채널이 준 상품코드를 내부 SKU 로 바꾸지 못한 줄이 여기 모인다. 그런 줄은
 * 버리지 않고 SKU 없이 적재되며 (ORD-004, ORD-005), 주문은 확정도 할당도
 * 되지 않은 채 멈춰 있다.
 *
 * 주문이 아니라 외부코드를 한 행으로 놓는다. 고치는 단위가 그것이기
 * 때문이다 — 매핑 하나를 등록하면 그 코드로 막혀 있던 주문이 한꺼번에
 * 풀린다. 주문 목록에서 보면 '막힌 주문 12건' 인데, 여기서 보면 '코드 2개'
 * 이고 손댈 곳도 두 군데다.
 *
 * 푸는 길이 둘이고 화면도 그 순서다.
 *   재처리     채널 SKU 매핑을 등록 · 확정한 뒤 누른다. 근본 해결이다
 *   SKU 지정   이 줄에만 붙인다. 매핑은 그대로라 같은 코드가 또 오면 또 막힌다
 *
 * 왜 안 풀리는지를 행마다 적는다. 재처리 버튼만 있고 이유가 없으면 사용자는
 * 눌러도 0 건이 풀리는 것을 보고 다시 누른다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as orderApi from '@/api/order.js'
import * as channelApi from '@/api/channel.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import SkuPicker from '@/components/SkuPicker.vue'

const session = useSessionStore()
const toast = useToastStore()

const groups = ref([])
const channels = ref([])
const loading = ref(false)
const loadError = ref('')

const filters = reactive({ keyword: '', channelId: '' })

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).slice(0, 16).replace('T', ' ') : '-')

const canUpdate = computed(() => session.can('ORD_ORDER', 'U'))
const updateDenyReason = computed(() => session.denyReason('ORD_ORDER', 'U'))
const readDenyReason = computed(() => session.denyReason('ORD_ORDER', 'R'))

/* ── 조회 ───────────────────────────────────────────────────── */

async function fetchGroups() {
  if (readDenyReason.value) return
  loading.value = true
  loadError.value = ''
  try {
    groups.value = await orderApi.unmappedGroups({
      keyword: filters.keyword || null,
      channelId: filters.channelId || null,
    })
  } catch (e) {
    loadError.value = e.message
    groups.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.channelId = ''
  fetchGroups()
}

onMounted(async () => {
  try {
    const ch = await channelApi.list({ size: 0 })
    channels.value = ch.rows ?? ch
  } catch {
    // 채널 목록을 못 받아도 조회는 되어야 한다. 드롭다운만 비게 둔다.
  }
  await fetchGroups()
})

const channelOptions = computed(() =>
  channels.value.map((c) => ({ value: c.channelId, label: c.channelName })),
)

/**
 * 표에 넣을 행.
 *
 * 행 키를 따로 만든다. 외부 상품코드만으로는 모자라다 — 같은 코드가 다른
 * 채널에 있을 수 있고, 옵션이 있고 없고도 다른 묶음이다. 키가 겹치면
 * 표가 한 행만 그리거나 선택이 엉뚱한 곳에 붙는다.
 */
const groupRows = computed(() =>
  groups.value.map((g) => ({
    ...g,
    rowKey: `${g.channelId}|${g.extProductCode}|${g.extOptionCode ?? ''}`,
  })),
)

/** 화면 맨 위 요약. 얼마나 밀려 있는지 한 줄로 본다. */
const summary = computed(() => ({
  codes: groups.value.length,
  lines: groups.value.reduce((s, g) => s + (g.lineCount ?? 0), 0),
  ready: groups.value.filter((g) => g.reprocessable).length,
}))

const columns = [
  { key: 'extProductCode', label: '외부 상품코드', width: '190px', cls: 'code' },
  { key: 'channelName', label: '채널', width: '110px' },
  { key: 'lineCount', label: '막힌 줄', width: '80px', align: 'right' },
  { key: 'orderCount', label: '주문', width: '70px', align: 'right' },
  { key: 'totalQty', label: '수량', width: '76px', align: 'right' },
  { key: 'oldestOrderedAt', label: '가장 오래된', width: '132px' },
  { key: 'reason', label: '왜 막혔나' },
  { key: '_act', label: '', width: '170px', align: 'right' },
]

/**
 * 행마다 왜 막혔는지.
 *
 * 서버의 mappingStatus 를 사람 말로 옮긴다. 셋이 서로 다른 조치를 부르므로
 * 상태값만 보여 주면 무엇을 해야 하는지 알 수 없다.
 */
function reason(g) {
  if (g.reprocessable) {
    return { tone: 'ok', text: `매핑 확정됨 (${g.mappedSkuId}) — 재처리하면 풀립니다` }
  }
  if (g.mappingStatus === 'PENDING') {
    return {
      tone: 'warn',
      text: `매핑이 '확인 전' (${g.mappedSkuId}) — 채널 SKU 매핑에서 확정하세요`,
    }
  }
  if (g.mappingStatus === 'STOPPED') {
    return { tone: 'stop', text: '매핑이 중지 상태 — 일부러 막아 둔 것인지 먼저 확인하세요' }
  }
  return { tone: 'none', text: '등록된 매핑 없음 — 채널 SKU 매핑을 먼저 등록하세요' }
}

/** 안내 박스는 전역 alert 색을 쓴다. 화면마다 색을 새로 만들면 뜻이 갈린다. */
function alertClass(g) {
  if (g.reprocessable) return 'alert-info'
  return g.mappingStatus === 'STOPPED' ? 'alert-danger' : 'alert-warn'
}

/* ── 재처리 (ORD-PG-004) ────────────────────────────────────── */

const busy = ref(false)

async function reprocessOne(g) {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  await runReprocess({
    channelId: g.channelId,
    extProductCode: g.extProductCode,
    extOptionCode: g.extOptionCode,
  })
}

/**
 * 전부 다시 훑는다.
 *
 * 매핑을 여러 건 손본 뒤 한 번에 쓸어 담는 용도다. MAPPED 인 것만 붙으므로
 * 아직 확인 전인 코드는 그대로 남고, 여러 번 눌러도 결과가 같다.
 */
async function reprocessAll() {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  if (!summary.value.ready) {
    return toast.warn('지금 재처리로 풀릴 코드가 없습니다. 매핑을 먼저 확정하세요.')
  }
  await runReprocess({})
}

async function runReprocess(payload) {
  busy.value = true
  try {
    const { result, message } = await orderApi.reprocess(payload)
    if (result.resolved > 0) toast.success(message)
    else toast.warn(message)
    await fetchGroups()
    if (detail.value) await openDetail(detail.value.group)
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* ── 묶음 안의 줄 ───────────────────────────────────────────── */

const detail = ref(null)
const detailLoading = ref(false)

const lineColumns = [
  { key: 'orderNo', label: '주문번호', width: '155px', cls: 'code' },
  { key: 'lineNo', label: '줄', width: '55px', align: 'right' },
  { key: 'displayName', label: '채널 표시명' },
  { key: 'orderQty', label: '수량', width: '70px', align: 'right' },
  { key: '_act', label: '', width: '120px', align: 'right' },
]

async function openDetail(g) {
  detailLoading.value = true
  try {
    const page = await orderApi.unmappedLines({
      channelId: g.channelId,
      extProductCode: g.extProductCode,
      extOptionCode: g.extOptionCode,
      size: 100,
    })
    detail.value = { group: g, rows: page.rows, total: page.total }
  } catch (e) {
    toast.error(e.message)
  } finally {
    detailLoading.value = false
  }
}

/* ── SKU 직접 지정 ──────────────────────────────────────────── */

const assigning = ref(null)
const picking = ref(false)
const assignForm = reactive({ sku: null, reason: '' })

function openAssign(line) {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  assigning.value = line
  assignForm.sku = null
  assignForm.reason = ''
}

async function saveAssign() {
  if (!assignForm.sku) return toast.warn('붙일 SKU 를 고르세요.')
  busy.value = true
  try {
    await orderApi.assignSku(assigning.value.orderSeq, assigning.value.lineSeq, {
      skuId: assignForm.sku.skuId,
      reason: assignForm.reason || null,
    })
    toast.success(
      `${assigning.value.orderNo} ${assigning.value.lineNo} 번째 줄에 ${assignForm.sku.skuId} 를 붙였습니다.`,
    )
    assigning.value = null
    await fetchGroups()
    // 묶음이 통째로 사라졌으면 닫고, 남았으면 남은 줄만 다시 읽는다.
    if (detail.value) {
      const still = groups.value.find(
        (g) =>
          g.extProductCode === detail.value.group.extProductCode &&
          g.channelId === detail.value.group.channelId,
      )
      if (still) await openDetail(still)
      else detail.value = null
    }
  } catch (e) {
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
        <h1 class="page-title">주문 오류대기</h1>
        <p class="page-desc">
          채널 상품코드를 내부 SKU 로 바꾸지 못한 줄입니다. 주문은
          <strong>버리지 않고 보관</strong>되며 확정과 할당만 멈춰 있습니다 —
          매핑을 등록하고 재처리하면 한꺼번에 풀립니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canUpdate || busy || !summary.ready"
          :title="updateDenyReason ?? '매핑이 확정된 코드를 모두 재처리합니다'"
          @click="reprocessAll()"
        >
          <span v-if="busy" class="spinner"></span>
          전체 재처리
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!-- ── 요약 ─────────────────────────────────────────────── -->
    <div v-if="!readDenyReason" class="summary-row">
      <div class="sum">
        <span class="sum-n">{{ num(summary.codes) }}</span>
        <span class="sum-l">막힌 코드</span>
      </div>
      <div class="sum">
        <span class="sum-n">{{ num(summary.lines) }}</span>
        <span class="sum-l">막힌 줄</span>
      </div>
      <div class="sum" :class="{ ready: summary.ready > 0 }">
        <span class="sum-n">{{ num(summary.ready) }}</span>
        <span class="sum-l">지금 풀 수 있는 코드</span>
      </div>
    </div>

    <!-- ── 조회 조건 ────────────────────────────────────────── -->
    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색"
        placeholder="외부코드 / 채널 표시명 / 주문번호"
        @enter="fetchGroups()"
      />
      <FormField
        v-model="filters.channelId"
        label="채널"
        type="select"
        placeholder="전체"
        :options="channelOptions"
        @change="fetchGroups()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="fetchGroups()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
        <button class="btn" @click="resetFilters()">초기화</button>
      </div>
    </div>

    <!-- ── 외부코드별 묶음 ──────────────────────────────────── -->
    <DataTable
      :columns="columns"
      :rows="groupRows"
      :loading="loading"
      row-key="rowKey"
      clickable
      empty-text="막혀 있는 주문 줄이 없습니다."
      @row-click="openDetail"
    >
      <template #cell-extProductCode="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.extOptionCode" class="small dim mono">옵션 {{ row.extOptionCode }}</div>
        <div class="small dim ellipsis">{{ row.extProductName ?? '표시명 없음' }}</div>
      </template>

      <template #cell-lineCount="{ value }">
        <strong>{{ num(value) }}</strong>
      </template>

      <template #cell-oldestOrderedAt="{ value }">
        <span class="small">{{ dt(value) }}</span>
      </template>

      <template #cell-reason="{ row }">
        <span class="reason" :class="`tone-${reason(row).tone}`">{{ reason(row).text }}</span>
      </template>

      <template #cell-_act="{ row }">
        <button class="btn btn-sm" @click.stop="openDetail(row)">줄 보기</button>
        <button
          class="btn btn-sm btn-primary"
          :disabled="!canUpdate || busy || !row.reprocessable"
          :title="
            row.reprocessable
              ? '이 코드로 막힌 줄을 모두 풉니다'
              : '매핑이 확정되어야 재처리됩니다'
          "
          @click.stop="reprocessOne(row)"
        >
          재처리
        </button>
      </template>
    </DataTable>

    <!-- ── 묶음 안의 줄 ─────────────────────────────────────── -->
    <ModalDialog
      v-if="detail"
      :title="detail.group.extProductCode"
      :subtitle="`${detail.group.channelName} · ${detail.group.extProductName ?? '표시명 없음'}`"
      size="wide"
      @close="detail = null"
    >
      <div class="alert mb-2" :class="alertClass(detail.group)">
        <span class="alert-icon">{{ detail.group.reprocessable ? '✅' : '⚠️' }}</span>
        <span>{{ reason(detail.group).text }}</span>
      </div>

      <DataTable
        :columns="lineColumns"
        :rows="detail.rows"
        :loading="detailLoading"
        row-key="lineSeq"
        :show-pager="false"
        empty-text="남은 줄이 없습니다."
      >
        <template #cell-displayName="{ row, value }">
          <span>{{ value }}</span>
          <div v-if="row.remark" class="small dim ellipsis">{{ row.remark }}</div>
        </template>

        <template #cell-_act="{ row }">
          <button
            class="btn btn-sm"
            :disabled="!canUpdate || busy"
            :title="updateDenyReason ?? '이 줄에만 SKU 를 붙입니다'"
            @click="openAssign(row)"
          >
            SKU 지정
          </button>
        </template>
      </DataTable>

      <p class="note">
        이 줄들은 모두 같은 외부코드 때문에 막혀 있습니다. 채널 SKU 매핑을 등록하고
        <strong>재처리</strong>하면 한 번에 풀립니다. 줄마다 SKU 를 지정하는 것은
        같은 코드가 다시 들어오면 또 막히므로 일회성 건에만 쓰세요.
      </p>

      <template #footer>
        <button class="btn" @click="detail = null">닫기</button>
        <button
          class="btn btn-primary"
          :disabled="!canUpdate || busy || !detail.group.reprocessable"
          @click="reprocessOne(detail.group)"
        >
          재처리
        </button>
      </template>
    </ModalDialog>

    <!-- ── SKU 직접 지정 ────────────────────────────────────── -->
    <ModalDialog
      v-if="assigning"
      :title="`${assigning.orderNo} · ${assigning.lineNo} 번째 줄`"
      :subtitle="assigning.displayName"
      @close="assigning = null"
    >
      <div class="form-grid">
        <div class="picked">
          <span class="dt">붙일 SKU</span>
          <span v-if="assignForm.sku" class="dd">
            <span class="code">{{ assignForm.sku.skuId }}</span>
            <span class="small dim"> {{ assignForm.sku.productName }}</span>
          </span>
          <span v-else class="dd dim">아직 고르지 않았습니다</span>
          <button class="btn btn-sm" @click="picking = true">
            {{ assignForm.sku ? '다시 고르기' : 'SKU 고르기' }}
          </button>
        </div>

        <FormField
          v-model="assignForm.reason"
          label="사유"
          placeholder="예) 채널 상품페이지에서 확인"
          hint="감사로그와 줄 비고에 남습니다. 나중에 '이게 왜 이 상품이지' 를 묻게 되는 자리입니다."
        />
      </div>

      <template #footer>
        <button class="btn" @click="assigning = null">취소</button>
        <button class="btn btn-primary" :disabled="busy || !assignForm.sku" @click="saveAssign()">
          <span v-if="busy" class="spinner"></span>
          지정
        </button>
      </template>
    </ModalDialog>

    <SkuPicker
      v-if="picking"
      title="이 줄에 붙일 SKU"
      @pick="
        (sku) => {
          assignForm.sku = sku
          picking = false
        }
      "
      @close="picking = false"
    />
  </div>
</template>

<style scoped>
/* 요약 — 얼마나 밀려 있는지 표 위에서 먼저 보이게 한다 */
.summary-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.sum {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  min-width: 120px;
}
.sum-n {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.1;
}
.sum-l {
  font-size: 12px;
  color: var(--text-3);
}
/* 지금 손대면 결과가 나오는 숫자만 눈에 띄게 한다 */
.sum.ready .sum-n {
  color: var(--success);
}

/* 왜 막혔나 — 조치가 다르므로 색으로도 가른다 */
.reason {
  font-size: 12px;
  line-height: 1.4;
}
.tone-ok {
  color: var(--success);
}
.tone-warn {
  color: var(--warn);
}
.tone-stop {
  color: var(--danger);
}
.tone-none {
  color: var(--text-3);
}

/* SKU 고르기 한 줄 */
.picked {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.picked .dt {
  font-size: 12px;
  color: var(--text-3);
  min-width: 68px;
}
.picked .dd {
  flex: 1;
  min-width: 160px;
}

.note {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-2);
}
</style>
