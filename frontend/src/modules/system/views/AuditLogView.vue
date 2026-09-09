<script setup>
/**
 * 변경 이력(감사로그) 화면.
 * 이 콘솔에서 발생한 모든 등록/수정/삭제가 기록된다.
 * 감사/분석 사용자에게는 개인정보 마스킹 정책이 적용되고, 다운로드는 AUD_DOWNLOAD 권한이 필요하다.
 */
import { computed, reactive, ref } from 'vue'
import { useAdminStore } from '@/stores/admin.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'

const admin = useAdminStore()
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
]

const ENTITY_OPTIONS = [
  { value: 'users', label: '사용자' },
  { value: 'orgs', label: '조직' },
  { value: 'roles', label: '역할' },
  { value: 'permissions', label: '권한' },
  { value: 'rolePermissions', label: '역할-권한' },
  { value: 'policies', label: '공통정책' },
]

const ACTION_COLOR = {
  CREATE: 'blue',
  UPDATE: 'amber',
  DELETE: 'red',
  LOGIN: 'green',
  LOGIN_FAIL: 'red',
  LOGOUT: 'gray',
  PWD_RESET: 'violet',
}
const ACTION_LABEL = {
  CREATE: '등록',
  UPDATE: '수정',
  DELETE: '삭제',
  LOGIN: '로그인',
  LOGIN_FAIL: '로그인 실패',
  LOGOUT: '로그아웃',
  PWD_RESET: '비번 초기화',
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

const filters = reactive({ keyword: '', entity: '', action: '', actorId: '' })
const detail = ref(null)

function resetFilters() {
  Object.assign(filters, { keyword: '', entity: '', action: '', actorId: '' })
}

const actorOptions = computed(() => {
  const seen = new Map()
  for (const l of admin.auditLogs) if (!seen.has(l.actorId)) seen.set(l.actorId, l.actorName)
  return [...seen].map(([value, label]) => ({ value, label: `${label} (${value})` }))
})

const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return admin.auditLogs
    .filter((l) => !filters.entity || l.entity === filters.entity)
    .filter((l) => !filters.action || l.action === filters.action)
    .filter((l) => !filters.actorId || l.actorId === filters.actorId)
    .filter(
      (l) =>
        !kw ||
        [l.logId, l.targetKey, l.entityLabel, l.actorName].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
})

const columns = [
  { key: 'at', label: '변경시각', width: '150px', sortable: true },
  { key: 'entityLabel', label: '대상', width: '100px', sortable: true },
  { key: 'action', label: '구분', width: '76px', align: 'center', sortable: true },
  { key: 'targetKey', label: '대상 키', width: '150px', cls: 'code' },
  { key: 'actorName', label: '처리자', width: '140px', sortable: true },
  { key: '_summary', label: '변경 요약', width: '300px' },
  { key: '_act', label: '', width: '78px', align: 'right' },
]

/** 스냅샷에서 사람이 읽을 요약을 만든다. */
function summarize(log) {
  const s = log.snapshot
  if (!s) return '-'
  if (log.action === 'LOGIN_FAIL') {
    return `로그인 실패 — ${FAIL_REASON[s.reason] ?? s.reason ?? '사유 미상'}`
  }
  if (log.action === 'LOGIN') return '로그인 성공'
  if (log.action === 'LOGOUT') return '로그아웃'
  if (log.action === 'PWD_RESET') {
    return `비밀번호 초기화 — 상태 ${s.status ?? '-'}`
  }
  if (log.entity === 'rolePermissions') {
    const n = Object.values(s.grants ?? {}).filter((v) => v?.length).length
    return `${admin.roleNameOf(s.roleId)} — 권한 ${n}개 부여`
  }
  const pick = [
    s.roleName && `역할명 ${s.roleName}`,
    s.userName && `이름 ${session.mask(s.userName, 'name')}`,
    s.permName && `권한명 ${s.permName}`,
    s.policyName && `정책 ${s.policyName}`,
    s.orgName && `조직 ${s.orgName}`,
    s.status && `상태 ${s.status}`,
    Array.isArray(s.roleIds) && `역할 ${s.roleIds.map((r) => admin.roleNameOf(r)).join('/')}`,
    s.useYn && `사용 ${s.useYn}`,
  ].filter(Boolean)
  return pick.join(' · ') || '-'
}

/** 민감 필드는 마스킹해서 상세 표시 */
const detailEntries = computed(() => {
  if (!detail.value?.snapshot) return []
  const MASK_KEYS = { userName: 'name', email: 'email', phone: 'phone' }
  return Object.entries(detail.value.snapshot)
    .filter(([k]) => !k.startsWith('_'))
    .map(([k, v]) => {
      let display
      if (v === null || v === undefined || v === '') display = '-'
      else if (Array.isArray(v)) display = v.join(', ')
      else if (typeof v === 'object') display = JSON.stringify(v)
      else display = MASK_KEYS[k] ? session.mask(v, MASK_KEYS[k]) : String(v)
      return { key: k, value: display }
    })
})

const canDownload = computed(() => session.can('AUD_DOWNLOAD', 'X'))

function download() {
  const result = session.check('AUD_DOWNLOAD', 'X')
  if (!result.allowed) {
    toast.error(result.reason)
    return
  }
  if (result.reason) toast.warn(result.reason)

  const header = ['로그ID', '변경시각', '대상', '구분', '대상키', '처리자ID', '처리자', '요약']
  const lines = rows.value.map((l) =>
    [
      l.logId,
      l.at,
      l.entityLabel,
      ACTION_LABEL[l.action] ?? l.action,
      l.targetKey,
      l.actorId,
      session.mask(l.actorName, 'name'),
      summarize(l).replaceAll('"', "'"),
    ]
      .map((v) => `"${String(v ?? '')}"`)
      .join(','),
  )
  // 엑셀에서 한글이 깨지지 않도록 BOM 을 붙인다.
  const blob = new Blob(['﻿' + [header.join(','), ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8;',
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  toast.success(`변경 이력 ${rows.value.length}건을 다운로드했습니다. (다운로드 사실은 감사 기록 대상입니다)`)
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">변경 이력</h1>
        <p class="page-desc">
          이 콘솔에서 발생한 사용자·조직·역할·권한·정책 변경 내역입니다. 최근 500건이 보관되며, 다운로드에는
          데이터 다운로드 권한(AUD_DOWNLOAD/X)이 필요합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn"
          :disabled="!canDownload || !rows.length"
          :title="canDownload ? 'CSV 다운로드' : (session.denyReason('AUD_DOWNLOAD', 'X') ?? '')"
          @click="download"
        >
          ⤓ CSV 다운로드
        </button>
      </div>
    </div>

    <div v-if="session.isMasked" class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span>
      <span>개인정보 마스킹 정책이 적용되어 이름·이메일·연락처가 가려져 표시·다운로드됩니다.</span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="filters.keyword" class="grow" label="검색어" placeholder="대상 키 / 처리자 / 로그ID" />
        <FormField v-model="filters.entity" label="대상" type="select" empty-option="전체" :options="ENTITY_OPTIONS" />
        <FormField v-model="filters.action" label="구분" type="select" empty-option="전체" :options="ACTION_OPTIONS" />
        <FormField v-model="filters.actorId" label="처리자" type="select" empty-option="전체" :options="actorOptions" />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="logId"
        :page-size="20"
        :default-sort="{ key: 'at', dir: 'desc' }"
      >
        <template #empty>
          아직 변경 이력이 없습니다. 역할·권한·정책·사용자를 수정하면 이 화면에 기록됩니다.
        </template>

        <template #cell-at="{ value }">
          <span class="mono small">{{ value }}</span>
        </template>

        <template #cell-action="{ value }">
          <span class="badge" :class="`badge-${ACTION_COLOR[value] ?? 'gray'}`">{{ ACTION_LABEL[value] ?? value }}</span>
        </template>

        <template #cell-actorName="{ row }">
          {{ session.mask(row.actorName, 'name') }}
          <span class="dim small mono">{{ row.actorId }}</span>
        </template>

        <template #cell-_summary="{ row }">
          <span class="truncate small" style="max-width: 44ch" :title="summarize(row)">{{ summarize(row) }}</span>
        </template>

        <template #cell-_act="{ row }">
          <button class="btn btn-sm" :disabled="!row.snapshot" @click="detail = row">상세</button>
        </template>
      </DataTable>
    </div>

    <ModalDialog
      v-if="detail"
      title="변경 이력 상세"
      :subtitle="`${detail.logId} · ${detail.at} · ${detail.entityLabel}`"
      @close="detail = null"
    >
      <div class="kv mb-2">
        <dt>대상 키</dt>
        <dd class="mono">{{ detail.targetKey }}</dd>
        <dt>구분</dt>
        <dd>
          <span class="badge" :class="`badge-${ACTION_COLOR[detail.action] ?? 'gray'}`">
            {{ ACTION_LABEL[detail.action] ?? detail.action }}
          </span>
        </dd>
        <dt>처리자</dt>
        <dd>{{ session.mask(detail.actorName, 'name') }} <span class="dim mono small">{{ detail.actorId }}</span></dd>
      </div>

      <div class="card">
        <div class="card-head"><span class="card-title">변경 후 스냅샷</span></div>
        <div class="card-body tight">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th style="width: 170px">항목</th>
                  <th>값</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in detailEntries" :key="e.key">
                  <td class="code">{{ e.key }}</td>
                  <td style="word-break: break-all">{{ e.value }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <template #footer>
        <button class="btn btn-primary" @click="detail = null">닫기</button>
      </template>
    </ModalDialog>
  </div>
</template>
