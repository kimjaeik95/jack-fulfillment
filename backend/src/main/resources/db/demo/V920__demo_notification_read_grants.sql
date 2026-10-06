-- 신규 설치에서는 V900 데모 역할이 V39보다 늦게 생성된다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, p.perm_seq, 'R', NULL, 'system'
  FROM tb_role r CROSS JOIN tb_permission p
 WHERE p.perm_id = 'SYS_NOTIFICATION'
   AND r.role_id IN ('HQ_MASTER','PURCHASER','ORDER_MGR','CENTER_MGR',
                     'INV_MANAGER','INBOUND_WORKER','PICK_PACK','CS_VIEWER','AUDITOR')
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission rp
                    WHERE rp.role_seq = r.role_seq AND rp.perm_seq = p.perm_seq
                      AND rp.action_code = 'R');
