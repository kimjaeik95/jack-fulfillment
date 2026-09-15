<script setup>
/**
 * 판매불가 전환 (INV-PG-005).
 *
 * 같은 자리에 있는 같은 물건의 '팔 수 있는지' 만 바꾼다. 총량(보유)은
 * 그대로다 — 물건이 어디로 가지 않았기 때문이다.
 *
 * 승인이 없다. 총량이 바뀌지 않아서인데, 그렇다고 가벼운 일은 아니다.
 * 판매불가로 돌리면 그만큼 팔 수 없게 되고, 되돌리면 다시 팔린다. 그래서
 * 사유가 필수고 이력이 남는다.
 *
 * 되돌리는 쪽(판매불가 → 정상)이 있는 이유는 사람이 판정을 틀리기 때문이다.
 * 되돌릴 수 없으면 잘못 잡은 불량을 조정 전표로 풀게 되고, 그러면 조정
 * 이력이 실제 조정이 아닌 것으로 오염된다.
 */
import { computed, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
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

/** TO_UNSELLABLE(정상 → 판매불가) / TO_NORMAL(판매불가 → 정상) */
const direction = ref('TO_UNSELLABLE')
const qty = ref(1)
const reasonCode = ref('')
const remark = ref('')

function choose(row) {
  stock.value = row
  picking.value = false
  serverError.value = ''
  done.value = null
  qty.value = 1
  reasonCode.value = ''
  remark.value = ''
}

function reset() {
  stock.value = null
  done.value = null
  serverError.value = ''
}

const toUnsellable = computed(() => direction.value === 'TO_UNSELLABLE')

/**
 * 이 방향으로 움직일 수 있는 최대 수량.
 *
 *   정상 → 판매불가  판매가능만큼. 할당된 것은 이미 주문이 잡고 있어
 *                    불량으로 돌리면 판매가능이 음수가 된다.
 *   판매불가 → 정상  지금 판매불가로 잡힌 만큼.
 */
const maxQty = computed(() => {
  if (!stock.value) return 0
  return toUnsellable.value ? stock.value.qtyAvailable : stock.value.qtyUnsellable
})

const overMax = computed(() => qty.value > maxQty.value)

/** 전환 후 네 수량이 어떻게 되는지 미리 보여 준다 */
const preview = computed(() => {
  if (!stock.value) return null
  const d = toUnsellable.value ? qty.value : -qty.value
  const unsellable = stock.value.qtyUnsellable + d
  return {
    qtyOnHand: stock.value.qtyOnHand,
    qtyAllocated: stock.value.qtyAllocated,
    qtyUnsellable: unsellable,
    qtyAvailable: stock.value.qtyOnHand - stock.value.qtyAllocated - unsellable,
  }
})

const canSubmit = computed(
  () => stock.value && qty.value > 0 && !overMax.value && reasonCode.value && !busy.value,
)

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const { result } = await opsApi.changeUnsellable({
      stockSeq: stock.value.stockSeq,
      direction: direction.value,
      qty: qty.value,
      reasonCode: reasonCode.value,
      remark: remark.value || null,
    })
    done.value = result.stocks[0]
    // 같은 재고를 이어서 다룰 수 있게 바뀐 값으로 갈아 끼운다.
    // 목록으로 돌려보내면 방금 고른 줄을 다시 찾아야 한다.
    stock.value = result.stocks[0]
    qty.value = 1
    remark.value = ''
    toast.success(
      `${toUnsellable.value ? '판매불가로' : '정상으로'} ${result.stocks[0].skuId} 처리했습니다.`,
    )
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
        <h1 class="page-title">판매불가 전환</h1>
        <p class="page-desc">
          파손 · 오염 · 검수대기처럼 <strong>있지만 팔 수 없는 재고</strong>를 갈라 둡니다.
          보유 수량은 바뀌지 않고 판매가능만 줄어듭니다 — 물건이 어디로 간 것이 아니기 때문입니다.
          수선이 끝났거나 판정이 틀렸으면 정상으로 되돌립니다.
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
      <!-- ── 1 어느 재고를 ──────────────────────────────────── -->
      <div class="step">
        <span class="step-no">1</span>
        <div class="step-body">
          <div class="step-title">어느 재고를</div>
          <div v-if="!stock" class="pick-empty">
            <button class="btn btn-primary" :disabled="!!readDenyReason" @click="picking = true">
              재고 고르기
            </button>
          </div>
          <div v-else class="picked">
            <div>
              <span class="code strong">{{ stock.locationFullCode }}</span>
              <span class="code" style="margin-left: 10px">{{ stock.skuId }}</span>
              <div class="small dim">
                {{ stock.productName }} · {{ stock.colorCode }} / {{ stock.sizeCode }}
              </div>
            </div>
            <div class="qty-row">
              <span>보유 <strong>{{ num(stock.qtyOnHand) }}</strong></span>
              <span>할당 <strong>{{ num(stock.qtyAllocated) }}</strong></span>
              <span>판매불가 <strong class="warn">{{ num(stock.qtyUnsellable) }}</strong></span>
              <span>판매가능 <strong class="ok">{{ num(stock.qtyAvailable) }}</strong></span>
            </div>
            <div class="btn-row">
              <button class="btn btn-sm" @click="picking = true">다른 재고</button>
              <button class="btn btn-sm" @click="reset()">비우기</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ── 2 어느 방향으로 ────────────────────────────────── -->
      <div class="step" :class="{ off: !stock }">
        <span class="step-no">2</span>
        <div class="step-body">
          <div class="step-title">어느 방향으로</div>
          <div class="dir-row">
            <button
              :class="['dir', { on: toUnsellable }]"
              :disabled="!stock"
              @click="direction = 'TO_UNSELLABLE'"
            >
              <span class="dir-label">정상 → 판매불가</span>
              <span class="small dim">파손 · 오염 · DP 로 뺀다</span>
            </button>
            <button
              :class="['dir', { on: !toUnsellable }]"
              :disabled="!stock"
              @click="direction = 'TO_NORMAL'"
            >
              <span class="dir-label">판매불가 → 정상</span>
              <span class="small dim">수선 완료 · 판정 정정</span>
            </button>
          </div>
        </div>
      </div>

      <!-- ── 3 얼마나, 왜 ──────────────────────────────────── -->
      <div class="step" :class="{ off: !stock }">
        <span class="step-no">3</span>
        <div class="step-body">
          <div class="step-title">얼마나, 왜</div>
          <div class="form-grid">
            <FormField
              v-model.number="qty"
              label="수량"
              type="number"
              required
              :disabled="!stock"
              :error="overMax ? `${num(maxQty)} 개까지만 가능합니다.` : ''"
              :help="
                toUnsellable
                  ? `판매가능 ${num(maxQty)} 개까지. 할당된 수량은 이미 주문이 잡고 있어 뺄 수 없습니다.`
                  : `판매불가 ${num(maxQty)} 개까지.`
              "
            />
            <FormField
              v-model="reasonCode"
              label="사유"
              type="select"
              required
              empty-option="선택하세요"
              :options="codeOptions('REASON_INSPECT')"
              :disabled="!stock"
              help="왜 못 팔게 됐는지가 남지 않으면 나중에 아무것도 설명할 수 없습니다."
            />
            <FormField
              v-model="remark"
              label="비고"
              class="span-2"
              :disabled="!stock"
              placeholder="사유코드로 설명되지 않는 부분"
            />
          </div>

          <!-- 처리하면 숫자가 어떻게 되는지 미리 보여 준다 -->
          <div v-if="stock && preview && !overMax" class="preview">
            <span class="small dim">처리하면</span>
            <span>보유 <strong>{{ num(preview.qtyOnHand) }}</strong></span>
            <span>
              판매불가
              <strong class="warn">{{ num(stock.qtyUnsellable) }} → {{ num(preview.qtyUnsellable) }}</strong>
            </span>
            <span>
              판매가능
              <strong :class="preview.qtyAvailable > 0 ? 'ok' : 'danger'">
                {{ num(stock.qtyAvailable) }} → {{ num(preview.qtyAvailable) }}
              </strong>
            </span>
          </div>
        </div>
      </div>

      <div v-if="serverError" class="alert alert-danger mt-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="btn-row submit-row">
        <span class="small dim">
          처리하면 되돌리는 것도 전환입니다 — 반대 방향으로 한 번 더 하면 됩니다.
          두 번 다 이력에 남습니다.
        </span>
        <button class="btn btn-primary" :disabled="!canSubmit" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          {{ toUnsellable ? '판매불가로 전환' : '정상으로 복귀' }}
        </button>
      </div>
    </div>

    <StockPicker
      v-if="picking"
      title="전환할 재고 고르기"
      @pick="choose"
      @close="picking = false"
    />

    <ModalDialog v-if="done" title="처리했습니다" @close="done = null">
      <div class="qty-grid">
        <div class="qty-cell">
          <span class="qty-label">보유</span>
          <strong class="qty-value">{{ num(done.qtyOnHand) }}</strong>
        </div>
        <div class="qty-cell">
          <span class="qty-label">할당</span>
          <strong class="qty-value">{{ num(done.qtyAllocated) }}</strong>
        </div>
        <div class="qty-cell">
          <span class="qty-label">판매불가</span>
          <strong class="qty-value warn">{{ num(done.qtyUnsellable) }}</strong>
        </div>
        <div class="qty-cell strong">
          <span class="qty-label">판매가능</span>
          <strong class="qty-value" :class="done.qtyAvailable > 0 ? 'ok' : 'danger'">
            {{ num(done.qtyAvailable) }}
          </strong>
        </div>
      </div>
      <p class="small dim mt-2">
        {{ done.locationFullCode }} · {{ done.skuId }} — 재고 이력에서 이 변경을 다시 볼 수 있습니다.
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
.step {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px dashed var(--line, #e5e7eb);
}
.step:last-of-type {
  border-bottom: none;
}
.step.off {
  opacity: 0.45;
}
.step-no {
  flex: 0 0 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--c-blue, #2563eb);
  color: #fff;
  text-align: center;
  line-height: 24px;
  font-size: 13px;
}
.step-body {
  flex: 1;
  min-width: 0;
}
.step-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.pick-empty {
  padding: 4px 0;
}
.picked {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
}
.picked > div:first-child {
  flex: 1 1 260px;
}
.strong {
  font-weight: 600;
}
.qty-row {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 13px;
}
.dir-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.dir {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
  padding: 10px 16px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.dir:disabled {
  cursor: not-allowed;
}
.dir.on {
  border-color: var(--c-blue, #2563eb);
  box-shadow: inset 0 0 0 1px var(--c-blue, #2563eb);
}
.dir-label {
  font-weight: 600;
  font-size: 13px;
}
.preview {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--bg-soft, #f8fafc);
  font-size: 13px;
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
.qty-grid {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.qty-cell {
  flex: 1 1 90px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
}
.qty-cell.strong {
  border-color: var(--c-blue, #2563eb);
}
.qty-label {
  font-size: 12px;
  color: var(--fg-dim, #6b7280);
}
.qty-value {
  font-size: 20px;
  font-variant-numeric: tabular-nums;
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
