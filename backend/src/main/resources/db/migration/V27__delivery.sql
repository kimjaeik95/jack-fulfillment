-- ============================================================================
-- 배송 (6차 · DLV-PG-001 ~ 004)
--
-- 송장을 붙여 택배사에 넘긴 뒤부터 고객이 받을 때까지를 본다. 출고확정으로
-- 재고는 이미 빠졌고 물건은 창고에 없는데, 아직 고객에게도 없는 구간이다.
-- <b>그 구간에 몇 개가 떠 있는지</b> 가 이 섹터가 답하는 것이다.
--
--
-- 【 배송상태는 사람이 찍는다 】
--
-- 원래 그림은 INT-IF-004(배송상태 수신)로 택배사에서 받아오는 것이었는데
-- 인터페이스가 전량 개발취소다. 송장번호를 사람이 적기로 한 것과 같은
-- 상황이라, 배송상태도 CS 가 택배사 조회 화면을 보고 찍는다.
--
-- 그래서 <b>상태를 바꾼 사건을 따로 남긴다</b> (tb_delivery_event). 자동으로
-- 들어오는 값이면 최신값만 있어도 되지만, 사람이 찍는 값은 '누가 언제 무엇을
-- 보고 이렇게 적었나' 가 있어야 나중에 다툴 때 근거가 된다. 나중에 인터페이스가
-- 생겨도 같은 표에 출처만 달리 쌓으면 된다.
--
--
-- 【 택배사를 공통코드에서 마스터로 옮긴다 】
--
-- V24 는 COURIER 공통코드로 택배사를 뒀다. 이름만 필요했으니 그때는 맞았다.
-- 이제 계약번호 · 단가 · 집화 마감시각 · 조회 URL 이 붙는데, 공통코드에는
-- attr1~4 밖에 없고 그것도 의미 없는 이름이라 넣는 순간 아무도 못 읽는다.
--
-- 코드그룹은 <b>지우지 않는다.</b> tb_waybill.courier_code 가 이미 그 값을
-- 쓰고 있고 발급된 송장이 남아 있다. 마스터의 courier_code 를 같은 값으로
-- 맞춰 두고, 코드그룹은 이력용으로 남겨 둔다.
-- ============================================================================


-- ============================================================================
-- 1. 택배사 (DLV-PG-001)
-- ============================================================================
CREATE TABLE tb_courier (
    courier_seq    bigint      GENERATED ALWAYS AS IDENTITY,

    -- COURIER 코드그룹과 같은 값을 쓴다. 이미 발급된 송장이 이 값을 물고 있다.
    courier_code   varchar(20) NOT NULL,
    courier_name   varchar(100) NOT NULL,

    -- 계약
    contract_no    varchar(50),
    contract_from  date,
    contract_to    date,
    /*
     * 박스당 단가. 원 단위 정수다.
     *
     * 실제 정산은 무게 · 부피 · 지역에 따라 갈리지만, 그 표까지 들이면
     * 택배사 관리가 아니라 요율 관리가 된다. 여기서는 '대략 얼마짜리
     * 계약인가' 를 아는 정도로 둔다 — 정산은 6차 범위가 아니다.
     */
    box_fee        integer,

    -- 집화 마감. 이 시각을 넘기면 오늘 못 나간다.
    pickup_cutoff  time,

    -- 배송조회 주소. {waybillNo} 를 실제 번호로 바꿔 연다.
    tracking_url   varchar(300),

    contact_name   varchar(50),
    contact_phone  varchar(30),

    remark         varchar(300),
    sort_order     integer     NOT NULL DEFAULT 0,
    use_yn         char(1)     NOT NULL DEFAULT 'Y',

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     varchar(30),
    updated_at     timestamp,

    CONSTRAINT pk_courier      PRIMARY KEY (courier_seq),
    CONSTRAINT uk_courier_code UNIQUE (courier_code),
    CONSTRAINT ck_courier_use  CHECK (use_yn IN ('Y','N')),
    CONSTRAINT ck_courier_fee  CHECK (box_fee IS NULL OR box_fee >= 0),
    -- 계약 기간이 거꾸로 들어가는 것을 막는다
    CONSTRAINT ck_courier_term CHECK (contract_from IS NULL OR contract_to IS NULL
                                   OR contract_from <= contract_to)
);

COMMENT ON TABLE  tb_courier IS '택배사 (DLV-PG-001). COURIER 공통코드와 courier_code 로 맞물린다';
COMMENT ON COLUMN tb_courier.pickup_cutoff IS '집화 마감시각. 넘기면 오늘 못 나간다';
COMMENT ON COLUMN tb_courier.tracking_url IS '배송조회 주소. {waybillNo} 자리를 실제 번호로 바꾼다';

-- V24 가 깔아 둔 택배사를 그대로 옮긴다. 이름이 바뀌면 이미 발급된 송장의
-- 표시가 달라지므로 코드와 이름을 같은 값으로 맞춘다.
INSERT INTO tb_courier (courier_code, courier_name, tracking_url, sort_order, created_by)
SELECT c.code_id, c.code_name,
       CASE c.code_id
           WHEN 'CJ'     THEN 'https://trace.cjlogistics.com/next/tracking.html?wblNo={waybillNo}'
           WHEN 'HANJIN' THEN 'https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?wblnum={waybillNo}'
           WHEN 'LOTTE'  THEN 'https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo={waybillNo}'
           WHEN 'LOGEN'  THEN 'https://www.ilogen.com/web/personal/trace/{waybillNo}'
           WHEN 'POST'   THEN 'https://service.epost.go.kr/trace.RetrieveEmsRigiTraceList.comm?sid1={waybillNo}'
           ELSE NULL
       END,
       c.sort_order, 'system'
  FROM tb_code c
  JOIN tb_code_group g ON g.code_group_seq = c.code_group_seq
 WHERE g.code_group_id = 'COURIER';


-- ============================================================================
-- 2. 배송상태 (DLV-PG-002)
--
-- tb_waybill 에 최신 상태를 얹는다. 이력은 tb_delivery_event 가 갖는다 —
-- 최신값만 두면 '언제 어디서 멈췄나' 를 못 본다.
--
-- waybill_status(발급 · 취소)와 다른 축이다. 취소된 송장은 배송 자체가
-- 시작되지 않은 것이고, 여기 상태는 <b>살아 있는 송장이 어디까지 갔나</b> 다.
-- ============================================================================
ALTER TABLE tb_waybill
    ADD COLUMN delivery_status varchar(20) NOT NULL DEFAULT 'READY',
    -- 마지막으로 상태를 바꾼 시각. 지연 판정의 기준이다.
    ADD COLUMN delivered_at    timestamp,
    ADD COLUMN status_at       timestamp,
    ADD COLUMN status_by       varchar(30);

ALTER TABLE tb_waybill
    ADD CONSTRAINT ck_waybill_delivery
        CHECK (delivery_status IN ('READY','IN_TRANSIT','OUT_FOR_DELIVERY',
                                   'DELIVERED','FAILED','RETURNING','LOST'));

-- 배송완료면 그 시각이 있어야 한다. 없으면 '언제 받았나' 에 답할 수 없고
-- 운송중 재고에서 언제 빠졌는지도 모른다.
ALTER TABLE tb_waybill
    ADD CONSTRAINT ck_waybill_delivered
        CHECK (delivery_status <> 'DELIVERED' OR delivered_at IS NOT NULL);

COMMENT ON COLUMN tb_waybill.delivery_status IS '배송상태 (DLV-PG-002). waybill_status(발급·취소)와 다른 축';
COMMENT ON COLUMN tb_waybill.delivered_at IS '배송완료 시각. 운송중 재고에서 빠지는 시점';

CREATE INDEX ix_waybill_delivery ON tb_waybill (delivery_status, status_at DESC)
    WHERE waybill_status = 'ISSUED';


-- ============================================================================
-- 3. 배송 사건 (DLV-PG-002 · 003)
--
-- 상태가 바뀔 때마다 한 줄. 사람이 찍는 값이라 누가 무엇을 보고 그렇게
-- 적었는지가 남아야 한다.
--
-- 실패도 여기 쌓인다. 따로 표를 두지 않는 이유는 실패가 배송의 한 상태일
-- 뿐이기 때문이다 — '부재 → 재시도 → 배송완료' 가 한 줄기로 읽혀야 하는데,
-- 실패만 다른 표에 두면 그 줄기가 끊어진다.
-- ============================================================================
CREATE TABLE tb_delivery_event (
    event_seq      bigint      GENERATED ALWAYS AS IDENTITY,

    waybill_seq    bigint      NOT NULL,

    -- 이 사건이 만든 상태. tb_waybill.delivery_status 와 같은 집합이다.
    event_status   varchar(20) NOT NULL,

    /*
     * 실패 · 지연 사유. 코드그룹 REASON_DLV_FAIL.
     *
     * 실패일 때만 필수다. 배송완료에 사유를 받으면 아무 의미 없는 값이
     * 쌓인다.
     */
    reason_code    varchar(30),
    remark         varchar(300),

    -- 사건이 일어난 시각. 적은 시각(created_at)과 다르다 — 어제 부재였던
    -- 것을 오늘 아침에 적을 수 있다.
    occurred_at    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    /*
     * 어디서 온 값인가. MANUAL 뿐이다.
     *
     * 지금은 사람이 찍는 것밖에 없지만 칸을 미리 둔다. 나중에 택배사
     * 연동이 생겼을 때 표를 새로 만들지 않고 출처만 달리 쌓으면 된다 —
     * 그때 '예전 것은 누가 찍었나' 를 잃지 않는다.
     */
    source         varchar(20) NOT NULL DEFAULT 'MANUAL',

    created_by     varchar(30) NOT NULL,
    created_at     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_dlv_event     PRIMARY KEY (event_seq),
    CONSTRAINT fk_dlv_waybill   FOREIGN KEY (waybill_seq) REFERENCES tb_waybill (waybill_seq),
    CONSTRAINT ck_dlv_status    CHECK (event_status IN ('READY','IN_TRANSIT','OUT_FOR_DELIVERY',
                                                        'DELIVERED','FAILED','RETURNING','LOST')),
    CONSTRAINT ck_dlv_source    CHECK (source IN ('MANUAL','INTERFACE')),
    -- 실패 · 분실 · 반송은 사유가 있어야 한다. 왜 못 갔는지가 이 표의 값이다.
    CONSTRAINT ck_dlv_reason    CHECK (event_status NOT IN ('FAILED','RETURNING','LOST')
                                    OR reason_code IS NOT NULL)
);

COMMENT ON TABLE  tb_delivery_event IS '배송 사건 (DLV-PG-002·003). 상태가 바뀔 때마다 한 줄';
COMMENT ON COLUMN tb_delivery_event.occurred_at IS '사건이 일어난 시각. 적은 시각과 다를 수 있다';

CREATE INDEX ix_dlvevt_waybill ON tb_delivery_event (waybill_seq, occurred_at DESC);
CREATE INDEX ix_dlvevt_when    ON tb_delivery_event (occurred_at DESC);


-- ============================================================================
-- 4. 재배송 (DLV-PG-003)
--
-- 실패한 박스를 다시 보낸다. 물건이 창고로 돌아온 경우는 반품이라 여기가
-- 아니다 (RTN-* 는 개발취소).
--
-- 같은 박스에 새 송장을 붙인다. 원 송장은 취소하고 재발행으로 잇는다 —
-- 그 길은 PAC-PG-004 가 이미 갖고 있다(reissued_from). 여기서는 '왜 다시
-- 보내는가' 만 더한다.
-- ============================================================================
ALTER TABLE tb_waybill
    ADD COLUMN redelivery_of bigint,
    ADD CONSTRAINT fk_waybill_redlv FOREIGN KEY (redelivery_of) REFERENCES tb_waybill (waybill_seq);

COMMENT ON COLUMN tb_waybill.redelivery_of IS '재배송이면 실패한 원 송장 (DLV-PG-003). 라벨 재발행(reissued_from)과 다르다';


-- ============================================================================
-- 5. 공통코드
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('DELIVERY_STATUS', '배송 상태', '송장이 어디까지 갔나', 'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('READY',            '인계대기',   '송장은 붙었고 아직 택배사가 안 가져갔다', 10),
        ('IN_TRANSIT',       '배송중',     '택배사가 실어 갔다',                      20),
        ('OUT_FOR_DELIVERY', '배달출발',   '오늘 배달 예정',                          30),
        ('DELIVERED',        '배송완료',   '고객이 받았다',                           40),
        ('FAILED',           '배송실패',   '못 전달했다. 다시 시도하거나 반송된다',   50),
        ('RETURNING',        '반송중',     '보낸 곳으로 돌아오는 중',                 60),
        ('LOST',             '분실',       '어디 있는지 모른다',                      70)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'DELIVERY_STATUS';

-- REASON_DLV_FAIL 은 V4 시드가 이미 깔아 뒀다 (부재 · 수취 거부 · 주소 불명).
-- 있는 것은 이름까지 그대로 두고 — 이미 그 코드로 적힌 기록이 있을 수 있다 —
-- 실제로 겪는데 고를 수 없던 사유만 더한다.
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('NO_CONTACT', '연락 두절',   '전화가 안 된다',          40),
        ('DAMAGED',    '파손',        '운송 중 상했다',          50),
        ('WEATHER',    '기상 · 재해', '날씨나 사고로 지연',      60),
        ('COURIER',    '택배사 사정', '물량 폭주 · 터미널 지연', 70),
        ('ETC',        '기타',        '위에 없는 사유',          90)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'REASON_DLV_FAIL'
 WHERE NOT EXISTS (
       SELECT 1 FROM tb_code x
        WHERE x.code_group_seq = g.code_group_seq AND x.code_id = v.code_id);


-- ============================================================================
-- 6. 권한
--
--   DLV_COURIER  택배사 관리       — 기준정보라 만들고 고친다
--   DLV_TRACK    배송 현황 · 갱신  — 상태를 찍는 것이 C 다. 사건을 하나 더
--                                     쌓는 일이지 기존 값을 고치는 게 아니다
--   DLV_FAIL     배송실패 · 재배송 — 실패를 적고 다시 보낸다
--   DLV_TRANSIT  운송중 재고 조회  — 조회만
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES ('DLV_COURIER', '택배사 관리',      'DLV', '배송 > 택배사',      710, 'system'),
       ('DLV_TRACK',   '배송 현황',        'DLV', '배송 > 배송 현황',   720, 'system'),
       ('DLV_FAIL',    '배송실패 · 재배송','DLV', '배송 > 배송실패',    730, 'system'),
       ('DLV_TRANSIT', '운송중 재고',      'DLV', '배송 > 운송중 재고', 740, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('DLV_COURIER', 'CRUDX'),
        ('DLV_TRACK',   'CRX'),
        ('DLV_FAIL',    'CRX'),
        ('DLV_TRANSIT', 'RX')
       ) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, a.perm_seq, a.action_code, NULL, 'system'
  FROM tb_role r
  JOIN tb_permission p ON p.perm_id IN ('DLV_COURIER','DLV_TRACK','DLV_FAIL','DLV_TRANSIT')
  JOIN tb_permission_action a ON a.perm_seq = p.perm_seq
 WHERE r.role_id = 'SYS_ADMIN';


-- ============================================================================
-- 7. 메뉴
--
-- 출고(70)와 사용자(80) 사이에 넣는다. 물건이 나간 다음 일이라 출고 바로
-- 뒤가 맞고, 5 단위로 끊어 온 자리에 맞춰 75 를 쓴다.
-- ============================================================================
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
VALUES ('GRP_DLV', '배송', NULL, NULL, '🚚', NULL, 75, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('DLV_COURIER',  '택배사 관리',       'delivery-courier',  '🏢', 'DLV_COURIER', 10),
        ('DLV_TRACK',    '송장 · 배송 현황',  'delivery-track',    '📦', 'DLV_TRACK',   20),
        ('DLV_FAIL',     '배송실패 · 지연',   'delivery-fail',     '⚠️', 'DLV_FAIL',    30),
        ('DLV_TRANSIT',  '운송중 재고',       'delivery-transit',  '🛣️', 'DLV_TRANSIT', 40)
       ) AS v(menu_id, menu_name, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = 'GRP_DLV'
  JOIN tb_permission p ON p.perm_id = v.perm_id;


-- ============================================================================
-- 8. 인계된 박스를 배송중으로 맞춘다
--
-- 이미 택배사에 넘긴 박스가 있다. 그 송장들은 READY 가 아니라 IN_TRANSIT
-- 이어야 한다 — 인계가 곧 '택배사가 실어 갔다' 이기 때문이다. 안 맞춰 두면
-- 운송중 재고가 처음부터 틀린 값을 보여 준다.
-- ============================================================================
UPDATE tb_waybill w
   SET delivery_status = 'IN_TRANSIT',
       status_at       = b.handed_over_at,
       status_by       = b.handed_over_by
  FROM tb_pack_box b
 WHERE b.box_seq = w.box_seq
   AND w.waybill_status = 'ISSUED'
   AND b.handed_over_at IS NOT NULL;

INSERT INTO tb_delivery_event (waybill_seq, event_status, remark, occurred_at, created_by)
SELECT w.waybill_seq, 'IN_TRANSIT', '택배 인계 (기존 자료 보정)',
       COALESCE(w.status_at, CURRENT_TIMESTAMP), 'system'
  FROM tb_waybill w
 WHERE w.delivery_status = 'IN_TRANSIT';
