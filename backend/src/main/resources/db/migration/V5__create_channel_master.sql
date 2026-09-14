-- ============================================================================
-- V5 : 판매채널 · 채널 SKU 매핑 (MST-PG-010 / MST-PG-011)
--
-- 요구사항 5장의 데이터 목록을 따른다.
--   세일즈채널     채널코드 · 채널명 · 채널타입(자사몰/오픈마켓) · 사용상태
--   채널 SKU 매핑  채널 FK · SKU FK · 플랫폼 상품코드 · 플랫폼 옵션코드 ·
--                  등록자 · 매핑상태 · 매핑일시
--
-- 기능 요구사항이 거는 규칙 두 가지가 설계를 좌우한다.
--   MST-007  중지 채널의 신규 주문 자동 처리 금지
--   MST-008  1 SKU ↔ N 외부코드. 동일 채널 내 외부코드 중복 불가
--
-- '채널 판매가' 는 이번에 만들지 않는다. 요구사항 MST-011 이 가격 정본
-- 시스템(WMS 인가 외부 채널인가)을 [결정 필요] 로 남겨 두었기 때문이다.
-- 그 답에 따라 테이블이 있어야 하는지 자체가 달라진다 — 채널이 정본이면
-- WMS 는 주문 시점 단가만 스냅샷으로 들고 있으면 된다.
-- ============================================================================


-- ============================================================================
-- 1. 판매채널 — 수요가 발생하는 지점
-- ============================================================================

CREATE TABLE tb_channel (
    channel_seq   bigint       GENERATED ALWAYS AS IDENTITY,
    channel_id    varchar(20)  NOT NULL,
    channel_name  varchar(100) NOT NULL,
    channel_type  varchar(20)  NOT NULL,
    sort_order    integer      NOT NULL DEFAULT 0,
    use_yn        char(1)      NOT NULL DEFAULT 'Y',
    created_by    varchar(30)  NOT NULL,
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,
    CONSTRAINT pk_channel        PRIMARY KEY (channel_seq),
    CONSTRAINT uk_channel_id     UNIQUE (channel_id),
    CONSTRAINT uk_channel_name   UNIQUE (channel_name),
    CONSTRAINT ck_channel_use_yn CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_channel_type ON tb_channel (channel_type);

COMMENT ON TABLE  tb_channel              IS '판매채널 — 수요 발생 지점. MST-007';
COMMENT ON COLUMN tb_channel.channel_id   IS '채널코드 (예: OWN, CPNG)';
COMMENT ON COLUMN tb_channel.channel_type IS '채널유형 — 코드그룹 CHANNEL_TYPE (OWN/OPEN)';
COMMENT ON COLUMN tb_channel.use_yn       IS '사용여부. 중지 채널의 신규 주문은 자동 처리하지 않는다 (MST-007)';


-- ============================================================================
-- 2. 채널 SKU 매핑 — 외부 상품코드와 내부 SKU 를 잇는다
--
-- 방향이 중요하다. 1 SKU ↔ N 외부코드다(MST-008). 같은 티셔츠가 쿠팡에
-- 두 개의 상품으로 올라가 있을 수 있고, 그 둘이 같은 SKU 를 가리킨다.
-- 그래서 (채널, SKU) 에는 유일 제약을 걸지 않는다.
--
-- 반대로 (채널, 외부 상품코드, 외부 옵션코드) 는 유일해야 한다. 주문이
-- 들어올 때 그 조합으로 SKU 를 찾는데, 둘 이상이면 어느 SKU 인지 정할 수
-- 없어 주문을 처리할 수 없다.
-- ============================================================================

CREATE TABLE tb_channel_sku (
    mapping_seq      bigint       GENERATED ALWAYS AS IDENTITY,
    channel_seq      bigint       NOT NULL,
    sku_seq          bigint       NOT NULL,
    ext_product_code varchar(100) NOT NULL,
    ext_option_code  varchar(100),
    ext_product_name varchar(300),
    mapping_status   varchar(20)  NOT NULL DEFAULT 'MAPPED',
    mapped_at        timestamp,
    use_yn           char(1)      NOT NULL DEFAULT 'Y',
    created_by       varchar(30)  NOT NULL,
    created_at       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       varchar(30),
    updated_at       timestamp,
    CONSTRAINT pk_channel_sku        PRIMARY KEY (mapping_seq),
    CONSTRAINT fk_chsku_channel      FOREIGN KEY (channel_seq) REFERENCES tb_channel (channel_seq),
    CONSTRAINT fk_chsku_sku          FOREIGN KEY (sku_seq)     REFERENCES tb_sku (sku_seq),
    CONSTRAINT ck_chsku_use_yn       CHECK (use_yn IN ('Y', 'N'))
);

-- 동일 채널 내 외부코드 중복 금지 (MST-008).
-- 옵션코드가 없는 플랫폼(단일 상품)이 있어 NULL 을 허용하는데, PostgreSQL 은
-- NULL 을 서로 다른 값으로 보므로 한 인덱스로는 막지 못한다. 두 경우로 나눈다.
CREATE UNIQUE INDEX ux_chsku_ext_opt ON tb_channel_sku
    (channel_seq, ext_product_code, ext_option_code)
    WHERE ext_option_code IS NOT NULL;
CREATE UNIQUE INDEX ux_chsku_ext_noopt ON tb_channel_sku
    (channel_seq, ext_product_code)
    WHERE ext_option_code IS NULL;

CREATE INDEX ix_chsku_sku     ON tb_channel_sku (sku_seq);
CREATE INDEX ix_chsku_channel ON tb_channel_sku (channel_seq, mapping_status);

COMMENT ON TABLE  tb_channel_sku                  IS '채널 SKU 매핑 — 1 SKU ↔ N 외부코드. MST-008';
COMMENT ON COLUMN tb_channel_sku.ext_product_code IS '플랫폼 상품코드. 주문이 이 값으로 들어온다';
COMMENT ON COLUMN tb_channel_sku.ext_option_code  IS '플랫폼 옵션코드. 옵션이 없는 플랫폼은 비운다';
COMMENT ON COLUMN tb_channel_sku.ext_product_name IS '플랫폼에 노출된 상품명. 대조용이라 내부 제품명과 달라도 된다';
COMMENT ON COLUMN tb_channel_sku.mapping_status   IS '매핑상태 — 코드그룹 MAPPING_STATUS (PENDING/MAPPED/STOPPED)';
COMMENT ON COLUMN tb_channel_sku.mapped_at        IS '매핑 완료 일시. 상태가 MAPPED 로 바뀐 시점';


-- ============================================================================
-- 3. 공통코드
-- ============================================================================

INSERT INTO tb_code_group (code_group_id, code_group_name, description, created_by) VALUES
  ('CHANNEL_TYPE',   '채널 유형', '자사몰 · 오픈마켓',                  'system'),
  ('MAPPING_STATUS', '매핑 상태', '외부 상품코드와 SKU 의 연결 상태',   'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('CHANNEL_TYPE',   'OWN',     '자사몰',     '직접 운영하는 쇼핑몰',                    'violet', 10),
        ('CHANNEL_TYPE',   'OPEN',    '오픈마켓',   '쿠팡 · 무신사 등 외부 플랫폼',            'blue',   20),

        -- 매핑 행이 있는데도 PENDING 인 경우가 있다. 채널에 상품을 올려
        -- 두었지만 외부코드를 아직 받지 못한 상태다. 그대로 두면 주문이
        -- 들어와도 어느 SKU 인지 알 수 없다.
        ('MAPPING_STATUS', 'PENDING', '대기',       '외부코드 확인 전. 주문이 오면 처리할 수 없다', 'amber', 10),
        ('MAPPING_STATUS', 'MAPPED',  '매핑완료',   '주문을 SKU 로 연결할 수 있다',            'green',  20),
        ('MAPPING_STATUS', 'STOPPED', '중지',       '해당 채널에서 판매 중단',                 'gray',   30)
       ) AS v(code_group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.code_group_id;


-- ============================================================================
-- 4. 권한
--
-- 채널과 매핑을 나눈다. 채널을 새로 여는 것은 사업 결정이고, 매핑은 상품을
-- 올릴 때마다 하는 일상 작업이라 맡는 사람이 다를 수 있다.
-- MST_CHANNEL 은 V3 에 이미 있다.
-- ============================================================================

INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES
  ('MST_CHANNEL_SKU', '채널 SKU 매핑', 'MST', '기준정보 > 채널매핑', 135, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES ('MST_CHANNEL_SKU', 'RCUDX')) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 채널에도 다운로드(X)를 연다. 매핑 목록은 엑셀로 주고받는 일이 잦다.
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, 'X', 'system'
  FROM tb_permission p
 WHERE p.perm_id = 'MST_CHANNEL';

-- 시스템 관리자는 조회만 한다 (정책 P001 취지). 등록 · 수정은 기준정보 담당.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'SYS_ADMIN'
   AND p.perm_id IN ('MST_CHANNEL', 'MST_CHANNEL_SKU');


-- ============================================================================
-- 5. 메뉴
-- ============================================================================

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('MST_CHANNELS',    '판매채널 관리',  'GRP_MASTER', 'channels',    '🛒', 'MST_CHANNEL',     80),
        ('MST_CHANNEL_SKUS', '채널 SKU 매핑', 'GRP_MASTER', 'channel-skus', '🔗', 'MST_CHANNEL_SKU', 90)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
