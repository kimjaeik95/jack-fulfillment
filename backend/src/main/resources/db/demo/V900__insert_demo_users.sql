-- ============================================================================
-- V900 : 데모 사용자 (로컬 개발 전용)  PostgreSQL
--
--   이 폴더(db/demo)는 local 프로파일에서만 Flyway locations 에 포함된다.
--   dev · prod 프로파일에는 적용되지 않으므로 데모 계정이 배포되지 않는다.
--
--   비밀번호는 전부 'wms1234!' (BCrypt strength 10).
--   역할별 화면 동작과 로그인 차단 사유를 확인하기 위한 계정들이다.
--     st2.stf1 — 비밀번호 5회 오류로 잠긴 상태
--     st3.stf1 — 사용중지 + 휴면 상태
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
        ('hq.master', '이기준', 'HQ001', 'master@corp.co.kr',  '010-2222-3333', '상품기획팀',        '과장',   'ACTIVE',        0::bigint, 0, TIMESTAMP '2026-09-08 08:40:00', 'Y'),
        ('buyer01',   '박구매', 'HQ001', 'buyer01@corp.co.kr', '010-3333-4444', '구매팀',            '대리',   'ACTIVE',  50000000,   0, TIMESTAMP '2026-09-07 17:55:00', 'Y'),
        ('dc1.mgr',   '최센터', 'DC001', 'dc1mgr@corp.co.kr',  '010-4444-5555', '이천센터 운영팀',   '센터장', 'ACTIVE',  20000000,   0, TIMESTAMP '2026-09-08 07:30:00', 'Y'),
        ('dc1.in01',  '정입고', 'DC001', 'dc1in01@corp.co.kr', '010-5555-6666', '이천센터 입고파트', '사원',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-08 06:58:00', 'Y'),
        ('dc1.pp01',  '한피킹', 'DC001', 'dc1pp01@corp.co.kr', '010-6666-7777', '이천센터 출고파트', '사원',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-08 06:55:00', 'Y'),
        ('dc2.mgr',   '오김해', 'DC002', 'dc2mgr@corp.co.kr',  '010-7777-8888', '김해센터 운영팀',   '센터장', 'ACTIVE',  20000000,   0, TIMESTAMP '2026-09-05 18:20:00', 'Y'),
        ('st1.mgr',   '강강남', 'ST001', 'st1mgr@corp.co.kr',  '010-8888-9999', '강남점',            '점장',   'ACTIVE',   5000000,   0, TIMESTAMP '2026-09-08 10:02:00', 'Y'),
        ('st1.stf1',  '윤직원', 'ST001', 'st1stf1@corp.co.kr', '010-9999-0000', '강남점',            '사원',   'ACTIVE',   1000000,   0, TIMESTAMP '2026-09-08 10:10:00', 'Y'),
        ('st2.stf1',  '서판교', 'ST002', 'st2stf1@corp.co.kr', '010-1212-3434', '판교점',            '사원',   'LOCKED',   1000000,   5, TIMESTAMP '2026-08-21 13:44:00', 'Y'),
        ('cs01',      '문상담', 'HQ001', 'cs01@corp.co.kr',    '010-2323-4545', 'CS팀',              '사원',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-08 09:31:00', 'Y'),
        ('audit01',   '노감사', 'HQ001', 'audit01@corp.co.kr', '010-3434-5656', '감사팀',            '차장',   'ACTIVE',         0,   0, TIMESTAMP '2026-09-04 15:10:00', 'Y'),
        ('st3.stf1',  '배해운', 'ST003', 'st3stf1@corp.co.kr', '010-4545-6767', '해운대점',          '사원',   'DORMANT',  1000000,   0, TIMESTAMP '2026-05-19 11:02:00', 'N')
       ) AS v(user_id, user_name, org_id, email, phone, dept_name, position_name,
              status, approval_limit, login_fail_count, last_login_at, use_yn)
  JOIN tb_org o ON o.org_id = v.org_id;


-- 역할 배정 — 오김해는 센터 관리자 + 피킹/패킹 두 역할을 갖는다
INSERT INTO tb_user_role (user_seq, role_seq, created_by)
SELECT u.user_seq, r.role_seq, 'system'
  FROM (VALUES
        ('hq.master', 'HQ_MASTER'),
        ('buyer01',   'PURCHASER'),
        ('dc1.mgr',   'CENTER_MGR'),
        ('dc1.in01',  'INBOUND_WORKER'),
        ('dc1.pp01',  'PICK_PACK'),
        ('dc2.mgr',   'CENTER_MGR'),
        ('dc2.mgr',   'PICK_PACK'),
        ('st1.mgr',   'STORE_MGR'),
        ('st1.stf1',  'STORE_STAFF'),
        ('st2.stf1',  'STORE_STAFF'),
        ('cs01',      'CS_VIEWER'),
        ('audit01',   'AUDITOR'),
        ('st3.stf1',  'STORE_STAFF')
       ) AS v(user_id, role_id)
  JOIN tb_user u ON u.user_id = v.user_id
  JOIN tb_role r ON r.role_id = v.role_id;
