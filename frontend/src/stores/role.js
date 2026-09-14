/**
 * 역할 스토어 — 실제 서버의 역할 목록.
 *
 * 역할 화면뿐 아니라 사용자 화면의 역할 배정 목록도 여기서 읽는다.
 * 조직과 같은 이유다 — 화면마다 출처가 다르면 한쪽에만 있는 역할을 골라
 * 저장할 때마다 서버에 거부당한다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as roleApi from '@/api/role.js'

export const useRoleStore = defineStore('role', () => {
  const roles = ref([])
  const loading = ref(false)
  const loaded = ref(false)
  /** 조회 권한이 없어 목록을 받지 못한 경우의 사유 */
  const denyReason = ref('')

  async function load(force = false) {
    if (loaded.value && !force) return roles.value
    loading.value = true
    try {
      const page = await roleApi.list({ size: 0 })
      roles.value = page.rows
      denyReason.value = ''
      loaded.value = true
    } catch (e) {
      // 역할 조회 권한이 없는 사람도 사용자 화면에는 들어올 수 있다.
      // 화면 전체를 실패로 만들지 않고 빈 목록으로 둔다.
      roles.value = []
      denyReason.value = e.message
      loaded.value = true
    } finally {
      loading.value = false
    }
    return roles.value
  }

  const roleMap = computed(() => Object.fromEntries(roles.value.map((r) => [r.roleId, r])))

  const roleOptions = computed(() =>
    roles.value.map((r) => ({
      value: r.roleId,
      label: `${r.roleName} (${r.roleId})`,
      disabled: r.useYn !== 'Y',
    })),
  )

  const roleNameOf = (roleId) => roleMap.value[roleId]?.roleName ?? roleId

    /**
   * 다른 화면이 이 데이터를 바꿨을 때 낡음으로 표시한다.
   * 다음에 이 목록을 쓰는 화면이 열릴 때 load(false) 가 다시 읽는다.
   */
  function invalidate() {
    loaded.value = false
  }

return { roles, loading, loaded, denyReason, load, invalidate, roleMap, roleOptions, roleNameOf }
})
