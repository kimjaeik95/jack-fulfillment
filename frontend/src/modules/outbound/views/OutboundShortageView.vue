<script setup>
/**
 * 피킹 결품 (OUT-PG-005).
 *
 * 집으러 갔는데 없던 줄을 모아 본다. 전산엔 있는데 실물이 없었다는
 * 기록이라, 이 목록은 <b>사실상 재고 오차 목록</b>이다.
 *
 * 처리하는 화면이 아니다. 결품을 적는 것은 물건을 찾으러 간 사람이 그
 * 자리에서 하고(피킹 화면), 여기서는 모아 놓고 <b>무엇이 자주 비는지</b>를
 * 본다 — 같은 SKU 나 같은 빈이 반복해서 뜨면 그 자리를 실사해야 한다.
 *
 * 그래서 '어느 빈에서 찾았어야 하나' 를 같이 보여 준다. 실사하러 갈 자리를
 * 못 짚으면 이 목록은 읽고 지나가는 종이가 된다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as outboundApi from '@/api/outbound.js'
import { codeOptions } from '@/api/codes.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const session = useSessionStore()
const hierarchy = useHierarchyStore()

const readDenyReason = computed(() =>
  session.can('OUT_SHORTAGE', 'R') ? '' : '피킹 결품을 볼 권한이 없습니다.',
)

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = outboundApi.PAGE_SIZE

const filters = reactive({ keyword: '', plantId: '', reasonCode: '', fromDate: '', toDate: '' })

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.shortages({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      reasonCode: filters.reasonCode || null,
      fromDate: filters.fromDate || null,
      toDate: filters.toDate || null,
      page: page.value,
      size,
    })
    rows.value = data.rows
    total.value = data.total
  } catch (e) {
    loadError.value = e.message
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function search() {
  page.value = 1
  await fetchPage()
}

function resetFilters() {
  filters.keyword = ''
  filters.plantId = ''
  filters.reasonCode = ''
  filters.fromDate = ''
  filters.toDate = ''
  search()
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

/**
 * 같은 SKU 가 몇 번 뜨나.
 *
 * 한 번은 실수일 수 있어도 반복되면 그 자리가 틀린 것이다. 목록만으로는
 * 눈에 안 띄어서 세어 둔다.
 */
const repeatOf = computed(() => {
  const m = {}
  for (const r of rows.value) m[r.skuId] = (m[r.skuId] ?? 0) + 1
  return m
})

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

/** '코드 — 설명' 으로 저장된 사유에서 코드만 떼어 낸다 */
const reasonCodeOf = (v) => (v ? String(v).split(' — ')[0] : '')
const reasonNoteOf = (v) => {
  const i = String(v ?? '').indexOf(' — ')
  return i < 0 ? '' : String(v).slice(i + 3)
}

const columns = [
  { key: 'skuId', label: 'SKU', width: '160px' },
  { key: 'productName', label: '스타일', width: '180px' },
  { key: 'locationHint', label: '찾았어야 할 빈', width: '130px', cls: 'code' },
  { key: 'shortageQty', label: '지시 / 집음 / 결품', width: '150px', align: 'right' },
  { key: 'shortageReason', label: '사유', width: '150px' },
  { key: 'outboundNo', label: '지시', width: '165px' },
  { key: 'plantName', label: '센터', width: '104px' },
  { key: 'assignedToName', label: '담당', width: '96px' },
  { key: 'instructedAt', label: '지시일시', width: '140px' },
  { key: '_act', label: '다음 작업', width: '150px', align: 'right' },
]

/*
 * 묶여 있는 수량.
 *
 * 결품을 적어도 <b>할당은 안 풀린다</b> — 실물이 없다는 것은 재고가 틀렸다는
 * 뜻이고, 할당만 풀면 판매가능이 늘어 다음 주문이 또 같은 자리를 잡기
 * 때문이다 (OutboundService.shortage 의 주석). 그래서 그 수량은 팔 수도
 * 내보낼 수도 없이 잡힌 채로 남는다.
 *
 * 전에는 그 사실이 화면 어디에도 없었다. '없었다' 는 과거만 보이고 '지금
 * 묶여 있다' 는 현재가 안 보이니, 읽고 지나가는 목록이 됐다. 할 일이 적힌
 * 곳은 알림뿐이었는데 그건 센터장만 본다.
 */
const lockedQty = computed(() =>
  rows.value.reduce((sum, r) => sum + (Number(r.shortageQty) || 0), 0),
)

const router = useRouter()

/** 장부엔 몇 개라고 되어 있나 — 고치기 전에 먼저 볼 곳 */
function goStock(row) {
  router.push({ name: 'stocks', query: { plantId: row.plantId, skuId: row.skuId } })
}

/** 실물에 맞춘다. 센터는 미리 채워 보낸다 — 줄에 이미 적혀 있는 값이다 */
function goAdjust(row) {
  router.push({ name: 'stock-adjust', query: { plantId: row.plantId, new: '1' } })
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">피킹 결품</h1>
        <p class="page-desc">
          집으러 갔는데 <strong>없던 것</strong>입니다. 전산에는 있는데 실물이 없었다는
          뜻이라, 이 목록은 사실상 <strong>재고 오차 목록</strong>입니다 — 같은 SKU 나 같은
          빈이 반복해서 뜨면 그 자리를 실사하세요.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!--
      '없었다' 가 아니라 '지금 묶여 있다' 를 말한다.

      결품을 적어도 할당은 안 풀리므로 그 수량은 팔 수도 내보낼 수도 없이
      잡힌 채로 남는다. 그 사실이 여태 알림에만 있었고 — 센터장만 본다 —
      이 화면에는 없었다. 그래서 목록이 '읽고 지나가는 종이' 가 됐다.
    -->
    <div v-if="!loading && lockedQty > 0" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>
        결품 {{ total }}건 · <strong>재고 {{ lockedQty }}개가 잡힌 채 묶여 있습니다.</strong>
        결품을 적어도 할당은 풀리지 않습니다 — 없는 물건을 다음 주문이 또 잡지 않게
        하려는 것입니다. <strong>실사나 재고조정으로 장부를 실물에 맞춰야</strong> 풀립니다.
        줄 오른쪽 <strong>다음 작업</strong>에서 바로 갈 수 있습니다.
      </span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색어"
        placeholder="지시번호 / 주문번호 / SKU / 스타일명"
        @enter="search()"
      />
      <FormField
        v-model="filters.plantId"
        label="센터"
        type="select"
        empty-option="전체"
        :options="hierarchy.plantOptions"
        @change="search()"
      />
      <FormField
        v-model="filters.reasonCode"
        label="사유"
        type="select"
        empty-option="전체"
        :options="codeOptions('REASON_PICK_SHORT')"
        @change="search()"
      />
      <FormField v-model="filters.fromDate" label="지시일 시작" type="date" @change="search()" />
      <FormField v-model="filters.toDate" label="끝" type="date" @change="search()" />
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
      row-key="lineSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      empty-text="집으러 갔는데 없던 건이 없습니다. 재고가 맞다는 뜻입니다."
    >
      <template #cell-skuId="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim">{{ row.colorCode }} / {{ row.sizeCode }}</div>
        <!-- 한 번은 실수일 수 있어도 반복되면 그 자리가 틀린 것이다 -->
        <div v-if="repeatOf[value] > 1" class="small danger">
          이 목록에 {{ repeatOf[value] }} 번 — 실사 대상
        </div>
      </template>

      <template #cell-shortageQty="{ row, value }">
        <span class="dim">{{ num(row.instructedQty) }} / </span>
        <span :class="row.pickedQty ? 'ok' : 'dim'">{{ num(row.pickedQty) }}</span>
        <span class="dim"> / </span>
        <strong class="danger">{{ num(value) }}</strong>
      </template>

      <template #cell-shortageReason="{ value }">
        <CodeBadge group="REASON_PICK_SHORT" :code="reasonCodeOf(value)" />
        <div v-if="reasonNoteOf(value)" class="small dim">{{ reasonNoteOf(value) }}</div>
      </template>

      <template #cell-outboundNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim">{{ row.orderNo }} · {{ row.receiverName }}</div>
      </template>

      <template #cell-instructedAt="{ value }">
        <span class="small">{{ dt(value) }}</span>
      </template>

      <!--
        다음으로 갈 곳.

        '그 자리를 실사하세요' 라고 적어 두기만 하면 빈코드를 눈으로 읽고
        다른 화면에서 다시 찾아야 한다. 센터와 SKU 는 이 줄이 이미 알고
        있으므로 채워서 보낸다.

        재고 보기가 먼저다 — 고치기 전에 '장부엔 몇 개라고 되어 있나' 를
        봐야 조정수량을 정할 수 있다.
      -->
      <template #cell-_act="{ row }">
        <button class="btn btn-sm" title="이 센터의 이 SKU 재고를 봅니다" @click="goStock(row)">
          재고 보기
        </button>
        <button class="btn btn-sm" title="장부를 실물에 맞춥니다" @click="goAdjust(row)">
          조정
        </button>
      </template>
    </DataTable>

    <div class="pager">
      <span class="small dim">총 {{ num(total) }}건 · {{ page }} / {{ totalPages }} 페이지</span>
      <div class="btn-row">
        <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">이전</button>
        <button class="btn btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">
          다음
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
</style>
