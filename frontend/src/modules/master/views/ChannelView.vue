<script setup>
/**
 * 판매채널 관리 (MST-PG-010).
 *
 * 채널은 수요가 들어오는 지점이다. 주문이 채널에서 오고, 그 주문의 외부
 * 상품코드를 내부 SKU 로 바꾸는 것이 채널 SKU 매핑이다.
 *
 * 이 화면에서 가장 중요한 숫자는 '미완료' 다 — 그 채널로 주문이 와도 SKU 를
 * 찾을 수 없는 상품의 수다. 매핑 0건인 채널을 열어 두는 것도 같은 문제라
 * 목록에서 바로 눈에 띄게 표시한다.
 *
 * 사용여부는 표시용이 아니다. 중지한 채널의 신규 주문은 자동으로 처리하지
 * 않는다(MST-007). 막지는 않고 저장할 때 서버가 알려 준다.
 *
 * 건수가 적어 전체를 받아 화면에서 페이징한다.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { codeOptions } from '@/api/codes.js'
import * as channelApi from '@/api/channel.js'
import { useCatalogStore } from '@/stores/catalog.js'
import { useSessionStore } from '@/stores/session.js'
import { useCrud } from '@/composables/useCrud.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const router = useRouter()
const catalog = useCatalogStore()
const session = useSessionStore()

const loadError = ref('')
const loading = ref(false)
const rows = ref([])

const filters = reactive({ keyword: '', channelType: '', useYn: '' })

function resetFilters() {
  Object.assign(filters, { keyword: '', channelType: '', useYn: '' })
}

async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await channelApi.list({ ...filters, size: 0 })
    rows.value = data.rows
  } catch (e) {
    rows.value = []
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)

const columns = [
  { key: 'channelId', label: '채널코드', width: '110px', sortable: true, cls: 'code' },
  { key: 'channelName', label: '채널명', width: '180px', sortable: true },
  { key: 'channelType', label: '유형', width: '96px', align: 'center', sortable: true },
  { key: 'mappingCount', label: 'SKU 매핑', width: '100px', align: 'right', sortable: true },
  { key: 'pendingCount', label: '미완료', width: '90px', align: 'right', sortable: true },
  { key: 'sortOrder', label: '정렬', width: '64px', align: 'right', sortable: true },
  { key: 'useYn', label: '사용', width: '72px', align: 'center', sortable: true },
  { key: '_act', label: '', width: '160px', align: 'right' },
]

const {
  open: dlgOpen, mode, form, errors, busy, serverError,
  askDelete, deleting, deleteMessage,
  canCreate, canUpdate, canDelete,
  createDenyReason, updateDenyReason, deleteDenyReason,
  openCreate, openEdit, close, submit, confirmDelete, doDelete,
} = useCrud({
  perm: 'MST_CHANNEL',
  pk: 'channelId',
  label: '판매채널',
  nameOf: (c) => `${c.channelName} (${c.channelId})`,
  api: {
    create: (payload) => channelApi.create(payload),
    update: (channelId, payload) => channelApi.update(channelId, payload),
    remove: (channelId) => channelApi.remove(channelId, '채널 삭제'),
  },
  async afterChange() {
    await fetchList()
    // 매핑 화면의 채널 드롭다운이 낡는다
    catalog.invalidate('channels')
  },
  blank: () => ({
    channelId: '',
    channelName: '',
    channelType: '',
    sortOrder: 0,
    useYn: 'Y',
  }),
  toForm: (row) => ({
    channelId: row.channelId,
    channelName: row.channelName,
    channelType: row.channelType,
    sortOrder: row.sortOrder ?? 0,
    useYn: row.useYn,
  }),
  toPayload: (f) => ({ ...f, sortOrder: Number(f.sortOrder) || 0 }),
  validate(f) {
    const e = {}
    if (!f.channelId?.trim()) e.channelId = '채널코드는 필수입니다.'
    else if (!/^[A-Z0-9][A-Z0-9_]{1,19}$/.test(f.channelId))
      e.channelId = '영문 대문자·숫자·밑줄 2~20자. 예) CPNG'
    if (!f.channelName?.trim()) e.channelName = '채널명은 필수입니다.'
    if (!f.channelType) e.channelType = '채널유형을 선택하세요.'
    return e
  },
})

/**
 * 중지 전환 안내.
 *
 * 저장하기 전에 보여 준다. 중지는 목록에서 감추는 일이 아니라 그 채널의
 * 신규 주문을 자동 처리하지 않겠다는 뜻이다(MST-007).
 */
const disableNotice = computed(() => {
  if (mode.value !== 'edit' || form.value.useYn !== 'N') return ''
  const original = rows.value.find((c) => c.channelId === form.value.channelId)
  if (!original || original.useYn !== 'Y') return ''
  return `중지하면 이 채널의 신규 주문은 자동으로 처리되지 않습니다. 기존 매핑 ${original.mappingCount ?? 0}건과 과거 주문은 그대로 남습니다.`
})

/** 매핑 화면으로 이 채널을 걸고 넘어간다 */
function openMappings(row) {
  router.push({ name: 'channel-skus', query: { channelId: row.channelId } })
}

const readDenyReason = computed(() => session.denyReason('MST_CHANNEL', 'R'))

/** 주문이 들어와도 처리하지 못하는 채널 — 사용 중인데 매핑이 비었거나 미완료가 있다 */
const riskyChannels = computed(() =>
  rows.value.filter(
    (c) => c.useYn === 'Y' && ((c.mappingCount ?? 0) === 0 || (c.pendingCount ?? 0) > 0),
  ),
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">판매채널 관리</h1>
        <p class="page-desc">
          주문이 들어오는 지점입니다. 채널만 열어 두면 주문을 받아도 어느 SKU 인지 알 수 없으므로
          <strong>채널마다 SKU 매핑이 있어야 합니다.</strong>
          사용여부를 중지로 바꾸면 그 채널의 신규 주문은 자동으로 처리되지 않습니다.
          채널코드는 등록 후 바꿀 수 없습니다 — 매핑과 주문이 코드로 채널을 부릅니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canCreate"
          :title="createDenyReason ?? '채널 등록'"
          @click="openCreate()"
        >
          + 채널 등록
        </button>
      </div>
    </div>

    <div v-if="readDenyReason" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ readDenyReason }}</span>
    </div>
    <div v-else-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>
    <div v-else-if="createDenyReason" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span><span>{{ createDenyReason }}</span>
    </div>

    <!-- 판매 중인데 주문을 처리할 수 없는 채널을 먼저 알린다 -->
    <div v-if="riskyChannels.length" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>
        판매 중인데 매핑이 준비되지 않은 채널이 {{ riskyChannels.length }}개 있습니다 —
        <strong>{{ riskyChannels.map((c) => c.channelName).join(', ') }}</strong>.
        이 채널로 주문이 들어오면 어느 SKU 인지 찾지 못합니다.
      </span>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField
          v-model="filters.keyword"
          class="grow"
          label="검색어"
          placeholder="채널코드 / 채널명"
          @keyup.enter="fetchList()"
        />
        <FormField
          v-model="filters.channelType"
          label="채널유형"
          type="select"
          empty-option="전체"
          :options="codeOptions('CHANNEL_TYPE')"
          @change="fetchList()"
        />
        <FormField
          v-model="filters.useYn"
          label="사용여부"
          type="select"
          empty-option="전체"
          :options="[
            { value: 'Y', label: '사용' },
            { value: 'N', label: '중지' },
          ]"
          @change="fetchList()"
        />
        <div class="toolbar-actions">
          <button class="btn btn-primary" :disabled="loading" @click="fetchList()">
            <span v-if="loading" class="spinner"></span>
            검색
          </button>
          <button class="btn" @click="resetFilters(); fetchList()">초기화</button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="rows"
        row-key="channelId"
        :muted-when="(c) => c.useYn !== 'Y'"
        empty-text="조건에 맞는 채널이 없습니다."
      >
        <template #cell-channelType="{ value }">
          <CodeBadge group="CHANNEL_TYPE" :code="value" />
        </template>

        <!-- 매핑 0건은 그 자체로 경고다. 채널은 열려 있는데 팔 것이 없다. -->
        <template #cell-mappingCount="{ row, value }">
          <span :class="{ dim: !value }">{{ value || '없음' }}</span>
          <span v-if="!value && row.useYn === 'Y'" class="small warn"> ⚠</span>
        </template>

        <!-- 주문이 와도 SKU 를 찾을 수 없는 상품 수 -->
        <template #cell-pendingCount="{ value }">
          <span v-if="value" class="badge badge-amber">{{ value }}</span>
          <span v-else class="dim">0</span>
        </template>

        <template #cell-useYn="{ value }">
          <CodeBadge group="USE_YN" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button class="btn btn-sm" title="이 채널의 SKU 매핑 보기" @click="openMappings(row)">
              매핑
            </button>
            <button
              class="btn btn-sm"
              :disabled="!canUpdate"
              :title="updateDenyReason ?? '수정'"
              @click="openEdit(row)"
            >
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

    <ModalDialog
      v-if="dlgOpen"
      :title="mode === 'create' ? '채널 등록' : '채널 수정'"
      :subtitle="mode === 'edit' ? form.channelId : '채널코드는 등록 후 변경할 수 없습니다.'"
      @close="close()"
    >
      <div v-if="serverError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span>
        <span style="white-space: pre-line">{{ serverError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="form.channelId"
          label="채널코드"
          required
          mono
          placeholder="CPNG"
          :disabled="mode === 'edit'"
          :error="errors.channelId"
          help="매핑과 주문이 이 코드로 채널을 부릅니다. 나중에 바꿀 수 없습니다."
        />
        <FormField
          v-model="form.channelName"
          label="채널명"
          required
          placeholder="쿠팡"
          :error="errors.channelName"
        />
        <FormField
          v-model="form.channelType"
          label="채널유형"
          type="select"
          required
          empty-option="선택하세요"
          :options="codeOptions('CHANNEL_TYPE')"
          :error="errors.channelType"
          help="자사몰은 직접 운영하는 몰, 오픈마켓은 외부 플랫폼입니다."
        />
        <FormField v-model="form.sortOrder" label="정렬순서" type="number" help="작을수록 위에 표시됩니다." />
        <FormField
          v-model="form.useYn"
          label="사용여부"
          type="switch"
          help="중지하면 이 채널의 신규 주문을 자동 처리하지 않습니다."
        />
      </div>

      <div v-if="disableNotice" class="alert alert-warn mt-2">
        <span class="alert-icon">⚠</span><span>{{ disableNotice }}</span>
      </div>

      <template #footer>
        <span class="left small dim">채널코드·채널명 중복은 저장 시 서버가 다시 검증합니다.</span>
        <button class="btn" :disabled="busy" @click="close()">취소</button>
        <button class="btn btn-primary" :disabled="busy" @click="submit()">
          <span v-if="busy" class="spinner"></span>
          저장
        </button>
      </template>
    </ModalDialog>

    <ConfirmDialog
      v-if="askDelete"
      title="채널 삭제"
      :message="deleteMessage"
      detail="SKU 매핑이 남아 있으면 삭제되지 않습니다. 판매만 멈추려면 사용여부를 중지로 바꾸세요 — 과거 주문의 채널 정보가 남습니다."
      confirm-label="삭제"
      danger
      :busy="deleting"
      @cancel="askDelete = null"
      @confirm="doDelete()"
    />
  </div>
</template>

<style scoped>
.warn {
  color: var(--c-amber);
}
</style>
