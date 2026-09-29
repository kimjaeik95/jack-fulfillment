<script setup>
/**
 * 운송중 재고 (DLV-PG-004).
 *
 * <b>창고에도 없고 고객에게도 없는 수량</b>이다. 출고확정으로 보유에서 빠졌고
 * (PAC-PG-005) 아직 배송완료가 안 찍힌 것.
 *
 * 재고 화면 어디에도 안 나오는 수량이라 이 화면이 따로 있다. "장부에 30개인데
 * 왜 40개를 팔았지" 같은 물음이 생겼을 때, 그 차이가 여기 떠 있다.
 *
 * 떠 있는 것 자체는 정상이다 — 어제 나간 물건은 당연히 길 위에 있다.
 * 문제는 <b>너무 오래 떠 있는 것</b>이라, 경과일과 멈춘 건수를 나란히 둔다.
 * 오래 떠 있으면 셋 중 하나다: 택배사가 늦거나, 실패했는데 아무도 안 적었거나,
 * 배송완료를 안 찍었거나. 마지막이 가장 흔하다.
 *
 * 수량은 <b>박스에 담은 것</b>에서 센다. 지시 수량이 아니다 — 결품이 있었으면
 * 지시보다 적게 나갔고, 실제로 길 위에 있는 것은 박스에 들어간 만큼이다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as deliveryApi from '@/api/delivery.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'

const session = useSessionStore()
const hierarchy = useHierarchyStore()

const readDenyReason = computed(() => session.denyReason('DLV_TRANSIT', 'R'))

const rows = ref([])
const summary = ref(null)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = deliveryApi.PAGE_SIZE
const couriers = ref([])

const filters = reactive({
  keyword: '',
  plantId: '',
  courierCode: '',
  delayedOnly: '',
  delayDays: 3,
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await deliveryApi.transit({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      courierCode: filters.courierCode || null,
      delayedOnly: filters.delayedOnly || null,
      delayDays: Number(filters.delayDays) || 3,
      page: page.value,
      size,
    })
    rows.value = data.page.rows
    total.value = data.page.total
    summary.value = data.summary
  } catch (e) {
    loadError.value = e.message
    rows.value = []
    total.value = 0
    summary.value = null
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
  try {
    couriers.value = (await deliveryApi.couriers({ useYn: 'Y' })).rows
  } catch {
    couriers.value = []
  }
  await fetchPage()
})

const courierOptions = computed(() =>
  couriers.value.map((c) => ({ value: c.courierCode, label: c.courierName })),
)

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const limit = computed(() => Number(filters.delayDays) || 3)

const columns = [
  { key: 'skuId', label: 'SKU', width: '170px' },
  { key: 'productName', label: '제품', width: '200px' },
  { key: 'transitQty', label: '운송중', width: '80px', align: 'right' },
  { key: 'waybillCount', label: '송장', width: '68px', align: 'right' },
  { key: 'oldestDays', label: '가장 오래', width: '86px', align: 'right' },
  { key: 'delayedCount', label: '지연', width: '68px', align: 'right' },
  { key: 'stuckCount', label: '멈춤', width: '68px', align: 'right' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">운송중 재고</h1>
        <p class="page-desc">
          <strong>창고에도 없고 고객에게도 없는 수량</strong>입니다. 출고확정으로 보유에서
          빠졌고 아직 배송완료가 안 찍힌 것이라, 재고 화면 어디에도 나오지 않습니다.
          떠 있는 것 자체는 정상입니다 — 문제는 너무 오래 떠 있는 것입니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!--
      합계는 전체를 한 번에 센 값이다. 페이지마다 다른 합계를 보여 주면
      화면 머리의 숫자를 아무도 못 믿는다.
    -->
    <div v-if="summary" class="sum-row mb-2">
      <div class="sum">
        <div class="sum-label">길 위의 수량</div>
        <div class="sum-value">{{ num(summary.transitQty) }}<span class="unit">개</span></div>
      </div>
      <div class="sum">
        <div class="sum-label">송장</div>
        <div class="sum-value">{{ num(summary.waybillCount) }}<span class="unit">건</span></div>
      </div>
      <div class="sum">
        <div class="sum-label">가장 오래</div>
        <div class="sum-value" :class="summary.oldestDays >= limit ? 'danger' : ''">
          {{ num(summary.oldestDays) }}<span class="unit">일</span>
        </div>
      </div>
      <div class="sum">
        <div class="sum-label">{{ limit }}일 초과</div>
        <div class="sum-value" :class="summary.delayedCount ? 'danger' : ''">
          {{ num(summary.delayedCount) }}<span class="unit">건</span>
        </div>
      </div>
      <div class="sum">
        <div class="sum-label">실패 · 반송으로 멈춤</div>
        <div class="sum-value" :class="summary.stuckCount ? 'danger' : ''">
          {{ num(summary.stuckCount) }}<span class="unit">건</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="SKU / 상품코드 / 제품명"
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
          v-model="filters.courierCode"
          label="택배사"
          type="select"
          empty-option="전체"
          :options="courierOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.delayDays"
          label="지연 기준"
          type="number"
          help="며칠 넘으면 늦은 것으로 볼지"
          @change="search()"
        />
        <FormField
          v-model="filters.delayedOnly"
          label="범위"
          type="select"
          empty-option="전체"
          :options="[{ value: 'Y', label: '오래 뜬 것만' }]"
          @change="search()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <div v-if="!loading && !rows.length" class="alert alert-ok m-2">
        <span class="alert-icon">✅</span>
        <span>길 위에 떠 있는 물건이 없습니다. 내보낸 것이 다 도착했습니다.</span>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="skuId"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        empty-text="조건에 맞는 것이 없습니다."
      >
        <template #cell-skuId="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.colorCode }} / {{ row.sizeCode }}</div>
        </template>

        <template #cell-productName="{ row, value }">
          {{ value }}
          <div class="small dim">{{ row.productId }}</div>
        </template>

        <template #cell-transitQty="{ value }">
          <strong>{{ num(value) }}</strong>
        </template>

        <template #cell-waybillCount="{ value }">{{ num(value) }}</template>

        <template #cell-oldestDays="{ value }">
          <span :class="value >= limit ? 'danger' : ''">{{ num(value) }}일</span>
        </template>

        <!-- 늦은 것 — 기준일을 넘겼는데 아직 안 갔다 -->
        <template #cell-delayedCount="{ value }">
          <span :class="value ? 'danger' : 'dim'">{{ num(value) }}</span>
        </template>

        <!--
          멈춘 것 — 실패나 반송으로 적혀 있다. 늦은 것과 다르다: 이쪽은
          이유를 아는 것이고, 배송실패 화면에서 다시 보낼 수 있다.
        -->
        <template #cell-stuckCount="{ value }">
          <span :class="value ? 'warn' : 'dim'">{{ num(value) }}</span>
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

    <p class="small dim mt-2">
      오래 떠 있으면 셋 중 하나입니다 — 택배사가 늦거나, 실패했는데 아무도 안 적었거나,
      배송완료를 안 찍었거나. 마지막이 가장 흔합니다. 송장별로 보려면
      <strong>송장 · 배송 현황</strong>에서 '길 위에 있는 것만' 으로 거르세요.
    </p>
  </div>
</template>

<style scoped>
.sum-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.sum {
  flex: 1 1 140px;
  padding: 10px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--c-surface, #fff);
}
.sum-label {
  font-size: 12px;
  color: var(--c-dim, #6b7280);
}
.sum-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}
.unit {
  font-size: 13px;
  font-weight: 400;
  margin-left: 2px;
  color: var(--c-dim, #6b7280);
}
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
