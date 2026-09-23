-- ============================================================================
-- 발주 미납종결 (PUR-PG-004)
--
-- 공급처가 "남은 20 은 못 보낸다" 고 했을 때 그 발주를 끝내는 길.
--
-- 지금은 길이 하나도 없다.
--
--   발주를 고쳐 80 으로  →  나간 발주는 못 고친다 (requireDraft)
--   발주를 취소          →  일부라도 입고되면 못 한다
--   요청 수량을 고친다   →  승인된 요청은 못 고친다 (requirePending)
--   그 요청 줄로 재발주  →  승인수량을 이미 다 썼으니 초과로 거절
--
-- 그래서 그 발주는 '부분입고' 로 영원히 남는다. 잔량 20 이 곧 들어올 것처럼
-- 진행현황에 계속 떠서, 입고 담당은 오지 않을 물건을 기다린다. 그런 건이
-- 쌓이면 '정말 독촉할 것' 과 '이미 끝난 것' 이 섞여 목록 자체를 아무도 안
-- 본다 — 납기 지남 필터까지 같이 무력해진다.
--
-- <b>분할 납품과는 다르다.</b> 공급처가 나눠 보내는 것은 정상이고 지금도 잘
-- 돈다 (부분입고 → 잔량 입고 → 입고완료). 둘을 시스템이 구분할 수는 없다 —
-- 잔량이 남은 발주가 '곧 온다' 인지 '안 온다' 인지는 공급처와 통화한 사람만
-- 안다. 그래서 자동 판정이 아니라 사람이 누르는 버튼이다.
--
-- 수량은 건드리지 않는다. 발주수량 100 도 기입고수량 80 도 그대로 두고,
-- '80 만 받고 끝냈다' 는 사실만 상태와 사유로 따로 적는다. 100 을 80 으로
-- 고치면 "얼마를 약속했었나" 가 사라진다 — 승인수량을 요청수량에 덮어쓰지
-- 않는 것과 같은 이유다 (PUR-003).
-- ============================================================================


-- ============================================================================
-- 1. 상태값
--
-- 입고완료(CLOSED)를 재사용하지 않는다. 둘은 다른 사실이다.
--
--   CLOSED       발주수량이 다 들어왔다.      잔량 0
--   SHORT_CLOSED 덜 들어왔지만 끝냈다.        잔량 남음
--
-- 섞으면 '잔량이 있는데 입고완료' 인 행이 생기고, 그러면 수량 대사에서
-- 누락인지 종결인지 구분할 수 없다. 그리고 실제로 되돌아간다 —
-- refreshOrderStatus(INB-004)가 CLOSED 도 다시 보기 때문에, 입고정정이
-- 한 번 들어오면 종결해 둔 발주가 부분입고로 되살아난다.
-- ============================================================================
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, 'SHORT_CLOSED', '미납종결',
       '남은 수량은 안 들어오는 것으로 확정하고 끝냈다.', 'gray', 45, 'system'
  FROM tb_code_group g
 WHERE g.code_group_id = 'ORDER_STATUS';

ALTER TABLE tb_purchase_order DROP CONSTRAINT ck_purord_status;
ALTER TABLE tb_purchase_order ADD  CONSTRAINT ck_purord_status
      CHECK (order_status IN ('DRAFT','ISSUED','PARTIAL','CLOSED','SHORT_CLOSED','CANCELED'));


-- ============================================================================
-- 2. 종결 사유
--
-- 사유를 필수로 받는다. 발주 취소가 사유를 받는 것과 같은 이유다 —
-- 잔량을 포기한 것은 돈과 납기에 대한 판단이라, 나중에 "왜 20 이 안
-- 들어왔나" 를 물으면 답이 있어야 한다.
--
-- 상태와 따로 두는 이유: '안 들어옴' 은 상태가, '왜' 는 사유가 답한다.
-- 사유를 상태값으로 만들면(단종종결 · 소진종결 …) 상태가 늘어날 때마다
-- 코드와 화면을 고쳐야 한다.
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('REASON_PO_CLOSE', '발주 미납종결 사유',
        '남은 수량을 안 받기로 한 이유', 'REASON', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('SUPPLY_OUT', '공급처 재고소진', '공급처에 물건이 남지 않았다',              'amber', 10),
        ('DISCONTINUE','단종',           '더 만들지 않는 품목이 되었다',              'red',   20),
        ('WITHDRAW',   '발주 철회',      '우리가 남은 수량을 안 사기로 했다',         'blue',  30),
        ('SEASON_OVER','시즌 종료',      '지금 받아도 팔 시기가 지났다',              'gray',  40),
        ('ETC',        '기타',           '위에 없는 사유. 비고에 적는다',             'gray',  90)
       ) AS v(code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'REASON_PO_CLOSE';


-- ============================================================================
-- 3. 종결 기록
--
-- 취소(canceled_by/at/reason)와 같은 모양으로 둔다. 한 발주가 취소되면서
-- 동시에 종결될 수는 없어 칸을 합칠 수도 있지만, 합치면 목록에서 그 값이
-- 취소 사유인지 종결 사유인지를 상태로 되짚어야 한다.
-- ============================================================================
ALTER TABLE tb_purchase_order
    ADD COLUMN closed_by     varchar(30),
    ADD COLUMN closed_at     timestamp,
    ADD COLUMN close_reason  varchar(300);

COMMENT ON COLUMN tb_purchase_order.closed_by    IS '미납종결한 사람';
COMMENT ON COLUMN tb_purchase_order.closed_at    IS '미납종결 시각';
COMMENT ON COLUMN tb_purchase_order.close_reason IS '미납종결 사유 — 코드그룹 REASON_PO_CLOSE (+ 비고)';
