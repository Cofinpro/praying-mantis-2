<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { TriangleAlert } from 'lucide-vue-next'

import { api, ApiError } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import logoUrl from '@/assets/cofinpro-logo.svg'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'

// FE-1.1, Figma frame "01 Login" (design.md)
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const email = ref('')
const password = ref('')

const {
  mutate: login,
  isPending,
  error,
} = useMutation({
  mutationFn: api.login,
  async onSuccess(user) {
    // The cache is keyed by query, not by user: drop anything a previous session left in this tab
    // (client.ts sends expired sessions here), so the new user never sees someone else's data
    queryClient.clear()
    // The response is the logged-in user, so seed the cache: the app shell (FE-1.2) reads
    // queryKeys.me and doesn't have to call GET /me again right after login
    queryClient.setQueryData(queryKeys.me, user)
    await router.replace(safeRedirect(route.query.redirect))
  },
})

function submit() {
  login({ email: email.value.trim(), password: password.value })
}

// client.ts sends us here with ?redirect=<where the user was>. Only follow paths inside the app:
// "//evil.com" or "/\evil.com" would take the user to another site (open redirect).
function safeRedirect(target: unknown): string {
  if (typeof target !== 'string' || !target.startsWith('/') || /^\/[/\\]/.test(target)) {
    return '/'
  }
  return target
}

const apiError = computed(() => (error.value instanceof ApiError ? error.value : null))

/** Field messages from a 400 Problem, keyed by field name */
const fieldErrors = computed<Record<string, string>>(() =>
  apiError.value?.status === 400
    ? Object.fromEntries((apiError.value.problem.errors ?? []).map((e) => [e.field, e.message]))
    : {},
)

// Field errors appear after a submit, while focus is still on the button. Move focus to the first
// invalid field so screen readers announce its error (aria-describedby is read on focus).
// A watch, not a computed, because moving focus is a side effect.
const formEl = useTemplateRef<HTMLFormElement>('form')
watch(fieldErrors, async (errors) => {
  if (Object.keys(errors).length === 0) {
    return
  }
  await nextTick()
  formEl.value?.querySelector<HTMLInputElement>('[aria-invalid="true"]')?.focus()
})

/** The banner above the form. The 401 text doesn't say which field was wrong (BE-1.2). */
const formError = computed(() => {
  if (!error.value || apiError.value?.status === 400) {
    return null
  }
  return apiError.value?.status === 401
    ? 'Email or password is incorrect.'
    : 'Something went wrong. Please try again.'
})
</script>

<template>
  <main class="login">
    <section class="brand">
      <img :src="logoUrl" alt="Cofinpro" class="brand__logo" />
      <div class="brand__pitch">
        <p class="brand__headline">
          Your time,<br />
          <span class="brand__accent">all in one place.</span>
        </p>
        <p class="brand__text">
          Timesheets, vacations and approvals for everyone at Cofinpro Portugal.
        </p>
      </div>
      <ul class="brand__features">
        <li><span class="dot dot--primary" />Timesheets</li>
        <li><span class="dot dot--success" />Vacations</li>
        <li><span class="dot dot--training" />Approvals</li>
      </ul>
    </section>

    <section class="panel">
      <form ref="form" class="form" @submit.prevent="submit">
        <div>
          <h1 class="form__title">Sign in</h1>
          <p class="form__subtitle">Use your Cofinpro email and password.</p>
        </div>

        <div v-if="formError" class="form__error" role="alert">
          <TriangleAlert :size="18" aria-hidden="true" />
          {{ formError }}
        </div>

        <BaseInput
          v-model="email"
          label="Email"
          type="email"
          autocomplete="username"
          maxlength="255"
          required
          :error="fieldErrors.email"
        />
        <BaseInput
          v-model="password"
          label="Password"
          type="password"
          autocomplete="current-password"
          maxlength="72"
          required
          :error="fieldErrors.password"
        />

        <BaseButton type="submit" block :disabled="isPending">
          {{ isPending ? 'Signing in…' : 'Sign in' }}
        </BaseButton>

        <p class="form__help">Trouble signing in? Ask an admin to reset your password.</p>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login {
  display: flex;
  min-height: 100vh;
  background: var(--color-surface);
}

.brand {
  flex: 0 0 640px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: var(--space-16);
  background: var(--color-ink);
  color: var(--color-surface);
}

.brand__logo {
  align-self: flex-start;
  height: 26px;
}

.brand__headline {
  margin: 0 0 var(--space-5);
  font-size: 56px;
  font-weight: 700;
  line-height: 62px;
  letter-spacing: -0.02em;
}

.brand__accent {
  color: var(--color-primary);
}

.brand__text {
  max-width: 460px;
  margin: 0;
  font-size: 18px;
  line-height: 28px;
  opacity: 0.7;
}

.brand__features {
  display: flex;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.brand__features li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 500;
  opacity: 0.7;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-pill);
}

.dot--primary {
  background: var(--color-primary);
}

.dot--success {
  background: var(--color-success);
}

.dot--training {
  background: var(--color-training);
}

.panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-8) var(--space-4);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 100%;
  max-width: 400px;
}

.form__title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.01em;
}

.form__subtitle {
  margin: 6px 0 0;
  font-size: 15px;
  color: var(--color-muted);
}

.form__error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-weight: 500;
}

.form__help {
  margin: 0;
  font-size: 13px;
  color: var(--color-muted);
}

/* No room for the brand panel on narrow screens: the form alone */
@media (max-width: 1024px) {
  .brand {
    display: none;
  }
}
</style>
