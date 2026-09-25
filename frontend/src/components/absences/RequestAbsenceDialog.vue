<script setup lang="ts">
import { computed, ref, useTemplateRef, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { CalendarDays, TriangleAlert } from 'lucide-vue-next'

import { api, type AbsenceRequest, type AbsenceTypeCode, type DayPart } from '@/api/client'
import { fieldErrors, problemMessage, showsStaleData } from '@/api/problems'
import { queryKeys } from '@/api/queryKeys'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import SegmentedControl from '@/components/SegmentedControl.vue'
import { useAbsenceBalance, useAbsenceTypes, usePublicHolidays } from '@/absences/queries'
import { allowedParts, workingDays } from '@/absences/workingDays'
import { formatNumber, today, yearOf } from '@/format/dates'

// FE-3.1, Figma frame "03 Absences – Request dialog"
const emit = defineEmits<{ close: []; created: [request: AbsenceRequest] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const queryClient = useQueryClient()
const { data: types } = useAbsenceTypes()

const type = ref<AbsenceTypeCode>('VACATION')
const startDate = ref(today())
const endDate = ref(today())
const startPart = ref<DayPart>('FULL')
const endPart = ref<DayPart>('FULL')
const reason = ref('')

const typeOptions = computed(() =>
  (types.value ?? []).map((t) => ({ value: t.code, label: t.name })),
)
const singleDay = computed(() => startDate.value === endDate.value)

// Keep the range valid while the user types: the end can't be before the start, and a single day
// has one part (the end part follows the start part)
watch(startDate, (start) => {
  if (endDate.value < start) {
    endDate.value = start
  }
})
watch([singleDay, startPart], ([single, part]) => {
  if (single) {
    endPart.value = part
  }
})
// A multi-day request starts FULL/AFTERNOON and ends FULL/MORNING (contract, DayPart)
watch(singleDay, (single) => {
  if (!single && !allowedParts(false, 'start').includes(startPart.value)) startPart.value = 'FULL'
  if (!single && !allowedParts(false, 'end').includes(endPart.value)) endPart.value = 'FULL'
})

const PART_LABELS: Record<DayPart, string> = {
  FULL: 'Full day',
  MORNING: 'Morning',
  AFTERNOON: 'Afternoon',
}
const partOptions = (end: 'start' | 'end') =>
  (['FULL', 'MORNING', 'AFTERNOON'] as const).map((value) => ({
    value,
    label: PART_LABELS[value],
    // On a single day the end simply follows the start
    disabled:
      (end === 'end' && singleDay.value) || !allowedParts(singleDay.value, end).includes(value),
  }))

// Live preview (the backend's count is the authoritative one, decision #15)
const startYear = computed(() => yearOf(startDate.value))
const endYear = computed(() => yearOf(endDate.value))
const holidaysStart = usePublicHolidays(startYear)
const holidaysEnd = usePublicHolidays(endYear)
const days = computed(() => {
  if (!startDate.value || !endDate.value || endDate.value < startDate.value) {
    return null
  }
  const holidays = new Set(
    [...(holidaysStart.data.value ?? []), ...(holidaysEnd.data.value ?? [])].map((h) => h.date),
  )
  return workingDays(
    {
      startDate: startDate.value,
      endDate: endDate.value,
      startPart: startPart.value,
      endPart: endPart.value,
    },
    holidays,
  )
})

// "N days left after approval", only for a type with a balance and a request within one year
const balance = useAbsenceBalance(startYear)
const left = computed(() => {
  const deducts = types.value?.find((t) => t.code === type.value)?.deductsFromBalance
  const remaining = balance.data.value?.find((b) => b.type === type.value)?.remainingDays
  if (!deducts || remaining == null || days.value === null || startYear.value !== endYear.value) {
    return null
  }
  return remaining - days.value
})

const daysText = computed(() =>
  days.value === null
    ? ''
    : `${formatNumber(days.value)} working ${days.value === 1 || days.value === 0.5 ? 'day' : 'days'}`,
)

const { mutate, isPending, error, reset } = useMutation({
  mutationFn: api.createMyAbsenceRequest,
  async onSuccess(created) {
    // The balance and every calendar month may have changed
    await queryClient.invalidateQueries({ queryKey: queryKeys.absences.all })
    emit('created', created)
    dialog.value?.close()
  },
  onError(error) {
    if (showsStaleData(error)) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.absences.all })
    }
  },
})

// A new attempt starts without the previous answer's errors
watch([type, startDate, endDate, startPart, endPart], () => reset())

const errors = computed(() => fieldErrors(error.value))
const banner = computed(() => problemMessage(error.value))

function submit() {
  mutate({
    type: type.value,
    startDate: startDate.value,
    endDate: endDate.value,
    startPart: startPart.value,
    endPart: endPart.value,
    ...(reason.value.trim() ? { reason: reason.value.trim() } : {}),
  })
}
</script>

<template>
  <BaseDialog ref="dialog" title="Request absence" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit">
      <BaseSelect
        v-model="type"
        variant="field"
        label="Type"
        :options="typeOptions"
        :error="errors.type"
      />

      <div class="form__row">
        <BaseInput
          v-model="startDate"
          type="date"
          label="Start date"
          required
          :error="errors.startDate"
        />
        <SegmentedControl
          v-model="startPart"
          label="Start"
          :options="partOptions('start')"
          :error="errors.startPart"
        />
      </div>

      <div class="form__row">
        <BaseInput
          v-model="endDate"
          type="date"
          label="End date"
          :min="startDate"
          required
          :error="errors.endDate"
        />
        <SegmentedControl
          v-model="endPart"
          label="End"
          :options="partOptions('end')"
          :error="errors.endPart"
        />
      </div>

      <BaseInput
        v-model="reason"
        multiline
        label="Reason (optional)"
        maxlength="500"
        :error="errors.reason"
      />

      <div v-if="days !== null" class="preview" aria-live="polite">
        <CalendarDays :size="24" aria-hidden="true" class="preview__icon" />
        <div>
          <p class="preview__days">{{ daysText }}</p>
          <p v-if="days === 0" class="preview__note preview__note--warning">
            There are no working days in this range.
          </p>
          <p v-else class="preview__note">
            Weekends and public holidays excluded
            <template v-if="left !== null && left >= 0">
              · {{ formatNumber(left) }} {{ left === 1 ? 'day' : 'days' }} left after approval
            </template>
          </p>
          <p v-if="left !== null && left < 0" class="preview__note preview__note--warning">
            That’s {{ formatNumber(-left) }} more than you have left in {{ startYear }}.
          </p>
        </div>
      </div>

      <div v-if="banner" class="form__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ banner }}
      </div>

      <footer class="form__footer">
        <BaseButton variant="secondary" @click="dialog?.close()">Cancel</BaseButton>
        <BaseButton type="submit" :disabled="isPending || days === 0">
          {{ isPending ? 'Submitting…' : 'Submit request' }}
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

.form__row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-4);
  align-items: start;
}

.preview {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: var(--space-4);
  border-radius: 12px;
  background: var(--color-primary-soft);
}

.preview__icon {
  flex: none;
  color: var(--color-primary);
}

.preview__days {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.preview__note {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--color-muted);
}

.preview__note--warning {
  color: var(--color-danger);
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
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
