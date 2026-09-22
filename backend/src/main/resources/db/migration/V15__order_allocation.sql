-- ============================================================================
-- 재고할당 (3차 · C섹터)
--   ORD-PG-005  재고할당
--
-- 테이블은 새로 만들지 않는다. tb_stock_alloc 이 V8 에 있고 V13 에서
-- order_line_seq 를 붙여 주문 줄을 가리키게 해 뒀다. 여기서는 그것을
-- 실제로 쓰기 시작하면서 모자란 코드와 메뉴만 채운다.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 재고이력의 전표유형에 '주문' 을 더한다
--
-- 할당은 재고 수량을 움직이므로 이력이 남는다 (STOCK_MOVE 의 ALLOCATE ·
-- RELEASE). 그 이력에 '무엇이 이 변경을 일으켰나' 를 적어야 하는데, 지금
-- STOCK_REF 에는 입고 · 출고 · 조정 · 이동 · 실사 · 입고정정뿐이다.
--
-- 출고전표(OUTBOUND)로 적을 수는 없다. 출고전표는 4차에 생기고, 할당 시점에는
-- 아직 없다 — 없는 전표를 가리키는 이력을 남기면 나중에 그 번호를 쫓아가
-- 아무것도 찾지 못한다. 할당을 일으킨 것은 주문이므로 주문이라고 적는다.
--
-- 재고이력 화면의 전표유형 필터가 이 코드그룹을 그대로 쓰므로, 여기 넣으면
-- 화면에서도 '주문' 으로 걸러 볼 수 있다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, 'ORDER', '주문', '주문 할당 · 할당해제로 움직인 재고', 'blue', 60, 'system'
  FROM tb_code_group g
 WHERE g.code_group_id = 'STOCK_REF';


-- ----------------------------------------------------------------------------
-- 할당 조회를 위한 인덱스
--
-- 할당은 SKU 하나로 후보 빈을 훑는다. 양품창고의 정상 로케이션만 보고 가용이
-- 남은 것만 쓰는데, ix_stock_sku 는 sku_seq 만 타므로 빈이 늘면 그만큼
-- 읽고 버린다. 지금은 빈이 열여섯 개라 차이가 없지만, 이 질의는 주문마다
-- 줄마다 돈다 — 늘어나는 쪽이 정해져 있는 자리다.
--
-- 부분 인덱스로 둔다. 가용이 0 인 행은 후보가 될 수 없어 색인에 넣을 이유가
-- 없고, 빼면 색인이 작아져 갱신도 싸진다.
-- ----------------------------------------------------------------------------
CREATE INDEX ix_stock_allocatable ON tb_stock (sku_seq, location_seq)
 WHERE qty_available > 0;


-- ----------------------------------------------------------------------------
-- 할당 내역을 주문 줄로 찾는 길
--
-- V13 이 ix_stalloc_ordline 을 만들어 뒀다. 여기서는 '살아 있는 할당' 만
-- 보는 조건을 더한다 — 해제된 할당은 수량이 0 이라 더할 것이 없는데,
-- 한 줄을 여러 번 잡고 풀면 죽은 행이 계속 쌓인다.
-- ----------------------------------------------------------------------------
CREATE INDEX ix_stalloc_live ON tb_stock_alloc (order_line_seq, stock_seq)
 WHERE qty_allocated > qty_released;


-- ----------------------------------------------------------------------------
-- 메뉴
--
-- 권한은 V13 에서 만든 ORD_ALLOC 을 그대로 쓴다 (R · C · D). 할당은 잡고
-- 푸는 것이라 고치는 개념이 없다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('ORD_ALLOCS', '재고할당', 'GRP_ORDER', 'order-allocations', '📦', 'ORD_ALLOC', 30)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
