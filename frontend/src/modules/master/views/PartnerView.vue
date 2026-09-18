<script setup>
/**
 * 거래처 관리 (MST-PG-012).
 *
 * 물건을 대 주는 곳과 사 가는 곳을 한 화면에서 다룬다. 공급처 화면과 고객
 * 화면을 따로 두었다가 합쳤다(V20) — 두 화면이 거의 같은 코드였고, 무엇보다
 * <b>한 회사가 양쪽인 경우</b>가 있다. 임가공이 그렇다. 원단을 공장에 넘기고
 * (그 공장이 우리 고객) 완성된 옷을 받는다(그 공장이 우리 공급처).
 *
 * 방향은 체크박스 둘이다. 둘 다 켜면 목록에 배지가 둘 달리고 발주에서도
 * 주문에서도 고를 수 있다. 최소 하나는 켜야 한다 — 어디에도 못 쓰는
 * 거래처는 목록에만 있고 어느 화면에서도 안 보여서, 만든 사람이 왜 없는지
 * 모른다. 서버와 DB 가 같은 검사를 한다.
 *
 * 개인은 여기 등록하지 않는다. 전화 주문이든 오픈마켓이든 주문이 받는
 * 사람을 직접 들고 있고 거래처는 비운다. 그래서 여기에는 조직만 있다.
 *
 * 주소가 두 종류다. <b>사업장 주소</b>는 회사의 법적 주소라 하나뿐이고
 * 세금계산서에 찍힌다 — 위쪽 폼의 칸이다. <b>배송지 · 반품지</b>는 거래할
 * 때 쓰는 곳이라 여럿이고 자주 바뀐다 — 아래 패널이 맡는다.
 *
 * 규칙 하나가 아래 패널의 전부다 — 거래처당 기본 주소는 하나 (MST-010).
 * 기본을 옮기면 서버가 기존 것을 내리고, 기본을 지우면 남은 것이 승계한다.
 * 사용자에게 "먼저 해제하세요" 를 요구하지 않는다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as partnerApi from '@/api/partner.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import DetailDialog from '@/components/DetailDialog.vue'

const session = useSessionStore()
const toast = useToastStore()

const loadError = ref('')
const loading = ref(false)
const rows = ref([])

const filters = reactive({ keyword: '', direction: '', status: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', direction: '', status: '', useYn: '' })
}

async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await partnerApi.list({ ...filters, size: 0 })
    rows.value = data.rows
    // 고른 고객이 사라졌으면(삭제·필터) 선택을 푼다
    if (selected.value && !rows.value.some((c) => c.partnerId === selected.value)) {
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

/*
 * 상세 보기 — 목록에 없는 칸(이메일 · 담당자 · 비고)이 여기 있다.
 * 배송지는 아래 패널이 따로 맡는다. 여러 건이고 등록 · 수정이 붙어 있어,
 * 읽기 전용 창에 끼워 넣으면 "여기선 왜 못 고치지" 가 된다.
 */
const detail = ref(null)
const detailFields = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: '거래처코드', value: d.partnerId, mono: true },
    { label: '거래처명', value: d.partnerName },
    { label: '거래 유형', slot: 'direction', span: true },
    { label: '사업자등록번호', value: d.bizRegNo, mono: true },
    { label: '대표자', value: d.ceoName },
    { label: '담당자', value: d.managerName },
    { label: '연락처', value: d.phone, mono: true },
    { label: '이메일', value: d.email },
    { label: '우편번호', value: d.zipCode, mono: true },
    { label: '사업장 주소', value: d.address, span: true },
    { label: '결제조건', slot: 'payTerm' },
    // 공급처일 때만 뜻이 있다. 0 이면 예정수량을 넘는 입고가 전부 승인 대상.
    ...(d.supplierYn === 'Y'
      ? [{
          label: '초과입고 허용',
          value: Number(d.overReceiptRate)
            ? `${d.overReceiptRate}%`
            : '0% (예정수량 초과 시 전부 승인)',
        }]
      : []),
    { label: '배송지·반품지', value: d.addressCount ? `${d.addressCount}건` : '없음' },
    { label: '거래상태', slot: 'status' },
    { label: '사용', slot: 'useYn' },
    { label: '비고', value: d.remark, span: true },
  ]
})

const columns = [
  { key: 'partnerId', label: '거래처코드', width: '100px', sortable: true, cls: 'code' },
  { key: 'partnerName', label: '거래처명', width: '150px', sortable: true },
  // 방향은 정렬하지 않는다. 플래그 둘이라 정렬 기준이 하나로 정해지지 않는다.
  { key: '_direction', label: '거래 유형', width: '120px', align: 'center' },
  { key: 'bizRegNo', label: '사업자등록번호', width: '130px', cls: 'code' },
  { key: 'phone', label: '연락처', width: '125px' },
  // 사업장 주소(한 칸짜리)와 헷갈리지 않게 제목에 무엇의 건수인지 적는다
  { key: 'addressCount', label: '배송지·반품지', width: '110px', align: 'right' },
  { key: 'status', label: '거래상태', width: '96px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '150px', align: 'right' },
]

/* ── 배송지 ─────────────────────────────────────────────────── */

const selected = ref('')
const addresses = ref([])
const addrLoading = ref(false)

const selectedPartner = computed(
  () => rows.value.find((c) => c.partnerId === selected.value) ?? null,
)

async function openAddresses(partnerId) {
  selected.value = partnerId
  addrLoading.value = true
  try {
    addresses.value = await partnerApi.addresses(partnerId)
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
        ? await partnerApi.createAddress(selected.value, payload)
        : await partnerApi.updateAddress(addrForm.value.addressSeq, payload)
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
    const warning = await partnerApi.removeAddress(askAddrDelete.value.addressSeq, '배송지 삭제')
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

/* ── 거래처 ─────────────────────────────────────────────────── */

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_PARTNER',
  pk: 'partnerId',
  label: '거래처',
  nameOf: (c) => `${c.partnerName} (${c.partnerId})`,
  api: {
    create: (payload) => partnerApi.create(payload),
    update: (partnerId, payload) => partnerApi.update(partnerId, payload),
    remove: (partnerId) => partnerApi.remove(partnerId, '거래처 삭제'),
  },
  async afterChange({ action, result }) {
    await fetchList()
    // 고객으로 등록했으면 배송지를 넣어야 판매오더를 낼 수 있다.
    // 공급처만이면 주소가 없어도 발주는 나가므로 굳이 열지 않는다.
    if (action === 'create' && result?.partner?.customerYn === 'Y') {
      await openAddresses(result.partner.partnerId)
    }
  },
  blank: () => ({
    partnerId: '',
    partnerName: '',
    supplierYn: 'N',
    customerYn: 'Y',
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
    partnerId: row.partnerId,
    partnerName: row.partnerName,
    supplierYn: row.supplierYn ?? 'N',
    customerYn: row.customerYn ?? 'N',
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
    // 공급처가 아니면 초과입고 허용률은 뜻이 없다. 0 으로 보낸다.
    overReceiptRate: f.supplierYn === 'Y' ? Number(f.overReceiptRate) || 0 : 0,
    remark: f.remark || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f) {
    const e = {}
    if (!f.partnerId?.trim()) e.partnerId = '거래처코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9-]{1,29}$/.test(f.partnerId))
      e.partnerId = '영문 대문자·숫자·하이픈 2~30자. 예) PTN-001'
    if (!f.partnerName?.trim()) e.partnerName = '거래처명은 필수입니다.'
    // 둘 다 끄면 어디에서도 고를 수 없는 거래처가 된다
    if (f.supplierYn !== 'Y' && f.customerYn !== 'Y')
      e.direction = '공급처 · 고객 중 최소 하나를 고르세요.'
    if (!f.status) e.status = '거래상태를 선택하세요.'
    if (f.bizRegNo && !/^\d{3}-\d{2}-\d{5}$/.test(f.bizRegNo))
      e.bizRegNo = '000-00-00000 형식으로 입력하세요.'
    if (f.zipCode && !/^\d{5}$/.test(f.zipCode)) e.zipCode = '우편번호는 숫자 5자리입니다.'
    if (f.phone && !/^[0-9-+() ]{7,30}$/.test(f.phone)) e.phone = '연락처 형식이 올바르지 않습니다.'
    return e
  },
})

/** 폼에서 지금 무엇으로 쓰려는 거래처인가 — 유형별 칸을 감추는 데 쓴다 */
const formIsSupplier = computed(() => form.value.supplierYn === 'Y')
const formIsCustomer = computed(() => form.value.customerYn === 'Y')

/** 사업자등록번호가 없으면 세금계산서를 끊을 수 없다 */
const missingBizNoNotice = computed(() => {
  if (form.value.bizRegNo) return ''
  return '사업자등록번호가 없습니다. 세금계산서 발행 전에 등록하세요.'
})

const notTradableNotice = computed(() => {
  if (form.value.status === 'ACTIVE' && form.value.useYn === 'Y') return ''
  return '거래중이 아닌 상태입니다. 이 고객으로는 신규 판매오더를 낼 수 없습니다.'
})

const readDenyReason = computed(() => session.denyReason('MST_PARTNER', 'R'))

/** 거래 중인데 배송지가 없는 고객 — 판매오더를 낼 수 없다 */
const noAddress = computed(() => rows.value.filter((c) => c.tradable && !c.addressCount))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">거래처 관리</h1>
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
        <strong>{{ noAddress.map((c) => c.partnerName).join(', ') }}</strong>.
        배송지가 있어야 판매오더를 낼 수 있습니다.
      </span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="코드 / 거래처명 / 사업자번호 / 담당자"
          @keyup.enter="fetchList()"
        />
        <!-- 양쪽인 거래처는 어느 쪽으로 걸러도 나온다 (서버가 OR 로 본다) -->
        <FormField
          v-model="filters.direction"
          label="거래 유형"
          type="select"
          empty-option="전체"
          :options="[
            { value: 'SUPPLIER', label: '공급처' },
            { value: 'CUSTOMER', label: '고객' },
          ]"
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
        row-key="partnerId"
        :muted-when="(c) => !c.tradable"
        empty-text="조건에 맞는 고객이 없습니다."
      >
        <!-- 거래처명을 누르면 상세가 열린다 (이메일 · 담당자 · 비고) -->
        <template #cell-partnerName="{ row, value }">
          <button class="link-cell" @click="detail = row">{{ value }}</button>
        </template>

        <!--
          배지를 방향마다 하나씩 단다. 양쪽이면 둘이 붙는다.
          '양쪽' 이라는 합친 이름을 만들지 않은 덕에 새로 배울 단어가 없다.
        -->
        <template #cell-_direction="{ row }">
          <span v-if="row.supplierYn === 'Y'" class="badge badge-blue">공급처</span>
          <span v-if="row.customerYn === 'Y'" class="badge badge-green">고객</span>
        </template>

        <template #cell-bizRegNo="{ value }">
          <span :class="{ dim: !value }">{{ value || '미등록' }}</span>
        </template>

        <!-- 기본 주소가 없으면 주문에서 매번 골라야 한다 -->
        <!--
          숫자만 찍으면 주소 자체로 읽힌다. 건수라는 것을 값에도 적는다.
          기본 주소가 없으면 주문에서 매번 골라야 하므로 같이 알린다.
        -->
        <template #cell-addressCount="{ row, value }">
          <span :class="{ dim: !value }">{{ value ? `${value}건` : '없음' }}</span>
          <span v-if="value && !row.hasDefaultAddress" class="small warn"> · 기본없음</span>
        </template>

        <template #cell-status="{ value }">
          <CodeBadge group="PARTNER_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              class="btn btn-sm"
              :class="{ 'btn-primary': selected === row.partnerId }"
              title="이 고객의 배송지 보기"
              @click="openAddresses(row.partnerId)"
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

    <DetailDialog
      v-if="detail"
      title="고객 상세"
      :subtitle="detail.partnerName + ' · ' + detail.partnerId"
      :fields="detailFields"
      :can-edit="canUpdate"
      :edit-deny-reason="updateDenyReason"
      @edit="openEdit(detail); detail = null"
      @close="detail = null"
    >
      <template #direction>
        <span v-if="detail.supplierYn === 'Y'" class="badge badge-blue">공급처</span>
        <span v-if="detail.customerYn === 'Y'" class="badge badge-green">고객</span>
        <span v-if="detail.supplierYn === 'Y' && detail.customerYn === 'Y'" class="small dim">
          사고팔기를 같이 하는 상대
        </span>
      </template>
      <template #payTerm>
        <CodeBadge v-if="detail.payTerm" group="PAY_TERM" :code="detail.payTerm" />
        <span v-else class="dim">-</span>
      </template>
      <template #status>
        <CodeBadge group="PARTNER_STATUS" :code="detail.status" />
      </template>
      <template #useYn>
        <CodeBadge group="USE_YN" :code="detail.useYn" />
      </template>
      <template #footer-note>
        배송지는 목록에서 '배송지' 를 눌러 따로 관리합니다.
      </template>
    </DetailDialog>

    <!-- ── 배송지 ─────────────────────────────────────────────── -->
    <div v-if="selected" class="card mt-2">
      <div class="panel-head">
        <div>
          <strong>{{ selectedPartner?.partnerName ?? selected }}</strong>
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
      :subtitle="mode === 'edit' ? form.partnerId : '거래처코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.partnerId"
          label="거래처코드"
          required
          mono
          placeholder="CUS-001"
          :disabled="mode === 'edit'"
          :error="errors.partnerId"
          help="판매오더가 이 코드로 고객을 부릅니다."
        />
        <FormField
          v-model="form.partnerName"
          label="거래처명"
          required
          placeholder="무신사스토어"
          :error="errors.partnerName"
        />
        <!--
          거래 방향. 체크박스 둘이고 최소 하나는 켜야 한다.

          유형 하나를 고르게 하지 않은 것은 '양쪽' 이 실제로 있기 때문이다 —
          임가공 업체는 우리에게 옷을 대 주면서(공급처) 우리 원단을 사 간다(고객).
          하나만 고르게 하면 그 회사를 두 줄로 등록해야 한다.
        -->
        <div class="field span-2" :class="{ invalid: !!errors.direction }">
          <label class="field-label">거래 유형<span class="req">*</span></label>
          <div class="check-group">
            <label class="check-line">
              <input
                type="checkbox"
                :checked="form.supplierYn === 'Y'"
                @change="form.supplierYn = $event.target.checked ? 'Y' : 'N'"
              />
              <span>공급처</span>
              <span class="small dim">— 이 거래처에서 물건을 사 옵니다</span>
            </label>
            <label class="check-line">
              <input
                type="checkbox"
                :checked="form.customerYn === 'Y'"
                @change="form.customerYn = $event.target.checked ? 'Y' : 'N'"
              />
              <span>고객</span>
              <span class="small dim">— 이 거래처에 물건을 팝니다</span>
            </label>
          </div>
          <span v-if="errors.direction" class="field-error">{{ errors.direction }}</span>
          <span v-else class="field-help">
            사고팔기를 같이 하는 상대(임가공 등)는 둘 다 고릅니다.
          </span>
        </div>
        <FormField
          v-model="form.bizRegNo"
          label="사업자등록번호"
          mono
          placeholder="000-00-00000"
          :error="errors.bizRegNo"
        />
        <!-- 대표자는 세금계산서에 찍힌다. 공급처 쪽에서 주로 쓴다. -->
        <FormField v-if="formIsSupplier" v-model="form.ceoName" label="대표자" placeholder="홍길동" />
        <FormField v-model="form.managerName" label="담당자" placeholder="김바이어" />
        <FormField
          v-model="form.phone"
          label="연락처"
          placeholder="02-1234-0001"
          :error="errors.phone"
        />
        <FormField v-model="form.email" label="이메일" placeholder="buyer@example.com" />
        <FormField
          v-model="form.zipCode"
          label="우편번호"
          mono
          placeholder="06236"
          :error="errors.zipCode"
        />
        <FormField
          v-model="form.address"
          label="사업장 주소"
          span
          placeholder="서울특별시 강남구 …"
          help="세금계산서에 찍히는 법적 주소입니다. 물건을 보내고 받을 곳은 아래 주소 패널에서 따로 등록합니다."
        />
        <FormField
          v-model="form.status"
          label="거래상태"
          type="select"
          required
          :options="codeOptions('PARTNER_STATUS')"
          :error="errors.status"
          help="거래중이 아니면 신규 발주·판매오더를 낼 수 없습니다."
        />
        <FormField
          v-model="form.payTerm"
          label="결제조건"
          type="select"
          empty-option="선택 안 함"
          :options="codeOptions('PAY_TERM')"
        />
        <!--
          초과입고 허용률은 사 오는 쪽에서만 뜻이 있다. 고객 전용 거래처에
          두면 채워도 아무 일이 일어나지 않는 값이 된다.
        -->
        <FormField
          v-if="formIsSupplier"
          v-model="form.overReceiptRate"
          label="초과입고 허용률 (%)"
          type="number"
          help="0 이면 예정수량을 1개라도 넘는 입고가 전부 승인 대상이 됩니다."
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
        <span class="left small dim">코드·거래처명 중복은 저장 시 서버가 다시 검증합니다.</span>
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
      :subtitle="selectedPartner?.partnerName ?? selected"
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
          on-label="기본"
          off-label="아님"
          help="기본으로 두면 기존 기본배송지는 자동으로 내려갑니다."
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
      title="거래처 삭제"
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
