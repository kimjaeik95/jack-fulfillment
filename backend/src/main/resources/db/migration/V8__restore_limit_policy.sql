-- ============================================================================
-- V8 : 승인한도 정책 복구
--
-- V7 이 매장을 걷어내면서 정책 두 건이 함께 사라졌다.
--   P007 타 매장 재고 직접 수정 금지 (SCOPE / BLOCK)
--   P008 매장 직원 승인 한도        (LIMIT / APPROVAL)
--
-- P008 은 시드에서 유일한 승인한도 정책이었다. 그 유형이 사라지면 승인정책
-- 관리 화면에서 "금액·수량 임계값을 넘으면 상위 승인" 이라는 규칙을 보여줄
-- 예시가 없어지고, 1차 재고조정 승인(INV-PG-007)이 무엇을 근거로 도는지도
-- 드러나지 않는다.
--
-- 요구사항 STK-009 가 "사유코드 필수. 임계값 초과 조정은 승인 필요" 라고
-- 정하고 있으므로, 같은 규칙을 센터 기준으로 다시 넣는다.
--
-- P007(SCOPE)은 복구하지 않는다. 조직 범위 통제는 이제 정책 행이 아니라
-- 데이터 범위(COM-PG-004)가 서버에서 직접 수행한다. 같은 통제를 두 곳에
-- 적으면 어느 쪽이 실제로 막는지 알 수 없게 된다.
--
-- 번호는 P011 로 이어 붙인다. 지워진 P007 · P008 을 다시 쓰면 감사로그에
-- 남은 옛 정책과 구분되지 않는다.
-- ============================================================================

INSERT INTO tb_policy (
    policy_id, policy_name, role_seq, perm_seq, policy_type, enforce_level,
    condition_expr, target_field, message, alt_process,
    limit_amount, limit_qty, remark, use_yn, created_by
)
SELECT 'P011',
       '센터 재고조정 승인 한도',
       r.role_seq,
       p.perm_seq,
       'LIMIT',
       'APPROVAL',
       'adjust.amount <= 5000000 && adjust.qty <= 1000',
       '재고조정 금액·수량',
       '재고조정 한도(500만원 / 1,000EA)를 초과했습니다. 본사 승인이 필요합니다.',
       '본사 기준정보 담당 승인 요청',
       5000000,
       1000,
       'STK-009 임계값 초과 조정은 승인 필요',
       'Y',
       'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'CENTER_MGR'
   AND p.perm_id = 'INV_ADJ_APPROVE';
