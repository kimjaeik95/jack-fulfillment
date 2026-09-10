<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CODE_GROUPS, codeLabel, codeOptions } from '@/api/codes.js'
import { useAdminStore } from '@/stores/admin.js'
import { useRoleStore } from '@/stores/role.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const admin = useAdminStore()
// 역할 목록만 실서버를 쓴다. 권한 매핑 자체는 아직 화면 임시 데이터라,
// 목록까지 임시로 두면 역할 화면에서 새로 만든 역할이 여기 나타나지 않는다.
const roleStore = useRoleStore()
const session = useSessionStore()
const toast = useToastStore()
const route = useRoute()
const router = useRouter()

const ACTIONS = CODE_GROUPS.PERM_ACTION.map((a) => a.code)

const selectedRoleId = ref('')
/** 편집 중인 매핑: { permId: string[] } */
const draft = reactive({})
const saving = ref(false)
const askLeave = ref(null) // 저장하지 않고 다른 역할로 이동할 때
const copyFrom = ref('')
const filters = reactive({ keyword: '', module: '', onlyGranted: false })

const canUpdate = computed(() => session.can('SYS_ROLE', 'U'))
const updateDenyReason = computed(() => session.denyReason('SYS_ROLE', 'U'))

const selectedRole = computed(() => roleStore.roleMap[selectedRoleId.value] ?? null)

/** 저장된 매핑 */
const savedGrants = computed(() => (selectedRoleId.value ? admin.grantMapOf(selectedRoleId.value) : {}))

function loadDraft(roleId) {
  for (const k of Object.keys(draft)) delete draft[k]
  const saved = admin.grantMapOf(roleId)
  for (const [permId, actions] of Object.entries(saved)) draft[permId] = [...actions]
}

function selectRole(roleId, force = false) {
  if (roleId === selectedRoleId.value) return
  if (isDirty.value && !force) {
    askLeave.value = roleId
    return
  }
  selectedRoleId.value = roleId
  loadDraft(roleId)
  copyFrom.value = ''
  router.replace({ query: { ...route.query, roleId } })
}

onMounted(() => {
  const initial =
    (route.query.roleId && roleStore.roleMap[route.query.roleId] ? route.query.roleId : null) ??
    [...roleStore.roles].sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))[0]?.roleId
  if (initial) {
    selectedRoleId.value = initial
    loadDraft(initial)
  }
})

// 다른 화면에서 역할이 추가/삭제되어도 목록이 유지되도록 보정
watch(
  () => roleStore.roles.length,
  () => {
    if (selectedRoleId.value && !roleStore.roleMap[selectedRoleId.value]) {
      selectedRoleId.value = roleStore.roles[0]?.roleId ?? ''
      if (selectedRoleId.value) loadDraft(selectedRoleId.value)
    }
  },
)

/* ---------------------------------------------------------------- */
/* 변경 감지                                                         */
/* ---------------------------------------------------------------- */
const normalize = (obj) =>
  JSON.stringify(
    Object.entries(obj)
      .filter(([, v]) => v?.length)
      .map(([k, v]) => [k, [...v].sort()])
      .sort((a, b) => a[0].localeCompare(b[0])),
  )

const isDirty = computed(() => normalize(draft) !== normalize(savedGrants.value))

const changeCount = computed(() => {
  const saved = savedGrants.value
  const keys = new Set([...Object.keys(saved), ...Object.keys(draft)])
  let n = 0
  for (const k of keys) {
    const a = [...(saved[k] ?? [])].sort().join('')
    const b = [...(draft[k] ?? [])].sort().join('')
    if (a !== b) n += 1
  }
  return n
})

/* ---------------------------------------------------------------- */
/* 매트릭스 데이터                                                    */
/* ---------------------------------------------------------------- */
const visiblePerms = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return admin.permissions
    .filter((p) => p.useYn === 'Y')
    .filter((p) => !filters.module || p.module === filters.module)
    .filter((p) => !kw || [p.permId, p.permName, p.menuPath].some((v) => String(v ?? '').toLowerCase().includes(kw)))
    .filter((p) => !filters.onlyGranted || (draft[p.permId]?.length ?? 0) > 0)
})

/** 모듈 그룹 헤더가 있는 평면 목록 */
const matrixRows = computed(() => {
  const out = []
  for (const mod of CODE_GROUPS.PERM_MODULE) {
    const items = visiblePerms.value
      .filter((p) => p.module === mod.code)
      .sort((a, b) => a.permId.localeCompare(b.permId))
    if (!items.length) continue
    out.push({ type: 'module', code: mod.code, label: mod.label, color: mod.color, count: items.length })
    for (const p of items) out.push({ type: 'perm', ...p })
  }
  return out
})

function isSupported(perm, action) {
  return perm.actions?.includes(action)
}

function isChecked(permId, action) {
  return (draft[permId] ?? []).includes(action)
}

function toggle(perm, action, checked) {
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  const cur = draft[perm.permId] ? [...draft[perm.permId]] : []
  const i = cur.indexOf(action)
  if (checked && i < 0) cur.push(action)
  if (!checked && i >= 0) cur.splice(i, 1)

  // 조회(R) 없이 다른 액션만 남지 않도록 보정한다.
  if (checked && action !== 'R' && !cur.includes('R') && isSupported(perm, 'R')) cur.push('R')
  if (!checked && action === 'R' && cur.length > 0) {
    toast.warn('조회 권한은 다른 액션의 전제 조건이므로 함께 해제됩니다.')
    draft[perm.permId] = []
    return
  }
  draft[perm.permId] = cur
}

/** 행 전체 토글 */
function toggleRow(perm) {
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  const all = perm.actions ?? []
  const cur = draft[perm.permId] ?? []
  draft[perm.permId] = cur.length === all.length ? [] : [...all]
}

/** 열(액션) 전체 토글 — 현재 보이는 권한에 한정 */
function toggleColumn(action) {
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  const targets = visiblePerms.value.filter((p) => isSupported(p, action))
  const allOn = targets.every((p) => isChecked(p.permId, action))
  for (const p of targets) toggle(p, action, !allOn)
}

const grantedCount = computed(() => Object.values(draft).filter((v) => v?.length).length)
const actionTotals = computed(() =>
  Object.fromEntries(
    ACTIONS.map((a) => [a, Object.values(draft).filter((v) => v?.includes(a)).length]),
  ),
)

/* ---------------------------------------------------------------- */
/* 액션                                                             */
/* ---------------------------------------------------------------- */
async function save() {
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  saving.value = true
  try {
    const payload = Object.fromEntries(Object.entries(draft).filter(([, v]) => v?.length))
    await admin.saveRolePermissions(selectedRoleId.value, payload)
    toast.success(`'${selectedRole.value?.roleName}' 권한 매핑을 저장했습니다. (${grantedCount.value}개 권한)`)
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}

function revert() {
  loadDraft(selectedRoleId.value)
  toast.info('저장 전 상태로 되돌렸습니다.')
}

function applyCopy() {
  if (!copyFrom.value) return
  if (!canUpdate.value) {
    toast.error(updateDenyReason.value)
    return
  }
  const src = admin.grantMapOf(copyFrom.value)
  for (const k of Object.keys(draft)) delete draft[k]
  for (const [permId, actions] of Object.entries(src)) draft[permId] = [...actions]
  toast.warn(`'${roleStore.roleNameOf(copyFrom.value)}' 의 권한 구성을 복사했습니다. 저장 버튼을 눌러야 반영됩니다.`)
}

const roleListRows = computed(() =>
  [...roleStore.roles]
    .sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))
    .map((r) => ({ ...r, count: admin.grantsOf(r.roleId).length })),
)

const copyOptions = computed(() => roleStore.roleOptions.filter((o) => o.value !== selectedRoleId.value))

/** 미저장 변경을 버리고 다른 역할로 이동 */
function confirmLeave() {
  const next = askLeave.value
  askLeave.value = null
  selectRole(next, true)
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">역할-권한 매핑</h1>
        <p class="page-desc">
          역할이 각 기능에서 수행할 수 있는 액션을 체크박스로 지정합니다. 권한에 정의되지 않은 액션은 선택할 수 없고,
          조회(R)는 다른 액션의 전제 조건으로 자동 부여됩니다.
        </p>
      </div>
      <div class="page-head-actions">
        <span v-if="isDirty" class="badge badge-amber">저장 전 변경 {{ changeCount }}건</span>
        <button class="btn" :disabled="!isDirty || saving" @click="revert">되돌리기</button>
        <button
          class="btn btn-primary"
          :disabled="!isDirty || saving || !canUpdate"
          :title="updateDenyReason ?? '변경 내용 저장'"
          @click="save"
        >
          <span v-if="saving" class="spinner"></span>
          저장
        </button>
      </div>
    </div>

    <div v-if="updateDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ updateDenyReason }} (조회만 가능합니다)</span>
    </div>

    <div style="display: grid; grid-template-columns: 260px 1fr; gap: 14px; align-items: start">
      <!-- 역할 목록 -->
      <div class="card">
        <div class="card-head"><span class="card-title">역할 ({{ roleListRows.length }})</span></div>
        <div class="card-body tight">
          <div
            v-for="r in roleListRows"
            :key="r.roleId"
            class="nav-item"
            :class="{ active: r.roleId === selectedRoleId }"
            :style="r.useYn !== 'Y' ? { opacity: 0.5 } : null"
            :title="r.description"
            style="border-radius: 0; padding: 8px 12px"
            @click="selectRole(r.roleId)"
          >
            <div style="min-width: 0">
              <div class="bold" style="font-size: 12.5px">{{ r.roleName }}</div>
              <div class="mono dim" style="font-size: 10.5px">{{ r.roleId }}</div>
            </div>
            <span class="nav-count">{{ r.count }}</span>
          </div>
        </div>
      </div>

      <!-- 매트릭스 -->
      <div class="card">
        <div class="card-head">
          <div>
            <span class="card-title">{{ selectedRole?.roleName ?? '역할을 선택하세요' }}</span>
            <CodeBadge v-if="selectedRole" group="ORG_TYPE" :code="selectedRole.orgScope" />
            <span v-if="selectedRole" class="dim small">
              · 부여 {{ grantedCount }} / 전체 {{ admin.permissions.length }}
            </span>
          </div>
          <div class="card-head-actions">
            <select v-model="copyFrom" class="select" style="width: 190px" :disabled="!canUpdate">
              <option value="">다른 역할에서 복사…</option>
              <option v-for="o in copyOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
            <button class="btn btn-sm" :disabled="!copyFrom || !canUpdate" @click="applyCopy">적용</button>
          </div>
        </div>

        <div class="toolbar" style="border-radius: 0">
          <FormField v-model="filters.keyword" class="grow" label="권한 검색" placeholder="권한코드 / 권한명 / 메뉴" />
          <FormField
            v-model="filters.module"
            label="모듈"
            type="select"
            empty-option="전체"
            :options="codeOptions('PERM_MODULE')"
          />
          <div class="field">
            <label class="field-label">표시</label>
            <label class="check-line">
              <input v-model="filters.onlyGranted" type="checkbox" />
              부여된 권한만
            </label>
          </div>
        </div>

        <div v-if="selectedRole?.restrictionSummary" class="alert alert-info" style="margin: 12px 14px 0">
          <span class="alert-icon">⚖</span>
          <span>
            <strong>제한/승인 사항</strong> — {{ selectedRole.restrictionSummary }}
            <RouterLink :to="{ name: 'policies', query: { roleId: selectedRoleId } }">정책 확인</RouterLink>
          </span>
        </div>

        <div class="matrix-wrap mt-2">
          <table class="matrix">
            <thead>
              <tr>
                <th class="col-perm">권한 ({{ visiblePerms.length }})</th>
                <th
                  v-for="a in ACTIONS"
                  :key="a"
                  class="cell-check"
                  :title="`${codeLabel('PERM_ACTION', a)} 열 전체 선택/해제`"
                  style="cursor: pointer"
                  @click="toggleColumn(a)"
                >
                  {{ a }}
                  <div class="dim" style="font-weight: 400; font-size: 10px">{{ codeLabel('PERM_ACTION', a) }}</div>
                  <div class="dim" style="font-weight: 400; font-size: 10px">{{ actionTotals[a] }}</div>
                </th>
                <th class="cell-check" title="행 전체 선택/해제">전체</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="row in matrixRows" :key="row.type === 'module' ? `m-${row.code}` : row.permId">
                <tr v-if="row.type === 'module'" class="module-row">
                  <td :colspan="ACTIONS.length + 2">
                    <CodeBadge group="PERM_MODULE" :code="row.code" />
                    <span class="dim small" style="margin-left: 6px">{{ row.count }}개 권한</span>
                  </td>
                </tr>
                <tr v-else>
                  <td class="col-perm">
                    <div>{{ row.permName }}</div>
                    <div class="mono dim" style="font-size: 10.5px">
                      {{ row.permId }}<span v-if="row.menuPath"> · {{ row.menuPath }}</span>
                    </div>
                  </td>
                  <td
                    v-for="a in ACTIONS"
                    :key="a"
                    class="cell-check"
                    :class="{ 'cell-off': !isSupported(row, a) }"
                    :title="
                      isSupported(row, a)
                        ? `${row.permName} — ${codeLabel('PERM_ACTION', a)}`
                        : `이 권한은 ${codeLabel('PERM_ACTION', a)} 액션을 지원하지 않습니다.`
                    "
                  >
                    <input
                      v-if="isSupported(row, a)"
                      type="checkbox"
                      :checked="isChecked(row.permId, a)"
                      :disabled="!canUpdate"
                      @change="toggle(row, a, $event.target.checked)"
                    />
                    <span v-else class="dim">·</span>
                  </td>
                  <td class="cell-check">
                    <button
                      class="btn btn-ghost btn-sm"
                      :disabled="!canUpdate"
                      title="이 권한의 모든 액션 선택/해제"
                      @click="toggleRow(row)"
                    >
                      {{ (draft[row.permId]?.length ?? 0) === (row.actions?.length ?? 0) ? '해제' : '전체' }}
                    </button>
                  </td>
                </tr>
              </template>
              <tr v-if="!matrixRows.length">
                <td :colspan="ACTIONS.length + 2">
                  <div class="table-empty">
                    <span class="table-empty-icon">🔍</span>
                    조건에 맞는 권한이 없습니다.
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pager">
          <span>
            부여 권한 <strong>{{ grantedCount }}</strong>개
            <span v-if="isDirty" class="badge badge-amber plain" style="margin-left: 6px">미저장</span>
          </span>
          <div class="spacer"></div>
          <button class="btn btn-sm" :disabled="!isDirty || saving" @click="revert">되돌리기</button>
          <button class="btn btn-sm btn-primary" :disabled="!isDirty || saving || !canUpdate" @click="save">저장</button>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-if="askLeave"
      title="저장하지 않은 변경"
      :message="`'${selectedRole?.roleName}' 의 변경 내용 ${changeCount}건이 저장되지 않았습니다.\n저장하지 않고 이동하시겠습니까?`"
      confirm-label="이동"
      danger
      @cancel="askLeave = null"
      @confirm="confirmLeave"
    />
  </div>
</template>
