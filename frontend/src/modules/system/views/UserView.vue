<script setup>
import { computed, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import { useAdminStore } from '@/stores/admin.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const admin = useAdminStore()
const session = useSessionStore()
const toast = useToastStore()

const filters = reactive({ keyword: '', orgId: '', roleId: '', status: '' })

function resetFilters() {
  filters.keyword = ''
  filters.orgId = ''
  filters.roleId = ''
  filters.status = ''
}

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return admin.users
    .filter((u) => !filters.orgId || u.orgId === filters.orgId)
    .filter((u) => !filters.roleId || u.roleIds?.includes(filters.roleId))
    .filter((u) => !filters.status || u.status === filters.status)
    .filter(
      (u) =>
        !kw ||
        [u.userId, u.userName, u.email, u.deptName, u.position].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
    .map((u) => ({
      ...u,
      orgName: admin.orgNameOf(u.orgId),
      orgType: admin.orgMap[u.orgId]?.orgType ?? null,
      roleNames: (u.roleIds ?? []).map((r) => admin.roleNameOf(r)).join(', '),
    }))
})

const columns = [
  { key: 'userId', label: '사용자ID', width: '120px', sortable: true, cls: 'code' },
  { key: 'userName', label: '이름', width: '90px', sortable: true },
  { key: 'orgName', label: '소속', width: '140px', sortable: true },
  { key: 'deptName', label: '부서/직위', width: '160px' },
  { key: 'roleIds', label: '배정 역할', width: '230px' },
  { key: 'email', label: '이메일', width: '180px' },
  { key: 'approvalLimit', label: '승인한도', width: '100px', align: 'right', sortable: true },
  { key: 'status', label: '상태', width: '76px', align: 'center', sortable: true },
  { key: 'lastLoginAt', label: '최근접속', width: '130px', sortable: true },
  { key: '_act', label: '', width: '160px', align: 'right' },
]

function labelOfOrgType(t) {
  return { HQ: '본사', DC: '물류센터', STORE: '매장' }[t] ?? t
}

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  entity: 'users',
  perm: 'SYS_USER',
  pk: 'userId',
  label: '사용자',
  nameOf: (u) => `${u.userName}(${u.userId})`,
  blank: () => ({
    userId: '',
    userName: '',
    email: '',
    phone: '',
    orgId: '',
    deptName: '',
    position: '',
    roleIds: [],
    status: 'ACTIVE',
    approvalLimit: 0,
    lastLoginAt: '',
    useYn: 'Y',
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.userId?.trim()) e.userId = '사용자ID는 필수입니다.'
    else if (!/^[a-z][a-z0-9._-]{2,29}$/.test(f.userId))
      e.userId = '영문 소문자로 시작하는 3~30자 (숫자 . _ - 허용)'
    else if (ctx.mode === 'create' && admin.users.some((u) => u.userId === f.userId))
      e.userId = '이미 사용 중인 사용자ID입니다.'
    if (!f.userName?.trim()) e.userName = '이름은 필수입니다.'
    if (!f.orgId) e.orgId = '소속 조직을 선택하세요.'
    if (f.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) e.email = '이메일 형식이 올바르지 않습니다.'
    else if (f.email && admin.users.some((u) => u.email === f.email && u.userId !== f.userId))
      e.email = '이미 사용 중인 이메일입니다.'
    if (f.phone && !/^\d{2,3}-\d{3,4}-\d{4}$/.test(f.phone)) e.phone = '010-1234-5678 형식으로 입력하세요.'
    if (!f.roleIds?.length) e.roleIds = '역할을 1개 이상 배정하세요.'
    if (Number(f.approvalLimit) < 0) e.approvalLimit = '0 이상으로 입력하세요.'
    return e
  },
})

/** 소속 조직유형에 배정 가능한 역할만 노출 (서버 검증과 동일 규칙) */
const assignableRoles = computed(() => {
  const orgType = admin.orgMap[form.value.orgId]?.orgType
  return admin.roles
    .filter((r) => r.useYn === 'Y')
    .map((r) => ({
      value: r.roleId,
      label: r.roleName,
      disabled: !!orgType && r.orgScope !== orgType,
      title:
        orgType && r.orgScope !== orgType
          ? `${r.roleName}은(는) ${labelOfOrgType(r.orgScope)} 소속에만 배정할 수 있습니다.`
          : r.summary,
    }))
    .sort((a, b) => Number(a.disabled) - Number(b.disabled))
})

/** 소속을 바꾸면 조직유형에 맞지 않는 역할 배정을 자동으로 정리한다. */
function onOrgChange(orgId) {
  form.value.orgId = orgId
  const orgType = admin.orgMap[orgId]?.orgType
  if (!orgType) return
  const kept = (form.value.roleIds ?? []).filter((rid) => admin.roleMap[rid]?.orgScope === orgType)
  if (kept.length !== (form.value.roleIds ?? []).length) {
    const dropped = (form.value.roleIds ?? [])
      .filter((rid) => !kept.includes(rid))
      .map((rid) => admin.roleNameOf(rid))
    form.value.roleIds = kept
    toast.warn(`소속 변경으로 배정 범위를 벗어난 역할을 해제했습니다: ${dropped.join(', ')}`)
  }
}

/** 배정된 역할로부터 유효 권한·정책을 미리 계산해서 보여준다. */
const effective = computed(() => {
  const ids = form.value.roleIds ?? []
  const grantMap = {}
  for (const m of admin.rolePermissions) {
    if (!ids.includes(m.roleId)) continue
    grantMap[m.permId] ??= new Set()
    for (const a of m.actions) grantMap[m.permId].add(a)
  }
  const perms = Object.entries(grantMap)
    .map(([permId, set]) => ({
      permId,
      permName: admin.permNameOf(permId),
      module: admin.permMap[permId]?.module,
      actions: [...set].sort(),
    }))
    .sort((a, b) => String(a.module).localeCompare(String(b.module)))
  const policies = admin.policies.filter((p) => ids.includes(p.roleId) && p.useYn === 'Y')
  return { perms, policies }
})

/** 상태 변경 (잠금 해제 등). 잠금 해제 시 로그인 실패 횟수도 함께 초기화한다. */
async function changeStatus(row, status) {
  if (!session.can('SYS_USER', 'U')) {
    toast.error(session.denyReason('SYS_USER', 'U'))
    return
  }
  try {
    const patch = status === 'ACTIVE' ? { status, loginFailCount: 0 } : { status }
    await admin.updateRow('users', row.userId, patch)
    toast.success(`'${row.userName}' 상태를 변경했습니다.`)
  } catch (e) {
    toast.error(e.message)
  }
}

/** 비밀번호 초기화 (+ 잠금 해제) */
const pwdResetTarget = ref(null)
const pwdResetting = ref(false)

function askPwdReset(row) {
  if (!session.can('SYS_USER', 'U')) {
    toast.error(session.denyReason('SYS_USER', 'U'))
    return
  }
  pwdResetTarget.value = row
}

async function doPwdReset() {
  pwdResetting.value = true
  try {
    const { initialPassword } = await admin.resetUserPassword(pwdResetTarget.value.userId)
    toast.success(
      `'${pwdResetTarget.value.userName}' 비밀번호를 초기화했습니다. 초기 비밀번호: ${initialPassword}\n최초 로그인 후 변경을 안내하세요.`,
    )
    pwdResetTarget.value = null
  } catch (e) {
    toast.error(e.message)
  } finally {
    pwdResetting.value = false
  }
}

const isSelf = (row) => row.userId === session.currentUserId
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">사용자 관리</h1>
        <p class="page-desc">
          사용자의 소속·역할·승인한도·상태를 관리합니다. 역할은 소속 조직유형과 일치해야 하며, 요청 역할과 승인 역할을
          동시에 배정하는 직무분리 위반은 저장 시 차단됩니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-primary" :disabled="!canCreate" :title="createDenyReason ?? '사용자 등록'" @click="openCreate()">
          + 사용자 등록
        </button>
      </div>
    </div>

    <div v-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>
    <div v-if="session.isMasked" class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span>
      <span>개인정보 마스킹 정책(P010)이 적용된 계정입니다. 이름·이메일·연락처가 가려져 표시됩니다.</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="filters.keyword" class="grow" label="검색어" placeholder="ID / 이름 / 이메일 / 부서" />
        <FormField v-model="filters.orgId" label="소속" type="select" empty-option="전체" :options="admin.orgOptions" />
        <FormField v-model="filters.roleId" label="역할" type="select" empty-option="전체" :options="admin.roleOptions" />
        <FormField
          v-model="filters.status"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('USER_STATUS')"
        />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="userId"
        :page-size="10"
        :muted-when="(u) => u.status === 'RETIRED' || u.useYn === 'N'"
        empty-text="조건에 맞는 사용자가 없습니다."
      >
        <template #cell-userName="{ row, value }">
          {{ session.mask(value, 'name') }}
          <span v-if="isSelf(row)" class="badge badge-blue plain" title="현재 접속 계정">본인</span>
        </template>

        <template #cell-orgName="{ row, value }">
          {{ value }}
          <CodeBadge group="ORG_TYPE" :code="row.orgType" />
        </template>

        <template #cell-deptName="{ row }">
          <span>{{ row.deptName || '-' }}</span>
          <span v-if="row.position" class="dim"> / {{ row.position }}</span>
        </template>

        <template #cell-roleIds="{ row }">
          <div class="chip-list">
            <span
              v-for="rid in row.roleIds"
              :key="rid"
              class="chip"
              :title="admin.roleMap[rid]?.summary"
            >
              {{ admin.roleNameOf(rid) }}
            </span>
            <span v-if="!row.roleIds?.length" class="badge badge-red plain">역할 없음</span>
          </div>
        </template>

        <template #cell-email="{ value }">
          <span class="small">{{ session.mask(value, 'email') || '-' }}</span>
        </template>

        <template #cell-approvalLimit="{ value }">
          <span v-if="value > 0">{{ value.toLocaleString() }}원</span>
          <span v-else class="dim">-</span>
        </template>

        <template #cell-status="{ row, value }">
          <CodeBadge group="USER_STATUS" :code="value" />
          <div
            v-if="row.loginFailCount > 0"
            class="small"
            :class="row.status === 'LOCKED' ? 'field-error' : 'dim'"
            :title="`비밀번호 연속 오류 ${row.loginFailCount}회`"
          >
            실패 {{ row.loginFailCount }}회
          </div>
        </template>

        <template #cell-lastLoginAt="{ value }">
          <span class="small dim">{{ value || '-' }}</span>
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              v-if="row.status === 'LOCKED'"
              class="btn btn-sm"
              :disabled="!canUpdate"
              title="잠금을 해제하고 로그인 실패 횟수를 초기화합니다."
              @click="changeStatus(row, 'ACTIVE')"
            >
              잠금해제
            </button>
            <button
              class="btn btn-sm"
              :disabled="!canUpdate"
              :title="updateDenyReason ?? '비밀번호를 초기값으로 되돌립니다.'"
              @click="askPwdReset(row)"
            >
              비번초기화
            </button>
            <button class="btn btn-sm" :disabled="!canUpdate" :title="updateDenyReason ?? '수정'" @click="openEdit(row)">
              수정
            </button>
            <button
              class="btn btn-sm btn-danger"
              :disabled="!canDelete || isSelf(row)"
              :title="isSelf(row) ? '본인 계정은 삭제할 수 없습니다.' : (deleteDenyReason ?? '삭제')"
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
      :title="mode === 'create' ? '사용자 등록' : '사용자 수정'"
      :subtitle="mode === 'edit' ? form.userId : '사용자ID는 등록 후 변경할 수 없습니다.'"
      size="wide"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.userId"
          label="사용자ID"
          required
          mono
          placeholder="st1.stf1"
          :disabled="mode === 'edit'"
          :error="errors.userId"
        />
        <FormField v-model="form.userName" label="이름" required placeholder="홍길동" :error="errors.userName" />
        <FormField v-model="form.email" label="이메일" placeholder="user@corp.co.kr" :error="errors.email" />
        <FormField v-model="form.phone" label="연락처" placeholder="010-1234-5678" :error="errors.phone" />

        <div class="field">
          <label class="field-label">소속 조직<span class="req">*</span></label>
          <select class="select" :class="{ invalid: !!errors.orgId }" :value="form.orgId" @change="onOrgChange($event.target.value)">
            <option value="">선택하세요</option>
            <option v-for="o in admin.orgOptions" :key="o.value" :value="o.value" :disabled="o.disabled">
              {{ o.label }}
            </option>
          </select>
          <span v-if="errors.orgId" class="field-error">{{ errors.orgId }}</span>
          <span v-else class="field-help">소속유형에 따라 배정 가능한 역할이 달라집니다.</span>
        </div>

        <FormField v-model="form.deptName" label="부서" placeholder="강남점" />
        <FormField v-model="form.position" label="직위" placeholder="점장" />
        <FormField
          v-model="form.status"
          label="상태"
          type="select"
          required
          :options="codeOptions('USER_STATUS')"
        />
        <FormField
          v-model="form.approvalLimit"
          label="승인한도 (원)"
          type="number"
          :error="errors.approvalLimit"
          help="0 이면 승인 권한 없음. 승인한도 정책(LIMIT)과 함께 적용됩니다."
        />

        <FormField
          v-model="form.roleIds"
          label="배정 역할"
          type="checks"
          required
          span
          :options="assignableRoles"
          :error="errors.roleIds"
          :help="form.orgId ? '소속 조직유형과 다른 범위의 역할은 선택할 수 없습니다.' : '먼저 소속 조직을 선택하세요.'"
        />
      </div>

      <!-- 유효 권한 미리보기 -->
      <div class="card mt-2">
        <div class="card-head">
          <span class="card-title">배정 결과 미리보기</span>
          <span class="dim small">선택한 역할로부터 계산된 유효 권한과 적용 정책</span>
        </div>
        <div class="card-body">
          <div class="grid-2">
            <div>
              <div class="field-label mb-1">유효 권한 ({{ effective.perms.length }})</div>
              <div v-if="effective.perms.length" class="chip-list">
                <span v-for="p in effective.perms" :key="p.permId" class="chip" :title="p.permId">
                  {{ p.permName }}
                  <span class="mono dim">{{ p.actions.join('') }}</span>
                </span>
              </div>
              <span v-else class="dim small">역할을 선택하면 권한이 계산됩니다.</span>
            </div>
            <div>
              <div class="field-label mb-1">적용 정책 ({{ effective.policies.length }})</div>
              <div v-if="effective.policies.length" class="flex-col">
                <div v-for="p in effective.policies" :key="p.policyId" class="small">
                  <CodeBadge group="POLICY_TYPE" :code="p.policyType" />
                  <CodeBadge group="ENFORCE_LEVEL" :code="p.enforceLevel" />
                  <span class="muted"> {{ p.policyName }}</span>
                  <div class="dim" style="padding-left: 2px">{{ p.message }}</div>
                </div>
              </div>
              <span v-else class="dim small">적용되는 정책이 없습니다.</span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <span class="left small dim">역할·소속 정합성과 직무분리 규칙은 저장 시 다시 검증됩니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="pwdResetTarget"
      title="비밀번호 초기화"
      :message="`'${pwdResetTarget.userName}(${pwdResetTarget.userId})' 의 비밀번호를 초기값으로 되돌립니다.`"
      detail="잠긴 계정이면 잠금도 함께 해제됩니다. 초기 비밀번호는 담당자에게 별도 경로로 전달하고 최초 로그인 시 변경을 안내하세요."
      confirm-label="초기화"
      :busy="pwdResetting"
      @cancel="pwdResetTarget = null"
      @confirm="doPwdReset"
    />

    <ConfirmDialog
      v-if="askDelete"
      title="사용자 삭제"
      :message="deleteMessage"
      detail="감사 목적상 실제 운영에서는 삭제보다 '퇴사' 상태 변경을 권장합니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>
