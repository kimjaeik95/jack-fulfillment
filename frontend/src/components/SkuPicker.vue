<script setup>
/**
 * SKU 고르기.
 *
 * 구매요청이 먼저 쓰고, 구매오더 · 입고 · 출고도 같은 일을 한다 — "어떤
 * 물건을" 로 시작하는 화면들이다. 그래서 모듈 안이 아니라 공용에 둔다.
 *
 * 재고 고르기(StockPicker)와 다른 점은 <b>재고가 없어도 고를 수 있다</b>는
 * 것이다. 사려는 물건은 대개 지금 없는 물건이라, 재고를 기준으로 고르게
 * 하면 정작 필요한 것을 못 고른다.
 *
 * 대신 지금 판매가능 수량을 함께 보여 준다. 얼마나 부족한지를 보고
 * 요청수량을 정하는 것이 실제 순서이기 때문이다.
 */
import { onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as skuApi from '@/api/sku.js'
import DataTable from './DataTable.vue'
import ModalDialog from './ModalDialog.vue'
import FormField from './FormField.vue'
import CodeBadge from './CodeBadge.vue'

const props = defineProps({
  title: { type: String, default: 'SKU 고르기' },
  /** 이미 담은 SKU 코드 — 목록에서 흐리게 표시한다 */
  pickedIds: { type: Array, default: () => [] },
})

const emit = defineEmits(['pick', 'close'])

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  colorCode: '',
  sizeCode: '',
  // 폐기한 SKU 는 살 이유가 없다. 기본으로 뺀다.
  status: 'ACTIVE',
})

async function search() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await skuApi.list({ ...filters, useYn: 'Y', page: 1, size: 50 })
    rows.value = data.rows
    total.value = data.total
  } catch (e) {
    rows.value = []
    total.value = 0
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(search)

const columns = [
  { key: 'skuId', label: 'SKU', width: '175px', cls: 'code' },
  { key: 'productName', label: '제품', width: '180px' },
  { key: 'colorCode', label: '색상', width: '84px', align: 'center' },
  { key: 'sizeCode', label: '사이즈', width: '76px', align: 'center' },
  { key: 'status', label: '상태', width: '86px', align: 'center' },
]

const already = (row) => props.pickedIds.includes(row.skuId)

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
        placeholder="SKU 코드 / 제품명"
        @enter="search()"
      />
      <FormField
        v-model="filters.colorCode"
        label="색상"
        type="select"
        empty-option="전체"
        :options="codeOptions('COLOR')"
        @change="search()"
      />
      <FormField
        v-model="filters.sizeCode"
        label="사이즈"
        type="select"
        empty-option="전체"
        :options="codeOptions('SIZE')"
        @change="search()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          검색
        </button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="skuId"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      :muted-when="already"
      empty-text="조건에 맞는 SKU 가 없습니다."
      @row-click="pick"
    >
      <template #cell-skuId="{ row, value }">
        <span class="code">{{ value }}</span>
        <span v-if="already(row)" class="small dim"> · 담음</span>
      </template>
      <template #cell-colorCode="{ value }">
        <CodeBadge group="COLOR" :code="value" />
      </template>
      <template #cell-sizeCode="{ value }">
        <span class="badge">{{ value }}</span>
      </template>
      <template #cell-status="{ value }">
        <CodeBadge group="SKU_STATUS" :code="value" />
      </template>
    </DataTable>

    <template #footer>
      <span class="left small dim">
        총 {{ total }}건 중 앞의 50건입니다. 검색어로 좁히세요.
        재고가 없어도 고를 수 있습니다 — 사려는 물건은 대개 지금 없는 물건입니다.
      </span>
      <button class="btn" @click="emit('close')">닫기</button>
    </template>
  </ModalDialog>
</template>
