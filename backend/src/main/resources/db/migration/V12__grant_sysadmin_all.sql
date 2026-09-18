-- ============================================================================
-- 시스템관리자를 마스터로 (COM-PG-003)
--
-- 지금까지 SYS_ADMIN 은 50 개 권한 중 21 개만 갖고 있었다. 문제는 개수가
-- 아니라 <b>일관성이 없었다</b>는 것이다.
--
--   MST_PLANT · WAREHOUSE · LOCATION   CDRUX  (다 됨)
--   MST_SKU · PRODUCT · BRAND · ...    R      (보기만)
--   SYS_POLICY                         없음   (승인정책 화면에 못 들어감)
--   MST_VENDOR · MST_PRICE · QRY_*     없음
--
-- 플랜트는 만들 수 있는데 SKU 는 못 만드는 것은 설명할 수 없고, 시스템관리자가
-- 승인정책을 못 만지는 것은 그냥 빠뜨린 것이다. 시연에서도 "관리자인데 창고가
-- 안 보인다" 로 걸렸다.
--
-- 그래서 <b>정의된 모든 권한의 모든 액션</b>을 준다. 데이터 범위는 이미
-- ALL 이다 (tb_role.default_data_scope) — 권한만 비어 있었다.
--
-- ----------------------------------------------------------------------------
-- 승인(A)까지 주는 것의 대가
--
-- 사용자가 '진짜 마스터' 를 골랐다. 그 선택의 결과를 여기 적어 둔다.
--
-- 이 시스템의 통제 축은 둘이다 — 감사로그와 '자기 요청 자기 승인 금지'(SoD).
-- SYS_ADMIN 이 요청 권한과 승인 권한을 다 가지면 <b>한 계정이 양쪽에 설 수
-- 있다</b>. 역할로 나눠 둔 통제가 이 계정에서는 성립하지 않는다.
--
-- 다만 SoD 검사 자체는 코드에 그대로 남는다 (AdjustService · CorrectService 의
-- requireNotSelfApproval). 그래서 관리자도 <b>자기가 올린 건은 여전히 자기가
-- 승인하지 못한다.</b> 남이 올린 건을 대신 승인할 수 있을 뿐이다.
--
-- 통제를 되살리고 싶으면 두 가지 길이 있다.
--   1 이 마이그레이션의 승인 액션만 되돌린다 (아래 되돌리기 참고)
--   2 tb_policy 에 평가 엔진을 만들어 "관리자의 승인은 사후 결재 대상" 같은
--     규칙을 건다 — 지금은 엔진이 없어 정책이 아무것도 막지 못한다
--
-- 되돌리기 (승인 액션만):
--   DELETE FROM tb_role_permission
--    WHERE created_by = 'v17.sysadmin' AND action_code = 'A';
-- 되돌리기 (전체):
--   DELETE FROM tb_role_permission WHERE created_by = 'v17.sysadmin';
-- ============================================================================

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, a.perm_seq, a.action_code, NULL, 'v17.sysadmin'
  FROM tb_role r
  JOIN tb_permission_action a ON TRUE
 WHERE r.role_id = 'SYS_ADMIN'
   -- 이미 갖고 있는 것은 건드리지 않는다. 기존 행의 created_by 를 덮으면
   -- 위의 '되돌리기' 가 원래 있던 권한까지 지운다.
   AND NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq
          AND x.perm_seq = a.perm_seq
          AND x.action_code = a.action_code);

-- ============================================================================
-- 확인 — 손으로 돌려 보는 질의다. 마이그레이션에는 넣지 않는다.
--
-- psql 의 \echo 를 여기 썼다가 기동이 죽었다. Flyway 는 파일을 JDBC 로
-- 그대로 보내므로 psql 전용 명령을 모른다 — 확인용 출력은 주석으로만 둔다.
--
--   -- SYS_ADMIN 이 못 가진 액션 (0 이어야 한다)
--   SELECT COUNT(*) FROM tb_permission_action a
--    WHERE NOT EXISTS (SELECT 1 FROM tb_role_permission rp
--                        JOIN tb_role r ON r.role_seq = rp.role_seq
--                       WHERE rp.perm_seq = a.perm_seq
--                         AND rp.action_code = a.action_code
--                         AND r.role_id = 'SYS_ADMIN');
--
--   -- 이번에 더해진 것
--   SELECT p.module_code, COUNT(DISTINCT p.perm_id), COUNT(*)
--     FROM tb_role_permission rp JOIN tb_permission p ON p.perm_seq = rp.perm_seq
--    WHERE rp.created_by = 'v17.sysadmin' GROUP BY 1 ORDER BY 1;
-- ============================================================================
