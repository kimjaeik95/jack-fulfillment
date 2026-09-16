-- ============================================================================
-- 시연 초기화 — 거래 데이터만 지운다
--
-- 시연은 반복된다. 두 번째 시연에서 "이미 사용 중인 번호입니다" 가 뜨면
-- 이야기가 거기서 끊기고, 보는 사람은 기능이 고장 난 줄 안다.
--
-- 지우는 것   발주 · 입고 · 재고 · 실사 · 조정 · 이동 · 전표번호 · 감사로그
-- 남기는 것   조직 · 거점 · 상품 · SKU · 채널 · 거래처 · 계정 · 권한 · 코드
--
-- 기준정보를 남기는 이유는 그게 시연의 무대이기 때문이다. 매번 다시 만들면
-- 시연 시간의 절반이 창고 등록에 들어간다. 기준정보를 보여 주고 싶으면
-- 대본 [3] 에서 새로 하나씩 더 만들면 된다 — 그건 지우지 않아도 된다.
--
-- <b>운영에서는 절대 돌리지 않는다.</b> local 프로파일의 시연 환경 전용이다.
--
--   export PGPASSWORD=<로컬 DB 비밀번호>
--   psql -U fulfillment -h localhost -d fulfillment -f docs/tools/demo-reset.sql
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 순서가 곧 외래키의 역순이다.
--
-- 정정 → 적치 · 검수 → 입고라인 → 입고 → 발주라인 → 발주 → 요청 순으로
-- 내려온다. 하나라도 건너뛰면 그 다음 DELETE 가 외래키에 걸리고, psql 은
-- 거기서부터 뒤를 통째로 건너뛴다 — 절반만 지워진 채로 시연을 시작하게 된다.
-- ----------------------------------------------------------------------------

-- 입고정정 (INB-PG-008)
DELETE FROM tb_inbound_correct_line;
DELETE FROM tb_inbound_correct;

-- 적치 · 검수 (INB-PG-003~007)
DELETE FROM tb_inbound_putaway;
DELETE FROM tb_inbound_inspect;
DELETE FROM tb_inbound_line;
DELETE FROM tb_inbound;

-- 구매오더 · 구매요청 (PUR-PG-001~006)
DELETE FROM tb_purchase_order_line;
DELETE FROM tb_purchase_order;
DELETE FROM tb_purchase_request_line;
DELETE FROM tb_purchase_request;

-- 재고실사 (INV-PG-008, 009)
DELETE FROM tb_stocktake_line;
DELETE FROM tb_stocktake;

-- 재고조정 (INV-PG-006, 007)
DELETE FROM tb_stock_adjust_line;
DELETE FROM tb_stock_adjust;

-- 재고와 이력 (INV-PG-001~003)
--
-- 할당은 주문(3차)이 만드는 것이라 아직 행이 없지만, 생기면 재고보다 먼저
-- 지워야 한다. 지금 넣어 두는 편이 나중에 빠뜨리는 것보다 낫다.
DELETE FROM tb_stock_alloc;
DELETE FROM tb_stock_history;
DELETE FROM tb_stock;

-- ----------------------------------------------------------------------------
-- 전표번호
--
-- 지우지 않으면 다음 시연의 첫 발주가 PO-...-0007 로 시작한다. 틀린 것은
-- 아니지만 "아까 여섯 건은 뭐였나" 를 설명하게 된다.
-- ----------------------------------------------------------------------------
DELETE FROM tb_doc_number WHERE doc_type IN ('REQ','PO','INB','INBC','ADJ','MOV','TAKE');

-- ----------------------------------------------------------------------------
-- 감사로그
--
-- 거래 전표에 달린 것만 지운다. 계정 · 권한 · 기준정보를 누가 건드렸는지는
-- 남긴다 — 그것까지 지우면 감사로그 화면이 텅 비어 [13] 에서 보여 줄 것이
-- 없어진다.
-- ----------------------------------------------------------------------------
DELETE FROM tb_audit_log_detail WHERE log_seq IN (
  SELECT log_seq FROM tb_audit_log
   WHERE target_key ~ '^(REQ|PO|INB|INBC|ADJ|MOV|TAKE)-');
DELETE FROM tb_audit_log
 WHERE target_key ~ '^(REQ|PO|INB|INBC|ADJ|MOV|TAKE)-';

COMMIT;

-- ============================================================================
-- 확인 — 위 줄은 모두 0, 아래 줄은 모두 0 보다 커야 한다
-- ============================================================================
\echo ''
\echo '-- 지워졌나 (모두 0) --------------------------------------'
SELECT (SELECT COUNT(*) FROM tb_purchase_request) AS "구매요청",
       (SELECT COUNT(*) FROM tb_purchase_order)   AS "발주",
       (SELECT COUNT(*) FROM tb_inbound)          AS "입고",
       (SELECT COUNT(*) FROM tb_inbound_correct)  AS "정정",
       (SELECT COUNT(*) FROM tb_stock)            AS "재고",
       (SELECT COUNT(*) FROM tb_stock_history)    AS "재고이력",
       (SELECT COUNT(*) FROM tb_stock_adjust)     AS "조정",
       (SELECT COUNT(*) FROM tb_stocktake)        AS "실사";

\echo ''
\echo '-- 무대는 남았나 (모두 0 초과) ----------------------------'
SELECT (SELECT COUNT(*) FROM tb_plant)    AS "플랜트",
       (SELECT COUNT(*) FROM tb_warehouse) AS "창고",
       (SELECT COUNT(*) FROM tb_location)  AS "빈",
       (SELECT COUNT(*) FROM tb_sku)       AS "SKU",
       (SELECT COUNT(*) FROM tb_supplier)  AS "공급처",
       (SELECT COUNT(*) FROM tb_user WHERE status = 'ACTIVE') AS "계정";
