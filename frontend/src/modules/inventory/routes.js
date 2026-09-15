/**
 * inventory 모듈 — 재고.
 *
 * 조회가 먼저다. 재고가 지금 어떤 상태인지 볼 수 없으면 바꾸는 기능을
 * 만들어도 결과를 확인할 수 없다.
 *
 * 화면이 둘이고 권한은 하나(QRY_STOCK)다. '재고 현황은 보는데 이력은 못 보는
 * 사람' 이라는 역할이 업무에 없기 때문이다 — 수량을 보여 준 이상 그 수량이
 * 왜 그런지도 답할 수 있어야 한다.
 *
 * 등록 · 수정 · 삭제 화면이 없다. 재고는 입고 · 출고 · 조정 · 실사의 결과로만
 * 바뀌고(P-02), 각 경로가 자기 화면과 자기 결재선을 갖는다.
 *
 * master 모듈과 나란히 두고 그 아래에 넣지 않는다. 기준정보는 사람이 정해
 * 넣는 값이고 재고는 업무의 결과로 쌓이는 값이라, 화면 성격도 권한 주인도
 * 다르다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드 (사이드바에서 권한 없는 메뉴는 흐리게 표시)
 */
export const routes = [
  {
    path: '/stocks',
    name: 'stocks',
    component: () => import('./views/StockView.vue'),
    meta: { title: '재고 현황', perm: 'QRY_STOCK' },
  },
  {
    // 이동이력(INV-PG-003)과 할당이력(INV-PG-004)을 탭으로 함께 둔다.
    // 현장에서 이 둘을 번갈아 보기 때문이다 — 화면을 나누면 재고 한 줄을
    // 두고 두 화면을 오가게 된다.
    path: '/stock-history',
    name: 'stock-history',
    component: () => import('./views/StockHistoryView.vue'),
    meta: { title: '재고 이력', perm: 'QRY_STOCK' },
  },
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
