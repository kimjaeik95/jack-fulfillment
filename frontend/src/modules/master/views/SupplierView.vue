<script setup>
/**
 * 공급처 관리 (MST-PG-012).
 *
 * 물건을 사 오는 상대다. 구매오더(5차)와 입고(6차)가 이 기준정보를 가리킨다.
 *
 * 이 화면에서 가장 중요한 정보는 '지금 발주를 낼 수 있는 상대인가' 다.
 * 거래상태와 사용여부 둘 다 봐야 알 수 있어서 서버가 tradable 로 판단해
 * 내려보낸다 (MST-010).
 *
 * 사업자등록번호 중복은 막지 않는다. 같은 사업자가 사업부별로 코드를 따로
 * 쓰는 경우가 실제로 있어서, 막으면 정당한 등록이 거부된다. 대신 알린다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as supplierApi from '@/api/supplier.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import DetailDialog from '@/components/DetailDialog.vue'

const session = useSessionStore()

const loadError = ref('')
const loading = ref(false)
const rows = ref([])

const filters = reactive({ keyword: '', status: '', payTerm: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', status: '', payTerm: '', useYn: '' })
}

async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await supplierApi.list({ ...filters, size: 0 })
    rows.value = data.rows
  } catch (e) {
    rows.value = []
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)

/*
 * 상세 보기 — 목록에 없는 칸(주소 · 이메일 · 대표자 · 비고)이 여기 있다.
 * 초과입고 허용률은 목록에도 있지만 숫자만 보인다. 여기서는 0 이 무슨 뜻인지
 * (예정수량을 1개라도 넘으면 전부 승인 대상) 까지 적어 준다.
 */
const detail = ref(null)
const detailFields = computed(() => {
  const d = detail.value
  if (!d) return []
  const rate = Number(d.overReceiptRate) || 0
  return [
    { label: '공급처코드', value: d.supplierId, mono: true },
    { label: '공급처명', value: d.supplierName },
    { label: '사업자등록번호', value: d.bizRegNo, mono: true },
    { label: '대표자', value: d.ceoName },
    { label: '담당자', value: d.managerName },
    { label: '연락처', value: d.phone, mono: true },
    { label: '이메일', value: d.email },
    { label: '우편번호', value: d.zipCode, mono: true },
    { label: '주소', value: d.address, span: true },
    { label: '결제조건', slot: 'payTerm' },
    { label: '초과입고 허용', value: rate ? `${rate}%` : '0% (예정수량 초과 시 전부 승인)' },
    { label: '거래상태', slot: 'status' },
    { label: '사용', slot: 'useYn' },
    { label: '비고', value: d.remark, span: true },
  ]
})

const columns = [
  { key: 'supplierId', label: '공급처코드', width: '110px', sortable: true, cls: 'code' },
  { key: 'supplierName', label: '공급처명', width: '160px', sortable: true },
  { key: 'bizRegNo', label: '사업자등록번호', width: '130px', cls: 'code' },
  { key: 'managerName', label: '담당자', width: '90px' },
  { key: 'phone', label: '연락처', width: '130px' },
  { key: 'payTerm', label: '결제조건', width: '96px', align: 'center', sortable: true },
  { key: 'overReceiptRate', label: '초과입고', width: '80px', align: 'right' },
  { key: 'status', label: '거래상태', width: '96px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_SUPPLIER',
  pk: 'supplierId',
  label: '공급처',
  nameOf: (s) => `${s.supplierName} (${s.supplierId})`,
  api: {
    create: (payload) => supplierApi.create(payload),
    update: (supplierId, payload) => supplierApi.update(supplierId, payload),
    remove: (supplierId) => supplierApi.remove(supplierId, '공급처 삭제'),
  },
  async afterChange() {
    await fetchList()
  },
  blank: () => ({
    supplierId: '',
    supplierName: '',
    bizRegNo: '',
    ceoName: '',
    managerName: '',
    phone: '',
    email: '',
    zipCode: '',
    address: '',
    status: 'ACTIVE',
    payTerm: '',
    overReceiptRate: 0,
    remark: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    supplierId: row.supplierId,
    supplierName: row.supplierName,
    bizRegNo: row.bizRegNo ?? '',
    ceoName: row.ceoName ?? '',
    managerName: row.managerName ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    zipCode: row.zipCode ?? '',
    address: row.address ?? '',
    status: row.status,
    payTerm: row.payTerm ?? '',
    overReceiptRate: row.overReceiptRate ?? 0,
    remark: row.remark ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    bizRegNo: f.bizRegNo || null,
    ceoName: f.ceoName || null,
    managerName: f.managerName || null,
    phone: f.phone || null,
    email: f.email || null,
    zipCode: f.zipCode || null,
    address: f.address || null,
    payTerm: f.payTerm || null,
    remark: f.remark || null,
    overReceiptRate: Number(f.overReceiptRate) || 0,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f) {
    const e = {}
    if (!f.supplierId?.trim()) e.supplierId = '공급처코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9-]{1,29}$/.test(f.supplierId))
      e.supplierId = '영문 대문자·숫자·하이픈 2~30자. 예) SUP-001'
    if (!f.supplierName?.trim()) e.supplierName = '공급처명은 필수입니다.'
    if (!f.status) e.status = '거래상태를 선택하세요.'
    if (f.bizRegNo && !/^\d{3}-\d{2}-\d{5}$/.test(f.bizRegNo))
      e.bizRegNo = '000-00-00000 형식으로 입력하세요.'
    if (f.zipCode && !/^\d{5}$/.test(f.zipCode)) e.zipCode = '숫자 5자리로 입력하세요.'
    if (f.phone && !/^[0-9-+() ]{7,30}$/.test(f.phone)) e.phone = '연락처 형식이 올바르지 않습니다.'
    const rate = Number(f.overReceiptRate)
    if (Number.isNaN(rate) || rate < 0 || rate > 100)
      e.overReceiptRate = '0~100 사이로 입력하세요.'
    return e
  },
})

/**
 * 거래불가 상태 안내.
 *
 * 저장하기 전에 보여 준다. 거래상태는 목록에서 보이는 값이 아니라 발주가
 * 되는지를 가르는 값이다 (MST-010).
 */
const notTradableNotice = computed(() => {
  if (form.value.status === 'ACTIVE' && form.value.useYn === 'Y') return ''
  return '거래중이 아닌 상태입니다. 이 공급처로는 신규 발주를 낼 수 없습니다. 진행 중인 구매오더와 입고는 그대로 남습니다.'
})

const readDenyReason = computed(() => session.denyReason('MST_SUPPLIER', 'R'))

/** 거래는 가능한데 사업자등록번호가 없는 곳 — 세금계산서를 끊을 수 없다 */
const missingBizNo = computed(() =>
  rows.value.filter((s) => s.tradable && !s.bizRegNo),
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">공급처 관리</h1>
        <p class="page-desc">
          물건을 사 오는 상대입니다. <strong>거래상태가 '거래중'이 아니면 신규 발주를 낼 수 없습니다.</strong>
          공급처코드는 등록 후 바꿀 수 없습니다 — 구매오더와 입고가 코드로 공급처를 부릅니다.
          사업자등록번호가 겹쳐도 막지 않습니다. 같은 사업자가 사업부별로 코드를 따로 쓰는 경우가 있습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '공급처 등록'"
          @click="openCreate()"
        >
          + 공급처 등록
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <div v-if="missingBizNo.length" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>
        거래 중인데 사업자등록번호가 없는 공급처가 {{ missingBizNo.length }}곳 있습니다 —
        <strong>{{ missingBizNo.map((s) => s.supplierName).join(', ') }}</strong>.
        세금계산서를 받으려면 등록해야 합니다.
      </span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="코드 / 공급처명 / 사업자번호 / 담당자"
          @keyup.enter="fetchList()"
        />
        <FormField
          v-model="filters.status"
          label="거래상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('PARTNER_STATUS')"
          @change="fetchList()"
        />
        <FormField
          v-model="filters.payTerm"
          label="결제조건"
          type="select"
          empty-option="전체"
          :options="codeOptions('PAY_TERM')"
          @change="fetchList()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="fetchList()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
          <button class="btn" @click="resetFilters(); fetchList()">초기화</button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="supplierId"
        :muted-when="(s) => !s.tradable"
        empty-text="조건에 맞는 공급처가 없습니다."
      >
        <!-- 공급처명을 누르면 상세가 열린다 (주소 · 이메일 · 비고) -->
        <template #cell-supplierName="{ row, value }">
          <button class="link-cell" @click="detail = row">{{ value }}</button>
        </template>

        <template #cell-bizRegNo="{ value }">
          <span :class="{ dim: !value }">{{ value || '미등록' }}</span>
        </template>

        <template #cell-payTerm="{ value }">
          <CodeBadge v-if="value" group="PAY_TERM" :code="value" />
          <span v-else class="dim">-</span>
        </template>

        <!-- 초과입고 허용률 0 이면 예정수량을 넘는 입고가 전부 승인 대상이다 -->
        <template #cell-overReceiptRate="{ value }">
          <span :class="{ dim: !Number(value) }">{{ Number(value) || 0 }}%</span>
        </template>

        <template #cell-status="{ row, value }">
          <CodeBadge group="PARTNER_STATUS" :code="value" />
          <span v-if="!row.tradable && row.useYn !== 'Y'" class="small dim"> · 미사용</span>
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              class="btn btn-sm"
              :disabled="!canUpdate"
              :title="updateDenyReason ?? '수정'"
              @click="openEdit(row)"
            >
              수정
            </button>
            <button
              class="btn btn-sm btn-danger"
              :disabled="!canDelete"
              :title="deleteDenyReason ?? '삭제'"
              @click="confirmDelete(row)"
            >
              삭제
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <DetailDialog
      v-if="detail"
      title="공급처 상세"
      :subtitle="detail.supplierName + ' · ' + detail.supplierId"
      :fields="detailFields"
      :can-edit="canUpdate"
      :edit-deny-reason="updateDenyReason"
      @edit="openEdit(detail); detail = null"
      @close="detail = null"
    >
      <template #payTerm>
        <CodeBadge group="PAY_TERM" :code="detail.payTerm" />
      </template>
      <template #status>
        <CodeBadge group="PARTNER_STATUS" :code="detail.status" />
      </template>
      <template #useYn>
        <CodeBadge group="USE_YN" :code="detail.useYn" />
      </template>
    </DetailDialog>

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '공급처 등록' : '공급처 수정'"
      :subtitle="mode === 'edit' ? form.supplierId : '공급처코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.supplierId"
          label="공급처코드"
          required
          mono
          placeholder="SUP-001"
          :disabled="mode === 'edit'"
          :error="errors.supplierId"
          help="구매오더와 입고가 이 코드로 공급처를 부릅니다."
        />
        <FormField
          v-model="form.supplierName"
          label="공급처명"
          required
          placeholder="대한섬유"
          :error="errors.supplierName"
        />
        <FormField
          v-model="form.bizRegNo"
          label="사업자등록번호"
          mono
          placeholder="000-00-00000"
          :error="errors.bizRegNo"
          help="겹쳐도 막지 않습니다. 저장 후 알려 드립니다."
        />
        <FormField v-model="form.ceoName" label="대표자명" placeholder="김대한" />
        <FormField v-model="form.managerName" label="담당자" placeholder="박과장" />
        <FormField
          v-model="form.phone"
          label="연락처"
          placeholder="031-1234-5678"
          :error="errors.phone"
        />
        <FormField v-model="form.email" label="이메일" placeholder="park@example.co.kr" />
        <FormField
          v-model="form.zipCode"
          label="우편번호"
          placeholder="10393"
          :error="errors.zipCode"
        />
        <FormField v-model="form.address" label="주소" span placeholder="경기도 고양시 …" />
        <FormField
          v-model="form.status"
          label="거래상태"
          type="select"
          required
          :options="codeOptions('PARTNER_STATUS')"
          :error="errors.status"
          help="거래중이 아니면 신규 발주를 낼 수 없습니다."
        />
        <FormField
          v-model="form.payTerm"
          label="결제조건"
          type="select"
          empty-option="선택 안 함"
          :options="codeOptions('PAY_TERM')"
        />
        <FormField
          v-model="form.overReceiptRate"
          label="초과입고 허용률 (%)"
          type="number"
          :error="errors.overReceiptRate"
          help="0이면 예정수량을 넘는 입고가 전부 승인 대상입니다."
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.remark" label="비고" span placeholder="주거래 · 취급 품목 등" />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="notTradableNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ notTradableNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">코드·공급처명 중복은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="공급처 삭제"
      :message="deleteMessage"
      detail="구매오더와 입고 이력이 생긴 뒤에는 삭제가 막힙니다. 거래만 멈추려면 거래상태를 '거래중지'나 '거래종료'로 바꾸세요 — 과거 발주 이력이 공급처를 가리키고 있습니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>
