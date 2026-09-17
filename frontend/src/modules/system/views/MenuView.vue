<script setup>
/**
 * 메뉴 관리 (COM-PG-005).
 *
 * 사이드바 구성을 화면에서 바꾼다. 예전에는 routes.js 에 적혀 있어서
 * 메뉴 순서 하나를 고치는 데도 배포가 필요했다.
 *
 * 라우트(어떤 컴포넌트를 그릴지)는 여전히 코드가 소유한다. 여기서 정하는 것은
 * "그 라우트를 사이드바 어디에 어떤 이름·아이콘·순서로 걸지"와
 * "누구에게 보일지(필요 권한)" 다.
 *
 * 그래서 두 가지가 어긋날 수 있고, 화면이 그걸 드러낸다.
 *   - 메뉴가 없는 화면 : 주소로는 들어가지지만 사이드바에 없다
 *   - 화면이 없는 메뉴 : 눌러도 갈 곳이 없다
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as menuApi from '@/api/menu.js'
import { useMenuStore } from '@/stores/menu.js'
import { usePermissionStore } from '@/stores/permission.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const router = useRouter()
const menuStore = useMenuStore()
const permStore = usePermissionStore()
const session = useSessionStore()
const toast = useToastStore()

const table = ref(null)
const menus = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', useYn: '' })
}

async function reload() {
  loading.value = true
  loadError.value = ''
  try {
    menus.value = await menuApi.list()
  } catch (e) {
    // 권한 부족(403)도 여기로 온다. 사유를 그대로 보여준다.
    loadError.value = e.message
    menus.value = []
  } finally {
    loading.value = false
  }
  // 사이드바도 같은 데이터를 보고 있으므로 함께 갱신한다
  await menuStore.reload()
}

onMounted(async () => {
  await permStore.load()
  await reload()
})

/** 라우터에 실제로 등록된 화면 — 메뉴가 가리킬 수 있는 후보 */
const routes = computed(() =>
  router
    .getRoutes()
    .filter((r) => r.name && !r.meta?.public && !r.meta?.passwordChange)
    .map((r) => ({ name: r.name, title: r.meta?.title ?? r.name, perm: r.meta?.perm ?? null })),
)
const routeNames = computed(() => new Set(routes.value.map((r) => r.name)))

const routeOptions = computed(() =>
  [...routes.value]
    .sort((a, b) => a.title.localeCompare(b.title, 'ko'))
    .map((r) => ({ value: r.name, label: `${r.title} (${r.name})` })),
)

/** 상위 메뉴 후보 — 그룹 머리글만. 자기 자신은 뺀다. */
const parentOptions = computed(() =>
  menus.value
    .filter((m) => m.group && m.menuId !== form.value.menuId)
    .map((m) => ({ value: m.menuId, label: `${m.menuName} (${m.menuId})` })),
)

const permOptions = computed(() =>
  permStore.permissions
    .filter((p) => p.useYn === 'Y')
    .map((p) => ({ value: p.permId, label: `${p.permName} (${p.permId})` })),
)

/**
 * 표는 그룹 아래 항목이 붙은 순서로 온다. 들여쓰기로 그 관계를 보여준다.
 * 정렬을 바꾸면 관계가 깨지므로 정렬 가능 열을 두지 않는다.
 */
const rows = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return menus.value
    .filter((m) => !filters.useYn || m.useYn === filters.useYn)
    .filter(
      (m) =>
        !kw ||
        [m.menuId, m.menuName, m.routeName, m.permId].some((v) =>
          String(v ?? '').toLowerCase().includes(kw),
        ),
    )
    .map((m) => ({
      ...m,
      // 가리키는 화면이 사라졌으면 눌러도 갈 곳이 없다
      brokenRoute: !m.group && !routeNames.value.has(m.routeName),
    }))
})

const columns = [
  { key: 'menuName', label: '메뉴', width: '230px' },
  { key: 'menuId', label: '메뉴코드', width: '130px', cls: 'code' },
  { key: 'routeName', label: '연결 화면', width: '200px' },
  { key: 'permId', label: '필요 권한', width: '190px' },
  { key: 'icon', label: '아이콘', width: '64px', align: 'center' },
  { key: 'sortOrder', label: '순서', width: '60px', align: 'right' },
  { key: 'useYn', label: '사용', width: '68px', align: 'center' },
  { key: '_act', label: '', width: '112px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'SYS_MENU',
  pk: 'menuId',
  label: '메뉴',
  nameOf: (m) => `${m.menuName}(${m.menuId})`,
  api: {
    create: (payload) => menuApi.create(payload),
    update: (menuId, payload) => menuApi.update(menuId, payload),
    remove: (menuId) => menuApi.remove(menuId, '메뉴 삭제'),
  },
  afterChange: reload,
  blank: () => ({
    menuId: '',
    menuName: '',
    groupYn: 'N',
    parentId: '',
    routeName: '',
    icon: '',
    permId: '',
    sortOrder: 10,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    ...row,
    groupYn: row.group ? 'Y' : 'N',
    parentId: row.parentId ?? '',
    routeName: row.routeName ?? '',
    icon: row.icon ?? '',
    permId: row.permId ?? '',
  }),
  toPayload: (f) => ({
    menuId: f.menuId,
    menuName: f.menuName,
    // 무엇을 만드는지는 만드는 사람이 고른다 — 라우트가 비었는지로 넘겨짚지 않는다
    groupYn: f.groupYn,
    parentId: f.parentId || null,
    routeName: f.groupYn === 'Y' ? null : f.routeName || null,
    icon: f.icon || null,
    permId: f.permId || null,
    sortOrder: Number(f.sortOrder) || 0,
    useYn: f.useYn,
  }),
  validate(f) {
    const e = {}
    if (!f.menuId?.trim()) e.menuId = '메뉴코드는 필수입니다.'
    else if (!/^[A-Z][A-Z0-9_]{1,29}$/.test(f.menuId))
      e.menuId = '영문 대문자로 시작하는 2~30자여야 합니다. (숫자 _ 허용)'
    if (!f.menuName?.trim()) e.menuName = '메뉴명은 필수입니다.'
    // 머리글은 갈 곳이 없고, 화면 메뉴는 반드시 있어야 한다
    if (f.groupYn !== 'Y' && !f.routeName) e.routeName = '화면 메뉴는 이동할 화면이 필요합니다.'
    if (f.groupYn !== 'Y' && !f.parentId) e.parentId = '화면 메뉴는 상위 머리글이 필요합니다.'
    if (f.routeName && !routeNames.value.has(f.routeName))
      e.routeName = `'${f.routeName}' 화면이 없습니다. 목록에서 고르세요.`
    return e
  },
})

const isGroup = computed(() => form.value.groupYn === 'Y')

/** 화면을 고르면 그 화면이 요구하는 권한을 기본값으로 채운다 */
function onRouteChange(name) {
  const route = routes.value.find((r) => r.name === name)
  if (route?.perm && !form.value.permId) {
    form.value.permId = route.perm
  }
  if (!form.value.menuName && route?.title) {
    form.value.menuName = route.title
  }
}

/**
 * 메뉴가 걸리지 않은 화면.
 *
 * 주소를 직접 치면 들어가지지만 사이드바에는 없다. 만들어 놓고 연결을
 * 잊은 화면이 여기 드러난다.
 */
const unlinked = computed(() => {
  const linked = new Set(menus.value.map((m) => m.routeName).filter(Boolean))
  return routes.value.filter((r) => !linked.has(r.name))
})

/** 가리키는 화면이 사라진 메뉴 */
const broken = computed(() => rows.value.filter((m) => m.brokenRoute))

function addForRoute(route) {
  openCreate({
    menuName: route.title,
    routeName: route.name,
    permId: route.perm ?? '',
    parentId: parentOptions.value[0]?.value ?? '',
    sortOrder: 90,
  })
}

async function reloadSidebar() {
  await menuStore.reload()
  toast.success('사이드바를 다시 불러왔습니다.')
}

const readDenyReason = computed(() => session.denyReason('SYS_MENU', 'R'))
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">메뉴 관리</h1>
        <p class="page-desc">
          사이드바의 구성·순서·아이콘과 메뉴별 필요 권한을 관리합니다. 화면(라우트) 자체는 코드가 소유하며,
          여기서는 그 화면을 어디에 어떤 이름으로 걸지 정합니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn" title="저장한 내용을 사이드바에 다시 반영합니다." @click="reloadSidebar">
          사이드바 새로고침
        </button>
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '메뉴 등록'"
          @click="openCreate()"
        >
          + 메뉴 등록
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <div v-if="broken.length" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span>
      <div>
        <div class="bold">연결된 화면이 없는 메뉴 {{ broken.length }}건</div>
        <div class="small">
          눌러도 이동할 수 없습니다. 화면 이름이 바뀌었거나 삭제된 경우입니다 —
          {{ broken.map((m) => `${m.menuName}(${m.routeName})`).join(', ') }}
        </div>
      </div>
    </div>

    <div v-if="unlinked.length" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <div>
        <div class="bold">메뉴가 걸리지 않은 화면 {{ unlinked.length }}건</div>
        <div class="small">주소로는 들어갈 수 있지만 사이드바에는 나타나지 않습니다.</div>
        <div class="chip-list mt-1">
          <button
            v-for="r in unlinked"
            :key="r.name"
            class="chip"
            :disabled="!canCreate"
            :title="createDenyReason ?? `'${r.title}' 메뉴 등록`"
            @click="addForRoute(r)"
          >
            {{ r.title }} +
          </button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="메뉴명 / 메뉴코드 / 화면 이름"
        />
        <FormField v-model="filters.useYn" label="사용" type="select" empty-option="전체" :options="codeOptions('USE_YN')" />
        <div class="toolbar-actions">
          <button class="btn" @click="resetFilters">초기화</button>
          <button class="btn" :disabled="loading" @click="reload()">
            <span v-if="loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        ref="table"
        :columns="columns"
        :rows="rows"
        row-key="menuId"
        :page-size="30"
        :muted-when="(m) => m.useYn !== 'Y'"
        empty-text="조건에 맞는 메뉴가 없습니다."
      >
        <template #cell-menuName="{ row, value }">
          <div v-if="row.group" class="bold">{{ value }}</div>
          <div v-else style="padding-left: 18px">
            <span class="dim">└</span> {{ value }}
          </div>
        </template>

        <template #cell-routeName="{ row, value }">
          <span v-if="row.group" class="badge badge-slate plain">그룹 머리글</span>
          <template v-else>
            <span class="mono small">{{ value }}</span>
            <div v-if="row.brokenRoute" class="small text-warn">⚠ 없는 화면</div>
          </template>
        </template>

        <template #cell-permId="{ row, value }">
          <span v-if="value" class="small">{{ row.permName }}</span>
          <span v-else-if="!row.group" class="small dim">제한 없음 (로그인만)</span>
          <div v-if="value" class="mono small dim">{{ value }}</div>
        </template>

        <template #cell-useYn="{ value }">
          <CodeBadge group="USE_YN" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button class="btn btn-sm" :disabled="!canUpdate" :title="updateDenyReason ?? '수정'" @click="openEdit(row)">
              수정
            </button>
            <button
              class="btn btn-sm btn-danger"
              :disabled="!canDelete"
              :title="deleteDenyReason ?? '삭제'"
              @click="confirmDelete(row)"
            >
              삭제
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- 등록/수정 -->
    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '메뉴 등록' : '메뉴 수정'"
      :subtitle="mode === 'edit' ? form.menuId : '상위 메뉴를 비우면 그룹 머리글이 됩니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.menuId"
          label="메뉴코드"
          required
          :readonly="mode === 'edit'"
          placeholder="SYS_USERS"
          :error="errors.menuId"
          :help="mode === 'edit' ? '메뉴코드는 변경할 수 없습니다.' : '감사로그가 이 코드로 메뉴를 부릅니다.'"
        />
        <FormField v-model="form.menuName" label="메뉴명" required placeholder="사용자 관리" :error="errors.menuName" />

        <!--
          무엇을 만드는지 먼저 고르게 한다.

          전에는 '상위 메뉴를 비우면 머리글' 이었다. 머리글이 최상위에만
          있을 때는 통했는데, 기준정보 아래를 한 번 더 나누면서 부모가 있는
          머리글이 생겼다. 그리고 그 방식에서는 화면 고르는 것을 깜빡한 것과
          머리글을 만들려던 것이 구별되지 않는다.
        -->
        <FormField
          v-model="form.groupYn"
          label="메뉴 유형"
          type="switch"
          on-label="머리글"
          off-label="화면"
          :help="isGroup
            ? '아래 메뉴를 묶기만 합니다. 누를 화면이 없습니다.'
            : '눌러서 화면으로 갑니다. 상위 머리글과 연결 화면이 필요합니다.'"
        />
        <FormField
          v-model="form.parentId"
          label="상위 메뉴"
          type="select"
          :required="!isGroup"
          :empty-option="isGroup ? '(없음 — 맨 위 머리글)' : '선택하세요'"
          :options="parentOptions"
          :error="errors.parentId"
          help="메뉴는 3단까지 둘 수 있습니다. (예: 기준정보 › 플랜트 › 창고 관리)"
        />
        <FormField
          v-if="!isGroup"
          v-model="form.routeName"
          label="연결 화면"
          type="select"
          required
          empty-option="선택하세요"
          :options="routeOptions"
          :error="errors.routeName"
          help="코드에 등록된 화면만 고를 수 있습니다."
          @update:modelValue="onRouteChange"
        />

        <FormField
          v-if="!isGroup"
          v-model="form.permId"
          label="필요 권한"
          type="select"
          empty-option="제한 없음 (로그인만 하면 보임)"
          :options="permOptions"
          help="이 권한의 조회(R)가 있는 사람에게만 사이드바에 보입니다."
        />
        <FormField v-model="form.icon" label="아이콘" placeholder="👤" help="이모지 1~2자" />

        <FormField
          v-model="form.sortOrder"
          label="정렬순서"
          type="number"
          help="같은 그룹 안에서 작은 값이 위로 옵니다."
        />
        <FormField v-model="form.useYn" label="사용여부" type="switch" />
      </div>

      <div v-if="isGroup" class="alert alert-info mt-2">
        <span class="alert-icon">ℹ</span>
        <span>그룹 머리글은 사이드바의 구분선 역할만 합니다. 보이는 하위 메뉴가 하나도 없으면 표시되지 않습니다.</span>
      </div>

      <template #footer>
        <span class="left small dim">저장 후 사이드바에 바로 반영됩니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="메뉴 삭제"
      :message="deleteMessage"
      detail="메뉴를 지워도 화면 자체는 남아 있어 주소로는 접근할 수 있습니다. 잠시 감추려면 '사용여부 = 미사용'을 쓰세요."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>
