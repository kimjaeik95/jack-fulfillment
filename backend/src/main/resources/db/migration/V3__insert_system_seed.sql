-- ============================================================================
-- V3 : 시스템 필수 시드  (운영 배포에 포함)
--
-- 여기 있는 것은 "예시" 가 아니라 없으면 시스템이 성립하지 않는 데이터다.
--   권한 정의    화면 하나하나에 대응한다. 프로그램이 늘면 여기도 늘어난다.
--   메뉴         사이드바가 이 표에서 그려진다. 비면 아무 화면도 못 간다.
--   회사 · 조직  admin 이 어딘가에 속해야 한다. 한 행씩만 둔다.
--   관리자 역할  전권 역할이 없으면 첫 로그인 후 아무것도 못 한다.
--   admin 계정   자가 가입이 없는 사내 시스템이므로 최초 1명은 시드가 만든다.
--
-- 반대로 여기 없는 것 — 센터 조직 · 플랜트 · 창고 · 빈 · 나머지 역할 ·
-- 공통정책 · 나머지 사용자 — 는 전부 화면에서 등록한다. db/demo 에 예시가
-- 있지만 그건 로컬 전용이고 운영에는 들어가지 않는다.
--
-- 전에는 이 구분이 없어서 예시 데이터가 마이그레이션에 섞여 있었다. 그래서
-- 범위가 바뀔 때마다 "앞에서 넣은 예시를 지우는" 마이그레이션을 새로 써야
-- 했고, 지우면 필요한 예시가 사라져 또 복구해야 했다.
-- ============================================================================


-- ============================================================================
-- 1. 권한(기능) 정의
--    액션 문자열('RCUD')을 string_to_array(str, NULL) 로 한 글자씩 배열로
--    만들고 unnest 로 행으로 펼친다. PostgreSQL 고유 기능이다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('SYS_COMPANY',       '회사·조직 관리',       'SYS', '시스템 > 회사·조직',   10,  'system'),
  ('SYS_USER',          '사용자 관리',          'SYS', '시스템 > 사용자관리',  20,  'system'),
  ('SYS_ROLE',          '역할/권한 관리',       'SYS', '시스템 > 역할관리',    30,  'system'),
  ('SYS_MENU',          '메뉴 관리',            'SYS', '시스템 > 메뉴관리',    35,  'system'),
  ('SYS_CODE',          '공통코드 관리',        'SYS', '시스템 > 공통코드',    40,  'system'),
  ('SYS_IF',            '인터페이스 설정',      'SYS', '시스템 > 인터페이스',  50,  'system'),
  ('SYS_POLICY',        '공통정책 관리',        'SYS', '시스템 > 공통정책',    60,  'system'),
  -- 거점 3단계. 재고주소가 이 위에서 정해지므로 기준정보의 첫 묶음이다.
  ('MST_PLANT',         '플랜트 관리',          'MST', '기준정보 > 플랜트',    105, 'system'),
  ('MST_WAREHOUSE',     '창고 관리',            'MST', '기준정보 > 창고',      106, 'system'),
  ('MST_LOCATION',      '로케이션 관리',        'MST', '기준정보 > 로케이션',  107, 'system'),
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
  ('QRY_ORDER',         '주문 조회',            'QRY', '조회 > 주문',          710, 'system'),
  ('QRY_DELIVERY',      '배송 조회',            'QRY', '조회 > 배송',          720, 'system'),
  ('QRY_STOCK',         '재고 조회',            'QRY', '조회 > 재고',          730, 'system'),
  ('AUD_HISTORY',       '변경 이력 조회',       'AUD', '감사 > 이력',          810, 'system'),
  ('AUD_LOG',           '시스템 로그 조회',     'AUD', '감사 > 로그',          820, 'system'),
  ('AUD_KPI',           'KPI 조회',             'AUD', '감사 > KPI',           830, 'system'),
  ('AUD_DOWNLOAD',      '데이터 다운로드',      'AUD', '감사 > 다운로드',      840, 'system');


-- 허용 액션 — 기능이 지원하는 액션의 최대 집합.
-- X(다운로드)를 R 과 분리하는 이유는, 조회할 수 있다고 파일로 빼도 되는 것은
-- 아니기 때문이다. 파일로 나간 데이터는 회수할 수 없다 (COM-PG-011).
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('SYS_COMPANY',       'RCUDX'),
        ('SYS_USER',          'RCUDX'),
        ('SYS_ROLE',          'RCUDX'),
        ('SYS_MENU',          'RCUD'),
        ('SYS_CODE',          'RCUDX'),
        ('SYS_IF',            'RCU'),
        ('SYS_POLICY',        'RCUDX'),
        ('MST_PLANT',         'RCUDX'),
        ('MST_WAREHOUSE',     'RCUDX'),
        ('MST_LOCATION',      'RCUDX'),
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
-- 2. 회사 · 조직 — 각각 한 행
--
-- 값은 자리표시자다. 운영 첫 배포 후 회사·조직 관리 화면에서 실제 값으로
-- 바꾼다. 시드가 그럴듯한 회사명을 넣어 두면 바꾸지 않고 그대로 쓰게 된다.
-- ============================================================================
INSERT INTO tb_company (company_id, company_name, biz_reg_no, ceo_name,
                        zip_code, address, phone, sort_order, created_by) VALUES
  ('CO001', '(회사명 미입력)', NULL, NULL, NULL, NULL, NULL, 10, 'system');

INSERT INTO tb_org (company_seq, org_id, org_name, org_type, parent_seq,
                    manager_name, sort_order, created_by)
SELECT c.company_seq, 'HQ001', '본사', 'HQ', NULL, NULL, 10, 'system'
  FROM tb_company c
 WHERE c.company_id = 'CO001';


-- ============================================================================
-- 3. 관리자 역할
--
-- 이 역할만 시드에 둔다. 나머지 역할(센터 관리자 · 작업자 · 감사 등)은 조직의
-- 운영 방식에 따라 달라지므로 화면에서 만든다.
--
-- INV_QTY_EDIT(업무 수량 직접 수정)는 부여하지 않는다. 시스템 관리자가
-- 업무 수량을 직접 고칠 수 있으면 재고 정합성의 책임 소재가 사라진다.
-- ============================================================================
INSERT INTO tb_role (role_id, role_name, description, org_scope, default_data_scope,
                     restriction_summary, sort_order, created_by) VALUES
  ('SYS_ADMIN', '시스템 관리자',
   '회사·조직·거점·사용자·역할·공통코드·정책 설정',
   'HQ', 'ALL', '업무 수량 직접 수정 금지', 10, 'system');

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('SYS_ADMIN', 'SYS_COMPANY',   'RCUDX'),
        ('SYS_ADMIN', 'SYS_USER',      'RCUDX'),
        ('SYS_ADMIN', 'SYS_ROLE',      'RCUDX'),
        ('SYS_ADMIN', 'SYS_MENU',      'RCUD'),
        ('SYS_ADMIN', 'SYS_CODE',      'RCUDX'),
        ('SYS_ADMIN', 'SYS_IF',        'RCU'),
        ('SYS_ADMIN', 'SYS_POLICY',    'RCUDX'),
        -- 최초 구축 단계에서 거점을 세울 수 있어야 한다. 이후 운영은
        -- 기준정보 담당 역할에 넘긴다.
        ('SYS_ADMIN', 'MST_PLANT',     'RCUDX'),
        ('SYS_ADMIN', 'MST_WAREHOUSE', 'RCUDX'),
        ('SYS_ADMIN', 'MST_LOCATION',  'RCUDX'),
        ('SYS_ADMIN', 'AUD_LOG',       'R'),
        ('SYS_ADMIN', 'AUD_HISTORY',   'R')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);


-- ============================================================================
-- 4. 최초 관리자 계정
--
-- 사내 시스템은 자가 가입이 없다. 그래서 최초 1명은 시드가 만들어야 한다.
-- 비밀번호는 BCrypt(strength 10) 해시이고 평문은 'wms1234!' 다.
--
-- must_change_password 는 기본값 'Y' 를 그대로 둔다. 이 계정의 초기 비밀번호는
-- 저장소에 공개되어 있으므로, 첫 로그인에서 반드시 바꿔야 한다.
-- ============================================================================
INSERT INTO tb_user (user_id, user_name, password_hash, org_seq, email,
                     dept_name, position_name, status, approval_limit, created_by)
SELECT 'admin', '시스템관리자',
       '$2a$10$ncjMxbbiOKNZRwCLqc/ROu.IMxzplGB6tD8vc5pzaNPcDVdMcnOkC',
       o.org_seq, NULL,
       NULL, NULL, 'ACTIVE', 0, 'system'
  FROM tb_org o
 WHERE o.org_id = 'HQ001';

INSERT INTO tb_user_role (user_seq, role_seq, created_by)
SELECT u.user_seq, r.role_seq, 'system'
  FROM tb_user u
 CROSS JOIN tb_role r
 WHERE u.user_id = 'admin'
   AND r.role_id = 'SYS_ADMIN';


-- ============================================================================
-- 5. 메뉴
--
-- 라우트 자체(어떤 컴포넌트를 그릴지)는 코드가 소유한다. 메뉴는 "그 라우트를
-- 사이드바 어디에 어떤 이름으로 걸지" 만 정한다.
--
-- 그룹을 먼저 넣고, 항목은 그룹의 menu_id 로 되짚어 parent_seq 를 채운다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by) VALUES
  ('GRP_STATUS', '현황',          NULL, NULL, NULL, NULL, 10, 'system'),
  ('GRP_USER',   '사용자 · 조직', NULL, NULL, NULL, NULL, 20, 'system'),
  ('GRP_MASTER', '기준정보',      NULL, NULL, NULL, NULL, 30, 'system'),
  ('GRP_AUTH',   '권한',          NULL, NULL, NULL, NULL, 40, 'system'),
  ('GRP_COMMON', '공통 정책',     NULL, NULL, NULL, NULL, 50, 'system'),
  ('GRP_AUDIT',  '감사',          NULL, NULL, NULL, NULL, 60, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('SYS_DASHBOARD', '권한 현황',      'GRP_STATUS', 'dashboard',        '◎',  'SYS_ROLE',      10),
        ('SYS_USERS',     '사용자 관리',    'GRP_USER',   'users',            '👤', 'SYS_USER',      10),
        ('SYS_COMPANIES', '회사 관리',      'GRP_USER',   'companies',        '🏛', 'SYS_COMPANY',   20),
        ('SYS_ORGS',      '조직 관리',      'GRP_USER',   'orgs',             '🏢', 'SYS_COMPANY',   30),
        ('MST_PLANTS',    '플랜트 관리',    'GRP_MASTER', 'plants',           '🏭', 'MST_PLANT',     10),
        ('MST_WAREHOUSES','창고 관리',      'GRP_MASTER', 'warehouses',       '📦', 'MST_WAREHOUSE', 20),
        ('MST_LOCATIONS', '로케이션 관리',  'GRP_MASTER', 'locations',        '🧭', 'MST_LOCATION',  30),
        ('SYS_ROLES',     '역할 관리',      'GRP_AUTH',   'roles',            '🎫', 'SYS_ROLE',      10),
        ('SYS_PERMS',     '권한 관리',      'GRP_AUTH',   'permissions',      '🔑', 'SYS_ROLE',      20),
        ('SYS_ROLEPERMS', '역할-권한 매핑', 'GRP_AUTH',   'role-permissions', '▦',  'SYS_ROLE',      30),
        ('SYS_MENUS',     '메뉴 관리',      'GRP_AUTH',   'menus',            '🗂', 'SYS_MENU',      40),
        ('SYS_POLICIES',  '공통정책 관리',  'GRP_COMMON', 'policies',         '⚖',  'SYS_POLICY',    10),
        ('SYS_CODES',     '공통코드',       'GRP_COMMON', 'codes',            '☰',  'SYS_CODE',      20),
        -- 업로드 이력은 권한을 걸지 않는다. 자기가 올린 결과는 누구나 봐야 하고,
        -- 남의 것까지 보려면 변경 이력 조회 권한(AUD_HISTORY/R)이 필요하다 —
        -- 그 판정은 서비스가 한다.
        ('SYS_UPLOADS',   '업로드 이력',    'GRP_COMMON', 'uploads',          '📤', NULL,            30),
        ('SYS_AUDITLOGS', '변경 이력',      'GRP_AUDIT',  'audit-logs',       '📜', 'AUD_HISTORY',   10)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g            ON g.menu_id = v.parent_id
  LEFT JOIN tb_permission p ON p.perm_id = v.perm_id;
