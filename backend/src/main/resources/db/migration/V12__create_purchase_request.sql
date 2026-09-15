-- ============================================================================
-- 구매요청 (PUR-PG-001, PUR-PG-002 / 요구사항 PUR-001 ~ PUR-003)
--
-- "이게 모자라니 사 주세요" 를 센터가 본사에 올린다. 아직 발주가 아니다 —
-- 발주는 구매오더(PUR-PG-003 이후)가 하고, 이 단계는 <b>무엇이 얼마나
-- 필요한지</b> 를 정하는 데까지다.
--
-- 재고조정(V11)과 모양이 닮았다. 요청과 승인을 나누고, 요청자는 자기
-- 요청을 승인할 수 없다 (AUTH-008). 다른 점은 <b>부분승인</b> 이다 —
-- 조정은 승인 아니면 반려지만, 구매요청은 "100 개 달랬는데 60 개만" 이
-- 정상적인 결론이다. 예산과 창고 자리가 유한하기 때문이다.
--
-- 그래서 라인에 요청수량과 승인수량을 따로 둔다. 승인수량을 요청수량에
-- 덮어쓰면 "얼마를 달라고 했었나" 가 사라지고, 다음 분기에 얼마를 요청해야
-- 하는지 판단할 근거가 없어진다.
-- ============================================================================


-- ============================================================================
-- 1. 구매요청 — 헤더 (PUR-001)
--
-- 센터 단위다. 창고가 아니다 — 어느 창고에 넣을지는 물건이 도착한 뒤
-- 적치(INB-PG-005)가 정하고, 요청 시점에는 아직 모른다.
-- ============================================================================
CREATE TABLE tb_purchase_request (
    request_seq    bigint      GENERATED ALWAYS AS IDENTITY,
    -- REQ-20260915-0001
    request_no     varchar(30) NOT NULL,

    -- 받을 물류센터. 데이터 범위를 플랜트 → 운영 조직으로 거슬러 판정한다.
    plant_seq      bigint      NOT NULL,

    -- 코드그룹 REQUEST_STATUS
    request_status varchar(20) NOT NULL DEFAULT 'REQUESTED',

    /*
     * 요청사유. 코드그룹 REASON_PURCHASE.
     *
     * 자유 입력으로 두지 않는 이유는 이 값이 나중에 집계 대상이기 때문이다.
     * "왜 사는가" 가 재고부족인지 신상품인지에 따라 구매 담당이 보는 눈이
     * 다르고, 재고부족 감지(STK-012)가 요청을 자동으로 만들 때도 코드를
     * 찍어야 한다. 문장으로 적어 두면 그때 셀 수가 없다.
     *
     * 문장으로 적고 싶은 것은 remark 에 따로 받는다.
     */
    reason_code    varchar(30) NOT NULL,
    remark         varchar(300),

    request_date   date        NOT NULL DEFAULT CURRENT_DATE,
    -- 언제까지 필요한가. 발주 납기를 정하는 근거다.
    required_date  date        NOT NULL,

    requested_by   varchar(30) NOT NULL,
    requested_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 승인 · 반려 (PUR-003). 반려는 사유가 필수다.
    decided_by     varchar(30),
    decided_at     timestamp,
    decide_remark  varchar(300),

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_purchase_request    PRIMARY KEY (request_seq),
    CONSTRAINT uk_purchase_request_no UNIQUE (request_no),
    CONSTRAINT fk_purreq_plant        FOREIGN KEY (plant_seq) REFERENCES tb_plant (plant_seq),
    CONSTRAINT ck_purreq_status       CHECK (request_status IN
                                       ('REQUESTED','APPROVED','PARTIAL','REJECTED','CANCELED')),
    -- 처리된 요청은 처리자와 시각이 있어야 한다. 누가 언제 승인했는지
    -- 남지 않는 승인은 승인이 아니다.
    CONSTRAINT ck_purreq_decided      CHECK (
        (request_status IN ('REQUESTED','CANCELED'))
     OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    -- 반려는 왜 반려했는지가 있어야 한다 (PUR-003)
    CONSTRAINT ck_purreq_reject       CHECK (request_status <> 'REJECTED'
                                          OR decide_remark IS NOT NULL),
    -- 필요일이 요청일보다 앞설 수는 없다
    CONSTRAINT ck_purreq_date         CHECK (required_date >= request_date)
);

CREATE INDEX ix_purreq_plant  ON tb_purchase_request (plant_seq, requested_at DESC);
CREATE INDEX ix_purreq_status ON tb_purchase_request (request_status, requested_at DESC);
CREATE INDEX ix_purreq_who    ON tb_purchase_request (requested_by, requested_at DESC);
-- 납기가 급한 것부터 보는 경로 (구매 담당의 작업 목록)
CREATE INDEX ix_purreq_need   ON tb_purchase_request (required_date)
 WHERE request_status IN ('REQUESTED','APPROVED','PARTIAL');


-- ============================================================================
-- 2. 구매요청 상세 (PUR-002)
--
-- SKU 단위다. 제품 단위로 받지 않는다 — 같은 코트라도 블랙 M 이 모자란
-- 것이지 베이지 L 까지 모자란 것은 아니고, 발주도 색상 · 사이즈별로
-- 나간다. 제품 단위로 받으면 그 아래 무엇을 얼마나 살지는 아무도 모른다.
-- ============================================================================
CREATE TABLE tb_purchase_request_line (
    line_seq          bigint      GENERATED ALWAYS AS IDENTITY,
    request_seq       bigint      NOT NULL,
    line_no           integer     NOT NULL,

    sku_seq           bigint      NOT NULL,
    request_qty       integer     NOT NULL,

    /*
     * 승인수량. 승인 전에는 비어 있다.
     *
     * 요청수량에 덮어쓰지 않는다. 덮으면 "얼마를 달라고 했었나" 가 사라져,
     * 다음에 얼마를 요청해야 하는지 판단할 근거가 없어진다. 60 개만 승인된
     * 것과 60 개를 요청한 것은 전혀 다른 사실이다.
     *
     * 0 도 유효한 승인수량이다 — 그 줄만 빼고 나머지는 승인한다는 뜻이다.
     */
    approved_qty      integer,

    -- 희망 공급처. 요청자가 아는 거래처가 있으면 적는다. 구매 담당이
    -- 그대로 따를 의무는 없어서 참고값이다.
    pref_supplier_seq bigint,
    remark            varchar(300),

    CONSTRAINT pk_purreq_line     PRIMARY KEY (line_seq),
    CONSTRAINT uk_purreq_line_no  UNIQUE (request_seq, line_no),
    -- 한 요청에 같은 SKU 를 두 줄 담을 수 없다. 두 줄이면 얼마를 사야
    -- 하는지 정할 수 없고, 합치는 것은 요청자가 할 일이다.
    CONSTRAINT uk_purreq_line_sku UNIQUE (request_seq, sku_seq),
    CONSTRAINT fk_purreql_req     FOREIGN KEY (request_seq) REFERENCES tb_purchase_request (request_seq)
                                  ON DELETE CASCADE,
    CONSTRAINT fk_purreql_sku     FOREIGN KEY (sku_seq)     REFERENCES tb_sku (sku_seq),
    CONSTRAINT fk_purreql_sup     FOREIGN KEY (pref_supplier_seq) REFERENCES tb_supplier (supplier_seq),
    CONSTRAINT ck_purreql_qty     CHECK (request_qty > 0),
    -- 승인수량은 요청수량을 넘을 수 없다 (PUR-003).
    -- 더 사야 한다면 그건 승인이 아니라 새 요청이다.
    CONSTRAINT ck_purreql_approved CHECK (approved_qty IS NULL
                                       OR (approved_qty >= 0 AND approved_qty <= request_qty))
);

CREATE INDEX ix_purreql_req ON tb_purchase_request_line (request_seq, line_no);
-- ' 이 SKU 를 누가 요청해 뒀나' — 중복 요청을 막는 경로
CREATE INDEX ix_purreql_sku ON tb_purchase_request_line (sku_seq);


-- ============================================================================
-- 3. 공통코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by) VALUES
  ('REQUEST_STATUS', '구매요청 상태', '요청 · 승인 · 부분승인 · 반려 · 취소', 'SYSTEM', 'system'),
  ('REASON_PURCHASE', '구매요청 사유', '재고부족 · 신상품 · 행사 · 교체', 'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('REQUEST_STATUS', 'REQUESTED', '승인대기',  '올렸고 아직 처리되지 않았다',          'amber', 10),
        ('REQUEST_STATUS', 'APPROVED',  '승인',      '요청한 수량 그대로 승인되었다',        'green', 20),
        ('REQUEST_STATUS', 'PARTIAL',   '부분승인',  '일부 수량만 승인되었다',               'blue',  30),
        ('REQUEST_STATUS', 'REJECTED',  '반려',      '반려 사유와 함께 돌려보냈다',          'red',   40),
        ('REQUEST_STATUS', 'CANCELED',  '취소',      '요청자가 스스로 거둬들였다',           'gray',  50)
       ) AS v(group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('REASON_PURCHASE', 'SHORTAGE',  '재고부족',   '판매가능 수량이 기준치 아래로 떨어졌다', 10),
        ('REASON_PURCHASE', 'NEW_ITEM',  '신상품',     '새로 전개하는 품목의 초도 물량',         20),
        ('REASON_PURCHASE', 'PROMOTION', '행사 대비',  '기획전 · 시즌 행사 물량',                30),
        ('REASON_PURCHASE', 'REPLACE',   '불량 교체',  '판매불가로 빠진 만큼 채운다',            40),
        ('REASON_PURCHASE', 'ETC',       '기타',       '위에 없는 사유. 비고에 적는다',          90)
       ) AS v(group_id, code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.group_id;


-- ============================================================================
-- 4. 권한
--
-- 승인 권한(PUR_REQ_APPROVE)은 V3 가 이미 정의해 뒀다. 요청 쪽만 새로
-- 만든다 — 재고조정에서 INV_ADJUST 와 INV_ADJ_APPROVE 를 나눈 것과 같다.
--
-- 나누는 이유는 한 사람이 둘 다 하면 승인이 통제가 아니라 절차가 되기
-- 때문이다 (AUTH-008). 둘 다 가진 사람이 있어도 서비스가 '자기 요청 자기
-- 승인' 을 막는다.
--
-- SYS_ADMIN 에게는 주지 않는다. 구매는 업무이고 시스템 관리자의 일이
-- 아니다 — V3 가 업무 권한을 주지 않은 것과 같은 이유다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by) VALUES
  ('PUR_REQUEST', '구매요청 등록', 'PUR', '구매 > 구매요청', 205, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 올리고(C) 고치고(U) 거둬들인다(D). 승인 뒤에는 셋 다 막힌다.
        ('PUR_REQUEST', 'RCUD')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);


-- ============================================================================
-- 5. 메뉴
--
-- '구매' 그룹을 새로 만든다. 재고(GRP_STOCK, 45) 다음, 앞으로 생길
-- 입고 · 출고보다 앞이다 — 물건이 들어오는 순서가 구매 → 입고다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
VALUES ('GRP_PUR', '구매', NULL, NULL, '🛒', NULL, 50, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('PUR_REQ',     '구매요청',      'GRP_PUR', 'purchase-requests',        '📝', 'PUR_REQUEST',     10),
        ('PUR_REQ_APV', '구매요청 승인', 'GRP_PUR', 'purchase-request-approve', '✅', 'PUR_REQ_APPROVE', 20)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
