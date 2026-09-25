<script setup lang="ts">
import { computed, useId, useTemplateRef } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { TriangleAlert } from 'lucide-vue-next'

import { api, type AbsenceRequest, type AbsenceType } from '@/api/client'
import { problemMessage, showsStaleData } from '@/api/problems'
import { queryKeys } from '@/api/queryKeys'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import { typeName } from '@/absences/types'
import { formatDays, formatRangeWithYear } from '@/format/dates'

// FE-3.2, Figma frame "09 Absences – Cancel confirmation". Opens on top of the details dialog.
const { request, types } = defineProps<{
  request: AbsenceRequest
  types: AbsenceType[] | undefined
}>()
const emit = defineEmits<{ close: []; cancelled: [request: AbsenceRequest] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const titleId = useId()
const queryClient = useQueryClient()

const summary = computed(
  () =>
    `${typeName(request.type, types)} · ${formatRangeWithYear(request.startDate, request.endDate)} · ${formatDays(request.workingDays)}.`,
)

const { mutate, isPending, error } = useMutation({
  mutationFn: () => api.cancelMyAbsenceRequest(request.id),
  async onSuccess(cancelled) {
    // The balance and every calendar month may have changed
    await queryClient.invalidateQueries({ queryKey: queryKeys.absences.all })
    emit('cancelled', cancelled)
    dialog.value?.close()
  },
  onError(error) {
    if (showsStaleData(error)) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.absences.all })
    }
  },
})

const banner = computed(() => problemMessage(error.value))
</script>

<template>
  <BaseDialog ref="dialog" width="narrow" :labelledby="titleId" @close="emit('close')">
    <div class="confirm">
      <span class="confirm__icon" aria-hidden="true">
        <TriangleAlert :size="22" />
      </span>
      <h2 :id="titleId" class="confirm__title">Cancel this request?</h2>
      <p class="confirm__text">
        {{ summary }} The days go back to your balance. If the request was already approved, your
        team lead is notified.
      </p>

      <div v-if="banner" class="confirm__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ banner }}
      </div>

      <footer class="confirm__footer">
        <BaseButton variant="secondary" @click="dialog?.close()">Keep request</BaseButton>
        <BaseButton variant="danger-fill" :disabled="isPending" @click="mutate()">
          {{ isPending ? 'Cancelling…' : 'Cancel request' }}
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
  background: var(--color-danger-soft);
  color: var(--color-danger);
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
</style>
