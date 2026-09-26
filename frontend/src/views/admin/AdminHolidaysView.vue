<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { Lock, Plus, Trash2, TriangleAlert, X } from 'lucide-vue-next'

import type { AdminPublicHoliday } from '@/api/client'
import { conflictType, fieldErrors, isForbidden, problemMessage } from '@/api/problems'
import {
  useAdminPublicHolidays,
  useCreatePublicHoliday,
  useDeletePublicHoliday,
} from '@/admin/queries'
import BaseButton from '@/components/BaseButton.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { formatDate, isIsoDate, today, weekday, weekdayName, yearOf } from '@/format/dates'

// FE-9.4, frame "15 Admin – Public holidays": next year's list by default, an inline row to add
// a holiday, and a trash button (with a confirmation) per holiday. The contract has no "copy from
// last year" endpoint, and Easter-based holidays move, so that button is left out.

const thisYear = yearOf(today())
const year = ref(thisYear + 1)
const yearOptions = [thisYear - 1, thisYear, thisYear + 1, thisYear + 2].map((y) => ({
  value: y,
  label: String(y),
}))

const holidays = useAdminPublicHolidays(year)
const forbidden = computed(() => isForbidden(holidays.error.value))

const isWeekend = (date: string) => weekday(date) >= 5
const rows = computed(() =>
  (holidays.data.value ?? []).map((h) => ({
    holiday: h,
    date: formatDate(h.date),
    day: weekdayName(h.date),
    weekend: isWeekend(h.date),
    deleteLabel: `Delete ${h.name}`,
  })),
)

// --- Adding: an inline row at the top of the table ------------------------------------------

const adding = ref(false)
const newDate = ref('')
const newName = ref('')
const checked = ref<{ date?: string; name?: string }>({})
const create = useCreatePublicHoliday()
const dateInput = useTemplateRef<HTMLInputElement>('dateInput')

const newDay = computed(() => (isIsoDate(newDate.value) ? weekdayName(newDate.value) : ''))

/** The date the failed request was for, to name it and the holiday already on it */
const sentDate = ref('')
const addErrors = computed(() => {
  const error = create.error.value
  if (conflictType(error) === '/problems/holiday-date-taken') {
    const existing = holidays.data.value?.find((h) => h.date === sentDate.value)
    const date = formatDate(sentDate.value)
    return {
      date: existing
        ? `${date} is already a holiday (${existing.name}).`
        : `${date} is already a public holiday.`,
    }
  }
  const fields = fieldErrors(error)
  return { date: checked.value.date ?? fields.date, name: checked.value.name ?? fields.name }
})
const addBanner = computed(() =>
  conflictType(create.error.value) ? null : problemMessage(create.error.value),
)

watch([newDate, newName], () => {
  create.reset()
  checked.value = {}
})

async function startAdding() {
  adding.value = true
  await nextTick()
  dateInput.value?.focus()
}

function cancelAdding() {
  adding.value = false
  newDate.value = ''
  newName.value = ''
  create.reset()
  checked.value = {}
}

async function addHoliday() {
  const date = newDate.value
  const name = newName.value.trim()
  checked.value = {
    date: !isIsoDate(date)
      ? 'Pick a date.'
      : yearOf(date) !== year.value
        ? `Pick a date in ${year.value}.`
        : undefined,
    name: name ? undefined : 'Enter a name.',
  }
  if (checked.value.date || checked.value.name) return
  sentDate.value = date
  try {
    await create.mutateAsync({ date, name })
    cancelAdding()
  } catch {
    // The mutation's error shows in the row
  }
}

// Another year starts without a half-typed holiday
watch(year, cancelAdding)

// --- Deleting, after a confirmation --------------------------------------------------------

const deleting = ref<AdminPublicHoliday | null>(null)
const remove = useDeletePublicHoliday()
const confirm = useTemplateRef<InstanceType<typeof ConfirmDialog>>('confirm')

function askDelete(holiday: AdminPublicHoliday) {
  remove.reset()
  deleting.value = holiday
}

async function deleteHoliday() {
  try {
    await remove.mutateAsync(deleting.value!.id)
    confirm.value?.close()
  } catch {
    // Shown in the dialog
  }
}

const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
const deleteText = computed(() => {
  const holiday = deleting.value
  if (!holiday) return ''
  const date = formatDate(holiday.date)
  return isWeekend(holiday.date)
    ? `${date} is a ${WEEKDAY_NAMES[weekday(holiday.date)]}, so no working days change.`
    : `${date} becomes a working day again for absence requests made from now on. Requests already made keep their working days.`
})

const title = computed(() => `Public holidays ${year.value}`)
const emptyText = computed(
  () => `No public holidays for ${year.value} yet. Add them with “Add holiday”.`,
)
</script>

<template>
  <section class="section" aria-labelledby="holidays-title">
    <header class="section__header">
      <div>
        <h2 id="holidays-title" class="section__title">{{ title }}</h2>
        <p v-if="!forbidden" class="section__subtitle">
          Excluded from working days. Weekend holidays have no effect.
        </p>
      </div>
      <div v-if="!forbidden" class="section__actions">
        <BaseSelect v-model="year" label="Year" :options="yearOptions" />
        <BaseButton :disabled="!holidays.isSuccess.value || adding" @click="startAdding">
          <Plus :size="18" aria-hidden="true" />
          Add holiday
        </BaseButton>
      </div>
    </header>

    <p v-if="holidays.isPending.value" class="state" role="status">Loading public holidays…</p>
    <div v-else-if="forbidden" class="state state--forbidden" role="alert">
      <Lock :size="24" aria-hidden="true" class="state__icon" />
      <p class="state__title">You need an admin account</p>
      <p>Only admins can manage public holidays. Ask an admin if you need access.</p>
    </div>
    <div v-else-if="holidays.isError.value" class="state state--error" role="alert">
      <p>The public holidays couldn't be loaded.</p>
      <BaseButton variant="secondary" size="small" @click="holidays.refetch()"
        >Try again</BaseButton
      >
    </div>
    <p v-else-if="rows.length === 0 && !adding" class="state" role="status">{{ emptyText }}</p>
    <div v-else class="card card--stack">
      <!-- The add row's inputs belong to this form through their `form` attribute: a <form>
           can't sit inside a <tr>, but Enter in either input still submits it -->
      <form id="add-holiday" novalidate @submit.prevent="addHoliday" />
      <table class="table table--stack">
        <caption class="visually-hidden">
          {{
            title
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col" class="col-date">Date</th>
            <th scope="col" class="col-day">Day</th>
            <th scope="col">Name</th>
            <th scope="col" class="col-actions"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="adding" class="add" @keydown.esc="cancelAdding">
            <td class="stack-lead">
              <input
                ref="dateInput"
                v-model="newDate"
                type="date"
                form="add-holiday"
                aria-label="Date"
                :min="`${year}-01-01`"
                :max="`${year}-12-31`"
                class="add__input add__input--date"
                :class="{ 'add__input--error': addErrors.date }"
                :aria-invalid="addErrors.date ? 'true' : undefined"
                :aria-describedby="addErrors.date ? 'add-holiday-date-error' : undefined"
              />
              <p v-if="addErrors.date" id="add-holiday-date-error" class="add__error">
                {{ addErrors.date }}
              </p>
            </td>
            <td class="muted" data-label="Day">{{ newDay }}</td>
            <td data-label="Name" class="stack-wide">
              <div class="add__name">
                <input
                  v-model="newName"
                  type="text"
                  form="add-holiday"
                  aria-label="Name"
                  maxlength="255"
                  placeholder="e.g. Christmas Eve"
                  class="add__input"
                  :class="{ 'add__input--error': addErrors.name }"
                  :aria-invalid="addErrors.name ? 'true' : undefined"
                  :aria-describedby="addErrors.name ? 'add-holiday-name-error' : undefined"
                />
                <BaseButton
                  type="submit"
                  size="small"
                  form="add-holiday"
                  :disabled="create.isPending.value"
                >
                  {{ create.isPending.value ? 'Saving…' : 'Save' }}
                </BaseButton>
              </div>
              <p v-if="addErrors.name" id="add-holiday-name-error" class="add__error">
                {{ addErrors.name }}
              </p>
              <p v-if="addBanner" class="add__error" role="alert">
                <TriangleAlert :size="14" aria-hidden="true" />
                {{ addBanner }}
              </p>
            </td>
            <td class="actions stack-corner">
              <button
                type="button"
                class="icon-button"
                aria-label="Cancel adding"
                @click="cancelAdding"
              >
                <X :size="18" aria-hidden="true" />
              </button>
            </td>
          </tr>
          <tr v-for="row in rows" :key="row.holiday.id" :class="{ weekend: row.weekend }">
            <th scope="row" class="date">{{ row.date }}</th>
            <td class="muted" data-label="Day">{{ row.day }}</td>
            <td data-label="Name">
              <span class="name">
                {{ row.holiday.name }}
                <StatusBadge v-if="row.weekend" status="draft" label="Weekend" />
              </span>
            </td>
            <td class="actions stack-corner">
              <button
                type="button"
                class="icon-button"
                :aria-label="row.deleteLabel"
                @click="askDelete(row.holiday)"
              >
                <Trash2 :size="18" aria-hidden="true" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ConfirmDialog
      v-if="deleting"
      ref="confirm"
      :icon="Trash2"
      :heading="`Delete ${deleting.name}?`"
      confirm-label="Delete holiday"
      danger
      :pending="remove.isPending.value"
      :error="problemMessage(remove.error.value)"
      @confirm="deleteHoliday"
      @close="deleting = null"
    >
      {{ deleteText }}
    </ConfirmDialog>
  </section>
</template>

<style scoped src="../../admin/section.css"></style>

<style scoped>
.col-date {
  width: 160px;
}

.col-day {
  width: 100px;
}

.col-actions {
  width: 64px;
}

.table tbody th.date {
  font-weight: 600;
}

.weekend,
.table tbody .weekend th.date {
  color: var(--color-muted);
}

.name {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.table td.actions {
  text-align: right;
}

.table tr.add td {
  vertical-align: top;
}

.add__name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.add__input {
  width: 260px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
  background: var(--color-surface);
  color: var(--color-ink);
  font: inherit;
  font-size: 14px;
}

.add__input--date {
  width: 140px;
}

.add__input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.add__input--error,
.add__input--error:focus {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 1px var(--color-danger);
}

.add__error {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  max-width: 320px;
  margin: var(--space-1) 0 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-danger);
  white-space: normal;
}
</style>
