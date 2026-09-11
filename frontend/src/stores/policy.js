/**
 * 공통정책 스토어 — 실제 서버의 정책 목록.
 *
 * 정책 화면이 목록을 그리는 데 쓰고, 사이드바가 건수를 읽는다.
 * 화면 안에 두지 않고 스토어로 뺀 이유는 조직·역할·권한과 같다 —
 * 같은 목록을 두 곳이 각자 받아 오면 한쪽만 낡은 상태가 된다.
 *
 * 정책은 "무엇을 할 수 있는가"가 아니라 "할 수 있는데 이런 조건에서는
 * 막거나 승인을 받아라"다. 로그인 시 사용중인 정책이 세션에 실려 판정에
 * 쓰이므로, 여기서 저장한 규칙이 곧 그 사람이 실제로 막히는 지점이 된다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as policyApi from '@/api/policy.js'

export const usePolicyStore = defineStore('policy', () => {
  const policies = ref([])
  const loading = ref(false)
  const loaded = ref(false)
  /** 조회 권한이 없어 목록을 받지 못한 경우의 사유 */
  const denyReason = ref('')

  async function load(force = false) {
    if (loaded.value && !force) return policies.value
    loading.value = true
    try {
      // 정책은 역할 수에 비례해 늘지만 아직 수십 건 규모라 전체를 받는다.
      const page = await policyApi.list({ size: 0 })
      policies.value = page.rows
      denyReason.value = ''
      loaded.value = true
    } catch (e) {
      // 정책 조회 권한이 없는 사람도 다른 화면에는 들어온다.
      // 부팅 전체를 실패로 만들지 않고 빈 목록으로 둔다.
      policies.value = []
      denyReason.value = e.message
      loaded.value = true
    } finally {
      loading.value = false
    }
    return policies.value
  }

  /** 한 역할에 걸린 사용중 정책 수 — 역할 화면의 '제한/승인' 표시에 쓴다 */
  const activeCountOf = (roleId) =>
    policies.value.filter((p) => p.roleId === roleId && p.useYn === 'Y').length

  const activeCount = computed(() => policies.value.filter((p) => p.useYn === 'Y').length)

  return { policies, loading, loaded, denyReason, load, activeCountOf, activeCount }
})
