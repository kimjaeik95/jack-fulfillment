-- ============================================================================
-- V3 : system 기준정보  (PostgreSQL)
--      조직 · 역할 · 권한 · 권한허용액션 · 역할권한 · 공통정책 · 최초 관리자 계정
--
--      요구사항 "2. 사용자·권한·공통 정책" 표를 그대로 반영한다.
--      업무코드(org_id, role_id, perm_id)로 조인해 대리키를 채우므로
--      INSERT 순서가 바뀌어도 FK 가 어긋나지 않는다.
-- ============================================================================


-- ============================================================================
-- 조직 — 본사 아래에 물류센터 2, 매장 3
-- ============================================================================
INSERT INTO tb_org (org_id, org_name, org_type, parent_seq, manager_name, sort_order, created_by)
VALUES ('HQ001', '본사', 'HQ', NULL, NULL, 10, 'system');

INSERT INTO tb_org (org_id, org_name, org_type, parent_seq, manager_name, sort_order, created_by)
SELECT v.org_id, v.org_name, v.org_type, p.org_seq, v.manager_name, v.sort_order, 'system'
  FROM (VALUES
        ('DC001', '이천물류센터', 'DC',    '최센터', 20),
        ('DC002', '김해물류센터', 'DC',    '오김해', 30),
        ('ST001', '강남점',       'STORE', '강강남', 40),
        ('ST002', '판교점',       'STORE', NULL,     50),
        ('ST003', '해운대점',     'STORE', NULL,     60)
       ) AS v(org_id, org_name, org_type, manager_name, sort_order)
 CROSS JOIN (SELECT org_seq FROM tb_org WHERE org_id = 'HQ001') AS p;


-- ============================================================================
-- 역할 — 표 1열
--   org_scope           이 역할을 배정할 수 있는 조직유형
--   default_data_scope  역할권한에서 미지정 시 상속되는 데이터 범위
--   restriction_summary 표 3열의 요약. 실제 통제는 tb_policy 가 수행한다
-- ============================================================================
INSERT INTO tb_role (role_id, role_name, description, org_scope, default_data_scope,
                     restriction_summary, sort_order, created_by)
VALUES
  ('SYS_ADMIN',      '시스템 관리자',      '회사·사용자·역할·공통코드·인터페이스 설정', 'HQ',    'ALL',     '업무 수량 직접 수정 금지',         10,  'system'),
  ('HQ_MASTER',      '본사 기준정보 담당', '제품/SKU/브랜드/채널/가격/공급처 관리',     'HQ',    'ALL',     'SKU 폐기는 재고 0 및 미처리 건 0', 20,  'system'),
  ('PURCHASER',      '구매 담당',          '구매요청 승인, PO 발주·취소',               'HQ',    'ALL',     '발주취소 사유 필수',               30,  'system'),
  ('CENTER_MGR',     '센터 관리자',        '입고·출고·실사 승인, 재고조정 승인',        'DC',    'OWN_ORG', '자기 요청 자기 승인 금지 권고',    40,  'system'),
  ('INBOUND_WORKER', '입고 작업자',        '입하·검수·적치 스캔',                       'DC',    'OWN_ORG', '확정 후 수정 불가, 정정요청',      50,  'system'),
  ('PICK_PACK',      '피킹/패킹 작업자',   '작업 할당·실적·결품 등록',                  'DC',    'OWN_ORG', '지시 외 SKU/수량 차단',            60,  'system'),
  ('STORE_MGR',      '매장 관리자',        '보충요청·이동 승인·매장실사',               'STORE', 'OWN_ORG', '타 매장 재고 직접 수정 금지',      70,  'system'),
  ('STORE_STAFF',    '매장 직원',          '매장입고·이동출고·택배요청',                'STORE', 'OWN_ORG', '승인 한도 적용',                   80,  'system'),
  ('CS_VIEWER',      'CS/조회 사용자',     '주문·배송·재고 조회',                       'HQ',    'ALL',     '수정 권한 없음',                   90,  'system'),
  ('AUDITOR',        '감사/분석 사용자',   '이력·로그·KPI 조회/다운로드',               'HQ',    'ALL',     '개인정보 마스킹',                  100, 'system');


-- ============================================================================
-- 권한(기능) + 권한허용액션
--   액션 문자열('RCUD')을 string_to_array(str, NULL) 로 한 글자씩 배열로 만들고
--   unnest 로 행으로 펼친다. PostgreSQL 고유 기능이다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('SYS_COMPANY',       '회사 관리',            'SYS', '시스템 > 회사관리',    10,  'system'),
  ('SYS_USER',          '사용자 관리',          'SYS', '시스템 > 사용자관리',  20,  'system'),
  ('SYS_ROLE',          '역할/권한 관리',       'SYS', '시스템 > 역할관리',    30,  'system'),
  ('SYS_CODE',          '공통코드 관리',        'SYS', '시스템 > 공통코드',    40,  'system'),
  ('SYS_IF',            '인터페이스 설정',      'SYS', '시스템 > 인터페이스',  50,  'system'),
  ('SYS_POLICY',        '공통정책 관리',        'SYS', '시스템 > 공통정책',    60,  'system'),
  ('MST_SKU',           '제품/SKU 관리',        'MST', '기준정보 > SKU',       110, 'system'),
  ('MST_BRAND',         '브랜드 관리',          'MST', '기준정보 > 브랜드',    120, 'system'),
  ('MST_CHANNEL',       '채널 관리',            'MST', '기준정보 > 채널',      130, 'system'),
  ('MST_PRICE',         '가격 관리',            'MST', '기준정보 > 가격',      140, 'system'),
  ('MST_VENDOR',        '공급처 관리',          'MST', '기준정보 > 공급처',    150, 'system'),
  ('PUR_REQ_APPROVE',   '구매요청 승인',        'PUR', '구매 > 구매요청',      210, 'system'),
  ('PUR_PO_ISSUE',      'PO 발주',              'PUR', '구매 > 발주',          220, 'system'),
  ('PUR_PO_CANCEL',     'PO 발주취소',          'PUR', '구매 > 발주취소',      230, 'system'),
  ('INB_ARRIVE',        '입하 스캔',            'INB', '입고 > 입하',          310, 'system'),
  ('INB_INSPECT',       '검수 스캔',            'INB', '입고 > 검수',          320, 'system'),
  ('INB_PUTAWAY',       '적치 스캔',            'INB', '입고 > 적치',          330, 'system'),
  ('INB_APPROVE',       '입고 확정/승인',       'INB', '입고 > 입고확정',      340, 'system'),
  ('INB_CORRECTION',    '입고 정정요청',        'INB', '입고 > 정정요청',      350, 'system'),
  ('OUT_APPROVE',       '출고 승인',            'OUT', '출고 > 출고승인',      410, 'system'),
  ('OUT_ASSIGN',        '작업 할당',            'OUT', '출고 > 작업할당',      420, 'system'),
  ('OUT_PICK',          '피킹 실적 등록',       'OUT', '출고 > 피킹',          430, 'system'),
  ('OUT_PACK',          '패킹 실적 등록',       'OUT', '출고 > 패킹',          440, 'system'),
  ('OUT_SHORTAGE',      '결품 등록',            'OUT', '출고 > 결품',          450, 'system'),
  ('INV_COUNT_APPROVE', '실사 승인',            'INV', '재고 > 실사승인',      510, 'system'),
  ('INV_ADJ_APPROVE',   '재고조정 승인',        'INV', '재고 > 조정승인',      520, 'system'),
  ('INV_QTY_EDIT',      '업무 수량 직접 수정',  'INV', '재고 > 수량보정',      530, 'system'),
  ('INV_STORE_COUNT',   '매장 실사',            'INV', '재고 > 매장실사',      540, 'system'),
  ('STR_REPLENISH',     '보충 요청',            'STR', '매장 > 보충요청',      610, 'system'),
  ('STR_MOVE_APPROVE',  '매장이동 승인',        'STR', '매장 > 이동승인',      620, 'system'),
  ('STR_RECEIVE',       '매장 입고',            'STR', '매장 > 매장입고',      630, 'system'),
  ('STR_MOVE_OUT',      '이동 출고',            'STR', '매장 > 이동출고',      640, 'system'),
  ('STR_PARCEL',        '택배 요청',            'STR', '매장 > 택배요청',      650, 'system'),
  ('QRY_ORDER',         '주문 조회',            'QRY', '조회 > 주문',          710, 'system'),
  ('QRY_DELIVERY',      '배송 조회',            'QRY', '조회 > 배송',          720, 'system'),
  ('QRY_STOCK',         '재고 조회',            'QRY', '조회 > 재고',          730, 'system'),
  ('AUD_HISTORY',       '변경 이력 조회',       'AUD', '감사 > 이력',          810, 'system'),
  ('AUD_LOG',           '시스템 로그 조회',     'AUD', '감사 > 로그',          820, 'system'),
  ('AUD_KPI',           'KPI 조회',             'AUD', '감사 > KPI',           830, 'system'),
  ('AUD_DOWNLOAD',      '데이터 다운로드',      'AUD', '감사 > 다운로드',      840, 'system');


INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('SYS_COMPANY',       'RCUD'),
        ('SYS_USER',          'RCUD'),
        ('SYS_ROLE',          'RCUD'),
        ('SYS_CODE',          'RCUD'),
        ('SYS_IF',            'RCU'),
        ('SYS_POLICY',        'RCUD'),
        ('MST_SKU',           'RCUD'),
        ('MST_BRAND',         'RCUD'),
        ('MST_CHANNEL',       'RCUD'),
        ('MST_PRICE',         'RCU'),
        ('MST_VENDOR',        'RCUD'),
        ('PUR_REQ_APPROVE',   'RA'),
        ('PUR_PO_ISSUE',      'RCU'),
        ('PUR_PO_CANCEL',     'RU'),
        ('INB_ARRIVE',        'RC'),
        ('INB_INSPECT',       'RC'),
        ('INB_PUTAWAY',       'RC'),
        ('INB_APPROVE',       'RA'),
        -- 작업자는 정정요청 등록(C), 센터 관리자는 승인(A) 하므로 A 를 포함한다
        ('INB_CORRECTION',    'RCA'),
        ('OUT_APPROVE',       'RA'),
        ('OUT_ASSIGN',        'RCU'),
        ('OUT_PICK',          'RC'),
        ('OUT_PACK',          'RC'),
        ('OUT_SHORTAGE',      'RC'),
        ('INV_COUNT_APPROVE', 'RA'),
        ('INV_ADJ_APPROVE',   'RA'),
        ('INV_QTY_EDIT',      'U'),
        ('INV_STORE_COUNT',   'RCU'),
        ('STR_REPLENISH',     'RCU'),
        ('STR_MOVE_APPROVE',  'RA'),
        ('STR_RECEIVE',       'RC'),
        ('STR_MOVE_OUT',      'RC'),
        ('STR_PARCEL',        'RC'),
        ('QRY_ORDER',         'R'),
        ('QRY_DELIVERY',      'R'),
        ('QRY_STOCK',         'R'),
        ('AUD_HISTORY',       'R'),
        ('AUD_LOG',           'R'),
        ('AUD_KPI',           'R'),
        ('AUD_DOWNLOAD',      'RX')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);


-- ============================================================================
-- 역할권한 — 역할이 실제로 부여받은 액션
--   data_scope 는 지정하지 않는다(NULL) → tb_role.default_data_scope 를 상속
--   시스템 관리자에게 INV_QTY_EDIT 를 부여하지 않는다 → 정책 P001 과 짝을 이룬다
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 시스템 관리자 : 설정 전권 + 감사 조회
        ('SYS_ADMIN',      'SYS_COMPANY',       'RCUD'),
        ('SYS_ADMIN',      'SYS_USER',          'RCUD'),
        ('SYS_ADMIN',      'SYS_ROLE',          'RCUD'),
        ('SYS_ADMIN',      'SYS_CODE',          'RCUD'),
        ('SYS_ADMIN',      'SYS_IF',            'RCU'),
        ('SYS_ADMIN',      'SYS_POLICY',        'RCUD'),
        ('SYS_ADMIN',      'AUD_LOG',           'R'),
        ('SYS_ADMIN',      'AUD_HISTORY',       'R'),

        -- 본사 기준정보 담당
        ('HQ_MASTER',      'MST_SKU',           'RCUD'),
        ('HQ_MASTER',      'MST_BRAND',         'RCUD'),
        ('HQ_MASTER',      'MST_CHANNEL',       'RCUD'),
        ('HQ_MASTER',      'MST_VENDOR',        'RCUD'),
        ('HQ_MASTER',      'MST_PRICE',         'RCU'),
        ('HQ_MASTER',      'QRY_STOCK',         'R'),

        -- 구매 담당
        ('PURCHASER',      'PUR_REQ_APPROVE',   'RA'),
        ('PURCHASER',      'PUR_PO_ISSUE',      'RCU'),
        ('PURCHASER',      'PUR_PO_CANCEL',     'RU'),
        ('PURCHASER',      'MST_VENDOR',        'R'),
        ('PURCHASER',      'QRY_STOCK',         'R'),

        -- 센터 관리자 : 승인 중심
        ('CENTER_MGR',     'INB_APPROVE',       'RA'),
        ('CENTER_MGR',     'OUT_APPROVE',       'RA'),
        ('CENTER_MGR',     'INV_COUNT_APPROVE', 'RA'),
        ('CENTER_MGR',     'INV_ADJ_APPROVE',   'RA'),
        ('CENTER_MGR',     'OUT_ASSIGN',        'RCU'),
        ('CENTER_MGR',     'INB_CORRECTION',    'RA'),
        ('CENTER_MGR',     'QRY_STOCK',         'R'),

        -- 입고 작업자 : 스캔 등록
        ('INBOUND_WORKER', 'INB_ARRIVE',        'RC'),
        ('INBOUND_WORKER', 'INB_INSPECT',       'RC'),
        ('INBOUND_WORKER', 'INB_PUTAWAY',       'RC'),
        ('INBOUND_WORKER', 'INB_CORRECTION',    'RC'),

        -- 피킹/패킹 작업자
        ('PICK_PACK',      'OUT_ASSIGN',        'R'),
        ('PICK_PACK',      'OUT_PICK',          'RC'),
        ('PICK_PACK',      'OUT_PACK',          'RC'),
        ('PICK_PACK',      'OUT_SHORTAGE',      'RC'),

        -- 매장 관리자
        ('STORE_MGR',      'STR_REPLENISH',     'RCU'),
        ('STORE_MGR',      'STR_MOVE_APPROVE',  'RA'),
        ('STORE_MGR',      'INV_STORE_COUNT',   'RCU'),
        ('STORE_MGR',      'QRY_STOCK',         'R'),

        -- 매장 직원
        ('STORE_STAFF',    'STR_RECEIVE',       'RC'),
        ('STORE_STAFF',    'STR_MOVE_OUT',      'RC'),
        ('STORE_STAFF',    'STR_PARCEL',        'RC'),
        ('STORE_STAFF',    'STR_REPLENISH',     'RC'),

        -- CS/조회 사용자 : 조회만
        ('CS_VIEWER',      'QRY_ORDER',         'R'),
        ('CS_VIEWER',      'QRY_DELIVERY',      'R'),
        ('CS_VIEWER',      'QRY_STOCK',         'R'),

        -- 감사/분석 사용자
        ('AUDITOR',        'AUD_HISTORY',       'R'),
        ('AUDITOR',        'AUD_LOG',           'R'),
        ('AUDITOR',        'AUD_KPI',           'R'),
        ('AUDITOR',        'AUD_DOWNLOAD',      'RX')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);


-- ============================================================================
-- 공통정책 — 표 3열 "제한/승인" 10개 항목
--   perm_id 가 NULL 이면 그 역할의 전체 기능에 적용된다.
-- ============================================================================
INSERT INTO tb_policy (policy_id, policy_name, role_seq, perm_seq, policy_type, enforce_level,
                       condition_expr, target_field, message, alt_process,
                       limit_amount, limit_qty, remark, created_by)
SELECT v.policy_id, v.policy_name, r.role_seq, p.perm_seq, v.policy_type, v.enforce_level,
       v.condition_expr, v.target_field, v.message, v.alt_process,
       v.limit_amount, v.limit_qty, v.remark, 'system'
  FROM (VALUES
        ('P001', '업무 수량 직접 수정 금지', 'SYS_ADMIN', 'INV_QTY_EDIT',
         'DENY', 'BLOCK',
         'action == "U" && target.type == "BIZ_QTY"',
         '재고/작업 수량 필드',
         '시스템 관리자는 업무 수량을 직접 수정할 수 없습니다. 재고조정 요청 프로세스를 이용하세요.',
         '재고조정 요청 → 센터 관리자 승인',
         NULL::bigint, NULL::integer,
         '설정 권한과 업무 데이터 변경 권한을 분리하기 위한 통제.'),

        ('P002', 'SKU 폐기 선행조건 검증', 'HQ_MASTER', 'MST_SKU',
         'CONDITION', 'BLOCK',
         'sku.onHandQty == 0 && sku.openTxCount == 0',
         'SKU 상태 = 폐기',
         '재고 수량이 0이고 미처리 건이 0건일 때만 SKU를 폐기할 수 있습니다.',
         '재고 소진/미처리 건 종결 후 재시도',
         NULL, NULL,
         '미처리 건: 발주/입고/출고/이동 진행중 전표.'),

        ('P003', '발주취소 사유 필수', 'PURCHASER', 'PUR_PO_CANCEL',
         'REQUIRED', 'BLOCK',
         'isNotEmpty(po.cancelReason)',
         'po.cancelReason',
         '발주 취소 시 취소 사유를 반드시 입력해야 합니다.',
         NULL,
         NULL, NULL,
         '사유 코드 + 자유 텍스트 10자 이상 권장.'),

        ('P004', '자기 요청 자기 승인 금지', 'CENTER_MGR', 'INV_ADJ_APPROVE',
         'SOD', 'WARN',
         'request.createdBy != approver.userId',
         '승인 처리',
         '본인이 요청한 건을 본인이 승인하고 있습니다. 다른 승인자에게 위임을 권고합니다.',
         '대체 승인자 지정 / 상위 승인',
         NULL, NULL,
         '권고(WARN) 단계. 감사 대응 강화 시 BLOCK 전환.'),

        ('P005', '입고 확정 후 수정 불가', 'INBOUND_WORKER', 'INB_APPROVE',
         'DENY', 'BLOCK',
         'inbound.status == "CONFIRMED"',
         '입하/검수/적치 실적',
         '입고 확정된 건은 수정할 수 없습니다. 정정요청을 등록하세요.',
         '입고 정정요청 → 센터 관리자 승인',
         NULL, NULL,
         '정정요청은 INB_CORRECTION 권한으로 등록. 입고 작업자에게 INB_APPROVE 는 미부여 상태다.'),

        ('P006', '지시 외 SKU/수량 차단', 'PICK_PACK', 'OUT_PICK',
         'DENY', 'BLOCK',
         'scan.sku in order.skuList && scan.qty <= order.remainQty',
         '피킹/패킹 스캔',
         '작업지시에 없는 SKU 또는 지시수량 초과 수량은 등록할 수 없습니다.',
         '결품 등록 또는 작업지시 변경 요청',
         NULL, NULL,
         '초과 스캔 시 결품/대체 프로세스로 유도.'),

        ('P007', '타 매장 재고 직접 수정 금지', 'STORE_MGR', 'INV_STORE_COUNT',
         'SCOPE', 'BLOCK',
         'target.orgId == user.orgId',
         '매장 재고/실사',
         '소속 매장 외의 재고는 직접 수정할 수 없습니다.',
         '매장 간 이동 요청 → 이동 승인',
         NULL, NULL,
         '데이터 범위(OWN_ORG)와 함께 동작.'),

        ('P008', '매장 직원 승인 한도', 'STORE_STAFF', 'STR_MOVE_OUT',
         'LIMIT', 'APPROVAL',
         'doc.amount <= 1000000 && doc.qty <= 100',
         '이동출고/택배요청 금액·수량',
         '승인 한도(100만원 / 100EA)를 초과했습니다. 매장 관리자 승인이 필요합니다.',
         '매장 관리자 승인',
         1000000, 100,
         '한도는 매장 등급별로 차등 적용 가능.'),

        ('P009', 'CS/조회 사용자 읽기전용', 'CS_VIEWER', NULL,
         'READONLY', 'BLOCK',
         'action in ["R"]',
         '전체 화면',
         '조회 전용 계정입니다. 등록·수정·삭제·승인이 제한됩니다.',
         '담당 부서에 처리 요청',
         NULL, NULL,
         '메뉴는 노출되나 액션 버튼이 비활성화된다.'),

        ('P010', '감사 사용자 개인정보 마스킹', 'AUDITOR', 'AUD_DOWNLOAD',
         'MASKING', 'LOG',
         'field.piiYn == "Y"',
         '수취인명, 연락처, 주소, 이메일',
         '개인정보 항목은 마스킹되어 조회·다운로드됩니다.',
         '원본 필요 시 개인정보 열람 승인 신청',
         NULL, NULL,
         '다운로드 시 사용자/시각/건수 감사로그 필수 기록.')
       ) AS v(policy_id, policy_name, role_id, perm_id, policy_type, enforce_level,
              condition_expr, target_field, message, alt_process,
              limit_amount, limit_qty, remark)
  JOIN tb_role r            ON r.role_id = v.role_id
  LEFT JOIN tb_permission p ON p.perm_id = v.perm_id;


-- ============================================================================
-- 최초 관리자 계정
--   비밀번호는 BCrypt(strength 10) 해시. 평문은 'wms1234!'.
--   최초 로그인 후 반드시 변경해야 한다.
-- ============================================================================
INSERT INTO tb_user (user_id, user_name, password_hash, org_seq, email, phone,
                     dept_name, position_name, status, approval_limit, created_by)
SELECT 'admin', '김시스',
       '$2a$10$ncjMxbbiOKNZRwCLqc/ROu.IMxzplGB6tD8vc5pzaNPcDVdMcnOkC',
       o.org_seq, 'admin@corp.co.kr', '010-1111-2222',
       '정보시스템팀', '팀장', 'ACTIVE', 0, 'system'
  FROM tb_org o
 WHERE o.org_id = 'HQ001';

INSERT INTO tb_user_role (user_seq, role_seq, created_by)
SELECT u.user_seq, r.role_seq, 'system'
  FROM tb_user u
 CROSS JOIN tb_role r
 WHERE u.user_id = 'admin'
   AND r.role_id = 'SYS_ADMIN';
