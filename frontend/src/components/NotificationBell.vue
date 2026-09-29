<script setup>
/**
 * 상단 막대의 알림 뱃지 (COM-PG-015).
 *
 * 안 읽은 <b>열린</b> 알림 수를 보여 준다. 닫힌 것은 이미 끝난 일이라 세지
 * 않고, 읽었지만 안 끝난 것도 세지 않는다 — 뱃지는 '새로 생긴 것이 있나'
 * 지 '할 일이 몇 개 남았나' 가 아니다. 남은 할 일은 알림함이 보여 준다.
 *
 * <b>화면을 옮길 때만 센다.</b> 창고 업무는 초 단위가 아니라 결품이 3분
 * 뒤에 보여도 아무 일 없고, 실시간 채널(WebSocket · SSE)을 들이면 그것부터
 * 관리 대상이 된다. 지금 코드에 setInterval 조차 없는데 연결을 하나 늘릴
 * 이유가 없다.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as notiApi from '@/api/notification.js'
import { useSessionStore } from '@/stores/session.js'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const allowed = computed(() => !session.denyReason('SYS_NOTIFICATION', 'R'))
const count = ref(0)

async function refresh() {
  if (!allowed.value) {
    count.value = 0
    return
  }
  try {
    count.value = await notiApi.unreadCount()
  } catch {
    // 뱃지가 안 떠도 업무는 돈다. 조용히 넘어간다.
    count.value = 0
  }
}

onMounted(refresh)
watch(() => route.fullPath, refresh)

function go() {
  router.push({ name: 'notifications' })
}
</script>

<template>
  <button
    v-if="allowed"
    class="btn btn-ghost btn-icon bell"
    :title="count ? `안 읽은 알림 ${count}건` : '알림함'"
    @click="go()"
  >
    🔔
    <!-- 99 를 넘으면 숫자보다 '많다' 가 정보다 -->
    <span v-if="count" class="dot">{{ count > 99 ? '99+' : count }}</span>
  </button>
</template>

<style scoped>
.bell {
  position: relative;
}
.dot {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--c-red, #dc2626);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  font-weight: 700;
}
</style>
