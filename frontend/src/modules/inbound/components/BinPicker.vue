<script setup>
/**
 * 놓을 빈 고르기 (INB-PG-006).
 *
 * 적치는 빈 라벨을 찍는 것이 정상 흐름이지만, 라벨이 지워졌거나 손으로
 * 넣어야 할 때 사람이 빈코드를 외우고 있을 수는 없다. 창고 하나에 빈이
 * 수십~수백 개다.
 *
 * <b>같은 SKU 가 이미 있는 빈을 맨 위에 둔다.</b> 같은 물건을 흩어 놓으면
 * 피킹이 여러 자리를 돌아야 하고, 실사도 같은 SKU 를 여러 줄로 세게 된다.
 * 이미 자리가 잡혀 있으면 거기에 합치는 것이 맞다.
 *
 * 이 입고의 창고 빈만 보여 준다. 다른 창고에 놓으면 재고는 늘지만 아무도
 * 그 자리를 보러 가지 않는다 — 서버도 같은 이유로 거절한다.
 */
import { computed, onMounted, ref } from 'vue'
import * as locationApi from '@/api/location.js'
import * as stockApi from '@/api/stock.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'

const props = defineProps({
  /** 이 입고의 센터 · 창고 — 여기 빈만 고를 수 있다 */
  plantId: { type: String, required: true },
  warehouseId: { type: String, required: true },
  /** 지금 놓으려는 SKU — 이미 있는 자리를 먼저 보여 준다 */
  skuId: { type: String, default: '' },
})

const emit = defineEmits(['pick', 'close'])

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    // 빈 목록과 '이 SKU 가 어디 있나' 를 함께 읽는다. 둘을 합쳐야 어느
    // 자리에 합치면 되는지가 한눈에 보인다.
    const [locs, stocks] = await Promise.all([
      locationApi.list({
        plantId: props.plantId,
        warehouseId: props.warehouseId,
        useYn: 'Y',
        size: 0,
      }),
      props.skuId
        ? stockApi.list({
            plantId: props.plantId,
            warehouseId: props.warehouseId,
            skuId: props.skuId,
            size: 0,
          })
        : Promise.resolve({ rows: [] }),
    ])

    const here = {}
    for (const s of stocks.page?.rows ?? stocks.rows ?? []) {
      here[s.locationId] = (here[s.locationId] ?? 0) + (s.qtyOnHand ?? 0)
    }

    rows.value = (locs.rows ?? locs).map((l) => ({
      ...l,
      sameSkuQty: here[l.locationId] ?? 0,
    }))
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

/**
 * 같은 SKU 가 있는 빈이 먼저, 그다음 빈코드 순.
 *
 * 이동중(TRANSIT) 빈은 맨 뒤로 보낸다. 거기 놓으면 재고는 잡히지만 그
 * 자리는 옮겨지는 중인 물건을 담는 곳이라 오래 두면 안 된다.
 */
const shown = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return rows.value
    .filter((l) => !k || l.locationId.toLowerCase().includes(k)
      || (l.zoneCode ?? '').toLowerCase().includes(k))
    .sort((a, b) => {
      if (a.sameSkuQty !== b.sameSkuQty) return b.sameSkuQty - a.sameSkuQty
      const at = a.locationType === 'TRANSIT' ? 1 : 0
      const bt = b.locationType === 'TRANSIT' ? 1 : 0
      if (at !== bt) return at - bt
      return a.locationId.localeCompare(b.locationId)
    })
})

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const columns = [
  { key: 'locationId', label: '빈', width: '150px', cls: 'code' },
  { key: 'zoneCode', label: '구역', width: '90px' },
  { key: 'locationType', label: '유형', width: '90px' },
  { key: 'sameSkuQty', label: '이 SKU', width: '96px', align: 'right' },
]
</script>

<template>
  <ModalDialog title="놓을 빈 고르기" @close="emit('close')">
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="toolbar">
      <FormField v-model="keyword" class="grow" label="빈 · 구역 검색" mono />
    </div>

    <p class="small dim note">
      <strong>{{ warehouseId }}</strong> 의 빈만 보입니다.
      <template v-if="skuId">
        같은 물건(<span class="code">{{ skuId }}</span>)이 이미 있는 자리를 위에 둡니다 —
        흩어 놓으면 피킹이 여러 자리를 돕니다.
      </template>
    </p>

    <DataTable
      :columns="columns"
      :rows="shown"
      row-key="locationSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      empty-text="이 창고에 쓸 수 있는 빈이 없습니다."
      @row-click="(row) => emit('pick', row)"
    >
      <template #cell-zoneCode="{ value }">
        <span :class="{ dim: !value }">{{ value || '-' }}</span>
      </template>

      <!-- 이동중 빈은 임시 자리다. 놓을 수는 있지만 눈에 띄게 둔다. -->
      <template #cell-locationType="{ value }">
        <span :class="value === 'TRANSIT' ? 'warn' : 'dim'" class="small">
          {{ value === 'NORMAL' ? '일반' : value === 'TRANSIT' ? '이동중' : value }}
        </span>
      </template>

      <template #cell-sameSkuQty="{ value }">
        <strong v-if="value" class="ok">{{ num(value) }}</strong>
        <span v-else class="dim">-</span>
      </template>
    </DataTable>

    <template #footer>
      <span class="left small dim">줄을 누르면 그 빈으로 채웁니다.</span>
      <button class="btn" @click="emit('close')">닫기</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.note {
  margin: 0 0 8px;
  line-height: 1.6;
}
/* 이미 같은 물건이 있는 자리 — 여기 합치는 것이 기본이다 */
.ok {
  color: var(--success);
}
.warn {
  color: var(--warn);
}
</style>
