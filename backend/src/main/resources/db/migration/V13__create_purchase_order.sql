-- ============================================================================
-- 구매오더 (PUR-PG-003 ~ PUR-PG-005 / 요구사항 PUR-004 ~ PUR-006)
--
-- 구매요청이 "사 주세요" 라면 구매오더는 <b>공급처와의 약속</b>이다.
-- 여기서부터 돈이 나가고, 물건이 들어올 근거가 생긴다.
--
-- 그래서 요청과 결정적으로 다른 것이 둘 있다.
--
--   1 단가를 박아 둔다 (PUR-004)
--     기준정보의 현재 원가를 조인해 보여 주면, 원가가 바뀐 다음 날
--     작년 발주 금액이 소급해서 달라진다. 발주 시점 값을 라인에 복사한다.
--
--   2 입고를 추적한다 (PUR-006)
--     발주수량과 기입고수량을 들고, 잔량은 DB 가 뺀다. 100 발주에 80 만
--     들어오면 잔량 20 이고 상태는 부분입고다.
--
-- 구매요청 없이 바로 발주하는 길도 연다 (PUR-005). 신상품 초도물량처럼
-- 본사가 먼저 판단하는 품목이 있어서, 요청 FK 를 NULL 로 허용한다.
-- ============================================================================


-- ============================================================================
-- 1. 구매오더 — 헤더 (PUR-004, PUR-005, PUR-006)
-- ============================================================================
CREATE TABLE tb_purchase_order (
    order_seq     bigint      GENERATED ALWAYS AS IDENTITY,
    -- PO-20260915-0001
    order_no      varchar(30) NOT NULL,

    supplier_seq  bigint      NOT NULL,
    -- 받을 물류센터. 데이터 범위를 플랜트 → 운영 조직으로 거슬러 판정한다.
    plant_seq     bigint      NOT NULL,

    /*
     * 근거가 된 구매요청. 없을 수 있다 (PUR-005).
     *
     * 신상품 초도물량처럼 센터의 요청 없이 본사가 바로 발주하는 경우가
     * 있다. 그때를 위해 별도 테이블을 만들지 않는다 — 발주는 요청에서
     * 왔든 아니든 공급처에 나가는 같은 문서다.
     */
    request_seq   bigint,

    -- 코드그룹 ORDER_STATUS
    order_status  varchar(20) NOT NULL DEFAULT 'DRAFT',

    /*
     * 발주일 · 납품예정일.
     *
     * 발주일은 확정(ISSUED) 시점에 찍힌다. 작성 중에는 비어 있다 —
     * 만들어 두고 며칠 뒤에 내보내는 일이 흔해서, 만든 날을 발주일로
     * 쓰면 납기 계산이 틀어진다.
     */
    order_date    date,
    due_date      date        NOT NULL,

    -- 코드그룹 PAY_TERM. 공급처 기준정보에서 가져와 담되 건별로 바꿀 수 있다.
    pay_term      varchar(20) NOT NULL,
    remark        varchar(300),

    issued_by     varchar(30),
    issued_at     timestamp,
    -- 취소 (PUR-006). 사유가 필수다.
    canceled_by   varchar(30),
    canceled_at   timestamp,
    cancel_reason varchar(300),

    created_by    varchar(30) NOT NULL,
    created_at    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,

    CONSTRAINT pk_purchase_order    PRIMARY KEY (order_seq),
    CONSTRAINT uk_purchase_order_no UNIQUE (order_no),
    CONSTRAINT fk_purord_supplier   FOREIGN KEY (supplier_seq) REFERENCES tb_supplier (supplier_seq),
    CONSTRAINT fk_purord_plant      FOREIGN KEY (plant_seq)    REFERENCES tb_plant (plant_seq),
    CONSTRAINT fk_purord_request    FOREIGN KEY (request_seq)  REFERENCES tb_purchase_request (request_seq),
    -- 작성 → 발주 → 부분입고 → 입고완료 / 취소 (PUR-006)
    CONSTRAINT ck_purord_status     CHECK (order_status IN
                                      ('DRAFT','ISSUED','PARTIAL','CLOSED','CANCELED')),
    -- 발주된 뒤에는 누가 언제 냈는지가 있어야 한다. 공급처에 나간 문서라
    -- 책임 소재가 남지 않으면 안 된다.
    CONSTRAINT ck_purord_issued     CHECK (
        (order_status IN ('DRAFT','CANCELED'))
     OR (issued_by IS NOT NULL AND issued_at IS NOT NULL AND order_date IS NOT NULL)),
    -- 취소는 왜 취소했는지가 있어야 한다 (PUR-006)
    CONSTRAINT ck_purord_cancel     CHECK (order_status <> 'CANCELED'
                                        OR (canceled_by IS NOT NULL AND cancel_reason IS NOT NULL))
);

CREATE INDEX ix_purord_supplier ON tb_purchase_order (supplier_seq, order_date DESC);
CREATE INDEX ix_purord_plant    ON tb_purchase_order (plant_seq, order_date DESC);
CREATE INDEX ix_purord_status   ON tb_purchase_order (order_status, due_date);
CREATE INDEX ix_purord_request  ON tb_purchase_order (request_seq);
-- 납기가 급한 미완료 발주부터 보는 경로 (진행현황 화면의 기본 정렬)
CREATE INDEX ix_purord_due      ON tb_purchase_order (due_date)
 WHERE order_status IN ('ISSUED','PARTIAL');


-- ============================================================================
-- 2. 구매오더 상세 (PUR-004, PUR-006)
--
-- 여기가 입고 추적의 자리다.
--
--   발주수량   공급처와 약속한 수량. 발주 후에는 바뀌지 않는다.
--   기입고수량 실제로 들어와 검수를 통과한 누계 (INB-004).
--   잔량       발주 − 기입고. DB 가 계산한다.
--
-- 잔량을 저장하지 않는 이유는 재고의 판매가능수량과 같다. 저장하면 언젠가
-- 발주 − 기입고 와 어긋나고, 그때 어느 쪽이 맞는지 아무도 모른다.
--
-- 기입고수량은 반대로 저장한다. 검수 이력을 매번 합산하면 회차가 쌓일수록
-- 느려지고, 무엇보다 INB-004 가 '검수 완료 경로 외에서는 직접 수정하지
-- 않는다' 를 요구한다 — 올리는 통로를 하나로 두겠다는 뜻이다.
-- ============================================================================
CREATE TABLE tb_purchase_order_line (
    line_seq          bigint      GENERATED ALWAYS AS IDENTITY,
    order_seq         bigint      NOT NULL,
    line_no           integer     NOT NULL,

    sku_seq           bigint      NOT NULL,

    /*
     * 어느 요청 줄에서 왔나. 직접 발주면 비어 있다.
     *
     * 이걸 들고 있어야 '승인수량보다 많이 발주했나' 를 볼 수 있다.
     * 요청 100 · 승인 80 인데 발주를 50 + 50 으로 두 번 내면 승인을
     * 넘는다 — 그 판정을 서비스가 이 값으로 한다.
     */
    request_line_seq  bigint,

    order_qty         integer     NOT NULL,
    -- 실제로 들어와 검수를 통과한 누계. 입고(INB-004)가 올린다.
    received_qty      integer     NOT NULL DEFAULT 0,
    /*
     * 남은 수량. 발주 − 기입고.
     *
     * 음수를 막지 않는다. 100 발주에 110 이 들어오는 초과입고가 실제로
     * 있고(INB-005), 그건 잔량이 −10 인 것이 아니라 초과 승인이 필요한
     * 별도 사건이다. 여기서 막으면 그 사건을 기록할 자리가 없어진다.
     */
    remain_qty        integer     GENERATED ALWAYS AS (order_qty - received_qty) STORED,

    /*
     * 발주 시점 단가 (PUR-004).
     *
     * 기준정보(tb_product.cost_amount)의 현재 값을 조인해 보여 주면,
     * 원가가 바뀐 다음 날 작년 발주 금액이 소급해서 달라진다. 발주 시점
     * 값을 복사해 둔다.
     */
    unit_price        numeric(15,2) NOT NULL,
    -- 줄 금액. 수량 × 단가. 헤더 합계는 저장하지 않고 이것을 더한다.
    line_amount       numeric(17,2) GENERATED ALWAYS AS (order_qty * unit_price) STORED,

    remark            varchar(300),

    CONSTRAINT pk_purord_line     PRIMARY KEY (line_seq),
    CONSTRAINT uk_purord_line_no  UNIQUE (order_seq, line_no),
    -- 한 발주에 같은 SKU 를 두 줄 담을 수 없다. 두 줄이면 얼마를 받아야
    -- 하는지 정할 수 없고, 입고 검수도 어느 줄에 붙일지 모른다.
    CONSTRAINT uk_purord_line_sku UNIQUE (order_seq, sku_seq),
    CONSTRAINT fk_purordl_order   FOREIGN KEY (order_seq) REFERENCES tb_purchase_order (order_seq)
                                  ON DELETE CASCADE,
    CONSTRAINT fk_purordl_sku     FOREIGN KEY (sku_seq)   REFERENCES tb_sku (sku_seq),
    CONSTRAINT fk_purordl_reqline FOREIGN KEY (request_line_seq)
                                  REFERENCES tb_purchase_request_line (line_seq),
    CONSTRAINT ck_purordl_qty     CHECK (order_qty > 0),
    CONSTRAINT ck_purordl_recv    CHECK (received_qty >= 0),
    CONSTRAINT ck_purordl_price   CHECK (unit_price >= 0)
);

CREATE INDEX ix_purordl_order   ON tb_purchase_order_line (order_seq, line_no);
CREATE INDEX ix_purordl_sku     ON tb_purchase_order_line (sku_seq);
CREATE INDEX ix_purordl_reqline ON tb_purchase_order_line (request_line_seq);
-- 아직 안 들어온 줄만 뽑는 경로 (입고예정 생성 · 진행현황)
CREATE INDEX ix_purordl_remain  ON tb_purchase_order_line (order_seq)
 WHERE order_qty > received_qty;


-- ============================================================================
-- 3. 공통코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by) VALUES
  ('ORDER_STATUS', '구매오더 상태', '작성 · 발주 · 부분입고 · 입고완료 · 취소', 'SYSTEM', 'system'),
  ('REASON_PO_CANCEL', '발주취소 사유', '공급 불가 · 납기 지연 · 단가 변경 · 수요 취소', 'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('ORDER_STATUS', 'DRAFT',    '작성중',   '아직 공급처에 나가지 않았다. 고칠 수 있다.', 'gray',  10),
        ('ORDER_STATUS', 'ISSUED',   '발주',     '공급처에 나갔다. 입고를 기다린다.',          'blue',  20),
        ('ORDER_STATUS', 'PARTIAL',  '부분입고', '일부만 들어왔다. 잔량이 남아 있다.',         'amber', 30),
        ('ORDER_STATUS', 'CLOSED',   '입고완료', '발주수량이 다 들어왔다.',                    'green', 40),
        ('ORDER_STATUS', 'CANCELED', '취소',     '사유와 함께 거둬들였다.',                    'red',   50)
       ) AS v(group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('REASON_PO_CANCEL', 'NO_SUPPLY',  '공급 불가',   '공급처가 물량을 대지 못한다',      10),
        ('REASON_PO_CANCEL', 'LATE',       '납기 지연',   '납기를 맞출 수 없어 거둬들인다',   20),
        ('REASON_PO_CANCEL', 'PRICE',      '단가 변경',   '합의한 단가로 진행할 수 없다',     30),
        ('REASON_PO_CANCEL', 'NO_DEMAND',  '수요 취소',   '더 이상 필요하지 않다',            40),
        ('REASON_PO_CANCEL', 'WRONG_ORDER','발주 오류',   '잘못 낸 발주를 거둬들인다',        50)
       ) AS v(group_id, code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 4. 권한
--
-- 새로 만들지 않는다. V3 가 이미 둘을 정의해 뒀고 그대로 맞는다.
--
--   PUR_PO_ISSUE  (CRU)  등록 · 수정 · 발주 확정
--   PUR_PO_CANCEL (RU)   발주 취소
--
-- 취소를 따로 떼어 둔 것이 V3 의 판단이고, 그 판단이 옳다. 발주를 내는
-- 일과 이미 나간 발주를 거둬들이는 일은 무게가 다르다 — 뒤쪽은 공급처가
-- 이미 생산에 들어갔을 수 있어서 돈이 걸린다.
--
-- 진행현황(PUR-PG-005)도 권한을 만들지 않는다. 발주를 읽어 보여 줄 뿐이라
-- PUR_PO_ISSUE 의 R 로 충분하다.
-- ============================================================================


-- ============================================================================
-- 5. 메뉴
--
-- V12 가 만든 '구매' 그룹 아래, 구매요청 다음에 붙인다. 일이 일어나는
-- 순서가 요청 → 결재 → 발주 → 진행확인이다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('PUR_ORDER',    '구매오더',      'GRP_PUR', 'purchase-orders',  '📄', 'PUR_PO_ISSUE', 30),
        ('PUR_PROGRESS', '발주 진행현황', 'GRP_PUR', 'purchase-progress', '📈', 'PUR_PO_ISSUE', 40)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
