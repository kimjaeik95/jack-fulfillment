<script setup>
/**
 * SKU 관리 (MST-PG-008).
 *
 * 재고 · 할당 · 입고 · 출고 · 주문이 전부 SKU 를 가리킨다. 그래서 서버가
 * 세 가지 유일성을 건다 — 내부코드 전역, 제품 내 옵션 조합, 바코드 전역.
 *
 * 제품 하나에 색상 × 사이즈 조합만큼 붙으므로 건수가 제품의 몇 배다.
 * **서버 페이징**을 쓰는 이유다.
 *
 * 바코드는 비워 둘 수 있다 — 아직 발급하지 않은 상태다. 그때 라벨에는
 * SKU 코드가 찍힌다.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as skuApi from '@/api/sku.js'
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
const loading = ref(false)

/** 서버가 돌려준 현재 페이지 */
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = skuApi.PAGE_SIZE

const filters = reactive({
  keyword: '',
  productId: '',
  categoryId: '',
  brandId: '',
  colorCode: '',
  sizeCode: '',
  status: '',
  barcodeYn: '',
})

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    productId: '',
    categoryId: '',
    brandId: '',
    colorCode: '',
    sizeCode: '',
    status: '',
    barcodeYn: '',
  })
}

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await skuApi.list({ ...filters, page: page.value, size })
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

async function search() {
  page.value = 1
  await fetchPage()
}

onMounted(async () => {
  // 제품·분류·브랜드 드롭다운이 필요하다
  await catalog.loadCategories(false)
  await catalog.loadBrands(false)
  await catalog.loadProducts(false)
  await fetchPage()
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

/** 바코드 발급 여부 선택지 */
const BARCODE_OPTIONS = [
  { value: 'Y', label: '발급됨' },
  { value: 'N', label: '미발급' },
]

const columns = [
  { key: 'skuId', label: 'SKU 코드', width: '180px', sortable: true, cls: 'code' },
  { key: 'productName', label: '제품', width: '170px', sortable: true },
  { key: 'colorCode', label: '색상', width: '84px', align: 'center', sortable: true },
  { key: 'sizeCode', label: '사이즈', width: '76px', align: 'center', sortable: true },
  { key: 'labelBarcode', label: '라벨 바코드', width: '140px', cls: 'code' },
  { key: 'status', label: '상태', width: '86px', align: 'center', sortable: true },
  { key: 'brandName', label: '브랜드', width: '100px' },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_SKU',
  pk: 'skuId',
  label: 'SKU',
  nameOf: (s) => `${s.skuId} (${s.productName})`,
  api: {
    create: (payload) => skuApi.create(payload),
    update: (skuId, payload) => skuApi.update(skuId, payload),
    remove: (skuId) => skuApi.remove(skuId, 'SKU 삭제'),
  },
  // 서버 페이징이라 등록한 행이 다른 페이지에 있을 수 있다. 코드로 찾아갈 수
  // 있게 검색어에 넣어 주는 편이 페이지를 헤매는 것보다 낫다.
  async afterChange({ action, result }) {
    if (action === 'create' && result?.sku) {
      filters.keyword = result.sku.skuId
      await search()
    } else {
      await fetchPage()
    }
  },
  blank: () => ({
    skuId: '',
    productId: filters.productId || '',
    colorCode: '',
    sizeCode: '',
    barcode: '',
    status: 'ACTIVE',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    skuId: row.skuId,
    productId: row.productId,
    colorCode: row.colorCode,
    sizeCode: row.sizeCode,
    barcode: row.barcode ?? '',
    status: row.status,
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    barcode: f.barcode || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f) {
    const e = {}
    if (!f.productId) e.productId = '제품을 선택하세요.'
    if (!f.skuId?.trim()) e.skuId = 'SKU 코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9-]{2,39}$/.test(f.skuId))
      e.skuId = '영문 대문자·숫자·하이픈 3~40자. 예) PRD-24001-BK-M'
    if (!f.colorCode) e.colorCode = '색상을 선택하세요.'
    if (!f.sizeCode) e.sizeCode = '사이즈를 선택하세요.'
    if (!f.status) e.status = 'SKU 상태를 선택하세요.'
    if (f.barcode && !/^[A-Z0-9-]{2,50}$/.test(f.barcode))
      e.barcode = '영문 대문자·숫자·하이픈 2~50자로 입력하세요.'
    return e
  },
})

/**
 * 수정 중에는 제품을 바꿀 수 없다.
 *
 * 재고와 주문이 이미 이 SKU 를 가리키고 있어서, 제품을 바꾸면 과거 거래가
 * 엉뚱한 제품의 것으로 읽힌다. 서버도 같은 이유로 거부한다.
 */
const productLocked = computed(() => mode.value === 'edit')

/**
 * SKU 코드 제안.
 *
 * 제품코드-색상-사이즈 로 만드는 것이 관행이다. 현장에서 스캔 결과를
 * 사람이 읽을 수 있어야 하기 때문이다. 등록할 때만 제안하고, 사용자가
 * 직접 고친 뒤에는 건드리지 않는다.
 */
const codeTouched = ref(false)
watch(
  () => [form.value.productId, form.value.colorCode, form.value.sizeCode],
  ([productId, color, sizeCode]) => {
    if (mode.value !== 'create' || codeTouched.value) return
    if (!productId || !color || !sizeCode) return
    form.value.skuId = `${productId}-${color}-${sizeCode}`
  },
)

/** 라벨에 실제로 찍히는 값. 바코드를 비우면 SKU 코드가 쓰인다. */
const labelPreview = computed(() => form.value.barcode || form.value.skuId || '-')

/**
 * 폐기 전환 안내.
 *
 * 정책 P002 는 재고 0 · 미처리 0 일 때만 폐기를 허용한다. 재고 기능이 아직
 * 없어 선행조건을 확인할 수 없으므로 막지 않고 알린다.
 */
const discardNotice = computed(() => {
  if (form.value.status !== 'DISCARDED') return ''
  const original = rows.value.find((s) => s.skuId === form.value.skuId)
  if (mode.value === 'edit' && original?.status === 'DISCARDED') return ''
  return '공통정책 P002 는 재고 0 · 미처리 0 일 때만 폐기를 허용합니다. 재고 기능이 아직 없어 선행조건을 확인하지 못하니 재고를 직접 확인하세요.'
})

const readDenyReason = computed(() => session.denyReason('MST_SKU', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">SKU 관리</h1>
        <p class="page-desc">
          색상 × 사이즈 단위입니다. <strong>재고 · 입고 · 출고 · 주문이 모두 SKU 를 가리킵니다.</strong>
          SKU 코드와 바코드는 전사에서 유일하고, 같은 제품 안에 같은 옵션 조합은 둘 수 없습니다.
          바코드는 나중에 발급해도 되며, 그때까지 라벨에는 SKU 코드가 찍힙니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? 'SKU 등록'"
          @click="codeTouched = false; openCreate()"
        >
          + SKU 등록
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
          placeholder="SKU 코드 / 바코드 / 제품명"
          @keyup.enter="search()"
        />
        <FormField
          v-model="filters.productId"
          label="제품"
          type="select"
          empty-option="전체"
          :options="catalog.productOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.colorCode"
          label="색상"
          type="select"
          empty-option="전체"
          :options="codeOptions('COLOR')"
          @change="search()"
        />
        <FormField
          v-model="filters.sizeCode"
          label="사이즈"
          type="select"
          empty-option="전체"
          :options="codeOptions('SIZE')"
          @change="search()"
        />
        <FormField
          v-model="filters.status"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('SKU_STATUS')"
          @change="search()"
        />
        <FormField
          v-model="filters.barcodeYn"
          label="바코드"
          type="select"
          empty-option="전체"
          :options="BARCODE_OPTIONS"
          help="미발급만 추려 라벨 출력 대상을 찾습니다"
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
        row-key="skuId"
        :page-size="0"
        :show-pager="false"
        :muted-when="(s) => s.useYn !== 'Y'"
        empty-text="조건에 맞는 SKU 가 없습니다."
      >
        <template #cell-colorCode="{ value }">
          <CodeBadge group="COLOR" :code="value" />
        </template>

        <template #cell-sizeCode="{ value }">
          <span class="badge">{{ value }}</span>
        </template>

        <!-- 바코드를 아직 발급하지 않았으면 SKU 코드가 찍힌다는 것을 표시한다 -->
        <template #cell-labelBarcode="{ row, value }">
          <span :class="{ dim: !row.barcode }">{{ value }}</span>
          <span v-if="!row.barcode" class="small dim"> (미발급)</span>
        </template>

        <template #cell-status="{ value }">
          <CodeBadge group="SKU_STATUS" :code="value" />
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

      <!-- 서버 페이징이라 DataTable 의 내장 페이저를 쓰지 않는다 -->
      <div class="pager">
        <span class="small dim">총 {{ total }}건 · {{ page }} / {{ totalPages }} 페이지</span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(1)">« 처음</button>
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">‹ 이전</button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">다음 ›</button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(totalPages)">마지막 »</button>
        </div>
      </div>
    </div>

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? 'SKU 등록' : 'SKU 수정'"
      :subtitle="mode === 'edit' ? form.skuId : 'SKU 코드와 제품은 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.productId"
          label="제품"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.productOptions"
          :disabled="productLocked"
          :error="errors.productId"
          :help="productLocked ? '제품은 바꿀 수 없습니다. 재고와 주문이 이 SKU 를 가리키고 있습니다.' : null"
        />
        <FormField
          v-model="form.colorCode"
          label="색상"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('COLOR')"
          :error="errors.colorCode"
          help="옵션이 없는 제품은 '단일'을 고르세요."
        />
        <FormField
          v-model="form.sizeCode"
          label="사이즈"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('SIZE')"
          :error="errors.sizeCode"
        />
        <FormField
          v-model="form.skuId"
          label="SKU 코드"
          required
          mono
          placeholder="PRD-24001-BK-M"
          :disabled="mode === 'edit'"
          :error="errors.skuId"
          help="제품·색상·사이즈로 자동 제안됩니다. 고쳐도 됩니다."
          @input="codeTouched = true"
        />
        <FormField
          v-model="form.barcode"
          label="바코드"
          mono
          placeholder="비우면 나중에 발급합니다"
          :error="errors.barcode"
          :help="`라벨에 찍힐 값: ${labelPreview}`"
        />
        <FormField
          v-model="form.status"
          label="SKU 상태"
          type="select"
          required
          :options="codeOptions('SKU_STATUS')"
          :error="errors.status"
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="discardNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ discardNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">코드·옵션 조합·바코드 중복은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="SKU 삭제"
      :message="deleteMessage"
      detail="재고가 남아 있는 SKU 는 재고 기능이 생긴 뒤 삭제가 막힙니다. 판매만 중단하려면 상태를 '일시중지'나 '폐기'로 바꾸세요."
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
