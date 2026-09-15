import { createRouter, createWebHashHistory } from 'vue-router'
import { pinia } from '@/stores/index.js'
import { useSessionStore } from '@/stores/session.js'
import * as auth from '@/modules/auth/routes.js'
import * as system from '@/modules/system/routes.js'
import * as master from '@/modules/master/routes.js'
import * as inventory from '@/modules/inventory/routes.js'
import * as purchase from '@/modules/purchase/routes.js'

/**
 * 업무 모듈 등록.
 *
 * 새 업무 영역을 추가할 때는 모듈 폴더에 routes.js 를 만들고 여기에 한 줄 추가한다.
 *   import * as oms from '@/modules/oms/routes.js'
 *   const modules = [auth, system, oms]
 *
 * 각 모듈은 자기 라우트(routes)와 사이드바 메뉴(menuGroups)를 소유한다.
 * 등록 순서가 메뉴 표시 순서가 된다.
 */
const modules = [auth, system, master, inventory, purchase]

/** 사이드바가 사용하는 메뉴 정의 (모듈별 그룹을 순서대로 이어붙인다) */
export const menuGroups = modules.flatMap((m) => m.menuGroups ?? [])

const routes = [
  ...modules.flatMap((m) => m.routes ?? []),
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

/**
 * 인증 가드.
 * 미인증 상태로 관리 화면에 진입하면 로그인 화면으로 보내고, 원래 목적지를
 * redirect 쿼리로 넘겨 로그인 후 그대로 이동시킨다.
 */
router.beforeEach(async (to) => {
  const session = useSessionStore(pinia)
  await session.ensureReady()

  if (to.meta.public) {
    // 이미 로그인한 상태에서 로그인 화면으로 오면 대시보드로 보낸다.
    return session.isAuthenticated ? { name: 'dashboard' } : true
  }
  if (!session.isAuthenticated) {
    return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }

  // 초기 비밀번호를 바꾸지 않은 계정은 변경 화면 외로 갈 수 없다.
  // 서버도 같은 규칙으로 API 를 막지만, 화면을 먼저 보내야 사용자가 무엇을
  // 해야 하는지 알 수 있다. 통제 자체는 서버가 한다.
  if (session.mustChangePassword && to.meta.passwordChange !== true) {
    return { name: 'password-change', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }

  // 바꿀 필요가 없는데 변경 화면으로 들어오는 것은 막지 않는다 (임의 변경 허용)
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? ''} | 풀필먼트 관리 시스템`
})

export default router
