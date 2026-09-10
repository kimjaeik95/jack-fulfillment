<script setup>
/**
 * 사용자 관리 (COM-PG-002) — 실제 API 연동.
 *
 * 사내 시스템이므로 자가 가입이 없다. 계정은 SYS_USER 권한을 가진 역할만
 * 만들 수 있고, 초기 비밀번호도 관리자가 정한다.
 *
 * 목록의 검색·필터·페이징·정렬은 모두 서버가 처리한다.
 * 전체를 받아 화면에서 자르면 사용자가 늘어났을 때 감당할 수 없다.
 *
 * 조직·역할 목록은 아직 Mock(admin 스토어)을 쓴다.
 * 해당 기능의 API 가 만들어지면 그때 교체한다.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as userApi from '@/api/user.js'
import { useAdminStore } from '@/stores/admin.js'
import { useOrgStore } from '@/stores/org.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const admin = useAdminStore()
// 조직은 실서버, 역할은 아직 Mock — 두 출처가 섞여 있는 동안 각각 명시한다
const orgStore = useOrgStore()
const session = useSessionStore()
const toast = useToastStore()

/* ------------------------------------------------------------------ */
/* 목록 — 서버 조회                                                     */
/* ------------------------------------------------------------------ */

const filters = reactive({ keyword: '', orgId: '', roleId: '', status: '' })
const paging = reactive({ page: 1, size: 10, sortBy: 'userId', sortDir: 'asc' })

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await userApi.list({ ...filters, ...paging })
    rows.value = result.rows
    total.value = result.total
  } catch (e) {
    // 권한 부족(403)도 여기로 온다. 사유를 그대로 보여준다.
    loadError.value = e.message
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 검색 조건이 바뀌면 1페이지로 되돌린다 (2페이지에서 조건을 바꾸면 빈 화면이 나온다) */
function applySearch() {
  paging.page = 1
  load()
}

function resetFilters() {
  Object.assign(filters, { keyword: '', orgId: '', roleId: '', status: '' })
  applySearch()
}

watch(() => [paging.page, paging.size, paging.sortBy, paging.sortDir], load)

onMounted(() => {
  load()
  // 소속 드롭다운은 실제 조직 목록을 써야 한다. 화면마다 출처가 다르면
  // 한쪽에만 있는 조직을 골라 저장할 때마다 서버에 거부당한다.
  orgStore.load()
})

const totalPages = computed(() => (paging.size > 0 ? Math.max(1, Math.ceil(total.value / paging.size)) : 1))

const pageNumbers = computed(() => {
  const tp = totalPages.value
  const cur = paging.page
  const from = Math.max(1, Math.min(cur - 2, tp - 4))
  const to = Math.min(tp, Math.max(cur + 2, 5))
  const out = []
  for (let i = from; i <= to; i++) out.push(i)
  return out
})

function goPage(p) {
  paging.page = Math.min(Math.max(1, p), totalPages.value)
}

function toggleSort(column) {
  if (!column.sortable) return
  const key = column.sortKey ?? column.key
  if (paging.sortBy === key) {
    paging.sortDir = paging.sortDir === 'asc' ? 'desc' : 'asc'
  } else {
    paging.sortBy = key
    paging.sortDir = 'asc'
  }
}

const columns = [
  { key: 'userId', label: '사용자ID', width: '120px', sortable: true, cls: 'code' },
  { key: 'userName', label: '이름', width: '95px', sortable: true },
  { key: 'orgName', label: '소속', width: '140px', sortable: true },
  { key: 'deptName', label: '부서/직위', width: '150px' },
  { key: 'roleNames', label: '배정 역할', width: '210px' },
  { key: 'email', label: '이메일', width: '175px' },
  { key: 'approvalLimit', label: '승인한도', width: '100px', align: 'right', sortable: true },
  { key: 'status', label: '상태', width: '80px', align: 'center', sortable: true },
  { key: 'lastLoginAt', label: '최근접속', width: '130px', sortable: true },
  { key: '_act', label: '', width: '210px', align: 'right' },
]

/* ------------------------------------------------------------------ */
/* 권한 게이팅 — 근거는 서버가 준 grants                                 */
/* ------------------------------------------------------------------ */

const canCreate = computed(() => session.can('SYS_USER', 'C'))
const canUpdate = computed(() => session.can('SYS_USER', 'U'))
const canDelete = computed(() => session.can('SYS_USER', 'D'))
const createDenyReason = computed(() => session.denyReason('SYS_USER', 'C'))
const updateDenyReason = computed(() => session.denyReason('SYS_USER', 'U'))
const deleteDenyReason = computed(() => session.denyReason('SYS_USER', 'D'))

const isSelf = (row) => row.userId === session.currentUserId

/* ------------------------------------------------------------------ */
/* 등록 · 수정                                                          */
/* ------------------------------------------------------------------ */

const dlgOpen = ref(false)
const mode = ref('create')
const busy = ref(false)
const serverError = ref('')
const errors = reactive({})

const blankForm = () => ({
  userId: '',
  userName: '',
  password: '',
  orgId: '',
  email: '',
  phone: '',
  deptName: '',
  positionName: '',
  status: 'ACTIVE',
  approvalLimit: 0,
  roleIds: [],
  useYn: 'Y',
  reason: '',
})

const form = reactive(blankForm())

function setForm(values) {
  Object.assign(form, blankForm(), values)
  Object.keys(errors).forEach((k) => delete errors[k])
  serverError.value = ''
}

function openCreate() {
  if (!canCreate.value) {
    toast.error(createDenyReason.value)
    return
  }
  mode.value = 'create'
  setForm({})
  dlgOpen.value = true
}

function openEdit(row) {
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  mode.value = 'edit'
  setForm({
    userId: row.userId,
    userName: row.userName,
    orgId: row.orgId,
    email: row.email ?? '',
    phone: row.phone ?? '',
    deptName: row.deptName ?? '',
    positionName: row.positionName ?? '',
    status: row.status,
    approvalLimit: row.approvalLimit ?? 0,
    roleIds: [...(row.roleIds ?? [])],
    useYn: row.useYn ?? 'Y',
  })
  dlgOpen.value = true
}

/** 소속 조직유형에 배정 가능한 역할만 노출 (서버 검증과 같은 규칙) */
const assignableRoles = computed(() => {
  const orgType = orgStore.orgMap[form.orgId]?.orgType
  return admin.roles
    .filter((r) => r.useYn === 'Y')
    .map((r) => ({
      value: r.roleId,
      label: r.roleName,
      disabled: !!orgType && r.orgScope !== orgType,
      title: orgType && r.orgScope !== orgType
        ? `${r.roleName}은(는) ${orgTypeLabel(r.orgScope)} 소속에만 배정할 수 있습니다.`
        : r.summary,
    }))
    .sort((a, b) => Number(a.disabled) - Number(b.disabled))
})

function orgTypeLabel(t) {
  return { HQ: '본사', DC: '물류센터', WAREHOUSE: '창고', STORE: '매장' }[t] ?? t
}

/** 소속을 바꾸면 범위를 벗어난 역할 배정을 정리한다 */
function onOrgChange(orgId) {
  form.orgId = orgId
  const orgType = orgStore.orgMap[orgId]?.orgType
  if (!orgType) return
  const kept = form.roleIds.filter((rid) => admin.roleMap[rid]?.orgScope === orgType)
  if (kept.length !== form.roleIds.length) {
    const dropped = form.roleIds.filter((rid) => !kept.includes(rid)).map((r) => admin.roleNameOf(r))
    form.roleIds = kept
    toast.warn(`소속 변경으로 배정 범위를 벗어난 역할을 해제했습니다: ${dropped.join(', ')}`)
  }
}

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (mode.value === 'create') {
    if (!form.userId.trim()) errors.userId = '사용자ID는 필수입니다.'
    else if (!/^[a-z][a-z0-9._-]{2,29}$/.test(form.userId))
      errors.userId = '영문 소문자로 시작하는 3~30자 (숫자 . _ - 허용)'
    if (!form.password) errors.password = '초기 비밀번호는 필수입니다.'
  }
  if (!form.userName.trim()) errors.userName = '이름은 필수입니다.'
  if (!form.orgId) errors.orgId = '소속 조직을 선택하세요.'
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
    errors.email = '이메일 형식이 올바르지 않습니다.'
  if (form.phone && !/^\d{2,3}-\d{3,4}-\d{4}$/.test(form.phone))
    errors.phone = '010-1234-5678 형식으로 입력하세요.'
  if (!form.roleIds.length) errors.roleIds = '역할을 1개 이상 배정하세요.'
  if (Number(form.approvalLimit) < 0) errors.approvalLimit = '0 이상으로 입력하세요.'
  return Object.keys(errors).length === 0
}

async function submit() {
  serverError.value = ''
  if (!validate()) {
    toast.warn('입력값을 확인하세요.')
    return
  }

  busy.value = true
  try {
    const payload = { ...form, approvalLimit: Number(form.approvalLimit) }
    if (mode.value === 'create') {
      await userApi.create(payload)
      toast.success(
        `'${form.userName}' 계정을 만들었습니다. 초기 비밀번호를 담당자에게 전달하세요. ` +
          '담당자는 최초 로그인 시 반드시 변경해야 합니다.',
      )
    } else {
      delete payload.password
      await userApi.update(form.userId, payload)
      toast.success('사용자 정보를 수정했습니다.')
    }
    dlgOpen.value = false
    await load()
  } catch (e) {
    // 업무 규칙 위반(직무분리·조직범위·비밀번호 정책)은 모달을 닫지 않고 사유를 보여준다
    serverError.value = e.message
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* ------------------------------------------------------------------ */
/* 잠금 해제 · 퇴사 처리 · 비밀번호 초기화                                */
/* ------------------------------------------------------------------ */

async function unlock(row) {
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  try {
    await userApi.unlock(row.userId, '관리자 잠금 해제')
    toast.success(`'${row.userName}' 계정 잠금을 해제했습니다. 실패 횟수도 초기화됐습니다.`)
    await load()
  } catch (e) {
    toast.error(e.message)
  }
}

const retireTarget = ref(null)
const retireReason = ref('')
const retiring = ref(false)

function askRetire(row) {
  if (!canDelete.value) {
    toast.error(deleteDenyReason.value)
    return
  }
  retireTarget.value = row
  retireReason.value = ''
}

async function doRetire() {
  retiring.value = true
  try {
    await userApi.retire(retireTarget.value.userId, retireReason.value || '퇴사 처리')
    toast.success(`'${retireTarget.value.userName}' 계정을 퇴사 처리했습니다.`)
    retireTarget.value = null
    await load()
  } catch (e) {
    toast.error(e.message)
  } finally {
    retiring.value = false
  }
}

const pwdTarget = ref(null)
const pwdValue = ref('')
const pwdReason = ref('')
const pwdBusy = ref(false)
const pwdError = ref('')

function askResetPassword(row) {
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  pwdTarget.value = row
  pwdValue.value = ''
  pwdReason.value = ''
  pwdError.value = ''
}

async function doResetPassword() {
  pwdError.value = ''
  if (!pwdValue.value) {
    pwdError.value = '새 비밀번호를 입력하세요.'
    return
  }
  pwdBusy.value = true
  try {
    await userApi.resetPassword(pwdTarget.value.userId, pwdValue.value, pwdReason.value || '비밀번호 초기화')
    toast.success(
      `'${pwdTarget.value.userName}' 비밀번호를 초기화했습니다. ` +
        '담당자는 다음 로그인 시 반드시 변경해야 합니다.',
    )
    pwdTarget.value = null
    await load()
  } catch (e) {
    pwdError.value = e.message
  } finally {
    pwdBusy.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">사용자 관리</h1>
        <p class="page-desc">
          사내 시스템이므로 자가 가입은 없습니다. 계정은 관리자가 만들고 초기 비밀번호를 정하며,
          담당자는 최초 로그인 시 반드시 변경해야 합니다.
          역할은 소속 조직유형과 일치해야 하고, 직무분리 위반은 저장 시 서버가 차단합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-primary" :disabled="!canCreate" :title="createDenyReason ?? '사용자 등록'" @click="openCreate">
          + 사용자 등록
        </button>
      </div>
    </div>

    <div v-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>
    <div v-if="session.isMasked" class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span>
      <span>개인정보 마스킹 정책이 적용된 계정입니다. 서버가 이름·이메일·연락처를 가려서 내려보냅니다.</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="filters.keyword" class="grow" label="검색어" placeholder="ID / 이름 / 이메일 / 부서" />
        <FormField v-model="filters.orgId" label="소속" type="select" empty-option="전체" :options="orgStore.orgOptions" />
        <FormField v-model="filters.roleId" label="역할" type="select" empty-option="전체" :options="admin.roleOptions" />
        <FormField
          v-model="filters.status"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('USER_STATUS')"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" @click="applySearch">조회</button>
          <button class="btn" @click="resetFilters">초기화</button>
        </div>
      </div>

      <div v-if="loading" class="loading-bar"></div>

      <div v-if="loadError" class="alert alert-danger" style="margin: 14px">
        <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <colgroup>
            <col v-for="c in columns" :key="`col-${c.key}`" :style="c.width ? { width: c.width } : null" />
          </colgroup>
          <thead>
            <tr>
              <th
                v-for="c in columns"
                :key="c.key"
                :class="[c.align === 'center' ? 'center' : '', c.align === 'right' ? 'right' : '', c.sortable ? 'sortable' : '']"
                @click="toggleSort(c)"
              >
                {{ c.label }}
                <span v-if="paging.sortBy === c.key" class="sort-arrow">{{ paging.sortDir === 'asc' ? '▲' : '▼' }}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.userId" :class="{ muted: row.status === 'RETIRED' || row.useYn === 'N' }">
              <td class="code">{{ row.userId }}</td>
              <td>
                {{ row.userName }}
                <span v-if="isSelf(row)" class="badge badge-blue plain" title="현재 접속 계정">본인</span>
              </td>
              <td>
                {{ row.orgName }}
                <CodeBadge group="ORG_TYPE" :code="row.orgType" />
              </td>
              <td>
                <span>{{ row.deptName || '-' }}</span>
                <span v-if="row.positionName" class="dim"> / {{ row.positionName }}</span>
              </td>
              <td>
                <div class="chip-list">
                  <span v-for="name in row.roleNames" :key="name" class="chip">{{ name }}</span>
                  <span v-if="!row.roleNames?.length" class="badge badge-red plain">역할 없음</span>
                </div>
              </td>
              <td><span class="small">{{ row.email || '-' }}</span></td>
              <td class="num">
                <span v-if="row.approvalLimit > 0">{{ row.approvalLimit.toLocaleString() }}원</span>
                <span v-else class="dim">-</span>
              </td>
              <td class="center">
                <CodeBadge group="USER_STATUS" :code="row.status" />
                <div
                  v-if="row.loginFailCount > 0"
                  class="small"
                  :class="row.status === 'LOCKED' ? 'field-error' : 'dim'"
                  :title="`비밀번호 연속 오류 ${row.loginFailCount}회`"
                >
                  실패 {{ row.loginFailCount }}회
                </div>
                <div v-if="row.mustChangePassword" class="small dim" title="최초 로그인 시 비밀번호를 변경해야 합니다">
                  변경 대기
                </div>
              </td>
              <td><span class="small dim">{{ row.lastLoginAt ? row.lastLoginAt.replace('T', ' ').slice(0, 16) : '-' }}</span></td>
              <td class="actions">
                <div class="btn-row" style="justify-content: flex-end">
                  <button
                    v-if="row.status === 'LOCKED'"
                    class="btn btn-sm"
                    :disabled="!canUpdate"
                    title="잠금을 해제하고 실패 횟수를 초기화합니다."
                    @click="unlock(row)"
                  >
                    잠금해제
                  </button>
                  <button
                    class="btn btn-sm"
                    :disabled="!canUpdate"
                    :title="updateDenyReason ?? '비밀번호를 새로 지정합니다.'"
                    @click="askResetPassword(row)"
                  >
                    비번초기화
                  </button>
                  <button class="btn btn-sm" :disabled="!canUpdate" :title="updateDenyReason ?? '수정'" @click="openEdit(row)">
                    수정
                  </button>
                  <button
                    class="btn btn-sm btn-danger"
                    :disabled="!canDelete || isSelf(row) || row.status === 'RETIRED'"
                    :title="isSelf(row) ? '본인 계정은 퇴사 처리할 수 없습니다.' : (deleteDenyReason ?? '퇴사 처리')"
                    @click="askRetire(row)"
                  >
                    퇴사
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length && !loading">
              <td :colspan="columns.length">
                <div class="table-empty">
                  <span class="table-empty-icon">🗂</span>
                  조건에 맞는 사용자가 없습니다.
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="total > 0" class="pager">
        <span>총 <strong>{{ total.toLocaleString() }}</strong>건</span>
        <select v-model.number="paging.size" class="select" style="width: 88px; min-height: 26px" @change="paging.page = 1">
          <option :value="10">10건</option>
          <option :value="20">20건</option>
          <option :value="50">50건</option>
        </select>
        <div class="pager-pages">
          <button class="pager-btn" :disabled="paging.page === 1" @click="goPage(1)">«</button>
          <button class="pager-btn" :disabled="paging.page === 1" @click="goPage(paging.page - 1)">‹</button>
          <button
            v-for="p in pageNumbers"
            :key="p"
            class="pager-btn"
            :class="{ active: p === paging.page }"
            @click="goPage(p)"
          >
            {{ p }}
          </button>
          <button class="pager-btn" :disabled="paging.page === totalPages" @click="goPage(paging.page + 1)">›</button>
          <button class="pager-btn" :disabled="paging.page === totalPages" @click="goPage(totalPages)">»</button>
        </div>
      </div>
    </div>

    <!-- 등록 / 수정 -->
    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '사용자 등록' : '사용자 수정'"
      :subtitle="mode === 'edit' ? form.userId : '사용자ID는 등록 후 변경할 수 없습니다.'"
      size="wide"
      @close="dlgOpen = false"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.userId"
          label="사용자ID"
          required
          mono
          placeholder="st4.stf1"
          :disabled="mode === 'edit'"
          :error="errors.userId"
        />
        <FormField v-model="form.userName" label="이름" required placeholder="홍길동" :error="errors.userName" />

        <FormField
          v-if="mode === 'create'"
          v-model="form.password"
          label="초기 비밀번호"
          type="password"
          required
          span
          :error="errors.password"
          help="8자 이상, 영문·숫자·특수문자 중 2종류 이상. 담당자가 최초 로그인 시 변경합니다."
        />

        <FormField v-model="form.email" label="이메일" placeholder="user@corp.co.kr" :error="errors.email" />
        <FormField v-model="form.phone" label="연락처" placeholder="010-1234-5678" :error="errors.phone" />

        <div class="field">
          <label class="field-label">소속 조직<span class="req">*</span></label>
          <select class="select" :class="{ invalid: !!errors.orgId }" :value="form.orgId" @change="onOrgChange($event.target.value)">
            <option value="">선택하세요</option>
            <option v-for="o in orgStore.orgOptions" :key="o.value" :value="o.value" :disabled="o.disabled">
              {{ o.label }}
            </option>
          </select>
          <span v-if="errors.orgId" class="field-error">{{ errors.orgId }}</span>
          <span v-else class="field-help">소속유형에 따라 배정 가능한 역할이 달라집니다.</span>
        </div>

        <FormField v-model="form.deptName" label="부서" placeholder="강남점" />
        <FormField v-model="form.positionName" label="직위" placeholder="사원" />
        <FormField v-model="form.status" label="상태" type="select" required :options="codeOptions('USER_STATUS')" />
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

        <FormField
          v-model="form.reason"
          label="처리 사유"
          span
          placeholder="신규 입사자 계정 발급 / 부서 이동 등"
          help="감사 이력에 기록됩니다."
        />
      </div>

      <template #footer>
        <span class="left small dim">직무분리·조직범위·비밀번호 정책은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="dlgOpen = false">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <!-- 비밀번호 초기화 -->
    <ModalDialog
      v-if="pwdTarget"
      title="비밀번호 초기화"
      :subtitle="`${pwdTarget.userName} (${pwdTarget.userId})`"
      size="narrow"
      @close="pwdTarget = null"
    >
      <div class="alert alert-warn mb-2">
        <span class="alert-icon">⚠</span>
        <span>새 비밀번호를 담당자에게 안전한 경로로 전달하세요. 담당자는 다음 로그인 시 반드시 변경해야 합니다.</span>
      </div>
      <FormField
        v-model="pwdValue"
        label="새 비밀번호"
        type="password"
        required
        help="8자 이상, 영문·숫자·특수문자 중 2종류 이상."
      />
      <FormField v-model="pwdReason" label="처리 사유" placeholder="비밀번호 분실 문의" help="감사 이력에 기록됩니다." />
      <div v-if="pwdError" class="alert alert-danger">
        <span class="alert-icon">⛔</span><span style="white-space: pre-line">{{ pwdError }}</span>
      </div>

      <template #footer>
        <button class="btn" :disabled="pwdBusy" @click="pwdTarget = null">취소</button>
        <button class="btn btn-primary" :disabled="pwdBusy" @click="doResetPassword">
          <span v-if="pwdBusy" class="spinner"></span>
          초기화
        </button>
      </template>
    </ModalDialog>

    <!-- 퇴사 처리 -->
    <ModalDialog
      v-if="retireTarget"
      title="퇴사 처리"
      :subtitle="`${retireTarget.userName} (${retireTarget.userId})`"
      size="narrow"
      @close="retireTarget = null"
    >
      <div class="alert alert-info mb-2">
        <span class="alert-icon">ℹ</span>
        <span>
          계정을 삭제하지 않고 상태를 <strong>퇴사</strong>로 바꾸고 사용여부를 내립니다.
          감사로그와 전표에 남은 행위자를 추적할 수 있어야 하기 때문입니다. 로그인은 즉시 차단됩니다.
        </span>
      </div>
      <FormField v-model="retireReason" label="처리 사유" placeholder="2026-09-30 퇴사" help="감사 이력에 기록됩니다." />

      <template #footer>
        <button class="btn" :disabled="retiring" @click="retireTarget = null">취소</button>
        <button class="btn btn-danger" :disabled="retiring" @click="doRetire">
          <span v-if="retiring" class="spinner"></span>
          퇴사 처리
        </button>
      </template>
    </ModalDialog>
  </div>
</template>
