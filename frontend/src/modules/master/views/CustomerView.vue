<script setup>
/**
 * 고객 · 배송지 관리 (MST-PG-013).
 *
 * 센터가 직접 파는 상대다. 채널 주문의 수령인과는 다르다 — 채널 주문(7차)은
 * 배송지를 주문이 직접 들고 있고, 여기 고객은 판매오더(11차)가 가리킨다.
 *
 * 한 화면에서 고객과 배송지를 함께 다룬다. 배송지는 고객 없이 존재하지
 * 않고, "이 고객의 배송지" 를 보려고 화면을 옮기는 것은 불필요한 이동이다.
 * 고객을 고르면 오른쪽에 배송지가 펼쳐진다.
 *
 * 규칙 하나가 이 화면의 전부다 — 고객당 기본 배송지는 하나 (MST-010).
 * 기본을 옮기면 서버가 기존 것을 내리고, 기본을 지우면 남은 것이 승계한다.
 * 사용자에게 "먼저 해제하세요" 를 요구하지 않는다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as customerApi from '@/api/customer.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const session = useSessionStore()
const toast = useToastStore()

const loadError = ref('')
const loading = ref(false)
const rows = ref([])

const filters = reactive({ keyword: '', customerType: '', status: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', customerType: '', status: '', useYn: '' })
}

async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await customerApi.list({ ...filters, size: 0 })
    rows.value = data.rows
    // 고른 고객이 사라졌으면(삭제·필터) 선택을 푼다
    if (selected.value && !rows.value.some((c) => c.customerId === selected.value)) {
      selected.value = ''
      addresses.value = []
    }
  } catch (e) {
    rows.value = []
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)

const columns = [
  { key: 'customerId', label: '고객코드', width: '100px', sortable: true, cls: 'code' },
  { key: 'customerName', label: '고객명', width: '150px', sortable: true },
  { key: 'customerType', label: '유형', width: '76px', align: 'center', sortable: true },
  { key: 'bizRegNo', label: '사업자등록번호', width: '130px', cls: 'code' },
  { key: 'phone', label: '연락처', width: '125px' },
  { key: 'addressCount', label: '배송지', width: '90px', align: 'right' },
  { key: 'status', label: '거래상태', width: '96px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '150px', align: 'right' },
]

/* ── 배송지 ─────────────────────────────────────────────────── */

const selected = ref('')
const addresses = ref([])
const addrLoading = ref(false)

const selectedCustomer = computed(
  () => rows.value.find((c) => c.customerId === selected.value) ?? null,
)

async function openAddresses(customerId) {
  selected.value = customerId
  addrLoading.value = true
  try {
    addresses.value = await customerApi.addresses(customerId)
  } catch (e) {
    addresses.value = []
    toast.error(e.message)
  } finally {
    addrLoading.value = false
  }
}

/* 배송지 등록 · 수정 폼 — useCrud 는 고객이 쓰므로 여기는 직접 다룬다 */
const addrOpen = ref(false)
const addrMode = ref('create')
const addrForm = ref(blankAddress())
const addrErrors = ref({})
const addrBusy = ref(false)
const addrServerError = ref('')

function blankAddress() {
  return {
    addressSeq: null,
    addressName: '',
    receiverName: '',
    phone: '',
    zipCode: '',
    address: '',
    addressDetail: '',
    deliveryMemo: '',
    defaultYn: 'N',
    sortOrder: 0,
    useYn: 'Y',
  }
}

function openAddrCreate() {
  addrMode.value = 'create'
  addrForm.value = blankAddress()
  addrErrors.value = {}
  addrServerError.value = ''
  addrOpen.value = true
}

function openAddrEdit(a) {
  addrMode.value = 'edit'
  addrForm.value = {
    addressSeq: a.addressSeq,
    addressName: a.addressName,
    receiverName: a.receiverName,
    phone: a.phone ?? '',
    zipCode: a.zipCode ?? '',
    address: a.address,
    addressDetail: a.addressDetail ?? '',
    deliveryMemo: a.deliveryMemo ?? '',
    defaultYn: a.defaultYn,
    sortOrder: a.sortOrder ?? 0,
    useYn: a.useYn,
  }
  addrErrors.value = {}
  addrServerError.value = ''
  addrOpen.value = true
}

function validateAddress(f) {
  const e = {}
  if (!f.addressName?.trim()) e.addressName = '배송지명은 필수입니다.'
  if (!f.receiverName?.trim()) e.receiverName = '수령인은 필수입니다.'
  if (!f.address?.trim()) e.address = '주소는 필수입니다.'
  if (f.zipCode && !/^\d{5}$/.test(f.zipCode)) e.zipCode = '숫자 5자리로 입력하세요.'
  if (f.phone && !/^[0-9-+() ]{7,30}$/.test(f.phone)) e.phone = '연락처 형식이 올바르지 않습니다.'
  return e
}

async function submitAddress() {
  addrErrors.value = validateAddress(addrForm.value)
  if (Object.keys(addrErrors.value).length) return

  addrBusy.value = true
  addrServerError.value = ''
  const payload = {
    ...addrForm.value,
    phone: addrForm.value.phone || null,
    zipCode: addrForm.value.zipCode || null,
    addressDetail: addrForm.value.addressDetail || null,
    deliveryMemo: addrForm.value.deliveryMemo || null,
    sortOrder: Number(addrForm.value.sortOrder) || 0,
    reason: addrMode.value === 'create' ? '배송지 등록' : '배송지 수정',
  }
  try {
    const { warning } =
      addrMode.value === 'create'
        ? await customerApi.createAddress(selected.value, payload)
        : await customerApi.updateAddress(addrForm.value.addressSeq, payload)
    if (warning) toast.warn(warning)
    else toast.success(addrMode.value === 'create' ? '배송지를 등록했습니다.' : '배송지를 수정했습니다.')
    addrOpen.value = false
    await openAddresses(selected.value)
    // 목록의 배송지 수 · 기본배송지 유무가 낡는다
    await fetchList()
  } catch (e) {
    // 서버(업무규칙) 오류는 모달을 닫지 않고 그대로 보여준다
    addrServerError.value = e.message
    toast.error(e.message)
  } finally {
    addrBusy.value = false
  }
}

const askAddrDelete = ref(null)
const addrDeleting = ref(false)

async function doAddrDelete() {
  addrDeleting.value = true
  try {
    const warning = await customerApi.removeAddress(askAddrDelete.value.addressSeq, '배송지 삭제')
    if (warning) toast.warn(warning)
    else toast.success('배송지를 삭제했습니다.')
    askAddrDelete.value = null
    await openAddresses(selected.value)
    await fetchList()
  } catch (e) {
    toast.error(e.message)
    askAddrDelete.value = null
  } finally {
    addrDeleting.value = false
  }
}

/* ── 고객 ───────────────────────────────────────────────────── */

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_CUSTOMER',
  pk: 'customerId',
  label: '고객',
  nameOf: (c) => `${c.customerName} (${c.customerId})`,
  api: {
    create: (payload) => customerApi.create(payload),
    update: (customerId, payload) => customerApi.update(customerId, payload),
    remove: (customerId) => customerApi.remove(customerId, '고객 삭제'),
  },
  async afterChange({ action, result }) {
    await fetchList()
    // 등록한 고객은 바로 배송지를 넣어야 판매오더를 낼 수 있다
    if (action === 'create' && result?.customer) {
      await openAddresses(result.customer.customerId)
    }
  },
  blank: () => ({
    customerId: '',
    customerName: '',
    customerType: 'B2B',
    bizRegNo: '',
    managerName: '',
    phone: '',
    email: '',
    status: 'ACTIVE',
    payTerm: '',
    remark: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    customerId: row.customerId,
    customerName: row.customerName,
    customerType: row.customerType,
    bizRegNo: row.bizRegNo ?? '',
    managerName: row.managerName ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    status: row.status,
    payTerm: row.payTerm ?? '',
    remark: row.remark ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    bizRegNo: f.bizRegNo || null,
    managerName: f.managerName || null,
    phone: f.phone || null,
    email: f.email || null,
    payTerm: f.payTerm || null,
    remark: f.remark || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f) {
    const e = {}
    if (!f.customerId?.trim()) e.customerId = '고객코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9-]{1,29}$/.test(f.customerId))
      e.customerId = '영문 대문자·숫자·하이픈 2~30자. 예) CUS-001'
    if (!f.customerName?.trim()) e.customerName = '고객명은 필수입니다.'
    if (!f.customerType) e.customerType = '고객유형을 선택하세요.'
    if (!f.status) e.status = '거래상태를 선택하세요.'
    if (f.bizRegNo && !/^\d{3}-\d{2}-\d{5}$/.test(f.bizRegNo))
      e.bizRegNo = '000-00-00000 형식으로 입력하세요.'
    if (f.phone && !/^[0-9-+() ]{7,30}$/.test(f.phone)) e.phone = '연락처 형식이 올바르지 않습니다.'
    return e
  },
})

/** B2B 인데 사업자등록번호가 없으면 세금계산서를 끊을 수 없다 */
const missingBizNoNotice = computed(() => {
  if (form.value.customerType !== 'B2B' || form.value.bizRegNo) return ''
  return '기업 고객인데 사업자등록번호가 없습니다. 세금계산서 발행 전에 등록하세요.'
})

const notTradableNotice = computed(() => {
  if (form.value.status === 'ACTIVE' && form.value.useYn === 'Y') return ''
  return '거래중이 아닌 상태입니다. 이 고객으로는 신규 판매오더를 낼 수 없습니다.'
})

const readDenyReason = computed(() => session.denyReason('MST_CUSTOMER', 'R'))

/** 거래 중인데 배송지가 없는 고객 — 판매오더를 낼 수 없다 */
const noAddress = computed(() => rows.value.filter((c) => c.tradable && !c.addressCount))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">고객 · 배송지 관리</h1>
        <p class="page-desc">
          센터가 직접 파는 상대입니다. 채널 주문의 수령인과는 다릅니다 — 채널 주문은 주문이
          배송지를 직접 들고 있고, 여기 고객은 판매오더가 가리킵니다.
          <strong>고객당 기본 배송지는 하나입니다.</strong>
          기본을 옮기면 기존 것은 자동으로 내려가고, 기본을 지우면 남은 것이 승계합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '고객 등록'"
          @click="openCreate()"
        >
          + 고객 등록
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

    <div v-if="noAddress.length" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>
        거래 중인데 배송지가 없는 고객이 {{ noAddress.length }}곳 있습니다 —
        <strong>{{ noAddress.map((c) => c.customerName).join(', ') }}</strong>.
        배송지가 있어야 판매오더를 낼 수 있습니다.
      </span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="코드 / 고객명 / 사업자번호 / 담당자"
          @keyup.enter="fetchList()"
        />
        <FormField
          v-model="filters.customerType"
          label="고객유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('CUSTOMER_TYPE')"
          @change="fetchList()"
        />
        <FormField
          v-model="filters.status"
          label="거래상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('PARTNER_STATUS')"
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
        row-key="customerId"
        :muted-when="(c) => !c.tradable"
        empty-text="조건에 맞는 고객이 없습니다."
      >
        <template #cell-customerType="{ value }">
          <CodeBadge group="CUSTOMER_TYPE" :code="value" />
        </template>

        <template #cell-bizRegNo="{ row, value }">
          <span :class="{ dim: !value }">{{ value || (row.customerType === 'B2B' ? '미등록' : '-') }}</span>
        </template>

        <!-- 기본배송지가 없으면 판매오더에서 매번 골라야 한다 -->
        <template #cell-addressCount="{ row, value }">
          <span :class="{ dim: !value }">{{ value || '없음' }}</span>
          <span v-if="value && !row.hasDefaultAddress" class="small warn"> · 기본없음</span>
        </template>

        <template #cell-status="{ value }">
          <CodeBadge group="PARTNER_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              class="btn btn-sm"
              :class="{ 'btn-primary': selected === row.customerId }"
              title="이 고객의 배송지 보기"
              @click="openAddresses(row.customerId)"
            >
              배송지
            </button>
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

    <!-- ── 배송지 ─────────────────────────────────────────────── -->
    <div v-if="selected" class="card mt-2">
      <div class="panel-head">
        <div>
          <strong>{{ selectedCustomer?.customerName ?? selected }}</strong>
          <span class="small dim code"> {{ selected }}</span>
          <span class="small dim"> · 배송지 {{ addresses.length }}건</span>
          <span v-if="addrLoading" class="small dim"><span class="spinner"></span> 불러오는 중…</span>
        </div>
        <div class="btn-row">
          <button
            class="btn btn-sm btn-primary"
            :disabled="!canCreate"
            :title="createDenyReason ?? '배송지 등록'"
            @click="openAddrCreate()"
          >
            + 배송지 등록
          </button>
          <button class="btn btn-sm" @click="selected = ''; addresses = []">닫기</button>
        </div>
      </div>

      <div v-if="!addresses.length && !addrLoading" class="empty-note">
        등록된 배송지가 없습니다. 배송지가 있어야 판매오더를 낼 수 있습니다.
        첫 배송지는 자동으로 기본배송지가 됩니다.
      </div>

      <div v-for="a in addresses" :key="a.addressSeq" class="addr">
        <div class="addr-main">
          <div>
            <strong>{{ a.addressName }}</strong>
            <span v-if="a.defaultYn === 'Y'" class="badge badge-green">기본</span>
            <span v-if="a.useYn !== 'Y'" class="badge badge-gray">미사용</span>
          </div>
          <div class="small">
            {{ a.receiverName }}
            <span v-if="a.phone" class="dim"> · {{ a.phone }}</span>
          </div>
          <div class="small dim">
            <span v-if="a.zipCode">({{ a.zipCode }}) </span>{{ a.fullAddress }}
          </div>
          <div v-if="a.deliveryMemo" class="small memo">요청: {{ a.deliveryMemo }}</div>
        </div>
        <div class="btn-row">
          <button
            class="btn btn-sm"
            :disabled="!canUpdate"
            :title="updateDenyReason ?? '수정'"
            @click="openAddrEdit(a)"
          >
            수정
          </button>
          <button
            class="btn btn-sm btn-danger"
            :disabled="!canDelete"
            :title="deleteDenyReason ?? '삭제'"
            @click="askAddrDelete = a"
          >
            삭제
          </button>
        </div>
      </div>
    </div>

    <!-- ── 고객 등록 · 수정 ───────────────────────────────────── -->
    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '고객 등록' : '고객 수정'"
      :subtitle="mode === 'edit' ? form.customerId : '고객코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.customerId"
          label="고객코드"
          required
          mono
          placeholder="CUS-001"
          :disabled="mode === 'edit'"
          :error="errors.customerId"
          help="판매오더가 이 코드로 고객을 부릅니다."
        />
        <FormField
          v-model="form.customerName"
          label="고객명"
          required
          placeholder="무신사스토어"
          :error="errors.customerName"
        />
        <FormField
          v-model="form.customerType"
          label="고객유형"
          type="select"
          required
          :options="codeOptions('CUSTOMER_TYPE')"
          :error="errors.customerType"
          help="기업은 사업자등록번호가 필요합니다."
        />
        <FormField
          v-model="form.bizRegNo"
          label="사업자등록번호"
          mono
          placeholder="000-00-00000"
          :error="errors.bizRegNo"
        />
        <FormField v-model="form.managerName" label="담당자" placeholder="김바이어" />
        <FormField
          v-model="form.phone"
          label="연락처"
          placeholder="02-1234-0001"
          :error="errors.phone"
        />
        <FormField v-model="form.email" label="이메일" placeholder="buyer@example.com" />
        <FormField
          v-model="form.status"
          label="거래상태"
          type="select"
          required
          :options="codeOptions('PARTNER_STATUS')"
          :error="errors.status"
          help="거래중이 아니면 신규 판매오더를 낼 수 없습니다."
        />
        <FormField
          v-model="form.payTerm"
          label="결제조건"
          type="select"
          empty-option="선택 안 함"
          :options="codeOptions('PAY_TERM')"
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.remark" label="비고" span placeholder="홀세일 계정 등" />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="missingBizNoNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ missingBizNoNotice }}</span>
      </div>
      <div v-else-if="notTradableNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ notTradableNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">코드·고객명 중복은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <!-- ── 배송지 등록 · 수정 ─────────────────────────────────── -->
    <ModalDialog
      v-if="addrOpen"
      :title="addrMode === 'create' ? '배송지 등록' : '배송지 수정'"
      :subtitle="selectedCustomer?.customerName ?? selected"
      @close="addrOpen = false"
    >
      <div v-if="addrServerError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ addrServerError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="addrForm.addressName"
          label="배송지명"
          required
          placeholder="본사 물류창고"
          :error="addrErrors.addressName"
        />
        <FormField
          v-model="addrForm.receiverName"
          label="수령인"
          required
          placeholder="김수령"
          :error="addrErrors.receiverName"
        />
        <FormField
          v-model="addrForm.phone"
          label="연락처"
          placeholder="02-1234-0001"
          :error="addrErrors.phone"
        />
        <FormField
          v-model="addrForm.zipCode"
          label="우편번호"
          placeholder="04524"
          :error="addrErrors.zipCode"
        />
        <FormField
          v-model="addrForm.address"
          label="주소"
          required
          span
          placeholder="서울특별시 중구 세종대로 110"
          :error="addrErrors.address"
        />
        <FormField
          v-model="addrForm.addressDetail"
          label="상세주소"
          span
          placeholder="지하 1층 입고장"
        />
        <FormField
          v-model="addrForm.deliveryMemo"
          label="배송 요청사항"
          span
          placeholder="평일 09~18시만 수령 가능"
          help="주문마다 덮어쓸 수 있는 기본값입니다."
        />
        <FormField
          v-model="addrForm.defaultYn"
          label="기본배송지"
          type="switch"
          help="켜면 기존 기본배송지는 자동으로 내려갑니다."
        />
        <FormField v-model="addrForm.sortOrder" label="정렬순서" type="number" />
        <FormField v-model="addrForm.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="addrMode === 'create' && !addresses.length" class="alert alert-info mt-2">
        <span class="alert-icon">ℹ</span>
        <span>첫 배송지라 기본배송지로 지정됩니다.</span>
      </div>

      <template #footer>
        <span class="left small dim">고객당 기본배송지는 하나입니다. 서버가 다시 확인합니다.</span>
        <button class="btn" :disabled="addrBusy" @click="addrOpen = false">취소</button>
        <button class="btn btn-primary" :disabled="addrBusy" @click="submitAddress()">
          <span v-if="addrBusy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="고객 삭제"
      :message="deleteMessage"
      detail="배송지가 남아 있으면 삭제되지 않습니다. 거래만 멈추려면 거래상태를 '거래종료'로 바꾸세요 — 과거 판매오더가 고객을 가리키고 있습니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />

    <ConfirmDialog
      v-if="askAddrDelete"
      title="배송지 삭제"
      :message="`${askAddrDelete.addressName} (${askAddrDelete.receiverName}) 배송지를 삭제합니다.`"
      :detail="
        askAddrDelete.defaultYn === 'Y'
          ? '기본배송지입니다. 삭제하면 남은 배송지 중 하나가 기본이 됩니다.'
          : '과거 판매오더가 이 배송지를 가리키고 있을 수 있습니다.'
      "
      confirm-label="삭제"
      danger
      :busy="addrDeleting"
      @cancel="askAddrDelete = null"
      @confirm="doAddrDelete()"
    />
  </div>
</template>

<style scoped>
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}
.empty-note {
  padding: 32px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.addr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
}
.addr + .addr {
  border-top: 1px solid var(--line, #e5e7eb);
}
.addr-main {
  min-width: 0;
}
.addr-main > div:first-child {
  display: flex;
  align-items: center;
  gap: 6px;
}
.memo {
  color: var(--c-amber);
}
.warn {
  color: var(--c-amber);
}
.code {
  margin-left: 4px;
}
</style>
