<script setup>
/**
 * 입고정정 승인 (INB-PG-008).
 *
 * 승인하면 <b>세 군데가 한꺼번에 움직인다</b> — 재고, 입고 기입고, 발주
 * 기입고와 상태. 재고조정 승인과 다른 점이 정확히 여기다.
 *
 * 그래서 승인자가 봐야 할 것도 셋이다.
 *   어느 자리에서 빼나   적치 행 단위라 자리가 정해져 있다
 *   지금 거기 얼마 있나  요청 뒤에 재고가 움직였을 수 있다
 *   발주가 어떻게 되나   잔량이 되살아나면 공급처에 다시 요청할 수 있다
 *
 * 요청자는 이 화면에 들어올 수 없다. 권한 액션이 나뉘어 있고(C vs A), 둘 다
 * 가진 사람이 있어도 자기 요청은 서버가 막는다 — 혼자 올리고 혼자 승인하면
 * 승인은 통제가 아니라 절차다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as correctApi from '@/api/inboundCorrect.js'
import { codeOptions } from '@/api/codes.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const hierarchy = useHierarchyStore()
const session = useSessionStore()
const toast = useToastStore()

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const signed = (v) => (v > 0 ? `+${nf.format(v)}` : nf.format(v ?? 0))

const canApprove = computed(() => session.can('INB_CORRECTION', 'A'))
const denyReason = computed(() => session.denyReason('INB_CORRECTION', 'A'))

const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', plantId: '', pendingOnly: 'Y' })

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const page = filters.pendingOnly === 'Y'
      ? await correctApi.pending({ keyword: filters.keyword, plantId: filters.plantId })
      : await correctApi.list({
          keyword: filters.keyword,
          plantId: filters.plantId,
          size: correctApi.PAGE_SIZE,
        })
    rows.value = page.rows
  } catch (e) {
    rows.value = []
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await fetchPage()
})

const waiting = computed(() => rows.value.filter((r) => r.pending))
const reduceTotal = computed(() =>
  waiting.value.reduce((s, r) => s + Math.min(0, r.totalDelta ?? 0), 0),
)

const columns = [
  { key: 'correctNo', label: '정정번호', width: '150px', cls: 'code' },
  { key: 'inboundNo', label: '입고번호', width: '150px', cls: 'code' },
  { key: '_where', label: '받은 곳', width: '140px' },
  { key: 'reasonName', label: '사유', width: '110px' },
  { key: '_delta', label: '순변동', width: '80px', align: 'right' },
  { key: 'requestedByName', label: '요청자', width: '100px' },
  { key: 'correctStatus', label: '상태', width: '84px', align: 'center' },
  { key: '_act', label: '', width: '90px', align: 'right' },
]

/* ── 결재 ────────────────────────────────────────────────────── */

const target = ref(null)
const busy = ref(false)
const serverError = ref('')
const remark = ref('')

async function openDetail(row) {
  serverError.value = ''
  remark.value = ''
  try {
    target.value = await correctApi.detail(row.correctSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/**
 * 요청 뒤에 다른 정정이 같은 적치를 가져갔나.
 *
 * 가져갔으면 뺄 것이 모자라 서버가 막는다. 누르기 전에 알아야 한다.
 */
const shortLines = computed(
  () =>
    target.value?.lines.filter((l) => l.decrease && -l.qtyDelta > l.remainingQty) ?? [],
)

const netDelta = computed(
  () => target.value?.lines.reduce((s, l) => s + (l.qtyDelta ?? 0), 0) ?? 0,
)

async function approve() {
  busy.value = true
  serverError.value = ''
  try {
    const { correct, warning } = await correctApi.approve(
      target.value.correctSeq,
      remark.value || null,
    )
    toast.success(`${correct.correctNo} 정정을 승인했습니다.`)
    if (warning) toast.warn(warning)
    target.value = null
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

async function reject() {
  if (!remark.value) {
    serverError.value =
      '반려 사유를 적으세요. 무엇을 고쳐야 하는지 모르면 같은 요청이 그대로 다시 올라옵니다.'
    return
  }
  busy.value = true
  serverError.value = ''
  try {
    const correct = await correctApi.reject(target.value.correctSeq, remark.value)
    toast.success(`${correct.correctNo} 정정을 반려했습니다.`)
    target.value = null
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">입고정정 승인</h1>
        <p class="page-desc">
          완료된 입고의 수량을 고치는 요청입니다. 승인하면
          <strong>재고 · 입고 기입고 · 발주 기입고가 한꺼번에</strong> 움직입니다.
          줄이는 정정이면 발주 잔량이 되살아나 공급처에 다시 요청할 수 있게 됩니다.
          <strong>되돌릴 수 없습니다.</strong>
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="denyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ denyReason }}</span>
    </div>

    <div class="summary">
      <div class="summary-item" :class="{ alarm: waiting.length > 0 }">
        <span class="summary-label">승인 대기</span>
        <strong class="summary-value">{{ num(waiting.length) }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">되감을 수량 합</span>
        <strong class="summary-value">{{ num(-reduceTotal) }}</strong>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="정정번호 / 입고번호 / 사유"
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
          v-model="filters.pendingOnly"
          label="범위"
          type="select"
          :options="[
            { value: 'Y', label: '대기만' },
            { value: '', label: '전체' },
          ]"
          @change="fetchPage()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="fetchPage()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="correctSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        :muted-when="(r) => !r.pending"
        empty-text="결재할 입고정정이 없습니다."
        @row-click="openDetail"
      >
        <template #cell-_where="{ row }">
          {{ row.plantName }}
          <div class="small dim">{{ row.warehouseName }}</div>
        </template>
        <template #cell-_delta="{ row }">
          <strong :class="row.totalDelta < 0 ? 'danger' : 'warn'">
            {{ signed(row.totalDelta) }}
          </strong>
        </template>
        <template #cell-correctStatus="{ value }">
          <CodeBadge group="CORRECT_STATUS" :code="value" />
        </template>
        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              v-if="row.pending"
              class="btn btn-sm btn-primary"
              :disabled="!canApprove"
              :title="denyReason ?? '결재'"
              @click.stop="openDetail(row)"
            >
              결재
            </button>
            <span v-else class="small dim">처리됨</span>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ── 결재 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="target"
      title="입고정정 결재"
      :subtitle="`${target.correctNo} · ${target.inboundNo}`"
      size="wide"
      @close="target = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="detail-head">
        <CodeBadge group="CORRECT_STATUS" :code="target.correctStatus" />
        <span>사유 <strong>{{ target.reasonName ?? target.reasonCode }}</strong></span>
        <span>요청 <strong>{{ target.requestedByName ?? target.requestedBy }}</strong></span>
        <span>
          순변동
          <strong :class="netDelta < 0 ? 'danger' : 'warn'">{{ signed(netDelta) }}</strong>
        </span>
      </div>
      <p v-if="target.remark" class="small dim">{{ target.remark }}</p>

      <div class="alert alert-warn mt-1">
        <span class="alert-icon">⚠</span>
        <span v-if="netDelta < 0">
          승인하면 재고에서 <strong>{{ num(-netDelta) }} 개</strong>가 빠지고, 발주 기입고도
          그만큼 줄어 <strong>잔량이 되살아납니다.</strong> 공급처에 다시 요청할지 함께
          정하세요. 되돌리려면 반대 방향으로 정정을 한 번 더 올려야 합니다.
        </span>
        <span v-else>
          승인하면 재고가 <strong>{{ num(netDelta) }} 개</strong> 늘고 발주 기입고도 그만큼
          올라갑니다. 공급처 허용 오차를 넘기면 이 승인이
          <strong>초과입고 승인을 겸해</strong> 기록됩니다 — 대금도 그만큼 나갑니다.
        </span>
      </div>

      <!--
        자리 · 놓은 수량 · 남은 수량 · 현재 재고를 나란히.
        '어디서 빼는지' 와 '지금 거기 있는지' 가 승인 판단의 전부다.
      -->
      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 32px" class="right">#</th>
            <th style="width: 150px">자리</th>
            <th style="width: 170px">SKU</th>
            <th style="width: 64px" class="right">놓음</th>
            <th style="width: 64px" class="right">남음</th>
            <th style="width: 70px" class="right">현재고</th>
            <th style="width: 70px" class="right">정정</th>
            <th style="width: 110px">줄 사유</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="l in target.lines"
            :key="l.lineSeq"
            :class="{ short: l.decrease && -l.qtyDelta > l.remainingQty }"
          >
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.locationId }}</span></td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td class="num">{{ num(l.putawayQty) }}</td>
            <td class="num">
              {{ num(l.remainingQty) }}
              <div v-if="l.correctedQty" class="small dim">정정 {{ signed(l.correctedQty) }}</div>
            </td>
            <td class="num dim">{{ num(l.qtyOnHand) }}</td>
            <td class="num">
              <strong :class="l.decrease ? 'danger' : 'warn'">{{ signed(l.qtyDelta) }}</strong>
            </td>
            <td class="small">{{ l.reasonName ?? '—' }}</td>
          </tr>
        </tbody>
      </table>

      <div v-if="shortLines.length" class="alert alert-danger mt-2">
        <span class="alert-icon">⛔</span>
        <span>
          요청 뒤에 다른 정정이 먼저 반영되어 뺄 것이 모자란 줄이
          {{ shortLines.length }} 개 있습니다. 이대로 승인하면 서버가 막습니다 —
          반려하고 남은 수량으로 다시 올리게 하세요.
        </span>
      </div>

      <div class="form-grid mt-2">
        <FormField
          v-model="remark"
          label="결재 사유"
          class="span-2"
          placeholder="반려는 필수. 예: 공급처 확인서 첨부 후 다시 올리세요"
          help="반려에는 사유가 필수입니다. 승인은 '요청한 대로' 라는 뜻이라 없어도 됩니다."
        />
      </div>

      <template #footer>
        <span class="left small dim">
          재고 · 입고 · 발주가 한 트랜잭션에서 움직입니다. 한 줄이라도 실패하면 전부 되돌아갑니다.
        </span>
        <button class="btn" :disabled="busy" @click="target = null">닫기</button>
        <button class="btn btn-danger" :disabled="!canApprove || busy" @click="reject()">
          반려
        </button>
        <button class="btn btn-primary" :disabled="!canApprove || busy" @click="approve()">
          <span v-if="busy" class="spinner"></span>
          승인
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.summary {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.summary-item {
  flex: 1 1 160px;
  padding: 10px 14px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--bg-card, #fff);
}
.summary-item.alarm {
  border-color: var(--c-red, #dc2626);
}
.summary-item.alarm .summary-value {
  color: var(--c-red, #dc2626);
}
.summary-label {
  display: block;
  font-size: 12px;
  color: var(--fg-dim, #6b7280);
}
.summary-value {
  font-size: 20px;
}
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.lines tr.short td {
  background: var(--c-red-soft, #fef2f2);
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.mt-1 {
  margin-top: 6px;
}
.mt-2 {
  margin-top: 12px;
}
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
