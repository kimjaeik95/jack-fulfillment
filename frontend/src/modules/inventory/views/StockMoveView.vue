<script setup>
/**
 * 로케이션간 재고이동 (INV-PG-011).
 *
 * 같은 센터 안에서 물건을 다른 빈으로 옮긴다. 총량은 그대로고 어디 있는지만
 * 바뀐다 — 그래서 승인을 거치지 않는다. 승인을 붙이면 현장이 물건을 옮겨
 * 놓고 시스템은 옮기지 못한 상태로 몇 시간을 보내게 되고, 그 사이 나가는
 * 피킹 지시가 전부 틀린 자리를 가리킨다.
 *
 * 판매불가 수량을 따로 입력받는 이유가 이 화면에서 가장 설명이 필요한
 * 부분이다. 판매불가는 보유의 부분집합이라, 10 개를 옮기는데 그중 3 개가
 * 불량이면 두 수량이 함께 움직여야 한다. 보유만 옮기면 출발지에는 있지도
 * 않은 물건 3 개가 불량으로 남고 도착지의 불량 3 개는 정상으로 둔갑한다.
 *
 * 할당된 수량은 옮길 수 없다. 피킹 지시가 이미 출발지를 가리키고 있어서,
 * 옮기면 작업자가 빈 자리에서 물건을 찾게 된다.
 */
import { computed, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as locationApi from '@/api/location.js'
import * as opsApi from '@/api/stockOps.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import StockPicker from '../components/StockPicker.vue'

const session = useSessionStore()
const toast = useToastStore()

const picking = ref(false)
const stock = ref(null)
const busy = ref(false)
const serverError = ref('')
const done = ref(null)

const toLocationSeq = ref('')
const qty = ref(1)
const unsellableQty = ref(0)
const reasonCode = ref('')
const remark = ref('')

/**
 * 도착 후보 — 같은 센터의 쓰는 빈.
 *
 * 창고는 넘어갈 수 있게 둔다. 정상창고에서 불량창고로 보내는 것이
 * 판매불가 처리의 실제 마무리라, 막으면 불량품을 정상 자리에 둔 채로
 * 두게 된다.
 */
const destinations = ref([])
const loadingDest = ref(false)

async function loadDestinations() {
  if (!stock.value) {
    destinations.value = []
    return
  }
  loadingDest.value = true
  try {
    const data = await locationApi.list({ plantId: stock.value.plantId, useYn: 'Y', size: 0 })
    destinations.value = data.rows.filter((l) => l.locationSeq !== stock.value.locationSeq)
  } catch (e) {
    destinations.value = []
    serverError.value = e.message
  } finally {
    loadingDest.value = false
  }
}

const destOptions = computed(() =>
  destinations.value.map((l) => ({
    value: l.locationSeq,
    // 창고를 라벨에 넣는다. 빈코드는 창고 안에서만 유일해서 코드만
    // 보여 주면 어느 창고 것인지 알 수 없다.
    label: `${l.warehouseId} · ${l.locationId}${l.zoneCode ? ` (${l.zoneCode})` : ''}`,
  })),
)

const destination = computed(
  () => destinations.value.find((l) => String(l.locationSeq) === String(toLocationSeq.value)) ?? null,
)

/** 창고를 넘어가나 — 넘어가면 그 사실을 알린다 */
const crossesWarehouse = computed(
  () => destination.value && stock.value && destination.value.warehouseId !== stock.value.warehouseId,
)

async function choose(row) {
  stock.value = row
  picking.value = false
  serverError.value = ''
  done.value = null
  toLocationSeq.value = ''
  qty.value = 1
  unsellableQty.value = 0
  await loadDestinations()
}

/**
 * 고른 재고와 입력을 비운다.
 *
 * 결과(done)는 건드리지 않는다. 옮기고 나서 이 함수를 부르는데, 여기서
 * 결과까지 지우면 방금 옮긴 내용을 보여 줄 창이 열리자마자 닫힌다.
 */
function reset() {
  stock.value = null
  destinations.value = []
  toLocationSeq.value = ''
  qty.value = 1
  unsellableQty.value = 0
  serverError.value = ''
}

/** 화면을 처음 상태로 — 결과 창까지 닫는다 */
function clearAll() {
  reset()
  done.value = null
}

/** 옮길 수 있는 최대 — 보유에서 할당을 뺀 만큼 */
const movable = computed(() =>
  stock.value ? stock.value.qtyOnHand - stock.value.qtyAllocated : 0,
)

const qtyError = computed(() => {
  if (!stock.value) return ''
  if (qty.value > movable.value) {
    return `${movable.value} 개까지만 옮길 수 있습니다. 나머지는 주문에 잡혀 있습니다.`
  }
  return ''
})

const unsellableError = computed(() => {
  if (!stock.value) return ''
  if (unsellableQty.value > qty.value) {
    return '옮기는 수량보다 많을 수 없습니다. 판매불가는 그 안에 들어 있는 것입니다.'
  }
  if (unsellableQty.value > stock.value.qtyUnsellable) {
    return `출발지의 판매불가는 ${stock.value.qtyUnsellable} 개입니다.`
  }
  return ''
})

const canSubmit = computed(
  () =>
    stock.value &&
    toLocationSeq.value &&
    qty.value > 0 &&
    !qtyError.value &&
    !unsellableError.value &&
    !busy.value,
)

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const { result } = await opsApi.transfer({
      fromStockSeq: stock.value.stockSeq,
      toLocationSeq: Number(toLocationSeq.value),
      qty: qty.value,
      unsellableQty: unsellableQty.value,
      reasonCode: reasonCode.value || null,
      remark: remark.value || null,
    })
    done.value = result
    toast.success(`${result.refNo} — ${qty.value} 개를 옮겼습니다.`)
    reset()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const denyReason = computed(() => session.denyReason('INV_MOVE', 'C'))
const readDenyReason = computed(() => session.denyReason('QRY_STOCK', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">로케이션 이동</h1>
        <p class="page-desc">
          같은 센터 안에서 물건을 다른 빈으로 옮깁니다. <strong>총량은 바뀌지 않고</strong>
          어디 있는지만 바뀝니다. 센터를 넘는 이동은 출고와 입고로 처리합니다 —
          트럭에 실려 있는 동안 재고가 어느 쪽에도 없는 기간이 생기기 때문입니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="denyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ denyReason }}</span>
    </div>

    <div class="card p-3">
      <div class="move-row">
        <!-- ── 출발지 ────────────────────────────────────────── -->
        <div class="side">
          <div class="side-title">출발</div>
          <div v-if="!stock" class="side-empty">
            <button class="btn btn-primary" :disabled="!!readDenyReason" @click="picking = true">
              재고 고르기
            </button>
          </div>
          <div v-else>
            <div class="code strong">{{ stock.locationFullCode }}</div>
            <div class="code">{{ stock.skuId }}</div>
            <div class="small dim">{{ stock.productName }}</div>
            <div class="qty-row">
              <span>보유 <strong>{{ num(stock.qtyOnHand) }}</strong></span>
              <span>할당 <strong>{{ num(stock.qtyAllocated) }}</strong></span>
              <span>판매불가 <strong class="warn">{{ num(stock.qtyUnsellable) }}</strong></span>
            </div>
            <div class="small dim">옮길 수 있는 수량 <strong>{{ num(movable) }}</strong></div>
            <div class="btn-row mt-1">
              <button class="btn btn-sm" @click="picking = true">다른 재고</button>
              <button class="btn btn-sm" @click="clearAll()">비우기</button>
            </div>
          </div>
        </div>

        <div class="arrow">→</div>

        <!-- ── 도착지 ────────────────────────────────────────── -->
        <div class="side">
          <div class="side-title">도착</div>
          <FormField
            v-model="toLocationSeq"
            label="빈"
            type="select"
            required
            :empty-option="stock ? '선택하세요' : '출발 재고를 먼저 고르세요'"
            :options="destOptions"
            :disabled="!stock || loadingDest"
            help="같은 센터의 쓰는 빈만 나옵니다. 창고는 넘어갈 수 있습니다."
          />
          <div v-if="crossesWarehouse" class="alert alert-info mt-1">
            <span class="alert-icon">ℹ</span>
            <span>
              {{ stock.warehouseId }} → {{ destination.warehouseId }} 로 창고가 바뀝니다.
              불량품을 불량창고로 보내는 경우라면 아래 판매불가 수량도 함께 넣으세요.
            </span>
          </div>
        </div>
      </div>

      <!-- ── 얼마나 ──────────────────────────────────────────── -->
      <div class="form-grid mt-2">
        <FormField
          v-model.number="qty"
          label="옮길 수량"
          type="number"
          required
          :disabled="!stock"
          :error="qtyError"
          :help="stock ? `보유 ${num(stock.qtyOnHand)} 중 ${num(stock.qtyAllocated)} 개는 주문에 잡혀 있어 옮길 수 없습니다.` : ''"
        />
        <FormField
          v-model.number="unsellableQty"
          label="그중 판매불가"
          type="number"
          :disabled="!stock || !stock.qtyUnsellable"
          :error="unsellableError"
          help="옮기는 수량 중 불량인 개수. 전부 정상이면 0 으로 둡니다."
        />
        <FormField
          v-model="reasonCode"
          label="사유"
          type="select"
          empty-option="없음"
          :options="codeOptions('REASON_ADJUST')"
          :disabled="!stock"
          help="이동은 사유가 없어도 되지만, 있으면 이력에 남습니다."
        />
        <FormField v-model="remark" label="비고" :disabled="!stock" />
      </div>

      <div v-if="serverError" class="alert alert-danger mt-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="btn-row submit-row">
        <span class="small dim">
          옮기면 출발지와 도착지 양쪽에 이력이 남고, 한 전표번호로 묶입니다.
        </span>
        <button class="btn btn-primary" :disabled="!canSubmit" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          이동
        </button>
      </div>
    </div>

    <StockPicker
      v-if="picking"
      title="옮길 재고 고르기"
      @pick="choose"
      @close="picking = false"
    />

    <ModalDialog v-if="done" :title="`${done.refNo} — 옮겼습니다`" @close="done = null">
      <div class="result-row">
        <div v-for="(s, i) in done.stocks" :key="s.stockSeq" class="result-card">
          <div class="small dim">{{ i === 0 ? '출발' : '도착' }}</div>
          <div class="code strong">{{ s.locationFullCode }}</div>
          <div class="qty-row">
            <span>보유 <strong>{{ num(s.qtyOnHand) }}</strong></span>
            <span>판매불가 <strong class="warn">{{ num(s.qtyUnsellable) }}</strong></span>
            <span>판매가능 <strong class="ok">{{ num(s.qtyAvailable) }}</strong></span>
          </div>
        </div>
      </div>
      <p class="small dim mt-2">
        이력 {{ done.historyCount }} 줄이 전표번호 {{ done.refNo }} 로 묶였습니다.
        재고 이력에서 이 번호로 찾으면 양쪽이 함께 나옵니다.
      </p>
      <template #footer>
        <button class="btn btn-primary" @click="done = null">확인</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.p-3 {
  padding: 16px;
}
.move-row {
  display: flex;
  align-items: stretch;
  gap: 12px;
  flex-wrap: wrap;
}
.side {
  flex: 1 1 280px;
  padding: 12px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
}
.side-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.side-empty {
  padding: 8px 0;
}
.arrow {
  align-self: center;
  font-size: 22px;
  color: var(--fg-dim, #6b7280);
}
.strong {
  font-weight: 600;
}
.qty-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
  margin-top: 6px;
}
.mt-1 {
  margin-top: 6px;
}
.submit-row {
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #e5e7eb);
  flex-wrap: wrap;
  gap: 10px;
}
.result-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.result-card {
  flex: 1 1 220px;
  padding: 10px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
}
.ok {
  color: var(--c-green, #16a34a);
}
.warn {
  color: var(--c-amber, #b45309);
}

@media (max-width: 720px) {
  .arrow {
    transform: rotate(90deg);
  }
}
</style>
