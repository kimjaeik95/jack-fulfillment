/**
 * purchase 모듈 — 구매.
 *
 * 물건이 들어오는 순서가 구매 → 입고다. 그 앞단이 여기다.
 *
 *   구매요청  센터가 "이게 모자라니 사 주세요" 를 올린다
 *   결재      본사 구매 담당이 승인 · 부분승인 · 반려한다
 *   구매오더  승인된 것을 공급처에 발주한다 — 여기부터 돈이 나간다
 *   진행현황  낸 발주가 얼마나 들어왔는지 본다
 *
 * 요청 화면이 둘이고 권한도 둘이다 (PUR_REQUEST · PUR_REQ_APPROVE). 나누는
 * 이유는 한 사람이 둘 다 하면 승인이 통제가 아니라 절차가 되기 때문이다
 * (AUTH-008). 둘 다 가진 사람이 있어도 서버가 '자기 요청 자기 승인' 을
 * 막는다 — 권한으로 막으면 그건 직무분리가 아니라 그냥 권한 부족이다.
 *
 * 발주는 나누지 않는다. 내는 일과 거두는 일의 무게가 달라 권한만 둘로
 * 두고(PUR_PO_ISSUE · PUR_PO_CANCEL) 화면은 하나다 — 작성과 발주를 두
 * 화면으로 나누면 "내가 만든 게 어디 갔지" 가 된다.
 *
 * 입고예정 생성(PUR-PG-006)은 여기 없다. 입고 테이블이 있어야 만들어져
 * INB-PG-001 과 한 몸이다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드 (사이드바에서 권한 없는 메뉴는 흐리게 표시)
 */
export const routes = [
  {
    path: '/purchase-requests',
    name: 'purchase-requests',
    component: () => import('./views/PurchaseRequestView.vue'),
    meta: { title: '구매요청', perm: 'PUR_REQUEST' },
  },
  {
    // 요청과 같은 데이터를 보지만 보는 이유가 다르다. 요청자는 자기가
    // 올린 것을, 결재자는 결재할 것을 본다 — 그래서 화면도 다르다.
    path: '/purchase-request-approve',
    name: 'purchase-request-approve',
    component: () => import('./views/PurchaseRequestApproveView.vue'),
    meta: { title: '구매요청 결재', perm: 'PUR_REQ_APPROVE' },
  },
  {
    // 작성(DRAFT)과 발주(ISSUED)가 한 화면이다. 상태로 구분한다.
    path: '/purchase-orders',
    name: 'purchase-orders',
    component: () => import('./views/PurchaseOrderView.vue'),
    meta: { title: '구매오더', perm: 'PUR_PO_ISSUE' },
  },
  {
    // 오더 목록과 같은 데이터를 본다. 다른 것은 무엇부터 보느냐다 —
    // 목록은 최근 순, 진행현황은 급한 납기부터 덜 들어온 것만.
    path: '/purchase-progress',
    name: 'purchase-progress',
    component: () => import('./views/PurchaseOrderProgressView.vue'),
    meta: { title: '발주 진행현황', perm: 'PUR_PO_ISSUE' },
  },
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
