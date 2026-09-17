<script setup>
/**
 * 창고 관리 (MST-PG-002).
 *
 * 창고는 플랜트 안의 구획이고, 창고유형(양품/반품/불량)이 재고의 판매가능
 * 여부를 가른다. 양품 창고의 재고만 판매가능수량에 들어간다.
 *
 * 창고코드는 플랜트 안에서만 유일하다. 센터마다 GD(양품) 창고가 있는 것이
 * 정상이므로, 코드 하나로는 창고가 특정되지 않는다. 그래서 화면 · API 모두
 * 플랜트코드와 창고코드를 짝으로 다룬다 — useCrud 는 단일 PK 를 전제하므로
 * `플랜트코드/창고코드` 합성 키(_key)를 만들어 넘긴다.
 *
 * 데이터 범위(COM-PG-004)는 소속 플랜트의 운영 조직을 따른다.
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as warehouseApi from '@/api/warehouse.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import DetailDialog from '@/components/DetailDialog.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()

const loadError = ref('')
const table = ref(null)

const filters = reactive({ keyword: '', plantId: '', warehouseType: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', plantId: '', warehouseType: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    // 소속 플랜트 드롭다운이 플랜트 목록을 쓴다
    await hierarchy.loadPlants(force)
    await hierarchy.loadWarehouses(force)
    if (hierarchy.denyReason.warehouses) loadError.value = hierarchy.denyReason.warehouses
  } catch (e) {
    loadError.value = e.message
  }
}

onMounted(() => reload(false))

/** 창고코드만으로는 행을 특정할 수 없다. 플랜트와 묶은 합성 키를 둔다. */
const keyOf = (w) => `${w.plantId}/${w.warehouseId}`

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return hierarchy.warehouses
    .filter((w) => !filters.plantId || w.plantId === filters.plantId)
    .filter((w) => !filters.warehouseType || w.warehouseType === filters.warehouseType)
    .filter((w) => !filters.useYn || w.useYn === filters.useYn)
    .filter(
      (w) =>
        !kw ||
        [w.warehouseId, w.warehouseName, w.positionDesc].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
    .map((w) => ({ ...w, _key: keyOf(w) }))
})

/* 상세 보기 — 목록에 없는 칸(위치 설명 · 비고)이 여기 있다 */
const detail = ref(null)
const detailFields = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: '플랜트', value: d.plantName },
    { label: '창고코드', value: d.warehouseId, mono: true },
    { label: '창고명', value: d.warehouseName },
    { label: '유형', slot: 'warehouseType' },
    { label: '빈', value: d.locationCount != null ? d.locationCount + '개' : null },
    { label: '사용', slot: 'useYn' },
    { label: '위치', value: d.positionDesc, span: true },
    { label: '비고', value: d.remark, span: true },
  ]
})

const columns = [
  { key: 'plantName', label: '플랜트', width: '150px', sortable: true },
  { key: 'warehouseId', label: '창고코드', width: '96px', sortable: true, cls: 'code' },
  { key: 'warehouseName', label: '창고명', width: '160px', sortable: true },
  { key: 'warehouseType', label: '유형', width: '84px', align: 'center', sortable: true },
  { key: 'positionDesc', label: '위치', width: '150px' },
  { key: 'locationCount', label: '빈', width: '78px', align: 'right', sortable: true },
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
  perm: 'MST_WAREHOUSE',
  pk: '_key',
  label: '창고',
  nameOf: (w) => `${w.plantName} / ${w.warehouseName}(${w.warehouseId})`,
  api: {
    create: (payload) => warehouseApi.create(payload),
    // 합성 키를 다시 플랜트코드와 창고코드로 나눈다
    update: (key, payload) => {
      const [plantId, warehouseId] = key.split('/')
      return warehouseApi.update(plantId, warehouseId, payload)
    },
    remove: (key) => {
      const [plantId, warehouseId] = key.split('/')
      return warehouseApi.remove(plantId, warehouseId, '창고 삭제')
    },
  },
  async afterChange({ action, result }) {
    await reload()
    // 플랜트 목록의 창고 수 · 빈 수가 낡는다
    hierarchy.invalidate('plants')
    if (action === 'create' && result?.warehouse) {
      await nextTick()
      table.value?.goToKey(keyOf(result.warehouse))
    }
  },
  blank: () => ({
    _key: '',
    plantId: filters.plantId || '',
    warehouseId: '',
    warehouseName: '',
    warehouseType: 'GOOD',
    positionDesc: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    _key: row._key,
    plantId: row.plantId,
    warehouseId: row.warehouseId,
    warehouseName: row.warehouseName,
    warehouseType: row.warehouseType,
    positionDesc: row.positionDesc ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    plantId: f.plantId,
    warehouseId: f.warehouseId,
    warehouseName: f.warehouseName,
    warehouseType: f.warehouseType,
    positionDesc: f.positionDesc || null,
    sortOrder: Number(f.sortOrder) || 0,
    useYn: f.useYn,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.plantId) e.plantId = '소속 플랜트를 선택하세요.'
    if (!f.warehouseId?.trim()) e.warehouseId = '창고코드는 필수입니다.'
    else if (!/^[A-Z0-9]{2,20}$/.test(f.warehouseId))
      e.warehouseId = '영문 대문자와 숫자 2~20자. 예) GD'
    else if (
      ctx.mode === 'create' &&
      hierarchy.warehouses.some((w) => w.plantId === f.plantId && w.warehouseId === f.warehouseId)
    )
      e.warehouseId = '이 플랜트 안에 이미 같은 창고코드가 있습니다.'
    if (!f.warehouseName?.trim()) e.warehouseName = '창고명은 필수입니다.'
    else if (
      hierarchy.warehouses.some(
        (w) =>
          w.plantId === f.plantId &&
          w.warehouseName === f.warehouseName.trim() &&
          w.warehouseId !== f.warehouseId,
      )
    )
      e.warehouseName = '이 플랜트 안에 이미 같은 창고명이 있습니다.'
    if (!f.warehouseType) e.warehouseType = '창고유형을 선택하세요.'
    return e
  },
})

/**
 * 수정 중에는 소속 플랜트를 바꿀 수 없다.
 *
 * 빈코드 체계가 플랜트 단위로 정해지므로, 창고만 옮기면 그 아래 빈들이
 * 엉뚱한 곳을 가리킨다. 서버도 같은 이유로 거부한다.
 */
const plantLocked = computed(() => mode.value === 'edit')

/**
 * 창고유형을 바꾸면 딸린 빈 재고의 판매가능 여부가 함께 바뀐다.
 * 막지는 않되(반품창고를 양품으로 승격하는 정당한 경우가 있다) 알려 준다.
 */
const typeChangeNotice = computed(() => {
  if (mode.value !== 'edit') return ''
  const original = hierarchy.warehouses.find(
    (w) => w.plantId === form.value.plantId && w.warehouseId === form.value.warehouseId,
  )
  if (!original || original.warehouseType === form.value.warehouseType) return ''
  if (!original.locationCount) return ''
  return `창고유형을 바꿉니다. 딸린 빈 ${original.locationCount}개에 있는 재고의 판매가능 여부가 이 유형을 따릅니다.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  if (row.locationCount) {
    return `딸린 빈 ${row.locationCount}개가 있어 삭제할 수 없습니다. 빈을 먼저 삭제하세요. 더 이상 쓰지 않는 창고라면 사용여부를 '미사용'으로 바꾸세요.`
  }
  return '딸린 빈이 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('MST_WAREHOUSE', 'R'))
const scopeNotice = computed(() => session.scopeNotice('MST_WAREHOUSE'))

/**
 * 플랜트가 하나뿐이면 고르게 할 이유가 없다.
 * 센터 계정은 데이터 범위 때문에 실제로 하나만 보이는 경우가 많다.
 */
watch(
  () => hierarchy.plants.length,
  (n) => {
    if (n === 1 && !filters.plantId) filters.plantId = hierarchy.plants[0].plantId
  },
  { immediate: true },
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">창고 관리</h1>
        <p class="page-desc">
          플랜트 안의 구획을 관리합니다. <strong>창고유형이 재고의 판매가능 여부를 가릅니다</strong> —
          양품 창고의 재고만 판매가능수량에 들어갑니다.
          창고코드는 플랜트 안에서만 유일하므로 센터마다 같은 코드를 쓸 수 있습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '창고 등록'"
          @click="openCreate()"
        >
          + 창고 등록
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

    <div v-if="scopeNotice" class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span><span>{{ scopeNotice }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="창고코드 / 창고명 / 위치"
        />
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
        />
        <FormField
          v-model="filters.warehouseType"
          label="창고유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('WH_TYPE')"
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
          <button class="btn" :disabled="hierarchy.loading" @click="reload(true)">
            <span v-if="hierarchy.loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="_key"
        :page-size="10"
        :muted-when="(w) => w.useYn !== 'Y'"
        empty-text="조건에 맞는 창고가 없습니다."
      >
        <!-- 창고명을 누르면 상세가 열린다 (위치 설명 · 비고) -->
        <template #cell-warehouseName="{ row, value }">
          <button class="link-cell" @click="detail = row">{{ value }}</button>
        </template>

        <template #cell-warehouseType="{ value }">
          <CodeBadge group="WH_TYPE" :code="value" />
        </template>

        <template #cell-positionDesc="{ value }">
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

    <DetailDialog
      v-if="detail"
      title="창고 상세"
      :subtitle="detail.warehouseName + ' · ' + detail.plantId + '-' + detail.warehouseId"
      :fields="detailFields"
      :can-edit="canUpdate"
      :edit-deny-reason="updateDenyReason"
      @edit="openEdit(detail); detail = null"
      @close="detail = null"
    >
      <template #warehouseType>
        <CodeBadge group="WAREHOUSE_TYPE" :code="detail.warehouseType" />
      </template>
      <template #useYn>
        <CodeBadge group="USE_YN" :code="detail.useYn" />
      </template>
    </DetailDialog>

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '창고 등록' : '창고 수정'"
      :subtitle="mode === 'edit' ? form._key : '창고코드와 소속 플랜트는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.plantId"
          label="소속 플랜트"
          type="select"
          required
          empty-option="선택하세요"
          :options="hierarchy.plantOptions"
          :disabled="plantLocked"
          :error="errors.plantId"
          :help="plantLocked ? '소속 플랜트는 바꿀 수 없습니다. 빈코드 체계가 플랜트 단위로 정해집니다.' : '창고코드는 이 플랜트 안에서만 유일해야 합니다.'"
        />
        <FormField
          v-model="form.warehouseId"
          label="창고코드"
          required
          mono
          placeholder="GD"
          :disabled="mode === 'edit'"
          :error="errors.warehouseId"
          help="영문 대문자·숫자 2~20자 (예: GD, RT, DF)"
        />
        <FormField
          v-model="form.warehouseName"
          label="창고명"
          required
          placeholder="이천 양품창고"
          :error="errors.warehouseName"
        />
        <FormField
          v-model="form.warehouseType"
          label="창고유형"
          type="select"
          required
          :options="codeOptions('WH_TYPE')"
          :error="errors.warehouseType"
          help="재고의 판매가능 여부를 가릅니다."
        />
        <FormField
          v-model="form.positionDesc"
          class="grow"
          label="위치"
          placeholder="A동 1~2층"
          help="현장에서 찾아가기 위한 설명입니다."
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="typeChangeNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ typeChangeNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">플랜트 내 중복과 유형 코드값은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="창고 삭제"
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
