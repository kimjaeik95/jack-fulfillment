-- ============================================================================
-- 알림함 (0차 · COM-PG-015)
--
-- 지금은 무슨 일이 생겨도 사람이 화면을 열어 봐야 안다. 배송실패가 났는지,
-- 운송중 재고가 열흘째 떠 있는지, 내 결재를 기다리는 요청이 있는지 아무도
-- 알려 주지 않는다.
--
--
-- 【 알림은 '소식' 이 아니라 '할 일' 이다 】
--
-- 기준은 <b>누군가 지금 뭔가를 해야 하는가</b> 하나다.
--
--   등록됐는데 아무도 할 일이 없으면   안 남긴다 (감사로그만)
--   예외가 났는데 코드가 처리했으면     안 남긴다
--   아무 일도 없었는데 할 일이 생겼으면 남긴다  ← 배치가 찾는다
--
-- '등록되면 다 알림' 으로 만들면 알림함이 감사로그가 되고, 감사로그가 되면
-- 아무도 안 본다. 감사로그는 무슨 일이 있었나를 전부 적는 자리(사후 추적)고
-- 여기는 누가 뭘 해야 하나만 적는 자리(사전 행동)다.
--
--
-- 【 끝나면 저절로 사라져야 한다 】
--
-- 이것이 이 표의 핵심이다. 사람이 '읽음' 을 눌러 지우게 하면 두 번 일하는
-- 것이고, 안 지우면 쌓인다. 결재를 끝내면 그 알림이 닫혀야 한다.
--
-- 그래서 (kind, ref_type, ref_no) 를 열쇠로 잡고 <b>열려 있는 동안 하나만</b>
-- 두는 부분 유니크를 건다. 문서 상태가 바뀌면 닫고, 배치가 만든 것은 다음
-- 배치에서 조건이 안 맞으면 닫는다.
--
--
-- 【 수신자는 사람이 아니라 역할 】
--
-- 사람으로 박으면 휴가 가고 퇴사하면 그 알림이 허공에 뜬다. 역할 + 조직으로
-- 걸고, 읽음 표시만 사람별로 둔다.
-- ============================================================================


-- ============================================================================
-- 1. 알림
-- ============================================================================
CREATE TABLE tb_notification (
    notification_seq bigint       GENERATED ALWAYS AS IDENTITY,

    -- 코드그룹 NOTI_KIND. 무슨 일인가
    kind             varchar(30)  NOT NULL,

    /*
     * 누구에게. 역할로 건다.
     *
     * role_seq 가 비면 전사 공지인데, 지금은 그런 알림이 없다. 칸만 열어
     * 둔다 — 나중에 '시스템 점검' 같은 것이 생기면 여기 쓴다.
     */
    role_seq         bigint,

    /*
     * 어느 센터 일인가.
     *
     * 이천센터 사람에게 김해센터 결품을 알리면 소음이다. 비면 센터를
     * 안 가리는 알림(전사 기준정보 같은 것)이다.
     */
    plant_seq        bigint,

    -- 무엇에 대한 알림인가. 누르면 이것으로 그 화면에 간다
    ref_type         varchar(30)  NOT NULL,
    ref_no           varchar(50)  NOT NULL,

    title            varchar(200) NOT NULL,
    /** 한 줄 더. 왜 이게 문제인지 */
    body             varchar(500),

    /*
     * 얼마나 급한가. 코드그룹 NOTI_LEVEL.
     *
     *   INFO   알아 두면 좋다
     *   WARN   챙겨야 한다
     *   ALERT  지금 봐야 한다 — 재고가 틀어졌거나 물건이 사라졌다
     */
    level            varchar(20)  NOT NULL DEFAULT 'INFO',

    /*
     * 열려 있나. 닫히면 목록에서 사라진다.
     *
     * 사람이 닫지 않는다 — 그 일이 끝나면 시스템이 닫는다.
     */
    closed_yn        char(1)      NOT NULL DEFAULT 'N',
    closed_at        timestamp,
    /** 무엇 때문에 닫혔나. 'DONE'(일이 끝남) · 'GONE'(조건이 사라짐) */
    closed_reason    varchar(30),

    /** 사건이 일어난 시각. 적힌 시각(created_at)과 다를 수 있다 */
    occurred_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by       varchar(30)  NOT NULL,
    created_at       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_notification    PRIMARY KEY (notification_seq),
    CONSTRAINT fk_noti_role       FOREIGN KEY (role_seq)  REFERENCES tb_role (role_seq),
    CONSTRAINT fk_noti_plant      FOREIGN KEY (plant_seq) REFERENCES tb_plant (plant_seq),
    CONSTRAINT ck_noti_closed     CHECK (closed_yn IN ('Y','N')),
    CONSTRAINT ck_noti_closed_at  CHECK (closed_yn = 'N' OR closed_at IS NOT NULL),
    CONSTRAINT ck_noti_level      CHECK (level IN ('INFO','WARN','ALERT'))
);

COMMENT ON TABLE  tb_notification IS '알림 (COM-PG-015). 소식이 아니라 할 일이다 — 그 일이 끝나면 닫힌다';
COMMENT ON COLUMN tb_notification.closed_yn IS '사람이 닫지 않는다. 그 일이 끝나면 시스템이 닫는다';
COMMENT ON COLUMN tb_notification.role_seq IS '수신자는 역할이다. 사람으로 박으면 휴가·퇴사 때 허공에 뜬다';

/*
 * 같은 것을 두 번 만들지 않는다.
 *
 * 배치가 매일 도는데 '발주 납기 초과' 를 매일 새로 만들면 열흘 뒤 같은
 * 알림이 열 개다. 열려 있는 동안엔 하나만 둔다.
 */
CREATE UNIQUE INDEX ux_noti_open
    ON tb_notification (kind, ref_type, ref_no)
 WHERE closed_yn = 'N';

CREATE INDEX ix_noti_inbox ON tb_notification (role_seq, closed_yn, occurred_at DESC);
CREATE INDEX ix_noti_ref   ON tb_notification (ref_type, ref_no);


-- ============================================================================
-- 2. 읽음
--
-- 사람별로 둔다. 알림 자체는 역할에 걸려 있어서 여러 사람이 같은 알림을
-- 보는데, 한 사람이 읽었다고 다른 사람에게서 사라지면 안 된다.
--
-- 읽음은 <b>뱃지 숫자용</b>이다. 알림이 사라지는 것은 닫힘이지 읽음이
-- 아니다 — 읽었다고 일이 끝난 것은 아니라서.
-- ============================================================================
CREATE TABLE tb_notification_read (
    notification_seq bigint      NOT NULL,
    user_seq         bigint      NOT NULL,
    read_at          timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_noti_read   PRIMARY KEY (notification_seq, user_seq),
    CONSTRAINT fk_notiread_n  FOREIGN KEY (notification_seq)
                              REFERENCES tb_notification (notification_seq) ON DELETE CASCADE,
    CONSTRAINT fk_notiread_u  FOREIGN KEY (user_seq) REFERENCES tb_user (user_seq)
);

COMMENT ON TABLE tb_notification_read IS '읽음 표시 (COM-PG-015). 뱃지 숫자용이고, 사라지는 것은 닫힘이지 읽음이 아니다';

CREATE INDEX ix_notiread_user ON tb_notification_read (user_seq);


-- ============================================================================
-- 3. 공통코드
--
-- 여덟 종류다. 더 넣으면 늘어나는 것은 소음뿐이다.
--
--   사건형 다섯 — 그 일이 일어난 자리에서 서비스가 남긴다
--   배치형 셋   — 사건이 없다. 아무도 안 한 것이 문제라 배치가 찾는다
-- ============================================================================
INSERT INTO tb_code_group (code_group_id, code_group_name, description, group_kind, created_by)
VALUES ('NOTI_KIND',  '알림 종류', '무슨 일인가',           'SYSTEM', 'system'),
       ('NOTI_LEVEL', '알림 등급', '얼마나 급한가',         'SYSTEM', 'system');

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        -- 사건형
        ('ORDER_UNMAPPED',  '미매핑 주문',   '채널 상품코드에 붙일 SKU 가 없다',        10),
        ('ALLOC_SHORT',     '할당 결품',     '전산에도 재고가 없어 못 잡았다',          20),
        ('PICK_SHORT',      '피킹 결품',     '전산엔 있는데 빈에 없다 — 재고가 틀렸다', 30),
        ('APPROVAL_WAIT',   '결재 대기',     '내 결재를 기다리는 문서가 있다',          40),
        ('DELIVERY_FAILED', '배송 실패',     '고객에게 못 갔다',                        50),
        -- 배치형
        ('PO_OVERDUE',      '발주 납기 초과', '납기가 지났는데 안 들어왔다',            60),
        ('TRANSIT_STUCK',   '운송중 지연',   '오래 길 위에 떠 있다',                    70),
        ('CHAIN_BROKEN',    '수량 체인 꺾임', '설명되지 않는 차이가 있다',              80)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'NOTI_KIND';

INSERT INTO tb_code (code_group_seq, code_id, code_name, description, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.sort_order, 'system'
  FROM (VALUES
        ('INFO',  '알림', '알아 두면 좋다',                          10),
        ('WARN',  '주의', '챙겨야 한다',                             20),
        ('ALERT', '경고', '지금 봐야 한다 — 재고가 틀어졌다',        30)
       ) AS v(code_id, code_name, description, sort_order)
  JOIN tb_code_group g ON g.code_group_id = 'NOTI_LEVEL';


-- ============================================================================
-- 4. 권한 · 메뉴
--
-- 알림함은 <b>모두가 본다.</b> 자기 역할에 온 것만 보이므로 막을 이유가 없고,
-- 막으면 그 사람에게 온 알림이 갈 데가 없어진다.
--
-- 액션은 R 뿐이다. 알림을 만들거나 지우는 것은 사람이 하는 일이 아니다 —
-- 읽음 표시는 조회의 부수 효과라 따로 권한을 두지 않는다.
-- ============================================================================
INSERT INTO tb_permission (perm_id, perm_name, module_code, menu_path, sort_order, created_by)
VALUES ('SYS_NOTIFICATION', '알림함', 'SYS', '현황 > 알림함', 17, 'system');

INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, 'R', 'system'
  FROM tb_permission p WHERE p.perm_id = 'SYS_NOTIFICATION';

-- 모든 역할에게. 자기 것만 보이므로 나눌 이유가 없다.
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, p.perm_seq, 'R', NULL, 'system'
  FROM tb_role r, tb_permission p
 WHERE p.perm_id = 'SYS_NOTIFICATION';

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT 'SYS_NOTIFICATION', '알림함', g.menu_seq, 'notifications', '🔔', p.perm_seq, 2, 'system'
  FROM tb_menu g, tb_permission p
 WHERE g.menu_id = 'GRP_STATUS' AND p.perm_id = 'SYS_NOTIFICATION';
