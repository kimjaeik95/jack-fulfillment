-- ============================================================================
-- 주문 대량 등록 (ORD-PG-012)
--
-- 코드값 하나뿐이다. 업로드의 뼈대(파일 파싱 · 행별 검증 · 부분성공 ·
-- 오류파일)는 COM-PG-010 이 이미 갖고 있고, 대상은 스프링 빈으로 등록만
-- 하면 찾아진다 — 주문 쪽에 만든 것은 '한 행을 어떻게 반영하는가' 뿐이다.
--
-- 코드그룹에 값이 있어야 화면의 대상 목록에 뜨고 업로드 이력도 이름으로
-- 남는다.
-- ============================================================================
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, 'ORDER', '주문',
       '채널이 준 엑셀로 주문을 한꺼번에 받는다 (ORD-PG-012)', 'blue', 70, 'system'
  FROM tb_code_group g
 WHERE g.code_group_id = 'UPLOAD_TARGET';
