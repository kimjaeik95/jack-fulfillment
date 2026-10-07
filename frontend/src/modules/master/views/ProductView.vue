<script setup>
/**
 * 스타일 관리 (MST-PG-007).
 *
 * 스타일은 고객이 고르는 단위이고, 창고에 쌓이고 팔리는 단위는 SKU 다.
 * 스타일만 등록하면 재고를 잡을 수 없어서 저장 후 그 안내가 뜬다.
 *
 * 건수가 많아 **서버 페이징**을 쓴다. 검색 조건이 바뀌면 서버에 다시
 * 물어본다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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
import ProductOptionsDialog from '../components/ProductOptionsDialog.vue'

const catalog = useCatalogStore()
const session = useSessionStore()
const optionProduct = ref(null)
const router = useRouter()
const skuReadDenyReason = computed(() => session.denyReason('MST_SKU', 'R'))

function openSkus(product) {
  router.push({ name: 'skus', query: { productId: product.productId } })
}

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

/** 방금 만든 스타일. 목록에서 짚어 주기만 하고 다른 뜻은 없다. */
const justMade = ref(null)

/**
 * 정렬.
 *
 * 평소에는 코드순이다. 등록 직후에만 '최근 등록순' 으로 바꿔 방금 만든
 * 것을 맨 위로 올린다 — 서버 페이징이라 코드순으로 두면 새 줄이 몇 번째
 * 페이지에 떨어질지 알 수가 없다.
 */
const sortBy = ref('')

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await productApi.list({
      ...filters,
      sortBy: sortBy.value || undefined,
      sortDir: sortBy.value ? 'desc' : undefined,
      page: page.value,
      size,
    })
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
  // 사람이 조건을 바꾸면 짚어 둔 줄과 임시 정렬은 뜻을 잃는다
  justMade.value = null
  sortBy.value = ''
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
    { label: '스타일코드', value: d.productId, mono: true },
    { label: '스타일명', value: d.productName },
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
  { key: 'productId', label: '스타일코드', width: '120px', sortable: true, cls: 'code' },
  { key: 'productName', label: '스타일명', width: '200px', sortable: true },
  { key: 'categoryPath', label: '분류', width: '190px' },
  { key: 'brandName', label: '브랜드', width: '110px', sortable: true },
  { key: 'seasonLabel', label: '시즌', width: '72px', align: 'center' },
  { key: 'costAmount', label: '원가', width: '100px', align: 'right', sortable: true },
  { key: 'status', label: '상태', width: '86px', align: 'center', sortable: true },
  { key: 'skuCount', label: 'SKU', width: '62px', align: 'right' },
  { key: '_options', label: '색상·사이즈', width: '100px', align: 'center' },
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
  label: '스타일',
  nameOf: (p) => `${p.productName}(${p.productId})`,
  api: {
    create: (payload) => productApi.create(payload),
    update: (productId, payload) => productApi.update(productId, payload),
    remove: (productId) => productApi.remove(productId, '스타일 삭제'),
  },
  /**
   * 등록하면 <b>목록을 그대로 두고</b> 방금 만든 것을 맨 위로 올린다.
   *
   * 전에는 검색어에 코드를 넣어 한 줄만 남겼다. 등록된 것을 확인하기에는
   * 확실하지만, 그 한 줄 말고는 아무것도 안 보여서 <b>비슷한 스타일이 이미
   * 있는지</b>를 볼 수가 없었다 — 같은 옷이 코드 두 개로 생기는 것이
   * 기준정보에서 가장 고치기 어려운 사고다.
   *
   * 서버 페이징이라 코드순으로 두면 새 줄이 몇 페이지에 떨어질지 모른다.
   * 그래서 이때만 '최근 등록순' 으로 바꾼다 — 맨 위에 있으니 찾을 필요가
   * 없다. 다음에 조회를 누르면 원래 정렬로 돌아간다.
   */
  async afterChange({ action, result }) {
    if (action === 'create' && result?.product) {
      filters.keyword = ''
      sortBy.value = 'createdAt'
      page.value = 1
      await fetchPage()
      justMade.value = result.product.productId
    } else {
      await fetchPage()
    }
    // 스타일 목록이 바뀌면 SKU 화면의 스타일 드롭다운도 낡는다
    await catalog.loadProducts(true)
    // 분류 · 브랜드 목록의 스타일 수가 낡는다
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
    if (!f.productId?.trim()) e.productId = '스타일코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9-]{2,29}$/.test(f.productId))
      e.productId = '영문 대문자·숫자·하이픈 3~30자. 예) PRD-24001'
    if (!f.productName?.trim()) e.productName = '스타일명은 필수입니다.'
    if (!f.categoryId) e.categoryId = '스타일분류를 선택하세요.'
    if (!f.brandId) e.brandId = '브랜드를 선택하세요.'
    if (!f.status) e.status = '스타일상태를 선택하세요.'
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
    return `이 스타일의 SKU ${row.skuCount}개가 있어 삭제할 수 없습니다. SKU 를 먼저 삭제하세요. 더 이상 팔지 않는 스타일이라면 상태를 '단종'으로 바꾸세요.`
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
        <h1 class="page-title">스타일 관리</h1>
        <p class="page-desc">
          고객이 보는 단위를 관리합니다. 실제로 창고에 쌓이고 팔리는 단위는 <strong>SKU</strong>이므로,
          스타일 등록 후 옵션 관리에서 색상·사이즈를 지정하고 SKU를 생성하세요.
          스타일은 소분류에만 등록할 수 있습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '스타일 등록'"
          @click="openCreate()"
        >
          + 스타일 등록
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
          placeholder="스타일코드 / 스타일명"
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
        :selected-key="justMade"
        :page-size="0"
        :show-pager="false"
        :muted-when="(p) => p.useYn !== 'Y'"
        empty-text="조건에 맞는 스타일이 없습니다."
      >
        <!-- 스타일명을 누르면 상세가 열린다 (원산지 · 생산일자 · 출시연도) -->
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
        <template #cell-skuCount="{ row, value }">
          <button
            v-if="value"
            class="btn btn-ghost btn-sm"
            :disabled="!!skuReadDenyReason"
            :title="skuReadDenyReason || `${row.productName}의 옵션 SKU 보기`"
            @click.stop="openSkus(row)"
          >{{ value }}</button>
          <span v-else class="dim">없음</span>
        </template>

        <template #cell-_options="{ row }">
          <button class="btn btn-sm" @click.stop="optionProduct = row">옵션 관리</button>
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
      title="스타일 상세"
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
      :title="mode === 'create' ? '스타일 등록' : '스타일 수정'"
      :subtitle="mode === 'edit' ? form.productId : '스타일코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.productId"
          label="스타일코드"
          required
          mono
          placeholder="PRD-24001"
          :disabled="mode === 'edit'"
          :error="errors.productId"
          help="영문 대문자·숫자·하이픈 3~30자"
        />
        <FormField
          v-model="form.productName"
          label="스타일명"
          required
          placeholder="베이직 반팔 티셔츠"
          :error="errors.productName"
        />
        <FormField
          v-model="form.categoryId"
          label="스타일분류"
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
          label="스타일상태"
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
      title="스타일 삭제"
      :message="deleteMessage"
      :detail="deleteDetail"
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
    <ProductOptionsDialog v-if="optionProduct" :product="optionProduct"
      :readonly="!session.can('MST_PRODUCT', 'U')" @close="optionProduct = null" />
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
