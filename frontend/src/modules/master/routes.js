/**
 * master 모듈 — 기준정보.
 *
 * 거점 3단계(플랜트 · 창고 · 로케이션)가 먼저 온다. 재고주소가 이 위에서
 * 정해지기 때문이다 — 플랜트 · 창고 · 빈 · 상품(SKU) · 거래처.
 * 제품 · SKU · 채널 · 공급처 · 고객은 이어서 붙는다.
 *
 * 회사 · 조직은 system 모듈에 둔다. 사용자 소속과 데이터 범위의 기준이라
 * 인증 · 권한과 함께 읽는 편이 자연스럽다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드 (사이드바에서 권한 없는 메뉴는 흐리게 표시)
 */
export const routes = [
  {
    path: '/plants',
    name: 'plants',
    component: () => import('./views/PlantView.vue'),
    meta: { title: '플랜트 관리', perm: 'MST_PLANT' },
  },
  {
    path: '/warehouses',
    name: 'warehouses',
    component: () => import('./views/WarehouseView.vue'),
    meta: { title: '창고 관리', perm: 'MST_WAREHOUSE' },
  },
  {
    path: '/locations',
    name: 'locations',
    component: () => import('./views/LocationView.vue'),
    meta: { title: '로케이션 관리', perm: 'MST_LOCATION' },
  },
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
