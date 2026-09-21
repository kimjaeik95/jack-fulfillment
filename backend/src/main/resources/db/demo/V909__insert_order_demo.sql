-- ============================================================================
-- 주문관리자 역할과 데모 사용자 (로컬 전용)
--
-- 3차에서 주문이 생기며 새로 필요해진 역할이다. 프로그램 목록이 ORD-PG-001
-- ~ 012 의 담당자를 '주문관리자' 로 적고 있는데, 기존 아홉 역할 중에는 그
-- 자리가 없었다.
--
-- CS_VIEWER 로 겸하지 않는 이유는 성격이 다르기 때문이다. CS 는 고객 문의에
-- 답하려고 조회만 하고, 주문관리자는 주문을 만들고 확정하고 할당한다. 한
-- 역할로 묶으면 조회만 하면 되는 사람에게 할당 권한까지 가게 된다.
--
-- org_scope 는 HQ 다. 주문은 채널 · 고객에 붙어 있어 특정 센터의 것이
-- 아니다 — 어느 센터에서 내보낼지는 할당이 정한다.
-- ============================================================================

INSERT INTO tb_role (role_id, role_name, description, org_scope, default_data_scope,
                     restriction_summary, sort_order, created_by)
VALUES
  ('ORDER_MGR', '주문 관리자', '주문 등록·확정·취소, 재고할당, 결품 처리',
   'HQ', 'ALL', '출고 확정 이후 취소 불가 — 반품으로 처리', 35, 'system');

-- 주문은 만들고 고치고 지운다. 출력(X)은 주문서 인쇄용이다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        ('ORDER_MGR', 'ORD_ORDER', 'RCUDX'),
        -- 할당은 잡고(C) 푸는(D) 것이다. 고치는 개념이 없다.
        ('ORDER_MGR', 'ORD_ALLOC', 'RCD')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

-- 주문을 넣으려면 거래처 · SKU · 재고를 볼 수 있어야 한다. 고르는 화면이
-- 그 목록을 부르기 때문이다 — 읽기만 준다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'ORDER_MGR'
   AND p.perm_id IN ('MST_PARTNER', 'MST_SKU', 'MST_PRODUCT', 'MST_CHANNEL',
                     'MST_CHANNEL_SKU', 'MST_PLANT', 'MST_WAREHOUSE', 'MST_LOCATION',
                     'QRY_STOCK', 'MST_REASON');

-- CS 도 주문을 읽어야 한다. 고객 문의의 대부분이 "내 주문 어디까지 갔나" 다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'CS_VIEWER'
   AND p.perm_id IN ('ORD_ORDER', 'ORD_ALLOC');

-- 센터 관리자도 읽는다. 어떤 주문이 우리 센터로 떨어질지 봐야 한다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'CENTER_MGR'
   AND p.perm_id IN ('ORD_ORDER', 'ORD_ALLOC');


-- ----------------------------------------------------------------------------
-- 데모 사용자
--
-- 비밀번호는 다른 데모 계정과 같다 (wms1234!). 로컬 전용이라 공유한다.
--
-- must_change_password 는 'N' 이다. 데모 계정은 바로 로그인해서 화면을 보는
-- 용도이고, 매번 변경 화면을 거치면 확인이 번거롭다 (V900 과 같은 판단).
-- ----------------------------------------------------------------------------
INSERT INTO tb_user (user_id, user_name, password_hash, org_seq, email, phone,
                     dept_name, position_name, status, approval_limit,
                     login_fail_count, last_login_at, must_change_password,
                     use_yn, created_by)
SELECT v.user_id, v.user_name,
       '$2a$10$ncjMxbbiOKNZRwCLqc/ROu.IMxzplGB6tD8vc5pzaNPcDVdMcnOkC',
       o.org_seq, v.email, v.phone, v.dept_name, v.position_name,
       v.status, v.approval_limit, 0, v.last_login_at, 'N', 'Y', 'system'
  FROM (VALUES
        ('ord01', '배주문', 'HQ001', 'ord01@corp.co.kr', '010-5555-6666',
         '주문운영팀', '대리', 'ACTIVE', 0, TIMESTAMP '2026-09-20 10:15:00')
       ) AS v(user_id, user_name, org_id, email, phone,
              dept_name, position_name, status, approval_limit, last_login_at)
  JOIN tb_org o ON o.org_id = v.org_id;

INSERT INTO tb_user_role (user_seq, role_seq, created_by)
SELECT u.user_seq, r.role_seq, 'system'
  FROM tb_user u
 CROSS JOIN tb_role r
 WHERE u.user_id = 'ord01' AND r.role_id = 'ORDER_MGR';
