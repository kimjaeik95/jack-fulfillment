-- ============================================================================
-- 현장 역할에 자기 일을 할 권한
--
-- 전 구간 API 테스트에서 드러난 것: 관리자 계정이 아니면 물건이 나가지
-- 않는다. 센터장은 입고예정을 못 만들고, 피킹/패킹 작업자는 자기가 담은
-- 박스를 못 닫고 송장을 못 뽑는다.
--
-- 원인은 부여가 역할이 생기기 전에 실행된 것이다. V11 이 INBOUND_WORKER ·
-- CENTER_MGR 에게 INB_PLAN 을 주는데, 그 역할들은 데모인 V900 에서
-- 만들어진다. 마이그레이션이 데모보다 먼저 도니 WHERE 절이 아무 행도 잡지
-- 못하고 조용히 0 건 들어갔다. 출고(V20 · V24 · V26)는 아예 SYS_ADMIN 에게만
-- 줬다.
--
-- 그래서 여기서 한 번에 메운다. 무엇을 주고 무엇을 안 주는지 아래에 적어
-- 둔다 — 나중에 '왜 이건 없지' 를 다시 묻지 않도록.
--
--
-- 【 센터 관리자 】 센터 안에서 벌어지는 일을 책임진다
--   INB_PLAN     CRUD   받을 준비는 창고가 한다
--   OUT_TARGET   RX     무엇을 내보내야 하는지 본다
--   OUT_ORDER    CRDX   출고지시를 내고, 잘못 낸 것을 거둔다
--   OUT_APPROVE  +C     출고확정 — 지금까지 A · R 만 있어 정작 못 찍었다
--   OUT_WAYBILL  R      송장 현황을 본다 (뽑는 것은 담는 사람이)
--
-- 【 입고 작업자 】 예정을 보고 찍는다
--   INB_PLAN     R      예정을 못 보면 입하를 찍을 수 없다. 자료범위가
--                       '본인 데이터' 로 떨어져서, 자기가 검수한 예정의
--                       상세조차 못 보고 있었다.
--
-- 【 피킹/패킹 작업자 】 집고 · 담고 · 붙인다
--   OUT_ORDER    R      자기가 맡은 지시를 본다
--   OUT_PACK     +U     자기가 담은 박스를 닫는다. 담을 수는 있는데 닫을
--                       수 없어서 매번 관리자를 불러야 했다.
--   OUT_WAYBILL  CRD    송장을 뽑아 붙이고, 잘못 뽑으면 취소한다.
--                       재발급은 취소 + 발급이라 D 가 없으면 못 고친다.
--
--
-- 일부러 주지 않은 것
--
--   피킹/패킹 → OUT_TARGET
--     출고대상은 '아직 지시가 안 난 주문' 이다. 무엇을 내보낼지 정하는
--     자리라 집는 사람이 볼 자리가 아니다. 수량체인 대사도 이 권한으로
--     판정하는데 그 역시 관리 업무다.
--
--   피킹/패킹 → OUT_APPROVE
--     담은 사람이 스스로 확정을 찍으면 아무도 확인하지 않는다. 재고가
--     줄어드는 단 하나의 지점이라 손을 바꾼다.
--
--   피킹/패킹 → OUT_ORDER 의 C · D
--     지시를 내고 거두는 것은 관리 업무다.
--
--   입고 작업자 → INB_PLAN 의 C · U · D
--     작업자가 예정을 만들면 받을 물건을 스스로 정하는 셈이 된다.
--
--   센터 관리자 → OUT_PICK · OUT_PACK
--     승인하는 사람이 실적까지 넣으면 P004(자기 요청 자기 승인 금지)의
--     취지가 출고에서 무너진다.
-- ============================================================================
INSERT INTO tb_role_permission (role_seq, perm_seq, action_code, created_by)
SELECT r.role_seq, p.perm_seq, pa.action_code, 'system'
  FROM (VALUES
        -- 센터 관리자
        ('CENTER_MGR',     'INB_PLAN',    'CRUD'),
        ('CENTER_MGR',     'OUT_TARGET',  'RX'),
        ('CENTER_MGR',     'OUT_ORDER',   'CRDX'),
        ('CENTER_MGR',     'OUT_APPROVE', 'C'),
        ('CENTER_MGR',     'OUT_WAYBILL', 'R'),

        -- 입고 작업자
        ('INBOUND_WORKER', 'INB_PLAN',    'R'),

        -- 피킹/패킹 작업자
        ('PICK_PACK',      'OUT_ORDER',   'R'),
        ('PICK_PACK',      'OUT_PACK',    'U'),
        ('PICK_PACK',      'OUT_WAYBILL', 'CRD')
       ) AS g(role_id, perm_id, actions)
  JOIN tb_role r       ON r.role_id = g.role_id
  JOIN tb_permission p ON p.perm_id = g.perm_id
  -- 권한에 등록되지 않은 액션은 주지 않는다. 문자열을 손으로 적는 만큼
  -- 오타가 조용히 통과하면 '준 줄 알았는데 없는' 상태가 또 생긴다.
  JOIN tb_permission_action pa
       ON pa.perm_seq = p.perm_seq
      AND POSITION(pa.action_code IN g.actions) > 0
 WHERE NOT EXISTS (
       SELECT 1 FROM tb_role_permission x
        WHERE x.role_seq = r.role_seq
          AND x.perm_seq = p.perm_seq
          AND x.action_code = pa.action_code);


-- ============================================================================
-- P006 — 조건부 규칙이 전면 차단으로 걸려 있다
--
-- '지시 외 SKU/수량 차단' 은 조건을 보고 판정하라는 규칙인데 policy_type 이
-- DENY 로 들어가 있다. 권한 판정기는 DENY 를 보면 규칙식을 읽지 않고 그냥
-- 막는다 (PermissionChecker.check 의 3번 분기). 그래서 PICK_PACK 역할이
-- OUT_PICK 으로 하는 조회 아닌 모든 동작이 차단됐다 — 피킹 실적도,
-- 출고검수도(검수 역시 OUT_PICK/C 로 판정한다). 조회만 통과해서 '집을 것' 은
-- 보이는데 집기만 안 되는 상태였다.
--
-- 정작 이 규칙이 말하는 초과 차단은 코드가 이미 한다. OutboundService.pick
-- 의 addPickedQty 가 0 행을 돌려주면 "지시 N 개 중 이미 M 개를 집고…" 로
-- 거부한다.
--
-- 그러니 유형을 CONDITION 으로 고친다. P002(SKU 폐기 선행조건)와 같은
-- 방식이다 — 규칙은 정책표에 남겨 무엇을 막는지 보이게 하고, 실제 판정은
-- 조건을 아는 코드가 한다.
-- ============================================================================
UPDATE tb_policy
   SET policy_type = 'CONDITION',
       remark      = '초과 스캔 시 결품/대체 프로세스로 유도. 조건 판정은 '
                  || 'OutboundService.pick 이 한다 — DENY 로 두면 피킹 자체가 막힌다.',
       updated_by  = 'system',
       updated_at  = now()
 WHERE policy_id = 'P006'
   AND policy_type = 'DENY';
