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

export const orgs = (params = {}) => download('/exports/orgs', params)
export const roles = (params = {}) => download('/exports/roles', params)
export const permissions = (params = {}) => download('/exports/permissions', params)
export const users = (params = {}) => download('/exports/users', params)
export const policies = (params = {}) => download('/exports/policies', params)
export const codes = (params = {}) => download('/exports/codes', params)

/**
 * 감사 이력만 경로가 다르다.
 * 조건(AuditLogSearch)과 요구 권한(AUD_DOWNLOAD/X)이 달라 그 화면이 먼저
 * 자기 경로를 갖고 있었다.
 */
export const auditLogs = (params = {}) => download('/audit-logs/export', params)
