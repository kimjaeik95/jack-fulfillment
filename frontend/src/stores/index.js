import { createPinia } from 'pinia'

/**
 * 앱 전체가 공유하는 단일 Pinia 인스턴스.
 *
 * 라우터 가드는 컴포넌트 밖에서 실행되므로 활성 인스턴스가 없을 수 있다.
 * 스토어를 명시적으로 이 인스턴스에 묶기 위해 모듈로 분리해서 내보낸다.
 *   useSessionStore(pinia)
 */
export const pinia = createPinia()
