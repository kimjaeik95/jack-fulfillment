<script setup>
/**
 * 재고실사 (D섹터 — INV-PG-008, INV-PG-009).
 *
 * 세 단계를 지나는 화면이라 한 화면에 둔다. 계획 · 입력 · 마감을 따로
 * 만들면 같은 실사를 두고 세 화면을 오가게 되고, 지금 어느 단계인지가
 * 화면마다 따로 관리된다.
 *
 *   계획(PLANNED)    대상을 뽑는다. 몇 번이고 다시 뽑을 수 있다.
 *   실사중(COUNTING) 수량을 받는다. 대상은 고정된다.
 *   마감(CLOSED)     차이를 재고에 반영한다. 되돌릴 수 없다.
 *
 * 블라인드 카운트에서는 장부수량이 <b>응답에 아예 오지 않는다</b>. 화면에서
 * 가리는 것으로는 부족하다 — 개발자 도구를 열면 그대로 보이고, 한 번이라도
 * 보이면 블라인드가 아니다. 그래서 여기서 할 일은 '가리는' 것이 아니라
 * '없는 값을 없는 대로 그리는' 것이다.
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as opsApi from '@/api/stockOps.js'
import * as stockApi from '@/api/stock.js'
import * as locationApi from '@/api/location.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import StockPicker from '../components/StockPicker.vue'
import SkuPicker from '@/components/SkuPicker.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const size = opsApi.PAGE_SIZE

/* ── 목록 ───────────────────────────────────────────────────── */

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  keyword: '',
  plantId: '',
  warehouseId: '',
  takeStatus: '',
  openOnly: 'Y',
  fromDate: stockApi.daysAgo(180),
  toDate: stockApi.daysAgo(-365),
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await opsApi.listTakes({ ...filters, page: page.value, size })
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

onMounted(async () => {
  await Promise.all([hierarchy.loadPlants(false), hierarchy.loadWarehouses(false)])
  await fetchPage()
})

const columns = [
  { key: 'takeNo', label: '실사번호', width: '150px', cls: 'code' },
  { key: 'takeName', label: '실사명', width: '170px' },
  { key: 'warehouseName', label: '창고', width: '120px' },
  { key: 'takeType', label: '유형', width: '80px', align: 'center' },
  { key: 'plannedDate', label: '계획일', width: '96px', align: 'center' },
  { key: '_progress', label: '진행', width: '150px' },
  { key: 'takeStatus', label: '상태', width: '80px', align: 'center' },
  { key: '_act', label: '', width: '78px', align: 'right' },
]

/* ── 계획 작성 ──────────────────────────────────────────────── */

const editing = ref(false)
const editSeq = ref(null)
const busy = ref(false)
const serverError = ref('')

const form = reactive({
  takeName: '',
  plantId: '',
  warehouseId: '',
  takeType: 'FULL',
  plannedDate: stockApi.daysAgo(0),
  blindYn: 'Y',
  targetZone: '',
  targetSkuKeyword: '',
  remark: '',
})

function openCreate() {
  editSeq.value = null
  Object.assign(form, {
    takeName: '',
    plantId: '',
    warehouseId: '',
    takeType: 'FULL',
    plannedDate: stockApi.daysAgo(0),
    blindYn: 'Y',
    targetZone: '',
    targetSkuKeyword: '',
    remark: '',
  })
  serverError.value = ''
  zones.value = []
  preview.value = null
  editing.value = true
}

function openEdit(take) {
  editSeq.value = take.takeSeq
  Object.assign(form, {
    takeName: take.takeName,
    plantId: take.plantId,
    warehouseId: take.warehouseId,
    takeType: take.takeType,
    plannedDate: take.plannedDate,
    blindYn: take.blind ? 'Y' : 'N',
    targetZone: take.targetZone ?? '',
    targetSkuKeyword: take.targetSkuKeyword ?? '',
    remark: take.remark ?? '',
  })
  serverError.value = ''
  editing.value = true
  // 고쳐 열 때는 이미 창고가 정해져 있어 watch 가 안 돈다. 직접 부른다.
  loadZones()
  loadPreview()
}

const formValid = computed(
  () => form.takeName.trim() && form.plantId && form.warehouseId && form.plannedDate && !busy.value,
)

/* ── 구역 목록 ──────────────────────────────────────────────────
 *
 * 구역은 창고마다 다르고 사람이 외울 수 없다. 외우게 하면 오타 하나로
 * 대상이 0 건이 되는데, 구역 조건은 정확일치라 '거의 맞는' 값도 안 걸린다.
 *
 * 코드표가 아니라 그 창고의 빈에 실제로 쓰인 값을 받는다 — 목록에 있으면
 * 반드시 쓸 수 있다.
 */
const zones = ref([])
const zoneOptions = computed(() => zones.value.map((z) => ({ value: z, label: `${z} 구역` })))

async function loadZones() {
  if (!form.plantId || !form.warehouseId) {
    zones.value = []
    return
  }
  try {
    zones.value = await locationApi.zones(form.plantId, form.warehouseId)
  } catch {
    // 구역을 못 읽어도 계획은 세울 수 있다 — 창고 전체가 기본이다
    zones.value = []
  }
}

/* ── 대상 미리보기 ───────────────────────────────────────────────
 *
 * 조건을 바꿀 때마다 몇 건이 잡히는지 보여 준다. 저장하고 대상까지
 * 만들어 봐야 0 건인 줄 아는 것은, 쓸 수 없는 계획을 만든 뒤에야
 * 알려 주는 것이다.
 */
const preview = ref(null)
const previewing = ref(false)
let previewTimer = null

async function loadPreview() {
  if (!form.plantId || !form.warehouseId) {
    preview.value = null
    return
  }
  previewing.value = true
  try {
    preview.value = await opsApi.previewTargets({
      plantId: form.plantId,
      warehouseId: form.warehouseId,
      targetZone: form.targetZone,
      targetSkuKeyword: form.targetSkuKeyword,
    })
  } catch {
    preview.value = null
  } finally {
    previewing.value = false
  }
}

// 키워드는 글자를 칠 때마다 바뀌므로 잠깐 기다렸다 부른다.
// 안 그러면 한 단어 치는 동안 질의가 열 번 나간다.
watch(
  () => [form.plantId, form.warehouseId, form.targetZone, form.targetSkuKeyword],
  ([plantId, warehouseId], old) => {
    if (!editing.value) return
    if (plantId !== old?.[0] || warehouseId !== old?.[1]) {
      // 창고가 바뀌면 이전 창고의 구역이 남아 있으면 안 된다
      form.targetZone = ''
      loadZones()
    }
    clearTimeout(previewTimer)
    previewTimer = setTimeout(loadPreview, 350)
  },
)

/** 0 건일 때 어느 조건이 범인인지 — 서버 진단과 같은 논리를 화면에서도 */
const previewNote = computed(() => {
  const p = preview.value
  if (!p) return ''
  if (p.warehouseTotal === 0) {
    return '이 창고에 재고가 없습니다. 입고 후에 실사하세요.'
  }
  if (p.matched > 0) return ''
  if (p.byZone === 0) {
    return `이 구역에는 재고가 없습니다. 창고 전체에는 ${p.warehouseTotal} 건이 있습니다.`
  }
  if (p.bySku === 0) {
    return `이 SKU 조건에 맞는 재고가 없습니다. 창고 전체에는 ${p.warehouseTotal} 건이 있습니다.`
  }
  return `구역에 ${p.byZone} 건, SKU 조건에 ${p.bySku} 건이 있지만 둘 다 만족하는 재고가 없습니다.`
})

async function submitPlan() {
  busy.value = true
  serverError.value = ''
  try {
    const payload = {
      takeName: form.takeName,
      plantId: form.plantId,
      warehouseId: form.warehouseId,
      takeType: form.takeType,
      plannedDate: form.plannedDate,
      blindYn: form.blindYn,
      targetZone: form.targetZone || null,
      targetSkuKeyword: form.targetSkuKeyword || null,
      remark: form.remark || null,
    }
    const take = editSeq.value
      ? await opsApi.updateTake(editSeq.value, payload)
      : await opsApi.createTake(payload)
    toast.success(`${take.takeNo} — 계획을 저장했습니다. 대상을 생성하세요.`)
    editing.value = false
    await search()
    await openDetail(take)
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

/* ── 상세 · 카운트 ──────────────────────────────────────────── */

const detail = ref(null)
const detailBusy = ref(false)
const detailError = ref('')
const lineFilter = ref('')
/** lineSeq -> 입력 중인 수량. 저장 전까지 화면에만 있다. */
const draft = reactive({})
const askClose = ref(null)
const askCancel = ref(null)

async function openDetail(row) {
  detailError.value = ''
  lineFilter.value = ''
  for (const k of Object.keys(draft)) delete draft[k]
  try {
    detail.value = await opsApi.detailTake(row.takeSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

async function reloadDetail() {
  if (!detail.value) return
  const params =
    lineFilter.value === 'diff'
      ? { diffOnly: 'Y' }
      : lineFilter.value === 'uncounted'
        ? { uncountedOnly: 'Y' }
        : {}
  detail.value = await opsApi.detailTake(detail.value.takeSeq, params)
}

async function act(fn, okMessage) {
  detailBusy.value = true
  detailError.value = ''
  try {
    const result = await fn()
    if (result?.warning) toast.warn(result.warning)
    if (okMessage) toast.success(okMessage)
    await reloadDetail()
    await fetchPage()
  } catch (e) {
    detailError.value = e.message
  } finally {
    detailBusy.value = false
  }
}

const generateTargets = () =>
  act(() => opsApi.generateTargets(detail.value.takeSeq), '대상을 다시 뽑았습니다.')

/* ── 재고에서 직접 담기 (지정실사) ──────────────────────────────
 *
 * 조건으로 훑는 것과 달리 창고의 재고 목록에서 고른다. 지정실사는
 * "이것만 세라" 라서 조건으로 표현되지 않는 경우가 많고, 무엇보다
 * 목록에서 고르면 <b>없는 제품을 넣을 방법이 없다</b>.
 *
 * 고른 재고를 바로 보낸다. 담을 것이 보통 몇 건이고, 모아 두었다
 * 한꺼번에 보내면 "담았는데 왜 목록에 없지" 가 된다.
 */
const picking = ref(false)

/** 이미 담긴 재고 — 고르기 화면에서 흐리게 표시한다 */
const pickedStockSeqs = computed(() =>
  (detail.value?.lines ?? []).map((l) => l.stockSeq).filter(Boolean),
)

/**
 * 고른 재고를 대상에 담는다.
 *
 * 여러 건을 한 번에 받는다. 서버(POST /targets/pick)는 진작부터 배열을
 * 받고 있었는데 화면이 한 건씩 보내고 창까지 닫아서, 수십 건을 담으려면
 * 검색 조건을 매번 다시 넣어야 했다.
 *
 * 창은 닫지 않는다. 담은 뒤에도 같은 조건으로 더 고를 수 있어야 한다 —
 * 지정실사는 몇 번에 나눠 담는 것이 실제 작업이다.
 */
function pickTarget(picked) {
  const list = Array.isArray(picked) ? picked : [picked]
  if (!list.length) return
  act(
    () => opsApi.pickTargets(detail.value.takeSeq, list.map((s) => s.stockSeq)),
    list.length === 1
      ? `${list[0].skuId} 을(를) 대상에 담았습니다.`
      : `${list.length} 건을 대상에 담았습니다.`,
  )
}

const removeTarget = (line) =>
  act(async () => {
    await opsApi.removeTarget(detail.value.takeSeq, line.lineSeq)
  }, `${line.skuId} 을(를) 대상에서 뺐습니다.`)

const start = () =>
  act(async () => {
    await opsApi.startTake(detail.value.takeSeq)
  }, '실사를 시작했습니다. 이제 대상은 바뀌지 않습니다.')

/* ── 바코드 스캔 (INV-PG-009) ───────────────────────────────── */

/**
 * 스캔은 입력 방법일 뿐이다.
 *
 * 찍을 때마다 서버에 저장하지 않는다. 기존 수량칸(draft)에 +1 씩 쌓고,
 * 다 세면 지금까지와 같은 '수량 기록' 버튼으로 한 번에 넣는다.
 *
 * 저장을 스캔마다 하면 안 되는 이유가 있다. 수량입력은 '같은 줄에 두 번째로
 * 들어온 수량은 재계수' 로 해석하는데 — 한 줄을 정하는 판단이라 옳다 —
 * 물건 다섯 개를 찍으면 저장이 다섯 번이라 1 차 1 개 · 재계수 1 개가 되어
 * 실제로 센 다섯이 사라진다.
 */
const scanning = ref(false)
const scanValue = ref('')
const scanBox = ref(null)
const scanBusy = ref(false)

/** 지금 서 있는 빈. 빈을 찍으면 바뀌고, 물건은 이 빈의 줄로 들어간다. */
const scanLocation = ref(null)

/** 이번 스캔으로 찍은 것들 — 방금 무엇을 찍었는지 눈으로 확인한다 */
const scanLog = ref([])

function openScan() {
  scanning.value = true
  scanValue.value = ''
  scanLog.value = []
  scanLocation.value = null
  focusScan()
}

/**
 * 입력칸에 포커스를 돌려놓는다.
 *
 * 스캐너는 키보드처럼 글자를 쳐 넣으므로 포커스가 이 칸에 없으면 아무 데나
 * 입력된다. 한 번 찍을 때마다 되돌려야 한 손에 단말을 들고 작업할 수 있다.
 */
function focusScan() {
  nextTick(() => scanBox.value?.focus())
}

function logScan(kind, text) {
  scanLog.value = [{ kind, text, at: Date.now() }, ...scanLog.value].slice(0, 12)
}

async function onScan() {
  const value = scanValue.value.trim()
  scanValue.value = ''
  focusScan()
  if (!value || scanBusy.value) return

  scanBusy.value = true
  try {
    const r = await opsApi.resolveScan(
      detail.value.takeSeq,
      value,
      scanLocation.value?.locationId,
    )

    if (r.kind === 'LOCATION') {
      scanLocation.value = r
      if (!r.lineCount) {
        toast.warn(`${r.locationFullCode} 은(는) 이 실사 대상에 없는 빈입니다.`)
        logScan('warn', `${r.locationFullCode} — 대상 없음`)
      } else {
        logScan('bin', `${r.locationFullCode} (대상 ${r.lineCount}줄)`)
      }
      return
    }

    if (r.kind === 'SKU') {
      // 장부에 없던 물건이다. 막지 않고 '계획에 없던 물건' 으로 넘긴다 —
      // 실사가 잡아야 하는 가장 중요한 경우다.
      if (!r.lineSeq) {
        toast.warn(r.message)
        logScan('warn', `${r.skuId} — ${r.message}`)
        if (scanLocation.value) {
          openAddLine()
          extra.locationId = scanLocation.value.locationId
          extra.skuId = r.skuId
          extraSku.value = { skuId: r.skuId, productName: r.productName }
        }
        return
      }
      const now = (Number(draft[r.lineSeq]) || 0) + 1
      draft[r.lineSeq] = now
      logScan('sku', `${r.skuId} ${r.productName ?? ''} → ${now}개`)
      return
    }

    toast.warn(r.message)
    logScan('warn', r.message)
  } catch (e) {
    toast.error(e.message)
    logScan('warn', e.message)
  } finally {
    scanBusy.value = false
  }
}

/** 스캔으로 쌓은 줄 수 — 저장 버튼이 몇 건인지 보여 준다 */
const scanTally = computed(() =>
  Object.entries(draft)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined)
    .length,
)

/** 입력한 줄만 보낸다. 안 건드린 줄까지 보내면 1 차가 재계수로 덮인다. */
const dirtyLines = computed(() =>
  Object.entries(draft)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined)
    .map(([lineSeq, qty]) => ({ lineSeq: Number(lineSeq), qty: Number(qty) })),
)

const saveCounts = () =>
  act(async () => {
    const lines = dirtyLines.value
    const result = await opsApi.countTake(detail.value.takeSeq, lines)
    for (const k of Object.keys(draft)) delete draft[k]
    return result
  }, `${dirtyLines.value.length} 줄을 기록했습니다.`)

async function doClose() {
  askClose.value = null
  await act(() => opsApi.closeTake(detail.value.takeSeq), '마감했습니다. 차이가 재고에 반영되었습니다.')
}

async function doCancel() {
  const take = askCancel.value
  askCancel.value = null
  try {
    await opsApi.cancelTake(take.takeSeq, '실사 취소')
    toast.success(`${take.takeNo} 을(를) 취소했습니다.`)
    detail.value = null
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
  }
}

/* ── 계획에 없던 물건 ───────────────────────────────────────── */

const addingLine = ref(false)
const extra = reactive({ locationId: '', skuId: '', qty: 1, reasonCode: '', remark: '' })

/**
 * 고른 SKU 의 전체 정보.
 *
 * extra.skuId 만 두면 화면에 코드밖에 안 남아, 고른 뒤에 그것이 맞는 물건인지
 * 확인할 길이 없다. 현장에서 바코드를 보고 고르는 화면이라 제품명이 보여야 한다.
 */
const extraSku = ref(null)
const pickingExtraSku = ref(false)

/**
 * 이 실사가 세는 창고의 빈 목록.
 *
 * 다른 창고 빈을 고를 일이 없다 — 실사는 창고 하나를 세는 일이다. 전체를
 * 보여 주면 옆 창고의 같은 이름 빈(1A-01-01 은 창고마다 있다)을 고를 수 있고,
 * 그러면 없던 재고가 엉뚱한 창고에 생긴다.
 */
const extraLocations = ref([])

async function loadExtraLocations() {
  try {
    const data = await locationApi.list({
      plantId: detail.value.plantId,
      warehouseId: detail.value.warehouseId,
      useYn: 'Y',
      size: 0,
    })
    extraLocations.value = data.rows ?? data
  } catch {
    // 목록을 못 받아도 창을 열어 둔다. 고를 것이 없으면 기록 버튼이 잠긴다.
    extraLocations.value = []
  }
}

const extraLocationOptions = computed(() =>
  extraLocations.value.map((l) => ({
    value: l.locationId,
    label: l.locationType === 'NORMAL' ? l.locationId : `${l.locationId} (${l.locationType})`,
  })),
)

function openAddLine() {
  Object.assign(extra, { locationId: '', skuId: '', qty: 1, reasonCode: '', remark: '' })
  extraSku.value = null
  detailError.value = ''
  addingLine.value = true
  loadExtraLocations()
}

function pickExtraSku(sku) {
  extraSku.value = sku
  extra.skuId = sku.skuId
  pickingExtraSku.value = false
}

async function submitExtra() {
  detailBusy.value = true
  detailError.value = ''
  try {
    const { warning } = await opsApi.addTakeLine(detail.value.takeSeq, {
      locationId: extra.locationId,
      skuId: extra.skuId,
      qty: Number(extra.qty),
      reasonCode: extra.reasonCode || null,
      remark: extra.remark || null,
    })
    if (warning) toast.warn(warning)
    toast.success('장부에 없던 재고를 기록했습니다.')
    addingLine.value = false
    await reloadDetail()
  } catch (e) {
    detailError.value = e.message
  } finally {
    detailBusy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const signed = (v) => (v > 0 ? `+${nf.format(v)}` : nf.format(v))

const canCreate = computed(() => session.can('INV_COUNT', 'C'))
const createDenyReason = computed(() => session.denyReason('INV_COUNT', 'C'))
const canCount = computed(() => session.can('INV_COUNT', 'U'))
const canClose = computed(() => session.can('INV_COUNT_APPROVE', 'A'))
const closeDenyReason = computed(() => session.denyReason('INV_COUNT_APPROVE', 'A'))

const progressPct = (t) =>
  t.lineCount ? Math.round(((t.countedCount ?? 0) / t.lineCount) * 100) : 0
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">재고실사</h1>
        <p class="page-desc">
          세어 보고 장부를 맞춥니다. <strong>무엇을 셀지 먼저 정하는 것</strong>이 조정과
          다른 점입니다 — 대상을 정하지 않고 세면 안 센 것이 남았는지 알 수 없습니다.
          계획 → 대상 생성 → 시작 → 입력 → 마감 순으로 갑니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '실사 계획'"
          @click="openCreate()"
        >
          + 실사 계획
        </button>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
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
          placeholder="실사번호 / 실사명"
          @enter="search()"
        />
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="filters.warehouseId = ''; search()"
        />
        <FormField
          v-model="filters.takeStatus"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('TAKE_STATUS')"
          @change="search()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <div class="quick">
        <button
          :class="['chip', { on: filters.openOnly === 'Y' }]"
          title="계획 · 실사중"
          @click="filters.openOnly = 'Y'; search()"
        >
          진행 중
        </button>
        <button
          :class="['chip', { on: filters.openOnly !== 'Y' }]"
          @click="filters.openOnly = ''; search()"
        >
          마감 · 취소 포함
        </button>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="takeSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        :muted-when="(t) => t.takeStatus === 'CANCELED'"
        empty-text="조건에 맞는 실사가 없습니다."
        @row-click="openDetail"
      >
        <template #cell-takeType="{ value }">
          <CodeBadge group="TAKE_TYPE" :code="value" />
        </template>
        <template #cell-takeStatus="{ value }">
          <CodeBadge group="TAKE_STATUS" :code="value" />
        </template>
        <template #cell-_progress="{ row }">
          <div v-if="row.lineCount" class="prog">
            <div class="prog-bar"><span :style="{ width: progressPct(row) + '%' }"></span></div>
            <span class="small dim">
              {{ num(row.countedCount) }} / {{ num(row.lineCount) }}
              <template v-if="row.diffCount"> · 차이 {{ num(row.diffCount) }}</template>
            </span>
          </div>
          <span v-else class="small dim">대상 없음</span>
        </template>
        <template #cell-_act="{ row }">
          <button class="btn btn-sm" @click.stop="openDetail(row)">열기</button>
        </template>
      </DataTable>

      <div class="pager">
        <span class="small dim">총 {{ num(total) }}건 · {{ page }} / {{ totalPages }} 페이지</span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(1)">« 처음</button>
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">‹ 이전</button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">다음 ›</button>
          <button class="btn btn-sm" :disabled="page >= totalPages || loading" @click="goPage(totalPages)">마지막 »</button>
        </div>
      </div>
    </div>

    <!-- ── 계획 작성 ────────────────────────────────────────── -->
    <ModalDialog
      v-if="editing"
      :title="editSeq ? '실사 계획 수정' : '실사 계획'"
      @close="editing = false"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.takeName"
          label="실사명"
          class="span-2"
          required
          placeholder="9월 정기 전수실사"
        />
        <FormField
          v-model="form.plantId"
          label="플랜트"
          type="select"
          required
          empty-option="선택하세요"
          :options="hierarchy.plantOptions"
          @change="form.warehouseId = ''"
        />
        <FormField
          v-model="form.warehouseId"
          label="창고"
          type="select"
          required
          empty-option="선택하세요"
          :options="form.plantId ? hierarchy.warehouseOptionsOf(form.plantId) : []"
          :disabled="!form.plantId"
          help="실사는 창고 단위입니다. 마감 권한도 창고 단위로 나뉩니다."
        />
        <FormField
          v-model="form.takeType"
          label="실사 유형"
          type="select"
          required
          :options="codeOptions('TAKE_TYPE')"
        />
        <FormField v-model="form.plannedDate" label="계획일" type="date" required />
        <!-- 구역은 창고마다 다르고 외울 수 없다. 그 창고에 실제로 쓰인
             값만 고르게 한다 — 목록에 있으면 반드시 대상이 잡힌다. -->
        <FormField
          v-model="form.targetZone"
          label="구역"
          type="select"
          empty-option="창고 전체"
          :options="zoneOptions"
          :disabled="!form.warehouseId"
          :help="
            !form.warehouseId
              ? '창고를 먼저 고르세요.'
              : zoneOptions.length
                ? '비우면 창고 전체입니다.'
                : '이 창고는 구역을 나누지 않았습니다. 창고 전체로 돕니다.'
          "
        />
        <!--
          정확한 SKU 코드를 적는 칸이 아니다. SKU코드와 제품명에 부분일치로
          걸어 대상을 좁히는 검색어이고, 비우면 창고 전체다. 라벨이 'SKU'
          로 시작하면 PRD-24001-BK-M 을 적고 싶어지는데 그럴 필요가 없다.

          아래 미리보기가 치는 대로 몇 건이 잡히는지 보여 주므로 오타는
          0 건으로 바로 드러난다.
        -->
        <FormField
          v-model="form.targetSkuKeyword"
          label="품목 검색어"
          placeholder="예: 티셔츠, 24001, BK"
          help="정확한 코드가 아니어도 됩니다. 제품명·SKU코드에 이 글자가 들어간 것만 셉니다. 비우면 창고 전체입니다."
        />
        <FormField
          v-model="form.blindYn"
          label="블라인드 카운트"
          type="switch"
          on-label="숨김"
          off-label="보여줌"
          class="span-2"
          help="세는 사람에게 장부수량을 숨깁니다. 보여 주면 맞추려는 쪽으로 세게 되어 실사의 목적 자체가 없어집니다."
        />
        <FormField v-model="form.remark" label="비고" class="span-2" />
      </div>

      <!-- 저장 전에 몇 건이 잡히는지 보여 준다. 저장하고 대상까지 만들어
           봐야 0 건인 줄 아는 것은 쓸 수 없는 계획을 만든 뒤에야 알려
           주는 것이다. -->
      <div v-if="form.warehouseId" class="preview" :class="{ empty: preview && !preview.matched }">
        <span v-if="previewing" class="spinner"></span>
        <template v-else-if="preview">
          <span>
            이 조건으로 셀 대상은
            <strong>{{ num(preview.matched) }}</strong> 건입니다
            <span v-if="preview.matched !== preview.warehouseTotal" class="dim">
              (창고 전체 {{ num(preview.warehouseTotal) }} 건)
            </span>
          </span>
          <span v-if="previewNote" class="small">{{ previewNote }}</span>
        </template>
      </div>

      <template #footer>
        <span class="left small dim">
          대상은 저장한 뒤 '대상 생성' 으로 뽑습니다.
        </span>
        <button class="btn" :disabled="busy" @click="editing = false">취소</button>
        <button class="btn btn-primary" :disabled="!formValid" @click="submitPlan()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <!-- ── 상세 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="detail"
      :title="`${detail.takeNo} · ${detail.takeName}`"
      :subtitle="`${detail.plantName} · ${detail.warehouseName}`"
      size="wide"
      @close="detail = null"
    >
      <div v-if="detailError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ detailError }}</span>
      </div>

      <!-- 단계 표시. 지금 무엇을 해야 하는지가 한눈에 보여야 한다 -->
      <div class="steps">
        <span :class="['step', { on: detail.planned, done: !detail.planned }]">계획</span>
        <span class="sep">→</span>
        <span :class="['step', { on: detail.counting, done: detail.closed }]">실사중</span>
        <span class="sep">→</span>
        <span :class="['step', { done: detail.closed }]">마감</span>
        <CodeBadge group="TAKE_STATUS" :code="detail.takeStatus" />
        <CodeBadge group="TAKE_TYPE" :code="detail.takeType" />
        <span v-if="detail.blind && !detail.closed" class="badge badge-violet" title="세는 사람에게 장부수량을 숨깁니다">
          블라인드
        </span>
      </div>

      <div class="detail-head">
        <span>계획일 <strong>{{ detail.plannedDate }}</strong></span>
        <span>대상 <strong>{{ num(detail.lineCount) }}</strong> 줄</span>
        <span>센 줄 <strong>{{ num(detail.countedCount) }}</strong></span>
        <span v-if="!detail.blind || detail.closed">
          차이 <strong class="warn">{{ num(detail.diffCount) }}</strong>
        </span>
        <span v-if="detail.closedBy">
          마감 <strong>{{ detail.closedByName }}</strong>
        </span>
      </div>

      <div v-if="detail.blind && !detail.closed" class="alert alert-info">
        <span class="alert-icon">🙈</span>
        <span>
          블라인드 실사라 장부수량과 차이가 <strong>서버에서 아예 오지 않습니다</strong>.
          마감하면 그때 보입니다 — 그때는 더 이상 셀 것이 없어 가릴 이유도 없습니다.
        </span>
      </div>
      <div v-if="detail.hasUncounted && detail.counting" class="alert alert-warn">
        <span class="alert-icon">⚠</span>
        <span>
          아직 세지 않은 줄이
          <strong>{{ num(detail.lineCount - detail.countedCount) }}개</strong> 있습니다.
          하나라도 남아 있으면 마감할 수 없습니다 — 안 센 것을 '차이 없음' 으로 두면
          실사를 안 한 것과 같은데, 기록상으로는 한 것이 되어 더 나쁩니다.
        </span>
      </div>

      <!-- 단계별 행동 -->
      <div class="act-row">
        <template v-if="detail.planned">
          <button
            class="btn"
            :disabled="detailBusy || !canCount"
            title="계획의 구역 · SKU 조건으로 창고를 훑어 대상을 다시 뽑습니다"
            @click="generateTargets()"
          >
            대상 생성
          </button>
          <!-- 조건으로 표현되지 않는 '이것만' 을 위해. 목록에서 고르므로
               없는 제품을 넣을 방법이 없다. -->
          <button
            class="btn"
            :disabled="detailBusy || !canCount"
            title="창고의 재고 목록에서 골라 담습니다. 이미 담긴 것은 건너뜁니다."
            @click="picking = true"
          >
            재고에서 담기
          </button>
          <button class="btn" :disabled="detailBusy || !canCount" @click="openEdit(detail)">
            계획 수정
          </button>
          <button
            class="btn btn-primary"
            :disabled="detailBusy || !canCount || !detail.lineCount"
            :title="detail.lineCount ? '대상을 고정하고 세기 시작합니다' : '대상을 먼저 생성하세요'"
            @click="start()"
          >
            실사 시작
          </button>
        </template>
        <template v-else-if="detail.counting">
          <button class="btn" :disabled="detailBusy || !canCount" @click="openAddLine()">
            + 계획에 없던 물건
          </button>
          <button
            class="btn btn-primary"
            :disabled="detailBusy || !dirtyLines.length"
            @click="saveCounts()"
          >
            <span v-if="detailBusy" class="spinner"></span>
            수량 저장 ({{ dirtyLines.length }})
          </button>
          <button
            class="btn btn-danger"
            :disabled="detailBusy || !canClose || detail.hasUncounted"
            :title="closeDenyReason ?? (detail.hasUncounted ? '안 센 줄이 남아 있습니다' : '차이를 재고에 반영합니다')"
            @click="askClose = detail"
          >
            마감
          </button>
        </template>
        <button
          v-if="!detail.closed"
          class="btn btn-sm"
          :disabled="detailBusy"
          @click="askCancel = detail"
        >
          실사 취소
        </button>

        <span class="spacer"></span>
        <div class="line-filter">
          <button :class="['chip', { on: !lineFilter }]" @click="lineFilter = ''; reloadDetail()">
            전체
          </button>
          <button
            :class="['chip', { on: lineFilter === 'uncounted' }]"
            @click="lineFilter = 'uncounted'; reloadDetail()"
          >
            안 센 것
          </button>
          <button
            v-if="!detail.blind || detail.closed"
            :class="['chip', { on: lineFilter === 'diff' }]"
            @click="lineFilter = 'diff'; reloadDetail()"
          >
            차이 난 것
          </button>
        </div>
      </div>

      <!--
        바코드 스캔.

        현장은 '목록에서 줄을 찾아 숫자를 친다' 가 아니라 '빈 앞에 서서
        물건을 하나씩 찍는다' 이다. 순서가 반대라 입력칸만으로는 안 되고,
        지금 어느 빈에 서 있는지를 화면이 들고 있어야 한다.
      -->
      <div v-if="detail.counting" class="scan-bar">
        <button class="btn btn-sm" :class="{ 'btn-primary': scanning }" @click="scanning ? (scanning = false) : openScan()">
          {{ scanning ? '스캔 닫기' : '📷 바코드로 세기' }}
        </button>
        <span v-if="scanning && scanLocation" class="scan-here">
          지금 자리 <span class="code">{{ scanLocation.locationFullCode }}</span>
          <span class="small dim">대상 {{ scanLocation.lineCount }}줄</span>
        </span>
        <span v-else-if="scanning" class="small dim">빈 라벨을 먼저 찍으세요.</span>
      </div>

      <div v-if="scanning && detail.counting" class="scan-panel">
        <input
          ref="scanBox"
          v-model="scanValue"
          class="input scan-input"
          :placeholder="scanLocation ? '물건 바코드 또는 SKU 코드' : '빈 라벨 또는 빈코드'"
          :disabled="!canCount || scanBusy"
          @keyup.enter="onScan()"
        />
        <p class="small dim scan-help">
          찍은 값이 빈인지 물건인지는 서버가 가립니다. 빈을 찍으면 자리가 바뀌고,
          물건을 찍으면 그 자리의 수량이 <strong>1 씩 올라갑니다</strong>.
          라벨이 안 읽히면 코드를 직접 치고 Enter 를 누르세요.
          <strong>저장은 아래 '수량 기록' 버튼</strong>을 눌러야 됩니다 — 지금까지 찍은 것은
          {{ scanTally }} 줄입니다.
        </p>
        <ul v-if="scanLog.length" class="scan-log">
          <li v-for="s in scanLog" :key="s.at" :class="s.kind">{{ s.text }}</li>
        </ul>
      </div>

      <table class="table lines">
        <thead>
          <tr>
            <th style="width: 160px">재고주소</th>
            <th style="width: 145px">SKU</th>
            <th v-if="!detail.blind || detail.closed" style="width: 70px" class="right">장부</th>
            <th style="width: 70px" class="right">1차</th>
            <th style="width: 70px" class="right">재계수</th>
            <th v-if="!detail.blind || detail.closed" style="width: 70px" class="right">차이</th>
            <th v-if="detail.counting" style="width: 96px">입력</th>
            <th style="width: 84px" align="center">상태</th>
            <!-- 계획 중에만 뺄 수 있다. 시작하면 대상은 고정된다. -->
            <th v-if="detail.planned" style="width: 40px"></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="l in detail.lines"
            :key="l.lineSeq"
            :class="{ phantom: l.phantom, diff: l.hasDiff }"
          >
            <td>
              <span class="code">{{ l.locationFullCode }}</span>
              <span v-if="l.phantom" class="badge badge-violet" title="장부에 없던 물건">장부 밖</span>
            </td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td v-if="!detail.blind || detail.closed" class="num">{{ num(l.qtyBook) }}</td>
            <td class="num">
              <span :class="{ dim: l.qtyCounted === null }">{{ num(l.qtyCounted) }}</span>
            </td>
            <td class="num">
              <span :class="{ dim: l.qtyRecount === null }">{{ num(l.qtyRecount) }}</span>
            </td>
            <td v-if="!detail.blind || detail.closed" class="num">
              <strong v-if="l.qtyDiff !== null" :class="l.qtyDiff > 0 ? 'ok' : l.qtyDiff < 0 ? 'danger' : 'dim'">
                {{ signed(l.qtyDiff) }}
              </strong>
              <span v-else class="dim">-</span>
            </td>
            <td v-if="detail.counting">
              <input
                v-model.number="draft[l.lineSeq]"
                type="number"
                min="0"
                class="input"
                :placeholder="l.counted ? '재계수' : '센 수량'"
                :disabled="!canCount"
              />
            </td>
            <td align="center">
              <CodeBadge group="TAKE_LINE_STATUS" :code="l.lineStatus" />
            </td>
            <td v-if="detail.planned">
              <button
                class="btn btn-sm btn-danger"
                :disabled="detailBusy || !canCount"
                title="이 줄을 대상에서 뺍니다"
                @click="removeTarget(l)"
              >
                ×
              </button>
            </td>
          </tr>
          <tr v-if="!detail.lines.length">
            <td colspan="9">
              <div class="table-empty">
                <span class="table-empty-icon">🗂</span>
                {{
                  detail.planned
                    ? "대상이 없습니다. '대상 생성' 으로 조건에 맞는 재고를 훑거나, '재고에서 담기' 로 직접 고르세요."
                    : '조건에 맞는 줄이 없습니다.'
                }}
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          {{ detail.closed
            ? '마감되었습니다. 차이는 재고에 반영되었고 되돌릴 수 없습니다.'
            : '마감하기 전에는 재고가 바뀌지 않습니다.' }}
        </span>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>

    <!--
      장부에 없는 물건이라 재고에서 고를 수 없다. SkuPicker 는 재고가 없어도
      고를 수 있어서 이 경우에 맞다 — StockPicker 는 재고 행을 고르는 것이라
      여기서는 아무것도 안 나온다.
    -->
    <SkuPicker
      v-if="pickingExtraSku"
      title="장부에 없던 물건의 SKU"
      @pick="pickExtraSku"
      @close="pickingExtraSku = false"
    />

    <!-- ── 계획에 없던 물건 ─────────────────────────────────── -->
    <ModalDialog v-if="addingLine" title="계획에 없던 물건" @close="addingLine = false">
      <p class="small">
        장부에 없는 물건이 창고에서 나온 경우입니다. 대상은 장부를 보고 뽑으므로 이런
        물건은 대상에 없습니다 — <strong>실사가 잡아야 하는 가장 중요한 경우</strong>입니다.
        재고 0 으로도 잡혀 있지 않으면 주문을 받지 못하니, 팔 수 있는 물건이 창고에서
        잠자고 있다는 뜻입니다.
      </p>
      <div v-if="detailError" class="alert alert-danger mb-2 mt-2">
        <span class="alert-icon">⛔</span><span>{{ detailError }}</span>
      </div>
      <div class="form-grid mt-2">
        <!--
          빈은 이 실사가 세는 창고의 것만 고른다. 손으로 적게 두면 옆 창고의
          같은 이름 빈을 적어도 통과하고, 없던 재고가 엉뚱한 창고에 생긴다.
        -->
        <FormField
          v-model="extra.locationId"
          label="빈"
          type="select"
          required
          empty-option="고르세요"
          :options="extraLocationOptions"
          :hint="
            extraLocations.length
              ? `${detail.warehouseName} 의 빈 ${extraLocations.length} 곳`
              : '빈 목록을 받지 못했습니다.'
          "
        />

        <!--
          SKU 는 목록에서 고른다. PRD-24001-BK-M 같은 코드를 현장에서 손으로
          적게 하면 한 글자 틀린 코드가 그대로 들어가고, 마감할 때 '없는 SKU'
          로 막힌다 — 그때는 이미 현품을 내려놓고 자리를 떠난 뒤다.
        -->
        <div class="picked-sku">
          <span class="ps-label">SKU<span class="req">*</span></span>
          <span v-if="extraSku" class="ps-value">
            <span class="code">{{ extraSku.skuId }}</span>
            <span class="small dim"> {{ extraSku.productName }}</span>
          </span>
          <span v-else class="ps-value dim">아직 고르지 않았습니다</span>
          <button class="btn btn-sm" @click="pickingExtraSku = true">
            {{ extraSku ? '다시 고르기' : 'SKU 고르기' }}
          </button>
        </div>

        <FormField v-model.number="extra.qty" label="실사수량" type="number" required />
        <FormField
          v-model="extra.reasonCode"
          label="사유"
          type="select"
          empty-option="선택하세요"
          :options="codeOptions('REASON_ADJUST')"
        />
        <FormField v-model="extra.remark" label="비고" class="span-2" />
      </div>
      <template #footer>
        <span class="left small dim">마감하면 재고 행이 새로 만들어집니다.</span>
        <button class="btn" :disabled="detailBusy" @click="addingLine = false">취소</button>
        <button
          class="btn btn-primary"
          :disabled="detailBusy || !extra.locationId || !extra.skuId || !extra.qty"
          @click="submitExtra()"
        >
          <span v-if="detailBusy" class="spinner"></span>
          기록
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askClose"
      title="실사 마감"
      :message="`${askClose.takeNo} 을(를) 마감합니까?`"
      detail="차이가 있는 줄이 재고에 반영되고, 장부에 없던 물건은 재고로 만들어집니다. 마감은 되돌릴 수 없습니다 — 다시 맞추려면 조정을 올려야 합니다."
      confirm-label="마감"
      danger
      :busy="detailBusy"
      @cancel="askClose = null"
      @confirm="doClose()"
    />

    <ConfirmDialog
      v-if="askCancel"
      title="실사 취소"
      :message="`${askCancel.takeNo} 을(를) 취소합니까?`"
      detail="세지 않고 접습니다. 대상과 지금까지 센 수량은 남습니다 — 왜 접었는지의 근거입니다. 재고는 바뀌지 않습니다."
      confirm-label="취소"
      danger
      @cancel="askCancel = null"
      @confirm="doCancel()"
    />

    <!-- 창고의 재고에서 직접 고른다. 목록에서 고르므로 없는 제품을 넣을
         방법이 없고, 제품명을 외울 필요도 없다. -->
    <!--
      multi 를 켠다. 실사 대상은 수십~수백 건을 담는 일이라 한 건씩 창이
      닫히면 검색 조건을 매번 다시 넣어야 한다. 조정 · 이동 · 판매불가는
      줄마다 사유와 수량을 적어야 해서 지금처럼 한 건씩이 맞다.
    -->
    <StockPicker
      v-if="picking && detail"
      title="실사 대상에 담을 재고"
      :plant-id="detail.plantId"
      :warehouse-id="detail.warehouseId"
      lock-warehouse
      multi
      :picked-seqs="pickedStockSeqs"
      @pick="pickTarget"
      @close="picking = false"
    />
  </div>
</template>

<style scoped>
/* 바코드 스캔 */
.scan-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 0 6px;
  flex-wrap: wrap;
}
.scan-here {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.scan-panel {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
  padding: 12px;
  margin-bottom: 10px;
}
/* 스캐너는 글자를 빠르게 쳐 넣는다. 칸이 크고 글자가 커야 눈으로 확인된다. */
.scan-input {
  width: 100%;
  font-size: 18px;
  font-family: var(--font-mono, monospace);
  padding: 10px 12px;
}
.scan-help {
  margin: 8px 0 0;
  line-height: 1.6;
}
.scan-log {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.7;
}
.scan-log .bin {
  color: var(--primary);
}
.scan-log .sku {
  color: var(--text);
}
.scan-log .warn {
  color: var(--warn);
}

/* SKU 고르기 한 줄 — FormField 가 아니라서 라벨 모양을 맞춰 준다 */
.picked-sku {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.picked-sku .ps-label {
  font-size: 12px;
  color: var(--text-2);
  min-width: 58px;
}
.picked-sku .req {
  color: var(--danger);
  margin-left: 2px;
}
.picked-sku .ps-value {
  flex: 1;
  min-width: 150px;
}

/* 대상 미리보기 — 저장 전에 몇 건이 잡히는지 */
.preview {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  font-size: 13px;
}
.preview.empty {
  border-color: var(--c-amber, #b45309);
  color: var(--c-amber, #b45309);
}
.quick,
.line-filter {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.quick {
  padding: 0 14px 10px;
}
.chip {
  padding: 5px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
}
.chip.on {
  background: var(--c-blue, #2563eb);
  border-color: var(--c-blue, #2563eb);
  color: #fff;
}
.prog {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.prog-bar {
  height: 5px;
  border-radius: 3px;
  background: var(--line, #e5e7eb);
  overflow: hidden;
}
.prog-bar span {
  display: block;
  height: 100%;
  background: var(--c-blue, #2563eb);
}
.steps {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.step {
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid var(--line, #e5e7eb);
  font-size: 12px;
  color: var(--fg-dim, #6b7280);
}
.step.on {
  border-color: var(--c-blue, #2563eb);
  color: var(--c-blue, #2563eb);
  font-weight: 600;
}
.step.done {
  background: var(--bg-soft, #f8fafc);
}
.sep {
  color: var(--fg-dim, #6b7280);
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 13px;
  margin-bottom: 8px;
}
.act-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 12px 0 8px;
  padding-top: 10px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.spacer {
  flex: 1;
}
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.lines .input {
  min-height: 28px;
  padding: 3px 6px;
  font-size: 13px;
  width: 100%;
}
.phantom td {
  background: color-mix(in srgb, var(--c-violet, #8b5cf6) 8%, transparent);
}
.diff td {
  background: color-mix(in srgb, var(--c-amber, #f59e0b) 8%, transparent);
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.ok {
  color: var(--c-green, #16a34a);
}
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
