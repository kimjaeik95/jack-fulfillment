<script setup>
/**
 * 제품 관리 (MST-PG-007).
 *
 * 제품은 고객이 고르는 단위이고, 창고에 쌓이고 팔리는 단위는 SKU 다.
 * 제품만 등록하면 재고를 잡을 수 없어서 저장 후 그 안내가 뜬다.
 *
 * 건수가 많아 **서버 페이징**을 쓴다. 검색 조건이 바뀌면 서버에 다시
 * 물어본다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as productApi from '@/api/product.js'
import { useCatalogStore } from '@/stores/catalog.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import DetailDialog from '@/components/DetailDialog.vue'

const catalog = useCatalogStore()
const session = useSessionStore()

const loadError = ref('')
const loading = ref(false)

/** 서버가 돌려준 현재 페이지 */
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = productApi.PAGE_SIZE

const filters = reactive({
  keyword: '',
  categoryId: '',
  brandId: '',
  status: '',
  season: '',
  useYn: '',
})

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    categoryId: '',
    brandId: '',
    status: '',
    season: '',
    useYn: '',
  })
}

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await productApi.list({ ...filters, page: page.value, size })
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
  // 분류·브랜드 드롭다운이 필요하다
  await catalog.loadCategories(false)
  await catalog.loadBrands(false)
  await fetchPage()
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

/*
 * 상세 보기 — 목록에 없는 칸(원산지 · 생산일자 · 출시연도)이 여기 있다.
 * SKU 건수는 목록에도 있지만, 0 이면 재고를 잡을 수 없다는 뜻이라
 * 여기서는 숫자 대신 그 뜻을 적는다.
 */
const detail = ref(null)
const detailFields = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: '제품코드', value: d.productId, mono: true },
    { label: '제품명', value: d.productName },
    { label: '분류', value: d.categoryPath, span: true },
    { label: '브랜드', value: d.brandName },
    { label: '시즌', value: d.seasonLabel },
    { label: '출시연도', value: d.releaseYear ? `${d.releaseYear}년` : null },
    { label: '원산지', value: d.originCountry },
    { label: '생산일자', value: d.producedOn },
    { label: '원가', value: d.costAmount === null ? null : won(d.costAmount) },
    { label: '상태', slot: 'status' },
    { label: 'SKU', value: d.skuCount ? `${d.skuCount}건` : '없음 (재고를 잡을 수 없다)' },
    { label: '사용', slot: 'useYn' },
  ]
})

const columns = [
  { key: 'productId', label: '제품코드', width: '120px', sortable: true, cls: 'code' },
  { key: 'productName', label: '제품명', width: '200px', sortable: true },
  { key: 'categoryPath', label: '분류', width: '190px' },
  { key: 'brandName', label: '브랜드', width: '110px', sortable: true },
  { key: 'seasonLabel', label: '시즌', width: '72px', align: 'center' },
  { key: 'costAmount', label: '원가', width: '100px', align: 'right', sortable: true },
  { key: 'status', label: '상태', width: '86px', align: 'center', sortable: true },
  { key: 'skuCount', label: 'SKU', width: '62px', align: 'right' },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_PRODUCT',
  pk: 'productId',
  label: '제품',
  nameOf: (p) => `${p.productName}(${p.productId})`,
  api: {
    create: (payload) => productApi.create(payload),
    update: (productId, payload) => productApi.update(productId, payload),
    remove: (productId) => productApi.remove(productId, '제품 삭제'),
  },
  // 서버 페이징이라 등록한 행이 다른 페이지에 있을 수 있다. 코드로 찾아갈 수
  // 있게 검색어에 넣어 주는 편이 페이지를 헤매는 것보다 낫다.
  async afterChange({ action, result }) {
    if (action === 'create' && result?.product) {
      filters.keyword = result.product.productId
      await search()
    } else {
      await fetchPage()
    }
    // 제품 목록이 바뀌면 SKU 화면의 제품 드롭다운도 낡는다
    await catalog.loadProducts(true)
    // 분류 · 브랜드 목록의 제품 수가 낡는다
    catalog.invalidate('categories', 'brands')
  },
  blank: () => ({
    productId: '',
    productName: '',
    categoryId: filters.categoryId || '',
    brandId: filters.brandId || '',
    status: 'PLANNED',
    originCountry: '',
    producedOn: '',
    costAmount: '',
    season: '',
    releaseYear: new Date().getFullYear(),
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    productId: row.productId,
    productName: row.productName,
    categoryId: row.categoryId,
    brandId: row.brandId,
    status: row.status,
    originCountry: row.originCountry ?? '',
    producedOn: row.producedOn ?? '',
    costAmount: row.costAmount ?? '',
    season: row.season ?? '',
    releaseYear: row.releaseYear ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    originCountry: f.originCountry || null,
    producedOn: f.producedOn || null,
    costAmount: f.costAmount === '' || f.costAmount === null ? null : Number(f.costAmount),
    season: f.season || null,
    releaseYear: f.releaseYear === '' || f.releaseYear === null ? null : Number(f.releaseYear),
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.productId?.trim()) e.productId = '제품코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9-]{2,29}$/.test(f.productId))
      e.productId = '영문 대문자·숫자·하이픈 3~30자. 예) PRD-24001'
    if (!f.productName?.trim()) e.productName = '제품명은 필수입니다.'
    if (!f.categoryId) e.categoryId = '제품분류를 선택하세요.'
    if (!f.brandId) e.brandId = '브랜드를 선택하세요.'
    if (!f.status) e.status = '제품상태를 선택하세요.'
    if (f.costAmount !== '' && Number(f.costAmount) < 0) e.costAmount = '원가는 0 이상이어야 합니다.'
    if (f.releaseYear !== '' && (Number(f.releaseYear) < 1900 || Number(f.releaseYear) > 2999))
      e.releaseYear = '출시연도는 1900 ~ 2999 사이여야 합니다.'
    return e
  },
})

/**
 * 단종 전환 안내.
 *
 * 막지 않는다 — 단종은 정상적인 업무다. 다만 SKU 와 재고가 그대로 남아
 * 소진될 때까지 팔린다는 것을 알려야 한다.
 */
const statusChangeNotice = computed(() => {
  if (mode.value !== 'edit' || form.value.status !== 'DISCONTINUED') return ''
  const original = rows.value.find((p) => p.productId === form.value.productId)
  if (!original || original.status === 'DISCONTINUED' || !original.skuCount) return ''
  return `SKU ${original.skuCount}개와 그 재고는 그대로 남아 소진될 때까지 판매됩니다. 완전히 내리려면 SKU 를 폐기해야 하며, 폐기는 재고 0 · 미처리 0 일 때만 가능합니다.`
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  if (row.skuCount) {
    return `이 제품의 SKU ${row.skuCount}개가 있어 삭제할 수 없습니다. SKU 를 먼저 삭제하세요. 더 이상 팔지 않는 제품이라면 상태를 '단종'으로 바꾸세요.`
  }
  return 'SKU 가 있으면 서버가 삭제를 거부합니다.'
})

const won = (v) => (v === null || v === undefined ? '-' : Number(v).toLocaleString())

const readDenyReason = computed(() => session.denyReason('MST_PRODUCT', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">제품 관리</h1>
        <p class="page-desc">
          고객이 보는 단위를 관리합니다. 실제로 창고에 쌓이고 팔리는 단위는 <strong>SKU</strong>이므로,
          제품을 등록한 뒤 색상 · 사이즈 SKU 를 만들어야 재고를 잡을 수 있습니다.
          제품은 소분류에만 등록할 수 있습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '제품 등록'"
          @click="openCreate()"
        >
          + 제품 등록
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
          placeholder="제품코드 / 제품명"
          @keyup.enter="search()"
        />
        <FormField
          v-model="filters.categoryId"
          label="분류"
          type="select"
          empty-option="전체"
          :options="catalog.categoryOptions"
          help="상위 분류를 고르면 하위까지 봅니다"
          @change="search()"
        />
        <FormField
          v-model="filters.brandId"
          label="브랜드"
          type="select"
          empty-option="전체"
          :options="catalog.brandOptions"
          @change="search()"
        />
        <FormField
          v-model="filters.status"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('PRODUCT_STATUS')"
          @change="search()"
        />
        <FormField
          v-model="filters.season"
          label="시즌"
          type="select"
          empty-option="전체"
          :options="codeOptions('SEASON')"
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
        row-key="productId"
        :page-size="0"
        :show-pager="false"
        :muted-when="(p) => p.useYn !== 'Y'"
        empty-text="조건에 맞는 제품이 없습니다."
      >
        <!-- 제품명을 누르면 상세가 열린다 (원산지 · 생산일자 · 출시연도) -->
        <template #cell-productName="{ row, value }">
          <button class="link-cell" @click="detail = row">{{ value }}</button>
        </template>

        <template #cell-categoryPath="{ value }">
          <span class="small">{{ value }}</span>
        </template>

        <template #cell-seasonLabel="{ value }">
          <span :class="{ dim: !value }">{{ value || '-' }}</span>
        </template>

        <template #cell-costAmount="{ value }">
          <span :class="{ dim: value === null }">{{ won(value) }}</span>
        </template>

        <template #cell-status="{ value }">
          <CodeBadge group="PRODUCT_STATUS" :code="value" />
        </template>

        <!-- SKU 가 없으면 재고를 잡을 수 없다. 눈에 띄게 둔다. -->
        <template #cell-skuCount="{ value }">
          <span :class="{ dim: !value }">{{ value || '없음' }}</span>
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

    <DetailDialog
      v-if="detail"
      title="제품 상세"
      :subtitle="detail.productName + ' · ' + detail.productId"
      :fields="detailFields"
      :can-edit="canUpdate"
      :edit-deny-reason="updateDenyReason"
      @edit="openEdit(detail); detail = null"
      @close="detail = null"
    >
      <template #status>
        <CodeBadge group="PRODUCT_STATUS" :code="detail.status" />
      </template>
      <template #useYn>
        <CodeBadge group="USE_YN" :code="detail.useYn" />
      </template>
    </DetailDialog>

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '제품 등록' : '제품 수정'"
      :subtitle="mode === 'edit' ? form.productId : '제품코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.productId"
          label="제품코드"
          required
          mono
          placeholder="PRD-24001"
          :disabled="mode === 'edit'"
          :error="errors.productId"
          help="영문 대문자·숫자·하이픈 3~30자"
        />
        <FormField
          v-model="form.productName"
          label="제품명"
          required
          placeholder="베이직 반팔 티셔츠"
          :error="errors.productName"
        />
        <FormField
          v-model="form.categoryId"
          label="제품분류"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.leafCategoryOptions"
          :error="errors.categoryId"
          help="소분류만 고를 수 있습니다."
        />
        <FormField
          v-model="form.brandId"
          label="브랜드"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.brandOptions"
          :error="errors.brandId"
        />
        <FormField
          v-model="form.status"
          label="제품상태"
          type="select"
          required
          :options="codeOptions('PRODUCT_STATUS')"
          :error="errors.status"
        />
        <FormField
          v-model="form.originCountry"
          label="생산지"
          type="select"
          empty-option="미지정"
          :options="codeOptions('COUNTRY')"
        />
        <FormField v-model="form.producedOn" label="생산일자" type="date" />
        <FormField
          v-model="form.costAmount"
          label="원가"
          type="number"
          placeholder="8500"
          :error="errors.costAmount"
          help="원 단위. 소수 두 자리까지 넣을 수 있습니다."
        />
        <FormField
          v-model="form.season"
          label="시즌"
          type="select"
          empty-option="미지정"
          :options="codeOptions('SEASON')"
          help="출시연도와 합쳐 26SS 처럼 표시됩니다."
        />
        <FormField
          v-model="form.releaseYear"
          label="출시연도"
          type="number"
          placeholder="2026"
          :error="errors.releaseYear"
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="statusChangeNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ statusChangeNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">분류 단계와 코드값은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="제품 삭제"
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
