<script setup>
/**
 * 상단 막대의 알림 뱃지 (COM-PG-015).
 *
 * 안 읽은 <b>열린</b> 알림 수를 보여 준다. 닫힌 것은 이미 끝난 일이라 세지
 * 않고, 읽었지만 안 끝난 것도 세지 않는다 — 뱃지는 '새로 생긴 것이 있나'
 * 지 '할 일이 몇 개 남았나' 가 아니다. 남은 할 일은 알림함이 보여 준다.
 *
 * <b>눌러도 화면을 안 옮긴다.</b> 예전에는 알림함으로 보냈는데, 그러면
 * '할 일이 있나' 를 한 번 보려고 하던 일을 통째로 떠나야 했다. 출고지시를
 * 짜다가 뱃지를 확인했더니 화면이 바뀌어 있고, 돌아오려면 메뉴를 다시
 * 찾아야 한다. 확인은 잠깐 멈추는 것이지 다른 데로 가는 것이 아니다.
 *
 * 그래서 아래로 작은 판을 편다. 검색창(Ctrl+K)이 화면 위에 겹쳐 뜨는 것과
 * 같은 이유다.
 *
 * <b>여기에 목록을 다 그리지 않는다.</b> 급한 것 몇 건만 보여 주고 나머지는
 * 알림함으로 넘긴다 — 거르기와 쪽 넘김까지 붙이기 시작하면 이 작은 판이
 * 또 하나의 화면이 된다.
 *
 * <b>화면을 옮길 때와 판을 열 때만 센다.</b> 창고 업무는 초 단위가 아니라
 * 결품이 3분 뒤에 보여도 아무 일 없고, 실시간 채널(WebSocket · SSE)을
 * 들이면 그것부터 관리 대상이 된다. 지금 코드에 setInterval 조차 없는데
 * 연결을 하나 늘릴 이유가 없다.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as notiApi from '@/api/notification.js'
import { useSessionStore } from '@/stores/session.js'
import CodeBadge from '@/components/CodeBadge.vue'

/** 판에 담는 건수. 넘으면 '알림함에서 더 보기' 로 넘긴다 */
const PEEK = 7

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const allowed = computed(() => !session.denyReason('SYS_NOTIFICATION', 'R'))

const count = ref(0)
const open = ref(false)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const root = ref(null)

async function refresh() {
  if (!allowed.value) {
    count.value = 0
    return
  }
  try {
    count.value = await notiApi.unreadCount()
  } catch {
    // 뱃지가 안 떠도 업무는 돈다. 조용히 넘어간다.
    count.value = 0
  }
}

/**
 * 판에 담을 것을 불러온다.
 *
 * 열린 것만, 서버가 매긴 순서 그대로다 — 급한 것이 위로, 그다음 오래된
 * 것이 위로. 새것이 위로 오면 오래 묵은 경고가 아래로 밀려 영영 안 보인다.
 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await notiApi.inbox({ page: 1, size: PEEK })
    rows.value = data.rows
    total.value = data.total
  } catch (e) {
    error.value = e.message
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await Promise.all([load(), refresh()])
  }
}

function close() {
  open.value = false
}

/**
 * 알림을 누르면 그 문서로 간다.
 *
 * 이때는 화면을 옮기는 것이 맞다 — 사람이 '이걸 처리하겠다' 고 고른 것이라,
 * 뱃지를 눌러 본 것과 뜻이 다르다.
 */
async function go(row) {
  if (!row.read) {
    try {
      await notiApi.markRead(row.notificationSeq)
      row.read = true
      count.value = Math.max(0, count.value - 1)
    } catch {
      // 읽음 표시가 안 돼도 이동은 막지 않는다
    }
  }
  close()
  if (row.route) {
    router.push({ name: row.route, query: row.refNo ? { keyword: row.refNo } : {} })
  }
}

/** 읽음일 뿐 닫는 것이 아니다. 할 일은 그대로 남는다 */
async function readAll() {
  loading.value = true
  try {
    await notiApi.markAllRead()
    await Promise.all([load(), refresh()])
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function goInbox() {
  close()
  router.push({ name: 'notifications' })
}

/* 바깥을 누르면 닫는다. 판이 떠 있는 채로 다른 것을 누르면 걸리적거린다 */
function onDocClick(e) {
  if (open.value && root.value && !root.value.contains(e.target)) close()
}
function onKey(e) {
  if (e.key === 'Escape' && open.value) close()
}

onMounted(() => {
  refresh()
  document.addEventListener('click', onDocClick)
  window.addEventListener('keydown', onKey)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('keydown', onKey)
})

// 화면을 옮기면 숫자를 다시 세고, 떠 있던 판은 닫는다
watch(
  () => route.fullPath,
  () => {
    close()
    refresh()
  },
)

const more = computed(() => Math.max(0, total.value - rows.value.length))

const dt = (v) => (v ? String(v).replace('T', ' ').slice(5, 16) : '-')
</script>

<template>
  <div v-if="allowed" ref="root" class="bell-wrap">
    <button
      class="btn btn-ghost btn-icon bell"
      :title="count ? `안 읽은 알림 ${count}건` : '알림'"
      @click="toggle()"
    >
      🔔
      <!-- 99 를 넘으면 숫자보다 '많다' 가 정보다 -->
      <span v-if="count" class="dot">{{ count > 99 ? '99+' : count }}</span>
    </button>

    <!-- 하던 화면 위에 겹쳐 편다. 확인은 잠깐 멈추는 것이지 떠나는 것이 아니다 -->
    <div v-if="open" class="nb-panel">
      <div class="nb-head">
        <strong>할 일</strong>
        <span class="dim small">{{ total }}건</span>
        <span class="nb-spacer"></span>
        <button class="btn btn-sm btn-ghost" :disabled="loading || !count" @click="readAll()">
          모두 읽음
        </button>
      </div>

      <div v-if="error" class="nb-msg danger">{{ error }}</div>
      <div v-else-if="loading && !rows.length" class="nb-msg dim">불러오는 중…</div>
      <div v-else-if="!rows.length" class="nb-msg dim">할 일이 없습니다.</div>

      <ul v-else class="nb-list">
        <li
          v-for="r in rows"
          :key="r.notificationSeq"
          class="nb-item"
          :class="{ unread: !r.read }"
          @click="go(r)"
        >
          <span class="lv" :class="r.level.toLowerCase()">
            {{ r.level === 'ALERT' ? '!' : r.level === 'WARN' ? '·' : '' }}
          </span>
          <div class="nb-body">
            <div class="nb-title">{{ r.title }}</div>
            <div class="nb-sub small dim">
              <CodeBadge group="NOTI_KIND" :code="r.kind" plain />
              <span v-if="r.refNo" class="code">{{ r.refNo }}</span>
              <span v-if="r.agingDays >= 3" class="danger">{{ r.agingDays }}일</span>
              <span v-else>{{ dt(r.occurredAt) }}</span>
            </div>
          </div>
        </li>
      </ul>

      <button class="nb-foot" @click="goInbox()">
        <template v-if="more">알림함에서 {{ more }}건 더 보기</template>
        <template v-else>알림함 열기</template>
      </button>
    </div>
  </div>
</template>

<style scoped>
.bell-wrap {
  position: relative;
}
.bell {
  position: relative;
}
.dot {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  font-weight: 700;
}

.nb-panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 1000;
  width: min(360px, calc(100vw - 24px));
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
.nb-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-bottom: 1px solid var(--border);
}
.nb-spacer {
  flex: 1 1 auto;
}
.nb-msg {
  padding: 18px 12px;
  font-size: 13px;
  text-align: center;
}
.nb-msg.danger {
  color: var(--danger);
}

.nb-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 58vh;
  overflow-y: auto;
}
.nb-item {
  display: flex;
  gap: 9px;
  padding: 9px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--border);
}
.nb-item:last-child {
  border-bottom: 0;
}
.nb-item:hover {
  background: var(--surface-3);
}
.nb-body {
  min-width: 0;
  flex: 1 1 auto;
}
.nb-title {
  font-size: 13px;
  line-height: 1.35;
}
.nb-item.unread .nb-title {
  font-weight: 700;
}
.nb-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nb-foot {
  display: block;
  width: 100%;
  padding: 9px 12px;
  border: 0;
  border-top: 1px solid var(--border);
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.nb-foot:hover {
  background: var(--surface-3);
}

.lv {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  margin-top: 1px;
  line-height: 18px;
  text-align: center;
  border-radius: 50%;
  font-weight: 700;
  font-size: 11px;
}
.lv.alert {
  background: var(--danger);
  color: #fff;
}
.lv.warn {
  background: var(--warn);
  color: #fff;
}
.lv.info {
  background: var(--border);
}
.danger {
  color: var(--danger);
  font-weight: 600;
}
</style>
