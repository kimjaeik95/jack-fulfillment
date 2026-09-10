-- ============================================================================
-- V5 : 감사 행위구분에 '비밀번호 변경' 추가
--
-- 관리자에 의한 초기화(PWD_RESET)와 사용자 본인 변경(PWD_CHANGE)은 구분해야 한다.
-- 감사에서 "누가 바꿨는가" 가 다르기 때문이다.
-- ============================================================================

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, 'PWD_CHANGE', '비밀번호 변경', '사용자 본인이 변경', 'violet', 75, 'system'
  FROM tb_code_group g
 WHERE g.code_group_id = 'AUDIT_ACTION';
