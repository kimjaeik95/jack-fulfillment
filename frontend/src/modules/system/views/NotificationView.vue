<script setup>
/**
 * 알림함 (COM-PG-015).
 *
 * <b>소식이 아니라 할 일이다.</b> 여기 뜬 것은 전부 누군가 뭔가를 해야 하는
 * 것이고, 그 일이 끝나면 저절로 사라진다 — 사람이 지우는 것이 아니다.
 *
 * 그래서 '지우기' 버튼이 없다. 읽음은 헤더 뱃지 숫자를 줄일 뿐이고, 목록에서
 * 빠지는 것은 <b>닫힘</b>이다. 배송실패를 읽기만 하고 재배송을 안 보냈으면
 * 그 알림은 남아 있어야 한다.
 *
 * 급한 것이 위로, 그다음 오래된 것이 위로 온다. 새것이 위로 오면 오래 묵은
 * 경고가 아래로 밀려 영영 안 보인다 — 알림함에서 가장 나쁜 일이다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as notiApi from '@/api/notification.js'
import { useSessionStore } from '@/stores/session.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const router = useRouter()
const session = useSessionStore()

const readDenyReason = computed(() => session.denyReason('SYS_NOTIFICATION', 'R'))

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = notiApi.PAGE_SIZE
const sweepMsg = ref('')

const filters = reactive({
  keyword: '',
  kind: '',
  level: '',
  includeClosed: '',
  unreadOnly: '',
})

async function fetchPage() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await notiApi.inbox({
      keyword: filters.keyword.trim() || null,
      kind: filters.kind || null,
      level: filters.level || null,
      includeClosed: filters.includeClosed || null,
      unreadOnly: filters.unreadOnly || null,
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

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function goPage(n) {
  if (n < 1 || n > totalPages.value || n === page.value) return
  page.value = n
  await fetchPage()
}

onMounted(fetchPage)

/**
 * 알림을 누르면 그 문서로 간다.
 *
 * 알림함에서 '배송 실패 3건' 을 보고 다시 메뉴를 뒤져 배송 화면을 찾아야
 * 하면 알려 준 보람이 없다.
 */
async function open(row) {
  if (!row.read) {
    try {
      await notiApi.markRead(row.notificationSeq)
      row.read = true
      session.refreshNotiBadge?.()
    } catch {
      // 읽음 표시가 안 돼도 이동은 막지 않는다
    }
  }
  if (row.route) {
    router.push({ name: row.route, query: row.refNo ? { keyword: row.refNo } : {} })
  }
}

async function readAll() {
  loading.value = true
  try {
    await notiApi.markAllRead()
    await fetchPage()
    session.refreshNotiBadge?.()
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

/**
 * 지금 훑기.
 *
 * 배치는 새벽에 도는데, 방금 고친 것이 목록에서 빠졌는지 바로 보고 싶을
 * 때가 있다. 훑는 것은 읽기와 닫기뿐이라 위험하지 않다.
 */
async function sweep() {
  loading.value = true
  sweepMsg.value = ''
  try {
    const r = await notiApi.sweep()
    sweepMsg.value =
      `납기초과 ${r.overdue} · 운송중지연 ${r.stuck} · 체인꺾임 ${r.broken} · ` +
      `결재대기 ${r.approvals} · 미매핑 ${r.unmapped} · 할당결품 ${r.allocShort}`
    await fetchPage()
    session.refreshNotiBadge?.()
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

const dt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const columns = [
  { key: 'level', label: '', width: '58px', align: 'center' },
  { key: 'kind', label: '종류', width: '110px' },
  { key: 'title', label: '무슨 일', width: '340px' },
  { key: 'refNo', label: '대상', width: '160px' },
  { key: 'agingDays', label: '경과', width: '66px', align: 'right' },
  { key: 'occurredAt', label: '언제', width: '124px' },
]
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">알림함</h1>
        <p class="page-desc">
          <strong>소식이 아니라 할 일입니다.</strong> 여기 뜬 것은 전부 누군가 뭔가를 해야
          하는 것이고, <strong>그 일이 끝나면 저절로 사라집니다</strong> — 지우는 버튼이
          없는 이유입니다. 읽음은 뱃지 숫자만 줄입니다.
          <br />
          <strong>내 역할에 온 것만</strong> 보입니다 — 결품은 센터장에게, 결재는 결재자에게,
          미매핑은 주문담당에게 갑니다. 비어 있으면 내가 할 일이 없다는 뜻입니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn" :disabled="loading" @click="readAll()">모두 읽음</button>
        <button class="btn" :disabled="loading" title="배치를 지금 한 번 돌립니다" @click="sweep()">
          <span v-if="loading" class="spinner"></span>
          지금 훑기
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-if="sweepMsg" class="alert alert-ok mb-2">
      <span class="alert-icon">✅</span><span>훑었습니다 — {{ sweepMsg }}</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="제목 / 번호"
          @enter="search()"
        />
        <FormField
          v-model="filters.kind"
          label="종류"
          type="select"
          empty-option="전체"
          :options="codeOptions('NOTI_KIND')"
          @change="search()"
        />
        <FormField
          v-model="filters.level"
          label="등급"
          type="select"
          empty-option="전체"
          :options="codeOptions('NOTI_LEVEL')"
          @change="search()"
        />
        <FormField
          v-model="filters.unreadOnly"
          label="읽음"
          type="select"
          empty-option="전체"
          :options="[{ value: 'Y', label: '안 읽은 것만' }]"
          @change="search()"
        />
        <!--
          기본은 열린 것만. 닫힌 알림은 이미 끝난 일이라, 섞으면 할 일을
          찾으러 온 사람이 끝난 일을 훑게 된다.
        -->
        <FormField
          v-model="filters.includeClosed"
          label="범위"
          type="select"
          empty-option="할 일만"
          :options="[{ value: 'Y', label: '끝난 것까지' }]"
          @change="search()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="search()">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
        </div>
      </div>

      <div v-if="!loading && !rows.length" class="alert alert-ok m-2">
        <span class="alert-icon">✅</span>
        <span>할 일이 없습니다.</span>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="notificationSeq"
        :loading="loading"
        :page-size="0"
        :show-pager="false"
        :muted-when="(r) => r.closed"
        :row-class="(r) => (r.read ? 'read' : 'unread')"
        empty-text="할 일이 없습니다."
        @row-click="open"
      >
        <template #cell-level="{ value }">
          <span class="lv" :class="value.toLowerCase()">
            {{ value === 'ALERT' ? '!' : value === 'WARN' ? '·' : '' }}
          </span>
        </template>

        <template #cell-kind="{ value }">
          <CodeBadge group="NOTI_KIND" :code="value" />
        </template>

        <template #cell-title="{ row, value }">
          <strong :class="{ unread: !row.read }">{{ value }}</strong>
          <div v-if="row.body" class="small dim">{{ row.body }}</div>
          <div v-if="row.closed" class="small dim">
            끝남 · {{ dt(row.closedAt) }}
            <template v-if="row.closedReason === 'GONE'">(조건이 사라짐)</template>
          </div>
        </template>

        <template #cell-refNo="{ row, value }">
          <span class="code">{{ value }}</span>
          <div v-if="row.plantName" class="small dim">{{ row.plantName }}</div>
        </template>

        <!-- 오래 묵을수록 눈에 띄게 -->
        <template #cell-agingDays="{ value }">
          <span :class="value >= 3 ? 'danger' : ''">{{ value }}일</span>
        </template>

        <template #cell-occurredAt="{ value }">
          <span class="small">{{ dt(value) }}</span>
        </template>
      </DataTable>

      <div class="pager">
        <span class="small dim">총 {{ total }}건 · {{ page }} / {{ totalPages }} 페이지</span>
        <div class="btn-row">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">이전</button>
          <button class="btn btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">
            다음
          </button>
        </div>
      </div>
    </div>

    <p class="small dim mt-2">
      여덟 종류입니다 — 미매핑 주문 · 할당 결품 · 피킹 결품 · 결재 대기 · 배송 실패 ·
      발주 납기 초과 · 운송중 지연 · 수량 체인 꺾임. 피킹 결품과 배송 실패는 그 자리에서
      바로 뜨고, 나머지는 새벽 배치가 찾습니다.
    </p>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--line, #e5e7eb);
}
.lv {
  display: inline-block;
  width: 20px;
  height: 20px;
  line-height: 20px;
  border-radius: 50%;
  font-weight: 700;
  font-size: 12px;
}
.lv.alert {
  background: var(--c-red, #dc2626);
  color: #fff;
}
.lv.warn {
  background: var(--c-amber, #b45309);
  color: #fff;
}
.lv.info {
  background: var(--line, #e5e7eb);
}
strong.unread {
  font-weight: 700;
}
.danger {
  color: var(--c-red, #dc2626);
  font-weight: 600;
}
</style>
