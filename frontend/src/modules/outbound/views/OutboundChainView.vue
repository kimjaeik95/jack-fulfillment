<script setup>
/**
 * 수량 체인 검증 (OUT-PG-007).
 *
 * <b>지시 → 집음 → 검수 → 담음 → 출고</b> 를 한 줄에 세운다.
 *
 * 각 단계가 앞 단계를 넘을 수 없게 막아 뒀으므로 이 줄은 늘 내림차순이고,
 * <b>어디서 숫자가 꺾이는지가 곧 어디서 틀어졌나</b> 이다.
 *
 *   지시 10 집음 10 검수 10 담음 10 출고 10   정상
 *   지시 10 집음  8 결품  2 …                 설명된 차이 — 피킹 결품
 *   지시 10 집음 10 검수  9 …                 설명 없는 차이 — 사고
 *
 * 둘의 차이를 보여 주는 것이 이 화면의 목적이다. 결품은 사유가 남아 있고,
 * 사고는 아무 설명이 없다.
 *
 * 기본은 <b>틀어진 것만</b>이다. 정상 건까지 보여 주면 찾으려던 것이 묻힌다 —
 * 대사하러 오는 자리지 진행 현황을 보는 자리가 아니다. 그리고 단계마다
 * <b>그 단계가 끝났을 때만</b> 본다: 패킹 중인 지시는 담음이 아직 0 이라
 * 늘 꺾여 보이는데, 그것까지 사고로 잡으면 목록이 진행중 목록이 된다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as outboundApi from '@/api/outbound.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const session = useSessionStore()
const hierarchy = useHierarchyStore()

const readDenyReason = computed(() =>
  session.can('OUT_TARGET', 'R') ? '' : '수량 체인을 볼 권한이 없습니다.',
)

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = outboundApi.PAGE_SIZE

const filters = reactive({ keyword: '', plantId: '', brokenOnly: 'Y', fromDate: '', toDate: '' })

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.chain({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      brokenOnly: filters.brokenOnly || 'N',
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
 * 그 단계가 끝났나.
 *
 * 숫자만으로는 <b>아직 안 했다</b> 와 <b>하다가 없어졌다</b> 를 구분할 수
 * 없다. 집음 5 · 검수 0 은 아직 안 센 것일 수도, 카트에서 사라진 것일 수도
 * 있는데 숫자가 똑같다. 구분해 주는 것은 상태뿐이다.
 *
 * 그래서 끝난 단계만 따진다. 안 그러면 지시를 낼 때마다 한 건씩 사고로
 * 쌓여서, 정작 찾으려던 것이 그 사이에 묻힌다.
 */
const 피킹끝 = ['PICKED', 'PACKING', 'PACKED', 'SHIPPED']
const 패킹끝 = ['PACKED', 'SHIPPED']

const pickDone = (r) => 피킹끝.includes(r.outboundStatus)
const packDone = (r) => 패킹끝.includes(r.outboundStatus)
const shipDone = (r) => r.outboundStatus === 'SHIPPED'

/**
 * 어느 단계에서 꺾였나.
 *
 * 숫자만 나란히 놓으면 사람이 다섯 칸을 눈으로 비교해야 한다. 어디가
 * 문제인지 한 문장으로 말해 줘야 이 화면이 쓸모가 있다.
 */
function breakAt(r) {
  if (pickDone(r) && r.instructedQty !== r.pickedQty + r.shortageQty) {
    return { at: '피킹', msg: `지시 ${r.instructedQty} 인데 집음 ${r.pickedQty} + 결품 ${r.shortageQty} — ${r.unexplained} 개가 설명되지 않습니다` }
  }
  // 패킹 중에는 세고 담고 또 세고 담을 수 있어 검수 < 집음 이 정상이다.
  // 패킹이 끝났는데도 모자라면 그때 설명이 필요하다.
  if (packDone(r) && r.inspectedQty !== r.pickedQty) {
    return { at: '검수', msg: `집음 ${r.pickedQty} 인데 센 것은 ${r.inspectedQty} — 카트에서 ${r.pickedQty - r.inspectedQty} 개가 빕니다` }
  }
  if (packDone(r) && r.packedQty !== r.inspectedQty) {
    return { at: '패킹', msg: `센 것 ${r.inspectedQty} 인데 담음 ${r.packedQty} — 박스에 안 들어간 ${r.inspectedQty - r.packedQty} 개가 어디 있는지 확인하세요` }
  }
  if (shipDone(r) && r.shippedQty !== r.packedQty) {
    return { at: '출고', msg: `담음 ${r.packedQty} 인데 재고에서 빠진 것은 ${r.shippedQty} — 장부와 박스가 다릅니다` }
  }
  return null
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

/** '코드 — 설명' 에서 코드만 */
const reasonCodeOf = (v) => (v ? String(v).split(' — ')[0] : '')

const columns = [
  { key: 'skuId', label: 'SKU', width: '160px' },
  { key: 'outboundNo', label: '지시 / 주문', width: '175px' },
  { key: 'instructedQty', label: '지시', width: '58px', align: 'right' },
  { key: 'pickedQty', label: '집음', width: '58px', align: 'right' },
  { key: 'shortageQty', label: '결품', width: '58px', align: 'right' },
  { key: 'inspectedQty', label: '검수', width: '58px', align: 'right' },
  { key: 'packedQty', label: '담음', width: '58px', align: 'right' },
  { key: 'shippedQty', label: '출고', width: '58px', align: 'right' },
  { key: '_diag', label: '판정', width: '260px' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">수량 체인 검증</h1>
        <p class="page-desc">
          <strong>지시 → 집음 → 검수 → 담음 → 출고</strong> 를 한 줄에 세웁니다. 각 단계가
          앞 단계를 넘을 수 없으므로 이 줄은 늘 내림차순이고,
          <strong>어디서 꺾이는지가 곧 어디서 틀어졌나</strong>입니다.
          결품은 사유가 남아 있고, 사고는 아무 설명이 없습니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색어"
        placeholder="지시번호 / 주문번호 / SKU / 제품명"
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
      <!--
        기본은 틀어진 것만. 전체를 보는 길도 남긴다 — 정상 건이 어떻게
        생겼는지 봐야 틀어진 것을 알아본다.
      -->
      <FormField
        v-model="filters.brokenOnly"
        label="범위"
        type="select"
        empty-option="전체 보기"
        :options="[{ value: 'Y', label: '틀어진 것만' }]"
        @change="search()"
      />
      <FormField v-model="filters.fromDate" label="지시일 시작" type="date" @change="search()" />
      <FormField v-model="filters.toDate" label="끝" type="date" @change="search()" />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
      </div>
    </div>

    <div v-if="filters.brokenOnly === 'Y' && !loading && !rows.length" class="alert alert-ok mb-2">
      <span class="alert-icon">✅</span>
      <span>
        틀어진 품목이 없습니다. 지시한 만큼 집히고, 센 만큼 담기고, 담은 만큼 나갔습니다.
      </span>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="lineSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      empty-text="볼 것이 없습니다."
    >
      <template #cell-skuId="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim">{{ row.colorCode }} / {{ row.sizeCode }}</div>
      </template>

      <template #cell-outboundNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim">
          {{ row.orderNo }} · {{ row.receiverName }}
          <CodeBadge group="OUTBOUND_STATUS" :code="row.outboundStatus" />
        </div>
      </template>

      <template #cell-instructedQty="{ value }"><strong>{{ num(value) }}</strong></template>

      <template #cell-pickedQty="{ row, value }">
        <span :class="pickDone(row) && value < row.instructedQty - row.shortageQty ? 'danger' : ''">
          {{ num(value) }}
        </span>
      </template>

      <!-- 결품은 설명된 차이다. 빨갛게 칠하지 않는다 -->
      <template #cell-shortageQty="{ row, value }">
        <span :class="value ? 'warn' : 'dim'">{{ num(value) }}</span>
        <div v-if="value > 0 && row.shortageReason" class="small dim">
          {{ reasonCodeOf(row.shortageReason) }}
        </div>
      </template>

      <template #cell-inspectedQty="{ row, value }">
        <span :class="packDone(row) && value < row.pickedQty ? 'danger' : ''">{{ num(value) }}</span>
      </template>

      <template #cell-packedQty="{ row, value }">
        <span :class="packDone(row) && value < row.inspectedQty ? 'danger' : ''">{{ num(value) }}</span>
      </template>

      <template #cell-shippedQty="{ row, value }">
        <span :class="shipDone(row) && value !== row.packedQty ? 'danger' : ''">
          {{ num(value) }}
        </span>
      </template>

      <!--
        숫자만 나란히 놓으면 다섯 칸을 눈으로 비교해야 한다. 어디가 문제인지
        한 문장으로 말해 줘야 이 화면이 쓸모가 있다.
      -->
      <template #cell-_diag="{ row }">
        <template v-if="breakAt(row)">
          <span class="badge badge-red">{{ breakAt(row).at }}</span>
          <div class="small danger">{{ breakAt(row).msg }}</div>
        </template>
        <template v-else-if="row.shortageQty > 0">
          <span class="badge badge-amber">결품</span>
          <div class="small dim">사유가 남아 있습니다 — 설명된 차이입니다</div>
        </template>
        <span v-else class="small dim">이상 없음</span>
      </template>
    </DataTable>

    <div class="pager">
      <span class="small dim">총 {{ num(total) }}품목 · {{ page }} / {{ totalPages }} 페이지</span>
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
.danger {
  color: var(--c-red, #dc2626);
  font-weight: 600;
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
