-- ============================================================================
-- 오류대기 메뉴 (ORD-PG-003, ORD-PG-004)
--
-- 주문 관리 화면에도 '미매핑' 빠른필터가 있지만 그것과 다른 화면이다.
-- 저기는 '미매핑 줄이 있는 주문' 을 세고, 여기는 '막힌 줄' 자체를 외부코드로
-- 묶어 센다 — 한 주문에 막힌 줄이 셋이면 저기는 1 건, 여기는 3 건이다.
--
-- 고치는 단위가 주문이 아니라 외부코드이기 때문에 화면을 나눈다. 매핑 하나를
-- 등록하면 여러 주문이 한꺼번에 풀리는데, 주문 목록에서는 그 사실이 보이지
-- 않는다.
--
-- 권한은 ORD_ORDER 를 그대로 쓴다. 새로 만들지 않는 이유는 주문을 볼 수
-- 있는 사람이 막힌 주문을 못 보는 것이 이상하기 때문이다 — 고치는 것은
-- U 액션이라 조회 전용 역할은 버튼이 잠긴다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('ORD_ERRORS', '주문 오류대기', 'GRP_ORDER', 'order-errors', '⚠️', 'ORD_ORDER', 20)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
