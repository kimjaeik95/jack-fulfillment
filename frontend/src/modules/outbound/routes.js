/**
 * outbound 모듈 — 출고 · 패킹 (4차).
 *
 * A섹터(출고대상 · 출고지시)와 B섹터(피킹 배정 · 스캔 · 결품)까지다.
 * 검수 · 패킹 · 송장(C · D섹터)과 출고확정(E섹터)이 뒤에 붙는다.
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
    path: '/outbound-picking',
    name: 'outbound-picking',
    component: () => import('./views/OutboundPickingView.vue'),
    meta: { title: '피킹', perm: 'OUT_PICK' },
  },
  {
    path: '/outbound-shortages',
    name: 'outbound-shortages',
    component: () => import('./views/OutboundShortageView.vue'),
    meta: { title: '피킹 결품', perm: 'OUT_SHORTAGE' },
  },
  {
    path: '/outbounds',
    name: 'outbounds',
    component: () => import('./views/OutboundView.vue'),
    meta: { title: '출고지시', perm: 'OUT_ORDER' },
  },
]
