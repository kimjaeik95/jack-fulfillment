<script setup>
/**
 * 공통정책 관리 (COM-PG-007) — 실제 서버 API 연동.
 *
 * 역할·권한이 "무엇을 할 수 있는가"라면, 정책은 "할 수 있는데 이런 조건에서는
 * 막거나 승인을 받아라"다. 요구사항 표의 3열(제한/승인)이 이 화면이다.
 *
 * 로그인 시 사용중인 정책이 세션에 실려 판정에 쓰인다. 즉 여기서 저장한
 * 규칙이 곧 그 사람이 실제로 막히는 지점이 된다.
 *
 * 유형별 필수값(LIMIT 은 한도, CONDITION 은 조건식 …)은 서버가 최종 판정한다.
 * 빠진 채 저장되면 판정 시점에 조용히 아무 일도 하지 않는 정책이 되기 때문이다.
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { codeItem, codeOptions } from '@/api/codes.js'
import * as rolePermApi from '@/api/rolePermission.js'
import * as policyApi from '@/api/policy.js'
import { usePolicyStore } from '@/stores/policy.js'
import { useRoleStore } from '@/stores/role.js'
import { usePermissionStore } from '@/stores/permission.js'
import * as exportApi from '@/api/export.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const policyStore = usePolicyStore()
const roleStore = useRoleStore()
const permStore = usePermissionStore()
const session = useSessionStore()
const toast = useToastStore()

const table = ref(null)
// 목록은 스토어가 들고 있다. 사이드바 건수도 같은 출처를 읽는다.
const policies = computed(() => policyStore.policies)
const loading = computed(() => policyStore.loading)
const loadError = computed(() => policyStore.denyReason)
const filters = reactive({ keyword: '', roleId: '', policyType: '', enforceLevel: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', roleId: '', policyType: '', enforceLevel: '', useYn: '' })
}

/** 항상 서버에서 다시 받는다. 방금 저장한 결과를 보려고 부르는 함수다. */
const reload = () => policyStore.load(true)

onMounted(async () => {
  await Promise.all([roleStore.load(), permStore.load(), policyStore.load()])
})

/**
 * 정책은 수십 건 규모라 전체를 받아 화면에서 거른다.
 * 서버도 검색·페이징을 지원하므로 늘어나면 화면만 바꾸면 된다.
 */
const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return policies.value
    .filter((p) => !filters.roleId || p.roleId === filters.roleId)
    .filter((p) => !filters.policyType || p.policyType === filters.policyType)
    .filter((p) => !filters.enforceLevel || p.enforceLevel === filters.enforceLevel)
    .filter((p) => !filters.useYn || p.useYn === filters.useYn)
    .filter(
      (p) =>
        !kw ||
        [p.policyId, p.policyName, p.message, p.targetField, p.conditionExpr, p.remark].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
    .map((p) => ({ ...p, permName: p.permId ? p.permName : '전체' }))
})

const columns = [
  { key: 'policyId', label: '정책ID', width: '78px', sortable: true, cls: 'code' },
  { key: 'policyName', label: '정책명', width: '190px', sortable: true },
  { key: 'roleName', label: '적용 역할', width: '128px', sortable: true },
  { key: 'permName', label: '대상 기능', width: '150px' },
  { key: 'policyType', label: '유형', width: '92px', align: 'center', sortable: true },
  { key: 'enforceLevel', label: '강도', width: '86px', align: 'center', sortable: true },
  { key: 'targetField', label: '대상 항목', width: '160px' },
  { key: 'message', label: '안내 메시지', width: '280px' },
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
  perm: 'SYS_POLICY',
  pk: 'policyId',
  label: '공통정책',
  nameOf: (p) => `${p.policyId} ${p.policyName}`,
  api: {
    // 정책ID 는 서버가 채번한다 (P011, P012 …)
    create: (payload) => policyApi.create(payload),
    update: (policyId, payload) => policyApi.update(policyId, payload),
    remove: (policyId) => policyApi.remove(policyId, '공통정책 삭제'),
  },
  // 등록 직후 새 행이 정렬상 뒤로 밀려 1페이지에 안 보이면, 저장했는데도
  // 아무 일도 없었던 것처럼 보인다. 해당 페이지로 옮겨준다.
  // 정책ID 는 서버가 채번하므로 폼이 아니라 응답에서 꺼낸다.
  async afterChange({ action, result }) {
    await reload()
    if (action === 'create' && result?.policy?.policyId) {
      await nextTick()
      table.value?.goToKey(result.policy.policyId)
    }
  },
  blank: () => ({
    policyId: '',
    policyName: '',
    roleId: '',
    permId: '',
    policyType: 'DENY',
    enforceLevel: 'BLOCK',
    conditionExpr: '',
    targetField: '',
    message: '',
    altProcess: '',
    limitAmount: 0,
    limitQty: 0,
    remark: '',
    useYn: 'Y',
  }),
  toForm: (row) => ({
    ...row,
    permId: row.permId ?? '',
    conditionExpr: row.conditionExpr ?? '',
    targetField: row.targetField ?? '',
    altProcess: row.altProcess ?? '',
    remark: row.remark ?? '',
    limitAmount: row.limitAmount ?? 0,
    limitQty: row.limitQty ?? 0,
  }),
  // 서버가 받는 항목만 담는다. 조회용으로 따라온 roleName·granted 등을 되돌려
  // 보내면 서버가 무시하긴 해도, 무엇이 편집 대상인지가 코드에서 흐려진다.
  toPayload: (f) => ({
    // 등록이면 비워 보낸다 — 서버가 채번한다 (P011, P012 …)
    policyId: f.policyId || null,
    policyName: f.policyName,
    roleId: f.roleId,
    permId: f.permId || null,
    policyType: f.policyType,
    enforceLevel: f.enforceLevel,
    conditionExpr: f.conditionExpr || null,
    targetField: f.targetField || null,
    message: f.message,
    altProcess: f.altProcess || null,
    // LIMIT 이 아니면 한도는 의미가 없다. 서버도 같은 판단으로 비워 저장한다.
    limitAmount: f.policyType === 'LIMIT' ? Number(f.limitAmount) : null,
    limitQty: f.policyType === 'LIMIT' ? Number(f.limitQty) : null,
    remark: f.remark || null,
    useYn: f.useYn,
  }),
  validate(f) {
    const e = {}
    if (!f.policyName?.trim()) e.policyName = '정책명은 필수입니다.'
    if (!f.roleId) e.roleId = '적용 역할을 선택하세요.'
    if (!f.policyType) e.policyType = '정책유형을 선택하세요.'
    if (!f.enforceLevel) e.enforceLevel = '적용강도를 선택하세요.'
    if (!f.message?.trim()) e.message = '차단/경고 시 사용자에게 보여줄 메시지는 필수입니다.'
    if (f.policyType === 'REQUIRED' && !f.targetField?.trim())
      e.targetField = '필수입력 정책은 대상 필드를 지정해야 합니다.'
    if (f.policyType === 'CONDITION' && !f.conditionExpr?.trim())
      e.conditionExpr = '조건충족 정책은 조건식이 필요합니다.'
    if (f.policyType === 'LIMIT' && Number(f.limitAmount) <= 0 && Number(f.limitQty) <= 0)
      e.limitAmount = '한도금액 또는 한도수량 중 하나는 0보다 커야 합니다.'
    if (f.enforceLevel === 'APPROVAL' && !f.altProcess?.trim())
      e.altProcess = '상위승인 정책은 누구의 승인을 받는지 적어야 합니다.'
    if (f.policyType === 'READONLY' && f.permId)
      e.permId = '읽기전용은 대상 기능을 비워 역할 전체에 적용하세요.'
    return e
  },
})

const typeHelp = computed(() => codeItem('POLICY_TYPE', form.value.policyType)?.desc ?? '')
const levelHelp = computed(() => codeItem('ENFORCE_LEVEL', form.value.enforceLevel)?.desc ?? '')
const isLimit = computed(() => form.value.policyType === 'LIMIT')

/** 목록의 ⚠ 표시 설명 — 서버의 granted=false 와 같은 뜻이다 */
const NOT_GRANTED_HELP =
  '해당 역할에 이 기능 권한이 매핑되어 있지 않아 이 정책은 실제로 평가되지 않습니다.'

/**
 * 선택한 역할이 보유한 권한 목록.
 *
 * 정책은 역할이 그 기능 권한을 가지고 있을 때만 평가된다. 없는 채로 두면
 * 규칙은 저장되지만 아무 일도 하지 않는다. 그래서 대상 기능을 고를 때
 * 보유 여부를 함께 보여준다. 역할이 바뀌면 그 역할의 매핑을 다시 읽는다.
 */
const ownedPerms = ref(new Set())

watch(
  () => form.value.roleId,
  async (roleId) => {
    ownedPerms.value = new Set()
    if (!roleId) return
    try {
      const result = await rolePermApi.fetchGrants(roleId)
      ownedPerms.value = new Set(result.grants.map((g) => g.permId))
    } catch {
      // 매핑을 못 읽어도 정책 등록 자체는 막지 않는다. 보유 표시만 사라진다.
    }
  },
  { immediate: true },
)

/** 권한이 없으면 이 정책은 평가되지 않는다 — 서버도 저장 시 같은 경고를 준다 */
const grantWarning = computed(() => {
  const { roleId, permId } = form.value
  if (!roleId || !permId || !ownedPerms.value.size) return ''
  if (ownedPerms.value.has(permId)) return ''
  return `'${roleStore.roleNameOf(roleId)}' 역할에는 '${permStore.permNameOf(permId)}' 권한이 매핑되어 있지 않습니다. `
    + '권한이 없으면 이 정책은 실제로 평가되지 않습니다.'
})

/** 대상 기능 옵션: 역할이 보유한 권한을 위로 정렬 */
const permOptionsForRole = computed(() => {
  const owned = ownedPerms.value
  return [...permStore.permissions]
    .filter((p) => p.useYn === 'Y')
    .map((p) => ({
      value: p.permId,
      label: `${owned.has(p.permId) ? '● ' : '○ '}${p.permName} (${p.permId})`,
      owned: owned.has(p.permId),
    }))
    .sort((a, b) => Number(b.owned) - Number(a.owned) || a.label.localeCompare(b.label, 'ko'))
})

/**
 * 제한사항이 적혀 있는데 정책이 없는 역할.
 * 역할 화면의 '제한/승인 사항'은 글일 뿐이고, 실제 통제는 여기 등록해야 동작한다.
 */
const coverage = computed(() =>
  roleStore.roles
    .filter((r) => r.useYn === 'Y' && r.restrictionSummary)
    .map((r) => ({
      roleId: r.roleId,
      roleName: r.roleName,
      restriction: r.restrictionSummary,
      count: policies.value.filter((p) => p.roleId === r.roleId && p.useYn === 'Y').length,
    }))
    .filter((r) => r.count === 0),
)

function addFor(roleId) {
  const role = roleStore.roleMap[roleId]
  openCreate({
    roleId,
    policyName: role?.restrictionSummary ?? '',
    message: role?.restrictionSummary ? `${role.restrictionSummary} — 정책에 따라 제한됩니다.` : '',
  })
}

const readDenyReason = computed(() => session.denyReason('SYS_POLICY', 'R'))
/* ------------------------------------------------------------------ */
/* CSV 다운로드 (COM-PG-011)                                           */
/* ------------------------------------------------------------------ */

/**
 * 조회할 수 있다고 내려받아도 되는 것은 아니다. 파일로 나간 데이터는
 * 회수할 수 없어 서버가 다운로드 액션(X)을 따로 판정한다.
 */
const downloadDenyReason = computed(() => session.denyReason('SYS_POLICY', 'X'))
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
    await exportApi.policies({ ...filters }, format)
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
        <h1 class="page-title">공통정책 관리</h1>
        <p class="page-desc">
          역할별 제한·승인 규칙(금지/필수/조건/직무분리/범위제한/한도/읽기전용/마스킹)을 등록합니다. 여기에 등록된 정책이
          실제 화면 동작(버튼 비활성, 저장 차단, 상위승인 요구)을 결정합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-primary" :disabled="!canCreate" :title="createDenyReason ?? '정책 등록'" @click="openCreate()">
          + 정책 등록
        </button>
      </div>
    </div>

    <div v-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <div v-if="coverage.length" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <div>
        <div class="bold">제한사항이 정의되어 있으나 정책이 등록되지 않은 역할 {{ coverage.length }}건</div>
        <div class="chip-list mt-1">
          <button
            v-for="c in coverage"
            :key="c.roleId"
            class="chip"
            style="cursor: pointer"
            :title="`'${c.restriction}' 정책 등록`"
            :disabled="!canCreate"
            @click="addFor(c.roleId)"
          >
            {{ c.roleName }} — {{ c.restriction }} +
          </button>
        </div>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="filters.keyword" class="grow" label="검색어" placeholder="정책명 / 메시지 / 조건식" />
        <FormField v-model="filters.roleId" label="적용 역할" type="select" empty-option="전체" :options="roleStore.roleOptions" />
        <FormField
          v-model="filters.policyType"
          label="유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('POLICY_TYPE')"
        />
        <FormField
          v-model="filters.enforceLevel"
          label="강도"
          type="select"
          empty-option="전체"
          :options="codeOptions('ENFORCE_LEVEL')"
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
          <button class="btn" :disabled="loading" @click="reload()">
            <span v-if="loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="policyId"
        :page-size="10"
        :muted-when="(p) => p.useYn !== 'Y'"
        empty-text="조건에 맞는 정책이 없습니다."
      >
        <template #cell-policyName="{ row, value }">
          <div>{{ value }}</div>
          <div v-if="row.altProcess && row.altProcess !== '-'" class="small dim">대안: {{ row.altProcess }}</div>
        </template>

        <template #cell-permName="{ row, value }">
          <span v-if="row.permId" class="small">{{ value }}</span>
          <span v-else class="badge badge-slate plain">전체 기능</span>
          <div v-if="row.granted === false" class="small text-warn" :title="NOT_GRANTED_HELP">⚠ 권한 미매핑</div>
        </template>

        <template #cell-policyType="{ value }">
          <CodeBadge group="POLICY_TYPE" :code="value" />
        </template>

        <template #cell-enforceLevel="{ value }">
          <CodeBadge group="ENFORCE_LEVEL" :code="value" />
        </template>

        <template #cell-targetField="{ row, value }">
          <span class="small">{{ value || '-' }}</span>
          <div v-if="row.policyType === 'LIMIT'" class="small dim">
            한도 {{ (row.limitAmount ?? 0).toLocaleString() }}원 / {{ row.limitQty ?? 0 }}EA
          </div>
        </template>

        <template #cell-message="{ row, value }">
          <span class="truncate" style="max-width: 42ch" :title="value">{{ value }}</span>
          <div v-if="row.conditionExpr" class="mono small dim truncate" style="max-width: 42ch" :title="row.conditionExpr">
            {{ row.conditionExpr }}
          </div>
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

    <!-- 등록/수정 -->
    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '공통정책 등록' : '공통정책 수정'"
      :subtitle="mode === 'edit' ? form.policyId : '정책ID는 저장 시 자동 부여됩니다.'"
      size="wide"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.policyName"
          label="정책명"
          required
          span
          placeholder="타 매장 재고 직접 수정 금지"
          :error="errors.policyName"
        />
        <FormField
          v-model="form.roleId"
          label="적용 역할"
          type="select"
          required
          empty-option="선택하세요"
          :options="roleStore.roleOptions"
          :error="errors.roleId"
        />
        <FormField
          v-model="form.permId"
          label="대상 기능(권한)"
          type="select"
          empty-option="전체 기능에 적용"
          :options="permOptionsForRole"
          help="● 는 해당 역할이 이미 보유한 권한입니다."
        />
        <FormField
          v-model="form.policyType"
          label="정책유형"
          type="select"
          required
          :options="codeOptions('POLICY_TYPE')"
          :help="typeHelp"
          :error="errors.policyType"
        />
        <FormField
          v-model="form.enforceLevel"
          label="적용강도"
          type="select"
          required
          :options="codeOptions('ENFORCE_LEVEL')"
          :help="levelHelp"
          :error="errors.enforceLevel"
        />

        <FormField
          v-model="form.targetField"
          label="대상 항목/필드"
          placeholder="po.cancelReason 또는 매장 재고/실사"
          :error="errors.targetField"
          :required="form.policyType === 'REQUIRED'"
        />
        <FormField
          v-model="form.conditionExpr"
          label="조건식"
          mono
          placeholder='sku.onHandQty == 0 && sku.openTxCount == 0'
          :error="errors.conditionExpr"
          :required="form.policyType === 'CONDITION'"
          help="조건이 참일 때 허용됩니다."
        />

        <template v-if="isLimit">
          <FormField
            v-model="form.limitAmount"
            label="한도금액 (원)"
            type="number"
            :error="errors.limitAmount"
            help="초과 시 상위 승인 필요"
          />
          <FormField v-model="form.limitQty" label="한도수량 (EA)" type="number" />
        </template>

        <FormField
          v-model="form.message"
          label="안내 메시지"
          type="textarea"
          required
          span
          :rows="2"
          placeholder="소속 매장 외의 재고는 직접 수정할 수 없습니다."
          :error="errors.message"
          help="차단·경고 시 사용자에게 그대로 노출됩니다."
        />
        <FormField
          v-model="form.altProcess"
          label="대안 프로세스"
          placeholder="매장 간 이동 요청 → 이동 승인"
          help="차단 시 사용자를 안내할 우회 경로"
        />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
        <FormField v-model="form.remark" label="비고" type="textarea" span :rows="2" placeholder="운영 메모, 근거 규정 등" />
      </div>

      <div v-if="grantWarning" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ grantWarning }}</span>
      </div>

      <template #footer>
        <span class="left small dim">동일 역할·대상·유형의 사용중 정책은 중복 등록할 수 없습니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="정책 삭제"
      :message="deleteMessage"
      detail="정책을 삭제하면 해당 통제가 즉시 해제됩니다. 일시 중지는 '사용여부 = 미사용'을 사용하세요."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>
