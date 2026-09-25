<script setup lang="ts">
import { computed, nextTick, ref, useId, useTemplateRef } from 'vue'
import { MessageSquareText, Plus, Trash2 } from 'lucide-vue-next'

import type { Project } from '@/api/client'
import BaseButton from '@/components/BaseButton.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import { dayOfMonth, formatWeekdayDate, weekday, weekdayName } from '@/format/dates'
import {
  cellKey,
  dayTotals,
  formatHours,
  parseHours,
  projectSubtitle,
  rowTotal,
  type GridRow,
} from '@/timesheets/grid'

// FE-6.1, the week grid of Figma frame "05 Timesheets": a real <table> (rows = projects, columns =
// days) so screen readers announce the project and day of every cell. The view owns the rows;
// this component only renders them and reports what the user typed.

export interface DayMark {
  label: string
  /** Accessible and hover text, e.g. "Training (approved absence)" */
  title: string
  color: string
  background: string
}

const {
  rows,
  days,
  projects,
  marks = {},
  readOnly = false,
  cellErrors = new Map<string, string>(),
  dayErrors = new Map<string, string>(),
} = defineProps<{
  rows: GridRow[]
  days: string[]
  /** Active projects: the ones that can be added, and the client for each row's subtitle */
  projects: Project[] | undefined
  /** Header chips per day: approved absences and public holidays */
  marks?: Record<string, DayMark[]>
  readOnly?: boolean
  /** Keyed by cellKey() */
  cellErrors?: Map<string, string>
  dayErrors?: Map<string, string>
}>()

const emit = defineEmits<{
  'update-hours': [projectId: number, day: string, text: string]
  add: [project: Project]
  remove: [projectId: number]
  notes: [projectId: number]
}>()

const id = useId()
const errorId = (key: string) => `${id}-error-${key.replace('|', '-')}`
const dayErrorId = (day: string) => `${id}-day-error-${day}`

const isWeekend = (day: string) => weekday(day) >= 5

const columns = computed(() =>
  days.map((day) => ({
    day,
    // Built here: Vue drops whitespace between elements on separate lines (learnings.md)
    label: `${weekdayName(day)} ${dayOfMonth(day)}`,
    long: formatWeekdayDate(day),
    weekend: isWeekend(day),
    marks: marks[day] ?? [],
  })),
)

const totals = computed(() => dayTotals(rows, days))
const weekTotal = computed(() => totals.value.reduce((a, b) => a + b, 0))

const tableRows = computed(() =>
  rows.map((row) => ({
    row,
    subtitle: projectSubtitle(row.project, projects),
    total: rowTotal(row, days),
    notes: Object.values(row.cells).filter((c) => c.description.trim() !== '').length,
  })),
)

// Everything wrong in the grid, listed under it, each message pointed to by its input
const errorList = computed(() => [
  ...rows.flatMap((row) =>
    days.flatMap((day) => {
      const key = cellKey(row.project.id, day)
      const message = cellErrors.get(key)
      return message
        ? [{ id: errorId(key), text: `${row.project.code}, ${formatWeekdayDate(day)}: ${message}` }]
        : []
    }),
  ),
  ...days.flatMap((day) => {
    const message = dayErrors.get(day)
    return message ? [{ id: dayErrorId(day), text: `${formatWeekdayDate(day)}: ${message}` }] : []
  }),
])

function describedBy(projectId: number, day: string): string | undefined {
  const ids = [
    cellErrors.has(cellKey(projectId, day)) ? errorId(cellKey(projectId, day)) : null,
    dayErrors.has(day) ? dayErrorId(day) : null,
  ].filter((x) => x !== null)
  return ids.length ? ids.join(' ') : undefined
}

function onInput(projectId: number, day: string, event: Event) {
  emit('update-hours', projectId, day, (event.target as HTMLInputElement).value)
}

/** On leaving a cell, "7,5" becomes "7.5" and "08" becomes "8"; invalid text stays for fixing */
function onBlur(projectId: number, day: string, event: Event) {
  const text = (event.target as HTMLInputElement).value
  const parsed = parseHours(text)
  if (parsed.error === undefined) {
    const normalized = parsed.hours === null ? '' : formatHours(parsed.hours)
    if (normalized !== text) {
      emit('update-hours', projectId, day, normalized)
    }
  }
}

// "+ Add project": the ghost button turns into a project select with Add and Cancel
const adding = ref(false)
const toAdd = ref(0)
const addable = computed(() =>
  (projects ?? []).filter((p) => !rows.some((r) => r.project.id === p.id)),
)
const addOptions = computed(() =>
  addable.value.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` })),
)
const table = useTemplateRef<HTMLTableElement>('table')

function startAdding() {
  toAdd.value = addable.value[0]?.id ?? 0
  adding.value = true
}

async function confirmAdd() {
  const project = addable.value.find((p) => p.id === toAdd.value)
  adding.value = false
  if (!project) {
    return
  }
  emit('add', project)
  // Straight to typing: focus the new row's Monday
  await nextTick()
  table.value?.querySelector<HTMLInputElement>(`input[data-project="${project.id}"]`)?.focus()
}
</script>

<template>
  <div class="card">
    <table ref="table" class="grid">
      <thead>
        <tr>
          <th scope="col" colspan="2" class="col-project head-label">Project</th>
          <th
            v-for="col in columns"
            :key="col.day"
            scope="col"
            class="col-day"
            :class="{ 'col-day--weekend': col.weekend }"
          >
            <span class="day">
              <abbr :title="col.long" class="day__label">{{ col.label }}</abbr>
              <span
                v-for="mark in col.marks"
                :key="mark.label"
                class="chip"
                :style="{ color: mark.color, background: mark.background }"
                :title="mark.title"
              >
                <span aria-hidden="true">{{ mark.label }}</span>
                <span class="visually-hidden">, {{ mark.title }}</span>
              </span>
            </span>
          </th>
          <th scope="col" class="col-total head-label">Total</th>
        </tr>
      </thead>

      <tbody>
        <tr v-for="{ row, subtitle, total, notes } in tableRows" :key="row.project.id">
          <th scope="row" class="project">
            <span class="project__code">{{ row.project.code }}</span>
            <span class="project__name"><span class="visually-hidden">, </span>{{ subtitle }}</span>
          </th>
          <!-- Its own cell, so the row header that screen readers repeat is just the project -->
          <td class="row-actions">
            <span class="row-actions__inner">
              <button
                v-if="!readOnly || notes > 0"
                type="button"
                class="icon-button"
                :class="{ 'icon-button--active': notes > 0 }"
                :aria-label="
                  notes > 0
                    ? `Descriptions for ${row.project.code} (${notes})`
                    : `Descriptions for ${row.project.code}`
                "
                @click="emit('notes', row.project.id)"
              >
                <MessageSquareText :size="16" aria-hidden="true" />
                <span v-if="notes > 0" aria-hidden="true">{{ notes }}</span>
              </button>
              <button
                v-if="!readOnly"
                type="button"
                class="icon-button"
                :aria-label="`Remove ${row.project.code}`"
                @click="emit('remove', row.project.id)"
              >
                <Trash2 :size="16" aria-hidden="true" />
              </button>
            </span>
          </td>
          <td
            v-for="col in columns"
            :key="col.day"
            class="cell"
            :class="{ 'cell--weekend': col.weekend }"
          >
            <span v-if="readOnly" class="hours hours--static">
              <template v-if="row.cells[col.day]?.hours">{{ row.cells[col.day]?.hours }}</template>
              <template v-else>
                <span aria-hidden="true" class="empty">–</span>
                <span class="visually-hidden">No hours</span>
              </template>
            </span>
            <input
              v-else
              class="hours"
              :class="{ 'hours--error': cellErrors.has(cellKey(row.project.id, col.day)) }"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              placeholder="–"
              :data-project="row.project.id"
              :value="row.cells[col.day]?.hours ?? ''"
              :aria-label="`${row.project.code}, ${col.long}, hours`"
              :aria-invalid="cellErrors.has(cellKey(row.project.id, col.day)) ? 'true' : undefined"
              :aria-describedby="describedBy(row.project.id, col.day)"
              @input="onInput(row.project.id, col.day, $event)"
              @blur="onBlur(row.project.id, col.day, $event)"
            />
          </td>
          <td class="total">{{ total ? formatHours(total) : '–' }}</td>
        </tr>

        <tr v-if="rows.length === 0">
          <td :colspan="days.length + 3" class="empty-row">
            {{ readOnly ? 'No hours were recorded this week.' : 'No hours yet this week.' }}
          </td>
        </tr>

        <tr v-if="!readOnly" class="add-row">
          <td :colspan="days.length + 3">
            <form v-if="adding" class="add-form" @submit.prevent="confirmAdd">
              <BaseSelect v-model="toAdd" :options="addOptions" label="Project to add" />
              <BaseButton type="submit" size="small">Add</BaseButton>
              <BaseButton variant="ghost" size="small" @click="adding = false">Cancel</BaseButton>
            </form>
            <BaseButton
              v-else
              variant="ghost"
              size="small"
              :disabled="addable.length === 0"
              :title="addable.length === 0 ? 'Every active project is on the grid' : undefined"
              @click="startAdding"
            >
              <Plus :size="16" aria-hidden="true" />
              Add project
            </BaseButton>
          </td>
        </tr>
      </tbody>

      <tfoot>
        <tr>
          <th scope="row" colspan="2" class="totals-label">Daily total</th>
          <td
            v-for="(col, i) in columns"
            :key="col.day"
            class="total"
            :class="{ 'total--error': dayErrors.has(col.day), 'total--empty': !totals[i] }"
          >
            {{ totals[i] ? formatHours(totals[i]!) : '–' }}
          </td>
          <td class="total total--week" :class="{ 'total--empty': !weekTotal }">
            {{ weekTotal ? formatHours(weekTotal) : '–' }}
          </td>
        </tr>
      </tfoot>
    </table>

    <ul v-if="errorList.length" class="errors">
      <li v-for="error in errorList" :id="error.id" :key="error.id">{{ error.text }}</li>
    </ul>
  </div>
</template>

<style scoped>
.card {
  overflow-x: auto;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

/* :where() keeps this at element specificity, so the class rules below override it */
:where(.grid) th,
:where(.grid) td {
  padding: 0;
  font-weight: inherit;
  text-align: center;
}

thead {
  background: var(--color-surface-alt);
}

thead th {
  vertical-align: bottom;
}

.head-label {
  padding: 14px var(--space-4);
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.col-project {
  min-width: 240px;
  text-align: left;
}

.col-day,
.col-total {
  width: 104px;
}

.col-day {
  padding: var(--space-3) var(--space-2);
}

.col-day--weekend {
  background: var(--color-grey);
}

.day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
}

.day__label {
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

.col-day--weekend .day__label {
  color: var(--color-muted);
}

.chip {
  max-width: 88px;
  overflow: hidden;
  padding: var(--space-1) 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

tbody tr,
tfoot tr {
  border-top: 1px solid var(--color-line);
}

.project {
  padding: var(--space-3) var(--space-4);
  text-align: left;
}

.project__code {
  display: block;
  color: var(--color-ink);
  font-weight: 600;
}

.project__name {
  display: block;
  margin-top: 2px;
  color: var(--color-muted);
  font-size: 12px;
}

.row-actions {
  width: 1%;
  padding-right: var(--space-2);
}

.row-actions__inner {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-1);
}

.icon-button {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 6px;
  border: none;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--color-muted);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.icon-button:hover {
  background: var(--color-surface-alt);
  color: var(--color-ink);
}

.icon-button--active {
  color: var(--color-primary);
}

.icon-button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.cell {
  padding: 10px var(--space-2);
}

.cell--weekend {
  background: var(--color-surface-alt);
}

.hours {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 40px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
  background: var(--color-surface);
  color: var(--color-ink);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
}

.hours::placeholder {
  color: var(--color-muted);
  font-weight: 400;
  opacity: 1;
}

/* The frame fades the empty weekend cells; they still take hours */
.cell--weekend .hours:placeholder-shown:not(:focus),
.cell--weekend .hours--static:has(.empty) {
  opacity: 0.5;
}

/* Same 2px trick as BaseInput: border plus a 1px shadow, so nothing shifts */
.hours:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.hours--error,
.hours--error:focus {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 1px var(--color-danger);
}

.hours--static {
  border-color: transparent;
  background: none;
}

.empty {
  color: var(--color-muted);
  font-weight: 400;
}

.total {
  padding: 14px var(--space-4);
  font-weight: 700;
}

.total--empty {
  color: var(--color-muted);
}

.total--error {
  color: var(--color-danger);
}

.total--week:not(.total--empty) {
  color: var(--color-primary);
  font-size: 16px;
}

.empty-row {
  padding: var(--space-4);
  color: var(--color-muted);
  text-align: left;
}

.add-row td {
  padding: var(--space-2);
  text-align: left;
}

.add-form {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-left: var(--space-2);
}

tfoot {
  background: var(--color-surface-alt);
}

.totals-label {
  padding: 14px var(--space-4);
  font-weight: 600;
  text-align: left;
}

.errors {
  margin: 0;
  padding: var(--space-3) var(--space-4) var(--space-3) var(--space-8);
  border-top: 1px solid var(--color-line);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: 13px;
  font-weight: 500;
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
