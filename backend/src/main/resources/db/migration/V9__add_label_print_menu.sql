-- ============================================================================
-- 빈 바코드 출력 화면 (MST-PG-004)
--
-- 새 권한을 만들지 않는다. MST_LOCATION 에 이미 X(출력·다운로드) 액션이
-- 있고, 라벨을 뽑는 것은 빈 정보를 읽어 종이에 옮기는 일이다. 데이터를
-- 바꾸지 않으므로 C·U·D 와는 성격이 다르고, 그래서 R 이 아니라 X 를 쓴다.
--
-- 권한을 따로 두면 "빈은 볼 수 있지만 라벨은 못 뽑는 사람" 이라는, 업무에
-- 없는 역할이 생긴다.
--
-- 테이블도 컬럼도 바뀌지 않는다 — 메뉴 한 줄이 전부다.
-- ============================================================================

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        -- 빈 관리(30) 바로 뒤. 같은 대상을 다루므로 붙여 둔다.
        ('MST_LABELS', '빈 바코드 출력', 'GRP_MASTER', 'label-print', '🏷', 'MST_LOCATION', 35)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
