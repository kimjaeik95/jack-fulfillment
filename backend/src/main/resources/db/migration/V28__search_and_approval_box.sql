-- ============================================================================
-- 통합검색 · 승인 작업함 (0차 · COM-PG-013, COM-PG-008)
--
-- 둘 다 새 자료를 만들지 않는다. 이미 있는 문서를 <b>다르게 모아 보는</b>
-- 화면이라 테이블이 없고 권한과 메뉴만 생긴다.
--
--
-- 【 통합검색 — 번호 하나로 그 건이 지나온 길 】
--
-- 지금은 ORD-20260923-0010 을 받아 들면 주문 화면에서 찾고, 지시번호를
-- 알아내 출고 화면으로 가고, 송장번호를 알아내 배송 화면으로 간다. 한 건이
-- 지나는 문서가 여섯이라 CS 가 전화를 받으면 화면을 여섯 번 연다.
--
-- <b>사슬은 둘로 끊겨 있다.</b>
--   구매 사슬  구매요청 → 발주 → 입고
--   판매 사슬  주문 → 출고지시 → 박스 · 송장 → 배송
-- 둘을 잇는 것은 재고인데, 재고는 수량이지 문서가 아니다. 입고된 그 물건이
-- 이 주문으로 나갔다는 것을 잇는 고리가 없다 (로트를 안 쓰기로 했으니
-- 당연하다). 그래서 검색은 <b>둘 중 하나</b>를 보여 준다. 잇는 척하면
-- 없는 관계를 있다고 말하는 것이 된다.
--
--
-- 【 승인 작업함 — 내가 결재할 것이 모여 있는 곳 】
--
-- 결재를 각 화면에 흩어 뒀다. 구매요청 결재는 구매요청 화면, 재고조정
-- 승인은 재고조정 화면, 입고정정 승인은 입고정정 화면. 구매담당이 아침에
-- "오늘 결재할 게 뭐지" 를 알려면 화면을 세 곳 돈다.
--
-- 다행히 세 문서가 <b>같은 모양</b>이다 — 대기 상태가 전부 REQUESTED 고,
-- 승인 권한이 전부 액션 A 다. 그래서 한 규칙으로 모을 수 있다.
--
-- 실사(INV_COUNT_APPROVE)는 뺐다. 대기 상태가 따로 없고 'COUNTING 인데
-- 다 세었다' 로 판정해야 하는데, 그 판정이 실사 화면 안에 있다. 같은 규칙에
-- 안 들어오는 것을 억지로 끼우면 작업함이 문서마다 다른 특례를 갖게 된다.
-- 출고확정(OUT_APPROVE)도 뺀다 — 결재가 아니라 실행이다.
-- ============================================================================


-- ============================================================================
-- 1. 권한
--
-- 통합검색은 R · X 만 있으면 된다. 만들 것이 없다.
--
-- 승인 작업함도 R 뿐이다. <b>결재 자체는 각 문서의 권한이 판정한다</b> —
-- 작업함에 승인 권한을 따로 두면 그 권한만으로 구매요청을 결재할 수 있게
-- 되어, PUR_REQ_APPROVE 로 막아 둔 것이 뒷문으로 열린다. 작업함은 모아
-- 보여 주기만 하고, 승인 버튼은 그 문서의 서비스를 부른다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES ('SYS_SEARCH',   '통합검색',     'SYS', '현황 > 통합검색',     15, 'system'),
       ('SYS_APPROVAL', '승인 작업함',  'SYS', '현황 > 승인 작업함',  16, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('SYS_SEARCH',   'RX'),
        ('SYS_APPROVAL', 'RX')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, a.perm_seq, a.action_code, NULL, 'system'
  FROM tb_role r
  JOIN tb_permission p ON p.perm_id IN ('SYS_SEARCH','SYS_APPROVAL')
  JOIN tb_permission_action a ON a.perm_seq = p.perm_seq
 WHERE r.role_id = 'SYS_ADMIN';


-- ============================================================================
-- 2. 메뉴
--
-- '현황' 그룹에 넣는다. 둘 다 무엇을 만드는 화면이 아니라 <b>지금 어떤가</b>
-- 를 보는 화면이고, 업무 모듈 어디에도 속하지 않는다 — 통합검색은 여섯
-- 모듈을 가로지르고, 작업함은 세 모듈을 가로지른다.
--
-- 대시보드(10) 앞에 둔다. 아침에 여는 순서가 '내 결재함 → 검색' 이지
-- 대시보드가 아니다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('SYS_APPROVAL', '승인 작업함', 'approval-box', '🗂️', 'SYS_APPROVAL', 4),
        ('SYS_SEARCH',   '통합검색',    'search',       '🔍', 'SYS_SEARCH',   6)
       ) AS v(menu_id, menu_name, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = 'GRP_STATUS'
  JOIN tb_permission p ON p.perm_id = v.perm_id;
