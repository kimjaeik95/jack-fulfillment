/**
 * 조직 스토어 — 실제 서버의 조직 목록.
 *
 * 조직 화면뿐 아니라 사용자 화면의 소속 드롭다운도 여기서 읽는다.
 * 두 화면이 서로 다른 출처를 보면, 한쪽에서 만든 조직이 다른 쪽에는
 * 없는 상태가 되어 저장할 때마다 서버에 거부당한다.
 *
 * 조직은 수십 건 규모의 기준정보라 전체를 한 번 받아 두고,
 * 변경이 일어난 화면이 load() 를 다시 부른다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as orgApi from '@/api/org.js'

export const useOrgStore = defineStore('org', () => {
  const orgs = ref([])
  const loading = ref(false)
  const loaded = ref(false)
  /** 조회 권한이 없어 목록을 받지 못한 경우의 사유 */
  const denyReason = ref('')

  /**
   * @param {boolean} force 이미 읽었어도 다시 읽을지
   */
  async function load(force = false) {
    if (loaded.value && !force) return orgs.value
    loading.value = true
    try {
      const page = await orgApi.list({ size: 0 })
      orgs.value = page.rows
      denyReason.value = ''
      loaded.value = true
    } catch (e) {
      // 조직 조회 권한이 없는 역할도 사용자 화면에는 들어올 수 있다.
      // 그때 화면 전체를 실패로 만들지 않고 빈 목록으로 둔다.
      orgs.value = []
      denyReason.value = e.message
      loaded.value = true
    } finally {
      loading.value = false
    }
    return orgs.value
  }

  const orgMap = computed(() => Object.fromEntries(orgs.value.map((o) => [o.orgId, o])))

  const orgOptions = computed(() =>
    orgs.value.map((o) => ({
      value: o.orgId,
      label: `${o.orgName} (${o.orgId})`,
      disabled: o.useYn !== 'Y',
    })),
  )

  const orgNameOf = (orgId) => orgMap.value[orgId]?.orgName ?? orgId

  return { orgs, loading, loaded, denyReason, load, orgMap, orgOptions, orgNameOf }
})
