-- ============================================================================
-- V4 : 최초 로그인 시 비밀번호 변경 강제
--
-- 사내 시스템은 자가 가입이 없다. 관리자가 계정을 만들고 초기 비밀번호를 정해
-- 담당자에게 전달하므로, 그 시점에 관리자와 담당자 두 사람이 같은 비밀번호를 안다.
-- 담당자가 최초 로그인에서 반드시 바꾸게 해야 그 상태가 해소된다.
--
-- password_changed_at 이 NULL 인지로 판단할 수도 있지만
-- "한 번도 안 바꿈" 과 "이력을 모름" 이 구분되지 않는다.
-- 또한 유출 의심 시 관리자가 임의로 변경을 강제할 수 있어야 하므로 별도 플래그를 둔다.
-- ============================================================================

ALTER TABLE tb_user
    ADD COLUMN must_change_password char(1) NOT NULL DEFAULT 'Y';

ALTER TABLE tb_user
    ADD CONSTRAINT ck_user_must_change CHECK (must_change_password IN ('Y', 'N'));

COMMENT ON COLUMN tb_user.must_change_password IS
    '최초/초기화 후 비밀번호 변경 필요 여부 Y/N. Y 이면 변경 화면 외 접근을 차단한다';

-- 기존 계정(시드로 만든 개발용 fixture)은 변경 강제 대상에서 제외한다.
-- 운영 첫 배포에서는 admin 을 'Y' 로 두어 최초 접속 시 반드시 바꾸게 해야 한다.
UPDATE tb_user
   SET must_change_password = 'N',
       password_changed_at  = CURRENT_TIMESTAMP
 WHERE must_change_password = 'Y';
