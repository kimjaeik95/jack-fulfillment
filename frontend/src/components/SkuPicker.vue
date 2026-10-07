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
import { computed, onMounted, reactive, ref } from 'vue'
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
  /**
   * 여러 개를 골라 한 번에 담을지.
   *
   * 한 건씩 담는 화면(미매핑 줄에 SKU 지정 등)은 고른 순간 정해지는 것이라
   * 그대로 두고, 여러 줄을 쌓는 화면만 켠다. 구매요청은 색상 · 사이즈별로
   * 여남은 건을 담는 일이 잦아 한 건마다 창이 닫히면 같은 검색을 반복하게
   * 된다.
   */
  multi: { type: Boolean, default: false },
})

const emit = defineEmits(['pick', 'pick-many', 'close'])

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

const columns = computed(() => [
  ...(props.multi ? [{ key: '_sel', label: '', width: '38px', align: 'center' }] : []),
  { key: 'skuId', label: 'SKU', width: '175px', cls: 'code' },
  { key: 'productName', label: '스타일', width: '180px' },
  { key: 'colorCode', label: '색상', width: '84px', align: 'center' },
  { key: 'sizeCode', label: '사이즈', width: '76px', align: 'center' },
  { key: 'status', label: '상태', width: '86px', align: 'center' },
])

const already = (row) => props.pickedIds.includes(row.skuId)

/* ── 여러 개 고르기 ─────────────────────────────────────────── */

/**
 * 고른 것. 검색을 다시 해도 유지된다.
 *
 * 색상으로 걸러 담고 사이즈로 다시 걸러 담는 식으로 쓰는데, 검색할 때마다
 * 비우면 그렇게 쓸 수가 없다. 그래서 행이 아니라 <b>SKU 그 자체</b>를 담는다.
 */
const chosen = ref([])
const isChosen = (row) => chosen.value.some((s) => s.skuId === row.skuId)

function toggle(row) {
  if (already(row)) return
  chosen.value = isChosen(row)
    ? chosen.value.filter((s) => s.skuId !== row.skuId)
    : [...chosen.value, row]
}

/** 지금 목록에서 아직 안 담긴 것을 한 번에 */
function chooseAllOnPage() {
  const add = rows.value.filter((r) => !already(r) && !isChosen(r))
  chosen.value = [...chosen.value, ...add]
}

function pick(row) {
  if (props.multi) {
    toggle(row)
    return
  }
  if (already(row)) return
  emit('pick', row)
}

function submitMany() {
  if (!chosen.value.length) return
  emit('pick-many', chosen.value)
  chosen.value = []
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
        placeholder="SKU 코드 / 스타일명"
        @enter="search()"
      />
      <FormField
        v-model="filters.colorCode"
        label="색상"
        placeholder="색상코드"
        @change="search()"
      />
      <FormField
        v-model="filters.sizeCode"
        label="사이즈"
        placeholder="사이즈코드"
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
      <!--
        칸이 아니라 줄을 누르게 한다. 체크박스에 pointer-events 를 끄면
        클릭이 줄로 흘러가, 어디를 눌러도 한 번만 토글된다 — 둘 다 살려
        두면 체크박스를 눌렀을 때 두 번 뒤집힌다.
      -->
      <template #cell-_sel="{ row }">
        <input type="checkbox" :checked="isChosen(row)" :disabled="already(row)" />
      </template>

      <template #cell-skuId="{ row, value }">
        <span class="code">{{ value }}</span>
        <span v-if="already(row)" class="small dim"> · 담음</span>
      </template>
      <template #cell-colorCode="{ row, value }">
        <span class="badge">{{ row.colorName || value }} ({{ value }})</span>
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
        <template v-if="multi">
          <br />
          줄을 눌러 고르고 <strong>담기</strong>를 누르세요. 검색을 다시 해도 고른 것은 남습니다.
        </template>
      </span>

      <template v-if="multi">
        <button class="btn btn-sm" :disabled="loading || !rows.length" @click="chooseAllOnPage()">
          이 목록 전체
        </button>
        <button class="btn btn-sm" :disabled="!chosen.length" @click="chosen = []">
          고른 것 비우기
        </button>
      </template>

      <button class="btn" @click="emit('close')">닫기</button>

      <button
        v-if="multi"
        class="btn btn-primary"
        :disabled="!chosen.length"
        @click="submitMany()"
      >
        {{ chosen.length ? `${chosen.length}건 담기` : '담기' }}
      </button>
    </template>
  </ModalDialog>
</template>

<style scoped>
/* 줄을 누르면 토글되므로 체크박스 자체는 표시만 한다 */
input[type='checkbox'] {
  pointer-events: none;
}
</style>
