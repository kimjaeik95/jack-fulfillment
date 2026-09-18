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
-- ============================================================================
-- 입고검수 · 적치 · 입고완료 (INB-PG-003 ~ INB-PG-007)
--
-- 입하가 '차에서 내렸다' 라면 검수는 <b>세어 보고 받아들인다</b> 이고,
-- 적치는 '어디에 놓았다', 입고완료는 '이제 우리 재고다' 이다.
--
-- 네 단계를 나누는 이유는 각각 틀릴 수 있기 때문이다.
--   내린 개수가 예정과 다를 수 있고 (입하)
--   세어 보니 또 다를 수 있고 (검수)
--   그중 일부는 파손이라 못 받을 수 있고 (거부)
--   받기로 한 것도 어디에 놓았는지 모르면 찾을 수 없다 (적치)
--
-- 한 단계로 합치면 어디서 틀어졌는지 영영 알 수 없다.
--
-- <b>재고는 입고완료에서만 늘어난다</b> (INB-008). 적치까지 끝난 수량만
-- 반영한다 — 마당에 있는 물건을 팔 수는 없다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 입고예정 라인에 누적 수량을 더한다
-- ----------------------------------------------------------------------------
ALTER TABLE tb_inbound_line
    /*
     * 기입고수량 — 검수를 통과한 것의 누계 (INB-004).
     *
     * 검수 완료 경로에서만 올린다. 회차별 실입고의 합과 항상 같아야 하고,
     * 다른 경로로 손대면 그 등식이 깨져 무엇이 맞는지 알 수 없게 된다.
     */
    ADD COLUMN received_qty integer NOT NULL DEFAULT 0,

    /*
     * 거부수량 — 파손 · 오품 등으로 받지 않은 것 (INB-006).
     *
     * 재고에 반영하지 않는다. 받은 적이 없는 물건이라 우리 것이 아니고,
     * 공급처에 돌려보내거나 값을 깎는 협상의 근거가 된다.
     */
    ADD COLUMN rejected_qty integer NOT NULL DEFAULT 0,

    /*
     * 적치수량 — 로케이션에 실제로 놓은 것 (INB-007).
     *
     * 검수를 통과했어도 아직 마당에 있으면 팔 수 없다. 입고완료는 이
     * 수량만 재고로 만든다.
     */
    ADD COLUMN putaway_qty integer NOT NULL DEFAULT 0,

    ADD CONSTRAINT ck_inbl_received CHECK (received_qty >= 0),
    ADD CONSTRAINT ck_inbl_rejected CHECK (rejected_qty >= 0),
    ADD CONSTRAINT ck_inbl_putaway  CHECK (putaway_qty >= 0),

    -- 놓은 것이 받은 것보다 많을 수는 없다. 초과입고는 '받은 것' 자체가
    -- 늘어나는 것이지 없는 물건을 놓는 것이 아니다.
    ADD CONSTRAINT ck_inbl_putaway_le CHECK (putaway_qty <= received_qty);

COMMENT ON COLUMN tb_inbound_line.received_qty IS '검수 통과 누계. 회차별 실입고의 합과 항상 일치 (INB-004)';
COMMENT ON COLUMN tb_inbound_line.rejected_qty IS '거부 누계. 재고에 반영하지 않는다 (INB-006)';
COMMENT ON COLUMN tb_inbound_line.putaway_qty  IS '로케이션에 놓은 수량. 입고완료가 이만큼만 재고로 만든다';

-- ----------------------------------------------------------------------------
-- 검수 회차
--
-- 회차를 행으로 남긴다 (INB-003). 한 번에 다 못 세는 일이 흔하고 — 오후에
-- 나머지가 오거나, 일부만 먼저 풀어 보거나 — 그때 덮어쓰면 "처음엔 몇 개라
-- 했었지" 를 아무도 답할 수 없다.
--
-- 예정수량은 검수 결과로 덮지 않는다. 예정과 실제의 차이가 곧 찾아야 할
-- 것인데, 덮으면 차이가 사라진다.
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound_inspect (
    inspect_seq   bigint      GENERATED ALWAYS AS IDENTITY,
    line_seq      bigint      NOT NULL,

    /* 이 라인의 몇 번째 검수인가. 1 부터. */
    round_no      integer     NOT NULL,

    /* 금회 합격 — 이만큼을 받아들인다 */
    passed_qty    integer     NOT NULL,

    /* 금회 거부 — 파손 · 오품 등 (INB-006) */
    rejected_qty  integer     NOT NULL DEFAULT 0,

    /*
     * 거부 사유. 코드그룹 REASON_INSPECT
     *
     * 거부가 있으면 반드시 있어야 한다. 사유 없는 거부는 공급처와 다툴 때
     * 아무것도 증명하지 못하고, 우리 쪽 실수인지도 구분할 수 없다.
     */
    reason_code   varchar(30),
    remark        varchar(300),

    inspected_by  varchar(30) NOT NULL,
    inspected_at  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_inbound_inspect PRIMARY KEY (inspect_seq),
    CONSTRAINT fk_inbi_line      FOREIGN KEY (line_seq) REFERENCES tb_inbound_line(line_seq) ON DELETE CASCADE,

    CONSTRAINT ck_inbi_passed   CHECK (passed_qty >= 0),
    CONSTRAINT ck_inbi_rejected CHECK (rejected_qty >= 0),
    -- 아무것도 안 한 검수는 기록할 것이 없다
    CONSTRAINT ck_inbi_any      CHECK (passed_qty > 0 OR rejected_qty > 0),
    -- 거부가 있으면 왜인지 남아야 한다 (INB-006)
    CONSTRAINT ck_inbi_reason   CHECK (rejected_qty = 0 OR reason_code IS NOT NULL)
);

CREATE UNIQUE INDEX ux_inbi_round ON tb_inbound_inspect (line_seq, round_no);
CREATE INDEX ix_inbi_line        ON tb_inbound_inspect (line_seq);

COMMENT ON TABLE  tb_inbound_inspect IS '검수 회차 — 덮어쓰지 않고 쌓는다 (INB-003)';

-- ----------------------------------------------------------------------------
-- 적치
--
-- 어디에 놓았는지를 남긴다 (INB-007). 한 줄을 여러 로케이션에 나눠 놓는 일이
-- 흔해서 행으로 쌓는다 — 100 개 중 60 개는 A-01, 40 개는 A-02 처럼.
--
-- 스캔 검증은 서비스가 한다. SKU 바코드와 로케이션 바코드를 둘 다 받아,
-- 지시한 것과 다르면 진행을 막는다. 사람이 눈으로 맞추는 것보다 스캔 두 번이
-- 빠르고 정확하다.
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound_putaway (
    putaway_seq  bigint      GENERATED ALWAYS AS IDENTITY,
    line_seq     bigint      NOT NULL,

    /* 어디에 놓았나 */
    location_seq bigint      NOT NULL,
    qty          integer     NOT NULL,

    /*
     * 재고 반영 이력.
     *
     * 입고완료가 재고를 올리면서 남긴 이력의 순번을 여기 적는다. 나중에
     * "이 물건이 언제 재고가 됐나" 를 적치 한 줄에서 바로 건너갈 수 있다.
     * 아직 완료 전이면 비어 있다.
     */
    history_seq  bigint,

    putaway_by   varchar(30) NOT NULL,
    putaway_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_inbound_putaway PRIMARY KEY (putaway_seq),
    CONSTRAINT fk_inbp_line     FOREIGN KEY (line_seq)     REFERENCES tb_inbound_line(line_seq) ON DELETE CASCADE,
    CONSTRAINT fk_inbp_location FOREIGN KEY (location_seq) REFERENCES tb_location(location_seq),
    CONSTRAINT fk_inbp_history  FOREIGN KEY (history_seq)  REFERENCES tb_stock_history(history_seq),

    CONSTRAINT ck_inbp_qty CHECK (qty > 0)
);

CREATE INDEX ix_inbp_line ON tb_inbound_putaway (line_seq);
CREATE INDEX ix_inbp_loc  ON tb_inbound_putaway (location_seq);

COMMENT ON TABLE  tb_inbound_putaway            IS '적치 — 어디에 몇 개 놓았나 (INB-007)';
COMMENT ON COLUMN tb_inbound_putaway.history_seq IS '입고완료가 남긴 재고이력. 완료 전엔 비어 있다';

-- ----------------------------------------------------------------------------
-- 입고 헤더 — 상태 확장과 초과승인
-- ----------------------------------------------------------------------------
ALTER TABLE tb_inbound
    /*
     * 초과입고 승인 (INB-005).
     *
     * 기입고가 예정을 넘고 그 초과분이 공급처 허용 오차율을 벗어나면
     * 승인 없이 입고를 완료할 수 없다. 시키지도 않은 물건을 말없이 받으면
     * 재고와 대금이 함께 틀어진다.
     */
    ADD COLUMN over_approved_by varchar(30),
    ADD COLUMN over_approved_at timestamp,
    ADD COLUMN over_approve_remark varchar(300),

    /* 입고완료 자취 */
    ADD COLUMN closed_by varchar(30),
    ADD COLUMN closed_at timestamp;

-- 상태를 넓힌다 (INB-009). 예정 → 입하 → 검수중 → 적치중 → 입고완료.
ALTER TABLE tb_inbound DROP CONSTRAINT ck_inbound_status;
ALTER TABLE tb_inbound ADD CONSTRAINT ck_inbound_status CHECK (inbound_status IN
    ('PLANNED','ARRIVED','INSPECTING','PUTAWAY','DONE','CANCELED'));

-- 완료면 언제 · 누가 닫았는지가 있어야 한다
ALTER TABLE tb_inbound ADD CONSTRAINT ck_inbound_closed CHECK (
    inbound_status <> 'DONE' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL));

COMMENT ON COLUMN tb_inbound.over_approved_by IS '초과입고 승인자. 허용 오차를 넘으면 이것 없이 완료 불가 (INB-005)';

-- ============================================================================
-- 코드 — 상태 세 가지를 더한다
-- ============================================================================
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.color, v.sort_order, 'system'
  FROM (VALUES
        ('INBOUND_STATUS', 'INSPECTING', '검수중',   '세어 보는 중이다. 아직 재고가 아니다.',        'amber', 30),
        ('INBOUND_STATUS', 'PUTAWAY',    '적치중',   '받기로 했고, 어디에 놓을지 정하는 중이다.',    'violet', 40),
        ('INBOUND_STATUS', 'DONE',       '입고완료', '적치까지 끝나 재고가 되었다.',                 'green', 50)
       ) AS v(group_id, code_id, code_name, description, color, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;

-- ============================================================================
-- 메뉴
--
-- 검수 · 적치를 나눠 둔다. 검수는 물건을 세는 일이고 적치는 자리에 놓는
-- 일이라 사람도 때도 다르다. 초과승인은 권한이 달라(INB_APPROVE) 또 나뉜다.
--
-- 입고완료는 화면을 따로 두지 않는다. 적치가 끝난 그 자리에서 누르는 것이
-- 자연스럽고, 별도 화면으로 빼면 "적치는 했는데 완료를 안 눌렀다" 가 는다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('INB_INSPECT',  '입고검수',   'inbound-inspect', '🔎', 'INB_INSPECT', 30),
        ('INB_OVER',     '초과입고 승인', 'inbound-approve', '⚖', 'INB_APPROVE', 40),
        ('INB_PUTAWAYS', '적치',       'inbound-putaway', '📍', 'INB_PUTAWAY', 50)
       ) AS v(menu_id, menu_name, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = 'GRP_INB'
  JOIN tb_permission p ON p.perm_id = v.perm_id;

-- ============================================================================
-- 권한 보완
--
-- 검수 · 적치는 예정을 보고 하는 일이라 예정 조회가 없으면 목록이 비어 있다.
-- 입고담당은 이미 INB_PLAN 을 가졌지만, 센터관리자는 검수 · 적치 권한이
-- 없어 화면에 들어갈 수 없었다 — 승인만 하고 현장은 안 보는 셈이었다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.code, 'system'
  FROM tb_role r, tb_permission p, (VALUES ('C'),('R')) AS a(code)
 WHERE r.role_id = 'CENTER_MGR'
   AND p.perm_id IN ('INB_INSPECT','INB_PUTAWAY')
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission x
                    WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
                      AND x.action_code = a.code);

-- 입고담당도 초과 승인 요청 결과를 봐야 한다. 승인은 못 하고 조회만.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r, tb_permission p
 WHERE r.role_id = 'INBOUND_WORKER' AND p.perm_id = 'INB_APPROVE'
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission x
                    WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
                      AND x.action_code = 'R');
-- ============================================================================
-- 입고정정 요청 · 승인 (INB-PG-008)
--
-- 완료된 입고의 수량이 틀렸을 때 고친다. 재고조정(INV-PG-006)과 무엇이
-- 다른가가 이 기능의 전부다.
--
--   재고조정  재고 숫자만 고친다. 입고 전표와 발주는 그대로다.
--   입고정정  재고 · 입고 전표 · 발주 기입고수량을 <b>함께</b> 되감는다.
--
-- 재고조정으로 때우면 재고는 맞아도 "공급처한테 30 개 덜 받았다" 가 어디에도
-- 남지 않는다. 발주는 여전히 다 들어온 것으로 닫혀 있어 다시 보내 달라고 할
-- 잔량이 없고, 나중에 누구 잘못이었는지도 답할 수 없다.
--
-- <b>완료된 입고만</b> 정정한다 (DONE). 아직 진행 중이면 정정할 이유가 없다 —
-- 검수를 다시 하거나 적치를 더 하면 된다. 정정은 이미 재고가 되어 버려서
-- 정상 경로로는 되돌릴 수 없게 된 것만 다룬다.
--
-- 권한은 새로 만들지 않는다. V3 가 INB_CORRECTION 을 이미 정의해 뒀고
-- 액션까지 나눠 뒀다 — 입고담당은 C(요청), 센터관리자는 A(승인).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 정정 전표
--
-- 재고조정 전표(tb_stock_adjust)와 같은 모양이다. 요청 → 승인/반려 흐름이
-- 같고, 같은 모양이면 화면도 코드도 읽는 법이 하나다.
--
-- 다른 점은 창고가 아니라 <b>입고</b>를 물고 있다는 것이다. 정정은 언제나
-- 특정 입고 한 건에 대한 이야기라, 그 입고를 통해 창고 · 플랜트 · 조직까지
-- 거슬러 올라간다.
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound_correct (
    correct_seq    bigint      GENERATED ALWAYS AS IDENTITY,
    -- INBC-20260916-0001
    correct_no     varchar(30) NOT NULL,

    -- 어느 입고를 고치나
    inbound_seq    bigint      NOT NULL,

    -- 코드그룹 CORRECT_STATUS
    correct_status varchar(20) NOT NULL DEFAULT 'REQUESTED',
    -- 코드그룹 REASON_CORRECT (수량 오기 · 공급처 미납 · 검수 착오 · 이중 계상)
    reason_code    varchar(30) NOT NULL,
    remark         varchar(300),

    requested_by   varchar(30) NOT NULL,
    requested_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    decided_by     varchar(30),
    decided_at     timestamp,
    decide_remark  varchar(300),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_inbound_correct    PRIMARY KEY (correct_seq),
    CONSTRAINT uk_inbound_correct_no UNIQUE (correct_no),
    CONSTRAINT fk_inbc_inbound       FOREIGN KEY (inbound_seq) REFERENCES tb_inbound (inbound_seq),
    CONSTRAINT ck_inbc_status        CHECK (correct_status IN ('REQUESTED','APPROVED','REJECTED','CANCELED')),
    -- 처리된 전표는 처리자와 시각이 있어야 한다. 누가 언제 승인했는지
    -- 남지 않는 승인은 승인이 아니다.
    CONSTRAINT ck_inbc_decided       CHECK (
        (correct_status IN ('REQUESTED','CANCELED'))
     OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    -- 반려는 왜 반려했는지가 있어야 한다
    CONSTRAINT ck_inbc_reject        CHECK (correct_status <> 'REJECTED' OR decide_remark IS NOT NULL)
);

CREATE INDEX ix_inbc_inbound ON tb_inbound_correct (inbound_seq, requested_at DESC);
CREATE INDEX ix_inbc_status  ON tb_inbound_correct (correct_status, requested_at DESC);
CREATE INDEX ix_inbc_who     ON tb_inbound_correct (requested_by, requested_at DESC);

COMMENT ON TABLE tb_inbound_correct IS '입고정정 전표 — 완료된 입고의 수량을 고친다 (INB-PG-008)';


-- ----------------------------------------------------------------------------
-- 정정 라인
--
-- <b>적치 행</b>에 건다. 입고 라인이 아니다.
--
-- 한 줄을 여러 자리에 나눠 놓는 일이 흔하다 — 100 개 중 60 개는 1A-01-01,
-- 40 개는 1A-01-02. 정정을 입고 라인에 걸면 "그럼 어느 자리에서 빼나" 를
-- 시스템이 멋대로 정하게 되고, 창고에 가 보면 없는 자리에서 뺀 것이 된다.
--
-- 적치 행에 걸면 자리 · SKU · 원래 이력이 한 번에 정해진다. 요청자도
-- "1A-01-01 에 놓은 60 개 중 10 개" 라고 눈에 보이는 대로 적는다.
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound_correct_line (
    line_seq     bigint      GENERATED ALWAYS AS IDENTITY,
    correct_seq  bigint      NOT NULL,
    line_no      integer     NOT NULL,

    -- 어느 적치를 고치나
    putaway_seq  bigint      NOT NULL,

    /*
     * 정정량. 부호가 방향이다.
     *
     *   음수  덜 받았는데 더 적었다 → 재고를 줄인다  (흔한 쪽)
     *   양수  더 받았는데 덜 적었다 → 재고를 늘린다
     *
     * 목표수량이 아니라 변동량으로 적는다. 요청과 승인 사이에 재고가 움직일
     * 수 있는데, 요청자가 "10 개 덜 왔더라" 고 했으면 승인 시점에도 10 개를
     * 빼는 것이 맞지 그 사이 입고된 것까지 없애는 것은 요청한 적 없는 일이다
     * (재고조정과 같은 이유다).
     */
    qty_delta    integer     NOT NULL,

    -- 라인별 사유. 비우면 헤더 사유를 따른다 — 한 전표에 미납과 오기가
    -- 섞이는 일이 실제로 있다.
    reason_code  varchar(30),
    remark       varchar(300),

    -- 승인으로 만들어진 재고이력. 정정 한 줄에서 재고 이력으로 바로 건너간다.
    applied_history_seq bigint,

    CONSTRAINT pk_inbc_line     PRIMARY KEY (line_seq),
    CONSTRAINT uk_inbc_line_no  UNIQUE (correct_seq, line_no),
    -- 한 전표에서 같은 적치를 두 번 고칠 수 없다. 두 줄이 서로 다른 값을
    -- 말하면 어느 쪽이 맞는지 정할 수 없다.
    CONSTRAINT uk_inbc_line_key UNIQUE (correct_seq, putaway_seq),
    CONSTRAINT fk_inbcl_correct FOREIGN KEY (correct_seq) REFERENCES tb_inbound_correct (correct_seq)
                                ON DELETE CASCADE,
    CONSTRAINT fk_inbcl_putaway FOREIGN KEY (putaway_seq) REFERENCES tb_inbound_putaway (putaway_seq),
    CONSTRAINT fk_inbcl_hist    FOREIGN KEY (applied_history_seq)
                                REFERENCES tb_stock_history (history_seq),
    -- 0 은 고치는 것이 없다. 승인자가 읽을 것만 남긴다.
    CONSTRAINT ck_inbcl_delta   CHECK (qty_delta <> 0)
);

CREATE INDEX ix_inbcl_correct ON tb_inbound_correct_line (correct_seq, line_no);
-- '이 적치에 걸린 미결 정정이 있나' — 두 번째 정정 요청이 먼저 확인한다
CREATE INDEX ix_inbcl_putaway ON tb_inbound_correct_line (putaway_seq);

COMMENT ON COLUMN tb_inbound_correct_line.qty_delta IS '음수=재고 차감, 양수=재고 추가. 목표가 아니라 변동량';


-- ============================================================================
-- 코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, created_by) VALUES
  ('CORRECT_STATUS', '입고정정 상태', '입고정정 전표의 결재 상태', 'system'),
  ('REASON_CORRECT', '입고정정 사유', '완료된 입고를 왜 고치는가', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.color, v.sort_order, 'system'
  FROM (VALUES
        ('CORRECT_STATUS', 'REQUESTED', '승인대기', '요청만 올라간 상태. 재고는 아직 그대로다.',   'amber',  10),
        ('CORRECT_STATUS', 'APPROVED',  '승인',     '재고 · 입고 · 발주에 반영되었다.',             'green',  20),
        ('CORRECT_STATUS', 'REJECTED',  '반려',     '되돌리지 않기로 했다. 사유가 남는다.',         'red',    30),
        ('CORRECT_STATUS', 'CANCELED',  '취소',     '요청자가 거둬들였다.',                         'gray',   40),

        -- 사유를 나누는 기준은 '누구 잘못인가' 다. 이 구분이 없으면 정정을
        -- 아무리 남겨도 공급처와 다툴 때 쓸 수 없다.
        ('REASON_CORRECT', 'SHORT_SHIP', '공급처 미납', '적게 왔는데 받은 것으로 적었다. 공급처 귀책.',     NULL, 10),
        ('REASON_CORRECT', 'OVER_SHIP',  '공급처 과납', '많이 왔는데 적게 적었다. 공급처 귀책.',             NULL, 20),
        ('REASON_CORRECT', 'MISCOUNT',   '검수 착오',   '우리가 잘못 셌다.',                                 NULL, 30),
        ('REASON_CORRECT', 'TYPO',       '수량 오기',   '세기는 맞게 셌는데 입력을 틀렸다.',                 NULL, 40),
        ('REASON_CORRECT', 'DOUBLE',     '이중 계상',   '같은 물건을 두 번 입고로 잡았다.',                  NULL, 50),

        -- 재고이동 이력 화면이 이동유형과 전표유형을 코드로 읽는다. 없으면
        -- 정정으로 만든 이력만 필터에서 빠지고 배지도 코드값 그대로 뜬다 —
        -- 되감은 기록이 가장 찾기 어려운 기록이 된다.
        ('STOCK_MOVE',     'CORRECT',        '입고정정', '완료된 입고를 되감았다 (INB-PG-008).', NULL, 45),
        ('STOCK_REF',      'INBOUND_CORRECT', '입고정정전표', '입고정정 전표에서 비롯된 변동.',   NULL, 60)
       ) AS v(group_id, code_id, code_name, description, color, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 메뉴
--
-- 요청과 승인을 나눈다. 같은 화면에 두면 승인자가 자기 결재함을 찾으려고
-- 남의 요청 목록을 먼저 뒤져야 한다 — 재고조정도 같은 이유로 나눠 뒀다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('INB_CORRECT_REQ', '입고정정 요청', 'inbound-corrects',        '✏', 'INB_CORRECTION', 60),
        ('INB_CORRECT_APV', '입고정정 승인', 'inbound-correct-approve', '🧾', 'INB_CORRECTION', 70)
       ) AS v(menu_id, menu_name, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = 'GRP_INB'
  JOIN tb_permission p ON p.perm_id = v.perm_id;


-- ============================================================================
-- 권한 보완
--
-- 요청자는 자기가 올린 것을 고치고 거둘 수 있어야 한다. V3 는 C · R · A 만
-- 줬는데, U(수정) · D(취소)가 없으면 잘못 적은 요청을 되돌릴 방법이 없어
-- 승인자에게 반려를 부탁해야 한다.
-- ============================================================================
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.code, 'system'
  FROM tb_permission p, (VALUES ('U'),('D')) AS a(code)
 WHERE p.perm_id = 'INB_CORRECTION'
   AND NOT EXISTS (SELECT 1 FROM tb_permission_action x
                    WHERE x.perm_seq = p.perm_seq AND x.action_code = a.code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.code, 'system'
  FROM tb_role r, tb_permission p, (VALUES ('U'),('D')) AS a(code)
 WHERE r.role_id = 'INBOUND_WORKER' AND p.perm_id = 'INB_CORRECTION'
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission x
                    WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
                      AND x.action_code = a.code);

-- 정정 화면은 완료된 입고를 골라 그 적치 내역을 보여 준다. 예정 조회가
-- 없으면 고를 목록 자체가 비어 있다 — 센터관리자는 이미 INB_PLAN 을 가졌다.


-- ============================================================================
-- 공급처 유형 강제
--
-- 맨 뒤에 붙인다. 위쪽 검수 · 정정 섹션이 tb_inbound 에 초과승인 · 마감 컬럼을
-- ALTER 로 더하기 때문에, 여기서 붙여야 컬럼 순서가 그 뒤로 온다.
--
-- 'Y' 고정값 컬럼과 복합 FK 로 공급처만 가리키게 한다 (V7 uk_partner_supplier).
-- 고객 전용 거래처를 입고에 넣으면 INSERT 가 거부된다.
-- ============================================================================
ALTER TABLE tb_inbound
    ADD COLUMN supplier_chk char(1) GENERATED ALWAYS AS ('Y') STORED;

ALTER TABLE tb_inbound
    ADD CONSTRAINT fk_inbound_supplier FOREIGN KEY (supplier_seq, supplier_chk)
        REFERENCES tb_partner (partner_seq, supplier_yn);
