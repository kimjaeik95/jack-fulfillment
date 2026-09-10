<script setup>
/**
 * 권한(기능) 관리 (COM-PG-005) — 실제 서버 API 연동.
 *
 * 권한은 화면·기능 하나를 가리키는 최소 단위다. 역할-권한 매핑 화면이
 * 같은 목록을 행으로 쓰기 때문에 전체를 한 번 받아 두고 검색·정렬·페이징은
 * 화면에서 처리한다.
 *
 * 사용중지·삭제·액션 축소가 곧바로 역할들의 기능 상실로 이어지므로,
 * 영향 범위(매핑 역할)를 목록에서 함께 받아 미리 보여준다.
 * 최종 판정과 경고문은 서버가 만든다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { CODE_GROUPS, codeOptions } from '@/api/codes.js'
import * as permissionApi from '@/api/permission.js'
import { usePermissionStore } from '@/stores/permission.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import ActionTags from '@/components/ActionTags.vue'

const permStore = usePermissionStore()
const session = useSessionStore()

const loadError = ref('')
/** 등록 직후 새 행이 있는 페이지로 이동시키기 위한 참조 */
const table = ref(null)
const filters = reactive({ keyword: '', moduleCode: '', mapped: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', moduleCode: '', mapped: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    await permStore.load(force)
    if (permStore.denyReason) loadError.value = permStore.denyReason
  } catch (e) {
    loadError.value = e.message
  }
}

// 진입 시에는 App 이 이미 읽어둔 목록을 그대로 쓴다
onMounted(() => reload(false))

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return permStore.permissions
    .filter((p) => !filters.moduleCode || p.moduleCode === filters.moduleCode)
    .filter((p) => !filters.useYn || p.useYn === filters.useYn)
    .filter((p) => {
      if (filters.mapped === 'Y') return p.roleCount > 0
      if (filters.mapped === 'N') return p.roleCount === 0
      return true
    })
    .filter(
      (p) =>
        !kw ||
        [p.permId, p.permName, p.menuPath].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
    .map((p) => ({ ...p, roleLabel: (p.roleNames ?? []).join(', ') }))
})

const columns = [
  { key: 'permId', label: '권한코드', width: '165px', sortable: true, cls: 'code' },
  { key: 'permName', label: '권한명', width: '160px', sortable: true },
  { key: 'moduleCode', label: '모듈', width: '96px', align: 'center', sortable: true },
  { key: 'menuPath', label: '메뉴 경로', width: '175px' },
  { key: 'actions', label: '허용 액션', width: '130px' },
  { key: 'roleCount', label: '매핑 역할', width: '210px' },
  { key: 'useYn', label: '사용', width: '68px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const MAPPED_OPTIONS = [
  { value: 'Y', label: '매핑됨' },
  { value: 'N', label: '미매핑' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'SYS_ROLE',
  pk: 'permId',
  label: '권한',
  nameOf: (p) => `${p.permName}(${p.permId})`,
  api: {
    create: (payload) => permissionApi.create(payload),
    update: (permId, payload) => permissionApi.update(permId, payload),
    remove: (permId) => permissionApi.remove(permId, '권한 삭제'),
  },
  // 등록 직후 새 행이 정렬상 뒤로 밀려 1페이지에 안 보이면, 저장했는데도
  // 아무 일도 없었던 것처럼 보인다. 해당 페이지로 옮겨준다.
  async afterChange({ action, key }) {
    await reload()
    if (action === 'create') {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    permId: '',
    permName: '',
    moduleCode: 'SYS',
    menuPath: '',
    actions: ['R'],
    sortOrder: (permStore.permissions.length + 1) * 10,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    permId: row.permId,
    permName: row.permName,
    moduleCode: row.moduleCode,
    menuPath: row.menuPath ?? '',
    actions: [...(row.actions ?? [])],
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({ ...f, sortOrder: Number(f.sortOrder) || 0 }),
  validate(f, ctx) {
    const e = {}
    if (!f.permId?.trim()) e.permId = '권한코드는 필수입니다.'
    else if (!/^[A-Z]{3}_[A-Z0-9_]{2,26}$/.test(f.permId))
      e.permId = '모듈코드_기능코드 형식으로 입력하세요. 예) STR_REPLENISH'
    else if (!f.permId.startsWith(`${f.moduleCode}_`))
      e.permId = `선택한 모듈(${f.moduleCode})과 권한코드 접두사가 일치해야 합니다.`
    else if (ctx.mode === 'create' && permStore.permissions.some((p) => p.permId === f.permId))
      e.permId = '이미 사용 중인 권한코드입니다.'
    if (!f.permName?.trim()) e.permName = '권한명은 필수입니다.'
    if (!f.moduleCode) e.moduleCode = '모듈을 선택하세요.'
    if (!f.actions?.length) e.actions = '허용 액션을 1개 이상 선택하세요.'
    else if (!f.actions.includes('R') && !f.actions.includes('X'))
      e.actions = '조회(R)가 없는 권한은 쓸 수 없습니다. 조회를 포함하거나 다운로드(X) 전용으로 만드세요.'
    return e
  },
})

const actionOptions = CODE_GROUPS.PERM_ACTION.map((a) => ({ value: a.code, label: `${a.code} ${a.label}` }))

/** 수정 중인 권한의 현재 상태 — 서버가 함께 내려준 값 */
const editing = computed(() =>
  mode.value === 'edit' ? (permStore.permMap[form.value.permId] ?? null) : null,
)

/**
 * 허용 액션에서 빼려는 항목이 이미 매핑돼 있으면 서버가 거부한다.
 * 어떤 역할이 어떤 액션을 쓰는지는 서버만 알 수 있으므로, 화면은
 * 확인이 필요하다는 사실만 미리 알린다.
 */
const shrinkNotice = computed(() => {
  const before = editing.value
  if (!before) return ''
  const allowed = new Set(form.value.actions ?? [])
  const removed = (before.actions ?? []).filter((a) => !allowed.has(a))
  if (!removed.length || !before.roleCount) return ''
  return `허용 액션에서 ${removed.join(', ')}을(를) 뺍니다. 이 권한을 쓰는 역할이 ${before.roleCount}개 있어, 그중 해당 액션을 부여받은 역할이 있으면 저장이 거부됩니다.`
})

/** 사용중지는 곧 기능 상실이다. 저장 전에 알린다. */
const disableNotice = computed(() => {
  const before = editing.value
  if (!before || before.useYn !== 'Y' || form.value.useYn !== 'N') return ''
  if (!before.roleCount) return ''
  return `미사용으로 바꾸면 이 기능을 가진 역할 ${before.roleCount}개는 다음 로그인부터 해당 기능을 쓸 수 없습니다. 로그인 시 유효권한은 사용중인 권한만 모아 계산하기 때문입니다.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  if (row.roleCount) {
    return `이 권한을 쓰는 역할 ${row.roleCount}개(${(row.roleNames ?? []).join(', ')})가 있어 삭제할 수 없습니다. 역할-권한 매핑에서 먼저 해제하세요.`
  }
  return '허용 액션도 함께 삭제됩니다. 역할에 매핑되어 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('SYS_ROLE', 'R'))

const moduleSummary = computed(() =>
  CODE_GROUPS.PERM_MODULE.map((m) => ({
    ...m,
    count: permStore.permissions.filter((p) => p.moduleCode === m.code).length,
  })).filter((m) => m.count > 0),
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">권한 관리</h1>
        <p class="page-desc">
          시스템의 기능 단위 권한을 정의합니다. 권한은 모듈로 분류되고, 각 권한은 허용 액션(조회·등록·수정·삭제·승인·다운로드)을
          갖습니다. 역할에 어떤 액션을 줄지는 역할-권한 매핑에서 결정합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-primary" :disabled="!canCreate" :title="createDenyReason ?? '권한 등록'" @click="openCreate()">
          + 권한 등록
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="card mb-2">
      <div class="card-body flex wrap" style="gap: 6px">
        <span class="small dim">모듈별 권한 수</span>
        <span v-for="m in moduleSummary" :key="m.code" class="badge" :class="`badge-${m.color}`">
          {{ m.label }} {{ m.count }}
        </span>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="filters.keyword" class="grow" label="검색어" placeholder="권한코드 / 권한명 / 메뉴경로" />
        <FormField
          v-model="filters.moduleCode"
          label="모듈"
          type="select"
          empty-option="전체"
          :options="codeOptions('PERM_MODULE')"
        />
        <FormField v-model="filters.mapped" label="매핑 여부" type="select" empty-option="전체" :options="MAPPED_OPTIONS" />
        <FormField v-model="filters.useYn" label="사용" type="select" empty-option="전체" :options="codeOptions('USE_YN')" />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
          <button class="btn" :disabled="permStore.loading" @click="reload(true)">
            <span v-if="permStore.loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="permId"
        :page-size="15"
        :muted-when="(p) => p.useYn !== 'Y'"
        :default-sort="{ key: 'permId', dir: 'asc' }"
        empty-text="조건에 맞는 권한이 없습니다."
      >
        <template #cell-moduleCode="{ value }">
          <CodeBadge group="PERM_MODULE" :code="value" />
        </template>

        <template #cell-menuPath="{ value }">
          <span class="small dim">{{ value || '-' }}</span>
        </template>

        <template #cell-actions="{ value }">
          <ActionTags :actions="value" />
        </template>

        <template #cell-roleCount="{ row }">
          <span v-if="row.roleCount === 0" class="badge badge-amber plain" title="어떤 역할에도 매핑되지 않은 권한입니다.">
            미매핑
          </span>
          <span v-else class="small">
            <strong>{{ row.roleCount }}</strong>
            <span class="dim truncate" style="max-width: 26ch" :title="row.roleLabel"> · {{ row.roleLabel }}</span>
          </span>
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
      :title="mode === 'create' ? '권한 등록' : '권한 수정'"
      :subtitle="mode === 'edit' ? form.permId : '권한코드는 모듈코드_기능코드 형식입니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.moduleCode"
          label="모듈"
          type="select"
          required
          :options="codeOptions('PERM_MODULE')"
          :error="errors.moduleCode"
        />
        <FormField
          v-model="form.permId"
          label="권한코드"
          required
          mono
          :placeholder="`${form.moduleCode}_FUNCTION`"
          :disabled="mode === 'edit'"
          :error="errors.permId"
        />
        <FormField v-model="form.permName" label="권한명" required span placeholder="보충 요청" :error="errors.permName" />
        <FormField v-model="form.menuPath" label="메뉴 경로" span placeholder="매장 > 보충요청" help="사용자에게 보여줄 화면 위치" />
        <FormField
          v-model="form.actions"
          label="허용 액션"
          type="checks"
          required
          span
          :options="actionOptions"
          :error="errors.actions"
          help="이 권한에서 허용 가능한 액션의 최대 집합입니다."
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="shrinkNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ shrinkNotice }}</span>
      </div>
      <div v-if="disableNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ disableNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">코드값·중복·매핑 충돌은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="권한 삭제"
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
