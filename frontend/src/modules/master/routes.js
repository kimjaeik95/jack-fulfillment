/**
 * master 모듈 — 기준정보.
 *
 * 거점 3단계(플랜트 · 창고 · 빈)가 먼저 온다. 재고주소가 이 위에서
 * 정해지기 때문이다 — 플랜트 · 창고 · 빈 · 상품(SKU) · 거래처.
 * 이어서 분류 · 브랜드 · 제품 · SKU 가 붙는다. SKU 는 모든 트랜잭션의
 * FK 기준이라 재고보다 먼저 서야 한다.
 *
 * 판매채널과 채널 SKU 매핑은 SKU 뒤에 온다. 매핑이 SKU 를 가리키므로
 * SKU 가 먼저 있어야 한다.
 *
 * 거래처(공급처 · 고객)와 사유코드가 마지막이다. 앞의 것들과 달리 서로
 * 참조하지 않고, 구매(5차) · 판매오더(11차) · 각 예외 처리가 쓴다.
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
    meta: { title: '빈 관리', perm: 'MST_LOCATION' },
  },
  {
    // 새 권한을 두지 않는다. MST_LOCATION 의 X(출력·다운로드)를 쓴다 —
    // 라벨은 빈 정보를 종이에 옮기는 일이라 데이터를 바꾸지 않는다.
    path: '/label-print',
    name: 'label-print',
    component: () => import('./views/LabelPrintView.vue'),
    meta: { title: '빈 바코드 출력', perm: 'MST_LOCATION' },
  },
  {
    path: '/categories',
    name: 'categories',
    component: () => import('./views/CategoryView.vue'),
    meta: { title: '카테고리 관리', perm: 'MST_CATEGORY' },
  },
  {
    path: '/brands',
    name: 'brands',
    component: () => import('./views/BrandView.vue'),
    meta: { title: '브랜드 관리', perm: 'MST_BRAND' },
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('./views/ProductView.vue'),
    meta: { title: '제품 관리', perm: 'MST_PRODUCT' },
  },
  {
    path: '/skus',
    name: 'skus',
    component: () => import('./views/SkuView.vue'),
    meta: { title: 'SKU 관리', perm: 'MST_SKU' },
  },
  {
    // 별도 권한을 두지 않는다. 하는 일이 SKU 를 만드는 것이라 MST_SKU 의 C 와
    // 같다 — 권한을 나누면 "SKU 는 만들 수 있지만 한 번에 만들지는 못하는
    // 사람" 이라는, 업무에 없는 역할이 생긴다.
    path: '/sku-bulk',
    name: 'sku-bulk',
    component: () => import('./views/SkuBulkView.vue'),
    meta: { title: 'SKU 일괄생성', perm: 'MST_SKU' },
  },
  {
    path: '/channels',
    name: 'channels',
    component: () => import('./views/ChannelView.vue'),
    meta: { title: '판매채널 관리', perm: 'MST_CHANNEL' },
  },
  {
    path: '/channel-skus',
    name: 'channel-skus',
    component: () => import('./views/ChannelSkuView.vue'),
    meta: { title: '채널 SKU 매핑', perm: 'MST_CHANNEL_SKU' },
  },
  {
    // 공급처와 고객을 합쳤다(V20). 한 회사가 양쪽인 경우가 있어서다 —
    // 임가공은 우리에게 옷을 대 주면서 우리 원단을 사 간다.
    path: '/partners',
    name: 'partners',
    component: () => import('./views/PartnerView.vue'),
    meta: { title: '거래처 관리', perm: 'MST_PARTNER' },
  },
  {
    // 저장은 공통코드와 같은 테이블이지만 화면과 권한은 다르다.
    // 공통코드에는 건드리면 권한 판정이 깨지는 값들이 있어 다루는 사람이 다르다.
    path: '/reasons',
    name: 'reasons',
    component: () => import('./views/ReasonView.vue'),
    meta: { title: '사유코드 관리', perm: 'MST_REASON' },
  },
]

/**
 * 사이드바 메뉴는 여기서 정의하지 않는다 (COM-PG-005).
 * tb_menu 가 소유하며 메뉴 관리 화면에서 바꾼다. 라우트는 계속 코드가 갖는다.
 */
export const menuGroups = []
