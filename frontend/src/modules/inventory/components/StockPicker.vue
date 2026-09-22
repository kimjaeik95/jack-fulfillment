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
  /**
   * 여러 건을 골라 한 번에 넘길지.
   *
   * 기본은 한 건이다. 조정 · 이동 · 판매불가는 한 줄씩 사유와 수량을 적어야
   * 하므로 여러 건을 받아도 쓸 데가 없다 — 실사 대상 담기만 다르다.
   * 거기서는 수백 건을 한 줄씩 누르는 것이 실제 작업이 안 된다.
   */
  multi: { type: Boolean, default: false },
})

const emit = defineEmits(['pick', 'close'])

/**
 * multi 일 때 고른 것들.
 *
 * 창을 닫지 않고 쌓는다. 한 건 담을 때마다 창이 닫히면 검색 조건을 매번
 * 다시 넣어야 하고, 그게 지금 실사 대상을 담을 때 벌어지는 일이다.
 */
const checked = ref([])

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

const isChecked = (row) => checked.value.some((r) => r.stockSeq === row.stockSeq)

function pick(row) {
  if (already(row)) return
  if (!props.multi) {
    emit('pick', row)
    return
  }
  // 누를 때마다 뒤집는다. 잘못 누른 것을 빼려고 창을 닫을 일은 없어야 한다.
  checked.value = isChecked(row)
    ? checked.value.filter((r) => r.stockSeq !== row.stockSeq)
    : [...checked.value, row]
}

function confirmMulti() {
  if (!checked.value.length) return
  emit('pick', checked.value)
  checked.value = []
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
        <span v-if="multi" class="mark">{{ isChecked(row) ? '☑' : '☐' }}</span>
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
        <template v-if="multi"> 줄을 눌러 고르고 한 번에 담으세요.</template>
      </span>
      <button class="btn" @click="emit('close')">닫기</button>
      <button
        v-if="multi"
        class="btn btn-primary"
        :disabled="!checked.length"
        @click="confirmMulti()"
      >
        {{ checked.length ? `${checked.length} 건 담기` : '담기' }}
      </button>
    </template>
  </ModalDialog>
</template>

<style scoped>
/* 고른 표시 — 체크박스를 따로 두면 줄 전체를 누르는 지금 동작과 어긋난다 */
.mark {
  margin-right: 6px;
  color: var(--text-3);
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
