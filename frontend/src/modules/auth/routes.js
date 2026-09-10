/**
 * auth 모듈 — 로그인/인증 화면.
 *
 * meta.public   인증 없이 접근할 수 있다.
 * meta.passwordChange
 *   초기 비밀번호를 바꾸지 않은 계정도 들어올 수 있는 화면.
 *   이 표시가 없는 화면은 라우터 가드가 비밀번호 변경 화면으로 돌려보낸다.
 *
 * 사이드바 메뉴에는 노출되지 않으므로 menuGroups 는 비어 있다.
 */
export const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/LoginView.vue'),
    meta: { title: '로그인', public: true },
  },
  {
    path: '/password',
    name: 'password-change',
    component: () => import('./views/PasswordChangeView.vue'),
    meta: { title: '비밀번호 변경', passwordChange: true },
  },
]

export const menuGroups = []
