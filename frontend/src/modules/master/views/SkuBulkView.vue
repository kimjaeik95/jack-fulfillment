<script setup>
/**
 * SKU 일괄생성 (MST-PG-009).
 *
 * 패션 의류는 제품 하나에 SKU 가 20~40건씩 붙는다. 색상 × 사이즈 조합이기
 * 때문이다. 한 건씩 등록하면 같은 일을 마흔 번 한다.
 *
 * 조합을 자동으로 정하지 않는다. 사이즈 공통코드에는 상의(S·M·L)와
 * 하의(28·30·32)가 함께 들어 있어서, 전 조합을 만들면 티셔츠에 28인치가
 * 생긴다. 무엇을 파는지는 사람이 안다 — 고른 것만 만든다.
 *
 * 고를 때마다 서버에 미리보기를 물어 본다. 화면이 혼자 계산하면 "7건이
 * 생깁니다" 를 보고 눌렀는데 5건이 생기는 일이 벌어진다. 판정은 한 곳에서만
 * 한다.
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

/* ── 고르는 것 ──────────────────────────────────────────────── */

// SKU 관리 화면에서 '일괄생성' 으로 넘어오면 그 제품이 골라진 채로 열린다
const productId = ref(route.query.productId ?? '')
const colorCodes = ref([])
const sizeCodes = ref([])
const status = ref('ACTIVE')
const reason = ref('')

const colorOptions = computed(() => codeOptions('COLOR'))
const sizeOptions = computed(() => codeOptions('SIZE'))

/* ── 미리보기 ───────────────────────────────────────────────── */

const preview = ref(null)
const previewing = ref(false)
const previewError = ref('')

const chosen = computed(
  () => !!productId.value && colorCodes.value.length > 0 && sizeCodes.value.length > 0,
)

async function runPreview() {
  if (!chosen.value) {
    preview.value = null
    previewError.value = ''
    return
  }
  previewing.value = true
  previewError.value = ''
  try {
    preview.value = await skuApi.previewBulk({
      productId: productId.value,
      colorCodes: colorCodes.value,
      sizeCodes: sizeCodes.value,
      status: status.value,
      reason: reason.value || null,
    })
  } catch (e) {
    preview.value = null
    previewError.value = e.message
  } finally {
    previewing.value = false
  }
}

/**
 * 고른 것이 바뀌면 다시 물어 본다.
 *
 * 체크 한 번마다 요청이 나가지 않도록 조금 기다린다. 색상 다섯 개를 연달아
 * 체크하면 요청 다섯 번이 아니라 한 번이면 된다.
 */
let timer = null
watch(
  [productId, colorCodes, sizeCodes, status],
  () => {
    // 결과를 띄워 둔 채로 조건을 바꾸면 그 결과는 더 이상 지금 화면의 것이 아니다
    result.value = null
    clearTimeout(timer)
    timer = setTimeout(runPreview, 400)
  },
  { deep: true },
)

onMounted(async () => {
  await catalog.loadProducts(false)
  await runPreview()
})

/** 미리보기 표 — 색상이 행, 사이즈가 열 */
const comboAt = (color, size) =>
  preview.value?.combos.find((c) => c.colorCode === color && c.sizeCode === size) ?? null

const skipped = computed(() => preview.value?.combos.filter((c) => !c.creatable) ?? [])

const labelOf = (options, code) => options.find((o) => o.value === code)?.label ?? code

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
      productId: productId.value,
      colorCodes: colorCodes.value,
      sizeCodes: sizeCodes.value,
      status: status.value,
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
function openSkus() {
  router.push({ name: 'skus', query: { productId: productId.value } })
}

const productName = computed(
  () => catalog.products.find((p) => p.productId === productId.value)?.productName ?? '',
)

const readDenyReason = computed(() => session.denyReason('MST_SKU', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">SKU 일괄생성</h1>
        <p class="page-desc">
          제품 하나에 <strong>색상 × 사이즈 조합만큼 SKU 를 한 번에 만듭니다.</strong>
          고른 조합만 만들며, 이미 있는 조합은 건너뛰고 사유를 알려 줍니다.
          바코드는 발급하지 않습니다 — 라벨을 뽑을 때 정하고, 그때까지 라벨에는 SKU 코드가 찍힙니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn" :disabled="!productId" @click="openSkus()">SKU 관리에서 보기</button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <!-- ── 1. 무엇을 만들지 고른다 ─────────────────────────────── -->
    <div class="card p-3 mb-2">
      <div class="form-grid">
        <FormField
          v-model="productId"
          label="제품"
          type="select"
          required
          empty-option="선택하세요"
          :options="catalog.productOptions"
          help="SKU 코드는 제품코드-색상-사이즈 로 만들어집니다."
        />
        <FormField
          v-model="status"
          label="SKU 상태"
          type="select"
          required
          :options="codeOptions('SKU_STATUS')"
          help="만들어질 SKU 전부에 같은 상태가 들어갑니다."
        />
      </div>

      <FormField
        v-model="colorCodes"
        label="색상"
        type="checks"
        span
        :options="colorOptions"
        help="옵션이 없는 제품은 '단일'만 고르세요."
      />
      <FormField
        v-model="sizeCodes"
        label="사이즈"
        type="checks"
        span
        :options="sizeOptions"
        help="상의(S·M·L)와 하의(28·30·32)가 한 목록에 있습니다. 이 제품에 맞는 것만 고르세요."
      />
      <FormField
        v-model="reason"
        label="사유"
        span
        placeholder="비우면 'SKU 일괄생성' 으로 기록됩니다"
        help="만들어진 SKU 마다 변경 이력에 남습니다."
      />
    </div>

    <!-- ── 2. 무엇이 생기는지 본다 ─────────────────────────────── -->
    <div v-if="!chosen" class="card empty-note">
      제품 · 색상 · 사이즈를 고르면 무엇이 만들어지는지 여기에 표시됩니다.
    </div>

    <div v-else class="card">
      <div class="panel-head">
        <div>
          <strong>{{ productName || productId }}</strong>
          <span class="small dim">
            · 색상 {{ colorCodes.length }} × 사이즈 {{ sizeCodes.length }} =
            {{ colorCodes.length * sizeCodes.length }}개 조합
          </span>
        </div>
        <span v-if="previewing" class="small dim"><span class="spinner"></span> 확인 중…</span>
      </div>

      <div v-if="previewError" class="alert alert-danger m-2">
        <span class="alert-icon">⛔</span><span>{{ previewError }}</span>
      </div>

      <template v-else-if="preview">
        <div
          :class="[
            'alert',
            'm-2',
            preview.creatableCount ? 'alert-info' : 'alert-warn',
          ]"
        >
          <span class="alert-icon">{{ preview.creatableCount ? 'ℹ' : '⚠' }}</span>
          <span v-if="preview.creatableCount">
            <strong>{{ preview.creatableCount }}건</strong>이 만들어집니다.
            <template v-if="preview.skipCount">
              {{ preview.skipCount }}건은 건너뜁니다 — 아래에 사유가 있습니다.
            </template>
          </span>
          <span v-else>
            만들 수 있는 조합이 없습니다. 고른 조합이 모두 이미 있거나 만들 수 없는 것입니다.
          </span>
        </div>

        <!-- 색상 × 사이즈 표. 어느 칸이 비어 있는지 한눈에 보여야 한다. -->
        <div class="matrix-wrap">
          <table class="matrix">
            <thead>
              <tr>
                <th class="corner"></th>
                <th v-for="sz in sizeCodes" :key="sz">{{ labelOf(sizeOptions, sz) }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in colorCodes" :key="c">
                <th class="rowhead"><CodeBadge group="COLOR" :code="c" /></th>
                <td v-for="sz in sizeCodes" :key="sz">
                  <template v-if="comboAt(c, sz)">
                    <span v-if="comboAt(c, sz).creatable" class="cell new" title="새로 만들어집니다">
                      + 생성
                    </span>
                    <span v-else class="cell has" :title="comboAt(c, sz).skipReason">
                      {{ comboAt(c, sz).skuId }}
                    </span>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="legend small dim">
          <span class="cell new">+ 생성</span> 새로 만들어질 조합 ·
          <span class="cell has">코드</span> 이미 있어 건너뛸 조합 (칸에 마우스를 올리면 사유)
        </div>

        <!-- 건너뛰는 이유가 '이미 있음' 이 아닌 것은 따로 읽혀야 한다 -->
        <div v-if="skipped.length" class="skip-list">
          <div class="small dim mb-1">건너뛸 {{ skipped.length }}건</div>
          <ul>
            <li v-for="s in skipped" :key="`${s.colorCode}/${s.sizeCode}`">
              <span class="badge">{{ labelOf(colorOptions, s.colorCode) }}</span>
              <span class="badge">{{ labelOf(sizeOptions, s.sizeCode) }}</span>
              <span class="small">{{ s.skipReason }}</span>
            </li>
          </ul>
        </div>

        <div class="panel-foot">
          <span class="small dim">
            만들기 전에 표를 확인하세요. 만든 뒤에는 SKU 를 하나씩 지워야 하고, 재고가 붙으면
            지울 수도 없습니다.
          </span>
          <button
            class="btn btn-primary"
            :disabled="!canCreate || !preview.creatableCount || previewing"
            :title="createDenyReason ?? 'SKU 일괄생성'"
            @click="askCreate = true"
          >
            {{ preview.creatableCount }}건 생성
          </button>
        </div>
      </template>
    </div>

    <!-- ── 3. 무엇이 생겼는지 본다 ─────────────────────────────── -->
    <div v-if="result" class="card mt-2">
      <div class="panel-head">
        <div>
          <strong>생성 결과</strong>
          <span class="small dim">
            · 요청 {{ result.requested }}건 · 생성 {{ result.createdCount }}건 · 건너뜀
            {{ result.skippedCount }}건
          </span>
        </div>
        <button class="btn btn-sm" @click="openSkus()">SKU 관리에서 보기</button>
      </div>

      <div v-if="result.createdCount" class="created m-2">
        <div class="small dim mb-1">만든 SKU</div>
        <div class="chips">
          <span v-for="s in result.created" :key="s.skuId" class="badge">{{ s.skuId }}</span>
        </div>
        <p class="small dim mt-1">
          바코드는 아직 발급하지 않았습니다. 지금 라벨을 뽑으면 SKU 코드가 찍힙니다 —
          SKU 관리에서 하나씩 넣을 수 있습니다.
        </p>
      </div>

      <div v-if="result.skippedCount" class="skip-list">
        <div class="small dim mb-1">건너뛴 {{ result.skippedCount }}건</div>
        <ul>
          <li v-for="s in result.skipped" :key="`${s.colorCode}/${s.sizeCode}`">
            <span class="badge">{{ labelOf(colorOptions, s.colorCode) }}</span>
            <span class="badge">{{ labelOf(sizeOptions, s.sizeCode) }}</span>
            <span class="small">{{ s.skipReason }}</span>
          </li>
        </ul>
      </div>
    </div>

    <ConfirmDialog
      v-if="askCreate"
      title="SKU 일괄생성"
      :message="`${productName || productId} 에 SKU ${preview?.creatableCount ?? 0}건을 만듭니다.`"
      :detail="
        `상태는 모두 '${codeOptions('SKU_STATUS').find((o) => o.value === status)?.label ?? status}' 로 들어가고 바코드는 발급하지 않습니다. ` +
        `만든 뒤에는 하나씩 지워야 하며, 재고가 붙은 SKU 는 지울 수 없습니다.`
      "
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
.mt-1 {
  margin-top: 6px;
}
.mb-1 {
  margin-bottom: 6px;
}
.empty-note {
  padding: 40px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
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
}
.matrix-wrap {
  overflow-x: auto;
  padding: 0 14px;
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
  padding: 8px 14px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.skip-list {
  padding: 0 14px 12px;
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
.created {
  padding-bottom: 4px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
</style>
