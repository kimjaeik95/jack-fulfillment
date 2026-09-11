/**
 * 메뉴 스토어 — 사이드바 구성 (COM-PG-005).
 *
 * 메뉴는 이제 서버가 소유한다. 무엇이 보이는지도 서버가 역할로 판정하므로
 * 화면은 받은 대로 그린다.
 *
 * 다만 사이드바가 비면 사용자는 어떤 화면에도 들어갈 수 없다. 그래서 메뉴를
 * 받지 못했을 때만 라우터에 등록된 화면으로 최소 메뉴를 만들어 띄운다.
 * 메뉴 테이블이 잘못돼도 최소한 메뉴 관리 화면까지는 갈 수 있어야 복구가 된다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as menuApi from '@/api/menu.js'
import { router } from '@/router/index.js'

export const useMenuStore = defineStore('menu', () => {
  /** 그룹 → 항목 2단 */
  const groups = ref([])
  const loading = ref(false)
  const loaded = ref(false)
  /** 메뉴를 받지 못한 사유. 비어 있으면 정상이다. */
  const loadError = ref('')
  /** 지금 보고 있는 메뉴가 서버 것인지, 비상용으로 만든 것인지 */
  const fallback = ref(false)

  async function load(force = false) {
    if (loaded.value && !force) return groups.value
    loading.value = true
    try {
      groups.value = await menuApi.my()
      loadError.value = ''
      fallback.value = false
      // 사용중인 메뉴가 하나도 없으면 화면에 들어갈 방법이 없다
      if (!groups.value.some((g) => g.children?.length)) {
        groups.value = fallbackGroups()
        fallback.value = true
        loadError.value = '표시할 메뉴가 없어 기본 메뉴로 대체했습니다. 메뉴 관리에서 확인하세요.'
      }
    } catch (e) {
      groups.value = fallbackGroups()
      fallback.value = true
      loadError.value = `메뉴를 불러오지 못해 기본 메뉴로 대체했습니다. ${e.message}`
    } finally {
      loading.value = false
      loaded.value = true
    }
    return groups.value
  }

  /**
   * 비상용 메뉴 — 라우터에 등록된 화면을 한 그룹에 모아 보여준다.
   * 순서도 분류도 없지만, 적어도 메뉴 관리 화면까지 갈 수 있다.
   */
  function fallbackGroups() {
    const items = router
      .getRoutes()
      .filter((r) => r.name && !r.meta?.public && !r.meta?.passwordChange)
      .map((r) => ({
        menuId: `FALLBACK_${r.name}`,
        menuName: r.meta?.title ?? r.name,
        routeName: r.name,
        icon: '•',
        permId: r.meta?.perm ?? null,
        children: [],
      }))
    return items.length
      ? [{ menuId: 'FALLBACK', menuName: '전체 화면', children: items }]
      : []
  }

  /** 메뉴를 고친 뒤 사이드바에 바로 반영하려면 이걸 부른다 */
  const reload = () => load(true)

  /** 사이드바 건수 배지를 붙일 수 있는 라우트 목록 */
  const routeNames = computed(() =>
    groups.value.flatMap((g) => (g.children ?? []).map((m) => m.routeName)),
  )

  return { groups, loading, loaded, loadError, fallback, load, reload, routeNames }
})
