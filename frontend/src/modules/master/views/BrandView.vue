<script setup>
/**
 * 브랜드 관리 (MST-PG-006).
 *
 * 요구사항 5장의 '식별코드' 가 브랜드코드다. 제품이 이 코드로 브랜드를
 * 부르므로 등록 후에는 바꿀 수 없다.
 *
 * 판정은 모두 서버가 한다. 여기서 막는 것은 왕복을 줄이기 위한 편의일 뿐이다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as brandApi from '@/api/brand.js'
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

const filters = reactive({ keyword: '', countryCode: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', countryCode: '', useYn: '' })
}

async function reload(force = true) {
  loadError.value = ''
  try {
    await catalog.loadBrands(force)
    if (catalog.denyReason.brands) loadError.value = catalog.denyReason.brands
  } catch (e) {
    loadError.value = e.message
  }
}

onMounted(() => reload(false))

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return catalog.brands
    .filter((b) => !filters.countryCode || b.countryCode === filters.countryCode)
    .filter((b) => !filters.useYn || b.useYn === filters.useYn)
    .filter(
      (b) =>
        !kw ||
        [b.brandId, b.brandName].some((v) => String(v ?? '').toLowerCase().includes(kw)),
    )
})

const columns = [
  { key: 'brandId', label: '브랜드코드', width: '110px', sortable: true, cls: 'code' },
  { key: 'brandName', label: '브랜드명', width: '200px', sortable: true },
  { key: 'countryCode', label: '국가', width: '110px', align: 'center', sortable: true },
  { key: 'productCount', label: '제품', width: '70px', align: 'right', sortable: true },
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
  perm: 'MST_BRAND',
  pk: 'brandId',
  label: '브랜드',
  nameOf: (b) => `${b.brandName}(${b.brandId})`,
  api: {
    create: (payload) => brandApi.create(payload),
    update: (brandId, payload) => brandApi.update(brandId, payload),
    remove: (brandId) => brandApi.remove(brandId, '브랜드 삭제'),
  },
  async afterChange({ action, key }) {
    await reload()
    if (action === 'create') {
      await nextTick()
      table.value?.goToKey(key)
    }
  },
  blank: () => ({
    brandId: '',
    brandName: '',
    countryCode: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    brandId: row.brandId,
    brandName: row.brandName,
    countryCode: row.countryCode ?? '',
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    ...f,
    countryCode: f.countryCode || null,
    sortOrder: Number(f.sortOrder) || 0,
  }),
  validate(f, ctx) {
    const e = {}
    if (!f.brandId?.trim()) e.brandId = '브랜드코드는 필수입니다.'
    else if (!/^[A-Z0-9]{2,20}$/.test(f.brandId))
      e.brandId = '영문 대문자와 숫자 2~20자. 예) JK'
    else if (ctx.mode === 'create' && catalog.brands.some((b) => b.brandId === f.brandId))
      e.brandId = '이미 사용 중인 브랜드코드입니다.'
    if (!f.brandName?.trim()) e.brandName = '브랜드명은 필수입니다.'
    else if (
      catalog.brands.some((b) => b.brandName === f.brandName.trim() && b.brandId !== f.brandId)
    )
      e.brandName = '이미 사용 중인 브랜드명입니다.'
    return e
  },
})

/** 삭제 확인창에 왜 막힐 수 있는지 미리 보여준다 */
const deleteDetail = computed(() => {
  const row = askDelete.value
  if (!row) return ''
  if (row.productCount) {
    return `이 브랜드의 제품 ${row.productCount}개가 있어 삭제할 수 없습니다. 제품을 먼저 정리하세요. 더 이상 취급하지 않는 브랜드라면 사용여부를 '미사용'으로 바꾸세요.`
  }
  return '이 브랜드의 제품이 있으면 서버가 삭제를 거부합니다.'
})

const readDenyReason = computed(() => session.denyReason('MST_BRAND', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">브랜드 관리</h1>
        <p class="page-desc">
          제품이 속한 브랜드를 관리합니다. 브랜드코드는 제품이 브랜드를 부르는 이름이라
          등록 후에는 바꿀 수 없습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '브랜드 등록'"
          @click="openCreate()"
        >
          + 브랜드 등록
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
          placeholder="브랜드코드 / 브랜드명"
        />
        <FormField
          v-model="filters.countryCode"
          label="국가"
          type="select"
          empty-option="전체"
          :options="codeOptions('COUNTRY')"
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
        row-key="brandId"
        :page-size="10"
        :muted-when="(b) => b.useYn !== 'Y'"
        empty-text="조건에 맞는 브랜드가 없습니다."
      >
        <template #cell-countryCode="{ value }">
          <CodeBadge v-if="value" group="COUNTRY" :code="value" />
          <span v-else class="dim">-</span>
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
      :title="mode === 'create' ? '브랜드 등록' : '브랜드 수정'"
      :subtitle="mode === 'edit' ? form.brandId : '브랜드코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.brandId"
          label="브랜드코드"
          required
          mono
          placeholder="JK"
          :disabled="mode === 'edit'"
          :error="errors.brandId"
          help="요구사항의 '식별코드'입니다. 영문 대문자·숫자 2~20자"
        />
        <FormField
          v-model="form.brandName"
          label="브랜드명"
          required
          placeholder="잭"
          :error="errors.brandName"
        />
        <FormField
          v-model="form.countryCode"
          label="국가"
          type="select"
          empty-option="미지정"
          :options="codeOptions('COUNTRY')"
          help="비워 둘 수 있습니다."
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <template #footer>
        <span class="left small dim">중복과 참조 무결성은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="브랜드 삭제"
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
