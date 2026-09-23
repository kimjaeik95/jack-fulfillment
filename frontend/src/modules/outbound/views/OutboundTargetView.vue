<script setup>
/**
 * 출고대상 (OUT-PG-001).
 *
 * 할당까지 끝났는데 아직 창고로 안 넘긴 주문. 여기서 골라 출고지시를
 * 만든다 (OUT-PG-002).
 *
 * 이 화면이 없으면 할당만 되고 안 나가는 주문이 생긴다. 그 주문이 잡아
 * 둔 재고는 다른 주문이 쓰지도 못한 채 묶여 있어서, 결품이 아닌데
 * 결품처럼 보이기 시작한다.
 *
 * 여러 건을 골라 한 번에 넘긴다. 한 건씩 눌러야 하면 50 건을 50 번
 * 눌러야 한다. 한 건이 실패해도 나머지는 만들어지고, 안 된 것은 이유를
 * 보여 준다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as outboundApi from '@/api/outbound.js'
import * as channelApi from '@/api/channel.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import ModalDialog from '@/components/ModalDialog.vue'

const router = useRouter()
const session = useSessionStore()
const hierarchy = useHierarchyStore()
const toast = useToastStore()

const canInstruct = computed(() => session.can('OUT_ORDER', 'C'))
const readDenyReason = computed(() =>
  session.can('OUT_TARGET', 'R') ? '' : '출고대상을 볼 권한이 없습니다.',
)

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = outboundApi.PAGE_SIZE

const filters = reactive({ keyword: '', channelId: '', plantId: '', singleOnly: '' })
const channels = ref([])

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await outboundApi.targets({
      keyword: filters.keyword.trim() || null,
      channelId: filters.channelId || null,
      plantId: filters.plantId || null,
      singleOnly: filters.singleOnly || null,
      page: page.value,
      size,
    })
    rows.value = data.rows
    total.value = data.total
    // 목록이 바뀌면 고른 것도 지운다. 안 보이는 줄이 골라진 채로 남으면
    // '3 건 지시' 를 눌렀는데 화면에 없던 주문이 나간다.
    picked.value = []
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
  filters.channelId = ''
  filters.plantId = ''
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
  try {
    const data = await channelApi.list({ useYn: 'Y', size: 0 })
    channels.value = data.rows
  } catch {
    channels.value = []
  }
  await fetchPage()
})

/* ── 고르기 ─────────────────────────────────────────────────── */

const picked = ref([])

const isPicked = (row) => picked.value.some((r) => r.orderSeq === row.orderSeq)

/**
 * 센터가 둘로 나뉜 주문은 못 고른다.
 *
 * 지시는 센터 한 곳의 작업이라 한 장으로 만들 수 없다. 고를 수 있게 두면
 * 저장할 때야 거절당하고, 어느 건이 문제였는지 다시 찾아야 한다.
 */
const blocked = (row) => row.multiPlant

function toggle(row) {
  if (blocked(row)) {
    toast.error(`${row.orderNo} 은(는) 잡아 둔 재고가 센터 두 곳에 나뉘어 있습니다.`)
    return
  }
  picked.value = isPicked(row)
    ? picked.value.filter((r) => r.orderSeq !== row.orderSeq)
    : [...picked.value, row]
}

function pickAll() {
  const able = rows.value.filter((r) => !blocked(r))
  picked.value = picked.value.length === able.length ? [] : able
}

const pickedQty = computed(() => picked.value.reduce((s, r) => s + (r.allocatedQty ?? 0), 0))

/* ── 펼쳐서 품목 보기 ─────────────────────────────────────────── */

/**
 * 주문번호만 보고는 무엇을 내보내는지 알 수 없다.
 *
 * 목록에는 요약만 싣는다 — 줄이 다섯인 주문까지 다 적으면 목록이 그것만으로
 * 채워진다. 확인하고 싶을 때 펼치면 줄 전체를 가져온다.
 *
 * 한 번 가져온 것은 들고 있는다. 고르는 동안 같은 주문을 여러 번 펼쳤다
 * 접었다 하는데, 그때마다 서버에 물으면 고르는 흐름이 끊긴다.
 */
const expanded = ref(new Set())
const lineCache = ref({})
const lineLoading = ref(null)

async function toggleExpand(row) {
  const seq = row.orderSeq
  const next = new Set(expanded.value)
  if (next.has(seq)) {
    next.delete(seq)
    expanded.value = next
    return
  }
  next.add(seq)
  expanded.value = next
  if (lineCache.value[seq]) return

  lineLoading.value = seq
  try {
    lineCache.value = { ...lineCache.value, [seq]: await outboundApi.targetLines(seq) }
  } catch (e) {
    toast.error(e.message)
    const back = new Set(expanded.value)
    back.delete(seq)
    expanded.value = back
  } finally {
    lineLoading.value = null
  }
}

/* ── 지시 만들기 ────────────────────────────────────────────── */

const confirming = ref(false)
const busy = ref(false)
const result = ref(null)

async function doInstruct() {
  busy.value = true
  try {
    const data = await outboundApi.create(picked.value.map((r) => r.orderSeq), null)
    confirming.value = false
    result.value = data
    if (data.failed.length === 0) {
      toast.success(`${data.made.length} 건을 창고로 넘겼습니다.`)
    } else {
      toast.warn(`${data.made.length} 건 지시 · ${data.failed.length} 건 실패`)
    }
    await fetchPage()
  } catch (e) {
    loadError.value = e.message
    confirming.value = false
  } finally {
    busy.value = false
  }
}

const nf = new Intl.NumberFormat('ko-KR')
const num = (v) => (v === null || v === undefined ? '-' : nf.format(v))
const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const columns = [
  { key: 'orderNo', label: '주문번호', width: '165px' },
  { key: 'channelName', label: '채널', width: '92px' },
  { key: 'receiverName', label: '수령인', width: '150px' },
  // 주문번호만 보고는 무엇이 나가는지 알 수 없다. 요약을 목록에 싣고,
  // 확인하려면 펼친다.
  { key: 'skuSummary', label: '무엇을', width: '210px' },
  { key: 'locationSummary', label: '어디서', width: '130px', cls: 'code' },
  { key: 'plantName', label: '센터', width: '104px' },
  { key: 'lineCount', label: '품목', width: '58px', align: 'right' },
  { key: 'allocatedQty', label: '지시수량', width: '84px', align: 'right' },
  { key: 'orderedAt', label: '주문일시', width: '130px' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">출고대상</h1>
        <p class="page-desc">
          재고를 잡아 둔 주문 중 <strong>아직 창고로 안 넘긴 것</strong>입니다.
          고른 뒤 지시를 만들면 창고에 집을 일이 생깁니다 — 여기 남아 있는 동안은
          재고만 묶여 있고 아무도 움직이지 않습니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn" @click="router.push({ name: 'outbounds' })">출고지시 보기</button>
        <button
          class="btn btn-primary"
          :disabled="!picked.length || !canInstruct"
          :title="canInstruct ? '고른 주문을 창고로 넘깁니다' : '출고지시 권한이 없습니다'"
          @click="confirming = true"
        >
          {{ picked.length ? `${picked.length} 건 지시` : '출고지시 만들기' }}
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!--
      방금 만든 결과. 부분 성공을 그대로 보여 준다 — 12 건 중 10 건 만들고
      2 건이 안 됐으면, 그 2 건만 손보면 된다.
    -->
    <div v-if="result" class="alert" :class="result.failed.length ? 'alert-warn' : 'alert-ok'">
      <span class="alert-icon">{{ result.failed.length ? '⚠' : '✅' }}</span>
      <div class="grow">
        <div>
          <strong>{{ result.made.length }} 건</strong> 지시했습니다.
          <template v-if="result.failed.length">
            <strong class="danger">{{ result.failed.length }} 건</strong>은 못 했습니다.
          </template>
        </div>
        <div v-if="result.made.length" class="small dim mt-1">
          {{ result.made.map((o) => o.outboundNo).join(', ') }}
        </div>
        <ul v-if="result.failed.length" class="fail-list">
          <li v-for="(f, i) in result.failed" :key="i" class="small">{{ f }}</li>
        </ul>
      </div>
      <button class="btn btn-sm" @click="result = null">닫기</button>
    </div>

    <div class="toolbar">
      <FormField
        v-model="filters.keyword"
        class="grow"
        label="검색어"
        placeholder="주문번호 / 채널주문번호 / 수령인"
        @enter="search()"
      />
      <FormField
        v-model="filters.channelId"
        label="채널"
        type="select"
        empty-option="전체"
        :options="channels.map((c) => ({ value: c.channelId, label: c.channelName }))"
        @change="search()"
      />
      <FormField
        v-model="filters.plantId"
        label="센터"
        type="select"
        empty-option="전체"
        :options="hierarchy.plantOptions"
        @change="search()"
      />
      <!--
        단포는 피킹 동선이 다르다 — 여러 지시를 한 번에 돌며 같은 SKU 를
        몰아 집는 편이 빠르다. 그래서 갈라서 뽑을 수 있게 둔다.
      -->
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
      <div class="toolbar-actions">
        <button class="btn btn-primary" :disabled="loading" @click="search()">
          <span v-if="loading" class="spinner"></span>
          조회
        </button>
        <button class="btn" @click="resetFilters()">초기화</button>
      </div>
    </div>

    <!--
      고른 것과 전체 고르기. 표 머리에 두고 싶지만 DataTable 이 머리 슬롯을
      주지 않아 여기 둔다 — 어차피 '몇 건 골랐나' 를 보는 자리가 필요하다.
    -->
    <div v-if="rows.length" class="picked-bar">
      <span v-if="picked.length">
        <strong>{{ picked.length }}</strong> 건 · 지시수량
        <strong>{{ num(pickedQty) }}</strong>
      </span>
      <span v-else class="dim">줄을 눌러 고르세요.</span>
      <div class="btn-row">
        <button class="btn btn-sm" @click="pickAll()">
          {{ picked.length && picked.length === rows.filter((r) => !blocked(r)).length
              ? '전체 해제' : '이 페이지 전체' }}
        </button>
        <button class="btn btn-sm" :disabled="!picked.length" @click="picked = []">비우기</button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="orderSeq"
      :loading="loading"
      :page-size="0"
      :show-pager="false"
      clickable
      :muted-when="blocked"
      empty-text="창고로 넘길 주문이 없습니다. 주문을 확정하고 재고를 할당하세요."
      @row-click="toggle"
    >
      <template #cell-orderNo="{ row, value }">
        <span class="mark">{{ isPicked(row) ? '☑' : '☐' }}</span>
        <span class="code">{{ value }}</span>
        <div v-if="row.extOrderNo" class="small dim">{{ row.extOrderNo }}</div>
      </template>

      <template #cell-receiverName="{ row, value }">
        {{ value }}
        <!-- 주소는 줄여서 붙인다. 전체를 펼칠 자리는 아니고, 같은 사람
             주문이 여럿인지만 보이면 된다 -->
        <div class="small dim ellip" :title="row.address">{{ row.address }}</div>
        <!--
          센터가 갈린 주문. 고르기 전에 알려 줘야 '왜 안 만들어지지' 를
          안 묻는다.
        -->
        <div v-if="row.multiPlant" class="small danger">
          재고가 센터 두 곳에 나뉘어 있습니다 — 한쪽 할당을 풀고 다시 잡으세요
        </div>
      </template>

      <template #cell-skuSummary="{ row, value }">
        <span class="code small">{{ value ?? '-' }}</span>
        <button class="link-btn small" @click.stop="toggleExpand(row)">
          {{ expanded.has(row.orderSeq) ? '접기 ▴' : '펼치기 ▾' }}
        </button>
        <div v-if="lineLoading === row.orderSeq" class="small dim">불러오는 중…</div>
        <!-- 지시를 만들 때 담을 줄과 같은 것. 미리 보는 것과 실제가 어긋나면
             미리 보는 의미가 없다 -->
        <table v-else-if="expanded.has(row.orderSeq)" class="sub">
          <tr v-for="l in lineCache[row.orderSeq] ?? []" :key="l.orderLineSeq">
            <td class="code">{{ l.locationHint }}</td>
            <td class="code">{{ l.skuId }}</td>
            <td>{{ l.colorCode }}/{{ l.sizeCode }}</td>
            <td class="sub-name">{{ l.productName }}</td>
            <td class="right"><strong>{{ num(l.instructedQty) }}</strong></td>
          </tr>
          <!--
            배송요청은 여기까지만 보인다.

            택배사에 넘기는 값이라 진짜 쓰이는 곳은 송장 발급(D섹터)이고,
            포장 방식이 달라지는 것은 패킹(C섹터)이다. 목록에 늘 띄우면
            '센터 두 곳에 나뉘어 있습니다' 같은 정작 봐야 할 경고가 묻힌다.
            다만 '○일 이후 배송' 처럼 지시 시점을 바꾸는 메모가 가끔 있어서
            볼 길은 남긴다.
          -->
          <tr v-if="row.deliveryMemo">
            <td colspan="5" class="memo">📌 {{ row.deliveryMemo }}</td>
          </tr>
        </table>
      </template>

      <template #cell-lineCount="{ row, value }">
        {{ num(value) }}
        <div v-if="row.singlePack" class="small dim">단포</div>
      </template>

      <template #cell-allocatedQty="{ row, value }">
        <strong>{{ num(value) }}</strong>
        <!-- 주문수량과 다르면 알려 준다. 결품이거나 일부를 이미 내보낸 것이다 -->
        <div v-if="row.totalQty !== value" class="small warn">주문 {{ num(row.totalQty) }}</div>
      </template>

      <template #cell-orderedAt="{ value }">
        <span class="small">{{ dt(value) }}</span>
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

    <!-- ── 지시 확인 ───────────────────────────────────────── -->
    <ModalDialog
      v-if="confirming"
      title="출고지시 만들기"
      :subtitle="`${picked.length} 건`"
      @close="confirming = null"
    >
      <p class="small">
        고른 주문을 창고 작업으로 넘깁니다. 주문마다 지시가 <strong>한 장씩</strong> 생기고,
        지시수량은 <strong>실제로 잡아 둔 수량</strong>입니다.
      </p>
      <p class="small dim">
        만든 지시는 고칠 수 없습니다 — 잘못 만들었으면 취소하고 다시 만듭니다.
      </p>
      <ul class="confirm-list">
        <li v-for="r in picked" :key="r.orderSeq" class="small">
          <span class="code">{{ r.orderNo }}</span>
          <span class="dim"> · {{ r.receiverName }} · {{ r.plantName }}</span>
          <strong> {{ num(r.allocatedQty) }}개</strong>
          <span v-if="r.singlePack" class="dim"> · 단포</span>
        </li>
      </ul>
      <template #footer>
        <button class="btn" :disabled="busy" @click="confirming = false">닫기</button>
        <button class="btn btn-primary" :disabled="busy" @click="doInstruct()">
          <span v-if="busy" class="spinner"></span>
          {{ picked.length }} 건 지시
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
/* 고른 표시 — 체크박스를 따로 두면 줄 전체를 누르는 지금 동작과 어긋난다 */
.mark {
  margin-right: 6px;
  color: var(--text-3);
  cursor: pointer;
}
/* 주소는 한 줄로 줄인다 — 전체는 title 로 본다 */
.ellip {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.memo {
  color: var(--c-amber, #b45309);
}
/* 펼치기 — 줄을 누르면 선택이 토글되므로 버튼은 stop 이 필요하다 */
.link-btn {
  margin-left: 6px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--c-blue, #2563eb);
  cursor: pointer;
}
.sub {
  margin-top: 4px;
  border-collapse: collapse;
  font-size: 12px;
}
.sub td {
  padding: 1px 8px 1px 0;
  white-space: nowrap;
}
.sub-name {
  color: var(--text-2, #6b7280);
}
.right {
  text-align: right;
}
.picked-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  margin-bottom: 8px;
  border-radius: 6px;
  background: var(--bg-2, #f6f7f9);
}
.fail-list {
  margin: 6px 0 0;
  padding-left: 18px;
}
.confirm-list {
  margin: 10px 0 0;
  padding-left: 18px;
  max-height: 260px;
  overflow-y: auto;
}
.confirm-list li {
  margin-bottom: 4px;
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
.grow {
  flex: 1;
}
.danger {
  color: var(--c-red, #dc2626);
}
.warn {
  color: var(--c-amber, #b45309);
}
</style>
