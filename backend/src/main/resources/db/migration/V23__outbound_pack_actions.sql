-- ============================================================================
-- 패킹 권한에 수정 · 삭제를 더한다 (PAC-PG-001, PAC-PG-002)
--
-- V4 가 0차에 OUT_PACK 을 'RC' 로 깔았다. '패킹 실적 등록' 만 생각한 것인데,
-- 박스를 다루려면 그것으로 모자란다.
--
--   U  박스를 닫는다 · 다시 연다 · 규격과 무게를 고친다
--   D  빈 박스를 지운다
--
-- 담는 것(C)과 닫는 것(U)은 다른 행위다. 닫으면 더 담을 수 없고 송장이
-- 붙는 대상이 되므로, 담을 수 있다고 닫을 수 있는 것은 아니다. 지우는
-- 것(D)은 빈 박스에만 되지만 그래도 문서를 없애는 일이라 따로 둔다.
--
-- 실제로 막혔다 — 관리자조차 '박스 닫기' 에서 'OUT_PACK/U 권한이 없습니다'
-- 로 거절당했다.
-- ============================================================================
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES ('OUT_PACK', 'UD')) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code)
 WHERE NOT EXISTS (
       SELECT 1 FROM tb_permission_action x
        WHERE x.perm_seq = p.perm_seq
          AND x.action_code = a.action_code);

/*
 * 시스템관리자에게 새 액션을 준다 (V12 · V13 · V20 과 같은 문장).
 * 권한 자체는 이미 있었지만 액션이 늘었으므로 다시 돌린다.
 */
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, a.perm_seq, a.action_code, NULL, 'v17.sysadmin'
  FROM tb_role r
  JOIN tb_permission_action a ON TRUE
 WHERE r.role_id = 'SYS_ADMIN'
   AND NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq
          AND x.perm_seq = a.perm_seq
          AND x.action_code = a.action_code);
