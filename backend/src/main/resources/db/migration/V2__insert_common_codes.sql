-- ============================================================================
-- V2 : 공통코드  (PostgreSQL)
--
-- 화면의 셀렉트박스 · 배지 라벨 · 서버 검증이 모두 이 코드를 참조한다.
-- 그래서 이 파일은 "예시 데이터" 가 아니라 코드와 함께 버전관리되어야 하는
-- 필수 데이터다 — 운영 배포에 포함된다.
--
-- 코드를 지우면 그 값을 쓰는 화면과 오류 메시지가 함께 사라져야 한다.
-- 서비스가 목록을 코드에 적어 두면 그게 안 된다. 실제로 RoleService 가
-- 조직유형 목록을 하드코딩해 두어, 매장을 걷어낸 뒤에도 orgScope="STORE" 인
-- 역할이 등록됐다. 목록은 반드시 이 표에서 읽는다 (CodeLabels · selectCodeIds).
--
-- attr1 은 화면 배지 색상이다.
-- ============================================================================

INSERT INTO tb_code_group (code_group_id, code_group_name, description, created_by) VALUES
  ('ORG_TYPE',      '조직 유형',     '회사 · 물류센터',                              'system'),
  ('PLANT_TYPE',    '플랜트 유형',   '거점의 역할',                                  'system'),
  ('WH_TYPE',       '창고 유형',     '플랜트 내 구획의 재고 성격',                   'system'),
  ('LOC_TYPE',      '빈 유형', '빈에 담기는 재고의 상태',                      'system'),
  ('USER_STATUS',   '사용자 상태',   '계정 상태. 물리 삭제 대신 이 값으로 통제한다', 'system'),
  ('PERM_MODULE',   '권한 모듈',     '기능 권한의 대분류',                           'system'),
  ('PERM_ACTION',   '권한 액션',     '조회 · 등록 · 수정 · 삭제 · 승인 · 다운로드',  'system'),
  ('DATA_SCOPE',    '데이터 범위',   '역할이 접근할 수 있는 데이터의 범위',          'system'),
  ('POLICY_TYPE',   '정책 유형',     '제한 · 승인 규칙의 종류',                      'system'),
  ('ENFORCE_LEVEL', '정책 적용강도', '위반 시 처리 방식',                            'system'),
  ('AUDIT_ACTION',  '감사 행위구분', '감사로그에 기록되는 행위의 종류',              'system'),
  ('UPLOAD_TARGET', '업로드 대상',   '대량 등록을 지원하는 데이터 종류',             'system'),
  ('UPLOAD_STATUS', '업로드 결과',   '대량 등록 처리 결과',                          'system'),
  ('USE_YN',        '사용여부',      '공통 사용여부',                                'system');


-- ============================================================================
-- 코드값
--   (그룹ID, 코드, 코드명, 설명, 색상, 정렬) 을 VALUES 로 나열하고
--   그룹ID 로 조인해 code_group_seq 를 채운다.
-- ============================================================================
INSERT INTO tb_code (code_group_seq, code_id, code_name, description, attr1, sort_order, created_by)
SELECT g.code_group_seq, v.code_id, v.code_name, v.description, v.attr1, v.sort_order, 'system'
  FROM (VALUES
        -- 조직 유형 ------------------------------------------------------
        -- 사람이 속하는 단위만 남긴다. 창고는 조직이 아니라 플랜트 하위의
        -- 구획이고, 오프라인 매장은 범위 밖이다(요구사항 1.2).
        ('ORG_TYPE',      'HQ',         '회사',           '본사 조직. 전사 데이터를 다룬다',          'violet', 10),
        ('ORG_TYPE',      'DC',         '물류센터',       '입출고를 수행하는 센터 조직',              'teal',   20),

        -- 플랜트 유형 ----------------------------------------------------
        ('PLANT_TYPE',    'DC',         '물류센터',       '보관 · 입출고를 모두 수행',                'teal',   10),
        ('PLANT_TYPE',    'RC',         '반품센터',       '반품 입고 · 검사 · 재판매 판정 전담',      'amber',  20),
        ('PLANT_TYPE',    'XD',         '크로스독',       '보관 없이 통과 (입고 즉시 출고)',          'blue',   30),

        -- 창고 유형 ------------------------------------------------------
        -- 재고의 판매가능 여부를 가르는 값이라 임의로 늘리면 안 된다.
        ('WH_TYPE',       'GOOD',       '양품',           '판매가능 재고',                            'green',  10),
        ('WH_TYPE',       'RETURN',     '반품',           '반품 입고 후 판정 대기',                   'amber',  20),
        ('WH_TYPE',       'DEFECT',     '불량',           '판매불가. 폐기 · 반송 대상',               'red',    30),

        -- 빈 유형 --------------------------------------------------
        ('LOC_TYPE',      'NORMAL',     '정상',           '일반 적치 · 피킹 빈',                'green',  10),
        ('LOC_TYPE',      'RETURN',     '반품',           '반품 전용 빈',                       'amber',  20),
        ('LOC_TYPE',      'DEFECT',     '불량',           '불량 격리 빈',                       'red',    30),
        ('LOC_TYPE',      'TRANSIT',    '운송중',         '이동 중 재고가 잠시 머무는 가상 빈', 'gray',   40),

        -- 사용자 상태 ----------------------------------------------------
        ('USER_STATUS',   'ACTIVE',     '정상',           '로그인 가능',                              'green',  10),
        ('USER_STATUS',   'LOCKED',     '잠김',           '비밀번호 연속 오류로 잠김. 관리자 해제 필요', 'red',  20),
        ('USER_STATUS',   'DORMANT',    '휴면',           '장기 미접속. 관리자 활성화 필요',          'amber',  30),
        ('USER_STATUS',   'RETIRED',    '퇴사',           '퇴사 처리. 로그인 불가',                   'gray',   40),

        -- 권한 모듈 ------------------------------------------------------
        ('PERM_MODULE',   'SYS',        '시스템설정',     '회사 · 조직 · 사용자 · 역할 · 코드 · 정책', 'slate', 10),
        ('PERM_MODULE',   'MST',        '기준정보',       '플랜트 · 창고 · 빈 · SKU · 채널 · 공급처', 'violet', 20),
        ('PERM_MODULE',   'PUR',        '구매',           '구매요청 · 발주',                          'amber',  30),
        ('PERM_MODULE',   'INB',        '입고',           '입하 · 검수 · 적치 · 확정',                'teal',   40),
        ('PERM_MODULE',   'OUT',        '출고',           '할당 · 피킹 · 패킹 · 결품',                'blue',   50),
        ('PERM_MODULE',   'INV',        '재고/실사',      '실사 · 재고조정',                          'green',  60),
        ('PERM_MODULE',   'QRY',        '조회',           '주문 · 배송 · 재고 조회',                  'cyan',   70),
        ('PERM_MODULE',   'AUD',        '감사/분석',      '이력 · 로그 · KPI · 다운로드',             'gray',   80),

        -- 권한 액션 ------------------------------------------------------
        ('PERM_ACTION',   'R',          '조회',           '다른 액션의 전제 조건',                    'gray',   10),
        ('PERM_ACTION',   'C',          '등록',           '신규 데이터 생성',                         'blue',   20),
        ('PERM_ACTION',   'U',          '수정',           '기존 데이터 변경',                         'amber',  30),
        ('PERM_ACTION',   'D',          '삭제',           '데이터 삭제 (물리/논리)',                  'red',    40),
        ('PERM_ACTION',   'A',          '승인',           '결재 · 승인 처리',                         'violet', 50),
        ('PERM_ACTION',   'X',          '다운로드',       '파일 내려받기',                            'teal',   60),

        -- 데이터 범위 ----------------------------------------------------
        ('DATA_SCOPE',    'ALL',        '전사',           '모든 조직의 데이터',                       'violet', 10),
        ('DATA_SCOPE',    'OWN_ORG',    '소속 조직',      '소속 조직과 역할조직범위에 등록된 조직',   'teal',   20),
        ('DATA_SCOPE',    'OWN_DATA',   '본인 데이터',    '본인이 등록한 데이터만',                   'gray',   30),

        -- 정책 유형 ------------------------------------------------------
        ('POLICY_TYPE',   'DENY',       '금지',           '해당 기능/필드의 실행을 막는다',           'red',    10),
        ('POLICY_TYPE',   'REQUIRED',   '필수입력',       '지정 필드가 비어 있으면 저장 불가',        'amber',  20),
        ('POLICY_TYPE',   'CONDITION',  '조건충족',       '조건식이 참일 때만 실행 허용',             'blue',   30),
        ('POLICY_TYPE',   'SOD',        '직무분리',       '요청자와 승인자가 동일인이면 제재',        'violet', 40),
        ('POLICY_TYPE',   'SCOPE',      '범위제한',       '소속 범위 밖의 데이터 차단',               'teal',   50),
        ('POLICY_TYPE',   'LIMIT',      '승인한도',       '금액/수량 한도 초과 시 상위 승인 필요',    'pink',   60),
        ('POLICY_TYPE',   'READONLY',   '읽기전용',       '조회만 허용, 모든 변경 차단',              'gray',   70),
        ('POLICY_TYPE',   'MASKING',    '마스킹',         '개인정보 항목을 가려서 노출',              'cyan',   80),

        -- 정책 적용강도 --------------------------------------------------
        ('ENFORCE_LEVEL', 'BLOCK',      '차단',           '저장 · 실행을 거부한다',                   'red',    10),
        ('ENFORCE_LEVEL', 'APPROVAL',   '상위승인',       '상위 권한자의 승인으로 통과',              'amber',  20),
        ('ENFORCE_LEVEL', 'WARN',       '경고',           '확인 후 진행 가능 (권고)',                 'blue',   30),
        ('ENFORCE_LEVEL', 'LOG',        '기록만',         '차단 없이 감사로그만 남긴다',              'gray',   40),

        -- 감사 행위구분 --------------------------------------------------
        ('AUDIT_ACTION',  'CREATE',     '등록',           '신규 등록',                                'blue',   10),
        ('AUDIT_ACTION',  'UPDATE',     '수정',           '변경 (전후 값 기록)',                      'amber',  20),
        ('AUDIT_ACTION',  'DELETE',     '삭제',           '삭제',                                     'red',    30),
        ('AUDIT_ACTION',  'LOGIN',      '로그인',         '로그인 성공',                              'green',  40),
        ('AUDIT_ACTION',  'LOGIN_FAIL', '로그인 실패',    '자격 오류 · 상태 차단',                    'red',    50),
        ('AUDIT_ACTION',  'LOGOUT',     '로그아웃',       '세션 종료',                                'gray',   60),
        -- 관리자 초기화와 본인 변경을 나눈다. 감사에서 "누가 바꿨는가" 가 다르다.
        ('AUDIT_ACTION',  'PWD_RESET',  '비밀번호 초기화', '관리자에 의한 초기화',                    'violet', 70),
        ('AUDIT_ACTION',  'PWD_CHANGE', '비밀번호 변경',  '사용자 본인이 변경',                       'violet', 75),
        ('AUDIT_ACTION',  'DOWNLOAD',   '다운로드',       '데이터 내려받기',                          'teal',   80),

        -- 업로드 대상 ----------------------------------------------------
        -- 여기 있는 값은 서버에 대응하는 UploadTarget 구현이 있어야 한다.
        -- 구현 없이 코드만 넣으면 화면에 고를 수 있는 항목으로 뜨고 실패한다.
        ('UPLOAD_TARGET', 'ORG',        '조직',           '회사 하위의 본사 · 센터 조직',             'blue',   10),
        ('UPLOAD_TARGET', 'PLANT',      '플랜트',         '물류센터 · 반품센터 · 크로스독',           'violet', 20),
        ('UPLOAD_TARGET', 'WAREHOUSE',  '창고',           '플랜트 내 양품 · 반품 · 불량 구획',        'teal',   30),
        ('UPLOAD_TARGET', 'LOCATION',   '빈',       '빈. 수백 건을 한 번에 올린다',             'green',  40),
        ('UPLOAD_TARGET', 'PERMISSION', '권한',           '기능 권한과 허용 액션',                    'amber',  50),
        ('UPLOAD_TARGET', 'CODE',       '공통코드',       '코드그룹 하위의 코드값',                   'cyan',   60),

        -- 업로드 결과 ----------------------------------------------------
        ('UPLOAD_STATUS', 'SUCCESS',    '전건 성공',      '모든 행이 반영되었다',                     'green',  10),
        ('UPLOAD_STATUS', 'PARTIAL',    '부분 성공',      '정상 행만 반영하고 오류 행은 남겼다',      'amber',  20),
        ('UPLOAD_STATUS', 'FAILED',     '전건 실패',      '반영된 행이 없다',                         'red',    30),

        -- 사용여부 ------------------------------------------------------
        ('USE_YN',        'Y',          '사용',           NULL,                                       'green',  10),
        ('USE_YN',        'N',          '미사용',         NULL,                                       'gray',   20)
       ) AS v(code_group_id, code_id, code_name, description, attr1, sort_order)
  JOIN tb_code_group g ON g.code_group_id = v.code_group_id;
