<script setup>
/**
 * 제품분류 관리 (MST-PG-005).
 *
 * 대 · 중 · 소 3단계 트리다. 목록은 경로순으로 내려오므로 그대로 펼쳐
 * 보여주면 트리처럼 읽힌다 — 단계만큼 들여쓰기를 준다.
 *
 * 단계와 상위 분류는 짝이다. 대분류는 상위가 없고, 중·소분류는 상위가
 * 반드시 있으며 그 상위는 한 단계 위여야 한다. 서버와 DB 가 같은 규칙을
 * 걸고, 여기서는 고를 수 있는 것만 보여줘서 헛걸음을 줄인다.
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as categoryApi from '@/api/category.js'
import { useCatalogStore } from '@/stores/catalog.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const catalog = useCatalogStore()
const session = useSessionStore()

const loadError = ref('')
const table = ref(null)

const filters = reactive({ keyword: '', levelNo: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', levelNo: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    await catalog.loadCategories(force)
    if (catalog.denyReason.categories) loadError.value = catalog.denyReason.categories
  } catch (e) {
    loadError.value = e.message
  }
}

onMounted(() => reload(false))

/** 단계 선택지. 코드그룹이 아니라 고정 3단계라 여기서 만든다. */
const LEVELS = [
  { value: 1, label: '대분류' },
  { value: 2, label: '중분류' },
  { value: 3, label: '소분류' },
]
const levelLabel = (n) => LEVELS.find((l) => l.value === n)?.label ?? `${n}단계`

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return catalog.categories
    .filter((c) => !filters.levelNo || c.levelNo === Number(filters.levelNo))
    .filter((c) => !filters.useYn || c.useYn === filters.useYn)
    .filter(
      (c) =>
        !kw ||
        [c.categoryId, c.categoryName, c.pathName].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
})

const columns = [
  { key: 'categoryName', label: '분류명', width: '220px' },
  { key: 'categoryId', label: '분류코드', width: '130px', sortable: true, cls: 'code' },
  { key: 'levelNo', label: '단계', width: '84px', align: 'center', sortable: true },
  { key: 'parentName', label: '상위 분류', width: '130px' },
  { key: 'childCount', label: '하위', width: '60px', align: 'right', sortable: true },
  { key: 'productCount', label: '제품', width: '60px', align: 'right', sortable: true },
  { key: 'useYn', label: '사용', width: '64px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_CATEGORY',
  pk: 'categoryId',
  label: '분류',
  nameOf: (c) => c.pathName ?? c.categoryName,
  api: {
    create: (payload) => categoryApi.create(payload),
    update: (categoryId, payload) => categoryApi.update(categoryId, payload),
    remove: (categoryId) => categoryApi.remove(categoryId, '분류 삭제'),
  },
  async afterChange({ action, key }) {
    await reload()
    if (action === 'create') {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    categoryId: '',
    categoryName: '',
    levelNo: 1,
    parentId: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    categoryId: row.categoryId,
    categoryName: row.categoryName,
    levelNo: row.levelNo,
    parentId: row.parentId ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    levelNo: Number(f.levelNo),
    // 대분류는 상위를 가질 수 없다. 단계를 대분류로 바꾸면 이전에 고른
    // 상위가 남아 있으므로 지운다.
    parentId: Number(f.levelNo) === 1 ? null : f.parentId || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.categoryId?.trim()) e.categoryId = '분류코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9-]{1,19}$/.test(f.categoryId))
      e.categoryId = '영문 대문자·숫자·하이픈 2~20자. 예) CLO-TOP'
    else if (
      ctx.mode === 'create' && catalog.categories.some((c) => c.categoryId === f.categoryId)
    )
      e.categoryId = '이미 사용 중인 분류코드입니다.'
    if (!f.categoryName?.trim()) e.categoryName = '분류명은 필수입니다.'
    if (!f.levelNo) e.levelNo = '분류 단계를 선택하세요.'
    if (Number(f.levelNo) === 1 && f.parentId) e.parentId = '대분류는 상위 분류를 가질 수 없습니다.'
    if (Number(f.levelNo) !== 1 && !f.parentId)
      e.parentId = `${levelLabel(Number(f.levelNo))}는 상위 분류가 필요합니다.`
    // 같은 부모 아래에서만 이름이 유일하다. 다른 갈래의 같은 이름은 정상이다.
    const sameParent = catalog.categories.filter(
      (c) => (c.parentId ?? '') === (f.parentId ?? '') && c.categoryId !== f.categoryId,
    )
    if (f.categoryName && sameParent.some((c) => c.categoryName === f.categoryName.trim()))
      e.categoryName = '같은 상위 분류 아래에 이미 같은 분류명이 있습니다.'
    return e
  },
})

/** 상위로 고를 수 있는 분류 — 한 단계 위만. 자기 자신은 뺀다. */
const parentOptions = computed(() =>
  catalog.parentCategoryOptions(form.value.levelNo, form.value.categoryId),
)

/** 단계를 바꾸면 이전에 고른 상위가 맞지 않는다. 비워서 다시 고르게 한다. */
watch(
  () => form.value.levelNo,
  () => {
    if (!dlgOpen.value) return
    form.value.parentId = ''
  },
)

/**
 * 단계 변경 안내.
 *
 * 하위 분류가 있는 상태에서 단계를 바꾸면 그 하위가 대 → 중 → 소 순서를
 * 벗어난다. 서버가 막지만, 저장을 누르기 전에 알려 준다.
 */
const levelChangeNotice = computed(() => {
  if (mode.value !== 'edit') return ''
  const original = catalog.categories.find((c) => c.categoryId === form.value.categoryId)
  if (!original || original.levelNo === Number(form.value.levelNo)) return ''
  if (!original.childCount) return ''
  return `하위 분류 ${original.childCount}개가 있어 단계를 바꿀 수 없습니다. 하위를 먼저 정리하세요.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  const blockers = []
  if (row.childCount) blockers.push(`하위 분류 ${row.childCount}개`)
  if (row.productCount) blockers.push(`제품 ${row.productCount}개`)
  return blockers.length
    ? `${blockers.join(', ')}이(가) 있어 삭제할 수 없습니다. 더 이상 쓰지 않는 분류라면 사용여부를 '미사용'으로 바꾸세요.`
    : '하위 분류나 제품이 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('MST_CATEGORY', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">카테고리 관리</h1>
        <p class="page-desc">
          제품분류를 <strong>대 · 중 · 소 3단계</strong>로 관리합니다. 제품은 소분류에만 등록할 수 있습니다.
          분류명은 같은 상위 분류 아래에서만 유일하면 되므로, 상의 &gt; 티셔츠와 아동 &gt; 티셔츠는 함께 둘 수 있습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '분류 등록'"
          @click="openCreate()"
        >
          + 분류 등록
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

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="분류코드 / 분류명"
        />
        <FormField
          v-model="filters.levelNo"
          label="단계"
          type="select"
          empty-option="전체"
          :options="LEVELS"
        />
        <FormField
          v-model="filters.useYn"
          label="사용"
          type="select"
          empty-option="전체"
          :options="codeOptions('USE_YN')"
        />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
          <button class="btn" :disabled="catalog.loading" @click="reload(true)">
            <span v-if="catalog.loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="categoryId"
        :page-size="20"
        :muted-when="(c) => c.useYn !== 'Y'"
        empty-text="조건에 맞는 분류가 없습니다."
      >
        <!-- 단계만큼 들여써서 목록이 트리처럼 읽히게 한다 -->
        <template #cell-categoryName="{ row, value }">
          <span :style="{ paddingLeft: `${(row.levelNo - 1) * 18}px` }">
            <span v-if="row.levelNo > 1" class="dim">└ </span>{{ value }}
          </span>
        </template>

        <template #cell-levelNo="{ value }">
          <span class="badge">{{ levelLabel(value) }}</span>
        </template>

        <template #cell-parentName="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
        </template>

        <template #cell-useYn="{ value }">
          <CodeBadge group="USE_YN" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              class="btn btn-sm"
              :disabled="!canUpdate"
              :title="updateDenyReason ?? '수정'"
              @click="openEdit(row)"
            >
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
      :title="mode === 'create' ? '분류 등록' : '분류 수정'"
      :subtitle="mode === 'edit' ? form.categoryId : '분류코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.categoryId"
          label="분류코드"
          required
          mono
          placeholder="CLO-TOP"
          :disabled="mode === 'edit'"
          :error="errors.categoryId"
          help="영문 대문자·숫자·하이픈 2~20자"
        />
        <FormField
          v-model="form.categoryName"
          label="분류명"
          required
          placeholder="상의"
          :error="errors.categoryName"
        />
        <FormField
          v-model="form.levelNo"
          label="분류 단계"
          type="select"
          required
          :options="LEVELS"
          :error="errors.levelNo"
          help="제품은 소분류에만 등록할 수 있습니다."
        />
        <FormField
          v-model="form.parentId"
          label="상위 분류"
          type="select"
          :empty-option="Number(form.levelNo) === 1 ? '없음 (최상위)' : '선택하세요'"
          :options="parentOptions"
          :disabled="Number(form.levelNo) === 1"
          :error="errors.parentId"
          :help="Number(form.levelNo) === 1 ? '대분류는 상위가 없습니다.' : '한 단계 위 분류만 고를 수 있습니다.'"
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="levelChangeNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ levelChangeNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">계층 규칙과 순환 참조는 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="분류 삭제"
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
