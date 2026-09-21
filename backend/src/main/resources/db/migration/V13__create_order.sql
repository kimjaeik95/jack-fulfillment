-- ============================================================================
-- 주문 (3차 · A섹터)
--   ORD-PG-009  주문 등록 (직접 생성)
--   ORD-PG-010  주문 라인 SKU 매핑
--   ORD-PG-011  주문 확정
--
-- 채널주문만 담는다. 판매오더(B2B)는 넣지 않는다.
--
-- 한때 둘을 한 테이블에 담고 order_type 으로 가르려 했다. 접었다 — 판매오더는
-- 11차(SLS)고 지금 만들 것이 아니다. 쓰지도 않을 유형 컬럼과 고객 FK 를
-- 미리 깔아 두면, 화면에는 안 보이는데 테이블에는 있는 칸이 여덟 차수 동안
-- 남는다. 그 칸이 무엇인지 나중에 아무도 확신하지 못한다.
--
-- 그래서 채널주문 하나로 좁힌다. 11차에 판매오더를 만들 때 컬럼을 더하는
-- 것은 그때 한 번이면 되고, 그 시점에는 무엇이 필요한지도 정확히 안다.
--
-- OMS 는 없다 (INT-IF-001 개발 취소). 주문은 세 갈래로 들어온다.
--   화면 등록      사람이 한 건씩. 채널에서 누락된 건을 보정할 때 쓴다
--   POST /orders   외부가 JSON 으로. 화면도 같은 입구를 쓴다
--   데모 생성기    시연용으로 여러 건을 한 번에 (local 전용)
--
-- 셋이 같은 서비스를 지나므로 검증 규칙이 갈라지지 않는다.
-- ============================================================================


-- ============================================================================
-- 1. 주문 헤더 (ORD-002, ORD-006)
--
-- 수령인과 배송지를 FK 가 아니라 값으로 복사해 둔다 (스냅샷).
--
-- 애초에 가리킬 곳도 없다. 오픈마켓에서 산 개인은 거래처로 등록하지 않는다 —
-- 주문마다 회사를 만들 이유가 없기 때문이다. 설령 등록한다 해도 FK 로 걸면
-- 주소 한 줄을 고치는 순간 지난 주문의 배송지가 전부 따라 바뀌고, '어디로
-- 보냈었나' 에 답할 수 없게 된다. ORD-003 이 채널 표시값에 대해 요구하는
-- 것과 같은 이유다.
-- ============================================================================
CREATE TABLE tb_order (
    order_seq      bigint      GENERATED ALWAYS AS IDENTITY,
    -- ORD-20260921-0001
    order_no       varchar(30) NOT NULL,

    /* 어느 채널로 들어왔나. 자사몰도 채널이라 빈 주문은 없다. */
    channel_seq    bigint      NOT NULL,
    /*
     * 채널이 준 주문번호. 우리 order_no 와는 다르다.
     *
     * 같은 주문이 두 번 전송돼도 한 건만 남아야 한다 (ORD-001 멱등).
     * 아래 부분 유니크가 그것을 막는다. OMS 가 아직 없지만 제약은 지금
     * 걸어 둔다 — 나중에 넣으려면 이미 쌓인 중복부터 치워야 한다.
     *
     * 비어 있을 수 있다. 채널에서 누락된 건을 화면에서 손으로 넣을 때는
     * 채널 주문번호를 모를 수 있기 때문이다.
     */
    ext_order_no   varchar(50),

    /*
     * 주문 상태. 코드그룹 SALES_ORDER_STATUS (ORD-006)
     *   RECEIVED   접수. 라인 SKU 가 아직 확정되지 않았을 수 있다
     *   CONFIRMED  확정. 검증을 통과해 할당 대상이 되었다
     *   ALLOCATED  할당완료
     *   PICKING    출고 진행 (4차)
     *   SHIPPED    출고완료 (4차)
     *   CANCELED   취소
     *
     * 헤더 상태는 라인 상태의 집계로 판정한다 (ORD-007). 일부 라인만 품절인
     * 주문이 있으므로 헤더 하나로는 표현되지 않는다.
     */
    order_status   varchar(20) NOT NULL DEFAULT 'RECEIVED',

    /* 채널 주문일시. 우리가 받은 시각(created_at)과 다르다. */
    ordered_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    /* ---- 수령인 · 배송지 스냅샷 (ORD-002) ------------------------------ */
    receiver_name  varchar(50)  NOT NULL,
    receiver_phone varchar(30),
    zip_code       varchar(10),
    address        varchar(200) NOT NULL,
    address_detail varchar(200),
    /* 배송 요청사항. 주문마다 다르다. */
    delivery_memo  varchar(200),

    remark         varchar(300),

    /* 취소 (ORD-008). 사유가 필수다 — 왜 취소됐는지 없으면 설명할 수 없다. */
    canceled_by    varchar(30),
    canceled_at    timestamp,
    cancel_reason  varchar(30),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_order        PRIMARY KEY (order_seq),
    CONSTRAINT uk_order_no     UNIQUE (order_no),

    CONSTRAINT fk_order_channel FOREIGN KEY (channel_seq) REFERENCES tb_channel (channel_seq),

    CONSTRAINT ck_order_status CHECK (order_status IN
        ('RECEIVED', 'CONFIRMED', 'ALLOCATED', 'PICKING', 'SHIPPED', 'CANCELED')),

    -- 취소했으면 누가 · 왜 가 있어야 한다 (ORD-008)
    CONSTRAINT ck_order_canceled CHECK (
        order_status <> 'CANCELED' OR (canceled_by IS NOT NULL AND cancel_reason IS NOT NULL))
);

/*
 * 같은 채널의 같은 주문번호는 한 번만 (ORD-001 멱등).
 *
 * 부분 유니크를 쓰는 이유는 ext_order_no 가 비어 있을 수 있기 때문이다.
 * NULL 끼리는 = 로 비교되지 않아 그냥 UNIQUE 로 걸어도 손으로 넣은 주문이
 * 여러 건일 때 막히지는 않지만, 의도가 조건에 드러나지 않는다. 명시한다.
 */
CREATE UNIQUE INDEX ux_order_ext ON tb_order (channel_seq, ext_order_no)
 WHERE ext_order_no IS NOT NULL;

CREATE INDEX ix_order_status   ON tb_order (order_status, ordered_at DESC);
CREATE INDEX ix_order_when     ON tb_order (ordered_at DESC);
CREATE INDEX ix_order_channel  ON tb_order (channel_seq, ordered_at DESC);
-- 수령인으로 찾는 일이 잦다 (CS 문의)
CREATE INDEX ix_order_receiver ON tb_order (receiver_name);

COMMENT ON TABLE  tb_order                IS '주문 — 채널에서 수집한 B2C 주문. 판매오더(B2B)는 11차. ORD-002';
COMMENT ON COLUMN tb_order.ext_order_no   IS '채널이 준 주문번호. 채널과 묶어 멱등 처리 (ORD-001)';
COMMENT ON COLUMN tb_order.receiver_name  IS '수령인 스냅샷. 기준정보가 바뀌어도 과거 주문은 불변';
COMMENT ON COLUMN tb_order.order_status   IS '코드그룹 SALES_ORDER_STATUS. 라인 상태의 집계로 판정 (ORD-007)';


-- ============================================================================
-- 2. 주문 라인 (ORD-003, ORD-007)
--
-- 라인마다 상태를 둔다. 한 주문에서 일부 SKU 만 품절 · 취소될 수 있고, 그때
-- 나머지 라인은 그대로 출고되어야 하기 때문이다. 헤더 상태 하나로는 '절반만
-- 할당됨' 을 표현할 수 없다.
--
-- 채널이 보여 준 상품명과 옵션을 함께 보관한다 (ORD-003). 나중에 제품명을
-- 바꿔도 과거 주문의 표시명은 그대로여야 한다 — CS 가 고객과 통화할 때
-- 고객이 보고 산 이름으로 얘기해야 하기 때문이다.
-- ============================================================================
CREATE TABLE tb_order_line (
    line_seq       bigint      GENERATED ALWAYS AS IDENTITY,
    order_seq      bigint      NOT NULL,
    line_no        integer     NOT NULL,

    /*
     * 내부 SKU. 비어 있을 수 있다 (ORD-004, ORD-005).
     *
     * 채널이 준 코드를 매핑 테이블로 변환하는데, 매핑이 없으면 변환에
     * 실패한다. 그때 주문을 버리지 않고 SKU 없이 적재한 뒤 오류대기로
     * 보낸다 — 버리면 고객은 주문했는데 우리에게는 없는 상태가 된다.
     *
     * 화면에서 직접 등록할 때는 SKU 를 골라 넣으므로 비지 않는다.
     */
    sku_seq        bigint,

    /* ---- 채널이 준 값 (ORD-003 스냅샷) --------------------------------- */
    ext_product_code varchar(50),
    ext_option_code  varchar(50),
    /* 주문 당시 채널 표시 상품명 · 옵션. 기준정보가 바뀌어도 보존한다. */
    ext_product_name varchar(200),
    ext_option_name  varchar(200),

    order_qty      integer     NOT NULL,

    /*
     * 라인 상태. 코드그룹 SALES_LINE_STATUS (ORD-007)
     *   RECEIVED   접수. SKU 미확정일 수 있다
     *   MAPPED     SKU 확정
     *   ALLOCATED  할당완료
     *   SHORTAGE   결품 — 할당 가능 수량이 부족 (ALC-003)
     *   CANCELED   취소
     */
    line_status    varchar(20) NOT NULL DEFAULT 'RECEIVED',

    /* 판매단가 · 금액. 정산은 이 시스템 범위 밖이라 참고값이다. */
    unit_price     numeric(13,2),
    line_amount    numeric(15,2),

    remark         varchar(300),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_order_line   PRIMARY KEY (line_seq),
    CONSTRAINT uk_order_line_no UNIQUE (order_seq, line_no),
    CONSTRAINT fk_ordl_order   FOREIGN KEY (order_seq) REFERENCES tb_order (order_seq)
                               ON DELETE CASCADE,
    CONSTRAINT fk_ordl_sku     FOREIGN KEY (sku_seq)   REFERENCES tb_sku (sku_seq),

    CONSTRAINT ck_ordl_qty     CHECK (order_qty > 0),
    CONSTRAINT ck_ordl_status  CHECK (line_status IN
        ('RECEIVED', 'MAPPED', 'ALLOCATED', 'SHORTAGE', 'CANCELED')),
    -- SKU 가 정해지지 않았는데 할당됐다고 할 수는 없다
    CONSTRAINT ck_ordl_mapped  CHECK (line_status IN ('RECEIVED', 'CANCELED') OR sku_seq IS NOT NULL)
);

/*
 * 한 주문에 같은 SKU 를 두 줄 담지 않는다.
 *
 * 두 줄이면 몇 개를 보내야 하는지 정할 수 없고, 할당도 어느 줄에 붙일지
 * 모른다. 합치는 것은 주문을 넣는 쪽이 할 일이다.
 *
 * SKU 가 아직 없는 줄(미매핑)은 이 제약에서 빠진다 — 매핑되기 전에는 같은
 * 상품인지 알 수 없기 때문이다.
 */
CREATE UNIQUE INDEX ux_ordl_sku ON tb_order_line (order_seq, sku_seq)
 WHERE sku_seq IS NOT NULL;

CREATE INDEX ix_ordl_order  ON tb_order_line (order_seq, line_no);
CREATE INDEX ix_ordl_sku    ON tb_order_line (sku_seq, line_status);
CREATE INDEX ix_ordl_status ON tb_order_line (line_status);

COMMENT ON TABLE  tb_order_line                  IS '주문 상세 — 라인마다 상태를 갖는다 (ORD-007)';
COMMENT ON COLUMN tb_order_line.sku_seq          IS '내부 SKU. 미매핑이면 NULL 이고 오류대기로 간다 (ORD-005)';
COMMENT ON COLUMN tb_order_line.ext_product_name IS '주문 당시 채널 표시명. 기준정보가 바뀌어도 보존 (ORD-003)';
COMMENT ON COLUMN tb_order_line.line_status      IS '코드그룹 SALES_LINE_STATUS';


-- ============================================================================
-- 3. 재고할당이 주문을 가리키게 한다
--
-- tb_stock_alloc 은 V8 에서 만들어 두고 쓰지 않았다. 주문상세 FK 자리를
-- order_no · order_line_no 문자열로 들고 있었는데, 주문 테이블이 없던
-- 시절이라 FK 를 걸 수 없었기 때문이다.
--
-- 이제 주문이 생겼으므로 제대로 가리킨다. 문자열 컬럼은 남겨 둔다 — 지우면
-- 이미 쓰는 코드가 있는지 확인해야 하고, 지금은 그 코드가 없지만 4차에서
-- 조회 편의로 쓸 수 있다.
-- ============================================================================
ALTER TABLE tb_stock_alloc ADD COLUMN order_line_seq bigint;

ALTER TABLE tb_stock_alloc
    ADD CONSTRAINT fk_stalloc_ordline FOREIGN KEY (order_line_seq)
        REFERENCES tb_order_line (line_seq);

CREATE INDEX ix_stalloc_ordline ON tb_stock_alloc (order_line_seq);

COMMENT ON COLUMN tb_stock_alloc.order_line_seq IS
    '주문상세 — 이 할당이 어느 주문 줄을 위한 것인가 (STK-005)';


-- ============================================================================
-- 4. 공통코드
--
-- 유형 코드그룹은 두지 않는다. 주문이 전부 채널주문이라 값이 하나뿐이고,
-- 값이 하나인 코드그룹은 화면에서 고를 것이 없다.
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES
  ('SALES_ORDER_STATUS', '주문 상태',      '접수 · 확정 · 할당 · 출고 · 취소', 'SYSTEM', 'system'),
  ('SALES_LINE_STATUS',  '주문 라인 상태', '라인별 진행 상태 (ORD-007)',      'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.color, v.sort_order, 'system'
  FROM (VALUES
        ('SALES_ORDER_STATUS', 'RECEIVED',  '접수',     '들어왔다. SKU 미확정일 수 있다',   'gray',  10),
        ('SALES_ORDER_STATUS', 'CONFIRMED', '확정',     '검증 통과. 할당 대상이다',          'blue',  20),
        ('SALES_ORDER_STATUS', 'ALLOCATED', '할당완료', '재고를 잡았다',                     'green', 30),
        ('SALES_ORDER_STATUS', 'PICKING',   '출고진행', '피킹 · 패킹 중 (4차)',              'teal',  40),
        ('SALES_ORDER_STATUS', 'SHIPPED',   '출고완료', '나갔다 (4차)',                      'slate', 50),
        ('SALES_ORDER_STATUS', 'CANCELED',  '취소',     '사유와 함께 거둬들였다',            'red',   90),

        ('SALES_LINE_STATUS', 'RECEIVED',  '접수',     'SKU 미확정일 수 있다',            'gray',  10),
        ('SALES_LINE_STATUS', 'MAPPED',    'SKU확정',  '내부 SKU 가 정해졌다',            'blue',  20),
        ('SALES_LINE_STATUS', 'ALLOCATED', '할당완료', '재고를 잡았다',                   'green', 30),
        ('SALES_LINE_STATUS', 'SHORTAGE',  '결품',     '할당 가능 수량이 부족 (ALC-003)', 'amber', 40),
        ('SALES_LINE_STATUS', 'CANCELED',  '취소',     '',                                'red',   90)
       ) AS v(group_id, code_id, code_name, description, color, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 5. 권한 · 메뉴
--
-- 주문 등록과 할당을 나눈다. 할당은 재고를 건드리는 일이라 창고 쪽 권한이다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('ORD_ORDER',  '주문 관리', 'ORD', '주문 > 주문관리', 200, 'system'),
  ('ORD_ALLOC',  '재고할당',  'ORD', '주문 > 재고할당', 210, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('ORD_ORDER', 'RCUDX'),
        -- 할당은 만들고(C) 푸는(D) 것이다. 고치는 개념이 없다.
        ('ORD_ALLOC', 'RCD')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

/*
 * 시스템관리자에게 새 권한의 모든 액션을 준다.
 *
 * V12 가 '정의된 모든 권한의 모든 액션' 을 SYS_ADMIN 에게 줬다. 그런데 그건
 * 그 시점에 있던 권한들이다. 여기서 만든 ORD_* 는 V12 가 본 적이 없으므로
 * 같이 주지 않으면 관리자만 주문 화면에서 막힌다.
 *
 * 문장은 V12 와 글자 그대로 같다 — '아직 안 준 것을 준다' 이므로 언제 돌려도
 * 결과가 같다. 앞으로 권한을 만드는 마이그레이션에도 이 블록을 같이 넣는다.
 * created_by 도 V12 와 맞춘다. V12 주석의 되돌리기 질의가 이 행들까지 같이
 * 지워야 맞기 때문이다.
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

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by) VALUES
  ('GRP_ORDER', '주문 · 할당', NULL, NULL, NULL, NULL, 35, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('ORD_ORDERS', '주문 관리', 'GRP_ORDER', 'orders', '🧾', 'ORD_ORDER', 10)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
