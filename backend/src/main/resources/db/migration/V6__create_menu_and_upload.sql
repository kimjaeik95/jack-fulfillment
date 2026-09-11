-- ============================================================================
-- V6 : 메뉴 관리(COM-PG-005) · 엑셀 업로드 관리(COM-PG-010)
--
-- 메뉴
--   지금까지 사이드바 메뉴는 프론트 routes.js 에 하드코딩되어 있었다. 그러면
--   "역할별 메뉴 구성"을 바꿀 때마다 배포해야 한다. 메뉴를 데이터로 옮겨
--   화면에서 순서 · 노출 · 필요권한을 관리한다.
--
--   라우트 자체(어떤 컴포넌트를 그릴지)는 코드가 계속 소유한다. 메뉴는
--   "그 라우트를 사이드바 어디에 어떤 이름으로 걸지"만 정한다.
--
-- 업로드 이력
--   대량 등록은 부분성공을 허용한다(CMN-004). 그러면 "몇 건 중 몇 건이
--   들어갔고 어느 행이 왜 실패했는지"가 남아야 사용자가 고쳐서 다시 올린다.
--   실패 행은 원문 그대로 보관해 오류 CSV 로 되돌려준다.
--
-- 다운로드 이력은 별도 테이블을 두지 않는다. tb_audit_log 의 DOWNLOAD 행위로
-- 이미 남고 있고, 같은 성격의 기록을 두 곳에 두면 반드시 한쪽이 낡는다.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 메뉴
-- ----------------------------------------------------------------------------
CREATE TABLE tb_menu (
    menu_seq     bigint       GENERATED ALWAYS AS IDENTITY,
    menu_id      varchar(30)  NOT NULL,
    menu_name    varchar(100) NOT NULL,
    parent_seq   bigint,
    route_name   varchar(50),
    icon         varchar(10),
    perm_seq     bigint,
    sort_order   integer      NOT NULL DEFAULT 0,
    use_yn       char(1)      NOT NULL DEFAULT 'Y',
    created_by   varchar(30)  NOT NULL,
    created_at   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   varchar(30),
    updated_at   timestamp,
    CONSTRAINT pk_menu            PRIMARY KEY (menu_seq),
    CONSTRAINT uk_menu_id         UNIQUE (menu_id),
    CONSTRAINT fk_menu_parent     FOREIGN KEY (parent_seq) REFERENCES tb_menu (menu_seq),
    CONSTRAINT fk_menu_perm       FOREIGN KEY (perm_seq)   REFERENCES tb_permission (perm_seq),
    CONSTRAINT ck_menu_use_yn     CHECK (use_yn IN ('Y', 'N')),
    -- 최상위는 그룹 머리글이라 라우트가 없고, 하위는 반드시 이동할 화면이 있어야 한다.
    CONSTRAINT ck_menu_route      CHECK ((parent_seq IS NULL) = (route_name IS NULL)),
    -- 자기 자신을 부모로 지정할 수 없다 (2단 구조라 그 이상의 순환은 생기지 않는다)
    CONSTRAINT ck_menu_not_self   CHECK (parent_seq IS NULL OR parent_seq <> menu_seq)
);

CREATE INDEX ix_menu_parent ON tb_menu (parent_seq, sort_order);
-- 같은 라우트를 두 메뉴가 가리키면 어느 쪽이 활성인지 알 수 없다.
CREATE UNIQUE INDEX ux_menu_route ON tb_menu (route_name) WHERE route_name IS NOT NULL;

COMMENT ON TABLE  tb_menu            IS '사이드바 메뉴 (COM-PG-005)';
COMMENT ON COLUMN tb_menu.menu_seq   IS '메뉴 순번 (PK)';
COMMENT ON COLUMN tb_menu.menu_id    IS '메뉴코드 (예: SYS_USERS)';
COMMENT ON COLUMN tb_menu.parent_seq IS '상위 메뉴. NULL 이면 그룹 머리글';
COMMENT ON COLUMN tb_menu.route_name IS '프론트 라우트 이름. 그룹 머리글은 NULL';
COMMENT ON COLUMN tb_menu.perm_seq   IS '노출에 필요한 권한. NULL 이면 로그인만 하면 보인다';
COMMENT ON COLUMN tb_menu.icon       IS '사이드바 아이콘 (이모지 1~2자)';


-- ----------------------------------------------------------------------------
-- 업로드 이력
-- ----------------------------------------------------------------------------
CREATE TABLE tb_upload_history (
    upload_seq    bigint       GENERATED ALWAYS AS IDENTITY,
    target_type   varchar(30)  NOT NULL,
    file_name     varchar(255) NOT NULL,
    total_count   integer      NOT NULL DEFAULT 0,
    success_count integer      NOT NULL DEFAULT 0,
    fail_count    integer      NOT NULL DEFAULT 0,
    status        varchar(20)  NOT NULL,
    message       varchar(500),
    uploaded_by   varchar(30)  NOT NULL,
    uploaded_at   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_upload_history     PRIMARY KEY (upload_seq),
    CONSTRAINT ck_upload_counts      CHECK (success_count + fail_count = total_count)
);

CREATE INDEX ix_upload_history_at ON tb_upload_history (uploaded_at DESC);

COMMENT ON TABLE  tb_upload_history             IS '대량 업로드 이력 (COM-PG-010)';
COMMENT ON COLUMN tb_upload_history.target_type IS '업로드 대상 — 코드그룹 UPLOAD_TARGET';
COMMENT ON COLUMN tb_upload_history.status      IS '처리 결과 — 코드그룹 UPLOAD_STATUS';
COMMENT ON COLUMN tb_upload_history.message     IS '전체 실패 시의 사유 (형식 오류 등)';

-- 실패 행. 사용자가 이 행만 내려받아 고친 뒤 다시 올린다.
CREATE TABLE tb_upload_error (
    upload_seq  bigint       NOT NULL,
    row_no      integer      NOT NULL,
    column_name varchar(100),
    message     varchar(500) NOT NULL,
    raw_line    text         NOT NULL,
    CONSTRAINT pk_upload_error    PRIMARY KEY (upload_seq, row_no),
    CONSTRAINT fk_upload_error_up FOREIGN KEY (upload_seq)
        REFERENCES tb_upload_history (upload_seq) ON DELETE CASCADE
);

COMMENT ON TABLE  tb_upload_error             IS '업로드 실패 행 (오류 CSV 재다운로드용)';
COMMENT ON COLUMN tb_upload_error.row_no      IS '파일 기준 행 번호 (머리글 제외, 1부터)';
COMMENT ON COLUMN tb_upload_error.raw_line    IS '실패한 행의 원문. 고쳐서 재업로드할 수 있게 그대로 보관';


-- ----------------------------------------------------------------------------
-- 공통코드
-- ----------------------------------------------------------------------------
INSERT INTO tb_code_group (code_group_id, code_group_name, description, created_by) VALUES
  ('UPLOAD_TARGET', '업로드 대상', '대량 등록을 지원하는 데이터 종류', 'system'),
  ('UPLOAD_STATUS', '업로드 결과', '대량 등록 처리 결과',             'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        ('UPLOAD_TARGET', 'ORG',        '조직',       '본사 · 물류센터 · 매장',        'blue',   10),
        ('UPLOAD_TARGET', 'PERMISSION', '권한',       '기능 권한과 허용 액션',         'amber',  20),
        ('UPLOAD_TARGET', 'CODE',       '공통코드',   '코드그룹 하위의 코드값',        'teal',   30),

        ('UPLOAD_STATUS', 'SUCCESS',    '전건 성공', '모든 행이 반영되었다',           'green',  10),
        ('UPLOAD_STATUS', 'PARTIAL',    '부분 성공', '정상 행만 반영하고 오류 행은 남겼다', 'amber', 20),
        ('UPLOAD_STATUS', 'FAILED',     '전건 실패', '반영된 행이 없다',               'red',    30)
       ) AS v(code_group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.code_group_id;


-- ----------------------------------------------------------------------------
-- 메뉴 관리 권한
--
-- 메뉴를 숨기거나 지울 수 있으므로 역할·권한(SYS_ROLE)과 같은 급으로 다룬다.
-- 시스템 관리자에게만 부여한다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by) VALUES
  ('SYS_MENU', '메뉴 관리', 'SYS', '시스템 > 메뉴관리', 35, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES ('SYS_MENU', 'RCUD')) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES ('SYS_ADMIN', 'SYS_MENU', 'RCUD')) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code);


-- ----------------------------------------------------------------------------
-- 다운로드 액션 (COM-PG-011)
--
-- 조회할 수 있다고 내려받아도 되는 것은 아니다. 파일로 나간 데이터는 회수할 수
-- 없으므로, 감사 다운로드(AUD_DOWNLOAD/X)와 같은 방식으로 목록 다운로드도
-- 별도 액션 X 로 분리해 역할별로 통제한다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, 'X', 'system'
  FROM tb_permission p
 WHERE p.perm_id IN ('SYS_COMPANY', 'SYS_USER', 'SYS_ROLE', 'SYS_CODE', 'SYS_POLICY');

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'X', 'system'
  FROM tb_role r
 CROSS JOIN tb_permission p
 WHERE r.role_id = 'SYS_ADMIN'
   AND p.perm_id IN ('SYS_COMPANY', 'SYS_USER', 'SYS_ROLE', 'SYS_CODE', 'SYS_POLICY');


-- ----------------------------------------------------------------------------
-- 메뉴 시드 — 지금 routes.js 에 하드코딩되어 있는 구성을 그대로 옮긴다.
--
-- 그룹을 먼저 넣고, 항목은 그룹의 menu_id 로 되짚어 parent_seq 를 채운다.
-- ----------------------------------------------------------------------------
INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by) VALUES
  ('GRP_STATUS', '현황',        NULL, NULL, NULL, NULL, 10, 'system'),
  ('GRP_USER',   '사용자 · 조직', NULL, NULL, NULL, NULL, 20, 'system'),
  ('GRP_AUTH',   '권한',        NULL, NULL, NULL, NULL, 30, 'system'),
  ('GRP_COMMON', '공통 정책',    NULL, NULL, NULL, NULL, 40, 'system'),
  ('GRP_AUDIT',  '감사',        NULL, NULL, NULL, NULL, 50, 'system');

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('SYS_DASHBOARD', '권한 현황',       'GRP_STATUS', 'dashboard',        '◎',  'SYS_ROLE',    10),
        ('SYS_USERS',     '사용자 관리',     'GRP_USER',   'users',            '👤', 'SYS_USER',    10),
        ('SYS_ORGS',      '조직 관리',       'GRP_USER',   'orgs',             '🏢', 'SYS_COMPANY', 20),
        ('SYS_ROLES',     '역할 관리',       'GRP_AUTH',   'roles',            '🎫', 'SYS_ROLE',    10),
        ('SYS_PERMS',     '권한 관리',       'GRP_AUTH',   'permissions',      '🔑', 'SYS_ROLE',    20),
        ('SYS_ROLEPERMS', '역할-권한 매핑',  'GRP_AUTH',   'role-permissions', '▦',  'SYS_ROLE',    30),
        ('SYS_MENUS',     '메뉴 관리',       'GRP_AUTH',   'menus',            '🗂', 'SYS_MENU',    40),
        ('SYS_POLICIES',  '공통정책 관리',   'GRP_COMMON', 'policies',         '⚖',  'SYS_POLICY',  10),
        ('SYS_CODES',     '공통코드',        'GRP_COMMON', 'codes',            '☰',  'SYS_CODE',    20),
        -- 업로드 이력은 권한을 걸지 않는다. 자기가 올린 결과는 누구나 봐야 하고,
        -- 남의 것까지 보려면 변경 이력 조회 권한(AUD_HISTORY/R)이 필요하다 —
        -- 그 판정은 서비스가 한다.
        ('SYS_UPLOADS',   '업로드 이력',     'GRP_COMMON', 'uploads',          '📤', NULL,          30),
        ('SYS_AUDITLOGS', '변경 이력',       'GRP_AUDIT',  'audit-logs',       '📜', 'AUD_HISTORY', 10)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g            ON g.menu_id = v.parent_id
  LEFT JOIN tb_permission p ON p.perm_id = v.perm_id;
