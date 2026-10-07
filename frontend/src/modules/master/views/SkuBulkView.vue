<script setup>
/**
 * SKU 일괄생성 (MST-PG-009).
 *
 * 패션 의류는 스타일 하나에 SKU 가 20~40건씩 붙는다. 색상 × 사이즈 조합이기
 * 때문이다. 한 건씩 등록하면 같은 일을 마흔 번 한다.
 *
 * 흐름은 장바구니와 같다. 스타일 하나를 고르고 색상 · 사이즈를 체크해 '추가' 로
 * 담고, 다음 스타일을 담고, 마지막에 한 번 만든다. 신상품 입고는 보통 여러
 * 스타일이 한꺼번에 오므로 스타일마다 화면을 다시 여는 것은 같은 일을 반복하는
 * 것이다.
 *
 * 만들기는 한 번의 요청이다. 다섯 스타일을 다섯 번 보내면 세 번째에서 실패했을
 * 때 앞의 둘은 이미 만들어져 있고, 사용자는 무엇이 만들어졌는지 모르는 채로
 * 다시 시도하게 된다.
 *
 * 스타일에 등록된 옵션 안에서 조합을 선택하고 서버에서 다시 검증한다.
 *
 * 담은 것이 바뀔 때마다 서버에 미리보기를 물어 본다. 화면이 혼자 계산하면
 * "7건이 생깁니다" 를 보고 눌렀는데 5건이 생기는 일이 벌어진다. 판정은 한
 * 곳에서만 한다.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as skuApi from '@/api/sku.js'
import * as optionApi from '@/api/productOptions.js'
import { useCatalogStore } from '@/stores/catalog.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()
const session = useSessionStore()
const toast = useToastStore()

/* ── 지금 고르는 중인 것 ────────────────────────────────────── */

// SKU 관리 화면에서 '일괄생성' 으로 넘어오면 그 스타일이 골라진 채로 열린다
const draft = ref({
  productId: route.query.productId ?? '',
  colorCodes: [],
  sizeCodes: [],
  status: 'ACTIVE',
})

const optionsByProduct = ref({})
const optionError = ref('')
const optionLoading = ref(false)
const colorOptions = computed(() => optionApi.choices(optionsByProduct.value[draft.value.productId] || [], 'COLOR'))
const sizeOptions = computed(() => optionApi.choices(optionsByProduct.value[draft.value.productId] || [], 'SIZE'))
let optionRequest = 0
watch(() => draft.value.productId, async (id) => {
  const request = ++optionRequest
  optionError.value = ''; optionLoading.value = false
  if (!id) return
  optionLoading.value = true
  try {
    const options = await optionApi.list(id)
    optionsByProduct.value[id] = options
    if (request === optionRequest) {
      draft.value.colorCodes = draft.value.colorCodes.filter(c => colorOptions.value.some(o => o.value === c))
      draft.value.sizeCodes = draft.value.sizeCodes.filter(c => sizeOptions.value.some(o => o.value === c))
    }
  } catch (e) { if (request === optionRequest) optionError.value = e.message }
  finally { if (request === optionRequest) optionLoading.value = false }
}, { immediate: true })
const statusOptions = computed(() => codeOptions('SKU_STATUS'))

const labelOf = (options, code) => options.find((o) => o.value === code)?.label ?? code
const productNameOf = (id) =>
  catalog.products.find((p) => p.productId === id)?.productName ?? id

const draftReady = computed(
  () =>
    !!draft.value.productId &&
    !optionLoading.value && !optionError.value &&
    draft.value.colorCodes.length > 0 &&
    draft.value.sizeCodes.length > 0,
)

const draftCount = computed(
  () => draft.value.colorCodes.length * draft.value.sizeCodes.length,
)

/* ── 담은 목록 ──────────────────────────────────────────────── */

const items = ref([])
const reason = ref('')

/**
 * 직전에 담은 색상 · 사이즈.
 *
 * 스타일을 바꾸면 체크를 비우지만(아래 onProductChange 참고), 같은 성격의
 * 스타일을 연달아 담는 일이 잦아 되살릴 수단이 필요하다.
 */
const lastUsed = ref(null)

/**
 * 담는다.
 *
 * 같은 스타일을 또 담는 것을 막지 않는다 — 색상을 나중에 떠올려 한 줄 더
 * 담는 것은 자연스럽다. 겹치는 조합은 서버가 '앞선 항목이 만듭니다' 로
 * 건너뛴다.
 */
function addDraft() {
  items.value = [
    ...items.value,
    {
      productId: draft.value.productId,
      colorCodes: [...draft.value.colorCodes],
      sizeCodes: [...draft.value.sizeCodes],
      status: draft.value.status,
    },
  ]
  lastUsed.value = {
    colorCodes: [...draft.value.colorCodes],
    sizeCodes: [...draft.value.sizeCodes],
  }
  /*
   * 스타일은 남긴다.
   *
   * 한 스타일에 색상을 나눠 담는 일이 잦다 — 기본 색을 먼저 담고 시즌 색을
   * 나중에 떠올리는 식이다. 담을 때마다 스타일을 비우면 같은 것을 드롭다운에서
   * 다시 찾아야 하고, '이전과 동일' 도 색상·사이즈만 되살리는 것이라 두 번
   * 일하게 된다.
   *
   * 다른 스타일으로 넘어갈 때는 드롭다운을 바꾸면 되고, 그때 onProductChange
   * 가 색상·사이즈를 비운다 — 상의 사이즈가 하의에 남는 것을 막는 장치는
   * 그대로 돈다.
   */
  draft.value = { ...draft.value, colorCodes: [], sizeCodes: [] }
}

/**
 * 스타일을 바꾸면 색상 · 사이즈를 비운다.
 *
 * 사이즈 목록에 상의(S·M·L)와 하의(28·30·32)가 함께 있다. 티셔츠를 담고
 * 팬츠를 골랐을 때 S·M·L 이 남아 있으면 PANTS-BK-M 같은 SKU 가 만들어지는데,
 * 이것은 아무것도 잡아내지 못한다 — 코드 규칙에 맞고, 중복도 아니고,
 * 미리보기에도 '생성' 으로 보인다. 서버는 팬츠에 M 이 이상하다는 것을 알
 * 방법이 없다. 그래서 남기는 쪽의 대가가 클릭 몇 번이 아니라 잘못된
 * 데이터다.
 *
 * 같은 성격의 스타일을 연달아 담는 경우는 '이전과 동일' 로 한 번에 되살린다.
 *
 * watch 가 아니라 change 인 이유는 '고치기' 때문이다. 고치기는 스타일과
 * 색상·사이즈를 함께 되돌리는데, 스타일을 감시하면 그때도 방금 되돌린 체크를
 * 지워 버린다. change 는 사람이 드롭다운을 건드렸을 때만 돈다.
 */
function onProductChange() {
  draft.value = { ...draft.value, colorCodes: [], sizeCodes: [] }
}

/** 직전에 담은 색상 · 사이즈를 그대로 다시 고른다 */
function reuseLast() {
  if (!lastUsed.value) return
  draft.value = {
    ...draft.value,
    colorCodes: lastUsed.value.colorCodes.filter(c => colorOptions.value.some(o => o.value === c)),
    sizeCodes: lastUsed.value.sizeCodes.filter(c => sizeOptions.value.some(o => o.value === c)),
  }
}

/** '이전과 동일' 을 권할 때 — 되살릴 것이 있고 지금은 비어 있을 때 */
const canReuse = computed(
  () =>
    !!lastUsed.value &&
    draft.value.colorCodes.length === 0 &&
    draft.value.sizeCodes.length === 0,
)

const lastUsedLabel = computed(() => {
  if (!lastUsed.value) return ''
  const colors = lastUsed.value.colorCodes.map((c) => labelOf(colorOptions.value, c)).join(' · ')
  const sizes = lastUsed.value.sizeCodes.map((z) => labelOf(sizeOptions.value, z)).join(' · ')
  return `${colors} × ${sizes}`
})

function removeItem(i) {
  items.value = items.value.filter((_, n) => n !== i)
}

/** 담은 항목을 다시 고르는 중으로 되돌린다 */
function editItem(i) {
  draft.value = { ...items.value[i], colorCodes: [...items.value[i].colorCodes], sizeCodes: [...items.value[i].sizeCodes] }
  removeItem(i)
}

function clearAll() {
  items.value = []
  result.value = null
}

/* ── 미리보기 ───────────────────────────────────────────────── */

const preview = ref(null)
const previewing = ref(false)
const previewError = ref('')

async function runPreview() {
  if (!items.value.length) {
    preview.value = null
    previewError.value = ''
    return
  }
  previewing.value = true
  previewError.value = ''
  try {
    preview.value = await skuApi.previewBulk({ items: items.value, reason: reason.value || null })
  } catch (e) {
    preview.value = null
    previewError.value = e.message
  } finally {
    previewing.value = false
  }
}

/** 담은 것이 바뀌면 다시 물어 본다 */
watch(
  items,
  () => {
    // 결과를 띄워 둔 채로 목록을 바꾸면 그 결과는 더 이상 지금 화면의 것이 아니다
    result.value = null
    runPreview()
  },
  { deep: true },
)

onMounted(async () => {
  await catalog.loadProducts(false)
})

/** 항목별 미리보기 — 담은 순서와 같다 */
const previewAt = (i) => preview.value?.items?.[i] ?? null

const comboAt = (i, color, size) =>
  previewAt(i)?.combos.find((c) => c.colorCode === color && c.sizeCode === size) ?? null

/** 접었다 펴는 상태 — 담은 직후에는 펴 둔다 */
const opened = ref(new Set())
const isOpen = (i) => opened.value.has(i)
function toggle(i) {
  const next = new Set(opened.value)
  next.has(i) ? next.delete(i) : next.add(i)
  opened.value = next
}
watch(
  () => items.value.length,
  (n, before) => {
    if (n > (before ?? 0)) opened.value = new Set([...opened.value, n - 1])
  },
)

/* ── 생성 ───────────────────────────────────────────────────── */

const askCreate = ref(false)
const creating = ref(false)
const result = ref(null)

const canCreate = computed(() => session.can('MST_SKU', 'C'))
const createDenyReason = computed(() => session.denyReason('MST_SKU', 'C'))

async function doCreate() {
  creating.value = true
  try {
    const { result: res, warning } = await skuApi.createBulk({
      items: items.value,
      reason: reason.value || null,
    })
    result.value = res
    askCreate.value = false
    if (warning) toast.warn(warning)
    else toast.success(`SKU ${res.createdCount}건을 만들었습니다.`)
    // 방금 만든 것이 이미 있는 조합이 되었다. 표를 다시 그린다.
    await runPreview()
    // 스타일 목록의 SKU 수가 낡는다
    catalog.invalidate('products')
  } catch (e) {
    toast.error(e.message)
    askCreate.value = false
  } finally {
    creating.value = false
  }
}

/**
 * 만든 뒤 다음 묶음을 시작한다.
 *
 * 만들고 나면 담은 목록이 그대로 남아, 미리보기가 전부 '이미 있습니다' 로
 * 바뀐다 — 방금 만들었으니 당연하지만, 다음 것을 담으려면 그 목록을 먼저
 * 치워야 한다.
 *
 * <b>자동으로 비우지는 않는다.</b> 무엇이 만들어졌는지 표에서 확인하는 것이
 * 이 화면의 마지막 단계다. 치우는 시점은 사람이 정한다.
 *
 * 스타일은 남긴다. 같은 스타일에 이어서 담는 경우가 있고, 다른 스타일이면
 * 드롭다운을 바꾸면 된다.
 */
function startNext() {
  items.value = []
  result.value = null
  reason.value = ''
  draft.value = { ...draft.value, colorCodes: [], sizeCodes: [] }
}

/** 만든 SKU 를 확인하러 간다 */
function openSkus(productId) {
  router.push({ name: 'skus', query: productId ? { productId } : {} })
}

const readDenyReason = computed(() => session.denyReason('MST_SKU', 'R'))
</script>

<template>
  <div>
    <p v-if="optionError" class="alert alert-danger">{{ optionError }}</p>
    <p v-else-if="draft.productId && !optionLoading && (!colorOptions.length || !sizeOptions.length)" class="alert alert-warn">
      스타일 화면의 옵션 관리에서 색상과 사이즈를 먼저 등록하세요.
    </p>
    <div class="page-head">
      <div>
        <h1 class="page-title">SKU 일괄생성</h1>
        <p class="page-desc">
          스타일마다 <strong>색상 × 사이즈 조합만큼 SKU 를 만듭니다.</strong>
          스타일을 하나씩 담은 뒤 마지막에 한 번에 만듭니다 — 담은 것은 전부 만들어지거나
          전부 만들어지지 않습니다. 이미 있는 조합은 건너뛰고 사유를 알려 줍니다.
          바코드는 발급하지 않습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn" @click="openSkus()">SKU 관리</button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <!-- ── 1. 스타일 하나를 골라 담는다 ──────────────────────────── -->
    <div class="card p-3 mb-2">
      <div class="form-grid">
        <FormField
          v-model="draft.productId"
          label="스타일"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.productOptions"
          help="SKU 코드는 스타일코드-색상-사이즈 로 만들어집니다."
          @change="onProductChange()"
        />
        <FormField
          v-model="draft.status"
          label="SKU 상태"
          type="select"
          required
          :options="statusOptions"
          help="이 항목으로 만들어질 SKU 전부에 같은 상태가 들어갑니다."
        />
      </div>

      <FormField
        v-model="draft.colorCodes"
        label="색상"
        type="checks"
        span
        :options="colorOptions"
        help="선택한 스타일에 등록된 색상만 표시됩니다."
      />
      <FormField
        v-model="draft.sizeCodes"
        label="사이즈"
        type="checks"
        span
        :options="sizeOptions"
        help="상의(S·M·L)와 하의(28·30·32)가 한 목록에 있습니다. 이 스타일에 맞는 것만 고르세요."
      />

      <!--
        스타일을 바꾸면 체크가 비워진다. 같은 성격의 스타일을 연달아 담을 때를
        위해 직전 조합을 한 번에 되살릴 수 있게 둔다.
      -->
      <div v-if="canReuse" class="reuse">
        <button class="btn btn-sm" @click="reuseLast()">↺ 이전과 동일</button>
        <span class="small dim">{{ lastUsedLabel }}</span>
      </div>

      <div class="add-bar">
        <span class="small dim">
          <template v-if="draftReady">
            {{ productNameOf(draft.productId) }} · 색상 {{ draft.colorCodes.length }} × 사이즈
            {{ draft.sizeCodes.length }} = <strong>{{ draftCount }}개 조합</strong>
          </template>
          <template v-else>
            스타일 · 색상 · 사이즈를 고르고 추가하세요. 스타일을 바꾸면 체크는 비워집니다 —
            선택한 스타일에 등록된 색상과 사이즈만 사용할 수 있습니다.
          </template>
        </span>
        <button class="btn btn-primary" :disabled="!draftReady" @click="addDraft()">
          + 목록에 추가
        </button>
      </div>
    </div>

    <!-- ── 2. 담은 목록 ───────────────────────────────────────── -->
    <div v-if="!items.length" class="card empty-note">
      담은 항목이 없습니다. 위에서 스타일을 골라 추가하면 여기에 쌓입니다.
    </div>

    <div v-else class="card">
      <div class="panel-head">
        <div>
          <strong>담은 목록</strong>
          <span class="small dim"> · 스타일 {{ items.length }}건</span>
          <span v-if="preview" class="small dim">
            · 조합 {{ preview.total }}개 · 생성 {{ preview.creatableCount }}건 · 건너뜀
            {{ preview.skipCount }}건
          </span>
        </div>
        <div class="btn-row">
          <span v-if="previewing" class="small dim"><span class="spinner"></span> 확인 중…</span>
          <button class="btn btn-sm" @click="clearAll()">전체 비우기</button>
        </div>
      </div>

      <div v-if="previewError" class="alert alert-danger m-2">
        <span class="alert-icon">⛔</span><span>{{ previewError }}</span>
      </div>

      <!-- 스타일별 허용 옵션은 서버에서도 검사한다. -->
      <div class="alert alert-warn m-2">
        <span class="alert-icon">⚠</span>
        <span>
          사이즈 체계가 섞이지 않았는지 미리보기의 SKU 코드를 확인하세요.
          <strong>스타일에 등록되지 않은 색상·사이즈는 생성할 수 없습니다.</strong>
        </span>
      </div>

      <!-- 항목마다 한 줄. 펴면 색상 × 사이즈 표가 나온다. -->
      <div v-for="(it, i) in items" :key="i" class="item">
        <div class="item-head">
          <button class="toggle" :title="isOpen(i) ? '접기' : '펴기'" @click="toggle(i)">
            {{ isOpen(i) ? '▾' : '▸' }}
          </button>
          <div class="item-title">
            <strong>{{ productNameOf(it.productId) }}</strong>
            <span class="small dim code"> {{ it.productId }}</span>
            <div class="small dim">
              {{ it.colorCodes.map((c) => labelOf(colorOptions, c)).join(' · ') }}
              &nbsp;×&nbsp;
              {{ it.sizeCodes.map((z) => labelOf(sizeOptions, z)).join(' · ') }}
              &nbsp;·&nbsp;
              {{ labelOf(statusOptions, it.status) }}
            </div>
          </div>
          <div class="item-count small">
            <template v-if="previewAt(i)">
              <span v-if="previewAt(i).creatableCount" class="cell new">
                + {{ previewAt(i).creatableCount }}건
              </span>
              <span v-else class="dim">생성 0건</span>
              <span v-if="previewAt(i).skipCount" class="badge badge-amber">
                건너뜀 {{ previewAt(i).skipCount }}
              </span>
            </template>
            <span v-else class="dim">확인 중…</span>
          </div>
          <div class="btn-row">
            <button class="btn btn-sm" title="고치려면 다시 고르기로 되돌립니다" @click="editItem(i)">
              고치기
            </button>
            <button class="btn btn-sm btn-danger" @click="removeItem(i)">빼기</button>
          </div>
        </div>

        <div v-if="isOpen(i) && previewAt(i)" class="item-body">
          <div class="matrix-wrap">
            <table class="matrix">
              <thead>
                <tr>
                  <th class="corner"></th>
                  <th v-for="sz in it.sizeCodes" :key="sz">{{ labelOf(sizeOptions, sz) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in it.colorCodes" :key="c">
                  <th class="rowhead"><span class="badge">{{ c }}</span></th>
                  <td v-for="sz in it.sizeCodes" :key="sz">
                    <template v-if="comboAt(i, c, sz)">
                      <span
                        v-if="comboAt(i, c, sz).creatable"
                        class="cell new"
                        title="새로 만들어집니다"
                      >
                        + 생성
                      </span>
                      <span v-else class="cell has" :title="comboAt(i, c, sz).skipReason">
                        {{ comboAt(i, c, sz).skuId }}
                      </span>
                    </template>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="legend small dim">
            <span class="cell new">+ 생성</span> 새로 만들어질 조합 ·
            <span class="cell has">코드</span> 건너뛸 조합 (칸에 마우스를 올리면 사유)
          </div>

          <div v-if="previewAt(i).skipCount" class="skip-list">
            <ul>
              <li v-for="s in previewAt(i).combos.filter((x) => !x.creatable)" :key="`${s.colorCode}/${s.sizeCode}`">
                <span class="badge">{{ labelOf(colorOptions, s.colorCode) }}</span>
                <span class="badge">{{ labelOf(sizeOptions, s.sizeCode) }}</span>
                <span class="small">{{ s.skipReason }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <div class="panel-foot">
        <FormField
          v-model="reason"
          label="사유"
          class="grow"
          placeholder="비우면 'SKU 일괄생성' 으로 기록됩니다"
          help="만들어진 SKU 마다 변경 이력에 남습니다."
        />
        <button
          class="btn btn-primary"
          :disabled="!canCreate || !preview?.creatableCount || previewing"
          :title="createDenyReason ?? 'SKU 일괄생성'"
          @click="askCreate = true"
        >
          {{ preview?.creatableCount ?? 0 }}건 생성
        </button>
      </div>

      <div v-if="preview && !preview.creatableCount" class="alert alert-warn m-2">
        <span class="alert-icon">⚠</span>
        <span>만들 수 있는 조합이 없습니다. 담은 조합이 모두 이미 있거나 만들 수 없는 것입니다.</span>
      </div>
    </div>

    <!-- ── 3. 무엇이 생겼는지 본다 ────────────────────────────── -->
    <div v-if="result" class="card mt-2">
      <div class="panel-head">
        <div>
          <strong>생성 결과</strong>
          <span class="small dim">
            · 스타일 {{ result.items.length }}건 · 요청 {{ result.requested }}건 · 생성
            {{ result.createdCount }}건 · 건너뜀 {{ result.skippedCount }}건
          </span>
        </div>
        <!--
          담은 목록을 치우고 다음 묶음으로. 자동으로 안 비우는 이유는
          무엇이 만들어졌는지 여기서 확인하는 것이 마지막 단계라서다.
        -->
        <button class="btn btn-primary btn-sm" @click="startNext()">이어서 더 만들기</button>
      </div>

      <div v-for="r in result.items" :key="r.productId" class="item">
        <div class="item-head">
          <div class="item-title">
            <strong>{{ r.productName }}</strong>
            <span class="small dim code"> {{ r.productId }}</span>
            <span class="small dim">
              · 생성 {{ r.createdCount }}건 · 건너뜀 {{ r.skippedCount }}건
            </span>
          </div>
          <button class="btn btn-sm" @click="openSkus(r.productId)">SKU 관리에서 보기</button>
        </div>
        <div class="item-body">
          <div v-if="r.createdCount" class="chips">
            <span v-for="s in r.created" :key="s.skuId" class="badge">{{ s.skuId }}</span>
          </div>
          <div v-if="r.skippedCount" class="skip-list">
            <ul>
              <li v-for="s in r.skipped" :key="`${s.colorCode}/${s.sizeCode}`">
                <span class="badge">{{ labelOf(colorOptions, s.colorCode) }}</span>
                <span class="badge">{{ labelOf(sizeOptions, s.sizeCode) }}</span>
                <span class="small">{{ s.skipReason }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <p class="small dim m-2">
        바코드는 아직 발급하지 않았습니다. 지금 라벨을 뽑으면 SKU 코드가 찍힙니다 —
        SKU 관리에서 하나씩 넣을 수 있습니다.
      </p>
    </div>

    <ConfirmDialog
      v-if="askCreate"
      title="SKU 일괄생성"
      :message="`스타일 ${items.length}건에 SKU ${preview?.creatableCount ?? 0}건을 만듭니다.`"
      detail="담은 것은 전부 만들어지거나 전부 만들어지지 않습니다. 바코드는 발급하지 않습니다. 만든 뒤에는 하나씩 지워야 하며, 재고가 붙은 SKU 는 지울 수 없습니다."
      confirm-label="생성"
      :busy="creating"
      @cancel="askCreate = false"
      @confirm="doCreate()"
    />
  </div>
</template>

<style scoped>
.p-3 {
  padding: 14px;
}
.m-2 {
  margin: 10px 14px;
}
.empty-note {
  padding: 40px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.reuse {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0 4px;
}
.add-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.panel-head,
.panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 14px;
}
.panel-head {
  border-bottom: 1px solid var(--line, #e5e7eb);
}
.panel-foot {
  border-top: 1px solid var(--line, #e5e7eb);
  align-items: flex-end;
}
.panel-foot .grow {
  flex: 1;
}
.item + .item {
  border-top: 1px solid var(--line, #e5e7eb);
}
.item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
}
.item-title {
  flex: 1;
  min-width: 0;
}
/* 스타일명 바로 뒤의 코드 — 인라인 공백은 접히므로 여백으로 띄운다 */
.item-title .code {
  margin-left: 6px;
}
.item-count {
  display: flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.toggle {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  width: 18px;
  color: var(--fg-dim, #6b7280);
}
.item-body {
  padding: 0 14px 12px 42px;
}
.matrix-wrap {
  overflow-x: auto;
}
.matrix {
  border-collapse: collapse;
  font-size: 12.5px;
}
.matrix th,
.matrix td {
  border: 1px solid var(--line, #e5e7eb);
  padding: 5px 9px;
  text-align: center;
  white-space: nowrap;
}
.matrix thead th {
  background: var(--bg-soft, #f8fafc);
  font-weight: 600;
}
.matrix .corner {
  background: transparent;
  border: none;
}
.matrix .rowhead {
  text-align: left;
  background: var(--bg-soft, #f8fafc);
}
.cell {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11.5px;
}
.cell.new {
  color: var(--c-green);
  border: 1px solid currentColor;
}
.cell.has {
  color: var(--fg-dim, #6b7280);
  border: 1px dashed currentColor;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.legend {
  padding: 8px 0 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.skip-list ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
.skip-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  border-top: 1px solid var(--line, #e5e7eb);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  padding-bottom: 6px;
}
</style>
