-- ============================================================================
-- 결품 관리 · 자동할당 배치 (3차 · D섹터)
--   ORD-PG-006  미할당 · 결품관리
--   ORD-BT-001  자동할당 배치
--   ORD-BT-002  미할당 재처리 배치
--
-- 테이블은 새로 만들지 않는다. 결품은 상태값이지 별도 자료가 아니다 —
-- tb_order_line.line_status 가 SHORTAGE 이고 잡아 둔 수량이 주문수량보다
-- 적은 줄이 곧 결품이다.
--
-- 따로 표를 만들면 같은 사실이 두 곳에 있게 되고, 둘이 어긋나는 날 어느
-- 쪽이 맞는지 알 수 없다. 재고할당(tb_stock_alloc)이 이미 '몇 개 잡았나'
-- 를 갖고 있으므로 모자란 수량은 뺄셈으로 나온다.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 결품 줄을 찾는 길
--
-- 결품 화면과 재처리 배치가 둘 다 'line_status = SHORTAGE' 로 훑는다.
-- ix_ordl_status 가 이미 line_status 를 타지만, 결품은 전체 줄 중 아주
-- 일부라서 부분 인덱스가 훨씬 작다 — 그리고 이 질의는 배치가 30 분마다
-- 돌므로 늘어나는 쪽이 정해져 있다.
--
-- order_seq 를 함께 넣는 이유는 배치가 주문 단위로 묶어 가져가기 때문이다.
-- ----------------------------------------------------------------------------
CREATE INDEX ix_ordl_shortage ON tb_order_line (order_seq, line_no)
 WHERE line_status = 'SHORTAGE';


-- ----------------------------------------------------------------------------
-- 메뉴
--
-- 권한은 ORD_ALLOC 을 그대로 쓴다. 결품은 할당이 만들어 낸 상태이고,
-- 고치는 방법도 다시 할당하거나 그 줄을 취소하는 것이라 같은 일의 뒷면이다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('ORD_SHORTS', '결품 관리', 'GRP_ORDER', 'order-shortages', '🚫', 'ORD_ALLOC', 40)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
