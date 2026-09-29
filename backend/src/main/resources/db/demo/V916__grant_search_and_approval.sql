-- ============================================================================
-- 통합검색 · 승인 작업함을 업무 역할에게 (0차)
--
-- V28 이 SYS_ADMIN 에게만 줬다. 업무 역할은 데모에서 만들어지므로 마이그
-- 레이션에서 주면 또 0 건 들어간다 (V914 의 함정).
--
--
-- 【 통합검색은 거의 모두에게 】
--
-- 번호 하나로 그 건이 어디까지 갔나를 보는 화면이다. 막을 이유가 별로
-- 없다 — 검색 자체는 아무것도 바꾸지 않고, <b>결과는 그 사람이 볼 수 있는
-- 것만</b> 나온다. 서비스가 문서 종류마다 그 사람의 조회 권한을 확인하고,
-- 없으면 그 종류를 아예 빼고 돌려준다.
--
-- 그래서 피킹/패킹 작업자가 검색해도 출고지시까지만 보이고, 구매요청은
-- 안 보인다. 권한이 없어서가 아니라 <b>검색 결과에서 빠지는</b> 것이다.
--
-- 입고 작업자는 뺀다. 스캔 단말로 일하는 자리라 검색할 일이 없고, 메뉴가
-- 하나 늘면 그 작은 화면에서 누를 것만 늘어난다.
--
--
-- 【 작업함은 결재하는 사람에게만 】
--
-- 결재할 것이 없는 사람에게 주면 늘 빈 화면이다. 액션 A 를 가진 역할 —
-- 구매담당 · 센터 관리자 · 본사 기준정보에게 준다.
--
-- 작업함 권한은 <b>모아 보는 것</b>까지다. 승인은 그 문서의 권한이
-- 판정하므로, 작업함이 보인다고 남의 결재를 할 수 있게 되지는 않는다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, pa.action_code, 'system'
  FROM (VALUES
        -- 통합검색
        ('PURCHASER',  'SYS_SEARCH',   'RX'),
        ('CENTER_MGR', 'SYS_SEARCH',   'RX'),
        ('ORDER_MGR',  'SYS_SEARCH',   'RX'),
        ('CS_VIEWER',  'SYS_SEARCH',   'RX'),
        ('HQ_MASTER',  'SYS_SEARCH',   'RX'),
        ('INV_MANAGER','SYS_SEARCH',   'RX'),
        ('PICK_PACK',  'SYS_SEARCH',   'R'),
        ('AUDITOR',    'SYS_SEARCH',   'R'),

        -- 승인 작업함 — 결재할 것이 있는 역할만
        ('PURCHASER',  'SYS_APPROVAL', 'RX'),
        ('CENTER_MGR', 'SYS_APPROVAL', 'RX'),
        ('HQ_MASTER',  'SYS_APPROVAL', 'RX')
       ) AS g(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = g.role_id
  JOIN tb_permission p ON p.perm_id = g.perm_id
  -- 권한에 등록되지 않은 액션은 주지 않는다 (V914 와 같은 방식)
  JOIN tb_permission_action pa
       ON pa.perm_seq = p.perm_seq
      AND POSITION(pa.action_code IN g.actions) > 0
 WHERE NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq
          AND x.perm_seq = p.perm_seq
          AND x.action_code = pa.action_code);
