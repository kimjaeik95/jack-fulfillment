-- ============================================================================
-- 기준정보 메뉴를 한 단 더 나눈다
--
-- 기준정보 한 그룹에 14개가 매달려 있었다. 사이드바 45개 중 3분의 1이 여기고,
-- 플랜트 · 창고 · 빈 · 카테고리 · 브랜드 · 제품 · SKU · 채널 · 공급처 · 고객이
-- 한 줄로 늘어서 있어 찾으려던 것을 눈으로 훑어야 했다.
--
-- 같은 것을 다루는 것끼리 묶는다.
--
--   플랜트   플랜트 · 창고 · 빈 · 빈 바코드      — 재고를 놓는 자리
--   제품     카테고리 · 브랜드 · 제품 · SKU      — 파는 물건
--   채널     판매채널 · 채널 SKU 매핑            — 파는 곳
--   거래처   공급처 · 고객                       — 사고파는 상대
--   사유코드                                     — 어디에도 안 붙어 그대로 둔다
--
-- 중간 그룹에는 route 도 권한도 달지 않는다. 머리글일 뿐이고, 눌러서 갈 곳이
-- 없다. 권한은 그 아래 항목이 각자 갖고 있어, 하나도 못 보는 사람에게는
-- 머리글째 사라진다.
--
-- 되돌리려면 이 파일이 만든 5행(created_by='v19.mastersplit')을 지우고
-- 아래 UPDATE 의 parent 를 GRP_MASTER 로 되돌리면 된다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 제약을 먼저 푼다
--
--   ck_menu_route : (parent_seq IS NULL) = (route_name IS NULL)
--
-- '머리글은 최상위에만 있다' 를 뜻한다. 2단일 때는 맞았는데, 부모가 있는
-- 머리글(기준정보 › 플랜트)이 생기면서 틀렸다.
--
-- 대신 반쪽을 남긴다 — <b>화면을 가진 메뉴는 반드시 부모가 있다.</b> 최상위가
-- 눌리면 사이드바 맨 윗줄이 화면으로 가 버려서, 그 아래 묶음을 펼 수단이
-- 없어진다. 이건 깊이와 무관하게 참이다.
--
-- 잃은 반쪽('하위는 라우트가 있어야 한다')은 이제 표현할 수 없다. 머리글인지
-- 아닌지는 자식이 있느냐로 갈리는데 CHECK 는 다른 행을 못 본다. 그래서 갈 곳
-- 없는 잎은 사이드바가 ⚠ 로 드러낸다.
-- ----------------------------------------------------------------------------
ALTER TABLE tb_menu DROP CONSTRAINT ck_menu_route;
ALTER TABLE tb_menu ADD CONSTRAINT ck_menu_route
  CHECK (route_name IS NULL OR parent_seq IS NOT NULL);

-- ----------------------------------------------------------------------------
-- 중간 그룹
--
-- sort_order 는 '재고를 놓는 자리 → 파는 물건 → 파는 곳 → 상대' 순이다.
-- 기준정보를 처음 채울 때도 이 순서로 채운다 — 플랜트가 없으면 창고를 못 만들고,
-- 제품이 없으면 SKU 를 못 만든다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, NULL, v.icon, NULL, v.sort_order, 'v19.mastersplit'
  FROM (VALUES
        ('GRP_MST_PLANT',   '플랜트',  '🏭', 10),
        ('GRP_MST_PRODUCT', '제품',    '👕', 20),
        ('GRP_MST_CHANNEL', '채널',    '🛒', 30),
        ('GRP_MST_PARTNER', '거래처',  '🤝', 40)
       ) AS v(menu_id, menu_name, icon, sort_order)
  JOIN tb_menu g ON g.menu_id = 'GRP_MASTER';

-- ----------------------------------------------------------------------------
-- 기존 항목을 새 부모 아래로 옮긴다
--
-- sort_order 도 그룹 안에서 다시 매긴다. 옛 값(10~120)을 그대로 두면 그룹은
-- 달라졌는데 번호는 전체 기준이라, 나중에 항목을 끼워 넣을 때 어디에 넣어야
-- 할지 알 수 없다.
-- ----------------------------------------------------------------------------
UPDATE tb_menu m
   SET parent_seq = g.menu_seq,
       sort_order = v.sort_order,
       updated_by = 'v19.mastersplit',
       updated_at = CURRENT_TIMESTAMP
  FROM (VALUES
        ('MST_PLANTS',       'GRP_MST_PLANT',   10),
        ('MST_WAREHOUSES',   'GRP_MST_PLANT',   20),
        ('MST_LOCATIONS',    'GRP_MST_PLANT',   30),
        ('MST_LABELS',       'GRP_MST_PLANT',   40),

        ('MST_CATEGORIES',   'GRP_MST_PRODUCT', 10),
        ('MST_BRANDS',       'GRP_MST_PRODUCT', 20),
        ('MST_PRODUCTS',     'GRP_MST_PRODUCT', 30),
        ('MST_SKUS',         'GRP_MST_PRODUCT', 40),
        ('MST_SKU_BULK',     'GRP_MST_PRODUCT', 50),

        ('MST_CHANNELS',     'GRP_MST_CHANNEL', 10),
        ('MST_CHANNEL_SKUS', 'GRP_MST_CHANNEL', 20),

        ('MST_SUPPLIERS',    'GRP_MST_PARTNER', 10),
        ('MST_CUSTOMERS',    'GRP_MST_PARTNER', 20)
       ) AS v(menu_id, parent_id, sort_order)
  JOIN tb_menu g ON g.menu_id = v.parent_id
 WHERE m.menu_id = v.menu_id;

-- 사유코드는 어느 묶음에도 안 붙는다. 기준정보 바로 아래에 그대로 둔다.
UPDATE tb_menu
   SET sort_order = 90,
       updated_by = 'v19.mastersplit',
       updated_at = CURRENT_TIMESTAMP
 WHERE menu_id = 'MST_REASONS';

-- ============================================================================
-- 확인 — 기준정보 직속은 중간그룹 4 + 사유코드 1 = 5행이어야 한다
--
--   SELECT p.menu_name AS 부모, m.menu_name, m.sort_order
--     FROM tb_menu m JOIN tb_menu p ON p.menu_seq = m.parent_seq
--    WHERE p.menu_id = 'GRP_MASTER' OR p.menu_id LIKE 'GRP_MST_%'
--    ORDER BY p.sort_order, m.sort_order;
-- ============================================================================
