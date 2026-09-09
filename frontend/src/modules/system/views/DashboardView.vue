<script setup>
/**
 * 권한 현황 대시보드.
 * 요구사항 표(역할 / 주요 권한 / 제한·승인)를 그대로 보여주면서,
 * 실제 등록된 권한 매핑·정책과의 차이(점검 항목)를 함께 노출한다.
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { CODE_GROUPS } from '@/api/codes.js'
import { useAdminStore } from '@/stores/admin.js'
import { useSessionStore } from '@/stores/session.js'
import CodeBadge from '@/components/CodeBadge.vue'

const admin = useAdminStore()
const session = useSessionStore()
const router = useRouter()

const stats = computed(() => [
  {
    label: '역할',
    value: admin.roles.length,
    sub: `사용 ${admin.roles.filter((r) => r.useYn === 'Y').length} / 미사용 ${admin.roles.filter((r) => r.useYn !== 'Y').length}`,
    to: 'roles',
  },
  {
    label: '권한',
    value: admin.permissions.length,
    sub: `${CODE_GROUPS.PERM_MODULE.length}개 모듈`,
    to: 'permissions',
  },
  {
    label: '역할-권한 매핑',
    value: admin.rolePermissions.length,
    sub: `평균 ${(admin.rolePermissions.length / Math.max(1, admin.roles.length)).toFixed(1)}개 / 역할`,
    to: 'role-permissions',
  },
  {
    label: '공통정책',
    value: admin.policies.length,
    sub: `차단 ${admin.policies.filter((p) => p.enforceLevel === 'BLOCK' && p.useYn === 'Y').length} · 경고 ${admin.policies.filter((p) => p.enforceLevel === 'WARN' && p.useYn === 'Y').length}`,
    to: 'policies',
  },
  {
    label: '사용자',
    value: admin.users.length,
    sub: `정상 ${admin.users.filter((u) => u.status === 'ACTIVE').length} · 잠김 ${admin.users.filter((u) => u.status === 'LOCKED').length}`,
    to: 'users',
  },
  {
    label: '조직',
    value: admin.orgs.length,
    sub: CODE_GROUPS.ORG_TYPE.map((t) => `${t.label} ${admin.orgs.filter((o) => o.orgType === t.code).length}`).join(' · '),
    to: 'orgs',
  },
])

/** 역할별 요약 (요구사항 표 재현 + 실제 등록 현황) */
const roleRows = computed(() => {
  const max = Math.max(1, ...admin.roles.map((r) => admin.grantsOf(r.roleId).length))
  return [...admin.roles]
    .sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))
    .map((r) => ({
      ...r,
      permCount: admin.grantsOf(r.roleId).length,
      permRatio: Math.round((admin.grantsOf(r.roleId).length / max) * 100),
      policies: admin.policiesOf(r.roleId).filter((p) => p.useYn === 'Y'),
      userCount: admin.userCountOf(r.roleId),
    }))
})

/* ---------------------------------------------------------------- */
/* 점검 항목                                                         */
/* ---------------------------------------------------------------- */

const unmappedPerms = computed(() =>
  admin.permissions.filter((p) => !admin.rolePermissions.some((m) => m.permId === p.permId)),
)

const rolesWithoutPolicy = computed(() =>
  admin.roles.filter((r) => r.useYn === 'Y' && r.restriction && admin.policiesOf(r.roleId).every((p) => p.useYn !== 'Y')),
)

const rolesWithoutPerm = computed(() =>
  admin.roles.filter((r) => r.useYn === 'Y' && admin.grantsOf(r.roleId).length === 0),
)

const usersWithoutRole = computed(() => admin.users.filter((u) => !u.roleIds?.length))

const lockedUsers = computed(() => admin.users.filter((u) => u.status === 'LOCKED'))

/** 소속 조직유형과 역할 적용범위가 어긋난 사용자 (데이터 정합성 이상) */
const scopeMismatch = computed(() =>
  admin.users
    .map((u) => {
      const orgType = admin.orgMap[u.orgId]?.orgType
      const bad = (u.roleIds ?? []).filter((rid) => admin.roleMap[rid] && admin.roleMap[rid].orgScope !== orgType)
      return bad.length ? { ...u, bad } : null
    })
    .filter(Boolean),
)

const checks = computed(() => [
  {
    label: '어떤 역할에도 매핑되지 않은 권한',
    count: unmappedPerms.value.length,
    detail: unmappedPerms.value.map((p) => p.permName).join(', '),
    to: 'permissions',
    tone: 'warn',
  },
  {
    label: '제한사항이 있으나 정책이 없는 역할',
    count: rolesWithoutPolicy.value.length,
    detail: rolesWithoutPolicy.value.map((r) => r.roleName).join(', '),
    to: 'policies',
    tone: 'warn',
  },
  {
    label: '권한이 하나도 없는 사용 중 역할',
    count: rolesWithoutPerm.value.length,
    detail: rolesWithoutPerm.value.map((r) => r.roleName).join(', '),
    to: 'role-permissions',
    tone: 'warn',
  },
  {
    label: '역할이 배정되지 않은 사용자',
    count: usersWithoutRole.value.length,
    // 마스킹 정책이 적용된 계정에서는 이름도 가려서 노출한다.
    detail: usersWithoutRole.value.map((u) => session.mask(u.userName, 'name')).join(', '),
    to: 'users',
    tone: 'danger',
  },
  {
    label: '소속·역할 범위가 어긋난 사용자',
    count: scopeMismatch.value.length,
    detail: scopeMismatch.value
      .map((u) => `${session.mask(u.userName, 'name')}(${u.bad.map((b) => admin.roleNameOf(b)).join('/')})`)
      .join(', '),
    to: 'users',
    tone: 'danger',
  },
  {
    label: '잠긴 계정',
    count: lockedUsers.value.length,
    detail: lockedUsers.value.map((u) => session.mask(u.userName, 'name')).join(', '),
    to: 'users',
    tone: 'warn',
  },
])

const openChecks = computed(() => checks.value.filter((c) => c.count > 0))

/** 현재 접속 계정의 유효 권한 요약 */
const myPerms = computed(() =>
  Object.entries(session.myGrants)
    .map(([permId, set]) => ({
      permId,
      permName: admin.permNameOf(permId),
      module: admin.permMap[permId]?.module,
      actions: [...set].sort(),
    }))
    .sort((a, b) => String(a.module).localeCompare(String(b.module))),
)

function go(name) {
  router.push({ name })
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">권한 현황</h1>
        <p class="page-desc">
          역할·권한·공통정책·사용자의 등록 현황과 정합성 점검 결과입니다. 요약 카드를 누르면 해당 관리 화면으로 이동합니다.
        </p>
      </div>
    </div>

    <div class="stat-grid mb-2">
      <div
        v-for="s in stats"
        :key="s.label"
        class="stat"
        style="cursor: pointer"
        :title="`${s.label} 관리로 이동`"
        @click="go(s.to)"
      >
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value">{{ s.value.toLocaleString() }}</div>
        <div class="stat-sub">{{ s.sub }}</div>
      </div>
    </div>

    <!-- 점검 -->
    <div class="card">
      <div class="card-head">
        <span class="card-title">정합성 점검</span>
        <span v-if="openChecks.length === 0" class="badge badge-green">이상 없음</span>
        <span v-else class="badge badge-amber">확인 필요 {{ openChecks.length }}건</span>
      </div>
      <div class="card-body tight">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th style="width: 300px">점검 항목</th>
                <th style="width: 76px" class="center">건수</th>
                <th>대상</th>
                <th style="width: 96px"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in checks" :key="c.label" :class="{ muted: c.count === 0 }">
                <td>{{ c.label }}</td>
                <td class="center">
                  <span
                    v-if="c.count > 0"
                    class="badge"
                    :class="c.tone === 'danger' ? 'badge-red' : 'badge-amber'"
                    >{{ c.count }}</span
                  >
                  <span v-else class="badge badge-green plain">0</span>
                </td>
                <td>
                  <span class="truncate small" style="max-width: 56ch" :title="c.detail">{{ c.detail || '-' }}</span>
                </td>
                <td class="right">
                  <button class="btn btn-sm" :disabled="c.count === 0" @click="go(c.to)">이동</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 역할별 현황 -->
    <div class="card">
      <div class="card-head">
        <span class="card-title">역할별 권한·정책 현황</span>
        <div class="card-head-actions">
          <button class="btn btn-sm" @click="go('roles')">역할 관리</button>
          <button class="btn btn-sm" @click="go('role-permissions')">권한 매핑</button>
        </div>
      </div>
      <div class="card-body tight">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th style="width: 130px">역할</th>
                <th style="width: 78px" class="center">범위</th>
                <th style="width: 230px">주요 권한</th>
                <th style="width: 210px">제한/승인</th>
                <th style="width: 130px">권한 수</th>
                <th style="width: 190px">등록 정책</th>
                <th style="width: 70px" class="center">사용자</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in roleRows" :key="r.roleId" :class="{ muted: r.useYn !== 'Y' }">
                <td>
                  <div class="bold">{{ r.roleName }}</div>
                  <div class="code">{{ r.roleId }}</div>
                </td>
                <td class="center"><CodeBadge group="ORG_TYPE" :code="r.orgScope" /></td>
                <td><span class="truncate small" :title="r.summary">{{ r.summary }}</span></td>
                <td>
                  <span v-if="r.restriction" class="truncate small" :title="r.restriction">{{ r.restriction }}</span>
                  <span v-else class="dim">-</span>
                </td>
                <td>
                  <div class="flex" style="gap: 6px">
                    <span style="min-width: 22px; text-align: right" class="bold">{{ r.permCount }}</span>
                    <span class="bar" style="flex: 1"><span :style="{ width: `${r.permRatio}%` }"></span></span>
                  </div>
                </td>
                <td>
                  <div v-if="r.policies.length" class="chip-list">
                    <span
                      v-for="p in r.policies"
                      :key="p.policyId"
                      class="chip"
                      :title="`${p.policyName} — ${p.message}`"
                    >
                      <CodeBadge group="POLICY_TYPE" :code="p.policyType" />
                    </span>
                  </div>
                  <span v-else-if="r.restriction" class="badge badge-amber plain">정책 미등록</span>
                  <span v-else class="dim">-</span>
                </td>
                <td class="center">{{ r.userCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 접속 계정 -->
    <div class="card">
      <div class="card-head">
        <span class="card-title">현재 접속 계정의 유효 권한</span>
        <span class="dim small">
          {{ session.currentUser?.userName }} · {{ session.myRoleNames.join(', ') || '역할 없음' }}
        </span>
        <div class="card-head-actions">
          <span v-if="session.isReadOnly" class="badge badge-red">조회 전용</span>
          <span v-if="session.isMasked" class="badge badge-cyan">마스킹</span>
        </div>
      </div>
      <div class="card-body">
        <div v-if="myPerms.length" class="chip-list">
          <span v-for="p in myPerms" :key="p.permId" class="chip" :title="p.permId">
            <CodeBadge group="PERM_MODULE" :code="p.module" />
            {{ p.permName }}
            <span class="mono dim">{{ p.actions.join('') }}</span>
          </span>
        </div>
        <div v-else class="alert alert-warn">
          <span class="alert-icon">⚠</span>
          <span>이 계정에는 부여된 권한이 없습니다. 헤더의 접속 계정을 바꿔 역할별 동작을 확인할 수 있습니다.</span>
        </div>

        <div v-if="session.myPolicies.length" class="mt-2">
          <div class="field-label mb-1">적용 중인 정책 ({{ session.myPolicies.length }})</div>
          <div class="flex-col">
            <div v-for="p in session.myPolicies" :key="p.policyId" class="alert">
              <span class="alert-icon">⚖</span>
              <span>
                <CodeBadge group="POLICY_TYPE" :code="p.policyType" />
                <CodeBadge group="ENFORCE_LEVEL" :code="p.enforceLevel" />
                <strong> {{ p.policyName }}</strong>
                <div class="dim">{{ p.message }}</div>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
