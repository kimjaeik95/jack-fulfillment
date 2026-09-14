-- ============================================================================
-- SKU 일괄생성 화면 (MST-PG-009)
--
-- 새 권한을 만들지 않는다. 하는 일이 SKU 를 만드는 것이라 MST_SKU 의 C 와
-- 같은 권한이다. 권한을 따로 두면 "SKU 는 만들 수 있지만 한 번에 만들지는
-- 못하는 사람" 이라는, 업무에 없는 역할이 생긴다.
--
-- 메뉴만 추가하면 되는 이유다. 테이블도 컬럼도 바뀌지 않는다.
-- ============================================================================

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        -- SKU 관리(70) 바로 뒤. 같은 대상을 다루므로 붙여 둔다.
        ('MST_SKU_BULK', 'SKU 일괄생성', 'GRP_MASTER', 'sku-bulk', '🧮', 'MST_SKU', 75)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
