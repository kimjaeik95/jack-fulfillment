<script setup>
/**
 * 결품 관리 (ORD-PG-006).
 *
 * 할당이 주문한 만큼 못 잡은 줄이 여기 모인다. 전부 못 잡은 것과 일부만
 * 잡은 것을 가르지 않는다 — 둘 다 물건이 덜 나가고, 할 일도 같다.
 *
 * 이 화면의 첫 질문은 '지금은 잡을 수 있나' 다. 결품은 대개 재고가 들어오면
 * 저절로 풀리는데, 들어온 줄 아무도 모르고 있는 것이 문제다. 그래서 줄마다
 * 지금 판매가능수량을 함께 보여 주고, 재고가 생긴 줄만 걸러 볼 수 있게 한다.
 *
 * 다시 할당하는 것은 주문 단위다. 줄 하나만 잡을 수 없는 것이 아니라,
 * 할당이 센터를 고를 때 그 주문의 모든 줄을 봐야 하기 때문이다 — 줄 단위로
 * 잡으면 한 주문이 여러 센터로 쪼개진다.
 *
 * 배치(ORD-BT-001/002)와 같은 코드를 쓴다. 화면에서 손으로 돌린 결과와
 * 밤에 배치가 낸 결과가 다르면 어느 쪽을 믿어야 할지 알 수 없다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as orderApi from '@/api/order.js'
import * as channelApi from '@/api/channel.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'

const session = useSessionStore()
const toast = useToastStore()

const size = orderApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const busy = ref(false)
const loadError = ref('')
const channels = ref([])

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).slice(0, 16).replace('T', ' ') : '-')

const canAlloc = computed(() => session.can('ORD_ALLOC', 'C'))
const allocDenyReason = computed(() => session.denyReason('ORD_ALLOC', 'C'))
const readDenyReason = computed(() => session.denyReason('ORD_ALLOC', 'R'))

/**
 * 기본은 '재고 생긴 것만' 이다.
 *
 * 결품 목록 전체는 대개 손쓸 수 없는 줄이라 봐도 할 일이 없다. 열었을 때
 * 바로 누를 수 있는 것이 보여야 이 화면에 다시 온다.
 */
const filters = reactive({ keyword: '', channelId: '', resolvableOnly: 'Y' })

async function fetchPage() {
  if (readDenyReason.value) return
  loading.value = true
  loadError.value = ''
  try {
    const data = await orderApi.shortages({
      keyword: filters.keyword || null,
      channelId: filters.channelId || null,
      resolvableOnly: filters.resolvableOnly || null,
      page: page.value,
      size,
    })
    rows.value = data.rows ?? []
    total.value = data.total ?? rows.value.length
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  fetchPage()
}

function resetFilters() {
  filters.keyword = ''
  filters.channelId = ''
  filters.resolvableOnly = 'Y'
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

/** 지금 보이는 것 중 바로 풀리는 줄 수 — 상단 버튼을 켤지 정한다 */
const resolvableCount = computed(() => rows.value.filter((r) => r.resolvable).length)

const columns = [
  { key: 'orderNo', label: '주문번호', width: '155px', cls: 'code' },
  { key: 'orderedAt', label: '주문일시', width: '132px' },
  { key: 'channelName', label: '채널', width: '100px' },
  { key: 'skuId', label: 'SKU', width: '170px' },
  { key: 'orderQty', label: '주문', width: '64px', align: 'right' },
  { key: 'allocatedQty', label: '잡음', width: '64px', align: 'right' },
  { key: 'shortQty', label: '모자람', width: '72px', align: 'right' },
  { key: 'qtyAvailable', label: '지금 재고', width: '86px', align: 'right' },
  { key: '_act', label: '', width: '110px', align: 'right' },
]

/* ── 다시 할당 ──────────────────────────────────────────────── */

async function retryOne(row) {
  if (!canAlloc.value) return toast.error(allocDenyReason.value)
  busy.value = true
  try {
    const { result, message } = await orderApi.allocate(row.orderSeq)
    if (result.allocatedQty > 0) toast.success(message)
    else toast.warn(message)
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/**
 * 재고가 생긴 결품 주문을 모두 다시 할당 (ORD-BT-002 를 손으로).
 *
 * 화면에 보이는 것만이 아니라 조건에 맞는 전부를 돈다. 페이지에 보이는
 * 것만 돌면 2 페이지에 있는 줄은 영원히 안 돌아간다.
 */
async function retryAll() {
  if (!canAlloc.value) return toast.error(allocDenyReason.value)
  busy.value = true
  try {
    const { result, message } = await orderApi.allocateRetry()
    if (result.succeeded > 0) toast.success(message)
    else toast.warn(message)
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/** 아직 한 줄도 안 잡은 주문을 쓸어 담아 할당 (ORD-BT-001 을 손으로) */
async function allocatePending() {
  if (!canAlloc.value) return toast.error(allocDenyReason.value)
  busy.value = true
  try {
    const { result, message } = await orderApi.allocatePending()
    if (result.succeeded > 0) toast.success(message)
    else toast.warn(message)
    await fetchPage()
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
        <h1 class="page-title">결품 관리</h1>
        <p class="page-desc">
          할당이 주문한 만큼 못 잡은 줄입니다. 대부분은
          <strong>재고가 들어오면 풀립니다</strong> — 들어온 줄 아무도 모르는 것이 문제라,
          줄마다 지금 재고를 함께 보여 줍니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn"
          :disabled="!canAlloc || busy"
          title="확정됐는데 아직 한 줄도 안 잡은 주문을 모두 할당합니다 (자동할당 배치와 같은 일)"
          @click="allocatePending()"
        >
          밀린 주문 할당
        </button>
        <button
          class="btn btn-primary"
          :disabled="!canAlloc || busy"
          title="재고가 생긴 결품 주문을 모두 다시 할당합니다 (재처리 배치와 같은 일)"
          @click="retryAll()"
        >
          <span v-if="busy" class="spinner"></span>
          재고 생긴 것 모두 재할당
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!--
      배치가 꺼져 있다는 사실을 화면에 적어 둔다. 안 적으면 '자동으로 되는
      줄 알았다' 가 된다.
    -->
    <div class="alert alert-info mb-2">
      <span class="alert-icon">ℹ️</span>
      <span>
        자동할당 · 재처리 배치는 <strong>기본으로 꺼져 있습니다.</strong>
        위 두 버튼이 같은 일을 손으로 합니다 — 운영에서 배치를 켜면 5 분 · 30 분마다
        스스로 돕니다.
      </span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색"
        placeholder="주문번호 / 수령인 / SKU / 제품명"
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
      <FormField
        v-model="filters.resolvableOnly"
        label="재고"
        type="select"
        placeholder="전체"
        :options="[{ value: 'Y', label: '지금 재고 있는 것만' }]"
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
      row-key="lineSeq"
      :show-pager="false"
      empty-text="결품 줄이 없습니다."
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim ellipsis">{{ row.receiverName }}</div>
      </template>

      <template #cell-orderedAt="{ value }">
        <span class="small">{{ dt(value) }}</span>
      </template>

      <template #cell-skuId="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim ellipsis">{{ row.productName }}</div>
      </template>

      <template #cell-shortQty="{ value }">
        <span class="short">{{ num(value) }}</span>
      </template>

      <!--
        이 화면에서 제일 중요한 칸이다. 모자란 만큼 이상이면 눌러서 다 풀리고,
        일부만 있으면 그만큼만 풀린다. 0 이면 기다리는 수밖에 없다.
      -->
      <template #cell-qtyAvailable="{ row, value }">
        <span v-if="!row.resolvable" class="dim">없음</span>
        <span v-else-if="row.fullyResolvable" class="ok">{{ num(value) }}</span>
        <span v-else class="partial">{{ num(value) }}</span>
      </template>

      <template #cell-_act="{ row }">
        <button
          class="btn btn-sm"
          :class="{ 'btn-primary': row.resolvable }"
          :disabled="!canAlloc || busy || !row.resolvable"
          :title="
            row.resolvable
              ? '이 주문을 다시 할당합니다'
              : '지금 재고가 없어 눌러도 잡히지 않습니다'
          "
          @click="retryOne(row)"
        >
          다시 할당
        </button>
      </template>
    </DataTable>

    <div class="pager">
      <span class="small dim">
        총 {{ num(total) }} 줄
        <template v-if="resolvableCount">
          · 이 페이지에서 <strong>{{ num(resolvableCount) }} 줄</strong>은 지금 풀 수 있습니다
        </template>
      </span>
      <span class="grow"></span>
      <button class="btn btn-sm" :disabled="page <= 1" @click="go(page - 1)">이전</button>
      <span class="small">{{ page }} / {{ totalPages }}</span>
      <button class="btn btn-sm" :disabled="page >= totalPages" @click="go(page + 1)">다음</button>
    </div>
  </div>
</template>

<style scoped>
/* 모자란 수량 — 이 화면이 존재하는 이유다 */
.short {
  color: var(--danger);
  font-weight: 600;
}

/* 지금 재고 — 다 채울 수 있으면 초록, 일부만이면 주황 */
.ok {
  color: var(--success);
  font-weight: 600;
}
.partial {
  color: var(--warn);
  font-weight: 600;
}

.pager {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.pager .grow {
  flex: 1;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}
</style>
