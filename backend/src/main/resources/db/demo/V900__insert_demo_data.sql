-- ============================================================================
-- V900 : 데모 데이터 (로컬 개발 전용)  PostgreSQL
--
-- 이 폴더(db/demo)는 local 프로파일에서만 Flyway locations 에 포함된다.
-- dev · prod 에는 적용되지 않으므로 여기 있는 것은 배포되지 않는다.
--
-- 담는 것: 운영 방식에 따라 달라지는 모든 것.
--   나머지 역할 · 역할권한 · 공통정책 · 센터 조직 · 플랜트 · 창고 · 빈 · 사용자
--
-- 담지 않는 것: 없으면 시스템이 성립하지 않는 것 (db/migration/V3 가 담당).
--
-- 이 경계를 지키는 것이 중요하다. 전에는 예시가 마이그레이션에 섞여 있어서,
-- 범위가 바뀔 때마다 예시를 지우는 마이그레이션을 새로 써야 했다.
-- 여기 있는 데이터는 마음대로 바꿔도 마이그레이션을 새로 쓸 필요가 없다.
--
-- 비밀번호는 전부 'wms1234!' (BCrypt strength 10).
-- ============================================================================


-- ============================================================================
-- 1. 회사 — 시드의 자리표시자를 데모 값으로 바꾼다
-- ============================================================================
UPDATE tb_company
   SET company_name = '잭풀필먼트',
       biz_reg_no   = '123-45-67890',
       ceo_name     = '이대표',
       zip_code     = '07326',
       address      = '서울 영등포구 여의대로 1',
       phone        = '02-1234-5678',
       updated_by   = 'system',
       updated_at   = CURRENT_TIMESTAMP
 WHERE company_id = 'CO001';


-- ============================================================================
-- 2. admin 의 비밀번호 변경 강제 해제
--
-- 시드(V3)는 admin 을 must_change_password='Y' 로 만든다. 초기 비밀번호가
-- 저장소에 공개되어 있으므로 운영에서는 첫 로그인에 반드시 바꿔야 한다.
-- 실제로 그렇게 동작한다 — 'Y' 인 동안 로그인은 되지만 다른 모든 API 가
-- PASSWORD_CHANGE_REQUIRED 로 막힌다.
--
-- 로컬은 화면을 바로 열어 보는 용도라 매번 변경 화면을 거치면 번거롭다.
-- 그래서 데모에서만 풀어 준다. 운영 배포에는 이 파일이 들어가지 않는다.
-- ============================================================================
UPDATE tb_user
   SET must_change_password = 'N',
       password_changed_at  = CURRENT_TIMESTAMP
 WHERE user_id = 'admin';


-- ============================================================================
-- 3. 센터 조직 — 본사 아래에 둘
-- ============================================================================
INSERT INTO tb_org (company_seq, org_id, org_name, org_type, parent_seq,
                    manager_name, phone, zip_code, address, sort_order, created_by)
SELECT c.company_seq, v.org_id, v.org_name, 'DC', h.org_seq,
       v.manager_name, v.phone, v.zip_code, v.address, v.sort_order, 'system'
  FROM (VALUES
        ('DC001', '이천센터 운영조직', '최센터', '031-100-1000', '17383', '경기 이천시 마장면 물류로 10', 20),
        ('DC002', '김해센터 운영조직', '오김해', '055-200-2000', '50969', '경남 김해시 주촌면 유통로 20', 30)
       ) AS v(org_id, org_name, manager_name, phone, zip_code, address, sort_order)
 CROSS JOIN tb_company c
  JOIN tb_org h ON h.org_id = 'HQ001'
 WHERE c.company_id = 'CO001';


-- ============================================================================
-- 4. 플랜트 — 센터 조직이 운영하는 물리 거점
--
-- 조직과 1:1 로 보이지만 같은 것이 아니다. 조직개편으로 이천센터 운영조직이
-- 사라져도 이천 플랜트의 재고는 그대로 있어야 한다. 그때 플랜트의 org_seq 만
-- 다른 조직으로 옮긴다.
-- ============================================================================
INSERT INTO tb_plant (org_seq, plant_id, plant_name, plant_type,
                      zip_code, address, manager_name, phone, sort_order, created_by)
SELECT o.org_seq, v.plant_id, v.plant_name, v.plant_type,
       v.zip_code, v.address, v.manager_name, v.phone, v.sort_order, 'system'
  FROM (VALUES
        ('DC001', 'PL001', '이천물류센터', 'DC', '17383', '경기 이천시 마장면 물류로 10', '최센터', '031-100-1000', 10),
        ('DC002', 'PL002', '김해물류센터', 'DC', '50969', '경남 김해시 주촌면 유통로 20', '오김해', '055-200-2000', 20),
        -- 반품센터는 같은 조직이 겸해서 운영한다. 조직 하나에 플랜트가 둘일
        -- 수 있다는 것을 데모로 보여 둔다.
        ('DC001', 'PL003', '이천반품센터', 'RC', '17383', '경기 이천시 마장면 물류로 12', '최센터', '031-100-1002', 30)
       ) AS v(org_id, plant_id, plant_name, plant_type, zip_code, address, manager_name, phone, sort_order)
  JOIN tb_org o ON o.org_id = v.org_id;


-- ============================================================================
-- 5. 창고 — 플랜트 안의 구획
--    코드는 플랜트 안에서만 유일하다. 센터마다 GD(양품)가 있는 것이 정상이다.
-- ============================================================================
INSERT INTO tb_warehouse (plant_seq, warehouse_id, warehouse_name, warehouse_type,
                          position_desc, sort_order, created_by)
SELECT p.plant_seq, v.warehouse_id, v.warehouse_name, v.warehouse_type,
       v.position_desc, v.sort_order, 'system'
  FROM (VALUES
        ('PL001', 'GD', '이천 양품창고', 'GOOD',   'A동 1~2층', 10),
        ('PL001', 'RT', '이천 반품창고', 'RETURN', 'B동 1층',   20),
        ('PL001', 'DF', '이천 불량창고', 'DEFECT', 'B동 2층',   30),
        ('PL002', 'GD', '김해 양품창고', 'GOOD',   '1동 1층',   10),
        ('PL002', 'RT', '김해 반품창고', 'RETURN', '2동 1층',   20),
        ('PL003', 'RT', '반품 판정창고', 'RETURN', '검사라인',  10)
       ) AS v(plant_id, warehouse_id, warehouse_name, warehouse_type, position_desc, sort_order)
  JOIN tb_plant p ON p.plant_id = v.plant_id;


-- ============================================================================
-- 6. 로케이션(빈)
--    코드는 전역 유일하다. 라벨에 찍혀 현장에서 스캔되는 값이므로
--    스캔 한 번으로 한 곳이 지목되어야 한다.
--
--    TRANSIT(운송중)은 물리적인 자리가 아니라 이동 중 재고가 잠시 머무는
--    가상 로케이션이다. 재고가 어디에도 없는 상태를 만들지 않기 위해 둔다.
-- ============================================================================
INSERT INTO tb_location (warehouse_seq, location_id, sector, zone_code, floor_no,
                         location_type, barcode, sort_order, created_by)
SELECT w.warehouse_seq, v.location_id, v.sector, v.zone_code, v.floor_no,
       v.location_type, v.barcode, v.sort_order, 'system'
  FROM (VALUES
        -- 이천 양품 — 섹터 1, A구역 1~2층
        ('PL001', 'GD', '1A-01-01', '1', 'A', '1', 'NORMAL',  'LOC1A0101', 10),
        ('PL001', 'GD', '1A-01-02', '1', 'A', '1', 'NORMAL',  'LOC1A0102', 20),
        ('PL001', 'GD', '1A-01-03', '1', 'A', '1', 'NORMAL',  'LOC1A0103', 30),
        ('PL001', 'GD', '1A-02-01', '1', 'A', '2', 'NORMAL',  'LOC1A0201', 40),
        ('PL001', 'GD', '1A-02-02', '1', 'A', '2', 'NORMAL',  'LOC1A0202', 50),
        ('PL001', 'GD', '1B-01-01', '1', 'B', '1', 'NORMAL',  'LOC1B0101', 60),
        ('PL001', 'GD', 'TRN-PL001', NULL, NULL, NULL, 'TRANSIT', NULL,    99),
        -- 이천 반품 · 불량
        ('PL001', 'RT', 'RT-01-01', 'R', 'A', '1', 'RETURN',  'LOCRT0101', 10),
        ('PL001', 'RT', 'RT-01-02', 'R', 'A', '1', 'RETURN',  'LOCRT0102', 20),
        ('PL001', 'DF', 'DF-01-01', 'D', 'A', '2', 'DEFECT',  'LOCDF0101', 10),
        -- 김해 양품 · 반품
        ('PL002', 'GD', '2A-01-01', '2', 'A', '1', 'NORMAL',  'LOC2A0101', 10),
        ('PL002', 'GD', '2A-01-02', '2', 'A', '1', 'NORMAL',  'LOC2A0102', 20),
        ('PL002', 'GD', '2A-02-01', '2', 'A', '2', 'NORMAL',  'LOC2A0201', 30),
        ('PL002', 'GD', 'TRN-PL002', NULL, NULL, NULL, 'TRANSIT', NULL,    99),
        ('PL002', 'RT', 'KRT-01-01', 'R', 'A', '1', 'RETURN', 'LOCKRT0101', 10),
        -- 이천반품센터 판정창고
        ('PL003', 'RT', 'JD-01-01', 'J', 'A', '1', 'RETURN',  'LOCJD0101', 10)
       ) AS v(plant_id, warehouse_id, location_id, sector, zone_code, floor_no,
              location_type, barcode, sort_order)
  JOIN tb_plant p     ON p.plant_id = v.plant_id
  JOIN tb_warehouse w ON w.plant_seq = p.plant_seq AND w.warehouse_id = v.warehouse_id;


-- ============================================================================
-- 7. 나머지 역할
--    org_scope           이 역할을 배정할 수 있는 조직유형
--    default_data_scope  역할권한에서 미지정 시 상속되는 데이터 범위
--    restriction_summary 요약 표기. 실제 통제는 tb_policy 가 수행한다
-- ============================================================================
INSERT INTO tb_role (role_id, role_name, description, org_scope, default_data_scope,
                     restriction_summary, sort_order, created_by)
VALUES
  ('HQ_MASTER',      '본사 기준정보 담당', '거점·제품/SKU/브랜드/채널/가격/공급처 관리', 'HQ', 'ALL',     'SKU 폐기는 재고 0 및 미처리 건 0', 20,  'system'),
  ('PURCHASER',      '구매 담당',          '구매요청 승인, PO 발주·취소',                'HQ', 'ALL',     '발주취소 사유 필수',               30,  'system'),
  ('CENTER_MGR',     '센터 관리자',        '입고·출고·실사 승인, 재고조정 승인',         'DC', 'OWN_ORG', '자기 요청 자기 승인 금지 권고',    40,  'system'),
  ('INBOUND_WORKER', '입고 작업자',        '입하·검수·적치 스캔',                        'DC', 'OWN_ORG', '확정 후 수정 불가, 정정요청',      50,  'system'),
  ('PICK_PACK',      '피킹/패킹 작업자',   '작업 할당·실적·결품 등록',                   'DC', 'OWN_ORG', '지시 외 SKU/수량 차단',            60,  'system'),
  ('CS_VIEWER',      'CS/조회 사용자',     '주문·배송·재고 조회',                        'HQ', 'ALL',     '수정 권한 없음',                   70,  'system'),
  ('AUDITOR',        '감사/분석 사용자',   '이력·로그·KPI 조회/다운로드',                'HQ', 'ALL',     '개인정보 마스킹',                  80,  'system');

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 본사 기준정보 담당 — 거점 3단계가 이 역할의 일이다
        ('HQ_MASTER',      'MST_PLANT',         'RCUDX'),
        ('HQ_MASTER',      'MST_WAREHOUSE',     'RCUDX'),
        ('HQ_MASTER',      'MST_LOCATION',      'RCUDX'),
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

        -- 센터 관리자 — 승인 중심. 거점은 조회만 (등록은 본사 소관).
        -- 자기 센터의 플랜트 · 창고 · 빈을 못 보면 재고 업무가 성립하지 않는다.
        ('CENTER_MGR',     'INB_APPROVE',       'RA'),
        ('CENTER_MGR',     'OUT_APPROVE',       'RA'),
        ('CENTER_MGR',     'INV_COUNT_APPROVE', 'RA'),
        ('CENTER_MGR',     'INV_ADJ_APPROVE',   'RA'),
        ('CENTER_MGR',     'OUT_ASSIGN',        'RCU'),
        ('CENTER_MGR',     'INB_CORRECTION',    'RA'),
        ('CENTER_MGR',     'MST_PLANT',         'R'),
        ('CENTER_MGR',     'MST_WAREHOUSE',     'R'),
        ('CENTER_MGR',     'MST_LOCATION',      'R'),
        ('CENTER_MGR',     'QRY_STOCK',         'R'),

        -- 입고 작업자 — 스캔 등록
        ('INBOUND_WORKER', 'INB_ARRIVE',        'RC'),
        ('INBOUND_WORKER', 'INB_INSPECT',       'RC'),
        ('INBOUND_WORKER', 'INB_PUTAWAY',       'RC'),
        ('INBOUND_WORKER', 'INB_CORRECTION',    'RC'),
        ('INBOUND_WORKER', 'MST_WAREHOUSE',      'R'),
        ('INBOUND_WORKER', 'MST_LOCATION',      'R'),

        -- 피킹/패킹 작업자
        ('PICK_PACK',      'OUT_ASSIGN',        'R'),
        ('PICK_PACK',      'OUT_PICK',          'RC'),
        ('PICK_PACK',      'OUT_PACK',          'RC'),
        ('PICK_PACK',      'OUT_SHORTAGE',      'RC'),
        ('PICK_PACK',      'MST_WAREHOUSE',      'R'),
        ('PICK_PACK',      'MST_LOCATION',      'R'),

        -- CS/조회 사용자 — 조회만
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
-- 8. 공통정책
--    perm_id 가 NULL 이면 그 역할의 전체 기능에 적용된다.
--
--    SCOPE(범위제한) 유형의 예시는 두지 않는다. 조직 범위 통제는 정책 행이
--    아니라 데이터 범위(COM-PG-004)가 서버에서 직접 수행한다. 같은 통제를
--    두 곳에 적으면 어느 쪽이 실제로 막는지 알 수 없게 된다.
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

        -- 유일한 승인한도(LIMIT) 예시. 이 유형이 없으면 승인정책 화면에
        -- "금액·수량 임계값을 넘으면 상위 승인" 이라는 규칙을 보여줄 수 없다.
        ('P007', '센터 재고조정 승인 한도', 'CENTER_MGR', 'INV_ADJ_APPROVE',
         'LIMIT', 'APPROVAL',
         'adjust.amount <= 5000000 && adjust.qty <= 1000',
         '재고조정 금액·수량',
         '재고조정 한도(500만원 / 1,000EA)를 초과했습니다. 본사 승인이 필요합니다.',
         '본사 기준정보 담당 승인 요청',
         5000000, 1000,
         'STK-009 임계값 초과 조정은 승인 필요.'),

        ('P008', '감사 다운로드 개인정보 마스킹', 'AUDITOR', 'AUD_DOWNLOAD',
         'MASKING', 'LOG',
         'mask(user.phone) && mask(user.email)',
         '연락처 · 이메일',
         '개인정보 항목은 마스킹되어 내려갑니다.',
         NULL,
         NULL, NULL,
         '마스킹 자체는 조회 계층에서 수행. 이 행은 규칙의 근거를 남긴다.'),

        ('P009', 'CS 사용자 읽기전용', 'CS_VIEWER', NULL,
         'READONLY', 'BLOCK',
         'action == "R"',
         '전체 기능',
         'CS/조회 사용자는 데이터를 변경할 수 없습니다.',
         '담당 부서에 변경 요청',
         NULL, NULL,
         'perm_id 가 NULL 이라 이 역할의 모든 기능에 적용된다.')
       ) AS v(policy_id, policy_name, role_id, perm_id, policy_type, enforce_level,
              condition_expr, target_field, message, alt_process,
              limit_amount, limit_qty, remark)
  JOIN tb_role r            ON r.role_id = v.role_id
  LEFT JOIN tb_permission p ON p.perm_id = v.perm_id;


-- ============================================================================
-- 9. 사용자
--    역할별 화면 동작과 로그인 차단 사유를 확인하기 위한 계정들이다.
--      dc2.in01 — 비밀번호 5회 오류로 잠긴 상태
--      dc2.pp01 — 사용중지 + 휴면 상태
--
--    must_change_password 는 'N' 으로 둔다. 데모 계정은 바로 로그인해서
--    화면을 보는 용도이고, 매번 변경 화면을 거치면 확인이 번거롭다.
--    운영의 admin 은 'Y' 다 (V3).
-- ============================================================================
INSERT INTO tb_user (user_id, user_name, password_hash, org_seq, email, phone,
                     dept_name, position_name, status, approval_limit, login_fail_count,
                     last_login_at, must_change_password, use_yn, created_by)
SELECT v.user_id, v.user_name,
       '$2a$10$ncjMxbbiOKNZRwCLqc/ROu.IMxzplGB6tD8vc5pzaNPcDVdMcnOkC',
       o.org_seq, v.email, v.phone,
       v.dept_name, v.position_name, v.status, v.approval_limit, v.login_fail_count,
       v.last_login_at, 'N', v.use_yn, 'system'
  FROM (VALUES
        ('hq.master', '이기준', 'HQ001', 'master@corp.co.kr',  '010-2222-3333', '상품기획팀',        '과장',   'ACTIVE',        0::bigint, 0, TIMESTAMP '2026-09-08 08:40:00', 'Y'),
        ('buyer01',   '박구매', 'HQ001', 'buyer01@corp.co.kr', '010-3333-4444', '구매팀',            '대리',   'ACTIVE',  50000000,   0, TIMESTAMP '2026-09-07 17:55:00', 'Y'),
        ('cs01',      '문상담', 'HQ001', 'cs01@corp.co.kr',    '010-2323-4545', 'CS팀',              '사원',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-08 09:31:00', 'Y'),
        ('audit01',   '노감사', 'HQ001', 'audit01@corp.co.kr', '010-3434-5656', '감사팀',            '차장',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-04 15:10:00', 'Y'),
        ('dc1.mgr',   '최센터', 'DC001', 'dc1mgr@corp.co.kr',  '010-4444-5555', '이천센터 운영팀',   '센터장', 'ACTIVE',  20000000,   0, TIMESTAMP '2026-09-08 07:30:00', 'Y'),
        ('dc1.in01',  '정입고', 'DC001', 'dc1in01@corp.co.kr', '010-5555-6666', '이천센터 입고파트', '사원',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-08 06:58:00', 'Y'),
        ('dc1.in02',  '조검수', 'DC001', 'dc1in02@corp.co.kr', '010-5656-6767', '이천센터 입고파트', '사원',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-08 06:59:00', 'Y'),
        ('dc1.pp01',  '한피킹', 'DC001', 'dc1pp01@corp.co.kr', '010-6666-7777', '이천센터 출고파트', '사원',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-08 06:55:00', 'Y'),
        ('dc2.mgr',   '오김해', 'DC002', 'dc2mgr@corp.co.kr',  '010-7777-8888', '김해센터 운영팀',   '센터장', 'ACTIVE',  20000000,   0, TIMESTAMP '2026-09-05 18:20:00', 'Y'),
        ('dc2.in01',  '남잠김', 'DC002', 'dc2in01@corp.co.kr', '010-1111-2222', '김해센터 입고파트', '사원',   'LOCKED',         0,   5, TIMESTAMP '2026-08-21 13:44:00', 'Y'),
        ('dc2.pp01',  '윤휴면', 'DC002', 'dc2pp01@corp.co.kr', '010-3333-4444', '김해센터 출고파트', '사원',   'DORMANT',        0,   0, TIMESTAMP '2026-05-19 11:02:00', 'N')
       ) AS v(user_id, user_name, org_id, email, phone, dept_name, position_name,
              status, approval_limit, login_fail_count, last_login_at, use_yn)
  JOIN tb_org o ON o.org_id = v.org_id;


-- 역할 배정 — 오김해는 센터 관리자 + 피킹/패킹 두 역할을 갖는다
INSERT INTO tb_user_role (user_seq, role_seq, created_by)
SELECT u.user_seq, r.role_seq, 'system'
  FROM (VALUES
        ('hq.master', 'HQ_MASTER'),
        ('buyer01',   'PURCHASER'),
        ('cs01',      'CS_VIEWER'),
        ('audit01',   'AUDITOR'),
        ('dc1.mgr',   'CENTER_MGR'),
        ('dc1.in01',  'INBOUND_WORKER'),
        ('dc1.in02',  'INBOUND_WORKER'),
        ('dc1.pp01',  'PICK_PACK'),
        ('dc2.mgr',   'CENTER_MGR'),
        ('dc2.mgr',   'PICK_PACK'),
        ('dc2.in01',  'INBOUND_WORKER'),
        ('dc2.pp01',  'PICK_PACK')
       ) AS v(user_id, role_id)
  JOIN tb_user u ON u.user_id = v.user_id
  JOIN tb_role r ON r.role_id = v.role_id;
