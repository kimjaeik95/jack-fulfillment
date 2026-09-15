/**
 * inventory 모듈 — 재고.
 *
 * 화면을 네 갈래로 나눈다. 나누는 기준은 '총량이 바뀌나' 다.
 *
 *   조회      현황 · 이력          아무것도 바꾸지 않는다
 *   상태/위치 판매불가 전환 · 이동  총량 그대로. 승인 없이 즉시 반영.
 *   조정      요청 · 승인          총량이 바뀐다. 요청자와 승인자를 나눈다.
 *   실사      계획 · 입력 · 마감   세어서 맞춘다. 마감이 승인이다.
 *
 * 승인이 붙는 기준이 그것이다. 총량이 그대로인 일에 승인을 붙이면 현장이
 * 물건을 옮겨 놓고 시스템은 옮기지 못한 상태로 몇 시간을 보내게 되고,
 * 그 사이 나가는 피킹 지시가 전부 틀린 자리를 가리킨다.
 *
 * 재고 수량을 직접 고치는 화면은 없다. 어느 화면에서 무엇을 하든 서버의
 * StockLedger 한 곳을 거치고, 거기서 수량 변경과 이력이 한 트랜잭션으로
 * 묶인다 (P-02).
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

  /* ── B섹터 — 총량은 그대로, 상태나 위치만 바뀐다 ───────────── */

  {
    // 판매불가 전환과 로케이션 이동이 같은 권한(INV_MOVE)을 쓴다.
    // 둘 다 '총량을 바꾸지 않는 현장 조작' 이라 같은 사람이 한다 —
    // 권한을 나누면 "옮길 수는 있는데 불량으로 돌리지는 못하는 사람"
    // 이라는, 업무에 없는 역할이 생긴다.
    path: '/stock-unsellable',
    name: 'stock-unsellable',
    component: () => import('./views/StockUnsellableView.vue'),
    meta: { title: '판매불가 전환', perm: 'INV_MOVE' },
  },
  {
    path: '/stock-move',
    name: 'stock-move',
    component: () => import('./views/StockMoveView.vue'),
    meta: { title: '로케이션 이동', perm: 'INV_MOVE' },
  },

  /* ── C섹터 — 총량이 바뀐다. 요청과 승인을 나눈다. ──────────── */

  {
    path: '/stock-adjust',
    name: 'stock-adjust',
    component: () => import('./views/StockAdjustView.vue'),
    meta: { title: '재고조정 요청', perm: 'INV_ADJUST' },
  },
  {
    // 요청과 다른 권한을 쓴다. 같은 데이터를 보지만 보는 이유가 다르고,
    // 무엇보다 한 사람이 둘 다 하면 승인이 통제가 아니라 절차가 된다.
    path: '/stock-adjust-approve',
    name: 'stock-adjust-approve',
    component: () => import('./views/StockAdjustApproveView.vue'),
    meta: { title: '재고조정 승인', perm: 'INV_ADJ_APPROVE' },
  },

  /* ── D섹터 — 세어서 맞춘다 ────────────────────────────────── */

  {
    // 계획 · 입력 · 마감을 한 화면에 둔다. 따로 만들면 같은 실사를 두고
    // 세 화면을 오가게 되고, 지금 어느 단계인지가 화면마다 따로 관리된다.
    // 마감만 다른 권한(INV_COUNT_APPROVE)을 요구하며, 화면 진입은
    // 계획·입력 권한으로 한다.
    path: '/stocktake',
    name: 'stocktake',
    component: () => import('./views/StocktakeView.vue'),
    meta: { title: '재고실사', perm: 'INV_COUNT' },
  },
  {
    // 권한을 새로 만들지 않는다. 읽어서 비교만 하므로 QRY_STOCK 으로
    // 충분하다 — 고치는 것은 조정과 실사의 일이다.
    path: '/stock-recon',
    name: 'stock-recon',
    component: () => import('./views/StockReconView.vue'),
    meta: { title: '재고 대사', perm: 'QRY_STOCK' },
  },
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
