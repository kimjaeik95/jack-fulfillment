-- ============================================================================
-- 재고 컬럼 순서를 재고주소 5축이 앞에 모이도록 다시 세운다
--
-- V21 · V22 가 supplier_chk · warehouse_seq · plant_seq 를 ALTER 로 붙였다.
-- PostgreSQL 은 붙인 컬럼을 맨 뒤에만 놓기 때문에, 재고 한 행을 특정하는
-- 다섯 항목이 앞뒤로 흩어졌다.
--
--   location_seq sku_seq supplier_seq ..수량.. ..감사.. warehouse_seq plant_seq
--   └─── 3축 ────────────────────┘                     └───── 나머지 2축 ────┘
--
-- 저장 방식에는 영향이 없지만 읽는 사람에게는 영향이 있다. 테이블을 열거나
-- ERD 를 볼 때 '재고주소가 무엇인가' 가 구조에서 바로 드러나야 한다.
--
-- PostgreSQL 에는 컬럼 순서를 바꾸는 ALTER 가 없어서 테이블을 다시 만든다.
-- 지금 하는 이유는 재고가 0 건이기 때문이다. 쌓인 뒤에는 전체를 복사하는
-- 동안 테이블이 잠긴다.
--
-- 순서는 계층을 따라 큰 것부터 내려온다.
--   플랜트 → 창고 → 빈 → SKU → 공급처
-- 앞의 셋이 어디에 있나, 뒤의 둘이 무엇이 · 어디서 온 것이냐다.
--
-- 제약 · 인덱스 · 생성컬럼은 이름까지 그대로 다시 만든다. 하나라도 이름이
-- 바뀌면 나중에 이 파일을 읽는 사람이 V10 · V21 · V22 와 대조하지 못한다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 자식들이 붙잡고 있는 손을 잠시 놓는다
--
-- tb_stock 을 지우려면 이 넷을 먼저 떼어야 한다. 끝에서 같은 이름으로 다시
-- 붙인다 — 가리키는 대상도 값도 바뀌지 않는다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_stock_history     DROP CONSTRAINT fk_sthist_stock;
ALTER TABLE tb_stock_alloc       DROP CONSTRAINT fk_stalloc_stock;
ALTER TABLE tb_stock_adjust_line DROP CONSTRAINT fk_stadjl_stock;
ALTER TABLE tb_stocktake_line    DROP CONSTRAINT fk_takel_stock;

ALTER TABLE tb_stock RENAME TO tb_stock_old;

-- 인덱스 이름은 테이블을 따라 바뀌지 않는다. 새 테이블에서 같은 이름을
-- 쓰려면 먼저 비켜 두어야 한다.
ALTER INDEX pk_stock                RENAME TO pk_stock_old;
ALTER INDEX ix_stock_loc            RENAME TO ix_stock_loc_old;
ALTER INDEX ix_stock_sku            RENAME TO ix_stock_sku_old;
ALTER INDEX ix_stock_plant          RENAME TO ix_stock_plant_old;
ALTER INDEX ix_stock_wh             RENAME TO ix_stock_wh_old;
ALTER INDEX ux_stock_key_supplier   RENAME TO ux_stock_key_supplier_old;
ALTER INDEX ux_stock_key_nosupplier RENAME TO ux_stock_key_nosupplier_old;


-- ----------------------------------------------------------------------------
-- 2. 다시 세운다
--
-- 판매가능수량과 supplier_chk 는 여전히 DB 가 계산한다 (P-01). 저장하면
-- 어긋난다 — 수량을 바꾸는 곳이 여섯 군데인데 그중 하나가 갱신을 빠뜨리면
-- 조용히 틀린 숫자가 남는다.
-- ----------------------------------------------------------------------------
CREATE TABLE tb_stock (
    stock_seq      bigint    GENERATED ALWAYS AS IDENTITY,

    -- ── 재고주소 5축 ─────────────────────────────────────────────────
    plant_seq      bigint    NOT NULL,
    warehouse_seq  bigint    NOT NULL,
    location_seq   bigint    NOT NULL,
    sku_seq        bigint    NOT NULL,
    -- 이 재고가 어느 공급처에서 왔나 (V21). 같은 빈 · 같은 SKU 라도 공급처가
    -- 다르면 행이 갈라진다. 이동입고처럼 출처가 없으면 NULL.
    supplier_seq   bigint,
    -- 공급처로 등록된 거래처만 가리키게 하는 고정값 (V20). 고객 전용 거래처를
    -- 넣으면 아래 fk_stock_supplier 가 거부한다.
    supplier_chk   char(1)   GENERATED ALWAYS AS ('Y') STORED,

    -- ── 수량 ────────────────────────────────────────────────────────
    qty_on_hand    integer   NOT NULL DEFAULT 0,
    qty_allocated  integer   NOT NULL DEFAULT 0,
    qty_unsellable integer   NOT NULL DEFAULT 0,
    qty_available  integer   GENERATED ALWAYS AS
                             (qty_on_hand - qty_allocated - qty_unsellable) STORED,

    last_counted_at timestamp,

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_stock PRIMARY KEY (stock_seq),

    -- 5축이 서로 어긋나지 않게 사슬로 묶는다 (V22).
    --   (location_seq, warehouse_seq) → 빈이 정말 그 창고의 것인가
    --   (warehouse_seq, plant_seq)    → 창고가 정말 그 플랜트의 것인가
    CONSTRAINT fk_stock_loc      FOREIGN KEY (location_seq)
        REFERENCES tb_location (location_seq),
    CONSTRAINT fk_stock_loc_wh   FOREIGN KEY (location_seq, warehouse_seq)
        REFERENCES tb_location (location_seq, warehouse_seq),
    CONSTRAINT fk_stock_wh_plant FOREIGN KEY (warehouse_seq, plant_seq)
        REFERENCES tb_warehouse (warehouse_seq, plant_seq),
    CONSTRAINT fk_stock_sku      FOREIGN KEY (sku_seq)
        REFERENCES tb_sku (sku_seq),
    CONSTRAINT fk_stock_supplier FOREIGN KEY (supplier_seq, supplier_chk)
        REFERENCES tb_partner (partner_seq, supplier_yn),

    -- 수량은 음수가 될 수 없다 (STK-003)
    CONSTRAINT ck_stock_on_hand    CHECK (qty_on_hand    >= 0),
    CONSTRAINT ck_stock_allocated  CHECK (qty_allocated  >= 0),
    CONSTRAINT ck_stock_unsellable CHECK (qty_unsellable >= 0),
    CONSTRAINT ck_stock_available  CHECK (qty_on_hand - qty_allocated - qty_unsellable >= 0)
);

-- 옮긴다. 생성컬럼(qty_available · supplier_chk)은 적지 않는다 — DB 가 다시
-- 계산한다. stock_seq 는 자식들이 이미 그 값으로 가리키고 있어 그대로 가져온다.
INSERT INTO tb_stock (stock_seq, plant_seq, warehouse_seq, location_seq, sku_seq,
                      supplier_seq, qty_on_hand, qty_allocated, qty_unsellable,
                      last_counted_at, created_by, created_at, updated_by, updated_at)
OVERRIDING SYSTEM VALUE
SELECT stock_seq, plant_seq, warehouse_seq, location_seq, sku_seq,
       supplier_seq, qty_on_hand, qty_allocated, qty_unsellable,
       last_counted_at, created_by, created_at, updated_by, updated_at
  FROM tb_stock_old;

-- 값을 직접 넣었으므로 다음 번호가 어디서 이어져야 하는지 알려 준다. 하지
-- 않으면 1 부터 다시 내주다가 기존 행과 부딪힌다.
SELECT setval(pg_get_serial_sequence('tb_stock', 'stock_seq'),
              COALESCE((SELECT MAX(stock_seq) FROM tb_stock), 0) + 1, false);

DROP TABLE tb_stock_old;


-- ----------------------------------------------------------------------------
-- 3. 인덱스
-- ----------------------------------------------------------------------------

-- 동일 조합 중복 행 생성 불가 (STK-002).
-- 공급처가 NULL 일 수 있어 둘로 나눈다. NULL 끼리는 = 로 비교되지 않아
-- 하나짜리 유니크로는 중복이 막히지 않는다.
CREATE UNIQUE INDEX ux_stock_key_supplier   ON tb_stock (location_seq, sku_seq, supplier_seq)
 WHERE supplier_seq IS NOT NULL;
CREATE UNIQUE INDEX ux_stock_key_nosupplier ON tb_stock (location_seq, sku_seq)
 WHERE supplier_seq IS NULL;

-- SKU 가 어디에 몇 개 있나 — 가장 잦은 질의다
CREATE INDEX ix_stock_sku   ON tb_stock (sku_seq, location_seq);
-- 빈 하나를 훑어 그 안의 재고를 보는 경로 (피킹 · 실사)
CREATE INDEX ix_stock_loc   ON tb_stock (location_seq, sku_seq);
-- 센터 · 창고로 거르는 경로. 데이터범위 판정이 여기를 지난다 (V22)
CREATE INDEX ix_stock_plant ON tb_stock (plant_seq, sku_seq);
CREATE INDEX ix_stock_wh    ON tb_stock (warehouse_seq, sku_seq);


-- ----------------------------------------------------------------------------
-- 4. 자식들의 손을 다시 잡아 준다
-- ----------------------------------------------------------------------------
ALTER TABLE tb_stock_history
    ADD CONSTRAINT fk_sthist_stock  FOREIGN KEY (stock_seq) REFERENCES tb_stock (stock_seq);
ALTER TABLE tb_stock_alloc
    ADD CONSTRAINT fk_stalloc_stock FOREIGN KEY (stock_seq) REFERENCES tb_stock (stock_seq);
ALTER TABLE tb_stock_adjust_line
    ADD CONSTRAINT fk_stadjl_stock  FOREIGN KEY (stock_seq) REFERENCES tb_stock (stock_seq);
ALTER TABLE tb_stocktake_line
    ADD CONSTRAINT fk_takel_stock   FOREIGN KEY (stock_seq) REFERENCES tb_stock (stock_seq);


-- ----------------------------------------------------------------------------
-- 5. 주석
-- ----------------------------------------------------------------------------
COMMENT ON COLUMN tb_stock.plant_seq IS
    '플랜트 순번 — 창고에서 유도되는 값을 조회를 위해 함께 둔다. fk_stock_wh_plant 가 창고와 어긋나지 않도록 강제한다.';
COMMENT ON COLUMN tb_stock.warehouse_seq IS
    '창고 순번 — 빈에서 유도되는 값을 조회를 위해 함께 둔다. fk_stock_loc_wh 가 빈과 어긋나지 않도록 강제한다.';
COMMENT ON COLUMN tb_stock.supplier_seq IS
    '공급처 순번 — 이 재고가 어느 공급처에서 왔나. 같은 빈 · 같은 SKU 라도 공급처가 다르면 행이 갈라진다. 출처를 모르는 재고(실사 무적재고 등)는 NULL.';
