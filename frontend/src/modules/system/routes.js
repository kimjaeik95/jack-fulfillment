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
    path: '/menus',
    name: 'menus',
    component: () => import('./views/MenuView.vue'),
    meta: { title: '메뉴 관리', perm: 'SYS_MENU' },
  },
  {
    path: '/uploads',
    name: 'uploads',
    component: () => import('./views/UploadView.vue'),
    // 권한을 걸지 않는다. 자기가 올린 결과는 누구나 확인할 수 있어야 한다.
    // 남의 업로드까지 보이는지는 서버가 판정한다.
    meta: { title: '업로드 이력' },
  },
  {
    path: '/audit-logs',
    name: 'audit-logs',
    component: () => import('./views/AuditLogView.vue'),
    meta: { title: '변경 이력', perm: 'AUD_HISTORY' },
  },
]

/**
 * 사이드바 메뉴는 더 이상 여기서 정의하지 않는다 (COM-PG-005).
 *
 * 메뉴 순서 하나를 바꾸는 데도 배포가 필요했기 때문에 tb_menu 로 옮겼고,
 * 지금은 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 소유한다 —
 * 메뉴는 "그 라우트를 사이드바 어디에 어떤 이름으로 걸지"만 정한다.
 *
 * 새 화면을 추가하려면 위 routes 에 넣은 뒤, 메뉴 관리에서 그 라우트 이름으로
 * 메뉴를 연결한다. 연결하지 않으면 주소로는 들어갈 수 있지만 사이드바에는
 * 나타나지 않는다.
 */
export const menuGroups = []
