-- ============================================================================
-- 공통정책을 사실에 맞춘다 — P004 · P007
--
-- 정책 표에 적혀 있는데 시스템이 안 하는 규칙이 둘 있었다. 없는 규칙보다
-- 나쁘다. 없으면 사람이 조심하는데, 있다고 적힌 규칙은 "막아 주겠지" 하고
-- 안 본다.
--
--
-- 【 P004 — 표가 코드보다 약했다 】
--
--   tb_policy   SOD / WARN     "권고합니다"
--   실제 코드    SOD_VIOLATION  던지고 막는다
--
-- AdjustService · RequestService · CorrectService 세 곳이 자기 요청 자기
-- 승인을 예외로 막고 있다. 사고는 안 났지만, 정책 화면을 보고 "권고구나"
-- 하면 틀린 이해를 한다. 코드가 하는 대로 표를 고친다.
--
--
-- 【 P007 — 아예 아무 일도 안 했다 】
--
-- '센터 재고조정 승인 한도 500만원 / 1,000EA' 가 화면에 떠 있는데,
-- AdjustService.approve() 는 그 숫자를 읽지 않았다. 센터장이 50,000EA
-- 조정도 혼자 승인하고 재고가 바뀌었다.
--
-- 이번에 approve() 에 한도 검사를 넣었다. 그러면서 표도 실제로 잴 수 있는
-- 모양으로 고친다.
--
--   limit_amount = 5,000,000   →  NULL
--
-- <b>조정 전표에는 금액이 없다.</b> tb_stock_adjust_line 은 수량만 담는다
-- (qty_before / qty_after / qty_delta) — 단가도 금액도 없다. 500만원은
-- 구조상 잴 수가 없는 수치라, 두면 또 하나의 거짓말이 된다.
--
--   limit_qty = 1,000          →  그대로. 이건 잴 수 있다
--
-- 전표 전체의 |변동량| 합으로 잰다. 줄마다 재면 50,000 개를 500 개씩
-- 100 줄로 쪼개 그냥 지나간다.
--
--
-- 【 넘으면 누가 승인하나 】
--
-- P007 의 alt_process 가 '본사 기준정보 담당 승인 요청' 이었는데, 정작
-- HQ_MASTER 에게 INV_ADJ_APPROVE 가 없었다. 한도만 걸면 1,000EA 넘는
-- 조정은 <b>아무도 승인할 수 없게</b> 된다 — 막다른 길이다.
--
--   지금  CENTER_MGR · SYS_ADMIN
--   추가  HQ_MASTER
--
-- 한도(P007)는 CENTER_MGR 에만 걸려 있어서 HQ_MASTER 는 제한 없이 받는다.
-- 시스템 관리자에게 떠넘기지 않는 것이 요점이다 — 그쪽은 설정을 보는
-- 자리지 업무 수량을 판단하는 자리가 아니다 (P001 이 같은 말을 한다).
-- ============================================================================


-- 1. P004 — 코드가 막고 있으니 표도 막는다고 적는다
UPDATE tb_policy
   SET enforce_level = 'BLOCK',
       message       = '본인이 요청한 건은 본인이 승인할 수 없습니다. 혼자 올리고 혼자 '
                    || '승인하면 승인은 통제가 아니라 절차가 됩니다. 다른 승인자에게 '
                    || '요청하세요.',
       alt_process   = '다른 승인자 / 상위 승인',
       remark        = 'AdjustService · RequestService · CorrectService 가 SOD_VIOLATION 으로 '
                    || '차단한다. 평가 엔진이 없어 규칙은 코드에 산다.',
       updated_by    = 'system',
       updated_at    = CURRENT_TIMESTAMP
 WHERE policy_id = 'P004';


-- 2. P007 — 잴 수 없는 금액을 떼고, 잴 수 있는 수량만 남긴다
UPDATE tb_policy
   SET policy_name    = '센터 재고조정 승인 수량 한도',
       enforce_level  = 'BLOCK',
       condition_expr = 'sum(abs(adjust.line.qtyDelta)) <= 1000',
       target_field   = '전표 총 변동량 (절대값 합)',
       limit_amount   = NULL,
       message        = '센터 승인 한도(1,000EA)를 초과했습니다. 본사 승인이 필요합니다.',
       alt_process    = '본사 기준정보 담당에게 승인 요청',
       remark         = '조정 전표에는 금액이 없어(tb_stock_adjust_line 은 수량만) 금액 한도는 '
                     || '잴 수 없다. 수량만 남긴다. AdjustService.approve() 가 전표 전체의 '
                     || '|변동량| 합으로 검사한다 — 줄마다 재면 쪼개서 지나간다.',
       updated_by     = 'system',
       updated_at     = CURRENT_TIMESTAMP
 WHERE policy_id = 'P007';


-- 3. 한도를 넘은 건을 받을 승인자를 만든다
--    이것이 없으면 1,000EA 초과 조정은 막다른 길이 된다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, v.action_code, 'system'
  FROM (VALUES ('R'), ('A')) AS v(action_code)
  JOIN tb_role r       ON r.role_id = 'HQ_MASTER'
  JOIN tb_permission p ON p.perm_id = 'INV_ADJ_APPROVE'
 WHERE NOT EXISTS (
        SELECT 1 FROM tb_role_permission x
         WHERE x.role_seq = r.role_seq
           AND x.perm_seq = p.perm_seq
           AND x.action_code = v.action_code);
