/**
 * inbound 모듈 — 입고.
 *
 * 물건이 들어오는 순서가 구매 → 입고다. 그 뒷단이 여기다.
 *
 *   입고예정  언제 · 어디로 · 무엇이 몇 개 오는지 미리 적는다
 *   입하 등록 차가 오면 내린 개수를 적는다
 *
 * 둘을 나눈 이유는 <b>하는 사람과 때가 다르기</b> 때문이다. 예정은 사무실에서
 * 미리 세우고, 입하는 창고에서 차를 앞에 두고 찍는다. 권한도 그래서 다르다
 * (INB_PLAN vs INB_ARRIVE).
 *
 * 여기까지는 <b>재고가 움직이지 않는다</b>. 물건이 마당에 있는 것과 팔 수
 * 있는 재고가 된 것은 다르다 — 검수(INB-PG-003)와 적치(INB-PG-006)가 끝나야
 * 재고가 된다. 그 화면들은 아직 없다.
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
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
