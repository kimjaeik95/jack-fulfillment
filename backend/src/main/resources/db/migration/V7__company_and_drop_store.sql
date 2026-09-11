-- ============================================================================
-- V7 : 회사·조직 관리 (MST-PG-001) — 회사 속성 추가 + 오프라인 매장 제거
--
-- 요구사항 1.2 는 오프라인 매장 운영 전반(매장 기준정보 · 매장재고 · 매장간
-- 이동 · 매장↔센터 보충 · POS)을 범위 외로 명시한다. 그런데 시드에는 매장
-- 조직 3건과 매장 역할 2개, STR_* 권한 5건이 들어 있었다. 온라인 판매는
-- 채널(세일즈 채널)로 다루므로 오프라인 매장은 걷어낸다.
--
-- 조직 계층은 확정된 대로 둔다.
--
--   회사 → 물류센터        tb_org      (사람이 소속되고 데이터범위가 도는 단위)
--     → 창고타입 → 빈      별도 테이블  (물건이 놓이는 장소, MST-PG-002/003)
--
-- 로케이션(빈)은 수만 행이 되고 재고의 FK 다. tb_org 에 섞으면 로그인 시점의
-- 데이터범위 계산(COM-PG-004)이 로케이션 수만 건을 타게 되므로 분리한다.
--
-- 재고 주소는 물류센터 · 창고타입 · 빈 · 상품코드 · 거래처 다섯 축이다.
-- 빈이 창고타입에, 창고타입이 센터에 속하므로 유일키는 빈 × 상품코드 × 거래처로
-- 좁혀지지만, 조회와 집계는 다섯 단계를 모두 축으로 쓴다.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1) 회사 전용 속성
--
-- 요구사항 5장의 '회사' 엔티티는 사업자등록번호 · 대표자명을 갖는다.
-- 물류센터는 우편번호를 갖는다. 주소 · 연락처 · 담당자명은 이미 있다.
--
-- 어느 컬럼이 어느 유형의 것인지 CHECK 로 못박는다. 한 테이블에 두 유형을
-- 담으면서 이것을 적어 두지 않으면, 센터에 사업자번호가 들어가 있어도
-- 아무도 모른다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_org ADD COLUMN biz_reg_no varchar(20);
ALTER TABLE tb_org ADD COLUMN ceo_name   varchar(50);
ALTER TABLE tb_org ADD COLUMN zip_code   varchar(10);

COMMENT ON COLUMN tb_org.biz_reg_no IS '사업자등록번호 — 회사(HQ)만';
COMMENT ON COLUMN tb_org.ceo_name   IS '대표자명 — 회사(HQ)만';
COMMENT ON COLUMN tb_org.zip_code   IS '우편번호';


-- ----------------------------------------------------------------------------
-- 2) 오프라인 매장 제거
--
-- 참조를 안쪽에서 바깥으로 지운다. 감사로그의 처리자 FK 는 ON DELETE SET NULL
-- 이라 사용자를 지워도 행위자 ID · 이름(문자열)은 남는다 — 이력은 보존된다.
-- ----------------------------------------------------------------------------

-- 매장 역할·권한에 걸린 정책 (P007 타 매장 재고 직접 수정 금지, P008 매장 직원 승인 한도)
DELETE FROM tb_policy
 WHERE role_seq IN (SELECT role_seq FROM tb_role WHERE role_id IN ('STORE_MGR', 'STORE_STAFF'))
    OR perm_seq IN (SELECT perm_seq FROM tb_permission
                     WHERE perm_id LIKE 'STR\_%' OR perm_id = 'INV_STORE_COUNT');

-- 매장 소속 사용자 (로컬 데모 계정)
DELETE FROM tb_user_role
 WHERE user_seq IN (SELECT user_seq FROM tb_user
                     WHERE org_seq IN (SELECT org_seq FROM tb_org WHERE org_type = 'STORE'));
DELETE FROM tb_user
 WHERE org_seq IN (SELECT org_seq FROM tb_org WHERE org_type = 'STORE');

-- 매장 역할
DELETE FROM tb_role_permission
 WHERE role_seq IN (SELECT role_seq FROM tb_role WHERE role_id IN ('STORE_MGR', 'STORE_STAFF'));
DELETE FROM tb_role_org_scope
 WHERE role_seq IN (SELECT role_seq FROM tb_role WHERE role_id IN ('STORE_MGR', 'STORE_STAFF'));
DELETE FROM tb_role WHERE role_id IN ('STORE_MGR', 'STORE_STAFF');

-- 매장 기능 권한 (보충 · 이동출고 · 이동승인 · 매장입고 · 택배 · 매장실사)
DELETE FROM tb_role_permission
 WHERE perm_seq IN (SELECT perm_seq FROM tb_permission
                     WHERE perm_id LIKE 'STR\_%' OR perm_id = 'INV_STORE_COUNT');
DELETE FROM tb_permission_action
 WHERE perm_seq IN (SELECT perm_seq FROM tb_permission
                     WHERE perm_id LIKE 'STR\_%' OR perm_id = 'INV_STORE_COUNT');
DELETE FROM tb_permission WHERE perm_id LIKE 'STR\_%' OR perm_id = 'INV_STORE_COUNT';

-- 매장 조직
DELETE FROM tb_role_org_scope
 WHERE org_seq IN (SELECT org_seq FROM tb_org WHERE org_type = 'STORE');
DELETE FROM tb_menu
 WHERE perm_seq IN (SELECT perm_seq FROM tb_permission WHERE perm_id LIKE 'STR\_%');
DELETE FROM tb_org WHERE org_type = 'STORE';


-- ----------------------------------------------------------------------------
-- 3) 조직유형 코드 정리
--
-- 남는 것은 회사와 물류센터 둘뿐이다.
--   STORE      범위 외
--   WAREHOUSE  창고타입은 별도 테이블로 간다 (MST-PG-002). 조직유형이 아니다.
--
-- HQ 의 이름을 '회사' 로 바꾼다. 요구사항이 부르는 이름이고, 사업자등록번호를
-- 갖는 주체가 '본사' 가 아니라 '회사' 이기 때문이다.
-- ----------------------------------------------------------------------------
UPDATE tb_code c
   SET code_name   = '회사',
       description = '법인. 사업자등록번호를 갖는 주체 (단일 법인이면 1행)',
       updated_by  = 'system',
       updated_at  = CURRENT_TIMESTAMP
  FROM tb_code_group g
 WHERE c.code_group_seq = g.code_group_seq
   AND g.code_group_id = 'ORG_TYPE'
   AND c.code_id = 'HQ';

UPDATE tb_code c
   SET description = '재고의 원천. 창고타입과 빈(로케이션)을 하위에 갖는다',
       updated_by  = 'system',
       updated_at  = CURRENT_TIMESTAMP
  FROM tb_code_group g
 WHERE c.code_group_seq = g.code_group_seq
   AND g.code_group_id = 'ORG_TYPE'
   AND c.code_id = 'DC';

DELETE FROM tb_code c
 USING tb_code_group g
 WHERE c.code_group_seq = g.code_group_seq
   AND g.code_group_id = 'ORG_TYPE'
   AND c.code_id IN ('WAREHOUSE', 'STORE');


-- ----------------------------------------------------------------------------
-- 4) 회사 정보
--
-- 단일 법인 1행. 실제 값은 운영에서 화면으로 고친다 — 여기 넣는 것은
-- 화면이 빈 칸만 보여주지 않도록 하는 초기값이다.
-- ----------------------------------------------------------------------------
UPDATE tb_org
   SET biz_reg_no = '000-00-00000',
       ceo_name   = '(대표자 미입력)',
       zip_code   = '06236',
       updated_by = 'system',
       updated_at = CURRENT_TIMESTAMP
 WHERE org_type = 'HQ';


-- ----------------------------------------------------------------------------
-- 5) 유형별 속성 제약
--
-- 데이터를 정리한 뒤에 건다. 먼저 걸면 기존 행 때문에 실패한다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_org ADD CONSTRAINT ck_org_company_only
    CHECK (org_type = 'HQ' OR (biz_reg_no IS NULL AND ceo_name IS NULL));
