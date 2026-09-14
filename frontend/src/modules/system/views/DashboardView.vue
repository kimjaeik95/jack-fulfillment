<script setup>
/**
 * 권한 현황 (COM-PG-003 요약).
 *
 * 등록 현황과 정합성 점검을 한 화면에서 보여준다. 요약 카드를 누르면 해당
 * 관리 화면으로 간다.
 *
 * 전에는 이 화면만 Mock(localStorage) 데이터를 읽고 있었다. 그래서 이미
 * 지워진 매장 역할을 계속 보여주고, 역할 수 · 조직 수가 사이드바와 서로
 * 다른 값을 말했다. 정합성 점검까지 Mock 기준이라 "확인 필요 N건" 을
 * 믿을 수 없었다. 실제 서버를 읽도록 다시 썼다.
 *
 * 건수는 모두 서버가 계산해 내려준 값을 쓴다(permCount · policyCount ·
 * userCount 등). 화면에서 다시 세면 서버와 어긋날 여지가 생긴다.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as userApi from '@/api/user.js'
import { useRoleStore } from '@/stores/role.js'
import { usePermissionStore } from '@/stores/permission.js'
import { usePolicyStore } from '@/stores/policy.js'
import { useOrgStore } from '@/stores/org.js'
import { useHierarchyStore } from '@/stores/hierarchy.js'
import { useCatalogStore } from '@/stores/catalog.js'
import { useSessionStore } from '@/stores/session.js'
import CodeBadge from '@/components/CodeBadge.vue'

const router = useRouter()
const roleStore = useRoleStore()
const permStore = usePermissionStore()
const policyStore = usePolicyStore()
const orgStore = useOrgStore()
const hierarchy = useHierarchyStore()
const catalog = useCatalogStore()
const session = useSessionStore()

const loading = ref(true)
/** 권한이 없어 못 읽은 부분의 이름. 숫자를 0 으로 보여주면 거짓말이 된다. */
const unreadable = ref([])

/** 사용자는 전용 스토어가 없다. 이 화면에서만 전체를 한 번 읽는다. */
const users = ref([])

async function loadAll() {
  loading.value = true
  const missing = []

  // 하나가 막혀도 나머지는 보여준다. 역할마다 읽을 수 있는 범위가 다르다.
  await Promise.all([
    roleStore.load(true),
    permStore.load(true),
    policyStore.load(true),
    orgStore.load(true),
    hierarchy.loadAll(true),
    catalog.loadCategories(true),
    catalog.loadBrands(true),
    catalog.loadProducts(true),
    userApi
      .list({ size: 0 })
      .then((p) => {
        users.value = p.rows
      })
      .catch(() => {
        users.value = []
        missing.push('사용자')
      }),
  ])

  if (roleStore.denyReason) missing.push('역할')
  if (permStore.denyReason) missing.push('권한')
  if (policyStore.denyReason) missing.push('공통정책')
  if (orgStore.denyReason) missing.push('조직')
  if (hierarchy.denyReason.companies) missing.push('회사')
  if (hierarchy.denyReason.plants) missing.push('플랜트')
  if (catalog.denyReason.categories) missing.push('분류')
  if (catalog.denyReason.products) missing.push('제품')

  unreadable.value = [...new Set(missing)]
  loading.value = false
}

onMounted(loadAll)

/* ---------------------------------------------------------------- */
/* 요약 카드                                                         */
/* ---------------------------------------------------------------- */

const n = (arr) => arr?.length ?? 0
const countBy = (arr, fn) => arr.filter(fn).length

const cards = computed(() => [
  {
    label: '역할',
    value: n(roleStore.roles),
    sub: `사용 ${countBy(roleStore.roles, (r) => r.useYn === 'Y')} / 미사용 ${countBy(roleStore.roles, (r) => r.useYn !== 'Y')}`,
    to: 'roles',
  },
  {
    label: '권한',
    value: n(permStore.permissions),
    sub: `${new Set(permStore.permissions.map((p) => p.moduleCode)).size}개 모듈`,
    to: 'permissions',
  },
  {
    label: '역할-권한 매핑',
    // 서버가 역할마다 세어 준 값을 합친다. 매핑 목록을 따로 받지 않아도 된다.
    value: roleStore.roles.reduce((sum, r) => sum + (r.permCount ?? 0), 0),
    sub: `평균 ${(roleStore.roles.reduce((s, r) => s + (r.permCount ?? 0), 0) / Math.max(1, n(roleStore.roles))).toFixed(1)}개 / 역할`,
    to: 'role-permissions',
  },
  {
    label: '공통정책',
    value: n(policyStore.policies),
    sub: `차단 ${countBy(policyStore.policies, (p) => p.enforceLevel === 'BLOCK' && p.useYn === 'Y')} · 경고 ${countBy(policyStore.policies, (p) => p.enforceLevel === 'WARN' && p.useYn === 'Y')}`,
    to: 'policies',
  },
  {
    label: '사용자',
    value: n(users.value),
    sub: `정상 ${countBy(users.value, (u) => u.status === 'ACTIVE')} · 잠김 ${countBy(users.value, (u) => u.status === 'LOCKED')}`,
    to: 'users',
  },
  {
    label: '회사 · 조직',
    value: n(orgStore.orgs),
    sub: `회사 ${n(hierarchy.companies)} · 조직 ${n(orgStore.orgs)}`,
    to: 'orgs',
  },
  {
    label: '거점',
    value: n(hierarchy.plants),
    sub: `창고 ${n(hierarchy.warehouses)} · 빈 ${hierarchy.plants.reduce((s, p) => s + (p.locationCount ?? 0), 0)}`,
    to: 'plants',
  },
  {
    label: '제품 · SKU',
    value: n(catalog.products),
    sub: `분류 ${n(catalog.categories)} · 브랜드 ${n(catalog.brands)} · SKU ${catalog.products.reduce((s, p) => s + (p.skuCount ?? 0), 0)}`,
    to: 'products',
  },
])

/* ---------------------------------------------------------------- */
/* 역할별 권한 · 정책 현황                                            */
/* ---------------------------------------------------------------- */

const roleRows = computed(() => {
  const max = Math.max(1, ...roleStore.roles.map((r) => r.permCount ?? 0))
  return [...roleStore.roles]
    .sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))
    .map((r) => ({
      ...r,
      permRatio: Math.round(((r.permCount ?? 0) / max) * 100),
      policies: policyStore.policies.filter((p) => p.roleId === r.roleId && p.useYn === 'Y'),
    }))
})

/* ---------------------------------------------------------------- */
/* 정합성 점검                                                       */
/* ---------------------------------------------------------------- */

/** 어떤 역할에도 매핑되지 않은 권한 — 아무도 쓸 수 없는 기능이다 */
const unmappedPerms = computed(() =>
  permStore.permissions.filter((p) => (p.roleCount ?? 0) === 0),
)

/** 제한사항을 적어 두었으나 정책으로 등록하지 않은 역할 — 통제가 동작하지 않는다 */
const rolesWithoutPolicy = computed(() =>
  roleStore.roles.filter(
    (r) => r.useYn === 'Y' && r.restrictionSummary && (r.policyCount ?? 0) === 0,
  ),
)

/** 권한이 하나도 없는 사용 중 역할 — 배정해도 아무것도 못 한다 */
const rolesWithoutPerm = computed(() =>
  roleStore.roles.filter((r) => r.useYn === 'Y' && (r.permCount ?? 0) === 0),
)

const usersWithoutRole = computed(() => users.value.filter((u) => !u.roleIds?.length))

const lockedUsers = computed(() => users.value.filter((u) => u.status === 'LOCKED'))

/**
 * 소속 조직유형과 역할 적용범위가 어긋난 사용자.
 *
 * 서버가 저장 시점에 막지만, 조직유형을 나중에 바꾸면 기존 사용자가 어긋난
 * 채로 남을 수 있다. 그걸 찾아낸다.
 */
const scopeMismatch = computed(() =>
  users.value
    .map((u) => {
      const bad = (u.roleIds ?? []).filter(
        (rid) => roleStore.roleMap[rid] && roleStore.roleMap[rid].orgScope !== u.orgType,
      )
      return bad.length ? { ...u, bad } : null
    })
    .filter(Boolean),
)

/** SKU 가 없는 제품 — 재고를 잡을 수 없어 팔 수 없다 */
const productsWithoutSku = computed(() =>
  catalog.products.filter((p) => (p.skuCount ?? 0) === 0),
)

/** 제품이 하나도 없는 소분류 — 분류만 만들고 채우지 않은 것 */
const emptyLeafCategories = computed(() =>
  catalog.categories.filter((c) => c.levelNo === 3 && (c.productCount ?? 0) === 0),
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
    detail: usersWithoutRole.value.map((u) => u.userName).join(', '),
    to: 'users',
    tone: 'warn',
  },
  {
    label: '소속·역할 범위가 어긋난 사용자',
    count: scopeMismatch.value.length,
    detail: scopeMismatch.value.map((u) => `${u.userName}(${u.bad.join(',')})`).join(', '),
    to: 'users',
    tone: 'danger',
  },
  {
    label: '잠긴 계정',
    count: lockedUsers.value.length,
    detail: lockedUsers.value.map((u) => u.userName).join(', '),
    to: 'users',
    tone: 'warn',
  },
  {
    label: 'SKU 가 없는 제품 (재고를 잡을 수 없음)',
    count: productsWithoutSku.value.length,
    detail: productsWithoutSku.value.map((p) => p.productName).join(', '),
    to: 'skus',
    tone: 'warn',
  },
  {
    label: '제품이 없는 소분류',
    count: emptyLeafCategories.value.length,
    detail: emptyLeafCategories.value.map((c) => c.pathName ?? c.categoryName).join(', '),
    to: 'categories',
    tone: 'info',
  },
])

const attention = computed(() => checks.value.filter((c) => c.count > 0 && c.tone !== 'info'))

const go = (name) => router.push({ name })

const readDenyReason = computed(() => session.denyReason('SYS_ROLE', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">권한 현황</h1>
        <p class="page-desc">
          역할·권한·공통정책·사용자·기준정보의 등록 현황과 정합성 점검 결과입니다.
          요약 카드를 누르면 해당 관리 화면으로 이동합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn" :disabled="loading" @click="loadAll()">
          <span v-if="loading" class="spinner"></span>
          새로고침
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>

    <!--
      권한이 없어 못 읽은 영역이 있으면 반드시 말한다. 0 으로 보여주면
      "없다" 와 "못 봤다" 를 구분할 수 없어 화면이 거짓말을 하게 된다.
    -->
    <div v-if="unreadable.length" class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span>
      <span>
        조회 권한이 없어 <strong>{{ unreadable.join(' · ') }}</strong> 는 집계에서 빠졌습니다.
        해당 숫자는 0 으로 보이지만 실제로 없는 것은 아닙니다.
      </span>
    </div>

    <div class="stat-row">
      <button v-for="c in cards" :key="c.label" class="stat" @click="go(c.to)">
        <div class="stat-label">{{ c.label }}</div>
        <div class="stat-value">{{ c.value.toLocaleString() }}</div>
        <div class="stat-sub">{{ c.sub }}</div>
      </button>
    </div>

    <div class="card mt-3">
      <div class="card-head">
        <span>정합성 점검</span>
        <span v-if="attention.length" class="badge badge-amber">확인 필요 {{ attention.length }}건</span>
        <span v-else class="badge badge-green">이상 없음</span>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th style="width: 320px">점검 항목</th>
            <th style="width: 80px" class="center">건수</th>
            <th>대상</th>
            <th style="width: 90px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in checks" :key="c.label" :class="{ dim: c.count === 0 }">
            <td>{{ c.label }}</td>
            <td class="center">
              <span class="badge" :class="c.count ? `badge-${c.tone === 'danger' ? 'red' : c.tone === 'info' ? 'gray' : 'amber'}` : 'badge-green'">
                {{ c.count }}
              </span>
            </td>
            <td class="small">{{ c.detail || '-' }}</td>
            <td class="right">
              <button class="btn btn-sm" :disabled="!c.count" @click="go(c.to)">이동</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card mt-3">
      <div class="card-head">
        <span>역할별 권한·정책 현황</span>
        <div class="btn-row">
          <button class="btn btn-sm" @click="go('roles')">역할 관리</button>
          <button class="btn btn-sm" @click="go('role-permissions')">권한 매핑</button>
        </div>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th style="width: 190px">역할</th>
            <th style="width: 96px" class="center">범위</th>
            <th style="width: 230px">제한/승인</th>
            <th style="width: 170px">권한 수</th>
            <th style="width: 150px">등록 정책</th>
            <th style="width: 80px" class="right">사용자</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in roleRows" :key="r.roleId" :class="{ dim: r.useYn !== 'Y' }">
            <td>
              <div>{{ r.roleName }}</div>
              <div class="small dim code">{{ r.roleId }}</div>
            </td>
            <td class="center"><CodeBadge group="ORG_TYPE" :code="r.orgScope" /></td>
            <td class="small">{{ r.restrictionSummary || '-' }}</td>
            <td>
              <div class="bar-row">
                <span class="bar-value">{{ r.permCount ?? 0 }}</span>
                <span class="bar"><span class="bar-fill" :style="{ width: `${r.permRatio}%` }"></span></span>
              </div>
            </td>
            <td>
              <span v-if="!r.policies.length" class="small dim">-</span>
              <CodeBadge
                v-for="p in r.policies"
                :key="p.policyId"
                group="POLICY_TYPE"
                :code="p.policyType"
                :title="p.policyName"
              />
            </td>
            <td class="right">{{ r.userCount ?? 0 }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.stat {
  text-align: left;
  background: var(--panel, #fff);
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
}
.stat:hover {
  border-color: var(--primary, #2563eb);
}
.stat-label {
  font-size: 12px;
  color: var(--dim, #6b7280);
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.3;
}
.stat-sub {
  font-size: 12px;
  color: var(--dim, #6b7280);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--line, #e5e7eb);
  font-weight: 600;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bar-value {
  min-width: 22px;
  text-align: right;
}
.bar {
  flex: 1;
  height: 6px;
  background: var(--line, #e5e7eb);
  border-radius: 3px;
  overflow: hidden;
}
.bar-fill {
  display: block;
  height: 100%;
  background: var(--primary, #2563eb);
}
</style>
