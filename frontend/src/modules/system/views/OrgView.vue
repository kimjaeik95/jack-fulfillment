<script setup>
/**
 * 조직 관리 (COM-PG-003) — 실제 서버 API 연동.
 *
 * 조직은 수십 건 규모의 기준정보라 전체를 한 번 받아 두고 검색·정렬·페이징은
 * 화면에서 처리한다. 상위 조직 드롭다운도 같은 목록을 쓰기 때문에, 서버에서
 * 걸러 받으면 목록을 두 번 읽어야 한다.
 *
 * 판정은 모두 서버가 한다. 여기서 막는 것은 왕복을 줄이기 위한 편의일 뿐이고,
 * 계층 규칙·순환 참조·참조 무결성은 저장 시 서버가 다시 검사한다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as orgApi from '@/api/org.js'
import { useOrgStore } from '@/stores/org.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import * as exportApi from '@/api/export.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const orgStore = useOrgStore()
const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const loadError = ref('')
/** 등록 직후 새 행이 있는 페이지로 이동시키기 위한 참조 */
const table = ref(null)

const filters = reactive({ keyword: '', orgType: '', companyId: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', orgType: '', companyId: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    // 소속 회사 드롭다운이 회사 목록을 쓴다. 회사를 못 읽는 역할도 조직
    // 화면에 들어올 수 있으므로, 실패해도 화면은 계속 그린다.
    await hierarchy.loadCompanies(force)
    await orgStore.load(force)
    if (orgStore.denyReason) loadError.value = orgStore.denyReason
  } catch (e) {
    loadError.value = e.message
  }
}

// 진입 시에는 App 이 이미 읽어둔 목록을 그대로 쓴다 (같은 요청을 두 번 보내지 않는다)
onMounted(() => reload(false))

/**
 * 회사가 하나뿐이면 고르게 할 이유가 없다. 기본값으로 채워 둔다.
 * 단일 법인 운영이 1차 범위이므로 대부분 이 경우다.
 */
function defaultCompanyId() {
  return hierarchy.companies.length === 1 ? hierarchy.companies[0].companyId : ''
}

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return orgStore.orgs
    .filter((o) => !filters.orgType || o.orgType === filters.orgType)
    .filter((o) => !filters.companyId || o.companyId === filters.companyId)
    .filter((o) => !filters.useYn || o.useYn === filters.useYn)
    .filter(
      (o) =>
        !kw ||
        [o.orgId, o.orgName, o.managerName].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
    .map((o) => ({ ...o, parentLabel: o.parentName ?? '-' }))
})

const columns = [
  { key: 'orgId', label: '조직코드', width: '104px', sortable: true, cls: 'code' },
  { key: 'orgName', label: '조직명', width: '160px', sortable: true },
  { key: 'orgType', label: '유형', width: '92px', align: 'center', sortable: true },
  { key: 'companyName', label: '회사', width: '120px', sortable: true },
  { key: 'parentLabel', label: '상위 조직', width: '128px' },
  { key: 'managerName', label: '책임자', width: '86px' },
  { key: 'childCount', label: '하위', width: '56px', align: 'right', sortable: true },
  { key: 'userCount', label: '인원', width: '56px', align: 'right', sortable: true },
  { key: 'plantCount', label: '플랜트', width: '64px', align: 'right', sortable: true },
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
  perm: 'SYS_COMPANY',
  pk: 'orgId',
  label: '조직',
  nameOf: (o) => `${o.orgName}(${o.orgId})`,
  api: {
    create: (payload) => orgApi.create(payload),
    update: (orgId, payload) => orgApi.update(orgId, payload),
    remove: (orgId) => orgApi.remove(orgId, '조직 삭제'),
  },
  // 등록 직후 새 행이 정렬상 뒤로 밀려 1페이지에 안 보이면, 저장했는데도
  // 아무 일도 없었던 것처럼 보인다. 해당 페이지로 옮겨준다.
  async afterChange({ action, key }) {
    await reload()
    // 회사 목록의 소속 조직 수가 낡는다
    hierarchy.invalidate('companies')
    if (action === "create") {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    orgId: '',
    orgName: '',
    orgType: 'DC',
    companyId: defaultCompanyId(),
    parentId: 'HQ001',
    managerName: '',
    phone: '',
    address: '',
    zipCode: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    orgId: row.orgId,
    orgName: row.orgName,
    orgType: row.orgType,
    companyId: row.companyId ?? '',
    parentId: row.parentId ?? '',
    managerName: row.managerName ?? '',
    phone: row.phone ?? '',
    address: row.address ?? '',
    zipCode: row.zipCode ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    // 본사는 상위를 가질 수 없다. 유형을 본사로 바꾸면 이전에 고른 상위가 남아 있으므로 지운다.
    parentId: f.orgType === 'HQ' ? null : f.parentId || null,
    zipCode: f.zipCode || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.orgId?.trim()) e.orgId = '조직코드는 필수입니다.'
    else if (!/^[A-Z]{2}\d{3}$/.test(f.orgId)) e.orgId = '영문 대문자 2자 + 숫자 3자 형식. 예) DC003'
    else if (ctx.mode === 'create' && orgStore.orgs.some((o) => o.orgId === f.orgId))
      e.orgId = '이미 사용 중인 조직코드입니다.'
    if (!f.orgName?.trim()) e.orgName = '조직명은 필수입니다.'
    else if (orgStore.orgs.some((o) => o.orgName === f.orgName.trim() && o.orgId !== f.orgId))
      e.orgName = '이미 사용 중인 조직명입니다.'
    if (!f.orgType) e.orgType = '조직유형을 선택하세요.'
    if (!f.companyId) e.companyId = '소속 회사를 선택하세요.'
    if (f.orgType === 'HQ' && f.parentId) e.parentId = '회사는 상위 조직을 가질 수 없습니다.'
    if (f.orgType !== 'HQ' && !f.parentId) e.parentId = '회사가 아닌 조직은 상위 조직이 필요합니다.'
    if (f.zipCode && !/^\d{5}$/.test(f.zipCode)) e.zipCode = '우편번호는 숫자 5자리입니다.'
    if (f.parentId && f.parentId === f.orgId) e.parentId = '자기 자신을 상위 조직으로 지정할 수 없습니다.'
    if (f.phone && !/^\d{2,3}-\d{3,4}-\d{4}$/.test(f.phone))
      e.phone = '02-1234-5678 형식으로 입력하세요.'
    return e
  },
})

/** 상위 조직 후보 — 자기 자신은 뺀다. 자기 하위는 서버가 순환 참조로 거른다. */
const parentOptions = computed(() =>
  orgStore.orgs
    .filter((o) => o.orgId !== form.value.orgId)
    .map((o) => ({ value: o.orgId, label: `${o.orgName} (${o.orgId})` })),
)

/**
 * 조직유형을 바꾸면 소속 사용자의 역할이 배정 범위를 벗어날 수 있다.
 * 누가 걸리는지는 서버만 알 수 있으므로(사용자별 역할까지 봐야 한다),
 * 여기서는 확인이 필요하다는 사실만 미리 알린다.
 */
const typeChangeNotice = computed(() => {
  if (mode.value !== 'edit') return ''
  const original = orgStore.orgMap[form.value.orgId]
  if (!original || original.orgType === form.value.orgType) return ''
  if (!original.userCount) return ''
  return `소속 인원 ${original.userCount}명이 있습니다. 저장 시 이들의 역할이 새 조직유형의 배정 범위에 맞는지 서버가 확인하며, 벗어나면 저장이 거부됩니다.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  const blockers = []
  if (row.childCount) blockers.push(`하위 조직 ${row.childCount}개`)
  if (row.userCount) blockers.push(`소속 사용자 ${row.userCount}명`)
  // 플랜트가 딸려 있으면 지울 수 없다 — 재고의 원천이 소속 조직을 잃는다
  if (row.plantCount) blockers.push(`운영 플랜트 ${row.plantCount}개`)
  return blockers.length
    ? `${blockers.join(', ')}이(가) 있어 삭제할 수 없습니다. 더 이상 쓰지 않는 조직이라면 사용여부를 '미사용'으로 바꾸세요.`
    : '소속 사용자 · 하위 조직 · 운영 플랜트가 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('SYS_COMPANY', 'R'))

/**
 * 데이터 범위 안내 (COM-PG-004).
 *
 * 전사 범위가 아니면 목록에 일부만 나온다. 그게 설정 때문인지 고장인지
 * 사용자는 구분할 수 없으므로 화면이 말해 준다.
 */
const scopeNotice = computed(() => session.scopeNotice('SYS_COMPANY'))
/* ------------------------------------------------------------------ */
/* CSV 다운로드 (COM-PG-011)                                           */
/* ------------------------------------------------------------------ */

/**
 * 조회할 수 있다고 내려받아도 되는 것은 아니다. 파일로 나간 데이터는
 * 회수할 수 없어 서버가 다운로드 액션(X)을 따로 판정한다.
 */
const downloadDenyReason = computed(() => session.denyReason('SYS_COMPANY', 'X'))
const downloading = ref('')

/**
 * 화면이 보고 있는 검색 조건 그대로 내보낸다 — 화면과 파일이 달라지면 안 된다.
 *
 * @param {'xlsx'|'csv'} format 엑셀이 기본. 받은 파일을 고쳐 다시 올리는
 *   흐름이 있어 CSV 로 주면 "CSV 로 다시 저장"을 시키게 된다.
 */
async function downloadAs(format) {
  downloading.value = format
  try {
    await exportApi.orgs({ ...filters }, format)
    toast.success('현재 검색 조건으로 내려받았습니다. 다운로드 사실은 감사 기록 대상입니다.')
  } catch (e) {
    toast.error(e.message)
  } finally {
    downloading.value = ''
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">조직 관리</h1>
        <p class="page-desc">
          회사 아래의 본사·물류센터 조직을 관리합니다. 조직은 사람이 속하는 단위이고, 조직유형은 역할 배정 범위(적용범위)와 데이터 범위 제한의 기준이 됩니다.
          물리적인 거점은 <strong>플랜트 관리</strong>에서 다룹니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-primary" :disabled="!canCreate" :title="createDenyReason ?? '조직 등록'" @click="openCreate()">
          + 조직 등록
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

    <!-- 목록이 왜 일부만 보이는지 알려준다 (COM-PG-004) -->
    <div v-if="scopeNotice" class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span><span>{{ scopeNotice }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="filters.keyword" class="grow" label="검색어" placeholder="조직코드 / 조직명 / 책임자" />
        <FormField
          v-model="filters.orgType"
          label="조직유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('ORG_TYPE')"
        />
        <FormField
          v-model="filters.companyId"
          label="회사"
          type="select"
          empty-option="전체"
          :options="hierarchy.companyOptions"
        />
        <FormField v-model="filters.useYn" label="사용" type="select" empty-option="전체" :options="codeOptions('USE_YN')" />
        <div class="toolbar-actions">
          <button
            class="btn"
            :disabled="!!downloading || !!downloadDenyReason"
            :title="downloadDenyReason ?? '현재 검색 조건으로 엑셀 내려받기'"
            @click="downloadAs('xlsx')"
          >
            <span v-if="downloading === 'xlsx'" class="spinner"></span>
            ⬇ 엑셀
          </button>
          <button
            class="btn"
            :disabled="!!downloading || !!downloadDenyReason"
            :title="downloadDenyReason ?? '현재 검색 조건으로 CSV 내려받기'"
            @click="downloadAs('csv')"
          >
            <span v-if="downloading === 'csv'" class="spinner"></span>
            CSV
          </button>
          <button class="btn" @click="resetFilters">초기화</button>
          <button class="btn" :disabled="orgStore.loading" @click="reload(true)">
            <span v-if="orgStore.loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="orgId"
        :page-size="10"
        :muted-when="(o) => o.useYn !== 'Y'"
        empty-text="조건에 맞는 조직이 없습니다."
      >
        <template #cell-orgType="{ value }">
          <CodeBadge group="ORG_TYPE" :code="value" />
        </template>

        <template #cell-managerName="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
        </template>

        <template #cell-useYn="{ value }">
          <CodeBadge group="USE_YN" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button class="btn btn-sm" :disabled="!canUpdate" :title="updateDenyReason ?? '수정'" @click="openEdit(row)">
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
      :title="mode === 'create' ? '조직 등록' : '조직 수정'"
      :subtitle="mode === 'edit' ? form.orgId : '조직코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.orgId"
          label="조직코드"
          required
          mono
          placeholder="DC003"
          :disabled="mode === 'edit'"
          :error="errors.orgId"
          help="영문 대문자 2자 + 숫자 3자 (예: DC003)"
        />
        <FormField v-model="form.orgName" label="조직명" required placeholder="여의도점" :error="errors.orgName" />
        <FormField
          v-model="form.orgType"
          label="조직유형"
          type="select"
          required
          :options="codeOptions('ORG_TYPE')"
          :error="errors.orgType"
          help="역할 배정 가능 범위를 결정합니다."
        />
        <FormField
          v-model="form.companyId"
          label="소속 회사"
          type="select"
          required
          empty-option="선택하세요"
          :options="hierarchy.companyOptions"
          :error="errors.companyId"
          help="모든 조직은 어느 회사에 속합니다."
        />
        <FormField
          v-model="form.parentId"
          label="상위 조직"
          type="select"
          :empty-option="form.orgType === 'HQ' ? '없음 (최상위)' : '선택하세요'"
          :options="parentOptions"
          :disabled="form.orgType === 'HQ'"
          :error="errors.parentId"
        />
        <FormField v-model="form.managerName" label="책임자" placeholder="홍길동" />
        <FormField v-model="form.phone" label="연락처" placeholder="02-1234-5678" :error="errors.phone" />
        <FormField v-model="form.address" class="grow" label="주소" placeholder="서울 영등포구 여의대로 1" />
        <FormField
          v-model="form.zipCode"
          label="우편번호"
          placeholder="07326"
          :error="errors.zipCode"
        />

        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="typeChangeNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ typeChangeNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">계층 규칙과 중복은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="조직 삭제"
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
