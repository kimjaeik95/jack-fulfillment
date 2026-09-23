<script setup>
/**
 * 출고지시 (OUT-PG-002).
 *
 * 창고가 집을 일의 목록이다. 무엇을 · 몇 개 · 어느 빈에서가 다 적혀 있어야
 * 사람이 움직일 수 있다.
 *
 * 만든 지시를 고치는 길은 두지 않는다. 창고에 이미 나간 작업이라, 잘못
 * 만들었으면 취소하고 다시 만든다 — 발주가 나간 뒤에는 못 고치는 것과
 * 같은 이유다.
 *
 * 취소는 <b>아직 아무도 안 잡은 지시</b>만 된다. 집기 시작한 뒤에 되돌리려면
 * 이미 집어 둔 물건을 어디에 놓을지부터 정해야 하는데, 그건 피킹(B섹터)의
 * 결품처리가 다룬다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as outboundApi from '@/api/outbound.js'
import { codeOptions } from '@/api/codes.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const router = useRouter()
const session = useSessionStore()
const hierarchy = useHierarchyStore()
const toast = useToastStore()

const canCancel = computed(() => session.can('OUT_ORDER', 'D'))
const readDenyReason = computed(() =>
  session.can('OUT_ORDER', 'R') ? '' : '출고지시를 볼 권한이 없습니다.',
)

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = outboundApi.PAGE_SIZE

const filters = reactive({
  keyword: '',
  plantId: '',
  outboundStatus: '',
  openOnly: 'Y',
  singleOnly: '',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.list({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      outboundStatus: filters.outboundStatus || null,
      openOnly: filters.openOnly || null,
      singleOnly: filters.singleOnly || null,
      page: page.value,
      size,
    })
    rows.value = data.rows
    total.value = data.total
  } catch (e) {
    loadError.value = e.message
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function search() {
  page.value = 1
  await fetchPage()
}

function resetFilters() {
  filters.keyword = ''
  filters.plantId = ''
  filters.outboundStatus = ''
  filters.openOnly = 'Y'
  filters.singleOnly = ''
  search()
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await fetchPage()
})

/* ── 상세 ───────────────────────────────────────────────────── */

const detail = ref(null)

async function openDetail(row) {
  try {
    detail.value = await outboundApi.detail(row.outboundSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/* ── 취소 ───────────────────────────────────────────────────── */

const askCancel = ref(null)
const cancelReason = reactive({ reasonCode: '', remark: '' })
const acting = ref(false)
const serverError = ref('')

function openCancel(row) {
  cancelReason.reasonCode = ''
  cancelReason.remark = ''
  serverError.value = ''
  askCancel.value = row
}

async function doCancel() {
  acting.value = true
  try {
    const out = await outboundApi.cancel(
      askCancel.value.outboundSeq,
      cancelReason.reasonCode,
      cancelReason.remark || null,
    )
    toast.success(`${out.outboundNo} 을(를) 거둬들였습니다.`)
    askCancel.value = null
    if (detail.value) detail.value = await outboundApi.detail(out.outboundSeq)
    await fetchPage()
  } catch (e) {
    serverError.value = e.message
  } finally {
    acting.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const columns = [
  { key: 'outboundNo', label: '지시번호', width: '170px', cls: 'code' },
  { key: 'orderNo', label: '주문', width: '170px' },
  { key: 'receiverName', label: '수령인', width: '90px' },
  { key: 'plantName', label: '센터', width: '110px' },
  { key: 'lineCount', label: '품목', width: '58px', align: 'right' },
  { key: 'totalInstructedQty', label: '지시 / 집음', width: '104px', align: 'right' },
  { key: 'outboundStatus', label: '상태', width: '90px', align: 'center' },
  { key: 'instructedAt', label: '지시일시', width: '140px' },
  { key: '_act', label: '', width: '70px', align: 'right' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">출고지시</h1>
        <p class="page-desc">
          창고가 집을 일입니다. 지시수량은 <strong>실제로 잡아 둔 수량</strong>이고,
          만든 뒤에는 고칠 수 없습니다 — 잘못 만들었으면 취소하고 다시 만듭니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-primary" @click="router.push({ name: 'outbound-targets' })">
          출고대상에서 만들기
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
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
        @enter="search()"
      />
      <FormField
        v-model="filters.plantId"
        label="센터"
        type="select"
        empty-option="전체"
        :options="hierarchy.plantOptions"
        @change="search()"
      />
      <FormField
        v-model="filters.outboundStatus"
        label="상태"
        type="select"
        empty-option="전체"
        :options="codeOptions('OUTBOUND_STATUS')"
        @change="search()"
      />
      <FormField
        v-model="filters.singleOnly"
        label="포장"
        type="select"
        empty-option="전체"
        :options="[
          { value: 'Y', label: '단포만' },
          { value: 'N', label: '다품목만' },
        ]"
        @change="search()"
      />
      <!-- 끝난 것(출고완료 · 취소)을 빼면 지금 할 일만 남는다 -->
      <FormField
        v-model="filters.openOnly"
        label="진행중만"
        type="select"
        empty-option="전체 보기"
        :options="[{ value: 'Y', label: '진행중만' }]"
        @change="search()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
        <button class="btn" @click="resetFilters()">초기화</button>
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
      empty-text="출고지시가 없습니다. 출고대상에서 만드세요."
      @row-click="openDetail"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div v-if="row.channelName" class="small dim">
          {{ row.channelName }}
          <template v-if="row.extOrderNo"> · {{ row.extOrderNo }}</template>
        </div>
      </template>

      <template #cell-lineCount="{ row, value }">
        {{ num(value) }}
        <div v-if="row.singlePack" class="small dim">단포</div>
      </template>

      <template #cell-totalInstructedQty="{ row, value }">
        <strong>{{ num(value) }}</strong>
        <span class="dim"> / </span>
        <span :class="row.totalPickedQty ? 'ok' : 'dim'">{{ num(row.totalPickedQty) }}</span>
        <div v-if="row.remainQty > 0 && row.totalPickedQty > 0" class="small warn">
          남음 {{ num(row.remainQty) }}
        </div>
      </template>

      <template #cell-outboundStatus="{ value }">
        <CodeBadge group="OUTBOUND_STATUS" :code="value" />
      </template>

      <template #cell-instructedAt="{ value }">
        <span class="small">{{ dt(value) }}</span>
      </template>

      <template #cell-_act="{ row }">
        <!--
          아직 아무도 안 잡은 지시만 거둔다. 집기 시작한 뒤에는 집어 둔
          물건을 어디에 놓을지부터 정해야 해서 피킹이 다룬다.
        -->
        <button
          v-if="row.created"
          class="btn btn-sm btn-danger"
          :disabled="!canCancel"
          @click.stop="openCancel(row)"
        >
          취소
        </button>
        <span v-else-if="row.working" class="small dim" title="작업이 시작되어 여기서는 못 거둡니다">
          작업중
        </span>
      </template>
    </DataTable>

    <div class="pager">
      <span class="small dim">총 {{ num(total) }}건 · {{ page }} / {{ totalPages }} 페이지</span>
      <div class="btn-row">
        <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">이전</button>
        <button class="btn btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">
          다음
        </button>
      </div>
    </div>

    <!-- ── 상세 ────────────────────────────────────────────── -->
    <ModalDialog
      v-if="detail"
      title="출고지시"
      :subtitle="detail.outboundNo"
      size="wide"
      @close="detail = null"
    >
      <div class="meta">
        <CodeBadge group="OUTBOUND_STATUS" :code="detail.outboundStatus" />
        <span>주문 <strong class="code">{{ detail.orderNo }}</strong></span>
        <span v-if="detail.channelName">{{ detail.channelName }}</span>
        <span>수령인 <strong>{{ detail.receiverName }}</strong></span>
        <span>센터 <strong>{{ detail.plantName }}</strong></span>
        <span v-if="detail.singlePack" class="tag">단포</span>
        <span>지시 <strong>{{ dt(detail.instructedAt) }}</strong></span>
        <span v-if="detail.instructedByName">{{ detail.instructedByName }}</span>
      </div>
      <div v-if="detail.cancelReason" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>취소 — {{ detail.cancelReason }}</span>
      </div>

      <table class="table lines">
        <thead>
          <tr>
            <th style="width: 44px">#</th>
            <th style="width: 170px">SKU</th>
            <th style="width: 180px">제품</th>
            <th style="width: 130px">집을 빈</th>
            <th style="width: 80px" class="right">지시</th>
            <th style="width: 80px" class="right">집음</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in detail.lines" :key="l.lineSeq">
            <td>{{ l.lineNo }}</td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.colorCode }} / {{ l.sizeCode }}</div>
            </td>
            <td class="small">
              {{ l.productName }}
              <div class="small dim">주문 {{ l.orderNo }} · {{ l.orderLineNo }}번 품목</div>
            </td>
            <!-- 할당이 고른 자리. 여러 곳이면 'A-01-03 외 1곳' -->
            <td class="code">{{ l.locationHint ?? '-' }}</td>
            <td class="right"><strong>{{ num(l.instructedQty) }}</strong></td>
            <td class="right" :class="l.pickedQty ? 'ok' : 'dim'">{{ num(l.pickedQty) }}</td>
          </tr>
        </tbody>
      </table>

      <p class="small dim mt-1">
        집은 수량은 피킹(4차 B섹터)이 채웁니다. 아직 피킹 화면이 없어 모두 0 입니다.
      </p>

      <template #footer>
        <button
          v-if="detail.created"
          class="btn btn-danger"
          :disabled="!canCancel"
          @click="openCancel(detail)"
        >
          지시 취소
        </button>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>

    <!-- ── 취소 — 사유가 필수다 ─────────────────────────────── -->
    <ModalDialog
      v-if="askCancel"
      title="출고지시 취소"
      :subtitle="askCancel.outboundNo"
      @close="askCancel = null"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>
      <p class="small">
        창고가 하기로 한 일을 거둬들입니다. <strong>잡아 둔 재고는 풀리지 않습니다</strong> —
        곧 다시 지시할 수도 있어서입니다. 재고를 놓으려면 주문 화면에서 할당을 해제하세요.
      </p>
      <div class="form-grid mt-2">
        <FormField
          v-model="cancelReason.reasonCode"
          label="취소 사유"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('REASON_OUT_CANCEL')"
          help="나중에 '이 주문 왜 안 나갔나' 를 묻게 됩니다."
        />
        <FormField
          v-model="cancelReason.remark"
          label="설명"
          placeholder="사유코드로 설명되지 않는 사정"
        />
      </div>
      <template #footer>
        <button class="btn" :disabled="acting" @click="askCancel = null">닫기</button>
        <button
          class="btn btn-danger"
          :disabled="!cancelReason.reasonCode || acting"
          @click="doCancel()"
        >
          <span v-if="acting" class="spinner"></span>
          지시 취소
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 6px;
  background: var(--bg-2, #f6f7f9);
}
.tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--line, #e5e7eb);
}
.lines th,
.lines td {
  vertical-align: top;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.mt-1 {
  margin-top: 6px;
}
.right {
  text-align: right;
}
.ok {
  color: var(--c-green, #16a34a);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
