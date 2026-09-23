-- ============================================================================
-- 피킹 (OUT-PG-003 ~ OUT-PG-005 / 4차 B섹터)
--
-- 출고지시가 '집어라' 라면 피킹은 '집었다' 이다.
--
-- 여기서 재고 수량은 바뀌지 않는다. 물건을 빈에서 꺼내 카트에 옮겼을 뿐
-- 아직 창고 안에 있고, 주문이 취소되면 도로 놓는다. 보유수량이 줄어드는
-- 것은 출고확정(E섹터)뿐이다 (P-01).
--
-- 그래서 피킹이 남기는 것은 둘이다.
--   1 지시 줄마다 '몇 개 집었나'      → tb_outbound_line.picked_qty
--   2 '어느 빈에서 몇 개 집었나'      → tb_outbound_pick
--
-- 2 가 없으면 출고확정이 어느 빈의 재고를 줄여야 할지 모른다. 한 줄이 여러
-- 빈에서 나뉘어 잡히기 때문에 '지시 줄에 5 개' 만으로는 부족하다.
-- ============================================================================


-- ============================================================================
-- 1. 작업자 배정 (OUT-PG-003)
--
-- 지시 단위로 맡긴다. 지시 하나가 주문 하나라 한 사람이 끝까지 도는 것이
-- 자연스럽고, 중간에 사람이 바뀌면 무엇을 집었는지 이어받을 근거가 없다.
--
-- 상태를 새로 만들지 않는다. 배정은 '누가 할 일인가' 이고 상태는 '어디까지
-- 갔나' 라서, 배정만 해 두고 아무도 안 집는 일이 정상이다. 상태는 첫 스캔에
-- 비로소 피킹중이 된다 — 그래야 배정만 된 지시를 아직 취소할 수 있다.
-- ============================================================================
ALTER TABLE tb_outbound
    ADD COLUMN assigned_to varchar(30),
    ADD COLUMN assigned_at timestamp,
    ADD COLUMN assigned_by varchar(30);

COMMENT ON COLUMN tb_outbound.assigned_to IS '피킹 담당 (OUT-PG-003). 비면 아직 아무도 안 맡았다';
COMMENT ON COLUMN tb_outbound.assigned_by IS '맡긴 사람. 스스로 잡으면 자기 자신';

-- '내가 맡은 아직 안 끝난 일' — 작업자 화면의 기본 경로
CREATE INDEX ix_outbound_assigned ON tb_outbound (assigned_to, outbound_status)
    WHERE outbound_status NOT IN ('SHIPPED','CANCELED');


-- ============================================================================
-- 2. 결품 수량 (OUT-PG-005)
--
-- 집으러 갔는데 없는 경우다. 할당 결품과 다르다.
--
--   할당 결품   전산에도 없다. 채널이 우리 재고보다 많이 팔았다.
--   피킹 결품   전산엔 있는데 실물이 없다. 재고 오차 · 파손 · 분실.
--
-- 지시수량을 줄이지 않고 칸을 따로 둔다. 5 를 3 으로 고치면 '몇 개를 집으라고
-- 했었나' 가 사라진다 — 발주에서 발주수량을 안 건드리는 것과 같은 원칙이다.
--
--   지시 5 = 집음 3 + 결품 2   → 남은 0, 이 줄은 끝났다
--
-- remain_qty 를 다시 정의한다. GENERATED 컬럼은 식을 바꿀 수 없어 지우고
-- 새로 만든다 — 저장된 값이 아니라 계산식이라 데이터는 잃지 않는다.
-- ============================================================================
ALTER TABLE tb_outbound_line DROP COLUMN remain_qty;

ALTER TABLE tb_outbound_line
    ADD COLUMN shortage_qty integer NOT NULL DEFAULT 0,
    ADD COLUMN shortage_reason varchar(30);

ALTER TABLE tb_outbound_line
    ADD COLUMN remain_qty integer
        GENERATED ALWAYS AS (instructed_qty - picked_qty - shortage_qty) STORED;

ALTER TABLE tb_outbound_line
    ADD CONSTRAINT ck_outbl_shortage CHECK (shortage_qty >= 0),
    -- 집은 것과 결품을 합쳐 지시수량을 넘을 수 없다. 넘으면 남은 수량이
    -- 음수가 되어 '덜 집었는데 다 끝난' 줄이 생긴다.
    ADD CONSTRAINT ck_outbl_total CHECK (picked_qty + shortage_qty <= instructed_qty),
    -- 결품을 적었으면 이유가 있어야 한다. 실물이 없다는 말이라, 왜 없는지가
    -- 남지 않으면 재고 오차를 나중에 추적할 수 없다.
    ADD CONSTRAINT ck_outbl_sh_reason CHECK (shortage_qty = 0 OR shortage_reason IS NOT NULL);

COMMENT ON COLUMN tb_outbound_line.shortage_qty    IS '집으러 갔는데 없던 수량 (OUT-PG-005)';
COMMENT ON COLUMN tb_outbound_line.shortage_reason IS '결품 사유 — 코드그룹 REASON_PICK_SHORT';


-- ============================================================================
-- 3. 피킹 실적 (OUT-PG-004)
--
-- 어느 빈에서 몇 개 집었는지. 출고확정(E섹터)이 이것을 보고 그 빈의 재고를
-- 줄인다 — 지시 줄에 '5 개' 만 있으면 어느 빈에서 뺄지 모른다.
--
-- 할당 행도 함께 가리킨다. 출고확정은 보유수량을 줄이면서 할당수량도 같이
-- 풀어야 하는데(P-01), 어느 할당을 소진한 것인지가 있어야 정확히 푼다.
--
-- 수정 · 삭제하지 않는다. 잘못 집었으면 <b>음수 실적</b>을 한 줄 더 넣는다 —
-- 재고이력과 같은 방식이다. 지우면 '집었다가 되돌렸다' 가 사라져, 무엇을
-- 카트에 실었는지 되짚을 수 없다.
-- ============================================================================
CREATE TABLE tb_outbound_pick (
    pick_seq        bigint      GENERATED ALWAYS AS IDENTITY,

    outbound_seq    bigint      NOT NULL,
    line_seq        bigint      NOT NULL,

    -- 어느 빈에서. 재고 행을 그대로 가리킨다 (SKU × 빈 × 화주).
    stock_seq       bigint      NOT NULL,
    -- 어느 할당을 소진했나. 출고확정이 이 할당을 푼다.
    alloc_seq       bigint,

    -- 집은 수량. 되돌릴 때는 음수다.
    picked_qty      integer     NOT NULL,

    picked_by       varchar(30) NOT NULL,
    picked_at       timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark          varchar(300),

    CONSTRAINT pk_outbound_pick  PRIMARY KEY (pick_seq),
    CONSTRAINT fk_outpick_out    FOREIGN KEY (outbound_seq) REFERENCES tb_outbound (outbound_seq)
                                 ON DELETE CASCADE,
    CONSTRAINT fk_outpick_line   FOREIGN KEY (line_seq)     REFERENCES tb_outbound_line (line_seq)
                                 ON DELETE CASCADE,
    CONSTRAINT fk_outpick_stock  FOREIGN KEY (stock_seq)    REFERENCES tb_stock (stock_seq),
    CONSTRAINT fk_outpick_alloc  FOREIGN KEY (alloc_seq)    REFERENCES tb_stock_alloc (alloc_seq),
    -- 0 은 아무 일도 안 일어난 것이라 남길 이유가 없다
    CONSTRAINT ck_outpick_qty    CHECK (picked_qty <> 0)
);

CREATE INDEX ix_outpick_line  ON tb_outbound_pick (line_seq, picked_at);
CREATE INDEX ix_outpick_out   ON tb_outbound_pick (outbound_seq, picked_at);
-- 출고확정이 '이 지시가 어느 빈에서 얼마를 가져갔나' 를 모으는 경로
CREATE INDEX ix_outpick_stock ON tb_outbound_pick (stock_seq);

COMMENT ON TABLE  tb_outbound_pick            IS '피킹 실적 (OUT-PG-004). 수정·삭제 없이 음수로 되돌린다';
COMMENT ON COLUMN tb_outbound_pick.stock_seq  IS '어느 빈에서 집었나. 출고확정이 이 재고를 줄인다';
COMMENT ON COLUMN tb_outbound_pick.alloc_seq  IS '어느 할당을 소진했나. 출고확정이 이 할당을 푼다';


-- ============================================================================
-- 4. 공통코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('REASON_PICK_SHORT', '피킹 결품 사유', '빈에 없음 · 파손 · 수량 부족', 'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('NOT_FOUND', '빈에 없음',   '전산에는 있는데 그 자리에 물건이 없다',   10),
        ('SHORT_QTY', '수량 부족',   '있긴 한데 적다',                          20),
        ('DAMAGED',   '파손',        '있는데 팔 수 없는 상태다',                30),
        ('WRONG_SKU', '다른 물건',   '그 자리에 다른 SKU 가 있다',              40),
        ('ETC',       '기타',        '위에 없는 사유. 비고에 적는다',           90)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'REASON_PICK_SHORT';


-- ============================================================================
-- 5. 메뉴
--
-- 권한은 V4 가 이미 깔아 뒀다 (OUT_ASSIGN · OUT_PICK · OUT_SHORTAGE).
-- 0차 시드가 출고 권한을 미리 정의해 둔 것이라, 여기서는 메뉴만 건다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('OUT_PICKING',   '피킹',      'GRP_OUT', 'outbound-picking',   '📲', 'OUT_PICK',     30),
        ('OUT_SHORTAGES', '피킹 결품', 'GRP_OUT', 'outbound-shortages', '⚠',  'OUT_SHORTAGE', 40)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
