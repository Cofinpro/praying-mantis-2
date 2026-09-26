<script setup lang="ts">
import { useId, useTemplateRef, type Component } from 'vue'
import { TriangleAlert } from 'lucide-vue-next'

import BaseButton from './BaseButton.vue'
import BaseDialog from './BaseDialog.vue'

// A 460px confirmation as in "09 Absences – Cancel confirmation": no title row, a round icon, a
// heading, the text (default slot) and the actions, secondary first. Emits `confirm` and leaves
// closing to the parent, so it can stay open with `error` when the action fails.
const {
  icon = TriangleAlert,
  cancelLabel = 'Cancel',
  danger = false,
} = defineProps<{
  heading: string
  confirmLabel: string
  cancelLabel?: string
  /** A danger-fill confirm button, for destructive actions */
  danger?: boolean
  icon?: Component
  /** Disables the buttons while the action runs */
  pending?: boolean
  /** Shown in a danger banner above the actions */
  error?: string | null
}>()
const emit = defineEmits<{ confirm: []; close: [] }>()

const headingId = useId()
const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')

defineExpose({ close: () => dialog.value?.close() })
</script>

<template>
  <BaseDialog ref="dialog" width="narrow" :labelledby="headingId" @close="emit('close')">
    <div class="confirm">
      <span class="confirm__icon" aria-hidden="true"><component :is="icon" :size="22" /></span>
      <h2 :id="headingId" class="confirm__title">{{ heading }}</h2>
      <p class="confirm__text"><slot /></p>
      <p v-if="error" class="confirm__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ error }}
      </p>
      <footer class="confirm__footer">
        <BaseButton variant="secondary" :disabled="pending" @click="dialog?.close()">
          {{ cancelLabel }}
        </BaseButton>
        <BaseButton
          :variant="danger ? 'danger-fill' : 'primary'"
          :disabled="pending"
          @click="emit('confirm')"
        >
          {{ confirmLabel }}
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
  margin: 0;
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
