/**
 * inbound 모듈 — 입고.
 *
 * 물건이 들어오는 순서가 구매 → 입고다. 그 뒷단이 여기다.
 *
 *   입고예정  언제 · 어디로 · 무엇이 몇 개 오는지 미리 적는다
 *   입하 등록 차가 오면 내린 개수를 적는다
 *   입고검수  세어 보고 받아들일 것과 못 받을 것을 가른다
 *   초과승인  시킨 것보다 많이 온 것을 사람이 한 번 본다
 *   적치      자리에 놓고, 다 놓으면 완료해 재고로 만든다
 *   입고정정  완료된 뒤에 수량이 틀렸던 것을 되감는다
 *
 * 나눈 이유는 <b>하는 사람과 때가 다르기</b> 때문이다. 예정은 사무실에서
 * 미리 세우고, 입하 · 검수 · 적치는 창고에서 물건을 앞에 두고 찍는다.
 * 권한도 그래서 다르다 (INB_PLAN · INB_ARRIVE · INB_INSPECT · INB_PUTAWAY ·
 * INB_APPROVE).
 *
 * 단계가 많아 보이지만 각각 <b>틀릴 수 있는 지점</b>이다. 내린 개수가 예정과
 * 다를 수 있고, 세어 보니 또 다를 수 있고, 그중 일부는 파손이라 못 받을 수
 * 있고, 받기로 한 것도 어디에 놓았는지 모르면 찾을 수 없다. 한 단계로
 * 합치면 어디서 틀어졌는지 영영 알 수 없다.
 *
 * <b>재고는 입고완료에서만 는다</b> (INB-008). 그리고 적치된 수량만.
 * 이 시스템에서 없던 재고가 생기는 유일한 경로다 — 조정도 실사도 이미 있는
 * 재고를 고치는 것이지 만들어 내는 것이 아니다.
 *
 * 입고완료 화면을 따로 두지 않았다. 적치가 끝난 그 자리에서 누르는 것이
 * 자연스럽고, 나누면 "적치는 했는데 완료를 안 눌렀다" 가 는다.
 *
 * 완료 뒤에 수량이 틀렸다면 입고정정으로 되감는다 (INB-PG-008). 재고조정과
 * 다른 점은 <b>발주 기입고까지 함께</b> 되감는다는 것이다 — 재고만 고치면
 * 발주는 다 들어온 것으로 닫혀 있어 공급처에 다시 요청할 잔량이 없다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드
 */
export const routes = [
  {
    path: '/inbounds',
    name: 'inbounds',
    component: () => import('./views/InboundPlanView.vue'),
    meta: { title: '입고예정', perm: 'INB_PLAN' },
  },
  {
    // 예정과 같은 데이터를 보지만 보는 이유가 다르다. 예정 화면은 계획을
    // 세우고, 이 화면은 아직 안 온 것만 급한 순으로 늘어놓는다.
    path: '/inbound-arrive',
    name: 'inbound-arrive',
    component: () => import('./views/InboundArriveView.vue'),
    meta: { title: '입하 등록', perm: 'INB_ARRIVE' },
  },
  {
    path: '/inbound-inspect',
    name: 'inbound-inspect',
    component: () => import('./views/InboundInspectView.vue'),
    meta: { title: '입고검수', perm: 'INB_INSPECT' },
  },
  {
    // 검수하는 사람과 승인하는 사람이 달라야 한다. 많이 받아 놓고 자기가
    // 승인하면 통제가 아니라 절차다.
    path: '/inbound-approve',
    name: 'inbound-approve',
    component: () => import('./views/InboundApproveView.vue'),
    meta: { title: '초과입고 승인', perm: 'INB_APPROVE' },
  },
  {
    // 적치와 입고완료가 한 화면이다. 다 놓은 자리에서 누르는 것이 자연스럽다.
    path: '/inbound-putaway',
    name: 'inbound-putaway',
    component: () => import('./views/InboundPutawayView.vue'),
    meta: { title: '적치 · 입고완료', perm: 'INB_PUTAWAY' },
  },
  {
    // 완료된 입고를 되감는다. 여기만 권한이 한 코드의 두 액션으로 갈린다 —
    // 요청은 C, 승인은 A. 그래서 화면도 둘이다.
    path: '/inbound-corrects',
    name: 'inbound-corrects',
    component: () => import('./views/InboundCorrectView.vue'),
    meta: { title: '입고정정 요청', perm: 'INB_CORRECTION' },
  },
  {
    path: '/inbound-correct-approve',
    name: 'inbound-correct-approve',
    component: () => import('./views/InboundCorrectApproveView.vue'),
    meta: { title: '입고정정 승인', perm: 'INB_CORRECTION' },
  },
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
