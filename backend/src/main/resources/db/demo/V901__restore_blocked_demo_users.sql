-- ============================================================================
-- V901 : 로그인 차단 데모 계정 복구 (로컬 개발 전용)  PostgreSQL
--
--   V7 이 오프라인 매장을 걷어내면서 매장 소속 데모 계정 4명이 함께 사라졌다.
--   그중 두 계정이 로그인 차단 사유를 보여주는 유일한 예시였다.
--
--     st2.stf1 — 비밀번호 5회 오류로 잠긴 상태
--     st3.stf1 — 사용중지 + 휴면 상태
--
--   이 상태를 눈으로 확인할 계정이 없으면 잠금 해제 · 휴면 안내 화면을 시연할
--   수도, 검증할 수도 없다. 물류센터 소속으로 같은 상태를 다시 만든다.
--
--   왜 V7 이 아니라 여기인가 — 데모 계정은 db/demo 에 둔다. 이 폴더는 local
--   프로파일에서만 Flyway locations 에 포함되므로 dev · prod 에는 배포되지 않는다.
--   V900 보다 뒤 번호여야 V900 이 만든 계정과 섞이지 않는다.
--
--   비밀번호는 다른 데모 계정과 같은 'wms1234!' (BCrypt strength 10).
-- ============================================================================

INSERT INTO tb_user (user_id, user_name, password_hash, org_seq, email, phone,
                     dept_name, position_name, status, approval_limit, login_fail_count,
                     last_login_at, use_yn, created_by)
SELECT v.user_id, v.user_name,
       '$2a$10$ncjMxbbiOKNZRwCLqc/ROu.IMxzplGB6tD8vc5pzaNPcDVdMcnOkC',
       o.org_seq, v.email, v.phone,
       v.dept_name, v.position_name, v.status, v.approval_limit, v.login_fail_count,
       v.last_login_at, v.use_yn, 'system'
  FROM (VALUES
        -- 실패 한도를 넘겨 잠긴 계정. 올바른 비밀번호로도 로그인되지 않는다.
        ('dc2.in01', '남잠김', 'DC002', 'dc2in01@corp.co.kr', '010-1111-2222',
         '김해센터 입고파트', '사원', 'LOCKED',  0::bigint, 5,
         TIMESTAMP '2026-08-21 13:44:00', 'Y'),
        -- 장기 미접속으로 휴면 전환 + 사용중지
        ('dc2.pp01', '윤휴면', 'DC002', 'dc2pp01@corp.co.kr', '010-3333-4444',
         '김해센터 출고파트', '사원', 'DORMANT',         0, 0,
         TIMESTAMP '2026-05-19 11:02:00', 'N')
       ) AS v(user_id, user_name, org_id, email, phone, dept_name, position_name,
              status, approval_limit, login_fail_count, last_login_at, use_yn)
  JOIN tb_org o ON o.org_id = v.org_id;

-- 역할은 센터 작업자로 둔다. 차단 사유를 보는 것이 목적이라 권한 구성은 단순하게.
INSERT INTO tb_user_role (user_seq, role_seq, created_by)
SELECT u.user_seq, r.role_seq, 'system'
  FROM (VALUES
        ('dc2.in01', 'INBOUND_WORKER'),
        ('dc2.pp01', 'PICK_PACK')
       ) AS v(user_id, role_id)
  JOIN tb_user u ON u.user_id = v.user_id
  JOIN tb_role r ON r.role_id = v.role_id;
