-- ============================================================================
-- 초기 재고 (로컬 전용)
--
-- 지금까지 재고는 데모 파일에 없었다. 손으로 입고를 태워 만들었기 때문에
-- DB 를 다시 깔면 재고 화면도 주문 화면의 가용수량도 전부 0 이 된다.
-- 시연에 재고가 필요하니 여기에 고정으로 깐다.
--
-- 입고 전표를 만들지 않고 재고에 바로 넣는다. 전표까지 흉내내면 발주 ·
-- 입고예정 · 검수가 전부 따라와야 하는데, 그건 이미 각 화면에서 손으로
-- 태워 보는 흐름이라 시드가 대신할 일이 아니다. 대신 이력에는 '조정' 으로
-- 남긴다 — 실제로 입고로 들어온 것이 아니기 때문이다. 이력에 RECEIVE 로
-- 적어 놓으면 입고 테이블을 뒤져도 짝이 없어 나중에 사람을 헷갈리게 한다.
--
-- 재고 배치가 노리는 것
--   · 같은 SKU 를 두 센터에 나눠 둔다 (PRD-24001-BK-M) — 할당이 센터를
--     고르는 것을 보이려면 한 군데만 있으면 안 된다.
--   · 한 빈에 여러 SKU, 한 SKU 가 여러 빈 — 둘 다 나오게 한다.
--   · 재킷은 일부러 적게 깐다 (GY-L 4장) — 결품 화면을 보려면 모자란
--     재고가 하나는 있어야 한다.
--   · 반품 · 불량창고에는 판매불가로 깐다 — 가용수량이 보유수량과
--     다른 줄이 있어야 계산식이 눈에 보인다.
-- ============================================================================

INSERT INTO tb_stock (location_seq, sku_seq, qty_on_hand, qty_unsellable, created_by)
SELECT l.location_seq, k.sku_seq, v.on_hand, v.unsellable, 'system'
  FROM (VALUES
        -- 이천 양품창고 — 티셔츠는 회전이 빨라 앞쪽 통로에 둔다
        ('1A-01-01', 'PRD-24001-BK-M',   120, 0),
        ('1A-01-02', 'PRD-24001-WH-M',    80, 0),
        ('1A-01-03', 'PRD-24001-BK-S',    40, 0),
        ('1A-01-03', 'PRD-24001-BK-L',    35, 0),
        ('1A-02-01', 'PRD-24002-WH-L',    25, 0),
        ('1A-02-01', 'PRD-24002-WH-M',    30, 0),
        ('1A-02-02', 'PRD-24003-NV-32',   12, 0),
        ('1A-02-02', 'PRD-24003-NV-30',   18, 0),
        -- 재킷은 단가가 높아 소량만 받는다. 결품 데모가 여기서 난다.
        ('1B-01-01', 'PRD-24004-GY-M',     6, 0),
        ('1B-01-01', 'PRD-24004-GY-L',     4, 0),
        -- 김해 양품창고 — BK-M 은 두 센터에 같이 있다
        ('2A-01-01', 'PRD-24001-BK-M',    60, 0),
        ('2A-01-02', 'PRD-24001-NV-M',    45, 0),
        ('2A-02-01', 'PRD-23001-BK-FREE', 20, 0),
        ('2A-02-01', 'PRD-23001-BE-FREE', 15, 0),
        -- 반품 · 불량은 전량 판매불가다. 가용 0 으로 잡혀야 맞다.
        ('RT-01-01', 'PRD-24001-WH-L',     5, 5),
        ('DF-01-01', 'PRD-24002-BE-M',     3, 3)
       ) AS v(location_id, sku_id, on_hand, unsellable)
  JOIN tb_location l ON l.location_id = v.location_id
  JOIN tb_sku      k ON k.sku_id      = v.sku_id;


-- ----------------------------------------------------------------------------
-- 이력
--
-- 재고 한 줄당 한 건이다. 판매불가가 섞인 줄은 보유와 판매불가를 따로
-- 적는다 — 수량필드가 다르면 다른 사건이라 한 줄에 못 담는다.
--
-- qty_before 는 전부 0 이다. 이 줄들이 그 재고의 첫 사건이기 때문이다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_stock_history (stock_seq, move_type, qty_field, qty_delta,
                              qty_before, qty_after, reason_group, reason_code,
                              remark, occurred_at, created_by)
SELECT s.stock_seq, 'ADJUST', 'ON_HAND', s.qty_on_hand,
       0, s.qty_on_hand, 'REASON_ADJUST', 'SYS_FIX',
       '데모 초기재고', timestamp '2026-09-15 09:00:00', 'system'
  FROM tb_stock s
 WHERE s.created_by = 'system';

INSERT INTO tb_stock_history (stock_seq, move_type, qty_field, qty_delta,
                              qty_before, qty_after, reason_group, reason_code,
                              remark, occurred_at, created_by)
SELECT s.stock_seq, 'UNSELLABLE', 'UNSELLABLE', s.qty_unsellable,
       0, s.qty_unsellable, 'REASON_INSPECT', 'DAMAGED',
       '데모 초기재고 — 판매불가 판정분', timestamp '2026-09-15 09:05:00', 'system'
  FROM tb_stock s
 WHERE s.created_by = 'system'
   AND s.qty_unsellable > 0;
