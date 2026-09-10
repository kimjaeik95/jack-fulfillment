/**
 * HTTP 계층 — 백엔드 REST API 호출.
 *
 * 백엔드는 모든 응답을 아래 껍데기로 감싼다.
 *   성공 : { success: true,  data: ..., warning: "정책 경고문(있을 때만)" }
 *   실패 : { success: false, code: "IN_USE", message: "사용자에게 보여줄 사유" }
 *
 * 이 모듈은 껍데기를 벗겨 { data, warning } 을 돌려주고,
 * 실패는 ApiError 로 던져 화면이 message 를 그대로 노출할 수 있게 한다.
 *
 * 인증은 세션 쿠키(JSESSIONID)로 유지되므로 모든 요청에 credentials: 'include' 가 필요하다.
 * 변경 요청(POST/PUT/DELETE)은 CSRF 토큰을 헤더로 함께 보낸다.
 */

const BASE = '/api'

/** 백엔드가 내려준 업무 오류 */
export class ApiError extends Error {
  constructor(message, code, status) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }

  /** 로그인이 필요한 상태인지 (세션 만료 포함) */
  get isUnauthenticated() {
    return this.status === 401
  }

  /** 초기 비밀번호를 아직 바꾸지 않아 차단된 상태인지 */
  get isPasswordChangeRequired() {
    return this.code === 'PASSWORD_CHANGE_REQUIRED'
  }
}

/** 백엔드가 내려준 XSRF-TOKEN 쿠키를 읽는다 (HttpOnly 가 아니라 JS 로 읽을 수 있다) */
function readCsrfToken() {
  const matched = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return matched ? decodeURIComponent(matched[1]) : null
}

/**
 * CSRF 토큰이 없으면 발급 엔드포인트를 한 번 호출해 쿠키를 받아온다.
 * 로그인 전에도 호출할 수 있도록 /auth/csrf 는 인증 없이 열려 있다.
 */
async function ensureCsrfToken() {
  if (readCsrfToken()) return
  await fetch(`${BASE}/auth/csrf`, { credentials: 'include' })
}

/**
 * @param {string} path   '/auth/login' 처럼 /api 이후의 경로
 * @param {object} options
 *   method  기본 GET
 *   body    객체를 넘기면 JSON 으로 직렬화한다
 *   query   { key: value } — 값이 ''/null/undefined 이면 제외한다
 * @returns {Promise<{data: any, warning: string|null}>}
 */
export async function request(path, options = {}) {
  const { method = 'GET', body, query } = options

  let url = BASE + path
  if (query) {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) {
      if (value === '' || value === null || value === undefined) continue
      params.append(key, String(value))
    }
    const qs = params.toString()
    if (qs) url += `?${qs}`
  }

  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  // 조회가 아니면 CSRF 토큰이 필요하다
  if (method !== 'GET' && method !== 'HEAD') {
    await ensureCsrfToken()
    const token = readCsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let response
  try {
    response = await fetch(url, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    // 네트워크 자체가 닿지 않은 경우 — 서버가 꺼져 있거나 프록시 설정이 없다
    throw new ApiError('서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인하세요.', 'NETWORK_ERROR', 0)
  }

  const payload = await readJson(response)

  if (!response.ok || payload?.success === false) {
    throw new ApiError(
      payload?.message ?? `요청이 실패했습니다. (HTTP ${response.status})`,
      payload?.code ?? `HTTP_${response.status}`,
      response.status,
    )
  }

  return { data: payload?.data ?? null, warning: payload?.warning ?? null }
}

async function readJson(response) {
  if (response.status === 204) return null
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    // JSON 이 아닌 응답 — 프록시 오류 페이지 등
    return null
  }
}

export const get = (path, query) => request(path, { method: 'GET', query })
export const post = (path, body) => request(path, { method: 'POST', body })
export const put = (path, body) => request(path, { method: 'PUT', body })
export const del = (path, body) => request(path, { method: 'DELETE', body })
