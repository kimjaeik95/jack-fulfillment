-- ============================================================================
-- 입고정정 요청 · 승인 (INB-PG-008)
--
-- 완료된 입고의 수량이 틀렸을 때 고친다. 재고조정(INV-PG-006)과 무엇이
-- 다른가가 이 기능의 전부다.
--
--   재고조정  재고 숫자만 고친다. 입고 전표와 발주는 그대로다.
--   입고정정  재고 · 입고 전표 · 발주 기입고수량을 <b>함께</b> 되감는다.
--
-- 재고조정으로 때우면 재고는 맞아도 "공급처한테 30 개 덜 받았다" 가 어디에도
-- 남지 않는다. 발주는 여전히 다 들어온 것으로 닫혀 있어 다시 보내 달라고 할
-- 잔량이 없고, 나중에 누구 잘못이었는지도 답할 수 없다.
--
-- <b>완료된 입고만</b> 정정한다 (DONE). 아직 진행 중이면 정정할 이유가 없다 —
-- 검수를 다시 하거나 적치를 더 하면 된다. 정정은 이미 재고가 되어 버려서
-- 정상 경로로는 되돌릴 수 없게 된 것만 다룬다.
--
-- 권한은 새로 만들지 않는다. V3 가 INB_CORRECTION 을 이미 정의해 뒀고
-- 액션까지 나눠 뒀다 — 입고담당은 C(요청), 센터관리자는 A(승인).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 정정 전표
--
-- 재고조정 전표(tb_stock_adjust)와 같은 모양이다. 요청 → 승인/반려 흐름이
-- 같고, 같은 모양이면 화면도 코드도 읽는 법이 하나다.
--
-- 다른 점은 창고가 아니라 <b>입고</b>를 물고 있다는 것이다. 정정은 언제나
-- 특정 입고 한 건에 대한 이야기라, 그 입고를 통해 창고 · 플랜트 · 조직까지
-- 거슬러 올라간다.
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound_correct (
    correct_seq    bigint      GENERATED ALWAYS AS IDENTITY,
    -- INBC-20260916-0001
    correct_no     varchar(30) NOT NULL,

    -- 어느 입고를 고치나
    inbound_seq    bigint      NOT NULL,

    -- 코드그룹 CORRECT_STATUS
    correct_status varchar(20) NOT NULL DEFAULT 'REQUESTED',
    -- 코드그룹 REASON_CORRECT (수량 오기 · 공급처 미납 · 검수 착오 · 이중 계상)
    reason_code    varchar(30) NOT NULL,
    remark         varchar(300),

    requested_by   varchar(30) NOT NULL,
    requested_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    decided_by     varchar(30),
    decided_at     timestamp,
    decide_remark  varchar(300),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_inbound_correct    PRIMARY KEY (correct_seq),
    CONSTRAINT uk_inbound_correct_no UNIQUE (correct_no),
    CONSTRAINT fk_inbc_inbound       FOREIGN KEY (inbound_seq) REFERENCES tb_inbound (inbound_seq),
    CONSTRAINT ck_inbc_status        CHECK (correct_status IN ('REQUESTED','APPROVED','REJECTED','CANCELED')),
    -- 처리된 전표는 처리자와 시각이 있어야 한다. 누가 언제 승인했는지
    -- 남지 않는 승인은 승인이 아니다.
    CONSTRAINT ck_inbc_decided       CHECK (
        (correct_status IN ('REQUESTED','CANCELED'))
     OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    -- 반려는 왜 반려했는지가 있어야 한다
    CONSTRAINT ck_inbc_reject        CHECK (correct_status <> 'REJECTED' OR decide_remark IS NOT NULL)
);

CREATE INDEX ix_inbc_inbound ON tb_inbound_correct (inbound_seq, requested_at DESC);
CREATE INDEX ix_inbc_status  ON tb_inbound_correct (correct_status, requested_at DESC);
CREATE INDEX ix_inbc_who     ON tb_inbound_correct (requested_by, requested_at DESC);

COMMENT ON TABLE tb_inbound_correct IS '입고정정 전표 — 완료된 입고의 수량을 고친다 (INB-PG-008)';


-- ----------------------------------------------------------------------------
-- 정정 라인
--
-- <b>적치 행</b>에 건다. 입고 라인이 아니다.
--
-- 한 줄을 여러 자리에 나눠 놓는 일이 흔하다 — 100 개 중 60 개는 1A-01-01,
-- 40 개는 1A-01-02. 정정을 입고 라인에 걸면 "그럼 어느 자리에서 빼나" 를
-- 시스템이 멋대로 정하게 되고, 창고에 가 보면 없는 자리에서 뺀 것이 된다.
--
-- 적치 행에 걸면 자리 · SKU · 원래 이력이 한 번에 정해진다. 요청자도
-- "1A-01-01 에 놓은 60 개 중 10 개" 라고 눈에 보이는 대로 적는다.
-- ----------------------------------------------------------------------------
CREATE TABLE tb_inbound_correct_line (
    line_seq     bigint      GENERATED ALWAYS AS IDENTITY,
    correct_seq  bigint      NOT NULL,
    line_no      integer     NOT NULL,

    -- 어느 적치를 고치나
    putaway_seq  bigint      NOT NULL,

    /*
     * 정정량. 부호가 방향이다.
     *
     *   음수  덜 받았는데 더 적었다 → 재고를 줄인다  (흔한 쪽)
     *   양수  더 받았는데 덜 적었다 → 재고를 늘린다
     *
     * 목표수량이 아니라 변동량으로 적는다. 요청과 승인 사이에 재고가 움직일
     * 수 있는데, 요청자가 "10 개 덜 왔더라" 고 했으면 승인 시점에도 10 개를
     * 빼는 것이 맞지 그 사이 입고된 것까지 없애는 것은 요청한 적 없는 일이다
     * (재고조정과 같은 이유다).
     */
    qty_delta    integer     NOT NULL,

    -- 라인별 사유. 비우면 헤더 사유를 따른다 — 한 전표에 미납과 오기가
    -- 섞이는 일이 실제로 있다.
    reason_code  varchar(30),
    remark       varchar(300),

    -- 승인으로 만들어진 재고이력. 정정 한 줄에서 재고 이력으로 바로 건너간다.
    applied_history_seq bigint,

    CONSTRAINT pk_inbc_line     PRIMARY KEY (line_seq),
    CONSTRAINT uk_inbc_line_no  UNIQUE (correct_seq, line_no),
    -- 한 전표에서 같은 적치를 두 번 고칠 수 없다. 두 줄이 서로 다른 값을
    -- 말하면 어느 쪽이 맞는지 정할 수 없다.
    CONSTRAINT uk_inbc_line_key UNIQUE (correct_seq, putaway_seq),
    CONSTRAINT fk_inbcl_correct FOREIGN KEY (correct_seq) REFERENCES tb_inbound_correct (correct_seq)
                                ON DELETE CASCADE,
    CONSTRAINT fk_inbcl_putaway FOREIGN KEY (putaway_seq) REFERENCES tb_inbound_putaway (putaway_seq),
    CONSTRAINT fk_inbcl_hist    FOREIGN KEY (applied_history_seq)
                                REFERENCES tb_stock_history (history_seq),
    -- 0 은 고치는 것이 없다. 승인자가 읽을 것만 남긴다.
    CONSTRAINT ck_inbcl_delta   CHECK (qty_delta <> 0)
);

CREATE INDEX ix_inbcl_correct ON tb_inbound_correct_line (correct_seq, line_no);
-- '이 적치에 걸린 미결 정정이 있나' — 두 번째 정정 요청이 먼저 확인한다
CREATE INDEX ix_inbcl_putaway ON tb_inbound_correct_line (putaway_seq);

COMMENT ON COLUMN tb_inbound_correct_line.qty_delta IS '음수=재고 차감, 양수=재고 추가. 목표가 아니라 변동량';


-- ============================================================================
-- 코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, created_by) VALUES
  ('CORRECT_STATUS', '입고정정 상태', '입고정정 전표의 결재 상태', 'system'),
  ('REASON_CORRECT', '입고정정 사유', '완료된 입고를 왜 고치는가', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.color, v.sort_order, 'system'
  FROM (VALUES
        ('CORRECT_STATUS', 'REQUESTED', '승인대기', '요청만 올라간 상태. 재고는 아직 그대로다.',   'amber',  10),
        ('CORRECT_STATUS', 'APPROVED',  '승인',     '재고 · 입고 · 발주에 반영되었다.',             'green',  20),
        ('CORRECT_STATUS', 'REJECTED',  '반려',     '되돌리지 않기로 했다. 사유가 남는다.',         'red',    30),
        ('CORRECT_STATUS', 'CANCELED',  '취소',     '요청자가 거둬들였다.',                         'gray',   40),

        -- 사유를 나누는 기준은 '누구 잘못인가' 다. 이 구분이 없으면 정정을
        -- 아무리 남겨도 공급처와 다툴 때 쓸 수 없다.
        ('REASON_CORRECT', 'SHORT_SHIP', '공급처 미납', '적게 왔는데 받은 것으로 적었다. 공급처 귀책.',     NULL, 10),
        ('REASON_CORRECT', 'OVER_SHIP',  '공급처 과납', '많이 왔는데 적게 적었다. 공급처 귀책.',             NULL, 20),
        ('REASON_CORRECT', 'MISCOUNT',   '검수 착오',   '우리가 잘못 셌다.',                                 NULL, 30),
        ('REASON_CORRECT', 'TYPO',       '수량 오기',   '세기는 맞게 셌는데 입력을 틀렸다.',                 NULL, 40),
        ('REASON_CORRECT', 'DOUBLE',     '이중 계상',   '같은 물건을 두 번 입고로 잡았다.',                  NULL, 50),

        -- 재고이동 이력 화면이 이동유형과 전표유형을 코드로 읽는다. 없으면
        -- 정정으로 만든 이력만 필터에서 빠지고 배지도 코드값 그대로 뜬다 —
        -- 되감은 기록이 가장 찾기 어려운 기록이 된다.
        ('STOCK_MOVE',     'CORRECT',        '입고정정', '완료된 입고를 되감았다 (INB-PG-008).', NULL, 45),
        ('STOCK_REF',      'INBOUND_CORRECT', '입고정정전표', '입고정정 전표에서 비롯된 변동.',   NULL, 60)
       ) AS v(group_id, code_id, code_name, description, color, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 메뉴
--
-- 요청과 승인을 나눈다. 같은 화면에 두면 승인자가 자기 결재함을 찾으려고
-- 남의 요청 목록을 먼저 뒤져야 한다 — 재고조정도 같은 이유로 나눠 뒀다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('INB_CORRECT_REQ', '입고정정 요청', 'inbound-corrects',        '✏', 'INB_CORRECTION', 60),
        ('INB_CORRECT_APV', '입고정정 승인', 'inbound-correct-approve', '🧾', 'INB_CORRECTION', 70)
       ) AS v(menu_id, menu_name, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = 'GRP_INB'
  JOIN tb_permission p ON p.perm_id = v.perm_id;


-- ============================================================================
-- 권한 보완
--
-- 요청자는 자기가 올린 것을 고치고 거둘 수 있어야 한다. V3 는 C · R · A 만
-- 줬는데, U(수정) · D(취소)가 없으면 잘못 적은 요청을 되돌릴 방법이 없어
-- 승인자에게 반려를 부탁해야 한다.
-- ============================================================================
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.code, 'system'
  FROM tb_permission p, (VALUES ('U'),('D')) AS a(code)
 WHERE p.perm_id = 'INB_CORRECTION'
   AND NOT EXISTS (SELECT 1 FROM tb_permission_action x
                    WHERE x.perm_seq = p.perm_seq AND x.action_code = a.code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.code, 'system'
  FROM tb_role r, tb_permission p, (VALUES ('U'),('D')) AS a(code)
 WHERE r.role_id = 'INBOUND_WORKER' AND p.perm_id = 'INB_CORRECTION'
   AND NOT EXISTS (SELECT 1 FROM tb_role_permission x
                    WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
                      AND x.action_code = a.code);

-- 정정 화면은 완료된 입고를 골라 그 적치 내역을 보여 준다. 예정 조회가
-- 없으면 고를 목록 자체가 비어 있다 — 센터관리자는 이미 INB_PLAN 을 가졌다.
