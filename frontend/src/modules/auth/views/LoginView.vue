<script setup>
/**
 * 로그인 화면.
 *
 * 인증 판정은 api.login 에서 수행하고, 이 화면은 결과 사유만 표시한다.
 * 백엔드가 없으므로 하단에 데모 계정 목록을 제공해 역할별 화면 동작을
 * 바로 확인할 수 있게 한다. (운영 빌드에서는 이 영역만 제거하면 된다)
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DEMO_PASSWORD, MAX_LOGIN_FAIL } from '@/api/auth.js'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'
import CodeBadge from '@/components/CodeBadge.vue'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const toast = useToastStore()

const userId = ref('')
const password = ref('')
const showPw = ref(false)
const remember = ref(localStorage.getItem('wms-admin-remember') === 'Y')
const busy = ref(false)
const errorMsg = ref('')
const errorCode = ref('')

const theme = ref(document.documentElement.dataset.theme)

onMounted(() => {
  if (remember.value) userId.value = session.lastLoginId()
  session.ensureReady()
})

const roleNameOf = (roleId) => session.demoRoles.find((r) => r.roleId === roleId)?.roleName ?? roleId

/**
 * 데모 계정 목록 — 역할별 대표 1건 (상태 이상 계정도 확인할 수 있게 포함).
 * 아직 Mock 데이터를 쓴다. 사용자 조회 API 가 만들어지면 서버 데이터로 교체한다.
 * 계정 목록은 프론트·백엔드 시드가 같은 원본이라 실제 로그인과 일치한다.
 */
const demoAccounts = computed(() =>
  [...session.demoUsers]
    .filter((u) => (u.roleIds ?? []).length > 0)
    // 첫 배정 역할이 같은 계정은 하나만 남긴다.
    .filter((u, i, arr) => arr.findIndex((x) => x.roleIds[0] === u.roleIds[0]) === i)
    .map((u) => ({
      userId: u.userId,
      userName: u.userName,
      status: u.status,
      useYn: u.useYn,
      roleNames: (u.roleIds ?? []).map(roleNameOf),
      deptName: u.deptName,
    })),
)

function fill(account) {
  userId.value = account.userId
  password.value = DEMO_PASSWORD
  errorMsg.value = ''
  errorCode.value = ''
}

async function submit() {
  errorMsg.value = ''
  errorCode.value = ''

  if (!userId.value.trim()) {
    errorMsg.value = '아이디를 입력하세요.'
    return
  }
  if (!password.value) {
    errorMsg.value = '비밀번호를 입력하세요.'
    return
  }

  busy.value = true
  try {
    const user = await session.login(userId.value.trim(), password.value)

    localStorage.setItem('wms-admin-remember', remember.value ? 'Y' : 'N')
    if (!remember.value) localStorage.removeItem('wms-admin-last-id')

    toast.success(`${user.userName}님, 환영합니다. (${session.myRoleNames.join(', ') || '역할 없음'})`)
    if (session.loginWarning) toast.warn(session.loginWarning)

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (e) {
    errorMsg.value = e.message
    errorCode.value = e.code ?? ''
    password.value = ''
  } finally {
    busy.value = false
  }
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  document.documentElement.dataset.theme = theme.value
  localStorage.setItem('wms-admin-theme', theme.value)
}

/** 잠김/휴면 등 관리자 조치가 필요한 오류인지 */
const needsAdmin = computed(() => ['LOCKED', 'DORMANT', 'DISABLED', 'RETIRED'].includes(errorCode.value))
</script>

<template>
  <div class="login-page">
    <button class="btn btn-icon login-theme" :title="theme === 'dark' ? '라이트 모드' : '다크 모드'" @click="toggleTheme">
      {{ theme === 'dark' ? '☀' : '☾' }}
    </button>

    <div class="login-wrap">
      <!-- 로그인 카드 -->
      <form class="card login-card" @submit.prevent="submit">
        <div class="login-brand">
          <span class="brand-mark" style="width: 34px; height: 34px; flex-basis: 34px; font-size: 15px">F</span>
          <div>
            <div class="login-title">풀필먼트 관리 시스템</div>
            <div class="dim small">기준정보 · 입고 · 주문 · 출고</div>
          </div>
        </div>

        <div class="field">
          <label class="field-label" for="login-id">아이디<span class="req">*</span></label>
          <input
            id="login-id"
            v-model="userId"
            class="input"
            :class="{ invalid: !!errorMsg }"
            type="text"
            autocomplete="username"
            placeholder="사번 또는 계정ID"
            :disabled="busy"
            autofocus
          />
        </div>

        <div class="field">
          <label class="field-label" for="login-pw">비밀번호<span class="req">*</span></label>
          <div class="pw-box">
            <input
              id="login-pw"
              v-model="password"
              class="input"
              :class="{ invalid: !!errorMsg }"
              :type="showPw ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="비밀번호"
              :disabled="busy"
            />
            <button
              type="button"
              class="btn btn-ghost btn-sm pw-toggle"
              :title="showPw ? '비밀번호 숨기기' : '비밀번호 표시'"
              @click="showPw = !showPw"
            >
              {{ showPw ? '숨김' : '표시' }}
            </button>
          </div>
        </div>

        <label class="check-line" style="padding-left: 0">
          <input v-model="remember" type="checkbox" :disabled="busy" />
          아이디 저장
        </label>

        <div v-if="errorMsg" class="alert alert-danger mt-1">
          <span class="alert-icon">⛔</span>
          <div>
            <div>{{ errorMsg }}</div>
            <div v-if="needsAdmin" class="small dim mt-1">
              시스템 관리자가 <strong>사용자 관리</strong> 화면에서 잠금 해제 또는 비밀번호 초기화를 처리해야 합니다.
            </div>
          </div>
        </div>

        <button class="btn btn-primary login-submit" type="submit" :disabled="busy">
          <span v-if="busy" class="spinner"></span>
          로그인
        </button>

        <p class="dim small center mt-1" style="margin-bottom: 0">
          비밀번호 {{ MAX_LOGIN_FAIL }}회 오류 시 계정이 잠깁니다.
        </p>
      </form>

      <!-- 데모 계정 -->
      <div class="card login-demo">
        <div class="card-head">
          <span class="card-title">데모 계정</span>
          <span class="dim small">역할별 화면 동작 확인용</span>
        </div>
        <div class="card-body">
          <div class="alert alert-info mb-2">
            <span class="alert-icon">ℹ</span>
            <span>
              공통 비밀번호 <strong class="mono">{{ DEMO_PASSWORD }}</strong> — 계정을 누르면 자동 입력됩니다.
            </span>
          </div>

          <div class="demo-list">
            <button
              v-for="a in demoAccounts"
              :key="a.userId"
              type="button"
              class="demo-item"
              :class="{ 'demo-item-off': a.status !== 'ACTIVE' || a.useYn === 'N' }"
              :title="`${a.userId} 로 자동 입력`"
              @click="fill(a)"
            >
              <div class="demo-item-main">
                <span class="bold">{{ a.userName }}</span>
                <span class="code">{{ a.userId }}</span>
              </div>
              <div class="demo-item-sub">
                <span v-for="rn in a.roleNames" :key="rn" class="chip">{{ rn }}</span>
                <CodeBadge v-if="a.status !== 'ACTIVE'" group="USER_STATUS" :code="a.status" />
              </div>
            </button>
          </div>

          <p class="dim small mt-2" style="margin-bottom: 0">
            잠김·휴면 계정으로 시도하면 로그인 차단 사유를 확인할 수 있습니다.
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(1100px 520px at 12% -10%, var(--primary-soft), transparent 60%),
    var(--bg);
}

.login-theme {
  position: fixed;
  top: 14px;
  right: 16px;
}

.login-wrap {
  display: grid;
  grid-template-columns: 380px 420px;
  gap: 18px;
  align-items: start;
  width: 100%;
  max-width: 830px;
}

@media (max-width: 860px) {
  .login-wrap {
    grid-template-columns: minmax(0, 420px);
    justify-content: center;
  }
}

.login-card {
  padding: 26px 24px 22px;
  box-shadow: var(--shadow-md);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 22px;
}

.login-title {
  font-size: 16.5px;
  font-weight: 700;
  letter-spacing: -0.4px;
}

.pw-box {
  position: relative;
  display: flex;
  align-items: center;
}

.pw-box .input {
  padding-right: 54px;
}

.pw-toggle {
  position: absolute;
  right: 4px;
}

.login-submit {
  width: 100%;
  height: 36px;
  margin-top: 14px;
  font-size: 13px;
}

.login-demo {
  box-shadow: var(--shadow-sm);
}

.demo-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
  max-height: 344px;
  overflow-y: auto;
  /* 목록이 잘린 지점을 흐리게 처리해 스크롤 가능함을 알린다. */
  mask-image: linear-gradient(to bottom, #000 calc(100% - 22px), transparent 100%);
  padding-bottom: 4px;
}

.demo-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
  color: var(--text);
  font-family: inherit;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}

.demo-item:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.demo-item-off {
  opacity: 0.62;
}

.demo-item-main {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.demo-item-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  align-items: center;
}
</style>
