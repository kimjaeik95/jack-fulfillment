<script setup>
/**
 * 변경 이력(감사로그) 조회 (COM-PG-009) — 실제 서버 API 연동.
 *
 * 이 콘솔에서 발생한 모든 등록·수정·삭제·로그인이 기록된다.
 * 등록 전용 테이블이라 이 화면에는 저장·삭제가 없다.
 *
 * 이력은 계속 쌓이기만 하므로 검색·기간·페이징을 모두 서버가 처리한다.
 * 전체를 받아 화면에서 자르는 방식은 며칠이면 감당할 수 없게 된다.
 *
 * 목록은 변경 항목 수만 받고, 전/후 값은 상세를 열 때 받는다.
 * 페이지마다 상세를 다 실어 보내면 응답이 커지는데 대부분은 펼치지 않는다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import * as auditApi from '@/api/audit.js'
import * as exportApi from '@/api/export.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'

const session = useSessionStore()
const toast = useToastStore()

const ACTION_OPTIONS = [
  { value: 'CREATE', label: '등록' },
  { value: 'UPDATE', label: '수정' },
  { value: 'DELETE', label: '삭제' },
  { value: 'LOGIN', label: '로그인' },
  { value: 'LOGIN_FAIL', label: '로그인 실패' },
  { value: 'LOGOUT', label: '로그아웃' },
  { value: 'PWD_RESET', label: '비밀번호 초기화' },
  { value: 'PWD_CHANGE', label: '비밀번호 변경' },
  { value: 'DOWNLOAD', label: '다운로드' },
]

/** 서버는 테이블명으로 저장한다. 화면은 업무 이름으로 고른다. */
const TARGET_OPTIONS = [
  { value: 'tb_user', label: '사용자' },
  { value: 'tb_org', label: '조직' },
  { value: 'tb_role', label: '역할' },
  { value: 'tb_permission', label: '권한' },
  { value: 'tb_role_permission', label: '역할-권한' },
  { value: 'tb_policy', label: '공통정책' },
  { value: 'tb_code', label: '공통코드' },
  { value: 'tb_audit_log', label: '감사이력(다운로드)' },
]

const ACTION_COLOR = {
  CREATE: 'blue',
  UPDATE: 'amber',
  DELETE: 'red',
  LOGIN: 'green',
  LOGIN_FAIL: 'red',
  LOGOUT: 'gray',
  PWD_RESET: 'violet',
  PWD_CHANGE: 'violet',
  DOWNLOAD: 'teal',
}

/** 로그인 실패 사유 코드 → 사람이 읽는 문구 */
const FAIL_REASON = {
  NOT_FOUND: '존재하지 않는 계정',
  BAD_PASSWORD: '비밀번호 불일치',
  LOCK_BY_FAIL: '실패 한도 초과로 잠금',
  LOCKED: '잠긴 계정',
  DORMANT: '휴면 계정',
  DISABLED: '사용 중지 계정',
  RETIRED: '퇴사 계정',
}

const filters = reactive({
  keyword: '',
  actionType: '',
  targetTable: '',
  actorUserId: '',
  fromDate: '',
  toDate: '',
})
const paging = reactive({ page: 1, size: 20 })

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await auditApi.list({ ...filters, ...paging })
    rows.value = result.rows
    total.value = result.total
  } catch (e) {
    // 권한 부족(403)도 여기로 온다. 사유를 그대로 보여준다.
    loadError.value = e.message
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 검색 조건이 바뀌면 1페이지로 되돌린다 (2페이지에서 조건을 바꾸면 빈 화면이 나온다) */
function applySearch() {
  paging.page = 1
  load()
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '', actionType: '', targetTable: '', actorUserId: '', fromDate: '', toDate: '',
  })
  applySearch()
}

onMounted(load)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / paging.size)))

const pageNumbers = computed(() => {
  const tp = totalPages.value
  const cur = paging.page
  const from = Math.max(1, Math.min(cur - 2, tp - 4))
  const to = Math.min(tp, Math.max(cur + 2, 5))
  const out = []
  for (let i = from; i <= to; i++) out.push(i)
  return out
})

function goPage(p) {
  paging.page = Math.min(Math.max(1, p), totalPages.value)
  load()
}

function changeSize(size) {
  paging.size = Number(size)
  paging.page = 1
  load()
}

/* ------------------------------------------------------------------ */
/* 상세                                                                */
/* ------------------------------------------------------------------ */

const detail = ref(null)
const detailLoading = ref(false)

async function openDetail(row) {
  detailLoading.value = true
  detail.value = { ...row, details: [] }
  try {
    detail.value = await auditApi.detail(row.logSeq)
  } catch (e) {
    toast.error(e.message)
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

/** 목록에 보여줄 한 줄 요약 */
function summarize(row) {
  if (row.actionType === 'LOGIN') return '로그인 성공'
  if (row.actionType === 'LOGOUT') return '로그아웃'
  if (row.actionType === 'LOGIN_FAIL') {
    return `로그인 실패 — ${FAIL_REASON[row.reason] ?? row.reason ?? '사유 미상'}`
  }
  if (row.changedColumnCount) {
    return `${row.reason ?? '-'} · ${row.changedColumnCount}개 항목 변경`
  }
  return row.reason ?? '-'
}

function stamp(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

/** 값이 비어 있으면 눈에 보이게 표시한다 (등록은 전이 없고 삭제는 후가 없다) */
function shown(value) {
  return value === null || value === undefined || value === '' ? '(없음)' : value
}

/* ------------------------------------------------------------------ */
/* 다운로드                                                            */
/* ------------------------------------------------------------------ */

const canDownload = computed(() => session.can('AUD_DOWNLOAD', 'X'))
const downloadDenyReason = computed(() => session.denyReason('AUD_DOWNLOAD', 'X'))

const downloading = ref(false)

/**
 * 서버가 만든 파일을 받는다.
 * 화면이 CSV 를 조립하면 서버가 적용한 마스킹과 어긋날 수 있고,
 * 다운로드 사실을 감사로그에 남기는 것도 서버 쪽에서만 할 수 있다.
 *
 * 주소창으로 여는 대신 받아서 저장한다. 권한이 없으면 서버가 JSON 을
 * 내려주는데, 링크였다면 그 JSON 이 파일로 저장되고 사용자는 무엇이
 * 잘못됐는지 알 수 없다.
 */
async function download() {
  const result = session.check('AUD_DOWNLOAD', 'X')
  if (!result.allowed) {
    toast.error(result.reason)
    return
  }
  if (result.reason) toast.warn(result.reason)

  downloading.value = true
  try {
    await exportApi.auditLogs({ ...filters })
    toast.success('현재 검색 조건으로 내려받았습니다. 다운로드 사실은 감사 기록 대상입니다.')
  } catch (e) {
    toast.error(e.message)
  } finally {
    downloading.value = false
  }
}

const readDenyReason = computed(() => session.denyReason('AUD_HISTORY', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">변경 이력</h1>
        <p class="page-desc">
          이 콘솔에서 발생한 사용자·조직·역할·권한 변경과 로그인 내역입니다. 이력은 고치거나 지울 수 없고,
          다운로드에는 데이터 다운로드 권한(AUD_DOWNLOAD/X)이 따로 필요합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn"
          :disabled="downloading || loading || !canDownload"
          :title="downloadDenyReason ?? '현재 검색 조건으로 CSV 내려받기'"
          @click="download"
        >
          <span v-if="downloading" class="spinner"></span>
          ⬇ CSV 다운로드
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="downloadDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ downloadDenyReason }} (조회만 가능합니다)</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="대상 키 / 처리자 / 사유"
          @enter="applySearch"
        />
        <FormField v-model="filters.targetTable" label="대상" type="select" empty-option="전체" :options="TARGET_OPTIONS" />
        <FormField v-model="filters.actionType" label="구분" type="select" empty-option="전체" :options="ACTION_OPTIONS" />
        <FormField v-model="filters.fromDate" label="시작일" type="date" />
        <FormField v-model="filters.toDate" label="종료일" type="date" />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="applySearch">
            <span v-if="loading" class="spinner"></span>
            조회
          </button>
          <button class="btn" @click="resetFilters">초기화</button>
        </div>
      </div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th style="width: 148px">발생시각</th>
              <th style="width: 96px">대상</th>
              <th style="width: 84px; text-align: center">구분</th>
              <th style="width: 150px">대상 키</th>
              <th style="width: 140px">처리자</th>
              <th>요약</th>
              <th style="width: 74px; text-align: right"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!rows.length">
              <td colspan="7">
                <div class="table-empty">
                  <span class="table-empty-icon">📜</span>
                  조건에 맞는 이력이 없습니다.
                </div>
              </td>
            </tr>
            <tr v-for="row in rows" :key="row.logSeq">
              <td class="mono small">{{ stamp(row.occurredAt) }}</td>
              <td>{{ row.targetLabel ?? '-' }}</td>
              <td style="text-align: center">
                <span class="badge" :class="`badge-${ACTION_COLOR[row.actionType] ?? 'gray'}`">
                  {{ row.actionName }}
                </span>
              </td>
              <td class="code">{{ row.targetKey ?? '-' }}</td>
              <td>
                <div style="min-width: 0">
                  <div>{{ row.actorName ?? '-' }}</div>
                  <div class="mono dim" style="font-size: 10.5px">{{ row.actorUserId }}</div>
                </div>
              </td>
              <td>
                <span class="truncate" :title="summarize(row)">{{ summarize(row) }}</span>
              </td>
              <td style="text-align: right">
                <button class="btn btn-sm" @click="openDetail(row)">상세</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="total > 0" class="pager">
        <span>총 <strong>{{ total.toLocaleString() }}</strong>건</span>
        <select
          class="select"
          style="width: 88px; min-height: 26px"
          :value="paging.size"
          @change="changeSize($event.target.value)"
        >
          <option :value="20">20건</option>
          <option :value="50">50건</option>
          <option :value="100">100건</option>
        </select>
        <div class="pager-pages">
          <button class="pager-btn" :disabled="paging.page === 1" title="첫 페이지" @click="goPage(1)">«</button>
          <button class="pager-btn" :disabled="paging.page === 1" title="이전" @click="goPage(paging.page - 1)">‹</button>
          <button
            v-for="p in pageNumbers"
            :key="p"
            class="pager-btn"
            :class="{ active: p === paging.page }"
            @click="goPage(p)"
          >
            {{ p }}
          </button>
          <button class="pager-btn" :disabled="paging.page === totalPages" title="다음" @click="goPage(paging.page + 1)">›</button>
          <button class="pager-btn" :disabled="paging.page === totalPages" title="마지막 페이지" @click="goPage(totalPages)">»</button>
        </div>
      </div>
    </div>

    <!-- 상세 — 변경된 항목의 전/후 값 -->
    <ModalDialog
      v-if="detail"
      title="변경 이력 상세"
      :subtitle="`#${detail.logSeq} · ${stamp(detail.occurredAt)}`"
      size="wide"
      @close="detail = null"
    >
      <div class="form-grid">
        <FormField :model-value="detail.actionName" label="구분" readonly />
        <FormField :model-value="detail.targetLabel ?? '-'" label="대상" readonly />
        <FormField :model-value="detail.targetKey ?? '-'" label="대상 키" mono readonly />
        <FormField :model-value="`${detail.actorName ?? '-'} (${detail.actorUserId})`" label="처리자" readonly />
        <FormField :model-value="detail.clientIp ?? '-'" label="요청 IP" mono readonly />
        <FormField :model-value="detail.reason ?? '-'" label="사유" span readonly />
      </div>

      <div class="card mt-2">
        <div class="card-head">
          <span class="card-title">변경된 항목 ({{ detail.details?.length ?? 0 }})</span>
        </div>
        <div class="card-body tight">
          <div v-if="detailLoading" class="dim small" style="padding: 12px">불러오는 중…</div>
          <table v-else-if="detail.details?.length" class="table">
            <thead>
              <tr>
                <th style="width: 150px">항목</th>
                <th>변경 전</th>
                <th>변경 후</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in detail.details" :key="d.columnName">
                <td>
                  <div>{{ d.label }}</div>
                  <div class="mono dim" style="font-size: 10.5px">{{ d.columnName }}</div>
                </td>
                <td :class="{ dim: d.beforeValue === null }">{{ shown(d.beforeValue) }}</td>
                <td class="bold">{{ shown(d.afterValue) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="dim small" style="padding: 12px">
            값 변경이 없는 행위입니다. (로그인·로그아웃·다운로드 등)
          </div>
        </div>
      </div>

      <template #footer>
        <span class="left small dim">이력은 수정·삭제할 수 없습니다.</span>
        <button class="btn" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>
  </div>
</template>
