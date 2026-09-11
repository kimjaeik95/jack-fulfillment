<script setup>
/**
 * 회사(법인) 관리 — MST-PG-001 의 회사 부분.
 *
 * 단일 법인이면 1행으로 운영한다. 그런데도 별도 화면인 이유는 다법인 ·
 * 다화주 확장 대비(NFR-OPS-04)다. 전에는 조직의 한 행(유형=회사)으로
 * 합쳐 두고 "회사일 때만 사업자정보를 채운다"를 제약으로 막았는데,
 * 그건 테이블을 나눠서 푸는 문제였다.
 *
 * 판정은 모두 서버가 한다. 여기서 막는 것은 왕복을 줄이기 위한 편의일 뿐이고,
 * 중복·참조 무결성은 저장 시 서버가 다시 검사한다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as companyApi from '@/api/company.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const loadError = ref('')
const table = ref(null)

const filters = reactive({ keyword: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    await hierarchy.loadCompanies(force)
    if (hierarchy.denyReason.companies) loadError.value = hierarchy.denyReason.companies
  } catch (e) {
    loadError.value = e.message
  }
}

onMounted(() => reload(false))

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return hierarchy.companies
    .filter((c) => !filters.useYn || c.useYn === filters.useYn)
    .filter(
      (c) =>
        !kw ||
        [c.companyId, c.companyName, c.ceoName, c.bizRegNo].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
})

const columns = [
  { key: 'companyId', label: '회사코드', width: '96px', sortable: true, cls: 'code' },
  { key: 'companyName', label: '회사명', width: '160px', sortable: true },
  { key: 'bizRegNo', label: '사업자등록번호', width: '132px', cls: 'code' },
  { key: 'ceoName', label: '대표자', width: '90px' },
  { key: 'phone', label: '연락처', width: '124px' },
  { key: 'orgCount', label: '소속 조직', width: '80px', align: 'right', sortable: true },
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
  // 조직과 같은 화면 묶음이라 권한도 같다
  perm: 'SYS_COMPANY',
  pk: 'companyId',
  label: '회사',
  nameOf: (c) => `${c.companyName}(${c.companyId})`,
  api: {
    create: (payload) => companyApi.create(payload),
    update: (companyId, payload) => companyApi.update(companyId, payload),
    remove: (companyId) => companyApi.remove(companyId, '회사 삭제'),
  },
  async afterChange({ action, key }) {
    await reload()
    if (action === 'create') {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    companyId: '',
    companyName: '',
    bizRegNo: '',
    ceoName: '',
    zipCode: '',
    address: '',
    phone: '',
    email: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    companyId: row.companyId,
    companyName: row.companyName,
    bizRegNo: row.bizRegNo ?? '',
    ceoName: row.ceoName ?? '',
    zipCode: row.zipCode ?? '',
    address: row.address ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    bizRegNo: f.bizRegNo || null,
    ceoName: f.ceoName || null,
    zipCode: f.zipCode || null,
    address: f.address || null,
    phone: f.phone || null,
    email: f.email || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.companyId?.trim()) e.companyId = '회사코드는 필수입니다.'
    else if (!/^[A-Z]{2}\d{3}$/.test(f.companyId))
      e.companyId = '영문 대문자 2자 + 숫자 3자 형식. 예) CO001'
    else if (ctx.mode === 'create' && hierarchy.companies.some((c) => c.companyId === f.companyId))
      e.companyId = '이미 사용 중인 회사코드입니다.'
    if (!f.companyName?.trim()) e.companyName = '회사명은 필수입니다.'
    else if (
      hierarchy.companies.some(
        (c) => c.companyName === f.companyName.trim() && c.companyId !== f.companyId,
      )
    )
      e.companyName = '이미 사용 중인 회사명입니다.'
    // 사업자등록번호는 비워 둘 수 있다. 시스템을 세우는 시점에 아직 확정되지
    // 않은 경우가 있고, 그때 등록을 막으면 조직을 만들 수 없다.
    if (f.bizRegNo && !/^\d{3}-\d{2}-\d{5}$/.test(f.bizRegNo))
      e.bizRegNo = '000-00-00000 형식으로 입력하세요.'
    else if (
      f.bizRegNo &&
      hierarchy.companies.some(
        (c) => c.bizRegNo === f.bizRegNo && c.companyId !== f.companyId,
      )
    )
      e.bizRegNo = '이미 등록된 사업자등록번호입니다.'
    if (f.zipCode && !/^\d{5}$/.test(f.zipCode)) e.zipCode = '우편번호는 숫자 5자리입니다.'
    if (f.phone && !/^\d{2,3}-\d{3,4}-\d{4}$/.test(f.phone))
      e.phone = '02-1234-5678 형식으로 입력하세요.'
    if (f.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email))
      e.email = '이메일 형식이 올바르지 않습니다.'
    return e
  },
})

/**
 * 두 번째 회사 등록 안내.
 *
 * 막지 않는다 — 다법인으로 넓힐 여지를 남겨 두라는 요구가 있다(NFR-OPS-04).
 * 다만 1차 범위는 단일 법인이므로, 회사를 늘리면 어떤 일이 생기는지 미리
 * 알려 준다. 저장 후 서버도 같은 내용을 warning 으로 돌려준다.
 */
const secondCompanyNotice = computed(() => {
  if (mode.value !== 'create') return ''
  if (hierarchy.companies.length === 0) return ''
  return `이미 회사가 ${hierarchy.companies.length}개 있습니다. 1차 범위는 단일 법인이라, 회사를 둘 이상 두면 조직 트리가 갈리고 사용자·데이터 범위가 회사별로 나뉩니다.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  if (row.orgCount) {
    return `소속 조직 ${row.orgCount}개가 있어 삭제할 수 없습니다. 조직을 다른 회사로 옮긴 뒤 삭제하세요. 더 이상 쓰지 않는 회사라면 사용여부를 '미사용'으로 바꾸세요.`
  }
  if (hierarchy.companies.length <= 1) {
    return '마지막 회사는 삭제할 수 없습니다. 조직과 사용자가 회사 아래에만 존재할 수 있습니다.'
  }
  return '소속 조직이 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('SYS_COMPANY', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">회사 관리</h1>
        <p class="page-desc">
          법인 정보를 관리합니다. 단일 법인이면 1행으로 운영하며, 모든 조직은 회사 아래에 속합니다.
          사업자등록번호는 값이 있으면 회사마다 유일해야 합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '회사 등록'"
          @click="openCreate()"
        >
          + 회사 등록
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

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="회사코드 / 회사명 / 대표자 / 사업자등록번호"
        />
        <FormField
          v-model="filters.useYn"
          label="사용"
          type="select"
          empty-option="전체"
          :options="codeOptions('USE_YN')"
        />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
          <button class="btn" :disabled="hierarchy.loading" @click="reload(true)">
            <span v-if="hierarchy.loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="companyId"
        :page-size="10"
        :muted-when="(c) => c.useYn !== 'Y'"
        empty-text="조건에 맞는 회사가 없습니다."
      >
        <template #cell-bizRegNo="{ value }">
          <span :class="{ dim: !value }">{{ value || '미입력' }}</span>
        </template>

        <template #cell-ceoName="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
        </template>

        <template #cell-phone="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
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
      :title="mode === 'create' ? '회사 등록' : '회사 수정'"
      :subtitle="mode === 'edit' ? form.companyId : '회사코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div v-if="secondCompanyNotice" class="alert alert-warn mb-2">
        <span class="alert-icon">⚠</span><span>{{ secondCompanyNotice }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.companyId"
          label="회사코드"
          required
          mono
          placeholder="CO001"
          :disabled="mode === 'edit'"
          :error="errors.companyId"
          help="영문 대문자 2자 + 숫자 3자 (예: CO001)"
        />
        <FormField
          v-model="form.companyName"
          label="회사명"
          required
          placeholder="주식회사 예시"
          :error="errors.companyName"
        />
        <FormField
          v-model="form.bizRegNo"
          label="사업자등록번호"
          mono
          placeholder="000-00-00000"
          :error="errors.bizRegNo"
          help="비워 둘 수 있습니다. 값이 있으면 회사마다 유일해야 합니다."
        />
        <FormField v-model="form.ceoName" label="대표자명" placeholder="홍길동" />
        <FormField v-model="form.phone" label="연락처" placeholder="02-1234-5678" :error="errors.phone" />
        <FormField
          v-model="form.email"
          label="이메일"
          placeholder="hq@example.com"
          :error="errors.email"
        />
        <FormField
          v-model="form.address"
          class="grow"
          label="주소"
          placeholder="서울 영등포구 여의대로 1"
        />
        <FormField v-model="form.zipCode" label="우편번호" placeholder="07326" :error="errors.zipCode" />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <template #footer>
        <span class="left small dim">사업자등록번호 중복과 참조 무결성은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="회사 삭제"
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
