<script setup>
/**
 * 역할 관리 (COM-PG-004) — 실제 서버 API 연동.
 *
 * 역할은 수십 건 규모의 기준정보라 전체를 한 번 받아 두고 검색·정렬·페이징은
 * 화면에서 처리한다.
 *
 * 사용중지·삭제가 곧바로 사용자의 권한 상실로 이어지므로, 영향 범위(권한·정책·
 * 사용자 건수)를 목록에서 함께 받아 미리 보여준다. 최종 판정과 경고문은 서버가 만든다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as roleApi from '@/api/role.js'
import * as orgApi from '@/api/org.js'
import * as orgScopeApi from '@/api/roleOrgScope.js'
import { useRoleStore } from '@/stores/role.js'
import { useAdminStore } from '@/stores/admin.js'
import * as exportApi from '@/api/export.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const roleStore = useRoleStore()
// 권한·정책 목록은 아직 Mock — 모달 하단 '연결 정보'에서만 쓴다
const admin = useAdminStore()
const session = useSessionStore()
const toast = useToastStore()
const router = useRouter()

const DATA_SCOPES = [
  { value: 'ALL', label: '전사 (모든 조직 데이터)' },
  { value: 'OWN_ORG', label: '소속 조직 (물류센터 한정)' },
  { value: 'OWN_DATA', label: '본인 등록 데이터' },
]

const loadError = ref('')
/** 등록 직후 새 행이 있는 페이지로 이동시키기 위한 참조 */
const table = ref(null)
const filters = reactive({ keyword: '', orgScope: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', orgScope: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    await roleStore.load(force)
    if (roleStore.denyReason) loadError.value = roleStore.denyReason
  } catch (e) {
    loadError.value = e.message
  }
}

// 진입 시에는 App 이 이미 읽어둔 목록을 그대로 쓴다
onMounted(() => reload(false))

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return roleStore.roles
    .filter((r) => !filters.orgScope || r.orgScope === filters.orgScope)
    .filter((r) => !filters.useYn || r.useYn === filters.useYn)
    .filter(
      (r) =>
        !kw ||
        [r.roleId, r.roleName, r.description, r.restrictionSummary].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
})

const columns = [
  { key: 'roleId', label: '역할코드', width: '134px', sortable: true, cls: 'code' },
  { key: 'roleName', label: '역할명', width: '126px', sortable: true },
  { key: 'orgScope', label: '적용범위', width: '84px', align: 'center', sortable: true },
  { key: 'description', label: '주요 권한', width: '230px' },
  { key: 'restrictionSummary', label: '제한/승인', width: '215px' },
  { key: 'permCount', label: '권한', width: '56px', align: 'right', sortable: true },
  { key: 'policyCount', label: '정책', width: '56px', align: 'right', sortable: true },
  { key: 'userCount', label: '사용자', width: '60px', align: 'right', sortable: true },
  { key: 'useYn', label: '사용', width: '64px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '150px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'SYS_ROLE',
  pk: 'roleId',
  label: '역할',
  nameOf: (r) => `${r.roleName}(${r.roleId})`,
  api: {
    create: (payload) => roleApi.create(payload),
    update: (roleId, payload) => roleApi.update(roleId, payload),
    remove: (roleId) => roleApi.remove(roleId, '역할 삭제'),
  },
  // 등록 직후 새 행이 정렬상 뒤로 밀려 1페이지에 안 보이면, 저장했는데도
  // 아무 일도 없었던 것처럼 보인다. 해당 페이지로 옮겨준다.
  async afterChange({ action, key }) {
    await reload()
    if (action === "create") {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    roleId: '',
    roleName: '',
    description: '',
    orgScope: 'HQ',
    defaultDataScope: 'ALL',
    restrictionSummary: '',
    sortOrder: (roleStore.roles.length + 1) * 10,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    roleId: row.roleId,
    roleName: row.roleName,
    description: row.description ?? '',
    orgScope: row.orgScope,
    defaultDataScope: row.defaultDataScope,
    restrictionSummary: row.restrictionSummary ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({ ...f, sortOrder: Number(f.sortOrder) || 0 }),
  validate(f, ctx) {
    const e = {}
    if (!f.roleId?.trim()) e.roleId = '역할코드는 필수입니다.'
    else if (!/^[A-Z][A-Z0-9_]{2,29}$/.test(f.roleId))
      e.roleId = '영문 대문자로 시작하는 3~30자 (숫자·밑줄 허용). 예) STORE_MGR'
    else if (ctx.mode === 'create' && roleStore.roles.some((r) => r.roleId === f.roleId))
      e.roleId = '이미 사용 중인 역할코드입니다.'
    if (!f.roleName?.trim()) e.roleName = '역할명은 필수입니다.'
    else if (roleStore.roles.some((r) => r.roleName === f.roleName.trim() && r.roleId !== f.roleId))
      e.roleName = '이미 사용 중인 역할명입니다.'
    if (!f.description?.trim()) e.description = '주요 권한 요약은 필수입니다.'
    if (!f.orgScope) e.orgScope = '적용범위를 선택하세요.'
    if (!f.defaultDataScope) e.defaultDataScope = '데이터 범위를 선택하세요.'
    if (Number(f.sortOrder) < 0) e.sortOrder = '0 이상으로 입력하세요.'
    return e
  },
})

/** 수정 중인 역할의 현재 상태 — 서버가 함께 내려준 건수 */
const editing = computed(() =>
  mode.value === 'edit' ? (roleStore.roleMap[form.value.roleId] ?? null) : null,
)

/**
 * 적용범위를 바꾸면 이미 배정된 사용자가 범위를 벗어날 수 있다.
 * 누가 걸리는지는 서버만 알 수 있으므로(사용자별 소속까지 봐야 한다),
 * 여기서는 확인이 필요하다는 사실만 미리 알린다.
 */
const scopeChangeNotice = computed(() => {
  const before = editing.value
  if (!before || before.orgScope === form.value.orgScope) return ''
  if (!before.userCount) return ''
  return `이 역할을 배정받은 사용자 ${before.userCount}명이 있습니다. 저장 시 이들의 소속이 새 적용범위에 맞는지 서버가 확인하며, 벗어나면 저장이 거부됩니다.`
})

/** 사용중지는 곧 권한 상실이다. 저장 전에 알린다. */
const disableNotice = computed(() => {
  const before = editing.value
  if (!before || before.useYn !== 'Y' || form.value.useYn !== 'N') return ''
  if (!before.userCount) return ''
  return `미사용으로 바꾸면 배정된 사용자 ${before.userCount}명은 다음 로그인부터 이 역할의 권한을 잃습니다. 로그인 시 유효권한은 사용중인 역할만 모아 계산하기 때문입니다.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  const blockers = []
  if (row.userCount) blockers.push(`배정된 사용자 ${row.userCount}명`)
  if (row.policyCount) blockers.push(`연결된 공통정책 ${row.policyCount}건`)
  if (blockers.length) {
    return `${blockers.join(', ')}이(가) 있어 삭제할 수 없습니다. 먼저 정리한 뒤 다시 시도하세요.`
  }
  return row.permCount
    ? `부여된 권한 ${row.permCount}종의 매핑도 함께 삭제됩니다.`
    : '배정된 사용자나 연결된 공통정책이 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('SYS_ROLE', 'R'))

function goMapping(row) {
  router.push({ name: 'role-permissions', query: { roleId: row.roleId } })
}

/* ------------------------------------------------------------------ */
/* 조직범위 — 겸직 (COM-PG-004)                                        */
/* ------------------------------------------------------------------ */

/**
 * 데이터 범위가 '소속 조직' 일 때 기본은 그 사람이 속한 조직과 그 하위다.
 * 대부분은 그것으로 맞는다 — 이천 담당자는 이천만 본다.
 *
 * 맞지 않는 경우가 겸직이다. 한 사람이 이천과 김해를 함께 맡으면 소속은
 * 하나인데 봐야 할 곳은 둘이다. 그 둘째 조직을 여기서 연다.
 *
 * 권한 매핑처럼 별도 화면으로 빼지 않았다. 매핑은 권한 수십 개짜리
 * 매트릭스지만 조직범위는 보통 한두 줄이라, 화면을 옮겨 다니게 하는
 * 비용이 더 크다.
 */
const scopeOpen = ref(false)
const scopeRole = ref(null)
const scopeInfo = ref(null)
const scopeRows = ref([])
const scopeReason = ref('')
const scopeError = ref('')
const scopeBusy = ref(false)
const orgs = ref([])

async function openScopes(row) {
  scopeRole.value = row
  scopeError.value = ''
  scopeReason.value = ''
  scopeOpen.value = true
  scopeInfo.value = null
  scopeRows.value = []
  try {
    // 사용중인 조직만 고를 수 있다. 사용중지된 곳은 지정해도 열리지 않는다.
    if (!orgs.value.length) {
      const data = await orgApi.list({ useYn: 'Y', size: 0 })
      orgs.value = data.rows
    }
    const info = await orgScopeApi.fetchScopes(row.roleId)
    scopeInfo.value = info
    scopeRows.value = info.scopes.map((s) => ({
      orgId: s.orgId,
      includeChildYn: s.includeChildYn,
    }))
  } catch (e) {
    scopeError.value = e.message
  }
}

const scopeOrgOptions = computed(() =>
  orgs.value.map((o) => ({ value: o.orgId, label: `${o.orgName} (${o.orgId})` })),
)

/** 이미 담은 조직은 다시 못 고르게 한다 — 같은 조직 두 줄은 저장이 거부된다 */
const scopeAvailable = computed(() => {
  const taken = new Set(scopeRows.value.map((r) => r.orgId))
  return scopeOrgOptions.value.filter((o) => !taken.has(o.value))
})

function addScopeRow() {
  const first = scopeAvailable.value[0]
  if (!first) return
  scopeRows.value = [...scopeRows.value, { orgId: first.value, includeChildYn: 'Y' }]
}

function removeScopeRow(i) {
  scopeRows.value = scopeRows.value.filter((_, idx) => idx !== i)
}

const orgNameOf = (orgId) => orgs.value.find((o) => o.orgId === orgId)?.orgName ?? orgId

/** 한 조직을 두 줄에 담았나 — 서버도 거부하지만 저장 전에 보여 준다 */
const scopeDuplicated = computed(() => {
  const seen = new Set()
  const dup = new Set()
  for (const r of scopeRows.value) {
    if (seen.has(r.orgId)) dup.add(r.orgId)
    seen.add(r.orgId)
  }
  return [...dup]
})

const scopeValid = computed(() => !scopeDuplicated.value.length && !scopeBusy.value)

async function submitScopes() {
  scopeBusy.value = true
  scopeError.value = ''
  try {
    const { scope, warning } = await orgScopeApi.save(
      scopeRole.value.roleId,
      scopeRows.value.map((r) => ({ orgId: r.orgId, includeChildYn: r.includeChildYn })),
      scopeReason.value || null,
    )
    toast.success(
      scope.scopes.length
        ? `${scope.roleName} — 조직 ${scope.scopes.length} 곳을 열었습니다.`
        : `${scope.roleName} 의 겸직 지정을 모두 해제했습니다.`,
    )
    if (warning) toast.warn(warning)
    scopeOpen.value = false
  } catch (e) {
    scopeError.value = e.message
  } finally {
    scopeBusy.value = false
  }
}
/* ------------------------------------------------------------------ */
/* CSV 다운로드 (COM-PG-011)                                           */
/* ------------------------------------------------------------------ */

/**
 * 조회할 수 있다고 내려받아도 되는 것은 아니다. 파일로 나간 데이터는
 * 회수할 수 없어 서버가 다운로드 액션(X)을 따로 판정한다.
 */
const downloadDenyReason = computed(() => session.denyReason('SYS_ROLE', 'X'))
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
    await exportApi.roles({ ...filters }, format)
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
        <h1 class="page-title">역할 관리</h1>
        <p class="page-desc">
          업무 역할을 정의하고 적용범위·데이터범위·제한사항을 관리합니다. 개별 기능 권한은
          <RouterLink :to="{ name: 'role-permissions' }">역할-권한 매핑</RouterLink>에서, 승인·금지 규칙은
          <RouterLink :to="{ name: 'policies' }">공통정책</RouterLink>에서 관리합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '역할 등록'"
          @click="openCreate()"
        >
          + 역할 등록
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
          placeholder="역할코드 / 역할명 / 권한 / 제한사항"
        />
        <FormField
          v-model="filters.orgScope"
          label="적용범위"
          type="select"
          empty-option="전체"
          :options="codeOptions('ORG_TYPE')"
        />
        <FormField
          v-model="filters.useYn"
          label="사용여부"
          type="select"
          empty-option="전체"
          :options="codeOptions('USE_YN')"
        />
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
          <button class="btn" :disabled="roleStore.loading" @click="reload(true)">
            <span v-if="roleStore.loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="roleId"
        :page-size="10"
        :muted-when="(r) => r.useYn !== 'Y'"
        empty-text="조건에 맞는 역할이 없습니다."
      >
        <template #cell-orgScope="{ value }">
          <CodeBadge group="ORG_TYPE" :code="value" />
        </template>

        <template #cell-description="{ value }">
          <span class="truncate" :title="value">{{ value }}</span>
        </template>

        <template #cell-restrictionSummary="{ row, value }">
          <span v-if="value" class="truncate" :title="value">{{ value }}</span>
          <span v-else class="dim">-</span>
          <span
            v-if="row.policyCount === 0 && value"
            class="badge badge-amber plain"
            title="제한사항이 공통정책으로 등록되지 않아 실제 통제가 동작하지 않습니다."
          >
            정책 미등록
          </span>
        </template>

        <template #cell-permCount="{ row, value }">
          <a href="#" :title="`${row.roleName}의 권한 매핑 보기`" @click.prevent="goMapping(row)">{{ value }}</a>
        </template>

        <template #cell-useYn="{ value }">
          <CodeBadge group="USE_YN" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button class="btn btn-sm" title="권한 매핑 화면으로 이동" @click="goMapping(row)">권한</button>
            <button
              class="btn btn-sm"
              :disabled="!canUpdate"
              :title="updateDenyReason ?? '겸직으로 열어 줄 조직 지정'"
              @click="openScopes(row)"
            >
              조직범위
            </button>
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

    <!-- 등록/수정 -->
    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '역할 등록' : '역할 수정'"
      :subtitle="mode === 'edit' ? form.roleId : '역할코드는 등록 후 변경할 수 없습니다.'"
      size="wide"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.roleId"
          label="역할코드"
          required
          mono
          placeholder="STORE_MGR"
          :disabled="mode === 'edit'"
          :error="errors.roleId"
          help="영문 대문자·숫자·밑줄"
        />
        <FormField
          v-model="form.roleName"
          label="역할명"
          required
          placeholder="센터 관리자"
          :error="errors.roleName"
        />
        <FormField
          v-model="form.orgScope"
          label="적용범위(소속유형)"
          type="select"
          required
          :options="codeOptions('ORG_TYPE')"
          :error="errors.orgScope"
          help="이 역할을 배정할 수 있는 조직유형"
        />
        <FormField
          v-model="form.defaultDataScope"
          label="데이터 범위"
          type="select"
          required
          :options="DATA_SCOPES"
          :error="errors.defaultDataScope"
          help="조회·변경이 허용되는 데이터 범위"
        />
        <FormField
          v-model="form.description"
          label="주요 권한 (요약)"
          type="textarea"
          required
          span
          :rows="2"
          placeholder="입고검수·재고조정 승인·실사"
          :error="errors.description"
          help="목록에 표시되는 역할 설명입니다."
        />
        <FormField
          v-model="form.restrictionSummary"
          label="제한/승인 사항 (요약)"
          type="textarea"
          span
          :rows="2"
          placeholder="재고조정 한도 초과 시 본사 승인"
          help="실제 통제는 공통정책에 등록해야 동작합니다."
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" :error="errors.sortOrder" />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="scopeChangeNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ scopeChangeNotice }}</span>
      </div>
      <div v-if="disableNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ disableNotice }}</span>
      </div>

      <div v-if="editing" class="card mt-2">
        <div class="card-head"><span class="card-title">연결 정보</span></div>
        <div class="card-body">
          <div class="grid-2">
            <div>
              <div class="field-label mb-1">부여된 권한 ({{ editing.permCount }})</div>
              <div v-if="admin.grantsOf(editing.roleId).length" class="chip-list">
                <span v-for="m in admin.grantsOf(editing.roleId)" :key="m.permId" class="chip">
                  {{ admin.permNameOf(m.permId) }}
                  <span class="mono dim">{{ m.actions.join('') }}</span>
                </span>
              </div>
              <span v-else class="dim small">부여된 권한이 없습니다.</span>
            </div>
            <div>
              <div class="field-label mb-1">적용 정책 ({{ editing.policyCount }})</div>
              <div v-if="admin.policiesOf(editing.roleId).length" class="flex-col">
                <div v-for="p in admin.policiesOf(editing.roleId)" :key="p.policyId" class="small">
                  <CodeBadge group="POLICY_TYPE" :code="p.policyType" />
                  <CodeBadge group="ENFORCE_LEVEL" :code="p.enforceLevel" />
                  <span class="muted"> {{ p.policyName }}</span>
                </div>
              </div>
              <span v-else class="dim small">등록된 정책이 없습니다.</span>
            </div>
          </div>
          <div v-if="editing.userCount" class="alert mt-2">
            <span class="alert-icon">ℹ</span>
            <span>이 역할을 배정받은 사용자 {{ editing.userCount }}명 (퇴사자 제외)</span>
          </div>
          <div class="small dim mt-1">
            권한·정책 상세는 아직 화면 임시 데이터입니다. 건수는 서버 값입니다.
          </div>
        </div>
      </div>

      <template #footer>
        <span class="left small dim">중복·적용범위·영향 범위는 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="역할 삭제"
      :message="deleteMessage"
      :detail="deleteDetail"
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />

    <!-- ── 조직범위 (겸직) ──────────────────────────────────── -->
    <ModalDialog
      v-if="scopeOpen"
      title="조직범위"
      :subtitle="scopeRole ? `${scopeRole.roleName} (${scopeRole.roleId})` : ''"
      size="wide"
      @close="scopeOpen = false"
    >
      <div v-if="scopeError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ scopeError }}</span>
      </div>

      <p class="small">
        이 역할을 가진 사람은 <strong>자기 소속 조직과 그 하위</strong>를 기본으로 봅니다.
        여기서 지정한 조직은 <strong>거기에 더해</strong> 열립니다 — 한 사람이 두 센터를
        겸해 맡는 경우에 씁니다. 비워 두면 소속 조직만 봅니다.
      </p>

      <!-- 지정해도 안 열리는 경우를 저장 전에 말해 준다 -->
      <div v-if="scopeInfo && scopeInfo.ownOrgGrantCount === 0" class="alert alert-warn mb-2 mt-2">
        <span class="alert-icon">⚠</span>
        <span>
          이 역할에는 '소속 조직' 범위로 동작하는 권한이 없어 <strong>지정해도 열리지
          않습니다.</strong> 역할의 데이터 범위가
          <strong>{{ DATA_SCOPES.find((s) => s.value === scopeInfo.defaultDataScope)?.label
            ?? scopeInfo.defaultDataScope }}</strong>
          입니다 — 겸직을 열려면 '소속 조직' 이어야 합니다.
        </span>
      </div>
      <div v-else-if="scopeInfo" class="detail-head mt-2">
        <span>
          데이터 범위
          <strong>{{ DATA_SCOPES.find((s) => s.value === scopeInfo.defaultDataScope)?.label
            ?? scopeInfo.defaultDataScope }}</strong>
        </span>
        <span>
          이 역할 사용자
          <strong :class="scopeInfo.userCount ? 'warn' : 'dim'">{{ scopeInfo.userCount }}</strong> 명
        </span>
      </div>

      <div class="lines-head">
        <strong>열어 줄 조직 {{ scopeRows.length }} 곳</strong>
        <button
          class="btn btn-sm btn-primary"
          :disabled="!scopeAvailable.length"
          :title="scopeAvailable.length ? '조직 추가' : '더 담을 조직이 없습니다'"
          @click="addScopeRow()"
        >
          + 조직 담기
        </button>
      </div>

      <div v-if="!scopeRows.length" class="empty-note">
        지정한 조직이 없습니다. 이 역할을 가진 사람은 자기 소속 조직만 봅니다.
      </div>

      <table v-else class="table lines">
        <thead>
          <tr>
            <th style="width: 260px">조직</th>
            <th style="width: 150px">하위 포함</th>
            <th>설명</th>
            <th style="width: 40px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in scopeRows" :key="`${r.orgId}-${i}`">
            <td>
              <select v-model="r.orgId" class="select">
                <option v-for="o in scopeOrgOptions" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </td>
            <td>
              <select v-model="r.includeChildYn" class="select">
                <option value="Y">하위까지 포함</option>
                <option value="N">이 조직만</option>
              </select>
            </td>
            <!-- 하위 포함이 기본인 이유를 화면에서 한 번 더 말해 준다 -->
            <td class="small dim">
              <template v-if="r.includeChildYn === 'Y'">
                {{ orgNameOf(r.orgId) }} 아래 조직이 나중에 생겨도 자동으로 포함됩니다.
              </template>
              <template v-else>
                {{ orgNameOf(r.orgId) }} 만 열립니다. 아래 조직은 따로 지정해야 합니다.
              </template>
            </td>
            <td><button class="btn btn-sm btn-danger" @click="removeScopeRow(i)">×</button></td>
          </tr>
          <tr v-if="scopeDuplicated.length">
            <td colspan="4" class="small danger">
              같은 조직이 두 번 있습니다 ({{ scopeDuplicated.join(', ') }}) — 하위 포함 여부가
              둘이면 어느 쪽이 맞는지 정할 수 없습니다.
            </td>
          </tr>
        </tbody>
      </table>

      <div class="form-grid mt-2">
        <FormField
          v-model="scopeReason"
          label="변경 사유"
          placeholder="예: 김해센터 겸임 발령"
          help="감사로그에 남습니다. 권한을 넓히는 변경이라 나중에 반드시 '왜 줬나' 를 묻게 됩니다."
        />
      </div>

      <template #footer>
        <span class="left small dim">
          저장하면 이 역할을 가진 사람이 <strong>다음 로그인부터</strong> 적용됩니다.
        </span>
        <button class="btn" :disabled="scopeBusy" @click="scopeOpen = false">닫기</button>
        <button class="btn btn-primary" :disabled="!scopeValid" @click="submitScopes()">
          <span v-if="scopeBusy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
/* 조직범위 모달 — 다른 줄 편집 화면(구매오더 · 구매요청)과 같은 모양으로 맞춘다 */
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
.lines .select {
  min-height: 28px;
  padding: 3px 6px;
  font-size: 13px;
  width: 100%;
}
.empty-note {
  padding: 24px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
