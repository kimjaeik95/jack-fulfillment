-- ============================================================================
-- tb_inbound.inbound_type 제약에 'DIRECT' 를 더한다
--
-- V31 이 공통코드에 직납입고를 넣었는데, 정작 저장이 안 됐다. 컬럼에
-- 값을 못 박아 둔 CHECK 제약이 따로 있었기 때문이다.
--
--   ck_inbound_type  CHECK (inbound_type IN ('PURCHASE','RETURN','TRANSFER'))
--
-- 코드표와 제약이 <b>같은 목록을 두 군데에 적어 두고 있다.</b> 한쪽만
-- 고치면 화면에는 뜨는데 저장은 안 되는, 가장 헷갈리는 모양이 된다 —
-- 실제로 V31 만 넣고 시험했을 때 "저장할 수 없는 값입니다" 가 나왔다.
--
-- 제약을 지우지 않고 남기는 이유는, 코드표가 응용 계층의 약속이라면
-- 제약은 DB 자신의 약속이라서다. 잘못된 값이 다른 경로(수기 SQL · 배치)로
-- 들어오는 것은 제약만 막는다.
--
--
-- 【 ck_inbound_order 는 그대로 둔다 】
--
--   CHECK (inbound_type <> 'PURCHASE' OR order_seq IS NOT NULL)
--
-- 구매입고일 때만 발주를 요구하므로 직납입고는 그냥 통과한다. 이것이
-- 원래부터 옳게 적혀 있어서 응용 코드도 같은 모양이었다.
-- ============================================================================
ALTER TABLE tb_inbound DROP CONSTRAINT ck_inbound_type;

ALTER TABLE tb_inbound ADD CONSTRAINT ck_inbound_type
    CHECK (inbound_type IN ('PURCHASE', 'DIRECT', 'RETURN', 'TRANSFER'));

COMMENT ON COLUMN tb_inbound.inbound_type IS
    '코드그룹 INBOUND_TYPE. 구매입고는 발주 필수, 직납입고는 발주 없이 받는다';
