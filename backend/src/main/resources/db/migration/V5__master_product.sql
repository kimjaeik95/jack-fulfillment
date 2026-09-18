-- ============================================================================
-- V4 : 제품 기준정보 — 카테고리 · 브랜드 · 제품 · SKU
--      (MST-PG-005 / 006 / 007 / 008)
--
-- 요구사항 5장의 데이터 목록을 따른다.
--   제품분류  분류명 · 대/중/소분류 · 사용상태
--   브랜드    브랜드명 · 식별코드 · 국가 · 사용상태
--   제품      제품분류 FK · 브랜드 FK · 제품명 · 상태 · 생산지 · 생산일자 ·
--             원가 · 시즌 · 출시연도
--   SKU       제품 FK · 색상 · 사이즈 · SKU 내부코드 · 바코드
--
-- 스펙에 없지만 넣은 것 두 가지. 둘 다 사용자 확인을 받았다.
--
--   1) 분류코드 · 제품코드
--      스펙은 분류명 · 제품명만 적는다. 그런데 재고 · 주문 · 업로드가
--      제품을 부를 방법이 이름뿐이면 이름을 바꿀 수 없다 — 바꾸는 순간
--      과거 데이터와 끊어진다. 브랜드(식별코드)와 SKU(내부코드)는 이미
--      코드를 갖고 있으므로 나머지 둘도 맞춘다.
--
--   2) SKU 상태
--      스펙에는 없는데 공통정책 P002 가 "SKU 폐기는 재고 0 및 미처리 건 0"
--      을 전제한다. 제품 상태만으로는 사이즈 하나를 단종시킬 수 없다.
--
-- 바코드 이력 테이블은 두지 않는다. MST-006 의 "재발급 시 이전 바코드 이력
-- 보관" 은 감사로그가 이미 컬럼 단위로 전/후를 남기므로 그것으로 성립한다.
-- 테이블을 따로 두면 같은 이력이 두 곳에 생기고 한쪽이 반드시 낡는다.
-- ============================================================================


-- ============================================================================
-- 1. 카테고리 — 대 · 중 · 소 자기참조 트리
--
-- 대/중/소를 세 컬럼으로 두지 않는 이유는, 그러면 분류가 데이터가 아니라
-- 컬럼이 되기 때문이다. 분류명 하나를 바꾸려면 그 분류에 속한 제품 행을
-- 모두 고쳐야 하고, 4단계로 늘리려면 스키마를 바꿔야 한다.
-- ============================================================================

CREATE TABLE tb_category (
    category_seq   bigint       GENERATED ALWAYS AS IDENTITY,
    category_id    varchar(20)  NOT NULL,
    category_name  varchar(100) NOT NULL,
    parent_seq     bigint,
    level_no       integer      NOT NULL,
    sort_order     integer      NOT NULL DEFAULT 0,
    use_yn         char(1)      NOT NULL DEFAULT 'Y',
    created_by     varchar(30)  NOT NULL,
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,
    CONSTRAINT pk_category         PRIMARY KEY (category_seq),
    CONSTRAINT uk_category_id      UNIQUE (category_id),
    CONSTRAINT fk_category_parent  FOREIGN KEY (parent_seq) REFERENCES tb_category (category_seq),
    CONSTRAINT ck_category_use_yn  CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_category_level   CHECK (level_no BETWEEN 1 AND 3),
    -- 대분류(1)만 부모가 없고, 중·소분류는 부모가 반드시 있다
    CONSTRAINT ck_category_parent_level CHECK ((level_no = 1) = (parent_seq IS NULL)),
    CONSTRAINT ck_category_not_self CHECK (parent_seq IS NULL OR parent_seq <> category_seq)
);

-- 분류명은 같은 부모 아래에서만 유일하다. 상의>티셔츠 와 아동>티셔츠 는
-- 둘 다 있을 수 있다. 부모가 NULL 인 대분류는 따로 본다 — PostgreSQL 은
-- NULL 을 서로 다른 값으로 보므로 한 인덱스로는 막지 못한다.
CREATE UNIQUE INDEX ux_category_name_root  ON tb_category (category_name)
    WHERE parent_seq IS NULL;
CREATE UNIQUE INDEX ux_category_name_child ON tb_category (parent_seq, category_name)
    WHERE parent_seq IS NOT NULL;

CREATE INDEX ix_category_parent ON tb_category (parent_seq, sort_order);

COMMENT ON TABLE  tb_category               IS '제품분류 — 대 · 중 · 소 트리. MST-005';
COMMENT ON COLUMN tb_category.category_seq  IS '분류 순번 (PK)';
COMMENT ON COLUMN tb_category.category_id   IS '분류코드 (예: CLO, CLO-TOP)';
COMMENT ON COLUMN tb_category.parent_seq    IS '상위 분류. 대분류는 NULL';
COMMENT ON COLUMN tb_category.level_no      IS '분류 단계 1=대 2=중 3=소';


-- ============================================================================
-- 2. 브랜드
--    스펙의 '식별코드' 가 brand_id 다.
-- ============================================================================

CREATE TABLE tb_brand (
    brand_seq     bigint       GENERATED ALWAYS AS IDENTITY,
    brand_id      varchar(20)  NOT NULL,
    brand_name    varchar(100) NOT NULL,
    country_code  varchar(20),
    sort_order    integer      NOT NULL DEFAULT 0,
    use_yn        char(1)      NOT NULL DEFAULT 'Y',
    created_by    varchar(30)  NOT NULL,
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,
    CONSTRAINT pk_brand        PRIMARY KEY (brand_seq),
    CONSTRAINT uk_brand_id     UNIQUE (brand_id),
    CONSTRAINT uk_brand_name   UNIQUE (brand_name),
    CONSTRAINT ck_brand_use_yn CHECK (use_yn IN ('Y', 'N'))
);

COMMENT ON TABLE  tb_brand              IS '브랜드. MST-005';
COMMENT ON COLUMN tb_brand.brand_id     IS '브랜드 식별코드';
COMMENT ON COLUMN tb_brand.country_code IS '브랜드 국가 — 코드그룹 COUNTRY';


-- ============================================================================
-- 3. 제품 — 고객이 보는 단위
--
-- 원가는 제품에 둔다(스펙). 사이즈별로 원가가 다른 경우는 지금 범위가 아니다.
-- 소수 두 자리를 남기는 이유는 수입 원가를 환산할 때 소수가 생기기 때문이다.
-- 반올림 시점을 스키마가 강제하지 않는다.
-- ============================================================================

CREATE TABLE tb_product (
    product_seq    bigint        GENERATED ALWAYS AS IDENTITY,
    product_id     varchar(30)   NOT NULL,
    product_name   varchar(200)  NOT NULL,
    category_seq   bigint        NOT NULL,
    brand_seq      bigint        NOT NULL,
    status         varchar(20)   NOT NULL DEFAULT 'PLANNED',
    origin_country varchar(20),
    produced_on    date,
    cost_amount    numeric(15,2),
    season         varchar(20),
    release_year   integer,
    sort_order     integer       NOT NULL DEFAULT 0,
    use_yn         char(1)       NOT NULL DEFAULT 'Y',
    created_by     varchar(30)   NOT NULL,
    created_at     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,
    CONSTRAINT pk_product          PRIMARY KEY (product_seq),
    CONSTRAINT uk_product_id       UNIQUE (product_id),
    -- 분류 · 브랜드에 제품이 걸려 있으면 지울 수 없다
    CONSTRAINT fk_product_category FOREIGN KEY (category_seq) REFERENCES tb_category (category_seq),
    CONSTRAINT fk_product_brand    FOREIGN KEY (brand_seq)    REFERENCES tb_brand (brand_seq),
    CONSTRAINT ck_product_use_yn   CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_product_cost     CHECK (cost_amount IS NULL OR cost_amount >= 0),
    CONSTRAINT ck_product_year     CHECK (release_year IS NULL OR release_year BETWEEN 1900 AND 2999)
);

CREATE INDEX ix_product_category ON tb_product (category_seq);
CREATE INDEX ix_product_brand    ON tb_product (brand_seq);
CREATE INDEX ix_product_status   ON tb_product (status);

COMMENT ON TABLE  tb_product                IS '제품 — 고객이 보는 단위. MST-005';
COMMENT ON COLUMN tb_product.product_id     IS '제품코드';
COMMENT ON COLUMN tb_product.status         IS '제품상태 — 코드그룹 PRODUCT_STATUS';
COMMENT ON COLUMN tb_product.origin_country IS '생산지 — 코드그룹 COUNTRY';
COMMENT ON COLUMN tb_product.cost_amount    IS '원가(원). 수입 환산 소수를 위해 두 자리를 남긴다';
COMMENT ON COLUMN tb_product.season         IS '시즌 — 코드그룹 SEASON. 출시연도와 합쳐 24SS 처럼 읽는다';


-- ============================================================================
-- 4. SKU — 모든 트랜잭션의 FK 기준
--
-- 색상 · 사이즈를 NOT NULL 로 두는 이유가 있다. "동일 제품 내 옵션 조합
-- 중복 불가"(MST-005)를 UNIQUE 로 거는데, NULL 을 허용하면 PostgreSQL 이
-- NULL 을 서로 다른 값으로 보아 같은 조합이 여러 번 들어간다.
-- 옵션이 없는 제품은 FREE(단일) 코드를 쓴다.
-- ============================================================================

CREATE TABLE tb_sku (
    sku_seq      bigint       GENERATED ALWAYS AS IDENTITY,
    sku_id       varchar(40)  NOT NULL,
    product_seq  bigint       NOT NULL,
    color_code   varchar(20)  NOT NULL,
    size_code    varchar(20)  NOT NULL,
    barcode      varchar(50),
    status       varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    sort_order   integer      NOT NULL DEFAULT 0,
    use_yn       char(1)      NOT NULL DEFAULT 'Y',
    created_by   varchar(30)  NOT NULL,
    created_at   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   varchar(30),
    updated_at   timestamp,
    CONSTRAINT pk_sku          PRIMARY KEY (sku_seq),
    -- SKU 내부코드 유일 (MST-005)
    CONSTRAINT uk_sku_id       UNIQUE (sku_id),
    -- 동일 제품 내 옵션 조합 중복 불가 (MST-005)
    CONSTRAINT uk_sku_option   UNIQUE (product_seq, color_code, size_code),
    CONSTRAINT fk_sku_product  FOREIGN KEY (product_seq) REFERENCES tb_product (product_seq),
    CONSTRAINT ck_sku_use_yn   CHECK (use_yn IN ('Y', 'N'))
);

-- 바코드 전역 유일 (MST-006). 아직 발급하지 않은 SKU 는 비어 있을 수 있다.
CREATE UNIQUE INDEX ux_sku_barcode ON tb_sku (barcode) WHERE barcode IS NOT NULL;

CREATE INDEX ix_sku_product ON tb_sku (product_seq, sort_order);
CREATE INDEX ix_sku_status  ON tb_sku (status);

COMMENT ON TABLE  tb_sku             IS 'SKU — 모든 트랜잭션의 FK 기준. MST-005';
COMMENT ON COLUMN tb_sku.sku_id      IS 'SKU 내부코드. 전역 유일';
COMMENT ON COLUMN tb_sku.color_code  IS '색상 — 코드그룹 COLOR. 옵션이 없으면 FREE';
COMMENT ON COLUMN tb_sku.size_code   IS '사이즈 — 코드그룹 SIZE. 옵션이 없으면 FREE';
COMMENT ON COLUMN tb_sku.barcode     IS '바코드. 전역 유일. 변경 이력은 감사로그가 남긴다';
COMMENT ON COLUMN tb_sku.status      IS 'SKU상태 — 코드그룹 SKU_STATUS. 폐기는 정책 P002 가 선행조건을 건다';


-- ============================================================================
-- 5. 공통코드
--
-- 화면의 셀렉트박스와 서버 검증이 모두 이 표를 읽는다. 목록을 코드에 적어
-- 두면 코드그룹이 바뀔 때 어긋난다.
-- ============================================================================

INSERT INTO tb_code_group (code_group_id, code_group_name, description, created_by) VALUES
  ('PRODUCT_STATUS', '제품 상태',  '기획 · 판매중 · 단종',                      'system'),
  ('SKU_STATUS',     'SKU 상태',   '판매중 · 일시중지 · 폐기',                  'system'),
  ('COLOR',          '색상',        'SKU 옵션. 자유 입력을 막아 같은 색이 갈라지는 것을 방지', 'system'),
  ('SIZE',           '사이즈',      'SKU 옵션. 상의 · 하의 체계를 함께 담는다',  'system'),
  ('SEASON',         '시즌',        '출시연도와 합쳐 24SS 처럼 읽는다',          'system'),
  ('COUNTRY',        '국가',        '브랜드 국가 · 제품 생산지',                 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        -- 제품 상태 ------------------------------------------------------
        ('PRODUCT_STATUS', 'PLANNED',      '기획',       '아직 판매하지 않는다',              'gray',   10),
        ('PRODUCT_STATUS', 'ACTIVE',       '판매중',     '정상 판매',                         'green',  20),
        ('PRODUCT_STATUS', 'DISCONTINUED', '단종',       '신규 판매 중지. 재고는 소진한다',   'red',    30),

        -- SKU 상태 -------------------------------------------------------
        -- 폐기는 정책 P002 가 "재고 0 · 미처리 0" 을 선행조건으로 건다
        ('SKU_STATUS',     'ACTIVE',       '판매중',     '정상 판매',                         'green',  10),
        ('SKU_STATUS',     'HOLD',         '일시중지',   '일시 판매 중지. 재고는 그대로 둔다', 'amber', 20),
        ('SKU_STATUS',     'DISCARDED',    '폐기',       '재고 0 · 미처리 0 일 때만 가능',    'red',    30),

        -- 색상 -----------------------------------------------------------
        ('COLOR',          'BK',           '블랙',       NULL,                                 'slate',  10),
        ('COLOR',          'WH',           '화이트',     NULL,                                 'gray',   20),
        ('COLOR',          'GY',           '그레이',     NULL,                                 'gray',   30),
        ('COLOR',          'NV',           '네이비',     NULL,                                 'blue',   40),
        ('COLOR',          'BE',           '베이지',     NULL,                                 'amber',  50),
        ('COLOR',          'RD',           '레드',       NULL,                                 'red',    60),
        ('COLOR',          'BL',           '블루',       NULL,                                 'blue',   70),
        ('COLOR',          'GR',           '그린',       NULL,                                 'green',  80),
        ('COLOR',          'FREE',         '단일',       '색상 옵션이 없는 제품',              'cyan',   99),

        -- 사이즈 ---------------------------------------------------------
        -- 상의(S/M/L)와 하의(28/30/32) 체계를 한 그룹에 함께 둔다. 분류별로
        -- 고를 수 있는 사이즈를 제한하는 것은 아직 하지 않는다.
        ('SIZE',           'XS',           'XS',         '상의',                               'gray',   10),
        ('SIZE',           'S',            'S',          '상의',                               'gray',   20),
        ('SIZE',           'M',            'M',          '상의',                               'gray',   30),
        ('SIZE',           'L',            'L',          '상의',                               'gray',   40),
        ('SIZE',           'XL',           'XL',         '상의',                               'gray',   50),
        ('SIZE',           'XXL',          'XXL',        '상의',                               'gray',   60),
        ('SIZE',           '28',           '28',         '하의',                               'blue',   70),
        ('SIZE',           '30',           '30',         '하의',                               'blue',   80),
        ('SIZE',           '32',           '32',         '하의',                               'blue',   90),
        ('SIZE',           '34',           '34',         '하의',                               'blue',   95),
        ('SIZE',           'FREE',         '단일',       '사이즈 옵션이 없는 제품',            'cyan',   99),

        -- 시즌 -----------------------------------------------------------
        ('SEASON',         'SS',           '봄·여름',    'Spring/Summer',                      'green',  10),
        ('SEASON',         'FW',           '가을·겨울',  'Fall/Winter',                        'amber',  20),
        ('SEASON',         'ALL',          '사계절',     '시즌 구분 없음',                     'gray',   30),

        -- 국가 -----------------------------------------------------------
        ('COUNTRY',        'KR',           '대한민국',   NULL,                                 'blue',   10),
        ('COUNTRY',        'CN',           '중국',       NULL,                                 'red',    20),
        ('COUNTRY',        'VN',           '베트남',     NULL,                                 'green',  30),
        ('COUNTRY',        'ID',           '인도네시아', NULL,                                 'teal',   40),
        ('COUNTRY',        'IT',           '이탈리아',   NULL,                                 'violet', 50),
        ('COUNTRY',        'US',           '미국',       NULL,                                 'slate',  60),
        ('COUNTRY',        'JP',           '일본',       NULL,                                 'pink',   70),
        ('COUNTRY',        'ETC',          '기타',       NULL,                                 'gray',   99)
       ) AS v(code_group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.code_group_id;


-- ============================================================================
-- 6. 권한
--
-- 기존 MST_SKU 는 '제품/SKU 관리' 였다. 프로그램 목록이 제품(MST-PG-007)과
-- SKU(MST-PG-008)를 나누므로 권한도 나눈다.
-- ============================================================================

INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('MST_CATEGORY', '카테고리 관리', 'MST', '기준정보 > 카테고리', 108, 'system'),
  ('MST_PRODUCT',  '제품 관리',     'MST', '기준정보 > 제품',     112, 'system');

UPDATE tb_permission
   SET perm_name  = 'SKU 관리',
       updated_by = 'system',
       updated_at = CURRENT_TIMESTAMP
 WHERE perm_id = 'MST_SKU';

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('MST_CATEGORY', 'RCUDX'),
        ('MST_PRODUCT',  'RCUDX')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 브랜드 · SKU 에도 다운로드(X)를 연다. 제품 기준정보는 엑셀로 주고받는 일이
-- 잦은데, 조회할 수 있다고 파일로 빼도 되는 것은 아니므로 액션을 따로 둔다.
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, 'X', 'system'
  FROM tb_permission p
 WHERE p.perm_id IN ('MST_BRAND', 'MST_SKU');

-- 시스템 관리자는 조회만 한다. 제품 · SKU 는 업무 데이터이고, 설정 권한과
-- 업무 데이터 변경 권한을 분리하는 것이 정책 P001 의 취지다. 등록 · 수정은
-- 기준정보 담당 역할이 한다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'SYS_ADMIN'
   AND p.perm_id IN ('MST_CATEGORY', 'MST_BRAND', 'MST_PRODUCT', 'MST_SKU');


-- ============================================================================
-- 7. 메뉴
-- ============================================================================

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('MST_CATEGORIES', '카테고리 관리', 'GRP_MST_PRODUCT', 'categories', '🗂', 'MST_CATEGORY', 10),
        ('MST_BRANDS',     '브랜드 관리',   'GRP_MST_PRODUCT', 'brands',     '🏷', 'MST_BRAND',    20),
        ('MST_PRODUCTS',   '제품 관리',     'GRP_MST_PRODUCT', 'products',   '👕', 'MST_PRODUCT',  30),
        ('MST_SKUS',       'SKU 관리',      'GRP_MST_PRODUCT', 'skus',       '🔖', 'MST_SKU',      40),
        -- 일괄생성은 별도 권한을 두지 않는다. 하는 일이 SKU 를 만드는 것이라
        -- MST_SKU 의 C 와 같다 — 권한을 나누면 "SKU 는 만들 수 있지만 한 번에
        -- 만들지는 못하는 사람" 이라는, 업무에 없는 역할이 생긴다.
        ('MST_SKU_BULK',   'SKU 일괄생성',  'GRP_MST_PRODUCT', 'sku-bulk',   '⚡', 'MST_SKU',      50)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
