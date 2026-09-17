<script setup>
import { computed, provide, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { loadCodes } from '@/api/codes.js'
import { useMenuStore } from '@/stores/menu.js'
import { useAdminStore } from '@/stores/admin.js'
import { useOrgStore } from '@/stores/org.js'
import { useRoleStore } from '@/stores/role.js'
import { usePermissionStore } from '@/stores/permission.js'
import { usePolicyStore } from '@/stores/policy.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import ToastHost from '@/components/ToastHost.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import NavNode from '@/components/NavNode.vue'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()
const orgStore = useOrgStore()
const roleStore = useRoleStore()
const permStore = usePermissionStore()
const policyStore = usePolicyStore()
const menuStore = useMenuStore()
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
        // 사이드바 구성. 이게 없으면 어떤 화면으로도 이동할 수 없다.
        menuStore.load(true),
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

/**
 * 사이드바 항목별 건수 표시 — 키는 라우트 이름이다 (메뉴가 라우트를 가리킨다).
 *
 * 서버에서 읽은 스토어만 쓴다. 전에는 사용자 · 변경이력 건수가 Mock 에서
 * 나와 권한 현황 화면과 서로 다른 숫자를 말했다. 세어 줄 값이 없는 항목은
 * 아예 표시하지 않는다 — 틀린 숫자보다 없는 편이 낫다.
 */
const counts = computed(() => ({
  orgs: orgStore.orgs.length,
  roles: roleStore.roles.length,
  permissions: permStore.permissions.length,
  'role-permissions': roleStore.roles.reduce((n, r) => n + (r.permCount ?? 0), 0),
  policies: policyStore.policies.length,
}))

const routeByName = computed(() =>
  Object.fromEntries(router.getRoutes().filter((r) => r.name).map((r) => [r.name, r])),
)

/**
 * 메뉴가 가리키는 라우트가 실제로 있는가.
 *
 * 메뉴는 데이터고 라우트는 코드라, 화면을 지우거나 이름을 바꾸면 갈 곳 없는
 * 메뉴가 남는다. 그걸 눌렀을 때 아무 일도 일어나지 않으면 고장으로 보이므로,
 * 사이드바에서 미리 표시하고 이동을 막는다.
 */
function routeExists(name) {
  return Boolean(name && routeByName.value[name])
}

/** 메뉴에 걸린 권한 — 서버가 함께 내려준다 (없으면 라우트 meta 로 되짚는다) */
function permOf(menu) {
  return menu.permId ?? routeByName.value[menu.routeName]?.meta?.perm ?? null
}

/**
 * 보이는 메뉴는 서버가 이미 걸렀다. 그래도 한 번 더 보는 이유는, 권한이
 * 세션 중에 바뀔 수 있고(역할-권한 매핑 저장 후 refresh) 그때 사이드바가
 * 바로 흐려져야 하기 때문이다.
 */
function menuAllowed(menu) {
  const perm = permOf(menu)
  return !perm || session.can(perm, 'R')
}

function menuTitle(menu) {
  // 아래쪽 목록에는 그룹 머리글이 없다. 어디 것인지는 여기서 말해 준다.
  const where = menu.groupName ? `${menu.groupName} › ` : ''
  if (!routeExists(menu.routeName)) {
    return `${where}${menu.menuName} — 연결된 화면(${menu.routeName})이 없습니다. 메뉴 관리에서 확인하세요.`
  }
  return menuAllowed(menu)
    ? menu.menuName
    : `${where}${menu.menuName} — 열 수는 있지만 내용은 보이지 않습니다. 필요한 권한: ${permOf(menu)}`
}

/**
 * 사이드바 순서 — 쓸 수 있는 그룹을 위로, 전부 잠긴 그룹을 아래로.
 *
 * 메뉴는 전부 보여 준다. 감추면 그런 기능이 있다는 것조차 알 수 없어
 * 권한을 요청할 생각도 못 한다. 흐리게 칠하지도 않는다 — 못 쓰는 항목이
 * 화면의 3분의 2인 역할이 있어서, 그때는 흐린 글씨가 배경처럼 깔려
 * 오히려 지저분해진다.
 *
 * 대신 순서로 말한다. 센터관리자는 24개 메뉴 중 16개를 못 쓰는데, 정작
 * 매일 쓰는 재고가 23번째에 있었다. 잠긴 그룹을 내리면 재고가 두 번째로
 * 올라온다.
 *
 * 역할별 정렬 테이블을 따로 두지 않는다. 이미 아는 권한으로 그릴 때
 * 계산하면 되고, 그러면 역할이 둘인 사람은 어느 순서를 쓸지 같은 문제가
 * 아예 생기지 않는다.
 *
 * 그룹 <b>안</b>의 순서는 건드리지 않는다. 기준정보가 늘 같은 순서여야
 * 손이 기억한다 — 움직이는 것은 그룹 덩어리뿐이다.
 */
/**
 * 권한이 없는 메뉴는 아예 내보내지 않는다.
 *
 * 전에는 아래쪽 '권한 없음' 묶음에 모아 두었다 — 있는 줄은 알아야 권한을
 * 요청할 수 있다는 이유였다. 그런데 그 묶음이 사이드바의 대부분을 차지했다.
 * 피킹 담당은 자기가 쓸 6개 아래에 못 쓰는 38줄을 달고 다녔고, 감사 담당은
 * 1개를 쓰려고 43줄을 지나가야 했다. 못 쓰는 것을 알리는 값보다 쓸 것을 못
 * 찾게 만드는 값이 컸다. 무엇이 더 필요한지는 관리자가 안다.
 *
 * 아래에서 위로 걸러야 한다. 머리글에는 권한이 안 달려 있어서(그 자체로는
 * 갈 곳이 아니므로) 한 단만 보면 늘 통과한다. 속이 빈 '거래처' 머리글이
 * 남아, 눌러 펴면 아무것도 없는 일이 생긴다.
 *
 * 가리키는 화면이 없는 메뉴(routeExists=false)는 감추지 않는다. 그건 권한
 * 문제가 아니라 메뉴와 라우트가 어긋났다는 뜻이라, 보여야 고친다.
 */
function pruneMenus(nodes) {
  return nodes.flatMap((n) => {
    if (n.children?.length) {
      const kids = pruneMenus(n.children)
      // 다 걸러진 머리글은 머리글째 사라진다
      return kids.length ? [{ ...n, children: kids }] : []
    }
    return menuAllowed(n) ? [n] : []
  })
}

const sortedGroups = computed(() => pruneMenus(menuStore.groups))

/* ------------------------------------------------------------------ */
/* 그룹 접기                                                           */
/* ------------------------------------------------------------------ */

/*
 * 그룹을 접는다.
 *
 * 시스템 관리자는 마스터라 45개를 전부 본다. 그룹 머리글까지 54줄이 한 번에
 * 깔려서, 찾으려던 것이 어디 있는지 눈으로 훑어야 했다.
 *
 * 접는 기준은 '지금 보고 있는 화면이 속한 그룹만 편다' 다. 사람이 다음에
 * 누를 것은 대개 방금 누른 것 옆에 있다.
 */
const NAV_OPEN_KEY = 'wms-nav-open'

const openGroups = ref(new Set())

/** 트리를 훑어 잎(실제로 갈 수 있는 화면)만 모은다 */
function leavesOf(nodes) {
  return nodes.flatMap((n) =>
    n.children?.length ? leavesOf(n.children) : (routeExists(n.routeName) ? [n] : []),
  )
}

/** 사이드바를 접었을 때 쓰는 평면 목록 */
const flatMenus = computed(() => leavesOf(sortedGroups.value))

/** 지금 화면에 이르는 길 위의 그룹들 — 그 화면이 보이려면 전부 펴져 있어야 한다 */
function pathTo(nodes, routeName, trail = []) {
  for (const n of nodes) {
    if (n.routeName === routeName) return trail
    if (n.children?.length) {
      const found = pathTo(n.children, routeName, [...trail, n.menuId])
      if (found) return found
    }
  }
  return null
}

/** 저장된 것이 있으면 그걸로, 없으면 지금 화면의 그룹만 */
function loadOpenGroups() {
  try {
    const saved = JSON.parse(localStorage.getItem(NAV_OPEN_KEY) ?? 'null')
    if (Array.isArray(saved)) return new Set(saved)
  } catch {
    /* 저장된 값이 깨졌으면 없는 셈 친다 */
  }
  return null
}

function toggleGroup(menuId) {
  navTouched.value = true
  const next = new Set(openGroups.value)
  next.has(menuId) ? next.delete(menuId) : next.add(menuId)
  openGroups.value = next
  try {
    localStorage.setItem(NAV_OPEN_KEY, JSON.stringify([...next]))
  } catch {
    /* 저장이 막혀 있어도 이번 화면에서는 접고 펴진다 */
  }
}

const isGroupOpen = (g) => isFlat(g) || openGroups.value.has(g.menuId)

/**
 * 지금 화면에 이르는 길 위의 그룹 전부.
 *
 * 한 단만 펴서는 안 된다. '제품 관리' 는 기준정보 › 제품 아래에 있어서, 둘 다
 * 펴야 보인다. 하나만 펴면 활성 표시가 접힌 그룹 안에 숨어 어디에 있는지
 * 알 수 없다.
 */
const currentTrail = computed(() => pathTo(sortedGroups.value, route.name) ?? [])

/*
 * 화면을 옮기면 그리로 가는 길을 편다. 다른 곳을 접지는 않는다 — 열어 둔 것을
 * 닫아 버리면 방금 훑던 목록이 사라져 어디에서 왔는지 놓친다.
 */
watch(currentTrail, (trail) => {
  if (!trail.length) return
  if (trail.every((id) => openGroups.value.has(id))) return
  openGroups.value = new Set([...openGroups.value, ...trail])
})

/**
 * 접기가 도움이 되는 크기를 넘었는가.
 *
 * 시스템 관리자는 45개를 보지만 피킹 담당은 6개다. 6개를 접어 두면 사이드바가
 * 머리글 두 줄로 남아, 접기가 덜어 주는 것 없이 펴는 품만 늘린다. 적으면
 * 그냥 다 펴 둔다.
 */
const NAV_FOLD_THRESHOLD = 12

/** 머리글을 전부 모은다 — 처음부터 다 펴 둘 때 쓴다 */
function allGroupIds(nodes) {
  return nodes.flatMap((n) => (n.children?.length ? [n.menuId, ...allGroupIds(n.children)] : []))
}

/*
 * 사람이 직접 접거나 편 적이 있는가.
 *
 * 기본값을 한 번만 정하면 안 된다. 메뉴는 로그인 직후 한 번, 권한이 들어온 뒤
 * 한 번 더 바뀌는데, 첫 번째 것으로 정하면 아직 걸러지지 않은 45개를 보고
 * '접어야겠다' 고 판단한다. 정작 그 사람이 쓸 수 있는 것은 6개인데 접힌 채로
 * 남는다 — 실제로 그렇게 나왔다.
 *
 * 그래서 사람이 손대기 전까지는 트리가 바뀔 때마다 다시 정한다.
 */
const navTouched = ref(false)

watch(
  sortedGroups,
  (groups) => {
    if (!groups.length || navTouched.value) return

    const saved = loadOpenGroups()
    if (saved) {
      openGroups.value = saved
      navTouched.value = true
      return
    }

    // 적으면 다 펴고, 많으면 지금 화면까지 가는 길만 편다
    openGroups.value =
      flatMenus.value.length <= NAV_FOLD_THRESHOLD
        ? new Set(allGroupIds(groups))
        : new Set(currentTrail.value)
  },
  { immediate: true },
)

/*
 * 한 줄을 그리는 데 필요한 것을 NavNode 에게 넘긴다. 단이 깊어질수록 props 로
 * 지나가기만 하는 인자가 늘어나 provide 로 둔다.
 */
provide('nav', {
  isOpen: (id) => openGroups.value.has(id),
  toggle: toggleGroup,
  routeExists,
  titleOf: menuTitle,
  countOf: (routeName) => counts.value?.[routeName],
})

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

    <!-- 메뉴 구성은 서버(tb_menu)가 소유한다. 메뉴 관리 화면에서 바꾼다. -->
    <nav class="sidenav">
      <!--
        쓸 수 있는 그룹이 먼저. 그 아래에 전부 잠긴 그룹을 모아 둔다.
        감추지는 않는다 — 있는 줄 알아야 권한을 요청할 수 있다.
      -->
      <!--
        사이드바를 접으면(아이콘만) 머리글을 그릴 자리가 없다. 그럴 때는
        묶음을 버리고 잎만 한 줄로 늘어놓는다 — 접힌 그룹 아이콘은 눌러도
        갈 곳이 없어서, 좁은 폭에서는 방해만 된다.
      -->
      <template v-if="collapsed">
        <RouterLink
          v-for="m in flatMenus"
          :key="m.menuId"
          v-slot="{ isActive, navigate }"
          :to="{ name: m.routeName }"
          custom
        >
          <div
            class="nav-item"
            :class="{ active: isActive }"
            :title="menuTitle(m)"
            @click="navigate"
          >
            <span class="nav-icon">{{ m.icon }}</span>
          </div>
        </RouterLink>
      </template>

      <!-- 펼친 상태 — 깊이가 고정이 아니라 재귀로 그린다 -->
      <NavNode v-else v-for="g in sortedGroups" :key="g.menuId" :node="g" :depth="0" />

      <!-- 권한 없는 메뉴는 아예 내보내지 않는다 (sortedGroups 주석 참고) -->

      <div v-if="menuStore.loadError && !collapsed" class="nav-note" :title="menuStore.loadError">
        ⚠ {{ menuStore.fallback ? '기본 메뉴로 표시 중' : menuStore.loadError }}
      </div>
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
