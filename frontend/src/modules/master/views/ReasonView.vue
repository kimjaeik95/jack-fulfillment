<script setup>
/**
 * 사유코드 관리 (MST-PG-014).
 *
 * 취소 · 반품 · 검수불량 · 결품 · 조정 · 배송실패 사유를 다룬다 (MST-012).
 * 수량을 변경한 모든 행위는 사유코드를 남긴다 (핵심원칙 P-04) — 여기가
 * 그 사유의 출처다.
 *
 * 테이블은 공통코드와 같다. 요구사항 데이터 표가 '사유코드 / 공통코드' 를
 * 한 행으로 묶었고 구조도 같다. 다만 화면과 권한은 갈랐다 — 공통코드에는
 * DATA_SCOPE · PERM_ACTION 처럼 건드리면 권한 판정이 깨지는 것들이 있어서,
 * 현장이 결품 사유 하나를 추가하려고 그 화면에 들어가야 한다면 같은 화면에서
 * 데이터 범위 코드도 지울 수 있게 된다.
 *
 * 그룹을 왼쪽, 그 안의 사유를 오른쪽에 둔다. 사유는 그룹 없이 뜻이 없다.
 */
import { computed, onMounted, ref } from 'vue'
import * as reasonApi from '@/api/reason.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'

const session = useSessionStore()
const toast = useToastStore()

const loadError = ref('')
const loading = ref(false)
const groups = ref([])

const selected = ref('')
const detail = ref(null)
const detailLoading = ref(false)

const canCreate = computed(() => session.can('MST_REASON', 'C'))
const canUpdate = computed(() => session.can('MST_REASON', 'U'))
const canDelete = computed(() => session.can('MST_REASON', 'D'))
const createDenyReason = computed(() => session.denyReason('MST_REASON', 'C'))
const updateDenyReason = computed(() => session.denyReason('MST_REASON', 'U'))
const deleteDenyReason = computed(() => session.denyReason('MST_REASON', 'D'))
const readDenyReason = computed(() => session.denyReason('MST_REASON', 'R'))

async function fetchGroups() {
  loading.value = true
  loadError.value = ''
  try {
    groups.value = await reasonApi.groups()
    if (!selected.value && groups.value.length) {
      await openGroup(groups.value[0].codeGroupId)
    } else if (selected.value && !groups.value.some((g) => g.codeGroupId === selected.value)) {
      selected.value = ''
      detail.value = null
    }
  } catch (e) {
    groups.value = []
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

async function openGroup(codeGroupId) {
  selected.value = codeGroupId
  detailLoading.value = true
  try {
    detail.value = await reasonApi.group(codeGroupId)
  } catch (e) {
    detail.value = null
    toast.error(e.message)
  } finally {
    detailLoading.value = false
  }
}

onMounted(fetchGroups)

/* ── 사유 그룹 ──────────────────────────────────────────────── */

const groupOpen = ref(false)
const groupMode = ref('create')
const groupForm = ref(blankGroup())
const groupErrors = ref({})
const groupBusy = ref(false)
const groupServerError = ref('')

function blankGroup() {
  return { codeGroupId: '', codeGroupName: '', description: '', useYn: 'Y' }
}

function openGroupCreate() {
  groupMode.value = 'create'
  groupForm.value = blankGroup()
  groupErrors.value = {}
  groupServerError.value = ''
  groupOpen.value = true
}

function openGroupEdit(g) {
  groupMode.value = 'edit'
  groupForm.value = {
    codeGroupId: g.codeGroupId,
    codeGroupName: g.codeGroupName,
    description: g.description ?? '',
    useYn: g.useYn,
  }
  groupErrors.value = {}
  groupServerError.value = ''
  groupOpen.value = true
}

async function submitGroup() {
  const e = {}
  if (!groupForm.value.codeGroupId?.trim()) e.codeGroupId = '그룹ID는 필수입니다.'
  else if (!/^[A-Z][A-Z0-9_]{2,29}$/.test(groupForm.value.codeGroupId))
    e.codeGroupId = '영문 대문자로 시작하는 3~30자. 예) REASON_CANCEL'
  if (!groupForm.value.codeGroupName?.trim()) e.codeGroupName = '그룹명은 필수입니다.'
  groupErrors.value = e
  if (Object.keys(e).length) return

  groupBusy.value = true
  groupServerError.value = ''
  const payload = {
    ...groupForm.value,
    description: groupForm.value.description || null,
    reason: groupMode.value === 'create' ? '사유 그룹 등록' : '사유 그룹 수정',
  }
  try {
    if (groupMode.value === 'create') {
      await reasonApi.createGroup(payload)
      toast.success('사유 그룹을 등록했습니다.')
    } else {
      await reasonApi.updateGroup(groupForm.value.codeGroupId, payload)
      toast.success('사유 그룹을 수정했습니다.')
    }
    groupOpen.value = false
    await fetchGroups()
    await openGroup(groupForm.value.codeGroupId)
  } catch (err) {
    // 서버(업무규칙) 오류는 모달을 닫지 않고 그대로 보여준다
    groupServerError.value = err.message
    toast.error(err.message)
  } finally {
    groupBusy.value = false
  }
}

const askGroupDelete = ref(null)
const groupDeleting = ref(false)

async function doGroupDelete() {
  groupDeleting.value = true
  try {
    await reasonApi.removeGroup(askGroupDelete.value.codeGroupId, '사유 그룹 삭제')
    toast.success('사유 그룹을 삭제했습니다.')
    askGroupDelete.value = null
    selected.value = ''
    detail.value = null
    await fetchGroups()
  } catch (e) {
    toast.error(e.message)
    askGroupDelete.value = null
  } finally {
    groupDeleting.value = false
  }
}

/* ── 사유코드 ───────────────────────────────────────────────── */

const codeOpen = ref(false)
const codeMode = ref('create')
const codeForm = ref(blankCode())
const codeErrors = ref({})
const codeBusy = ref(false)
const codeServerError = ref('')

function blankCode() {
  return { codeId: '', codeName: '', description: '', sortOrder: 0, useYn: 'Y' }
}

function openCodeCreate() {
  codeMode.value = 'create'
  codeForm.value = blankCode()
  // 마지막 정렬값 + 10 — 새로 만든 사유가 맨 뒤로 간다
  const last = detail.value?.codes?.[detail.value.codes.length - 1]
  codeForm.value.sortOrder = (last?.sortOrder ?? 0) + 10
  codeErrors.value = {}
  codeServerError.value = ''
  codeOpen.value = true
}

function openCodeEdit(c) {
  codeMode.value = 'edit'
  codeForm.value = {
    codeId: c.codeId,
    codeName: c.codeName,
    description: c.description ?? '',
    sortOrder: c.sortOrder ?? 0,
    useYn: c.useYn,
  }
  codeErrors.value = {}
  codeServerError.value = ''
  codeOpen.value = true
}

async function submitCode() {
  const e = {}
  if (!codeForm.value.codeId?.trim()) e.codeId = '코드는 필수입니다.'
  else if (!/^[A-Z][A-Z0-9_]{1,29}$/.test(codeForm.value.codeId))
    e.codeId = '영문 대문자로 시작하는 2~30자. 예) CUST_CHANGE'
  if (!codeForm.value.codeName?.trim()) e.codeName = '사유명은 필수입니다.'
  codeErrors.value = e
  if (Object.keys(e).length) return

  codeBusy.value = true
  codeServerError.value = ''
  const payload = {
    ...codeForm.value,
    description: codeForm.value.description || null,
    sortOrder: Number(codeForm.value.sortOrder) || 0,
    reason: codeMode.value === 'create' ? '사유 등록' : '사유 수정',
  }
  try {
    if (codeMode.value === 'create') {
      await reasonApi.createCode(selected.value, payload)
      toast.success('사유를 등록했습니다.')
    } else {
      await reasonApi.updateCode(selected.value, codeForm.value.codeId, payload)
      toast.success('사유를 수정했습니다.')
    }
    codeOpen.value = false
    await openGroup(selected.value)
    await fetchGroups()
  } catch (err) {
    codeServerError.value = err.message
    toast.error(err.message)
  } finally {
    codeBusy.value = false
  }
}

const askCodeDelete = ref(null)
const codeDeleting = ref(false)

async function doCodeDelete() {
  codeDeleting.value = true
  try {
    await reasonApi.removeCode(selected.value, askCodeDelete.value.codeId, '사유 삭제')
    toast.success('사유를 삭제했습니다.')
    askCodeDelete.value = null
    await openGroup(selected.value)
    await fetchGroups()
  } catch (e) {
    toast.error(e.message)
    askCodeDelete.value = null
  } finally {
    codeDeleting.value = false
  }
}

/** 사유가 하나도 없는 그룹 — 그 예외를 처리할 때 고를 것이 없다 */
const emptyGroups = computed(() =>
  groups.value.filter((g) => g.useYn === 'Y' && !g.codeCount),
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">사유코드 관리</h1>
        <p class="page-desc">
          취소 · 반품 · 검수불량 · 결품 · 조정 · 배송실패 사유입니다.
          <strong>수량을 변경한 모든 행위는 사유코드를 남깁니다.</strong>
          그래서 각 예외 처리에서 사유 선택은 필수이고, 여기 없는 사유는 고를 수 없습니다.
          사용 중인 사유는 삭제해도 과거 이력은 그대로 남습니다 — 지우기보다 사용여부를 끄세요.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '사유 그룹 등록'"
          @click="openGroupCreate()"
        >
          + 사유 그룹
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

    <div v-if="emptyGroups.length" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>
        사유가 하나도 없는 그룹이 있습니다 —
        <strong>{{ emptyGroups.map((g) => g.codeGroupName).join(', ') }}</strong>.
        그 예외를 처리할 때 고를 사유가 없어 저장이 막힙니다.
      </span>
    </div>

    <div class="split">
      <!-- 왼쪽 — 사유 그룹 -->
      <div class="card side">
        <div class="panel-head">
          <strong class="small">사유 그룹</strong>
          <span class="small dim">{{ groups.length }}개</span>
        </div>
        <button
          v-for="g in groups"
          :key="g.codeGroupId"
          :class="['group-row', { on: selected === g.codeGroupId, off: g.useYn !== 'Y' }]"
          @click="openGroup(g.codeGroupId)"
        >
          <div class="group-name">{{ g.codeGroupName }}</div>
          <div class="small dim code">{{ g.codeGroupId }}</div>
          <div class="small dim">
            사유 {{ g.codeCount ?? 0 }}개
            <span v-if="g.useYn !== 'Y'"> · 미사용</span>
          </div>
        </button>
        <div v-if="!groups.length && !loading" class="empty-note small">
          사유 그룹이 없습니다.
        </div>
      </div>

      <!-- 오른쪽 — 그 그룹의 사유 -->
      <div class="card grow">
        <div v-if="!detail" class="empty-note">
          왼쪽에서 사유 그룹을 고르세요.
        </div>

        <template v-else>
          <div class="panel-head">
            <div>
              <strong>{{ detail.codeGroupName }}</strong>
              <span class="small dim code"> {{ detail.codeGroupId }}</span>
              <div v-if="detail.description" class="small dim">{{ detail.description }}</div>
            </div>
            <div class="btn-row">
              <span v-if="detailLoading" class="small dim"><span class="spinner"></span></span>
              <button
                class="btn btn-sm btn-primary"
                :disabled="!canCreate"
                :title="createDenyReason ?? '사유 등록'"
                @click="openCodeCreate()"
              >
                + 사유 등록
              </button>
              <button
                class="btn btn-sm"
                :disabled="!canUpdate"
                :title="updateDenyReason ?? '그룹 수정'"
                @click="openGroupEdit(detail)"
              >
                그룹 수정
              </button>
              <button
                class="btn btn-sm btn-danger"
                :disabled="!canDelete"
                :title="deleteDenyReason ?? '그룹 삭제'"
                @click="askGroupDelete = detail"
              >
                그룹 삭제
              </button>
            </div>
          </div>

          <div v-if="!detail.codes?.length" class="empty-note">
            사유가 없습니다. 이 예외를 처리할 때 고를 것이 없어 저장이 막힙니다.
          </div>

          <div v-for="c in detail.codes" :key="c.codeId" class="code-row">
            <div class="code-main">
              <div>
                <strong>{{ c.codeName }}</strong>
                <span class="small dim code"> {{ c.codeId }}</span>
                <span v-if="c.useYn !== 'Y'" class="badge badge-gray">미사용</span>
              </div>
              <div v-if="c.description" class="small dim">{{ c.description }}</div>
            </div>
            <div class="btn-row">
              <button
                class="btn btn-sm"
                :disabled="!canUpdate"
                :title="updateDenyReason ?? '수정'"
                @click="openCodeEdit(c)"
              >
                수정
              </button>
              <button
                class="btn btn-sm btn-danger"
                :disabled="!canDelete"
                :title="deleteDenyReason ?? '삭제'"
                @click="askCodeDelete = c"
              >
                삭제
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- ── 사유 그룹 등록 · 수정 ──────────────────────────────── -->
    <ModalDialog
      v-if="groupOpen"
      :title="groupMode === 'create' ? '사유 그룹 등록' : '사유 그룹 수정'"
      :subtitle="groupMode === 'edit' ? groupForm.codeGroupId : '그룹ID는 등록 후 변경할 수 없습니다.'"
      @close="groupOpen = false"
    >
      <div v-if="groupServerError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ groupServerError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="groupForm.codeGroupId"
          label="그룹ID"
          required
          mono
          placeholder="REASON_CANCEL"
          :disabled="groupMode === 'edit'"
          :error="groupErrors.codeGroupId"
          help="화면과 서버가 이 문자열로 사유 목록을 부릅니다."
        />
        <FormField
          v-model="groupForm.codeGroupName"
          label="그룹명"
          required
          placeholder="취소 사유"
          :error="groupErrors.codeGroupName"
        />
        <FormField
          v-model="groupForm.description"
          label="설명"
          span
          placeholder="주문 취소 (ORD-008)"
        />
        <FormField v-model="groupForm.useYn" label="사용여부" type="switch" />
      </div>

      <template #footer>
        <span class="left small dim">이 화면에서 만든 그룹은 사유코드로 저장됩니다.</span>
        <button class="btn" :disabled="groupBusy" @click="groupOpen = false">취소</button>
        <button class="btn btn-primary" :disabled="groupBusy" @click="submitGroup()">
          <span v-if="groupBusy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <!-- ── 사유 등록 · 수정 ───────────────────────────────────── -->
    <ModalDialog
      v-if="codeOpen"
      :title="codeMode === 'create' ? '사유 등록' : '사유 수정'"
      :subtitle="detail?.codeGroupName"
      @close="codeOpen = false"
    >
      <div v-if="codeServerError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ codeServerError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="codeForm.codeId"
          label="코드"
          required
          mono
          placeholder="CUST_CHANGE"
          :disabled="codeMode === 'edit'"
          :error="codeErrors.codeId"
          help="이력에 저장되는 값입니다. 나중에 바꿀 수 없습니다."
        />
        <FormField
          v-model="codeForm.codeName"
          label="사유명"
          required
          placeholder="고객 변심"
          :error="codeErrors.codeName"
          help="현장에서 고르는 화면에 이 이름이 보입니다."
        />
        <FormField
          v-model="codeForm.description"
          label="설명"
          span
          placeholder="어떤 경우에 고르는지"
        />
        <FormField v-model="codeForm.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField
          v-model="codeForm.useYn"
          label="사용여부"
          type="switch"
          help="끄면 새로 고를 수 없지만 과거 이력은 그대로 남습니다."
        />
      </div>

      <template #footer>
        <span class="left small dim">코드 중복은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="codeBusy" @click="codeOpen = false">취소</button>
        <button class="btn btn-primary" :disabled="codeBusy" @click="submitCode()">
          <span v-if="codeBusy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askGroupDelete"
      title="사유 그룹 삭제"
      :message="`${askGroupDelete.codeGroupName} (${askGroupDelete.codeGroupId}) 그룹을 삭제합니다.`"
      detail="그룹 안에 사유가 남아 있으면 삭제되지 않습니다. 더 쓰지 않을 뿐이라면 사용여부를 끄세요 — 과거 이력이 이 사유를 가리키고 있습니다."
      confirm-label="삭제"
      danger
      :busy="groupDeleting"
      @cancel="askGroupDelete = null"
      @confirm="doGroupDelete()"
    />

    <ConfirmDialog
      v-if="askCodeDelete"
      title="사유 삭제"
      :message="`${askCodeDelete.codeName} (${askCodeDelete.codeId}) 사유를 삭제합니다.`"
      detail="이미 이 사유로 기록된 이력은 그대로 남지만, 목록에서 이름을 찾을 수 없게 됩니다. 더 쓰지 않을 뿐이라면 사용여부를 끄는 편이 낫습니다."
      confirm-label="삭제"
      danger
      :busy="codeDeleting"
      @cancel="askCodeDelete = null"
      @confirm="doCodeDelete()"
    />
  </div>
</template>

<style scoped>
.split {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.side {
  width: 240px;
  flex: 0 0 240px;
}
.grow {
  flex: 1;
  min-width: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}
.group-row {
  display: block;
  width: 100%;
  text-align: left;
  padding: 9px 14px;
  border: none;
  border-bottom: 1px solid var(--line, #e5e7eb);
  background: transparent;
  cursor: pointer;
}
.group-row:hover {
  background: var(--bg-soft, #f8fafc);
}
.group-row.on {
  background: var(--bg-soft, #f1f5f9);
  box-shadow: inset 3px 0 0 var(--c-blue, #2563eb);
}
.group-row.off .group-name {
  color: var(--fg-dim, #6b7280);
}
.group-name {
  font-weight: 600;
  font-size: 13px;
}
.code-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 14px;
}
.code-row + .code-row {
  border-top: 1px solid var(--line, #e5e7eb);
}
.code-main {
  min-width: 0;
}
.code-main > div:first-child {
  display: flex;
  align-items: center;
  gap: 6px;
}
.empty-note {
  padding: 32px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.code {
  margin-left: 4px;
}

@media (max-width: 900px) {
  .split {
    flex-direction: column;
  }
  .side {
    width: 100%;
    flex: 1 1 auto;
  }
}
</style>
