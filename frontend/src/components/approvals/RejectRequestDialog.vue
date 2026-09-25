<script setup lang="ts">
import { computed, ref, useId, useTemplateRef } from 'vue'
import { TriangleAlert } from 'lucide-vue-next'

import type { AbsenceType, TeamAbsenceRequest } from '@/api/client'
import { fieldErrors, problemMessage } from '@/api/problems'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import BaseInput from '@/components/BaseInput.vue'
import { APPROVAL_MESSAGES } from '@/approvals/messages'
import { useDecideAbsenceRequest } from '@/approvals/queries'
import { typeName } from '@/absences/types'

// FE-5.1, the reject popover in Figma frame "04 Approvals". It's a modal <dialog> (BaseDialog)
// instead of a popover anchored to the row: focus trap, Esc and the inert page come for free,
// and there's no positioning code. The comment is required (decision 31).
const { item, types } = defineProps<{
  item: TeamAbsenceRequest
  types: AbsenceType[] | undefined
}>()
const emit = defineEmits<{ close: [] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const titleId = useId()

const title = computed(() => {
  const firstName = item.requester.name.trim().split(/\s+/)[0]
  return `Reject ${firstName}’s ${typeName(item.request.type, types).toLowerCase()} request?`
})

const comment = ref('')
/** Set on submit, so the field isn't red before the user tried */
const blank = ref(false)

const { mutate, isPending, error } = useDecideAbsenceRequest()

function submit() {
  blank.value = comment.value.trim() === ''
  if (blank.value) {
    return
  }
  mutate(
    { item, decision: 'reject', comment: comment.value.trim() },
    { onSuccess: () => dialog.value?.close() },
  )
}

const commentError = computed(() =>
  blank.value ? 'Please say why, so they know what to change.' : fieldErrors(error.value).comment,
)
const banner = computed(() => problemMessage(error.value, APPROVAL_MESSAGES))
</script>

<template>
  <BaseDialog ref="dialog" width="narrow" :labelledby="titleId" @close="emit('close')">
    <form class="reject" novalidate @submit.prevent="submit">
      <h2 :id="titleId" class="reject__title">{{ title }}</h2>

      <BaseInput
        v-model="comment"
        label="Comment (required)"
        multiline
        required
        maxlength="500"
        :error="commentError"
      />

      <div v-if="banner" class="reject__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ banner }}
      </div>

      <footer class="reject__footer">
        <BaseButton variant="ghost" size="small" @click="dialog?.close()">Cancel</BaseButton>
        <BaseButton type="submit" variant="dark" size="small" :disabled="isPending">
          {{ isPending ? 'Rejecting…' : 'Reject request' }}
        </BaseButton>
      </footer>
    </form>
  </BaseDialog>
</template>

<style scoped>
.reject {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.reject__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.reject__error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: 13px;
  font-weight: 500;
}

.reject__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
