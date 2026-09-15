<script setup>
/**
 * 재고 대사 (INV-PG-010).
 *
 * "지금 재고가 맞나" 에 답한다. 맞는지 확인하는 방법은 하나뿐이다 —
 * 같은 것을 두 군데서 세어 비교한다.
 *
 * 다섯 검사 중 앞의 셋(CRITICAL)은 정합성이 깨진 것이고, 뒤의 둘(WARNING)은
 * 깨질 조짐이다. 그 구분을 서버가 정해 내려보낸다. 화면마다 '이 검사는
 * 빨강, 저 검사는 노랑' 을 다시 적으면 화면에 따라 같은 문제가 다르게
 * 보인다.
 *
 * 이 화면은 아무것도 바꾸지 않는다. 발견한 것을 고치는 것은 조정(C섹터)이나
 * 실사(D섹터)의 일이라, 각 줄에서 그쪽으로 건너갈 수 있게 해 둔다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as opsApi from '@/api/stockOps.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import FormField from '@/components/FormField.vue'

const router = useRouter()
const hierarchy = useHierarchyStore()
const session = useSessionStore()

const result = ref(null)
const loading = ref(false)
const loadError = ref('')

const filters = reactive({
  plantId: '',
  warehouseId: '',
  staleDays: 90,
  pendingDays: 7,
  limit: 200,
})

async function run() {
  loading.value = true
  loadError.value = ''
  try {
    result.value = await opsApi.reconcile(filters)
  } catch (e) {
    result.value = null
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([hierarchy.loadPlants(false), hierarchy.loadWarehouses(false)])
  await run()
  // 걸린 검사만 펼쳐 둔다. 깨끗한 검사는 볼 것이 없고, 다 펼치면
  // 정작 봐야 할 것이 스크롤 아래로 밀린다.
  applyDefaults()
})

/** 펼쳐 둔 검사. 걸린 것이 있으면 처음부터 펼친다. */
const open = reactive({})

function toggle(code) {
  open[code] = !open[code]
}

/** 결과가 오면 걸린 검사만 펼쳐 둔다 — 깨끗한 검사는 볼 것이 없다 */
function applyDefaults() {
  if (!result.value) return
  for (const c of result.value.checks) {
    open[c.checkCode] = c.found > 0
  }
}

const checks = computed(() => {
  const list = result.value?.checks ?? []
  return list
})

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const signed = (v) => (v > 0 ? `+${nf.format(v)}` : nf.format(v))

const severityLabel = (s) => (s === 'CRITICAL' ? '정합성 붕괴' : '확인 필요')

/** 발견한 것을 고치러 간다 — 조정 요청이나 실사 계획으로 */
function goFix(check) {
  if (check.checkCode === 'STALE') {
    router.push({ name: 'stocktake' })
  } else if (check.checkCode === 'PENDING') {
    router.push({ name: 'stock-adjust-approve' })
  } else {
    router.push({ name: 'stock-adjust' })
  }
}

const fixLabel = (check) =>
  check.checkCode === 'STALE'
    ? '실사 계획 세우기'
    : check.checkCode === 'PENDING'
      ? '결재함 열기'
      : '조정 요청 올리기'

const readDenyReason = computed(() => session.denyReason('QRY_STOCK', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">재고 대사</h1>
        <p class="page-desc">
          <strong>같은 것을 두 군데서 세어 비교합니다.</strong> 장부 수량과 재고이력의 합,
          할당수량과 할당이력의 합이 각각 맞아야 합니다. 어긋나면 이력을 남기지 않고 수량을
          바꾼 경로가 있다는 뜻입니다. 이 화면은 아무것도 바꾸지 않습니다 — 고치는 것은
          조정과 실사의 일입니다.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="filters.warehouseId = ''; run().then(applyDefaults)"
        />
        <FormField
          v-model="filters.warehouseId"
          label="창고"
          type="select"
          empty-option="전체"
          :options="filters.plantId ? hierarchy.warehouseOptionsOf(filters.plantId) : []"
          :disabled="!filters.plantId"
          @change="run().then(applyDefaults)"
        />
        <FormField
          v-model.number="filters.staleDays"
          label="실사 기준"
          type="number"
          help="며칠 넘게 안 세면 확인 대상으로 볼지"
        />
        <FormField
          v-model.number="filters.pendingDays"
          label="결재 기준"
          type="number"
          help="며칠 넘게 결재 안 되면 묵은 것으로 볼지"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="run().then(applyDefaults)">
            <span v-if="loading" class="spinner"></span>
            대사 실행
          </button>
        </div>
      </div>
    </div>

    <!-- ── 총평 ────────────────────────────────────────────── -->
    <div v-if="result" :class="['verdict', result.clean ? 'ok' : 'bad']">
      <span class="verdict-icon">{{ result.clean ? '✓' : '⚠' }}</span>
      <div>
        <strong v-if="result.clean">정합성 이상 없음</strong>
        <strong v-else>정합성이 깨진 곳이 있습니다</strong>
        <div class="small">
          <template v-if="result.clean">
            장부와 이력이 모두 맞습니다. 아래 '확인 필요' 항목은 틀렸다는 뜻이 아니라
            맞는지 아무도 확인하지 않았다는 뜻입니다.
          </template>
          <template v-else>
            아래 '정합성 붕괴' 항목부터 보세요. 이력을 남기지 않고 수량을 바꾼 경로가
            있다는 뜻이라, 그 경로를 찾지 않으면 고쳐도 다시 어긋납니다.
          </template>
          전체 {{ num(result.totalFound) }}건.
        </div>
      </div>
    </div>

    <!-- ── 검사별 ──────────────────────────────────────────── -->
    <div v-for="c in checks" :key="c.checkCode" class="card check">
      <button class="check-head" @click="toggle(c.checkCode)">
        <span :class="['sev', c.severity.toLowerCase(), { none: c.found === 0 }]">
          {{ c.found === 0 ? '이상 없음' : severityLabel(c.severity) }}
        </span>
        <span class="check-title">{{ c.title }}</span>
        <span :class="['count', { zero: c.found === 0 }]">{{ num(c.found) }}건</span>
        <span class="caret">{{ open[c.checkCode] ? '▲' : '▼' }}</span>
      </button>

      <div v-show="open[c.checkCode]" class="check-body">
        <p class="small dim">{{ c.description }}</p>

        <div v-if="c.found === 0" class="empty-note">이 검사에 걸린 것이 없습니다.</div>

        <template v-else>
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th style="width: 175px">재고주소</th>
                  <th style="width: 150px">SKU</th>
                  <th style="width: 80px" class="right">장부</th>
                  <th style="width: 80px" class="right">대조</th>
                  <th style="width: 76px" class="right">차이</th>
                  <th>비고</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(r, i) in c.rows" :key="`${c.checkCode}-${i}`">
                  <td>
                    <span v-if="r.locationFullCode" class="code">{{ r.locationFullCode }}</span>
                    <span v-else class="dim">{{ r.plantName }}</span>
                  </td>
                  <td>
                    <span v-if="r.skuId" class="code">{{ r.skuId }}</span>
                    <div v-if="r.productName" class="small dim">{{ r.productName }}</div>
                    <span v-if="!r.skuId" class="dim">-</span>
                  </td>
                  <td class="num">{{ num(r.bookQty) }}</td>
                  <td class="num">{{ num(r.computedQty) }}</td>
                  <td class="num">
                    <strong v-if="r.diffQty !== null" :class="r.diffQty > 0 ? 'ok' : r.diffQty < 0 ? 'danger' : 'dim'">
                      {{ signed(r.diffQty) }}
                    </strong>
                    <span v-else class="dim">-</span>
                  </td>
                  <td class="small">
                    <span v-if="r.days !== null && r.days !== undefined">{{ r.days }}일 · </span>
                    {{ r.detail ?? '-' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="check-foot">
            <span class="small dim">
              <template v-if="c.found > c.rows.length">
                {{ num(c.found) }}건 중 앞의 {{ num(c.rows.length) }}건입니다.
                이만큼 많으면 목록을 다 보는 것이 목적이 아니라 원인을 찾는 것이 목적입니다.
              </template>
              <template v-else>{{ num(c.found) }}건 전부입니다.</template>
            </span>
            <button class="btn btn-sm btn-primary" @click="goFix(c)">{{ fixLabel(c) }}</button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.verdict {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px;
  border-radius: 8px;
  margin: 10px 0;
  border: 1px solid var(--line, #e5e7eb);
}
.verdict.ok {
  border-color: var(--c-green, #16a34a);
  background: color-mix(in srgb, var(--c-green, #16a34a) 6%, transparent);
}
.verdict.bad {
  border-color: var(--c-red, #dc2626);
  background: color-mix(in srgb, var(--c-red, #dc2626) 6%, transparent);
}
.verdict-icon {
  font-size: 20px;
  line-height: 1.2;
}
.check {
  margin-bottom: 8px;
}
.check-head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
}
.check-title {
  flex: 1;
  font-weight: 600;
}
.sev {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: #fff;
}
.sev.critical {
  background: var(--c-red, #dc2626);
}
.sev.warning {
  background: var(--c-amber, #f59e0b);
}
.sev.none {
  background: var(--c-green, #16a34a);
}
.count {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.count.zero {
  color: var(--fg-dim, #6b7280);
  font-weight: 400;
}
.caret {
  color: var(--fg-dim, #6b7280);
  font-size: 11px;
}
.check-body {
  padding: 0 14px 12px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.check-body > .small {
  margin: 10px 0;
}
.check-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.empty-note {
  padding: 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.ok {
  color: var(--c-green, #16a34a);
}
.danger {
  color: var(--c-red, #dc2626);
}
</style>
