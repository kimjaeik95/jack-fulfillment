-- ============================================================================
-- 재고의 화주(vendor)를 공급처(supplier)로 바꾼다
--
-- 왜 바꾸나.
--
-- vendor 는 '화주' — 창고에 물건을 맡긴 주인 — 를 뜻하려던 이름이다. 3PL
-- 에서 남의 물건을 대신 보관할 때 화주별로 재고를 섞으면 안 되기 때문에
-- 둔 컬럼이었다.
--
-- 그런데 이 시스템은 3PL 이 아니다. 발주(tb_purchase_order)로 물건을 사
-- 오고, 입고하면 우리 소유가 되고, 우리가 판다. 화주는 언제나 '우리'
-- 하나다. 값이 언제나 같으면 컬럼이 아니다 — 그래서 입고 로직이 여기에
-- 계속 NULL 을 넣고 있었다. 버그가 아니라 넣을 값이 없었던 것이다.
--
-- 대신 실제로 필요한 것이 따로 있다. '이 물건이 누구 것인가' 가 아니라
-- '이 물건이 어디서 왔는가' 다. 같은 SKU 를 공급처 둘에서 받으면 불량이
-- 터졌을 때 어느 쪽 물량인지 가려야 하고, 정산도 그 단위로 한다.
--
-- 두 뜻은 다르지만 가리키는 테이블은 같았다. V20 이 FK 를
-- (partner_seq, supplier_yn) 으로 묶어 두어서, 이 컬럼은 이미 공급처만
-- 가리킬 수 있다 — 고객 전용 거래처는 INSERT 가 거부된다. 이름만 화주로
-- 남아 있었을 뿐 실체는 진작 공급처였다.
--
-- 그래서 뜻을 공급처로 확정하고 이름을 맞춘다. 화주가 정말 필요해지는
-- 날(3PL 을 하게 되면) 그때 별도 컬럼으로 새로 만든다. 지금 이름만
-- 남겨 두면, 나중에 읽는 사람이 없는 개념을 있다고 믿는다.
--
-- 데이터는 건드리지 않는다. 전부 NULL 이고, 이름만 바뀐다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 재고
--
-- RENAME 을 쓴다. 컬럼을 새로 만들고 옮기면 FK 와 부분 유니크 인덱스를
-- 다시 걸어야 하는데, RENAME 은 그것들을 그대로 따라오게 한다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_stock RENAME COLUMN vendor_seq TO supplier_seq;
ALTER TABLE tb_stock RENAME COLUMN vendor_chk TO supplier_chk;

ALTER TABLE tb_stock RENAME CONSTRAINT fk_stock_vendor TO fk_stock_supplier;

ALTER INDEX ux_stock_key_vendor   RENAME TO ux_stock_key_supplier;
ALTER INDEX ux_stock_key_novendor RENAME TO ux_stock_key_nosupplier;

COMMENT ON COLUMN tb_stock.supplier_seq IS
    '공급처 순번 — 이 재고가 어느 공급처에서 왔나. 같은 빈 · 같은 SKU 라도 '
    '공급처가 다르면 행이 갈라진다. 출처를 모르는 재고(실사 무적재고 등)는 NULL.';

-- ----------------------------------------------------------------------------
-- 2. 실사 라인
--
-- 재고와 같은 축으로 세야 한다. 재고가 공급처로 갈라지는데 실사가 합쳐
-- 세면, 차이가 났을 때 어느 공급처 물량이 틀렸는지 가릴 수 없다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_stocktake_line RENAME COLUMN vendor_seq TO supplier_seq;
ALTER TABLE tb_stocktake_line RENAME COLUMN vendor_chk TO supplier_chk;

ALTER TABLE tb_stocktake_line RENAME CONSTRAINT fk_takel_vendor TO fk_takel_supplier;

ALTER INDEX ux_takel_key_vendor   RENAME TO ux_takel_key_supplier;
ALTER INDEX ux_takel_key_novendor RENAME TO ux_takel_key_nosupplier;

COMMENT ON COLUMN tb_stocktake_line.supplier_seq IS
    '공급처 순번 — 세는 단위를 재고와 맞춘다. 장부에 없던 실물(무적재고)은 NULL.';
