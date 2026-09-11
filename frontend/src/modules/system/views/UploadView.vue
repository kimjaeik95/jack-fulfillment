<script setup>
/**
 * 대량 업로드 · 업로드 이력 (COM-PG-010).
 *
 * 화면 하나가 두 가지를 한다 — 파일을 올리는 곳과, 올린 결과를 다시 확인하는 곳.
 * 부분 성공을 전제하기 때문에 "올렸다"로 끝나지 않고 "몇 건이 왜 안 들어갔는지"를
 * 되짚어야 하고, 그건 업로드 직후뿐 아니라 나중에도 필요하다.
 *
 * 파일 형식은 UTF-8 CSV 다. 엑셀에서 [다른 이름으로 저장] → [CSV UTF-8] 로
 * 저장하면 그대로 올라간다. xlsx 를 고르면 서버가 그 안내와 함께 거절한다.
 */
import { computed, onMounted, ref } from 'vue'
import * as uploadApi from '@/api/upload.js'
import { useToastStore } from '@/stores/toast.js'
import DataTable from '@/components/DataTable.vue'
import ModalDialog from '@/components/ModalDialog.vue'
import FormField from '@/components/FormField.vue'
import CodeBadge from '@/components/CodeBadge.vue'

const toast = useToastStore()

const targets = ref([])
const histories = ref([])
const loading = ref(false)
const loadError = ref('')

/** 업로드 대화상자 */
const dlgOpen = ref(false)
const selectedType = ref('')
const file = ref(null)
const fileInput = ref(null)
const uploading = ref(false)
const uploadError = ref('')
/** 방금 올린 결과. 대화상자를 닫아도 화면 위쪽에 남겨 둔다. */
const lastResult = ref(null)

const target = computed(() => targets.value.find((t) => t.type === selectedType.value) ?? null)
const targetOptions = computed(() =>
  targets.value.map((t) => ({ value: t.type, label: t.label })),
)
const canUpload = computed(() => targets.value.length > 0)

async function reload() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await uploadApi.history({ size: 100 })
    histories.value = page.rows
  } catch (e) {
    loadError.value = e.message
    histories.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    // 권한이 없는 대상은 서버가 아예 내려주지 않는다
    targets.value = await uploadApi.targets()
    selectedType.value = targets.value[0]?.type ?? ''
  } catch {
    // 올릴 대상이 없어도 이력은 볼 수 있어야 한다
    targets.value = []
  }
  await reload()
})

const columns = [
  { key: 'uploadSeq', label: '번호', width: '64px', align: 'right' },
  { key: 'uploadedAt', label: '일시', width: '150px' },
  { key: 'targetLabel', label: '대상', width: '96px' },
  { key: 'fileName', label: '파일', width: '220px' },
  { key: 'totalCount', label: '총', width: '64px', align: 'right' },
  { key: 'successCount', label: '성공', width: '64px', align: 'right' },
  { key: 'failCount', label: '실패', width: '64px', align: 'right' },
  { key: 'status', label: '결과', width: '96px', align: 'center' },
  { key: 'uploaderName', label: '올린 사람', width: '110px' },
  { key: '_act', label: '', width: '110px', align: 'right' },
]

function openDialog() {
  file.value = null
  uploadError.value = ''
  dlgOpen.value = true
}

function onFilePick(event) {
  file.value = event.target.files?.[0] ?? null
  uploadError.value = ''
}

async function downloadTemplate() {
  try {
    await uploadApi.downloadTemplate(selectedType.value)
    toast.success(`${target.value?.label} 템플릿을 내려받았습니다.`)
  } catch (e) {
    toast.error(e.message)
  }
}

async function doUpload() {
  if (!file.value) {
    uploadError.value = '올릴 파일을 선택하세요.'
    return
  }
  uploading.value = true
  uploadError.value = ''
  try {
    const { result, warning } = await uploadApi.upload(selectedType.value, file.value)
    lastResult.value = result
    if (warning) {
      toast.warn(warning)
    } else {
      toast.success(`${result.successCount.toLocaleString()}건을 반영했습니다.`)
    }
    dlgOpen.value = false
    file.value = null
    if (fileInput.value) fileInput.value.value = ''
    await reload()
  } catch (e) {
    // 파일 형식·머리글 오류는 대화상자를 닫지 않고 그대로 보여준다.
    // 사용자가 파일을 바꿔 바로 다시 시도할 수 있어야 한다.
    uploadError.value = e.message
  } finally {
    uploading.value = false
  }
}

async function downloadErrors(uploadSeq) {
  try {
    await uploadApi.downloadErrors(uploadSeq)
    toast.success('오류 파일을 내려받았습니다. 사유를 고쳐 다시 올리면 됩니다.')
  } catch (e) {
    toast.error(e.message)
  }
}

const stamp = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">업로드 이력</h1>
        <p class="page-desc">
          기준정보를 파일로 한 번에 등록합니다. 정상 행은 반영되고 잘못된 행만 사유와 함께 남으므로,
          오류 파일을 내려받아 고친 뒤 다시 올리면 됩니다.
        </p>
      </div>
      <div class="page-head-actions">
        <button
          class="btn btn-primary"
          :disabled="!canUpload"
          :title="canUpload ? '파일 올리기' : '올릴 수 있는 대상이 없습니다. 등록 권한이 필요합니다.'"
          @click="openDialog"
        >
          ⬆ 파일 올리기
        </button>
      </div>
    </div>

    <div v-if="!canUpload" class="alert alert-warn mb-2">
      <span class="alert-icon">⚠</span>
      <span>
        현재 계정으로 올릴 수 있는 대상이 없습니다. 대량 등록은 그 데이터의 등록(C) 권한이 있어야 합니다.
        아래 이력에는 본인이 올린 내역만 표시됩니다.
      </span>
    </div>
    <div v-if="loadError" class="alert alert-danger mb-2">
      <span class="alert-icon">⛔</span><span>{{ loadError }}</span>
    </div>

    <!-- 방금 올린 결과 -->
    <div v-if="lastResult" class="card mb-2">
      <div class="card-body">
        <div class="flex wrap" style="gap: 10px; align-items: baseline">
          <span class="bold">{{ lastResult.targetLabel }} 업로드 결과</span>
          <CodeBadge group="UPLOAD_STATUS" :code="lastResult.status" />
          <span class="small dim">{{ lastResult.fileName }}</span>
          <span class="spacer"></span>
          <span class="small">
            총 {{ lastResult.totalCount.toLocaleString() }}건 ·
            등록 {{ lastResult.createdCount.toLocaleString() }} ·
            수정 {{ lastResult.updatedCount.toLocaleString() }} ·
            <span :class="lastResult.failCount ? 'text-warn bold' : 'dim'">
              실패 {{ lastResult.failCount.toLocaleString() }}
            </span>
          </span>
          <button class="btn btn-sm btn-ghost" @click="lastResult = null">닫기</button>
        </div>

        <div v-if="lastResult.errors.length" class="mt-2">
          <table class="mini-table">
            <thead>
              <tr><th style="width: 72px">행</th><th>사유</th></tr>
            </thead>
            <tbody>
              <tr v-for="e in lastResult.errors" :key="e.rowNo">
                <td class="right mono">{{ e.rowNo }}</td>
                <td>{{ e.message }}</td>
              </tr>
            </tbody>
          </table>
          <div class="flex mt-1">
            <span v-if="lastResult.errorsTruncated" class="small dim">
              앞쪽 {{ lastResult.errors.length }}건만 표시했습니다. 전체는 오류 파일로 받으세요.
            </span>
            <span class="spacer"></span>
            <button class="btn btn-sm" @click="downloadErrors(lastResult.uploadSeq)">
              ⬇ 오류 파일 받기
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <span class="small dim">
          최근 업로드 {{ histories.length }}건.
          다른 사람이 올린 내역은 변경 이력 조회 권한이 있을 때만 보입니다.
        </span>
        <div class="toolbar-actions">
          <button class="btn" :disabled="loading" @click="reload()">
            <span v-if="loading" class="spinner"></span>
            새로고침
          </button>
        </div>
      </div>

      <DataTable
        :columns="columns"
        :rows="histories"
        row-key="uploadSeq"
        :page-size="15"
        empty-text="업로드 이력이 없습니다."
      >
        <template #cell-uploadedAt="{ value }">
          <span class="small">{{ stamp(value) }}</span>
        </template>

        <template #cell-fileName="{ row, value }">
          <span class="truncate" style="max-width: 28ch" :title="value">{{ value }}</span>
          <div v-if="row.message" class="small text-warn">{{ row.message }}</div>
        </template>

        <template #cell-failCount="{ value }">
          <span :class="value ? 'text-warn bold' : 'dim'">{{ value }}</span>
        </template>

        <template #cell-status="{ value }">
          <CodeBadge group="UPLOAD_STATUS" :code="value" />
        </template>

        <template #cell-_act="{ row }">
          <div class="btn-row" style="justify-content: flex-end">
            <button
              v-if="row.failCount > 0"
              class="btn btn-sm"
              title="실패한 행만 사유와 함께 내려받습니다."
              @click="downloadErrors(row.uploadSeq)"
            >
              ⬇ 오류
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- 업로드 -->
    <ModalDialog v-if="dlgOpen" title="파일 올리기" subtitle="UTF-8 CSV" @close="dlgOpen = false">
      <div v-if="uploadError" class="alert alert-danger mb-2">
        <span class="alert-icon">⛔</span><span>{{ uploadError }}</span>
      </div>

      <div class="form-grid">
        <FormField
          v-model="selectedType"
          label="대상"
          type="select"
          required
          :options="targetOptions"
          span
          help="권한이 있는 대상만 표시됩니다."
        />
      </div>

      <div v-if="target" class="alert alert-info mt-2">
        <span class="alert-icon">ℹ</span>
        <div>
          <div>
            첫 줄에 머리글이 있어야 합니다 —
            <span class="mono small">{{ target.headers.join(', ') }}</span>
          </div>
          <div class="small mt-1">
            필수 열: <span class="bold">{{ target.requiredHeaders.join(', ') }}</span> ·
            이미 있는 항목은 수정으로 처리됩니다 · 한 번에 최대 10,000행
          </div>
          <button class="btn btn-sm mt-1" @click="downloadTemplate">⬇ 템플릿 받기</button>
        </div>
      </div>

      <div class="field span-2 mt-2">
        <label class="field-label">파일<span class="req">*</span></label>
        <input ref="fileInput" class="input" type="file" accept=".csv,text/csv" @change="onFilePick" />
        <span class="field-help">
          엑셀 파일(.xlsx)은 그대로 읽지 못합니다. [다른 이름으로 저장] → [CSV UTF-8(쉼표로 분리)] 로
          저장한 뒤 올려 주세요.
        </span>
      </div>

      <template #footer>
        <span class="left small dim">정상 행만 반영되고 잘못된 행은 사유와 함께 남습니다.</span>
        <button class="btn" :disabled="uploading" @click="dlgOpen = false">취소</button>
        <button class="btn btn-primary" :disabled="uploading || !file" @click="doUpload">
          <span v-if="uploading" class="spinner"></span>
          올리기
        </button>
      </template>
    </ModalDialog>
  </div>
</template>
