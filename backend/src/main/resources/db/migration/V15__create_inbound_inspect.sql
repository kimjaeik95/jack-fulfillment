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
