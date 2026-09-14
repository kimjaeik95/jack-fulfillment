<script setup>
/**
 * 채널 SKU 매핑 (MST-PG-011).
 *
 * 플랫폼의 외부 상품코드를 내부 SKU 로 잇는다. 주문이 들어오면 이 표를 보고
 * 무엇을 집어야 하는지 정한다. 여기가 비어 있으면 주문을 받고도 무엇을
 * 보내야 할지 알 수 없다.
 *
 * 두 규칙이 핵심이다 (MST-008).
 *   1 SKU ↔ N 외부코드       같은 SKU 를 한 채널에 단품·2매묶음으로 따로 올릴 수 있다
 *   동일 채널 내 외부코드 유일  주문이 그 코드로 SKU 를 찾으므로 둘이면 정할 수 없다
 *
 * 탭이 둘이다. '매핑 목록' 은 이미 이은 것, '미매핑 SKU' 는 아직 잇지 않은
 * 것(MST-009)이다. 후자가 실무에서 더 급하다 — 판매를 시작하기 전에 비어
 * 있는 것을 찾아야 하기 때문이다.
 *
 * SKU 수 × 채널 수만큼 늘어나므로 서버 페이징을 쓴다.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as channelSkuApi from '@/api/channelSku.js'
import { useCatalogStore } from '@/stores/catalog.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const route = useRoute()
const catalog = useCatalogStore()
const session = useSessionStore()

/** 'mapped' = 매핑 목록, 'unmapped' = 미매핑 SKU */
const tab = ref('mapped')

const loadError = ref('')
const loading = ref(false)
const size = channelSkuApi.PAGE_SIZE

/* ── 매핑 목록 ──────────────────────────────────────────────── */

const rows = ref([])
const total = ref(0)
const page = ref(1)

const filters = reactive({
  keyword: '',
  // 판매채널 화면에서 '매핑' 을 눌러 넘어오면 그 채널이 걸린 채로 열린다
  channelId: route.query.channelId ?? '',
  productId: '',
  mappingStatus: '',
})

function resetFilters() {
  Object.assign(filters, { keyword: '', channelId: '', productId: '', mappingStatus: '' })
}

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await channelSkuApi.list({ ...filters, page: page.value, size })
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

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

/* ── 미매핑 SKU (MST-009) ───────────────────────────────────── */

const unmappedRows = ref([])
const unmappedTotal = ref(0)
const unmappedPage = ref(1)
const unmappedFilters = reactive({ channelId: '', keyword: '' })

/**
 * 채널을 고르지 않으면 조회하지 않는다.
 *
 * '어느 채널에 매핑이 없는가' 가 질문이므로 채널 없이는 뜻이 없다.
 * 전 채널을 한 번에 보여 주면 SKU 하나가 채널 수만큼 반복돼 읽기 어렵다.
 */
async function fetchUnmapped() {
  if (!unmappedFilters.channelId) {
    unmappedRows.value = []
    unmappedTotal.value = 0
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const data = await channelSkuApi.unmapped({
      ...unmappedFilters,
      page: unmappedPage.value,
      size,
    })
    unmappedRows.value = data.rows
    unmappedTotal.value = data.total
  } catch (e) {
    unmappedRows.value = []
    unmappedTotal.value = 0
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

async function searchUnmapped() {
  unmappedPage.value = 1
  await fetchUnmapped()
}

const unmappedTotalPages = computed(() => Math.max(1, Math.ceil(unmappedTotal.value / size)))

async function goUnmappedPage(n) {
  if (n < 1 || n > unmappedTotalPages.value || n === unmappedPage.value) return
  unmappedPage.value = n
  await fetchUnmapped()
}

/**
 * 라우트의 channelId 를 따라간다.
 *
 * 해시 라우터는 쿼리만 바뀌면 컴포넌트를 다시 만들지 않는다. 판매채널
 * 화면에서 '매핑' 으로 넘어와 채널이 걸린 뒤, 사이드바로 이 화면을 다시
 * 열면 걸어 둔 채널이 그대로 남아 전체를 보는 줄 알고 일부만 보게 된다.
 */
watch(
  () => route.query.channelId,
  async (next) => {
    const value = next ?? ''
    if (value === filters.channelId) return
    filters.channelId = value
    await search()
  },
)

/** 탭을 바꿀 때 보고 있던 채널을 그대로 가져간다 */
watch(tab, async (next) => {
  loadError.value = ''
  if (next === 'unmapped') {
    if (!unmappedFilters.channelId) unmappedFilters.channelId = filters.channelId
    await searchUnmapped()
  } else {
    await fetchPage()
  }
})

onMounted(async () => {
  await catalog.loadChannels(false)
  await catalog.loadProducts(false)
  await fetchPage()
})

/* ── 표 ─────────────────────────────────────────────────────── */

const columns = [
  { key: 'channelName', label: '채널', width: '130px', sortable: true },
  { key: 'extCodeLabel', label: '플랫폼 상품/옵션코드', width: '190px', cls: 'code' },
  { key: 'extProductName', label: '플랫폼 상품명', width: '200px' },
  { key: 'skuId', label: 'SKU', width: '175px', sortable: true, cls: 'code' },
  { key: 'productName', label: '제품', width: '160px', sortable: true },
  { key: 'mappingStatus', label: '매핑상태', width: '96px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const unmappedColumns = [
  { key: 'skuId', label: 'SKU', width: '180px', sortable: true, cls: 'code' },
  { key: 'productName', label: '제품', width: '200px', sortable: true },
  { key: 'colorCode', label: '색상', width: '84px', align: 'center' },
  { key: 'sizeCode', label: '사이즈', width: '76px', align: 'center' },
  { key: 'status', label: 'SKU 상태', width: '96px', align: 'center' },
  { key: '_act', label: '', width: '100px', align: 'right' },
]

/* ── 등록 · 수정 · 삭제 ─────────────────────────────────────── */

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_CHANNEL_SKU',
  pk: 'mappingSeq',
  label: '매핑',
  nameOf: (m) => `${m.channelName} · ${m.extCodeLabel} → ${m.skuId}`,
  api: {
    create: (payload) => channelSkuApi.create(payload),
    update: (mappingSeq, payload) => channelSkuApi.update(mappingSeq, payload),
    remove: (mappingSeq) => channelSkuApi.remove(mappingSeq, '매핑 삭제'),
  },
  // 서버 페이징이라 등록한 행이 다른 페이지에 있을 수 있다. 외부코드로
  // 찾아갈 수 있게 검색어에 넣어 준다.
  async afterChange({ action, result }) {
    if (action === 'create' && result?.mapping) {
      tab.value = 'mapped'
      filters.keyword = result.mapping.extProductCode
      await search()
    } else {
      await fetchPage()
    }
    // 채널 목록의 매핑 수 · 미완료 수가 낡는다
    catalog.invalidate('channels')
    // 미매핑 목록도 방금 이은 SKU 만큼 줄었다
    if (unmappedFilters.channelId) await fetchUnmapped()
  },
  blank: () => ({
    channelId: filters.channelId || unmappedFilters.channelId || '',
    skuId: '',
    extProductCode: '',
    extOptionCode: '',
    extProductName: '',
    mappingStatus: 'MAPPED',
    useYn: 'Y',
  }),
  toForm: (row) => ({
    mappingSeq: row.mappingSeq,
    channelId: row.channelId,
    skuId: row.skuId,
    extProductCode: row.extProductCode,
    extOptionCode: row.extOptionCode ?? '',
    extProductName: row.extProductName ?? '',
    mappingStatus: row.mappingStatus,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({
    channelId: f.channelId,
    skuId: f.skuId,
    extProductCode: f.extProductCode,
    extOptionCode: f.extOptionCode || null,
    extProductName: f.extProductName || null,
    mappingStatus: f.mappingStatus,
    useYn: f.useYn,
  }),
  validate(f) {
    const e = {}
    if (!f.channelId) e.channelId = '채널을 선택하세요.'
    if (!f.skuId?.trim()) e.skuId = 'SKU 코드를 입력하세요.'
    if (!f.extProductCode?.trim()) e.extProductCode = '플랫폼 상품코드는 필수입니다.'
    else if (f.extProductCode.length > 100) e.extProductCode = '100자 이하로 입력하세요.'
    if (f.extOptionCode && f.extOptionCode.length > 100)
      e.extOptionCode = '100자 이하로 입력하세요.'
    if (!f.mappingStatus) e.mappingStatus = '매핑상태를 선택하세요.'
    return e
  },
})

/**
 * 수정 중에는 채널을 바꿀 수 없다.
 *
 * 외부코드 체계가 채널마다 달라서, 채널만 바꾸면 그 코드가 새 채널에서
 * 무엇을 가리키는지 알 수 없다. 서버도 같은 이유로 거부한다.
 */
const channelLocked = computed(() => mode.value === 'edit')

/** 미매핑 목록에서 바로 등록으로 넘어간다 */
function mapThis(sku) {
  openCreate({ channelId: unmappedFilters.channelId, skuId: sku.skuId })
}

/** 저장 전에, 매핑을 걸어도 주문이 처리되지 않는 경우를 알린다 */
const pendingNotice = computed(() => {
  if (form.value.mappingStatus === 'MAPPED') return ''
  return '매핑완료가 아니면 이 코드로 들어온 주문을 SKU 로 연결하지 못합니다. 확인이 끝나면 매핑완료로 바꾸세요.'
})

const selectedChannelStopped = computed(() => {
  const c = catalog.channels.find((x) => x.channelId === form.value.channelId)
  return c && c.useYn !== 'Y' ? c.channelName : ''
})

const readDenyReason = computed(() => session.denyReason('MST_CHANNEL_SKU', 'R'))

/** 목록에 중지된 채널의 매핑이 섞여 있는지 */
const stoppedInList = computed(
  () => new Set(rows.value.filter((r) => r.channelUseYn !== 'Y').map((r) => r.channelName)),
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">채널 SKU 매핑</h1>
        <p class="page-desc">
          플랫폼의 외부 상품코드를 내부 SKU 로 잇습니다.
          <strong>주문이 들어오면 이 표를 보고 무엇을 집을지 정합니다.</strong>
          같은 SKU 를 한 채널에 단품·묶음으로 여러 번 올릴 수 있지만,
          한 채널 안에서 같은 외부코드는 하나만 둘 수 있습니다 — 둘이면 어느 SKU 인지 정할 수 없습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '매핑 등록'"
          @click="openCreate()"
        >
          + 매핑 등록
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

    <div class="tabs">
      <button :class="['tab', { on: tab === 'mapped' }]" @click="tab = 'mapped'">
        매핑 목록
      </button>
      <button :class="['tab', { on: tab === 'unmapped' }]" @click="tab = 'unmapped'">
        미매핑 SKU
      </button>
      <span class="small dim tab-note">
        미매핑 SKU 는 고른 채널에 매핑이 하나도 없는 SKU 입니다. 판매를 시작하기 전에 비워 두면
        주문이 들어온 뒤에야 빠진 것을 알게 됩니다.
      </span>
    </div>

    <!-- ── 매핑 목록 ─────────────────────────────────────────── -->
    <div v-show="tab === 'mapped'" class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="외부코드 / 플랫폼 상품명 / SKU / 제품명"
          @keyup.enter="search()"
        />
        <FormField
          v-model="filters.channelId"
          label="채널"
          type="select"
          empty-option="전체"
          :options="catalog.channelOptions"
          @change="search()"
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
          v-model="filters.mappingStatus"
          label="매핑상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('MAPPING_STATUS')"
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

      <!-- 매핑은 살아 있어도 채널이 중지면 주문이 자동 처리되지 않는다 (MST-007) -->
      <div v-if="stoppedInList.size" class="alert alert-warn m-2">
        <span class="alert-icon">⚠</span>
        <span>
          중지된 채널({{ [...stoppedInList].join(', ') }})의 매핑이 목록에 있습니다.
          매핑은 살아 있지만 그 채널의 신규 주문은 자동으로 처리되지 않습니다.
        </span>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="mappingSeq"
        :page-size="0"
        :show-pager="false"
        :muted-when="(m) => m.useYn !== 'Y' || m.channelUseYn !== 'Y'"
        empty-text="조건에 맞는 매핑이 없습니다."
      >
        <template #cell-channelName="{ row, value }">
          {{ value }}
          <span v-if="row.channelUseYn !== 'Y'" class="small warn"> (중지)</span>
        </template>

        <template #cell-extProductName="{ value }">
          <span :class="{ dim: !value }">{{ value || '미입력' }}</span>
        </template>

        <template #cell-productName="{ row, value }">
          {{ value }}
          <span class="small dim">{{ row.colorCode }}/{{ row.sizeCode }}</span>
        </template>

        <template #cell-mappingStatus="{ value }">
          <CodeBadge group="MAPPING_STATUS" :code="value" />
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

    <!-- ── 미매핑 SKU (MST-009) ──────────────────────────────── -->
    <div v-show="tab === 'unmapped'" class="card">
      <div class="toolbar">
        <FormField
          v-model="unmappedFilters.channelId"
          label="채널"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.channelOptions"
          help="어느 채널에 빠진 SKU 를 볼지 고릅니다"
          @change="searchUnmapped()"
        />
        <FormField
          v-model="unmappedFilters.keyword"
          class="grow"
          label="검색어"
          placeholder="SKU 코드 / 제품명"
          @keyup.enter="searchUnmapped()"
        />
        <div class="toolbar-actions">
          <button
            class="btn btn-primary"
            :disabled="loading || !unmappedFilters.channelId"
            @click="searchUnmapped()"
          >
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <div v-if="!unmappedFilters.channelId" class="empty-note">
        채널을 먼저 고르세요. 어느 채널에 매핑이 빠졌는지를 보는 화면입니다.
      </div>

      <template v-else>
        <div v-if="unmappedTotal" class="alert alert-warn m-2">
          <span class="alert-icon">⚠</span>
          <span>
            이 채널에 매핑이 없는 SKU 가 <strong>{{ unmappedTotal }}건</strong> 있습니다.
            이대로 판매가 시작되면 주문이 들어와도 어느 SKU 인지 찾지 못합니다.
            폐기한 SKU 는 목록에서 제외했습니다.
          </span>
        </div>
        <div v-else class="alert alert-info m-2">
          <span class="alert-icon">✓</span>
          <span>이 채널은 판매 중인 SKU 가 모두 매핑되어 있습니다.</span>
        </div>

        <DataTable
          :columns="unmappedColumns"
          :rows="unmappedRows"
          row-key="skuId"
          :page-size="0"
          :show-pager="false"
          empty-text="매핑이 빠진 SKU 가 없습니다."
        >
          <template #cell-colorCode="{ value }">
            <CodeBadge group="COLOR" :code="value" />
          </template>

          <template #cell-sizeCode="{ value }">
            <span class="badge">{{ value }}</span>
          </template>

          <template #cell-status="{ value }">
            <CodeBadge group="SKU_STATUS" :code="value" />
          </template>

          <template #cell-_act="{ row }">
            <div class="btn-row" style="justify-content: flex-end">
              <button
                class="btn btn-sm btn-primary"
                :disabled="!canCreate"
                :title="createDenyReason ?? '이 SKU 를 매핑합니다'"
                @click="mapThis(row)"
              >
                매핑
              </button>
            </div>
          </template>
        </DataTable>

        <div class="pager">
          <span class="small dim">
            총 {{ unmappedTotal }}건 · {{ unmappedPage }} / {{ unmappedTotalPages }} 페이지
          </span>
          <div class="btn-row">
            <button class="btn btn-sm" :disabled="unmappedPage <= 1 || loading" @click="goUnmappedPage(1)">« 처음</button>
            <button class="btn btn-sm" :disabled="unmappedPage <= 1 || loading" @click="goUnmappedPage(unmappedPage - 1)">‹ 이전</button>
            <button class="btn btn-sm" :disabled="unmappedPage >= unmappedTotalPages || loading" @click="goUnmappedPage(unmappedPage + 1)">다음 ›</button>
            <button class="btn btn-sm" :disabled="unmappedPage >= unmappedTotalPages || loading" @click="goUnmappedPage(unmappedTotalPages)">마지막 »</button>
          </div>
        </div>
      </template>
    </div>

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '매핑 등록' : '매핑 수정'"
      :subtitle="mode === 'edit' ? form.extProductCode : '한 채널 안에서 같은 외부코드는 하나만 둘 수 있습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.channelId"
          label="채널"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.channelOptions"
          :disabled="channelLocked"
          :error="errors.channelId"
          :help="channelLocked ? '채널은 바꿀 수 없습니다. 외부코드 체계가 채널마다 다릅니다.' : null"
        />
        <FormField
          v-model="form.skuId"
          label="SKU 코드"
          required
          mono
          placeholder="PRD-24001-BK-M"
          :error="errors.skuId"
          help="내부 SKU 코드를 그대로 입력합니다. 없는 코드면 저장 시 서버가 거부합니다."
        />
        <FormField
          v-model="form.extProductCode"
          label="플랫폼 상품코드"
          required
          mono
          placeholder="쿠팡 7788001"
          :error="errors.extProductCode"
          help="플랫폼이 주는 값이라 형식을 강제하지 않습니다."
        />
        <FormField
          v-model="form.extOptionCode"
          label="플랫폼 옵션코드"
          mono
          placeholder="옵션 개념이 없으면 비워 둡니다"
          :error="errors.extOptionCode"
        />
        <FormField
          v-model="form.extProductName"
          label="플랫폼 상품명"
          class="span-2"
          placeholder="플랫폼에 등록된 이름 (확인용)"
          help="플랫폼 화면에 보이는 이름입니다. 매핑이 맞는지 눈으로 확인할 때 씁니다."
        />
        <FormField
          v-model="form.mappingStatus"
          label="매핑상태"
          type="select"
          required
          :options="codeOptions('MAPPING_STATUS')"
          :error="errors.mappingStatus"
        />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="pendingNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ pendingNotice }}</span>
      </div>
      <div v-if="selectedChannelStopped" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span>
        <span>
          {{ selectedChannelStopped }} 은(는) 중지된 채널입니다. 매핑은 저장되지만 그 채널의 신규
          주문은 자동으로 처리되지 않습니다.
        </span>
      </div>

      <template #footer>
        <span class="left small dim">
          같은 채널 · 같은 외부코드 중복은 저장 시 서버가 다시 검증합니다.
        </span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="매핑 삭제"
      :message="deleteMessage"
      detail="삭제하면 이 외부코드로 들어오는 주문을 SKU 로 연결하지 못합니다. 판매만 잠시 멈추려면 매핑상태를 '매핑대기'로 두거나 사용여부를 끄세요."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.tab {
  padding: 7px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
}
.tab.on {
  background: var(--c-blue, #2563eb);
  border-color: var(--c-blue, #2563eb);
  color: #fff;
}
.tab-note {
  margin-left: 6px;
  flex: 1 1 320px;
}
.warn {
  color: var(--c-amber);
}
.empty-note {
  padding: 32px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.m-2 {
  margin: 10px 14px;
}
</style>
