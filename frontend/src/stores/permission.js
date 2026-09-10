/**
 * 권한(기능) 스토어 — 실제 서버의 권한 목록.
 *
 * 권한 화면뿐 아니라 역할-권한 매핑 화면의 행 목록도 여기서 읽는다.
 * 조직·역할과 같은 이유다 — 화면마다 출처가 다르면 한쪽에만 있는 권한을
 * 두고 저장할 때마다 서버에 거부당한다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as permissionApi from '@/api/permission.js'

export const usePermissionStore = defineStore('permission', () => {
  const permissions = ref([])
  const loading = ref(false)
  const loaded = ref(false)
  /** 조회 권한이 없어 목록을 받지 못한 경우의 사유 */
  const denyReason = ref('')

  async function load(force = false) {
    if (loaded.value && !force) return permissions.value
    loading.value = true
    try {
      const page = await permissionApi.list({ size: 0 })
      permissions.value = page.rows
      denyReason.value = ''
      loaded.value = true
    } catch (e) {
      permissions.value = []
      denyReason.value = e.message
      loaded.value = true
    } finally {
      loading.value = false
    }
    return permissions.value
  }

  const permMap = computed(() =>
    Object.fromEntries(permissions.value.map((p) => [p.permId, p])),
  )

  const permOptions = computed(() =>
    permissions.value.map((p) => ({
      value: p.permId,
      label: `${p.permName} (${p.permId})`,
      disabled: p.useYn !== 'Y',
    })),
  )

  const permNameOf = (permId) => permMap.value[permId]?.permName ?? permId

  return { permissions, loading, loaded, denyReason, load, permMap, permOptions, permNameOf }
})
