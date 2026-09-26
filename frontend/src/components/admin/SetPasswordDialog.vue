<script setup lang="ts">
import { computed, ref, useTemplateRef, watch } from 'vue'
import { TriangleAlert } from 'lucide-vue-next'

import type { AdminUser } from '@/api/client'
import { fieldErrors, problemMessage } from '@/api/problems'
import { useSetAdminUserPassword } from '@/admin/queries'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import BaseInput from '@/components/BaseInput.vue'

// FE-9.1: "Set new password" in the edit dialog opens this on top of it (not in the frames; see
// design.md). For a forgotten password: there's no self-service reset (decision #35).
const { user } = defineProps<{ user: AdminUser }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const password = ref('')

const { mutate, isPending, error, reset } = useSetAdminUserPassword()
watch(password, () => reset())

const errors = computed(() => fieldErrors(error.value))
const banner = computed(() => problemMessage(error.value))

function submit() {
  mutate(
    { id: user.id, body: { password: password.value } },
    {
      onSuccess() {
        emit('saved')
        dialog.value?.close()
      },
    },
  )
}
</script>

<template>
  <BaseDialog ref="dialog" title="Set new password" width="narrow" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit">
      <p class="form__intro">
        For {{ user.name }}. They can log in with it straight away; sessions already open stay valid
        until they expire.
      </p>
      <BaseInput
        v-model="password"
        type="password"
        label="New password (8 to 72 characters)"
        autocomplete="new-password"
        minlength="8"
        maxlength="72"
        required
        :error="errors.password"
      />
      <div v-if="banner" class="form__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ banner }}
      </div>
      <footer class="form__footer">
        <BaseButton variant="secondary" @click="dialog?.close()">Cancel</BaseButton>
        <BaseButton type="submit" :disabled="isPending">
          {{ isPending ? 'Saving…' : 'Set password' }}
        </BaseButton>
      </footer>
    </form>
  </BaseDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.form__intro {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
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

.form__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* Phones (design.md, "Mobile"): full-width buttons, the main action on top */
@media (max-width: 720px) {
  .form__footer {
    flex-direction: column-reverse;
  }
}
</style>
