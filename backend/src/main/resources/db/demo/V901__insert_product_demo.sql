-- ============================================================================
-- V901 : 제품 기준정보 데모 (로컬 개발 전용)
--
-- 이 폴더(db/demo)는 local 프로파일에서만 Flyway locations 에 포함된다.
-- dev · prod 에는 적용되지 않는다.
--
-- 담는 것: 기준정보 담당 역할의 권한 부여 + 카테고리 · 브랜드 · 제품 · SKU 예시.
-- 역할(HQ_MASTER)이 V900 에서 만들어지므로 그 권한도 여기서 준다.
-- ============================================================================


-- ============================================================================
-- 1. 기준정보 담당 권한
--    제품 기준정보의 등록 · 수정은 이 역할의 일이다. 시스템 관리자는 조회만
--    한다 (V4).
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('HQ_MASTER', 'MST_CATEGORY', 'RCUDX'),
        ('HQ_MASTER', 'MST_PRODUCT',  'RCUDX')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 브랜드 · SKU 는 V900 에서 RCUD 를 이미 받았다. 다운로드(X)만 더한다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'X', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'HQ_MASTER'
   AND p.perm_id IN ('MST_BRAND', 'MST_SKU');

-- 센터 인원도 제품 · SKU 를 읽어야 한다. 입고 검수와 피킹이 SKU 를 스캔하고,
-- 그때 무슨 제품인지 화면에 보여야 하기 때문이다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id IN ('CENTER_MGR', 'INBOUND_WORKER', 'PICK_PACK')
   AND p.perm_id IN ('MST_PRODUCT', 'MST_SKU');


-- ============================================================================
-- 2. 카테고리 — 대 · 중 · 소 3단계
--
-- 단계별로 나눠 넣는다. 중분류는 대분류의 순번을, 소분류는 중분류의 순번을
-- 알아야 하는데 IDENTITY 값은 넣어 보기 전에는 모른다.
-- ============================================================================

-- 대분류
INSERT INTO tb_category (category_id, category_name, parent_seq, level_no, sort_order, created_by) VALUES
  ('CLO', '의류', NULL, 1, 10, 'system'),
  ('ACC', '잡화', NULL, 1, 20, 'system');

-- 중분류
INSERT INTO tb_category (category_id, category_name, parent_seq, level_no, sort_order, created_by)
SELECT v.category_id, v.category_name, p.category_seq, 2, v.sort_order, 'system'
  FROM (VALUES
        ('CLO-TOP', '상의',   'CLO', 10),
        ('CLO-BTM', '하의',   'CLO', 20),
        ('CLO-OUT', '아우터', 'CLO', 30),
        ('ACC-BAG', '가방',   'ACC', 10)
       ) AS v(category_id, category_name, parent_id, sort_order)
  JOIN tb_category p ON p.category_id = v.parent_id;

-- 소분류
INSERT INTO tb_category (category_id, category_name, parent_seq, level_no, sort_order, created_by)
SELECT v.category_id, v.category_name, p.category_seq, 3, v.sort_order, 'system'
  FROM (VALUES
        ('CLO-TOP-TS', '티셔츠',   'CLO-TOP', 10),
        ('CLO-TOP-SH', '셔츠',     'CLO-TOP', 20),
        ('CLO-BTM-PT', '팬츠',     'CLO-BTM', 10),
        ('CLO-BTM-SK', '스커트',   'CLO-BTM', 20),
        ('CLO-OUT-JK', '재킷',     'CLO-OUT', 10),
        ('ACC-BAG-BP', '백팩',     'ACC-BAG', 10),
        ('ACC-BAG-TT', '토트백',   'ACC-BAG', 20)
       ) AS v(category_id, category_name, parent_id, sort_order)
  JOIN tb_category p ON p.category_id = v.parent_id;


-- ============================================================================
-- 3. 브랜드
-- ============================================================================
INSERT INTO tb_brand (brand_id, brand_name, country_code, sort_order, created_by) VALUES
  ('JK',  '잭',        'KR', 10, 'system'),
  ('JKB', '잭베이직',  'KR', 20, 'system'),
  ('MLN', '밀라노라인', 'IT', 30, 'system');


-- ============================================================================
-- 4. 제품
--    시즌과 출시연도를 합쳐 24SS 처럼 읽는다.
-- ============================================================================
INSERT INTO tb_product (product_id, product_name, category_seq, brand_seq, status,
                        origin_country, produced_on, cost_amount, season, release_year,
                        sort_order, created_by)
SELECT v.product_id, v.product_name, c.category_seq, b.brand_seq, v.status,
       v.origin_country, v.produced_on, v.cost_amount, v.season, v.release_year,
       v.sort_order, 'system'
  FROM (VALUES
        ('PRD-24001', '베이직 반팔 티셔츠', 'CLO-TOP-TS', 'JKB', 'ACTIVE',
         'VN', DATE '2026-02-10',  8500.00::numeric, 'SS', 2026, 10),
        ('PRD-24002', '옥스포드 셔츠',      'CLO-TOP-SH', 'JK',  'ACTIVE',
         'VN', DATE '2026-02-20', 14200.00,          'SS', 2026, 20),
        ('PRD-24003', '와이드 데님 팬츠',   'CLO-BTM-PT', 'JK',  'ACTIVE',
         'CN', DATE '2026-03-05', 19800.00,          'ALL', 2026, 30),
        ('PRD-24004', '울 블렌드 재킷',     'CLO-OUT-JK', 'MLN', 'PLANNED',
         'IT', NULL,              78000.00,          'FW', 2026, 40),
        ('PRD-23001', '캔버스 백팩',        'ACC-BAG-BP', 'JKB', 'DISCONTINUED',
         'CN', DATE '2025-08-15', 12300.00,          'ALL', 2025, 50)
       ) AS v(product_id, product_name, category_id, brand_id, status,
              origin_country, produced_on, cost_amount, season, release_year, sort_order)
  JOIN tb_category c ON c.category_id = v.category_id
  JOIN tb_brand    b ON b.brand_id    = v.brand_id;


-- ============================================================================
-- 5. SKU
--
-- 내부코드는 제품코드-색상-사이즈 로 만든다. 사람이 읽을 수 있어야 현장에서
-- 스캔 결과를 확인할 수 있다.
--
-- 바코드는 일부만 채운다. 아직 발급하지 않은 SKU 가 있는 상태를 만들어,
-- "바코드 없이도 저장되고 라벨에는 SKU 코드가 찍힌다" 를 화면에서 볼 수 있게
-- 한다.
-- ============================================================================
INSERT INTO tb_sku (sku_id, product_seq, color_code, size_code, barcode, status,
                    sort_order, created_by)
SELECT v.sku_id, p.product_seq, v.color_code, v.size_code, v.barcode, v.status,
       v.sort_order, 'system'
  FROM (VALUES
        -- 베이직 반팔 티셔츠 — 3색 × 3사이즈
        ('PRD-24001-BK-S',  'PRD-24001', 'BK', 'S',  '8801000000011', 'ACTIVE', 10),
        ('PRD-24001-BK-M',  'PRD-24001', 'BK', 'M',  '8801000000028', 'ACTIVE', 20),
        ('PRD-24001-BK-L',  'PRD-24001', 'BK', 'L',  '8801000000035', 'ACTIVE', 30),
        ('PRD-24001-WH-S',  'PRD-24001', 'WH', 'S',  '8801000000042', 'ACTIVE', 40),
        ('PRD-24001-WH-M',  'PRD-24001', 'WH', 'M',  '8801000000059', 'ACTIVE', 50),
        ('PRD-24001-WH-L',  'PRD-24001', 'WH', 'L',  '8801000000066', 'ACTIVE', 60),
        ('PRD-24001-NV-M',  'PRD-24001', 'NV', 'M',  NULL,            'ACTIVE', 70),
        ('PRD-24001-NV-L',  'PRD-24001', 'NV', 'L',  NULL,            'ACTIVE', 80),

        -- 옥스포드 셔츠
        ('PRD-24002-WH-M',  'PRD-24002', 'WH', 'M',  '8801000000110', 'ACTIVE', 10),
        ('PRD-24002-WH-L',  'PRD-24002', 'WH', 'L',  '8801000000127', 'ACTIVE', 20),
        ('PRD-24002-BE-M',  'PRD-24002', 'BE', 'M',  '8801000000134', 'HOLD',   30),

        -- 와이드 데님 팬츠 — 하의 사이즈 체계
        ('PRD-24003-NV-28', 'PRD-24003', 'NV', '28', '8801000000210', 'ACTIVE', 10),
        ('PRD-24003-NV-30', 'PRD-24003', 'NV', '30', '8801000000227', 'ACTIVE', 20),
        ('PRD-24003-NV-32', 'PRD-24003', 'NV', '32', '8801000000234', 'ACTIVE', 30),
        ('PRD-24003-BK-30', 'PRD-24003', 'BK', '30', NULL,            'ACTIVE', 40),

        -- 울 블렌드 재킷 — 아직 기획 단계라 바코드 미발급
        ('PRD-24004-GY-M',  'PRD-24004', 'GY', 'M',  NULL,            'ACTIVE', 10),
        ('PRD-24004-GY-L',  'PRD-24004', 'GY', 'L',  NULL,            'ACTIVE', 20),

        -- 캔버스 백팩 — 단종 제품. 옵션이 없어 FREE 를 쓴다.
        ('PRD-23001-BK-FREE', 'PRD-23001', 'BK', 'FREE', '8801000000310', 'DISCARDED', 10),
        ('PRD-23001-BE-FREE', 'PRD-23001', 'BE', 'FREE', '8801000000327', 'HOLD',      20)
       ) AS v(sku_id, product_id, color_code, size_code, barcode, status, sort_order)
  JOIN tb_product p ON p.product_id = v.product_id;
