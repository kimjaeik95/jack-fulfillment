<script setup>
/**
 * 출고검수 (OUT-PG-006).
 *
 * 집은 것을 다시 센다. 왜 또 세냐면, 피킹은 <b>빈 앞에서</b> 찍고 검수는
 * <b>카트를 앞에 두고</b> 찍기 때문이다 — 집는 중에 옆 칸 물건이 섞이거나
 * 카트가 바뀌는 일이 실제로 있고, 그걸 잡아내는 것이 이 단계의 유일한
 * 목적이다.
 *
 * 그래서 빈을 안 찍는다. 어디서 가져왔는지가 아니라 카트에 무엇이 들었는지를
 * 세는 일이라, SKU 만 찍으면 된다.
 *
 * 여기서도 재고 수량은 안 바뀐다. 보유수량이 줄어드는 것은 출고확정이다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import * as outboundApi from '@/api/outbound.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const session = useSessionStore()
const hierarchy = useHierarchyStore()
const toast = useToastStore()

const canInspect = computed(() => session.can('OUT_PICK', 'C'))

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', plantId: '' })

/**
 * 검수할 지시만.
 *
 * 피킹이 끝났어야 센다. 아직 집는 중인 지시를 세라고 하면 카트가 덜 찬
 * 상태로 세게 되고, 그건 검수가 아니라 중간 점검이다.
 */
async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.list({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      openOnly: 'Y',
      size: 100,
      sortBy: 'instructedAt',
      sortDir: 'asc',
    })
    rows.value = (data.rows ?? []).filter(
      (o) => o.totalPickedQty > 0 && o.totalPickedQty > (o.totalInspectedQty ?? 0),
    )
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await fetchPage()
})

/* ── 세기 ───────────────────────────────────────────────────── */

const target = ref(null)
const tasks = ref([])
const scan = reactive({ skuScan: '', qty: 1 })
const skuInput = ref(null)
const qtyInput = ref(null)
const busy = ref(false)
const scanError = ref('')

async function openInspect(row) {
  target.value = row
  await loadTasks()
  Object.assign(scan, { skuScan: '', qty: 1 })
  scanError.value = ''
  await nextTick()
  skuInput.value?.focus?.()
}

async function loadTasks() {
  tasks.value = await outboundApi.inspectTasks(target.value.outboundSeq)
}

const openTasks = computed(() => tasks.value.filter((t) => t.toInspectQty > 0))
const doneQty = computed(() => tasks.value.reduce((s, t) => s + (t.inspectedQty ?? 0), 0))
const leftQty = computed(() => openTasks.value.reduce((s, t) => s + t.toInspectQty, 0))

/** 찍은 태그가 가리키는 줄. 빈이 없어 SKU 하나로 정해진다 */
const matched = computed(() => {
  const sku = scan.skuScan.trim()
  if (!sku) return null
  return openTasks.value.find((t) => t.skuBarcode === sku || t.skuId === sku) ?? null
})

async function onSkuScanned() {
  scanError.value = ''
  const m = matched.value
  if (!m) {
    scanError.value = scan.skuScan.trim()
      ? `${scan.skuScan} 은(는) 이 지시에서 셀 것이 없습니다. 카트에 남의 물건이 들어온 것은 아닌지 확인하세요.`
      : ''
    scan.skuScan = ''
    await nextTick()
    skuInput.value?.focus?.()
    return
  }
  scan.qty = m.toInspectQty
  await nextTick()
  qtyInput.value?.select?.()
}

const qtyError = computed(() => {
  const m = matched.value
  if (!m) return ''
  const q = Number(scan.qty)
  if (!q || q < 1) return '1 이상이어야 합니다.'
  if (q > m.toInspectQty) return `셀 것은 ${m.toInspectQty} 개입니다.`
  return ''
})

const canSubmit = computed(
  () => matched.value && !qtyError.value && !busy.value && canInspect.value,
)

async function submitInspect() {
  if (!canSubmit.value) return
  busy.value = true
  scanError.value = ''
  const m = matched.value
  try {
    const out = await outboundApi.inspect(target.value.outboundSeq, {
      lineSeq: m.lineSeq,
      qty: Number(scan.qty),
    })
    toast.success(`${m.skuId} ${scan.qty} 개`)
    target.value = out
    await loadTasks()
    scan.skuScan = ''
    scan.qty = 1
    await nextTick()
    skuInput.value?.focus?.()
    await fetchPage()
  } catch (e) {
    scanError.value = e.message
    scan.skuScan = ''
    await nextTick()
    skuInput.value?.focus?.()
  } finally {
    busy.value = false
  }
}

/** 집은 대로 한 번에. 카트를 보고 맞다고 판단했을 때 누른다 */
async function doInspectAll() {
  busy.value = true
  try {
    const out = await outboundApi.inspectAll(target.value.outboundSeq)
    toast.success('집은 대로 세었습니다.')
    target.value = out
    await loadTasks()
    await fetchPage()
  } catch (e) {
    scanError.value = e.message
  } finally {
    busy.value = false
  }
}

async function undo(task) {
  try {
    const out = await outboundApi.inspect(target.value.outboundSeq, {
      lineSeq: task.lineSeq,
      qty: -task.inspectedQty,
    })
    toast.success(`${task.skuId} ${task.inspectedQty} 개를 되돌렸습니다.`)
    target.value = out
    await loadTasks()
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const columns = [
  { key: 'outboundNo', label: '지시번호', width: '170px', cls: 'code' },
  { key: 'orderNo', label: '주문', width: '160px' },
  { key: 'receiverName', label: '수령인', width: '84px' },
  { key: 'plantName', label: '센터', width: '104px' },
  { key: 'totalPickedQty', label: '집음 / 검수', width: '110px', align: 'right' },
  { key: 'assignedToName', label: '담당', width: '100px' },
  { key: 'outboundStatus', label: '상태', width: '90px', align: 'center' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">출고검수</h1>
        <p class="page-desc">
          집은 것을 <strong>카트 앞에서 다시 셉니다</strong>. 피킹은 빈 앞에서 찍고
          검수는 카트를 앞에 두고 찍습니다 — 집는 중에 옆 칸 물건이 섞이거나 카트가
          바뀌는 일을 여기서 잡습니다. <strong>빈은 안 찍습니다</strong>.
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색어"
        placeholder="지시번호 / 주문번호 / 수령인"
        @enter="fetchPage()"
      />
      <FormField
        v-model="filters.plantId"
        label="센터"
        type="select"
        empty-option="전체"
        :options="hierarchy.plantOptions"
        @change="fetchPage()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="fetchPage()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="outboundSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      empty-text="셀 것이 없습니다. 피킹이 끝난 지시가 여기 옵니다."
      @row-click="openInspect"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.singlePack" class="small dim">단포</div>
      </template>

      <template #cell-totalPickedQty="{ row, value }">
        <strong>{{ num(value) }}</strong>
        <span class="dim"> / </span>
        <span :class="row.totalInspectedQty ? 'ok' : 'dim'">{{ num(row.totalInspectedQty) }}</span>
      </template>

      <template #cell-assignedToName="{ value }">
        <span v-if="value">{{ value }}</span>
        <span v-else class="small dim">-</span>
      </template>

      <template #cell-outboundStatus="{ value }">
        <CodeBadge group="OUTBOUND_STATUS" :code="value" />
      </template>
    </DataTable>

    <!-- ── 세기 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      title="검수"
      :subtitle="`${target.outboundNo} · ${target.receiverName}`"
      size="wide"
      @close="target = null"
    >
      <div class="sum">
        <span>집음 <strong>{{ num(target.totalPickedQty) }}</strong></span>
        <span class="ok">센 것 <strong>{{ num(doneQty) }}</strong></span>
        <span :class="leftQty ? 'danger' : 'ok'">남음 <strong>{{ num(leftQty) }}</strong></span>
        <CodeBadge group="OUTBOUND_STATUS" :code="target.outboundStatus" />
      </div>

      <div v-if="scanError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ scanError }}</span>
      </div>

      <div v-if="!openTasks.length" class="alert alert-ok mb-2">
        <span class="alert-icon">✅</span>
        <span>다 세었습니다. 패킹으로 넘어갑니다.</span>
      </div>

      <div v-else class="scan-grid">
        <FormField
          ref="skuInput"
          v-model="scan.skuScan"
          label="SKU 스캔"
          mono
          placeholder="카트에서 꺼내 태그를 찍으세요"
          :help="matched ? matched.productName : '빈은 찍지 않습니다'"
          @enter="onSkuScanned()"
        />
        <FormField
          ref="qtyInput"
          v-model="scan.qty"
          label="수량"
          type="number"
          :disabled="!matched"
          :error="qtyError"
          :help="matched ? `셀 것 ${matched.toInspectQty} 개` : ''"
          @enter="submitInspect()"
        />
        <button class="btn btn-primary scan-submit" :disabled="!canSubmit" @click="submitInspect()">
          <span v-if="busy" class="spinner"></span>
          세었다
        </button>
        <!--
          하나씩 찍는 것이 기본이다. 단포처럼 한 줄 한 개짜리를 하루에 수백 건
          치는 곳에서는 그 한 번이 그대로 시간이 되어, 카트를 보고 맞다고
          판단했을 때 누르는 길을 둔다 — 검수를 건너뛰는 것과는 다르다.
        -->
        <button class="btn scan-submit" :disabled="busy || !canInspect" @click="doInspectAll()">
          집은 대로 전부
        </button>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 170px">SKU</th>
            <th style="width: 190px">제품</th>
            <th style="width: 70px" class="right">지시</th>
            <th style="width: 70px" class="right">집음</th>
            <th style="width: 70px" class="right">센 것</th>
            <th style="width: 70px" class="right">남음</th>
            <th style="width: 100px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.lineSeq" :class="{ done: t.toInspectQty <= 0 }">
            <td>
              <span class="code">{{ t.skuId }}</span>
              <div class="small dim">{{ t.colorCode }} / {{ t.sizeCode }}</div>
            </td>
            <td class="small">{{ t.productName }}</td>
            <td class="right dim">{{ num(t.instructedQty) }}</td>
            <td class="right">{{ num(t.pickedQty) }}</td>
            <td class="right" :class="t.inspectedQty ? 'ok' : 'dim'">{{ num(t.inspectedQty) }}</td>
            <td class="right" :class="t.toInspectQty > 0 ? 'danger' : 'dim'">
              {{ num(t.toInspectQty) }}
            </td>
            <td class="right">
              <button
                v-if="t.inspectedQty > 0"
                class="btn btn-sm"
                :disabled="!canInspect"
                title="센 것을 되돌립니다"
                @click="undo(t)"
              >
                되돌리기
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          센 만큼만 박스에 담을 수 있습니다 — 지시 ≥ 집음 ≥ 검수 ≥ 담음.
        </span>
        <button class="btn" @click="target = null">닫기</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.sum {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 6px;
  background: var(--bg-2, #f6f7f9);
}
.scan-grid {
  display: grid;
  grid-template-columns: 1.6fr 0.7fr auto auto;
  gap: 10px;
  align-items: end;
}
@media (max-width: 720px) {
  .scan-grid {
    grid-template-columns: 1fr;
  }
}
.scan-submit {
  height: 38px;
}
.lines th,
.lines td {
  vertical-align: top;
}
/* 다 센 줄은 흐리게 — 남은 것이 눈에 먼저 들어와야 한다 */
.done {
  opacity: 0.55;
}
.right {
  text-align: right;
}
.mt-2 {
  margin-top: 10px;
}
.ok {
  color: var(--c-green, #16a34a);
}
.danger {
  color: var(--c-red, #dc2626);
}
</style>
