<script setup>
/**
 * 재고할당 (ORD-PG-005).
 *
 * 확정된 주문에 실제 재고를 붙인다. 붙인다는 것은 그 수량을 남이 못 가져가게
 * 잡아 두는 것이지 물건을 옮기는 것이 아니다 — 물건은 4차 피킹에서 움직인다.
 *
 * 주문 화면과 나눈 이유는 보는 것이 다르기 때문이다. 주문 화면은 '무엇을
 * 누가 시켰나' 를 보고, 여기는 '지금 잡을 수 있나' 를 본다. 같은 주문이라도
 * 여기서 필요한 것은 줄마다의 가용재고와 어느 센터에서 나갈지다.
 *
 * 어느 빈에서 잡을지는 서버가 정한다 (ALC-002). 사람이 고르게 두면 매번
 * 다른 판단이 섞이고, 그 판단은 재고 상황을 다 보고 해야 하는 것이라
 * 화면에서 할 수 있는 일이 아니다. 대신 잡은 뒤 어디서 잡았는지 보여 준다.
 *
 * 모자라면 잡히는 만큼만 잡고 그 줄은 결품이 된다 (ALC-003). 주문 전체를
 * 막지 않는다 — 열 줄 중 한 줄 때문에 나갈 수 있는 아홉 줄을 묶어 둘 수는
 * 없다.
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

const session = useSessionStore()
const toast = useToastStore()

const size = orderApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')
const channels = ref([])

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).slice(0, 16).replace('T', ' ') : '-')

const canAlloc = computed(() => session.can('ORD_ALLOC', 'C'))
const canRelease = computed(() => session.can('ORD_ALLOC', 'D'))
const allocDenyReason = computed(() => session.denyReason('ORD_ALLOC', 'C'))
const releaseDenyReason = computed(() => session.denyReason('ORD_ALLOC', 'D'))
const readDenyReason = computed(() => session.denyReason('ORD_ALLOC', 'R'))

/**
 * 빠른 필터.
 *
 * 이 화면에 오는 이유는 둘이다 — 아직 안 잡은 것을 잡거나, 결품 난 것을
 * 다시 보거나. 조건을 조합하게 하는 대신 그 질문 그대로 버튼을 둔다.
 */
const QUICK = [
  { key: 'todo', label: '할당 대기', desc: '확정됐지만 아직 다 못 잡은 주문', status: 'CONFIRMED' },
  { key: 'done', label: '할당완료', desc: '모든 줄이 잡힌 주문', status: 'ALLOCATED' },
  { key: 'all', label: '전체', desc: '확정 이후 주문 전부', status: '' },
]
const quick = ref('todo')

const filters = reactive({ keyword: '', channelId: '' })

async function fetchPage() {
  if (readDenyReason.value) return
  loading.value = true
  loadError.value = ''
  try {
    const q = QUICK.find((x) => x.key === quick.value)
    const data = await orderApi.list({
      keyword: filters.keyword || null,
      channelId: filters.channelId || null,
      orderStatus: q?.status || null,
      page: page.value,
      size,
      sortBy: 'orderedAt',
      sortDir: 'asc',
    })
    // 확정 이전 주문은 할당 대상이 아니다. '전체' 를 골라도 접수 · 취소는
    // 빼 둔다 — 여기서 할 수 있는 일이 없는 줄이 목록을 채우면 방해만 된다.
    rows.value = (data.rows ?? []).filter((o) =>
      ['CONFIRMED', 'ALLOCATED', 'PICKING', 'SHIPPED'].includes(o.orderStatus),
    )
    total.value = data.total ?? rows.value.length
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

function applyQuick(key) {
  quick.value = key
  page.value = 1
  fetchPage()
}

function search() {
  page.value = 1
  fetchPage()
}

function resetFilters() {
  filters.keyword = ''
  filters.channelId = ''
  search()
}

const totalPages = computed(() => Math.max(Math.ceil(total.value / size), 1))
async function go(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

onMounted(async () => {
  try {
    const ch = await channelApi.list({ size: 0 })
    channels.value = ch.rows ?? ch
  } catch {
    // 채널 목록을 못 받아도 조회는 되어야 한다.
  }
  await fetchPage()
})

const channelOptions = computed(() =>
  channels.value.map((c) => ({ value: c.channelId, label: c.channelName })),
)

const columns = [
  { key: 'orderNo', label: '주문번호', width: '155px', cls: 'code' },
  { key: 'channelName', label: '채널', width: '110px' },
  { key: 'receiverName', label: '수령인', width: '130px' },
  { key: 'lineCount', label: '줄', width: '56px', align: 'right' },
  { key: 'totalQty', label: '수량', width: '70px', align: 'right' },
  { key: 'orderStatus', label: '상태', width: '100px' },
  { key: 'orderedAt', label: '주문일시', width: '132px' },
  { key: '_act', label: '', width: '160px', align: 'right' },
]

/* ── 상세 · 할당 ────────────────────────────────────────────── */

const picked = ref(null)
const allocs = ref([])
const busy = ref(false)

async function openDetail(order) {
  try {
    const [detail, list] = await Promise.all([
      orderApi.detail(order.orderSeq),
      orderApi.allocations(order.orderSeq),
    ])
    picked.value = detail
    allocs.value = list
  } catch (e) {
    toast.error(e.message)
  }
}

/** 줄마다 잡힌 양을 모아 둔다. 상세 표가 '몇 개 중 몇 개' 를 보여야 한다. */
const allocsByLine = computed(() => {
  const map = {}
  for (const a of allocs.value) {
    if (a.qtyHeld <= 0) continue
    ;(map[a.orderLineSeq] ??= []).push(a)
  }
  return map
})

/** 푼 기록. 왜 같은 주문에 할당이 여러 건인지 설명한다. */
const releasedAllocs = computed(() => allocs.value.filter((a) => a.qtyReleased > 0))

async function doAllocate(order) {
  if (!canAlloc.value) return toast.error(allocDenyReason.value)
  busy.value = true
  try {
    const { result, message } = await orderApi.allocate(order.orderSeq)
    if (result.shortLines > 0) toast.warn(message)
    else toast.success(message)
    await fetchPage()
    if (picked.value?.orderSeq === order.orderSeq) await openDetail(order)
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* ── 할당해제 ───────────────────────────────────────────────── */

const releasing = ref(null)
const releaseReason = ref('')

function openRelease(order) {
  if (!canRelease.value) return toast.error(releaseDenyReason.value)
  releasing.value = order
  releaseReason.value = ''
}

async function doRelease() {
  if (!releaseReason.value) return toast.warn('푸는 사유를 고르세요.')
  busy.value = true
  try {
    const { message } = await orderApi.releaseAllocation(
      releasing.value.orderSeq,
      releaseReason.value,
    )
    toast.success(message)
    const target = releasing.value
    releasing.value = null
    await fetchPage()
    if (picked.value?.orderSeq === target.orderSeq) await openDetail(target)
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/** 이 주문이 잡아 둔 것이 있나 — 해제 버튼을 켜는 조건 */
function hasLiveAlloc(order) {
  return ['ALLOCATED', 'CONFIRMED'].includes(order.orderStatus)
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">재고할당</h1>
        <p class="page-desc">
          확정된 주문에 재고를 잡아 둡니다. 물건은 아직 움직이지 않습니다 —
          <strong>남이 못 가져가게 예약</strong>하는 것이고, 실제로 집는 것은 피킹입니다.
          모자라면 잡히는 만큼만 잡고 그 줄은 결품이 됩니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

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

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색"
        placeholder="주문번호 / 채널주문번호 / 수령인"
        @enter="search()"
      />
      <FormField
        v-model="filters.channelId"
        label="채널"
        type="select"
        placeholder="전체"
        :options="channelOptions"
        @change="search()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
        <button class="btn" @click="resetFilters()">초기화</button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      row-key="orderSeq"
      clickable
      :selected-key="picked?.orderSeq ?? null"
      :show-pager="false"
      empty-text="할당할 주문이 없습니다."
      @row-click="openDetail"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.extOrderNo" class="small dim mono">{{ row.extOrderNo }}</div>
      </template>

      <template #cell-orderStatus="{ value }">
        <CodeBadge group="SALES_ORDER_STATUS" :code="value" />
      </template>

      <template #cell-orderedAt="{ value }">
        <span class="small">{{ dt(value) }}</span>
      </template>

      <template #cell-_act="{ row }">
        <button
          class="btn btn-sm btn-primary"
          :disabled="!canAlloc || busy || ['PICKING', 'SHIPPED'].includes(row.orderStatus)"
          :title="allocDenyReason ?? '재고를 잡습니다. 두 번 눌러도 안전합니다.'"
          @click.stop="doAllocate(row)"
        >
          할당
        </button>
        <button
          class="btn btn-sm"
          :disabled="!canRelease || busy || !hasLiveAlloc(row)"
          :title="releaseDenyReason ?? '잡아 둔 재고를 풉니다'"
          @click.stop="openRelease(row)"
        >
          해제
        </button>
      </template>
    </DataTable>

    <div v-if="totalPages > 1" class="pager">
      <button class="btn btn-sm" :disabled="page <= 1" @click="go(page - 1)">이전</button>
      <span class="small">{{ page }} / {{ totalPages }} · 총 {{ num(total) }} 건</span>
      <button class="btn btn-sm" :disabled="page >= totalPages" @click="go(page + 1)">다음</button>
    </div>

    <!-- ── 상세 — 어디서 얼마나 잡았나 ──────────────────────── -->
    <ModalDialog
      v-if="picked"
      :title="picked.orderNo"
      :subtitle="`${picked.channelName ?? ''} · ${picked.receiverName}`"
      size="wide"
      @close="picked = null"
    >
      <div class="section-title">주문 줄</div>
      <table class="mini">
        <thead>
          <tr>
            <th style="width: 40px">줄</th>
            <th>SKU</th>
            <th style="width: 70px" class="r">주문</th>
            <th style="width: 70px" class="r">잡음</th>
            <th style="width: 70px" class="r">모자람</th>
            <th style="width: 90px">상태</th>
            <th>잡은 곳</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in picked.lines" :key="l.lineSeq">
            <td>{{ l.lineNo }}</td>
            <td>
              <span class="code">{{ l.skuId ?? '(미매핑)' }}</span>
              <div class="small dim ellipsis">{{ l.displayName }}</div>
            </td>
            <td class="r">{{ num(l.orderQty) }}</td>
            <td class="r"><strong>{{ num(l.qtyAllocated) }}</strong></td>
            <td class="r">
              <span v-if="l.remainQty > 0" class="short">{{ num(l.remainQty) }}</span>
              <span v-else class="dim">-</span>
            </td>
            <td><CodeBadge group="SALES_LINE_STATUS" :code="l.lineStatus" /></td>
            <td>
              <!--
                한 줄이 여러 빈에서 나올 수 있다 (ALC-002). 피킹이 몇 군데를
                도는지가 여기서 드러난다.
              -->
              <div v-for="a in allocsByLine[l.lineSeq] ?? []" :key="a.allocSeq" class="small">
                <span class="mono">{{ a.locationFullCode }}</span>
                <span class="dim"> {{ a.plantName }}</span>
                <strong> {{ num(a.qtyHeld) }}개</strong>
              </div>
              <span v-if="!(allocsByLine[l.lineSeq] ?? []).length" class="dim small">
                아직 안 잡음
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <!--
        푼 기록을 따로 보여 준다. 없으면 '왜 같은 주문에 할당이 여러 건이지'
        를 설명할 수 없다.
      -->
      <template v-if="releasedAllocs.length">
        <div class="section-title">푼 기록</div>
        <table class="mini">
          <thead>
            <tr>
              <th style="width: 40px">줄</th>
              <th>잡았던 곳</th>
              <th style="width: 70px" class="r">푼 수량</th>
              <th style="width: 110px">사유</th>
              <th style="width: 132px">시각</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in releasedAllocs" :key="a.allocSeq">
              <td>{{ a.orderLineNo }}</td>
              <td><span class="mono small">{{ a.locationFullCode }}</span></td>
              <td class="r">{{ num(a.qtyReleased) }}</td>
              <td class="small">{{ a.releaseReasonName ?? a.releaseReason ?? '-' }}</td>
              <td class="small">{{ dt(a.releasedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </template>

      <template #footer>
        <button class="btn" @click="picked = null">닫기</button>
        <button
          class="btn"
          :disabled="!canRelease || busy || !hasLiveAlloc(picked)"
          @click="openRelease(picked)"
        >
          할당해제
        </button>
        <button
          class="btn btn-primary"
          :disabled="!canAlloc || busy || ['PICKING', 'SHIPPED'].includes(picked.orderStatus)"
          @click="doAllocate(picked)"
        >
          <span v-if="busy" class="spinner"></span>
          할당
        </button>
      </template>
    </ModalDialog>

    <!-- ── 할당해제 ─────────────────────────────────────────── -->
    <ModalDialog
      v-if="releasing"
      :title="`${releasing.orderNo} 할당해제`"
      subtitle="잡아 둔 재고를 모두 풉니다"
      @close="releasing = null"
    >
      <div class="alert alert-warn mb-2">
        <span class="alert-icon">⚠️</span>
        <span>
          이 주문이 잡고 있던 수량이 <strong>판매가능수량으로 돌아갑니다.</strong>
          다른 주문이 먼저 가져갈 수 있으니, 다시 할당할 생각이라면 그때 같은 재고가
          남아 있지 않을 수 있습니다.
        </span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="releaseReason"
          label="사유"
          type="select"
          required
          placeholder="고르세요"
          :options="codeOptions('REASON_SHORT')"
          hint="재고이력과 감사로그에 남습니다. 없으면 재고가 왜 돌아왔는지 설명할 수 없습니다."
        />
      </div>

      <template #footer>
        <button class="btn" @click="releasing = null">취소</button>
        <button class="btn btn-danger" :disabled="busy || !releaseReason" @click="doRelease()">
          <span v-if="busy" class="spinner"></span>
          해제
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.quick-row {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
  margin: 14px 0 6px;
}
.section-title:first-child {
  margin-top: 0;
}

/* 모달 안의 작은 표 — 줄마다 어디서 몇 개를 잡았는지 */
.mini {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.mini th,
.mini td {
  border-bottom: 1px solid var(--border);
  padding: 6px 8px;
  text-align: left;
  vertical-align: top;
}
.mini th {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-3);
  background: var(--surface-2);
}
.mini .r {
  text-align: right;
}

/* 못 채운 수량 — 이 화면에서 유일하게 조치가 필요한 숫자다 */
.short {
  color: var(--danger);
  font-weight: 600;
}

.pager {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 10px;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}
</style>
