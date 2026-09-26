<script setup lang="ts">
import { computed, ref, useId, useTemplateRef, watch } from 'vue'
import { TriangleAlert } from 'lucide-vue-next'

import type { Client, Project, ProjectInput } from '@/api/client'
import { checkProject, projectFormErrors } from '@/admin/projectForm'
import { useCreateAdminProject, useUpdateAdminProject } from '@/admin/queries'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseToggle from '@/components/BaseToggle.vue'
import { CLIENT_CODES, clientLabel } from '@/format/labels'

// FE-9.3, the project dialog of frame "14 Admin – Projects". Without `project` it's "Add project".
const { project } = defineProps<{ project?: Project }>()
const emit = defineEmits<{ close: []; saved: [project: Project] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')

const code = ref(project?.code ?? '')
const name = ref(project?.name ?? '')
/** '' = internal: a <select> can't hold undefined */
const client = ref<Client | ''>(project ? (project.client ?? '') : 'DKB')
const isBillable = ref(project?.isBillable ?? true)
const isActive = ref(project?.isActive ?? true)

const clientOptions: { value: Client | ''; label: string }[] = [
  { value: '', label: 'Internal (no client)' },
  ...CLIENT_CODES.map((value) => ({ value, label: clientLabel(value) })),
]

const create = useCreateAdminProject()
const update = useUpdateAdminProject()
const mutation = project ? update : create
const isPending = computed(() => mutation.isPending.value)

/** What the form found before sending; the backend's answer comes after */
const checked = ref<ReturnType<typeof checkProject>>({})
const sentCode = ref('')
const errors = computed(() => {
  const server = projectFormErrors(mutation.error.value, sentCode.value)
  return { fields: { ...server.fields, ...checked.value }, banner: server.banner }
})

// A change starts over without the previous answer's errors
watch([code, name, client, isBillable, isActive], () => {
  mutation.reset()
  checked.value = {}
})

function body(): ProjectInput {
  return {
    code: code.value.trim(),
    name: name.value.trim(),
    isBillable: isBillable.value,
    isActive: isActive.value,
    // Left out means internal (contract)
    ...(client.value ? { client: client.value } : {}),
  }
}

async function submit() {
  checked.value = checkProject(code.value, name.value)
  if (Object.keys(checked.value).length) return
  sentCode.value = code.value
  try {
    const saved = project
      ? await update.mutateAsync({ id: project.id, body: body() })
      : await create.mutateAsync(body())
    emit('saved', saved)
    dialog.value?.close()
  } catch {
    // The mutation's error shows in the form
  }
}

const billableLabel = useId()
const billableHint = useId()
const activeLabel = useId()
const activeHint = useId()
</script>

<template>
  <BaseDialog ref="dialog" :title="project ? 'Edit project' : 'Add project'" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit">
      <div class="form__row">
        <BaseInput
          v-model="code"
          label="Code"
          autocomplete="off"
          maxlength="30"
          required
          :error="errors.fields.code"
        />
        <BaseSelect
          v-model="client"
          variant="field"
          label="Client"
          :options="clientOptions"
          :error="errors.fields.client"
        />
      </div>
      <BaseInput
        v-model="name"
        label="Name"
        autocomplete="off"
        maxlength="255"
        required
        :error="errors.fields.name"
      />

      <div class="switch">
        <div>
          <p :id="billableLabel" class="switch__label">Billable</p>
          <p :id="billableHint" class="switch__hint">Hours on it are billed to the client</p>
        </div>
        <BaseToggle v-model="isBillable" :labelledby="billableLabel" :describedby="billableHint" />
      </div>
      <div class="switch">
        <div>
          <p :id="activeLabel" class="switch__label">Active</p>
          <p :id="activeHint" class="switch__hint">
            Only active projects can be picked in timesheets. Hours already booked stay.
          </p>
        </div>
        <BaseToggle v-model="isActive" :labelledby="activeLabel" :describedby="activeHint" />
      </div>

      <div v-if="errors.banner" class="form__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ errors.banner }}
      </div>

      <footer class="form__footer">
        <BaseButton variant="secondary" @click="dialog?.close()">Cancel</BaseButton>
        <BaseButton type="submit" :disabled="isPending">
          {{ isPending ? 'Saving…' : project ? 'Save changes' : 'Add project' }}
        </BaseButton>
      </footer>
    </form>
  </BaseDialog>
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

.switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 14px var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: 12px;
}

.switch__label {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.switch__hint {
  margin: 2px 0 0;
  font-size: 12px;
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

/* Phones (design.md, "Mobile"): one field per line, full-width buttons with the main action on top */
@media (max-width: 720px) {
  .form__row {
    grid-template-columns: 1fr;
  }

  .form__footer {
    flex-direction: column-reverse;
  }
}
</style>
