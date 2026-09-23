/**
 * outbound 모듈 — 출고 · 패킹 (4차).
 *
 * A섹터는 여기까지다 — 출고대상 조회와 출고지시 생성. 피킹(B섹터) ·
 * 검수 · 패킹 · 송장(C · D섹터) · 출고확정(E섹터)이 뒤에 붙는다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드
 */
export const routes = [
  {
    path: '/outbound-targets',
    name: 'outbound-targets',
    component: () => import('./views/OutboundTargetView.vue'),
    meta: { title: '출고대상', perm: 'OUT_TARGET' },
  },
  {
    path: '/outbounds',
    name: 'outbounds',
    component: () => import('./views/OutboundView.vue'),
    meta: { title: '출고지시', perm: 'OUT_ORDER' },
  },
]
