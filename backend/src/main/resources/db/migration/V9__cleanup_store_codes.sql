-- ============================================================================
-- V9 : 매장 잔재 코드 정리
--
-- V7 이 매장 조직 · 역할 · 사용자 · 정책을 걷어냈지만 공통코드는 남아 있었다.
-- 남은 코드는 그냥 지저분한 정도가 아니라 실제로 잘못된 데이터를 만든다.
-- PERM_MODULE 의 STR(매장) 은 권한 관리 화면의 모듈 선택 목록에 계속 떠서,
-- 범위 밖인 매장 권한을 지금도 새로 등록할 수 있다. 역할의 orgScope 가
-- 코드그룹을 안 읽어서 삭제된 STORE 를 받아줬던 것과 같은 종류의 문제다.
--
-- STR 을 쓰는 권한은 이미 0건이므로 지운다 (use_yn='N' 로 숨기지 않는 이유:
-- 참조가 없으니 이력을 남길 대상이 없고, 남겨두면 언제 되살아나는지 모른다).
--
-- 설명 문구도 함께 맞춘다. 조직유형에서 창고를 빼는 것은 창고를 안 쓴다는
-- 뜻이 아니라, 창고가 조직이 아니라 물류센터 아래의 창고타입 · 빈으로
-- 관리되기 때문이다 (재고주소 = 물류센터-창고타입-빈-상품-거래처).
-- ============================================================================

DELETE FROM tb_code
 WHERE code_id = 'STR'
   AND code_group_seq = (SELECT code_group_seq FROM tb_code_group
                          WHERE code_group_id = 'PERM_MODULE');

UPDATE tb_code_group
   SET description = '회사 · 물류센터',
       updated_by  = 'system',
       updated_at  = CURRENT_TIMESTAMP
 WHERE code_group_id = 'ORG_TYPE';

UPDATE tb_code
   SET description = '본사 · 물류센터',
       updated_by  = 'system',
       updated_at  = CURRENT_TIMESTAMP
 WHERE code_id = 'ORG'
   AND code_group_seq = (SELECT code_group_seq FROM tb_code_group
                          WHERE code_group_id = 'UPLOAD_TARGET');
