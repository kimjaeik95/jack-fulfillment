-- ============================================================================
-- 구매요청 (PUR-PG-001, PUR-PG-002 / 요구사항 PUR-001 ~ PUR-003)
--
-- "이게 모자라니 사 주세요" 를 센터가 본사에 올린다. 아직 발주가 아니다 —
-- 발주는 구매오더(PUR-PG-003 이후)가 하고, 이 단계는 <b>무엇이 얼마나
-- 필요한지</b> 를 정하는 데까지다.
--
-- 재고조정(V11)과 모양이 닮았다. 요청과 승인을 나누고, 요청자는 자기
-- 요청을 승인할 수 없다 (AUTH-008). 다른 점은 <b>부분승인</b> 이다 —
-- 조정은 승인 아니면 반려지만, 구매요청은 "100 개 달랬는데 60 개만" 이
-- 정상적인 결론이다. 예산과 창고 자리가 유한하기 때문이다.
--
-- 그래서 라인에 요청수량과 승인수량을 따로 둔다. 승인수량을 요청수량에
-- 덮어쓰면 "얼마를 달라고 했었나" 가 사라지고, 다음 분기에 얼마를 요청해야
-- 하는지 판단할 근거가 없어진다.
-- ============================================================================


-- ============================================================================
-- 1. 구매요청 — 헤더 (PUR-001)
--
-- 센터 단위다. 창고가 아니다 — 어느 창고에 넣을지는 물건이 도착한 뒤
-- 적치(INB-PG-005)가 정하고, 요청 시점에는 아직 모른다.
-- ============================================================================
CREATE TABLE tb_purchase_request (
    request_seq    bigint      GENERATED ALWAYS AS IDENTITY,
    -- REQ-20260915-0001
    request_no     varchar(30) NOT NULL,

    -- 받을 물류센터. 데이터 범위를 플랜트 → 운영 조직으로 거슬러 판정한다.
    plant_seq      bigint      NOT NULL,

    -- 코드그룹 REQUEST_STATUS
    request_status varchar(20) NOT NULL DEFAULT 'REQUESTED',

    /*
     * 요청사유. 코드그룹 REASON_PURCHASE.
     *
     * 자유 입력으로 두지 않는 이유는 이 값이 나중에 집계 대상이기 때문이다.
     * "왜 사는가" 가 재고부족인지 신상품인지에 따라 구매 담당이 보는 눈이
     * 다르고, 재고부족 감지(STK-012)가 요청을 자동으로 만들 때도 코드를
     * 찍어야 한다. 문장으로 적어 두면 그때 셀 수가 없다.
     *
     * 문장으로 적고 싶은 것은 remark 에 따로 받는다.
     */
    reason_code    varchar(30) NOT NULL,
    remark         varchar(300),

    request_date   date        NOT NULL DEFAULT CURRENT_DATE,
    -- 언제까지 필요한가. 발주 납기를 정하는 근거다.
    required_date  date        NOT NULL,

    requested_by   varchar(30) NOT NULL,
    requested_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 승인 · 반려 (PUR-003). 반려는 사유가 필수다.
    decided_by     varchar(30),
    decided_at     timestamp,
    decide_remark  varchar(300),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_purchase_request    PRIMARY KEY (request_seq),
    CONSTRAINT uk_purchase_request_no UNIQUE (request_no),
    CONSTRAINT fk_purreq_plant        FOREIGN KEY (plant_seq) REFERENCES tb_plant (plant_seq),
    CONSTRAINT ck_purreq_status       CHECK (request_status IN
                                       ('REQUESTED','APPROVED','PARTIAL','REJECTED','CANCELED')),
    -- 처리된 요청은 처리자와 시각이 있어야 한다. 누가 언제 승인했는지
    -- 남지 않는 승인은 승인이 아니다.
    CONSTRAINT ck_purreq_decided      CHECK (
        (request_status IN ('REQUESTED','CANCELED'))
     OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    -- 반려는 왜 반려했는지가 있어야 한다 (PUR-003)
    CONSTRAINT ck_purreq_reject       CHECK (request_status <> 'REJECTED'
                                          OR decide_remark IS NOT NULL),
    -- 필요일이 요청일보다 앞설 수는 없다
    CONSTRAINT ck_purreq_date         CHECK (required_date >= request_date)
);

CREATE INDEX ix_purreq_plant  ON tb_purchase_request (plant_seq, requested_at DESC);
CREATE INDEX ix_purreq_status ON tb_purchase_request (request_status, requested_at DESC);
CREATE INDEX ix_purreq_who    ON tb_purchase_request (requested_by, requested_at DESC);
-- 납기가 급한 것부터 보는 경로 (구매 담당의 작업 목록)
CREATE INDEX ix_purreq_need   ON tb_purchase_request (required_date)
 WHERE request_status IN ('REQUESTED','APPROVED','PARTIAL');


-- ============================================================================
-- 2. 구매요청 상세 (PUR-002)
--
-- SKU 단위다. 제품 단위로 받지 않는다 — 같은 코트라도 블랙 M 이 모자란
-- 것이지 베이지 L 까지 모자란 것은 아니고, 발주도 색상 · 사이즈별로
-- 나간다. 제품 단위로 받으면 그 아래 무엇을 얼마나 살지는 아무도 모른다.
-- ============================================================================
CREATE TABLE tb_purchase_request_line (
    line_seq          bigint      GENERATED ALWAYS AS IDENTITY,
    request_seq       bigint      NOT NULL,
    line_no           integer     NOT NULL,

    sku_seq           bigint      NOT NULL,
    request_qty       integer     NOT NULL,

    /*
     * 승인수량. 승인 전에는 비어 있다.
     *
     * 요청수량에 덮어쓰지 않는다. 덮으면 "얼마를 달라고 했었나" 가 사라져,
     * 다음에 얼마를 요청해야 하는지 판단할 근거가 없어진다. 60 개만 승인된
     * 것과 60 개를 요청한 것은 전혀 다른 사실이다.
     *
     * 0 도 유효한 승인수량이다 — 그 줄만 빼고 나머지는 승인한다는 뜻이다.
     */
    approved_qty      integer,

    -- 희망 공급처. 요청자가 아는 거래처가 있으면 적는다. 구매 담당이
    -- 그대로 따를 의무는 없어서 참고값이다.
    pref_supplier_seq bigint,
    remark            varchar(300),

    CONSTRAINT pk_purreq_line     PRIMARY KEY (line_seq),
    -- 공급처로 등록된 거래처만 가리키게 하는 고정값. 고객 전용 거래처를 넣으면
    -- 아래 복합 FK 가 거부한다 (V5 uk_partner_supplier 참조).
    supplier_chk char(1) GENERATED ALWAYS AS ('Y') STORED,
    CONSTRAINT uk_purreq_line_no  UNIQUE (request_seq, line_no),
    -- 한 요청에 같은 SKU 를 두 줄 담을 수 없다. 두 줄이면 얼마를 사야
    -- 하는지 정할 수 없고, 합치는 것은 요청자가 할 일이다.
    CONSTRAINT uk_purreq_line_sku UNIQUE (request_seq, sku_seq),
    CONSTRAINT fk_purreql_req     FOREIGN KEY (request_seq) REFERENCES tb_purchase_request (request_seq)
                                  ON DELETE CASCADE,
    CONSTRAINT fk_purreql_sku     FOREIGN KEY (sku_seq)     REFERENCES tb_sku (sku_seq),
    CONSTRAINT fk_purreql_sup     FOREIGN KEY (pref_supplier_seq, supplier_chk)
                                  REFERENCES tb_partner (partner_seq, supplier_yn),
    CONSTRAINT ck_purreql_qty     CHECK (request_qty > 0),
    -- 승인수량은 요청수량을 넘을 수 없다 (PUR-003).
    -- 더 사야 한다면 그건 승인이 아니라 새 요청이다.
    CONSTRAINT ck_purreql_approved CHECK (approved_qty IS NULL
                                       OR (approved_qty >= 0 AND approved_qty <= request_qty))
);

CREATE INDEX ix_purreql_req ON tb_purchase_request_line (request_seq, line_no);
-- ' 이 SKU 를 누가 요청해 뒀나' — 중복 요청을 막는 경로
CREATE INDEX ix_purreql_sku ON tb_purchase_request_line (sku_seq);


-- ============================================================================
-- 3. 공통코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by) VALUES
  ('REQUEST_STATUS', '구매요청 상태', '요청 · 승인 · 부분승인 · 반려 · 취소', 'SYSTEM', 'system'),
  ('REASON_PURCHASE', '구매요청 사유', '재고부족 · 신상품 · 행사 · 교체', 'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('REQUEST_STATUS', 'REQUESTED', '승인대기',  '올렸고 아직 처리되지 않았다',          'amber', 10),
        ('REQUEST_STATUS', 'APPROVED',  '승인',      '요청한 수량 그대로 승인되었다',        'green', 20),
        ('REQUEST_STATUS', 'PARTIAL',   '부분승인',  '일부 수량만 승인되었다',               'blue',  30),
        ('REQUEST_STATUS', 'REJECTED',  '반려',      '반려 사유와 함께 돌려보냈다',          'red',   40),
        ('REQUEST_STATUS', 'CANCELED',  '취소',      '요청자가 스스로 거둬들였다',           'gray',  50)
       ) AS v(group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('REASON_PURCHASE', 'SHORTAGE',  '재고부족',   '판매가능 수량이 기준치 아래로 떨어졌다', 10),
        ('REASON_PURCHASE', 'NEW_ITEM',  '신상품',     '새로 전개하는 품목의 초도 물량',         20),
        ('REASON_PURCHASE', 'PROMOTION', '행사 대비',  '기획전 · 시즌 행사 물량',                30),
        ('REASON_PURCHASE', 'REPLACE',   '불량 교체',  '판매불가로 빠진 만큼 채운다',            40),
        ('REASON_PURCHASE', 'ETC',       '기타',       '위에 없는 사유. 비고에 적는다',          90)
       ) AS v(group_id, code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 4. 권한
--
-- 승인 권한(PUR_REQ_APPROVE)은 V3 가 이미 정의해 뒀다. 요청 쪽만 새로
-- 만든다 — 재고조정에서 INV_ADJUST 와 INV_ADJ_APPROVE 를 나눈 것과 같다.
--
-- 나누는 이유는 한 사람이 둘 다 하면 승인이 통제가 아니라 절차가 되기
-- 때문이다 (AUTH-008). 둘 다 가진 사람이 있어도 서비스가 '자기 요청 자기
-- 승인' 을 막는다.
--
-- SYS_ADMIN 에게는 주지 않는다. 구매는 업무이고 시스템 관리자의 일이
-- 아니다 — V3 가 업무 권한을 주지 않은 것과 같은 이유다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by) VALUES
  ('PUR_REQUEST', '구매요청 등록', 'PUR', '구매 > 구매요청', 205, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 올리고(C) 고치고(U) 거둬들인다(D). 승인 뒤에는 셋 다 막힌다.
        ('PUR_REQUEST', 'RCUD')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);


-- ============================================================================
-- 5. 메뉴
--
-- '구매' 그룹을 새로 만든다. 재고(GRP_STOCK, 45) 다음, 앞으로 생길
-- 입고 · 출고보다 앞이다 — 물건이 들어오는 순서가 구매 → 입고다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
VALUES ('GRP_PUR', '구매', NULL, NULL, '🛒', NULL, 50, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('PUR_REQ',     '구매요청',      'GRP_PUR', 'purchase-requests',        '📝', 'PUR_REQUEST',     10),
        ('PUR_REQ_APV', '구매요청 승인', 'GRP_PUR', 'purchase-request-approve', '✅', 'PUR_REQ_APPROVE', 20)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
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

    -- 공급처로 등록된 거래처만 가리키게 하는 고정값. 고객 전용 거래처를 넣으면
    -- 아래 복합 FK 가 거부한다 (V5 uk_partner_supplier 참조).
    supplier_chk char(1) GENERATED ALWAYS AS ('Y') STORED,
    CONSTRAINT pk_purchase_order    PRIMARY KEY (order_seq),
    CONSTRAINT uk_purchase_order_no UNIQUE (order_no),
    CONSTRAINT fk_purord_supplier   FOREIGN KEY (supplier_seq, supplier_chk)
                                    REFERENCES tb_partner (partner_seq, supplier_yn),
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
