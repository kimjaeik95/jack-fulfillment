/**
 * 초기 시드 데이터.
 * 요구사항으로 주어진 "2. 사용자·권한·공통 정책" 표를 아래 4개 엔터티로 정규화한 것.
 *
 *   표의 [역할]      -> roles
 *   표의 [주요 권한]  -> permissions + rolePermissions (M:N 매핑)
 *   표의 [제한/승인]  -> policies
 *   운영에 필요한 부가 -> orgs, users
 */

/* ------------------------------------------------------------------ */
/* 조직                                                                */
/* ------------------------------------------------------------------ */
export const ORGS = [
  { orgId: 'HQ001', orgName: '본사', orgType: 'HQ', parentId: null, useYn: 'Y' },
  { orgId: 'DC001', orgName: '이천물류센터', orgType: 'DC', parentId: 'HQ001', useYn: 'Y' },
  { orgId: 'DC002', orgName: '김해물류센터', orgType: 'DC', parentId: 'HQ001', useYn: 'Y' },
  { orgId: 'ST001', orgName: '강남점', orgType: 'STORE', parentId: 'HQ001', useYn: 'Y' },
  { orgId: 'ST002', orgName: '판교점', orgType: 'STORE', parentId: 'HQ001', useYn: 'Y' },
  { orgId: 'ST003', orgName: '해운대점', orgType: 'STORE', parentId: 'HQ001', useYn: 'Y' },
]

/* ------------------------------------------------------------------ */
/* 역할 (표 1열)                                                        */
/* ------------------------------------------------------------------ */
export const ROLES = [
  {
    roleId: 'SYS_ADMIN',
    roleName: '시스템 관리자',
    orgScope: 'HQ',
    summary: '회사·사용자·역할·공통코드·인터페이스 설정',
    restriction: '업무 수량 직접 수정 금지',
    dataScope: 'ALL',
    sortOrder: 10,
    useYn: 'Y',
  },
  {
    roleId: 'HQ_MASTER',
    roleName: '본사 기준정보 담당',
    orgScope: 'HQ',
    summary: '제품/SKU/브랜드/채널/가격/공급처 관리',
    restriction: 'SKU 폐기는 재고 0 및 미처리 건 0',
    dataScope: 'ALL',
    sortOrder: 20,
    useYn: 'Y',
  },
  {
    roleId: 'PURCHASER',
    roleName: '구매 담당',
    orgScope: 'HQ',
    summary: '구매요청 승인, PO 발주·취소',
    restriction: '발주취소 사유 필수',
    dataScope: 'ALL',
    sortOrder: 30,
    useYn: 'Y',
  },
  {
    roleId: 'CENTER_MGR',
    roleName: '센터 관리자',
    orgScope: 'DC',
    summary: '입고·출고·실사 승인, 재고조정 승인',
    restriction: '자기 요청 자기 승인 금지 권고',
    dataScope: 'OWN_ORG',
    sortOrder: 40,
    useYn: 'Y',
  },
  {
    roleId: 'INBOUND_WORKER',
    roleName: '입고 작업자',
    orgScope: 'DC',
    summary: '입하·검수·적치 스캔',
    restriction: '확정 후 수정 불가, 정정요청',
    dataScope: 'OWN_ORG',
    sortOrder: 50,
    useYn: 'Y',
  },
  {
    roleId: 'PICK_PACK',
    roleName: '피킹/패킹 작업자',
    orgScope: 'DC',
    summary: '작업 할당·실적·결품 등록',
    restriction: '지시 외 SKU/수량 차단',
    dataScope: 'OWN_ORG',
    sortOrder: 60,
    useYn: 'Y',
  },
  {
    roleId: 'STORE_MGR',
    roleName: '매장 관리자',
    orgScope: 'STORE',
    summary: '보충요청·이동 승인·매장실사',
    restriction: '타 매장 재고 직접 수정 금지',
    dataScope: 'OWN_ORG',
    sortOrder: 70,
    useYn: 'Y',
  },
  {
    roleId: 'STORE_STAFF',
    roleName: '매장 직원',
    orgScope: 'STORE',
    summary: '매장입고·이동출고·택배요청',
    restriction: '승인 한도 적용',
    dataScope: 'OWN_ORG',
    sortOrder: 80,
    useYn: 'Y',
  },
  {
    roleId: 'CS_VIEWER',
    roleName: 'CS/조회 사용자',
    orgScope: 'HQ',
    summary: '주문·배송·재고 조회',
    restriction: '수정 권한 없음',
    dataScope: 'ALL',
    sortOrder: 90,
    useYn: 'Y',
  },
  {
    roleId: 'AUDITOR',
    roleName: '감사/분석 사용자',
    orgScope: 'HQ',
    summary: '이력·로그·KPI 조회/다운로드',
    restriction: '개인정보 마스킹',
    dataScope: 'ALL',
    sortOrder: 100,
    useYn: 'Y',
  },
]

/* ------------------------------------------------------------------ */
/* 권한 (표 2열을 기능 단위로 분해)                                       */
/* ------------------------------------------------------------------ */
export const PERMISSIONS = [
  // 시스템 설정
  { permId: 'SYS_COMPANY', permName: '회사 관리', module: 'SYS', actions: ['R', 'C', 'U', 'D'], menuPath: '시스템 > 회사관리', useYn: 'Y' },
  { permId: 'SYS_USER', permName: '사용자 관리', module: 'SYS', actions: ['R', 'C', 'U', 'D'], menuPath: '시스템 > 사용자관리', useYn: 'Y' },
  { permId: 'SYS_ROLE', permName: '역할/권한 관리', module: 'SYS', actions: ['R', 'C', 'U', 'D'], menuPath: '시스템 > 역할관리', useYn: 'Y' },
  { permId: 'SYS_CODE', permName: '공통코드 관리', module: 'SYS', actions: ['R', 'C', 'U', 'D'], menuPath: '시스템 > 공통코드', useYn: 'Y' },
  { permId: 'SYS_IF', permName: '인터페이스 설정', module: 'SYS', actions: ['R', 'C', 'U'], menuPath: '시스템 > 인터페이스', useYn: 'Y' },
  { permId: 'SYS_POLICY', permName: '공통정책 관리', module: 'SYS', actions: ['R', 'C', 'U', 'D'], menuPath: '시스템 > 공통정책', useYn: 'Y' },

  // 기준정보
  { permId: 'MST_SKU', permName: '제품/SKU 관리', module: 'MST', actions: ['R', 'C', 'U', 'D'], menuPath: '기준정보 > SKU', useYn: 'Y' },
  { permId: 'MST_BRAND', permName: '브랜드 관리', module: 'MST', actions: ['R', 'C', 'U', 'D'], menuPath: '기준정보 > 브랜드', useYn: 'Y' },
  { permId: 'MST_CHANNEL', permName: '채널 관리', module: 'MST', actions: ['R', 'C', 'U', 'D'], menuPath: '기준정보 > 채널', useYn: 'Y' },
  { permId: 'MST_PRICE', permName: '가격 관리', module: 'MST', actions: ['R', 'C', 'U'], menuPath: '기준정보 > 가격', useYn: 'Y' },
  { permId: 'MST_VENDOR', permName: '공급처 관리', module: 'MST', actions: ['R', 'C', 'U', 'D'], menuPath: '기준정보 > 공급처', useYn: 'Y' },

  // 구매
  { permId: 'PUR_REQ_APPROVE', permName: '구매요청 승인', module: 'PUR', actions: ['R', 'A'], menuPath: '구매 > 구매요청', useYn: 'Y' },
  { permId: 'PUR_PO_ISSUE', permName: 'PO 발주', module: 'PUR', actions: ['R', 'C', 'U'], menuPath: '구매 > 발주', useYn: 'Y' },
  { permId: 'PUR_PO_CANCEL', permName: 'PO 발주취소', module: 'PUR', actions: ['R', 'U'], menuPath: '구매 > 발주취소', useYn: 'Y' },

  // 입고
  { permId: 'INB_ARRIVE', permName: '입하 스캔', module: 'INB', actions: ['R', 'C'], menuPath: '입고 > 입하', useYn: 'Y' },
  { permId: 'INB_INSPECT', permName: '검수 스캔', module: 'INB', actions: ['R', 'C'], menuPath: '입고 > 검수', useYn: 'Y' },
  { permId: 'INB_PUTAWAY', permName: '적치 스캔', module: 'INB', actions: ['R', 'C'], menuPath: '입고 > 적치', useYn: 'Y' },
  { permId: 'INB_APPROVE', permName: '입고 확정/승인', module: 'INB', actions: ['R', 'A'], menuPath: '입고 > 입고확정', useYn: 'Y' },
  { permId: 'INB_CORRECTION', permName: '입고 정정요청', module: 'INB', actions: ['R', 'C'], menuPath: '입고 > 정정요청', useYn: 'Y' },

  // 출고
  { permId: 'OUT_APPROVE', permName: '출고 승인', module: 'OUT', actions: ['R', 'A'], menuPath: '출고 > 출고승인', useYn: 'Y' },
  { permId: 'OUT_ASSIGN', permName: '작업 할당', module: 'OUT', actions: ['R', 'C', 'U'], menuPath: '출고 > 작업할당', useYn: 'Y' },
  { permId: 'OUT_PICK', permName: '피킹 실적 등록', module: 'OUT', actions: ['R', 'C'], menuPath: '출고 > 피킹', useYn: 'Y' },
  { permId: 'OUT_PACK', permName: '패킹 실적 등록', module: 'OUT', actions: ['R', 'C'], menuPath: '출고 > 패킹', useYn: 'Y' },
  { permId: 'OUT_SHORTAGE', permName: '결품 등록', module: 'OUT', actions: ['R', 'C'], menuPath: '출고 > 결품', useYn: 'Y' },

  // 재고/실사
  { permId: 'INV_COUNT_APPROVE', permName: '실사 승인', module: 'INV', actions: ['R', 'A'], menuPath: '재고 > 실사승인', useYn: 'Y' },
  { permId: 'INV_ADJ_APPROVE', permName: '재고조정 승인', module: 'INV', actions: ['R', 'A'], menuPath: '재고 > 조정승인', useYn: 'Y' },
  { permId: 'INV_QTY_EDIT', permName: '업무 수량 직접 수정', module: 'INV', actions: ['U'], menuPath: '재고 > 수량보정', useYn: 'Y' },
  { permId: 'INV_STORE_COUNT', permName: '매장 실사', module: 'INV', actions: ['R', 'C', 'U'], menuPath: '재고 > 매장실사', useYn: 'Y' },

  // 매장
  { permId: 'STR_REPLENISH', permName: '보충 요청', module: 'STR', actions: ['R', 'C', 'U'], menuPath: '매장 > 보충요청', useYn: 'Y' },
  { permId: 'STR_MOVE_APPROVE', permName: '매장이동 승인', module: 'STR', actions: ['R', 'A'], menuPath: '매장 > 이동승인', useYn: 'Y' },
  { permId: 'STR_RECEIVE', permName: '매장 입고', module: 'STR', actions: ['R', 'C'], menuPath: '매장 > 매장입고', useYn: 'Y' },
  { permId: 'STR_MOVE_OUT', permName: '이동 출고', module: 'STR', actions: ['R', 'C'], menuPath: '매장 > 이동출고', useYn: 'Y' },
  { permId: 'STR_PARCEL', permName: '택배 요청', module: 'STR', actions: ['R', 'C'], menuPath: '매장 > 택배요청', useYn: 'Y' },

  // 조회
  { permId: 'QRY_ORDER', permName: '주문 조회', module: 'QRY', actions: ['R'], menuPath: '조회 > 주문', useYn: 'Y' },
  { permId: 'QRY_DELIVERY', permName: '배송 조회', module: 'QRY', actions: ['R'], menuPath: '조회 > 배송', useYn: 'Y' },
  { permId: 'QRY_STOCK', permName: '재고 조회', module: 'QRY', actions: ['R'], menuPath: '조회 > 재고', useYn: 'Y' },

  // 감사/분석
  { permId: 'AUD_HISTORY', permName: '변경 이력 조회', module: 'AUD', actions: ['R'], menuPath: '감사 > 이력', useYn: 'Y' },
  { permId: 'AUD_LOG', permName: '시스템 로그 조회', module: 'AUD', actions: ['R'], menuPath: '감사 > 로그', useYn: 'Y' },
  { permId: 'AUD_KPI', permName: 'KPI 조회', module: 'AUD', actions: ['R'], menuPath: '감사 > KPI', useYn: 'Y' },
  { permId: 'AUD_DOWNLOAD', permName: '데이터 다운로드', module: 'AUD', actions: ['R', 'X'], menuPath: '감사 > 다운로드', useYn: 'Y' },
]

/* ------------------------------------------------------------------ */
/* 역할-권한 매핑                                                        */
/* grant: 해당 역할이 그 권한에서 실제로 허용받은 액션 목록                   */
/* ------------------------------------------------------------------ */
export const ROLE_PERMISSIONS = [
  // 시스템 관리자 — 설정 전권, 단 업무 수량 직접 수정은 미부여(정책 P001과 짝)
  ...['SYS_COMPANY', 'SYS_USER', 'SYS_ROLE', 'SYS_CODE', 'SYS_IF', 'SYS_POLICY'].map((p) => ({
    roleId: 'SYS_ADMIN', permId: p, actions: ['R', 'C', 'U', 'D'],
  })),
  { roleId: 'SYS_ADMIN', permId: 'AUD_LOG', actions: ['R'] },
  { roleId: 'SYS_ADMIN', permId: 'AUD_HISTORY', actions: ['R'] },

  // 본사 기준정보 담당
  ...['MST_SKU', 'MST_BRAND', 'MST_CHANNEL', 'MST_VENDOR'].map((p) => ({
    roleId: 'HQ_MASTER', permId: p, actions: ['R', 'C', 'U', 'D'],
  })),
  { roleId: 'HQ_MASTER', permId: 'MST_PRICE', actions: ['R', 'C', 'U'] },
  { roleId: 'HQ_MASTER', permId: 'QRY_STOCK', actions: ['R'] },

  // 구매 담당
  { roleId: 'PURCHASER', permId: 'PUR_REQ_APPROVE', actions: ['R', 'A'] },
  { roleId: 'PURCHASER', permId: 'PUR_PO_ISSUE', actions: ['R', 'C', 'U'] },
  { roleId: 'PURCHASER', permId: 'PUR_PO_CANCEL', actions: ['R', 'U'] },
  { roleId: 'PURCHASER', permId: 'MST_VENDOR', actions: ['R'] },
  { roleId: 'PURCHASER', permId: 'QRY_STOCK', actions: ['R'] },

  // 센터 관리자
  { roleId: 'CENTER_MGR', permId: 'INB_APPROVE', actions: ['R', 'A'] },
  { roleId: 'CENTER_MGR', permId: 'OUT_APPROVE', actions: ['R', 'A'] },
  { roleId: 'CENTER_MGR', permId: 'INV_COUNT_APPROVE', actions: ['R', 'A'] },
  { roleId: 'CENTER_MGR', permId: 'INV_ADJ_APPROVE', actions: ['R', 'A'] },
  { roleId: 'CENTER_MGR', permId: 'OUT_ASSIGN', actions: ['R', 'C', 'U'] },
  { roleId: 'CENTER_MGR', permId: 'INB_CORRECTION', actions: ['R', 'A'] },
  { roleId: 'CENTER_MGR', permId: 'QRY_STOCK', actions: ['R'] },

  // 입고 작업자
  { roleId: 'INBOUND_WORKER', permId: 'INB_ARRIVE', actions: ['R', 'C'] },
  { roleId: 'INBOUND_WORKER', permId: 'INB_INSPECT', actions: ['R', 'C'] },
  { roleId: 'INBOUND_WORKER', permId: 'INB_PUTAWAY', actions: ['R', 'C'] },
  { roleId: 'INBOUND_WORKER', permId: 'INB_CORRECTION', actions: ['R', 'C'] },

  // 피킹/패킹 작업자
  { roleId: 'PICK_PACK', permId: 'OUT_ASSIGN', actions: ['R'] },
  { roleId: 'PICK_PACK', permId: 'OUT_PICK', actions: ['R', 'C'] },
  { roleId: 'PICK_PACK', permId: 'OUT_PACK', actions: ['R', 'C'] },
  { roleId: 'PICK_PACK', permId: 'OUT_SHORTAGE', actions: ['R', 'C'] },

  // 매장 관리자
  { roleId: 'STORE_MGR', permId: 'STR_REPLENISH', actions: ['R', 'C', 'U'] },
  { roleId: 'STORE_MGR', permId: 'STR_MOVE_APPROVE', actions: ['R', 'A'] },
  { roleId: 'STORE_MGR', permId: 'INV_STORE_COUNT', actions: ['R', 'C', 'U'] },
  { roleId: 'STORE_MGR', permId: 'QRY_STOCK', actions: ['R'] },

  // 매장 직원
  { roleId: 'STORE_STAFF', permId: 'STR_RECEIVE', actions: ['R', 'C'] },
  { roleId: 'STORE_STAFF', permId: 'STR_MOVE_OUT', actions: ['R', 'C'] },
  { roleId: 'STORE_STAFF', permId: 'STR_PARCEL', actions: ['R', 'C'] },
  { roleId: 'STORE_STAFF', permId: 'STR_REPLENISH', actions: ['R', 'C'] },

  // CS/조회 사용자 — 조회만
  { roleId: 'CS_VIEWER', permId: 'QRY_ORDER', actions: ['R'] },
  { roleId: 'CS_VIEWER', permId: 'QRY_DELIVERY', actions: ['R'] },
  { roleId: 'CS_VIEWER', permId: 'QRY_STOCK', actions: ['R'] },

  // 감사/분석 사용자
  { roleId: 'AUDITOR', permId: 'AUD_HISTORY', actions: ['R'] },
  { roleId: 'AUDITOR', permId: 'AUD_LOG', actions: ['R'] },
  { roleId: 'AUDITOR', permId: 'AUD_KPI', actions: ['R'] },
  { roleId: 'AUDITOR', permId: 'AUD_DOWNLOAD', actions: ['R', 'X'] },
]

/* ------------------------------------------------------------------ */
/* 공통정책 (표 3열)                                                     */
/* ------------------------------------------------------------------ */
export const POLICIES = [
  {
    policyId: 'P001',
    policyName: '업무 수량 직접 수정 금지',
    roleId: 'SYS_ADMIN',
    permId: 'INV_QTY_EDIT',
    policyType: 'DENY',
    enforceLevel: 'BLOCK',
    conditionExpr: 'action == "U" && target.type == "BIZ_QTY"',
    targetField: '재고/작업 수량 필드',
    message: '시스템 관리자는 업무 수량을 직접 수정할 수 없습니다. 재고조정 요청 프로세스를 이용하세요.',
    altProcess: '재고조정 요청 → 센터 관리자 승인',
    useYn: 'Y',
    remark: '설정 권한과 업무 데이터 변경 권한을 분리하기 위한 통제.',
  },
  {
    policyId: 'P002',
    policyName: 'SKU 폐기 선행조건 검증',
    roleId: 'HQ_MASTER',
    permId: 'MST_SKU',
    policyType: 'CONDITION',
    enforceLevel: 'BLOCK',
    conditionExpr: 'sku.onHandQty == 0 && sku.openTxCount == 0',
    targetField: 'SKU 상태 = 폐기',
    message: '재고 수량이 0이고 미처리 건이 0건일 때만 SKU를 폐기할 수 있습니다.',
    altProcess: '재고 소진/미처리 건 종결 후 재시도',
    useYn: 'Y',
    remark: '미처리 건: 발주/입고/출고/이동 진행중 전표.',
  },
  {
    policyId: 'P003',
    policyName: '발주취소 사유 필수',
    roleId: 'PURCHASER',
    permId: 'PUR_PO_CANCEL',
    policyType: 'REQUIRED',
    enforceLevel: 'BLOCK',
    conditionExpr: 'isNotEmpty(po.cancelReason)',
    targetField: 'po.cancelReason',
    message: '발주 취소 시 취소 사유를 반드시 입력해야 합니다.',
    altProcess: '-',
    useYn: 'Y',
    remark: '사유 코드 + 자유 텍스트 10자 이상 권장.',
  },
  {
    policyId: 'P004',
    policyName: '자기 요청 자기 승인 금지',
    roleId: 'CENTER_MGR',
    permId: 'INV_ADJ_APPROVE',
    policyType: 'SOD',
    enforceLevel: 'WARN',
    conditionExpr: 'request.createdBy != approver.userId',
    targetField: '승인 처리',
    message: '본인이 요청한 건을 본인이 승인하고 있습니다. 다른 승인자에게 위임을 권고합니다.',
    altProcess: '대체 승인자 지정 / 상위 승인',
    useYn: 'Y',
    remark: '권고(WARN) 단계. 감사 대응 강화 시 BLOCK 전환.',
  },
  {
    policyId: 'P005',
    policyName: '입고 확정 후 수정 불가',
    roleId: 'INBOUND_WORKER',
    permId: 'INB_APPROVE',
    policyType: 'DENY',
    enforceLevel: 'BLOCK',
    conditionExpr: 'inbound.status == "CONFIRMED"',
    targetField: '입하/검수/적치 실적',
    message: '입고 확정된 건은 수정할 수 없습니다. 정정요청을 등록하세요.',
    altProcess: '입고 정정요청 → 센터 관리자 승인',
    useYn: 'Y',
    remark: '정정요청은 INB_CORRECTION 권한으로 등록.',
  },
  {
    policyId: 'P006',
    policyName: '지시 외 SKU/수량 차단',
    roleId: 'PICK_PACK',
    permId: 'OUT_PICK',
    policyType: 'DENY',
    enforceLevel: 'BLOCK',
    conditionExpr: 'scan.sku in order.skuList && scan.qty <= order.remainQty',
    targetField: '피킹/패킹 스캔',
    message: '작업지시에 없는 SKU 또는 지시수량 초과 수량은 등록할 수 없습니다.',
    altProcess: '결품 등록 또는 작업지시 변경 요청',
    useYn: 'Y',
    remark: '초과 스캔 시 결품/대체 프로세스로 유도.',
  },
  {
    policyId: 'P007',
    policyName: '타 매장 재고 직접 수정 금지',
    roleId: 'STORE_MGR',
    permId: 'INV_STORE_COUNT',
    policyType: 'SCOPE',
    enforceLevel: 'BLOCK',
    conditionExpr: 'target.orgId == user.orgId',
    targetField: '매장 재고/실사',
    message: '소속 매장 외의 재고는 직접 수정할 수 없습니다.',
    altProcess: '매장 간 이동 요청 → 이동 승인',
    useYn: 'Y',
    remark: '데이터 범위(dataScope=OWN_ORG)와 함께 동작.',
  },
  {
    policyId: 'P008',
    policyName: '매장 직원 승인 한도',
    roleId: 'STORE_STAFF',
    permId: 'STR_MOVE_OUT',
    policyType: 'LIMIT',
    enforceLevel: 'APPROVAL',
    conditionExpr: 'doc.amount <= 1000000 && doc.qty <= 100',
    targetField: '이동출고/택배요청 금액·수량',
    message: '승인 한도(100만원 / 100EA)를 초과했습니다. 매장 관리자 승인이 필요합니다.',
    altProcess: '매장 관리자 승인',
    useYn: 'Y',
    limitAmount: 1000000,
    limitQty: 100,
    remark: '한도는 매장 등급별로 차등 적용 가능.',
  },
  {
    policyId: 'P009',
    policyName: 'CS/조회 사용자 읽기전용',
    roleId: 'CS_VIEWER',
    permId: null,
    policyType: 'READONLY',
    enforceLevel: 'BLOCK',
    conditionExpr: 'action in ["R"]',
    targetField: '전체 화면',
    message: '조회 전용 계정입니다. 등록·수정·삭제·승인이 제한됩니다.',
    altProcess: '담당 부서에 처리 요청',
    useYn: 'Y',
    remark: '메뉴는 노출되나 액션 버튼이 비활성화된다.',
  },
  {
    policyId: 'P010',
    policyName: '감사 사용자 개인정보 마스킹',
    roleId: 'AUDITOR',
    permId: 'AUD_DOWNLOAD',
    policyType: 'MASKING',
    enforceLevel: 'LOG',
    conditionExpr: 'field.piiYn == "Y"',
    targetField: '수취인명, 연락처, 주소, 이메일',
    message: '개인정보 항목은 마스킹되어 조회·다운로드됩니다.',
    altProcess: '원본 필요 시 개인정보 열람 승인 신청',
    useYn: 'Y',
    remark: '다운로드 시 사용자/시각/건수 감사로그 필수 기록.',
  },
]

/* ------------------------------------------------------------------ */
/* 사용자                                                              */
/* ------------------------------------------------------------------ */

/**
 * 데모용 공통 비밀번호.
 * 실제 시스템에서는 단방향 해시(bcrypt 등)를 저장하고 서버에서만 검증한다.
 * 이 프로젝트는 백엔드가 없어 평문으로 두며, 로그인 화면에 안내를 노출한다.
 */
export const DEMO_PASSWORD = 'wms1234!'

/** 로그인 연속 실패 허용 횟수 (초과 시 계정 잠금) */
export const MAX_LOGIN_FAIL = 5

const USER_ROWS = [
  {
    userId: 'admin', userName: '김시스', email: 'admin@corp.co.kr', phone: '010-1111-2222',
    orgId: 'HQ001', deptName: '정보시스템팀', position: '팀장',
    roleIds: ['SYS_ADMIN'], status: 'ACTIVE', approvalLimit: 0,
    lastLoginAt: '2026-09-08 09:12', useYn: 'Y',
  },
  {
    userId: 'hq.master', userName: '이기준', email: 'master@corp.co.kr', phone: '010-2222-3333',
    orgId: 'HQ001', deptName: '상품기획팀', position: '과장',
    roleIds: ['HQ_MASTER'], status: 'ACTIVE', approvalLimit: 0,
    lastLoginAt: '2026-09-08 08:40', useYn: 'Y',
  },
  {
    userId: 'buyer01', userName: '박구매', email: 'buyer01@corp.co.kr', phone: '010-3333-4444',
    orgId: 'HQ001', deptName: '구매팀', position: '대리',
    roleIds: ['PURCHASER'], status: 'ACTIVE', approvalLimit: 50000000,
    lastLoginAt: '2026-09-07 17:55', useYn: 'Y',
  },
  {
    userId: 'dc1.mgr', userName: '최센터', email: 'dc1mgr@corp.co.kr', phone: '010-4444-5555',
    orgId: 'DC001', deptName: '이천센터 운영팀', position: '센터장',
    roleIds: ['CENTER_MGR'], status: 'ACTIVE', approvalLimit: 20000000,
    lastLoginAt: '2026-09-08 07:30', useYn: 'Y',
  },
  {
    userId: 'dc1.in01', userName: '정입고', email: 'dc1in01@corp.co.kr', phone: '010-5555-6666',
    orgId: 'DC001', deptName: '이천센터 입고파트', position: '사원',
    roleIds: ['INBOUND_WORKER'], status: 'ACTIVE', approvalLimit: 0,
    lastLoginAt: '2026-09-08 06:58', useYn: 'Y',
  },
  {
    userId: 'dc1.pp01', userName: '한피킹', email: 'dc1pp01@corp.co.kr', phone: '010-6666-7777',
    orgId: 'DC001', deptName: '이천센터 출고파트', position: '사원',
    roleIds: ['PICK_PACK'], status: 'ACTIVE', approvalLimit: 0,
    lastLoginAt: '2026-09-08 06:55', useYn: 'Y',
  },
  {
    userId: 'dc2.mgr', userName: '오김해', email: 'dc2mgr@corp.co.kr', phone: '010-7777-8888',
    orgId: 'DC002', deptName: '김해센터 운영팀', position: '센터장',
    roleIds: ['CENTER_MGR', 'PICK_PACK'], status: 'ACTIVE', approvalLimit: 20000000,
    lastLoginAt: '2026-09-05 18:20', useYn: 'Y',
  },
  {
    userId: 'st1.mgr', userName: '강강남', email: 'st1mgr@corp.co.kr', phone: '010-8888-9999',
    orgId: 'ST001', deptName: '강남점', position: '점장',
    roleIds: ['STORE_MGR'], status: 'ACTIVE', approvalLimit: 5000000,
    lastLoginAt: '2026-09-08 10:02', useYn: 'Y',
  },
  {
    userId: 'st1.stf1', userName: '윤직원', email: 'st1stf1@corp.co.kr', phone: '010-9999-0000',
    orgId: 'ST001', deptName: '강남점', position: '사원',
    roleIds: ['STORE_STAFF'], status: 'ACTIVE', approvalLimit: 1000000,
    lastLoginAt: '2026-09-08 10:10', useYn: 'Y',
  },
  {
    userId: 'st2.stf1', userName: '서판교', email: 'st2stf1@corp.co.kr', phone: '010-1212-3434',
    orgId: 'ST002', deptName: '판교점', position: '사원',
    roleIds: ['STORE_STAFF'], status: 'LOCKED', approvalLimit: 1000000,
    lastLoginAt: '2026-08-21 13:44', useYn: 'Y',
  },
  {
    userId: 'cs01', userName: '문상담', email: 'cs01@corp.co.kr', phone: '010-2323-4545',
    orgId: 'HQ001', deptName: 'CS팀', position: '사원',
    roleIds: ['CS_VIEWER'], status: 'ACTIVE', approvalLimit: 0,
    lastLoginAt: '2026-09-08 09:31', useYn: 'Y',
  },
  {
    userId: 'audit01', userName: '노감사', email: 'audit01@corp.co.kr', phone: '010-3434-5656',
    orgId: 'HQ001', deptName: '감사팀', position: '차장',
    roleIds: ['AUDITOR'], status: 'ACTIVE', approvalLimit: 0,
    lastLoginAt: '2026-09-04 15:10', useYn: 'Y',
  },
  {
    userId: 'st3.stf1', userName: '배해운', email: 'st3stf1@corp.co.kr', phone: '010-4545-6767',
    orgId: 'ST003', deptName: '해운대점', position: '사원',
    roleIds: ['STORE_STAFF'], status: 'DORMANT', approvalLimit: 1000000,
    lastLoginAt: '2026-05-19 11:02', useYn: 'N',
  },
]

/**
 * 인증 필드를 붙여서 내보낸다.
 * - password        데모 공통 비밀번호
 * - loginFailCount  연속 로그인 실패 횟수 (MAX_LOGIN_FAIL 초과 시 잠금)
 * 판교점 직원(st2.stf1)은 이미 실패로 잠긴 상태를 재현한다.
 */
export const USERS = USER_ROWS.map((u) => ({
  ...u,
  password: DEMO_PASSWORD,
  loginFailCount: u.status === 'LOCKED' ? MAX_LOGIN_FAIL : 0,
}))
