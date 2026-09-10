/**
 * 세션 스토어.
 *
 * 인증과 권한 판정의 근거는 **서버가 내려준 값**이다.
 * 로그인 응답(/auth/login) 과 세션 복원(/auth/me) 이 아래를 함께 준다.
 *   grants   : { permId: [액션...] }  보유 역할의 합집합
 *   policies : 적용 중인 정책 목록
 *   readOnly / masked : 정책에서 파생된 플래그
 *
 * 화면의 버튼 활성 여부는 이 값으로 판단하고, 최종 판정은 서버가 다시 한다.
 * (화면에서 막아도 API 를 직접 호출하면 통과되므로 서버 판정이 진짜 통제다)
 *
 * 판정 순서 — 백엔드 PermissionChecker 와 동일하다.
 *   1) grants 에 해당 액션이 있는가            → 없으면 거부
 *   2) READONLY 정책이 걸린 계정인가 (조회 외)  → 거부
 *   3) 대상 권한에 BLOCK 강도 DENY 정책이 있는가 → 거부
 *   4) WARN / APPROVAL 정책이 있으면            → 허용하되 사유를 반환
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth.js'
import * as mockApi from '@/api/client.js'

const LAST_ID_KEY = 'wms-admin-last-id'

export const useSessionStore = defineStore('session', () => {
  /** 서버가 내려준 인증 정보. null 이면 미인증 */
  const me = ref(null)
  const loginWarning = ref('')
  const ready = ref(false)

  /**
   * 로그인 화면의 데모 계정 목록 — 아직 Mock 데이터를 쓴다.
   * 사용자 조회 API 가 만들어지면 그때 서버 데이터로 교체한다.
   * (백엔드 시드와 프론트 시드가 같은 원본이라 계정 목록이 일치한다)
   */
  const demoUsers = ref([])
  const demoRoles = ref([])

  /* ---------------------------------------------------------------- */
  /* 세션 준비                                                         */
  /* ---------------------------------------------------------------- */

  let readyPromise = null

  /** 라우터 가드에서 최초 1회만 세션을 확인하도록 보장한다 */
  function ensureReady() {
    readyPromise ??= restore()
    return readyPromise
  }

  /** 새로고침 후 세션 복원. 미인증이면 조용히 null 로 둔다. */
  async function restore() {
    await loadDemoAccounts()
    try {
      me.value = await authApi.me()
    } catch (e) {
      // 401 은 정상적인 "아직 로그인 안 함" 상태다
      if (!e.isUnauthenticated) {
        console.warn('[session] 세션 복원 실패:', e.message)
      }
      me.value = null
    } finally {
      ready.value = true
    }
  }

  /** 로그인 화면의 데모 계정 목록 (Mock) */
  async function loadDemoAccounts() {
    try {
      const [users, roles] = await Promise.all([
        mockApi.list('users', { size: 0 }),
        mockApi.list('roles', { size: 0 }),
      ])
      demoUsers.value = users.rows
      demoRoles.value = roles.rows
    } catch {
      demoUsers.value = []
      demoRoles.value = []
    }
  }

  /* ---------------------------------------------------------------- */
  /* 로그인 / 로그아웃                                                 */
  /* ---------------------------------------------------------------- */

  async function login(userId, password) {
    const result = await authApi.login(userId, password)
    me.value = result.me
    loginWarning.value = result.warning ?? ''
    localStorage.setItem(LAST_ID_KEY, result.me.userId)
    // 로그인 성공 후에는 세션 준비가 끝난 상태로 본다
    readyPromise = Promise.resolve()
    ready.value = true
    return result.me
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (e) {
      // 세션이 이미 끊긴 경우에도 화면 상태는 로그아웃으로 만든다
      console.warn('[session] 로그아웃 요청 실패:', e.message)
    }
    me.value = null
    loginWarning.value = ''
    readyPromise = null
  }


  /**
   * 본인 비밀번호 변경.
   * 성공하면 서버가 변경 강제 플래그를 해제한 인증 정보를 돌려주므로
   * 세션 상태를 그것으로 교체한다.
   */
  async function changePassword(currentPassword, newPassword, confirmPassword) {
    me.value = await authApi.changePassword(currentPassword, newPassword, confirmPassword)
    return me.value
  }
  /** 역할·권한·정책을 변경한 뒤 재로그인 없이 반영한다 */
  async function refreshGrants() {
    me.value = await authApi.refresh()
    return me.value
  }

  /* ---------------------------------------------------------------- */
  /* 파생 상태                                                         */
  /* ---------------------------------------------------------------- */

  const isAuthenticated = computed(() => me.value !== null)
  const currentUserId = computed(() => me.value?.userId ?? null)
  const currentUser = computed(() => me.value)

  const myRoleIds = computed(() => me.value?.roleIds ?? [])
  const myRoleNames = computed(() => me.value?.roleNames ?? [])
  /** { permId: [액션...] } */
  const myGrants = computed(() => me.value?.grants ?? {})
  const myPolicies = computed(() => me.value?.policies ?? [])

  const isReadOnly = computed(() => me.value?.readOnly === true)
  const isMasked = computed(() => me.value?.masked === true)
  /** 초기 비밀번호를 아직 바꾸지 않아 다른 기능이 막힌 상태 */
  const mustChangePassword = computed(() => me.value?.mustChangePassword === true)

  /* ---------------------------------------------------------------- */
  /* 권한 판정                                                         */
  /* ---------------------------------------------------------------- */

  /**
   * @returns {{allowed: boolean, reason: string|null, level: string|null, policyId: string|null}}
   */
  function check(permId, action = 'R') {
    if (!me.value) {
      return { allowed: false, reason: '로그인이 필요합니다.', level: 'BLOCK', policyId: null }
    }

    const actions = myGrants.value[permId]
    if (!actions || !actions.includes(action)) {
      const roles = myRoleNames.value.join(', ') || '역할 없음'
      return {
        allowed: false,
        reason: `'${roles}' 역할에는 이 기능(${permId}/${action}) 권한이 없습니다.`,
        level: 'BLOCK',
        policyId: null,
      }
    }

    if (action !== 'R') {
      const readOnly = myPolicies.value.find(
        (p) => p.policyType === 'READONLY' && p.enforceLevel === 'BLOCK',
      )
      if (readOnly) {
        return { allowed: false, reason: readOnly.message, level: 'BLOCK', policyId: readOnly.policyId }
      }

      const deny = myPolicies.value.find(
        (p) =>
          p.policyType === 'DENY' &&
          p.enforceLevel === 'BLOCK' &&
          (p.permId === null || p.permId === permId),
      )
      if (deny) {
        return { allowed: false, reason: deny.message, level: 'BLOCK', policyId: deny.policyId }
      }
    }

    const advisory = myPolicies.value.find(
      (p) => p.permId === permId && ['WARN', 'APPROVAL'].includes(p.enforceLevel),
    )
    if (advisory) {
      return {
        allowed: true,
        reason: advisory.message,
        level: advisory.enforceLevel,
        policyId: advisory.policyId,
      }
    }

    return { allowed: true, reason: null, level: null, policyId: null }
  }

  const can = (permId, action = 'R') => check(permId, action).allowed

  const denyReason = (permId, action = 'R') => {
    const result = check(permId, action)
    return result.allowed ? null : result.reason
  }

  /* ---------------------------------------------------------------- */
  /* 마스킹                                                            */
  /* ---------------------------------------------------------------- */

  /** 마스킹 정책이 적용된 계정이면 개인정보를 가린다 */
  function mask(value, kind = 'text') {
    if (!isMasked.value || !value) return value ?? ''
    const s = String(value)
    if (kind === 'email') {
      const [id, domain] = s.split('@')
      if (!domain) return `${s.slice(0, 2)}***`
      return `${id.slice(0, 2)}***@${domain}`
    }
    if (kind === 'phone') return s.replace(/(\d{2,3})-(\d{3,4})-(\d{4})/, '$1-****-$3')
    if (kind === 'name') {
      return s.length <= 2 ? `${s[0]}*` : `${s[0]}${'*'.repeat(s.length - 2)}${s.at(-1)}`
    }
    return s.length <= 2 ? '**' : `${s.slice(0, 2)}${'*'.repeat(s.length - 2)}`
  }

  /** 아이디 저장 기능이 기억해 둔 마지막 로그인 ID */
  const lastLoginId = () => localStorage.getItem(LAST_ID_KEY) ?? ''

  return {
    // 상태
    me, ready, loginWarning, demoUsers, demoRoles,
    // 파생
    isAuthenticated, currentUserId, currentUser,
    myRoleIds, myRoleNames, myGrants, myPolicies,
    isReadOnly, isMasked, mustChangePassword,
    // 액션
    ensureReady, restore, loadDemoAccounts, login, logout, refreshGrants, changePassword,
    check, can, denyReason, mask, lastLoginId,
  }
})
