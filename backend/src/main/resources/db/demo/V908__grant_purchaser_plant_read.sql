-- ============================================================================
-- 구매 담당에게 플랜트 읽기 권한
--
-- V907 이 재고 담당자에게 준 것과 같은 누락이다. 구매 담당은 결재함에서
-- 센터로 거르고, 직접 요청을 올릴 때는 받을 센터를 골라야 한다 — 어느
-- 센터로 보낼지 모르면 발주를 낼 수 없다.
--
-- 두 역할에서 같은 누락이 났다는 것이 이 방식의 약점을 보여 준다.
-- 화면이 필요로 하는 것은 '기준정보를 관리할 권한(MST_PLANT)' 이 아니라
-- '내가 쓸 수 있는 센터 목록' 인데, 그 둘이 지금은 같은 권한에 묶여 있다.
-- 업무 역할이 늘 때마다 여기에 한 줄씩 붙게 된다.
--
-- 근본 해법은 둘 중 하나다.
--   1) 데이터 범위 기반의 '내 센터 목록' 조회 경로를 따로 둔다
--   2) 거점 3단계 읽기를 업무 역할의 기본으로 묶는다
-- 어느 쪽이든 설계 판단이라 지금 임의로 정하지 않고 이 줄로 메운다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r, tb_permission p
 WHERE r.role_id = 'PURCHASER'
   AND p.perm_id = 'MST_PLANT'
   AND NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
          AND x.action_code = 'R');
