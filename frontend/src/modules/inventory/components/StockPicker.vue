<script setup>
/**
 * 재고 한 줄 고르기.
 *
 * 판매불가 전환 · 로케이션 이동 · 조정 요청이 모두 '어느 재고를' 로
 * 시작한다. 셋이 각자 검색창을 만들면 같은 화면이 셋이 되고, 한 곳만
 * 고치면 나머지는 옛날 동작을 한다.
 *
 * 고를 때 네 수량을 함께 보여 준다. 재고를 고르는 사람이 판단해야 하는
 * 것이 '이 자리에 얼마나 있나' 가 아니라 '이 중 얼마를 건드릴 수 있나'
 * 이기 때문이다 — 보유 100 이라도 90 이 할당이면 옮길 수 있는 것은 10 이다.
 *
 * 고르면 닫힌다. 여러 줄을 담는 것은 조정 요청뿐이고, 그건 고른 뒤
 * 그 화면이 목록에 쌓는다.
 */
import { onMounted, reactive, ref } from 'vue'
import * as stockApi from '@/api/stock.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'

const props = defineProps({
  title: { type: String, default: '재고 고르기' },
  /** 이 창고의 재고만 — 조정 전표는 한 창고만 담는다 */
  plantId: { type: String, default: '' },
  warehouseId: { type: String, default: '' },
  /** 창고를 바꿀 수 있는지. 조정 요청은 전표의 창고가 정해져 있어 잠근다. */
  lockWarehouse: { type: Boolean, default: false },
  /** 이미 담은 재고 순번 — 목록에서 흐리게 표시한다 */
  pickedSeqs: { type: Array, default: () => [] },
})

const emit = defineEmits(['pick', 'close'])

const hierarchy = useHierarchyStore()

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: props.plantId,
  warehouseId: props.warehouseId,
  locationId: '',
  // 기본은 재고가 있는 것만. 0 짜리를 옮기거나 불량으로 돌릴 일은 없다.
  onHandOnly: 'Y',
})

async function search() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await stockApi.list({ ...filters, page: 1, size: 50 })
    rows.value = data.page.rows
    total.value = data.page.total
  } catch (e) {
    rows.value = []
    total.value = 0
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([hierarchy.loadPlants(false), hierarchy.loadWarehouses(false)])
  await search()
})

const columns = [
  { key: 'locationFullCode', label: '재고주소', width: '175px', cls: 'code' },
  { key: 'skuId', label: 'SKU', width: '160px', cls: 'code' },
  { key: 'productName', label: '제품', width: '150px' },
  { key: 'qtyOnHand', label: '보유', width: '68px', align: 'right' },
  { key: 'qtyAllocated', label: '할당', width: '68px', align: 'right' },
  { key: 'qtyUnsellable', label: '판매불가', width: '78px', align: 'right' },
  { key: 'qtyAvailable', label: '판매가능', width: '80px', align: 'right' },
]

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const already = (row) => props.pickedSeqs.includes(row.stockSeq)

function pick(row) {
  if (already(row)) return
  emit('pick', row)
}
</script>

<template>
  <ModalDialog :title="title" size="wide" @close="emit('close')">
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색어"
        placeholder="SKU / 제품명 / 빈코드"
        @enter="search()"
      />
      <FormField
        v-model="filters.plantId"
        label="플랜트"
        type="select"
        empty-option="전체"
        :options="hierarchy.plantOptions"
        :disabled="lockWarehouse"
        @change="search()"
      />
      <FormField
        v-model="filters.warehouseId"
        label="창고"
        type="select"
        empty-option="전체"
        :options="filters.plantId ? hierarchy.warehouseOptionsOf(filters.plantId) : []"
        :disabled="lockWarehouse || !filters.plantId"
        @change="search()"
      />
      <FormField v-model="filters.locationId" label="빈코드" mono @enter="search()" />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          검색
        </button>
      </div>
    </div>

    <p v-if="lockWarehouse" class="small dim" style="margin: 0 0 8px">
      이 전표의 창고 재고만 담을 수 있습니다. 승인 권한이 창고 단위로 나뉘기 때문입니다.
    </p>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="stockSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      :muted-when="already"
      empty-text="조건에 맞는 재고가 없습니다."
      @row-click="pick"
    >
      <template #cell-locationFullCode="{ row, value }">
        <span class="code">{{ value }}</span>
        <span v-if="already(row)" class="small dim"> · 담음</span>
      </template>
      <template #cell-skuId="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim">{{ row.colorCode }} / {{ row.sizeCode }}</div>
      </template>
      <template #cell-qtyOnHand="{ value }">{{ num(value) }}</template>
      <template #cell-qtyAllocated="{ value }">
        <span :class="{ dim: !value }">{{ num(value) }}</span>
      </template>
      <template #cell-qtyUnsellable="{ value }">
        <span :class="value ? 'warn' : 'dim'">{{ num(value) }}</span>
      </template>
      <template #cell-qtyAvailable="{ value }">
        <strong :class="value > 0 ? 'ok' : 'danger'">{{ num(value) }}</strong>
      </template>
    </DataTable>

    <template #footer>
      <span class="left small dim">
        총 {{ num(total) }}건 중 앞의 50건입니다. 검색어로 좁히세요.
      </span>
      <button class="btn" @click="emit('close')">닫기</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
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
