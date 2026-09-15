<script setup>
/**
 * 빈 바코드 출력 (MST-PG-004).
 *
 * 빈 라벨을 만들어 라벨지에 인쇄한다. 현장에서 스캔해 '어디' 를 특정하는
 * 종이다.
 *
 * 라벨에 두 가지가 들어간다.
 *   사람이 읽는 값  1A-01-01        크게. 현장에서 부르는 이름
 *   기계가 읽는 값  PL001GD1A0101   바코드. 센터·창고까지 담아 전사에서 하나
 *
 * 빈코드는 창고 안에서만 유일해(V7) 그것만 찍으면 스캔값이 어느 센터
 * 것인지 세션에 기대야 한다. 세션이 틀리면 재고가 조용히 엉뚱한 센터에
 * 잡히므로 바코드가 혼자 위치를 특정한다.
 *
 * 라벨지 규격은 화면에서 고른다. 프린터마다 인쇄 시작 위치가 몇 mm씩
 * 달라서 규격을 정확히 넣어도 한 번에 맞지 않는다 — 시험 인쇄로 맞추고
 * 미세조정 값을 저장해 둔다.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as locationApi from '@/api/location.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import FormField from '@/components/FormField.vue'
import BarcodeSvg from '@/components/BarcodeSvg.vue'

const route = useRoute()
const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

/* ── 대상 고르기 ────────────────────────────────────────────── */

const filters = reactive({
  plantId: route.query.plantId ?? '',
  warehouseId: route.query.warehouseId ?? '',
  keyword: '',
})

const rows = ref([])
const picked = ref(new Set())
const loading = ref(false)
const loadError = ref('')

async function fetchRows() {
  if (!filters.plantId) {
    rows.value = []
    picked.value = new Set()
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    // 라벨은 쓰는 빈만 뽑는다. 미사용 빈에 라벨을 붙일 이유가 없다.
    const data = await locationApi.list({ ...filters, useYn: 'Y', size: 0 })
    rows.value = data.rows
    picked.value = new Set(data.rows.map((l) => l.locationSeq))
  } catch (e) {
    rows.value = []
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 창고까지 받는다. 아래 창고 드롭다운이 warehouseOptionsOf 로 이 목록에서
  // 골라내므로, 플랜트만 받으면 플랜트를 골라도 창고 칸이 비어 있다.
  await Promise.all([hierarchy.loadPlants(false), hierarchy.loadWarehouses(false)])
  await fetchRows()
})

watch(() => [filters.plantId, filters.warehouseId], fetchRows)

const warehouseOptions = computed(() =>
  filters.plantId ? hierarchy.warehouseOptionsOf(filters.plantId) : [],
)

function toggle(seq) {
  const next = new Set(picked.value)
  next.has(seq) ? next.delete(seq) : next.add(seq)
  picked.value = next
}
const allPicked = computed(() => rows.value.length > 0 && picked.value.size === rows.value.length)
function toggleAll() {
  picked.value = allPicked.value ? new Set() : new Set(rows.value.map((l) => l.locationSeq))
}

/** 인쇄 대상 — 고른 순서가 아니라 목록 순서를 따른다 */
const labels = computed(() => rows.value.filter((l) => picked.value.has(l.locationSeq)))

/* ── 라벨지 규격 ────────────────────────────────────────────── */

/**
 * 흔히 쓰는 규격 몇 개.
 *
 * 정확한 수치는 라벨지 제조사마다 다르다. 여기 값은 출발점이고, 시험
 * 인쇄로 맞춘 뒤 미세조정에 차이를 넣는다.
 */
const PRESETS = [
  { value: 'A4_2x8', label: 'A4 · 2칸 × 8줄 (99 × 34mm)',
    cols: 2, rows: 8, w: 99, h: 34, top: 12.7, left: 4.6, gapX: 2.5, gapY: 0 },
  { value: 'A4_3x8', label: 'A4 · 3칸 × 8줄 (64 × 34mm)',
    cols: 3, rows: 8, w: 64, h: 34, top: 12.7, left: 7, gapX: 2.5, gapY: 0 },
  { value: 'A4_2x6', label: 'A4 · 2칸 × 6줄 (99 × 46mm)',
    cols: 2, rows: 6, w: 99, h: 46, top: 9, left: 4.6, gapX: 2.5, gapY: 0 },
  { value: 'ROLL_100x50', label: '롤 라벨 · 100 × 50mm (한 장씩)',
    cols: 1, rows: 1, w: 100, h: 50, top: 0, left: 0, gapX: 0, gapY: 0 },
  { value: 'CUSTOM', label: '직접 입력' },
]

const preset = ref('A4_2x8')
const spec = reactive({ cols: 2, rows: 8, w: 99, h: 34, top: 12.7, left: 4.6, gapX: 2.5, gapY: 0 })
/** 시험 인쇄로 맞춘 차이 — 프린터마다 인쇄 시작 위치가 다르다 */
const nudge = reactive({ x: 0, y: 0 })

watch(preset, (v) => {
  const p = PRESETS.find((x) => x.value === v)
  if (p && v !== 'CUSTOM') Object.assign(spec, { ...p })
})

/** 설정은 브라우저에 남긴다 — 맞춰 둔 값을 매번 다시 넣게 할 이유가 없다 */
const STORE_KEY = 'wms.labelSpec'
onMounted(() => {
  try {
    const saved = JSON.parse(localStorage.getItem(STORE_KEY) || 'null')
    if (saved) {
      preset.value = saved.preset ?? 'A4_2x8'
      Object.assign(spec, saved.spec ?? {})
      Object.assign(nudge, saved.nudge ?? {})
    }
  } catch {
    // 저장값이 깨졌거나 브라우저가 막은 경우 — 기본값으로 간다
  }
})
watch(
  [preset, spec, nudge],
  () => {
    try {
      localStorage.setItem(
        STORE_KEY,
        JSON.stringify({ preset: preset.value, spec: { ...spec }, nudge: { ...nudge } }),
      )
    } catch {
      // 저장이 막혀도 인쇄 자체는 된다
    }
  },
  { deep: true },
)

const perSheet = computed(() => Math.max(1, Number(spec.cols) * Number(spec.rows)))
const sheetCount = computed(() => Math.ceil(labels.value.length / perSheet.value) || 0)

/* ── 바코드 크기 ────────────────────────────────────────────── */

/**
 * 가는 바 하나의 너비.
 *
 * 칸 폭에 맞춰 정한다. 너무 가늘면 감열 203dpi 프린터에서 바가 뭉개져
 * 안 읽히고, 너무 굵으면 바코드가 칸을 넘는다.
 *
 * Code128 은 글자 하나에 11모듈, 시작·검사·정지에 35모듈을 쓴다.
 * 여기에 좌우 여백 20모듈을 더해 칸 폭의 90% 에 들어가도록 잡는다.
 */
const moduleWidth = computed(() => {
  const longest = labels.value.reduce((m, l) => Math.max(m, (l.labelBarcode || '').length), 8)
  const modules = longest * 11 + 35 + 20
  const usable = Number(spec.w) * 0.9
  // 0.19mm 는 203dpi 프린터의 1.5도트 — 이보다 가늘면 실용적이지 않다
  return Math.max(0.19, Math.min(0.5, usable / modules))
})

const barcodeHeight = computed(() => Math.max(8, Number(spec.h) * 0.38))

/* ── 인쇄 ───────────────────────────────────────────────────── */

const testMode = ref(false)

function doPrint(test) {
  testMode.value = test
  // 렌더가 끝난 뒤에 인쇄창을 연다
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      window.print()
      testMode.value = false
    })
  })
}

/** 시험 인쇄용 — 칸 위치만 보이는 빈 라벨 */
const testCells = computed(() => Array.from({ length: perSheet.value }, (_, i) => i + 1))

const readDenyReason = computed(() => session.denyReason('MST_LOCATION', 'R'))
const printDenyReason = computed(() => session.denyReason('MST_LOCATION', 'X'))
const canPrint = computed(() => session.can('MST_LOCATION', 'X'))

/** 바코드가 없는 빈 — 라벨에 빈코드가 찍혀 다른 센터와 겹칠 수 있다 */
const noBarcode = computed(() => labels.value.filter((l) => !l.barcode))
</script>

<template>
  <div>
    <div class="page-head no-print">
      <div>
        <h1 class="page-title">빈 바코드 출력</h1>
        <p class="page-desc">
          빈 라벨을 만들어 라벨지에 인쇄합니다.
          <strong>사람은 크게 인쇄된 빈코드를 읽고, 스캐너는 바코드를 읽습니다.</strong>
          바코드에는 센터·창고까지 담겨 전사에서 한 곳을 지목합니다 — 빈코드만으로는
          다른 센터와 겹칠 수 있기 때문입니다.
          프린터마다 인쇄 시작 위치가 달라 한 번에 맞지 않습니다. 시험 인쇄로 맞추세요.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2 no-print">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2 no-print">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="printDenyReason" class="alert alert-warn mb-2 no-print">
      <span class="alert-icon">⚠</span><span>{{ printDenyReason }}</span>
    </div>

    <!-- ── 1. 대상 ───────────────────────────────────────────── -->
    <div class="card p-3 mb-2 no-print">
      <div class="toolbar">
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          required
          empty-option="선택하세요"
          :options="hierarchy.plantOptions"
        />
        <FormField
          v-model="filters.warehouseId"
          label="창고"
          type="select"
          empty-option="전체"
          :options="warehouseOptions"
          :disabled="!filters.plantId"
        />
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="빈코드 / 바코드 / 구역"
          @keyup.enter="fetchRows()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading || !filters.plantId" @click="fetchRows()">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <div v-if="!filters.plantId" class="empty-note">
        플랜트를 고르면 그 안의 빈이 나옵니다. 사용 중인 빈만 대상입니다.
      </div>

      <template v-else>
        <div class="pick-head">
          <label class="check-line">
            <input type="checkbox" :checked="allPicked" @change="toggleAll()" />
            전체 선택
          </label>
          <span class="small dim">
            {{ rows.length }}개 중 <strong>{{ labels.length }}개</strong> 선택 ·
            {{ sheetCount }}장 ({{ perSheet }}칸/장)
          </span>
        </div>
        <div class="picks">
          <label v-for="l in rows" :key="l.locationSeq" class="pick">
            <input
              type="checkbox"
              :checked="picked.has(l.locationSeq)"
              @change="toggle(l.locationSeq)"
            />
            <span class="code">{{ l.locationId }}</span>
            <span class="small dim">{{ l.warehouseId }}</span>
            <span v-if="!l.barcode" class="small warn" title="바코드 미발급">⚠</span>
          </label>
        </div>
      </template>
    </div>

    <div v-if="noBarcode.length" class="alert alert-warn mb-2 no-print">
      <span class="alert-icon">⚠</span>
      <span>
        바코드를 발급하지 않은 빈이 {{ noBarcode.length }}개 있습니다 —
        <strong>{{ noBarcode.map((l) => l.locationId).join(', ') }}</strong>.
        라벨에는 빈코드가 찍히는데, 빈코드는 창고 안에서만 유일해 다른 센터와 겹칠 수 있습니다.
        빈 관리에서 권장 바코드를 넣으세요.
      </span>
    </div>

    <!-- ── 2. 라벨지 규격 ────────────────────────────────────── -->
    <div v-if="labels.length" class="card p-3 mb-2 no-print">
      <div class="form-grid">
        <FormField
          v-model="preset"
          label="라벨지 규격"
          type="select"
          :options="PRESETS"
          help="봉투에 적힌 규격이 없으면 직접 재서 넣으세요."
        />
        <FormField v-model="spec.cols" label="가로 칸 수" type="number" />
        <FormField v-model="spec.rows" label="세로 줄 수" type="number" />
        <FormField v-model="spec.w" label="칸 가로 (mm)" type="number" />
        <FormField v-model="spec.h" label="칸 세로 (mm)" type="number" />
        <FormField v-model="spec.top" label="위 여백 (mm)" type="number" />
        <FormField v-model="spec.left" label="왼쪽 여백 (mm)" type="number" />
        <FormField v-model="spec.gapX" label="칸 사이 가로 (mm)" type="number" />
        <FormField v-model="spec.gapY" label="칸 사이 세로 (mm)" type="number" />
      </div>

      <div class="nudge">
        <strong class="small">미세조정</strong>
        <FormField v-model="nudge.x" label="가로 (mm)" type="number" />
        <FormField v-model="nudge.y" label="세로 (mm)" type="number" />
        <span class="small dim">
          시험 인쇄가 오른쪽으로 2mm 밀렸으면 가로에 -2 를 넣으세요. 설정은 이 브라우저에 저장됩니다.
        </span>
      </div>

      <div class="print-bar">
        <span class="small dim">
          가는 바 {{ moduleWidth.toFixed(2) }}mm · Code128 ·
          <strong>인쇄 배율을 100%(실제 크기)로 두세요.</strong>
          '페이지에 맞춤'이 켜져 있으면 바 두께가 어긋나 스캔되지 않습니다.
        </span>
        <div class="btn-row">
          <button class="btn" @click="doPrint(true)">시험 인쇄 (빈 칸)</button>
          <button
            class="btn btn-primary"
            :disabled="!canPrint"
            :title="printDenyReason ?? '라벨 인쇄'"
            @click="doPrint(false)"
          >
            {{ labels.length }}장 인쇄
          </button>
        </div>
      </div>
    </div>

    <!-- ── 3. 미리보기 = 인쇄물 ──────────────────────────────── -->
    <div v-if="labels.length" class="card preview-wrap">
      <div class="panel-head no-print">
        <strong class="small">미리보기</strong>
        <span class="small dim">화면에 보이는 그대로 인쇄됩니다</span>
      </div>

      <div
        class="sheet"
        :style="{
          paddingTop: `${Number(spec.top) + Number(nudge.y)}mm`,
          paddingLeft: `${Number(spec.left) + Number(nudge.x)}mm`,
          gridTemplateColumns: `repeat(${spec.cols}, ${spec.w}mm)`,
          columnGap: `${spec.gapX}mm`,
          rowGap: `${spec.gapY}mm`,
        }"
      >
        <!-- 시험 인쇄 — 칸 위치만 보이는 빈 라벨 -->
        <template v-if="testMode">
          <div
            v-for="n in testCells"
            :key="`t${n}`"
            class="cell test"
            :style="{ width: `${spec.w}mm`, height: `${spec.h}mm` }"
          >
            <span class="small">{{ n }}</span>
          </div>
        </template>

        <template v-else>
          <div
            v-for="l in labels"
            :key="l.locationSeq"
            class="cell"
            :style="{ width: `${spec.w}mm`, height: `${spec.h}mm` }"
          >
            <div class="site">{{ l.plantName }} · {{ l.warehouseName }}</div>
            <div class="loc">{{ l.locationId }}</div>
            <BarcodeSvg
              :value="l.labelBarcode"
              format="CODE128"
              :module-width="moduleWidth"
              :height="barcodeHeight"
              :font-size="2.4"
            />
          </div>
        </template>
      </div>
    </div>

    <div v-else-if="filters.plantId && !loading" class="card empty-note no-print">
      선택한 빈이 없습니다. 위에서 라벨을 뽑을 빈을 고르세요.
    </div>
  </div>
</template>

<style scoped>
.p-3 {
  padding: 14px;
}
.empty-note {
  padding: 32px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 14px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}
.pick-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0 6px;
  border-top: 1px solid var(--line, #e5e7eb);
  margin-top: 10px;
}
.picks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  max-height: 180px;
  overflow-y: auto;
}
.pick {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  cursor: pointer;
}
.pick .code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.nudge {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 10px;
  margin-top: 6px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.nudge .field {
  width: 110px;
}
.print-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 12px;
  margin-top: 10px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.warn {
  color: var(--c-amber);
}

/* ── 라벨 한 장 ─────────────────────────────────────────────── */
.preview-wrap {
  overflow-x: auto;
}
.sheet {
  display: grid;
  justify-content: start;
  background: #ffffff;
  /* A4 폭. 화면에서도 실제 배치를 그대로 보여 준다. */
  width: 210mm;
}
.cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  box-sizing: border-box;
  padding: 1mm;
  /* 화면에서는 칸 경계를 보여 주고, 인쇄할 때는 지운다 */
  outline: 1px dashed #d4d4d8;
  outline-offset: -1px;
  color: #000000;
}
.cell.test {
  color: #9ca3af;
}
.site {
  font-size: 2.2mm;
  line-height: 1.2;
  color: #4b5563;
}
.loc {
  font-size: 5mm;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: 0.2mm;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

/* ── 인쇄 ───────────────────────────────────────────────────── */
@media print {
  .no-print {
    display: none !important;
  }
  .preview-wrap {
    overflow: visible;
    border: none;
    box-shadow: none;
    background: transparent;
  }
  .cell {
    /* 라벨지에는 칸 경계를 찍지 않는다 — 라벨 자체가 경계다 */
    outline: none;
  }
  .sheet {
    width: auto;
  }
}
</style>
