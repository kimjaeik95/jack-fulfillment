/**
 * CRUD 화면 공통 로직.
 *
 * - 등록/수정 모달 상태와 폼 데이터
 * - 저장 전 클라이언트 검증 → 저장 시 서버(Mock API) 검증 오류 표시
 * - 삭제 확인 → 실행
 * - 권한/정책 판정에 따른 버튼 활성 제어 및 차단 사유 안내
 */
import { computed, ref } from 'vue'
import { useAdminStore } from '@/stores/admin.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'

/**
 * @param {object} cfg
 *  entity   'roles' | 'users' | ...
 *  perm     화면이 요구하는 권한코드 (예: 'SYS_ROLE')
 *  pk       PK 필드명
 *  label    사용자에게 보여줄 엔터티 명칭
 *  blank()  빈 폼 객체 생성 함수
 *  toForm(row)  행 -> 폼 변환 (기본: 얕은 복제)
 *  validate(form, ctx)  { field: message } 반환
 *  nameOf(row)  삭제 확인 문구에 쓸 표시명
 */
export function useCrud(cfg) {
  const admin = useAdminStore()
  const session = useSessionStore()
  const toast = useToastStore()

  const open = ref(false)
  const mode = ref('create') // create | edit
  const form = ref(cfg.blank())
  const errors = ref({})
  const busy = ref(false)
  const serverError = ref('')

  const askDelete = ref(null)
  const deleting = ref(false)

  const canRead = computed(() => session.can(cfg.perm, 'R'))
  const canCreate = computed(() => session.can(cfg.perm, 'C'))
  const canUpdate = computed(() => session.can(cfg.perm, 'U'))
  const canDelete = computed(() => session.can(cfg.perm, 'D'))

  const createDenyReason = computed(() => session.denyReason(cfg.perm, 'C'))
  const updateDenyReason = computed(() => session.denyReason(cfg.perm, 'U'))
  const deleteDenyReason = computed(() => session.denyReason(cfg.perm, 'D'))

  /** 권한 없으면 사유를 토스트로 안내하고 false 반환 */
  function guard(action) {
    const result = session.check(cfg.perm, action)
    if (!result.allowed) {
      toast.error(result.reason)
      return false
    }
    if (result.reason) toast.warn(result.reason)
    return true
  }

  function openCreate(preset = {}) {
    if (!guard('C')) return
    mode.value = 'create'
    form.value = { ...cfg.blank(), ...preset }
    errors.value = {}
    serverError.value = ''
    open.value = true
  }

  function openEdit(row) {
    if (!guard('U')) return
    mode.value = 'edit'
    form.value = cfg.toForm ? cfg.toForm(row) : JSON.parse(JSON.stringify(row))
    errors.value = {}
    serverError.value = ''
    open.value = true
  }

  /** 수정 권한이 없을 때 상세만 보여주기 위한 진입점 */
  function openView(row) {
    mode.value = 'view'
    form.value = cfg.toForm ? cfg.toForm(row) : JSON.parse(JSON.stringify(row))
    errors.value = {}
    serverError.value = ''
    open.value = true
  }

  function close() {
    open.value = false
    errors.value = {}
    serverError.value = ''
  }

  async function submit() {
    serverError.value = ''
    errors.value = cfg.validate ? cfg.validate(form.value, { mode: mode.value, admin }) : {}
    if (Object.keys(errors.value).length > 0) {
      toast.warn('입력값을 확인하세요.')
      return false
    }
    if (!guard(mode.value === 'create' ? 'C' : 'U')) return false

    busy.value = true
    try {
      const payload = cfg.toPayload ? cfg.toPayload(form.value) : { ...form.value }
      if (mode.value === 'create') {
        await admin.createRow(cfg.entity, payload)
        toast.success(`${cfg.label}을(를) 등록했습니다.`)
      } else {
        await admin.updateRow(cfg.entity, form.value[cfg.pk], payload)
        toast.success(`${cfg.label} 정보를 수정했습니다.`)
      }
      open.value = false
      return true
    } catch (e) {
      // 서버(업무규칙) 오류는 모달을 닫지 않고 그대로 보여준다.
      serverError.value = e.message
      toast.error(e.message)
      return false
    } finally {
      busy.value = false
    }
  }

  function confirmDelete(row) {
    if (!guard('D')) return
    askDelete.value = row
  }

  async function doDelete() {
    if (!askDelete.value) return
    deleting.value = true
    try {
      await admin.removeRow(cfg.entity, askDelete.value[cfg.pk])
      toast.success(`${cfg.label}을(를) 삭제했습니다.`)
      askDelete.value = null
    } catch (e) {
      toast.error(e.message)
      askDelete.value = null
    } finally {
      deleting.value = false
    }
  }

  const deleteMessage = computed(() => {
    if (!askDelete.value) return ''
    const name = cfg.nameOf ? cfg.nameOf(askDelete.value) : askDelete.value[cfg.pk]
    return `'${name}' ${cfg.label}을(를) 삭제합니다.\n삭제 후에는 복구할 수 없습니다.`
  })

  const isView = computed(() => mode.value === 'view')

  return {
    // 상태
    open, mode, form, errors, busy, serverError, isView,
    askDelete, deleting, deleteMessage,
    // 권한
    canRead, canCreate, canUpdate, canDelete,
    createDenyReason, updateDenyReason, deleteDenyReason,
    // 액션
    openCreate, openEdit, openView, close, submit, confirmDelete, doDelete, guard,
  }
}
