<script setup>
/**
 * 입고검수 (INB-PG-003).
 *
 * 입하가 "차에서 내렸다" 라면 검수는 <b>세어 보고 받아들인다</b> 이다.
 * 내린 개수와 세어 본 개수는 또 다르고, 그 차이를 찾는 것이 이 화면의 일이다.
 *
 * 합격은 기입고로 쌓이고 발주 잔량까지 줄인다. 거부는 <b>재고가 되지
 * 않는다</b> — 받은 적 없는 물건이라 우리 것이 아니고, 공급처와 값을
 * 협상하는 근거가 된다.
 *
 * 한 번에 다 세지 않아도 된다. 회차로 쌓이므로 오후에 나머지를 세면 그것이
 * 2 회차다. 앞 회차를 덮지 않는 이유는, 덮으면 "처음엔 몇 개라 했었지" 를
 * 아무도 답할 수 없기 때문이다.
 *
 * 여기서도 재고는 안 는다. 검수를 통과해도 아직 마당에 있다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as inboundApi from '@/api/inbound.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const size = inboundApi.PAGE_SIZE

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

/**
 * 검수할 것은 '입하했고 아직 다 안 센 것' 이다.
 *
 * 입하(ARRIVED)와 검수중(INSPECTING)이 여기 해당한다 — 분할 검수 때문에
 * 검수중도 계속 보여야 한다.
 */
const filters = reactive({ keyword: '', plantId: '', inboundStatus: '' })

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const [arrived, inspecting] = await Promise.all([
      inboundApi.list({ ...filters, inboundStatus: 'ARRIVED', page: 1, size }),
      inboundApi.list({ ...filters, inboundStatus: 'INSPECTING', page: 1, size }),
    ])
    rows.value = [...arrived.rows, ...inspecting.rows]
    total.value = arrived.total + inspecting.total
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

const pendingCount = computed(
  () => rows.value.filter((r) => r.totalArrivedQty > r.totalReceivedQty + r.totalRejectedQty).length,
)
const rejectedCount = computed(() => rows.value.reduce((s, r) => s + (r.totalRejectedQty ?? 0), 0))

const columns = [
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '120px' },
  { key: '_where', label: '받은 곳', width: '146px' },
  { key: 'arrivedAt', label: '입하', width: '104px', align: 'center' },
  { key: '_qty', label: '입하 / 검수', width: '120px', align: 'right' },
  { key: 'inboundStatus', label: '상태', width: '84px', align: 'center' },
  { key: '_act', label: '', width: '82px', align: 'right' },
]

/* ── 검수 입력 ──────────────────────────────────────────────── */

const target = ref(null)
const busy = ref(false)
const serverError = ref('')
/** 줄별 금회 검수 — { passed, rejected, reason } */
const draft = reactive({})

async function openInspect(row) {
  serverError.value = ''
  try {
    /*
     * 순서가 중요하다. draft 를 먼저 비우고 await 하면, 기다리는 동안
     * target 은 옛 줄을 들고 있는데 draft 는 비어 있어 화면이 없는 값을
     * 읽는다. 받아 온 뒤에 한꺼번에 바꾼다.
     */
    const full = await inboundApi.detail(row.inboundSeq)
    for (const k of Object.keys(draft)) delete draft[k]
    for (const l of full.lines) {
      // 안 센 수량을 합격으로 미리 채운다. 대부분은 다 맞게 오고,
      // 줄마다 같은 숫자를 다시 치게 하면 오타만 는다.
      draft[l.lineSeq] = { passed: l.pendingInspectQty, rejected: 0, reason: '' }
    }
    target.value = full
  } catch (e) {
    loadError.value = e.message
  }
}

/** 이번에 손댄 줄만 보낸다 — 안 건드린 줄까지 보내면 0 짜리 회차가 쌓인다 */
const dirtyLines = computed(() =>
  (target.value?.lines ?? []).filter((l) => {
    const d = draft[l.lineSeq]
    return d && (Number(d.passed) > 0 || Number(d.rejected) > 0)
  }),
)

const lineError = (l) => {
  const d = draft[l.lineSeq]
  if (!d) return ''
  const handled = Number(d.passed || 0) + Number(d.rejected || 0)
  if (handled > l.pendingInspectQty) {
    return `안 센 수량 ${l.pendingInspectQty} 개를 넘습니다.`
  }
  if (Number(d.rejected) > 0 && !d.reason) {
    return '거부 사유를 고르세요.'
  }
  return ''
}

const anyError = computed(() => (target.value?.lines ?? []).some((l) => lineError(l)))
const valid = computed(() => dirtyLines.value.length > 0 && !anyError.value && !busy.value)

const totalPassed = computed(() =>
  dirtyLines.value.reduce((s, l) => s + Number(draft[l.lineSeq].passed || 0), 0),
)
const totalRejected = computed(() =>
  dirtyLines.value.reduce((s, l) => s + Number(draft[l.lineSeq].rejected || 0), 0),
)

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const { inbound, warning } = await inboundApi.inspect(
      target.value.inboundSeq,
      dirtyLines.value.map((l) => ({
        lineSeq: l.lineSeq,
        passedQty: Number(draft[l.lineSeq].passed || 0),
        rejectedQty: Number(draft[l.lineSeq].rejected || 0),
        reasonCode: draft[l.lineSeq].reason || null,
      })),
    )
    toast.success(
      `${inbound.inboundNo} 검수 — 합격 ${totalPassed.value} / 거부 ${totalRejected.value}`,
    )
    if (warning) toast.warn(warning)
    // 남은 수량이 있으면 화면을 닫지 않는다. 분할 검수는 이어서 하는 일이다.
    if (inbound.lines.some((l) => l.pendingInspectQty > 0)) {
      await openInspect(inbound)
    } else {
      target.value = null
    }
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).slice(0, 10) : '-')

const canInspect = computed(() => session.can('INB_INSPECT', 'C'))
const denyReason = computed(() => session.denyReason('INB_INSPECT', 'C'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">입고검수</h1>
        <p class="page-desc">
          세어 보고 <strong>받아들일 것과 못 받을 것</strong>을 가릅니다. 합격은 기입고로
          쌓이고 발주 잔량을 줄입니다. <strong>거부는 재고가 되지 않습니다</strong> —
          받은 적 없는 물건이라 공급처와 처리를 정해야 합니다. 한 번에 다 세지 않아도
          됩니다. <strong>여기서도 재고는 늘지 않습니다.</strong>
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
        <span class="summary-label">검수 대기</span>
        <strong class="summary-value">{{ num(total) }}</strong>
      </div>
      <div class="summary-item" :class="{ alarm: pendingCount > 0 }">
        <span class="summary-label">안 센 건</span>
        <strong class="summary-value">{{ num(pendingCount) }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">거부 수량</span>
        <strong class="summary-value">{{ num(rejectedCount) }}</strong>
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
        empty-text="검수할 입고가 없습니다. 입하를 먼저 등록하세요."
        @row-click="openInspect"
      >
        <template #cell-_where="{ row }">
          {{ row.plantName }}
          <div class="small dim">{{ row.warehouseName }}</div>
        </template>

        <template #cell-arrivedAt="{ row, value }">
          {{ dt(value) }}
          <div v-if="row.vehicleNo" class="small dim">{{ row.vehicleNo }}</div>
        </template>

        <!-- 내린 개수와 센 개수를 한 칸에. 떼어 놓으면 '얼마나 남았나' 를 눈으로 빼야 한다. -->
        <template #cell-_qty="{ row }">
          <strong>{{ num(row.totalArrivedQty) }}</strong>
          <span class="dim"> / </span>
          <strong :class="row.totalReceivedQty ? 'ok' : 'dim'">
            {{ num(row.totalReceivedQty) }}
          </strong>
          <div v-if="row.totalRejectedQty" class="small warn">거부 {{ row.totalRejectedQty }}</div>
        </template>

        <template #cell-inboundStatus="{ value }">
          <CodeBadge group="INBOUND_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              class="btn btn-sm btn-primary"
              :disabled="!canInspect"
              :title="denyReason ?? '검수'"
              @click.stop="openInspect(row)"
            >
              검수
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ── 검수 입력 ────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      title="입고검수"
      :subtitle="`${target.inboundNo} · ${target.supplierName ?? '공급처 없음'} → ${target.warehouseName}`"
      size="wide"
      @close="target = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="detail-head">
        <CodeBadge group="INBOUND_STATUS" :code="target.inboundStatus" />
        <span>예정 <strong>{{ num(target.totalPlannedQty) }}</strong></span>
        <span>입하 <strong>{{ num(target.totalArrivedQty) }}</strong></span>
        <span>
          기입고 <strong class="ok">{{ num(target.totalReceivedQty) }}</strong>
          <span v-if="target.totalRejectedQty" class="warn">
            · 거부 {{ num(target.totalRejectedQty) }}
          </span>
        </span>
        <span v-if="target.vehicleNo" class="dim">차량 {{ target.vehicleNo }}</span>
      </div>

      <div v-if="target.overQty > 0" class="alert mt-1" :class="target.needsOverApproval ? 'alert-warn' : 'alert-info'">
        <span class="alert-icon">{{ target.needsOverApproval ? '⚠' : 'ℹ' }}</span>
        <span v-if="target.needsOverApproval">
          예정보다 <strong>{{ target.overQty }}개</strong> 많이 받았습니다 (허용
          {{ target.allowedOverQty }}개). <strong>초과입고 승인</strong>을 받아야 입고를
          완료할 수 있습니다.
        </span>
        <span v-else>
          예정보다 {{ target.overQty }}개 많지만 공급처 허용 오차({{ target.allowedOverQty }}개)
          안이라 그대로 받습니다.
        </span>
      </div>

      <div class="lines-head">
        <strong>
          금회 검수 — 합격 {{ num(totalPassed) }} / 거부 {{ num(totalRejected) }}
        </strong>
        <span class="small dim">안 센 수량으로 채워 뒀습니다. 다른 품목만 고치세요.</span>
      </div>

      <table class="table lines">
        <thead>
          <tr>
            <th style="width: 32px" class="right">#</th>
            <th style="width: 160px">SKU</th>
            <th style="width: 64px" class="right">예정</th>
            <th style="width: 64px" class="right">입하</th>
            <th style="width: 64px" class="right">안 셈</th>
            <th style="width: 84px">합격</th>
            <th style="width: 84px">거부</th>
            <th style="width: 130px">거부 사유</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in target.lines" :key="l.lineSeq" :class="{ done: l.inspectDone }">
            <td class="num">{{ l.lineNo }}</td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td class="num">{{ num(l.plannedQty) }}</td>
            <td class="num">{{ num(l.arrivedQty) }}</td>
            <td class="num">
              <strong :class="l.pendingInspectQty ? 'warn' : 'dim'">
                {{ num(l.pendingInspectQty) }}
              </strong>
            </td>
            <td>
              <input
                v-model.number="draft[l.lineSeq].passed"
                type="number"
                class="input"
                min="0"
                :disabled="l.inspectDone"
              />
            </td>
            <td>
              <input
                v-model.number="draft[l.lineSeq].rejected"
                type="number"
                class="input"
                min="0"
                :disabled="l.inspectDone"
              />
            </td>
            <!-- 거부가 있으면 사유가 필수다. 사유 없는 거부는 나중에 아무것도 증명하지 못한다. -->
            <td>
              <select
                v-model="draft[l.lineSeq].reason"
                class="select"
                :disabled="l.inspectDone || !Number(draft[l.lineSeq].rejected)"
              >
                <option value="">선택</option>
                <option v-for="o in codeOptions('REASON_INSPECT')" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </td>
          </tr>
          <tr v-for="l in target.lines.filter((x) => lineError(x))" :key="`e${l.lineSeq}`">
            <td colspan="8" class="small danger">{{ l.skuId }} — {{ lineError(l) }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 회차를 그대로 보여 준다. 합계만 주면 "2회차에 뭐가 있었지" 를 답할 수 없다. -->
      <template v-if="target.inspects.length">
        <div class="lines-head">
          <strong>지난 검수 {{ target.inspects.length }} 회</strong>
          <span class="small dim">덮어쓰지 않고 쌓입니다.</span>
        </div>
        <table class="table lines">
          <thead>
            <tr>
              <th style="width: 56px" class="right">회차</th>
              <th style="width: 160px">SKU</th>
              <th style="width: 70px" class="right">합격</th>
              <th style="width: 70px" class="right">거부</th>
              <th style="width: 110px">사유</th>
              <th style="width: 90px">검수자</th>
              <th style="width: 110px">시각</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in target.inspects" :key="r.inspectSeq">
              <td class="num">{{ r.roundNo }}</td>
              <td><span class="code">{{ r.skuId }}</span></td>
              <td class="num">{{ num(r.passedQty) }}</td>
              <td class="num">
                <span :class="r.hasRejected ? 'warn' : 'dim'">{{ num(r.rejectedQty) }}</span>
              </td>
              <td class="small">{{ r.reasonName ?? '-' }}</td>
              <td class="small">{{ r.inspectedByName ?? r.inspectedBy }}</td>
              <td class="small dim">{{ String(r.inspectedAt ?? '').replace('T', ' ').slice(0, 16) }}</td>
            </tr>
          </tbody>
        </table>
      </template>

      <template #footer>
        <span class="left small dim">
          검수해도 재고는 늘지 않습니다. 적치까지 끝나야 팔 수 있는 재고가 됩니다.
        </span>
        <button class="btn" :disabled="busy" @click="target = null">닫기</button>
        <button class="btn btn-primary" :disabled="!valid || !canInspect" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          검수 기록
        </button>
      </template>
    </ModalDialog>
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
.summary-item.alarm {
  border-color: var(--c-amber, #b45309);
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
.lines .input,
.lines .select {
  min-height: 28px;
  padding: 3px 6px;
  font-size: 13px;
  width: 100%;
}
.done td {
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
.warn {
  color: var(--c-amber, #b45309);
}
</style>
