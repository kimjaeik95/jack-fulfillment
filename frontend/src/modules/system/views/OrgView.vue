<script setup>
import { computed, reactive } from 'vue'
import { codeOptions } from '@/api/codes.js'
import { useAdminStore } from '@/stores/admin.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const admin = useAdminStore()

const filters = reactive({ keyword: '', orgType: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', orgType: '', useYn: '' })
}

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  const order = { HQ: 0, DC: 1, STORE: 2 }
  return admin.orgs
    .filter((o) => !filters.orgType || o.orgType === filters.orgType)
    .filter((o) => !filters.useYn || o.useYn === filters.useYn)
    .filter((o) => !kw || [o.orgId, o.orgName].some((v) => String(v ?? '').toLowerCase().includes(kw)))
    .map((o) => ({
      ...o,
      parentName: o.parentId ? admin.orgNameOf(o.parentId) : '-',
      userCount: admin.users.filter((u) => u.orgId === o.orgId).length,
      childCount: admin.orgs.filter((c) => c.parentId === o.orgId).length,
    }))
    .sort((a, b) => (order[a.orgType] ?? 9) - (order[b.orgType] ?? 9) || a.orgId.localeCompare(b.orgId))
})

const columns = [
  { key: 'orgId', label: '조직코드', width: '110px', sortable: true, cls: 'code' },
  { key: 'orgName', label: '조직명', width: '170px', sortable: true },
  { key: 'orgType', label: '유형', width: '96px', align: 'center', sortable: true },
  { key: 'parentName', label: '상위 조직', width: '140px' },
  { key: 'childCount', label: '하위', width: '64px', align: 'right', sortable: true },
  { key: 'userCount', label: '소속 인원', width: '84px', align: 'right', sortable: true },
  { key: 'useYn', label: '사용', width: '68px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  entity: 'orgs',
  perm: 'SYS_COMPANY',
  pk: 'orgId',
  label: '조직',
  nameOf: (o) => `${o.orgName}(${o.orgId})`,
  blank: () => ({ orgId: '', orgName: '', orgType: 'STORE', parentId: 'HQ001', useYn: 'Y' }),
  validate(f, ctx) {
    const e = {}
    if (!f.orgId?.trim()) e.orgId = '조직코드는 필수입니다.'
    else if (!/^[A-Z]{2}\d{3}$/.test(f.orgId)) e.orgId = '영문 대문자 2자 + 숫자 3자 형식. 예) ST004'
    else if (ctx.mode === 'create' && admin.orgs.some((o) => o.orgId === f.orgId))
      e.orgId = '이미 사용 중인 조직코드입니다.'
    if (!f.orgName?.trim()) e.orgName = '조직명은 필수입니다.'
    else if (admin.orgs.some((o) => o.orgName === f.orgName.trim() && o.orgId !== f.orgId))
      e.orgName = '이미 사용 중인 조직명입니다.'
    if (!f.orgType) e.orgType = '조직유형을 선택하세요.'
    if (f.orgType !== 'HQ' && !f.parentId) e.parentId = '본사가 아닌 조직은 상위 조직이 필요합니다.'
    if (f.parentId && f.parentId === f.orgId) e.parentId = '자기 자신을 상위 조직으로 지정할 수 없습니다.'
    return e
  },
})

const parentOptions = computed(() =>
  admin.orgs
    .filter((o) => o.orgId !== form.value.orgId)
    .map((o) => ({ value: o.orgId, label: `${o.orgName} (${o.orgId})` })),
)

/** 조직유형을 바꾸면 배정 가능한 역할이 달라지므로 영향 사용자를 알려준다. */
const typeChangeWarning = computed(() => {
  if (mode.value !== 'edit') return ''
  const original = admin.orgMap[form.value.orgId]
  if (!original || original.orgType === form.value.orgType) return ''
  const affected = admin.users
    .filter((u) => u.orgId === form.value.orgId)
    .filter((u) => (u.roleIds ?? []).some((rid) => admin.roleMap[rid]?.orgScope !== form.value.orgType))
  return affected.length
    ? `조직유형을 변경하면 소속 사용자 ${affected.length}명의 역할이 배정 범위를 벗어납니다: ` +
        `${affected.map((u) => u.userName).join(', ')}. 사용자 화면에서 역할을 다시 배정하세요.`
    : ''
})
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">조직 관리</h1>
        <p class="page-desc">
          본사·물류센터·매장 조직을 관리합니다. 조직유형은 역할 배정 범위(적용범위)와 데이터 범위 제한의 기준이 됩니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-primary" :disabled="!canCreate" :title="createDenyReason ?? '조직 등록'" @click="openCreate()">
          + 조직 등록
        </button>
      </div>
    </div>

    <div v-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="filters.keyword" class="grow" label="검색어" placeholder="조직코드 / 조직명" />
        <FormField
          v-model="filters.orgType"
          label="조직유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('ORG_TYPE')"
        />
        <FormField v-model="filters.useYn" label="사용" type="select" empty-option="전체" :options="codeOptions('USE_YN')" />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
        </div>
      </div>

      <DataTable
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
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.orgId"
          label="조직코드"
          required
          mono
          placeholder="ST004"
          :disabled="mode === 'edit'"
          :error="errors.orgId"
          help="HQ/DC/ST + 3자리 숫자"
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
          v-model="form.parentId"
          label="상위 조직"
          type="select"
          empty-option="없음 (최상위)"
          :options="parentOptions"
          :error="errors.parentId"
        />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="typeChangeWarning" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ typeChangeWarning }}</span>
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
      title="조직 삭제"
      :message="deleteMessage"
      detail="소속 사용자나 하위 조직이 있으면 삭제할 수 없습니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>
