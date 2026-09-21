-- ============================================================================
-- 채널주문 데모 (로컬 전용)
--
-- 아홉 건이다. 숫자를 채우려는 것이 아니라 화면에서 갈라지는 길을 하나씩
-- 밟게 하려는 것이다.
--
--   접수 · 확정 · 취소          상태별로 목록과 버튼이 달라진다
--   네 채널 전부                채널 필터가 한 값만 도는지 본다
--   미매핑 줄                   오류대기 조회와 확정 차단 (ORD-005)
--   재고보다 많은 수량          결품 데모 (재킷 10 주문, 재고 4)
--
-- 날짜는 9/19 · 9/20 · 9/21 로 나눈다. 목록 기본정렬이 최신순이라 하루에
-- 다 몰아 넣으면 정렬이 도는지 확인할 수 없다.
--
-- 수령인은 전부 개인이고, 이 사람들은 거래처에 없다 — 주문이 이름과 주소를
-- 값으로 복사해 갖는다. 나중에 그 사람이 이사를 가도 지난 주문의 배송지는
-- 그대로여야 하기 때문이다 (ORD-002).
-- ============================================================================
INSERT INTO tb_order (order_no, channel_seq, ext_order_no,
                      order_status, ordered_at, receiver_name, receiver_phone,
                      zip_code, address, address_detail, delivery_memo, remark,
                      canceled_by, canceled_at, cancel_reason, created_by, created_at)
SELECT v.order_no, c.channel_seq, v.ext_order_no,
       v.order_status, v.ordered_at, v.receiver_name, v.receiver_phone,
       v.zip_code, v.address, v.address_detail, v.delivery_memo, v.remark,
       v.canceled_by, v.canceled_at, v.cancel_reason, 'system', v.ordered_at
  FROM (VALUES
        -- 9/19 --------------------------------------------------------------
        ('ORD-20260919-0001', 'CPNG', 'CP-99001',
         'CONFIRMED', timestamp '2026-09-19 09:12:00',
         '김서연', '010-2211-3344', '06035',
         '서울특별시 강남구 가로수길 21', '302호', '부재 시 문 앞', NULL,
         NULL, NULL, NULL),
        ('ORD-20260919-0002', 'MSSN', 'MS-77120',
         'RECEIVED', timestamp '2026-09-19 14:40:00',
         '박지훈', '010-7788-1020', '13529',
         '경기도 성남시 분당구 판교역로 166', '105동 902호', NULL, NULL,
         NULL, NULL, NULL),
        -- 세 줄짜리. 상세 화면에서 줄이 여럿일 때를 본다.
        ('ORD-20260919-0003', 'OWN', 'OWN-5490',
         'CONFIRMED', timestamp '2026-09-19 16:05:00',
         '윤지호', '010-6060-7070', '04524',
         '서울특별시 중구 세종대로 110', '1201호', NULL, '자사몰 기획전', NULL, NULL, NULL),
        -- 9/20 --------------------------------------------------------------
        -- 매핑이 PENDING 인 상품코드로 들어온 주문. 줄에 SKU 가 안 붙는다.
        ('ORD-20260920-0001', 'MSSN', 'MS-77133',
         'RECEIVED', timestamp '2026-09-20 10:02:00',
         '이하늘', '010-3030-5050', '48058',
         '부산광역시 해운대구 센텀중앙로 90', '1203호', NULL, NULL,
         NULL, NULL, NULL),
        ('ORD-20260920-0002', 'OWN', 'OWN-5521',
         'CONFIRMED', timestamp '2026-09-20 11:31:00',
         '정민아', '010-9090-1212', '34126',
         '대전광역시 유성구 대학로 99', NULL, '경비실 맡겨주세요', NULL,
         NULL, NULL, NULL),
        -- 판매중단(STOPPED) 매핑으로 들어와 취소한 주문
        ('ORD-20260920-0003', 'SMST', 'SS-31002',
         'CANCELED', timestamp '2026-09-20 13:15:00',
         '최윤호', '010-5566-7788', '61186',
         '광주광역시 북구 첨단과기로 123', '202동 401호', NULL, NULL,
         'system', timestamp '2026-09-20 15:00:00', 'OUT_OF_STOCK'),
        ('ORD-20260920-0004', 'CPNG', 'CP-99012',
         'RECEIVED', timestamp '2026-09-20 17:22:00',
         '강다온', '010-2323-4545', '17384',
         '경기도 이천시 부발읍 경충대로 2000', '3층', '문 앞에 두지 마세요', NULL,
         NULL, NULL, NULL),
        -- 9/21 --------------------------------------------------------------
        -- 재킷 10 장. 재고는 4 장뿐이라 할당하면 결품이 난다.
        ('ORD-20260921-0005', 'CPNG', 'CP-99044',
         'RECEIVED', timestamp '2026-09-21 08:45:00',
         '한소미', '010-1414-2828', '03925',
         '서울특별시 마포구 월드컵북로 400', '7층', NULL, '기획전 물량',
         NULL, NULL, NULL),
        -- 채널주문번호 없이 손으로 넣은 건. 누락 주문을 보정하는 흐름이다.
        ('ORD-20260921-0006', 'MSSN', NULL,
         'RECEIVED', timestamp '2026-09-21 09:58:00',
         '오세린', '010-8282-9393', '04780',
         '서울특별시 성동구 연무장길 33', '1층', NULL, 'CS 접수 — 채널 누락분',
         NULL, NULL, NULL)
       ) AS v(order_no, channel_id, ext_order_no,
              order_status, ordered_at, receiver_name, receiver_phone,
              zip_code, address, address_detail, delivery_memo, remark,
              canceled_by, canceled_at, cancel_reason)
  JOIN tb_channel c ON c.channel_id = v.channel_id;


-- ----------------------------------------------------------------------------
-- 주문 줄
--
-- 줄 상태는 주문 상태가 아니라 SKU 가 붙었느냐로 갈린다. SKU 가 있으면
-- 등록하는 순간 MAPPED 고, 없으면 RECEIVED 로 남아 오류대기가 된다
-- (SalesOrderSaveRequest.toNewLine). 취소한 주문의 줄만 CANCELED 다.
--
-- 그래서 접수 상태인 주문에도 MAPPED 줄이 있다. 주문의 RECEIVED 는 "아직
-- 확정을 안 눌렀다" 는 뜻이고, 줄의 MAPPED 는 "무엇을 보낼지는 안다" 는
-- 뜻이라 서로 다른 것을 가리킨다.
--
-- 외부 상품코드는 SKU 를 붙인 뒤에도 지우지 않는다. 수집해 온 원문이라
-- 나중에 매핑을 의심할 때 볼 것이 그것뿐이다 (ORD-003).
-- ----------------------------------------------------------------------------
INSERT INTO tb_order_line (order_seq, line_no, sku_seq, ext_product_code, ext_option_code,
                           ext_product_name, ext_option_name, order_qty, line_status,
                           unit_price, line_amount, remark, created_by, created_at)
SELECT o.order_seq, v.line_no, k.sku_seq, v.ext_product_code, v.ext_option_code,
       v.ext_product_name, v.ext_option_name, v.order_qty, v.line_status,
       v.unit_price, v.unit_price * v.order_qty, v.remark, 'system', o.created_at
  FROM (VALUES
        -- ORD-20260919-0001 쿠팡 · 확정
        ('ORD-20260919-0001', 1, 'PRD-24001-BK-M', 'CP7788001', '1000123456',
         '베이직 반팔 티셔츠', '블랙 / M', 2, 'MAPPED', 19900, NULL),
        ('ORD-20260919-0001', 2, 'PRD-24001-WH-M', 'CP7788001', '1000123457',
         '베이직 반팔 티셔츠', '화이트 / M', 1, 'MAPPED', 19900, NULL),
        -- ORD-20260919-0002 무신사 · 접수
        ('ORD-20260919-0002', 1, 'PRD-24002-WH-L', 'MSS-330011', NULL,
         '옥스포드 셔츠', '화이트 / L', 3, 'MAPPED', 49000, NULL),
        -- ORD-20260919-0003 공식몰 · 확정 · 세 줄
        ('ORD-20260919-0003', 1, 'PRD-24001-BK-M', 'OWN-24001', 'BK-M',
         '베이직 반팔 티셔츠', '블랙 / M', 2, 'MAPPED', 19900, NULL),
        ('ORD-20260919-0003', 2, 'PRD-24001-WH-M', 'OWN-24001', 'WH-M',
         '베이직 반팔 티셔츠', '화이트 / M', 1, 'MAPPED', 19900, NULL),
        ('ORD-20260919-0003', 3, 'PRD-24002-WH-M', 'OWN-24002', 'WH-M',
         '옥스포드 셔츠', '화이트 / M', 1, 'MAPPED', 49000, NULL),
        -- ORD-20260920-0001 무신사 · 미매핑 (매핑상태 PENDING)
        ('ORD-20260920-0001', 1, NULL, 'MSS-330022', NULL,
         '와이드 데님 팬츠', '네이비 / 32', 1, 'RECEIVED', 59000,
         '채널 매핑 확인 전 — SKU 미정'),
        -- ORD-20260920-0002 공식몰 · 확정
        ('ORD-20260920-0002', 1, 'PRD-24001-BK-S', 'OWN-24001', 'BK-S',
         '베이직 반팔 티셔츠', '블랙 / S', 1, 'MAPPED', 19900, NULL),
        ('ORD-20260920-0002', 2, 'PRD-24003-NV-30', 'OWN-24003', 'NV-30',
         '와이드 데님 팬츠', '네이비 / 30', 2, 'MAPPED', 59000, NULL),
        -- ORD-20260920-0003 스마트스토어 · 취소 (매핑상태 STOPPED)
        ('ORD-20260920-0003', 1, NULL, 'SS-9001', NULL,
         '캔버스 백팩', '블랙', 1, 'CANCELED', 39000, '판매중단 상품'),
        -- ORD-20260920-0004 쿠팡 · 접수
        ('ORD-20260920-0004', 1, 'PRD-24001-BK-M', 'CP7788001', '1000123456',
         '베이직 반팔 티셔츠', '블랙 / M', 1, 'MAPPED', 19900, NULL),
        ('ORD-20260920-0004', 2, 'PRD-24001-WH-M', 'CP7788001', '1000123457',
         '베이직 반팔 티셔츠', '화이트 / M', 1, 'MAPPED', 19900, NULL),
        -- ORD-20260921-0005 쿠팡 · 접수 (재고 4 < 주문 10 이라 할당하면 결품)
        ('ORD-20260921-0005', 1, 'PRD-24004-GY-L', 'CP7788999', '1000999888',
         '울 블렌드 재킷', '그레이 / L', 10, 'MAPPED', 129000, NULL),
        -- ORD-20260921-0006 무신사 · 손으로 넣은 건이라 외부코드가 없다
        ('ORD-20260921-0006', 1, 'PRD-23001-BK-FREE', NULL, NULL, NULL, NULL,
         1, 'MAPPED', 39000, NULL)
       ) AS v(order_no, line_no, sku_id, ext_product_code, ext_option_code,
              ext_product_name, ext_option_name, order_qty, line_status,
              unit_price, remark)
  JOIN tb_order o ON o.order_no = v.order_no
  LEFT JOIN tb_sku k ON k.sku_id = v.sku_id;


-- ----------------------------------------------------------------------------
-- 채번 카운터를 맞춘다
--
-- 주문번호를 직접 박아 넣었으니 카운터도 같이 올려야 한다. 안 올리면 화면에서
-- 만든 첫 주문이 ORD-20260921-0005 를 다시 받아 유니크 제약에 걸린다.
--
-- 이미 올라가 있을 수 있어 GREATEST 로 덮어쓴다 — 카운터는 내려가면 안 된다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_doc_number (doc_type, doc_date, last_seq) VALUES
  ('ORD', date '2026-09-19', 3),
  ('ORD', date '2026-09-20', 4),
  ('ORD', date '2026-09-21', 6)
ON CONFLICT (doc_type, doc_date)
DO UPDATE SET last_seq = GREATEST(tb_doc_number.last_seq, EXCLUDED.last_seq);
