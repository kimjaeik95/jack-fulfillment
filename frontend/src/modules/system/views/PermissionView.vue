<script setup>
import { computed, reactive } from 'vue'
import { CODE_GROUPS, codeOptions } from '@/api/codes.js'
import { useAdminStore } from '@/stores/admin.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import ActionTags from '@/components/ActionTags.vue'

const admin = useAdminStore()

const filters = reactive({ keyword: '', module: '', mapped: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', module: '', mapped: '', useYn: '' })
}

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return admin.permissions
    .filter((p) => !filters.module || p.module === filters.module)
    .filter((p) => !filters.useYn || p.useYn === filters.useYn)
    .filter(
      (p) =>
        !kw ||
        [p.permId, p.permName, p.menuPath].some((v) => String(v ?? '').toLowerCase().includes(kw)),
    )
    .map((p) => {
      const roles = admin.rolesOf(p.permId)
      return { ...p, roleCount: roles.length, roleNames: roles.map((r) => r.roleName).join(', ') }
    })
    .filter((p) => {
      if (filters.mapped === 'Y') return p.roleCount > 0
      if (filters.mapped === 'N') return p.roleCount === 0
      return true
    })
})

const columns = [
  { key: 'permId', label: '권한코드', width: '165px', sortable: true, cls: 'code' },
  { key: 'permName', label: '권한명', width: '160px', sortable: true },
  { key: 'module', label: '모듈', width: '96px', align: 'center', sortable: true },
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
  entity: 'permissions',
  perm: 'SYS_ROLE',
  pk: 'permId',
  label: '권한',
  nameOf: (p) => `${p.permName}(${p.permId})`,
  blank: () => ({ permId: '', permName: '', module: 'SYS', actions: ['R'], menuPath: '', useYn: 'Y' }),
  validate(f, ctx) {
    const e = {}
    if (!f.permId?.trim()) e.permId = '권한코드는 필수입니다.'
    else if (!/^[A-Z]{3}_[A-Z0-9_]{2,26}$/.test(f.permId))
      e.permId = '모듈코드_기능코드 형식으로 입력하세요. 예) STR_REPLENISH'
    else if (!f.permId.startsWith(`${f.module}_`))
      e.permId = `선택한 모듈(${f.module})과 권한코드 접두사가 일치해야 합니다.`
    else if (ctx.mode === 'create' && admin.permissions.some((p) => p.permId === f.permId))
      e.permId = '이미 사용 중인 권한코드입니다.'
    if (!f.permName?.trim()) e.permName = '권한명은 필수입니다.'
    if (!f.module) e.module = '모듈을 선택하세요.'
    if (!f.actions?.length) e.actions = '허용 액션을 1개 이상 선택하세요.'
    else if (!f.actions.includes('R') && !f.actions.includes('X'))
      e.actions = '조회(R)가 없는 권한은 사용할 수 없습니다. 조회를 포함하세요.'
    return e
  },
})

const actionOptions = CODE_GROUPS.PERM_ACTION.map((a) => ({ value: a.code, label: `${a.code} ${a.label}` }))

/** 액션을 줄이면 기존 매핑과 충돌할 수 있으므로 미리 알린다. */
const shrinkWarning = computed(() => {
  if (mode.value !== 'edit') return ''
  const permId = form.value.permId
  const allowed = new Set(form.value.actions ?? [])
  const conflicts = admin.rolePermissions
    .filter((m) => m.permId === permId)
    .filter((m) => m.actions.some((a) => !allowed.has(a)))
    .map((m) => `${admin.roleNameOf(m.roleId)}(${m.actions.filter((a) => !allowed.has(a)).join(',')})`)
  return conflicts.length
    ? `허용 액션에서 제외된 항목이 이미 매핑되어 있습니다: ${conflicts.join(', ')}. 역할-권한 매핑을 함께 정리하세요.`
    : ''
})

const moduleSummary = computed(() =>
  CODE_GROUPS.PERM_MODULE.map((m) => ({
    ...m,
    count: admin.permissions.filter((p) => p.module === m.code).length,
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
          v-model="filters.module"
          label="모듈"
          type="select"
          empty-option="전체"
          :options="codeOptions('PERM_MODULE')"
        />
        <FormField v-model="filters.mapped" label="매핑 여부" type="select" empty-option="전체" :options="MAPPED_OPTIONS" />
        <FormField v-model="filters.useYn" label="사용" type="select" empty-option="전체" :options="codeOptions('USE_YN')" />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="permId"
        :page-size="15"
        :muted-when="(p) => p.useYn !== 'Y'"
        :default-sort="{ key: 'permId', dir: 'asc' }"
        empty-text="조건에 맞는 권한이 없습니다."
      >
        <template #cell-module="{ value }">
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
            <span class="dim truncate" style="max-width: 26ch" :title="row.roleNames"> · {{ row.roleNames }}</span>
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
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.module"
          label="모듈"
          type="select"
          required
          :options="codeOptions('PERM_MODULE')"
          :error="errors.module"
        />
        <FormField
          v-model="form.permId"
          label="권한코드"
          required
          mono
          :placeholder="`${form.module}_FUNCTION`"
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
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="shrinkWarning" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ shrinkWarning }}</span>
      </div>

      <template #footer>
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
      detail="역할에 매핑되어 있거나 정책의 대상인 권한은 삭제할 수 없습니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>
