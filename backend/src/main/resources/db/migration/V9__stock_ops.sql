-- ============================================================================
-- 재고 B · C · D 섹터 — 이동 · 조정 · 실사
--
-- A섹터(V10)는 읽기만 했다. 여기서부터 재고 수량을 바꾸는 경로가 처음
-- 생긴다. 네 갈래인데 성격이 다르다.
--
--   B 판매불가 전환   총량 그대로, 팔 수 있는지만 바뀐다      즉시 반영
--   B 로케이션 이동   총량 그대로, 어디 있는지만 바뀐다        즉시 반영
--   C 재고조정        총량이 바뀐다                            요청 → 승인
--   D 재고실사        세어 본 결과로 총량을 맞춘다             계획 → 입력 → 마감
--
-- 총량이 바뀌는 둘(C · D)만 승인을 거친다. 총량이 그대로인 둘(B)은 거치지
-- 않는다 — 승인을 붙이면 현장이 물건을 옮겨 놓고 시스템은 옮기지 못한
-- 상태로 몇 시간을 보내게 되고, 그 사이의 피킹이 전부 틀린다.
--
-- B섹터는 테이블을 만들지 않는다. 옮기거나 상태만 바꾸는 일은 tb_stock 의
-- UPDATE 와 tb_stock_history 의 INSERT 로 끝나고, 짝이 되는 두 이력은
-- V10 이 미리 둔 ref_type · ref_no 로 묶는다. 전표 테이블을 따로 만들면
-- 아무도 다시 열어 보지 않는 행이 쌓인다.
-- ============================================================================


-- ============================================================================
-- 1. 전표번호 채번
--
-- ADJ-20260915-0001 같은 번호를 만든다. 날짜가 바뀌면 1 부터 다시 센다.
--
-- 시퀀스(nextval)를 쓰지 않는 이유는 날짜별로 되돌려야 하기 때문이다.
-- 애플리케이션에서 max+1 을 읽어 쓰면 동시에 두 명이 같은 번호를 받는다 —
-- ON CONFLICT DO UPDATE ... RETURNING 은 한 문장 안에서 끝나 그 틈이 없다.
--
-- 조정 · 이동 · 실사가 지금 쓰고, 입고(6차) · 출고(9차)도 같은 표를 쓴다.
-- ============================================================================
CREATE TABLE tb_doc_number (
    -- ADJ(조정) · MOV(이동) · TAKE(실사) · 이후 INB · OUT
    doc_type  varchar(10) NOT NULL,
    doc_date  date        NOT NULL,
    last_seq  integer     NOT NULL DEFAULT 0,

    CONSTRAINT pk_doc_number PRIMARY KEY (doc_type, doc_date),
    CONSTRAINT ck_docnum_seq CHECK (last_seq >= 0)
);

COMMENT ON TABLE  tb_doc_number          IS '전표번호 채번 — 일자별로 1 부터';
COMMENT ON COLUMN tb_doc_number.doc_type IS '전표 종류 (ADJ/MOV/TAKE/INB/OUT)';


-- ============================================================================
-- 2. 재고조정 (C섹터 — INV-PG-006, INV-PG-007)
--
-- 장부와 실물이 다를 때 장부를 실물에 맞춘다. 총량이 바뀌므로 요청과 승인을
-- 나눈다 (STK-009).
--
-- 요청 시점의 장부수량(qty_before)을 라인에 박아 둔다. 승인은 나중에 나는데
-- 그 사이 재고가 또 움직일 수 있다. 박아 두지 않으면 "무엇을 보고 이 조정을
-- 요청했는지" 를 승인자가 알 수 없고, 승인 시점 수량에 delta 를 그냥 더하면
-- 요청자가 의도하지 않은 결과가 된다.
--
-- 그래서 승인은 목표수량(qty_after)이 아니라 변동량(qty_delta)을 반영한다.
-- 대신 승인 시점 장부가 요청 시점과 달라졌으면 경고한다 — 막지는 않는다.
-- 재고가 움직였다고 조정 요청이 무효가 되는 것은 아니기 때문이다.
-- ============================================================================
CREATE TABLE tb_stock_adjust (
    adjust_seq     bigint      GENERATED ALWAYS AS IDENTITY,
    -- ADJ-20260915-0001
    adjust_no      varchar(30) NOT NULL,

    -- 조정은 한 창고 안에서 낸다. 데이터 범위를 창고 → 플랜트 → 조직으로
    -- 거슬러 판정하므로 헤더가 창고를 들고 있어야 한다.
    warehouse_seq  bigint      NOT NULL,

    -- 코드그룹 ADJUST_STATUS
    adjust_status  varchar(20) NOT NULL DEFAULT 'REQUESTED',
    -- 코드그룹 REASON_ADJUST (실사 차이 · 분실 · 파손 · 전산 오류 정정)
    reason_code    varchar(30) NOT NULL,
    remark         varchar(300),

    requested_by   varchar(30) NOT NULL,
    requested_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 승인 · 반려 (STK-009). 반려는 사유가 필수다.
    decided_by     varchar(30),
    decided_at     timestamp,
    decide_remark  varchar(300),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_stock_adjust    PRIMARY KEY (adjust_seq),
    CONSTRAINT uk_stock_adjust_no UNIQUE (adjust_no),
    CONSTRAINT fk_stadj_wh        FOREIGN KEY (warehouse_seq) REFERENCES tb_warehouse (warehouse_seq),
    CONSTRAINT ck_stadj_status    CHECK (adjust_status IN ('REQUESTED','APPROVED','REJECTED','CANCELED')),
    -- 처리된 전표는 처리자와 시각이 있어야 한다. 누가 언제 승인했는지
    -- 남지 않는 승인은 승인이 아니다.
    CONSTRAINT ck_stadj_decided   CHECK (
        (adjust_status IN ('REQUESTED','CANCELED'))
     OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    -- 반려는 왜 반려했는지가 있어야 한다
    CONSTRAINT ck_stadj_reject    CHECK (adjust_status <> 'REJECTED' OR decide_remark IS NOT NULL)
);

CREATE INDEX ix_stadj_wh     ON tb_stock_adjust (warehouse_seq, requested_at DESC);
CREATE INDEX ix_stadj_status ON tb_stock_adjust (adjust_status, requested_at DESC);
CREATE INDEX ix_stadj_who    ON tb_stock_adjust (requested_by, requested_at DESC);


CREATE TABLE tb_stock_adjust_line (
    line_seq      bigint      GENERATED ALWAYS AS IDENTITY,
    adjust_seq    bigint      NOT NULL,
    line_no       integer     NOT NULL,

    stock_seq     bigint      NOT NULL,

    -- 어느 수량을 조정하나 — 보유 또는 판매불가.
    --
    -- 할당(ALLOCATED)은 조정할 수 없다. 주문이 만든 값이라 사람이 직접
    -- 고치면 주문과 재고가 어긋난다. 할당을 풀어야 하면 주문 쪽에서 푼다.
    qty_field     varchar(20) NOT NULL,

    -- 요청 시점의 장부수량. 승인이 나중에 나므로 그때의 값과 다를 수 있다.
    qty_before    integer     NOT NULL,
    -- 이렇게 맞추고 싶다는 목표
    qty_after     integer     NOT NULL,
    -- 실제로 반영되는 값. 승인 시점 장부에 이 값을 더한다.
    qty_delta     integer     GENERATED ALWAYS AS (qty_after - qty_before) STORED,

    -- 라인별 사유. 비우면 헤더 사유를 따른다 — 한 전표에 분실과 파손이
    -- 섞이는 일이 실제로 있다.
    reason_code   varchar(30),
    remark        varchar(300),

    -- 승인으로 만들어진 이력. 조정 한 줄에서 재고 이력으로 바로 건너간다.
    applied_history_seq bigint,

    CONSTRAINT pk_stadj_line     PRIMARY KEY (line_seq),
    CONSTRAINT uk_stadj_line_no  UNIQUE (adjust_seq, line_no),
    -- 한 전표에서 같은 재고 · 같은 수량항목을 두 번 조정할 수 없다.
    -- 두 줄이 서로 다른 목표를 말하면 어느 쪽이 맞는지 정할 수 없다.
    CONSTRAINT uk_stadj_line_key UNIQUE (adjust_seq, stock_seq, qty_field),
    CONSTRAINT fk_stadjl_adj     FOREIGN KEY (adjust_seq) REFERENCES tb_stock_adjust (adjust_seq)
                                 ON DELETE CASCADE,
    CONSTRAINT fk_stadjl_stock   FOREIGN KEY (stock_seq)  REFERENCES tb_stock (stock_seq),
    CONSTRAINT fk_stadjl_hist    FOREIGN KEY (applied_history_seq)
                                 REFERENCES tb_stock_history (history_seq),
    CONSTRAINT ck_stadjl_field   CHECK (qty_field IN ('ON_HAND','UNSELLABLE')),
    CONSTRAINT ck_stadjl_before  CHECK (qty_before >= 0),
    CONSTRAINT ck_stadjl_after   CHECK (qty_after  >= 0),
    -- 바뀌는 게 없는 줄은 둘 이유가 없다. 승인자가 읽을 것만 남긴다.
    CONSTRAINT ck_stadjl_change  CHECK (qty_after <> qty_before)
);

-- 헤더를 열면 라인을 순서대로 읽는다
CREATE INDEX ix_stadjl_adj   ON tb_stock_adjust_line (adjust_seq, line_no);
-- '이 재고에 걸린 미결 조정이 있나' — 이동 · 실사가 먼저 확인한다
CREATE INDEX ix_stadjl_stock ON tb_stock_adjust_line (stock_seq);


-- ============================================================================
-- 3. 재고실사 (D섹터 — INV-PG-008, INV-PG-009)
--
-- 세어 보고 장부를 맞춘다. 조정과 다른 점은 대상을 먼저 정한다는 것이다 —
-- 무엇을 셀지 정하지 않고 세면 안 센 것이 남았는지 알 수 없다.
--
-- 계획 시점의 장부수량(qty_book)을 라인에 박아 둔다. 세는 동안에도 입고 ·
-- 출고는 계속 일어나므로, 마감 시점 장부와 비교하면 '세는 사이에 정상적으로
-- 움직인 수량' 까지 차이로 잡힌다.
--
-- 블라인드 카운트(blind_yn)는 세는 사람에게 장부수량을 숨기는 것이다.
-- 보여 주면 맞추려는 쪽으로 세게 된다 — 실사의 목적 자체가 없어진다.
-- 숨기는 것은 화면의 일이지만, 그 방침은 계획이 들고 있어야 한다.
-- ============================================================================
CREATE TABLE tb_stocktake (
    take_seq      bigint      GENERATED ALWAYS AS IDENTITY,
    -- TAKE-20260915-0001
    take_no       varchar(30) NOT NULL,
    take_name     varchar(100) NOT NULL,

    warehouse_seq bigint      NOT NULL,

    -- 코드그룹 TAKE_TYPE — 전수 · 순환 · 지정
    take_type     varchar(20) NOT NULL,
    -- 코드그룹 TAKE_STATUS — 계획 · 실사중 · 마감 · 취소
    take_status   varchar(20) NOT NULL DEFAULT 'PLANNED',
    -- 세는 사람에게 장부수량을 숨긴다
    blind_yn      char(1)     NOT NULL DEFAULT 'Y',

    -- 순환실사에서 대상을 좁힌 조건. 무엇을 기준으로 골랐는지 남긴다.
    target_zone   varchar(30),
    target_sku_keyword varchar(100),

    planned_date  date        NOT NULL,
    started_at    timestamp,
    -- 마감 = 차이를 재고에 반영한 시점. 되돌릴 수 없다.
    closed_by     varchar(30),
    closed_at     timestamp,
    remark        varchar(300),

    created_by    varchar(30) NOT NULL,
    created_at    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,

    CONSTRAINT pk_stocktake    PRIMARY KEY (take_seq),
    CONSTRAINT uk_stocktake_no UNIQUE (take_no),
    CONSTRAINT fk_take_wh      FOREIGN KEY (warehouse_seq) REFERENCES tb_warehouse (warehouse_seq),
    CONSTRAINT ck_take_type    CHECK (take_type   IN ('FULL','CYCLE','SPOT')),
    CONSTRAINT ck_take_status  CHECK (take_status IN ('PLANNED','COUNTING','CLOSED','CANCELED')),
    CONSTRAINT ck_take_blind   CHECK (blind_yn IN ('Y','N')),
    -- 마감했으면 누가 언제 했는지가 있어야 한다
    CONSTRAINT ck_take_closed  CHECK (
        take_status <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL))
);

CREATE INDEX ix_take_wh     ON tb_stocktake (warehouse_seq, planned_date DESC);
CREATE INDEX ix_take_status ON tb_stocktake (take_status, planned_date DESC);


CREATE TABLE tb_stocktake_line (
    line_seq     bigint      GENERATED ALWAYS AS IDENTITY,
    take_seq     bigint      NOT NULL,

    -- 세어야 할 자리와 물건. 장부에 없어도 이 둘은 항상 있다.
    location_seq bigint      NOT NULL,
    sku_seq      bigint      NOT NULL,

    -- 거래처. 재고 한 행의 키가 로케이션 × SKU × 거래처라(STK-002) 실사
    -- 라인도 같은 키를 따라간다.
    --
    -- 합치지 않는다. 한 빈에 공급처가 다른 같은 SKU 가 있을 때 둘을 한 줄로
    -- 세면, 나중에 차이가 났을 때 그것이 누구 물건인지 정할 수 없다.
    -- 한쪽에 몰아 반영하면 남의 재고가 조용히 줄어들고 정산이 틀어진다.
    -- 애초에 공급처가 섞이지 않도록 구역을 나누는 것이 현장의 관행이고,
    -- 그 관행이 깨졌다는 사실도 실사가 드러내야 한다.
    vendor_seq   bigint,

    -- 장부의 재고 행. 장부에 없는데 실물이 나온 경우(무적재고)에는 비어
    -- 있다 — 마감할 때 재고 행을 새로 만든다. 실사가 잡아야 하는 것이
    -- 바로 이 경우라, NULL 을 허용하지 않으면 그것을 기록할 자리가 없다.
    stock_seq    bigint,

    -- 계획 시점의 장부수량. 세는 동안에도 입고 · 출고는 일어나므로
    -- 마감 시점 장부와 비교하면 정상 변동까지 차이로 잡힌다.
    qty_book     integer     NOT NULL DEFAULT 0,

    -- 1차 카운트
    qty_counted  integer,
    counted_by   varchar(30),
    counted_at   timestamp,
    -- 재계수 (INV-PG-009). 차이가 나면 한 번 더 센다.
    qty_recount  integer,
    recount_by   varchar(30),
    recount_at   timestamp,

    -- 최종 수량 — 재계수가 있으면 그것, 없으면 1차.
    qty_final    integer     GENERATED ALWAYS AS (COALESCE(qty_recount, qty_counted)) STORED,
    -- 차이 = 최종 − 장부. 아직 세지 않았으면 NULL.
    qty_diff     integer     GENERATED ALWAYS AS
                             (COALESCE(qty_recount, qty_counted) - qty_book) STORED,

    -- 코드그룹 TAKE_LINE_STATUS
    line_status  varchar(20) NOT NULL DEFAULT 'TARGET',
    -- 차이의 사유 (P-04). 코드그룹 REASON_ADJUST.
    reason_code  varchar(30),
    remark       varchar(300),

    -- 마감으로 만들어진 이력
    applied_history_seq bigint,

    CONSTRAINT pk_take_line    PRIMARY KEY (line_seq),
    CONSTRAINT fk_takel_take   FOREIGN KEY (take_seq)     REFERENCES tb_stocktake (take_seq)
                               ON DELETE CASCADE,
    CONSTRAINT fk_takel_loc    FOREIGN KEY (location_seq) REFERENCES tb_location (location_seq),
    CONSTRAINT fk_takel_sku    FOREIGN KEY (sku_seq)      REFERENCES tb_sku (sku_seq),
    CONSTRAINT fk_takel_vendor FOREIGN KEY (vendor_seq)   REFERENCES tb_partner (partner_seq),
    CONSTRAINT fk_takel_stock  FOREIGN KEY (stock_seq)    REFERENCES tb_stock (stock_seq),
    CONSTRAINT fk_takel_hist   FOREIGN KEY (applied_history_seq)
                               REFERENCES tb_stock_history (history_seq),
    CONSTRAINT ck_takel_status CHECK (line_status IN ('TARGET','COUNTED','RECOUNT','CONFIRMED')),
    CONSTRAINT ck_takel_book   CHECK (qty_book    >= 0),
    CONSTRAINT ck_takel_count  CHECK (qty_counted IS NULL OR qty_counted >= 0),
    CONSTRAINT ck_takel_recnt  CHECK (qty_recount IS NULL OR qty_recount >= 0),
    -- 재계수는 1차를 세고 나서만 할 수 있다
    CONSTRAINT ck_takel_order  CHECK (qty_recount IS NULL OR qty_counted IS NOT NULL),
    -- 센 사람과 시각은 수량과 함께 남는다
    CONSTRAINT ck_takel_who    CHECK (
        (qty_counted IS NULL) = (counted_by IS NULL)
    AND (qty_counted IS NULL) = (counted_at IS NULL))
);

-- 같은 자리 · 같은 물건 · 같은 공급처를 두 번 세지 않는다. 두 번 세면 어느
-- 쪽이 맞는지 정할 수 없고, 합산하면 실제의 두 배가 된다.
--
-- 거래처가 NULL 일 수 있어 인덱스를 둘로 나눈다. NULL 끼리는 = 로 비교되지
-- 않아 하나짜리 유니크로는 중복이 막히지 않는다 — tb_stock 이 같은 이유로
-- 같은 처리를 하고 있다.
CREATE UNIQUE INDEX ux_takel_key_vendor
    ON tb_stocktake_line (take_seq, location_seq, sku_seq, vendor_seq)
 WHERE vendor_seq IS NOT NULL;
CREATE UNIQUE INDEX ux_takel_key_novendor
    ON tb_stocktake_line (take_seq, location_seq, sku_seq)
 WHERE vendor_seq IS NULL;

CREATE INDEX ix_takel_take ON tb_stocktake_line (take_seq, line_status);
CREATE INDEX ix_takel_loc  ON tb_stocktake_line (location_seq, sku_seq);
-- 차이가 난 줄만 뽑는 경로 (마감 전 확인 · 대사)
CREATE INDEX ix_takel_diff ON tb_stocktake_line (take_seq) WHERE qty_diff <> 0;


-- ============================================================================
-- 4. 공통코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by) VALUES
  ('ADJUST_STATUS',    '재고조정 상태', '요청 · 승인 · 반려 · 취소',        'SYSTEM', 'system'),
  ('TAKE_TYPE',        '실사 유형',     '전수 · 순환 · 지정',               'SYSTEM', 'system'),
  ('TAKE_STATUS',      '실사 상태',     '계획 · 실사중 · 마감 · 취소',      'SYSTEM', 'system'),
  ('TAKE_LINE_STATUS', '실사 라인 상태', '대상 · 1차완료 · 재계수 · 확정',  'SYSTEM', 'system'),
  ('QTY_FIELD',        '재고 수량항목', '보유 · 할당 · 판매불가',           'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('ADJUST_STATUS', 'REQUESTED', '승인대기', '요청자가 올렸고 아직 처리되지 않았다', 'amber',  10),
        ('ADJUST_STATUS', 'APPROVED',  '승인',     '재고에 반영되었다. 되돌릴 수 없다',    'green',  20),
        ('ADJUST_STATUS', 'REJECTED',  '반려',     '반려 사유와 함께 돌려보냈다',          'red',    30),
        ('ADJUST_STATUS', 'CANCELED',  '취소',     '요청자가 스스로 거둬들였다',           'gray',   40),

        ('TAKE_TYPE', 'FULL',  '전수실사', '창고의 모든 재고를 센다',                   'blue',   10),
        ('TAKE_TYPE', 'CYCLE', '순환실사', '구역 · 품목을 나눠 돌아가며 센다',          'teal',   20),
        ('TAKE_TYPE', 'SPOT',  '지정실사', '문제가 의심되는 것만 골라 센다',            'violet', 30),

        ('TAKE_STATUS', 'PLANNED',  '계획',   '대상을 정했고 아직 세지 않았다',          'gray',   10),
        ('TAKE_STATUS', 'COUNTING', '실사중', '세는 중이다',                             'amber',  20),
        ('TAKE_STATUS', 'CLOSED',   '마감',   '차이를 재고에 반영했다. 되돌릴 수 없다',  'green',  30),
        ('TAKE_STATUS', 'CANCELED', '취소',   '세지 않고 접었다',                        'red',    40),

        ('TAKE_LINE_STATUS', 'TARGET',    '대상',     '아직 세지 않았다',              'gray',  10),
        ('TAKE_LINE_STATUS', 'COUNTED',   '1차완료', '한 번 셌다',                     'blue',  20),
        ('TAKE_LINE_STATUS', 'RECOUNT',   '재계수',   '차이가 있어 다시 셌다',         'amber', 30),
        ('TAKE_LINE_STATUS', 'CONFIRMED', '확정',     '마감으로 재고에 반영되었다',    'green', 40),

        ('QTY_FIELD', 'ON_HAND',    '보유',     '창고에 실제로 있는 수량',            'blue',  10),
        ('QTY_FIELD', 'ALLOCATED',  '할당',     '주문이 잡아 둔 수량',                'amber', 20),
        ('QTY_FIELD', 'UNSELLABLE', '판매불가', '불량 · 오염 · 검수대기',             'red',   30)
       ) AS v(group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;

-- 판매불가 전환의 사유. 기존 REASON_INSPECT(검수불량)만으로는 부족하다 —
-- 되돌리는 쪽(판매불가 → 정상)의 사유가 없었다.
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('REASON_INSPECT', 'CONTAMINATED', '오염',        NULL,                          50),
        ('REASON_INSPECT', 'DISPLAY',      'DP 사용',     '전시 · 촬영으로 뺀 재고',     60),
        ('REASON_INSPECT', 'REPAIRED',     '수선 완료',   '판매불가에서 정상으로 되돌림', 70),
        ('REASON_INSPECT', 'MISJUDGED',    '판정 정정',   '판매불가로 잘못 잡은 것 되돌림', 80),
        ('REASON_ADJUST',  'MOVE_ERROR',   '이동 오류',   '엉뚱한 빈으로 옮긴 것 정정',  50)
       ) AS v(group_id, code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 5. 권한
--
-- 총량이 바뀌는 일(조정 · 실사)은 요청 권한과 승인 권한을 나눈다. 승인
-- 권한은 V3 가 이미 정의해 뒀으므로(INV_ADJ_APPROVE · INV_COUNT_APPROVE)
-- 요청 쪽만 새로 만든다.
--
-- 목록 조회는 각자의 권한이 R 로 답한다. 요청자는 INV_ADJUST 의 R 로 자기
-- 요청을 보고, 승인자는 INV_ADJ_APPROVE 의 R 로 결재함을 본다 — 같은
-- 데이터를 보지만 보는 이유가 달라 화면도 다르다.
--
-- 재고 대사(INV-PG-010)는 권한을 만들지 않는다. 읽어서 비교만 하므로
-- QRY_STOCK 으로 충분하다.
--
-- SYS_ADMIN 에게는 주지 않는다. V3 가 INV_QTY_EDIT 를 주지 않은 것과 같은
-- 이유다 — 시스템 관리자가 업무 수량을 바꿀 수 있으면 재고 정합성의 책임
-- 소재가 사라진다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by) VALUES
  ('INV_MOVE',   '재고이동 · 판매불가 전환', 'INV', '재고 > 이동/상태전환', 540, 'system'),
  ('INV_ADJUST', '재고조정 요청',            'INV', '재고 > 조정요청',      550, 'system'),
  ('INV_COUNT',  '재고실사 계획 · 입력',     'INV', '재고 > 실사',          560, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 이동 · 전환은 즉시 반영이라 '등록' 이 곧 실행이다
        ('INV_MOVE',   'RC'),
        -- 요청은 올리고(C) 고치고(U) 거둬들인다(D). 승인 뒤에는 셋 다 막힌다.
        ('INV_ADJUST', 'RCUD'),
        -- 실사는 계획을 세우고(C) 세어 넣는다(U)
        ('INV_COUNT',  'RCUD')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 승인 권한에 R 을 붙인다. V3 는 'RA' 로 넣어 두었는데, 결재함을 여는
-- 행위가 R 이라 이미 있다. 여기서는 확인만 하고 넘어간다.


-- ============================================================================
-- 6. 메뉴
--
-- V10 이 만든 '재고' 그룹 아래에 붙인다. 순서는 일이 일어나는 순서다 —
-- 현황을 보고, 옮기거나 상태를 바꾸고, 안 맞으면 조정을 올리고, 주기적으로
-- 세어 맞춘다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('INV_UNSELL',   '판매불가 전환',   'GRP_STOCK', 'stock-unsellable', '🚫', 'INV_MOVE',          30),
        ('INV_MOVE_LOC', '로케이션 이동',   'GRP_STOCK', 'stock-move',       '↔️', 'INV_MOVE',          40),
        ('INV_ADJ_REQ',  '재고조정 요청',   'GRP_STOCK', 'stock-adjust',     '📝', 'INV_ADJUST',        50),
        ('INV_ADJ_APV',  '재고조정 승인',   'GRP_STOCK', 'stock-adjust-approve', '✅', 'INV_ADJ_APPROVE', 60),
        ('INV_TAKE',     '재고실사',        'GRP_STOCK', 'stocktake',        '📋', 'INV_COUNT',         70),
        ('INV_RECON',    '재고 대사',       'GRP_STOCK', 'stock-recon',      '🔍', 'QRY_STOCK',         80)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
