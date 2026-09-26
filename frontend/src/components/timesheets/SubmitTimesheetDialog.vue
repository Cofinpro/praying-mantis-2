<script setup lang="ts">
import { computed, useId, useTemplateRef } from 'vue'
import { Send, TriangleAlert } from 'lucide-vue-next'

import { problemMessage } from '@/api/problems'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import { useSubmitTimesheet } from '@/timesheets/queries'

// FE-6.2: the confirmation before "Submit week" (frame "05 Timesheets" has the button; the
// confirmation follows the 460px pattern of "09 Absences – Cancel confirmation").
const { weekStart, title, totalHours } = defineProps<{
  weekStart: string
  /** "Week 43 · 19–25 Oct 2026" */
  title: string
  totalHours: string
}>()
const emit = defineEmits<{ close: [] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const titleId = useId()

const submit = useSubmitTimesheet()

// A timesheet has its own wording for the conflicts the absence forms also know
const MESSAGES = {
  '/problems/no-approver': 'Nobody can approve this week yet. Please ask an admin.',
}
const banner = computed(() => problemMessage(submit.error.value, MESSAGES))

const summary = computed(() =>
  totalHours === '–' ? `${title}, no hours.` : `${title}, ${totalHours} hours.`,
)

function onSubmit() {
  submit.mutate(weekStart, { onSuccess: () => dialog.value?.close() })
}
</script>

<template>
  <BaseDialog ref="dialog" width="narrow" :labelledby="titleId" @close="emit('close')">
    <div class="confirm">
      <span class="confirm__icon" aria-hidden="true"><Send :size="22" /></span>
      <h2 :id="titleId" class="confirm__title">Submit this week?</h2>
      <p class="confirm__text">
        {{ summary }} Your team lead (or an admin, if you have none) is notified to approve it.
        Until then, the week is read-only.
      </p>

      <div v-if="banner" class="confirm__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ banner }}
      </div>

      <footer class="confirm__footer">
        <BaseButton variant="secondary" @click="dialog?.close()">Cancel</BaseButton>
        <BaseButton :disabled="submit.isPending.value" @click="onSubmit">
          {{ submit.isPending.value ? 'Submitting…' : 'Submit week' }}
        </BaseButton>
      </footer>
    </div>
  </BaseDialog>
</template>

<style scoped>
.confirm {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-4);
}

.confirm__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.confirm__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.confirm__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-muted);
}

.confirm__error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  align-self: stretch;
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-weight: 500;
}

.confirm__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  align-self: stretch;
}

/* Phones (design.md, "Mobile"): full-width buttons, the main action on top */
@media (max-width: 720px) {
  .confirm__footer {
    flex-direction: column-reverse;
  }
}
</style>
