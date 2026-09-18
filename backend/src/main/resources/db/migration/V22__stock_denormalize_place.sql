-- ============================================================================
-- 재고에 창고 · 플랜트를 직접 들려 준다
--
-- 재고 1행을 특정하는 다섯 항목 — 플랜트 · 창고 · 빈 · SKU · 공급처 — 가운데
-- 셋(빈 · SKU · 공급처)만 재고가 들고 있었다. 창고 · 플랜트는 빈을 타고
-- 올라가 얻었다.
--
--   tb_stock → tb_location → tb_warehouse → tb_plant
--
-- 보여 주는 데는 문제가 없다. 올라가는 방향은 언제나 1건이라 인덱스 탐색
-- 몇 번이면 끝난다. 문제는 거를 때다 — 방향이 반대라 1건이 N 건으로
-- 펼쳐진다.
--
--   "PL001 재고"  →  그 플랜트의 창고  →  그 창고들의 빈 N 개
--                 →  빈 하나하나로 재고를 뒤진다  =  N 번
--
-- N 은 재고 행 수가 아니라 '그 센터에 깔린 빈의 개수' 다. 지금은 센터당
-- 열 개 남짓이라 공짜지만, 물량을 받기 시작해 창고가 늘고 빈이 수천 개씩
-- 깔리면 조회 한 번이 그 개수만큼의 인덱스 탐색이 된다.
--
-- 그리고 이 경로는 예외가 아니라 주 경로다. 사용자가 센터를 고르지 않아도
-- 데이터범위 판정(COM-PG-004)이 tb_plant.org_seq 로 걸리기 때문에, 시스템
-- 관리자가 아닌 모든 사용자의 모든 재고 조회가 여기를 지난다.
--
-- 창고 · 플랜트를 재고가 직접 들고 있으면 조건을 재고에 바로 걸 수 있다.
-- 인덱스 한 번 훑고 끝난다.
--
-- 지금 하는 이유. 재고가 0 건이라 백필이 공짜다. 쌓인 뒤에 컬럼을 더하면
-- 그 동안 테이블이 잠긴다.
--
-- PK 는 건드리지 않는다. stock_seq 가 그대로 PK 다 — 이력 · 할당 · 조정 ·
-- 실사가 재고를 가리키는 방법이 바뀌지 않아야 한다. 이력은 재고보다 훨씬
-- 빨리 늘어나는 테이블이라, 가리키는 값이 8 바이트에서 40 바이트로 늘면
-- 그것이 그대로 비용이 된다. 늘어나는 것은 FK 뿐이다.
--
-- 값이 어긋날 걱정은 DB 에 맡긴다. 아래 복합 FK 두 개가 '빈이 그 창고에
-- 속하고, 창고가 그 플랜트에 속한다' 를 강제한다. 어긋난 행은 INSERT 가
-- 실패한다 — 사람이 실수할 자리를 남기지 않는다. V20 에서 거래처 유형에
-- 쓴 것과 같은 수법이다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 컬럼을 넣고 빈에서 채운다
--
-- 먼저 NULL 로 넣고 채운 뒤 NOT NULL 로 조인다. 처음부터 NOT NULL 로 만들면
-- 기본값이 필요한데, 여기에는 맞는 기본값이 없다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_stock ADD COLUMN warehouse_seq bigint,
                     ADD COLUMN plant_seq     bigint;

UPDATE tb_stock s
   SET warehouse_seq = l.warehouse_seq,
       plant_seq     = w.plant_seq
  FROM tb_location l
  JOIN tb_warehouse w ON w.warehouse_seq = l.warehouse_seq
 WHERE l.location_seq = s.location_seq;

ALTER TABLE tb_stock ALTER COLUMN warehouse_seq SET NOT NULL,
                     ALTER COLUMN plant_seq     SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. 어긋남을 DB 가 막는다
--
-- 복합 FK 는 가리키는 쪽에 같은 모양의 유니크가 있어야 걸린다. 둘 다 PK 를
-- 앞에 둔 조합이라 이미 유일하지만, 제약으로 선언해 두어야 FK 가 붙는다.
--
-- 두 개를 이어 걸면 사슬 전체가 보장된다.
--   (location_seq, warehouse_seq) → 빈이 정말 그 창고의 것인가
--   (warehouse_seq, plant_seq)    → 창고가 정말 그 플랜트의 것인가
-- ----------------------------------------------------------------------------
ALTER TABLE tb_location  ADD CONSTRAINT uk_location_self_wh     UNIQUE (location_seq, warehouse_seq);
ALTER TABLE tb_warehouse ADD CONSTRAINT uk_warehouse_self_plant UNIQUE (warehouse_seq, plant_seq);

ALTER TABLE tb_stock
    ADD CONSTRAINT fk_stock_loc_wh FOREIGN KEY (location_seq, warehouse_seq)
        REFERENCES tb_location (location_seq, warehouse_seq),
    ADD CONSTRAINT fk_stock_wh_plant FOREIGN KEY (warehouse_seq, plant_seq)
        REFERENCES tb_warehouse (warehouse_seq, plant_seq);

-- ----------------------------------------------------------------------------
-- 3. 인덱스
--
-- 컬럼만 넣어서는 빨라지지 않는다. 거를 때 쓸 인덱스가 있어야 한다.
--
-- 뒤에 sku_seq 를 붙인다. 센터로 거른 다음 SKU 로 한 번 더 좁히는 것이 가장
-- 잦은 조회라, 인덱스 안에서 둘 다 끝낼 수 있다.
--
-- 데이터범위도 플랜트로 판정하므로, 권한이 좁은 사용자의 모든 조회가 이
-- 인덱스를 함께 쓴다.
-- ----------------------------------------------------------------------------
CREATE INDEX ix_stock_plant ON tb_stock (plant_seq, sku_seq);
CREATE INDEX ix_stock_wh    ON tb_stock (warehouse_seq, sku_seq);

COMMENT ON COLUMN tb_stock.warehouse_seq IS
    '창고 순번 — 빈에서 유도되는 값을 조회를 위해 함께 둔다. '
    'fk_stock_loc_wh 가 빈과 어긋나지 않도록 강제한다.';
COMMENT ON COLUMN tb_stock.plant_seq IS
    '플랜트 순번 — 창고에서 유도되는 값을 조회를 위해 함께 둔다. '
    'fk_stock_wh_plant 가 창고와 어긋나지 않도록 강제한다.';
