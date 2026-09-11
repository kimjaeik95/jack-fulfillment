/**
 * 대량 업로드 API (COM-PG-010).
 *
 * 부분 성공을 전제한다. 정상 행은 반영되고 잘못된 행만 사유와 함께 남는다 —
 * 파일 전체가 완벽해야만 한 건이라도 들어가는 방식은 실무에서 쓸 수 없다.
 *
 * 파일 형식은 UTF-8 CSV 다. 엑셀에서 [다른 이름으로 저장] → [CSV UTF-8] 로
 * 저장하면 그대로 올라간다. xlsx 를 올리면 서버가 그 안내와 함께 거절한다.
 */
import { download, get, upload as uploadFile } from './http.js'

/**
 * 내가 올릴 수 있는 대상 목록.
 * 권한이 없는 대상은 애초에 내려오지 않으므로 화면이 따로 거를 필요가 없다.
 *
 * @returns {Promise<{type, label, permId, headers: string[], requiredHeaders: string[]}[]>}
 */
export async function targets() {
  const { data } = await get('/uploads/targets')
  return data
}

/** 빈 템플릿 내려받기 — 머리글 한 줄 + 예시 한 줄 */
export function downloadTemplate(type) {
  return download(`/uploads/targets/${encodeURIComponent(type)}/template`)
}

/**
 * 파일 올리기.
 *
 * @returns {Promise<{result: object, warning: string|null}>}
 *   result.successCount / failCount / errors[] 로 결과를 보여준다.
 *   실패가 있으면 warning 이 함께 온다.
 */
export async function upload(type, file) {
  const { data, warning } = await uploadFile(`/uploads/${encodeURIComponent(type)}`, file)
  return { result: data, warning }
}

/**
 * 업로드 이력.
 * 자기가 올린 것은 누구나 본다. 남의 것까지 보려면 변경 이력 조회 권한이 필요하다 —
 * 그 판정은 서버가 하므로 화면은 받은 대로 그리면 된다.
 */
export async function history(params = {}) {
  const { data } = await get('/uploads', {
    targetType: params.targetType,
    page: params.page,
    size: params.size,
  })
  return data
}

/** 실패 행만 담긴 CSV. 원문에 '오류사유' 열이 붙어 있어 고쳐서 다시 올릴 수 있다. */
export function downloadErrors(uploadSeq) {
  return download(`/uploads/${uploadSeq}/errors`)
}
