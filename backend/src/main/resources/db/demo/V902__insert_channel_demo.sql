-- ============================================================================
-- V902 : 판매채널 · 채널 SKU 매핑 데모 (로컬 개발 전용)
--
-- 이 폴더(db/demo)는 local 프로파일에서만 Flyway locations 에 포함된다.
--
-- 매핑을 일부러 다 채우지 않는다. 매핑이 없는 SKU 가 있어야 "매핑 없이
-- 판매가 개시되는 것을 방지"(MST-009)하는 누락 점검 화면을 볼 수 있다.
-- ============================================================================


-- ============================================================================
-- 1. 권한
--    채널과 매핑은 기준정보 담당의 일이다. CS 는 주문을 보다가 "이 외부코드가
--    어느 SKU 인가" 를 확인해야 하므로 매핑을 읽을 수 있어야 한다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('HQ_MASTER', 'MST_CHANNEL',     'X'),
        ('HQ_MASTER', 'MST_CHANNEL_SKU', 'RCUDX')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'CS_VIEWER'
   AND p.perm_id IN ('MST_CHANNEL', 'MST_CHANNEL_SKU');


-- ============================================================================
-- 2. 판매채널
-- ============================================================================
INSERT INTO tb_channel (channel_id, channel_name, channel_type, sort_order, use_yn, created_by) VALUES
  ('OWN',  '잭 공식몰',   'OWN',  10, 'Y', 'system'),
  ('CPNG', '쿠팡',        'OPEN', 20, 'Y', 'system'),
  ('MSSN', '무신사',      'OPEN', 30, 'Y', 'system'),
  -- 중지 채널이 하나 있어야 "중지 채널의 신규 주문 자동 처리 금지"(MST-007)를
  -- 화면에서 확인할 수 있다.
  ('SMST', '스마트스토어', 'OPEN', 40, 'N', 'system');


-- ============================================================================
-- 3. 채널 SKU 매핑
--
-- 1 SKU ↔ N 외부코드를 보여준다. 공식몰의 반팔 티셔츠 블랙 M 은 하나지만,
-- 쿠팡에는 같은 SKU 가 단품과 2매 묶음 두 상품으로 올라가 있다.
-- ============================================================================
INSERT INTO tb_channel_sku (channel_seq, sku_seq, ext_product_code, ext_option_code,
                            ext_product_name, mapping_status, mapped_at, created_by)
SELECT c.channel_seq, s.sku_seq, v.ext_product_code, v.ext_option_code,
       v.ext_product_name, v.mapping_status,
       CASE WHEN v.mapping_status = 'MAPPED' THEN TIMESTAMP '2026-03-02 10:00:00' END,
       'system'
  FROM (VALUES
        -- 공식몰 — 옵션코드를 쓰는 플랫폼
        ('OWN',  'PRD-24001-BK-S', 'OWN-24001', 'BK-S', '베이직 반팔 티셔츠', 'MAPPED'),
        ('OWN',  'PRD-24001-BK-M', 'OWN-24001', 'BK-M', '베이직 반팔 티셔츠', 'MAPPED'),
        ('OWN',  'PRD-24001-BK-L', 'OWN-24001', 'BK-L', '베이직 반팔 티셔츠', 'MAPPED'),
        ('OWN',  'PRD-24001-WH-M', 'OWN-24001', 'WH-M', '베이직 반팔 티셔츠', 'MAPPED'),
        ('OWN',  'PRD-24002-WH-M', 'OWN-24002', 'WH-M', '옥스포드 셔츠',      'MAPPED'),
        ('OWN',  'PRD-24003-NV-30', 'OWN-24003', 'NV-30', '와이드 데님 팬츠', 'MAPPED'),

        -- 쿠팡 — 같은 SKU 가 두 상품에 걸린다 (단품 · 2매 묶음)
        ('CPNG', 'PRD-24001-BK-M', 'CP7788001', '1000123456', '[잭] 베이직 반팔티 블랙 M',      'MAPPED'),
        ('CPNG', 'PRD-24001-BK-M', 'CP7788999', '1000999888', '[잭] 베이직 반팔티 2매 블랙 M',  'MAPPED'),
        ('CPNG', 'PRD-24001-WH-M', 'CP7788001', '1000123457', '[잭] 베이직 반팔티 화이트 M',    'MAPPED'),

        -- 무신사 — 옵션코드가 없는 플랫폼 (상품코드 하나로 지목)
        ('MSSN', 'PRD-24002-WH-L', 'MSS-330011', NULL, '옥스포드 셔츠 화이트 L', 'MAPPED'),
        -- 상품은 올렸는데 외부코드를 아직 못 받은 상태
        ('MSSN', 'PRD-24003-NV-32', 'MSS-330022', NULL, '와이드 데님 팬츠 32',   'PENDING'),

        -- 스마트스토어는 채널이 중지되어 매핑도 중지
        ('SMST', 'PRD-23001-BK-FREE', 'SS-9001', NULL, '캔버스 백팩 블랙',       'STOPPED')
       ) AS v(channel_id, sku_id, ext_product_code, ext_option_code,
              ext_product_name, mapping_status)
  JOIN tb_channel c ON c.channel_id = v.channel_id
  JOIN tb_sku     s ON s.sku_id     = v.sku_id;
