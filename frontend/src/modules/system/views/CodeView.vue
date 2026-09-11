<script setup>
/**
 * 공통코드 관리 (COM-PG-006) — 실제 서버 API 연동.
 *
 * 코드는 화면의 셀렉트박스·배지 라벨이자 서버의 값 검증 기준이다.
 * 예전에는 프론트 상수(api/codes.js)에 적어 뒀는데, 그러면 이 화면에서
 * 고쳐도 쓰는 곳이 모른다. 이제 서버가 단일 출처다.
 *
 * 왼쪽에서 그룹을 고르고 오른쪽에서 그 안의 코드를 편집한다.
 * 그룹이 9개뿐이라 목록 전체를 왼쪽에 펼쳐 두는 편이 찾기 쉽다.
 *
 * 저장하면 loadCodes(true) 로 앱 전체의 라벨을 다시 받는다.
 * 그러지 않으면 이 화면에서는 바뀌었는데 다른 화면은 옛 라벨을 계속 쓴다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { loadCodes } from '@/api/codes.js'
import * as codeApi from '@/api/code.js'
import * as exportApi from '@/api/export.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'

const session = useSessionStore()
const toast = useToastStore()

/** 배지 색상 — styles.css 의 badge-* 와 짝이 맞아야 한다 */
const COLOR_OPTIONS = [
  'slate', 'gray', 'blue', 'green', 'amber', 'red', 'violet', 'teal', 'pink', 'cyan',
].map((c) => ({ value: c, label: c }))

const groups = ref([])
const selectedGroupId = ref('')
const codes = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', useYn: '' })

const canCreate = computed(() => session.can('SYS_CODE', 'C'))
const canUpdate = computed(() => session.can('SYS_CODE', 'U'))
const canDelete = computed(() => session.can('SYS_CODE', 'D'))
const createDenyReason = computed(() => session.denyReason('SYS_CODE', 'C'))
const updateDenyReason = computed(() => session.denyReason('SYS_CODE', 'U'))
const deleteDenyReason = computed(() => session.denyReason('SYS_CODE', 'D'))
const readDenyReason = computed(() => session.denyReason('SYS_CODE', 'R'))

const selectedGroup = computed(
  () => groups.value.find((g) => g.codeGroupId === selectedGroupId.value) ?? null,
)

async function loadGroups(keepSelection = true) {
  loading.value = true
  loadError.value = ''
  try {
    groups.value = await codeApi.listGroups(filters)
    if (!keepSelection || !groups.value.some((g) => g.codeGroupId === selectedGroupId.value)) {
      selectedGroupId.value = groups.value[0]?.codeGroupId ?? ''
    }
    if (selectedGroupId.value) await loadCodesOf(selectedGroupId.value)
    else codes.value = []
  } catch (e) {
    // 권한 부족(403)도 여기로 온다. 사유를 그대로 보여준다.
    loadError.value = e.message
    groups.value = []
    codes.value = []
  } finally {
    loading.value = false
  }
}

async function loadCodesOf(groupId) {
  try {
    const detail = await codeApi.getGroup(groupId)
    codes.value = detail.codes ?? []
  } catch (e) {
    toast.error(e.message)
    codes.value = []
  }
}

async function selectGroup(groupId) {
  if (groupId === selectedGroupId.value) return
  selectedGroupId.value = groupId
  await loadCodesOf(groupId)
}

onMounted(() => loadGroups(false))

/** 저장 후 — 목록과 앱 전체 라벨을 함께 갱신한다 */
async function refreshAll() {
  await loadGroups()
  await loadCodes(true)
}

/* ------------------------------------------------------------------ */
/* 코드그룹 등록 · 수정                                                 */
/* ------------------------------------------------------------------ */

const groupDlg = ref(false)
const groupMode = ref('create')
const groupBusy = ref(false)
const groupError = ref('')
const groupErrors = reactive({})
const groupForm = reactive({ codeGroupId: '', codeGroupName: '', description: '', useYn: 'Y' })

function openGroupCreate() {
  if (!canCreate.value) return toast.error(createDenyReason.value)
  groupMode.value = 'create'
  Object.assign(groupForm, { codeGroupId: '', codeGroupName: '', description: '', useYn: 'Y' })
  Object.keys(groupErrors).forEach((k) => delete groupErrors[k])
  groupError.value = ''
  groupDlg.value = true
}

function openGroupEdit(group) {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  groupMode.value = 'edit'
  Object.assign(groupForm, {
    codeGroupId: group.codeGroupId,
    codeGroupName: group.codeGroupName,
    description: group.description ?? '',
    useYn: group.useYn,
  })
  Object.keys(groupErrors).forEach((k) => delete groupErrors[k])
  groupError.value = ''
  groupDlg.value = true
}

async function saveGroup() {
  Object.keys(groupErrors).forEach((k) => delete groupErrors[k])
  if (!groupForm.codeGroupId?.trim()) groupErrors.codeGroupId = '코드그룹ID는 필수입니다.'
  else if (!/^[A-Z][A-Z0-9_]{2,29}$/.test(groupForm.codeGroupId))
    groupErrors.codeGroupId = '영문 대문자로 시작하는 3~30자 (숫자·밑줄 허용). 예) ORG_TYPE'
  else if (groupMode.value === 'create' && groups.value.some((g) => g.codeGroupId === groupForm.codeGroupId))
    groupErrors.codeGroupId = '이미 사용 중인 코드그룹ID입니다.'
  if (!groupForm.codeGroupName?.trim()) groupErrors.codeGroupName = '코드그룹명은 필수입니다.'
  if (Object.keys(groupErrors).length) return toast.warn('입력값을 확인하세요.')

  groupBusy.value = true
  groupError.value = ''
  try {
    if (groupMode.value === 'create') {
      await codeApi.createGroup({ ...groupForm })
      toast.success('코드그룹을 등록했습니다.')
    } else {
      await codeApi.updateGroup(groupForm.codeGroupId, { ...groupForm })
      toast.success('코드그룹을 수정했습니다.')
    }
    groupDlg.value = false
    selectedGroupId.value = groupForm.codeGroupId
    await refreshAll()
  } catch (e) {
    // 보호 그룹 차단 등 업무 규칙 위반은 모달을 닫지 않고 사유를 보여준다
    groupError.value = e.message
    toast.error(e.message)
  } finally {
    groupBusy.value = false
  }
}

const groupToDelete = ref(null)
const groupDeleting = ref(false)

function confirmGroupDelete(group) {
  if (!canDelete.value) return toast.error(deleteDenyReason.value)
  groupToDelete.value = group
}

async function doGroupDelete() {
  groupDeleting.value = true
  try {
    await codeApi.removeGroup(groupToDelete.value.codeGroupId, '코드그룹 삭제')
    toast.success('코드그룹을 삭제했습니다.')
    groupToDelete.value = null
    await loadGroups(false)
    await loadCodes(true)
  } catch (e) {
    toast.error(e.message)
    groupToDelete.value = null
  } finally {
    groupDeleting.value = false
  }
}

/* ------------------------------------------------------------------ */
/* 코드 등록 · 수정                                                     */
/* ------------------------------------------------------------------ */

const codeDlg = ref(false)
const codeMode = ref('create')
const codeBusy = ref(false)
const codeError = ref('')
const codeErrors = reactive({})
const codeForm = reactive({
  codeId: '', codeName: '', description: '', color: 'gray', sortOrder: 0, useYn: 'Y',
})

function openCodeCreate() {
  if (!canCreate.value) return toast.error(createDenyReason.value)
  if (!selectedGroupId.value) return toast.warn('코드그룹을 먼저 선택하세요.')
  codeMode.value = 'create'
  Object.assign(codeForm, {
    codeId: '', codeName: '', description: '', color: 'gray',
    sortOrder: (codes.value.length + 1) * 10, useYn: 'Y',
  })
  Object.keys(codeErrors).forEach((k) => delete codeErrors[k])
  codeError.value = ''
  codeDlg.value = true
}

function openCodeEdit(code) {
  if (!canUpdate.value) return toast.error(updateDenyReason.value)
  codeMode.value = 'edit'
  Object.assign(codeForm, {
    codeId: code.codeId,
    codeName: code.codeName,
    description: code.description ?? '',
    color: code.color ?? 'gray',
    sortOrder: code.sortOrder ?? 0,
    useYn: code.useYn,
  })
  Object.keys(codeErrors).forEach((k) => delete codeErrors[k])
  codeError.value = ''
  codeDlg.value = true
}

async function saveCode() {
  Object.keys(codeErrors).forEach((k) => delete codeErrors[k])
  if (!codeForm.codeId?.trim()) codeErrors.codeId = '코드값은 필수입니다.'
  else if (!/^[A-Z0-9][A-Z0-9_]{0,29}$/.test(codeForm.codeId))
    codeErrors.codeId = '영문 대문자·숫자로 시작하는 1~30자 (밑줄 허용). 예) ACTIVE'
  else if (codeMode.value === 'create' && codes.value.some((c) => c.codeId === codeForm.codeId))
    codeErrors.codeId = '이 그룹에 이미 있는 코드값입니다.'
  if (!codeForm.codeName?.trim()) codeErrors.codeName = '코드명은 필수입니다.'
  if (Object.keys(codeErrors).length) return toast.warn('입력값을 확인하세요.')

  codeBusy.value = true
  codeError.value = ''
  try {
    const payload = { ...codeForm, sortOrder: Number(codeForm.sortOrder) || 0 }
    if (codeMode.value === 'create') {
      await codeApi.createCode(selectedGroupId.value, payload)
      toast.success('코드를 등록했습니다.')
    } else {
      await codeApi.updateCode(selectedGroupId.value, codeForm.codeId, payload)
      toast.success('코드를 수정했습니다.')
    }
    codeDlg.value = false
    await refreshAll()
  } catch (e) {
    codeError.value = e.message
    toast.error(e.message)
  } finally {
    codeBusy.value = false
  }
}

const codeToDelete = ref(null)
const codeDeleting = ref(false)

function confirmCodeDelete(code) {
  if (!canDelete.value) return toast.error(deleteDenyReason.value)
  codeToDelete.value = code
}

async function doCodeDelete() {
  codeDeleting.value = true
  try {
    await codeApi.removeCode(selectedGroupId.value, codeToDelete.value.codeId, '코드 삭제')
    toast.success('코드를 삭제했습니다.')
    codeToDelete.value = null
    await refreshAll()
  } catch (e) {
    // 보호 그룹은 삭제 대신 미사용 전환을 쓰라는 안내가 온다
    toast.error(e.message)
    codeToDelete.value = null
  } finally {
    codeDeleting.value = false
  }
}
/* ------------------------------------------------------------------ */
/* CSV 다운로드 (COM-PG-011)                                           */
/* ------------------------------------------------------------------ */

/**
 * 조회할 수 있다고 내려받아도 되는 것은 아니다. 파일로 나간 데이터는
 * 회수할 수 없어 서버가 다운로드 액션(X)을 따로 판정한다.
 *
 * 그룹 안에 코드가 들어 있는 구조지만 파일은 평면으로 받는다 —
 * 업로드 템플릿과 열이 같아야 내려받아 고친 뒤 그대로 올릴 수 있다.
 */
const downloadDenyReason = computed(() => session.denyReason('SYS_CODE', 'X'))
const downloading = ref('')

/** @param {'xlsx'|'csv'} format 엑셀이 기본 */
async function downloadAs(format) {
  downloading.value = format
  try {
    await exportApi.codes({ ...filters }, format)
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
        <h1 class="page-title">공통코드</h1>
        <p class="page-desc">
          화면의 셀렉트박스·배지 라벨이자 서버의 값 검증 기준입니다. 여기서 고치면 모든 화면에 바로 반영됩니다.
          서버가 값 검증에 쓰는 그룹(권한 액션·조직유형 등)은 삭제·사용중지할 수 없습니다.
        </p>
      </div>
      <div class="page-head-actions">
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
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '코드그룹 등록'"
          @click="openGroupCreate"
        >
          + 코드그룹 등록
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

    <div style="display: grid; grid-template-columns: 300px 1fr; gap: 14px; align-items: start">
      <!-- 코드그룹 -->
      <div class="card">
        <div class="card-head">
          <span class="card-title">코드그룹 ({{ groups.length }})</span>
        </div>
        <div class="card-body tight">
          <div class="toolbar" style="padding: 8px 10px">
            <FormField
              v-model="filters.keyword"
              class="grow"
              placeholder="그룹ID / 그룹명"
              @enter="loadGroups()"
            />
            <button class="btn btn-sm" :disabled="loading" @click="loadGroups()">조회</button>
          </div>
          <div
            v-for="g in groups"
            :key="g.codeGroupId"
            class="nav-item"
            :class="{ active: g.codeGroupId === selectedGroupId }"
            :style="g.useYn !== 'Y' ? { opacity: 0.5 } : null"
            :title="g.description"
            style="border-radius: 0; padding: 8px 12px"
            @click="selectGroup(g.codeGroupId)"
          >
            <div style="min-width: 0">
              <div class="bold" style="font-size: 12.5px">{{ g.codeGroupName }}</div>
              <div class="mono dim" style="font-size: 10.5px">{{ g.codeGroupId }}</div>
            </div>
            <span class="nav-count">{{ g.codeCount }}</span>
          </div>
          <div v-if="!groups.length && !loadError" class="dim small" style="padding: 12px">
            조건에 맞는 코드그룹이 없습니다.
          </div>
        </div>
      </div>

      <!-- 코드 -->
      <div class="card">
        <div class="card-head">
          <div>
            <span class="card-title">{{ selectedGroup?.codeGroupName ?? '코드그룹을 선택하세요' }}</span>
            <span v-if="selectedGroup" class="mono dim small"> {{ selectedGroup.codeGroupId }}</span>
            <span v-if="selectedGroup && selectedGroup.useYn !== 'Y'" class="badge badge-gray">미사용</span>
          </div>
          <div class="btn-row">
            <button
              v-if="selectedGroup"
              class="btn btn-sm"
              :disabled="!canUpdate"
              :title="updateDenyReason ?? '그룹 정보 수정'"
              @click="openGroupEdit(selectedGroup)"
            >
              그룹 수정
            </button>
            <button
              v-if="selectedGroup"
              class="btn btn-sm btn-danger"
              :disabled="!canDelete"
              :title="deleteDenyReason ?? '그룹 삭제'"
              @click="confirmGroupDelete(selectedGroup)"
            >
              그룹 삭제
            </button>
            <button
              class="btn btn-sm btn-primary"
              :disabled="!canCreate || !selectedGroupId"
              :title="createDenyReason ?? '코드 등록'"
              @click="openCodeCreate"
            >
              + 코드 등록
            </button>
          </div>
        </div>

        <div v-if="selectedGroup?.description" class="alert alert-info" style="margin: 12px 14px 0">
          <span class="alert-icon">ℹ</span><span>{{ selectedGroup.description }}</span>
        </div>

        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th style="width: 130px">코드값</th>
                <th style="width: 150px">코드명</th>
                <th style="width: 86px; text-align: center">배지</th>
                <th>설명</th>
                <th style="width: 74px; text-align: right">정렬</th>
                <th style="width: 68px; text-align: center">사용</th>
                <th style="width: 112px; text-align: right"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!codes.length">
                <td colspan="7">
                  <div class="table-empty">
                    <span class="table-empty-icon">☰</span>
                    이 그룹에는 코드가 없습니다.
                  </div>
                </td>
              </tr>
              <tr v-for="c in codes" :key="c.codeId" :class="{ muted: c.useYn !== 'Y' }">
                <td class="code">{{ c.codeId }}</td>
                <td>{{ c.codeName }}</td>
                <td style="text-align: center">
                  <span class="badge" :class="`badge-${c.color ?? 'gray'}`">{{ c.codeName }}</span>
                </td>
                <td>
                  <span v-if="c.description" class="truncate small" :title="c.description">{{ c.description }}</span>
                  <span v-else class="dim">-</span>
                </td>
                <td style="text-align: right" class="mono small">{{ c.sortOrder }}</td>
                <td style="text-align: center">
                  <span class="badge" :class="c.useYn === 'Y' ? 'badge-green' : 'badge-gray'">
                    {{ c.useYn === 'Y' ? '사용' : '미사용' }}
                  </span>
                </td>
                <td style="text-align: right">
                  <div class="btn-row" style="justify-content: flex-end">
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
                      @click="confirmCodeDelete(c)"
                    >
                      삭제
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 코드그룹 등록/수정 -->
    <ModalDialog
      v-if="groupDlg"
      :title="groupMode === 'create' ? '코드그룹 등록' : '코드그룹 수정'"
      :subtitle="groupMode === 'edit' ? groupForm.codeGroupId : '그룹ID는 등록 후 변경할 수 없습니다.'"
      @close="groupDlg = false"
    >
      <div v-if="groupError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ groupError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="groupForm.codeGroupId"
          label="코드그룹ID"
          required
          mono
          placeholder="SHIP_TYPE"
          :disabled="groupMode === 'edit'"
          :error="groupErrors.codeGroupId"
          help="화면·서버 코드가 이 이름으로 참조합니다."
        />
        <FormField
          v-model="groupForm.codeGroupName"
          label="코드그룹명"
          required
          placeholder="배송 유형"
          :error="groupErrors.codeGroupName"
        />
        <FormField v-model="groupForm.description" label="설명" span placeholder="어디에 쓰이는 코드인지" />
        <FormField v-model="groupForm.useYn" label="사용여부" type="switch" />
      </div>

      <template #footer>
        <span class="left small dim">중복·보호 그룹 여부는 저장 시 서버가 검증합니다.</span>
        <button class="btn" :disabled="groupBusy" @click="groupDlg = false">취소</button>
        <button class="btn btn-primary" :disabled="groupBusy" @click="saveGroup">
          <span v-if="groupBusy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <!-- 코드 등록/수정 -->
    <ModalDialog
      v-if="codeDlg"
      :title="codeMode === 'create' ? '코드 등록' : '코드 수정'"
      :subtitle="`${selectedGroup?.codeGroupName ?? ''}${codeMode === 'edit' ? ` · ${codeForm.codeId}` : ''}`"
      @close="codeDlg = false"
    >
      <div v-if="codeError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ codeError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="codeForm.codeId"
          label="코드값"
          required
          mono
          placeholder="EXPRESS"
          :disabled="codeMode === 'edit'"
          :error="codeErrors.codeId"
          help="업무 데이터가 이 값을 그대로 저장합니다. 등록 후 변경 불가."
        />
        <FormField
          v-model="codeForm.codeName"
          label="코드명"
          required
          placeholder="특급배송"
          :error="codeErrors.codeName"
        />
        <FormField
          v-model="codeForm.color"
          label="배지 색상"
          type="select"
          :options="COLOR_OPTIONS"
          help="목록·배지에 쓰이는 색"
        />
        <FormField v-model="codeForm.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="codeForm.description" label="설명" span placeholder="이 코드의 의미" />
        <FormField v-model="codeForm.useYn" label="사용여부" type="switch" />
      </div>

      <div class="card mt-2">
        <div class="card-body flex" style="gap: 8px">
          <span class="small dim">미리보기</span>
          <span class="badge" :class="`badge-${codeForm.color}`">{{ codeForm.codeName || '코드명' }}</span>
        </div>
      </div>

      <template #footer>
        <span class="left small dim">저장하면 모든 화면의 라벨이 함께 갱신됩니다.</span>
        <button class="btn" :disabled="codeBusy" @click="codeDlg = false">취소</button>
        <button class="btn btn-primary" :disabled="codeBusy" @click="saveCode">
          <span v-if="codeBusy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="groupToDelete"
      title="코드그룹 삭제"
      :message="`'${groupToDelete.codeGroupName}(${groupToDelete.codeGroupId})' 코드그룹을 삭제합니다.`"
      :detail="groupToDelete.codeCount
        ? `그룹 안에 코드 ${groupToDelete.codeCount}건이 있어 서버가 삭제를 거부합니다. 코드를 먼저 정리하세요.`
        : '서버가 값 검증에 쓰는 그룹은 삭제할 수 없습니다.'"
      confirm-label="삭제"
      danger
      :busy="groupDeleting"
      @cancel="groupToDelete = null"
      @confirm="doGroupDelete"
    />

    <ConfirmDialog
      v-if="codeToDelete"
      title="코드 삭제"
      :message="`'${codeToDelete.codeName}(${codeToDelete.codeId})' 코드를 삭제합니다.`"
      detail="업무 데이터가 이 값을 저장하고 있으면 이름 없는 값이 남습니다. 더 이상 쓰지 않을 뿐이라면 삭제 대신 사용여부를 '미사용'으로 바꾸세요."
      confirm-label="삭제"
      danger
      :busy="codeDeleting"
      @cancel="codeToDelete = null"
      @confirm="doCodeDelete"
    />
  </div>
</template>
