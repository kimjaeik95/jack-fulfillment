-- ============================================================================
-- 배송 권한 (6차 · DLV-PG-001 ~ 004)
--
-- V27 이 SYS_ADMIN 에게만 줬다. 업무 역할은 데모(V900·V909)에서 만들어지므로
-- 마이그레이션에서 주면 또 0 건 들어간다 — V914 에서 겪은 그 함정이다.
--
--
-- 【 CS/조회 사용자 】 이 섹터의 주인이다
--
-- 요구사항이 DLV-PG-001~004 의 담당자를 모두 'CS/배송담당자' 로 적었다.
-- 전화를 받아 "어디까지 갔어요" 에 답하고, 못 간 것을 찾아 다시 보내는 일이
-- 한 사람의 일이라서다.
--
--   DLV_TRACK    CRX   배송상태를 찍는다. 새 사실을 하나 쌓는 일이라 C 다
--   DLV_FAIL     CRX   실패를 적고 다시 보낸다
--   DLV_TRANSIT  RX    운송중 재고를 본다
--   DLV_COURIER  R     택배사를 고르려면 봐야 한다. 만들지는 못한다
--
-- CS_VIEWER 는 지금 READONLY 정책(P009)이 걸려 있어 조회 아닌 동작이 전부
-- 막힌다. 그래서 <b>권한만으로는 배송상태를 못 찍는다.</b> 그 정책을 풀지는
-- 않는다 — '조회 전용 계정' 이라는 뜻으로 깔아 둔 것이라 여기서 뒤집으면
-- 그 계정의 성격이 바뀐다. 실제로 찍을 사람에게는 별도 역할을 주는 것이
-- 맞고, 지금은 그 역할이 없어 관리자와 센터장이 대신한다.
--
--
-- 【 센터 관리자 】 자기 센터에서 나간 것이 도착했는지 본다
--
--   DLV_TRACK    CRX   인계한 뒤가 궁금한 사람이 결국 센터다
--   DLV_FAIL     CRX   실패한 것을 다시 보낸다
--   DLV_TRANSIT  RX    떠 있는 수량은 자기 센터 재고의 연장이다
--   DLV_COURIER  R
--
--
-- 【 본사 기준정보 】 택배사를 만들고 계약을 적는다
--
--   DLV_COURIER  CRUDX  계약은 회사가 맺는다. 센터가 정할 일이 아니다
--
--
-- 일부러 주지 않은 것
--
--   센터 관리자 → DLV_COURIER 의 C · U · D
--     택배사 계약은 회사 단위다. 센터마다 다른 단가를 적기 시작하면
--     어느 것이 진짜인지 알 수 없게 된다.
--
--   피킹/패킹 작업자 → 배송 전부
--     넘기는 것까지가 창고 일이다. 넘긴 뒤는 CS 와 센터가 본다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, pa.action_code, 'system'
  FROM (VALUES
        -- CS/배송담당자 — 요구사항이 적은 이 섹터의 담당자
        ('CS_VIEWER',  'DLV_TRACK',   'CRX'),
        ('CS_VIEWER',  'DLV_FAIL',    'CRX'),
        ('CS_VIEWER',  'DLV_TRANSIT', 'RX'),
        ('CS_VIEWER',  'DLV_COURIER', 'R'),

        -- 센터 관리자 — 자기 센터에서 나간 것이 도착했는지 본다
        ('CENTER_MGR', 'DLV_TRACK',   'CRX'),
        ('CENTER_MGR', 'DLV_FAIL',    'CRX'),
        ('CENTER_MGR', 'DLV_TRANSIT', 'RX'),
        ('CENTER_MGR', 'DLV_COURIER', 'R'),

        -- 본사 기준정보 — 택배사와 계약을 관리한다
        ('HQ_MASTER',  'DLV_COURIER', 'CRUDX')
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
