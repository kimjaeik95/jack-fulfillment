/**
 * auth 모듈 — 로그인/인증 화면.
 *
 * meta.public: true 인 라우트는 인증 없이 접근할 수 있다.
 * 사이드바 메뉴에는 노출되지 않으므로 menuGroups 는 비어 있다.
 */
export const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/LoginView.vue'),
    meta: { title: '로그인', public: true },
  },
]

export const menuGroups = []
