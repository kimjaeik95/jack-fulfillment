/**
 * Mock API 클라이언트.
 *
 * ─────────────────────────────────────────────────────────────
 *  실제 백엔드로 교체할 때는 이 파일만 바꾸면 된다.
 *  아래 5개 함수 시그니처(list/get/create/update/remove)를 유지한 채
 *  본문을 fetch 호출로 바꾸면 화면 코드는 손대지 않아도 된다.
 *
 *    export async function list(entity, params) {
 *      const qs = new URLSearchParams(params).toString()
 *      const res = await fetch(`/api/${entity}?${qs}`)
 *      if (!res.ok) throw new ApiError(await res.text())
 *      return res.json()
 *    }
 * ─────────────────────────────────────────────────────────────
 */
import * as SEED from './seed.js'
import { DEMO_PASSWORD, MAX_LOGIN_FAIL } from './seed.js'

export { DEMO_PASSWORD, MAX_LOGIN_FAIL }

const STORAGE_KEY = 'wms-admin-db-v1'
const LATENCY_MS = 180

/**
 * 저장 데이터 스키마 버전.
 * 시드 구조나 필드가 바뀌면 이 값을 올린다. 브라우저에 남아 있던 이전 버전
 * 데이터는 자동으로 폐기되고 시드로 다시 시작한다.
 *   2 — 사용자에 password / loginFailCount 추가 (로그인 기능)
 */
const SCHEMA_VERSION = 2

/** 업무 규칙 위반 등 사용자에게 보여줄 오류 */
export class ApiError extends Error {
  constructor(message, code = 'BIZ_ERROR') {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/** 엔터티 메타 — PK 필드와 라벨 */
const ENTITY_META = {
  orgs: { pk: 'orgId', label: '조직' },
  roles: { pk: 'roleId', label: '역할' },
  permissions: { pk: 'permId', label: '권한' },
  rolePermissions: { pk: '_id', label: '역할-권한' },
  policies: { pk: 'policyId', label: '공통정책' },
  users: { pk: 'userId', label: '사용자' },
  auditLogs: { pk: 'logId', label: '감사로그' },
}

/* ------------------------------------------------------------------ */
/* 저장소                                                              */
/* ------------------------------------------------------------------ */

function initialDb() {
  return {
    _schema: SCHEMA_VERSION,
    orgs: clone(SEED.ORGS),
    roles: clone(SEED.ROLES),
    permissions: clone(SEED.PERMISSIONS),
    // 매핑은 PK가 없으므로 합성키를 부여한다.
    rolePermissions: clone(SEED.ROLE_PERMISSIONS).map((m) => ({
      ...m,
      _id: `${m.roleId}::${m.permId}`,
    })),
    policies: clone(SEED.POLICIES),
    users: clone(SEED.USERS),
    auditLogs: [],
    _seq: 1000,
  }
}

let db = null

function load() {
  if (db) return db
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      const keysOk = Object.keys(ENTITY_META).every((k) => Array.isArray(parsed[k]))
      // 스키마가 바뀌면(예: 사용자에 password 필드 추가) 이전 저장 데이터를 쓸 수 없다.
      // 버전이 다르거나 키가 누락된 경우 시드로 다시 시작한다.
      if (keysOk && parsed._schema === SCHEMA_VERSION) {
        db = parsed
        return db
      }
      if (keysOk) {
        console.info(
          `[fulfillment] 저장된 데이터의 스키마(${parsed._schema ?? '없음'})가 현재 버전(${SCHEMA_VERSION})과 달라 초기 데이터로 재설정합니다.`,
        )
      }
    }
  } catch {
    /* 파싱 실패 시 시드로 재시작 */
  }
  db = initialDb()
  save()
  return db
}

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(db))
  } catch {
    /* 용량 초과 등은 무시 (메모리 상태는 유지) */
  }
}

function clone(v) {
  return JSON.parse(JSON.stringify(v))
}

/** 조회 응답·감사로그에 실려서는 안 되는 필드 */
const SECRET_FIELDS = ['password']

/** 비밀번호 등 비밀 정보를 제거한다. (서버 응답 규약과 동일하게 취급) */
function redact(row) {
  if (!row || typeof row !== 'object') return row
  const out = { ...row }
  for (const f of SECRET_FIELDS) delete out[f]
  return out
}

function delay(ms = LATENCY_MS) {
  return new Promise((r) => setTimeout(r, ms))
}

function nextSeq() {
  const d = load()
  d._seq += 1
  return d._seq
}

function nowStamp() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 저장소를 시드 상태로 되돌린다. */
export async function resetDb() {
  await delay(80)
  db = initialDb()
  save()
  return true
}

/* ------------------------------------------------------------------ */
/* 인증                                                                */
/*                                                                    */
/* 로그인 · 로그아웃은 실제 백엔드로 이전했다 → src/api/auth.js         */
/* 여기에는 관리자가 수행하는 비밀번호 초기화만 남는다.                 */
/* (사용자 관리 화면이 아직 Mock 이므로 함께 유지한다)                  */
/* ------------------------------------------------------------------ */


/** 관리자에 의한 비밀번호 초기화 (+ 잠금 해제) */
export async function resetPassword(userId, actor) {
  await delay()
  const d = load()
  const user = d.users.find((u) => u.userId === userId)
  if (!user) throw new ApiError(`사용자 정보를 찾을 수 없습니다. (${userId})`, 'NOT_FOUND')

  user.password = DEMO_PASSWORD
  user.loginFailCount = 0
  if (user.status === 'LOCKED') user.status = 'ACTIVE'
  user.updatedBy = actor?.userId ?? 'system'
  user.updatedAt = nowStamp()

  writeAudit('users', 'PWD_RESET', userId, { userId, userName: user.userName, status: user.status }, actor)
  save()
  return { initialPassword: DEMO_PASSWORD }
}

/* ------------------------------------------------------------------ */
/* 감사로그                                                            */
/* ------------------------------------------------------------------ */

function writeAudit(entity, action, targetKey, snapshot, actor) {
  const d = load()
  d.auditLogs.unshift({
    logId: `L${nextSeq()}`,
    entity,
    entityLabel: ENTITY_META[entity]?.label ?? entity,
    action, // CREATE | UPDATE | DELETE
    targetKey,
    actorId: actor?.userId ?? 'system',
    actorName: actor?.userName ?? '시스템',
    at: nowStamp(),
    // 감사로그에 비밀번호가 남지 않도록 제거한다.
    snapshot: snapshot ? redact(clone(snapshot)) : null,
  })
  // 로그는 최근 500건만 보관
  if (d.auditLogs.length > 500) d.auditLogs.length = 500
}

/* ------------------------------------------------------------------ */
/* 조회 / 검색 / 정렬 / 페이징                                            */
/* ------------------------------------------------------------------ */

/**
 * @param {string} entity
 * @param {object} params
 *   keyword      전체 텍스트 검색어
 *   filters      { field: value } 완전일치 필터 (값이 ''/null 이면 무시)
 *   inFilters    { field: [values] } 배열 컬럼 포함 검색
 *   sortBy,sortDir
 *   page,size    (size=0 이면 전체)
 */
export async function list(entity, params = {}) {
  await delay()
  const d = load()
  const source = d[entity]
  if (!source) throw new ApiError(`알 수 없는 엔터티: ${entity}`, 'NOT_FOUND')

  let rows = clone(source)

  // 완전일치 필터
  const filters = params.filters || {}
  for (const [field, value] of Object.entries(filters)) {
    if (value === '' || value === null || value === undefined) continue
    rows = rows.filter((r) => String(r[field] ?? '') === String(value))
  }

  // 배열 컬럼 포함 필터 (예: users.roleIds 에 특정 역할 포함)
  const inFilters = params.inFilters || {}
  for (const [field, value] of Object.entries(inFilters)) {
    if (!value) continue
    rows = rows.filter((r) => Array.isArray(r[field]) && r[field].includes(value))
  }

  // 키워드 검색
  const kw = (params.keyword || '').trim().toLowerCase()
  if (kw) {
    rows = rows.filter((r) =>
      Object.values(r).some((v) => {
        if (v === null || v === undefined) return false
        if (Array.isArray(v)) return v.join(',').toLowerCase().includes(kw)
        if (typeof v === 'object') return false
        return String(v).toLowerCase().includes(kw)
      }),
    )
  }

  // 정렬
  if (params.sortBy) {
    const dir = params.sortDir === 'desc' ? -1 : 1
    rows.sort((a, b) => {
      const av = a[params.sortBy]
      const bv = b[params.sortBy]
      if (av === bv) return 0
      if (av === null || av === undefined) return 1
      if (bv === null || bv === undefined) return -1
      if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir
      return String(av).localeCompare(String(bv), 'ko') * dir
    })
  }

  const total = rows.length
  const size = Number(params.size ?? 0)
  const page = Number(params.page ?? 1)
  if (size > 0) rows = rows.slice((page - 1) * size, page * size)

  return { rows: rows.map(redact), total, page, size }
}

export async function get(entity, key) {
  await delay(60)
  const d = load()
  const meta = ENTITY_META[entity]
  if (!meta) throw new ApiError(`알 수 없는 엔터티: ${entity}`, 'NOT_FOUND')
  const found = d[entity].find((r) => r[meta.pk] === key)
  if (!found) throw new ApiError(`${meta.label} 정보를 찾을 수 없습니다. (${key})`, 'NOT_FOUND')
  return redact(clone(found))
}

/* ------------------------------------------------------------------ */
/* 변경                                                                */
/* ------------------------------------------------------------------ */

export async function create(entity, payload, actor) {
  await delay()
  const d = load()
  const meta = ENTITY_META[entity]
  if (!meta) throw new ApiError(`알 수 없는 엔터티: ${entity}`, 'NOT_FOUND')

  const row = clone(payload)
  if (!row[meta.pk]) row[meta.pk] = `${entity.toUpperCase().slice(0, 3)}${nextSeq()}`

  if (d[entity].some((r) => r[meta.pk] === row[meta.pk])) {
    throw new ApiError(`이미 존재하는 ${meta.label} 코드입니다. (${row[meta.pk]})`, 'DUPLICATE')
  }

  validate(entity, row, d, true)

  row.createdBy = actor?.userId ?? 'system'
  row.createdAt = nowStamp()
  row.updatedBy = row.createdBy
  row.updatedAt = row.createdAt

  d[entity].push(row)
  writeAudit(entity, 'CREATE', row[meta.pk], row, actor)
  save()
  return redact(clone(row))
}

export async function update(entity, key, payload, actor) {
  await delay()
  const d = load()
  const meta = ENTITY_META[entity]
  if (!meta) throw new ApiError(`알 수 없는 엔터티: ${entity}`, 'NOT_FOUND')

  const idx = d[entity].findIndex((r) => r[meta.pk] === key)
  if (idx < 0) throw new ApiError(`${meta.label} 정보를 찾을 수 없습니다. (${key})`, 'NOT_FOUND')

  const merged = { ...d[entity][idx], ...clone(payload), [meta.pk]: key }
  validate(entity, merged, d, false)

  merged.updatedBy = actor?.userId ?? 'system'
  merged.updatedAt = nowStamp()

  d[entity][idx] = merged
  writeAudit(entity, 'UPDATE', key, merged, actor)
  save()
  return redact(clone(merged))
}

export async function remove(entity, key, actor) {
  await delay()
  const d = load()
  const meta = ENTITY_META[entity]
  if (!meta) throw new ApiError(`알 수 없는 엔터티: ${entity}`, 'NOT_FOUND')

  const idx = d[entity].findIndex((r) => r[meta.pk] === key)
  if (idx < 0) throw new ApiError(`${meta.label} 정보를 찾을 수 없습니다. (${key})`, 'NOT_FOUND')

  checkDeletable(entity, key, d)

  const [removed] = d[entity].splice(idx, 1)
  cascade(entity, key, d)
  writeAudit(entity, 'DELETE', key, removed, actor)
  save()
  return true
}

/** 역할-권한 매핑 저장 (체크박스 매트릭스 전용 - upsert/delete 를 한 번에) */
export async function saveRolePermissions(roleId, grants, actor) {
  await delay()
  const d = load()
  if (!d.roles.some((r) => r.roleId === roleId)) {
    throw new ApiError(`역할 정보를 찾을 수 없습니다. (${roleId})`, 'NOT_FOUND')
  }

  // grants: { permId: ['R','C', ...] } — 빈 배열이면 매핑 제거
  d.rolePermissions = d.rolePermissions.filter((m) => m.roleId !== roleId)
  for (const [permId, actions] of Object.entries(grants)) {
    if (!Array.isArray(actions) || actions.length === 0) continue
    if (!d.permissions.some((p) => p.permId === permId)) {
      throw new ApiError(`존재하지 않는 권한입니다. (${permId})`, 'NOT_FOUND')
    }
    d.rolePermissions.push({
      _id: `${roleId}::${permId}`,
      roleId,
      permId,
      actions: [...actions],
      updatedBy: actor?.userId ?? 'system',
      updatedAt: nowStamp(),
    })
  }

  writeAudit('rolePermissions', 'UPDATE', roleId, { roleId, grants }, actor)
  save()
  return true
}

/* ------------------------------------------------------------------ */
/* 검증 규칙                                                            */
/* ------------------------------------------------------------------ */

function req(value, label) {
  if (value === null || value === undefined || String(value).trim() === '') {
    throw new ApiError(`${label}은(는) 필수 입력 항목입니다.`, 'REQUIRED')
  }
}

function validate(entity, row, d, isCreate) {
  switch (entity) {
    case 'orgs':
      req(row.orgId, '조직코드')
      req(row.orgName, '조직명')
      req(row.orgType, '조직유형')
      if (row.parentId && row.parentId === row.orgId) {
        throw new ApiError('상위 조직으로 자기 자신을 지정할 수 없습니다.')
      }
      if (row.parentId && !d.orgs.some((o) => o.orgId === row.parentId)) {
        throw new ApiError(`상위 조직이 존재하지 않습니다. (${row.parentId})`)
      }
      break

    case 'roles':
      req(row.roleId, '역할코드')
      req(row.roleName, '역할명')
      req(row.summary, '주요 권한 요약')
      if (!/^[A-Z][A-Z0-9_]{2,29}$/.test(row.roleId)) {
        throw new ApiError('역할코드는 영문 대문자·숫자·밑줄 3~30자로 입력하세요. (예: STORE_MGR)', 'FORMAT')
      }
      if (row.useYn === 'N') {
        const inUse = d.users.filter(
          (u) => u.roleIds?.includes(row.roleId) && u.status === 'ACTIVE',
        )
        if (inUse.length > 0) {
          throw new ApiError(
            `정상 상태 사용자 ${inUse.length}명이 이 역할을 사용 중이므로 미사용 처리할 수 없습니다. (${inUse
              .slice(0, 3)
              .map((u) => u.userName)
              .join(', ')}${inUse.length > 3 ? ' 외' : ''})`,
            'IN_USE',
          )
        }
      }
      break

    case 'permissions':
      req(row.permId, '권한코드')
      req(row.permName, '권한명')
      req(row.module, '모듈')
      if (!Array.isArray(row.actions) || row.actions.length === 0) {
        throw new ApiError('허용 액션을 1개 이상 선택하세요.', 'REQUIRED')
      }
      break

    case 'policies': {
      req(row.policyName, '정책명')
      req(row.roleId, '적용 역할')
      req(row.policyType, '정책유형')
      req(row.enforceLevel, '적용강도')
      req(row.message, '안내 메시지')
      if (!d.roles.some((r) => r.roleId === row.roleId)) {
        throw new ApiError(`적용 역할이 존재하지 않습니다. (${row.roleId})`)
      }
      if (row.permId && !d.permissions.some((p) => p.permId === row.permId)) {
        throw new ApiError(`대상 권한이 존재하지 않습니다. (${row.permId})`)
      }
      if (row.policyType === 'REQUIRED') req(row.targetField, '필수 대상 필드')
      if (row.policyType === 'CONDITION') req(row.conditionExpr, '조건식')
      if (row.policyType === 'LIMIT') {
        const amt = Number(row.limitAmount ?? 0)
        const qty = Number(row.limitQty ?? 0)
        if (amt <= 0 && qty <= 0) {
          throw new ApiError('승인한도 정책은 한도금액 또는 한도수량 중 하나를 0보다 크게 입력해야 합니다.')
        }
      }
      // 동일 역할 + 동일 권한 + 동일 유형의 사용중 정책 중복 방지
      const dup = d.policies.find(
        (p) =>
          p.policyId !== row.policyId &&
          p.roleId === row.roleId &&
          (p.permId ?? null) === (row.permId ?? null) &&
          p.policyType === row.policyType &&
          p.useYn === 'Y' &&
          row.useYn === 'Y',
      )
      if (dup) {
        throw new ApiError(
          `동일한 역할·대상·유형의 사용중 정책이 이미 있습니다. (${dup.policyId} ${dup.policyName})`,
          'DUPLICATE',
        )
      }
      break
    }

    case 'users': {
      req(row.userId, '사용자ID')
      req(row.userName, '사용자명')
      req(row.orgId, '소속 조직')
      req(row.status, '상태')
      if (!/^[a-z][a-z0-9._-]{2,29}$/.test(row.userId)) {
        throw new ApiError('사용자ID는 영문 소문자로 시작하는 3~30자(숫자 . _ - 허용)로 입력하세요.', 'FORMAT')
      }
      if (row.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(row.email)) {
        throw new ApiError('이메일 형식이 올바르지 않습니다.', 'FORMAT')
      }
      if (row.email && d.users.some((u) => u.userId !== row.userId && u.email === row.email)) {
        throw new ApiError(`이미 사용 중인 이메일입니다. (${row.email})`, 'DUPLICATE')
      }
      if (!d.orgs.some((o) => o.orgId === row.orgId)) {
        throw new ApiError(`소속 조직이 존재하지 않습니다. (${row.orgId})`)
      }
      if (!Array.isArray(row.roleIds) || row.roleIds.length === 0) {
        throw new ApiError('역할을 1개 이상 배정하세요.', 'REQUIRED')
      }

      const org = d.orgs.find((o) => o.orgId === row.orgId)
      for (const rid of row.roleIds) {
        const role = d.roles.find((r) => r.roleId === rid)
        if (!role) throw new ApiError(`배정 역할이 존재하지 않습니다. (${rid})`)
        if (role.useYn !== 'Y') {
          throw new ApiError(`미사용 역할은 배정할 수 없습니다. (${role.roleName})`, 'IN_USE')
        }
        // 조직유형과 역할 적용범위 정합성
        if (role.orgScope !== org.orgType) {
          throw new ApiError(
            `'${role.roleName}' 역할은 ${orgTypeLabel(role.orgScope)} 소속에만 배정할 수 있습니다. ` +
              `(현재 소속: ${org.orgName} / ${orgTypeLabel(org.orgType)})`,
            'SCOPE',
          )
        }
      }

      // 직무분리: 요청 역할과 승인 역할 동시 보유 경고성 차단
      const SOD_PAIRS = [
        ['INBOUND_WORKER', 'CENTER_MGR'],
        ['STORE_STAFF', 'STORE_MGR'],
      ]
      for (const [a, b] of SOD_PAIRS) {
        if (row.roleIds.includes(a) && row.roleIds.includes(b)) {
          throw new ApiError(
            `직무분리 위반: '${roleName(d, a)}'과 '${roleName(d, b)}'는 동시에 배정할 수 없습니다.`,
            'SOD',
          )
        }
      }

      // 조회 전용 역할과 변경 권한 역할의 혼합 방지
      if (row.roleIds.includes('CS_VIEWER') && row.roleIds.length > 1) {
        throw new ApiError("'CS/조회 사용자'는 다른 역할과 함께 배정할 수 없습니다. (수정 권한 없음 정책)", 'SOD')
      }

      const limit = Number(row.approvalLimit ?? 0)
      if (limit < 0) throw new ApiError('승인한도는 0 이상이어야 합니다.', 'FORMAT')

      // 신규 계정은 초기 비밀번호를 부여한다. (최초 로그인 시 변경이 원칙)
      if (!row.password) row.password = DEMO_PASSWORD
      if (row.loginFailCount === undefined) row.loginFailCount = 0
      break
    }

    default:
      break
  }

  if (isCreate && row.useYn === undefined) row.useYn = 'Y'
}

function orgTypeLabel(t) {
  return { HQ: '본사', DC: '물류센터', STORE: '매장' }[t] ?? t
}

function roleName(d, roleId) {
  return d.roles.find((r) => r.roleId === roleId)?.roleName ?? roleId
}

/** 삭제 가능 여부 (참조 무결성) */
function checkDeletable(entity, key, d) {
  if (entity === 'roles') {
    const users = d.users.filter((u) => u.roleIds?.includes(key))
    if (users.length > 0) {
      throw new ApiError(
        `이 역할을 배정받은 사용자가 ${users.length}명 있어 삭제할 수 없습니다. 먼저 배정을 해제하세요.`,
        'IN_USE',
      )
    }
    const pols = d.policies.filter((p) => p.roleId === key)
    if (pols.length > 0) {
      throw new ApiError(
        `이 역할에 연결된 공통정책이 ${pols.length}건 있어 삭제할 수 없습니다. (${pols
          .slice(0, 3)
          .map((p) => p.policyId)
          .join(', ')})`,
        'IN_USE',
      )
    }
  }

  if (entity === 'permissions') {
    const maps = d.rolePermissions.filter((m) => m.permId === key)
    if (maps.length > 0) {
      throw new ApiError(
        `${maps.length}개 역할에 매핑된 권한입니다. 역할-권한 매핑을 먼저 해제하세요.`,
        'IN_USE',
      )
    }
    const pols = d.policies.filter((p) => p.permId === key)
    if (pols.length > 0) {
      throw new ApiError(`이 권한을 대상으로 하는 정책이 ${pols.length}건 있어 삭제할 수 없습니다.`, 'IN_USE')
    }
  }

  if (entity === 'orgs') {
    const users = d.users.filter((u) => u.orgId === key)
    if (users.length > 0) {
      throw new ApiError(`이 조직에 소속된 사용자가 ${users.length}명 있어 삭제할 수 없습니다.`, 'IN_USE')
    }
    const children = d.orgs.filter((o) => o.parentId === key)
    if (children.length > 0) {
      throw new ApiError(`하위 조직이 ${children.length}건 있어 삭제할 수 없습니다.`, 'IN_USE')
    }
  }

  if (entity === 'users' && key === 'admin') {
    throw new ApiError('최고 관리자 계정(admin)은 삭제할 수 없습니다.', 'PROTECTED')
  }
}

/** 삭제 후 연쇄 정리 */
function cascade(entity, key, d) {
  if (entity === 'roles') {
    d.rolePermissions = d.rolePermissions.filter((m) => m.roleId !== key)
  }
}

