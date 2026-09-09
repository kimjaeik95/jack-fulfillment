/**
 * system 모듈 — 사용자·조직·역할·권한·공통정책·공통코드·감사.
 *
 * 다른 모든 모듈(oms, wms, master 등)이 의존하는 공통 기반이다.
 * 사용자·역할·로그인은 여러 업무 영역이 공유하므로 특정 업무 모듈에 속하지 않는다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드 (사이드바에서 권한 없는 메뉴는 흐리게 표시)
 */
export const routes = [
  {
    path: '/',
    name: 'dashboard',
    component: () => import('./views/DashboardView.vue'),
    meta: { title: '권한 현황', perm: 'SYS_ROLE' },
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('./views/UserView.vue'),
    meta: { title: '사용자 관리', perm: 'SYS_USER' },
  },
  {
    path: '/orgs',
    name: 'orgs',
    component: () => import('./views/OrgView.vue'),
    meta: { title: '조직 관리', perm: 'SYS_COMPANY' },
  },
  {
    path: '/roles',
    name: 'roles',
    component: () => import('./views/RoleView.vue'),
    meta: { title: '역할 관리', perm: 'SYS_ROLE' },
  },
  {
    path: '/permissions',
    name: 'permissions',
    component: () => import('./views/PermissionView.vue'),
    meta: { title: '권한 관리', perm: 'SYS_ROLE' },
  },
  {
    path: '/role-permissions',
    name: 'role-permissions',
    component: () => import('./views/RolePermissionView.vue'),
    meta: { title: '역할-권한 매핑', perm: 'SYS_ROLE' },
  },
  {
    path: '/policies',
    name: 'policies',
    component: () => import('./views/PolicyView.vue'),
    meta: { title: '공통정책 관리', perm: 'SYS_POLICY' },
  },
  {
    path: '/codes',
    name: 'codes',
    component: () => import('./views/CodeView.vue'),
    meta: { title: '공통코드', perm: 'SYS_CODE' },
  },
  {
    path: '/audit-logs',
    name: 'audit-logs',
    component: () => import('./views/AuditLogView.vue'),
    meta: { title: '변경 이력', perm: 'AUD_HISTORY' },
  },
]

/** 사이드바 메뉴 그룹 (이 모듈이 소유) */
export const menuGroups = [
  {
    label: '현황',
    items: [{ name: 'dashboard', icon: '◎', label: '권한 현황' }],
  },
  {
    label: '사용자 · 조직',
    items: [
      { name: 'users', icon: '👤', label: '사용자 관리' },
      { name: 'orgs', icon: '🏢', label: '조직 관리' },
    ],
  },
  {
    label: '권한',
    items: [
      { name: 'roles', icon: '🎫', label: '역할 관리' },
      { name: 'permissions', icon: '🔑', label: '권한 관리' },
      { name: 'role-permissions', icon: '▦', label: '역할-권한 매핑' },
    ],
  },
  {
    label: '공통 정책',
    items: [
      { name: 'policies', icon: '⚖', label: '공통정책 관리' },
      { name: 'codes', icon: '☰', label: '공통코드' },
    ],
  },
  {
    label: '감사',
    items: [{ name: 'audit-logs', icon: '📜', label: '변경 이력' }],
  },
]
