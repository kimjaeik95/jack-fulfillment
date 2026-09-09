/**
 * 공통코드 정의.
 * 화면의 셀렉트박스 / 배지 라벨은 모두 이 파일을 참조한다.
 * 실제 시스템에서는 공통코드 테이블(TB_COM_CODE)에서 내려받는 값이며,
 * 여기서는 그 구조(그룹 - 코드 - 라벨 - 색상)를 그대로 흉내낸다.
 */

/** 코드그룹 정의 */
export const CODE_GROUPS = {
  /** 권한 모듈(대분류) */
  PERM_MODULE: [
    { code: 'SYS', label: '시스템설정', color: 'slate' },
    { code: 'MST', label: '기준정보', color: 'violet' },
    { code: 'PUR', label: '구매', color: 'amber' },
    { code: 'INB', label: '입고', color: 'teal' },
    { code: 'OUT', label: '출고', color: 'blue' },
    { code: 'INV', label: '재고/실사', color: 'green' },
    { code: 'STR', label: '매장', color: 'pink' },
    { code: 'QRY', label: '조회', color: 'cyan' },
    { code: 'AUD', label: '감사/분석', color: 'gray' },
  ],

  /** 권한 액션 유형 */
  PERM_ACTION: [
    { code: 'R', label: '조회', color: 'gray' },
    { code: 'C', label: '등록', color: 'blue' },
    { code: 'U', label: '수정', color: 'amber' },
    { code: 'D', label: '삭제', color: 'red' },
    { code: 'A', label: '승인', color: 'violet' },
    { code: 'X', label: '다운로드', color: 'teal' },
  ],

  /** 정책(제한/승인) 유형 */
  POLICY_TYPE: [
    { code: 'DENY', label: '금지', color: 'red', desc: '해당 기능/필드에 대한 실행을 막는다.' },
    { code: 'REQUIRED', label: '필수입력', color: 'amber', desc: '지정 필드가 비어 있으면 저장 불가.' },
    { code: 'CONDITION', label: '조건충족', color: 'blue', desc: '조건식이 참일 때만 실행 허용.' },
    { code: 'SOD', label: '직무분리', color: 'violet', desc: '요청자와 승인자가 동일인이면 제재.' },
    { code: 'SCOPE', label: '범위제한', color: 'teal', desc: '소속(센터/매장) 범위 밖의 데이터 차단.' },
    { code: 'LIMIT', label: '승인한도', color: 'pink', desc: '금액/수량 한도 초과 시 상위 승인 필요.' },
    { code: 'READONLY', label: '읽기전용', color: 'gray', desc: '조회만 허용, 모든 변경 차단.' },
    { code: 'MASKING', label: '마스킹', color: 'cyan', desc: '개인정보 항목을 마스킹하여 노출.' },
  ],

  /** 정책 적용 강도 */
  ENFORCE_LEVEL: [
    { code: 'BLOCK', label: '차단', color: 'red', desc: '저장/실행을 거부한다.' },
    { code: 'APPROVAL', label: '상위승인', color: 'amber', desc: '상위 권한자의 승인으로 통과.' },
    { code: 'WARN', label: '경고', color: 'blue', desc: '확인 팝업 후 진행 가능(권고).' },
    { code: 'LOG', label: '기록만', color: 'gray', desc: '차단 없이 감사로그만 남긴다.' },
  ],

  /** 조직 유형 */
  ORG_TYPE: [
    { code: 'HQ', label: '본사', color: 'violet' },
    { code: 'DC', label: '물류센터', color: 'teal' },
    { code: 'STORE', label: '매장', color: 'pink' },
  ],

  /** 사용자 상태 */
  USER_STATUS: [
    { code: 'ACTIVE', label: '정상', color: 'green' },
    { code: 'LOCKED', label: '잠김', color: 'red' },
    { code: 'DORMANT', label: '휴면', color: 'amber' },
    { code: 'RETIRED', label: '퇴사', color: 'gray' },
  ],

  /** 사용여부 */
  USE_YN: [
    { code: 'Y', label: '사용', color: 'green' },
    { code: 'N', label: '미사용', color: 'gray' },
  ],
}

/** 그룹 내 코드 라벨 조회 */
export function codeLabel(group, code) {
  const found = (CODE_GROUPS[group] || []).find((c) => c.code === code)
  return found ? found.label : (code ?? '-')
}

/** 그룹 내 코드 색상 조회 */
export function codeColor(group, code) {
  const found = (CODE_GROUPS[group] || []).find((c) => c.code === code)
  return found ? found.color : 'gray'
}

/** 그룹 내 코드 상세 조회 */
export function codeItem(group, code) {
  return (CODE_GROUPS[group] || []).find((c) => c.code === code) || null
}

/** 셀렉트박스용 옵션 배열 */
export function codeOptions(group) {
  return (CODE_GROUPS[group] || []).map((c) => ({ value: c.code, label: c.label }))
}
