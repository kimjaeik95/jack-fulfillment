<script setup>
/**
 * 송장 · 배송 현황 (DLV-PG-002).
 *
 * CS 가 전화를 받는 자리다. 물음은 늘 하나다 — <b>"이 주문 어디까지 갔어요"</b>.
 * 그 답을 내려면 송장 · 박스 · 지시 · 주문 · 수령인 다섯 표를 돌아야 해서,
 * 화면을 다섯 번 여는 대신 한 줄에 이어 붙였다.
 *
 * <b>상태는 사람이 찍는다.</b> 택배사에서 받아오는 인터페이스(INT-IF-004)가
 * 개발취소라 자동으로 들어오는 길이 없다. 그래서 택배사 조회 화면을 옆에
 * 띄워 두고 옮겨 적는 것이 실제 동선이고, 화면도 그에 맞췄다 —
 * <b>여러 장을 골라 한 번에 찍는다.</b> 한 건씩 누르게 하면 열두 번 왕복한다.
 *
 * 재고는 건드리지 않는다. 물건은 출고확정 때 이미 보유에서 빠졌다 (P-01).
 * 여기서 하는 일은 어디까지 갔는지를 적는 것뿐이고, 그 값으로 운송중 재고가
 * 계산된다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as deliveryApi from '@/api/delivery.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import ChainButton from '@/components/ChainButton.vue'

const session = useSessionStore()
const hierarchy = useHierarchyStore()

const readDenyReason = computed(() => session.denyReason('DLV_TRACK', 'R'))
const writeDenyReason = computed(() => session.denyReason('DLV_TRACK', 'C'))

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = deliveryApi.PAGE_SIZE

const couriers = ref([])

const filters = reactive({
  keyword: '',
  courierCode: '',
  deliveryStatus: '',
  plantId: '',
  fromDate: '',
  toDate: '',
  delayedOnly: '',
  inTransitOnly: 'Y',
})

/** 고른 송장들. 상태를 한 번에 찍으려고 모은다 */
const picked = ref(new Set())
const pickedRows = computed(() => rows.value.filter((r) => picked.value.has(r.waybillSeq)))

function toggle(row) {
  const s = new Set(picked.value)
  if (s.has(row.waybillSeq)) s.delete(row.waybillSeq)
  else s.add(row.waybillSeq)
  picked.value = s
}

/** 지금 보이는 것 중 아직 길 위에 있는 것만 고른다 — 이미 받은 건 찍을 게 없다 */
function pickAllInTransit() {
  picked.value = new Set(rows.value.filter((r) => r.inTransit).map((r) => r.waybillSeq))
}

function clearPicked() {
  picked.value = new Set()
}

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await deliveryApi.deliveries({
      keyword: filters.keyword.trim() || null,
      courierCode: filters.courierCode || null,
      deliveryStatus: filters.deliveryStatus || null,
      plantId: filters.plantId || null,
      fromDate: filters.fromDate || null,
      toDate: filters.toDate || null,
      delayedOnly: filters.delayedOnly || null,
      inTransitOnly: filters.inTransitOnly || null,
      page: page.value,
      size,
    })
    rows.value = data.rows
    total.value = data.total
    // 페이지가 바뀌면 고른 것을 비운다. 안 보이는 줄이 선택된 채 남아 있으면
    // '열두 건 찍었는데 왜 스무 건이 바뀌었지' 가 된다.
    clearPicked()
  } catch (e) {
    loadError.value = e.message
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function search() {
  page.value = 1
  await fetchPage()
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

onMounted(async () => {
  await hierarchy.loadPlants(false)
  try {
    couriers.value = (await deliveryApi.couriers({ useYn: 'Y' })).rows
  } catch {
    couriers.value = []
  }
  await fetchPage()
})

const courierOptions = computed(() =>
  couriers.value.map((c) => ({ value: c.courierCode, label: c.courierName })),
)

/* ── 상태 찍기 ─────────────────────────────────────────────── */

const statusDlg = ref(false)
const statusForm = reactive({ deliveryStatus: '', reasonCode: '', remark: '', occurredAt: '' })
const statusBusy = ref(false)
const statusError = ref('')
const statusResult = ref(null)

/** 실패 · 반송 · 분실은 사유가 있어야 한다. 왜 못 갔는지가 이 기록의 값이다 */
const needsReason = computed(() =>
  ['FAILED', 'RETURNING', 'LOST'].includes(statusForm.deliveryStatus),
)

function openStatus() {
  Object.assign(statusForm, {
    deliveryStatus: '',
    reasonCode: '',
    remark: '',
    occurredAt: '',
  })
  statusError.value = ''
  statusResult.value = null
  statusDlg.value = true
}

async function submitStatus() {
  if (!statusForm.deliveryStatus) {
    statusError.value = '배송상태를 고르세요.'
    return
  }
  if (needsReason.value && !statusForm.reasonCode) {
    statusError.value = '실패 · 반송 · 분실은 사유를 골라야 합니다.'
    return
  }
  statusBusy.value = true
  statusError.value = ''
  try {
    statusResult.value = await deliveryApi.updateDeliveryStatus({
      waybillNos: pickedRows.value.map((r) => r.waybillNo),
      deliveryStatus: statusForm.deliveryStatus,
      reasonCode: statusForm.reasonCode || null,
      remark: statusForm.remark || null,
      occurredAt: statusForm.occurredAt || null,
    })
    await fetchPage()
  } catch (e) {
    statusError.value = e.message
  } finally {
    statusBusy.value = false
  }
}

/* ── 배송이력 ─────────────────────────────────────────────── */

const eventDlg = ref(null)
const events = ref([])
const eventsLoading = ref(false)

async function openEvents(row) {
  eventDlg.value = row
  eventsLoading.value = true
  try {
    events.value = await deliveryApi.deliveryEvents(row.waybillSeq)
  } catch {
    events.value = []
  } finally {
    eventsLoading.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const columns = [
  { key: '_pick', label: '', width: '40px', align: 'center' },
  { key: 'waybillNo', label: '송장', width: '170px' },
  { key: 'deliveryStatus', label: '배송상태', width: '150px' },
  { key: 'orderNo', label: '주문 / 수령인', width: '190px' },
  { key: 'totalPackedQty', label: '수량', width: '60px', align: 'right' },
  { key: 'daysInTransit', label: '경과', width: '66px', align: 'right' },
  { key: 'handedOverAt', label: '인계', width: '124px' },
  { key: '_act', label: '', width: '118px', align: 'right' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">송장 · 배송 현황</h1>
        <p class="page-desc">
          택배사에 넘긴 박스가 어디까지 갔는지 봅니다. 택배사 연동이 없어
          <strong>상태는 사람이 찍습니다</strong> — 택배사 조회 화면을 보고
          여러 장을 골라 한 번에 바꾸세요. 재고는 출고확정 때 이미 빠졌으므로
          여기서는 바뀌지 않습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!pickedRows.length || !!writeDenyReason"
          :title="writeDenyReason ?? '고른 송장의 배송상태를 바꿉니다'"
          @click="openStatus()"
        >
          배송상태 찍기{{ pickedRows.length ? ` (${pickedRows.length})` : '' }}
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="송장번호 / 지시번호 / 주문번호 / 수령인 / 연락처"
          @enter="search()"
        />
        <FormField
          v-model="filters.courierCode"
          label="택배사"
          type="select"
          empty-option="전체"
          :options="courierOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.deliveryStatus"
          label="배송상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('DELIVERY_STATUS')"
          @change="search()"
        />
        <FormField
          v-model="filters.plantId"
          label="센터"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="search()"
        />
        <!--
          기본은 아직 길 위에 있는 것만. 이미 받은 것까지 섞으면 챙길 것이
          묻힌다 — 여기 오는 이유는 '안 간 것' 을 찾으려는 것이다.
        -->
        <FormField
          v-model="filters.inTransitOnly"
          label="범위"
          type="select"
          empty-option="전체 보기"
          :options="[{ value: 'Y', label: '길 위에 있는 것만' }]"
          @change="search()"
        />
        <FormField
          v-model="filters.delayedOnly"
          label="지연"
          type="select"
          empty-option="전체"
          :options="[{ value: 'Y', label: '3일 넘은 것만' }]"
          @change="search()"
        />
        <FormField v-model="filters.fromDate" label="인계일 시작" type="date" @change="search()" />
        <FormField v-model="filters.toDate" label="끝" type="date" @change="search()" />
        <div class="toolbar-actions">
          <button class="btn" :disabled="loading" @click="pickAllInTransit()">
            길 위의 것 모두 고르기
          </button>
          <button class="btn" :disabled="!pickedRows.length" @click="clearPicked()">
            선택 해제
          </button>
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="waybillSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        :muted-when="(r) => r.canceled"
        empty-text="조건에 맞는 송장이 없습니다."
      >
        <template #cell-_pick="{ row }">
          <input
            type="checkbox"
            :checked="picked.has(row.waybillSeq)"
            :disabled="row.canceled"
            :title="row.canceled ? '취소된 송장에는 상태를 찍을 수 없습니다' : ''"
            @change="toggle(row)"
          />
        </template>

        <template #cell-waybillNo="{ row, value }">
          <!-- 택배사에 조회주소를 등록해 두면 바로 열 수 있다 -->
          <a
            v-if="row.trackingUrl"
            class="code"
            :href="row.trackingUrl"
            target="_blank"
            rel="noopener noreferrer"
          >{{ value }}</a>
          <span v-else class="code">{{ value }}</span>
          <div class="small dim">
            {{ row.courierName }} · {{ row.outboundNo }} · {{ row.boxNo }}번 박스
          </div>
          <div v-if="row.canceled" class="small danger">취소됨 — {{ row.cancelReason }}</div>
          <div v-else-if="row.redeliveryOfNo" class="small warn">
            재배송 (원 송장 {{ row.redeliveryOfNo }})
          </div>
        </template>

        <template #cell-deliveryStatus="{ row, value }">
          <CodeBadge group="DELIVERY_STATUS" :code="value" />
          <div v-if="row.lastReasonName" class="small danger">{{ row.lastReasonName }}</div>
          <div v-if="row.statusAt" class="small dim">
            {{ dt(row.statusAt) }}{{ row.statusByName ? ` · ${row.statusByName}` : '' }}
          </div>
        </template>

        <template #cell-orderNo="{ row, value }">
          <span class="code">{{ value ?? '-' }}</span>
          <div class="small dim">{{ row.receiverName }} · {{ row.receiverPhone }}</div>
          <div class="small dim">{{ row.address }} {{ row.addressDetail }}</div>
        </template>

        <template #cell-totalPackedQty="{ value }">{{ num(value) }}</template>

        <!--
          경과일은 인계 시각부터다. 송장을 뽑은 시각이 아니라 택배사가 실어 간
          시각부터가 배송이다. 아직 안 넘겼으면 '늦은' 것이 아니라 '안 나간'
          것이라 출고 쪽이 볼 일이다.
        -->
        <template #cell-daysInTransit="{ row, value }">
          <span v-if="value === null || value === undefined" class="dim">-</span>
          <span v-else :class="row.inTransit && value >= 3 ? 'danger' : ''">{{ value }}일</span>
        </template>

        <template #cell-handedOverAt="{ value }">
          <span v-if="value" class="small">{{ dt(value) }}</span>
          <span v-else class="small dim">아직 안 넘김</span>
        </template>

        <!--
          '배송이력' 은 이 송장의 상태 변화(배송중 → 배달출발 → 실패)고,
          '진행이력' 은 이 건이 주문부터 지나온 문서다. 둘은 다른 것을 답한다 —
          배송이 왜 늦나 와 이 주문이 어디까지 갔나.
        -->
        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <ChainButton kind="WAYBILL" :seq="row.waybillSeq" :no="row.waybillNo" />
            <button class="btn btn-sm" @click="openEvents(row)">배송이력</button>
          </div>
        </template>
      </DataTable>

      <div class="pager">
        <span class="small dim">
          총 {{ num(total) }}건 · {{ page }} / {{ totalPages }} 페이지
          <template v-if="pickedRows.length"> · {{ pickedRows.length }}건 선택</template>
        </span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">이전</button>
          <button class="btn btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">
            다음
          </button>
        </div>
      </div>
    </div>

    <!-- ── 배송상태 찍기 ────────────────────────────────────── -->
    <ModalDialog
      v-if="statusDlg"
      title="배송상태 찍기"
      :subtitle="`${pickedRows.length}건`"
      @close="statusDlg = false"
    >
      <div v-if="statusError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ statusError }}</span>
      </div>

      <div v-if="statusResult" class="mb-2">
        <div class="alert alert-ok">
          <span class="alert-icon">✅</span>
          <span>{{ statusResult.done.length }}건을 바꿨습니다.</span>
        </div>
        <div v-if="statusResult.failed.length" class="alert alert-warn mt-1">
          <span class="alert-icon">⚠</span>
          <span>
            못 바꾼 {{ statusResult.failed.length }}건:
            <div v-for="(f, i) in statusResult.failed" :key="i" class="small">{{ f }}</div>
          </span>
        </div>
      </div>

      <template v-else>
        <div class="picked-list small dim mb-2">
          {{ pickedRows.map((r) => r.waybillNo).join(', ') }}
        </div>

        <div class="form-grid">
          <FormField
            v-model="statusForm.deliveryStatus"
            label="배송상태"
            type="select"
            required
            empty-option="고르세요"
            :options="codeOptions('DELIVERY_STATUS')"
          />
          <FormField
            v-if="needsReason"
            v-model="statusForm.reasonCode"
            label="사유"
            type="select"
            required
            empty-option="고르세요"
            :options="codeOptions('REASON_DLV_FAIL')"
            help="왜 못 갔는지가 없으면 나중에 고객에게도 택배사에도 설명할 수 없습니다."
          />
          <FormField
            v-model="statusForm.occurredAt"
            label="발생 시각"
            type="datetime-local"
            help="비우면 지금으로 봅니다. 어제 부재였던 것을 오늘 적을 때 씁니다."
          />
          <FormField v-model="statusForm.remark" class="span-2" label="비고" type="textarea" />
        </div>
      </template>

      <template #footer>
        <span class="left small dim">
          한 건이 실패해도 나머지는 처리됩니다. 재고는 바뀌지 않습니다.
        </span>
        <button class="btn" :disabled="statusBusy" @click="statusDlg = false">
          {{ statusResult ? '닫기' : '취소' }}
        </button>
        <button
          v-if="!statusResult"
          class="btn btn-primary"
          :disabled="statusBusy"
          @click="submitStatus()"
        >
          <span v-if="statusBusy" class="spinner"></span>
          찍기
        </button>
      </template>
    </ModalDialog>

    <!-- ── 배송이력 ─────────────────────────────────────────── -->
    <ModalDialog
      v-if="eventDlg"
      title="배송이력"
      :subtitle="`${eventDlg.waybillNo} · ${eventDlg.courierName}`"
      @close="eventDlg = null"
    >
      <div v-if="eventsLoading" class="dim">불러오는 중…</div>
      <div v-else-if="!events.length" class="dim">
        아직 아무 사건도 없습니다. 인계를 찍으면 배송중부터 쌓입니다.
      </div>
      <ol v-else class="trail">
        <li v-for="e in events" :key="e.eventSeq">
          <div>
            <CodeBadge group="DELIVERY_STATUS" :code="e.eventStatus" />
            <span v-if="e.reasonName" class="danger small"> {{ e.reasonName }}</span>
          </div>
          <div class="small dim">
            {{ dt(e.occurredAt) }} · {{ e.createdByName ?? e.createdBy }}
            <template v-if="e.source === 'MANUAL'"> (직접 입력)</template>
          </div>
          <div v-if="e.remark" class="small">{{ e.remark }}</div>
        </li>
      </ol>

      <template #footer>
        <span class="left small dim">
          택배사 연동이 없어 모두 사람이 적은 값입니다. 누가 언제 적었는지가 남습니다.
        </span>
        <button class="btn" @click="eventDlg = null">닫기</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.danger {
  color: var(--c-red, #dc2626);
  font-weight: 600;
}
.warn {
  color: var(--c-amber, #b45309);
}
.picked-list {
  max-height: 80px;
  overflow-y: auto;
  word-break: break-all;
}
.trail {
  list-style: none;
  margin: 0;
  padding: 0;
}
.trail li {
  padding: 8px 0 8px 12px;
  border-left: 2px solid var(--line, #e5e7eb);
}
.trail li + li {
  margin-top: 2px;
}
</style>
