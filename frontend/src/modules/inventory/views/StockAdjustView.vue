<script setup>
/**
 * 재고조정 요청 (INV-PG-006).
 *
 * 장부와 실물이 다를 때 장부를 실물에 맞춘다. 총량이 바뀌므로 요청과
 * 승인을 나눈다 — 이동이나 판매불가 전환과 갈리는 지점이 그것이다.
 *
 * 한 전표에 여러 줄을 담는다. 낱개로 올리면 승인자가 같은 창고의 조정을
 * 수십 건 따로 열어 봐야 하고, 그러다 보면 읽지 않고 누른다.
 *
 * 목표수량만 입력받는다. 변동량은 요청 시점 장부수량과의 차이라 서버가
 * 계산한다 — 화면이 보낸 변동량을 믿으면 화면이 낡은 수량을 보고 있었을 때
 * 엉뚱한 값이 반영된다. 화면은 미리보기만 한다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { codeOptions } from '@/api/codes.js'
import * as opsApi from '@/api/stockOps.js'
import * as stockApi from '@/api/stock.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'
import StockPicker from '../components/StockPicker.vue'

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
  adjustStatus: '',
  fromDate: stockApi.daysAgo(30),
  toDate: stockApi.daysAgo(0),
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await opsApi.listAdjusts({ ...filters, page: page.value, size })
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
  { key: 'adjustNo', label: '전표번호', width: '150px', cls: 'code' },
  { key: 'warehouseName', label: '창고', width: '130px' },
  { key: 'reasonName', label: '사유', width: '110px' },
  { key: 'lineCount', label: '줄', width: '56px', align: 'right' },
  { key: 'totalDelta', label: '순변동', width: '80px', align: 'right' },
  { key: 'adjustStatus', label: '상태', width: '86px', align: 'center' },
  { key: 'requestedByName', label: '요청자', width: '90px' },
  { key: 'requestedAt', label: '요청시각', width: '120px' },
  { key: '_act', label: '', width: '120px', align: 'right' },
]

/* ── 작성 (장바구니식) ──────────────────────────────────────── */

const editing = ref(false)
const editSeq = ref(null)
const picking = ref(false)
const busy = ref(false)
const serverError = ref('')

const form = reactive({ plantId: '', warehouseId: '', reasonCode: '', remark: '' })
/** 담은 줄 — { stock, qtyField, qtyAfter, reasonCode, remark } */
const lines = ref([])

const pickedSeqs = computed(() => lines.value.map((l) => l.stock.stockSeq))

function openCreate() {
  editSeq.value = null
  Object.assign(form, { plantId: '', warehouseId: '', reasonCode: '', remark: '' })
  lines.value = []
  serverError.value = ''
  editing.value = true
}

/**
 * 담을 재고를 고른다.
 *
 * 첫 줄이 전표의 창고를 정한다. 한 전표는 한 창고만 담으므로, 고르는 순간
 * 창고가 잠긴다 — 승인 권한이 창고 단위로 나뉘기 때문이다.
 */
function addLine(row) {
  picking.value = false
  if (!form.plantId) {
    form.plantId = row.plantId
    form.warehouseId = row.warehouseId
  }
  lines.value = [
    ...lines.value,
    {
      stock: row,
      qtyField: 'ON_HAND',
      // 목표의 출발점을 현재 수량으로 둔다. 0 으로 두면 저장할 때마다
      // "전량 없앨 뻔했다" 는 실수가 난다.
      qtyAfter: row.qtyOnHand,
      reasonCode: '',
      remark: '',
    },
  ]
}

function removeLine(i) {
  lines.value = lines.value.filter((_, idx) => idx !== i)
  if (!lines.value.length) {
    form.plantId = ''
    form.warehouseId = ''
  }
}

/** 수량항목을 바꾸면 목표의 출발점도 그 항목의 현재값으로 옮긴다 */
function onFieldChange(line) {
  line.qtyAfter =
    line.qtyField === 'ON_HAND' ? line.stock.qtyOnHand : line.stock.qtyUnsellable
}

const currentOf = (line) =>
  line.qtyField === 'ON_HAND' ? line.stock.qtyOnHand : line.stock.qtyUnsellable

const deltaOf = (line) => Number(line.qtyAfter) - currentOf(line)

const lineError = (line) => {
  if (line.qtyAfter === '' || line.qtyAfter === null) return '목표수량을 입력하세요.'
  if (Number(line.qtyAfter) < 0) return '0 이상이어야 합니다.'
  if (deltaOf(line) === 0) return '바뀌는 것이 없습니다.'
  return ''
}

const formValid = computed(
  () =>
    form.reasonCode &&
    lines.value.length > 0 &&
    lines.value.every((l) => !lineError(l)) &&
    !busy.value,
)

/** 순변동 — 늘리는 줄과 줄이는 줄이 섞이면 상쇄된다 */
const previewDelta = computed(() =>
  lines.value.reduce((sum, l) => sum + (lineError(l) ? 0 : deltaOf(l)), 0),
)

async function submit() {
  busy.value = true
  serverError.value = ''
  try {
    const payload = {
      plantId: form.plantId,
      warehouseId: form.warehouseId,
      reasonCode: form.reasonCode,
      remark: form.remark || null,
      lines: lines.value.map((l) => ({
        stockSeq: l.stock.stockSeq,
        qtyField: l.qtyField,
        qtyAfter: Number(l.qtyAfter),
        reasonCode: l.reasonCode || null,
        remark: l.remark || null,
      })),
    }
    const { adjust } = editSeq.value
      ? await opsApi.updateAdjust(editSeq.value, payload)
      : await opsApi.createAdjust(payload)
    toast.success(`${adjust.adjustNo} — ${adjust.lineCount} 줄을 올렸습니다.`)
    editing.value = false
    await search()
  } catch (e) {
    serverError.value = e.message
  } finally {
    busy.value = false
  }
}

/* ── 상세 · 취소 ────────────────────────────────────────────── */

const detail = ref(null)
const askCancel = ref(null)
const canceling = ref(false)

async function openDetail(row) {
  try {
    detail.value = await opsApi.detailAdjust(row.adjustSeq)
  } catch (e) {
    loadError.value = e.message
  }
}

/** 승인 전이면 담긴 줄을 그대로 꺼내 고칠 수 있다 */
async function openEdit(row) {
  try {
    const full = await opsApi.detailAdjust(row.adjustSeq)
    editSeq.value = full.adjustSeq
    Object.assign(form, {
      plantId: full.plantId,
      warehouseId: full.warehouseId,
      reasonCode: full.reasonCode,
      remark: full.remark ?? '',
    })
    lines.value = full.lines.map((l) => ({
      stock: {
        stockSeq: l.stockSeq,
        locationFullCode: l.locationFullCode,
        skuId: l.skuId,
        productName: l.productName,
        plantId: full.plantId,
        warehouseId: full.warehouseId,
        // 목표의 기준은 '지금' 장부수량이다. 요청 시점 값을 쓰면 그 사이의
        // 변동을 모르는 채로 다시 올리게 된다.
        qtyOnHand: l.qtyField === 'ON_HAND' ? l.qtyCurrent : 0,
        qtyUnsellable: l.qtyField === 'UNSELLABLE' ? l.qtyCurrent : 0,
      },
      qtyField: l.qtyField,
      qtyAfter: l.qtyAfter,
      reasonCode: l.reasonCode ?? '',
      remark: l.remark ?? '',
    }))
    serverError.value = ''
    editing.value = true
  } catch (e) {
    loadError.value = e.message
  }
}

async function doCancel() {
  canceling.value = true
  try {
    await opsApi.cancelAdjust(askCancel.value.adjustSeq, '요청 철회')
    toast.success(`${askCancel.value.adjustNo} 을(를) 거둬들였습니다.`)
    askCancel.value = null
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
    askCancel.value = null
  } finally {
    canceling.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const signed = (v) => (v > 0 ? `+${nf.format(v)}` : nf.format(v))
const stamp = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const canCreate = computed(() => session.can('INV_ADJUST', 'C'))
const createDenyReason = computed(() => session.denyReason('INV_ADJUST', 'C'))
const myId = computed(() => session.currentUserId ?? '')
/** 남이 올린 요청은 고치거나 거둘 수 없다. 승인자는 반려로 돌려보낸다. */
const mine = (row) => row.requestedBy === myId.value

const QTY_FIELD_OPTIONS = [
  { value: 'ON_HAND', label: '보유' },
  { value: 'UNSELLABLE', label: '판매불가' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">재고조정 요청</h1>
        <p class="page-desc">
          장부와 실물이 다를 때 <strong>장부를 실물에 맞춥니다</strong>. 총량이 바뀌므로
          센터 관리자의 승인을 거칩니다 — 요청만으로는 재고가 변하지 않습니다.
          한 전표에 여러 줄을 담으세요. 낱개로 올리면 승인자가 수십 건을 따로 열어 봐야 합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '조정 요청'"
          @click="openCreate()"
        >
          + 조정 요청
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
          placeholder="전표번호 / 사유 / 비고"
          @enter="search()"
        />
        <FormField v-model="filters.fromDate" label="시작일" type="date" @change="search()" />
        <FormField v-model="filters.toDate" label="종료일" type="date" @change="search()" />
        <FormField
          v-model="filters.plantId"
          label="플랜트"
          type="select"
          empty-option="전체"
          :options="hierarchy.plantOptions"
          @change="filters.warehouseId = ''; search()"
        />
        <FormField
          v-model="filters.adjustStatus"
          label="상태"
          type="select"
          empty-option="전체"
          :options="codeOptions('ADJUST_STATUS')"
          @change="search()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="adjustSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        clickable
        :muted-when="(a) => a.adjustStatus === 'CANCELED' || a.adjustStatus === 'REJECTED'"
        empty-text="조건에 맞는 조정 전표가 없습니다."
        @row-click="openDetail"
      >
        <template #cell-lineCount="{ value }">{{ num(value) }}</template>
        <template #cell-totalDelta="{ value }">
          <strong :class="value > 0 ? 'ok' : value < 0 ? 'danger' : 'dim'">
            {{ signed(value ?? 0) }}
          </strong>
        </template>
        <template #cell-adjustStatus="{ value }">
          <CodeBadge group="ADJUST_STATUS" :code="value" />
        </template>
        <template #cell-requestedAt="{ value }">
          <span class="small">{{ stamp(value) }}</span>
        </template>
        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <template v-if="row.pending && mine(row)">
              <button class="btn btn-sm" @click.stop="openEdit(row)">수정</button>
              <button class="btn btn-sm btn-danger" @click.stop="askCancel = row">취소</button>
            </template>
            <span v-else-if="row.pending" class="small dim" title="남이 올린 요청은 반려로 돌려보냅니다">
              {{ row.requestedByName }} 요청
            </span>
          </div>
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

    <!-- ── 작성 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="editing"
      :title="editSeq ? '조정 요청 수정' : '조정 요청'"
      size="wide"
      @close="editing = false"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.reasonCode"
          label="조정 사유"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('REASON_ADJUST')"
          help="줄마다 다른 사유를 넣을 수도 있습니다. 비우면 이 사유를 따릅니다."
        />
        <FormField
          :model-value="form.warehouseId ? `${form.plantId} · ${form.warehouseId}` : ''"
          label="창고"
          readonly
          :placeholder="'첫 줄을 담으면 정해집니다'"
          help="한 전표는 한 창고만 담습니다. 승인 권한이 창고 단위로 나뉘기 때문입니다."
        />
        <FormField v-model="form.remark" label="비고" class="span-2" />
      </div>

      <div class="lines-head">
        <strong>조정할 재고 {{ lines.length }} 줄</strong>
        <button class="btn btn-sm btn-primary" @click="picking = true">+ 재고 담기</button>
      </div>

      <div v-if="!lines.length" class="empty-note">
        담은 재고가 없습니다. '재고 담기' 로 한 줄 이상 담으세요.
      </div>

      <table v-else class="table lines">
        <thead>
          <tr>
            <th style="width: 170px">재고주소</th>
            <th style="width: 150px">SKU</th>
            <th style="width: 100px">수량항목</th>
            <th style="width: 80px" class="right">현재</th>
            <th style="width: 100px">목표</th>
            <th style="width: 76px" class="right">변동</th>
            <th style="width: 120px">사유</th>
            <th style="width: 40px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in lines" :key="l.stock.stockSeq + l.qtyField">
            <td>
              <span class="code">{{ l.stock.locationFullCode }}</span>
            </td>
            <td>
              <span class="code">{{ l.stock.skuId }}</span>
              <div class="small dim">{{ l.stock.productName }}</div>
            </td>
            <td>
              <select v-model="l.qtyField" class="select" @change="onFieldChange(l)">
                <option v-for="o in QTY_FIELD_OPTIONS" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </td>
            <td class="num">{{ num(currentOf(l)) }}</td>
            <td>
              <input v-model.number="l.qtyAfter" type="number" class="input" min="0" />
            </td>
            <td class="num">
              <strong :class="deltaOf(l) > 0 ? 'ok' : deltaOf(l) < 0 ? 'danger' : 'dim'">
                {{ signed(deltaOf(l)) }}
              </strong>
            </td>
            <td>
              <select v-model="l.reasonCode" class="select">
                <option value="">헤더 사유</option>
                <option v-for="o in codeOptions('REASON_ADJUST')" :key="o.value" :value="o.value">
                  {{ o.label }}
                </option>
              </select>
            </td>
            <td>
              <button class="btn btn-sm btn-danger" @click="removeLine(i)">×</button>
            </td>
          </tr>
          <tr v-for="(l, i) in lines.filter((x) => lineError(x))" :key="`e${i}`" class="err-row">
            <td colspan="8" class="small danger">
              {{ l.stock.locationFullCode }} / {{ l.stock.skuId }} — {{ lineError(l) }}
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="lines.length" class="preview">
        <span class="small dim">순변동</span>
        <strong :class="previewDelta > 0 ? 'ok' : previewDelta < 0 ? 'danger' : 'dim'">
          {{ signed(previewDelta) }}
        </strong>
        <span class="small dim">
          늘리는 줄과 줄이는 줄이 섞이면 상쇄됩니다. 규모가 아니라 순변동입니다.
        </span>
      </div>

      <template #footer>
        <span class="left small dim">
          승인되기 전까지는 재고가 바뀌지 않습니다. 변동량은 저장할 때 서버가 다시 계산합니다.
        </span>
        <button class="btn" :disabled="busy" @click="editing = false">취소</button>
        <button class="btn btn-primary" :disabled="!formValid" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          {{ editSeq ? '저장' : '요청 올리기' }}
        </button>
      </template>
    </ModalDialog>

    <StockPicker
      v-if="picking"
      title="조정할 재고 담기"
      :plant-id="form.plantId"
      :warehouse-id="form.warehouseId"
      :lock-warehouse="!!form.warehouseId"
      :picked-seqs="pickedSeqs"
      @pick="addLine"
      @close="picking = false"
    />

    <!-- ── 상세 ─────────────────────────────────────────────── -->
    <ModalDialog
      v-if="detail"
      :title="detail.adjustNo"
      :subtitle="`${detail.plantName} · ${detail.warehouseName}`"
      size="wide"
      @close="detail = null"
    >
      <div class="detail-head">
        <CodeBadge group="ADJUST_STATUS" :code="detail.adjustStatus" />
        <span>사유 <strong>{{ detail.reasonName }}</strong></span>
        <span>요청 <strong>{{ detail.requestedByName }}</strong> · {{ stamp(detail.requestedAt) }}</span>
        <span v-if="detail.decidedBy">
          처리 <strong>{{ detail.decidedByName }}</strong> · {{ stamp(detail.decidedAt) }}
        </span>
      </div>
      <p v-if="detail.remark" class="small">{{ detail.remark }}</p>
      <div v-if="detail.decideRemark" class="alert alert-warn mt-1">
        <span class="alert-icon">⚠</span><span>{{ detail.decideRemark }}</span>
      </div>

      <div v-if="detail.staleLineCount" class="alert alert-info mt-2">
        <span class="alert-icon">ℹ</span>
        <span>
          요청한 뒤 장부가 움직인 줄이 <strong>{{ detail.staleLineCount }}개</strong> 있습니다.
          승인하면 반영되는 것은 목표수량이 아니라 변동량이라, 결과가 목표와 다를 수 있습니다.
        </span>
      </div>

      <table class="table lines mt-2">
        <thead>
          <tr>
            <th style="width: 40px" class="right">#</th>
            <th style="width: 170px">재고주소</th>
            <th style="width: 150px">SKU</th>
            <th style="width: 84px">수량항목</th>
            <th style="width: 80px" class="right">요청시점</th>
            <th style="width: 80px" class="right">현재</th>
            <th style="width: 80px" class="right">목표</th>
            <th style="width: 70px" class="right">변동</th>
            <th style="width: 110px">사유</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in detail.lines" :key="l.lineSeq" :class="{ stale: l.stale }">
            <td class="num">{{ l.lineNo }}</td>
            <td><span class="code">{{ l.locationFullCode }}</span></td>
            <td>
              <span class="code">{{ l.skuId }}</span>
              <div class="small dim">{{ l.productName }}</div>
            </td>
            <td class="small">{{ l.qtyField === 'ON_HAND' ? '보유' : '판매불가' }}</td>
            <td class="num">{{ num(l.qtyBefore) }}</td>
            <td class="num">
              <span :class="{ warn: l.stale }">{{ num(l.qtyCurrent) }}</span>
              <div v-if="l.stale" class="small warn">움직임</div>
            </td>
            <td class="num">{{ num(l.qtyAfter) }}</td>
            <td class="num">
              <strong :class="l.increase ? 'ok' : 'danger'">{{ signed(l.qtyDelta) }}</strong>
            </td>
            <td class="small">{{ l.reasonName ?? detail.reasonName }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <span class="left small dim">
          {{ detail.applied
            ? '이미 재고에 반영되었습니다. 되돌리려면 반대 방향으로 한 번 더 올리세요.'
            : '아직 재고에 반영되지 않았습니다.' }}
        </span>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askCancel"
      title="조정 요청 취소"
      :message="`${askCancel.adjustNo} 을(를) 거둬들입니까?`"
      detail="지우지 않고 '취소' 로 남습니다. 올렸다가 거둔 사실도 기록입니다 — 같은 재고에 대해 올렸다 거두기를 반복하는 것이 보이면 그 자리에 다른 문제가 있다는 뜻입니다."
      confirm-label="거둬들이기"
      danger
      :busy="canceling"
      @cancel="askCancel = null"
      @confirm="doCancel()"
    />
  </div>
</template>

<style scoped>
.lines-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 14px 0 8px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.lines th,
.lines td {
  padding: 6px 8px;
  vertical-align: middle;
}
.lines .select,
.lines .input {
  min-height: 28px;
  padding: 3px 6px;
  font-size: 13px;
  width: 100%;
}
.err-row td {
  padding-top: 0;
  border-top: none;
}
.empty-note {
  padding: 24px 16px;
  text-align: center;
  color: var(--fg-dim, #6b7280);
  font-size: 13px;
}
.preview {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--bg-soft, #f8fafc);
}
.detail-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.stale td {
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
.mt-1 {
  margin-top: 6px;
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
