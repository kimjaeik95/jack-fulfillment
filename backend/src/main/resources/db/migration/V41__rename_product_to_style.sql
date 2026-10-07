-- ============================================================================
-- 화면 용어를 '제품' 에서 '스타일' 로
--
-- 의류는 하나의 디자인을 <b>스타일</b>이라 부르고, 거기에 색상 · 사이즈가
-- 붙은 것이 판매 단위(SKU)다. 현장이 쓰는 말이 '스타일' 인데 화면만 '제품'
-- 이라고 하면, 같은 것을 두 이름으로 부르게 된다.
--
--   스타일  PRD-24001         베이직 반팔 티셔츠
--   SKU     PRD-24001-BK-M    그 스타일의 블랙 M
--
-- SKU 는 그대로 둔다. 업계에서도 그대로 쓰는 말이고, 스타일과 층위가 달라
-- 섞일 염려가 없다.
--
--
-- 【 코드는 안 바꾼다 】
--
-- tb_product · ProductService · productName 은 그대로다. 화면 용어와 코드
-- 식별자가 다른 것은 흔하고, 테이블 · 매퍼 · DTO 를 전부 따라 바꾸면 되돌릴
-- 수 없는 변경이 수백 곳으로 번진다. 바뀌는 것은 <b>사람이 읽는 말</b>뿐이다.
--
-- 권한 ID(MST_PRODUCT)도 그대로다 — 역할 · 정책이 그 문자열로 묶여 있다.
-- 바뀌는 것은 화면에 뜨는 권한 '이름' 이다.
-- ============================================================================

UPDATE tb_menu
   SET menu_name = '스타일 관리', updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE menu_id = 'MST_PRODUCTS';

UPDATE tb_menu
   SET menu_name = '스타일', updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE menu_id = 'GRP_MST_PRODUCT';

UPDATE tb_permission
   SET perm_name  = '스타일 관리',
       menu_path  = '기준정보 > 스타일',
       updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE perm_id = 'MST_PRODUCT';

UPDATE tb_code_group
   SET code_group_name = '스타일 상태', updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE code_group_id = 'PRODUCT_STATUS';

-- 역할 설명에도 나온다. 역할 관리 화면에 그대로 뜬다.
UPDATE tb_role
   SET description = '거점·스타일/SKU/브랜드/채널/가격/공급처 관리',
       updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE role_id = 'HQ_MASTER';
