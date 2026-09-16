<script setup>
/**
 * 초과입고 승인 (INB-PG-004).
 *
 * 시킨 것보다 많이 온 물건을 말없이 받으면 <b>재고와 대금이 함께 틀어진다</b>.
 * 그래서 허용 오차를 넘긴 초과는 사람이 한 번 보고 넘긴다.
 *
 * 허용 오차는 공급처 기준정보가 갖는다. 박스 단위로 오는 물건은 낱개로 딱
 * 맞출 수 없어서, 공급처마다 몇 %까지는 그냥 받기로 미리 정해 둔다 —
 * 그 안이면 이 화면에 오지도 않는다.
 *
 * 검수하는 사람과 승인하는 사람을 권한으로 나눈다 (INB_INSPECT vs
 * INB_APPROVE). 많이 받아 놓고 자기가 승인하면 통제가 아니라 절차다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
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

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', plantId: '' })

/**
 * 승인이 필요한 건만 모은다.
 *
 * 서버에 '승인대기' 상태가 따로 없다. 초과 여부는 수량으로 판정되는 것이라
 * 상태로 들고 있으면 검수할 때마다 상태를 다시 계산해야 하고, 그 계산이
 * 한 번 틀리면 영영 어긋난다. 그래서 진행 중인 것을 받아 여기서 거른다.
 */
async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const [inspecting, putaway] = await Promise.all([
      inboundApi.list({ ...filters, inboundStatus: 'INSPECTING', size: inboundApi.PAGE_SIZE }),
      inboundApi.list({ ...filters, inboundStatus: 'PUTAWAY', size: inboundApi.PAGE_SIZE }),
    ])
    rows.value = [...inspecting.rows, ...putaway.rows].filter((r) => r.needsOverApproval)
  } catch (e) {
    rows.value = []
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

const waiting = computed(() => rows.value.filter((r) => !r.overApproved))
const overTotal = computed(() => waiting.value.reduce((s, r) => s + r.overQty, 0))

const columns = [
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: 'supplierName', label: '공급처', width: '124px' },
  { key: '_where', label: '받은 곳', width: '142px' },
  { key: '_qty', label: '예정 / 받음', width: '116px', align: 'right' },
  { key: '_over', label: '초과 / 허용', width: '110px', align: 'right' },
  { key: 'inboundStatus', label: '상태', width: '84px', align: 'center' },
  { key: '_act', label: '', width: '90px', align: 'right' },
]

const target = ref(null)
const busy = ref(false)
const serverError = ref('')
const reason = ref('')

async function openDetail(row) {
  serverError.value = ''
  reason.value = ''
  try {
    target.value = await inboundApi.detail(row.inboundSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const inbound = await inboundApi.approveOver(target.value.inboundSeq, reason.value || null)
    toast.success(`${inbound.inboundNo} 초과 ${inbound.overQty} 개를 승인했습니다.`)
    target.value = null
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const canApprove = computed(() => session.can('INB_APPROVE', 'A'))
const denyReason = computed(() => session.denyReason('INB_APPROVE', 'A'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">초과입고 승인</h1>
        <p class="page-desc">
          시킨 것보다 많이 온 물건입니다. 말없이 받으면 <strong>재고와 대금이 함께
          틀어집니다.</strong> 공급처마다 정해 둔 허용 오차 안이면 여기 오지 않습니다 —
          그 오차를 넘긴 것만 사람이 한 번 보고 넘깁니다.
          <strong>승인해야 입고를 완료할 수 있습니다.</strong>
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
      <div class="summary-item" :class="{ alarm: waiting.length > 0 }">
        <span class="summary-label">승인 대기</span>
        <strong class="summary-value">{{ num(waiting.length) }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">초과 수량 합</span>
        <strong class="summary-value">{{ num(overTotal) }}</strong>
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
        :muted-when="(r) => r.overApproved"
        empty-text="승인할 초과입고가 없습니다. 허용 오차 안의 초과는 여기 오지 않습니다."
        @row-click="openDetail"
      >
        <template #cell-_where="{ row }">
          {{ row.plantName }}
          <div class="small dim">{{ row.warehouseName }}</div>
        </template>

        <template #cell-_qty="{ row }">
          {{ num(row.totalPlannedQty) }}
          <span class="dim"> / </span>
          <strong class="warn">{{ num(row.totalReceivedQty) }}</strong>
        </template>

        <!-- 초과와 허용을 나란히. 얼마나 넘었는지가 판단의 전부다. -->
        <template #cell-_over="{ row }">
          <strong class="danger">+{{ num(row.overQty) }}</strong>
          <span class="dim"> / {{ num(row.allowedOverQty) }}</span>
        </template>

        <template #cell-inboundStatus="{ value }">
          <CodeBadge group="INBOUND_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              v-if="!row.overApproved"
              class="btn btn-sm btn-primary"
              :disabled="!canApprove"
              :title="denyReason ?? '초과입고 승인'"
              @click.stop="openDetail(row)"
            >
              승인
            </button>
            <span v-else class="small dim">승인됨</span>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ── 승인 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      title="초과입고 승인"
      :subtitle="`${target.inboundNo} · ${target.supplierName ?? '공급처 없음'}`"
      size="wide"
      @close="target = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="detail-head">
        <CodeBadge group="INBOUND_STATUS" :code="target.inboundStatus" />
        <span v-if="target.orderNo">근거 <strong class="code">{{ target.orderNo }}</strong></span>
        <span>예정 <strong>{{ num(target.totalPlannedQty) }}</strong></span>
        <span>받음 <strong class="warn">{{ num(target.totalReceivedQty) }}</strong></span>
        <span>초과 <strong class="danger">+{{ num(target.overQty) }}</strong></span>
        <span class="dim">허용 {{ num(target.allowedOverQty) }}</span>
      </div>

      <div class="alert alert-warn mt-1">
        <span class="alert-icon">⚠</span>
        <span>
          승인하면 이 초과분까지 <strong>입고완료 시 재고가 됩니다.</strong>
          대금도 그만큼 나갑니다 — 공급처와 이야기가 된 건인지 확인하세요.
          완료된 뒤에 잘못이었다면 입고정정으로 되감습니다.
        </span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 32px" class="right">#</th>
            <th style="width: 180px">SKU</th>
            <th style="width: 70px" class="right">예정</th>
            <th style="width: 70px" class="right">입하</th>
            <th style="width: 70px" class="right">받음</th>
            <th style="width: 70px" class="right">초과</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in target.lines" :key="l.lineSeq" :class="{ over: l.over }">
            <td class="num">{{ l.lineNo }}</td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td class="num">{{ num(l.plannedQty) }}</td>
            <td class="num">{{ num(l.arrivedQty) }}</td>
            <td class="num"><strong>{{ num(l.receivedQty) }}</strong></td>
            <td class="num">
              <strong v-if="l.over" class="danger">+{{ num(l.overQty) }}</strong>
              <span v-else class="dim">-</span>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="form-grid mt-2">
        <FormField
          v-model="reason"
          label="승인 사유"
          class="span-2"
          placeholder="예: 박스 단위 포장이라 낱개로 맞출 수 없음"
          help="감사로그에 남습니다. 나중에 '왜 더 받았나' 를 반드시 묻게 됩니다."
        />
      </div>

      <template #footer>
        <span class="left small dim">
          승인해도 재고는 아직 안 늡니다. 적치까지 끝나고 입고완료를 눌러야 늡니다.
        </span>
        <button class="btn" :disabled="busy" @click="target = null">닫기</button>
        <button class="btn btn-primary" :disabled="!canApprove || busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          초과 승인
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
  flex: 1 1 160px;
  padding: 10px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--bg-card, #fff);
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
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.lines tr.over td {
  background: var(--c-amber-soft, #fffbeb);
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
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
