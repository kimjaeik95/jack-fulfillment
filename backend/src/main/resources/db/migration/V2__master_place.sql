-- ============================================================================
-- V2 : 거점 — 플랜트 · 창고 · 빈
--
-- 조직(V1)에서 떼어 둔 이유는 둘의 수명이 다르기 때문이다. 조직은 개편되지만
-- 거점은 그대로 있고, 그 반대도 있다. 플랜트가 조직을 참조하므로 조직개편은
-- 플랜트의 org_seq 만 바꾸면 되고, 그 아래 창고 · 빈 · 재고는 건드리지 않는다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 유형 컬럼(plant_type / warehouse_type / location_type)은 tb_code 를 참조하지만
-- FK 는 걸지 않는다 — V1 설계규칙 4).
-- ----------------------------------------------------------------------------


-- ----------------------------------------------------------------------------
-- 플랜트 (물류센터) — 재고의 원천
-- ----------------------------------------------------------------------------
CREATE TABLE tb_plant (
    plant_seq     bigint       GENERATED ALWAYS AS IDENTITY,
    org_seq       bigint       NOT NULL,
    plant_id      varchar(20)  NOT NULL,
    plant_name    varchar(100) NOT NULL,
    plant_type    varchar(20)  NOT NULL,
    zip_code      varchar(10),
    address       varchar(300),
    manager_name  varchar(50),
    phone         varchar(30),
    sort_order    integer      NOT NULL DEFAULT 0,
    use_yn        char(1)      NOT NULL DEFAULT 'Y',
    created_by    varchar(30)  NOT NULL,
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,
    CONSTRAINT pk_plant         PRIMARY KEY (plant_seq),
    CONSTRAINT uk_plant_id      UNIQUE (plant_id),
    CONSTRAINT uk_plant_name    UNIQUE (plant_name),
    -- 플랜트가 딸린 조직은 삭제를 막는다
    CONSTRAINT fk_plant_org     FOREIGN KEY (org_seq) REFERENCES tb_org (org_seq),
    CONSTRAINT ck_plant_use_yn  CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_plant_org  ON tb_plant (org_seq);
CREATE INDEX ix_plant_type ON tb_plant (plant_type);

COMMENT ON TABLE  tb_plant              IS '플랜트 (물류센터) — 재고의 원천. MST-001';
COMMENT ON COLUMN tb_plant.plant_seq    IS '플랜트 순번 (PK)';
COMMENT ON COLUMN tb_plant.org_seq      IS '운영 조직 순번. 조직개편 시 이 값만 바꾼다';
COMMENT ON COLUMN tb_plant.plant_id     IS '플랜트코드 (예: PL001)';
COMMENT ON COLUMN tb_plant.plant_type   IS '플랜트유형 — 코드그룹 PLANT_TYPE (DC/RC/XD)';


-- ----------------------------------------------------------------------------
-- 창고 — 플랜트 안의 구획 (양품 / 반품 / 불량)
-- ----------------------------------------------------------------------------
CREATE TABLE tb_warehouse (
    warehouse_seq   bigint       GENERATED ALWAYS AS IDENTITY,
    plant_seq       bigint       NOT NULL,
    warehouse_id    varchar(20)  NOT NULL,
    warehouse_name  varchar(100) NOT NULL,
    warehouse_type  varchar(20)  NOT NULL,
    position_desc   varchar(200),
    sort_order      integer      NOT NULL DEFAULT 0,
    use_yn          char(1)      NOT NULL DEFAULT 'Y',
    created_by      varchar(30)  NOT NULL,
    created_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      varchar(30),
    updated_at      timestamp,
    CONSTRAINT pk_warehouse         PRIMARY KEY (warehouse_seq),
    -- 코드는 플랜트 안에서만 유일하다. 센터마다 양품 창고가 있는 것이
    -- 정상이고, 전역 유일로 두면 코드에 플랜트를 중복해 적어야 한다.
    CONSTRAINT uk_warehouse_id      UNIQUE (plant_seq, warehouse_id),
    CONSTRAINT uk_warehouse_name    UNIQUE (plant_seq, warehouse_name),
    CONSTRAINT fk_warehouse_plant   FOREIGN KEY (plant_seq) REFERENCES tb_plant (plant_seq),
    CONSTRAINT ck_warehouse_use_yn  CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_warehouse_plant ON tb_warehouse (plant_seq, sort_order);
CREATE INDEX ix_warehouse_type  ON tb_warehouse (warehouse_type);

COMMENT ON TABLE  tb_warehouse                IS '창고 — 플랜트 내 구획. MST-002';
COMMENT ON COLUMN tb_warehouse.warehouse_seq  IS '창고 순번 (PK)';
COMMENT ON COLUMN tb_warehouse.warehouse_id   IS '창고코드. 플랜트 안에서 유일 (예: GD, RT, DF)';
COMMENT ON COLUMN tb_warehouse.warehouse_type IS '창고유형 — 코드그룹 WH_TYPE (GOOD/RETURN/DEFECT)';
COMMENT ON COLUMN tb_warehouse.position_desc  IS '물리적 위치 설명. position 은 함수명과 겹쳐 회피';


-- ----------------------------------------------------------------------------
-- 빈 — 피킹 · 적치 단위
-- ----------------------------------------------------------------------------
CREATE TABLE tb_location (
    location_seq   bigint       GENERATED ALWAYS AS IDENTITY,
    warehouse_seq  bigint       NOT NULL,
    location_id    varchar(30)  NOT NULL,
    sector         varchar(20),
    zone_code      varchar(20),
    floor_no       varchar(20),
    location_type  varchar(20)  NOT NULL,
    barcode        varchar(50),
    sort_order     integer      NOT NULL DEFAULT 0,
    use_yn         char(1)      NOT NULL DEFAULT 'Y',
    created_by     varchar(30)  NOT NULL,
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,
    CONSTRAINT pk_location         PRIMARY KEY (location_seq),
    /*
     * 빈코드는 창고 안에서만 유일하다 (MST-PG-003).
     *
     * 전역 유일로 두면 두 번째 센터가 같은 코드를 쓰지 못한다. 현장은 자기
     * 센터의 랙을 자연스럽게 1A · 1B 로 부르는데, 전역 유일을 강요하면
     * "이천은 A~M, 김해는 N~Z" 같은 사람이 기억해야 하는 규칙이 생기고
     * 센터가 늘수록 구역 문자가 동난다.
     *
     * 창고코드가 이미 '플랜트 안에서만 유일' 이므로 층위도 맞는다.
     *
     * 대신 빈코드만으로는 한 곳이 지목되지 않는다. 그 역할은 바코드가 혼자
     * 진다 — 아래 ux_location_barcode 를 보라.
     */
    CONSTRAINT uk_location_wh_id   UNIQUE (warehouse_seq, location_id),
    CONSTRAINT fk_location_wh      FOREIGN KEY (warehouse_seq)
        REFERENCES tb_warehouse (warehouse_seq),
    CONSTRAINT ck_location_use_yn  CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_location_wh   ON tb_location (warehouse_seq, sort_order);
CREATE INDEX ix_location_type ON tb_location (location_type);
-- 바코드는 전역 유일이다. 빈코드가 창고별 유일로 바뀐 뒤 '스캔 한 번으로 한 곳'
-- 을 지목하는 역할은 바코드가 혼자 진다 — 라벨에 찍히는 값은 센터 · 창고까지
-- 담아 'PL001GD1A0101' 처럼 만든다 (Location.barcodeValue).
-- 값이 없는 빈(미발급)은 NULL 이라 인덱스에 걸리지 않는다.
CREATE UNIQUE INDEX ux_location_barcode ON tb_location (barcode)
    WHERE barcode IS NOT NULL;

COMMENT ON TABLE  tb_location               IS '빈 — 피킹 · 적치 단위. MST-003';
COMMENT ON COLUMN tb_location.location_seq  IS '빈 순번 (PK)';
COMMENT ON COLUMN tb_location.location_id   IS '빈코드. 창고 안에서만 유일 (예: 1A-01-01)';
COMMENT ON COLUMN tb_location.sector        IS '섹터';
COMMENT ON COLUMN tb_location.zone_code     IS '구역. zone 은 AT TIME ZONE 과 겹쳐 회피';
COMMENT ON COLUMN tb_location.floor_no      IS '층. floor 는 내장 함수명과 겹쳐 회피';
COMMENT ON COLUMN tb_location.location_type IS '빈유형 — 코드그룹 LOC_TYPE (NORMAL/RETURN/DEFECT/TRANSIT)';
COMMENT ON COLUMN tb_location.barcode       IS '라벨 바코드. 비우면 location_id 를 그대로 쓴다';

