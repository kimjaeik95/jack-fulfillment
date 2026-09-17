<script setup>
/**
 * 상세 보기 — 읽기 전용 (공통).
 *
 * 목록에는 자주 보는 칸만 둔다. 이메일 · 연락처 · 주소까지 다 넣으면 표가
 * 옆으로 흘러 정작 찾으려던 것이 안 보인다. 그래서 나머지는 이 창이 맡는다.
 *
 * <b>전에는 '수정' 을 눌러야 그것들을 볼 수 있었다.</b> 고칠 생각이 없는
 * 사람에게 수정 화면을 열게 하는 셈이라, 보다가 실수로 저장하거나 — 더 흔하게
 * 는 — 수정 권한이 없어 아예 못 보는 일이 생겼다. 보는 것과 고치는 것은
 * 권한부터 다르다.
 *
 * 쓰는 쪽은 fields 만 만들면 된다.
 *
 *   <DetailDialog
 *     v-if="detail"
 *     title="사용자 상세"
 *     :subtitle="detail.userId"
 *     :fields="detailFields"
 *     :can-edit="canUpdate"
 *     @edit="openEdit(detail)"
 *     @close="detail = null" />
 *
 * 값이 비어 있으면 '-' 로 채운다. 칸을 통째로 빼면 같은 종류의 두 건이
 * 다른 모양으로 보여, 무엇이 빠졌는지 오히려 알기 어렵다.
 */
import ModalDialog from './ModalDialog.vue'

defineProps({
  title: { type: String, default: '상세' },
  subtitle: { type: String, default: '' },
  /**
   * 보여줄 항목.
   *   { label, value, span?, mono?, slot? }
   *
   * span 이면 한 줄을 다 쓴다 (주소 · 비고처럼 긴 값).
   * mono 면 고정폭으로 — 코드 · 번호는 자릿수가 맞아야 읽힌다.
   * slot 이면 그 이름의 슬롯으로 그린다 (배지 · 칩처럼 모양이 있는 값).
   */
  fields: { type: Array, required: true },
  /** 수정 버튼을 띄울지. 권한이 없으면 보기만 한다. */
  canEdit: { type: Boolean, default: false },
  editDenyReason: { type: String, default: null },
  size: { type: String, default: 'normal' },
})

const emit = defineEmits(['close', 'edit'])

const shown = (v) => (v === null || v === undefined || v === '' ? '-' : v)
</script>

<template>
  <ModalDialog :title="title" :subtitle="subtitle" :size="size" @close="emit('close')">
    <dl class="detail-grid">
      <template v-for="f in fields" :key="f.label">
        <div class="detail-item" :class="{ span: f.span }">
          <dt>{{ f.label }}</dt>
          <dd :class="{ mono: f.mono, dim: !f.slot && (f.value === null || f.value === undefined || f.value === '') }">
            <slot v-if="f.slot" :name="f.slot" :field="f" />
            <template v-else>{{ shown(f.value) }}</template>
          </dd>
        </div>
      </template>
    </dl>

    <slot name="extra" />

    <template #footer>
      <span class="left small dim">
        <slot name="footer-note">보기 전용입니다. 고치려면 수정을 누르세요.</slot>
      </span>
      <button class="btn" @click="emit('close')">닫기</button>
      <button
        class="btn btn-primary"
        :disabled="!canEdit"
        :title="editDenyReason ?? '수정'"
        @click="emit('edit')"
      >
        수정
      </button>
    </template>
  </ModalDialog>
</template>

<style scoped>
/*
  두 칸으로 벌린다. 한 칸이면 세로로 길어져 스크롤해야 하고, 세 칸이면
  값이 잘려 줄바꿈이 생긴다.
*/
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px 18px;
  margin: 0;
}
.detail-item {
  display: flex;
  gap: 10px;
  padding: 7px 2px;
  border-bottom: 1px solid var(--line, #e5e7eb);
  min-width: 0;
}
.detail-item.span {
  grid-column: 1 / -1;
}
.detail-item dt {
  flex: 0 0 96px;
  color: var(--fg-dim, #6b7280);
  font-size: 12px;
  padding-top: 1px;
}
.detail-item dd {
  margin: 0;
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  /* 긴 이메일 · 주소가 표를 밀지 않게 */
  overflow-wrap: anywhere;
}

/* 화면이 좁으면 한 칸으로 */
@media (max-width: 640px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
