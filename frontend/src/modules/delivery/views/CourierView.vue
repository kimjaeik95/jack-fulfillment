<script setup>
/**
 * 택배사 관리 (DLV-PG-001).
 *
 * V24 까지는 COURIER 공통코드가 이 자리였다. 이름만 필요했을 때는 그것으로
 * 충분했는데, 계약번호 · 단가 · 집화 마감시각이 붙으면서 담을 곳이 없어졌다.
 *
 * <b>택배사 코드는 등록할 때만 정한다.</b> 이미 발급된 송장이 코드를 값으로
 * 들고 있어서, 나중에 바꾸면 지난 송장이 없는 택배사를 가리키게 된다. 이름은
 * 바꿔도 된다 — 회사 이름은 실제로 바뀐다.
 *
 * 판정은 모두 서버가 한다. 여기서 막는 것은 왕복을 줄이려는 편의다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as deliveryApi from '@/api/delivery.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const session = useSessionStore()

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const table = ref(null)

const filters = reactive({ keyword: '', useYn: '', expiredOnly: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', useYn: '', expiredOnly: '' })
  reload()
}

async function reload() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await deliveryApi.couriers({
      keyword: filters.keyword.trim() || null,
      useYn: filters.useYn || null,
      expiredOnly: filters.expiredOnly || null,
    })
    rows.value = data.rows
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(reload)

const columns = [
  { key: 'courierCode', label: '코드', width: '96px', sortable: true, cls: 'code' },
  { key: 'courierName', label: '택배사', width: '140px', sortable: true },
  { key: 'contractNo', label: '계약', width: '180px' },
  { key: 'boxFee', label: '박스당', width: '84px', align: 'right', sortable: true },
  { key: 'pickupCutoff', label: '집화마감', width: '84px', align: 'center' },
  { key: 'waybillCount', label: '송장', width: '68px', align: 'right', sortable: true },
  { key: 'useYn', label: '사용', width: '64px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'DLV_COURIER',
  pk: 'courierCode',
  label: '택배사',
  nameOf: (c) => `${c.courierName}(${c.courierCode})`,
  api: {
    create: (payload) => deliveryApi.createCourier(payload),
    update: (code, payload) => deliveryApi.updateCourier(code, payload),
    remove: (code) => deliveryApi.deleteCourier(code),
  },
  async afterChange({ action, key }) {
    await reload()
    if (action === 'create') {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    courierCode: '',
    courierName: '',
    contractNo: '',
    contractFrom: '',
    contractTo: '',
    boxFee: '',
    pickupCutoff: '',
    trackingUrl: '',
    contactName: '',
    contactPhone: '',
    remark: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (r) => ({
    courierCode: r.courierCode,
    courierName: r.courierName,
    contractNo: r.contractNo ?? '',
    contractFrom: r.contractFrom ?? '',
    contractTo: r.contractTo ?? '',
    boxFee: r.boxFee ?? '',
    pickupCutoff: (r.pickupCutoff ?? '').slice(0, 5),
    trackingUrl: r.trackingUrl ?? '',
    contactName: r.contactName ?? '',
    contactPhone: r.contactPhone ?? '',
    remark: r.remark ?? '',
    sortOrder: r.sortOrder ?? 0,
    useYn: r.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    contractNo: f.contractNo || null,
    contractFrom: f.contractFrom || null,
    contractTo: f.contractTo || null,
    boxFee: f.boxFee === '' || f.boxFee === null ? null : Number(f.boxFee),
    // <input type="time"> 은 HH:mm 을 주는데 서버는 LocalTime 이라 초가 필요하다
    pickupCutoff: f.pickupCutoff ? `${f.pickupCutoff}:00` : null,
    trackingUrl: f.trackingUrl || null,
    contactName: f.contactName || null,
    contactPhone: f.contactPhone || null,
    remark: f.remark || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.courierCode?.trim()) e.courierCode = '택배사 코드는 필수입니다.'
    else if (!/^[A-Z0-9_]{2,20}$/.test(f.courierCode))
      e.courierCode = '영문 대문자 · 숫자 · 밑줄 2~20자. 예) CJ'
    else if (ctx.mode === 'create' && rows.value.some((c) => c.courierCode === f.courierCode))
      e.courierCode = '이미 등록된 택배사 코드입니다.'

    if (!f.courierName?.trim()) e.courierName = '택배사명은 필수입니다.'
    else if (rows.value.some(
      (c) => c.courierName === f.courierName.trim() && c.courierCode !== f.courierCode,
    ))
      e.courierName = '같은 이름의 택배사가 이미 있습니다. 송장 화면에서 둘을 구분할 수 없습니다.'

    if (f.contractFrom && f.contractTo && f.contractFrom > f.contractTo)
      e.contractTo = '계약 종료일이 시작일보다 빠릅니다.'

    if (f.boxFee !== '' && Number(f.boxFee) < 0) e.boxFee = '0원 이상이어야 합니다.'

    // 조회주소는 선택이지만, 적었으면 치환자리가 있어야 쓸모가 있다
    if (f.trackingUrl && !f.trackingUrl.includes('{waybillNo}'))
      e.trackingUrl = '송장번호가 들어갈 자리에 {waybillNo} 를 넣으세요.'

    return e
  },
})

const readDenyReason = computed(() => session.denyReason('DLV_COURIER', 'R'))

/** 왜 못 지울 수 있는지 미리 알려 준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  if (row.waybillCount) {
    return `이 택배사로 나간 송장 ${row.waybillCount}건이 있어 삭제할 수 없습니다. 더 쓰지 않으려면 사용여부를 '미사용'으로 바꾸세요 — 지난 송장이 이 택배사를 가리키고 있습니다.`
  }
  return '이 택배사로 나간 송장이 있으면 서버가 삭제를 거부합니다.'
})

const nf = new Intl.NumberFormat('ko-KR')
const won = (v) => (v === null || v === undefined || v === '' ? '-' : nf.format(v) + '원')
const hhmm = (v) => (v ? String(v).slice(0, 5) : '-')
const ymd = (v) => (v ? String(v).slice(0, 10) : '')
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">택배사 관리</h1>
        <p class="page-desc">
          송장을 발급할 택배사와 계약 조건을 관리합니다.
          <strong>택배사 코드는 등록 후 바꿀 수 없습니다</strong> — 이미 발급된 송장이 그
          코드를 들고 있어서, 바꾸면 지난 송장의 택배사를 잃습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '택배사 등록'"
          @click="openCreate()"
        >
          + 택배사 등록
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
          placeholder="코드 / 택배사명 / 계약번호 / 담당자"
          @enter="reload()"
        />
        <FormField
          v-model="filters.useYn"
          label="사용"
          type="select"
          empty-option="전체"
          :options="codeOptions('USE_YN')"
          @change="reload()"
        />
        <!--
          계약이 지난 택배사로 송장을 계속 뽑고 있으면 정산 때 드러난다.
          그 전에 찾으려고 두는 필터다.
        -->
        <FormField
          v-model="filters.expiredOnly"
          label="계약"
          type="select"
          empty-option="전체"
          :options="[{ value: 'Y', label: '만료된 것만' }]"
          @change="reload()"
        />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
          <button class="btn" :disabled="loading" @click="reload()">
            <span v-if="loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="courierCode"
        :loading="loading"
        :page-size="15"
        :muted-when="(c) => c.useYn !== 'Y'"
        empty-text="조건에 맞는 택배사가 없습니다."
      >
        <template #cell-courierName="{ row, value }">
          {{ value }}
          <div v-if="!row.trackingUrl" class="small dim">조회주소 없음</div>
        </template>

        <template #cell-contractNo="{ row, value }">
          <span v-if="value" class="code">{{ value }}</span>
          <span v-else class="dim">-</span>
          <div v-if="row.contractTo" class="small" :class="row.expired ? 'danger' : 'dim'">
            {{ ymd(row.contractFrom) || '?' }} ~ {{ ymd(row.contractTo) }}
            <strong v-if="row.expired">만료</strong>
          </div>
        </template>

        <template #cell-boxFee="{ value }">{{ won(value) }}</template>
        <template #cell-pickupCutoff="{ value }">{{ hhmm(value) }}</template>

        <!-- 계약은 끝났는데 송장이 아직 나가고 있는 것이 이 화면이 잡을 것이다 -->
        <template #cell-waybillCount="{ row, value }">
          <span :class="row.expired && value > 0 ? 'danger' : ''">{{ nf.format(value ?? 0) }}</span>
        </template>

        <template #cell-useYn="{ value }">
          <CodeBadge group="USE_YN" :code="value" />
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

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '택배사 등록' : '택배사 수정'"
      :subtitle="mode === 'edit' ? form.courierCode : '택배사 코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.courierCode"
          label="택배사 코드"
          required
          mono
          placeholder="CJ"
          :disabled="mode === 'edit'"
          :error="errors.courierCode"
          help="송장이 이 값으로 택배사를 가리킵니다. 영문 대문자 · 숫자 2~20자"
        />
        <FormField
          v-model="form.courierName"
          label="택배사명"
          required
          placeholder="CJ대한통운"
          :error="errors.courierName"
        />
        <FormField v-model="form.contractNo" label="계약번호" placeholder="비워 둘 수 있습니다" />
        <FormField v-model="form.contractFrom" label="계약 시작" type="date" />
        <FormField
          v-model="form.contractTo"
          label="계약 종료"
          type="date"
          :error="errors.contractTo"
          help="지나면 목록에서 만료로 표시됩니다."
        />
        <FormField
          v-model="form.boxFee"
          label="박스당 단가"
          type="number"
          :error="errors.boxFee"
          help="원 단위. 실제 정산은 무게·지역으로 갈리지만 대략을 적어 둡니다."
        />
        <FormField
          v-model="form.pickupCutoff"
          label="집화 마감"
          type="time"
          help="이 시각을 넘기면 오늘 못 나갑니다."
        />
        <FormField
          v-model="form.trackingUrl"
          class="span-2"
          label="배송조회 주소"
          placeholder="https://example.com/track?no={waybillNo}"
          :error="errors.trackingUrl"
          help="송장번호가 들어갈 자리에 {waybillNo} 를 넣으세요. 배송현황에서 조회 링크로 씁니다."
        />
        <FormField v-model="form.contactName" label="담당자" />
        <FormField v-model="form.contactPhone" label="연락처" placeholder="02-0000-0000" />
        <FormField v-model="form.remark" class="span-2" label="비고" type="textarea" />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField
          v-model="form.useYn"
          label="사용여부"
          type="switch"
          help="미사용으로 바꿔도 배송 중인 송장은 그대로 추적됩니다."
        />
      </div>

      <template #footer>
        <span class="left small dim">중복과 참조 무결성은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="택배사 삭제"
      :message="deleteMessage"
      :detail="deleteDetail"
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>

<style scoped>
.danger {
  color: var(--c-red, #dc2626);
  font-weight: 600;
}
</style>
