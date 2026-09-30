-- ============================================================================
-- REASON_CORRECT 를 사유코드 쪽으로 옮긴다
--
-- 사유인데 공통코드 칸에 앉아 있었다. V11 이 group_kind 를 안 적어서 기본값
-- 'SYSTEM' 으로 들어갔다 — 컬럼은 V1 부터 있었고, 앞뒤 마이그레이션(V10 ·
-- V13 · V19 · V20 · V21)은 모두 명시한다. V11 만 빠뜨렸다.
--
--   REASON_ADJUST · REASON_CANCEL · REASON_RETURN …   REASON   (11개)
--   REASON_CORRECT                                     SYSTEM   ← 이것만
--
--
-- 【 왜 이게 문제인가 】
--
-- 두 화면을 가른 이유가 권한이다. 공통코드에는 DATA_SCOPE · PERM_ACTION ·
-- POLICY_TYPE 처럼 <b>건드리면 권한 판정이 깨지는</b> 것들이 있어서,
-- SYS_CODE 는 시스템 관리자만 갖는다.
--
-- 그 바람에 '검수 착오' 같은 사유 하나를 고치려면 시스템 관리자여야 했다.
-- 본사 기준정보 담당(MST_REASON CDRU 보유)도 손댈 수 없었다.
--
--   사유를 고치려고 데이터범위 코드를 지울 수 있는 권한을 받는 것
--
-- 이 사유코드를 따로 뺀 이유와 정확히 반대되는 모양이다.
--
--
-- 【 깨질 것 】
--
-- 없다. group_kind 는 두 화면이 자기 것만 거르는 데만 쓰인다. 코드값
-- (SHORT_SHIP · OVER_SHIP · MISCOUNT · TYPO · DOUBLE)은 그대로라
-- CorrectService 가 읽는 값도 그대로다.
--
-- 같이 들어온 CORRECT_STATUS 는 상태 코드라 SYSTEM 이 맞다. 건드리지 않는다.
-- ============================================================================
UPDATE tb_code_group
   SET group_kind = 'REASON',
       updated_by = 'system',
       updated_at = CURRENT_TIMESTAMP
 WHERE code_group_id = 'REASON_CORRECT'
   AND group_kind    = 'SYSTEM';
