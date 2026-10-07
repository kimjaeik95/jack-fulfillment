-- ============================================================================
-- 정책 표가 코드와 어긋난 곳을 맞춘다
--
-- 이 표는 규칙을 '실행' 하지 않는다. 평가 엔진이 없어서 판정은 서비스마다
-- 손으로 쓴 자바 · SQL 이 하고, 표는 <b>어떤 통제가 있는지를 사람에게
-- 보여 주는 자리</b>다 (limit_qty · MASKING · READONLY 세 값만 실제로 읽힌다).
--
-- 그래서 표가 틀려도 시스템은 안 멈춘다. 대신 <b>읽는 사람이 틀린 것을
-- 믿는다.</b> 없는 규칙보다 있다고 믿게 만드는 규칙이 나쁘다 — V918 에서
-- P004 · P007 을 고친 것과 같은 이유다.
--
--
-- 【 P005 — 가리키는 기능이 틀렸다 】
--
--   지금  INBOUND_WORKER × INB_APPROVE
--
-- 입고 작업자에게 INB_APPROVE 가 없다. 화면이 '⚠ 권한 미매핑' 으로 띄우는
-- 그것인데, <b>할 수도 없는 일에 조건을 붙여 둔</b> 꼴이다.
--
-- 이 규칙이 실제로 막는 자리는 검수 · 적치다. ReceiptService 의
-- requireInspectable 류가 상태를 보고 거절하고, 그 경로의 권한은
-- INB_INSPECT 다 — 입고 작업자가 가지고 있다.
--
--   조건식  inbound.status == "CONFIRMED"
--
-- <b>CONFIRMED 라는 상태가 없다.</b> tb_inbound 의 상태는 PLANNED ·
-- ARRIVED · INSPECTING · PUTAWAY · DONE · CANCELED 다. 입고완료는 DONE 이다.
-- 게다가 다른 식은 전부 '통과 조건' 인데 이것만 '금지 조건' 으로 적혀 방향이
-- 반대였다.
--
--
-- 【 P001 · P008 — 조건식 칸인데 조건이 아니다 】
--
--   P001  action == "U" && target.type == "BIZ_QTY"
--   P008  mask(user.phone) && mask(user.email)
--
-- P001 의 target.type · BIZ_QTY 는 코드에 없는 개념이다. 이 통제는 조건으로
-- 막는 것이 아니라 <b>그런 기능을 아예 만들지 않은 것</b>이다 — 수량은
-- StockLedger 를 거쳐야만 바뀌고, 그 길은 전 시스템에 다섯 군데뿐이다.
--
-- P008 은 참/거짓을 내는 조건이 아니라 '하는 일' 이다. 마스킹은 조회 계층이
-- 응답을 만들 때 수행하고, 엔진이 판정할 것이 없다.
--
-- 둘 다 비운다. CONDITION 유형만 조건식을 필수로 요구하므로(PolicyService
-- NEEDS_CONDITION) DENY · MASKING 은 비워도 저장된다.
--
--
-- 【 P003 · P004 · P006 · P007 — 이름을 실제 필드에 맞춘다 】
--
-- 식은 의사코드지만, 가리키는 이름이 코드에 없으면 읽는 사람이 찾아갈 데가
-- 없다. 실재하는 이름으로 바꾼다.
--
--   po.cancelReason          → OrderCancelRequest.reasonCode
--   request.createdBy        → adjust.requestedBy  (StockAdjust)
--   order.skuList/remainQty  → tb_outbound_line 의 세 칸
--   <= 1000                  → <= policy.limitQty   (한도를 식에서 뺀다)
--
-- 마지막이 특히 그렇다. 한도가 식에 박혀 있으면 1000 을 500 으로 바꿀 때
-- 식까지 고쳐야 하는데, limit_qty 컬럼이 따로 있는 이유가 그것이다.
-- ============================================================================


-- 1. P005 — 대상기능을 실제로 막는 자리로, 상태값을 실재하는 것으로
UPDATE tb_policy
   SET perm_seq       = (SELECT perm_seq FROM tb_permission WHERE perm_id = 'INB_INSPECT'),
       condition_expr = 'inbound.status != ''DONE''',
       target_field   = '검수 · 적치 실적',
       message        = '입고완료된 건은 검수 · 적치 실적을 고칠 수 없습니다. '
                     || '정정요청을 등록하세요.',
       remark         = 'ReceiptService 가 상태를 보고 거절한다(requireInspectable 류). '
                     || '전에는 INB_APPROVE 를 가리켰는데 입고 작업자에게 그 권한이 없어 '
                     || '''권한 미매핑'' 으로 떴다 — 실제로 막히는 경로는 검수 · 적치이고 '
                     || '그 권한이 INB_INSPECT 다. 상태값도 틀려 있었다: CONFIRMED 는 '
                     || '없는 값이고 입고완료는 DONE 이다.',
       updated_by     = 'system',
       updated_at     = CURRENT_TIMESTAMP
 WHERE policy_id = 'P005';


-- 2. P001 — 조건으로 막는 통제가 아니다. 기능 자체가 없다
UPDATE tb_policy
   SET condition_expr = NULL,
       remark         = '조건으로 막는 통제가 아니라 그런 기능을 만들지 않은 것이다. '
                     || '수량은 StockLedger.apply 를 거쳐야만 바뀌고 그 길은 다섯 군데뿐이며, '
                     || 'INV_QTY_EDIT 을 쓰는 코드는 없다. 설정 권한과 업무 데이터 변경 권한을 '
                     || '분리하기 위한 통제라는 선언으로 남긴다.',
       updated_by     = 'system',
       updated_at     = CURRENT_TIMESTAMP
 WHERE policy_id = 'P001';


-- 3. P008 — 조건이 아니라 하는 일이다
UPDATE tb_policy
   SET condition_expr = NULL,
       remark         = '참/거짓을 내는 조건이 아니라 수행이다. LoginUser.isMasked() 가 '
                     || 'MASKING 정책의 유무만 보고, 가리는 일은 UserResponse · '
                     || 'AuditQueryService 가 응답을 만들 때 한다. 조건식 칸은 비운다.',
       updated_by     = 'system',
       updated_at     = CURRENT_TIMESTAMP
 WHERE policy_id = 'P008';


-- 4. P003 · P004 · P006 · P007 — 식의 이름을 실재하는 필드로
UPDATE tb_policy
   SET condition_expr = 'isNotEmpty(request.reasonCode)',
       remark         = '사유 코드 + 자유 텍스트 10자 이상 권장. OrderCancelRequest 의 '
                     || '@NotBlank 와 codeValues.require(REASON_PO_CANCEL) 가 막는다.',
       updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE policy_id = 'P003';

UPDATE tb_policy
   SET condition_expr = 'adjust.requestedBy != actor.userId',
       updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE policy_id = 'P004';

UPDATE tb_policy
   SET condition_expr = 'line.pickedQty + line.shortageQty + scan.qty <= line.instructedQty',
       remark         = '초과 스캔 시 결품/대체 프로세스로 유도. 판정은 매퍼의 '
                     || 'addPickedQty · addShortageQty UPDATE 가 WHERE 로 한다 — '
                     || '0건 수정이면 서비스가 거절한다. 화면이 아니라 SQL 이 막으므로 '
                     || 'API 를 직접 불러도 넘지 못한다.',
       updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE policy_id = 'P006';

UPDATE tb_policy
   SET condition_expr = 'sum(abs(adjust.lines.qtyDelta)) <= policy.limitQty',
       updated_by = 'system', updated_at = CURRENT_TIMESTAMP
 WHERE policy_id = 'P007';
