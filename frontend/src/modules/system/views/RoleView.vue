<script setup>
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import { useAdminStore } from '@/stores/admin.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const admin = useAdminStore()
const router = useRouter()

const DATA_SCOPES = [
  { value: 'ALL', label: '전사 (모든 조직 데이터)' },
  { value: 'OWN_ORG', label: '소속 조직 (센터/매장 한정)' },
  { value: 'OWN_DATA', label: '본인 등록 데이터' },
]

const filters = reactive({ keyword: '', orgScope: '', useYn: '' })

function resetFilters() {
  filters.keyword = ''
  filters.orgScope = ''
  filters.useYn = ''
}

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return admin.roles
    .filter((r) => !filters.orgScope || r.orgScope === filters.orgScope)
    .filter((r) => !filters.useYn || r.useYn === filters.useYn)
    .filter(
      (r) =>
        !kw ||
        [r.roleId, r.roleName, r.summary, r.restriction].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
    .map((r) => ({
      ...r,
      permCount: admin.grantsOf(r.roleId).length,
      userCount: admin.userCountOf(r.roleId),
      policyCount: admin.policiesOf(r.roleId).filter((p) => p.useYn === 'Y').length,
    }))
    .sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))
})

const columns = [
  { key: 'roleId', label: '역할코드', width: '140px', sortable: true, cls: 'code' },
  { key: 'roleName', label: '역할명', width: '130px', sortable: true },
  { key: 'orgScope', label: '적용범위', width: '86px', align: 'center', sortable: true },
  { key: 'summary', label: '주요 권한', width: '240px' },
  { key: 'restriction', label: '제한/승인', width: '230px' },
  { key: 'permCount', label: '권한', width: '58px', align: 'right', sortable: true },
  { key: 'policyCount', label: '정책', width: '58px', align: 'right', sortable: true },
  { key: 'userCount', label: '사용자', width: '62px', align: 'right', sortable: true },
  { key: 'useYn', label: '사용', width: '68px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '150px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  entity: 'roles',
  perm: 'SYS_ROLE',
  pk: 'roleId',
  label: '역할',
  nameOf: (r) => `${r.roleName}(${r.roleId})`,
  blank: () => ({
    roleId: '',
    roleName: '',
    orgScope: 'HQ',
    dataScope: 'ALL',
    summary: '',
    restriction: '',
    sortOrder: (admin.roles.length + 1) * 10,
    useYn: 'Y',
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.roleId?.trim()) e.roleId = '역할코드는 필수입니다.'
    else if (!/^[A-Z][A-Z0-9_]{2,29}$/.test(f.roleId))
      e.roleId = '영문 대문자로 시작하는 3~30자 (숫자·밑줄 허용). 예) STORE_MGR'
    else if (ctx.mode === 'create' && admin.roles.some((r) => r.roleId === f.roleId))
      e.roleId = '이미 사용 중인 역할코드입니다.'
    if (!f.roleName?.trim()) e.roleName = '역할명은 필수입니다.'
    else if (admin.roles.some((r) => r.roleName === f.roleName.trim() && r.roleId !== f.roleId))
      e.roleName = '이미 사용 중인 역할명입니다.'
    if (!f.summary?.trim()) e.summary = '주요 권한 요약은 필수입니다.'
    if (!f.orgScope) e.orgScope = '적용범위를 선택하세요.'
    if (Number(f.sortOrder) < 0) e.sortOrder = '0 이상으로 입력하세요.'
    return e
  },
})

/** 수정 중인 역할의 현재 권한/정책/사용자 (모달 하단 참고 영역) */
const editingInfo = computed(() => {
  const id = form.value.roleId
  if (mode.value === 'create' || !id) return null
  return {
    perms: admin.grantsOf(id),
    policies: admin.policiesOf(id),
    users: admin.users.filter((u) => u.roleIds?.includes(id)),
  }
})

function goMapping(row) {
  router.push({ name: 'role-permissions', query: { roleId: row.roleId } })
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

    <div v-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>{{ createDenyReason }}</span>
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
          <button class="btn" @click="resetFilters">초기화</button>
        </div>
      </div>

      <DataTable
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

        <template #cell-summary="{ value }">
          <span class="truncate" :title="value">{{ value }}</span>
        </template>

        <template #cell-restriction="{ row, value }">
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
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
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
          placeholder="매장 관리자"
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
          v-model="form.dataScope"
          label="데이터 범위"
          type="select"
          :options="DATA_SCOPES"
          help="조회·변경이 허용되는 데이터 범위"
        />
        <FormField
          v-model="form.summary"
          label="주요 권한 (요약)"
          type="textarea"
          required
          span
          :rows="2"
          placeholder="보충요청·이동 승인·매장실사"
          :error="errors.summary"
          help="목록에 표시되는 역할 설명입니다."
        />
        <FormField
          v-model="form.restriction"
          label="제한/승인 사항 (요약)"
          type="textarea"
          span
          :rows="2"
          placeholder="타 매장 재고 직접 수정 금지"
          help="실제 통제는 공통정책에 등록해야 동작합니다."
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" :error="errors.sortOrder" />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="editingInfo" class="card mt-2">
        <div class="card-head"><span class="card-title">연결 정보</span></div>
        <div class="card-body">
          <div class="grid-2">
            <div>
              <div class="field-label mb-1">부여된 권한 ({{ editingInfo.perms.length }})</div>
              <div v-if="editingInfo.perms.length" class="chip-list">
                <span v-for="m in editingInfo.perms" :key="m.permId" class="chip">
                  {{ admin.permNameOf(m.permId) }}
                  <span class="mono dim">{{ m.actions.join('') }}</span>
                </span>
              </div>
              <span v-else class="dim small">부여된 권한이 없습니다.</span>
            </div>
            <div>
              <div class="field-label mb-1">적용 정책 ({{ editingInfo.policies.length }})</div>
              <div v-if="editingInfo.policies.length" class="flex-col">
                <div v-for="p in editingInfo.policies" :key="p.policyId" class="small">
                  <CodeBadge group="POLICY_TYPE" :code="p.policyType" />
                  <CodeBadge group="ENFORCE_LEVEL" :code="p.enforceLevel" />
                  <span class="muted"> {{ p.policyName }}</span>
                </div>
              </div>
              <span v-else class="dim small">등록된 정책이 없습니다.</span>
            </div>
          </div>
          <div v-if="editingInfo.users.length" class="alert mt-2">
            <span class="alert-icon">ℹ</span>
            <span>
              이 역할을 배정받은 사용자 {{ editingInfo.users.length }}명 —
              {{ editingInfo.users.slice(0, 6).map((u) => u.userName).join(', ')
              }}{{ editingInfo.users.length > 6 ? ` 외 ${editingInfo.users.length - 6}명` : '' }}
            </span>
          </div>
        </div>
      </div>

      <template #footer>
        <span class="left small dim">필수 항목(*)을 모두 입력해야 저장됩니다.</span>
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
      detail="배정된 사용자나 연결된 공통정책이 있으면 삭제할 수 없습니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>
