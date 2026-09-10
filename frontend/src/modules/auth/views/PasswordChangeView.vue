<script setup>
/**
 * 비밀번호 변경 화면.
 *
 * 두 가지 경우에 들어온다.
 *   1) 관리자가 만든 계정으로 최초 로그인 — 변경 전까지 다른 기능이 막힌다 (강제)
 *   2) 사용자가 스스로 바꾸려는 경우 (임의)
 *
 * 정책 검증은 서버가 최종 판정하지만, 여기서도 같은 기준을 보여준다.
 * 저장을 눌러야 알 수 있으면 사용자가 여러 번 되돌아오게 되기 때문이다.
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session.js'
import { useToastStore } from '@/stores/toast.js'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const toast = useToastStore()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showPw = ref(false)
const busy = ref(false)
const errorMsg = ref('')

/** 강제 변경으로 들어왔는지 (다른 기능이 막힌 상태) */
const forced = computed(() => session.mustChangePassword)

/**
 * 서버 PasswordPolicy 와 같은 규칙.
 * 서버가 최종 판정하므로 여기서는 통과 여부만 보여주고 저장을 막지는 않는다.
 */
const rules = computed(() => {
  const pw = newPassword.value
  const kinds =
    (/[A-Za-z]/.test(pw) ? 1 : 0) +
    (/\d/.test(pw) ? 1 : 0) +
    (/[^A-Za-z0-9\s]/.test(pw) ? 1 : 0)
  const userId = session.currentUser?.userId ?? ''

  return [
    { label: '8자 이상', ok: pw.length >= 8 && pw.length <= 64 },
    { label: '영문·숫자·특수문자 중 2종류 이상', ok: kinds >= 2 },
    { label: '공백 없음', ok: pw.length > 0 && !/\s/.test(pw) },
    {
      label: '아이디와 다름',
      ok: pw.length > 0 && pw.toLowerCase() !== userId.toLowerCase(),
    },
    { label: '새 비밀번호 확인 일치', ok: pw.length > 0 && pw === confirmPassword.value },
  ]
})

const allOk = computed(() => rules.value.every((r) => r.ok))

async function submit() {
  errorMsg.value = ''
  if (!currentPassword.value) {
    errorMsg.value = '현재 비밀번호를 입력하세요.'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    errorMsg.value = '새 비밀번호가 서로 일치하지 않습니다.'
    return
  }

  busy.value = true
  try {
    await session.changePassword(currentPassword.value, newPassword.value, confirmPassword.value)
    toast.success('비밀번호를 변경했습니다.')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (e) {
    errorMsg.value = e.message
    currentPassword.value = ''
  } finally {
    busy.value = false
  }
}

async function logout() {
  await session.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="pw-page">
    <div class="card pw-card">
      <div class="pw-head">
        <span class="brand-mark" style="width: 34px; height: 34px; flex-basis: 34px; font-size: 15px">F</span>
        <div>
          <div class="pw-title">비밀번호 변경</div>
          <div class="dim small">{{ session.currentUser?.userName }} ({{ session.currentUser?.userId }})</div>
        </div>
      </div>

      <div v-if="forced" class="alert alert-warn mb-2">
        <span class="alert-icon">⚠</span>
        <div>
          <div class="bold">초기 비밀번호를 변경해야 합니다.</div>
          <div class="small mt-1">
            관리자가 정한 비밀번호는 관리자도 알고 있습니다.
            변경하기 전까지 다른 기능을 사용할 수 없습니다.
          </div>
        </div>
      </div>

      <form @submit.prevent="submit">
        <div class="field">
          <label class="field-label" for="pw-current">현재 비밀번호<span class="req">*</span></label>
          <input
            id="pw-current"
            v-model="currentPassword"
            class="input"
            :class="{ invalid: !!errorMsg }"
            :type="showPw ? 'text' : 'password'"
            autocomplete="current-password"
            :disabled="busy"
            autofocus
          />
        </div>

        <div class="field">
          <label class="field-label" for="pw-new">새 비밀번호<span class="req">*</span></label>
          <input
            id="pw-new"
            v-model="newPassword"
            class="input"
            :type="showPw ? 'text' : 'password'"
            autocomplete="new-password"
            :disabled="busy"
          />
        </div>

        <div class="field">
          <label class="field-label" for="pw-confirm">새 비밀번호 확인<span class="req">*</span></label>
          <input
            id="pw-confirm"
            v-model="confirmPassword"
            class="input"
            :type="showPw ? 'text' : 'password'"
            autocomplete="new-password"
            :disabled="busy"
          />
        </div>

        <label class="check-line" style="padding-left: 0">
          <input v-model="showPw" type="checkbox" :disabled="busy" />
          비밀번호 표시
        </label>

        <ul class="pw-rules">
          <li v-for="rule in rules" :key="rule.label" :class="{ ok: rule.ok }">
            <span class="pw-rule-mark">{{ rule.ok ? '✓' : '·' }}</span>{{ rule.label }}
          </li>
        </ul>

        <div v-if="errorMsg" class="alert alert-danger mt-1">
          <span class="alert-icon">⛔</span>
          <span style="white-space: pre-line">{{ errorMsg }}</span>
        </div>

        <button class="btn btn-primary pw-submit" type="submit" :disabled="busy || !allOk">
          <span v-if="busy" class="spinner"></span>
          변경
        </button>

        <button v-if="forced" class="btn btn-ghost pw-logout" type="button" :disabled="busy" @click="logout">
          나중에 하기 (로그아웃)
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.pw-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(1100px 520px at 12% -10%, var(--primary-soft), transparent 60%),
    var(--bg);
}

.pw-card {
  width: 100%;
  max-width: 440px;
  padding: 26px 24px 22px;
  box-shadow: var(--shadow-md);
}

.pw-head {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 20px;
}

.pw-title {
  font-size: 16.5px;
  font-weight: 700;
  letter-spacing: -0.4px;
}

.pw-rules {
  list-style: none;
  margin: 12px 0 0;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
  font-size: 11.5px;
  color: var(--text-3);
}

.pw-rules li {
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1.9;
}

.pw-rules li.ok {
  color: var(--success);
}

.pw-rule-mark {
  width: 10px;
  flex: 0 0 10px;
  font-weight: 700;
}

.pw-submit {
  width: 100%;
  height: 36px;
  margin-top: 14px;
  font-size: 13px;
}

.pw-logout {
  width: 100%;
  height: 30px;
  margin-top: 6px;
  font-size: 12px;
}
</style>
