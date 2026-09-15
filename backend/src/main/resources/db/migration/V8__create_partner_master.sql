-- ============================================================================
-- 거래처 기준정보 — 공급처 · 고객 · 고객배송지 · 사유코드
--   MST-PG-012  공급처 관리      (요구사항 MST-010)
--   MST-PG-013  고객·배송지 관리 (요구사항 MST-010)
--   MST-PG-014  사유코드 관리    (요구사항 MST-012)
--
-- 요구사항 5장 데이터 요구사항이 정의한 속성을 그대로 따른다.
--   공급처   코드 · 공급처명 · 사업자등록번호 · 담당자 · 주소 · 거래상태 · 결제조건
--   고객     고객유형(B2B/B2C) · 고객명 · 연락처 · 상태
--   고객배송지 배송지명 · 수령인 · 주소 · 기본배송지 여부
--   사유코드  코드그룹 · 코드 · 코드명 · 사용상태 · 정렬순서
--
-- 사유코드는 테이블을 만들지 않는다. 데이터 요구사항이 '사유코드 / 공통코드'
-- 를 한 행으로 묶고 속성도 같게 적었다 — 이미 있는 tb_code_group · tb_code 가
-- 그 구조다. 새 테이블을 만들면 같은 모양이 두 벌이 되고, 코드 조회 코드도
-- 두 벌이 된다.
--
-- 대신 화면과 권한은 가른다. 공통코드에는 DATA_SCOPE · PERM_ACTION ·
-- POLICY_TYPE 처럼 건드리면 권한 판정이 깨지는 것들이 있다. 현장이 결품 사유
-- 하나를 추가하려고 그 화면에 들어가야 한다면, 같은 화면에서 데이터 범위
-- 코드도 지울 수 있게 된다. 그래서 group_kind 로 나눈다.
-- ============================================================================


-- ============================================================================
-- 1. 공급처 (MST-010)
--
-- 구매오더(5차)와 입고(6차)가 이 테이블을 가리킨다. 거래중지된 공급처로는
-- 신규 발주를 낼 수 없어야 하는데(MST-010), 그 판정은 구매 기능이 생길 때
-- 이 status 를 본다.
--
-- 사업자등록번호는 유일제약을 걸지 않는다. 요구사항이 '중복 경고' 라고
-- 했지 금지라고 하지 않았다 — 같은 사업자가 사업부별로 코드를 따로 쓰는
-- 경우가 실제로 있다. 서비스가 경고만 한다.
-- ============================================================================
CREATE TABLE tb_supplier (
    supplier_seq   bigint       GENERATED ALWAYS AS IDENTITY,
    supplier_id    varchar(30)  NOT NULL,
    supplier_name  varchar(100) NOT NULL,
    biz_reg_no     varchar(20),
    ceo_name       varchar(50),
    manager_name   varchar(50),
    phone          varchar(30),
    email          varchar(100),
    zip_code       varchar(10),
    address        varchar(200),
    -- 코드그룹 PARTNER_STATUS (ACTIVE/SUSPENDED/CLOSED)
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    -- 코드그룹 PAY_TERM (PREPAID/COD/NET30/NET60/MONTHLY)
    pay_term       varchar(20),
    -- 초과입고 허용 오차율 (INB-005). 공급처마다 다르게 잡는다.
    over_receipt_rate numeric(5,2) NOT NULL DEFAULT 0,
    remark         varchar(300),
    sort_order     integer      NOT NULL DEFAULT 0,
    use_yn         char(1)      NOT NULL DEFAULT 'Y',
    created_by     varchar(30)  NOT NULL,
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,
    CONSTRAINT pk_supplier        PRIMARY KEY (supplier_seq),
    CONSTRAINT uk_supplier_id     UNIQUE (supplier_id),
    CONSTRAINT ck_supplier_use_yn CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_supplier_rate   CHECK (over_receipt_rate >= 0 AND over_receipt_rate <= 100)
);

CREATE INDEX ix_supplier_name   ON tb_supplier (supplier_name);
CREATE INDEX ix_supplier_status ON tb_supplier (status, sort_order);


-- ============================================================================
-- 2. 고객 (MST-010)
--
-- 센터가 직접 파는 상대다. 채널 주문의 수령인과는 다르다 — 채널 주문은
-- 주문 테이블(7차)이 배송지를 직접 들고 있고, 여기 고객은 판매오더(11차)가
-- 가리킨다.
--
-- B2B 는 사업자등록번호가 있고 B2C 는 없다. 그래서 NULL 을 허용하고,
-- 유형과의 정합성은 서비스가 본다.
-- ============================================================================
CREATE TABLE tb_customer (
    customer_seq   bigint       GENERATED ALWAYS AS IDENTITY,
    customer_id    varchar(30)  NOT NULL,
    customer_name  varchar(100) NOT NULL,
    -- 코드그룹 CUSTOMER_TYPE (B2B/B2C)
    customer_type  varchar(20)  NOT NULL,
    biz_reg_no     varchar(20),
    manager_name   varchar(50),
    phone          varchar(30),
    email          varchar(100),
    -- 코드그룹 PARTNER_STATUS — 공급처와 같은 값을 쓴다
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    pay_term       varchar(20),
    remark         varchar(300),
    sort_order     integer      NOT NULL DEFAULT 0,
    use_yn         char(1)      NOT NULL DEFAULT 'Y',
    created_by     varchar(30)  NOT NULL,
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,
    CONSTRAINT pk_customer        PRIMARY KEY (customer_seq),
    CONSTRAINT uk_customer_id     UNIQUE (customer_id),
    CONSTRAINT ck_customer_use_yn CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_customer_name ON tb_customer (customer_name);
CREATE INDEX ix_customer_type ON tb_customer (customer_type, sort_order);


-- ============================================================================
-- 3. 고객 배송지 (MST-010)
--
-- 고객 하나에 배송지 여럿. 판매오더(11차)가 배송지를 FK 로 가리킨다.
--
-- '고객당 기본 배송지 1건' 을 부분 유니크 인덱스로 강제한다. 애플리케이션만
-- 믿으면 동시에 두 요청이 들어왔을 때 둘 다 통과한다. NULL 이 아니라 'N' 을
-- 쓰므로 조건을 명시한다.
-- ============================================================================
CREATE TABLE tb_customer_address (
    address_seq    bigint       GENERATED ALWAYS AS IDENTITY,
    customer_seq   bigint       NOT NULL,
    address_name   varchar(100) NOT NULL,
    receiver_name  varchar(50)  NOT NULL,
    phone          varchar(30),
    zip_code       varchar(10),
    address        varchar(200) NOT NULL,
    address_detail varchar(200),
    -- 배송 요청사항 기본값. 주문마다 덮어쓸 수 있다.
    delivery_memo  varchar(200),
    default_yn     char(1)      NOT NULL DEFAULT 'N',
    sort_order     integer      NOT NULL DEFAULT 0,
    use_yn         char(1)      NOT NULL DEFAULT 'Y',
    created_by     varchar(30)  NOT NULL,
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,
    CONSTRAINT pk_customer_address     PRIMARY KEY (address_seq),
    CONSTRAINT fk_cusaddr_customer     FOREIGN KEY (customer_seq)
        REFERENCES tb_customer (customer_seq),
    CONSTRAINT ck_cusaddr_default      CHECK (default_yn IN ('Y', 'N')),
    CONSTRAINT ck_cusaddr_use_yn       CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_cusaddr_customer ON tb_customer_address (customer_seq, sort_order);

-- 고객당 기본 배송지는 하나 (MST-010)
CREATE UNIQUE INDEX ux_cusaddr_default
    ON tb_customer_address (customer_seq)
 WHERE default_yn = 'Y';


-- ============================================================================
-- 4. 코드그룹 구분 — 누가 관리하는 코드인가
--
--   SYSTEM  시스템이 동작하는 데 쓰는 값. 바꾸면 권한 판정 · 상태 전이가
--           깨진다. 시스템 관리자만 다룬다 (공통코드 화면).
--   REASON  업무가 예외를 설명하는 값. 현장이 새 실패 유형을 발견하면
--           늘어난다. 기준정보 담당이 다룬다 (사유코드 화면).
--
-- 코드 자체의 구조는 같으므로 테이블을 나누지 않고 이 한 컬럼으로 가른다.
-- 화면이 각자 자기 구분만 조회한다.
-- ============================================================================
ALTER TABLE tb_code_group
    ADD COLUMN group_kind varchar(20) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE tb_code_group
    ADD CONSTRAINT ck_code_group_kind CHECK (group_kind IN ('SYSTEM', 'REASON'));

CREATE INDEX ix_code_group_kind ON tb_code_group (group_kind, code_group_id);


-- ============================================================================
-- 5. 공통코드 — 거래처와 사유코드
--
-- 사유코드 그룹은 요구사항 MST-012 가 열거한 여섯 가지다.
--   취소 · 반품 · 검수불량 · 결품 · 조정 · 배송실패
--
-- 각 그룹의 코드는 업무가 정하는 것이라 화면에서 추가할 수 있다. 여기서는
-- 요구사항이 예로 든 것과 각 예외 처리에서 반드시 필요한 최소만 넣는다.
-- ============================================================================
-- 거래처 상태 · 결제조건 · 고객유형은 SYSTEM 이다. 서버가 이 값으로
-- 분기하므로(거래중지면 발주 차단) 업무가 임의로 늘릴 수 없다.
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by) VALUES
  ('PARTNER_STATUS', '거래처 상태',   '거래중 · 거래중지 · 거래종료',           'SYSTEM', 'system'),
  ('PAY_TERM',       '결제조건',      '선결제 · 착불 · 30일 · 60일 · 월말결산', 'SYSTEM', 'system'),
  ('CUSTOMER_TYPE',  '고객 유형',     'B2B · B2C',                              'SYSTEM', 'system'),
  -- 사유는 업무가 늘린다. 현장이 새 실패 유형을 발견하면 코드가 생긴다.
  ('REASON_CANCEL',  '취소 사유',     '주문 취소 (ORD-008)',                    'REASON', 'system'),
  ('REASON_RETURN',  '반품 사유',     '반품 접수',                              'REASON', 'system'),
  ('REASON_INSPECT', '검수불량 사유', '입고 거부 (INB-006)',                    'REASON', 'system'),
  ('REASON_SHORT',   '결품 사유',     '피킹 결품 (OUT-007)',                    'REASON', 'system'),
  ('REASON_ADJUST',  '재고조정 사유', '실사 차이 · 분실 · 파손 (STK-009)',      'REASON', 'system'),
  ('REASON_DLV_FAIL','배송실패 사유', '주소불명 · 부재 (DLV-006)',              'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        -- 거래처 상태
        ('PARTNER_STATUS', 'ACTIVE',    '거래중',     '신규 거래 가능',                      10),
        ('PARTNER_STATUS', 'SUSPENDED', '거래중지',   '신규 거래 차단. 기존 건은 마무리 가능', 20),
        ('PARTNER_STATUS', 'CLOSED',    '거래종료',   '거래 관계 종료',                      30),
        -- 결제조건
        ('PAY_TERM', 'PREPAID', '선결제',     '발주 시 선지급',       10),
        ('PAY_TERM', 'COD',     '착불',       '입고 시 지급',         20),
        ('PAY_TERM', 'NET30',   '30일',       '세금계산서 후 30일',   30),
        ('PAY_TERM', 'NET60',   '60일',       '세금계산서 후 60일',   40),
        ('PAY_TERM', 'MONTHLY', '월말결산',   '당월 마감 익월 지급',  50),
        -- 고객 유형
        ('CUSTOMER_TYPE', 'B2B', '기업',   '사업자. 사업자등록번호 필요', 10),
        ('CUSTOMER_TYPE', 'B2C', '개인',   '개인 소비자',                 20),
        -- 취소 사유 (ORD-008)
        ('REASON_CANCEL', 'CUST_CHANGE', '고객 변심',     NULL, 10),
        ('REASON_CANCEL', 'OUT_OF_STOCK','재고 부족',     NULL, 20),
        ('REASON_CANCEL', 'WRONG_ORDER', '주문 오류',     NULL, 30),
        ('REASON_CANCEL', 'DUPLICATE',   '중복 주문',     NULL, 40),
        -- 반품 사유
        ('REASON_RETURN', 'CUST_CHANGE', '단순 변심',     NULL, 10),
        ('REASON_RETURN', 'DEFECT',      '상품 불량',     NULL, 20),
        ('REASON_RETURN', 'WRONG_ITEM',  '오배송',        NULL, 30),
        ('REASON_RETURN', 'DAMAGED',     '배송 중 파손',  NULL, 40),
        -- 검수불량 사유 (INB-006)
        ('REASON_INSPECT', 'DAMAGED',    '파손',          NULL, 10),
        ('REASON_INSPECT', 'SHORTAGE',   '수량 부족',     NULL, 20),
        ('REASON_INSPECT', 'WRONG_ITEM', '오품',          NULL, 30),
        ('REASON_INSPECT', 'QUALITY',    '품질 미달',     NULL, 40),
        -- 결품 사유 (OUT-007)
        ('REASON_SHORT', 'NOT_FOUND',  '현품 없음',       NULL, 10),
        ('REASON_SHORT', 'DAMAGED',    '현품 파손',       NULL, 20),
        ('REASON_SHORT', 'LOC_ERROR',  '위치 오류',       NULL, 30),
        -- 재고조정 사유 (STK-009)
        ('REASON_ADJUST', 'STOCKTAKE', '실사 차이',       NULL, 10),
        ('REASON_ADJUST', 'LOST',      '분실',            NULL, 20),
        ('REASON_ADJUST', 'BROKEN',    '파손',            NULL, 30),
        ('REASON_ADJUST', 'SYS_FIX',   '전산 오류 정정',  NULL, 40),
        -- 배송실패 사유 (DLV-006)
        ('REASON_DLV_FAIL', 'BAD_ADDRESS', '주소 불명',   NULL, 10),
        ('REASON_DLV_FAIL', 'ABSENT',      '수령인 부재', NULL, 20),
        ('REASON_DLV_FAIL', 'REFUSED',     '수취 거부',   NULL, 30)
       ) AS v(group_id, code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 6. 권한
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('MST_SUPPLIER', '공급처 관리',     'MST', '기준정보 > 공급처',     140, 'system'),
  ('MST_CUSTOMER', '고객·배송지 관리', 'MST', '기준정보 > 고객',      150, 'system'),
  ('MST_REASON',   '사유코드 관리',   'MST', '기준정보 > 사유코드',   160, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('MST_SUPPLIER', 'RCUDX'),
        ('MST_CUSTOMER', 'RCUDX'),
        ('MST_REASON',   'RCUD')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 시스템 관리자는 조회만 한다 (정책 P001 취지). 등록 · 수정은 기준정보 담당.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'SYS_ADMIN'
   AND p.perm_id IN ('MST_SUPPLIER', 'MST_CUSTOMER', 'MST_REASON');


-- ============================================================================
-- 7. 메뉴
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('MST_SUPPLIERS', '공급처 관리',      'GRP_MASTER', 'suppliers', '🚚', 'MST_SUPPLIER', 100),
        ('MST_CUSTOMERS', '고객·배송지 관리', 'GRP_MASTER', 'customers', '🧑', 'MST_CUSTOMER', 110),
        ('MST_REASONS',   '사유코드 관리',    'GRP_MASTER', 'reasons',   '🏷', 'MST_REASON',   120)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
