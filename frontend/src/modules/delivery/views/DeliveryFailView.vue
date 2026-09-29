<script setup>
/**
 * 배송실패 · 지연 (DLV-PG-003).
 *
 * 못 간 것들을 모아 보고, 다시 보낸다.
 *
 * <b>송장이 아니라 사건을 센다.</b> 같은 송장이 두 번 실패했으면 두 줄이다 —
 * 송장 단위로 묶으면 '또 실패했다' 가 안 보이는데, 두 번 실패한 건은 세 번째를
 * 기대하지 말고 다른 택배사로 보내거나 고객에게 전화할 일이다.
 *
 * <b>재배송은 같은 박스에 새 송장을 붙이는 것</b>이다. 물건이 창고로 돌아왔으면
 * 반품이고 그건 여기가 아니다 (RTN-* 는 개발취소라 아직 길이 없다).
 * 택배사가 같은 번호로 다시 시도하는 경우는 재배송이 아니라 상태만
 * 되돌리면 된다 — 배송현황 화면에서 한다.
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

const session = useSessionStore()
const hierarchy = useHierarchyStore()

const readDenyReason = computed(() => session.denyReason('DLV_FAIL', 'R'))
const writeDenyReason = computed(() => session.denyReason('DLV_FAIL', 'C'))

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = deliveryApi.PAGE_SIZE
const couriers = ref([])

const filters = reactive({
  keyword: '',
  courierCode: '',
  plantId: '',
  fromDate: '',
  toDate: '',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await deliveryApi.failures({
      keyword: filters.keyword.trim() || null,
      courierCode: filters.courierCode || null,
      plantId: filters.plantId || null,
      fromDate: filters.fromDate || null,
      toDate: filters.toDate || null,
      page: page.value,
      size,
    })
    rows.value = data.rows
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function search() {
  page.value = 1
  await fetchPage()
}

async function goPage(n) {
  if (n < 1 || n === page.value) return
  if (n > page.value && rows.value.length < size) return
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

/* ── 재배송 ───────────────────────────────────────────────── */

const redlvDlg = ref(null)
const redlvForm = reactive({ courierCode: '', waybillNo: '', reasonCode: '', remark: '' })
const redlvBusy = ref(false)
const redlvError = ref('')
const redlvDone = ref(null)

function openRedelivery(row) {
  redlvDlg.value = row
  Object.assign(redlvForm, {
    // 실패한 곳을 기본으로 둔다. 바꿀 수 있게 열어 두되, 대개는 같은 곳이
    // 다시 시도한다.
    courierCode: row.courierCode,
    waybillNo: '',
    reasonCode: row.reasonCode ?? '',
    remark: '',
  })
  redlvError.value = ''
  redlvDone.value = null
}

async function submitRedelivery() {
  if (!redlvForm.courierCode) {
    redlvError.value = '택배사를 고르세요.'
    return
  }
  if (!redlvForm.waybillNo.trim()) {
    redlvError.value = '새 송장번호를 입력하세요.'
    return
  }
  if (!redlvForm.reasonCode) {
    redlvError.value = '재배송 사유를 고르세요.'
    return
  }
  redlvBusy.value = true
  redlvError.value = ''
  try {
    redlvDone.value = await deliveryApi.redeliver(redlvDlg.value.waybillSeq, {
      courierCode: redlvForm.courierCode,
      waybillNo: redlvForm.waybillNo.trim(),
      reasonCode: redlvForm.reasonCode,
      remark: redlvForm.remark || null,
    })
    await fetchPage()
  } catch (e) {
    redlvError.value = e.message
  } finally {
    redlvBusy.value = false
  }
}

const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const columns = [
  { key: 'occurredAt', label: '언제', width: '126px' },
  { key: 'eventStatus', label: '무슨 일', width: '110px' },
  { key: 'reasonName', label: '사유', width: '150px' },
  { key: 'waybillNo', label: '송장', width: '180px' },
  { key: 'remark', label: '비고', width: '200px' },
  { key: 'createdByName', label: '적은 사람', width: '100px' },
  { key: '_act', label: '', width: '90px', align: 'right' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">배송실패 · 지연</h1>
        <p class="page-desc">
          못 간 것들을 모아 봅니다. <strong>송장이 아니라 사건을 셉니다</strong> —
          같은 송장이 두 번 실패했으면 두 줄입니다. 두 번 실패한 건은 세 번째를
          기대하지 말고 다른 택배사로 보내거나 고객에게 연락할 일입니다.
        </p>
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
          placeholder="송장번호 / 지시번호"
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
          v-model="filters.plantId"
          label="센터"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="search()"
        />
        <FormField v-model="filters.fromDate" label="발생일 시작" type="date" @change="search()" />
        <FormField v-model="filters.toDate" label="끝" type="date" @change="search()" />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <div v-if="!loading && !rows.length" class="alert alert-ok m-2">
        <span class="alert-icon">✅</span>
        <span>못 간 것이 없습니다. 보낸 것이 다 갔습니다.</span>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="eventSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        empty-text="조건에 맞는 것이 없습니다."
      >
        <template #cell-occurredAt="{ row, value }">
          <span class="small">{{ dt(value) }}</span>
          <div class="small dim">적음 {{ dt(row.createdAt) }}</div>
        </template>

        <template #cell-eventStatus="{ value }">
          <CodeBadge group="DELIVERY_STATUS" :code="value" />
        </template>

        <template #cell-reasonName="{ row, value }">
          <span class="danger">{{ value ?? row.reasonCode ?? '-' }}</span>
        </template>

        <template #cell-waybillNo="{ row, value }">
          <span class="code">{{ value }}</span>
          <div class="small dim">{{ row.courierName }}</div>
        </template>

        <template #cell-remark="{ value }">
          <span v-if="value" class="small">{{ value }}</span>
          <span v-else class="small dim">-</span>
        </template>

        <template #cell-createdByName="{ row, value }">
          <span class="small">{{ value ?? row.createdBy }}</span>
        </template>

        <template #cell-_act="{ row }">
          <button
            class="btn btn-sm"
            :disabled="!!writeDenyReason"
            :title="writeDenyReason ?? '같은 박스에 새 송장을 붙입니다'"
            @click="openRedelivery(row)"
          >
            재배송
          </button>
        </template>
      </DataTable>

      <div class="pager">
        <span class="small dim">{{ page }} 페이지 · {{ rows.length }}건</span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">이전</button>
          <button class="btn btn-sm" :disabled="rows.length < size" @click="goPage(page + 1)">
            다음
          </button>
        </div>
      </div>
    </div>

    <ModalDialog
      v-if="redlvDlg"
      title="재배송"
      :subtitle="`원 송장 ${redlvDlg.waybillNo} · ${redlvDlg.courierName}`"
      @close="redlvDlg = null"
    >
      <div v-if="redlvError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ redlvError }}</span>
      </div>

      <div v-if="redlvDone" class="alert alert-ok">
        <span class="alert-icon">✅</span>
        <span>
          새 송장 <strong>{{ redlvDone.waybillNo }}</strong> 를 붙였습니다.
          원 송장은 취소됐고, 박스는 그대로입니다.
        </span>
      </div>

      <template v-else>
        <div class="alert alert-warn mb-2">
          <span class="alert-icon">⚠</span>
          <span>
            원 송장을 취소하고 <strong>같은 박스에</strong> 새 송장을 붙입니다.
            물건은 창고로 돌아오지 않았으므로 재고는 바뀌지 않습니다 — 운송중 재고에
            그대로 남습니다.
            <br />
            택배사가 <strong>같은 번호로 다시 시도</strong>하는 것이면 여기가 아니라
            배송현황에서 상태만 되돌리세요.
          </span>
        </div>

        <div class="form-grid">
          <FormField
            v-model="redlvForm.courierCode"
            label="택배사"
            type="select"
            required
            empty-option="고르세요"
            :options="courierOptions"
            help="한 곳이 두 번 실패하면 다른 곳으로 보낼 수 있습니다."
          />
          <FormField
            v-model="redlvForm.waybillNo"
            label="새 송장번호"
            required
            mono
            placeholder="택배사에서 받은 번호"
            help="취소된 번호도 다시 쓸 수 없습니다."
          />
          <FormField
            v-model="redlvForm.reasonCode"
            label="재배송 사유"
            type="select"
            required
            empty-option="고르세요"
            :options="codeOptions('REASON_DLV_FAIL')"
          />
          <FormField v-model="redlvForm.remark" class="span-2" label="비고" type="textarea" />
        </div>
      </template>

      <template #footer>
        <span class="left small dim">
          실패 · 반송 · 분실로 적힌 송장만 다시 보낼 수 있습니다.
        </span>
        <button class="btn" :disabled="redlvBusy" @click="redlvDlg = null">
          {{ redlvDone ? '닫기' : '취소' }}
        </button>
        <button
          v-if="!redlvDone"
          class="btn btn-primary"
          :disabled="redlvBusy"
          @click="submitRedelivery()"
        >
          <span v-if="redlvBusy" class="spinner"></span>
          재배송
        </button>
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
</style>
