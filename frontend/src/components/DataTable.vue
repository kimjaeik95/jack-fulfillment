<script setup>
/**
 * 목록 테이블.
 * 정렬·페이징은 컴포넌트 내부에서 처리하고, 필터링은 상위 화면에서 rows 를 만들어 넘긴다.
 *
 * columns: [{ key, label, width, align('left'|'center'|'right'), sortable, cls, sortKey }]
 * 셀 커스터마이즈: <template #cell-{key}="{ row, value }">
 */
import { computed, ref, watch } from 'vue'

const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, required: true },
  loading: { type: Boolean, default: false },
  selectedKey: { type: [String, Number], default: null },
  clickable: { type: Boolean, default: false },
  pageSize: { type: Number, default: 10 },
  emptyText: { type: String, default: '조회된 데이터가 없습니다.' },
  mutedWhen: { type: Function, default: null },
  defaultSort: { type: Object, default: null }, // { key, dir }
})

const emit = defineEmits(['row-click'])

const sortKey = ref(props.defaultSort?.key ?? null)
const sortDir = ref(props.defaultSort?.dir ?? 'asc')
const page = ref(1)
const size = ref(props.pageSize)

// 필터가 바뀌어 행 수가 줄면 현재 페이지를 보정한다.
watch(
  () => props.rows.length,
  () => {
    if ((page.value - 1) * size.value >= props.rows.length) page.value = 1
  },
)

function toggleSort(col) {
  if (!col.sortable) return
  const key = col.sortKey ?? col.key
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDir.value = 'asc'
  }
}

const sorted = computed(() => {
  if (!sortKey.value) return props.rows
  const dir = sortDir.value === 'desc' ? -1 : 1
  return [...props.rows].sort((a, b) => {
    const av = a[sortKey.value]
    const bv = b[sortKey.value]
    if (av === bv) return 0
    if (av === null || av === undefined || av === '') return 1
    if (bv === null || bv === undefined || bv === '') return -1
    if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir
    return String(av).localeCompare(String(bv), 'ko') * dir
  })
})

const total = computed(() => sorted.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const paged = computed(() => {
  if (size.value <= 0) return sorted.value
  const start = (page.value - 1) * size.value
  return sorted.value.slice(start, start + size.value)
})

/** 현재 페이지 주변 번호만 노출 */
const pageNumbers = computed(() => {
  const tp = totalPages.value
  const cur = page.value
  const span = 2
  const from = Math.max(1, Math.min(cur - span, tp - span * 2))
  const to = Math.min(tp, Math.max(cur + span, span * 2 + 1))
  const out = []
  for (let i = from; i <= to; i++) out.push(i)
  return out
})

function go(p) {
  page.value = Math.min(Math.max(1, p), totalPages.value)
}

function cellValue(row, col) {
  return col.key.split('.').reduce((acc, k) => (acc == null ? acc : acc[k]), row)
}

/**
 * 특정 행이 있는 페이지로 이동한다.
 *
 * 등록 직후에 쓴다. 새 행이 정렬 순서상 뒤로 밀리면 1페이지에 나타나지 않아,
 * 저장에 성공했는데도 아무 일도 일어나지 않은 것처럼 보인다.
 */
function goToKey(key) {
  if (size.value <= 0) return
  const index = sorted.value.findIndex((r) => r[props.rowKey] === key)
  if (index < 0) return
  page.value = Math.floor(index / size.value) + 1
}

defineExpose({ resetPage: () => (page.value = 1), goToKey })
</script>

<template>
  <div>
    <div v-if="loading" class="loading-bar"></div>

    <div class="table-wrap">
      <table class="table">
        <colgroup>
          <col v-for="c in columns" :key="`col-${c.key}`" :style="c.width ? { width: c.width } : null" />
        </colgroup>
        <thead>
          <tr>
            <th
              v-for="c in columns"
              :key="c.key"
              :class="[c.align === 'center' ? 'center' : '', c.align === 'right' ? 'right' : '', c.sortable ? 'sortable' : '', c.headCls]"
              @click="toggleSort(c)"
            >
              {{ c.label }}
              <span v-if="sortKey === (c.sortKey ?? c.key)" class="sort-arrow">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in paged"
            :key="row[rowKey]"
            :class="{
              selected: selectedKey !== null && row[rowKey] === selectedKey,
              clickable,
              muted: mutedWhen ? mutedWhen(row) : false,
            }"
            @click="clickable && emit('row-click', row)"
          >
            <td
              v-for="c in columns"
              :key="c.key"
              :class="[c.cls, c.align === 'center' ? 'center' : '', c.align === 'right' ? 'num' : '']"
            >
              <slot :name="`cell-${c.key}`" :row="row" :value="cellValue(row, c)">
                {{ cellValue(row, c) ?? '-' }}
              </slot>
            </td>
          </tr>
          <tr v-if="!paged.length">
            <td :colspan="columns.length">
              <div class="table-empty">
                <span class="table-empty-icon">🗂</span>
                <slot name="empty">{{ emptyText }}</slot>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > 0" class="pager">
      <span>총 <strong>{{ total.toLocaleString() }}</strong>건</span>
      <select v-model.number="size" class="select" style="width: 88px; min-height: 26px" @change="page = 1">
        <option :value="10">10건</option>
        <option :value="20">20건</option>
        <option :value="50">50건</option>
        <option :value="0">전체</option>
      </select>
      <div v-if="size > 0" class="pager-pages">
        <button class="pager-btn" :disabled="page === 1" title="첫 페이지" @click="go(1)">«</button>
        <button class="pager-btn" :disabled="page === 1" title="이전" @click="go(page - 1)">‹</button>
        <button
          v-for="p in pageNumbers"
          :key="p"
          class="pager-btn"
          :class="{ active: p === page }"
          @click="go(p)"
        >
          {{ p }}
        </button>
        <button class="pager-btn" :disabled="page === totalPages" title="다음" @click="go(page + 1)">›</button>
        <button class="pager-btn" :disabled="page === totalPages" title="마지막 페이지" @click="go(totalPages)">»</button>
      </div>
    </div>
  </div>
</template>
