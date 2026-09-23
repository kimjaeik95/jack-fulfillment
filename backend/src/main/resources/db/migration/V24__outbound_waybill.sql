-- ============================================================================
-- 송장 발급 · 취소 · 재발행 (PAC-PG-003, PAC-PG-004 / 4차 D섹터)
--
-- 박스 하나에 송장 하나. 밖으로 나가는 유일한 식별자다 — 박스번호는 우리
-- 안에서만 쓰는 이름이고, 고객이 배송조회에 넣는 것은 송장번호다.
--
-- <b>번호는 사람이 입력한다.</b> 택배사 연동(INT-IF-*)이 전부 개발 취소라
-- 우리가 번호를 만들 수 없다. 만들면 라벨에 가짜 번호가 찍히고 고객이
-- 배송조회를 했을 때 아무것도 안 나온다 — 그건 송장이 없는 것보다 나쁘다.
--
-- 그래서 실제 동선은 이렇다.
--
--   택배사 프로그램에서 송장을 뽑는다 (번호와 라벨이 거기서 나온다)
--   그 번호를 여기 적는다
--   우리는 '어느 박스가 어느 번호로 나갔나' 를 들고 있는다
--
-- 연동이 생기면 이 표는 그대로 두고 번호를 채우는 주체만 바뀐다.
-- ============================================================================


-- ============================================================================
-- 1. 송장
-- ============================================================================
CREATE TABLE tb_waybill (
    waybill_seq    bigint      GENERATED ALWAYS AS IDENTITY,

    -- 어느 박스에 붙었나. 박스 하나에 살아 있는 송장은 하나뿐이다.
    box_seq        bigint      NOT NULL,

    -- 코드그룹 COURIER
    courier_code   varchar(20) NOT NULL,
    -- 택배사가 준 번호. 사람이 적는다.
    waybill_no     varchar(50) NOT NULL,

    -- 코드그룹 WAYBILL_STATUS
    waybill_status varchar(20) NOT NULL DEFAULT 'ISSUED',

    /*
     * 재발행이면 원래 송장.
     *
     * 잘못 적었거나 라벨이 찢어져 다시 뽑는 일이 있다. 그때 원 송장을
     * 가리켜 두면 '이 박스가 왜 송장이 둘인가' 에 답할 수 있다 — 안 두면
     * 취소된 번호와 새 번호가 아무 관계 없이 나란히 남는다.
     */
    reissued_from  bigint,

    issued_by      varchar(30) NOT NULL,
    issued_at      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 취소. 사유가 필수다 — 택배사에도 알려야 하는 일이라 근거가 남아야 한다.
    canceled_by    varchar(30),
    canceled_at    timestamp,
    cancel_reason  varchar(300),

    remark         varchar(300),

    CONSTRAINT pk_waybill        PRIMARY KEY (waybill_seq),
    CONSTRAINT fk_waybill_box    FOREIGN KEY (box_seq) REFERENCES tb_pack_box (box_seq),
    CONSTRAINT fk_waybill_orig   FOREIGN KEY (reissued_from) REFERENCES tb_waybill (waybill_seq),
    CONSTRAINT ck_waybill_status CHECK (waybill_status IN ('ISSUED','CANCELED')),
    -- 취소했으면 사유가 있어야 한다
    CONSTRAINT ck_waybill_cancel CHECK (waybill_status <> 'CANCELED'
                                     OR (canceled_by IS NOT NULL AND cancel_reason IS NOT NULL))
);

/*
 * 박스 하나에 살아 있는 송장은 하나.
 *
 * 취소된 송장은 여럿 남을 수 있어 부분 유니크로 건다 — 재발행하면 취소된
 * 것 옆에 새 것이 생기고, 둘 다 살아 있으면 어느 번호로 나갔는지 모른다.
 */
CREATE UNIQUE INDEX ux_waybill_box ON tb_waybill (box_seq)
    WHERE waybill_status = 'ISSUED';

/*
 * 같은 택배사에 같은 번호를 두 번 적을 수 없다.
 *
 * 취소된 것까지 포함해 막는다. 택배사가 이미 쓴 번호라 다시 살아날 수 없고,
 * 무엇보다 이 제약이 잡아 주는 실제 사고는 '앞 박스 번호를 그대로 붙여넣기'
 * 다 — 그러면 두 박스가 같은 번호로 나가 한쪽이 통째로 사라진다.
 */
CREATE UNIQUE INDEX ux_waybill_no ON tb_waybill (courier_code, waybill_no);

CREATE INDEX ix_waybill_issued ON tb_waybill (issued_at DESC);

COMMENT ON TABLE  tb_waybill               IS '송장 (PAC-PG-003, PAC-PG-004). 박스 하나에 하나';
COMMENT ON COLUMN tb_waybill.waybill_no    IS '택배사가 준 번호. 연동이 없어 사람이 적는다';
COMMENT ON COLUMN tb_waybill.reissued_from IS '재발행이면 원래 송장. 없으면 첫 발급이다';


-- ============================================================================
-- 2. 공통코드
--
-- 택배사를 거래처(tb_partner)로 두지 않는다. 거래처는 공급처/고객으로
-- 갈려 있고 택배사는 그 어느 쪽도 아니다 — 물건을 사거나 파는 상대가
-- 아니라 실어 나르는 곳이다. 유형을 하나 더 만들면 기존 FK 와 화면이
-- 전부 영향을 받는데, 지금 필요한 것은 '어느 회사 송장인가' 뿐이다.
-- 계약 · 단가가 필요해지면 그때 거래처로 올린다.
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('COURIER',        '택배사',     '송장을 발급하는 택배사',       'SYSTEM', 'system'),
       ('WAYBILL_STATUS', '송장 상태',  '발급 · 취소',                  'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('CJ',     'CJ대한통운', '',  10),
        ('HANJIN', '한진택배',   '',  20),
        ('LOTTE',  '롯데택배',   '',  30),
        ('LOGEN',  '로젠택배',   '',  40),
        ('POST',   '우체국택배', '',  50),
        ('ETC',    '기타',       '위에 없는 택배사', 90)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'COURIER';

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('ISSUED',   '발급',  '이 번호로 나간다.',              'green', 10),
        ('CANCELED', '취소',  '사유와 함께 거둬들였다.',        'red',   90)
       ) AS v(code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'WAYBILL_STATUS';

-- 송장 취소 사유
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('REASON_WB_CANCEL', '송장 취소 사유', '오기재 · 라벨 손상 · 주문 취소', 'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('WRONG_NO',   '번호 오기재', '적은 번호가 틀렸다',                10),
        ('LABEL_LOST', '라벨 손상',   '찢어지거나 지워져 다시 뽑는다',     20),
        ('REPACK',     '재포장',      '박스를 다시 싸게 되었다',           30),
        ('ORDER_STOP', '주문 취소',   '고객이 주문을 거둬들였다',          40),
        ('ETC',        '기타',        '위에 없는 사유. 비고에 적는다',     90)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'REASON_WB_CANCEL';


-- ============================================================================
-- 3. 권한
--
-- 새로 만든다. V4 의 OUT_PACK 은 박스를 다루는 권한이고, 송장은 밖으로
-- 나가는 문서라 무게가 다르다 — 잘못 붙이면 물건이 엉뚱한 데로 간다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES ('OUT_WAYBILL', '송장 발급', 'OUT', '출고 > 송장', 320, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 발급(C) · 취소(D) · 출력(X). 고치는 개념이 없다 — 번호를 잘못
        -- 적었으면 취소하고 새 번호로 다시 뽑는다.
        ('OUT_WAYBILL', 'RCDX')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, a.perm_seq, a.action_code, NULL, 'v17.sysadmin'
  FROM tb_role r
  JOIN tb_permission_action a ON TRUE
 WHERE r.role_id = 'SYS_ADMIN'
   AND NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq
          AND x.perm_seq = a.perm_seq
          AND x.action_code = a.action_code);


-- ============================================================================
-- 4. 메뉴
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('OUT_WAYBILLS', '송장', 'GRP_OUT', 'outbound-waybills', '🏷', 'OUT_WAYBILL', 70)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
