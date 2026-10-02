-- ============================================================================
-- 센터 관리자가 자기 센터의 출고 작업을 직접 할 수 있게 한다
--
-- 지금까지 센터장에게 보이는 것은 출고의 <b>앞과 뒤</b>뿐이었다.
--
--   출고대상 · 출고지시            보인다   (OUT_TARGET · OUT_ORDER)
--   피킹 · 결품 · 검수 · 패킹      안 보인다
--   송장 · 출고확정 · 수량 체인    보인다   (OUT_WAYBILL · OUT_APPROVE · OUT_TARGET)
--
-- 가운데 넷이 빠져 있다. 지시를 내는 사람이고 출고확정으로 끝맺는 사람인데,
-- 그 사이에 무엇이 밀려 있는지는 못 봤다. 가장 어긋난 곳은 수량 체인 검증이다
-- — '지시 6 집음 4 검수 4' 라는 <b>숫자는 보는데 그 숫자가 만들어지는 화면은
-- 못 본다.</b> 왜 4인지 보러 갈 데가 없었다.
--
-- 사이드바가 권한 없는 메뉴를 통째로 감추므로(App.vue pruneMenus), 센터장
-- 화면에서는 네 메뉴가 아예 사라져 있었다.
--
--
-- 【 보기만이 아니라 일까지 】
--
-- 바쁜 날에는 센터장도 거든다. 읽기만 주면 '보이는데 아무것도 못 하는'
-- 화면이 넷 늘어날 뿐이다.
--
--   OUT_PICK      R,C    피킹 · 출고검수 (두 화면이 같은 권한을 쓴다)
--   OUT_SHORTAGE  R,C    피킹 결품
--   OUT_PACK      R,C,U  패킹 (U 는 박스 열고 닫기)
--
-- 범위는 건드리지 않는다. data_scope 를 NULL 로 두면 역할 기본값인 OWN_ORG
-- 를 그대로 받는다 — <b>센터장은 여전히 자기 조직 것만 본다.</b> 최센터가
-- 김해 지시를 집는 일은 생기지 않는다.
--
--
-- 【 알고 넘어가는 것 】
--
-- 이제 한 사람이 집고 · 검수하고 · 확정까지 할 수 있다. 출고확정에는
-- 자기검증 금지가 없다 — P004 는 INV_ADJ_APPROVE 하나에만 걸려 있고,
-- 막는 코드도 AdjustService · CorrectService 둘뿐이라 ship() 은 집은 사람이
-- 누구인지 보지 않는다.
--
-- 누가 했는지는 남는다 (tb_outbound_pick.picked_by · tb_outbound.shipped_by).
-- 사후에 볼 수 있다는 뜻이고, 실시간으로 막는 장치는 없다. 분리가 필요해지면
-- 그때 ship() 에 넣을 자리다.
--
-- 송장 발행(OUT_WAYBILL C/D)은 넣지 않는다. 센터장은 이미 R 을 갖고 있고,
-- 발행은 패킹 다음의 별도 업무다.
-- ============================================================================

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, v.action_code, 'system'
  FROM (VALUES
        ('OUT_PICK',     'R'),
        ('OUT_PICK',     'C'),
        ('OUT_SHORTAGE', 'R'),
        ('OUT_SHORTAGE', 'C'),
        ('OUT_PACK',     'R'),
        ('OUT_PACK',     'C'),
        ('OUT_PACK',     'U')
       ) AS v(perm_id, action_code)
  JOIN tb_role       r ON r.role_id = 'CENTER_MGR'
  JOIN tb_permission p ON p.perm_id = v.perm_id
 WHERE NOT EXISTS (
        SELECT 1 FROM tb_role_permission x
         WHERE x.role_seq    = r.role_seq
           AND x.perm_seq    = p.perm_seq
           AND x.action_code = v.action_code);
