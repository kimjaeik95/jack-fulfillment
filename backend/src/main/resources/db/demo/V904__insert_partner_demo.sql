-- ============================================================================
-- 공급처 · 고객 · 배송지 예시 (로컬 전용)
--
-- 기준정보 담당(HQ_MASTER)에게 거래처 권한을 준다. 사유코드도 같다 —
-- 현장이 새 실패 유형을 발견하면 늘리는 것이 이 사람의 일이다.
-- ============================================================================

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('HQ_MASTER', 'MST_SUPPLIER', 'RCUDX'),
        ('HQ_MASTER', 'MST_CUSTOMER', 'RCUDX'),
        ('HQ_MASTER', 'MST_REASON',   'RCUD')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 구매담당은 공급처를 읽고 고친다. 발주 상대를 관리하는 것이 그 일이다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES ('PURCHASER', 'MST_SUPPLIER', 'RCU')) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- CS 는 고객과 배송지를 읽는다. 주문 문의에 답하려면 봐야 한다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'CS_VIEWER'
   AND p.perm_id IN ('MST_CUSTOMER', 'MST_REASON');


-- ============================================================================
-- 공급처
-- ============================================================================
INSERT INTO tb_supplier (supplier_id, supplier_name, biz_reg_no, ceo_name, manager_name,
                         phone, email, zip_code, address, status, pay_term,
                         over_receipt_rate, remark, sort_order, created_by) VALUES
  ('SUP-001', '대한섬유',   '124-81-00001', '김대한', '박과장',
   '031-1234-5678', 'park@daehan.co.kr', '10393', '경기도 고양시 일산동구 중앙로 1',
   'ACTIVE', 'NET30', 3.00, '주거래. 티셔츠 · 셔츠', 10, 'system'),
  ('SUP-002', '한성어패럴', '214-81-00002', '이한성', '최대리',
   '06236', 'choi@hansung.co.kr', '06236', '서울특별시 강남구 테헤란로 200',
   'ACTIVE', 'MONTHLY', 5.00, '팬츠 전문', 20, 'system'),
  ('SUP-003', '누리액세서리', '312-81-00003', '정누리', NULL,
   '051-777-8888', NULL, '48058', '부산광역시 해운대구 센텀중앙로 90',
   'ACTIVE', 'PREPAID', 0.00, '가방 · 잡화. 소량 거래', 30, 'system'),
  -- 거래를 멈춘 곳도 하나 둔다. 목록에서 구분이 보이는지 확인하려면 필요하다.
  ('SUP-004', '구일텍스',   '408-81-00004', '한구일', '윤사원',
   '053-222-3333', NULL, '41585', '대구광역시 북구 칠성로 10',
   'SUSPENDED', 'NET60', 0.00, '품질 이슈로 거래 중지 (2026-06)', 40, 'system');

-- 연락처 자리에 우편번호가 들어간 SUP-002 를 바로잡는다.
-- 시드 오타를 그대로 두면 검증에서 '연락처 형식' 을 확인할 수 없다.
UPDATE tb_supplier SET phone = '02-555-0002' WHERE supplier_id = 'SUP-002';


-- ============================================================================
-- 고객 — B2B 와 B2C 를 함께 둔다
-- ============================================================================
INSERT INTO tb_customer (customer_id, customer_name, customer_type, biz_reg_no,
                         manager_name, phone, email, status, pay_term,
                         remark, sort_order, created_by) VALUES
  ('CUS-001', '무신사스토어', 'B2B', '120-81-10001', '김바이어',
   '02-1234-0001', 'buyer@musinsa.example', 'ACTIVE', 'NET30',
   '홀세일 계정', 10, 'system'),
  ('CUS-002', '이천리테일',  'B2B', '130-81-10002', '박구매',
   '031-500-0002', NULL, 'ACTIVE', 'MONTHLY',
   '지역 편집샵', 20, 'system'),
  ('CUS-003', '홍길동',      'B2C', NULL, NULL,
   '010-1111-2222', 'hong@example.com', 'ACTIVE', 'PREPAID',
   '직거래 개인고객', 30, 'system'),
  ('CUS-004', '폐업상사',    'B2B', '140-81-10004', NULL,
   '02-999-0004', NULL, 'CLOSED', NULL,
   '2026-03 폐업', 40, 'system');


-- ============================================================================
-- 배송지 — 고객당 기본 1건 (MST-010)
--
-- CUS-002 는 일부러 기본배송지 없이 두지 않는다. 부분 유니크 인덱스가
-- '하나 이하' 를 강제하지 실재를 강제하지는 않지만, 서비스가 첫 배송지를
-- 자동으로 기본으로 만들기 때문에 실제로는 늘 하나가 있다.
-- ============================================================================
INSERT INTO tb_customer_address (customer_seq, address_name, receiver_name, phone,
                                 zip_code, address, address_detail, delivery_memo,
                                 default_yn, sort_order, created_by)
SELECT c.customer_seq, v.address_name, v.receiver_name, v.phone,
       v.zip_code, v.address, v.address_detail, v.delivery_memo,
       v.default_yn, v.sort_order, 'system'
  FROM (VALUES
        ('CUS-001', '본사 물류창고', '김바이어', '02-1234-0001', '04524',
         '서울특별시 중구 세종대로 110', '지하 1층 입고장', '평일 09~18시만 수령 가능', 'Y', 10),
        ('CUS-001', '성수 쇼룸',     '이매니저', '02-1234-0009', '04781',
         '서울특별시 성동구 아차산로 100', '3층', NULL, 'N', 20),
        ('CUS-002', '이천 본점',     '박구매',   '031-500-0002', '17384',
         '경기도 이천시 부발읍 경충대로 2000', NULL, '문 앞에 두지 마세요', 'Y', 10),
        ('CUS-003', '자택',          '홍길동',   '010-1111-2222', '06236',
         '서울특별시 강남구 테헤란로 5', '101동 1004호', '부재 시 경비실', 'Y', 10),
        ('CUS-004', '구 사무실',     '담당자',   '02-999-0004', '07327',
         '서울특별시 영등포구 여의대로 10', NULL, NULL, 'Y', 10)
       ) AS v(customer_id, address_name, receiver_name, phone, zip_code,
              address, address_detail, delivery_memo, default_yn, sort_order)
  JOIN tb_customer c ON c.customer_id = v.customer_id;
