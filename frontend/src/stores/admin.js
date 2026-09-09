/**
 * 기준정보 스토어.
 * 조직 / 역할 / 권한 / 역할-권한매핑 / 공통정책 / 사용자 / 감사로그를
 * 한 곳에서 로딩·캐싱하고 CRUD 액션을 노출한다.
 *
 * 화면(View)은 이 스토어만 호출하고 api/client 를 직접 부르지 않는다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as api from '@/api/client.js'
import { useSessionStore } from './session.js'

export const useAdminStore = defineStore('admin', () => {
  const orgs = ref([])
  const roles = ref([])
  const permissions = ref([])
  const rolePermissions = ref([])
  const policies = ref([])
  const users = ref([])
  const auditLogs = ref([])

  const loading = ref(false)
  const loaded = ref(false)

  const ENTITY_REFS = { orgs, roles, permissions, rolePermissions, policies, users, auditLogs }

  /* ---------------------------------------------------------------- */
  /* 조회                                                             */
  /* ---------------------------------------------------------------- */

  /** 전체 기준정보 로드 (앱 진입 시 1회, 이후 변경 시 해당 엔터티만 갱신) */
  async function loadAll(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      const keys = Object.keys(ENTITY_REFS)
      const results = await Promise.all(keys.map((k) => api.list(k, { size: 0 })))
      keys.forEach((k, i) => {
        ENTITY_REFS[k].value = results[i].rows
      })
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function reload(entity) {
    const target = ENTITY_REFS[entity]
    if (!target) return
    const { rows } = await api.list(entity, { size: 0 })
    target.value = rows
  }

  /* ---------------------------------------------------------------- */
  /* 변경                                                             */
  /* ---------------------------------------------------------------- */

  function actor() {
    const session = useSessionStore()
    return session.currentUser ? { userId: session.currentUser.userId, userName: session.currentUser.userName } : null
  }

  /**
   * 인증·권한 판정의 근거는 서버(/auth/me)가 준 값이므로,
   * Mock 데이터를 고쳐도 현재 세션의 권한은 바뀌지 않는다.
   *
   * 여기서는 로그인 화면의 데모 계정 목록만 갱신한다.
   * 실제 권한을 다시 받아야 할 때는 session.refreshGrants() 를 쓴다.
   */
  async function syncSession(entity) {
    if (entity === 'users' || entity === 'roles') {
      await useSessionStore().loadDemoAccounts()
    }
  }

  async function createRow(entity, payload) {
    const row = await api.create(entity, payload, actor())
    await Promise.all([reload(entity), reload('auditLogs')])
    await syncSession(entity)
    return row
  }

  async function updateRow(entity, key, payload) {
    const row = await api.update(entity, key, payload, actor())
    await Promise.all([reload(entity), reload('auditLogs')])
    await syncSession(entity)
    return row
  }

  async function removeRow(entity, key) {
    await api.remove(entity, key, actor())
    const affected = entity === 'roles' ? [entity, 'rolePermissions', 'auditLogs'] : [entity, 'auditLogs']
    await Promise.all(affected.map(reload))
    await syncSession(entity)
    return true
  }

  async function saveRolePermissions(roleId, grants) {
    await api.saveRolePermissions(roleId, grants, actor())
    await Promise.all([reload('rolePermissions'), reload('auditLogs')])
    await syncSession('rolePermissions')
    return true
  }

  /** 관리자에 의한 비밀번호 초기화 (+ 잠금 해제) */
  async function resetUserPassword(userId) {
    const result = await api.resetPassword(userId, actor())
    await Promise.all([reload('users'), reload('auditLogs')])
    await syncSession('users')
    return result
  }

  async function resetAll() {
    await api.resetDb()
    await loadAll(true)
    // Mock 데이터만 초기화된다. 로그인 세션은 서버가 관리하므로 유지된다.
    await useSessionStore().loadDemoAccounts()
  }

  /* ---------------------------------------------------------------- */
  /* 파생 데이터                                                        */
  /* ---------------------------------------------------------------- */

  const roleMap = computed(() => Object.fromEntries(roles.value.map((r) => [r.roleId, r])))
  const permMap = computed(() => Object.fromEntries(permissions.value.map((p) => [p.permId, p])))
  const orgMap = computed(() => Object.fromEntries(orgs.value.map((o) => [o.orgId, o])))

  const roleOptions = computed(() =>
    [...roles.value]
      .sort((a, b) => (a.sortOrder ?? 999) - (b.sortOrder ?? 999))
      .map((r) => ({ value: r.roleId, label: `${r.roleName} (${r.roleId})`, disabled: r.useYn !== 'Y' })),
  )

  const permOptions = computed(() =>
    permissions.value.map((p) => ({ value: p.permId, label: `${p.permName} (${p.permId})` })),
  )

  const orgOptions = computed(() =>
    orgs.value.map((o) => ({ value: o.orgId, label: `${o.orgName} (${o.orgId})`, disabled: o.useYn !== 'Y' })),
  )

  /** 역할별 권한 매핑 조회 */
  function grantsOf(roleId) {
    return rolePermissions.value.filter((m) => m.roleId === roleId)
  }

  /** 역할별 권한 매핑을 { permId: actions[] } 형태로 */
  function grantMapOf(roleId) {
    return Object.fromEntries(grantsOf(roleId).map((m) => [m.permId, [...m.actions]]))
  }

  /** 권한을 보유한 역할 목록 */
  function rolesOf(permId) {
    return rolePermissions.value
      .filter((m) => m.permId === permId)
      .map((m) => roleMap.value[m.roleId])
      .filter(Boolean)
  }

  /** 역할에 걸린 정책 */
  function policiesOf(roleId) {
    return policies.value.filter((p) => p.roleId === roleId)
  }

  /** 역할을 배정받은 사용자 수 */
  function userCountOf(roleId) {
    return users.value.filter((u) => u.roleIds?.includes(roleId)).length
  }

  const roleNameOf = (roleId) => roleMap.value[roleId]?.roleName ?? roleId
  const permNameOf = (permId) => permMap.value[permId]?.permName ?? permId
  const orgNameOf = (orgId) => orgMap.value[orgId]?.orgName ?? orgId

  return {
    orgs, roles, permissions, rolePermissions, policies, users, auditLogs,
    loading, loaded,
    loadAll, reload,
    createRow, updateRow, removeRow, saveRolePermissions, resetUserPassword, resetAll,
    roleMap, permMap, orgMap,
    roleOptions, permOptions, orgOptions,
    grantsOf, grantMapOf, rolesOf, policiesOf, userCountOf,
    roleNameOf, permNameOf, orgNameOf,
  }
})
