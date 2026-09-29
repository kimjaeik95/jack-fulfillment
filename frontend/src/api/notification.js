/**
 * 알림함 API (COM-PG-015).
 *
 * <b>알림은 소식이 아니라 할 일이다.</b> 기준은 '누군가 지금 뭔가를 해야
 * 하는가' 하나고, 그 일이 끝나면 저절로 닫힌다 — 사람이 지우는 것이 아니다.
 *
 * 그래서 만드는 경로도 지우는 경로도 없다. 만드는 것은 업무 서비스와 배치가
 * 하고, 여기서 할 수 있는 것은 보는 것과 읽음 표시뿐이다.
 *
 * <b>읽음과 닫힘은 다르다.</b> 읽음은 헤더 뱃지 숫자용이고, 목록에서
 * 사라지는 것은 닫힘이다. 배송실패를 읽기만 하고 재배송을 안 보냈으면 그
 * 알림은 남아 있어야 한다.
 */
import { get, post } from './http.js'

export const PAGE_SIZE = 50

export async function inbox(params = {}) {
  const { data } = await get('/notifications', {
    keyword: params.keyword,
    kind: params.kind,
    level: params.level,
    includeClosed: params.includeClosed,
    unreadOnly: params.unreadOnly,
    page: params.page,
    size: params.size,
  })
  return data
}

/**
 * 헤더 뱃지 숫자.
 *
 * 화면을 옮길 때만 부른다. 창고 업무는 초 단위가 아니라 결품이 3분 뒤에
 * 보여도 아무 일 없고, 실시간 채널을 들이면 그것부터 관리 대상이 된다.
 */
export async function unreadCount() {
  const { data } = await get('/notifications/unread')
  return data ?? 0
}

export async function markRead(notificationSeq) {
  return post(`/notifications/${notificationSeq}/read`)
}

/** 읽음일 뿐 닫는 것이 아니다. 할 일은 그대로 남는다 */
export async function markAllRead() {
  const { data } = await post('/notifications/read-all')
  return data ?? 0
}

/**
 * 지금 훑기.
 *
 * 배치는 새벽에 도는데, 방금 고친 것이 목록에서 빠졌는지 바로 보고 싶을
 * 때가 있다. 훑는 것은 읽기와 닫기뿐이라 위험하지 않다.
 */
export async function sweep() {
  const { data } = await post('/notifications/sweep')
  return data
}
