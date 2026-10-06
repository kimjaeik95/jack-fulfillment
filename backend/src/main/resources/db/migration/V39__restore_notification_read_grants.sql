-- V29 이후 추가된 업무 역할도 자기 역할/센터의 알림을 읽을 수 있어야 한다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, p.perm_seq, 'R', NULL, 'system'
  FROM tb_role r CROSS JOIN tb_permission p
 WHERE p.perm_id = 'SYS_NOTIFICATION'
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission rp
                    WHERE rp.role_seq = r.role_seq AND rp.perm_seq = p.perm_seq
                      AND rp.action_code = 'R');
