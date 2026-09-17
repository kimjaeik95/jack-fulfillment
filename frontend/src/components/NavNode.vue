<script setup>
/**
 * 사이드바 한 줄 (재귀).
 *
 * 메뉴는 tb_menu 의 parent_seq 가 만드는 트리고, 깊이가 고정이 아니다.
 * 처음에는 그룹 → 항목 2단이었는데 기준정보 한 그룹에 14개가 매달리면서
 * 그 아래를 플랜트 · 제품 · 채널 · 거래처로 한 번 더 나눴다(V19).
 *
 * 깊이를 코드에 박으면 중간 그룹을 끼울 때마다 화면을 고쳐야 하고, 더 나쁘게는
 * 못 그린 단이 조용히 사라진다 — 메뉴가 안 보이는데 오류는 안 난다. 그래서
 * 자기 자신을 부른다.
 *
 * 도우미(권한 확인 · 접힘 상태 따위)는 App.vue 가 provide 로 넘긴다. 한 줄을
 * 그리는 데 필요한 것이 예닐곱 개라, props 로 줄줄이 내려보내면 단이 깊어질수록
 * 지나가기만 하는 인자가 늘어난다.
 */
import { inject } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  node: { type: Object, required: true },
  depth: { type: Number, default: 0 },
})

const nav = inject('nav')

const hasKids = (n) => Boolean(n.children?.length)

/**
 * 머리글을 달지 않는 경우 — 아래가 잎 하나뿐일 때.
 *
 * '현황 › 권한 현황' 처럼 두 줄이 되는데 접을 것이 없다. 접기 버튼만 늘어난다.
 */
const isFlat = (n) => hasKids(n) && n.children.length === 1 && !hasKids(n.children[0])

/** 단마다 12px 씩 들여쓴다. 0단은 들여쓰지 않는다. */
const indent = (d) => ({ paddingLeft: `${10 + d * 12}px` })
</script>

<template>
  <!-- 잎 하나뿐인 그룹은 그 잎으로 갈음한다 -->
  <NavNode v-if="isFlat(node)" :node="node.children[0]" :depth="depth" />

  <!-- 그룹 — 머리글을 누르면 접히고 펴진다 -->
  <template v-else-if="hasKids(node)">
    <button
      class="nav-group-label nav-group-toggle"
      :class="{ on: nav.isOpen(node.menuId), [`depth-${depth}`]: true }"
      :style="indent(depth)"
      :title="`${node.menuName} ${node.children.length}개`"
      @click="nav.toggle(node.menuId)"
    >
      <span class="nav-caret">{{ nav.isOpen(node.menuId) ? '▾' : '▸' }}</span>
      <span class="nav-group-name">{{ node.menuName }}</span>
      <span class="nav-group-count">{{ node.children.length }}</span>
    </button>
    <template v-if="nav.isOpen(node.menuId)">
      <NavNode
        v-for="child in node.children"
        :key="child.menuId"
        :node="child"
        :depth="depth + 1"
      />
    </template>
  </template>

  <!--
    가리키는 화면이 없는 메뉴는 눌러도 이동할 수 없으므로 링크로 만들지 않는다.
    감추지도 않는다 — 권한 문제가 아니라 메뉴와 라우트가 어긋났다는 뜻이고,
    보여야 고친다.
  -->
  <div
    v-else-if="!nav.routeExists(node.routeName)"
    class="nav-item nav-broken"
    :style="indent(depth)"
    :title="nav.titleOf(node)"
  >
    <span class="nav-icon">⚠</span>
    <span>{{ node.menuName }}</span>
  </div>

  <RouterLink v-else v-slot="{ isActive, navigate }" :to="{ name: node.routeName }" custom>
    <div
      class="nav-item"
      :class="{ active: isActive }"
      :style="indent(depth)"
      :title="nav.titleOf(node)"
      @click="navigate"
    >
      <span class="nav-icon">{{ node.icon }}</span>
      <span>{{ node.menuName }}</span>
      <span v-if="nav.countOf(node.routeName) !== undefined" class="nav-count">
        {{ nav.countOf(node.routeName) }}
      </span>
    </div>
  </RouterLink>
</template>
