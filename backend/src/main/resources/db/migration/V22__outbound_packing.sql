-- ============================================================================
-- 출고검수 · 박스 · 패킹 (OUT-PG-006, PAC-PG-001, PAC-PG-002 / 4차 C섹터)
--
-- 피킹이 '집었다' 라면 패킹은 '박스에 담았다' 이다.
--
-- 여기서도 재고 수량은 바뀌지 않는다. 물건은 카트에서 박스로 옮겨졌을 뿐
-- 아직 창고 안에 있고, 보유수량이 줄어드는 것은 출고확정(E섹터)뿐이다.
--
-- 새로 들어오는 개념은 <b>박스</b> 하나다. 지시 하나가 박스 여러 개로 나갈
-- 수 있고(부피 초과), 박스마다 송장이 하나씩 붙는다 (D섹터).
-- ============================================================================


-- ============================================================================
-- 1. 출고검수 (OUT-PG-006)
--
-- 집은 것을 다시 세는 일이다. 왜 또 세냐면, 피킹은 '빈 앞에서' 찍고 검수는
-- '카트를 앞에 두고' 찍기 때문이다 — 집는 중에 옆 칸 물건이 섞이거나
-- 카트가 바뀌는 일이 실제로 있다.
--
-- 표를 새로 만들지 않는다. 지시 라인에 검수수량 한 칸을 더한다 — 검수는
-- 피킹과 같은 대상(지시 줄)을 세는 일이고, 문서를 하나 더 만들면 '집은
-- 수량' 과 '센 수량' 이 서로 다른 표에 흩어져 대사할 때마다 조인해야 한다.
-- ============================================================================
ALTER TABLE tb_outbound_line
    ADD COLUMN inspected_qty integer NOT NULL DEFAULT 0;

ALTER TABLE tb_outbound_line
    ADD CONSTRAINT ck_outbl_inspected CHECK (inspected_qty >= 0),
    -- 집은 것보다 많이 셀 수는 없다. 많으면 카트에 남의 물건이 들어온 것이고,
    -- 그건 세는 것이 아니라 찾아내야 할 사고다.
    ADD CONSTRAINT ck_outbl_insp_max CHECK (inspected_qty <= picked_qty);

COMMENT ON COLUMN tb_outbound_line.inspected_qty IS '출고검수에서 다시 센 수량 (OUT-PG-006)';

ALTER TABLE tb_outbound
    ADD COLUMN inspected_by varchar(30),
    ADD COLUMN inspected_at timestamp;

COMMENT ON COLUMN tb_outbound.inspected_by IS '출고검수를 끝낸 사람 (OUT-PG-006)';


-- ============================================================================
-- 2. 박스 (PAC-PG-001)
--
-- 실제로 물건이 들어가는 상자 하나. 송장이 여기 붙는다 (D섹터).
--
-- <b>지시에 매단다.</b> 주문이 아니라 지시인 이유는, 지시가 창고 작업의
-- 단위이고 박스를 닫는 것도 창고에서 하는 일이기 때문이다. 나중에 합포로
-- 지시 하나가 주문 여럿을 담게 되면 박스도 자연히 따라간다.
--
-- 박스번호는 지시 안에서만 센다 (1, 2, 3...). 전역 번호를 채번하지 않는
-- 이유는, 사람이 부르는 이름이 '이 주문의 2번 박스' 이지 'BOX-00012345'
-- 가 아니기 때문이다. 밖으로 나가는 식별자는 송장번호다.
-- ============================================================================
CREATE TABLE tb_pack_box (
    box_seq       bigint      GENERATED ALWAYS AS IDENTITY,
    outbound_seq  bigint      NOT NULL,
    -- 이 지시 안에서의 번호. 1 부터.
    box_no        integer     NOT NULL,

    -- 코드그룹 BOX_STATUS
    box_status    varchar(20) NOT NULL DEFAULT 'OPEN',

    -- 코드그룹 BOX_TYPE. 택배사 요금이 규격으로 갈려 골라 둔다.
    box_type      varchar(20),

    /*
     * 실측 무게 · 크기.
     *
     * 택배사에 넘길 값이라 비워 둘 수 있다 — 저울이 없는 센터가 있고,
     * 규격 박스만 쓰면 무게를 안 재기도 한다. 송장 발급(D섹터)이 필요하면
     * 그때 요구한다.
     */
    weight_g      integer,
    width_mm      integer,
    height_mm     integer,
    depth_mm      integer,

    -- 닫은 사람 · 시각. 닫으면 더 담을 수 없다.
    closed_by     varchar(30),
    closed_at     timestamp,

    remark        varchar(300),

    created_by    varchar(30) NOT NULL,
    created_at    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,

    CONSTRAINT pk_pack_box      PRIMARY KEY (box_seq),
    CONSTRAINT uk_pack_box_no   UNIQUE (outbound_seq, box_no),
    CONSTRAINT fk_packbox_out   FOREIGN KEY (outbound_seq) REFERENCES tb_outbound (outbound_seq)
                                ON DELETE CASCADE,
    CONSTRAINT ck_packbox_status CHECK (box_status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_packbox_no     CHECK (box_no > 0),
    CONSTRAINT ck_packbox_measure CHECK (
        (weight_g IS NULL OR weight_g > 0)
        AND (width_mm  IS NULL OR width_mm  > 0)
        AND (height_mm IS NULL OR height_mm > 0)
        AND (depth_mm  IS NULL OR depth_mm  > 0)),
    -- 닫았으면 누가 언제 닫았는지가 있어야 한다
    CONSTRAINT ck_packbox_closed CHECK (box_status <> 'CLOSED'
                                     OR (closed_by IS NOT NULL AND closed_at IS NOT NULL))
);

CREATE INDEX ix_packbox_out ON tb_pack_box (outbound_seq, box_no);

COMMENT ON TABLE  tb_pack_box            IS '출고 박스 (PAC-PG-001). 송장이 여기 붙는다';
COMMENT ON COLUMN tb_pack_box.box_no     IS '지시 안에서의 번호. 밖으로 나가는 식별자는 송장번호다';
COMMENT ON COLUMN tb_pack_box.box_status IS 'OPEN(담는 중) / CLOSED(닫음). 닫으면 더 못 담는다';


-- ============================================================================
-- 3. 박스에 담은 것 (PAC-PG-002)
--
-- 어느 박스에 어느 지시 줄을 몇 개 담았나.
--
-- 지시 줄을 가리킨다. SKU 를 직접 적지 않는 이유는, '무엇을' 은 이미 지시
-- 줄이 들고 있고 여기서 또 적으면 둘이 어긋날 수 있기 때문이다.
--
-- 한 줄이 여러 박스에 나뉠 수 있다. 티셔츠 10 장이 부피 때문에 두 박스로
-- 갈리는 것이 정상이라, (박스, 줄) 조합을 유일하게 두되 같은 줄이 다른
-- 박스에 또 나오는 것은 막지 않는다.
-- ============================================================================
CREATE TABLE tb_pack_box_line (
    box_line_seq bigint      GENERATED ALWAYS AS IDENTITY,
    box_seq      bigint      NOT NULL,
    -- 어느 지시 줄을 담았나
    line_seq     bigint      NOT NULL,

    packed_qty   integer     NOT NULL,

    packed_by    varchar(30) NOT NULL,
    packed_at    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_pack_box_line   PRIMARY KEY (box_line_seq),
    -- 한 박스에 같은 줄을 두 번 담지 않는다. 두 줄이면 몇 개가 들었는지
    -- 정할 수 없고, 빼려 할 때 어느 줄을 뺄지도 모른다 — 더 담으려면
    -- 수량을 더한다.
    CONSTRAINT uk_packbl_line     UNIQUE (box_seq, line_seq),
    CONSTRAINT fk_packbl_box      FOREIGN KEY (box_seq)  REFERENCES tb_pack_box (box_seq)
                                  ON DELETE CASCADE,
    CONSTRAINT fk_packbl_line     FOREIGN KEY (line_seq) REFERENCES tb_outbound_line (line_seq),
    CONSTRAINT ck_packbl_qty      CHECK (packed_qty > 0)
);

CREATE INDEX ix_packbl_box  ON tb_pack_box_line (box_seq);
-- '이 줄이 어느 박스에 얼마나 들어갔나' — 다 담았는지 세는 경로
CREATE INDEX ix_packbl_line ON tb_pack_box_line (line_seq);

COMMENT ON TABLE  tb_pack_box_line           IS '박스에 담은 것 (PAC-PG-002)';
COMMENT ON COLUMN tb_pack_box_line.line_seq  IS '출고지시 줄. SKU 는 그쪽이 들고 있다';


-- ============================================================================
-- 4. 공통코드
-- ============================================================================
-- group_kind 는 SYSTEM · REASON 둘뿐이다 (V1 ck_code_group_kind). 박스 규격은
-- 기준정보 성격이지만 그 종류가 없어 SYSTEM 으로 둔다.
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('BOX_STATUS', '박스 상태', '담는 중 · 닫음', 'SYSTEM', 'system'),
       ('BOX_TYPE',   '박스 규격', '택배 규격 상자', 'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('OPEN',   '담는 중', '아직 담고 있다. 더 넣을 수 있다.',        'blue',  10),
        ('CLOSED', '닫음',    '테이프를 붙였다. 더 못 넣는다.',          'green', 20)
       ) AS v(code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'BOX_STATUS';

-- 택배 규격. 요금이 여기서 갈려 고르게 둔다. 실제 치수는 센터가 쓰는
-- 상자에 맞춰 기준정보 화면에서 고친다.
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('S',   '소형 (1호)',  '가로세로높이 합 60cm 이내',  10),
        ('M',   '중형 (3호)',  '합 80cm 이내',               20),
        ('L',   '대형 (5호)',  '합 120cm 이내',              30),
        ('XL',  '특대 (6호)',  '합 160cm 이내',              40),
        ('BAG', '택배봉투',    '의류 단품에 쓴다',           50)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'BOX_TYPE';


-- ============================================================================
-- 5. 메뉴
--
-- 권한은 V4 가 이미 깔아 뒀다 (OUT_PACK). 검수는 피킹과 같은 사람이 이어서
-- 하는 일이라 OUT_PICK 을 그대로 쓴다 — 권한을 하나 더 만들면 현장에서
-- '검수 권한이 없습니다' 로 막히는 사람이 생긴다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('OUT_INSPECT', '출고검수', 'GRP_OUT', 'outbound-inspect', '🔍', 'OUT_PICK', 50),
        ('OUT_PACKING', '패킹',     'GRP_OUT', 'outbound-packing', '📦', 'OUT_PACK', 60)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
