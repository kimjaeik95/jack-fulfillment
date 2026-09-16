-- ============================================================================
-- 입고예정 · 입하 (PUR-PG-006, INB-PG-001, INB-PG-002)
--
-- 발주가 '공급처와의 약속' 이라면 입고예정은 <b>우리 창고가 받을 준비</b>다.
-- 언제 · 어디로 · 무엇이 몇 개 오는지를 미리 적어 두고, 물건이 실제로
-- 도착하면 그 위에 입하를 얹는다.
--
-- 예정을 따로 두는 이유가 있다. 발주에서 바로 검수로 가면 "오늘 뭐가
-- 들어오나" 를 알 수 없다. 창고는 인력과 자리를 미리 잡아야 하는데 그
-- 근거가 예정이다. 그리고 한 발주가 두 번에 나눠 들어오면 예정도 둘이다 —
-- 발주 한 줄에 도착 한 번을 강제할 수 없다.
--
-- 이번 마이그레이션이 만드는 것은 <b>예정과 입하까지</b>다. 검수(INB-003)
-- 이후는 다음 단계라 그 컬럼을 두지 않는다. 쓰는 코드가 없는 컬럼을 미리
-- 만들면 무엇이 채워지고 무엇이 비어 있는지 아무도 모르게 된다.
--
-- 재고는 여기서 움직이지 않는다. 물건이 도착한 것과 우리 재고가 된 것은
-- 다르다 — 검수를 통과하고 적치까지 끝나야 팔 수 있는 재고다 (INB-008).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 입고예정
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound (
    inbound_seq   bigint      GENERATED ALWAYS AS IDENTITY,

    inbound_no    varchar(30) NOT NULL,

    /*
     * 입고 종류. 코드그룹 INBOUND_TYPE
     *
     * 구매입고는 발주가 근거다. 반품입고는 주문이 근거인데 주문이 아직
     * 없어 지금은 근거 없이 받는다 — 3차에서 주문이 생기면 채운다.
     * 이동입고는 다른 센터에서 온 것이라 우리 쪽 근거가 원래 없다.
     */
    inbound_type  varchar(20) NOT NULL,

    /*
     * 근거 발주. 구매입고면 필수다 (INB-001).
     *
     * 발주 없이 구매입고를 만들면 "누가 시킨 물건인지 모르는데 창고에
     * 들어온" 상태가 된다. 그건 입고가 아니라 사고다.
     */
    order_seq     bigint,

    /* 받을 곳. 창고까지 정해야 한다 — 같은 센터에도 양품·불량 창고가 따로 있다. */
    plant_seq     bigint      NOT NULL,
    warehouse_seq bigint      NOT NULL,

    /* 보내는 곳. 이동입고는 공급처가 없을 수 있다. */
    supplier_seq  bigint,

    planned_date  date        NOT NULL,

    /*
     * 상태. 코드그룹 INBOUND_STATUS
     *
     * 예정 → 입하 → (검수 → 적치 → 완료). 괄호 안은 다음 단계에서 붙는다.
     */
    inbound_status varchar(20) NOT NULL DEFAULT 'PLANNED',

    /* ---- 입하 (INB-PG-002) --------------------------------------------
     * 물건이 실제로 도착했을 때 채워진다.
     *
     * 차량번호와 기사를 남기는 이유는, 수량이 안 맞거나 파손이 나왔을 때
     * 어느 차로 왔는지가 첫 번째 단서이기 때문이다. 나중에 물어보면
     * 아무도 기억하지 못한다.
     */
    arrived_at    timestamp,
    arrived_by    varchar(30),
    vehicle_no    varchar(30),
    driver_name   varchar(50),
    arrive_remark varchar(300),

    canceled_by   varchar(30),
    canceled_at   timestamp,
    cancel_reason varchar(300),

    remark        varchar(300),

    created_by    varchar(30) NOT NULL,
    created_at    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,

    CONSTRAINT pk_inbound     PRIMARY KEY (inbound_seq),
    CONSTRAINT uk_inbound_no  UNIQUE (inbound_no),

    CONSTRAINT fk_inbound_order    FOREIGN KEY (order_seq)     REFERENCES tb_purchase_order(order_seq),
    CONSTRAINT fk_inbound_plant    FOREIGN KEY (plant_seq)     REFERENCES tb_plant(plant_seq),
    CONSTRAINT fk_inbound_wh       FOREIGN KEY (warehouse_seq) REFERENCES tb_warehouse(warehouse_seq),
    CONSTRAINT fk_inbound_supplier FOREIGN KEY (supplier_seq)  REFERENCES tb_supplier(supplier_seq),

    CONSTRAINT ck_inbound_status CHECK (inbound_status IN
        ('PLANNED','ARRIVED','CANCELED')),
    CONSTRAINT ck_inbound_type   CHECK (inbound_type IN
        ('PURCHASE','RETURN','TRANSFER')),

    -- 구매입고는 발주가 있어야 한다 (INB-001)
    CONSTRAINT ck_inbound_order  CHECK (inbound_type <> 'PURCHASE' OR order_seq IS NOT NULL),

    -- 입하 상태면 언제 · 누가 받았는지가 있어야 한다. 하나만 찍히면
    -- '받긴 받았는데 아무도 모르는' 상태가 남는다.
    CONSTRAINT ck_inbound_arrived CHECK (
        inbound_status <> 'ARRIVED' OR (arrived_at IS NOT NULL AND arrived_by IS NOT NULL)),

    CONSTRAINT ck_inbound_canceled CHECK (
        inbound_status <> 'CANCELED' OR (canceled_by IS NOT NULL AND cancel_reason IS NOT NULL))
);

CREATE INDEX ix_inbound_plan   ON tb_inbound (planned_date, inbound_status);
CREATE INDEX ix_inbound_order  ON tb_inbound (order_seq);
CREATE INDEX ix_inbound_wh     ON tb_inbound (warehouse_seq, inbound_status);

COMMENT ON TABLE  tb_inbound               IS '입고예정 — 창고가 받을 준비 (INB-PG-001)';
COMMENT ON COLUMN tb_inbound.inbound_type  IS '코드그룹 INBOUND_TYPE. 구매입고는 발주 필수';
COMMENT ON COLUMN tb_inbound.vehicle_no    IS '수량 불일치·파손 추적의 첫 단서';

-- ----------------------------------------------------------------------------
-- 입고예정 상세
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound_line (
    line_seq       bigint      GENERATED ALWAYS AS IDENTITY,
    inbound_seq    bigint      NOT NULL,
    line_no        integer     NOT NULL,

    sku_seq        bigint      NOT NULL,

    /* 어느 발주 줄에서 왔나. 직접 등록한 예정이면 비어 있다. */
    order_line_seq bigint,

    /*
     * 입고 예정수량.
     *
     * 발주 잔량을 넘을 수 없다 (INB-002). 넘는 예정을 허용하면 창고가
     * 오지 않을 물건의 자리를 잡는다.
     *
     * 실제로 더 들어오는 것(초과입고)은 다른 이야기다. 그건 검수에서
     * 판정하고 승인을 받는다 (INB-005) — 예정 단계에서 미리 열어 두지
     * 않는다.
     */
    planned_qty    integer     NOT NULL,

    /*
     * 입하수량 — 차에서 내린 개수 (INB-PG-002).
     *
     * 검수 전 값이라 '우리가 받은 수량' 이 아니다. 세어 보면 달라질 수
     * 있고, 그 차이를 찾는 것이 검수다. 그래서 예정수량을 덮어쓰지 않고
     * 따로 적는다 (INB-003).
     */
    arrived_qty    integer,

    remark         varchar(300),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_inbound_line   PRIMARY KEY (line_seq),
    CONSTRAINT fk_inbl_inbound   FOREIGN KEY (inbound_seq)    REFERENCES tb_inbound(inbound_seq) ON DELETE CASCADE,
    CONSTRAINT fk_inbl_sku       FOREIGN KEY (sku_seq)        REFERENCES tb_sku(sku_seq),
    CONSTRAINT fk_inbl_orderline FOREIGN KEY (order_line_seq) REFERENCES tb_purchase_order_line(line_seq),

    CONSTRAINT ck_inbl_planned CHECK (planned_qty > 0),
    CONSTRAINT ck_inbl_arrived CHECK (arrived_qty IS NULL OR arrived_qty >= 0)
);

-- 한 예정에 같은 SKU 가 두 줄이면 몇 개를 받아야 하는지 정할 수 없고,
-- 검수도 어느 줄에 붙일지 모른다.
CREATE UNIQUE INDEX ux_inbl_sku ON tb_inbound_line (inbound_seq, sku_seq);
CREATE INDEX ix_inbl_orderline  ON tb_inbound_line (order_line_seq);

COMMENT ON TABLE  tb_inbound_line             IS '입고예정 상세 (INB-002)';
COMMENT ON COLUMN tb_inbound_line.planned_qty IS '발주 잔량을 넘을 수 없다';
COMMENT ON COLUMN tb_inbound_line.arrived_qty IS '차에서 내린 개수. 검수 전이라 확정 수량이 아니다';

-- ============================================================================
-- 코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, created_by)
VALUES
  ('INBOUND_TYPE',   '입고 종류',   '구매입고 · 반품입고 · 이동입고', 'system'),
  ('INBOUND_STATUS', '입고 상태',   '예정 · 입하 · 취소',             'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.color, v.sort_order, 'system'
  FROM (VALUES
        ('INBOUND_TYPE',   'PURCHASE', '구매입고', '발주한 물건이 들어온다. 발주 참조가 필수다.', 'blue',  10),
        ('INBOUND_TYPE',   'RETURN',   '반품입고', '고객이 돌려보낸 물건. 검수 전까지 팔 수 없다.', 'amber', 20),
        ('INBOUND_TYPE',   'TRANSFER', '이동입고', '다른 센터에서 넘어온 물건.',                    'gray',  30),
        ('INBOUND_STATUS', 'PLANNED',  '예정',     '아직 도착하지 않았다. 고칠 수 있다.',           'gray',  10),
        ('INBOUND_STATUS', 'ARRIVED',  '입하',     '도착해서 내렸다. 아직 재고는 아니다.',          'blue',  20),
        ('INBOUND_STATUS', 'CANCELED', '취소',     '사유와 함께 거둬들였다.',                       'red',   90)
       ) AS v(group_id, code_id, code_name, description, color, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;

-- ============================================================================
-- 권한 · 메뉴
--
-- V3 가 입하·검수·적치·확정 권한은 미리 정의해 뒀는데 <b>입고예정</b>이
-- 없었다. 예정을 만드는 일은 스캔이 아니라 사무 작업이라 성격이 다르다.
-- ============================================================================
-- 입하(310)보다 앞이다. 예정이 있어야 입하를 찍는다.
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES ('INB_PLAN', '입고예정 관리', 'INB', '입고 > 입고예정', 305, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.code, 'system'
  FROM tb_permission p, (VALUES ('C'),('R'),('U'),('D')) AS a(code)
 WHERE p.perm_id = 'INB_PLAN';

-- 입고담당은 예정을 만들고 입하를 찍는다. 센터관리자는 그 위를 본다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.code, 'system'
  FROM tb_role r, tb_permission p, (VALUES ('C'),('R'),('U'),('D')) AS a(code)
 WHERE p.perm_id = 'INB_PLAN' AND r.role_id IN ('INBOUND_WORKER','CENTER_MGR');

-- 구매담당은 자기가 낸 발주가 예정으로 넘어갔는지 본다. 만들지는 않는다 —
-- 받을 준비는 창고가 한다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r, tb_permission p
 WHERE p.perm_id = 'INB_PLAN' AND r.role_id = 'PURCHASER';

-- 입하는 예정을 보고 찍는 것이라 예정 조회가 없으면 할 수 없다
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r, tb_permission p
 WHERE p.perm_id = 'INB_ARRIVE' AND r.role_id = 'CENTER_MGR'
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission x
                    WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq AND x.action_code = 'R');

/*
 * 입고담당에게 플랜트 읽기.
 *
 * 입고예정은 '받을 센터' 를 반드시 골라야 만들 수 있는데, MST_PLANT 가
 * 없으면 그 드롭다운이 <b>조용히 빈 채로</b> 뜬다. 목록 조회는 권한이
 * 없어도 화면 전체를 실패로 만들지 않고 그 종류만 비워 두도록 되어 있어서
 * (catalog · hierarchy 스토어의 loadKind), 오류 없이 선택지만 사라진다.
 *
 * 이 누락이 V907(재고담당) · V908(구매담당)에 이어 <b>세 번째</b>다. 업무
 * 역할이 늘 때마다 같은 줄이 붙는다는 뜻이고, 그 자체가 설계 신호다 —
 * 화면이 필요로 하는 것은 '기준정보를 관리할 권한' 이 아니라 '내가 쓸 수
 * 있는 센터 목록' 인데 둘이 한 권한에 묶여 있다.
 *
 * 근본 해법은 데이터 범위 기반의 '내 센터' 조회 경로를 따로 두거나, 거점
 * 3단계 읽기를 업무 역할의 기본으로 묶는 것이다. 어느 쪽이든 설계 판단이라
 * 여기서 정하지 않고 이 줄로 메운다.
 */
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r, tb_permission p
 WHERE p.perm_id = 'MST_PLANT' AND r.role_id = 'INBOUND_WORKER'
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission x
                    WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq AND x.action_code = 'R');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
VALUES ('GRP_INB', '입고', NULL, NULL, '📥', NULL, 47, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('INB_PLANS',   '입고예정', 'inbounds',        '🗓', 'INB_PLAN',   10),
        ('INB_ARRIVES', '입하 등록', 'inbound-arrive', '🚛', 'INB_ARRIVE', 20)
       ) AS v(menu_id, menu_name, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = 'GRP_INB'
  JOIN tb_permission p ON p.perm_id = v.perm_id;

-- 구매(50)와 공통 정책(50)이 같은 값이라 순서가 DB 가 주는 대로 나왔다.
-- 입고를 그 사이에 끼우기 전에 갈라 둔다.
UPDATE tb_menu SET sort_order = 55 WHERE menu_id = 'GRP_COMMON';
