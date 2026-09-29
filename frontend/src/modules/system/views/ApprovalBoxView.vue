<script setup>
/**
 * 승인 작업함 (COM-PG-008).
 *
 * 결재할 것이 모여 있는 곳. 지금은 구매요청 결재가 구매요청 화면에,
 * 재고조정 승인이 재고조정 화면에, 입고정정 승인이 입고정정 화면에 흩어져
 * 있어서 아침에 "오늘 결재할 게 뭐지" 를 알려면 화면을 세 곳 돈다.
 *
 * <b>내가 올린 것도 보여 준다.</b> 결재는 못 하지만 (P004 · 직무분리)
 * '내가 올린 게 아직 안 나갔다' 를 아는 것이 이 화면의 쓸모 중 하나다.
 * 대신 누를 수 없다는 것을 줄에서 바로 말해 준다 — 열어 보고 나서 막히면
 * 그 왕복이 전부 헛걸음이다.
 *
 * <b>부분승인은 여기서 안 한다.</b> 목록에서 할 수 있는 것은 '봤고 괜찮다'
 * 까지고, 줄마다 수량을 고치는 일은 문서를 보고 해야 한다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as approvalApi from '@/api/approval.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'

const router = useRouter()
const session = useSessionStore()

const readDenyReason = computed(() => session.denyReason('SYS_APPROVAL', 'R'))

const items = ref([])
const hidden = ref([])
const mineCount = ref(0)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')

const tab = ref('pending')
const historyRows = ref([])
const historyMine = ref(false)

async function reload() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await approvalApi.pending(keyword.value.trim() || null)
    items.value = data.items
    hidden.value = data.hidden
    mineCount.value = data.mineCount
  } catch (e) {
    loadError.value = e.message
    items.value = []
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  loading.value = true
  try {
    historyRows.value = await approvalApi.history({ mineOnly: historyMine.value, limit: 50 })
  } catch (e) {
    loadError.value = e.message
    historyRows.value = []
  } finally {
    loading.value = false
  }
}

async function goTab(t) {
  tab.value = t
  if (t === 'history') await loadHistory()
  else await reload()
}

onMounted(reload)

/** 내가 올린 것인가 — 자기 결재는 막힌다 (P004) */
const isMine = (row) => !!session.currentUserId && row.requestedBy === session.currentUserId

/* ── 승인 · 반려 ───────────────────────────────────────────── */

const dlg = ref(null)
const form = reactive({ action: 'approve', reason: '' })
const busy = ref(false)
const dlgError = ref('')

function open(row, action) {
  dlg.value = row
  form.action = action
  form.reason = ''
  dlgError.value = ''
}

async function submit() {
  if (form.action === 'reject' && !form.reason.trim()) {
    dlgError.value = '반려 사유를 적으세요. 올린 사람이 무엇을 고쳐야 할지 알아야 다시 올립니다.'
    return
  }
  busy.value = true
  dlgError.value = ''
  try {
    const { kind, seq } = dlg.value
    if (form.action === 'approve') await approvalApi.approve(kind, seq, form.reason || null)
    else await approvalApi.reject(kind, seq, form.reason)
    dlg.value = null
    await reload()
  } catch (e) {
    dlgError.value = e.message
  } finally {
    busy.value = false
  }
}

/** 그 문서 화면으로 — 부분승인이나 자세히 볼 때 */
function goTo(row) {
  if (!row.route) return
  router.push({ name: row.route, query: { keyword: row.no } })
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const columns = [
  { key: 'kindLabel', label: '문서', width: '92px' },
  { key: 'no', label: '번호', width: '170px' },
  { key: 'label', label: '내용', width: '200px' },
  { key: 'totalQty', label: '수량', width: '86px', align: 'right' },
  { key: 'requestedByName', label: '올린 사람', width: '120px' },
  { key: 'waitingDays', label: '대기', width: '70px', align: 'right' },
  { key: '_act', label: '', width: '170px', align: 'right' },
]

const historyColumns = [
  { key: 'kindLabel', label: '문서', width: '92px' },
  { key: 'no', label: '번호', width: '170px' },
  { key: 'actionType', label: '처리', width: '80px' },
  { key: 'reason', label: '사유', width: '260px' },
  { key: 'actorName', label: '처리한 사람', width: '120px' },
  { key: 'when', label: '언제', width: '130px' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">승인 작업함</h1>
        <p class="page-desc">
          결재할 것이 모여 있습니다 — 구매요청 · 재고조정 · 입고정정.
          <strong>오래 묵은 것이 위로</strong> 옵니다. 부분승인은 문서 화면에서 하세요.
        </p>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <div class="tabs mb-2">
      <button class="btn" :class="{ 'btn-primary': tab === 'pending' }" @click="goTab('pending')">
        결재 대기{{ items.length ? ` (${items.length})` : '' }}
      </button>
      <button class="btn" :class="{ 'btn-primary': tab === 'history' }" @click="goTab('history')">
        처리이력
      </button>
    </div>

    <!-- ── 결재 대기 ──────────────────────────────────────── -->
    <div v-if="tab === 'pending'" class="card">
      <div class="toolbar">
        <FormField
          v-model="keyword"
          class="grow"
          label="검색어"
          placeholder="문서번호 / 센터 · 창고 / 올린 사람"
          @enter="reload()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="reload()">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <!-- 못 보는 것과 없는 것을 구분해 준다 -->
      <div v-if="hidden.length" class="alert alert-warn m-2">
        <span class="alert-icon">⚠</span>
        <span>
          결재 권한이 없어 <strong>{{ hidden.join(' · ') }}</strong> 은(는) 목록에서 빠졌습니다.
        </span>
      </div>

      <div v-if="mineCount > 0" class="alert m-2">
        <span class="alert-icon">🔒</span>
        <span>
          {{ mineCount }}건은 <strong>본인이 올린 문서</strong>라 결재할 수 없습니다.
          혼자 올리고 혼자 승인하면 통제 없이 나갑니다 — 다른 결재자에게 부탁하세요.
        </span>
      </div>

      <div v-if="!loading && !items.length" class="alert alert-ok m-2">
        <span class="alert-icon">✅</span>
        <span>결재할 것이 없습니다.</span>
      </div>

      <DataTable
        :columns="columns"
        :rows="items"
        row-key="no"
        :loading="loading"
        :page-size="20"
        :muted-when="isMine"
        empty-text="결재할 것이 없습니다."
      >
        <template #cell-no="{ row, value }">
          <span class="code">{{ value }}</span>
          <div v-if="row.plantName" class="small dim">{{ row.plantName }}</div>
        </template>

        <template #cell-label="{ row, value }">
          {{ value }}
          <div v-if="row.reasonName" class="small dim">{{ row.reasonName }}</div>
        </template>

        <template #cell-totalQty="{ row, value }">
          {{ num(value) }}
          <div class="small dim">{{ num(row.lineCount) }}품목</div>
        </template>

        <template #cell-requestedByName="{ row, value }">
          {{ value }}
          <div v-if="isMine(row)" class="small danger">본인</div>
        </template>

        <!-- 오래 묵을수록 눈에 띄게. 결재함에서 가장 나쁜 것은
             오래된 것이 아래로 밀려 영영 안 보이는 것이다 -->
        <template #cell-waitingDays="{ value }">
          <span :class="value >= 3 ? 'danger' : ''">{{ value }}일</span>
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button class="btn btn-sm" @click="goTo(row)">문서</button>
            <button
              class="btn btn-sm btn-primary"
              :disabled="isMine(row)"
              :title="isMine(row) ? '본인이 올린 문서는 결재할 수 없습니다' : '승인'"
              @click="open(row, 'approve')"
            >
              승인
            </button>
            <button
              class="btn btn-sm btn-danger"
              :disabled="isMine(row)"
              :title="isMine(row) ? '본인이 올린 문서는 결재할 수 없습니다' : '반려'"
              @click="open(row, 'reject')"
            >
              반려
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ── 처리이력 ───────────────────────────────────────── -->
    <div v-else class="card">
      <div class="toolbar">
        <FormField
          v-model="historyMine"
          label="범위"
          type="select"
          :options="[
            { value: false, label: '전체' },
            { value: true, label: '내가 처리한 것만' },
          ]"
          @change="loadHistory()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="loadHistory()">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <DataTable
        :columns="historyColumns"
        :rows="historyRows"
        row-key="when"
        :loading="loading"
        :page-size="20"
        empty-text="처리한 것이 없습니다."
      >
        <template #cell-no="{ value }"><span class="code">{{ value }}</span></template>
        <template #cell-reason="{ value }">
          <span v-if="value" class="small">{{ value }}</span>
          <span v-else class="small dim">-</span>
        </template>
        <template #cell-when="{ value }"><span class="small">{{ dt(value) }}</span></template>
      </DataTable>

      <p class="small dim m-2">
        감사로그에서 읽어 옵니다. 결재는 이미 거기에 다 적히고 있어서, 이력용 표를 따로
        두면 같은 사실이 두 군데 쌓이고 둘이 어긋나는 날이 옵니다.
      </p>
    </div>

    <ModalDialog
      v-if="dlg"
      :title="form.action === 'approve' ? '승인' : '반려'"
      :subtitle="`${dlg.kindLabel} ${dlg.no} · ${dlg.requestedByName}`"
      @close="dlg = null"
    >
      <div v-if="dlgError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ dlgError }}</span>
      </div>

      <div v-if="form.action === 'approve'" class="alert alert-warn mb-2">
        <span class="alert-icon">⚠</span>
        <span>
          <strong>{{ num(dlg.totalQty) }}개 / {{ num(dlg.lineCount) }}품목</strong>을
          요청한 그대로 승인합니다.
          <template v-if="dlg.kind === 'INV_ADJUST'">
            <br />재고조정은 승인하는 순간 <strong>재고가 실제로 움직입니다.</strong>
          </template>
          <template v-else-if="dlg.kind === 'INB_CORRECT'">
            <br />입고정정은 승인하는 순간 <strong>재고 · 입고 · 발주 기입고를 함께 되감습니다.</strong>
          </template>
          <br />수량을 고치려면 취소하고 <strong>문서</strong> 버튼으로 가세요.
        </span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.reason"
          class="span-2"
          label="의견"
          type="textarea"
          :required="form.action === 'reject'"
          :placeholder="form.action === 'reject'
            ? '무엇을 고쳐야 다시 올릴 수 있는지 적으세요'
            : '비워 둘 수 있습니다'"
        />
      </div>

      <template #footer>
        <span class="left small dim">
          권한과 직무분리는 저장 시 서버가 다시 판정합니다.
        </span>
        <button class="btn" :disabled="busy" @click="dlg = null">취소</button>
        <button
          class="btn"
          :class="form.action === 'approve' ? 'btn-primary' : 'btn-danger'"
          :disabled="busy"
          @click="submit()"
        >
          <span v-if="busy" class="spinner"></span>
          {{ form.action === 'approve' ? '승인' : '반려' }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 6px;
}
.danger {
  color: var(--c-red, #dc2626);
  font-weight: 600;
}
</style>
