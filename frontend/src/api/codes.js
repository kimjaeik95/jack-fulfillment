/**
 * 공통코드 — 화면의 셀렉트박스 / 배지 라벨.
 *
 * 값은 서버(tb_code_group / tb_code)에서 온다. 예전에는 이 파일에 상수로
 * 적어 뒀는데, 그러면 공통코드 관리 화면에서 코드를 고쳐도 쓰는 곳이 모른다.
 * 관리 화면의 목적 자체가 무력화되므로 서버를 단일 출처로 삼는다.
 *
 * 함수 이름과 인자는 그대로 뒀다. 이 파일을 쓰는 곳이 10개 파일 · 57곳이라,
 * 계약을 바꾸면 그 전부를 함께 고쳐야 한다.
 *
 * CODE_GROUPS 는 reactive 객체다. load() 가 내용을 채우면 이를 읽는
 * computed 가 자동으로 다시 계산된다. App 이 부팅 중에 load() 를 부르고
 * 그때까지 RouterView 를 띄우지 않으므로, 화면이 빈 목록을 보는 일은 없다.
 */
import { reactive } from 'vue'
import * as codeApi from './code.js'

/**
 * { PERM_MODULE: [{ code, label, color, desc }], ... }
 *
 * 서버의 codeId/codeName/color/description 을 화면이 쓰던 이름으로 옮긴다.
 * 이름을 맞추는 편이 호출부 57곳을 고치는 것보다 싸다.
 */
export const CODE_GROUPS = reactive({})

let loaded = false

/**
 * 서버에서 코드를 받아 채운다.
 *
 * @param {boolean} force 이미 받았어도 다시 받을지 (코드를 고친 뒤)
 */
export async function loadCodes(force = false) {
  if (loaded && !force) return CODE_GROUPS

  const groups = await codeApi.lookup()

  // 기존 키를 지우고 새로 채운다. 그룹이 삭제된 경우 남아 있으면 안 된다.
  for (const key of Object.keys(CODE_GROUPS)) delete CODE_GROUPS[key]
  for (const group of groups) {
    CODE_GROUPS[group.codeGroupId] = group.codes.map((c) => ({
      code: c.codeId,
      label: c.codeName,
      color: c.color ?? 'gray',
      desc: c.description ?? null,
    }))
  }
  loaded = true
  return CODE_GROUPS
}

/** 코드를 받았는지 — 화면이 빈 목록과 미로딩을 구분해야 할 때 */
export function codesLoaded() {
  return loaded
}

/** 그룹 내 코드 라벨 조회 */
export function codeLabel(group, code) {
  const found = (CODE_GROUPS[group] || []).find((c) => c.code === code)
  return found ? found.label : (code ?? '-')
}

/** 그룹 내 코드 색상 조회 */
export function codeColor(group, code) {
  const found = (CODE_GROUPS[group] || []).find((c) => c.code === code)
  return found ? found.color : 'gray'
}

/** 그룹 내 코드 상세 조회 */
export function codeItem(group, code) {
  return (CODE_GROUPS[group] || []).find((c) => c.code === code) || null
}

/** 셀렉트박스용 옵션 배열 */
export function codeOptions(group) {
  return (CODE_GROUPS[group] || []).map((c) => ({ value: c.code, label: c.label }))
}
