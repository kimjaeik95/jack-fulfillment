-- ============================================================================
-- 재고 B · C · D 섹터 데모 — 재고 담당자 역할과 계정
--
-- 재고를 옮기고 · 조정을 올리고 · 실사를 돌리는 사람이 데모에 없었다.
-- 센터 관리자(CENTER_MGR)는 승인만 하는 역할이라 그 사람이 요청까지 하면
-- 자기 요청을 자기가 승인하게 된다 — 검증하려는 규칙을 검증할 수 없다.
--
-- CENTER_MGR 의 restriction_summary 가 '자기 요청 자기 승인 금지 권고' 인
-- 것이 이 역할이 필요한 이유 그대로다. 요청자와 승인자가 따로 있어야
-- 그 규칙이 무엇을 막는지 화면에서 보인다.
--
-- 재고 담당자에게 승인 권한을 주지 않는다. 주면 역할이 둘로 나뉜 의미가
-- 없어지고, 서비스의 '요청자는 자기 요청을 승인할 수 없다' 검사가 실제로
-- 무엇을 막는지 아무도 확인하지 못한다.
-- ============================================================================
INSERT INTO tb_role (role_id, role_name, description, org_scope, default_data_scope,
                     restriction_summary, sort_order, created_by)
VALUES
  ('INV_MANAGER', '재고 담당자',
   '재고 이동·판매불가 전환, 조정 요청, 실사 계획·입력',
   'DC', 'OWN_ORG', '조정·실사 마감은 센터 관리자 승인 필요', 45, 'system');

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 이동 · 상태전환은 즉시 반영. 총량이 그대로라 승인을 거치지 않는다.
        ('INV_MANAGER', 'INV_MOVE',   'RC'),
        -- 조정은 올리고 고치고 거둬들인다. 승인은 못 한다.
        ('INV_MANAGER', 'INV_ADJUST', 'RCUD'),
        -- 실사는 계획하고 세어 넣는다. 마감(승인)은 못 한다.
        ('INV_MANAGER', 'INV_COUNT',  'RCUD'),
        -- 재고를 못 보면 무엇을 옮길지 정할 수 없다. 대사 화면도 이 권한이다.
        ('INV_MANAGER', 'QRY_STOCK',  'R'),
        -- 옮길 자리를 고르려면 빈 목록이 필요하다
        ('INV_MANAGER', 'MST_WAREHOUSE', 'R'),
        ('INV_MANAGER', 'MST_LOCATION',  'R'),

        -- 센터 관리자도 조정을 올리고 실사를 돌린다. 승인만 하는 역할이
        -- 아니다 — 현장에서 차이를 먼저 발견하는 사람이 관리자인 경우가
        -- 흔하다.
        --
        -- 요청과 승인을 둘 다 가진 역할이 하나는 있어야 '자기 요청 자기
        -- 승인 금지' 가 실제로 무엇을 막는지 확인할 수 있다. 권한으로
        -- 막으면 그건 직무분리가 아니라 그냥 권한 부족이다.
        ('CENTER_MGR',  'INV_ADJUST', 'RCUD'),
        ('CENTER_MGR',  'INV_COUNT',  'RCUD'),
        -- 관리자도 현장에서 상태를 바꿔야 할 때가 있다
        ('CENTER_MGR',  'INV_MOVE',   'RC')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);


-- ============================================================================
-- 계정 — 이천센터에 한 명, 김해센터에 한 명.
--
-- 두 센터에 각각 두는 이유는 데이터 범위 때문이다. 한 명만 두면 '이천
-- 담당자가 김해 재고를 조정할 수 없다' 를 확인할 상대가 없다.
--
-- 비밀번호는 다른 데모 계정과 같은 wms1234! 다 (V900 과 같은 해시).
-- 로컬 전용이며 dev · prod 프로파일은 db/demo 를 적용하지 않는다.
-- ============================================================================
INSERT INTO tb_user (user_id, user_name, password_hash, org_seq, email, phone,
                     dept_name, position_name, status, approval_limit, login_fail_count,
                     last_login_at, must_change_password, use_yn, created_by)
SELECT v.user_id, v.user_name,
       '$2a$10$ncjMxbbiOKNZRwCLqc/ROu.IMxzplGB6tD8vc5pzaNPcDVdMcnOkC',
       o.org_seq, v.email, v.phone,
       v.dept_name, v.position_name, 'ACTIVE', 0, 0,
       v.last_login_at, 'N', 'Y', 'system'
  FROM (VALUES
        ('dc1.inv01', '강재고', 'DC001', 'dc1inv01@corp.co.kr', '010-8888-9999',
         '이천센터 재고파트', '대리', TIMESTAMP '2026-09-08 08:10:00'),
        ('dc2.inv01', '서재고', 'DC002', 'dc2inv01@corp.co.kr', '010-9999-0000',
         '김해센터 재고파트', '대리', TIMESTAMP '2026-09-08 08:12:00')
       ) AS v(user_id, user_name, org_id, email, phone, dept_name, position_name, last_login_at)
  JOIN tb_org o ON o.org_id = v.org_id;

INSERT INTO tb_user_role (user_seq, role_seq, created_by)
SELECT u.user_seq, r.role_seq, 'system'
  FROM (VALUES
        ('dc1.inv01', 'INV_MANAGER'),
        ('dc2.inv01', 'INV_MANAGER')
       ) AS v(user_id, role_id)
  JOIN tb_user u ON u.user_id = v.user_id
  JOIN tb_role r ON r.role_id = v.role_id;
