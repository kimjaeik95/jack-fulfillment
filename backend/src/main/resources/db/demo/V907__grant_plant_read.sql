-- ============================================================================
-- 재고 담당자에게 플랜트 읽기 권한 (V905 누락분)
--
-- V905 가 INV_MANAGER 에게 창고(MST_WAREHOUSE)와 빈(MST_LOCATION)은 줬는데
-- 그 위의 플랜트(MST_PLANT)를 빠뜨렸다. 재고주소가 플랜트 → 창고 → 빈
-- 세 단계인데 맨 위만 없는 상태였다.
--
-- 그래서 화면의 '플랜트' 드롭다운이 조용히 비어 있었다. 목록 조회는
-- 권한이 없어도 화면 전체를 실패로 만들지 않고 그 종류만 비워 두도록
-- 되어 있어서(catalog · hierarchy 스토어의 loadKind), 오류 없이 선택지만
-- 사라진다 — 고르지 못하는 이유를 화면이 말해 주지 않는다.
--
-- 재고 화면에서는 필터가 하나 비는 정도였지만, 구매요청은 센터를 반드시
-- 골라야 올릴 수 있어서 그때 드러났다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, 'R', 'system'
  FROM tb_role r, tb_permission p
 WHERE r.role_id = 'INV_MANAGER'
   AND p.perm_id = 'MST_PLANT'
   AND NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq AND x.perm_seq = p.perm_seq
          AND x.action_code = 'R');
