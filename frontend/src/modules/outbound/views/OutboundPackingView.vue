<script setup>
/**
 * 패킹 · 박스 관리 (PAC-PG-001, PAC-PG-002).
 *
 * 검수한 것을 박스에 담는다. 지시 하나가 박스 여러 개로 나갈 수 있고(부피
 * 초과), 박스마다 송장이 하나씩 붙는다 (D섹터).
 *
 * <b>검수한 것만 담을 수 있다.</b> 수량이 지시 ≥ 집음 ≥ 검수 ≥ 담음 으로
 * 좁혀지는 것이 규칙이고, 그래야 마지막에 '어디서 틀어졌나' 를 한 줄로
 * 짚을 수 있다 (OUT-PG-007).
 *
 * 여기서도 재고 수량은 안 바뀐다. 물건은 카트에서 박스로 옮겨졌을 뿐이다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import * as outboundApi from '@/api/outbound.js'
import { codeOptions } from '@/api/codes.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const session = useSessionStore()
const hierarchy = useHierarchyStore()
const toast = useToastStore()

const canPack = computed(() => session.can('OUT_PACK', 'C'))
const canManageBox = computed(() => session.can('OUT_PACK', 'U'))

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', plantId: '' })

/** 검수가 끝난 것이 여기 온다. 센 만큼만 담을 수 있기 때문이다 */
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
    rows.value = (data.rows ?? []).filter((o) => (o.totalInspectedQty ?? 0) > 0)
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

/* ── 담기 ───────────────────────────────────────────────────── */

const target = ref(null)
const boxes = ref([])
const tasks = ref([])
const activeBoxSeq = ref(null)
const scan = reactive({ skuScan: '', qty: 1 })
const skuInput = ref(null)
const qtyInput = ref(null)
const busy = ref(false)
const scanError = ref('')

async function openPacking(row) {
  target.value = row
  await reload()
  // 열려 있는 박스가 있으면 그것부터 담는다. 없으면 하나 만들어야 한다.
  activeBoxSeq.value = boxes.value.find((b) => b.open)?.boxSeq ?? null
  Object.assign(scan, { skuScan: '', qty: 1 })
  scanError.value = ''
  await nextTick()
  skuInput.value?.focus?.()
}

async function reload() {
  const [bs, ts] = await Promise.all([
    outboundApi.boxes(target.value.outboundSeq),
    outboundApi.inspectTasks(target.value.outboundSeq),
  ])
  boxes.value = bs
  tasks.value = ts
}

const activeBox = computed(
  () => boxes.value.find((b) => b.boxSeq === activeBoxSeq.value) ?? null,
)

/** 이 지시 줄이 박스들에 들어간 총 수량 */
const packedOf = (lineSeq) =>
  boxes.value.reduce(
    (s, b) => s + (b.lines.find((l) => l.lineSeq === lineSeq)?.packedQty ?? 0),
    0,
  )

/** 아직 담을 것이 남은 줄 — 검수한 만큼에서 담은 만큼을 뺀다 */
const openTasks = computed(() =>
  tasks.value
    .map((t) => ({ ...t, packed: packedOf(t.lineSeq) }))
    .filter((t) => t.inspectedQty > t.packed),
)

const leftQty = computed(() =>
  openTasks.value.reduce((s, t) => s + (t.inspectedQty - t.packed), 0),
)

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
      ? `${scan.skuScan} 은(는) 담을 것이 없습니다. 검수를 먼저 했는지 확인하세요.`
      : ''
    scan.skuScan = ''
    await nextTick()
    skuInput.value?.focus?.()
    return
  }
  scan.qty = m.inspectedQty - m.packed
  await nextTick()
  qtyInput.value?.select?.()
}

const qtyError = computed(() => {
  const m = matched.value
  if (!m) return ''
  const q = Number(scan.qty)
  const room = m.inspectedQty - m.packed
  if (!q || q < 1) return '1 이상이어야 합니다.'
  if (q > room) return `담을 수 있는 것은 ${room} 개입니다.`
  return ''
})

const canSubmit = computed(
  () => activeBox.value?.open && matched.value && !qtyError.value && !busy.value && canPack.value,
)

async function submitPack() {
  if (!canSubmit.value) return
  busy.value = true
  scanError.value = ''
  const m = matched.value
  try {
    await outboundApi.pack(activeBoxSeq.value, { lineSeq: m.lineSeq, qty: Number(scan.qty) })
    toast.success(`${activeBox.value.boxNo}번 박스에 ${m.skuId} ${scan.qty} 개`)
    await reload()
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

/* ── 박스 (PAC-PG-001) ──────────────────────────────────────── */

const boxForm = reactive({ boxType: '', weightG: '', widthMm: '', heightMm: '', depthMm: '' })
const editingBox = ref(null)

async function addBox() {
  try {
    const b = await outboundApi.addBox(target.value.outboundSeq, {})
    toast.success(`${b.boxNo}번 박스를 만들었습니다.`)
    await reload()
    activeBoxSeq.value = b.boxSeq
    await nextTick()
    skuInput.value?.focus?.()
  } catch (e) {
    toast.error(e.message)
  }
}

function openBoxEdit(box) {
  boxForm.boxType = box.boxType ?? ''
  boxForm.weightG = box.weightG ?? ''
  boxForm.widthMm = box.widthMm ?? ''
  boxForm.heightMm = box.heightMm ?? ''
  boxForm.depthMm = box.depthMm ?? ''
  editingBox.value = box
}

const numOrNull = (v) => (v === '' || v === null ? null : Number(v))

async function saveBox() {
  try {
    await outboundApi.updateBox(editingBox.value.boxSeq, {
      boxType: boxForm.boxType || null,
      weightG: numOrNull(boxForm.weightG),
      widthMm: numOrNull(boxForm.widthMm),
      heightMm: numOrNull(boxForm.heightMm),
      depthMm: numOrNull(boxForm.depthMm),
    })
    toast.success('박스 정보를 저장했습니다.')
    editingBox.value = null
    await reload()
  } catch (e) {
    toast.error(e.message)
  }
}

async function closeBox(box) {
  try {
    await outboundApi.closeBox(box.boxSeq)
    toast.success(`${box.boxNo}번 박스를 닫았습니다.`)
    await reload()
    // 닫았으면 다음 열린 박스로 옮긴다. 없으면 비워 둔다 — 새로 만들어야 한다.
    activeBoxSeq.value = boxes.value.find((b) => b.open)?.boxSeq ?? null
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  }
}

async function reopenBox(box) {
  try {
    await outboundApi.reopenBox(box.boxSeq)
    toast.success(`${box.boxNo}번 박스를 다시 열었습니다.`)
    await reload()
    activeBoxSeq.value = box.boxSeq
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  }
}

const askDeleteBox = ref(null)

async function doDeleteBox() {
  try {
    await outboundApi.deleteBox(askDeleteBox.value.boxSeq)
    toast.success(`${askDeleteBox.value.boxNo}번 박스를 지웠습니다.`)
    askDeleteBox.value = null
    await reload()
    activeBoxSeq.value = boxes.value.find((b) => b.open)?.boxSeq ?? null
  } catch (e) {
    toast.error(e.message)
    askDeleteBox.value = null
  }
}

/** 박스에서 뺀다. 음수로 보내면 실적이 줄어든다 */
async function unpack(box, line) {
  try {
    await outboundApi.pack(box.boxSeq, { lineSeq: line.lineSeq, qty: -line.packedQty })
    toast.success(`${line.skuId} ${line.packedQty} 개를 뺐습니다.`)
    await reload()
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
  { key: 'totalInspectedQty', label: '검수 / 담음', width: '110px', align: 'right' },
  { key: 'boxCount', label: '박스', width: '60px', align: 'right' },
  { key: 'outboundStatus', label: '상태', width: '90px', align: 'center' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">패킹</h1>
        <p class="page-desc">
          검수한 것을 박스에 담습니다. 지시 하나가 <strong>박스 여러 개</strong>로 나갈 수
          있고, 박스마다 송장이 하나씩 붙습니다. <strong>검수한 만큼만</strong> 담을 수
          있습니다.
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
      empty-text="담을 것이 없습니다. 검수가 끝난 지시가 여기 옵니다."
      @row-click="openPacking"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.singlePack" class="small dim">단포</div>
      </template>

      <template #cell-totalInspectedQty="{ row, value }">
        <strong>{{ num(value) }}</strong>
        <span class="dim"> / </span>
        <span :class="row.totalPackedQty ? 'ok' : 'dim'">{{ num(row.totalPackedQty) }}</span>
      </template>

      <template #cell-outboundStatus="{ value }">
        <CodeBadge group="OUTBOUND_STATUS" :code="value" />
      </template>
    </DataTable>

    <!-- ── 담기 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      title="패킹"
      :subtitle="`${target.outboundNo} · ${target.receiverName}`"
      size="wide"
      @close="target = null"
    >
      <div class="sum">
        <span>검수 <strong>{{ num(target.totalInspectedQty) }}</strong></span>
        <span class="ok">담음 <strong>{{ num(target.totalPackedQty) }}</strong></span>
        <span :class="leftQty ? 'danger' : 'ok'">남음 <strong>{{ num(leftQty) }}</strong></span>
        <CodeBadge group="OUTBOUND_STATUS" :code="target.outboundStatus" />
        <button class="btn btn-sm btn-primary" :disabled="!canPack" @click="addBox()">
          + 박스 추가
        </button>
      </div>

      <div v-if="scanError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ scanError }}</span>
      </div>

      <div v-if="!boxes.length" class="alert alert-warn mb-2">
        <span class="alert-icon">📦</span>
        <span>박스가 없습니다. <strong>+ 박스 추가</strong>로 하나 만들고 담으세요.</span>
      </div>
      <div v-else-if="!leftQty" class="alert alert-ok mb-2">
        <span class="alert-icon">✅</span>
        <span>
          다 담았습니다. <strong>열린 박스를 모두 닫으면</strong> 패킹완료가 됩니다 —
          그 다음은 송장 발급입니다 (4차 D섹터).
        </span>
      </div>

      <!--
        어느 박스에 담을지 먼저 고른다. 담는 곳이 여럿이라 '지금 이 박스' 가
        눈에 보여야 엉뚱한 상자에 넣지 않는다.
      -->
      <div v-if="boxes.length" class="box-strip">
        <button
          v-for="b in boxes"
          :key="b.boxSeq"
          class="box-chip"
          :class="{ active: b.boxSeq === activeBoxSeq, closed: b.closed }"
          :disabled="b.closed"
          :title="b.closed ? '닫힌 박스입니다' : '이 박스에 담습니다'"
          @click="activeBoxSeq = b.boxSeq"
        >
          <strong>{{ b.boxNo }}번</strong>
          <span class="small">{{ num(b.totalPackedQty) }}개</span>
          <span v-if="b.boxType" class="small dim">{{ b.boxType }}</span>
          <span v-if="b.closed" class="small">🔒</span>
        </button>
      </div>

      <div v-if="activeBox?.open && leftQty" class="scan-grid">
        <FormField
          ref="skuInput"
          v-model="scan.skuScan"
          label="SKU 스캔"
          mono
          :placeholder="`${activeBox.boxNo}번 박스에 담을 물건의 태그를 찍으세요`"
          :help="matched ? matched.productName : '검수한 것만 담을 수 있습니다'"
          @enter="onSkuScanned()"
        />
        <FormField
          ref="qtyInput"
          v-model="scan.qty"
          label="수량"
          type="number"
          :disabled="!matched"
          :error="qtyError"
          :help="matched ? `담을 것 ${matched.inspectedQty - matched.packed} 개` : ''"
          @enter="submitPack()"
        />
        <button class="btn btn-primary scan-submit" :disabled="!canSubmit" @click="submitPack()">
          <span v-if="busy" class="spinner"></span>
          {{ activeBox.boxNo }}번에 담기
        </button>
      </div>

      <!-- 박스별 내용 -->
      <div v-for="b in boxes" :key="b.boxSeq" class="box-card" :class="{ closed: b.closed }">
        <div class="box-head">
          <strong>{{ b.boxNo }}번 박스</strong>
          <CodeBadge group="BOX_STATUS" :code="b.boxStatus" />
          <span v-if="b.boxType" class="small dim">{{ b.boxType }}</span>
          <span v-if="b.weightG" class="small dim">{{ num(b.weightG) }}g</span>
          <span class="small dim">{{ num(b.totalPackedQty) }}개</span>
          <span v-if="b.closedByName" class="small dim">닫음 {{ b.closedByName }}</span>
          <span class="grow"></span>
          <button
            v-if="b.open"
            class="btn btn-sm"
            :disabled="!canManageBox"
            @click="openBoxEdit(b)"
          >
            규격 · 무게
          </button>
          <button
            v-if="b.open"
            class="btn btn-sm btn-primary"
            :disabled="!canManageBox || b.empty"
            :title="b.empty ? '빈 박스는 닫지 않습니다' : '더 담을 수 없게 됩니다'"
            @click="closeBox(b)"
          >
            닫기
          </button>
          <button
            v-else
            class="btn btn-sm"
            :disabled="!canManageBox"
            title="송장이 붙기 전까지는 다시 열 수 있습니다"
            @click="reopenBox(b)"
          >
            다시 열기
          </button>
          <button
            v-if="b.open && b.empty"
            class="btn btn-sm btn-danger"
            :disabled="!canManageBox"
            @click="askDeleteBox = b"
          >
            ×
          </button>
        </div>

        <div v-if="!b.lines.length" class="small dim empty-box">비어 있습니다.</div>
        <table v-else class="table sub">
          <tr v-for="l in b.lines" :key="l.boxLineSeq">
            <td class="code">{{ l.skuId }}</td>
            <td class="small dim">{{ l.colorCode }} / {{ l.sizeCode }}</td>
            <td class="small">{{ l.productName }}</td>
            <td class="right"><strong>{{ num(l.packedQty) }}</strong></td>
            <td class="right">
              <button
                v-if="b.open"
                class="btn btn-sm"
                :disabled="!canPack"
                title="이 박스에서 뺍니다"
                @click="unpack(b, l)"
              >
                빼기
              </button>
            </td>
          </tr>
        </table>
      </div>

      <template #footer>
        <span class="left small dim">
          재고는 출고확정에서 줄어듭니다. 지금은 박스에 담은 것까지입니다.
        </span>
        <button class="btn" @click="target = null">닫기</button>
      </template>
    </ModalDialog>

    <!-- ── 박스 규격 · 실측 ─────────────────────────────────── -->
    <ModalDialog
      v-if="editingBox"
      title="박스 규격 · 무게"
      :subtitle="`${editingBox.boxNo}번 박스`"
      @close="editingBox = null"
    >
      <p class="small dim">
        택배사에 넘길 값입니다. 저울이 없으면 비워 두세요 — 송장 발급이 필요하면 그때
        요구합니다. 닫은 뒤에는 고칠 수 없습니다.
      </p>
      <div class="form-grid mt-2">
        <FormField
          v-model="boxForm.boxType"
          label="규격"
          type="select"
          empty-option="고르지 않음"
          :options="codeOptions('BOX_TYPE')"
        />
        <FormField v-model="boxForm.weightG" label="무게 (g)" type="number" />
        <FormField v-model="boxForm.widthMm" label="가로 (mm)" type="number" />
        <FormField v-model="boxForm.heightMm" label="세로 (mm)" type="number" />
        <FormField v-model="boxForm.depthMm" label="높이 (mm)" type="number" />
      </div>
      <template #footer>
        <button class="btn" @click="editingBox = null">닫기</button>
        <button class="btn btn-primary" @click="saveBox()">저장</button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDeleteBox"
      title="박스 지우기"
      :message="`${askDeleteBox.boxNo}번 박스를 지웁니까? 비어 있는 박스만 지울 수 있습니다.`"
      confirm-label="지우기"
      danger
      @confirm="doDeleteBox()"
      @cancel="askDeleteBox = null"
    />
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
/* 지금 담는 박스를 고르는 줄. 여럿이라 '이 박스' 가 보여야 한다 */
.box-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.box-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--line, #e5e7eb);
  background: var(--bg-1, #fff);
  cursor: pointer;
}
.box-chip.active {
  border-color: var(--c-blue, #2563eb);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.18);
}
.box-chip.closed {
  opacity: 0.6;
  cursor: default;
}
.scan-grid {
  display: grid;
  grid-template-columns: 1.6fr 0.7fr auto;
  gap: 10px;
  align-items: end;
  margin-bottom: 12px;
}
@media (max-width: 720px) {
  .scan-grid {
    grid-template-columns: 1fr;
  }
}
.scan-submit {
  height: 38px;
}
.box-card {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid var(--line, #e5e7eb);
}
.box-card.closed {
  background: var(--bg-2, #f6f7f9);
}
.box-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.grow {
  flex: 1;
}
.empty-box {
  padding: 6px 0;
}
.sub td {
  padding: 3px 8px 3px 0;
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
