<script setup>
/**
 * 로케이션(빈) 관리 (MST-PG-003).
 *
 * 재고주소의 마지막 물리 단계다. 여기까지 오면 재고가 놓일 자리가 정해진다.
 *   재고주소 = 플랜트 - 창고 - 빈 - 상품(SKU) - 거래처
 *
 * 다른 기준정보 화면과 달리 **서버 페이징**을 쓴다. 센터 하나에 수백~수천
 * 건이 생기므로 전체를 받아 화면에서 거르면 첫 화면이 멈춘다. 그래서 검색
 * 조건이 바뀌면 서버에 다시 물어본다.
 *
 * 로케이션코드는 바꿀 수 없다 — 이미 인쇄된 라벨이 현장에 붙어 있으므로
 * 코드를 바꾸면 그 라벨이 다른 곳을 가리킨다.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as locationApi from '@/api/location.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const loadError = ref('')
const loading = ref(false)

/** 서버가 돌려준 현재 페이지 */
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = locationApi.PAGE_SIZE

const filters = reactive({
  keyword: '',
  plantId: '',
  warehouseId: '',
  locationType: '',
  useYn: '',
})

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    plantId: '',
    warehouseId: '',
    locationType: '',
    useYn: '',
  })
}

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await locationApi.list({ ...filters, page: page.value, size })
    rows.value = data.rows
    total.value = data.total
  } catch (e) {
    rows.value = []
    total.value = 0
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

/** 검색 조건이 바뀌면 1페이지부터 다시 읽는다 */
async function search() {
  page.value = 1
  await fetchPage()
}

onMounted(async () => {
  // 플랜트·창고 드롭다운이 필요하다
  await hierarchy.loadPlants(false)
  await hierarchy.loadWarehouses(false)
  await fetchPage()
})

/** 플랜트를 바꾸면 그 플랜트의 창고만 고를 수 있어야 한다 */
watch(
  () => filters.plantId,
  () => {
    filters.warehouseId = ''
    search()
  },
)

const warehouseOptions = computed(() =>
  filters.plantId ? hierarchy.warehouseOptionsOf(filters.plantId) : [],
)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

const columns = [
  { key: 'locationId', label: '로케이션코드', width: '124px', sortable: true, cls: 'code' },
  { key: 'plantName', label: '플랜트', width: '140px' },
  { key: 'warehouseName', label: '창고', width: '132px' },
  { key: 'locationType', label: '유형', width: '84px', align: 'center' },
  { key: 'sector', label: '섹터', width: '60px', align: 'center' },
  { key: 'zoneCode', label: '구역', width: '60px', align: 'center' },
  { key: 'floorNo', label: '층', width: '54px', align: 'center' },
  { key: 'labelBarcode', label: '라벨 바코드', width: '124px', cls: 'code' },
  { key: 'useYn', label: '사용', width: '64px', align: 'center' },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_LOCATION',
  pk: 'locationId',
  label: '로케이션',
  nameOf: (l) => `${l.locationId} (${l.warehouseName})`,
  api: {
    create: (payload) => locationApi.create(payload),
    update: (locationId, payload) => locationApi.update(locationId, payload),
    remove: (locationId) => locationApi.remove(locationId, '로케이션 삭제'),
  },
  // 서버 페이징이라 현재 페이지만 다시 읽는다. 등록한 행이 다른 페이지에
  // 있을 수 있으므로, 코드로 찾아갈 수 있게 검색어에 넣어 주는 편이
  // 페이지를 헤매는 것보다 낫다.
  async afterChange({ action, result }) {
    if (action === 'create' && result?.location) {
      filters.keyword = result.location.locationId
      await search()
      toast.success(`등록한 로케이션 ${result.location.locationId} 을(를) 검색어에 넣었습니다.`)
    } else {
      await fetchPage()
    }
  },
  blank: () => ({
    locationId: '',
    plantId: filters.plantId || '',
    warehouseId: filters.warehouseId || '',
    sector: '',
    zoneCode: '',
    floorNo: '',
    locationType: 'NORMAL',
    barcode: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    locationId: row.locationId,
    plantId: row.plantId,
    warehouseId: row.warehouseId,
    sector: row.sector ?? '',
    zoneCode: row.zoneCode ?? '',
    floorNo: row.floorNo ?? '',
    locationType: row.locationType,
    barcode: row.barcode ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    plantId: f.plantId,
    warehouseId: f.warehouseId,
    locationId: f.locationId,
    sector: f.sector || null,
    zoneCode: f.zoneCode || null,
    floorNo: f.floorNo || null,
    locationType: f.locationType,
    barcode: f.barcode || null,
    sortOrder: Number(f.sortOrder) || 0,
    useYn: f.useYn,
  }),
  validate(f) {
    const e = {}
    if (!f.plantId) e.plantId = '소속 플랜트를 선택하세요.'
    if (!f.warehouseId) e.warehouseId = '소속 창고를 선택하세요.'
    if (!f.locationId?.trim()) e.locationId = '로케이션코드는 필수입니다.'
    // 공백과 한글은 막는다. 바코드로 인쇄했을 때 스캐너가 읽지 못하거나
    // 잘려도 조용히 다른 로케이션이 되어 버린다.
    else if (!/^[A-Z0-9][A-Z0-9-]{1,29}$/.test(f.locationId))
      e.locationId = '영문 대문자·숫자·하이픈 2~30자. 예) 1A-01-01'
    if (!f.locationType) e.locationType = '로케이션유형을 선택하세요.'
    if (f.barcode && !/^[A-Z0-9-]{2,50}$/.test(f.barcode))
      e.barcode = '영문 대문자·숫자·하이픈 2~50자로 입력하세요.'
    return e
  },
})

/** 폼에서 고른 플랜트의 창고만 */
const formWarehouseOptions = computed(() =>
  form.value.plantId ? hierarchy.warehouseOptionsOf(form.value.plantId) : [],
)

/**
 * 창고유형과 로케이션유형이 어긋나면 알린다.
 *
 * 막지 않는다 — 양품창고에 불량 격리 빈을 하나 두는 정당한 구성이 있고,
 * 운송중(TRANSIT)은 어느 창고에든 붙는다. 다만 재고의 판매가능 여부는
 * 창고유형이 정하므로 의도한 것인지 확인해야 한다. 서버도 같은 판단을 한다.
 */
const typeMismatchNotice = computed(() => {
  const { plantId, warehouseId, locationType } = form.value
  if (!plantId || !warehouseId || !locationType) return ''
  if (locationType === 'TRANSIT') return ''
  const whType = hierarchy.warehouseTypeOf(plantId, warehouseId)
  if (!whType || whType === locationType) return ''
  if (whType === 'GOOD' && locationType === 'NORMAL') return ''
  return `창고유형(${whType})과 로케이션유형(${locationType})이 다릅니다. 재고의 판매가능 여부는 창고유형이 정하므로, 의도한 구성인지 확인하세요.`
})

/** 바코드를 비우면 로케이션코드가 라벨이 된다 */
const labelPreview = computed(() => form.value.barcode || form.value.locationId || '-')

const readDenyReason = computed(() => session.denyReason('MST_LOCATION', 'R'))
const scopeNotice = computed(() => session.scopeNotice('MST_LOCATION'))

/** 플랜트가 하나뿐이면 골라 둔다 (센터 계정은 대개 하나만 보인다) */
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
        <h1 class="page-title">로케이션 관리</h1>
        <p class="page-desc">
          창고 안의 빈(적치·피킹 단위)을 관리합니다. 로케이션코드는 전사에서 유일하며
          <strong>라벨로 인쇄되어 현장에 붙기 때문에 등록 후 바꿀 수 없습니다</strong>.
          건수가 많아 목록은 서버에서 {{ size }}건씩 나눠 받습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '로케이션 등록'"
          @click="openCreate()"
        >
          + 로케이션 등록
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
          placeholder="로케이션코드 / 바코드 / 섹터 / 구역"
          @keyup.enter="search()"
        />
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
        />
        <FormField
          v-model="filters.warehouseId"
          label="창고"
          type="select"
          empty-option="전체"
          :options="warehouseOptions"
          :disabled="!filters.plantId"
          :help="filters.plantId ? null : '플랜트를 먼저 고르세요'"
          @change="search()"
        />
        <FormField
          v-model="filters.locationType"
          label="유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('LOC_TYPE')"
          @change="search()"
        />
        <FormField
          v-model="filters.useYn"
          label="사용"
          type="select"
          empty-option="전체"
          :options="codeOptions('USE_YN')"
          @change="search()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
          <button class="btn" @click="resetFilters(); search()">초기화</button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="locationId"
        :page-size="0"
        :show-pager="false"
        :muted-when="(l) => l.useYn !== 'Y'"
        empty-text="조건에 맞는 로케이션이 없습니다."
      >
        <template #cell-locationType="{ value }">
          <CodeBadge group="LOC_TYPE" :code="value" />
        </template>

        <template #cell-sector="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
        </template>

        <template #cell-zoneCode="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
        </template>

        <template #cell-floorNo="{ value }">
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

      <!--
        서버 페이징이라 DataTable 의 내장 페이저를 쓰지 않는다. 화면이 들고
        있는 것은 현재 페이지뿐이므로, 페이지를 옮기면 서버에 다시 물어본다.
      -->
      <div class="pager">
        <span class="small dim">
          총 {{ total }}건 · {{ page }} / {{ totalPages }} 페이지
        </span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(1)">« 처음</button>
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">‹ 이전</button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">
            다음 ›
          </button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(totalPages)">
            마지막 »
          </button>
        </div>
      </div>
    </div>

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '로케이션 등록' : '로케이션 수정'"
      :subtitle="mode === 'edit' ? form.locationId : '로케이션코드는 등록 후 변경할 수 없습니다 (라벨이 현장에 붙습니다).'"
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
          :error="errors.plantId"
        />
        <FormField
          v-model="form.warehouseId"
          label="소속 창고"
          type="select"
          required
          empty-option="선택하세요"
          :options="formWarehouseOptions"
          :disabled="!form.plantId"
          :error="errors.warehouseId"
          :help="form.plantId ? null : '플랜트를 먼저 고르세요'"
        />
        <FormField
          v-model="form.locationId"
          label="로케이션코드"
          required
          mono
          placeholder="1A-01-01"
          :disabled="mode === 'edit'"
          :error="errors.locationId"
          help="전사에서 유일해야 합니다. 영문 대문자·숫자·하이픈 2~30자"
        />
        <FormField
          v-model="form.locationType"
          label="로케이션유형"
          type="select"
          required
          :options="codeOptions('LOC_TYPE')"
          :error="errors.locationType"
        />
        <FormField v-model="form.sector" label="섹터" placeholder="1" />
        <FormField v-model="form.zoneCode" label="구역" placeholder="A" />
        <FormField v-model="form.floorNo" label="층" placeholder="1" />
        <FormField
          v-model="form.barcode"
          label="바코드"
          mono
          placeholder="비우면 로케이션코드를 씁니다"
          :error="errors.barcode"
          :help="`라벨에 찍힐 값: ${labelPreview}`"
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="typeMismatchNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ typeMismatchNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">코드·바코드 중복과 데이터 범위는 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="로케이션 삭제"
      :message="deleteMessage"
      detail="현장에 붙은 라벨이 있으면 함께 떼어야 합니다. 재고가 남아 있는 로케이션은 재고 기능이 생긴 뒤 삭제가 막힙니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
</style>
