<script setup>
import { computed, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { loadCodes } from '@/api/codes.js'
import { menuGroups } from '@/router/index.js'
import { useAdminStore } from '@/stores/admin.js'
import { useOrgStore } from '@/stores/org.js'
import { useRoleStore } from '@/stores/role.js'
import { usePermissionStore } from '@/stores/permission.js'
import { usePolicyStore } from '@/stores/policy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ToastHost from '@/components/ToastHost.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()
const orgStore = useOrgStore()
const roleStore = useRoleStore()
const permStore = usePermissionStore()
const policyStore = usePolicyStore()
const session = useSessionStore()
const toast = useToastStore()

const collapsed = ref(localStorage.getItem('wms-admin-nav') === 'collapsed')
const theme = ref(document.documentElement.dataset.theme)
const booting = ref(false)
const resetAsk = ref(false)
const resetting = ref(false)
const logoutAsk = ref(false)

/** 로그인·비밀번호 변경 화면은 헤더·사이드바 없이 전체 화면으로 렌더한다. */
const isPublic = computed(() => route.meta.public === true || route.meta.passwordChange === true)

/**
 * 인증 상태가 되면 기준정보를 적재한다.
 * (라우터 가드가 세션을 먼저 준비하므로 여기서는 관리 데이터만 로딩한다)
 */
watch(
  () => session.isAuthenticated,
  async (authed) => {
    if (!authed) return
    booting.value = true
    try {
      // 공통코드는 모든 화면의 셀렉트박스·배지 라벨이다. 이걸 받기 전에
      // 화면을 띄우면 드롭다운이 빈 채로 그려지므로, 부팅이 끝날 때까지
      // RouterView 를 내보내지 않는다 (아래 v-if="booting").
      // 정책·감사이력 등 남은 Mock 화면 때문에 admin 스토어도 함께 채운다.
      await Promise.all([
        loadCodes(true),
        admin.loadAll(true), orgStore.load(true), roleStore.load(true), permStore.load(true),
        policyStore.load(true),
      ])
    } catch (e) {
      toast.error(`기준정보를 불러오지 못했습니다. ${e.message}`)
    } finally {
      booting.value = false
    }
  },
  { immediate: true },
)

function toggleNav() {
  collapsed.value = !collapsed.value
  localStorage.setItem('wms-admin-nav', collapsed.value ? 'collapsed' : 'expanded')
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  document.documentElement.dataset.theme = theme.value
  localStorage.setItem('wms-admin-theme', theme.value)
}

/** 사이드바 항목별 건수 표시 */
const counts = computed(() => ({
  users: admin.users.length,
  orgs: orgStore.orgs.length,
  roles: roleStore.roles.length,
  permissions: permStore.permissions.length,
  'role-permissions': roleStore.roles.reduce((n, r) => n + (r.permCount ?? 0), 0),
  policies: policyStore.policies.length,
  'audit-logs': admin.auditLogs.length,
}))

const routeByName = computed(() =>
  Object.fromEntries(router.getRoutes().filter((r) => r.name).map((r) => [r.name, r])),
)

function permOf(name) {
  return routeByName.value[name]?.meta?.perm ?? null
}

function menuAllowed(name) {
  const perm = permOf(name)
  return !perm || session.can(perm, 'R')
}

async function doLogout() {
  logoutAsk.value = false
  const name = session.currentUser?.userName
  await session.logout()
  toast.info(`${name ?? '사용자'}님, 로그아웃되었습니다.`)
  await router.replace({ name: 'login' })
}

async function doReset() {
  resetting.value = true
  try {
    await admin.resetAll()
    // 초기화 대상은 브라우저에 저장된 Mock 데이터뿐이다.
    // 로그인 세션과 PostgreSQL 데이터는 그대로 유지된다.
    toast.success('Mock 데이터를 초기 상태로 되돌렸습니다. (로그인 세션은 유지)')
    resetAsk.value = false
  } catch (e) {
    toast.error(e.message)
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <!-- 로그인 화면 -->
  <RouterView v-if="isPublic" />

  <!-- 관리 화면 -->
  <div v-else class="layout" :class="{ 'nav-collapsed': collapsed }">
    <div class="brand">
      <span class="brand-mark">F</span>
      <span v-if="!collapsed" class="brand-name">풀필먼트 관리</span>
    </div>

    <header class="appbar">
      <button class="btn btn-ghost btn-icon" title="메뉴 접기/펼치기" @click="toggleNav">☰</button>
      <span class="appbar-title">{{ route.meta.title }}</span>

      <span v-if="session.isReadOnly" class="badge badge-red" title="조회 전용 계정 정책(READONLY)이 적용되었습니다.">
        조회 전용
      </span>
      <span v-if="session.isMasked" class="badge badge-cyan" title="개인정보 마스킹 정책이 적용되었습니다.">
        마스킹 적용
      </span>

      <div class="appbar-spacer"></div>

      <div class="me" :title="`${session.currentUser?.userId} · ${session.currentUser?.orgName ?? ''}`">
        <span class="me-avatar">{{ session.currentUser?.userName?.[0] ?? '?' }}</span>
        <div class="me-text">
          <div class="me-name">
            {{ session.currentUser?.userName }}
            <span class="dim small">{{ session.currentUser?.orgName }}</span>
          </div>
          <div class="dim small">{{ session.myRoleNames.join(', ') || '역할 없음' }}</div>
        </div>
      </div>

      <button class="btn btn-ghost btn-icon" :title="theme === 'dark' ? '라이트 모드' : '다크 모드'" @click="toggleTheme">
        {{ theme === 'dark' ? '☀' : '☾' }}
      </button>
      <button class="btn btn-ghost btn-sm" title="샘플 데이터를 초기 상태로 되돌립니다." @click="resetAsk = true">
        초기화
      </button>
      <button class="btn btn-sm" title="로그아웃" @click="logoutAsk = true">로그아웃</button>
    </header>

    <nav class="sidenav">
      <template v-for="g in menuGroups" :key="g.label">
        <div class="nav-group-label">{{ collapsed ? '·' : g.label }}</div>
        <RouterLink
          v-for="m in g.items"
          :key="m.name"
          v-slot="{ isActive, navigate }"
          :to="{ name: m.name }"
          custom
        >
          <div
            class="nav-item"
            :class="{ active: isActive }"
            :style="menuAllowed(m.name) ? null : { opacity: 0.45 }"
            :title="menuAllowed(m.name) ? m.label : `${m.label} — 현재 계정에 조회 권한이 없습니다(${permOf(m.name)})`"
            @click="navigate"
          >
            <span class="nav-icon">{{ m.icon }}</span>
            <template v-if="!collapsed">
              <span>{{ m.label }}</span>
              <span v-if="counts[m.name] !== undefined" class="nav-count">{{ counts[m.name] }}</span>
            </template>
          </div>
        </RouterLink>
      </template>
    </nav>

    <main class="main">
      <div v-if="booting" class="card">
        <div class="card-body flex">
          <span class="spinner"></span>
          <span class="muted">기준정보를 불러오는 중입니다…</span>
        </div>
      </div>
      <RouterView v-else />
    </main>
  </div>

  <ConfirmDialog
    v-if="logoutAsk"
    title="로그아웃"
    :message="`'${session.currentUser?.userName}' 계정에서 로그아웃합니다.`"
    detail="저장하지 않은 편집 내용은 사라집니다."
    confirm-label="로그아웃"
    @cancel="logoutAsk = false"
    @confirm="doLogout"
  />

  <ConfirmDialog
    v-if="resetAsk"
    title="샘플 데이터 초기화"
    message="현재 브라우저에 저장된 모든 변경 내용을 지우고 초기 시드 데이터로 되돌립니다."
    detail="브라우저에 저장된 Mock 데이터만 초기화됩니다. 로그인 세션과 PostgreSQL 데이터는 영향을 받지 않습니다."
    confirm-label="초기화"
    danger
    :busy="resetting"
    @cancel="resetAsk = false"
    @confirm="doReset"
  />

  <ToastHost />
</template>

<style scoped>
.me {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px 3px 4px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface-2);
  max-width: 250px;
}

.me-avatar {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 12px;
}

.me-text {
  min-width: 0;
  line-height: 1.35;
}

.me-name {
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
}

.me-text > div {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
