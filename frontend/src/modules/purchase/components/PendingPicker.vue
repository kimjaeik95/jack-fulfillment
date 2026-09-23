<script setup>
/**
 * 발주 대기 고르기 (PUR-PG-003).
 *
 * <b>결재는 끝났는데 아직 공급처에 안 나간 줄</b>을 보여 주고, 골라서
 * 발주에 담는다.
 *
 * 요청 한 건을 통째로 담던 자리다. 그런데 요청은 SKU 마다 공급처가
 * 달라 한 건에 섞여 들어오고, 발주는 공급처 한 곳에 나가는 문서다.
 * 통째로 담으면 A 사 물건이 적힌 B 사 발주서가 만들어진다. 그래서
 * 요청이 아니라 <b>줄</b>을 고른다.
 *
 * 줄 단위라 얻는 것이 또 있다.
 *
 *   · 여러 요청에서 같은 공급처 줄만 모아 한 발주로 낼 수 있다
 *   · 이미 나간 만큼이 빠져서, 남은 수량만 보인다
 *   · 절반만 나간 줄도 남은 수량으로 다시 뜬다
 *
 * 공급처가 안 적힌 줄도 함께 보여 준다. 그 칸은 요청자가 아는 경우에만
 * 적는 참고값이라 대부분 비어 있어서, 빼 버리면 정작 발주해야 할 것이
 * 안 보인다. 다만 <b>이 발주로 담는다는 것을 알 수 있게</b> 표시한다 —
 * 어디로 보낼지는 발주 담당이 정하는 것이고, 그 판단이 곧 이 발주다.
 */
import { onMounted, ref } from 'vue'
import * as orderApi from '@/api/purchaseOrder.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'

const props = defineProps({
  /** 이 발주의 공급처. 이 공급처 줄 + 공급처 미정 줄만 보인다 */
  supplierId: { type: String, default: '' },
  supplierName: { type: String, default: '' },
  /** 이 발주의 센터. 발주 하나는 센터 한 곳으로만 간다 */
  plantId: { type: String, default: '' },
  /** 이미 담아 둔 요청 줄 순번 — 두 번 담지 않게 */
  pickedLineSeqs: { type: Array, default: () => [] },
})

const emit = defineEmits(['pick', 'close'])

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const checked = ref([])

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await orderApi.pending({
      keyword: keyword.value.trim() || null,
      supplierId: props.supplierId || null,
      plantId: props.plantId || null,
      size: 100,
    })
    rows.value = page.rows ?? []
    total.value = page.total ?? rows.value.length
  } catch (e) {
    loadError.value = e.message
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(load)

const already = (row) => props.pickedLineSeqs.includes(row.lineSeq)
const isChecked = (row) => checked.value.some((r) => r.lineSeq === row.lineSeq)

function pick(row) {
  if (already(row)) return
  checked.value = isChecked(row)
    ? checked.value.filter((r) => r.lineSeq !== row.lineSeq)
    : [...checked.value, row]
}

function confirm() {
  if (!checked.value.length) return
  emit('pick', checked.value)
  checked.value = []
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const d = (v) => (v ? String(v).slice(0, 10) : '-')

const columns = [
  { key: 'skuId', label: 'SKU', width: '160px' },
  { key: 'productName', label: '제품명', width: '180px' },
  { key: 'requestNo', label: '요청', width: '150px', cls: 'code' },
  { key: 'requiredDate', label: '필요일', width: '104px' },
  { key: 'remainQty', label: '남은수량', width: '92px', align: 'right' },
  { key: 'supplierName', label: '공급처', width: '110px' },
]
</script>

<template>
  <ModalDialog title="발주 대기 고르기" size="wide" @close="emit('close')">
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="keyword"
        class="grow"
        label="검색어"
        placeholder="요청번호 / SKU / 제품명"
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
      결재는 끝났는데 <strong>아직 공급처에 안 나간 줄</strong>입니다. 이미 발주한 만큼은
      빠져 있습니다.
      <template v-if="supplierName">
        <br />
        <strong>{{ supplierName }}</strong> 것과 <strong>공급처 미정</strong>인 줄만
        보입니다 — 다른 공급처 것은 그 공급처로 따로 발주하세요.
      </template>
      <template v-else>
        <br />
        위에서 공급처를 먼저 고르면 그 공급처 것만 보여 줍니다.
      </template>
    </p>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="lineSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      :muted-when="already"
      empty-text="발주할 것이 남아 있지 않습니다."
      @row-click="pick"
    >
      <template #cell-skuId="{ row, value }">
        <span class="mark">{{ isChecked(row) ? '☑' : '☐' }}</span>
        <span class="code">{{ value }}</span>
        <div class="small dim">{{ row.colorCode }} / {{ row.sizeCode }}</div>
        <div v-if="already(row)" class="small dim">이미 담음</div>
      </template>

      <template #cell-requestNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim">{{ row.plantName }} · {{ row.requestedByName ?? '-' }}</div>
      </template>

      <template #cell-requiredDate="{ row, value }">
        <span :class="{ over: row.overdue }">{{ d(value) }}</span>
        <div v-if="row.overdue" class="small over">지남</div>
      </template>

      <!-- 남은 수량이 담기는 값이다. 승인수량이 아니라 -->
      <template #cell-remainQty="{ row, value }">
        <strong>{{ num(value) }}</strong>
        <div v-if="row.orderedQty > 0" class="small dim">
          승인 {{ num(row.approvedQty) }} · 발주 {{ num(row.orderedQty) }}
        </div>
      </template>

      <template #cell-supplierName="{ value }">
        <span v-if="value">{{ value }}</span>
        <!--
          비어 있는 것이 정상이다. 그대로 담으면 이 발주의 공급처가 된다 —
          '-' 로만 두면 무엇이 빠진 것처럼 보인다.
        -->
        <span v-else class="small dim">미정 · 이 발주로</span>
      </template>
    </DataTable>

    <template #footer>
      <span class="left small dim">
        총 {{ num(total) }}줄. 줄을 눌러 고르고 한 번에 담으세요.
      </span>
      <button class="btn" @click="emit('close')">닫기</button>
      <button class="btn btn-primary" :disabled="!checked.length" @click="confirm()">
        {{ checked.length ? `${checked.length} 줄 담기` : '담기' }}
      </button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.note {
  margin: 0 0 8px;
  line-height: 1.6;
}
/* 고른 표시 — 체크박스를 따로 두면 줄 전체를 누르는 지금 동작과 어긋난다 */
.mark {
  margin-right: 6px;
  color: var(--text-3);
}
/* 필요일이 지난 줄 — 이미 늦었다 */
.over {
  color: var(--danger);
  font-weight: 600;
}
</style>
