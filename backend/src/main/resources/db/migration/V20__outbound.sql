-- ============================================================================
-- 출고지시 (OUT-PG-001, OUT-PG-002 / 4차)
--
-- 할당까지 끝난 주문을 창고 작업으로 바꾸는 문서다.
--
-- 주문과 따로 두는 이유가 셋이다.
--
--   1 보는 사람이 다르다
--     주문은 고객과의 약속이고 지시는 창고가 할 일이다. 주소가 바뀌거나
--     주문이 취소되는 것은 주문의 사건이고, 누가 어느 통로를 도는지는
--     지시의 사건이다. 한 표에 담으면 서로의 변경이 섞인다.
--
--   2 작업이 붙는다
--     작업자 배정 · 피킹 · 검수 · 패킹이 전부 이 문서에 매달린다 (B · C섹터).
--     주문에 달면 주문 표가 창고 작업 표가 된다.
--
--   3 나중에 1:1 이 아니게 된다
--     같은 수취인의 주문 두 건을 한 번에 집어 한 박스로 보내는 일(합포)이
--     생기면 지시 1 : 주문 N 이 된다. 그때 표를 갈아엎지 않도록, 주문을
--     가리키는 자리를 처음부터 라인에 둔다.
--
-- 지금은 주문 1 건 = 지시 1 건이다. 묶음 작업(웨이브)은 개발 취소(OUT-PG-008)
-- 이고, 합포 후보 찾기도 개발 취소(PAC-PG-007)라 묶을 근거가 아직 없다.
-- ============================================================================


-- ============================================================================
-- 1. 출고지시 - 헤더
-- ============================================================================
CREATE TABLE tb_outbound (
    outbound_seq    bigint      GENERATED ALWAYS AS IDENTITY,
    -- OUT-20260923-0001
    outbound_no     varchar(30) NOT NULL,

    /*
     * 나가는 센터.
     *
     * 주문이 아니라 할당된 재고가 정한다. 주문에는 센터가 없다 - 어느
     * 창고에서 보낼지는 할당이 빈을 고르면서 정해진다. 그래서 지시를
     * 만들 때 할당된 빈들의 센터를 보고 채운다.
     *
     * 데이터 범위 판정도 이 값으로 한다 (센터 -> 운영 조직).
     */
    plant_seq       bigint      NOT NULL,

    -- 코드그룹 OUTBOUND_STATUS
    outbound_status varchar(20) NOT NULL DEFAULT 'CREATED',

    /*
     * 단포인가.
     *
     * 한 줄 한 개짜리 주문이다. 피킹 동선이 달라서 따로 센다 - 단포는
     * 여러 지시를 한 번에 돌며 같은 SKU 를 몰아 집는 편이 빠르고, 여러
     * 품목짜리는 지시별로 집어야 섞이지 않는다.
     *
     * 계산해서 넣지 않고 만들 때 박아 둔다. 주문 줄은 확정 뒤에 안 바뀌고,
     * 매번 세면 목록 질의가 줄 수만큼 무거워진다.
     */
    single_pack     char(1)     NOT NULL DEFAULT 'N',

    -- 언제까지 내보내야 하나. 채널 약속이 없으면 비어 있다.
    ship_due_date   date,

    /*
     * 지시 시각과 출고 확정 시각.
     *
     * 만든 때와 나간 때를 나눠 둔다. 만들어 두고 다음 날 나가는 일이 흔한데,
     * 만든 날을 출고일로 쓰면 배송 지연 판정이 전부 하루씩 틀어진다.
     */
    instructed_by   varchar(30) NOT NULL,
    instructed_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_by      varchar(30),
    shipped_at      timestamp,

    -- 취소. 사유가 필수다 - 창고가 하던 일을 되돌리는 것이라 근거가 남아야 한다.
    canceled_by     varchar(30),
    canceled_at     timestamp,
    cancel_reason   varchar(300),

    remark          varchar(300),

    created_by      varchar(30) NOT NULL,
    created_at      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      varchar(30),
    updated_at      timestamp,

    CONSTRAINT pk_outbound        PRIMARY KEY (outbound_seq),
    CONSTRAINT uk_outbound_no     UNIQUE (outbound_no),
    CONSTRAINT fk_outbound_plant  FOREIGN KEY (plant_seq) REFERENCES tb_plant (plant_seq),
    CONSTRAINT ck_outbound_status CHECK (outbound_status IN
                                    ('CREATED','PICKING','PICKED','PACKING','PACKED',
                                     'SHIPPED','CANCELED')),
    CONSTRAINT ck_outbound_single CHECK (single_pack IN ('Y','N')),
    -- 나갔으면 누가 언제 냈는지가 있어야 한다
    CONSTRAINT ck_outbound_shipped CHECK (outbound_status <> 'SHIPPED'
                                       OR (shipped_by IS NOT NULL AND shipped_at IS NOT NULL)),
    -- 취소했으면 사유가 있어야 한다
    CONSTRAINT ck_outbound_cancel  CHECK (outbound_status <> 'CANCELED'
                                       OR cancel_reason IS NOT NULL)
);

CREATE INDEX ix_outbound_plant ON tb_outbound (plant_seq, outbound_status);
-- '아직 안 나간 지시' - 작업 목록의 기본 경로
CREATE INDEX ix_outbound_open  ON tb_outbound (outbound_status, instructed_at)
    WHERE outbound_status NOT IN ('SHIPPED','CANCELED');

COMMENT ON TABLE  tb_outbound             IS '출고지시 (OUT-PG-002)';
COMMENT ON COLUMN tb_outbound.plant_seq   IS '나가는 센터 - 할당된 빈이 정한다';
COMMENT ON COLUMN tb_outbound.single_pack IS '단포 여부 (1줄 1개). 피킹 동선이 다르다';


-- ============================================================================
-- 2. 출고지시 - 라인
--
-- 주문 줄을 그대로 가리킨다. 수량을 복사하지 않고 지시수량을 따로 드는
-- 이유는, 한 주문 줄이 나뉘어 나갈 수 있기 때문이다 - 결품으로 6 개만 먼저
-- 보내고 4 개는 나중에 보내면 지시 줄이 둘이 된다 (B섹터).
--
-- 주문을 여기서 가리킨다. 헤더가 아니라 라인에 두면, 나중에 합포로 지시
-- 하나가 주문 여럿을 담게 될 때 표를 바꾸지 않아도 된다. 지금은 '한 지시의
-- 모든 줄은 같은 주문' 이라는 검증만 서비스가 건다.
-- ============================================================================
CREATE TABLE tb_outbound_line (
    line_seq        bigint      GENERATED ALWAYS AS IDENTITY,
    outbound_seq    bigint      NOT NULL,
    line_no         integer     NOT NULL,

    -- 주문 줄. 이 지시가 무엇을 내보내는지의 근거다.
    order_line_seq  bigint      NOT NULL,
    sku_seq         bigint      NOT NULL,

    -- 내보내라고 지시한 수량. 할당수량에서 온다.
    instructed_qty  integer     NOT NULL,
    -- 실제로 집은 수량 (B섹터). 아직 0 이다.
    picked_qty      integer     NOT NULL DEFAULT 0,
    -- 남은 수량 - DB 가 뺀다. 발주의 remain_qty 와 같은 방식이다.
    remain_qty      integer     GENERATED ALWAYS AS (instructed_qty - picked_qty) STORED,

    remark          varchar(300),

    CONSTRAINT pk_outbound_line  PRIMARY KEY (line_seq),
    CONSTRAINT uk_outbl_line_no  UNIQUE (outbound_seq, line_no),
    -- 한 지시에 같은 주문 줄을 두 번 담을 수 없다. 두 줄이면 얼마를
    -- 집어야 하는지 정할 수 없고, 피킹 스캔도 어느 줄에 붙일지 모른다.
    CONSTRAINT uk_outbl_ordline  UNIQUE (outbound_seq, order_line_seq),
    CONSTRAINT fk_outbl_outbound FOREIGN KEY (outbound_seq)   REFERENCES tb_outbound (outbound_seq)
                                 ON DELETE CASCADE,
    CONSTRAINT fk_outbl_ordline  FOREIGN KEY (order_line_seq) REFERENCES tb_order_line (line_seq),
    CONSTRAINT fk_outbl_sku      FOREIGN KEY (sku_seq)        REFERENCES tb_sku (sku_seq),
    CONSTRAINT ck_outbl_qty      CHECK (instructed_qty > 0),
    CONSTRAINT ck_outbl_picked   CHECK (picked_qty >= 0 AND picked_qty <= instructed_qty)
);

CREATE INDEX ix_outbl_outbound ON tb_outbound_line (outbound_seq, line_no);
-- '이 주문 줄이 어느 지시로 나갔나' - 출고대상 조회가 이미 지시된 줄을 빼는 경로
CREATE INDEX ix_outbl_ordline  ON tb_outbound_line (order_line_seq);

COMMENT ON TABLE  tb_outbound_line                IS '출고지시 라인 (OUT-PG-002)';
COMMENT ON COLUMN tb_outbound_line.order_line_seq IS '주문 줄. 헤더가 아니라 여기 둔다 - 나중에 합포로 지시 1 : 주문 N 이 되어도 표가 안 바뀐다';
COMMENT ON COLUMN tb_outbound_line.picked_qty     IS '실제로 집은 수량 (B섹터 피킹)';


-- ============================================================================
-- 3. 공통코드
--
-- 상태는 전 과정을 미리 넣는다. A섹터가 쓰는 것은 CREATED 와 CANCELED 뿐이지만,
-- 값이 없으면 화면이 뒤 단계를 보여줄 수 없고 지금 흐름이 어디까지 가는지도
-- 안 보인다. 주문 상태가 PICKING 과 SHIPPED 를 3차에 미리 넣어 둔 것과 같다.
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('OUTBOUND_STATUS', '출고지시 상태', '지시 · 피킹 · 패킹 · 출고 · 취소', 'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('CREATED',  '지시',     '창고가 집을 일이 생겼다. 아직 아무도 안 잡았다.', 'gray',   10),
        ('PICKING',  '피킹중',   '작업자가 배정되어 집는 중이다.',                  'blue',   20),
        ('PICKED',   '피킹완료', '다 집었다. 검수를 기다린다.',                     'teal',   30),
        ('PACKING',  '패킹중',   '박스에 담는 중이다.',                             'violet', 40),
        ('PACKED',   '패킹완료', '박스가 닫혔다. 송장을 기다린다.',                 'amber',  50),
        ('SHIPPED',  '출고완료', '나갔다. 재고가 줄었다.',                          'green',  60),
        ('CANCELED', '취소',     '사유와 함께 거둬들였다. 할당은 풀린다.',          'red',    90)
       ) AS v(code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'OUTBOUND_STATUS';

-- 출고지시 취소 사유
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('REASON_OUT_CANCEL', '출고지시 취소 사유', '주문 취소 · 재고 문제 · 지시 오류', 'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('ORDER_CANCEL', '주문 취소', '고객이 주문을 거둬들였다',          10),
        ('STOCK_ISSUE',  '재고 문제', '집으려 보니 물건이 없거나 못 판다', 20),
        ('WRONG_INSTR',  '지시 오류', '잘못 만든 지시를 거둬들인다',       30),
        ('ETC',          '기타',      '위에 없는 사유. 비고에 적는다',     90)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'REASON_OUT_CANCEL';


-- ============================================================================
-- 4. 권한
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('OUT_TARGET', '출고대상 조회', 'OUT', '출고 > 출고대상', 300, 'system'),
  ('OUT_ORDER',  '출고지시',      'OUT', '출고 > 출고지시', 310, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 출고대상은 보는 화면이다. 거기서 만드는 것은 지시 권한으로 판정한다.
        ('OUT_TARGET', 'RX'),
        -- 지시는 만들고(C) 거둬들인다(D). 만든 지시의 내용을 고치는 개념은
        -- 없다 - 잘못 만들었으면 취소하고 다시 만든다.
        ('OUT_ORDER',  'RCDX')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

/*
 * 시스템관리자에게 새 권한의 모든 액션을 준다 (V12 · V13 과 같은 문장).
 * 여기서 만든 OUT_* 는 V12 가 본 적이 없으므로 같이 주지 않으면 관리자만
 * 출고 화면에서 막힌다.
 */
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, a.perm_seq, a.action_code, NULL, 'v17.sysadmin'
  FROM tb_role r
  JOIN tb_permission_action a ON TRUE
 WHERE r.role_id = 'SYS_ADMIN'
   AND NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq
          AND x.perm_seq = a.perm_seq
          AND x.action_code = a.action_code);


-- ============================================================================
-- 5. 메뉴
--
-- '출고 · 패킹' 그룹을 새로 만든다. 주문 · 할당(35) 다음이다 - 물건이 나가는
-- 순서가 주문 -> 할당 -> 출고다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
VALUES ('GRP_OUT', '출고 · 패킹', NULL, NULL, '📦', NULL, 40, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('OUT_TARGETS', '출고대상', 'GRP_OUT', 'outbound-targets', '🎯', 'OUT_TARGET', 10),
        ('OUT_ORDERS',  '출고지시', 'GRP_OUT', 'outbounds',        '🚚', 'OUT_ORDER',  20)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
