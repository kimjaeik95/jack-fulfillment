-- ============================================================================
-- 출고확정 · 택배 인계 (PAC-PG-005, PAC-PG-006 / 4차 E섹터)
--
-- <b>여기서 재고가 줄어든다.</b> 지금까지는 아무것도 줄지 않았다 —
--
--   할당      qty_allocated +3   판매가능 -3
--   피킹      안 바뀜   (빈에서 카트로 옮겼을 뿐)
--   검수      안 바뀜   (카트를 세었을 뿐)
--   패킹      안 바뀜   (카트에서 박스로 옮겼을 뿐)
--   송장      안 바뀜   (번호를 적었을 뿐)
--   출고확정  qty_on_hand -3, qty_allocated -3   <- 여기
--
-- 보유와 할당을 <b>같이</b> 줄이는 것이 핵심이다. 따로 하면 그 사이에
-- 판매가능(= 보유 - 할당 - 판매불가)이 3 만큼 늘어 보이고, 그 순간 다른
-- 주문이 없는 재고를 잡는다.
--
-- 어느 빈의 재고를 줄일지는 피킹 실적(tb_outbound_pick)이 말해 준다.
-- 그래서 B섹터에서 stock_seq 와 alloc_seq 를 남겨 뒀다 — '지시 줄에 3 개'
-- 만으로는 한 줄이 여러 빈에서 나뉘어 잡혔을 때 어디서 뺄지 모른다.
--
-- 새 표는 없다. 지시 · 박스에 인계 자취만 더한다.
-- ============================================================================


-- ============================================================================
-- 1. 택배 인계 (PAC-PG-006)
--
-- 택배사가 실제로 실어 간 시점. 출고확정과 나누는 이유는 둘이 다른 사건이라
-- 서다 — 출고확정은 우리가 '나갔다' 고 장부를 닫는 것이고, 인계는 택배사가
-- '받았다' 고 확인하는 것이다. 대개 같은 날이지만 확정한 물건이 집화 차를
-- 놓쳐 하루 밀리는 일이 있고, 그때 배송 지연을 누구 탓으로 볼지가 갈린다.
--
-- 박스에 건다. 송장이 박스에 붙고 집화도 박스 단위로 스캔하기 때문이다.
-- 한 지시의 박스 셋 중 둘만 실려 가는 일이 실제로 있다.
-- ============================================================================
ALTER TABLE tb_pack_box
    ADD COLUMN handed_over_by varchar(30),
    ADD COLUMN handed_over_at timestamp;

COMMENT ON COLUMN tb_pack_box.handed_over_by IS '택배사에 넘긴 사람 (PAC-PG-006)';
COMMENT ON COLUMN tb_pack_box.handed_over_at IS '집화 스캔 시각. 비면 아직 안 넘어갔다';

-- '아직 안 넘어간 박스' — 인계 화면의 기본 경로
CREATE INDEX ix_packbox_handover ON tb_pack_box (outbound_seq)
    WHERE handed_over_at IS NULL;


-- ============================================================================
-- 2. 출고확정 자취
--
-- 언제 · 누가 확정했는지는 tb_outbound 의 shipped_by / shipped_at 이 이미
-- 들고 있다 (V20). 여기서는 <b>되돌린 자취</b>만 더한다.
--
-- 출고확정은 되돌릴 수 없다. 재고가 이미 줄었고 물건이 창고에 없기 때문에,
-- 전산만 돌려놓으면 팔 수 있다고 표시된 수량이 실제로는 없는 상태가 된다.
-- 잘못 확정했으면 반품으로 처리한다 (6차).
--
-- 그래서 확정 사유는 안 받는다 — 정상 흐름의 마지막 단계라 '왜 내보냈나'
-- 는 주문이 이미 답한다. 대신 인계 취소(집화 차가 안 왔다)는 있을 수 있어
-- 그것만 자취를 남긴다.
-- ============================================================================
COMMENT ON COLUMN tb_outbound.shipped_by IS '출고확정한 사람 (PAC-PG-005). 여기서 재고가 줄었다';
COMMENT ON COLUMN tb_outbound.shipped_at IS '출고확정 시각. 배송 지연 판정의 기준일';


-- ============================================================================
-- 3. 메뉴
--
-- 권한은 V4 의 OUT_APPROVE('출고 승인', RA)를 쓴다. 0차 시드가 '출고를
-- 승인하는' 권한으로 깔아 둔 것인데, 실제로 그 자리에 오는 행위가
-- 출고확정이다 — 재고를 줄이고 문서를 닫는 마지막 결정이라 피킹 · 패킹과
-- 무게가 다르다.
--
-- 액션에 C 를 더한다. RA 만으로는 '승인(A)' 밖에 못 하는데, 출고확정은
-- 새 사실을 만드는 일(C)에 가깝다 — 결재가 아니라 실행이다.
-- ============================================================================
INSERT INTO tb_permission_action (perm_seq, action_code, created_by)
SELECT p.perm_seq, a.action_code, 'system'
  FROM (VALUES ('OUT_APPROVE', 'C')) AS v(perm_id, actions)
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code)
 WHERE NOT EXISTS (
       SELECT 1 FROM tb_permission_action x
        WHERE x.perm_seq = p.perm_seq
          AND x.action_code = a.action_code);

INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, data_scope, created_by)
SELECT r.role_seq, a.perm_seq, a.action_code, NULL, 'v17.sysadmin'
  FROM tb_role r
  JOIN tb_permission_action a ON TRUE
 WHERE r.role_id = 'SYS_ADMIN'
   AND NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq
          AND x.perm_seq = a.perm_seq
          AND x.action_code = a.action_code);

INSERT INTO tb_menu (menu_id, menu_name, parent_seq, route_name, icon, perm_seq, sort_order, created_by)
SELECT v.menu_id, v.menu_name, g.menu_seq, v.route_name, v.icon, p.perm_seq, v.sort_order, 'system'
  FROM (VALUES
        ('OUT_SHIP',  '출고확정 · 인계', 'GRP_OUT', 'outbound-ship',  '🚛', 'OUT_APPROVE', 80),
        ('OUT_CHAIN', '수량 체인 검증',  'GRP_OUT', 'outbound-chain', '🔗', 'OUT_TARGET',  90)
       ) AS v(menu_id, menu_name, parent_id, route_name, icon, perm_id, sort_order)
  JOIN tb_menu g       ON g.menu_id = v.parent_id
  JOIN tb_permission p ON p.perm_id = v.perm_id;
