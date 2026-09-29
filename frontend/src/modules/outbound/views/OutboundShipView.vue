<script setup>
/**
 * 출고확정 · 택배 인계 (PAC-PG-005, PAC-PG-006).
 *
 * <b>여기서 재고가 줄어든다.</b> 지금까지는 아무것도 줄지 않았다 — 할당이
 * 잡아 뒀을 뿐, 피킹 · 검수 · 패킹 · 송장은 물건을 옮기고 세고 적었을 뿐이다.
 *
 * 화면이 두 부분이다.
 *
 *   내보낼 것   패킹이 끝나고 송장까지 붙은 지시. 누르면 재고가 빠진다.
 *   인계        택배사가 실어 갈 때 송장번호를 찍는다.
 *
 * 둘을 나눈 이유는 다른 사건이라서다. 출고확정은 우리가 '나갔다' 고 장부를
 * 닫는 것이고, 인계는 택배사가 '받았다' 고 확인하는 것이다 — 확정한 물건이
 * 집화 차를 놓쳐 하루 밀리는 일이 있고, 그때 배송 지연을 누구 탓으로 볼지가
 * 갈린다.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import * as outboundApi from '@/api/outbound.js'
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

const canShip = computed(() => session.can('OUT_APPROVE', 'C'))

/* ── 내보낼 것 ──────────────────────────────────────────────── */

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', plantId: '' })

/**
 * 패킹이 끝난 지시만.
 *
 * 송장이 다 붙었는지는 서버가 확정할 때 다시 본다 — 여기서는 목록을 좁히는
 * 용도고, 막는 것은 서버가 한다.
 */
async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.list({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      outboundStatus: 'PACKED',
      size: 100,
      sortBy: 'instructedAt',
      sortDir: 'asc',
    })
    rows.value = data.rows ?? []
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
  await nextTick()
  scanInput.value?.focus?.()
})

/* ── 출고확정 ───────────────────────────────────────────────── */

const confirming = ref(null)
const boxes = ref([])
const busy = ref(false)

async function openShip(row) {
  confirming.value = row
  try {
    boxes.value = await outboundApi.waybillsOf(row.outboundSeq)
  } catch {
    boxes.value = []
  }
}

/** 살아 있는 송장만. 취소된 것은 나갈 번호가 아니다 */
const liveWaybills = computed(() => boxes.value.filter((w) => w.issued))

async function doShip() {
  busy.value = true
  try {
    const out = await outboundApi.ship(confirming.value.outboundSeq)
    toast.success(`${out.outboundNo} 을(를) 내보냈습니다. 재고에서 빠졌습니다.`)
    confirming.value = null
    await fetchPage()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

/* ── 인계 (집화 스캔) ───────────────────────────────────────── */

const scan = ref('')
const scanInput = ref(null)
const scanned = ref([])
const handResult = ref(null)

/**
 * 찍은 번호를 쌓아 둔다.
 *
 * 기사가 쌓인 박스를 차례로 찍고 마지막에 한 번 보내는 편이, 찍을 때마다
 * 왕복하는 것보다 빠르다. 같은 번호를 두 번 찍으면 두 번째는 버린다 —
 * 스캐너가 한 번에 두 번 읽는 일이 있다.
 */
async function onScan() {
  const no = scan.value.trim().replace(/[^0-9A-Za-z]/g, '')
  scan.value = ''
  await nextTick()
  scanInput.value?.focus?.()
  if (!no) return
  if (scanned.value.includes(no)) {
    toast.warn(`${no} 는 이미 찍었습니다.`)
    return
  }
  scanned.value = [...scanned.value, no]
}

async function doHandOver() {
  if (!scanned.value.length) return
  busy.value = true
  try {
    handResult.value = await outboundApi.handOver(scanned.value)
    if (handResult.value.failed.length === 0) {
      toast.success(`${handResult.value.done.length} 개를 넘겼습니다.`)
    } else {
      toast.warn(
        `${handResult.value.done.length} 개 인계 · ${handResult.value.failed.length} 개 실패`,
      )
    }
    scanned.value = []
    await fetchPage()
    await nextTick()
    scanInput.value?.focus?.()
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))

const columns = [
  { key: 'outboundNo', label: '지시번호', width: '170px', cls: 'code' },
  { key: 'orderNo', label: '주문', width: '160px' },
  { key: 'receiverName', label: '수령인', width: '84px' },
  { key: 'plantName', label: '센터', width: '104px' },
  { key: 'totalPackedQty', label: '담음', width: '64px', align: 'right' },
  { key: 'boxCount', label: '박스', width: '58px', align: 'right' },
  { key: 'outboundStatus', label: '상태', width: '90px', align: 'center' },
  { key: '_act', label: '', width: '100px', align: 'right' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">출고확정 · 인계</h1>
        <p class="page-desc">
          <strong>여기서 재고가 줄어듭니다.</strong> 지금까지는 할당이 잡아 뒀을 뿐,
          피킹 · 검수 · 패킹은 물건을 옮기고 세고 담았을 뿐입니다.
          <strong>되돌릴 수 없습니다</strong> — 잘못 내보냈으면 반품으로 처리합니다.
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!--
      집화 스캔. 기사가 오면 바로 찍어야 해서 맨 위에 두고 포커스를 준다 —
      목록을 지나 스크롤해 내려가게 할 자리가 아니다.
    -->
    <div class="handover">
      <div class="hand-head">
        <strong>택배 인계</strong>
        <span class="small dim">송장번호를 찍으세요. 박스번호가 아닙니다.</span>
      </div>
      <div class="hand-grid">
        <FormField
          ref="scanInput"
          v-model="scan"
          label="송장 스캔"
          mono
          placeholder="기사가 실어 갈 박스의 송장을 찍으세요"
          @enter="onScan()"
        />
        <button
          class="btn btn-primary hand-submit"
          :disabled="!scanned.length || busy || !canShip"
          @click="doHandOver()"
        >
          <span v-if="busy" class="spinner"></span>
          {{ scanned.length ? `${scanned.length} 개 인계` : '인계' }}
        </button>
        <button class="btn hand-submit" :disabled="!scanned.length" @click="scanned = []">
          비우기
        </button>
      </div>
      <div v-if="scanned.length" class="chips">
        <span v-for="no in scanned" :key="no" class="chip code">{{ no }}</span>
      </div>
      <div
        v-if="handResult"
        class="alert mt-1"
        :class="handResult.failed.length ? 'alert-warn' : 'alert-ok'"
      >
        <span class="alert-icon">{{ handResult.failed.length ? '⚠' : '✅' }}</span>
        <div class="grow">
          <div><strong>{{ handResult.done.length }} 개</strong> 넘겼습니다.</div>
          <ul v-if="handResult.failed.length" class="fail">
            <li v-for="(f, i) in handResult.failed" :key="i" class="small">{{ f }}</li>
          </ul>
        </div>
        <button class="btn btn-sm" @click="handResult = null">닫기</button>
      </div>
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
      empty-text="내보낼 것이 없습니다. 패킹이 끝나고 송장까지 붙은 지시가 여기 옵니다."
      @row-click="openShip"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.singlePack" class="small dim">단포</div>
      </template>

      <template #cell-outboundStatus="{ value }">
        <CodeBadge group="OUTBOUND_STATUS" :code="value" />
      </template>

      <template #cell-_act="{ row }">
        <button
          class="btn btn-sm btn-primary"
          :disabled="!canShip"
          @click.stop="openShip(row)"
        >
          내보내기
        </button>
      </template>
    </DataTable>

    <!-- ── 출고확정 확인 ────────────────────────────────────── -->
    <ModalDialog
      v-if="confirming"
      title="출고확정"
      :subtitle="`${confirming.outboundNo} · ${confirming.receiverName}`"
      @close="confirming = null"
    >
      <!--
        무엇이 얼마나 빠지는지 먼저 보여 준다. 되돌릴 수 없는 판단이라
        숫자를 읽고 누르는 것과 그냥 누르는 것은 다르다.
      -->
      <div class="alert alert-warn">
        <span class="alert-icon">⚠</span>
        <span>
          <strong>재고 {{ num(confirming.totalPackedQty) }} 개</strong>가 빠집니다.
          되돌릴 수 없습니다 — 잘못 내보냈으면 반품으로 처리해야 합니다.
        </span>
      </div>

      <div class="sum">
        <span>박스 <strong>{{ num(confirming.boxCount) }}</strong></span>
        <span>담음 <strong>{{ num(confirming.totalPackedQty) }}</strong></span>
        <span>{{ confirming.plantName }}</span>
      </div>

      <div class="section-title">나갈 송장</div>
      <table v-if="liveWaybills.length" class="table sub">
        <tr v-for="w in liveWaybills" :key="w.waybillSeq">
          <td>{{ w.boxNo }}번 박스</td>
          <td class="code">{{ w.waybillNo }}</td>
          <td class="small dim">{{ w.courierName }}</td>
          <td class="right">{{ num(w.totalPackedQty) }}개</td>
        </tr>
      </table>
      <div v-else class="alert alert-danger mt-1">
        <span class="alert-icon">⛔</span>
        <span>송장이 붙은 박스가 없습니다. 송장을 먼저 발급하세요.</span>
      </div>

      <p class="small dim mt-2">
        보유수량과 할당수량이 <strong>같이</strong> 줄어듭니다. 판매가능은 안 바뀝니다 —
        할당할 때 이미 뺐기 때문입니다.
      </p>

      <template #footer>
        <button class="btn" :disabled="busy" @click="confirming = null">닫기</button>
        <button
          class="btn btn-danger"
          :disabled="busy || !liveWaybills.length || !canShip"
          @click="doShip()"
        >
          <span v-if="busy" class="spinner"></span>
          {{ num(confirming.totalPackedQty) }} 개 내보내기
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
/* 집화 스캔 — 기사가 오면 바로 찍어야 해서 맨 위에 둔다 */
.handover {
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 6px;
  border: 1px solid var(--c-blue, #2563eb);
  background: var(--bg-2, #f6f7f9);
}
.hand-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}
.hand-grid {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  align-items: end;
}
@media (max-width: 720px) {
  .hand-grid {
    grid-template-columns: 1fr;
  }
}
.hand-submit {
  height: 38px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.chip {
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--bg-1, #fff);
  border: 1px solid var(--line, #e5e7eb);
  font-size: 12px;
}
.sum {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 10px 12px;
  margin: 10px 0;
  border-radius: 6px;
  background: var(--bg-2, #f6f7f9);
}
.section-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.sub td {
  padding: 3px 10px 3px 0;
}
.fail {
  margin: 6px 0 0;
  padding-left: 18px;
}
.right {
  text-align: right;
}
.grow {
  flex: 1;
}
.mt-1 {
  margin-top: 6px;
}
.mt-2 {
  margin-top: 10px;
}
</style>
