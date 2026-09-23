/**
 * outbound 모듈 — 출고 · 패킹 (4차).
 *
 * A섹터(출고대상 · 출고지시) · B섹터(피킹) · C섹터(검수 · 박스 · 패킹) ·
 * D섹터(송장)까지다. 출고확정 · 택배인계 · 수량체인 검증(E섹터)이 뒤에 붙는다.
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
    path: '/outbound-inspect',
    name: 'outbound-inspect',
    component: () => import('./views/OutboundInspectView.vue'),
    meta: { title: '출고검수', perm: 'OUT_PICK' },
  },
  {
    path: '/outbound-packing',
    name: 'outbound-packing',
    component: () => import('./views/OutboundPackingView.vue'),
    meta: { title: '패킹', perm: 'OUT_PACK' },
  },
  {
    path: '/outbound-waybills',
    name: 'outbound-waybills',
    component: () => import('./views/OutboundWaybillView.vue'),
    meta: { title: '송장', perm: 'OUT_WAYBILL' },
  },
  {
    path: '/outbounds',
    name: 'outbounds',
    component: () => import('./views/OutboundView.vue'),
    meta: { title: '출고지시', perm: 'OUT_ORDER' },
  },
]
