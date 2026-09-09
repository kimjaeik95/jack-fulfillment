-- ============================================================================
-- V1 : system 영역 스키마  (PostgreSQL)
--      사용자 · 조직 · 역할 · 권한 · 공통정책 · 공통코드 · 감사로그
--
-- 설계 규칙
--   1) PK 는 대리키(*_seq, GENERATED ALWAYS AS IDENTITY). 업무코드는 UNIQUE.
--      ALWAYS 로 두어 애플리케이션이 시퀀스 값을 직접 넣는 실수를 막는다.
--   2) 테이블명은 tb_ 접두어. user / role 같은 예약어 충돌을 피한다.
--   3) 식별자는 따옴표로 감싸지 않는다. PostgreSQL 은 소문자로 정규화한다.
--   4) 코드성 컬럼(status, org_type, policy_type ...)은 tb_code 를 참조하지만
--      FK 는 걸지 않는다. 코드 신규 추가 시 업무 데이터 입력이 막히는 것을 방지한다.
--      각 컬럼의 코드그룹은 COMMENT 에 명시한다.
--   5) 물리 삭제 금지 대상은 use_yn / status 로 비활성 처리한다.
--   6) 참조 삭제 정책
--        매핑(연결) 테이블 → ON DELETE CASCADE  (부모가 사라지면 연결도 무의미)
--        마스터 참조       → 기본 RESTRICT      (애플리케이션이 사유를 안내하며 차단)
--        감사로그의 사용자 → ON DELETE SET NULL (계정이 지워져도 이력은 보존)
--
-- 시각 컬럼은 timestamp(타임존 없음)를 쓴다. 단일 국가 운영을 전제로 한 선택이며,
-- 해외 거점이 생기면 timestamptz 로 전환해야 한다.
-- ============================================================================


-- ============================================================================
-- 1. 공통코드
-- ============================================================================

CREATE TABLE tb_code_group (
    code_group_seq   bigint       GENERATED ALWAYS AS IDENTITY,
    code_group_id    varchar(30)  NOT NULL,
    code_group_name  varchar(100) NOT NULL,
    description      varchar(300),
    use_yn           char(1)      NOT NULL DEFAULT 'Y',
    created_by       varchar(30)  NOT NULL,
    created_at       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       varchar(30),
    updated_at       timestamp,
    CONSTRAINT pk_code_group        PRIMARY KEY (code_group_seq),
    CONSTRAINT uk_code_group_id     UNIQUE (code_group_id),
    CONSTRAINT ck_code_group_use_yn CHECK (use_yn IN ('Y', 'N'))
);

COMMENT ON TABLE  tb_code_group                 IS '코드그룹';
COMMENT ON COLUMN tb_code_group.code_group_seq  IS '코드그룹 순번 (PK)';
COMMENT ON COLUMN tb_code_group.code_group_id   IS '코드그룹 ID (예: POLICY_TYPE)';
COMMENT ON COLUMN tb_code_group.code_group_name IS '코드그룹명';
COMMENT ON COLUMN tb_code_group.use_yn          IS '사용여부 Y/N';


CREATE TABLE tb_code (
    code_seq        bigint       GENERATED ALWAYS AS IDENTITY,
    code_group_seq  bigint       NOT NULL,
    code_id         varchar(30)  NOT NULL,
    code_name       varchar(100) NOT NULL,
    description     varchar(300),
    attr1           varchar(50),
    attr2           varchar(50),
    sort_order      integer      NOT NULL DEFAULT 0,
    use_yn          char(1)      NOT NULL DEFAULT 'Y',
    created_by      varchar(30)  NOT NULL,
    created_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      varchar(30),
    updated_at      timestamp,
    CONSTRAINT pk_code        PRIMARY KEY (code_seq),
    CONSTRAINT uk_code        UNIQUE (code_group_seq, code_id),
    CONSTRAINT fk_code_group  FOREIGN KEY (code_group_seq)
                              REFERENCES tb_code_group (code_group_seq) ON DELETE CASCADE,
    CONSTRAINT ck_code_use_yn CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_code_group_sort ON tb_code (code_group_seq, sort_order);

COMMENT ON TABLE  tb_code            IS '공통코드';
COMMENT ON COLUMN tb_code.code_seq   IS '코드 순번 (PK)';
COMMENT ON COLUMN tb_code.code_id    IS '코드값 (예: DENY, BLOCK, ACTIVE)';
COMMENT ON COLUMN tb_code.code_name  IS '코드명';
COMMENT ON COLUMN tb_code.attr1      IS '부가속성1 — 화면 배지 색상';
COMMENT ON COLUMN tb_code.attr2      IS '부가속성2 — 예비';
COMMENT ON COLUMN tb_code.sort_order IS '정렬순서';


-- ============================================================================
-- 2. 조직
--    최상위(회사) 아래에 센터 · 창고 · 매장이 계층으로 붙는다.
--    사용자의 소속이며 데이터 접근 범위 격리의 기준이 된다.
-- ============================================================================

CREATE TABLE tb_org (
    org_seq       bigint       GENERATED ALWAYS AS IDENTITY,
    org_id        varchar(20)  NOT NULL,
    org_name      varchar(100) NOT NULL,
    org_type      varchar(20)  NOT NULL,
    parent_seq    bigint,
    manager_name  varchar(50),
    phone         varchar(30),
    address       varchar(300),
    sort_order    integer      NOT NULL DEFAULT 0,
    use_yn        char(1)      NOT NULL DEFAULT 'Y',
    created_by    varchar(30)  NOT NULL,
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    varchar(30),
    updated_at    timestamp,
    CONSTRAINT pk_org         PRIMARY KEY (org_seq),
    CONSTRAINT uk_org_id      UNIQUE (org_id),
    CONSTRAINT uk_org_name    UNIQUE (org_name),
    -- 하위 조직이 있으면 삭제를 막는다 (애플리케이션이 사유를 안내한다)
    CONSTRAINT fk_org_parent  FOREIGN KEY (parent_seq) REFERENCES tb_org (org_seq),
    CONSTRAINT ck_org_use_yn  CHECK (use_yn IN ('Y', 'N')),
    -- 자기 자신을 상위로 지정할 수 없다
    CONSTRAINT ck_org_parent  CHECK (parent_seq IS NULL OR parent_seq <> org_seq)
);

CREATE INDEX ix_org_parent ON tb_org (parent_seq);
CREATE INDEX ix_org_type   ON tb_org (org_type);

COMMENT ON TABLE  tb_org              IS '조직 (회사 · 물류센터 · 창고 · 매장)';
COMMENT ON COLUMN tb_org.org_seq      IS '조직 순번 (PK)';
COMMENT ON COLUMN tb_org.org_id       IS '조직코드 (예: HQ001, DC001, ST001)';
COMMENT ON COLUMN tb_org.org_type     IS '조직유형 — 코드그룹 ORG_TYPE (HQ/DC/WAREHOUSE/STORE)';
COMMENT ON COLUMN tb_org.parent_seq   IS '상위 조직 순번. 최상위는 NULL';
COMMENT ON COLUMN tb_org.manager_name IS '조직 책임자명';


-- ============================================================================
-- 3. 사용자
--    물리 삭제 금지. 퇴사 · 중지는 status / use_yn 으로 처리한다.
-- ============================================================================

CREATE TABLE tb_user (
    user_seq             bigint       GENERATED ALWAYS AS IDENTITY,
    user_id              varchar(30)  NOT NULL,
    user_name            varchar(50)  NOT NULL,
    password_hash        varchar(100) NOT NULL,
    org_seq              bigint       NOT NULL,
    email                varchar(100),
    phone                varchar(30),
    dept_name            varchar(50),
    position_name        varchar(50),
    status               varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_limit       bigint       NOT NULL DEFAULT 0,
    login_fail_count     integer      NOT NULL DEFAULT 0,
    last_login_at        timestamp,
    password_changed_at  timestamp,
    use_yn               char(1)      NOT NULL DEFAULT 'Y',
    created_by           varchar(30)  NOT NULL,
    created_at           timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           varchar(30),
    updated_at           timestamp,
    CONSTRAINT pk_user            PRIMARY KEY (user_seq),
    CONSTRAINT uk_user_id         UNIQUE (user_id),
    CONSTRAINT uk_user_email      UNIQUE (email),
    -- 소속 사용자가 있는 조직은 삭제를 막는다
    CONSTRAINT fk_user_org        FOREIGN KEY (org_seq) REFERENCES tb_org (org_seq),
    CONSTRAINT ck_user_use_yn     CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_user_approval   CHECK (approval_limit >= 0),
    CONSTRAINT ck_user_fail_count CHECK (login_fail_count >= 0)
);

CREATE INDEX ix_user_org    ON tb_user (org_seq);
CREATE INDEX ix_user_status ON tb_user (status);
-- 로그인은 user_id 로만 조회하므로 uk_user_id 인덱스가 그대로 쓰인다

COMMENT ON TABLE  tb_user                     IS '사용자';
COMMENT ON COLUMN tb_user.user_seq            IS '사용자 순번 (PK)';
COMMENT ON COLUMN tb_user.user_id             IS '로그인 ID';
COMMENT ON COLUMN tb_user.password_hash       IS 'BCrypt 해시 (60자). 평문 저장 금지';
COMMENT ON COLUMN tb_user.org_seq             IS '소속 조직 순번';
COMMENT ON COLUMN tb_user.position_name       IS '직위. position 은 내장 함수명과 겹쳐 회피';
COMMENT ON COLUMN tb_user.status              IS '계정상태 — 코드그룹 USER_STATUS (ACTIVE/LOCKED/DORMANT/RETIRED)';
COMMENT ON COLUMN tb_user.approval_limit      IS '승인 한도 금액(원). 0 = 승인 권한 없음';
COMMENT ON COLUMN tb_user.login_fail_count    IS '연속 로그인 실패 횟수. 한도 초과 시 status=LOCKED';
COMMENT ON COLUMN tb_user.password_changed_at IS '비밀번호 최종 변경일시 — 변경 주기 통제용';


-- ============================================================================
-- 4. 역할 / 사용자역할
-- ============================================================================

CREATE TABLE tb_role (
    role_seq             bigint       GENERATED ALWAYS AS IDENTITY,
    role_id              varchar(30)  NOT NULL,
    role_name            varchar(100) NOT NULL,
    description          varchar(500),
    org_scope            varchar(20)  NOT NULL,
    default_data_scope   varchar(20)  NOT NULL DEFAULT 'OWN_ORG',
    restriction_summary  varchar(500),
    sort_order           integer      NOT NULL DEFAULT 0,
    use_yn               char(1)      NOT NULL DEFAULT 'Y',
    created_by           varchar(30)  NOT NULL,
    created_at           timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           varchar(30),
    updated_at           timestamp,
    CONSTRAINT pk_role        PRIMARY KEY (role_seq),
    CONSTRAINT uk_role_id     UNIQUE (role_id),
    CONSTRAINT uk_role_name   UNIQUE (role_name),
    CONSTRAINT ck_role_use_yn CHECK (use_yn IN ('Y', 'N'))
);

COMMENT ON TABLE  tb_role                     IS '역할';
COMMENT ON COLUMN tb_role.role_seq            IS '역할 순번 (PK)';
COMMENT ON COLUMN tb_role.role_id             IS '역할코드 (예: STORE_MGR)';
COMMENT ON COLUMN tb_role.description         IS '주요 권한 요약';
COMMENT ON COLUMN tb_role.org_scope           IS '배정 가능 조직유형 — 코드그룹 ORG_TYPE';
COMMENT ON COLUMN tb_role.default_data_scope  IS '기본 데이터범위 — 코드그룹 DATA_SCOPE. 역할권한에서 미지정 시 상속';
COMMENT ON COLUMN tb_role.restriction_summary IS '제한/승인 사항 요약. 실제 통제는 tb_policy 가 수행';


CREATE TABLE tb_user_role (
    user_seq    bigint      NOT NULL,
    role_seq    bigint      NOT NULL,
    created_by  varchar(30) NOT NULL,
    created_at  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_role      PRIMARY KEY (user_seq, role_seq),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_seq)
                                 REFERENCES tb_user (user_seq) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_seq)
                                 REFERENCES tb_role (role_seq) ON DELETE CASCADE
);

CREATE INDEX ix_user_role_role ON tb_user_role (role_seq);

COMMENT ON TABLE tb_user_role IS '사용자역할 — 한 사용자에게 여러 역할 부여 가능';


-- ============================================================================
-- 5. 권한(기능) / 권한허용액션
--    tb_permission        : 기능 단위 (메뉴/기능코드의 마스터)
--    tb_permission_action : 그 기능이 지원하는 액션의 최대 집합
--                           예) 인터페이스 설정은 삭제(D)가 없고,
--                               구매요청 승인은 조회(R) · 승인(A) 만 있다
-- ============================================================================

CREATE TABLE tb_permission (
    perm_seq     bigint       GENERATED ALWAYS AS IDENTITY,
    perm_id      varchar(30)  NOT NULL,
    perm_name    varchar(100) NOT NULL,
    module_code  varchar(20)  NOT NULL,
    menu_path    varchar(200),
    sort_order   integer      NOT NULL DEFAULT 0,
    use_yn       char(1)      NOT NULL DEFAULT 'Y',
    created_by   varchar(30)  NOT NULL,
    created_at   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   varchar(30),
    updated_at   timestamp,
    CONSTRAINT pk_permission        PRIMARY KEY (perm_seq),
    CONSTRAINT uk_permission_id     UNIQUE (perm_id),
    CONSTRAINT ck_permission_use_yn CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_permission_module ON tb_permission (module_code, sort_order);

COMMENT ON TABLE  tb_permission             IS '권한 (기능 단위)';
COMMENT ON COLUMN tb_permission.perm_seq    IS '권한 순번 (PK)';
COMMENT ON COLUMN tb_permission.perm_id     IS '기능코드 (예: STR_REPLENISH)';
COMMENT ON COLUMN tb_permission.module_code IS '모듈 — 코드그룹 PERM_MODULE';
COMMENT ON COLUMN tb_permission.menu_path   IS '화면 위치 (예: 매장 > 보충요청)';


CREATE TABLE tb_permission_action (
    perm_seq     bigint      NOT NULL,
    action_code  varchar(10) NOT NULL,
    created_by   varchar(30) NOT NULL,
    created_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_permission_action      PRIMARY KEY (perm_seq, action_code),
    CONSTRAINT fk_permission_action_perm FOREIGN KEY (perm_seq)
                                         REFERENCES tb_permission (perm_seq) ON DELETE CASCADE
);

COMMENT ON TABLE  tb_permission_action             IS '권한허용액션 — 기능이 지원하는 액션의 최대 집합';
COMMENT ON COLUMN tb_permission_action.action_code IS '액션 — 코드그룹 PERM_ACTION (R/C/U/D/A/X)';


-- ============================================================================
-- 6. 역할권한
--    역할이 각 기능에서 수행할 수 있는 액션. 매핑 화면의 체크박스 하나 = 이 테이블 한 행.
--    data_scope 가 NULL 이면 tb_role.default_data_scope 를 상속한다.
-- ============================================================================

CREATE TABLE tb_role_permission (
    role_seq     bigint      NOT NULL,
    perm_seq     bigint      NOT NULL,
    action_code  varchar(10) NOT NULL,
    data_scope   varchar(20),
    created_by   varchar(30) NOT NULL,
    created_at   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   varchar(30),
    updated_at   timestamp,
    CONSTRAINT pk_role_permission      PRIMARY KEY (role_seq, perm_seq, action_code),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_seq)
                                       REFERENCES tb_role (role_seq) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_perm FOREIGN KEY (perm_seq)
                                       REFERENCES tb_permission (perm_seq) ON DELETE CASCADE
);

CREATE INDEX ix_role_permission_perm ON tb_role_permission (perm_seq);

COMMENT ON TABLE  tb_role_permission            IS '역할권한 — 역할이 부여받은 기능별 액션';
COMMENT ON COLUMN tb_role_permission.data_scope IS '데이터범위 — 코드그룹 DATA_SCOPE. NULL 이면 역할 기본값 상속';


-- ============================================================================
-- 7. 역할조직범위
--    data_scope 코드값만으로는 "이 센터장은 이천 + 김해 두 곳" 을 표현할 수 없다.
--    소속 조직 외의 추가 접근 대상을 여기에 등록한다. (COM-PG-004)
-- ============================================================================

CREATE TABLE tb_role_org_scope (
    role_seq          bigint      NOT NULL,
    org_seq           bigint      NOT NULL,
    include_child_yn  char(1)     NOT NULL DEFAULT 'Y',
    created_by        varchar(30) NOT NULL,
    created_at        timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_role_org_scope       PRIMARY KEY (role_seq, org_seq),
    CONSTRAINT fk_role_org_scope_role  FOREIGN KEY (role_seq)
                                       REFERENCES tb_role (role_seq) ON DELETE CASCADE,
    CONSTRAINT fk_role_org_scope_org   FOREIGN KEY (org_seq)
                                       REFERENCES tb_org (org_seq) ON DELETE CASCADE,
    CONSTRAINT ck_role_org_scope_child CHECK (include_child_yn IN ('Y', 'N'))
);

COMMENT ON TABLE  tb_role_org_scope                  IS '역할조직범위 — 소속 외 추가 접근 조직';
COMMENT ON COLUMN tb_role_org_scope.include_child_yn IS '하위 조직까지 포함 Y/N';


-- ============================================================================
-- 8. 공통정책
--    역할별 제한 · 승인 규칙. 화면의 버튼 비활성 · 저장 차단 · 상위승인 요구를 결정한다.
--    perm_seq 가 NULL 이면 그 역할의 전체 기능에 적용된다.
-- ============================================================================

CREATE TABLE tb_policy (
    policy_seq      bigint       GENERATED ALWAYS AS IDENTITY,
    policy_id       varchar(20)  NOT NULL,
    policy_name     varchar(200) NOT NULL,
    role_seq        bigint       NOT NULL,
    perm_seq        bigint,
    policy_type     varchar(20)  NOT NULL,
    enforce_level   varchar(20)  NOT NULL,
    condition_expr  text,
    target_field    varchar(200),
    message         text         NOT NULL,
    alt_process     varchar(300),
    limit_amount    bigint,
    limit_qty       integer,
    remark          text,
    use_yn          char(1)      NOT NULL DEFAULT 'Y',
    created_by      varchar(30)  NOT NULL,
    created_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      varchar(30),
    updated_at      timestamp,
    CONSTRAINT pk_policy         PRIMARY KEY (policy_seq),
    CONSTRAINT uk_policy_id      UNIQUE (policy_id),
    -- 연결된 정책이 있는 역할·권한은 삭제를 막는다
    CONSTRAINT fk_policy_role    FOREIGN KEY (role_seq) REFERENCES tb_role (role_seq),
    CONSTRAINT fk_policy_perm    FOREIGN KEY (perm_seq) REFERENCES tb_permission (perm_seq),
    CONSTRAINT ck_policy_use_yn  CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_policy_limit   CHECK (limit_amount IS NULL OR limit_amount >= 0),
    -- 승인한도 정책은 금액 또는 수량 중 하나가 반드시 있어야 한다
    CONSTRAINT ck_policy_limit_required CHECK (
        policy_type <> 'LIMIT'
        OR COALESCE(limit_amount, 0) > 0
        OR COALESCE(limit_qty, 0) > 0
    )
);

-- 로그인 시 사용중 정책만 조회하므로 부분 인덱스가 효율적이다
CREATE INDEX ix_policy_role_active ON tb_policy (role_seq) WHERE use_yn = 'Y';
CREATE INDEX ix_policy_perm        ON tb_policy (perm_seq);

COMMENT ON TABLE  tb_policy                IS '공통정책 — 역할별 제한 · 승인 규칙';
COMMENT ON COLUMN tb_policy.policy_id      IS '정책코드 (예: P001)';
COMMENT ON COLUMN tb_policy.perm_seq       IS '대상 기능. NULL 이면 해당 역할의 전체 기능';
COMMENT ON COLUMN tb_policy.policy_type    IS '정책유형 — 코드그룹 POLICY_TYPE (DENY/REQUIRED/CONDITION/SOD/SCOPE/LIMIT/READONLY/MASKING)';
COMMENT ON COLUMN tb_policy.enforce_level  IS '적용강도 — 코드그룹 ENFORCE_LEVEL (BLOCK/APPROVAL/WARN/LOG)';
COMMENT ON COLUMN tb_policy.condition_expr IS '조건식. CONDITION 유형은 필수';
COMMENT ON COLUMN tb_policy.target_field   IS '대상 필드. REQUIRED 유형은 필수';
COMMENT ON COLUMN tb_policy.message        IS '차단 · 경고 시 사용자에게 노출할 문구';
COMMENT ON COLUMN tb_policy.alt_process    IS '차단 시 안내할 대안 프로세스';


-- ============================================================================
-- 9. 감사로그
--    등록 전용. 수정 · 삭제하지 않는다.
--    계정이 비활성되거나 조직이 개편돼도 이력이 읽혀야 하므로
--    행위자 ID/이름을 FK 와 별도로 값으로 복사해 둔다.
-- ============================================================================

CREATE TABLE tb_audit_log (
    log_seq        bigint       GENERATED ALWAYS AS IDENTITY,
    user_seq       bigint,
    actor_user_id  varchar(30)  NOT NULL,
    actor_name     varchar(50),
    action_type    varchar(20)  NOT NULL,
    target_table   varchar(50),
    target_key     varchar(100),
    reason         text,
    client_ip      varchar(45),
    request_id     varchar(50),
    occurred_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_log      PRIMARY KEY (log_seq),
    -- 계정이 물리 삭제되어도 이력은 남긴다 (actor_user_id 사본으로 추적 가능)
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_seq)
                                 REFERENCES tb_user (user_seq) ON DELETE SET NULL
);

CREATE INDEX ix_audit_log_occurred ON tb_audit_log (occurred_at DESC);
CREATE INDEX ix_audit_log_actor    ON tb_audit_log (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_log_target   ON tb_audit_log (target_table, target_key);
CREATE INDEX ix_audit_log_action   ON tb_audit_log (action_type, occurred_at DESC);

COMMENT ON TABLE  tb_audit_log               IS '감사로그 (등록 전용 — 수정 · 삭제 금지)';
COMMENT ON COLUMN tb_audit_log.user_seq      IS '행위자 사용자 순번. 없는 계정으로 로그인 시도 시 NULL';
COMMENT ON COLUMN tb_audit_log.actor_user_id IS '행위자 ID (값 복사 — 계정 삭제 후에도 추적 가능)';
COMMENT ON COLUMN tb_audit_log.action_type   IS '행위구분 — 코드그룹 AUDIT_ACTION';
COMMENT ON COLUMN tb_audit_log.target_table  IS '대상 테이블 (예: tb_user)';
COMMENT ON COLUMN tb_audit_log.target_key    IS '대상 키 (업무코드 기준)';
COMMENT ON COLUMN tb_audit_log.reason        IS '변경 사유 / 실패 사유';
COMMENT ON COLUMN tb_audit_log.client_ip     IS '요청 IP (IPv6 고려 45자)';
COMMENT ON COLUMN tb_audit_log.request_id    IS '요청 추적 ID — 같은 요청의 여러 로그를 묶는다';


CREATE TABLE tb_audit_log_detail (
    log_detail_seq  bigint      GENERATED ALWAYS AS IDENTITY,
    log_seq         bigint      NOT NULL,
    column_name     varchar(50) NOT NULL,
    before_value    text,
    after_value     text,
    CONSTRAINT pk_audit_log_detail     PRIMARY KEY (log_detail_seq),
    CONSTRAINT fk_audit_log_detail_log FOREIGN KEY (log_seq)
                                       REFERENCES tb_audit_log (log_seq) ON DELETE CASCADE
);

CREATE INDEX ix_audit_log_detail_log ON tb_audit_log_detail (log_seq);

COMMENT ON TABLE  tb_audit_log_detail              IS '감사로그 상세 — 변경된 컬럼당 한 행';
COMMENT ON COLUMN tb_audit_log_detail.before_value IS '변경 전 값. 신규 등록이면 NULL';
COMMENT ON COLUMN tb_audit_log_detail.after_value  IS '변경 후 값. 삭제면 NULL';
