<script setup>
/**
 * SKU 일괄생성 (MST-PG-009).
 *
 * 패션 의류는 제품 하나에 SKU 가 20~40건씩 붙는다. 색상 × 사이즈 조합이기
 * 때문이다. 한 건씩 등록하면 같은 일을 마흔 번 한다.
 *
 * 흐름은 장바구니와 같다. 제품 하나를 고르고 색상 · 사이즈를 체크해 '추가' 로
 * 담고, 다음 제품을 담고, 마지막에 한 번 만든다. 신상품 입고는 보통 여러
 * 제품이 한꺼번에 오므로 제품마다 화면을 다시 여는 것은 같은 일을 반복하는
 * 것이다.
 *
 * 만들기는 한 번의 요청이다. 다섯 제품을 다섯 번 보내면 세 번째에서 실패했을
 * 때 앞의 둘은 이미 만들어져 있고, 사용자는 무엇이 만들어졌는지 모르는 채로
 * 다시 시도하게 된다.
 *
 * 조합을 자동으로 정하지 않는다. 사이즈 공통코드에는 상의(S·M·L)와
 * 하의(28·30·32)가 함께 들어 있어서, 전 조합을 만들면 티셔츠에 28인치가
 * 생긴다. 무엇을 파는지는 사람이 안다 — 고른 것만 만든다.
 *
 * 담은 것이 바뀔 때마다 서버에 미리보기를 물어 본다. 화면이 혼자 계산하면
 * "7건이 생깁니다" 를 보고 눌렀는데 5건이 생기는 일이 벌어진다. 판정은 한
 * 곳에서만 한다.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as skuApi from '@/api/sku.js'
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

// SKU 관리 화면에서 '일괄생성' 으로 넘어오면 그 제품이 골라진 채로 열린다
const draft = ref({
  productId: route.query.productId ?? '',
  colorCodes: [],
  sizeCodes: [],
  status: 'ACTIVE',
})

const colorOptions = computed(() => codeOptions('COLOR'))
const sizeOptions = computed(() => codeOptions('SIZE'))
const statusOptions = computed(() => codeOptions('SKU_STATUS'))

const labelOf = (options, code) => options.find((o) => o.value === code)?.label ?? code
const productNameOf = (id) =>
  catalog.products.find((p) => p.productId === id)?.productName ?? id

const draftReady = computed(
  () =>
    !!draft.value.productId &&
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
 * 담는다.
 *
 * 같은 제품을 또 담는 것을 막지 않는다 — 색상을 나중에 떠올려 한 줄 더
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
  // 제품만 비운다. 다음 제품도 같은 색상·사이즈 구성인 경우가 많아
  // 체크를 그대로 두는 편이 손이 덜 간다.
  draft.value = { ...draft.value, productId: '' }
}

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
    // 제품 목록의 SKU 수가 낡는다
    catalog.invalidate('products')
  } catch (e) {
    toast.error(e.message)
    askCreate.value = false
  } finally {
    creating.value = false
  }
}

/** 만든 SKU 를 확인하러 간다 */
function openSkus(productId) {
  router.push({ name: 'skus', query: productId ? { productId } : {} })
}

const readDenyReason = computed(() => session.denyReason('MST_SKU', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">SKU 일괄생성</h1>
        <p class="page-desc">
          제품마다 <strong>색상 × 사이즈 조합만큼 SKU 를 만듭니다.</strong>
          제품을 하나씩 담은 뒤 마지막에 한 번에 만듭니다 — 담은 것은 전부 만들어지거나
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

    <!-- ── 1. 제품 하나를 골라 담는다 ──────────────────────────── -->
    <div class="card p-3 mb-2">
      <div class="form-grid">
        <FormField
          v-model="draft.productId"
          label="제품"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.productOptions"
          help="SKU 코드는 제품코드-색상-사이즈 로 만들어집니다."
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
        help="옵션이 없는 제품은 '단일'만 고르세요."
      />
      <FormField
        v-model="draft.sizeCodes"
        label="사이즈"
        type="checks"
        span
        :options="sizeOptions"
        help="상의(S·M·L)와 하의(28·30·32)가 한 목록에 있습니다. 이 제품에 맞는 것만 고르세요."
      />

      <div class="add-bar">
        <span class="small dim">
          <template v-if="draftReady">
            {{ productNameOf(draft.productId) }} · 색상 {{ draft.colorCodes.length }} × 사이즈
            {{ draft.sizeCodes.length }} = <strong>{{ draftCount }}개 조합</strong>
          </template>
          <template v-else>
            제품 · 색상 · 사이즈를 고르고 추가하세요. 색상·사이즈 체크는 다음 제품에도 그대로
            남습니다.
          </template>
        </span>
        <button class="btn btn-primary" :disabled="!draftReady" @click="addDraft()">
          + 목록에 추가
        </button>
      </div>
    </div>

    <!-- ── 2. 담은 목록 ───────────────────────────────────────── -->
    <div v-if="!items.length" class="card empty-note">
      담은 항목이 없습니다. 위에서 제품을 골라 추가하면 여기에 쌓입니다.
    </div>

    <div v-else class="card">
      <div class="panel-head">
        <div>
          <strong>담은 목록</strong>
          <span class="small dim"> · 제품 {{ items.length }}건</span>
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
                  <th class="rowhead"><CodeBadge group="COLOR" :code="c" /></th>
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
            · 제품 {{ result.items.length }}건 · 요청 {{ result.requested }}건 · 생성
            {{ result.createdCount }}건 · 건너뜀 {{ result.skippedCount }}건
          </span>
        </div>
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
      :message="`제품 ${items.length}건에 SKU ${preview?.creatableCount ?? 0}건을 만듭니다.`"
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
/* 제품명 바로 뒤의 코드 — 인라인 공백은 접히므로 여백으로 띄운다 */
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
