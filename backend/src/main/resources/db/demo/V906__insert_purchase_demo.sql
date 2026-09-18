-- ============================================================================
-- 구매요청 데모 — 누가 올리고 누가 결재하는가
--
-- 요청은 센터가 올리고 결재는 본사 구매 담당이 한다. 물건이 모자란 것을
-- 아는 사람은 현장이고, 예산과 거래처를 아는 사람은 본사이기 때문이다.
--
-- 그래서 요청 권한은 센터 쪽 역할에, 승인 권한은 구매 담당(PURCHASER)에
-- 붙인다. PURCHASER 는 V900 이 이미 PUR_REQ_APPROVE 를 갖고 있다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, a.action_code, 'system'
  FROM (VALUES
        -- 재고 담당자가 올린다. 무엇이 모자란지 재고 화면에서 보는 사람이다.
        ('INV_MANAGER', 'PUR_REQUEST', 'RCUD'),
        -- 센터 관리자도 올린다. 현장에서 먼저 발견하는 경우가 흔하다.
        ('CENTER_MGR',  'PUR_REQUEST', 'RCUD'),

        -- 구매 담당은 결재함을 열어야 하므로 요청을 읽어야 하고, 올리기도
        -- 한다. 신상품 초도물량처럼 센터가 아니라 본사가 먼저 판단하는
        -- 품목이 있기 때문이다 — 요구사항이 '요청 없는 직접 발주'(PUR-005)
        -- 를 따로 둔 것도 같은 사정이다.
        --
        -- 그래서 요청과 결재를 둘 다 가진 역할이 하나는 있어야 한다.
        -- 권한으로 막으면 그건 직무분리가 아니라 그냥 권한 부족이고,
        -- '자기 요청 자기 승인 금지'(AUTH-008)가 실제로 무엇을 막는지
        -- 아무도 확인하지 못한다.
        ('PURCHASER',   'PUR_REQUEST', 'RCUD'),

        -- 공급처를 골라야 하므로 거래처를 읽는다. V900 이 MST_VENDOR 를
        -- 줬는데 실제 거래처 화면의 권한은 MST_PARTNER 다.
        ('PURCHASER',   'MST_PARTNER', 'R'),
        ('INV_MANAGER', 'MST_PARTNER', 'R'),

        -- 요청은 SKU 단위라 SKU 를 읽을 수 있어야 한다
        ('INV_MANAGER', 'MST_SKU', 'R'),
        ('PURCHASER',   'MST_SKU', 'R')
       ) AS v(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = v.role_id
  JOIN tb_permission p ON p.perm_id = v.perm_id
 CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL)) AS a(action_code)
 WHERE NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
          AND x.action_code = a.action_code);


-- ============================================================================
-- 구매 담당의 조직범위.
--
-- PURCHASER 는 본사(HQ) 소속이고 데이터범위가 ALL 이라 모든 센터의 요청을
-- 본다. 별도 조직범위를 줄 필요가 없다 — 구매는 전사 단위 업무다.
--
-- 반대로 센터 담당은 OWN_ORG 라 자기 센터 요청만 본다. 남의 센터가 무엇을
-- 요청했는지는 알 필요도, 알아서도 안 된다.
-- ============================================================================
