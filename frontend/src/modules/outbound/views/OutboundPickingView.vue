<script setup>
/**
 * 피킹 (OUT-PG-004) · 작업자 배정 (OUT-PG-003).
 *
 * 창고에서 물건을 집는 화면이다. 스캐너는 키보드처럼 문자열을 치고 엔터를
 * 누른다 — 드라이버도 설정도 없고, 화면이 할 일은 <b>포커스를 옮겨 주는
 * 것</b>뿐이다. 손을 안 떼고 빈 → SKU → 빈 → SKU 로 이어 찍는다.
 *
 * 스캔한 문자열은 서버에 묻지 않고 <b>집을 목록과 맞춰 본다.</b> 목록이
 * 이미 손에 있고 한 지시의 칸은 몇 개뿐이라, 스캔마다 왕복하면 그 지연이
 * 스캔 속도에 그대로 걸린다.
 *
 * 여기서 재고 수량은 안 바뀐다. 물건을 빈에서 꺼내 카트에 옮겼을 뿐이고,
 * 보유수량이 줄어드는 것은 출고확정(E섹터)이다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import * as outboundApi from '@/api/outbound.js'
import * as userApi from '@/api/user.js'
import { codeOptions } from '@/api/codes.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const session = useSessionStore()
const hierarchy = useHierarchyStore()
const toast = useToastStore()

const canPick = computed(() => session.can('OUT_PICK', 'C'))
const canAssign = computed(() => session.can('OUT_ASSIGN', 'U'))
const canShort = computed(() => session.can('OUT_SHORTAGE', 'C'))

/* ── 지시 목록 ──────────────────────────────────────────────── */

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', plantId: '', mineOnly: 'Y' })

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.list({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      openOnly: 'Y',
      size: 100,
      sortBy: 'instructedAt',
      sortDir: 'asc',
    })
    // '내가 맡은 것' 은 화면에서 거른다. 서버 조건을 하나 더 만드는 것보다,
    // 어차피 진행중 지시는 많지 않고 화면에서 바로 토글하는 편이 빠르다.
    rows.value = (data.rows ?? []).filter(
      (o) => filters.mineOnly !== 'Y' || o.assignedTo === session.currentUserId,
    )
  } catch (e) {
    loadError.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await fetchPage()
  try {
    users.value = (await userApi.list({ useYn: 'Y', size: 0 })).rows
  } catch {
    users.value = []
  }
})

/* ── 배정 (OUT-PG-003) ──────────────────────────────────────── */

const users = ref([])
const assigning = ref(null)
const assignTo = ref('')

function openAssign(row) {
  assignTo.value = row.assignedTo ?? ''
  assigning.value = row
}

async function doAssign(userId) {
  try {
    await outboundApi.assign([assigning.value.outboundSeq], userId)
    toast.success(userId ? '맡겼습니다.' : '배정을 풀었습니다.')
    assigning.value = null
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  }
}

/** 아무도 안 맡은 지시를 내가 잡는다. 작업자가 스스로 집어 가는 동선이다 */
async function takeIt(row) {
  try {
    await outboundApi.assign([row.outboundSeq], session.currentUserId)
    toast.success(`${row.outboundNo} 을(를) 맡았습니다.`)
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  }
}

/* ── 집기 (OUT-PG-004) ──────────────────────────────────────── */

const target = ref(null)
const tasks = ref([])
const scan = reactive({ locationScan: '', skuScan: '', qty: 1 })
const locInput = ref(null)
const skuInput = ref(null)
const qtyInput = ref(null)
const busy = ref(false)
const scanError = ref('')

async function openPicking(row) {
  target.value = row
  await loadTasks()
  Object.assign(scan, { locationScan: '', skuScan: '', qty: 1 })
  scanError.value = ''
  await nextTick()
  locInput.value?.focus?.()
}

async function loadTasks() {
  tasks.value = await outboundApi.pickTasks(target.value.outboundSeq)
}

/** 아직 집을 것이 남은 칸만 */
const openTasks = computed(() => tasks.value.filter((t) => t.toPickQty > 0))

const doneQty = computed(() => tasks.value.reduce((s, t) => s + (t.pickedQty ?? 0), 0))
const leftQty = computed(() => openTasks.value.reduce((s, t) => s + t.toPickQty, 0))

/**
 * 스캔한 문자열이 가리키는 칸.
 *
 * 빈과 SKU 를 둘 다 찍어야 정해진다. 같은 빈에 여러 SKU 가 있고 같은 SKU 가
 * 여러 빈에 있어서, 하나만으로는 어느 칸인지 모른다.
 */
const matched = computed(() => {
  const loc = scan.locationScan.trim()
  const sku = scan.skuScan.trim()
  if (!loc || !sku) return null
  return (
    openTasks.value.find(
      (t) =>
        (t.locationBarcode === loc || t.locationId === loc) &&
        (t.skuBarcode === sku || t.skuId === sku),
    ) ?? null
  )
})

/** 빈만 찍었을 때 그 자리에서 집을 것 — 다음에 무엇을 찍을지 보여 준다 */
const atLocation = computed(() => {
  const loc = scan.locationScan.trim()
  if (!loc) return []
  return openTasks.value.filter((t) => t.locationBarcode === loc || t.locationId === loc)
})

/** 빈을 찍으면 SKU 칸으로 — 스캐너는 손을 안 뗀다 */
async function onLocationScanned() {
  scanError.value = ''
  if (atLocation.value.length === 0 && scan.locationScan.trim()) {
    scanError.value = `${scan.locationScan} 에서 집을 것이 없습니다. 빈을 다시 확인하세요.`
    scan.locationScan = ''
    await nextTick()
    locInput.value?.focus?.()
    return
  }
  await nextTick()
  skuInput.value?.focus?.()
}

/**
 * 태그 대신 눌러서 고르기.
 *
 * 태그가 찢어지거나 안 읽히는 일이 실제로 있다. 그때 작업을 멈추게 할 수는
 * 없어서 남기는 길이다 — 스캔이 기본이고 이것은 예외라, 누르면 SKU 칸이
 * 채워져 스캔과 같은 자리로 들어간다.
 */
async function chooseItem(task) {
  scan.skuScan = task.skuBarcode ?? task.skuId
  await onSkuScanned()
}

/** SKU 를 찍으면 수량 칸으로. 남은 수량을 미리 채워 엔터만 치면 되게 한다 */
async function onSkuScanned() {
  scanError.value = ''
  const m = matched.value
  if (!m) {
    scanError.value = scan.skuScan.trim()
      ? `${scan.locationScan} 에 ${scan.skuScan} 은(는) 집을 것이 없습니다.`
      : ''
    scan.skuScan = ''
    await nextTick()
    skuInput.value?.focus?.()
    return
  }
  scan.qty = m.toPickQty
  await nextTick()
  qtyInput.value?.select?.()
}

const qtyError = computed(() => {
  const m = matched.value
  if (!m) return ''
  const q = Number(scan.qty)
  if (!q || q < 1) return '1 이상이어야 합니다.'
  if (q > m.toPickQty) return `이 자리에서 집을 것은 ${m.toPickQty} 개입니다.`
  return ''
})

const canSubmit = computed(() => matched.value && !qtyError.value && !busy.value && canPick.value)

async function submitPick() {
  if (!canSubmit.value) return
  busy.value = true
  scanError.value = ''
  const m = matched.value
  try {
    const out = await outboundApi.pick(target.value.outboundSeq, {
      lineSeq: m.lineSeq,
      stockSeq: m.stockSeq,
      allocSeq: m.allocSeq,
      qty: Number(scan.qty),
    })
    toast.success(`${m.skuId} ${scan.qty} 개`)
    target.value = out
    await loadTasks()
    // 다음 칸으로. 같은 빈에 남은 것이 있으면 빈은 그대로 두고 SKU 만
    // 비운다 — 작업자가 그 자리에 서 있기 때문이다.
    const sameBin = openTasks.value.some(
      (t) => t.locationBarcode === scan.locationScan.trim() || t.locationId === scan.locationScan.trim(),
    )
    scan.skuScan = ''
    scan.qty = 1
    if (!sameBin) scan.locationScan = ''
    await nextTick()
    ;(sameBin ? skuInput : locInput).value?.focus?.()
    await fetchPage()
  } catch (e) {
    scanError.value = e.message
    scan.skuScan = ''
    await nextTick()
    skuInput.value?.focus?.()
  } finally {
    busy.value = false
  }
}

/* ── 결품 (OUT-PG-005) ──────────────────────────────────────── */

const shorting = ref(null)
const shortForm = reactive({ qty: 1, reasonCode: '', remark: '' })

function openShortage(task) {
  shortForm.qty = task.toPickQty
  shortForm.reasonCode = ''
  shortForm.remark = ''
  shorting.value = task
}

async function doShortage() {
  busy.value = true
  try {
    const out = await outboundApi.shortage(target.value.outboundSeq, {
      lineSeq: shorting.value.lineSeq,
      qty: Number(shortForm.qty),
      reasonCode: shortForm.reasonCode,
      remark: shortForm.remark || null,
    })
    toast.warn(`${shorting.value.skuId} ${shortForm.qty} 개를 결품으로 두었습니다.`)
    target.value = out
    shorting.value = null
    await loadTasks()
    await fetchPage()
    await nextTick()
    locInput.value?.focus?.()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* ── 되돌리기 ───────────────────────────────────────────────── */

async function undo(task, qty) {
  try {
    const out = await outboundApi.pick(target.value.outboundSeq, {
      lineSeq: task.lineSeq,
      stockSeq: task.stockSeq,
      allocSeq: task.allocSeq,
      qty: -qty,
    })
    toast.success(`${task.skuId} ${qty} 개를 되돌렸습니다.`)
    target.value = out
    await loadTasks()
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const columns = [
  { key: 'outboundNo', label: '지시번호', width: '170px', cls: 'code' },
  { key: 'orderNo', label: '주문', width: '160px' },
  { key: 'receiverName', label: '수령인', width: '84px' },
  { key: 'plantName', label: '센터', width: '104px' },
  { key: 'totalInstructedQty', label: '지시 / 집음', width: '104px', align: 'right' },
  { key: 'assignedToName', label: '담당', width: '110px' },
  { key: 'outboundStatus', label: '상태', width: '90px', align: 'center' },
  { key: '_act', label: '', width: '96px', align: 'right' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">피킹</h1>
        <p class="page-desc">
          창고에서 물건을 집습니다. <strong>빈 → SKU → 수량</strong> 순으로 찍으면
          포커스가 따라갑니다 — 손을 안 떼고 이어 찍으세요. 여기서
          <strong>재고는 줄지 않습니다</strong>. 빈에서 꺼내 카트에 옮겼을 뿐입니다.
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색어"
        placeholder="지시번호 / 주문번호 / 수령인"
        @enter="fetchPage()"
      />
      <FormField
        v-model="filters.plantId"
        label="센터"
        type="select"
        empty-option="전체"
        :options="hierarchy.plantOptions"
        @change="fetchPage()"
      />
      <FormField
        v-model="filters.mineOnly"
        label="범위"
        type="select"
        empty-option="전체 지시"
        :options="[{ value: 'Y', label: '내가 맡은 것' }]"
        @change="fetchPage()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="fetchPage()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="outboundSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      empty-text="집을 지시가 없습니다. '전체 지시' 로 바꿔 보세요."
      @row-click="openPicking"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.singlePack" class="small dim">단포</div>
      </template>

      <template #cell-totalInstructedQty="{ row, value }">
        <strong>{{ num(value) }}</strong>
        <span class="dim"> / </span>
        <span :class="row.totalPickedQty ? 'ok' : 'dim'">{{ num(row.totalPickedQty) }}</span>
        <div v-if="row.totalShortageQty > 0" class="small warn">결품 {{ num(row.totalShortageQty) }}</div>
      </template>

      <template #cell-assignedToName="{ row, value }">
        <span v-if="value">{{ value }}</span>
        <span v-else class="small dim">아무도 안 맡음</span>
      </template>

      <template #cell-outboundStatus="{ value }">
        <CodeBadge group="OUTBOUND_STATUS" :code="value" />
      </template>

      <template #cell-_act="{ row }">
        <button
          v-if="!row.assigned"
          class="btn btn-sm"
          :disabled="!canAssign"
          title="내가 맡습니다"
          @click.stop="takeIt(row)"
        >
          맡기
        </button>
        <button
          v-else
          class="btn btn-sm"
          :disabled="!canAssign"
          title="담당을 바꿉니다"
          @click.stop="openAssign(row)"
        >
          담당변경
        </button>
      </template>
    </DataTable>

    <!-- ── 집기 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      title="집기"
      :subtitle="`${target.outboundNo} · ${target.receiverName}`"
      size="wide"
      @close="target = null"
    >
      <div class="sum">
        <span>지시 <strong>{{ num(target.totalInstructedQty) }}</strong></span>
        <span class="ok">집음 <strong>{{ num(doneQty) }}</strong></span>
        <span v-if="target.totalShortageQty > 0" class="warn">
          결품 <strong>{{ num(target.totalShortageQty) }}</strong>
        </span>
        <span :class="leftQty ? 'danger' : 'ok'">남음 <strong>{{ num(leftQty) }}</strong></span>
        <CodeBadge group="OUTBOUND_STATUS" :code="target.outboundStatus" />
      </div>

      <div v-if="scanError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ scanError }}</span>
      </div>

      <div v-if="!openTasks.length" class="alert alert-ok mb-2">
        <span class="alert-icon">✅</span>
        <span>이 지시는 다 집었습니다. 검수·패킹으로 넘어갑니다 (4차 C섹터).</span>
      </div>

      <!--
        빈 → SKU → 수량. 포커스가 따라간다.

        수량을 미리 채워 두어 엔터만 치면 끝난다 — 대부분은 그 자리에 있는
        것을 다 집기 때문이다.
      -->
      <div v-else class="scan-grid">
        <FormField
          ref="locInput"
          v-model="scan.locationScan"
          label="빈 스캔"
          mono
          placeholder="빈 라벨을 찍으세요"
          :help="atLocation.length ? `집을 것 ${atLocation.length} 가지` : '빈 라벨부터 찍습니다'"
          @enter="onLocationScanned()"
        />
        <FormField
          ref="skuInput"
          v-model="scan.skuScan"
          label="SKU 스캔"
          mono
          placeholder="상품 태그를 찍으세요"
          :disabled="!scan.locationScan"
          :help="matched ? `${matched.productName}` : '빈을 먼저 찍으세요'"
          @enter="onSkuScanned()"
        />
        <FormField
          ref="qtyInput"
          v-model="scan.qty"
          label="수량"
          type="number"
          :disabled="!matched"
          :error="qtyError"
          :help="matched ? `이 자리에서 ${matched.toPickQty} 개` : ''"
          @enter="submitPick()"
        />
        <button class="btn btn-primary scan-submit" :disabled="!canSubmit" @click="submitPick()">
          <span v-if="busy" class="spinner"></span>
          집었다
        </button>
      </div>

        <!--
          빈을 찍으면 그 자리에서 집을 것을 크게 보여 준다.

          SKU 스캔을 없애지는 않는다. 두 스캔이 다른 것을 확인하기 때문이다 —
          빈은 '맞는 자리에 왔나', SKU 는 '맞는 물건을 집었나'. 빈만 찍고
          자동으로 채우면 옆 칸 물건을 집어도 전산은 맞다고 한다. 같은 옷의
          다른 사이즈가 나란히 있는 것이 의류 창고의 기본이라, 그 확인이
          오배송을 막는 마지막 자리다.

          대신 <b>무엇을 집을지 찾느라 표를 훑는 일</b>은 없앤다. 하나뿐이면
          그것만 띄우고, 여럿이면 나란히 띄운다.
        -->
        <div v-if="atLocation.length" class="at-loc">
          <div class="at-loc-head">
            <span class="code">{{ scan.locationScan }}</span> 에서 집을 것
            <span v-if="atLocation.length > 1" class="dim">
              · {{ atLocation.length }} 가지 — 태그를 찍어 고르세요
            </span>
            <span v-else class="dim"> · 이것을 집고 태그를 찍으세요</span>
          </div>
          <div class="at-loc-items">
            <div
              v-for="t in atLocation"
              :key="`${t.lineSeq}-${t.stockSeq}`"
              class="at-item"
              :class="{ hit: matched && matched.stockSeq === t.stockSeq && matched.lineSeq === t.lineSeq }"
            >
              <div class="at-qty">{{ num(t.toPickQty) }}<span class="dim">개</span></div>
              <div class="at-body">
                <div class="code">{{ t.skuId }}</div>
                <div class="at-name">{{ t.productName }}</div>
                <div class="small dim">{{ t.colorCode }} / {{ t.sizeCode }}</div>
              </div>
              <!--
                태그가 찢어지거나 안 읽히는 일이 실제로 있다. 그때 작업을
                멈추게 할 수는 없어서 누르는 길을 남기되, 스캔이 기본임이
                보이도록 작게 둔다.
              -->
              <button class="btn btn-sm at-pick" :disabled="!canPick" @click="chooseItem(t)">
                태그 대신 고르기
              </button>
            </div>
          </div>
        </div>

      <!-- 집을 목록 전체. 다 집은 칸까지 포함해 되돌릴 수 있게 둔다 -->
      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 120px">빈</th>
            <th style="width: 160px">SKU</th>
            <th style="width: 170px">제품</th>
            <th style="width: 70px" class="right">잡음</th>
            <th style="width: 70px" class="right">집음</th>
            <th style="width: 70px" class="right">남음</th>
            <th style="width: 130px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="`${t.lineSeq}-${t.stockSeq}`" :class="{ done: t.toPickQty <= 0 }">
            <td class="code">{{ t.locationId }}</td>
            <td>
              <span class="code">{{ t.skuId }}</span>
              <div class="small dim">{{ t.colorCode }} / {{ t.sizeCode }}</div>
            </td>
            <td class="small">{{ t.productName }}</td>
            <td class="right">{{ num(t.allocQty) }}</td>
            <td class="right" :class="t.pickedQty ? 'ok' : 'dim'">{{ num(t.pickedQty) }}</td>
            <td class="right" :class="t.toPickQty > 0 ? 'danger' : 'dim'">{{ num(t.toPickQty) }}</td>
            <td class="right">
              <button
                v-if="t.toPickQty > 0"
                class="btn btn-sm"
                :disabled="!canShort"
                title="집으러 갔는데 없습니다"
                @click="openShortage(t)"
              >
                없음
              </button>
              <button
                v-if="t.pickedQty > 0"
                class="btn btn-sm"
                :disabled="!canPick"
                title="집은 것을 되돌립니다"
                @click="undo(t, t.pickedQty)"
              >
                되돌리기
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          재고는 출고확정에서 줄어듭니다. 지금은 카트에 옮긴 것까지입니다.
        </span>
        <button class="btn" @click="target = null">닫기</button>
      </template>
    </ModalDialog>

    <!-- ── 결품 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="shorting"
      title="집으러 갔는데 없습니다"
      :subtitle="`${shorting.locationId} · ${shorting.skuId}`"
      @close="shorting = null"
    >
      <p class="small">
        전산에는 있는데 <strong>실물이 없는</strong> 경우입니다. 재고가 틀렸다는 뜻이라
        사유가 남아야 나중에 추적할 수 있습니다.
      </p>
      <p class="small dim">
        지시수량은 줄이지 않습니다 — 지시 {{ num(shorting.lineInstructedQty) }} = 집음 +
        결품으로 남겨, 몇 개를 집으라고 했었는지를 지우지 않습니다.
        <strong>잡아 둔 재고도 풀지 않습니다</strong>: 풀면 판매가능이 늘어 다음 주문이 또
        같은 자리를 잡습니다. 재고를 맞추는 것은 실사가 할 일입니다.
      </p>
      <div class="form-grid mt-2">
        <FormField v-model="shortForm.qty" label="모자란 수량" type="number" required />
        <FormField
          v-model="shortForm.reasonCode"
          label="결품 사유"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('REASON_PICK_SHORT')"
        />
        <FormField v-model="shortForm.remark" label="설명" placeholder="본 대로 적으세요" />
      </div>
      <template #footer>
        <button class="btn" :disabled="busy" @click="shorting = null">닫기</button>
        <button
          class="btn btn-danger"
          :disabled="!shortForm.reasonCode || busy"
          @click="doShortage()"
        >
          <span v-if="busy" class="spinner"></span>
          결품 처리
        </button>
      </template>
    </ModalDialog>

    <!-- ── 담당 변경 ────────────────────────────────────────── -->
    <ModalDialog
      v-if="assigning"
      title="담당 변경"
      :subtitle="assigning.outboundNo"
      @close="assigning = null"
    >
      <p class="small">
        맡은 사람이 자리를 비우면 다시 나눠 줍니다. 이미 집은 것은 그대로 남습니다.
      </p>
      <FormField
        v-model="assignTo"
        label="맡을 사람"
        type="select"
        empty-option="배정 풀기"
        :options="users.map((u) => ({ value: u.userId, label: `${u.userName} (${u.userId})` }))"
      />
      <template #footer>
        <button class="btn" @click="assigning = null">닫기</button>
        <button class="btn btn-primary" @click="doAssign(assignTo)">
          {{ assignTo ? '맡기기' : '배정 풀기' }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.sum {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 6px;
  background: var(--bg-2, #f6f7f9);
}
/* 빈 · SKU · 수량 · 버튼이 한 줄에. 좁으면 접힌다 */
.scan-grid {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 0.7fr auto;
  gap: 10px;
  align-items: end;
}
@media (max-width: 720px) {
  .scan-grid {
    grid-template-columns: 1fr;
  }
}
/* 빈을 찍으면 그 자리에서 집을 것 — 표를 훑지 않게 크게 */
.at-loc {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid var(--c-blue, #2563eb);
  background: var(--bg-2, #f6f7f9);
}
.at-loc-head {
  margin-bottom: 8px;
}
.at-loc-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.at-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid var(--line, #e5e7eb);
  background: var(--bg-1, #fff);
}
/* 태그를 찍어 맞은 칸 */
.at-item.hit {
  border-color: var(--c-green, #16a34a);
  box-shadow: 0 0 0 2px rgba(22, 163, 74, 0.18);
}
.at-qty {
  font-size: 22px;
  font-weight: 700;
  min-width: 52px;
  text-align: right;
}
.at-qty .dim {
  font-size: 12px;
  font-weight: 400;
  margin-left: 2px;
}
.at-name {
  font-size: 13px;
}
.at-pick {
  align-self: flex-end;
}
.scan-submit {
  height: 38px;
}
.lines th,
.lines td {
  vertical-align: top;
}
/* 다 집은 칸은 흐리게 — 남은 것이 눈에 먼저 들어와야 한다 */
.done {
  opacity: 0.55;
}
.right {
  text-align: right;
}
.mt-2 {
  margin-top: 10px;
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
