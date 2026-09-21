-- ============================================================================
-- 거래처 데모 정정 (로컬 전용, V904 보완)
--
-- 고객 두 건이 이 시스템의 전제와 어긋나 있었다.
--
-- 'CUS-001 무신사스토어'
--   무신사는 여기서 판매채널(MSSN)이다. 우리가 무신사에 파는 것이 아니라
--   무신사에서 들어온 주문을 수집한다. 같은 이름이 채널에도 거래처에도
--   있으면 화면마다 어느 쪽인지 되물어야 한다.
--
-- 'CUS-003 홍길동'
--   거래처에는 계속 거래하는 상대만 둔다고 정했다. 개인 수령인은 주문이
--   값으로 들고 가지 거래처로 만들지 않는다 (V904 주석). 개인 이름이 하나
--   남아 있으면 그 규칙이 무너진 것처럼 보인다.
--
-- 지우지 않고 이름만 바꾼다. 주소가 딸려 있어 지우면 주소까지 다시 넣어야
-- 하는데, 바꿔야 할 것은 이름뿐이다.
--
-- 둘 다 아직 주문에 쓰이지 않는다. 3차 주문은 채널주문만이고, 거래처에
-- 파는 판매오더는 11차(판매관리)다. 그래도 지금 고쳐 둔다 — 시연에서
-- 거래처 화면을 열면 바로 보이는 데이터다.
-- ============================================================================
UPDATE tb_partner
   SET partner_name = '대한패션유통',
       email        = 'buyer@daehanfa.example',
       remark       = '홀세일 · 수도권 편집샵 공급',
       updated_by   = 'system',
       updated_at   = now()
 WHERE partner_id = 'CUS-001';

UPDATE tb_partner
   SET partner_name = '성수편집샵',
       biz_reg_no   = '150-81-10003',
       manager_name = '최대표',
       phone        = '02-467-0003',
       email        = 'shop@seongsu.example',
       remark       = '소규모 편집샵 · 선결제',
       updated_by   = 'system',
       updated_at   = now()
 WHERE partner_id = 'CUS-003';

-- 주소도 개인 자택에서 매장으로 바꾼다. 수령인이 '홍길동' 인 채로 남으면
-- 이름만 바꾼 티가 난다.
UPDATE tb_partner_address a
   SET address_name   = '성수 매장',
       receiver_name  = '최대표',
       phone          = '02-467-0003',
       zip_code       = '04780',
       address        = '서울특별시 성동구 연무장길 33',
       address_detail = '1층',
       delivery_memo  = '영업시간 11~20시',
       updated_by     = 'system',
       updated_at     = now()
  FROM tb_partner p
 WHERE p.partner_seq = a.partner_seq
   AND p.partner_id  = 'CUS-003';
