<script setup>
/**
 * 공통코드 조회 화면.
 * 코드값은 화면·정책·검증에서 함께 참조하는 상수이므로 이 콘솔에서는 조회 전용으로 제공하고,
 * 실제 변경은 공통코드 마스터(TB_COM_CODE) 배포 절차를 따른다.
 */
import { computed, ref } from 'vue'
import { CODE_GROUPS } from '@/api/codes.js'
import { useAdminStore } from '@/stores/admin.js'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'

const admin = useAdminStore()

const GROUP_LABELS = {
  PERM_MODULE: '권한 모듈(대분류)',
  PERM_ACTION: '권한 액션 유형',
  POLICY_TYPE: '정책 유형',
  ENFORCE_LEVEL: '정책 적용강도',
  ORG_TYPE: '조직 유형',
  USER_STATUS: '사용자 상태',
  USE_YN: '사용여부',
}

const GROUP_USAGE = {
  PERM_MODULE: '권한 관리 · 역할-권한 매핑',
  PERM_ACTION: '권한 관리 · 역할-권한 매핑',
  POLICY_TYPE: '공통정책 관리',
  ENFORCE_LEVEL: '공통정책 관리',
  ORG_TYPE: '조직 관리 · 역할 적용범위',
  USER_STATUS: '사용자 관리',
  USE_YN: '전 화면 공통',
}

const groupId = ref('PERM_MODULE')
const keyword = ref('')

const groupOptions = Object.keys(CODE_GROUPS).map((g) => ({
  value: g,
  label: `${GROUP_LABELS[g] ?? g} (${g})`,
}))

/** 코드가 실제 데이터에서 몇 건 쓰이는지 계산 */
function usageCount(group, code) {
  switch (group) {
    case 'PERM_MODULE':
      return admin.permissions.filter((p) => p.module === code).length
    case 'PERM_ACTION':
      return admin.rolePermissions.filter((m) => m.actions?.includes(code)).length
    case 'POLICY_TYPE':
      return admin.policies.filter((p) => p.policyType === code).length
    case 'ENFORCE_LEVEL':
      return admin.policies.filter((p) => p.enforceLevel === code).length
    case 'ORG_TYPE':
      return admin.orgs.filter((o) => o.orgType === code).length
    case 'USER_STATUS':
      return admin.users.filter((u) => u.status === code).length
    case 'USE_YN':
      return [...admin.roles, ...admin.permissions, ...admin.policies, ...admin.orgs].filter(
        (r) => r.useYn === code,
      ).length
    default:
      return 0
  }
}

const rows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return (CODE_GROUPS[groupId.value] ?? [])
    .map((c, i) => ({
      ...c,
      seq: i + 1,
      usage: usageCount(groupId.value, c.code),
    }))
    .filter((c) => !kw || [c.code, c.label, c.desc].some((v) => String(v ?? '').toLowerCase().includes(kw)))
})

const columns = [
  { key: 'seq', label: '순서', width: '58px', align: 'right' },
  { key: 'code', label: '코드', width: '110px', sortable: true, cls: 'code' },
  { key: 'label', label: '코드명', width: '150px', sortable: true },
  { key: 'desc', label: '설명', width: '320px' },
  { key: 'color', label: '표시색', width: '96px', align: 'center' },
  { key: 'usage', label: '사용 건수', width: '92px', align: 'right', sortable: true },
]

const allGroups = computed(() =>
  Object.keys(CODE_GROUPS).map((g) => ({
    group: g,
    label: GROUP_LABELS[g] ?? g,
    usage: GROUP_USAGE[g] ?? '-',
    count: CODE_GROUPS[g].length,
  })),
)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1 class="page-title">공통코드</h1>
        <p class="page-desc">
          역할·권한·정책 화면에서 공통으로 사용하는 코드 집합입니다. 코드값 변경은 화면 동작과 기존 데이터에 함께 영향을
          주므로 이 화면에서는 조회만 제공합니다.
        </p>
      </div>
    </div>

    <div class="alert alert-info mb-2">
      <span class="alert-icon">ℹ</span>
      <span>
        코드 추가·변경이 필요하면 <span class="mono">src/api/codes.js</span> 의 코드그룹을 수정하거나, 운영 환경에서는
        공통코드 마스터 배포 절차를 따르세요.
      </span>
    </div>

    <div class="card mb-2">
      <div class="card-head"><span class="card-title">코드그룹 ({{ allGroups.length }})</span></div>
      <div class="card-body tight">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th style="width: 170px">그룹ID</th>
                <th style="width: 190px">그룹명</th>
                <th style="width: 70px" class="center">코드수</th>
                <th>사용 화면</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="g in allGroups"
                :key="g.group"
                class="clickable"
                :class="{ selected: g.group === groupId }"
                @click="groupId = g.group"
              >
                <td class="code">{{ g.group }}</td>
                <td>{{ g.label }}</td>
                <td class="center">{{ g.count }}</td>
                <td class="dim small">{{ g.usage }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <FormField v-model="groupId" label="코드그룹" type="select" :options="groupOptions" />
        <FormField v-model="keyword" class="grow" label="검색어" placeholder="코드 / 코드명 / 설명" />
      </div>

      <DataTable :columns="columns" :rows="rows" row-key="code" :page-size="20" empty-text="코드가 없습니다.">
        <template #cell-label="{ row }">
          <span class="badge" :class="`badge-${row.color}`">{{ row.label }}</span>
        </template>

        <template #cell-desc="{ value }">
          <span class="small">{{ value || '-' }}</span>
        </template>

        <template #cell-color="{ value }">
          <span class="mono small dim">{{ value }}</span>
        </template>

        <template #cell-usage="{ value }">
          <span v-if="value > 0">{{ value }}</span>
          <span v-else class="dim">0</span>
        </template>
      </DataTable>
    </div>
  </div>
</template>
