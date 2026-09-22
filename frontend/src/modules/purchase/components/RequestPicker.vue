<script setup>
/**
 * 근거 구매요청 고르기 (PUR-PG-004).
 *
 * 발주는 승인된 요청을 보고 만든다. 그런데 요청번호를 손으로 적게 하면
 * REQ-20260915-0001 을 다른 화면에서 찾아 옮겨 적어야 하고, 한 글자만
 * 틀려도 '찾을 수 없습니다' 로 막힌다.
 *
 * <b>발주할 수 있는 것만 보여 준다</b> — 승인 · 부분승인. 결재 전인 요청은
 * 발주 근거가 될 수 없고, 반려 · 취소는 사지 말라고 한 것이다. 목록에
 * 있으면 반드시 담을 것이 있다는 뜻이라야 고르는 사람이 헛걸음을 안 한다.
 *
 * 승인수량을 함께 보여 준다. 요청 100 · 승인 60 이면 발주에 담기는 것은
 * 60 이고, 그 차이를 고르기 전에 알아야 "왜 40 이 빠졌지" 를 안 묻는다.
 */
import { computed, onMounted, ref } from 'vue'
import * as purchaseApi from '@/api/purchase.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const props = defineProps({
  /** 발주가 이미 센터를 골랐으면 그 센터 것만 */
  plantId: { type: String, default: '' },
})

const emit = defineEmits(['pick', 'close'])

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')

/**
 * 승인 · 부분승인을 따로 불러 합친다.
 *
 * 서버에 '발주 가능한 것만' 이라는 조건이 없어서다. 상태를 두 번 물어
 * 합치는 편이, 전부 받아 화면에서 거르는 것보다 낫다 — 받아서 거르면
 * 페이지를 넘길 때 건수가 안 맞는다.
 */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const common = {
      keyword: keyword.value.trim() || null,
      plantId: props.plantId || null,
      size: 50,
      sortBy: 'requiredDate',
      sortDir: 'asc',
    }
    const [approved, partial] = await Promise.all([
      purchaseApi.list({ ...common, requestStatus: 'APPROVED' }),
      purchaseApi.list({ ...common, requestStatus: 'PARTIAL' }),
    ])
    rows.value = [...(approved.rows ?? []), ...(partial.rows ?? [])]
      // 필요일이 급한 것부터. 센터가 언제까지 필요하다고 적은 날이다.
      .sort((a, b) => String(a.requiredDate ?? '9999').localeCompare(String(b.requiredDate ?? '9999')))
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const d = (v) => (v ? String(v).slice(0, 10) : '-')

/** 필요일이 지났나 — 이미 늦은 요청이다 */
const overdue = (row) => row.requiredDate && row.requiredDate < new Date().toISOString().slice(0, 10)

/** 결재가 깎았나 — 요청보다 승인이 적으면 그 차이를 알려야 한다 */
const cut = (row) => (row.totalRequestQty ?? 0) - (row.totalApprovedQty ?? 0)

const columns = [
  { key: 'requestNo', label: '요청번호', width: '150px', cls: 'code' },
  { key: 'plantName', label: '센터', width: '110px' },
  { key: 'requiredDate', label: '필요일', width: '100px' },
  { key: 'requestedByName', label: '요청자', width: '90px' },
  { key: 'lineCount', label: '줄', width: '50px', align: 'right' },
  { key: 'totalApprovedQty', label: '승인수량', width: '92px', align: 'right' },
  { key: 'requestStatus', label: '결재', width: '90px' },
]
</script>

<template>
  <ModalDialog title="근거 구매요청 고르기" size="wide" @close="emit('close')">
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="keyword"
        class="grow"
        label="검색어"
        placeholder="요청번호 / 요청자"
        @enter="load()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="load()">
          <span v-if="loading" class="spinner"></span>
          검색
        </button>
      </div>
    </div>

    <p class="small dim note">
      결재가 끝난 요청만 보입니다 (승인 · 부분승인).
      <template v-if="plantId"> 위에서 고른 센터로 좁혀 놓았습니다.</template>
      필요일이 급한 것부터입니다. 발주에는 <strong>승인수량</strong>대로 담깁니다.
    </p>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="requestSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      empty-text="발주할 수 있는 승인 요청이 없습니다."
      @row-click="(row) => emit('pick', row)"
    >
      <template #cell-requiredDate="{ row, value }">
        <span :class="{ over: overdue(row) }">{{ d(value) }}</span>
        <span v-if="overdue(row)" class="small over"> 지남</span>
      </template>

      <template #cell-totalApprovedQty="{ row, value }">
        <strong>{{ num(value) }}</strong>
        <!-- 결재가 깎은 만큼. 고르기 전에 알아야 '왜 이만큼만 담겼지' 를 안 묻는다 -->
        <div v-if="cut(row) > 0" class="small dim">요청 {{ num(row.totalRequestQty) }}</div>
      </template>

      <template #cell-requestStatus="{ value }">
        <CodeBadge group="REQUEST_STATUS" :code="value" />
      </template>
    </DataTable>

    <template #footer>
      <span class="left small dim">줄을 누르면 그 요청으로 담습니다.</span>
      <button class="btn" @click="emit('close')">닫기</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.note {
  margin: 0 0 8px;
  line-height: 1.6;
}
/* 필요일이 지난 요청 — 이미 늦었다 */
.over {
  color: var(--danger);
  font-weight: 600;
}
</style>
