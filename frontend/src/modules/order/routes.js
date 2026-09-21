/**
 * order 모듈 — 주문 · 할당 (3차).
 *
 * 채널에서 들어온 주문을 다룬다. 판매오더(B2B)는 11차(판매관리)다.
 *
 * 지금은 주문(등록 · 확정)과 오류대기(재처리)까지다. 할당(ORD-PG-005) ·
 * 결품 · 취소는 이어지는 섹터에서 붙는다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드
 */
export const routes = [
  {
    path: '/orders',
    name: 'orders',
    component: () => import('./views/OrderView.vue'),
    meta: { title: '주문 관리', perm: 'ORD_ORDER' },
  },
  {
    path: '/orders/errors',
    name: 'order-errors',
    component: () => import('./views/OrderErrorView.vue'),
    meta: { title: '주문 오류대기', perm: 'ORD_ORDER' },
  },
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
