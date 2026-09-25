<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

import type { AbsenceRequest, AbsenceStatus, AbsenceType } from '@/api/client'
import BaseButton from '@/components/BaseButton.vue'
import { useMyAbsenceRequests, usePublicHolidays } from '@/absences/queries'
import { buildMonth, gridRange, type CalendarEntry } from '@/absences/calendar'
import { typeColor, typeName } from '@/absences/types'
import {
  addMonths,
  dayOfMonth,
  formatDays,
  formatMonth,
  formatRange,
  monthStart,
  today,
  yearOf,
} from '@/format/dates'

// FE-2.2: month view of my absences, Figma frame "02 Absences". Our own component rather than a
// calendar library (decision #28, proposed).
// Clicking a chip selects its request (FE-3.2 opens the details dialog for it)
const { types } = defineProps<{ types: AbsenceType[] | undefined }>()
const emit = defineEmits<{ select: [request: AbsenceRequest] }>()

const todayIso = today()
const month = ref(monthStart(todayIso))
const range = computed(() => gridRange(month.value))

const requests = useMyAbsenceRequests(
  () => range.value.from,
  () => range.value.to,
)
// The grid can reach into the previous or next year (e.g. 28 Dec – 7 Feb); same year = one request
const holidaysFrom = usePublicHolidays(() => yearOf(range.value.from))
const holidaysTo = usePublicHolidays(() => yearOf(range.value.to))

const showClosed = ref(false)
const HIDDEN: AbsenceStatus[] = ['REJECTED', 'CANCELLED']

const weeks = computed(() => {
  const visible = (requests.data.value ?? []).filter(
    (r) => showClosed.value || !HIDDEN.includes(r.status),
  )
  const holidays = [...(holidaysFrom.data.value ?? []), ...(holidaysTo.data.value ?? [])]
  return buildMonth(month.value, visible, holidays)
})

const legend = computed(() =>
  (['VACATION', 'TRAINING', 'SICK'] as const).map((code) => ({
    label: typeName(code, types),
    color: typeColor(code).solid,
  })),
)

function chipStyle({ request }: CalendarEntry) {
  const color = typeColor(request.type)
  if (request.status === 'PENDING') {
    return { background: color.soft }
  }
  return HIDDEN.includes(request.status) ? {} : { background: color.solid }
}

function chipLabel({ request, half }: CalendarEntry): string {
  if (half) {
    return half === 'MORNING' ? 'AM' : 'PM'
  }
  const status: Partial<Record<AbsenceStatus, string>> = {
    PENDING: 'Pending',
    REJECTED: 'Rejected',
    CANCELLED: 'Cancelled',
  }
  return status[request.status] ?? typeName(request.type, types)
}

/** Everything a screen reader needs, since colour and dashes carry meaning */
function chipDescription(r: AbsenceRequest): string {
  const part =
    r.startDate === r.endDate && r.startPart !== 'FULL' ? `, ${r.startPart.toLowerCase()}` : ''
  return `${typeName(r.type, types)}, ${r.status.toLowerCase()}, ${formatRange(r.startDate, r.endDate)}${part}, ${formatDays(r.workingDays)}`
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
</script>

<template>
  <section class="calendar" aria-labelledby="calendar-title">
    <header class="calendar__header">
      <div class="calendar__nav">
        <button
          type="button"
          class="icon-button"
          aria-label="Previous month"
          @click="month = addMonths(month, -1)"
        >
          <ChevronLeft :size="20" aria-hidden="true" />
        </button>
        <h2 id="calendar-title" class="calendar__title" aria-live="polite">
          {{ formatMonth(month) }}
        </h2>
        <button
          type="button"
          class="icon-button"
          aria-label="Next month"
          @click="month = addMonths(month, 1)"
        >
          <ChevronRight :size="20" aria-hidden="true" />
        </button>
        <BaseButton variant="secondary" size="small" @click="month = monthStart(todayIso)">
          Today
        </BaseButton>
        <span v-if="requests.isFetching.value" class="calendar__loading" role="status">
          Loading…
        </span>
      </div>

      <ul class="legend" aria-label="Legend">
        <li v-for="item in legend" :key="item.label">
          <span class="legend__dot" :style="{ background: item.color }" />{{ item.label }}
        </li>
        <li><span class="legend__dot legend__dot--holiday" />Public holiday</li>
        <li><span class="legend__pending" />Pending</li>
        <li>
          <label class="legend__toggle">
            <input v-model="showClosed" type="checkbox" />
            Show rejected and cancelled
          </label>
        </li>
      </ul>
    </header>

    <div v-if="requests.isError.value" class="calendar__error" role="alert">
      Your absences couldn't be loaded.
      <BaseButton variant="secondary" size="small" @click="requests.refetch()"
        >Try again</BaseButton
      >
    </div>

    <table class="grid">
      <thead>
        <tr>
          <th v-for="day in WEEKDAYS" :key="day" scope="col">{{ day }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="week in weeks" :key="week[0]!.date">
          <td
            v-for="day in week"
            :key="day.date"
            class="day"
            :class="{
              'day--outside': !day.inMonth,
              'day--weekend': day.weekend,
              'day--holiday': day.holiday,
              'day--today': day.date === todayIso,
            }"
            :data-date="day.date"
          >
            <span class="day__number">{{ dayOfMonth(day.date) }}</span>
            <span v-if="day.holiday" class="day__holiday">{{ day.holiday }}</span>
            <ul v-if="day.entries.length" class="day__entries">
              <li v-for="entry in day.entries" :key="entry.request.id">
                <!-- One tab stop per labelled chip, not one per day of a long absence -->
                <button
                  type="button"
                  class="chip"
                  :class="[
                    `chip--${entry.request.status.toLowerCase()}`,
                    entry.half && `chip--${entry.half.toLowerCase()}`,
                  ]"
                  :style="chipStyle(entry)"
                  :title="chipDescription(entry.request)"
                  :tabindex="entry.showLabel || entry.half ? 0 : -1"
                  @click="emit('select', entry.request)"
                >
                  <span v-if="entry.showLabel || entry.half" aria-hidden="true">
                    {{ chipLabel(entry) }}
                  </span>
                  <span class="visually-hidden">{{ chipDescription(entry.request) }}</span>
                </button>
              </li>
            </ul>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.calendar {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-6);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.calendar__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.calendar__nav {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.calendar__title {
  min-width: 150px;
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  text-align: center;
}

.calendar__loading {
  font-size: 13px;
  color: var(--color-muted);
}

.calendar__error {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  font-weight: 500;
}

.icon-button {
  display: inline-flex;
  padding: var(--space-1);
  border: none;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--color-ink);
  cursor: pointer;
}

.icon-button:focus-visible,
.legend__toggle input:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: var(--color-muted);
}

.legend li {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend__dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-pill);
}

.legend__dot--holiday {
  background: var(--color-line);
}

.legend__pending {
  width: 14px;
  height: 10px;
  border: 1px dashed var(--color-primary);
  border-radius: 3px;
  background: var(--color-primary-soft);
}

.legend__toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.grid {
  width: 100%;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
  border-collapse: separate;
  border-spacing: 0;
  overflow: hidden;
  table-layout: fixed;
}

.grid th {
  padding: 0 0 var(--space-2) 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-muted);
  text-align: left;
}

.grid thead th {
  background: var(--color-surface);
}

.day {
  height: 92px;
  padding: var(--space-2);
  border-top: 1px solid var(--color-line);
  border-right: 1px solid var(--color-line);
  vertical-align: top;
}

.day:last-child {
  border-right: none;
}

.day--weekend {
  background: var(--color-surface-alt);
}

.day--holiday {
  background: var(--color-grey);
}

.day__number {
  display: block;
  font-size: 13px;
  font-weight: 600;
}

.day--outside .day__number {
  color: var(--color-muted);
  opacity: 0.5;
}

.day--today .day__number {
  color: var(--color-primary);
}

.day__holiday {
  display: block;
  margin-top: var(--space-1);
  font-size: 11px;
  font-weight: 500;
  color: var(--color-muted);
}

.day__entries {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.chip {
  display: block;
  width: 100%;
  min-height: 24px;
  padding: 5px var(--space-2);
  overflow: hidden;
  border: none;
  border-radius: var(--radius-chip);
  font: inherit;
  text-align: left;
  cursor: pointer;
  color: var(--color-surface);
  font-size: 12px;
  font-weight: 600;
  line-height: 14px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.chip:focus-visible {
  outline: 2px solid var(--color-ink);
  outline-offset: 2px;
}

.chip--pending {
  border: 1px dashed var(--color-primary);
  color: var(--color-primary);
}

.chip--rejected,
.chip--cancelled {
  background: var(--color-grey);
  color: var(--color-muted);
  text-decoration: line-through;
}

/* Half days take half the cell, on the side of the day they cover */
.chip--morning,
.chip--afternoon {
  width: calc(50% - 2px);
}

.chip--afternoon {
  margin-left: auto;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
