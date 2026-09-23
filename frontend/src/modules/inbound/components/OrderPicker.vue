<script setup>
/**
 * 근거 발주 고르기 (PUR-PG-006).
 *
 * 입고예정은 발주를 근거로 만든다. 그런데 발주번호를 손으로 적게 하면
 * PO-20260916-0001 을 어딘가에서 옮겨 적어야 하고, 한 글자만 틀려도
 * '없는 발주' 로 막힌다.
 *
 * <b>아직 안 들어온 발주만 보여 준다</b> (openOnly). 이미 다 들어온 발주로
 * 예정을 만들 일은 없고, 목록에 있으면 반드시 담을 것이 있다는 뜻이라야
 * 고르는 사람이 헛걸음을 안 한다.
 *
 * 센터 · 공급처를 이미 골랐으면 그 조건으로 좁힌다. 입고예정 화면에서
 * 위쪽에 고른 값이 있는데 목록이 전 창고의 발주를 보여 주면, 고를 때마다
 * 그 값들을 눈으로 다시 대조해야 한다.
 */
import { onMounted, reactive, ref, watch } from 'vue'
import * as orderApi from '@/api/purchaseOrder.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const props = defineProps({
  title: { type: String, default: '근거 발주 고르기' },
  /** 입고예정이 이미 고른 값 — 있으면 그 조건으로 좁힌다 */
  plantId: { type: String, default: '' },
  supplierId: { type: String, default: '' },
})

const emit = defineEmits(['pick', 'close'])

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: props.plantId,
  supplierId: props.supplierId,
})

async function search() {
  loading.value = true
  loadError.value = ''
  try {
    // progress 가 '아직 안 들어온 발주, 급한 납기부터' 를 이미 뜻한다.
    // 조건을 여기서 다시 적으면 진행현황 화면과 목록이 갈라질 수 있다.
    const data = await orderApi.progress({
      keyword: filters.keyword || null,
      plantId: filters.plantId || null,
      supplierId: filters.supplierId || null,
      size: 50,
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

// 위쪽에서 센터 · 공급처를 바꾸면 목록도 따라간다.
watch(
  () => [props.plantId, props.supplierId],
  ([p, s]) => {
    filters.plantId = p
    filters.supplierId = s
    search()
  },
)

onMounted(search)

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const d = (v) => (v ? String(v).slice(0, 10) : '-')

/** 납기가 지났나 — 먼저 처리할 것을 눈에 띄게 한다 */
const overdue = (row) => row.dueDate && row.dueDate < new Date().toISOString().slice(0, 10)

const columns = [
  { key: 'orderNo', label: '발주번호', width: '150px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '130px' },
  { key: 'plantName', label: '센터', width: '110px' },
  { key: 'dueDate', label: '납기', width: '100px' },
  { key: 'lineCount', label: '품목', width: '58px', align: 'right' },
  { key: 'remainQty', label: '남은 수량', width: '86px', align: 'right' },
  { key: 'orderStatus', label: '상태', width: '90px' },
]
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
        placeholder="발주번호 / 공급처"
        @enter="search()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          검색
        </button>
      </div>
    </div>

    <p class="small dim note">
      아직 안 들어온 발주만 보입니다 (발주됨 · 부분입고).
      <template v-if="plantId || supplierId">
        위에서 고른 센터 · 공급처로 좁혀 놓았습니다.
      </template>
      납기가 이른 것부터입니다.
    </p>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="orderSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      empty-text="담을 것이 남은 발주가 없습니다."
      @row-click="(row) => emit('pick', row)"
    >
      <template #cell-dueDate="{ row, value }">
        <span :class="{ over: overdue(row) }">{{ d(value) }}</span>
        <span v-if="overdue(row)" class="small over"> 지남</span>
      </template>
      <template #cell-remainQty="{ value }">
        <strong>{{ num(value) }}</strong>
      </template>
      <template #cell-orderStatus="{ value }">
        <CodeBadge group="ORDER_STATUS" :code="value" />
      </template>
    </DataTable>

    <template #footer>
      <span class="left small dim">총 {{ num(total) }}건 중 앞의 50건입니다.</span>
      <button class="btn" @click="emit('close')">닫기</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.note {
  margin: 0 0 8px;
  line-height: 1.6;
}
/* 납기 지난 것 — 먼저 손봐야 할 것이다 */
.over {
  color: var(--danger);
  font-weight: 600;
}
</style>
