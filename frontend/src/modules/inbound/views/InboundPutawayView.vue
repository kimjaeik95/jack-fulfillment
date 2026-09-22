<script setup>
/**
 * 적치 · 입고완료 (INB-PG-005, INB-PG-006, INB-PG-007).
 *
 * 검수를 통과했어도 아직 마당에 있다. <b>자리에 놓아야 찾을 수 있고, 찾을
 * 수 있어야 팔 수 있다.</b>
 *
 * 스캔 두 번으로 확인한다 (INB-007). SKU 바코드와 로케이션 바코드를 둘 다
 * 받아 지시와 대조한다 — 다른 물건을 그 자리에 놓으면 재고는 맞는 것처럼
 * 보이는데 정작 찾을 때 없는 물건을 찾게 된다.
 *
 * 입고완료를 별도 화면으로 빼지 않았다. 적치가 끝난 그 자리에서 누르는
 * 것이 자연스럽고, 화면을 나누면 "적치는 했는데 완료를 안 눌렀다" 가 는다.
 *
 * <b>재고는 완료를 눌러야 는다.</b> 그리고 적치된 수량만.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import * as inboundApi from '@/api/inbound.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({ keyword: '', plantId: '' })

/** 적치할 것은 검수를 시작한 것들이다 — 검수중이거나 적치중 */
async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const [inspecting, putaway] = await Promise.all([
      inboundApi.list({ ...filters, inboundStatus: 'INSPECTING', size: inboundApi.PAGE_SIZE }),
      inboundApi.list({ ...filters, inboundStatus: 'PUTAWAY', size: inboundApi.PAGE_SIZE }),
    ])
    rows.value = [...inspecting.rows, ...putaway.rows]
    total.value = inspecting.total + putaway.total
  } catch (e) {
    rows.value = []
    total.value = 0
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

const search = () => fetchPage()

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await fetchPage()
})

const readyCount = computed(() => rows.value.filter((r) => r.closable).length)
const blockedCount = computed(
  () => rows.value.filter((r) => r.needsOverApproval && !r.overApproved).length,
)

const columns = [
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '118px' },
  { key: '_where', label: '받은 곳', width: '142px' },
  { key: '_qty', label: '기입고 / 적치', width: '124px', align: 'right' },
  { key: 'inboundStatus', label: '상태', width: '84px', align: 'center' },
  { key: '_act', label: '', width: '120px', align: 'right' },
]

/* ── 적치 스캔 ──────────────────────────────────────────────── */

const target = ref(null)
const busy = ref(false)
const serverError = ref('')
const askClose = ref(null)

/** 스캔 입력. 스캐너는 문자열 + Enter 를 보낸다. */
const scan = reactive({ lineSeq: null, skuScan: '', locationScan: '', qty: 1 })
const skuInput = ref(null)
const locInput = ref(null)

async function openPutaway(row) {
  serverError.value = ''
  Object.assign(scan, { lineSeq: null, skuScan: '', locationScan: '', qty: 1 })
  try {
    target.value = await inboundApi.detail(row.inboundSeq)
    // 아직 놓을 것이 남은 첫 줄을 미리 고른다. 대개 줄이 하나다.
    const first = target.value.lines.find((l) => l.pendingPutawayQty > 0)
    if (first) selectLine(first)
    await nextTick()
    skuInput.value?.focus?.()
  } catch (e) {
    loadError.value = e.message
  }
}

/** 줄을 고르면 남은 수량을 미리 채운다 */
function selectLine(line) {
  scan.lineSeq = line.lineSeq
  scan.qty = line.pendingPutawayQty
  scan.skuScan = ''
  scan.locationScan = ''
}

const selectedLine = computed(
  () => (target.value?.lines ?? []).find((l) => l.lineSeq === scan.lineSeq) ?? null,
)

/**
 * 지금 '놓기' 를 못 누르는 이유.
 *
 * 줄을 고르면 수량은 실제로 채워지는데 SKU · 로케이션 칸은 placeholder 만
 * 바뀐다. 회색 글씨가 값처럼 보여서 다 찬 줄 알고 버튼을 누르게 되고,
 * 버튼은 아무 말 없이 잠겨 있다 — 왜 안 되는지 화면 어디에도 없었다.
 */
const missing = computed(() => {
  if (!scan.lineSeq) return '먼저 위에서 놓을 줄을 고르세요.'
  if (!scan.skuScan.trim()) return `① SKU 를 스캔하세요 (${selectedLine.value?.skuId ?? ''}).`
  if (!scan.locationScan.trim()) return '② 놓을 자리의 빈 라벨을 스캔하세요.'
  const q = Number(scan.qty)
  const pending = selectedLine.value?.pendingPutawayQty ?? 0
  if (!(q >= 1)) return '수량은 1 이상이어야 합니다.'
  if (q > pending) return `남은 ${pending} 개보다 많이 놓을 수 없습니다.`
  return ''
})

const scanValid = computed(
  () =>
    scan.lineSeq &&
    scan.skuScan.trim() &&
    scan.locationScan.trim() &&
    Number(scan.qty) >= 1 &&
    Number(scan.qty) <= (selectedLine.value?.pendingPutawayQty ?? 0) &&
    !busy.value,
)

/** SKU 를 찍으면 로케이션 칸으로 넘어간다 — 스캐너는 손을 안 뗀다 */
async function onSkuScanned() {
  await nextTick()
  locInput.value?.focus?.()
}

async function submitPutaway() {
  busy.value = true
  serverError.value = ''
  try {
    const { inbound, warning } = await inboundApi.putaway(target.value.inboundSeq, {
      lineSeq: scan.lineSeq,
      skuScan: scan.skuScan.trim(),
      locationScan: scan.locationScan.trim(),
      qty: Number(scan.qty),
    })
    toast.success(`${scan.qty} 개를 놓았습니다.`)
    if (warning) toast.warn(warning)
    target.value = inbound
    const next = inbound.lines.find((l) => l.pendingPutawayQty > 0)
    if (next) {
      selectLine(next)
      await nextTick()
      skuInput.value?.focus?.()
    } else {
      Object.assign(scan, { lineSeq: null, skuScan: '', locationScan: '', qty: 1 })
    }
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
    // 스캔이 틀렸으면 그 칸을 비우고 다시 찍게 한다
    scan.skuScan = ''
    scan.locationScan = ''
    await nextTick()
    skuInput.value?.focus?.()
  } finally {
    busy.value = false
  }
}

async function doClose() {
  const row = askClose.value
  askClose.value = null
  busy.value = true
  try {
    const { inbound, warning } = await inboundApi.close(row.inboundSeq)
    toast.success(`${inbound.inboundNo} 입고완료 — 재고가 늘었습니다.`)
    if (warning) toast.warn(warning)
    if (target.value?.inboundSeq === inbound.inboundSeq) target.value = null
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const canPutaway = computed(() => session.can('INB_PUTAWAY', 'C'))
const denyReason = computed(() => session.denyReason('INB_PUTAWAY', 'C'))

/**
 * 완료는 권한이 다르다 (INB_APPROVE).
 *
 * 놓는 사람과 "이제 우리 재고다" 라고 선언하는 사람을 나눈다 — 총량이
 * 바뀌는 일이라 조정 · 실사에서 요청자와 승인자를 나눈 것과 같은 이유다.
 *
 * 그래서 입고담당에게는 버튼이 눌리지 않는다. 숨기지 않고 왜 못 누르는지
 * 말해 준다 — 안 보이면 "완료가 어디 있지" 를 찾게 된다.
 */
const canClose = computed(() => session.can('INB_APPROVE', 'A'))
const closeDenyReason = computed(
  () => session.denyReason('INB_APPROVE', 'A') ?? '재고가 늘어납니다. 되돌릴 수 없습니다.',
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">적치 · 입고완료</h1>
        <p class="page-desc">
          검수를 통과했어도 아직 마당에 있습니다. <strong>자리에 놓아야 찾을 수 있고,
          찾을 수 있어야 팔 수 있습니다.</strong> SKU 와 로케이션을 둘 다 스캔해
          지시와 대조합니다 — 다르면 막습니다. <strong>재고는 입고완료를 눌러야 늘고,
          적치된 수량만</strong> 반영됩니다.
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="denyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ denyReason }}</span>
    </div>

    <div class="summary">
      <div class="summary-item">
        <span class="summary-label">적치 대상</span>
        <strong class="summary-value">{{ num(total) }}</strong>
      </div>
      <div class="summary-item" :class="{ ready: readyCount > 0 }">
        <span class="summary-label">완료 가능</span>
        <strong class="summary-value">{{ num(readyCount) }}</strong>
      </div>
      <div class="summary-item" :class="{ alarm: blockedCount > 0 }">
        <span class="summary-label">승인 대기</span>
        <strong class="summary-value">{{ num(blockedCount) }}</strong>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="입고번호 / 발주번호 / 공급처"
          @enter="search()"
        />
        <FormField
          v-model="filters.plantId"
          label="센터"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
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
        row-key="inboundSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        empty-text="적치할 입고가 없습니다. 검수를 먼저 하세요."
        @row-click="openPutaway"
      >
        <template #cell-_where="{ row }">
          {{ row.plantName }}
          <div class="small dim">{{ row.warehouseName }}</div>
        </template>

        <template #cell-_qty="{ row }">
          <strong>{{ num(row.totalReceivedQty) }}</strong>
          <span class="dim"> / </span>
          <strong :class="row.totalPutawayQty >= row.totalReceivedQty ? 'ok' : 'warn'">
            {{ num(row.totalPutawayQty) }}
          </strong>
          <div v-if="row.needsOverApproval && !row.overApproved" class="small danger">
            초과승인 필요
          </div>
        </template>

        <template #cell-inboundStatus="{ value }">
          <CodeBadge group="INBOUND_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button class="btn btn-sm" :disabled="!canPutaway" @click.stop="openPutaway(row)">
              적치
            </button>
            <button
              v-if="row.closable"
              class="btn btn-sm btn-primary"
              :disabled="!canClose"
              :title="closeDenyReason"
              @click.stop="askClose = row"
            >
              완료
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ── 적치 스캔 ────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      title="적치"
      :subtitle="`${target.inboundNo} · ${target.warehouseName}`"
      size="wide"
      @close="target = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="detail-head">
        <CodeBadge group="INBOUND_STATUS" :code="target.inboundStatus" />
        <span>기입고 <strong>{{ num(target.totalReceivedQty) }}</strong></span>
        <span>
          적치 <strong :class="target.totalPutawayQty >= target.totalReceivedQty ? 'ok' : 'warn'">
            {{ num(target.totalPutawayQty) }}
          </strong>
        </span>
        <span v-if="target.totalRejectedQty" class="warn">
          거부 {{ num(target.totalRejectedQty) }} (재고 안 됨)
        </span>
      </div>

      <div
        v-if="target.needsOverApproval && !target.overApproved"
        class="alert alert-warn mt-1"
      >
        <span class="alert-icon">⚠</span>
        <span>
          예정보다 <strong>{{ target.overQty }}개</strong> 많이 받았습니다 (허용
          {{ target.allowedOverQty }}개). 적치는 할 수 있지만
          <strong>초과입고 승인 없이는 완료할 수 없습니다.</strong>
        </span>
      </div>

      <!-- 놓을 줄 고르기 -->
      <div class="lines-head">
        <strong>놓을 줄</strong>
        <span class="small dim">남은 수량이 있는 줄만 고를 수 있습니다.</span>
      </div>
      <table class="table lines">
        <thead>
          <tr>
            <th style="width: 36px"></th>
            <th style="width: 32px" class="right">#</th>
            <th style="width: 170px">SKU</th>
            <th style="width: 70px" class="right">기입고</th>
            <th style="width: 70px" class="right">적치</th>
            <th style="width: 70px" class="right">남음</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="l in target.lines"
            :key="l.lineSeq"
            :class="{ picked: l.lineSeq === scan.lineSeq, done: l.putawayDone }"
            @click="l.pendingPutawayQty > 0 && selectLine(l)"
          >
            <td>
              <input
                type="radio"
                :checked="l.lineSeq === scan.lineSeq"
                :disabled="l.pendingPutawayQty === 0"
                @change="selectLine(l)"
              />
            </td>
            <td class="num">{{ l.lineNo }}</td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td class="num">{{ num(l.receivedQty) }}</td>
            <td class="num">{{ num(l.putawayQty) }}</td>
            <td class="num">
              <strong :class="l.pendingPutawayQty ? 'warn' : 'dim'">
                {{ num(l.pendingPutawayQty) }}
              </strong>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 스캔 -->
      <div class="lines-head">
        <strong>스캔</strong>
        <span v-if="missing" class="small warn">{{ missing }}</span>
        <span v-else class="small dim">
          바코드가 안 읽히면 SKU 코드 · 빈 코드를 직접 넣어도 됩니다.
        </span>
      </div>
      <div class="form-grid">
        <FormField
          ref="skuInput"
          v-model="scan.skuScan"
          label="① SKU 스캔"
          mono
          :placeholder="selectedLine ? `${selectedLine.skuId} 를 스캔` : '줄을 먼저 고르세요'"
          :disabled="!scan.lineSeq"
          help="지시한 줄과 다르면 막습니다."
          @enter="onSkuScanned()"
        />
        <FormField
          ref="locInput"
          v-model="scan.locationScan"
          label="② 로케이션 스캔"
          mono
          placeholder="빈 라벨을 스캔 (예: 1A-01-01)"
          :disabled="!scan.lineSeq"
          help="이 입고의 창고 안이어야 합니다."
          @enter="scanValid && submitPutaway()"
        />
        <FormField
          v-model.number="scan.qty"
          label="수량"
          type="number"
          :disabled="!scan.lineSeq"
          :help="selectedLine ? `남은 ${selectedLine.pendingPutawayQty} 개까지` : ''"
        />
      </div>

      <!-- 이미 놓은 것 -->
      <template v-if="target.putaways.length">
        <div class="lines-head">
          <strong>놓은 자리 {{ target.putaways.length }} 곳</strong>
          <span class="small dim">한 줄을 여러 자리에 나눠 놓을 수 있습니다.</span>
        </div>
        <table class="table lines">
          <thead>
            <tr>
              <th style="width: 170px">SKU</th>
              <th style="width: 190px">자리</th>
              <th style="width: 70px" class="right">수량</th>
              <th style="width: 90px">놓은이</th>
              <th style="width: 84px" align="center">재고반영</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in target.putaways" :key="p.putawaySeq">
              <td><span class="code">{{ p.skuId }}</span></td>
              <td>
                <span class="code">{{ p.locationFullCode }}</span>
                <div v-if="p.zoneCode" class="small dim">{{ p.zoneCode }} 구역</div>
              </td>
              <td class="num">{{ num(p.qty) }}</td>
              <td class="small">{{ p.putawayByName ?? p.putawayBy }}</td>
              <td align="center">
                <span v-if="p.applied" class="badge badge-green plain">완료</span>
                <span v-else class="small dim">대기</span>
              </td>
            </tr>
          </tbody>
        </table>
      </template>

      <template #footer>
        <span class="left small dim">
          {{
            target.closable
              ? '적치가 끝났습니다. 완료를 누르면 재고가 늘어납니다.'
              : target.needsOverApproval && !target.overApproved
                ? '초과입고 승인을 먼저 받아야 완료할 수 있습니다.'
                : '아직 놓을 것이 남았습니다.'
          }}
        </span>
        <button class="btn" :disabled="busy" @click="target = null">닫기</button>
        <span v-if="missing && canPutaway" class="small warn need">{{ missing }}</span>
        <button
          class="btn"
          :disabled="!scanValid || !canPutaway"
          :title="missing || '이 자리에 놓습니다'"
          @click="submitPutaway()"
        >
          <span v-if="busy" class="spinner"></span>
          놓기
        </button>
        <button
          v-if="target.closable"
          class="btn btn-primary"
          :disabled="!canClose || busy"
          :title="closeDenyReason"
          @click="askClose = target"
        >
          입고완료
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askClose"
      title="입고완료"
      :message="`${askClose.inboundNo} — 적치한 ${num(askClose.totalPutawayQty)} 개를 재고로 반영합니까?`"
      detail="여기서 재고가 늘어납니다. 되돌릴 수 없습니다 — 줄이려면 재고조정으로 처리해야 합니다. 적치하지 않은 수량은 재고가 되지 않으며, 거부한 수량도 반영되지 않습니다."
      confirm-label="입고완료"
      :busy="busy"
      @cancel="askClose = null"
      @confirm="doClose()"
    />
  </div>
</template>

<style scoped>
.summary {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.summary-item {
  flex: 1 1 140px;
  padding: 10px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--bg-card, #fff);
}
.summary-item.ready {
  border-color: var(--c-green, #16a34a);
}
.summary-item.alarm {
  border-color: var(--c-red, #dc2626);
}
.summary-item.alarm .summary-value {
  color: var(--c-red, #dc2626);
}
.summary-label {
  display: block;
  font-size: 12px;
  color: var(--fg-dim, #6b7280);
}
.summary-value {
  font-size: 20px;
}
.lines-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 14px 0 8px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.lines tr.picked td {
  background: var(--primary-soft, #eff6ff);
}
.lines tr.done td {
  opacity: 0.55;
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.mt-1 {
  margin-top: 6px;
}
.ok {
  color: var(--c-green, #16a34a);
}
.danger {
  color: var(--c-red, #dc2626);
}
/* 버튼 옆에 왜 못 누르는지 — 바닥 줄이 좁아 넘치지 않게 둔다 */
.need {
  margin-right: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 340px;
}

.warn {
  color: var(--c-amber, #b45309);
}
</style>
