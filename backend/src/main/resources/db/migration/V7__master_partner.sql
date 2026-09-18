-- ============================================================================
-- V5 : 거래처 — 공급처와 고객을 한 테이블에
--   MST-PG-012  거래처 관리 (요구사항 MST-010)
--
-- 공급처와 고객을 나누지 않는다.
--
-- 둘은 컬럼 대부분이 겹치는 같은 모양의 테이블이었다. 그리고 한 회사가 양쪽인
-- 경우가 실제로 있다 — 임가공 업체는 우리에게 옷을 대 주면서 우리 원단을 사
-- 간다. 나눠 두면 그 회사를 두 번 등록해야 하고, 사업자등록번호가 같은 두
-- 행이 서로 다른 담당자 · 주소를 갖게 된다.
--
-- 방향은 플래그 둘로 구분한다.
--   supplier_yn   우리가 물건을 사 오는 상대인가
--   customer_yn   우리가 물건을 파는 상대인가
--
-- 'BOTH' 같은 유형값을 두지 않은 이유가 있다. 유형 하나로 두면 '공급처로 쓸
-- 수 있는 거래처' 를 FK 로 강제할 수 없다 — PostgreSQL 의 FK 는 부분 유니크
-- 인덱스를 참조하지 못하기 때문이다. 플래그로 나누면 (seq, 'Y') 조합에
-- 유니크를 걸 수 있고, 가리키는 쪽이 그 조합을 FK 로 물면 유형이 강제된다.
-- 발주 · 입고 · 재고 · 실사가 그 방식으로 공급처만 가리킨다.
--
-- B2C 는 이 테이블에 들어오지 않는다. 오픈마켓 주문은 수령인 정보를 주문이
-- 스냅샷으로 들고 가고 거래처는 비운다 — 주문 하나마다 회사를 만들 이유가
-- 없고, 전화로 들어온 단체주문도 마찬가지다.
--
-- 사업자등록번호에 유일제약을 걸지 않는다. 요구사항이 '중복 경고' 라고 했지
-- 금지라고 하지 않았다 — 같은 사업자가 사업부별로 코드를 따로 쓰는 경우가
-- 실제로 있다. 서비스가 경고만 한다.
--
-- 사유코드는 테이블을 만들지 않는다. 데이터 요구사항이 '사유코드 / 공통코드'
-- 를 한 행으로 묶고 속성도 같게 적었다 — 이미 있는 tb_code_group · tb_code 가
-- 그 구조다. 대신 화면과 권한은 group_kind 로 가른다 (V1).
-- ============================================================================

CREATE TABLE tb_partner (
    partner_seq    bigint       GENERATED ALWAYS AS IDENTITY,
    partner_id     varchar(30)  NOT NULL,
    partner_name   varchar(100) NOT NULL,
    biz_reg_no     varchar(20),
    ceo_name       varchar(50),
    manager_name   varchar(50),
    phone          varchar(30),
    email          varchar(100),
    zip_code       varchar(10),
    address        varchar(200),
    -- 코드그룹 PARTNER_STATUS (ACTIVE/SUSPENDED/CLOSED).
    -- 거래중이 아니면 신규 발주 · 판매오더를 낼 수 없다 (MST-010).
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

    -- 거래 방향. 둘 다 켤 수 있다 (임가공).
    supplier_yn    char(1)      NOT NULL DEFAULT 'N',
    customer_yn    char(1)      NOT NULL DEFAULT 'N',

    CONSTRAINT pk_partner            PRIMARY KEY (partner_seq),
    CONSTRAINT uk_partner_id         UNIQUE (partner_id),
    CONSTRAINT ck_partner_use_yn     CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_supplier_rate      CHECK (over_receipt_rate >= 0 AND over_receipt_rate <= 100),
    CONSTRAINT ck_partner_supplier_yn CHECK (supplier_yn IN ('Y', 'N')),
    CONSTRAINT ck_partner_customer_yn CHECK (customer_yn IN ('Y', 'N')),
    -- 어느 쪽도 아닌 거래처는 만들 수 없다. 방향이 없으면 어느 화면에서도
    -- 고를 수 없어 등록한 사람조차 다시 찾지 못한다.
    CONSTRAINT ck_partner_direction  CHECK (supplier_yn = 'Y' OR customer_yn = 'Y')
);

/*
 * 유형을 FK 로 강제하기 위한 유니크.
 *
 * partner_seq 가 이미 PK 라 이 조합도 당연히 유일하다. 그런데도 선언하는
 * 이유는, 가리키는 쪽이 (seq, 'Y') 를 복합 FK 로 물려면 같은 모양의 유니크가
 * 있어야 하기 때문이다. 가리키는 쪽은 'Y' 고정값 컬럼(*_chk)을 두고 이 유니크를
 * 참조한다 — 그러면 공급처가 아닌 거래처를 발주에 넣는 순간 INSERT 가 실패한다.
 */
ALTER TABLE tb_partner
    ADD CONSTRAINT uk_partner_supplier UNIQUE (partner_seq, supplier_yn),
    ADD CONSTRAINT uk_partner_customer UNIQUE (partner_seq, customer_yn);

CREATE INDEX ix_partner_name   ON tb_partner (partner_name);
CREATE INDEX ix_partner_status ON tb_partner (status, sort_order);

COMMENT ON TABLE  tb_partner                   IS '거래처 — 공급처 · 고객 통합. MST-010';
COMMENT ON COLUMN tb_partner.partner_seq       IS '거래처 순번 (PK)';
COMMENT ON COLUMN tb_partner.partner_id        IS '거래처코드 (예: SUP-001, CUS-001)';
COMMENT ON COLUMN tb_partner.supplier_yn       IS '공급처로 쓰는가 — 발주 · 입고 · 재고가 이 값이 Y 인 것만 가리킨다';
COMMENT ON COLUMN tb_partner.customer_yn       IS '고객으로 쓰는가 — 판매오더가 이 값이 Y 인 것만 가리킨다';
COMMENT ON COLUMN tb_partner.status            IS '거래상태 — 코드그룹 PARTNER_STATUS (ACTIVE/SUSPENDED/CLOSED)';
COMMENT ON COLUMN tb_partner.pay_term          IS '결제조건 — 코드그룹 PAY_TERM';
COMMENT ON COLUMN tb_partner.over_receipt_rate IS '초과입고 허용 오차율 % (INB-005)';
COMMENT ON COLUMN tb_partner.biz_reg_no        IS '사업자등록번호. 중복을 막지 않고 경고만 한다';


-- ============================================================================
-- 거래처 주소 — 배송지 · 반품지
--
-- 거래처 하나에 주소 여럿. 판매오더(11차)가 배송지를 FK 로 가리킨다.
--
-- 용도를 addr_type 으로 가른다. 공급처에는 반품지가 필요하고 고객에는 배송지가
-- 필요한데, 둘을 한 테이블에 담으면서 용도를 구분하지 않으면 발주 반품 화면에
-- 고객 배송지가 섞여 나온다.
--
-- '거래처 · 용도당 기본 주소 1건' 을 부분 유니크 인덱스로 강제한다. 애플리케이션만
-- 믿으면 동시에 두 요청이 들어왔을 때 둘 다 통과한다.
-- ============================================================================
CREATE TABLE tb_partner_address (
    address_seq    bigint       GENERATED ALWAYS AS IDENTITY,
    partner_seq    bigint       NOT NULL,
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
    -- 코드그룹 ADDR_TYPE (SHIP/RETURN/ETC)
    addr_type      varchar(20)  NOT NULL DEFAULT 'SHIP',

    CONSTRAINT pk_partner_address  PRIMARY KEY (address_seq),
    CONSTRAINT fk_partaddr_partner FOREIGN KEY (partner_seq)
        REFERENCES tb_partner (partner_seq),
    CONSTRAINT ck_partaddr_default CHECK (default_yn IN ('Y', 'N')),
    CONSTRAINT ck_partaddr_use_yn  CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_partaddr_type    CHECK (addr_type IN ('SHIP', 'RETURN', 'ETC'))
);

CREATE INDEX ix_partaddr_partner ON tb_partner_address (partner_seq, sort_order);

/*
 * 거래처당 기본 주소는 하나 (MST-010).
 *
 * 용도(addr_type)를 키에 넣지 않았다. 그래서 지금은 한 거래처가 기본 배송지와
 * 기본 반품지를 함께 가질 수 없다 — 고객 배송지만 있던 시절의 제약이 그대로
 * 남은 것이다. 반품지를 실제로 쓰기 시작하면 (partner_seq, addr_type) 으로
 * 넓혀야 한다.
 */
CREATE UNIQUE INDEX ux_partaddr_default
    ON tb_partner_address (partner_seq)
 WHERE default_yn = 'Y';

COMMENT ON TABLE  tb_partner_address              IS '거래처 주소 — 배송지 · 반품지. MST-010';
COMMENT ON COLUMN tb_partner_address.partner_seq  IS '거래처 순번';
COMMENT ON COLUMN tb_partner_address.addr_type    IS '주소 용도 — 코드그룹 ADDR_TYPE (SHIP/RETURN/ETC)';
COMMENT ON COLUMN tb_partner_address.default_yn   IS '기본 주소 여부. 거래처 · 용도당 하나';


-- ============================================================================
-- 권한
--
-- 공급처와 고객을 한 화면에서 다루므로 권한도 하나다 (MST_PARTNER). 나눠 두면
-- "공급처는 고치는데 고객은 못 고치는 사람" 이 한 화면 안에서 절반만 쓰게 된다.
-- 방향별로 조이고 싶으면 공통정책으로 건다 — 정책 엔진이 이미 있어 코드를
-- 고치지 않아도 된다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('MST_PARTNER', '거래처 관리',   'MST', '기준정보 > 거래처',   140, 'system'),
  ('MST_REASON',  '사유코드 관리', 'MST', '기준정보 > 사유코드', 160, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('MST_PARTNER', 'RCUDX'),
        ('MST_REASON',  'RCUD')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 시스템 관리자는 조회만 한다 (정책 P001 취지). 등록 · 수정은 기준정보 담당.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'SYS_ADMIN'
   AND p.perm_id IN ('MST_PARTNER', 'MST_REASON');


-- ============================================================================
-- 메뉴
--
-- 거래처 묶음 아래에 한 줄만 남는다. 하위가 하나뿐인 머리글은 사이드바가
-- 머리글 없이 그 한 줄로 그린다 (NavNode.isFlat).
--
-- 사유코드는 어느 묶음에도 안 붙는다. 기준정보 바로 아래에 둔다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('MST_PARTNERS', '거래처 관리',   'GRP_MST_PARTNER', 'partners', '🤝', 'MST_PARTNER', 10),
        ('MST_REASONS',  '사유코드 관리', 'GRP_MASTER',      'reasons',  '🏷', 'MST_REASON',  90)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
