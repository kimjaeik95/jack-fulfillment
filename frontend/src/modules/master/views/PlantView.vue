<script setup>
/**
 * 플랜트(물류센터) 관리 — MST-PG-001 의 거점 부분.
 *
 * 재고의 원천이다. 재고주소가 여기서 시작한다.
 *   재고주소 = 플랜트 - 창고 - 빈 - 상품(SKU) - 거래처
 *
 * 조직과 1:1 로 보이지만 같은 것이 아니다. 조직개편으로 운영조직이 사라져도
 * 플랜트의 재고는 그대로 있어야 하므로, 그때 운영 조직만 바꾼다.
 *
 * 데이터 범위(COM-PG-004)가 적용된다 — 센터 계정에는 자기 조직의 플랜트만
 * 내려온다. 판정은 서버가 하고, 여기서는 왜 일부만 보이는지 알려 준다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as plantApi from '@/api/plant.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useOrgStore } from '@/stores/org.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const orgStore = useOrgStore()
const session = useSessionStore()

const loadError = ref('')
const table = ref(null)

const filters = reactive({ keyword: '', plantType: '', orgId: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', plantType: '', orgId: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    // 운영 조직 드롭다운이 조직 목록을 쓴다. 조직을 못 읽는 역할도 이 화면에
    // 들어올 수 있으므로(센터 관리자는 플랜트 R 만 갖는다) 실패해도 계속 그린다.
    await orgStore.load(force)
    await hierarchy.loadPlants(force)
    if (hierarchy.denyReason.plants) loadError.value = hierarchy.denyReason.plants
  } catch (e) {
    loadError.value = e.message
  }
}

onMounted(() => reload(false))

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return hierarchy.plants
    .filter((p) => !filters.plantType || p.plantType === filters.plantType)
    .filter((p) => !filters.orgId || p.orgId === filters.orgId)
    .filter((p) => !filters.useYn || p.useYn === filters.useYn)
    .filter(
      (p) =>
        !kw ||
        [p.plantId, p.plantName, p.managerName].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
})

const columns = [
  { key: 'plantId', label: '플랜트코드', width: '104px', sortable: true, cls: 'code' },
  { key: 'plantName', label: '플랜트명', width: '160px', sortable: true },
  { key: 'plantType', label: '유형', width: '96px', align: 'center', sortable: true },
  { key: 'orgName', label: '운영 조직', width: '150px', sortable: true },
  { key: 'managerName', label: '담당자', width: '86px' },
  { key: 'warehouseCount', label: '창고', width: '60px', align: 'right', sortable: true },
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
  perm: 'MST_PLANT',
  pk: 'plantId',
  label: '플랜트',
  nameOf: (p) => `${p.plantName}(${p.plantId})`,
  api: {
    create: (payload) => plantApi.create(payload),
    update: (plantId, payload) => plantApi.update(plantId, payload),
    remove: (plantId) => plantApi.remove(plantId, '플랜트 삭제'),
  },
  async afterChange({ action, key }) {
    await reload()
    if (action === 'create') {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    plantId: '',
    plantName: '',
    plantType: 'DC',
    orgId: '',
    zipCode: '',
    address: '',
    managerName: '',
    phone: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    plantId: row.plantId,
    plantName: row.plantName,
    plantType: row.plantType,
    orgId: row.orgId ?? '',
    zipCode: row.zipCode ?? '',
    address: row.address ?? '',
    managerName: row.managerName ?? '',
    phone: row.phone ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    zipCode: f.zipCode || null,
    address: f.address || null,
    managerName: f.managerName || null,
    phone: f.phone || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.plantId?.trim()) e.plantId = '플랜트코드는 필수입니다.'
    else if (!/^[A-Z]{2}\d{3}$/.test(f.plantId))
      e.plantId = '영문 대문자 2자 + 숫자 3자 형식. 예) PL001'
    else if (ctx.mode === 'create' && hierarchy.plants.some((p) => p.plantId === f.plantId))
      e.plantId = '이미 사용 중인 플랜트코드입니다.'
    if (!f.plantName?.trim()) e.plantName = '플랜트명은 필수입니다.'
    else if (
      hierarchy.plants.some((p) => p.plantName === f.plantName.trim() && p.plantId !== f.plantId)
    )
      e.plantName = '이미 사용 중인 플랜트명입니다.'
    if (!f.plantType) e.plantType = '플랜트유형을 선택하세요.'
    if (!f.orgId) e.orgId = '운영 조직을 선택하세요.'
    if (f.zipCode && !/^\d{5}$/.test(f.zipCode)) e.zipCode = '우편번호는 숫자 5자리입니다.'
    if (f.phone && !/^\d{2,3}-\d{3,4}-\d{4}$/.test(f.phone))
      e.phone = '02-1234-5678 형식으로 입력하세요.'
    return e
  },
})

/**
 * 운영 조직 후보.
 *
 * 물류센터 조직만 거르지 않는다 — 본사가 직접 운영하는 거점(크로스독 등)이
 * 있을 수 있고, 조직유형과 플랜트유형은 서로 다른 축이다.
 */
const orgOptions = computed(() => orgStore.orgOptions)

/**
 * 운영 조직을 옮기면 데이터 범위가 바뀐다.
 *
 * 범위 밖 조직으로 옮기면 저장한 본인이 그 플랜트를 다시 볼 수 없게 되므로
 * 서버가 거부한다. 그 전에 알려 준다.
 */
const orgChangeNotice = computed(() => {
  if (mode.value !== 'edit') return ''
  const original = hierarchy.plants.find((p) => p.plantId === form.value.plantId)
  if (!original || original.orgId === form.value.orgId) return ''
  return `운영 조직을 ${original.orgName}에서 옮깁니다. 이 플랜트의 창고·빈·재고는 그대로 남지만, 누가 볼 수 있는지(데이터 범위)가 새 조직 기준으로 바뀝니다.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  if (row.warehouseCount) {
    return `딸린 창고 ${row.warehouseCount}개(빈 ${row.locationCount ?? 0}개)가 있어 삭제할 수 없습니다. 창고를 먼저 삭제하세요. 더 이상 쓰지 않는 플랜트라면 사용여부를 '미사용'으로 바꾸세요.`
  }
  return '딸린 창고가 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('MST_PLANT', 'R'))

/**
 * 데이터 범위 안내 (COM-PG-004).
 *
 * 전사 범위가 아니면 목록에 일부만 나온다. 그게 설정 때문인지 고장인지
 * 사용자는 구분할 수 없으므로 화면이 말해 준다.
 */
const scopeNotice = computed(() => session.scopeNotice('MST_PLANT'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">플랜트 관리</h1>
        <p class="page-desc">
          물류센터·반품센터 같은 물리 거점을 관리합니다. 재고주소가 여기서 시작합니다 —
          <strong>플랜트 · 창고 · 빈 · 상품 · 거래처</strong>.
          조직개편이 있으면 플랜트를 지우지 말고 운영 조직만 옮기세요.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '플랜트 등록'"
          @click="openCreate()"
        >
          + 플랜트 등록
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

    <!-- 목록이 왜 일부만 보이는지 알려준다 (COM-PG-004) -->
    <div v-if="scopeNotice" class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span><span>{{ scopeNotice }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="플랜트코드 / 플랜트명 / 담당자"
        />
        <FormField
          v-model="filters.plantType"
          label="플랜트유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('PLANT_TYPE')"
        />
        <FormField
          v-model="filters.orgId"
          label="운영 조직"
          type="select"
          empty-option="전체"
          :options="orgOptions"
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
        row-key="plantId"
        :page-size="10"
        :muted-when="(p) => p.useYn !== 'Y'"
        empty-text="조건에 맞는 플랜트가 없습니다."
      >
        <template #cell-plantType="{ value }">
          <CodeBadge group="PLANT_TYPE" :code="value" />
        </template>

        <template #cell-managerName="{ value }">
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
      :title="mode === 'create' ? '플랜트 등록' : '플랜트 수정'"
      :subtitle="mode === 'edit' ? form.plantId : '플랜트코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.plantId"
          label="플랜트코드"
          required
          mono
          placeholder="PL001"
          :disabled="mode === 'edit'"
          :error="errors.plantId"
          help="영문 대문자 2자 + 숫자 3자 (예: PL001)"
        />
        <FormField
          v-model="form.plantName"
          label="플랜트명"
          required
          placeholder="이천물류센터"
          :error="errors.plantName"
        />
        <FormField
          v-model="form.plantType"
          label="플랜트유형"
          type="select"
          required
          :options="codeOptions('PLANT_TYPE')"
          :error="errors.plantType"
          help="거점이 하는 일을 정합니다."
        />
        <FormField
          v-model="form.orgId"
          label="운영 조직"
          type="select"
          required
          empty-option="선택하세요"
          :options="orgOptions"
          :error="errors.orgId"
          help="누가 볼 수 있는지(데이터 범위)의 기준이 됩니다."
        />
        <FormField v-model="form.managerName" label="담당자" placeholder="홍길동" />
        <FormField v-model="form.phone" label="연락처" placeholder="031-100-1000" :error="errors.phone" />
        <FormField
          v-model="form.address"
          class="grow"
          label="주소"
          placeholder="경기 이천시 마장면 물류로 10"
        />
        <FormField v-model="form.zipCode" label="우편번호" placeholder="17383" :error="errors.zipCode" />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="orgChangeNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ orgChangeNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">유형 코드값과 데이터 범위는 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="플랜트 삭제"
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
