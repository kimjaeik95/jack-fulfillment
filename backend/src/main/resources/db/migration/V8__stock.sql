-- ============================================================================
-- 재고 코어 (4단계 재고관리 · A섹터)
--   INV-PG-001  재고 현황 조회
--   INV-PG-002  재고 상세 조회
--   INV-PG-003  재고이동 이력 조회
--   INV-PG-004  할당 이력 조회
--
-- 이 세 테이블이 뒤 단계 전부의 바탕이다. 입고(6차) · 할당(8차) · 출고(9차)가
-- 여기에 수량을 쓴다. 요구사항 6.2 가 "4단계를 확정하기 전에 6·8·9 단계를
-- 만들면 전면 재작업" 이라고 경고한 그 자리다.
--
-- 성격이 다른 세 테이블이다 (핵심원칙 P-02).
--   tb_stock          현재 상태  UPDATE 한다      지금 몇 개 있나
--   tb_stock_history  기록      INSERT 만 한다    왜 그렇게 됐나
--   tb_stock_alloc    기록      INSERT 만 한다    누구에게 잡혀 있나
--
-- 어디에도 DELETE 는 없다. 재고가 0 이 되어도 행은 남는다 — 지우면 '왜 0
-- 인지' 를 설명할 수 없다.
-- ============================================================================


-- ============================================================================
-- 1. 재고 (STK-001 ~ STK-004)
--
-- 재고 1행 = 로케이션 × SKU × 거래처 (STK-002).
--
-- 판매가능수량을 컬럼으로 저장하지 않고 DB 가 계산하게 둔다.
--   판매가능 = 보유 − 할당 − 판매불가   (P-01, STK-004)
-- 저장하면 어긋난다 — 수량을 바꾸는 곳이 입고 · 출고 · 할당 · 해제 · 조정 ·
-- 실사로 여섯 군데인데, 그중 하나가 판매가능 갱신을 빠뜨리면 조용히 틀린
-- 숫자가 남는다. GENERATED 로 두면 그 실수가 불가능하다.
--
-- 음수 금지도 DB 가 건다 (STK-003 '어떤 경로로도 음수가 되지 않는다').
-- 애플리케이션만 믿으면 동시에 두 요청이 들어왔을 때 둘 다 통과한다.
-- ============================================================================
CREATE TABLE tb_stock (
    stock_seq      bigint    GENERATED ALWAYS AS IDENTITY,
    location_seq   bigint    NOT NULL,
    sku_seq        bigint    NOT NULL,
    -- 거래처. 3PL 처럼 화주별로 재고를 섞을 수 없는 경우에 쓴다.
    -- 자사 물류는 대개 비어 있어 NULL 을 허용한다.
    vendor_seq     bigint,

    -- 실제로 창고에 있는 수량. 출고 확정 시점에 줄어든다 (P-01).
    qty_on_hand    integer   NOT NULL DEFAULT 0,
    -- 주문에 잡혀 있는 수량. 할당 시점에 늘어난다 (P-01).
    qty_allocated  integer   NOT NULL DEFAULT 0,
    -- 불량 · 오염 · 검수대기 등 팔 수 없는 수량 (STK-004).
    qty_unsellable integer   NOT NULL DEFAULT 0,

    -- 팔 수 있는 수량. 저장하지 않고 DB 가 계산한다.
    qty_available  integer   GENERATED ALWAYS AS
                             (qty_on_hand - qty_allocated - qty_unsellable) STORED,

    -- 마지막 실사일자 (STK-001). 실사(INV-PG-009)가 갱신한다.
    last_counted_at timestamp,

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_stock        PRIMARY KEY (stock_seq),
    CONSTRAINT fk_stock_loc    FOREIGN KEY (location_seq) REFERENCES tb_location (location_seq),
    CONSTRAINT fk_stock_sku    FOREIGN KEY (sku_seq)      REFERENCES tb_sku (sku_seq),
    CONSTRAINT fk_stock_vendor FOREIGN KEY (vendor_seq)   REFERENCES tb_partner (partner_seq),

    -- 수량은 음수가 될 수 없다 (STK-003)
    CONSTRAINT ck_stock_on_hand    CHECK (qty_on_hand    >= 0),
    CONSTRAINT ck_stock_allocated  CHECK (qty_allocated  >= 0),
    CONSTRAINT ck_stock_unsellable CHECK (qty_unsellable >= 0),
    -- 할당 + 판매불가가 보유를 넘을 수 없다. 넘으면 판매가능이 음수가 된다.
    CONSTRAINT ck_stock_available  CHECK (qty_on_hand - qty_allocated - qty_unsellable >= 0)
);

-- 동일 조합 중복 행 생성 불가 (STK-002).
--
-- 거래처가 NULL 일 수 있어 인덱스를 둘로 나눈다. NULL 끼리는 = 로 비교되지
-- 않아 하나짜리 유니크로는 중복이 막히지 않는다 — 채널 SKU 매핑(V5)에서
-- 같은 이유로 같은 처리를 했다.
CREATE UNIQUE INDEX ux_stock_key_vendor ON tb_stock (location_seq, sku_seq, vendor_seq)
 WHERE vendor_seq IS NOT NULL;
CREATE UNIQUE INDEX ux_stock_key_novendor ON tb_stock (location_seq, sku_seq)
 WHERE vendor_seq IS NULL;

-- 'SKU 가 어디에 몇 개 있나' 가 가장 잦은 질의다 (재고 현황 조회)
CREATE INDEX ix_stock_sku ON tb_stock (sku_seq, location_seq);
-- 빈 하나를 스캔해 그 안의 재고를 보는 경로 (피킹 · 실사)
CREATE INDEX ix_stock_loc ON tb_stock (location_seq, sku_seq);


-- 2. 재고이력 (STK-007)
--
-- 수량이 바뀐 모든 사건을 남긴다. 수정 · 삭제 불가.
--
-- 변경 전/후 수량을 함께 적는다 (P-04 '사유코드 · 시점 · 대조수량 3종').
-- 변동수량만 남기면 나중에 합계가 안 맞을 때 어느 시점부터 틀어졌는지
-- 찾을 수 없다. 전/후를 적어 두면 그 줄에서 바로 드러난다.
--
-- 수량 변경과 이력 생성은 같은 트랜잭션이어야 한다 (STK-007). 서비스가
-- 그것을 보장한다 — 한쪽만 남으면 재고와 이력이 어긋난다.
-- ============================================================================
CREATE TABLE tb_stock_history (
    history_seq   bigint      GENERATED ALWAYS AS IDENTITY,
    stock_seq     bigint      NOT NULL,

    -- 코드그룹 STOCK_MOVE (RECEIVE/ISSUE/RETURN/ADJUST/MOVE/UNSELLABLE)
    move_type     varchar(20) NOT NULL,
    -- 어느 수량이 움직였나 — ON_HAND / ALLOCATED / UNSELLABLE
    qty_field     varchar(20) NOT NULL,
    -- 변동수량. 늘면 양수, 줄면 음수.
    qty_delta     integer     NOT NULL,
    qty_before    integer     NOT NULL,
    qty_after     integer     NOT NULL,

    -- 사유코드 (P-04). 코드그룹은 move_type 에 따라 다르다 —
    -- 조정이면 REASON_ADJUST, 판매불가면 REASON_INSPECT 식이다.
    reason_code   varchar(30),
    reason_group  varchar(30),
    -- 사유코드로 설명되지 않는 부분을 사람이 적는다
    remark        varchar(300),

    -- 이 변경을 일으킨 전표. 입고 · 출고 · 조정 · 이동 번호가 들어간다.
    -- 해당 테이블은 6차 이후에 생기므로 지금은 FK 를 걸지 않는다.
    ref_type      varchar(20),
    ref_no        varchar(50),

    occurred_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    varchar(30) NOT NULL,

    CONSTRAINT pk_stock_history  PRIMARY KEY (history_seq),
    CONSTRAINT fk_sthist_stock   FOREIGN KEY (stock_seq) REFERENCES tb_stock (stock_seq),
    CONSTRAINT ck_sthist_field   CHECK (qty_field IN ('ON_HAND', 'ALLOCATED', 'UNSELLABLE')),
    -- 전 + 변동 = 후. 이게 어긋난 이력은 남길 이유가 없다.
    CONSTRAINT ck_sthist_math    CHECK (qty_before + qty_delta = qty_after)
);

CREATE INDEX ix_sthist_stock ON tb_stock_history (stock_seq, occurred_at DESC);
-- 기간으로 훑는 조회 (재고이동 이력)
CREATE INDEX ix_sthist_when  ON tb_stock_history (occurred_at DESC);
-- 전표에서 거슬러 오는 경로 (이 입고로 무엇이 바뀌었나)
CREATE INDEX ix_sthist_ref   ON tb_stock_history (ref_type, ref_no);


-- ============================================================================
-- 3. 재고할당 (STK-005, STK-006)
--
-- 주문이 재고를 잡아 둔 기록. 수정 · 삭제 불가 — 해제도 UPDATE 가 아니라
-- 해제 컬럼을 채우는 방식이다. 할당했다가 푼 사실 자체가 이력이다.
--
-- 주문상세는 7차에 생긴다. 지금은 번호만 들고 FK 를 걸지 않는다 — 없는
-- 테이블을 참조하는 제약은 만들 수 없고, 지금 만들어 두면 주문 테이블
-- 설계가 이 컬럼에 끌려간다.
-- ============================================================================
CREATE TABLE tb_stock_alloc (
    alloc_seq      bigint      GENERATED ALWAYS AS IDENTITY,
    stock_seq      bigint      NOT NULL,

    -- 주문상세 참조 (7차). 지금은 번호만.
    order_no       varchar(50),
    order_line_no  integer,

    qty_allocated  integer     NOT NULL,
    -- 코드그룹 ALLOC_STATUS (ALLOCATED/RELEASED/PICKED)
    alloc_status   varchar(20) NOT NULL DEFAULT 'ALLOCATED',
    allocated_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 해제 (STK-006). 해제 이유는 필수다 — 왜 풀었는지가 남지 않으면
    -- 재고가 왜 돌아왔는지 설명할 수 없다.
    qty_released   integer     NOT NULL DEFAULT 0,
    release_reason varchar(30),
    released_at    timestamp,

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_stock_alloc  PRIMARY KEY (alloc_seq),
    CONSTRAINT fk_stalloc_stock FOREIGN KEY (stock_seq) REFERENCES tb_stock (stock_seq),
    CONSTRAINT ck_stalloc_qty      CHECK (qty_allocated > 0),
    CONSTRAINT ck_stalloc_released CHECK (qty_released >= 0 AND qty_released <= qty_allocated),
    -- 해제했으면 이유가 있어야 한다 (STK-006)
    CONSTRAINT ck_stalloc_reason   CHECK (qty_released = 0 OR release_reason IS NOT NULL)
);

CREATE INDEX ix_stalloc_stock ON tb_stock_alloc (stock_seq, allocated_at DESC);
CREATE INDEX ix_stalloc_order ON tb_stock_alloc (order_no, order_line_no);
CREATE INDEX ix_stalloc_when  ON tb_stock_alloc (allocated_at DESC);


-- ============================================================================
-- 4. 공통코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by) VALUES
  ('STOCK_MOVE',   '재고 이동유형', '입고 · 출고 · 반품 · 조정 · 이동 · 판매불가', 'SYSTEM', 'system'),
  ('ALLOC_STATUS', '할당 상태',     '할당 · 해제 · 출고소진',                      'SYSTEM', 'system'),
  ('STOCK_REF',    '재고 변경 출처', '입고 · 출고 · 조정 · 이동 · 실사 전표',       'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('STOCK_MOVE', 'RECEIVE',    '입고',      '구매입고 · 반품입고 · 이동입고', 10),
        ('STOCK_MOVE', 'ISSUE',      '출고',      '출고 확정으로 보유수량 차감',    20),
        ('STOCK_MOVE', 'RETURN',     '반품',      '고객 반품 회수',                 30),
        ('STOCK_MOVE', 'ADJUST',     '조정',      '실사 차이 · 분실 · 파손',        40),
        ('STOCK_MOVE', 'MOVE',       '이동',      '로케이션간 이동',                50),
        ('STOCK_MOVE', 'UNSELLABLE', '판매불가',  '정상 ↔ 불량 상태 전환',          60),
        ('STOCK_MOVE', 'ALLOCATE',   '할당',      '주문 할당으로 가용수량 차감',    70),
        ('STOCK_MOVE', 'RELEASE',    '할당해제',  '할당 해제로 가용수량 복원',      80),

        ('ALLOC_STATUS', 'ALLOCATED', '할당중',   '재고를 잡아 둔 상태',            10),
        ('ALLOC_STATUS', 'RELEASED',  '해제',     '주문 취소 등으로 풀린 상태',     20),
        ('ALLOC_STATUS', 'PICKED',    '출고소진', '피킹되어 보유수량에서 빠짐',     30),

        ('STOCK_REF', 'INBOUND',   '입고전표',   NULL, 10),
        ('STOCK_REF', 'OUTBOUND',  '출고전표',   NULL, 20),
        ('STOCK_REF', 'ADJUST',    '조정전표',   NULL, 30),
        ('STOCK_REF', 'MOVE',      '이동전표',   NULL, 40),
        ('STOCK_REF', 'STOCKTAKE', '실사전표',   NULL, 50)
       ) AS v(group_id, code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 5. 권한
--
-- 새로 만들지 않는다. V3 가 이미 재고 권한을 설계해 두었다.
--
--   QRY_STOCK          재고 조회        A섹터가 쓴다
--   INV_ADJ_APPROVE    재고조정 승인    C섹터가 쓴다
--   INV_COUNT_APPROVE  실사 승인        D섹터가 쓴다
--   INV_QTY_EDIT       업무 수량 직접 수정
--
-- QRY_STOCK 은 이미 본사 기준정보 · 센터장 · 구매 · CS 가 갖고 있다.
-- 재고는 보는 사람이 많다 — CS 는 '재고 있나요' 에 답해야 하고, 구매는
-- 발주할지 정해야 한다.
--
-- 시스템 관리자에게도 조회를 준다 (정책 P001 취지 — 조회만).
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r CROSS JOIN tb_permission p
 WHERE r.role_id = 'SYS_ADMIN' AND p.perm_id = 'QRY_STOCK'
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission x
                    WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
                      AND x.action_code = 'R');

-- 재고 다운로드(X). QRY_STOCK 에 액션이 R 만 있어 추가한다 —
-- 재고 목록을 엑셀로 받는 일이 잦다 (NFR-UI-02).
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, 'X', 'system'
  FROM tb_permission p
 WHERE p.perm_id = 'QRY_STOCK'
   AND NOT EXISTS (SELECT 1 FROM tb_permission_action a
                    WHERE a.perm_seq = p.perm_seq AND a.action_code = 'X');


-- ============================================================================
-- 6. 메뉴
--
-- 재고는 기준정보와 성격이 달라 그룹을 새로 만든다. 기준정보 아래에 두면
-- 스무 개가 넘어 찾을 수 없다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
VALUES ('GRP_STOCK', '재고', NULL, NULL, '📦', NULL, 45, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('INV_STOCKS',  '재고 현황', 'GRP_STOCK', 'stocks',        '📊', 'QRY_STOCK', 10),
        ('INV_HISTORY', '재고 이력', 'GRP_STOCK', 'stock-history', '🧾', 'QRY_STOCK', 20)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
