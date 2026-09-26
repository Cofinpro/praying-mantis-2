<script setup lang="ts">
import { computed, ref, useId, useTemplateRef, watch } from 'vue'
import { CircleCheck, Info, KeyRound, TriangleAlert } from 'lucide-vue-next'

import type { AdminUser, AdminUserUpdate, Client, Level } from '@/api/client'
import { useCreateAdminUser, useUpdateAdminUser } from '@/admin/queries'
import { userFormErrors } from '@/admin/userForm'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseToggle from '@/components/BaseToggle.vue'
import SetPasswordDialog from '@/components/admin/SetPasswordDialog.vue'
import { CLIENT_CODES, LEVEL_CODES, clientLabel, levelLabel } from '@/format/labels'

// FE-9.1, frame "12 Admin – Edit user". Without `user` it's "Add user": the same form plus the
// initial password the admin sets (decision #35).
const { user, users } = defineProps<{
  /** The user to edit; leave it out to add one */
  user?: AdminUser
  /** Everyone, for the team lead choices and to name who already has an email */
  users: AdminUser[]
}>()
const emit = defineEmits<{ close: []; saved: [user: AdminUser] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')

const name = ref(user?.name ?? '')
const email = ref(user?.email ?? '')
const client = ref<Client>(user?.client ?? 'DKB')
const level = ref<Level>(user?.level ?? 'JUNIOR')
/** 0 = no team lead: a <select> can't hold undefined, and ids start at 1 */
const teamLeadId = ref<number>(user?.teamLead?.id ?? 0)
const isAdmin = ref(user?.isAdmin ?? false)
const password = ref('')

const clientOptions = CLIENT_CODES.map((value) => ({ value, label: clientLabel(value) }))
const levelOptions = LEVEL_CODES.map((value) => ({ value, label: levelLabel(value) }))
// Everyone but the user themself (a 400 otherwise); the backend rejects longer cycles with a 409
const teamLeadOptions = computed(() => [
  { value: 0, label: 'No team lead (an admin approves)' },
  ...users.filter((u) => u.id !== user?.id).map((u) => ({ value: u.id, label: u.name })),
])

const create = useCreateAdminUser()
const update = useUpdateAdminUser()
const mutation = user ? update : create
const isPending = computed(() => mutation.isPending.value)

// A change starts over without the previous answer's errors
watch([name, email, client, level, teamLeadId, isAdmin, password], () => mutation.reset())

// Snapshot of what was sent, so the messages name what the user tried, not what they typed since
const sentEmail = ref('')
const sentTeamLead = ref(0)
const errors = computed(() =>
  userFormErrors(mutation.error.value, {
    users,
    editedName: user?.name,
    teamLeadName: users.find((u) => u.id === sentTeamLead.value)?.name,
    email: sentEmail.value,
  }),
)

function body(): AdminUserUpdate {
  return {
    name: name.value.trim(),
    email: email.value.trim(),
    client: client.value,
    level: level.value,
    isAdmin: isAdmin.value,
    // Left out means "no team lead" (contract)
    ...(teamLeadId.value ? { teamLeadId: teamLeadId.value } : {}),
  }
}

async function submit() {
  sentEmail.value = email.value
  sentTeamLead.value = teamLeadId.value
  try {
    const saved = user
      ? await update.mutateAsync({ id: user.id, body: body() })
      : await create.mutateAsync({ ...body(), password: password.value })
    emit('saved', saved)
    dialog.value?.close()
  } catch {
    // The mutation's error shows in the form
  }
}

// "Set new password" opens a small dialog on top (edit only)
const settingPassword = ref(false)
const passwordSet = ref(false)

const adminLabelId = useId()
const adminHintId = useId()
const adminErrorId = useId()
const adminDescribedBy = computed(() =>
  errors.value.fields.isAdmin ? `${adminHintId} ${adminErrorId}` : adminHintId,
)
</script>

<template>
  <BaseDialog ref="dialog" :title="user ? 'Edit user' : 'Add user'" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit">
      <BaseInput
        v-model="name"
        label="Name"
        autocomplete="off"
        maxlength="255"
        required
        :error="errors.fields.name"
      />
      <BaseInput
        v-model="email"
        type="email"
        label="Email"
        autocomplete="off"
        maxlength="255"
        required
        :error="errors.fields.email"
      />
      <BaseInput
        v-if="!user"
        v-model="password"
        type="password"
        label="Initial password (8 to 72 characters)"
        autocomplete="new-password"
        minlength="8"
        maxlength="72"
        required
        :error="errors.fields.password"
      />

      <div class="form__row">
        <BaseSelect
          v-model="client"
          variant="field"
          label="Client"
          :options="clientOptions"
          :error="errors.fields.client"
        />
        <BaseSelect
          v-model="level"
          variant="field"
          label="Level"
          :options="levelOptions"
          :error="errors.fields.level"
        />
      </div>

      <BaseSelect
        v-model="teamLeadId"
        variant="field"
        label="Team lead"
        :options="teamLeadOptions"
        :error="errors.fields.teamLeadId"
      />

      <div class="admin" :class="{ 'admin--error': errors.fields.isAdmin }">
        <div class="admin__row">
          <div>
            <p :id="adminLabelId" class="admin__label">Admin</p>
            <p :id="adminHintId" class="admin__hint">
              Can manage users, entitlements, projects and holidays
            </p>
          </div>
          <BaseToggle
            v-model="isAdmin"
            :labelledby="adminLabelId"
            :describedby="adminDescribedBy"
          />
        </div>
        <p v-if="errors.fields.isAdmin" :id="adminErrorId" class="admin__error">
          {{ errors.fields.isAdmin }}
        </p>
      </div>

      <p class="note">
        <Info :size="18" aria-hidden="true" class="note__icon" />
        Nobody can be their own team lead, directly or through others. The backend rejects cycles.
      </p>

      <p v-if="passwordSet" class="success" role="status">
        <CircleCheck :size="18" aria-hidden="true" />
        New password set for {{ user?.name }}.
      </p>

      <div v-if="errors.banner" class="form__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ errors.banner }}
      </div>

      <footer class="form__footer">
        <BaseButton v-if="user" variant="ghost" @click="settingPassword = true">
          <KeyRound :size="18" aria-hidden="true" />
          Set new password
        </BaseButton>
        <div class="form__actions">
          <BaseButton variant="secondary" @click="dialog?.close()">Cancel</BaseButton>
          <BaseButton type="submit" :disabled="isPending">
            {{ isPending ? 'Saving…' : user ? 'Save changes' : 'Add user' }}
          </BaseButton>
        </div>
      </footer>
    </form>
  </BaseDialog>

  <SetPasswordDialog
    v-if="settingPassword && user"
    :user="user"
    @saved="passwordSet = true"
    @close="settingPassword = false"
  />
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
  align-items: start;
}

.admin {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: 14px var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: 12px;
}

.admin--error {
  border-color: var(--color-danger);
}

.admin__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.admin__label {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.admin__hint {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-muted);
}

.admin__error {
  margin: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-danger);
}

.note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0;
  padding: 14px;
  border-radius: 12px;
  background: var(--color-surface-alt);
  font-size: 13px;
  line-height: 19px;
  color: var(--color-muted);
}

.note__icon {
  flex: none;
}

.success {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-success-soft);
  color: var(--color-success-ink);
  font-size: 14px;
  font-weight: 500;
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
  align-items: center;
  gap: var(--space-3);
}

.form__actions {
  display: flex;
  gap: var(--space-3);
  margin-left: auto;
}
</style>
