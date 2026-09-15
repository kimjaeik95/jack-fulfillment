/**
 * purchase 모듈 — 구매.
 *
 * 물건이 들어오는 순서가 구매 → 입고다. 그 앞단이 여기다.
 *
 *   구매요청  센터가 "이게 모자라니 사 주세요" 를 올린다
 *   결재      본사 구매 담당이 승인 · 부분승인 · 반려한다
 *
 * 화면이 둘이고 권한도 둘이다 (PUR_REQUEST · PUR_REQ_APPROVE). 나누는
 * 이유는 한 사람이 둘 다 하면 승인이 통제가 아니라 절차가 되기 때문이다
 * (AUTH-008). 둘 다 가진 사람이 있어도 서버가 '자기 요청 자기 승인' 을
 * 막는다 — 권한으로 막으면 그건 직무분리가 아니라 그냥 권한 부족이다.
 *
 * 구매오더(PUR-PG-003~006)는 아직 없다. 요청이 승인되면 그때 발주로
 * 넘어가고, 발주부터가 공급처와의 약속이다.
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
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
