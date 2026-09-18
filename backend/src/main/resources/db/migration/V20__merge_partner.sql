-- ============================================================================
-- 공급처와 고객을 거래처 하나로 합친다 (MST-PG-012, 013 → MST-PG-012)
--
-- 두 테이블이 21칸 중 16칸이 같았다. 사업자등록번호 · 담당자 · 연락처 ·
-- 결제조건 · 거래상태 — 상대가 파는 쪽이냐 사는 쪽이냐만 달랐지 '거래하는
-- 법인' 이라는 점에서는 같은 것이었다.
--
-- 합치는 진짜 이유는 <b>한 회사가 양쪽인 경우</b>다. 임가공이 그렇다 —
-- 원단을 공장에 넘기고(그 공장이 우리 고객) 완성된 옷을 받는다(그 공장이
-- 우리 공급처). 지금은 같은 회사를 두 줄로 등록해야 하고, 연락처가 바뀌면
-- 두 군데를 고쳐야 하며, 한 군데만 고치면 갈린다.
--
-- 지금이 가장 싼 시점이다. 공급처는 5개 테이블이 물고 있지만 고객은
-- 배송지 하나뿐이다. 3차 주문이 붙으면 고객 참조가 3~4개로 는다.
--
-- ----------------------------------------------------------------------------
-- 유형을 값 하나(SUPPLIER/CUSTOMER/BOTH)로 두지 않고 플래그 둘로 나눈 이유
--
--   supplier_yn · customer_yn
--
-- FK 로 유형을 막기 위해서다. 'BOTH' 를 값으로 두면 '공급처 또는 양쪽' 을
-- 참조 무결성으로 표현할 수 없다 — PostgreSQL 의 FK 는 부분 유니크 인덱스를
-- 참조하지 못한다. 플래그로 두면 (partner_seq, supplier_yn) 유니크를 걸고
-- 참조하는 쪽이 supplier_yn='Y' 를 고정으로 가리키게 만들 수 있다.
--
-- 그러면 <b>발주서에 고객을 넣는 일이 DB 에서 거부된다.</b> 앱 검증에
-- 맡기지 않는다 — 지금 분리된 두 테이블이 주던 보장을 그대로 유지한다.
--
-- 덤으로 화면과도 맞는다. 배지 두 개(공급처 · 고객)를 다는 것이 곧 플래그
-- 둘이고, '양쪽' 이라는 새 단어를 사용자가 배울 필요가 없다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. tb_supplier 를 tb_partner 로 바꾼다
--
-- 새 테이블을 만들어 옮기지 않고 이름을 바꾼다. 공급처를 참조하는 FK 가
-- 5개인데, RENAME 은 그 FK 들을 그대로 따라오게 한다. 새로 만들면 5개를
-- 전부 끊었다 다시 이어야 하고, 그 사이에 데이터가 비는 창이 생긴다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_supplier RENAME TO tb_partner;

ALTER TABLE tb_partner RENAME COLUMN supplier_seq  TO partner_seq;
ALTER TABLE tb_partner RENAME COLUMN supplier_id   TO partner_id;
ALTER TABLE tb_partner RENAME COLUMN supplier_name TO partner_name;

ALTER TABLE tb_partner RENAME CONSTRAINT pk_supplier        TO pk_partner;
ALTER TABLE tb_partner RENAME CONSTRAINT uk_supplier_id     TO uk_partner_id;
ALTER TABLE tb_partner RENAME CONSTRAINT ck_supplier_use_yn TO ck_partner_use_yn;

-- ----------------------------------------------------------------------------
-- 2. 거래 방향 플래그
--
-- 기존 행은 전부 공급처였다. 최소 하나는 켜져 있어야 한다 — 둘 다 꺼진
-- 거래처는 어디에도 못 쓰는 행이라 만들 이유가 없다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_partner
    ADD COLUMN supplier_yn char(1) NOT NULL DEFAULT 'N',
    ADD COLUMN customer_yn char(1) NOT NULL DEFAULT 'N';

UPDATE tb_partner SET supplier_yn = 'Y';

ALTER TABLE tb_partner
    ADD CONSTRAINT ck_partner_supplier_yn CHECK (supplier_yn IN ('Y', 'N')),
    ADD CONSTRAINT ck_partner_customer_yn CHECK (customer_yn IN ('Y', 'N')),
    ADD CONSTRAINT ck_partner_direction
        CHECK (supplier_yn = 'Y' OR customer_yn = 'Y');

-- FK 가 참조할 유니크. 이것이 있어야 '공급처인 거래처만' 을 DB 가 지킬 수 있다.
ALTER TABLE tb_partner
    ADD CONSTRAINT uk_partner_supplier UNIQUE (partner_seq, supplier_yn),
    ADD CONSTRAINT uk_partner_customer UNIQUE (partner_seq, customer_yn);

-- ----------------------------------------------------------------------------
-- 3. 고객을 거래처로 옮긴다
--
-- customer_type(B2B/B2C)은 버린다. 개인은 이제 거래처로 등록하지 않는다 —
-- 전화 주문이든 오픈마켓이든 주문에 받는 사람만 적고 거래처는 비운다.
-- 그러면 이 테이블에는 조직만 남아 유형 칸이 의미를 잃는다.
--
-- 공급처에만 있던 칸(대표자 · 주소 · 초과입고 허용률)은 비워 둔다.
-- 고객에게는 해당이 없고, 화면이 유형에 따라 감춘다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_partner (
    partner_id, partner_name, biz_reg_no, manager_name, phone, email,
    status, pay_term, remark, sort_order, use_yn,
    supplier_yn, customer_yn, created_by, created_at, updated_by, updated_at
)
SELECT c.customer_id, c.customer_name, c.biz_reg_no, c.manager_name, c.phone, c.email,
       c.status, c.pay_term, c.remark, c.sort_order, c.use_yn,
       'N', 'Y', c.created_by, c.created_at, c.updated_by, c.updated_at
  FROM tb_customer c;

-- ----------------------------------------------------------------------------
-- 4. 배송지 — 고객 전용에서 거래처 공용으로
--
-- 공급처에도 필요하다. 반품을 보낼 곳이 사업장 주소와 다른 경우가 흔하다
-- (본사는 서울, 공장은 지방). 용도를 addr_type 으로 가른다.
--
-- 사업장 주소(tb_partner.zip_code · address)는 칼럼으로 남긴다. 성격이
-- 다르다 — 그것은 회사의 법적 속성이고 세금계산서에 찍히며 하나뿐이다.
-- 여기 있는 것은 거래할 때 쓰는 곳이고 여럿이며 자주 바뀐다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_customer_address RENAME TO tb_partner_address;
ALTER TABLE tb_partner_address RENAME COLUMN customer_seq TO partner_seq;

ALTER TABLE tb_partner_address RENAME CONSTRAINT pk_customer_address TO pk_partner_address;
ALTER TABLE tb_partner_address RENAME CONSTRAINT ck_cusaddr_default  TO ck_partaddr_default;
ALTER TABLE tb_partner_address RENAME CONSTRAINT ck_cusaddr_use_yn   TO ck_partaddr_use_yn;

-- 옛 FK 는 tb_customer 를 가리킨다. 끊고 거래처로 다시 잇는다.
ALTER TABLE tb_partner_address DROP CONSTRAINT fk_cusaddr_customer;

-- 고객 seq → 거래처 seq. 코드로 짝을 찾는다 (seq 는 새로 채번되었다).
UPDATE tb_partner_address a
   SET partner_seq = p.partner_seq
  FROM tb_customer c
  JOIN tb_partner p ON p.partner_id = c.customer_id
 WHERE a.partner_seq = c.customer_seq;

ALTER TABLE tb_partner_address
    ADD CONSTRAINT fk_partaddr_partner FOREIGN KEY (partner_seq)
        REFERENCES tb_partner (partner_seq);

ALTER TABLE tb_partner_address
    ADD COLUMN addr_type varchar(20) NOT NULL DEFAULT 'SHIP';

ALTER TABLE tb_partner_address
    ADD CONSTRAINT ck_partaddr_type CHECK (addr_type IN ('SHIP', 'RETURN', 'ETC'));

-- ----------------------------------------------------------------------------
-- 5. 옛 고객 테이블 제거
-- ----------------------------------------------------------------------------
DROP TABLE tb_customer;

-- ----------------------------------------------------------------------------
-- 6. 공급처를 참조하는 곳이 '공급처인 거래처' 만 가리키게 한다
--
-- 생성칼럼으로 'Y' 를 고정해 두고 (partner_seq, 'Y') 를 참조한다. 고객
-- 전용 거래처는 supplier_yn='N' 이라 이 조합이 없어 INSERT 가 거부된다.
--
-- 재고 · 실사는 화주(vendor)를 가리키므로 같은 규칙을 쓴다. 위탁 재고의
-- 화주는 우리에게 물건을 대는 쪽이다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_purchase_order
    ADD COLUMN supplier_chk char(1) GENERATED ALWAYS AS ('Y') STORED;
ALTER TABLE tb_purchase_order DROP CONSTRAINT fk_purord_supplier;
ALTER TABLE tb_purchase_order
    ADD CONSTRAINT fk_purord_supplier FOREIGN KEY (supplier_seq, supplier_chk)
        REFERENCES tb_partner (partner_seq, supplier_yn);

ALTER TABLE tb_inbound
    ADD COLUMN supplier_chk char(1) GENERATED ALWAYS AS ('Y') STORED;
ALTER TABLE tb_inbound DROP CONSTRAINT fk_inbound_supplier;
ALTER TABLE tb_inbound
    ADD CONSTRAINT fk_inbound_supplier FOREIGN KEY (supplier_seq, supplier_chk)
        REFERENCES tb_partner (partner_seq, supplier_yn);

ALTER TABLE tb_purchase_request_line
    ADD COLUMN supplier_chk char(1) GENERATED ALWAYS AS ('Y') STORED;
ALTER TABLE tb_purchase_request_line DROP CONSTRAINT fk_purreql_sup;
ALTER TABLE tb_purchase_request_line
    ADD CONSTRAINT fk_purreql_sup FOREIGN KEY (pref_supplier_seq, supplier_chk)
        REFERENCES tb_partner (partner_seq, supplier_yn);

ALTER TABLE tb_stock
    ADD COLUMN vendor_chk char(1) GENERATED ALWAYS AS ('Y') STORED;
ALTER TABLE tb_stock DROP CONSTRAINT fk_stock_vendor;
ALTER TABLE tb_stock
    ADD CONSTRAINT fk_stock_vendor FOREIGN KEY (vendor_seq, vendor_chk)
        REFERENCES tb_partner (partner_seq, supplier_yn);

ALTER TABLE tb_stocktake_line
    ADD COLUMN vendor_chk char(1) GENERATED ALWAYS AS ('Y') STORED;
ALTER TABLE tb_stocktake_line DROP CONSTRAINT fk_takel_vendor;
ALTER TABLE tb_stocktake_line
    ADD CONSTRAINT fk_takel_vendor FOREIGN KEY (vendor_seq, vendor_chk)
        REFERENCES tb_partner (partner_seq, supplier_yn);

-- ----------------------------------------------------------------------------
-- 7. 공통코드 — 주소 용도를 추가하고, 고객유형을 거둔다
-- ----------------------------------------------------------------------------
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('ADDR_TYPE', '주소 용도', '배송지 · 반품지 · 기타', 'SYSTEM', 'v20.partner');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'v20.partner'
  FROM (VALUES
        ('SHIP',   '배송지', '물건을 보낼 곳',   'blue', 10),
        ('RETURN', '반품지', '반품을 받을 곳',   'amber', 20),
        ('ETC',    '기타',   '그 밖의 주소',     'gray',  30)
       ) AS v(code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'ADDR_TYPE';

-- 고객유형(B2B/B2C)은 쓰지 않는다. 개인은 거래처로 등록하지 않기로 했다.
DELETE FROM tb_code
 WHERE code_group_seq = (SELECT code_group_seq FROM tb_code_group WHERE code_group_id = 'CUSTOMER_TYPE');
DELETE FROM tb_code_group WHERE code_group_id = 'CUSTOMER_TYPE';

-- ----------------------------------------------------------------------------
-- 8. 권한 — MST_SUPPLIER + MST_CUSTOMER 를 MST_PARTNER 로
--
-- 역할 매핑은 두 권한의 액션을 합집합으로 옮긴다. 구매담당(PURCHASER)이
-- 고객까지 고칠 수 있게 되는데, 그것이 걸리면 공통정책으로 조인다 —
-- 정책 엔진이 이미 있어 코드 변경 없이 걸 수 있다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
SELECT 'MST_PARTNER', '거래처 관리', p.module_code, p.menu_path, p.sort_order, 'v20.partner'
  FROM tb_permission p WHERE p.perm_id = 'MST_SUPPLIER';

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT n.perm_seq, a.action_code, 'v20.partner'
  FROM tb_permission n
  CROSS JOIN (SELECT DISTINCT pa.action_code
                FROM tb_permission_action pa
                JOIN tb_permission p ON p.perm_seq = pa.perm_seq
               WHERE p.perm_id IN ('MST_SUPPLIER', 'MST_CUSTOMER')) a
 WHERE n.perm_id = 'MST_PARTNER';

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT DISTINCT rp.role_seq, n.perm_seq, rp.action_code, rp.data_scope, 'v20.partner'
  FROM tb_role_permission rp
  JOIN tb_permission p ON p.perm_seq = rp.perm_seq
  CROSS JOIN tb_permission n
 WHERE p.perm_id IN ('MST_SUPPLIER', 'MST_CUSTOMER')
   AND n.perm_id = 'MST_PARTNER';

-- ----------------------------------------------------------------------------
-- 9. 메뉴 — 두 화면을 하나로
--
-- 거래처 묶음(V19) 아래에 거래처 관리 하나만 남는다. 하위가 하나뿐인
-- 머리글은 사이드바가 머리글 없이 그 한 줄로 그린다.
-- ----------------------------------------------------------------------------
UPDATE tb_menu
   SET menu_id    = 'MST_PARTNERS',
       menu_name  = '거래처 관리',
       route_name = 'partners',
       perm_seq   = (SELECT perm_seq FROM tb_permission WHERE perm_id = 'MST_PARTNER'),
       sort_order = 10,
       updated_by = 'v20.partner',
       updated_at = CURRENT_TIMESTAMP
 WHERE menu_id = 'MST_SUPPLIERS';

DELETE FROM tb_menu WHERE menu_id = 'MST_CUSTOMERS';

-- 옛 권한 정리. 역할 매핑 → 액션 → 권한 순서로 내려간다.
DELETE FROM tb_role_permission
 WHERE perm_seq IN (SELECT perm_seq FROM tb_permission WHERE perm_id IN ('MST_SUPPLIER', 'MST_CUSTOMER'));
DELETE FROM tb_permission_action
 WHERE perm_seq IN (SELECT perm_seq FROM tb_permission WHERE perm_id IN ('MST_SUPPLIER', 'MST_CUSTOMER'));
DELETE FROM tb_permission WHERE perm_id IN ('MST_SUPPLIER', 'MST_CUSTOMER');

-- ============================================================================
-- 확인
--
--   SELECT partner_id, partner_name, supplier_yn, customer_yn FROM tb_partner ORDER BY 1;
--     → 공급처 4건(Y/N) + 고객 4건(N/Y) = 8건
--   SELECT count(*) FROM tb_partner_address;          → 5건, 전부 SHIP
--   SELECT perm_id FROM tb_permission WHERE perm_id LIKE 'MST_%PART%';  → MST_PARTNER
-- ============================================================================
