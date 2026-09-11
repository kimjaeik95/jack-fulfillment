/**
 * 목록 다운로드 API (COM-PG-011).
 *
 * 화면이 보고 있는 검색 조건을 그대로 넘긴다. 조건 없이 전체를 받으면
 * "화면엔 3건인데 파일엔 50건"이 되어, 받은 사람이 무엇을 보고 있는지 알 수 없다.
 *
 * 조회 권한(R)만으로는 내려받을 수 없다. 서버가 다운로드 액션(X)을 따로
 * 판정하므로, 권한이 없으면 사유가 담긴 오류가 돌아온다.
 */
import { download } from './http.js'

/**
 * 형식은 xlsx(기본) 또는 csv.
 * 기본을 엑셀로 둔 이유는 받은 파일을 채워 다시 올리는 흐름 때문이다 —
 * CSV 로 주면 엑셀에서 열어 고친 뒤 "CSV 로 다시 저장"을 해야 한다.
 */
export const orgs = (params = {}, format) => download('/exports/orgs', { ...params, format })
export const roles = (params = {}, format) => download('/exports/roles', { ...params, format })
export const permissions = (params = {}, format) =>
  download('/exports/permissions', { ...params, format })
export const users = (params = {}, format) => download('/exports/users', { ...params, format })
export const policies = (params = {}, format) =>
  download('/exports/policies', { ...params, format })
export const codes = (params = {}, format) => download('/exports/codes', { ...params, format })

/**
 * 감사 이력만 경로가 다르다.
 * 조건(AuditLogSearch)과 요구 권한(AUD_DOWNLOAD/X)이 달라 그 화면이 먼저
 * 자기 경로를 갖고 있었다.
 */
export const auditLogs = (params = {}) => download('/audit-logs/export', params)
