-- ============================================================================
-- 구매발주 데모 (로컬 전용)
--
-- 발주가 한 건도 없어서 입고 흐름 전체가 막혀 있었다. 입고예정은 발주를
-- 근거로 만들고(PUR-PG-006), 검수 · 적치는 그 예정을 받아 도는데, 시작점이
-- 비어 있으니 화면을 열어도 할 수 있는 일이 없다.
--
-- 입고를 함께 넣지는 않는다. 받은 수량(received_qty)은 입고가 만드는 값이라
-- 여기서 채우면 발주에는 '받았다' 고 적혀 있는데 입고전표는 없는 상태가
-- 된다. 부분입고 · 입고완료 상태를 보고 싶으면 화면에서 예정을 만들어
-- 검수를 태우면 된다 — 그게 이 데모가 열어 주려는 길이다.
--
-- 그래서 상태는 셋뿐이다.
--   DRAFT     아직 안 나갔다. 고칠 수 있고 입고예정도 못 만든다
--   ISSUED    나갔다. 입고예정의 근거가 된다
--   CANCELED  거둬들였다. 사유가 남는다
--
-- 단가는 참고값이다. 정산은 이 시스템 범위 밖이라 금액이 맞는지는 아무도
-- 보지 않지만, 비워 두면 발주서에 금액이 안 찍혀 실물과 달라 보인다.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 발주 헤더
--
-- 납기를 셋으로 갈라 둔다.
--   지난 것    독촉 대상이다. 진행현황 화면이 이것을 붉게 보여 준다
--   임박한 것  이번 주에 받을 것
--   여유 있는 것
--
-- 센터도 갈라 둔다. 김해 건이 하나는 있어야 '내 센터 것만' 이 걸러지는지
-- 화면에서 확인할 수 있다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_purchase_order (order_no, supplier_seq, plant_seq, order_status,
                               order_date, due_date, pay_term, remark,
                               issued_by, issued_at, canceled_by, canceled_at, cancel_reason,
                               created_by, created_at)
SELECT v.order_no, s.partner_seq, p.plant_seq, v.order_status,
       v.order_date, v.due_date, s.pay_term, v.remark,
       v.issued_by, v.issued_at, v.canceled_by, v.canceled_at, v.cancel_reason,
       'system', v.created_at
  FROM (VALUES
        -- 납기가 지났는데 아직 안 들어왔다. 독촉 대상.
        ('PO-20260915-0001', 'SUP-001', 'PL001', 'ISSUED',
         date '2026-09-15', date '2026-09-19', '가을 티셔츠 보충',
         'buyer01', timestamp '2026-09-15 10:20:00', NULL, NULL, NULL,
         timestamp '2026-09-15 09:40:00'),
        -- 이번 주 납기
        ('PO-20260918-0001', 'SUP-002', 'PL001', 'ISSUED',
         date '2026-09-18', date '2026-09-25', '셔츠 · 데님 정기',
         'buyer01', timestamp '2026-09-18 14:05:00', NULL, NULL, NULL,
         timestamp '2026-09-18 13:30:00'),
        -- 김해로 들어올 건. 센터 필터를 확인하는 자리다.
        ('PO-20260920-0001', 'SUP-003', 'PL002', 'ISSUED',
         date '2026-09-20', date '2026-09-30', '백팩 시즌오프 대비',
         'buyer01', timestamp '2026-09-20 11:10:00', NULL, NULL, NULL,
         timestamp '2026-09-20 10:50:00'),
        -- 아직 안 나갔다. 입고예정을 만들 수 없어야 한다.
        ('PO-20260921-0001', 'SUP-004', 'PL001', 'DRAFT',
         NULL, date '2026-10-05', '재킷 추가 검토중',
         NULL, NULL, NULL, NULL, NULL,
         timestamp '2026-09-21 16:20:00'),
        -- 공급처가 못 준다고 해서 거둬들인 건
        ('PO-20260916-0001', 'SUP-001', 'PL001', 'CANCELED',
         date '2026-09-16', date '2026-09-24', '원단 수급 불가 통보',
         'buyer01', timestamp '2026-09-16 09:30:00',
         'buyer01', timestamp '2026-09-17 15:40:00', 'NO_SUPPLY',
         timestamp '2026-09-16 09:10:00')
       ) AS v(order_no, supplier_id, plant_id, order_status,
              order_date, due_date, remark,
              issued_by, issued_at, canceled_by, canceled_at, cancel_reason,
              created_at)
  JOIN tb_partner s ON s.partner_id = v.supplier_id AND s.supplier_yn = 'Y'
  JOIN tb_plant   p ON p.plant_id   = v.plant_id;


-- ----------------------------------------------------------------------------
-- 발주 줄
--
-- 한 발주에 같은 SKU 를 두 줄 담지 않는다 (uk_purord_line_sku). 몇 개를
-- 받을지 정할 수 없기 때문이다.
--
-- received_qty 는 기본값 0 그대로 둔다. 받은 수량은 입고가 만드는 값이고,
-- 여기서 채우면 발주에는 받았다고 적혀 있는데 입고전표가 없는 상태가 된다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_purchase_order_line (order_seq, line_no, sku_seq, order_qty, unit_price, remark)
SELECT o.order_seq, v.line_no, k.sku_seq, v.order_qty, v.unit_price, v.remark
  FROM (VALUES
        -- PO-20260915-0001 대한섬유 · 티셔츠 (납기 지남)
        ('PO-20260915-0001', 1, 'PRD-24001-BK-M',  120, 8500,  NULL),
        ('PO-20260915-0001', 2, 'PRD-24001-WH-M',   80, 8500,  NULL),
        ('PO-20260915-0001', 3, 'PRD-24001-BK-L',   60, 8500,  '라지 물량 부족'),
        -- PO-20260918-0001 한성어패럴 · 셔츠 · 데님
        ('PO-20260918-0001', 1, 'PRD-24002-WH-L',   40, 21000, NULL),
        ('PO-20260918-0001', 2, 'PRD-24003-NV-32',  30, 26000, NULL),
        -- PO-20260920-0001 누리액세서리 · 백팩 (김해)
        ('PO-20260920-0001', 1, 'PRD-23001-BK-FREE', 50, 16000, NULL),
        ('PO-20260920-0001', 2, 'PRD-23001-BE-FREE', 30, 16000, NULL),
        -- PO-20260921-0001 구일텍스 · 재킷 (작성중)
        ('PO-20260921-0001', 1, 'PRD-24004-GY-M',   20, 58000, '단가 협의중'),
        ('PO-20260921-0001', 2, 'PRD-24004-GY-L',   20, 58000, '단가 협의중'),
        -- PO-20260916-0001 취소된 건
        ('PO-20260916-0001', 1, 'PRD-24003-NV-30',  40, 26000, NULL)
       ) AS v(order_no, line_no, sku_id, order_qty, unit_price, remark)
  JOIN tb_purchase_order o ON o.order_no = v.order_no
  JOIN tb_sku            k ON k.sku_id   = v.sku_id;


-- ----------------------------------------------------------------------------
-- 채번 카운터
--
-- 발주번호를 직접 박아 넣었으니 카운터도 올려야 한다. 안 올리면 화면에서
-- 만든 첫 발주가 같은 번호를 다시 받아 유니크 제약에 걸린다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_doc_number (doc_type, doc_date, last_seq) VALUES
  ('PO', date '2026-09-15', 1),
  ('PO', date '2026-09-16', 1),
  ('PO', date '2026-09-18', 1),
  ('PO', date '2026-09-20', 1),
  ('PO', date '2026-09-21', 1)
ON CONFLICT (doc_type, doc_date)
DO UPDATE SET last_seq = GREATEST(tb_doc_number.last_seq, EXCLUDED.last_seq);
