<script setup>
/**
 * 송장 발급 · 취소 · 재발행 (PAC-PG-003, PAC-PG-004).
 *
 * 박스 하나에 송장 하나. <b>밖으로 나가는 유일한 식별자</b>다 — 박스번호는
 * 우리 안에서만 쓰는 이름이고, 고객이 배송조회에 넣는 것은 송장번호다.
 *
 * <b>번호는 사람이 적는다.</b> 택배사 연동(INT-IF-*)이 전부 개발 취소라
 * 우리가 번호를 만들 수 없다 — 만들면 라벨에 가짜 번호가 찍히고 고객이
 * 배송조회를 했을 때 아무것도 안 나온다. 그건 송장이 없는 것보다 나쁘다.
 *
 * 그래서 화면이 두 부분이다.
 *
 *   붙일 것   닫힌 박스 중 아직 송장이 없는 것. 번호를 적어 붙인다.
 *   붙인 것   발급된 송장. 취소 · 재발행 · 라벨 확인.
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import * as outboundApi from '@/api/outbound.js'
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

const canIssue = computed(() => session.can('OUT_WAYBILL', 'C'))
const canCancel = computed(() => session.can('OUT_WAYBILL', 'D'))

/* ── 붙일 것 — 송장 없는 닫힌 박스 ──────────────────────────── */

const pending = ref([])
const loadingPending = ref(false)

/**
 * 패킹이 진행된 지시를 훑어 송장 없는 닫힌 박스를 모은다.
 *
 * 서버에 '송장 없는 박스' 목록을 따로 두지 않았다. 지시마다 박스를 읽어야
 * 하는 질의라 목록으로 뽑으면 무거운데, 패킹 중인 지시는 한 번에 몇 건
 * 안 된다 — 화면에서 모으는 편이 가볍다.
 */
async function loadPending() {
  loadingPending.value = true
  try {
    const data = await outboundApi.list({
      plantId: filters.plantId || null,
      openOnly: 'Y',
      size: 100,
      sortBy: 'instructedAt',
      sortDir: 'asc',
    })
    const targets = (data.rows ?? []).filter((o) => o.boxCount > 0)
    const found = []
    for (const o of targets) {
      const [boxes, wbs] = await Promise.all([
        outboundApi.boxes(o.outboundSeq),
        outboundApi.waybillsOf(o.outboundSeq),
      ])
      const live = new Set(wbs.filter((w) => w.issued).map((w) => w.boxSeq))
      for (const b of boxes) {
        if (b.closed && !live.has(b.boxSeq)) found.push({ ...b, outbound: o })
      }
    }
    pending.value = found
  } catch (e) {
    toast.error(e.message)
    pending.value = []
  } finally {
    loadingPending.value = false
  }
}

/* ── 붙인 것 ────────────────────────────────────────────────── */

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = outboundApi.PAGE_SIZE

const filters = reactive({
  keyword: '',
  plantId: '',
  courierCode: '',
  waybillStatus: 'ISSUED',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.waybills({
      keyword: filters.keyword.trim() || null,
      plantId: filters.plantId || null,
      courierCode: filters.courierCode || null,
      waybillStatus: filters.waybillStatus || null,
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
  await Promise.all([fetchPage(), loadPending()])
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

onMounted(async () => {
  await hierarchy.loadPlants(false)
  await Promise.all([fetchPage(), loadPending()])
})

/* ── 발급 ───────────────────────────────────────────────────── */

const issuing = ref(null)
const issueForm = reactive({ courierCode: '', waybillNo: '', remark: '' })
const noInput = ref(null)
const busy = ref(false)
const formError = ref('')

async function openIssue(box) {
  // 택배사는 대개 고정이라 직전에 쓴 것을 기억한다 — 50 건을 붙이는데
  // 매번 고르게 하면 그 한 번이 그대로 시간이 된다.
  issueForm.courierCode = lastCourier.value || ''
  issueForm.waybillNo = ''
  issueForm.remark = ''
  formError.value = ''
  issuing.value = box
  await nextTick()
  noInput.value?.focus?.()
}

const lastCourier = ref(localStorage.getItem('wms.lastCourier') ?? '')

async function doIssue() {
  if (!issueForm.courierCode || !issueForm.waybillNo.trim()) return
  busy.value = true
  formError.value = ''
  try {
    const w = await outboundApi.issueWaybill(issuing.value.boxSeq, {
      courierCode: issueForm.courierCode,
      waybillNo: issueForm.waybillNo,
      remark: issueForm.remark || null,
    })
    toast.success(`${w.courierName} ${w.waybillNo}`)
    lastCourier.value = issueForm.courierCode
    localStorage.setItem('wms.lastCourier', issueForm.courierCode)
    issuing.value = null
    await search()
  } catch (e) {
    formError.value = e.message
    // 번호가 틀렸으면 그 칸을 비우고 다시 적게 한다
    issueForm.waybillNo = ''
    await nextTick()
    noInput.value?.focus?.()
  } finally {
    busy.value = false
  }
}

/* ── 취소 · 재발행 ──────────────────────────────────────────── */

const acting = ref(null)
const actMode = ref('cancel')
const actForm = reactive({ reasonCode: '', remark: '', courierCode: '', waybillNo: '' })

function openAct(w, mode) {
  actMode.value = mode
  actForm.reasonCode = ''
  actForm.remark = ''
  actForm.courierCode = w.courierCode
  actForm.waybillNo = ''
  formError.value = ''
  acting.value = w
}

async function doAct() {
  busy.value = true
  formError.value = ''
  try {
    if (actMode.value === 'cancel') {
      await outboundApi.cancelWaybill(acting.value.waybillSeq, {
        reasonCode: actForm.reasonCode,
        remark: actForm.remark || null,
      })
      toast.success(`${acting.value.waybillNo} 를 취소했습니다.`)
    } else {
      const w = await outboundApi.reissueWaybill(acting.value.waybillSeq, {
        courierCode: actForm.courierCode,
        waybillNo: actForm.waybillNo,
        reasonCode: actForm.reasonCode,
        remark: actForm.remark || null,
      })
      toast.success(`${w.courierName} ${w.waybillNo} 로 다시 뽑았습니다.`)
    }
    acting.value = null
    await search()
  } catch (e) {
    formError.value = e.message
  } finally {
    busy.value = false
  }
}

/* ── 라벨 확인 ──────────────────────────────────────────────── */

const labelOf = ref(null)

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const columns = [
  { key: 'waybillNo', label: '송장번호', width: '160px', cls: 'code' },
  { key: 'courierName', label: '택배사', width: '110px' },
  { key: 'receiverName', label: '수령인', width: '150px' },
  { key: 'outboundNo', label: '지시 / 박스', width: '175px' },
  { key: 'totalPackedQty', label: '개수', width: '60px', align: 'right' },
  { key: 'waybillStatus', label: '상태', width: '80px', align: 'center' },
  { key: 'issuedAt', label: '발급', width: '140px' },
  { key: '_act', label: '', width: '150px', align: 'right' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">송장</h1>
        <p class="page-desc">
          박스 하나에 송장 하나. <strong>번호는 택배사 프로그램에서 뽑아 여기 적습니다</strong> —
          연동이 없어 우리가 번호를 만들면 배송조회에 아무것도 안 나옵니다.
          고치는 기능은 없습니다: 잘못 적었으면 <strong>취소하고 다시 뽑습니다</strong>.
        </p>
      </div>
    </div>

    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!--
      붙일 것 — 닫힌 박스인데 송장이 없는 것. 이게 안 보이면 송장 없는
      박스가 인계를 기다리며 남는다.
    -->
    <div class="pending">
      <div class="pending-head">
        <strong>송장 붙일 박스</strong>
        <span v-if="loadingPending" class="spinner"></span>
        <span v-else class="dim">{{ pending.length }} 개</span>
        <span class="grow"></span>
        <button class="btn btn-sm" :disabled="loadingPending" @click="loadPending()">새로고침</button>
      </div>
      <div v-if="!pending.length && !loadingPending" class="small dim">
        붙일 박스가 없습니다. 패킹에서 박스를 닫으면 여기 나타납니다.
      </div>
      <div v-else class="pending-items">
        <button
          v-for="b in pending"
          :key="b.boxSeq"
          class="pend-chip"
          :disabled="!canIssue"
          @click="openIssue(b)"
        >
          <strong>{{ b.outbound.outboundNo }}</strong>
          <span>{{ b.boxNo }}번 박스</span>
          <span class="dim">{{ b.outbound.receiverName }}</span>
          <span class="small dim">{{ num(b.totalPackedQty) }}개</span>
        </button>
      </div>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색어"
        placeholder="송장번호 / 지시번호 / 주문번호 / 수령인"
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
        v-model="filters.courierCode"
        label="택배사"
        type="select"
        empty-option="전체"
        :options="codeOptions('COURIER')"
        @change="search()"
      />
      <FormField
        v-model="filters.waybillStatus"
        label="상태"
        type="select"
        empty-option="전체 (취소 포함)"
        :options="codeOptions('WAYBILL_STATUS')"
        @change="search()"
      />
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="waybillSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      empty-text="발급된 송장이 없습니다."
    >
      <template #cell-waybillNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <!-- 다시 뽑은 것이면 무엇을 대체했는지. 안 보이면 '왜 둘인가' 를 묻는다 -->
        <div v-if="row.reissued" class="small dim">재발행 ← {{ row.reissuedFromNo }}</div>
      </template>

      <template #cell-receiverName="{ row, value }">
        {{ value }}
        <div class="small dim ellip" :title="row.address">{{ row.address }}</div>
        <div v-if="row.deliveryMemo" class="small memo">📌 {{ row.deliveryMemo }}</div>
      </template>

      <template #cell-outboundNo="{ row, value }">
        <span class="code">{{ value }}</span>
        <div class="small dim">{{ row.boxNo }}번 박스 · {{ row.orderNo }}</div>
      </template>

      <template #cell-waybillStatus="{ row, value }">
        <CodeBadge group="WAYBILL_STATUS" :code="value" />
        <div v-if="row.cancelReason" class="small dim">{{ row.cancelReason }}</div>
      </template>

      <template #cell-issuedAt="{ row, value }">
        <span class="small">{{ dt(value) }}</span>
        <div v-if="row.issuedByName" class="small dim">{{ row.issuedByName }}</div>
      </template>

      <template #cell-_act="{ row }">
        <button class="btn btn-sm" @click="labelOf = row">라벨</button>
        <template v-if="row.issued">
          <button class="btn btn-sm" :disabled="!canCancel" @click="openAct(row, 'reissue')">
            재발행
          </button>
          <button
            class="btn btn-sm btn-danger"
            :disabled="!canCancel"
            @click="openAct(row, 'cancel')"
          >
            취소
          </button>
        </template>
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

    <!-- ── 발급 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="issuing"
      title="송장 붙이기"
      :subtitle="`${issuing.outbound.outboundNo} · ${issuing.boxNo}번 박스`"
      @close="issuing = null"
    >
      <div v-if="formError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ formError }}</span>
      </div>

      <div class="ship-to">
        <div><strong>{{ issuing.outbound.receiverName }}</strong></div>
        <div class="small">{{ issuing.outbound.orderNo }} · {{ num(issuing.totalPackedQty) }}개</div>
      </div>

      <p class="small dim">
        택배사 프로그램에서 송장을 뽑고 <strong>그 번호를 적으세요</strong>. 하이픈과 공백은
        지워집니다 — 같은 번호가 둘로 보이지 않게 하려는 것입니다.
      </p>

      <div class="form-grid mt-2">
        <FormField
          v-model="issueForm.courierCode"
          label="택배사"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('COURIER')"
          help="직전에 쓴 택배사가 미리 골라집니다."
        />
        <FormField
          ref="noInput"
          v-model="issueForm.waybillNo"
          label="송장번호"
          mono
          required
          placeholder="라벨의 번호를 그대로"
          @enter="doIssue()"
        />
        <FormField v-model="issueForm.remark" label="비고" placeholder="필요하면" />
      </div>

      <template #footer>
        <button class="btn" :disabled="busy" @click="issuing = null">닫기</button>
        <button
          class="btn btn-primary"
          :disabled="busy || !issueForm.courierCode || !issueForm.waybillNo.trim()"
          @click="doIssue()"
        >
          <span v-if="busy" class="spinner"></span>
          붙이기
        </button>
      </template>
    </ModalDialog>

    <!-- ── 취소 · 재발행 ────────────────────────────────────── -->
    <ModalDialog
      v-if="acting"
      :title="actMode === 'cancel' ? '송장 취소' : '송장 재발행'"
      :subtitle="`${acting.courierName} ${acting.waybillNo}`"
      @close="acting = null"
    >
      <div v-if="formError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ formError }}</span>
      </div>

      <p v-if="actMode === 'cancel'" class="small">
        이 번호로는 안 나갑니다. <strong>택배사에도 알려야 합니다</strong> — 화면에서
        지운다고 상대방이 아는 것은 아닙니다. 박스는 닫힌 채로 둡니다.
      </p>
      <p v-else class="small">
        원래 송장을 거두고 <strong>새 번호를 붙입니다</strong>. 택배사에서 새로 뽑은 번호를
        적으세요 — 취소한 번호는 다시 살아나지 않습니다.
      </p>

      <div class="form-grid mt-2">
        <FormField
          v-model="actForm.reasonCode"
          :label="actMode === 'cancel' ? '취소 사유' : '재발행 사유'"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('REASON_WB_CANCEL')"
        />
        <template v-if="actMode === 'reissue'">
          <FormField
            v-model="actForm.courierCode"
            label="택배사"
            type="select"
            required
            empty-option="선택하세요"
            :options="codeOptions('COURIER')"
            help="택배사를 바꿔 다시 뽑을 수도 있습니다."
          />
          <FormField
            v-model="actForm.waybillNo"
            label="새 송장번호"
            mono
            required
            placeholder="새로 뽑은 라벨의 번호"
          />
        </template>
        <FormField v-model="actForm.remark" label="설명" placeholder="사유코드로 설명되지 않는 사정" />
      </div>

      <template #footer>
        <button class="btn" :disabled="busy" @click="acting = null">닫기</button>
        <button
          class="btn"
          :class="actMode === 'cancel' ? 'btn-danger' : 'btn-primary'"
          :disabled="
            busy ||
            !actForm.reasonCode ||
            (actMode === 'reissue' && (!actForm.courierCode || !actForm.waybillNo.trim()))
          "
          @click="doAct()"
        >
          <span v-if="busy" class="spinner"></span>
          {{ actMode === 'cancel' ? '송장 취소' : '다시 뽑기' }}
        </button>
      </template>
    </ModalDialog>

    <!-- ── 라벨 확인 ────────────────────────────────────────── -->
    <ModalDialog
      v-if="labelOf"
      title="라벨 내용"
      :subtitle="labelOf.waybillNo"
      @close="labelOf = null"
    >
      <!--
        택배사 양식으로 인쇄하지 않는다. 라벨은 택배사 프로그램이 낸다 —
        여기서는 '이 번호가 누구에게 가는가' 만 확인한다. 붙일 때 옆 박스와
        바뀌는 사고를 막는 자리다.
      -->
      <div class="label">
        <div class="label-row">
          <span class="label-key">송장</span>
          <strong class="code big">{{ labelOf.waybillNo }}</strong>
          <span class="dim">{{ labelOf.courierName }}</span>
        </div>
        <div class="label-row">
          <span class="label-key">받는 분</span>
          <strong>{{ labelOf.receiverName }}</strong>
          <span>{{ labelOf.receiverPhone }}</span>
        </div>
        <div class="label-row">
          <span class="label-key">주소</span>
          <span>
            <template v-if="labelOf.zipCode">({{ labelOf.zipCode }}) </template>
            {{ labelOf.address }} {{ labelOf.addressDetail }}
          </span>
        </div>
        <div v-if="labelOf.deliveryMemo" class="label-row">
          <span class="label-key">배송요청</span>
          <span class="memo">{{ labelOf.deliveryMemo }}</span>
        </div>
        <div class="label-row">
          <span class="label-key">내용</span>
          <span>
            {{ labelOf.outboundNo }} · {{ labelOf.boxNo }}번 박스 ·
            {{ num(labelOf.totalPackedQty) }}개
          </span>
        </div>
        <div class="label-row">
          <span class="label-key">보내는 곳</span>
          <span>{{ labelOf.plantName }}</span>
        </div>
      </div>
      <p class="small dim mt-2">
        라벨 인쇄는 택배사 프로그램에서 합니다. 여기서는 붙일 때 헷갈리지 않도록
        내용만 확인합니다.
      </p>
      <template #footer>
        <button class="btn" @click="labelOf = null">닫기</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
/* 붙일 박스 — 안 보이면 송장 없는 박스가 인계를 기다리며 남는다 */
.pending {
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 6px;
  border: 1px solid var(--c-amber, #b45309);
  background: var(--bg-2, #f6f7f9);
}
.pending-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.pending-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.pend-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--line, #e5e7eb);
  background: var(--bg-1, #fff);
  cursor: pointer;
}
.ship-to {
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--bg-2, #f6f7f9);
  margin-bottom: 8px;
}
.label {
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 6px;
  padding: 12px;
}
.label-row {
  display: flex;
  gap: 10px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--line, #e5e7eb);
}
.label-row:last-child {
  border-bottom: 0;
}
.label-key {
  min-width: 64px;
  color: var(--text-2, #6b7280);
}
.big {
  font-size: 18px;
}
.ellip {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.memo {
  color: var(--c-amber, #b45309);
}
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.grow {
  flex: 1;
}
.mt-2 {
  margin-top: 10px;
}
</style>
