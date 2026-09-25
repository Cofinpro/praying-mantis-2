<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import {
  onBeforeRouteLeave,
  onBeforeRouteUpdate,
  useRoute,
  useRouter,
  type LocationQuery,
} from 'vue-router'
import { ChevronLeft, ChevronRight, Info, TriangleAlert } from 'lucide-vue-next'

import type { Project, Timesheet, TimeEntryInput, TimesheetStatus } from '@/api/client'
import { fieldErrors, problemMessage } from '@/api/problems'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EntryDescriptionsDialog from '@/components/timesheets/EntryDescriptionsDialog.vue'
import SubmitTimesheetDialog from '@/components/timesheets/SubmitTimesheetDialog.vue'
import TimesheetGrid from '@/components/timesheets/TimesheetGrid.vue'
import { useAbsenceTypes } from '@/absences/queries'
import {
  addDays,
  formatInstantDate,
  formatRangeWithYear,
  isIsoDate,
  isoWeek,
  today,
  weekStartOf,
} from '@/format/dates'
import {
  cellKey,
  emptyRow,
  entriesKey,
  formatHours,
  toEntries,
  toRows,
  validate,
  weekDays,
  type GridRow,
} from '@/timesheets/grid'
import { dayMarks } from '@/timesheets/marks'
import { useActiveProjects, useMyTimesheet, useSaveTimesheet } from '@/timesheets/queries'

// FE-6.1, Figma frame "05 Timesheets": my hours for one week, per project and day.
// The week comes from `?week=YYYY-MM-DD` (notifications link there, T-4.1), else today's week.
const route = useRoute()
const router = useRouter()

const weekFromQuery = (query: LocationQuery) =>
  weekStartOf(isIsoDate(query.week) ? query.week : today())

const weekStart = computed(() => weekFromQuery(route.query))
const days = computed(() => weekDays(weekStart.value))
const title = computed(
  () =>
    `Week ${isoWeek(weekStart.value)} · ${formatRangeWithYear(weekStart.value, addDays(weekStart.value, 6))}`,
)

const timesheet = useMyTimesheet(weekStart)
const { data: projects } = useActiveProjects()
const { data: types } = useAbsenceTypes()
const save = useSaveTimesheet()

// --- The editable copy of the week ---------------------------------------------------------------

const rows = ref<GridRow[]>([])
/** The week `rows` belong to, so an old week's rows never show under a new week's title */
const rowsWeek = ref<string | null>(null)

const savedKey = (sheet: Timesheet | undefined) =>
  sheet ? entriesKey(toEntries(toRows(sheet))) : ''
const rowsKey = () => entriesKey(toEntries(rows.value))
const errors = computed(() => validate(rows.value, days.value))
const hasErrors = computed(() => errors.value.cells.size > 0 || errors.value.days.size > 0)

// Take the server's week whenever there's nothing of the user's to lose: another week, or the
// same week while the grid still matches what was loaded (or already matches the new data, as
// after a save). So a background refetch never wipes unsaved typing.
watch(
  () => timesheet.data.value,
  (next, previous) => {
    if (!next) {
      return
    }
    const sameWeek = rowsWeek.value === next.weekStart
    const untouched = !hasErrors.value && rowsKey() === savedKey(previous)
    const alreadyThere = !hasErrors.value && rowsKey() === savedKey(next)
    // E.g. after a 409: the week was submitted elsewhere, and edits can't be kept anyway
    const readOnly = next.status === 'SUBMITTED' || next.status === 'APPROVED'
    if (!sameWeek || untouched || alreadyThere || readOnly) {
      rows.value = toRows(next)
      rowsWeek.value = next.weekStart
    }
  },
  { immediate: true },
)

const loaded = computed(
  () => timesheet.data.value !== undefined && rowsWeek.value === weekStart.value,
)
const status = computed<TimesheetStatus>(() => timesheet.data.value?.status ?? 'DRAFT')
const badge = computed(() => status.value.toLowerCase() as Lowercase<TimesheetStatus>)
// Decision 32: only DRAFT and REJECTED weeks can change
const editable = computed(() => status.value === 'DRAFT' || status.value === 'REJECTED')

const dirty = computed(
  () => loaded.value && (hasErrors.value || rowsKey() !== savedKey(timesheet.data.value)),
)

function findRow(projectId: number) {
  return rows.value.find((r) => r.project.id === projectId)
}

function edited() {
  // A server error is about what was sent; once the grid changes it may no longer apply
  if (save.isError.value) {
    save.reset()
  }
}

function onUpdateHours(projectId: number, day: string, text: string) {
  const cell = findRow(projectId)?.cells[day]
  if (cell) {
    cell.hours = text
    edited()
  }
}

function onAdd(project: Project) {
  rows.value.push(emptyRow(project, days.value))
}

function onRemove(projectId: number) {
  rows.value = rows.value.filter((r) => r.project.id !== projectId)
  edited()
}

// --- Descriptions ---------------------------------------------------------------------------------

const describing = ref<number | null>(null)
const describingRow = computed(() =>
  describing.value === null ? undefined : findRow(describing.value),
)

function onApplyDescriptions(descriptions: Record<string, string>) {
  const row = describingRow.value
  if (!row) {
    return
  }
  for (const [day, text] of Object.entries(descriptions)) {
    const cell = row.cells[day]
    if (cell) {
      cell.description = text
    }
  }
  edited()
}

// --- Header chips: approved absences and public holidays (from the GET, decision 32) --------------

const marks = computed(() => dayMarks(timesheet.data.value, days.value, types.value))

// --- Saving ---------------------------------------------------------------------------------------

/** The body of the last PUT, to point a 400's `entries[2].hours` back at its cell */
const sent = ref<TimeEntryInput[]>([])

function onSave() {
  sent.value = toEntries(rows.value)
  save.mutate({ weekStart: weekStart.value, entries: sent.value })
}

const serverErrors = computed(() => {
  const cells = new Map<string, string>()
  let general: string | null = null
  for (const [field, message] of Object.entries(fieldErrors(save.error.value))) {
    const match = /^entries\[(\d+)]/.exec(field)
    const entry = match ? sent.value[Number(match[1])] : undefined
    if (entry) {
      cells.set(cellKey(entry.projectId, entry.workDate), message)
    } else {
      general = `The week couldn’t be saved: ${message}.`
    }
  }
  return { cells, general }
})

const cellErrors = computed(() => new Map([...serverErrors.value.cells, ...errors.value.cells]))
const saveError = computed(
  () =>
    problemMessage(save.error.value) ??
    serverErrors.value.general ??
    (serverErrors.value.cells.size
      ? 'Some hours weren’t accepted. See the list under the grid.'
      : null),
)

// --- Submitting (FE-6.2) ------------------------------------------------------------------------

const submitting = ref(false)
const saveFirstId = useId()
const savedTotal = computed(() => {
  const total = timesheet.data.value?.totalHours ?? 0
  return total ? formatHours(total) : '–'
})

// --- Week navigation and the unsaved-changes guard ----------------------------------------------

function goToWeek(week: string) {
  const isThisWeek = week === weekStartOf(today())
  // The current week keeps a clean /timesheets URL
  void router.push({ query: { ...route.query, week: isThisWeek ? undefined : week } })
}

/** Resolves the open "Discard changes?" dialog: true leaves, false stays */
const pendingLeave = ref<((leave: boolean) => void) | null>(null)
const discardTitleId = useId()

function confirmDiscard(): boolean | Promise<boolean> {
  if (!dirty.value) {
    return true
  }
  return new Promise((resolve) => {
    pendingLeave.value = resolve
  })
}

function answerDiscard(leave: boolean) {
  pendingLeave.value?.(leave)
  pendingLeave.value = null
}

onBeforeRouteLeave(() => confirmDiscard())
// Changing ?week= reuses this component, so only an update hook sees it
onBeforeRouteUpdate((to) => (weekFromQuery(to.query) === weekStart.value ? true : confirmDiscard()))

// Closing the tab or reloading: the browser shows its own "Leave site?" prompt
function onBeforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault()
  }
}
onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

// --- Status lines ---------------------------------------------------------------------------------

const decidedOn = computed(() => {
  const at = timesheet.data.value?.decidedAt
  return at ? ` on ${formatInstantDate(at)}` : ''
})
const approverName = computed(() => timesheet.data.value?.approver?.name)

const statusNote = computed(() => {
  const sheet = timesheet.data.value
  if (!sheet) {
    return null
  }
  if (sheet.status === 'SUBMITTED') {
    const on = sheet.submittedAt ? ` on ${formatInstantDate(sheet.submittedAt)}` : ''
    const who = approverName.value ? ` ${approverName.value} decides on it next.` : ''
    return `Submitted${on}.${who} The week is read-only.`
  }
  if (sheet.status === 'APPROVED') {
    const by = approverName.value ? ` by ${approverName.value}` : ''
    return `Approved${by}${decidedOn.value}. The week is read-only.`
  }
  return null
})

const rejection = computed(() => {
  const sheet = timesheet.data.value
  if (sheet?.status !== 'REJECTED') {
    return null
  }
  const by = approverName.value ? ` by ${approverName.value}` : ''
  return {
    heading: `Rejected${by}${decidedOn.value}`,
    comment: sheet.decisionComment,
  }
})
</script>

<template>
  <div class="timesheets">
    <header class="page-header">
      <h1 class="page-header__title">Timesheets</h1>
      <p class="page-header__subtitle">Record your hours per project, then submit the week</p>
    </header>

    <div class="toolbar">
      <div class="toolbar__week">
        <button
          type="button"
          class="nav-button"
          aria-label="Previous week"
          @click="goToWeek(addDays(weekStart, -7))"
        >
          <ChevronLeft :size="20" aria-hidden="true" />
        </button>
        <h2 class="toolbar__title" aria-live="polite">{{ title }}</h2>
        <button
          type="button"
          class="nav-button"
          aria-label="Next week"
          @click="goToWeek(addDays(weekStart, 7))"
        >
          <ChevronRight :size="20" aria-hidden="true" />
        </button>
        <BaseButton variant="secondary" size="small" @click="goToWeek(weekStartOf(today()))">
          Today
        </BaseButton>
        <StatusBadge v-if="loaded" :status="badge" />
      </div>
      <p v-if="dirty" class="toolbar__unsaved" role="status">
        <span class="dot" aria-hidden="true" />
        Unsaved changes
      </p>
      <p v-else-if="save.isSuccess.value" class="toolbar__saved" role="status">Saved</p>
    </div>

    <div v-if="rejection" class="banner banner--danger" role="note">
      <TriangleAlert :size="18" aria-hidden="true" />
      <div>
        <p class="banner__heading">{{ rejection.heading }}</p>
        <p v-if="rejection.comment" class="banner__text">“{{ rejection.comment }}”</p>
        <p class="banner__text">Correct the hours, save, and submit the week again.</p>
      </div>
    </div>

    <div v-if="statusNote && loaded" class="banner banner--info" role="note">
      <Info :size="18" aria-hidden="true" />
      <p class="banner__text">{{ statusNote }}</p>
    </div>

    <div v-if="saveError" class="banner banner--danger" role="alert">
      <TriangleAlert :size="18" aria-hidden="true" />
      <p class="banner__text">{{ saveError }}</p>
    </div>

    <p v-if="timesheet.isError.value" class="state state--error" role="alert">
      The week couldn’t be loaded.
      <BaseButton variant="secondary" size="small" @click="timesheet.refetch()">
        Try again
      </BaseButton>
    </p>
    <p v-else-if="!loaded" class="state" role="status">Loading the week…</p>

    <template v-else>
      <TimesheetGrid
        :rows="rows"
        :days="days"
        :projects="projects"
        :marks="marks"
        :read-only="!editable"
        :cell-errors="cellErrors"
        :day-errors="errors.days"
        @update-hours="onUpdateHours"
        @add="onAdd"
        @remove="onRemove"
        @notes="describing = $event"
      />

      <footer class="footer">
        <p class="footer__note">
          Hours are 0–24 per entry and per day, in quarter hours. Submitted and approved weeks are
          read-only.
        </p>
        <div v-if="editable" class="footer__actions">
          <p v-if="dirty" :id="saveFirstId" class="footer__note">Save before submitting</p>
          <BaseButton
            variant="secondary"
            :disabled="!dirty || hasErrors || save.isPending.value"
            @click="onSave"
          >
            {{ save.isPending.value ? 'Saving…' : 'Save' }}
          </BaseButton>
          <!-- FE-6.2: only what's saved can be submitted, so unsaved changes disable it -->
          <BaseButton
            :disabled="dirty || save.isPending.value"
            :aria-describedby="dirty ? saveFirstId : undefined"
            @click="submitting = true"
          >
            Submit week
          </BaseButton>
        </div>
      </footer>
    </template>

    <SubmitTimesheetDialog
      v-if="submitting && timesheet.data.value"
      :week-start="weekStart"
      :title="title"
      :total-hours="savedTotal"
      @close="submitting = false"
    />

    <EntryDescriptionsDialog
      v-if="describingRow"
      :key="describingRow.project.id"
      :row="describingRow"
      :days="days"
      :read-only="!editable"
      @apply="onApplyDescriptions"
      @close="describing = null"
    />

    <BaseDialog
      v-if="pendingLeave"
      width="narrow"
      :labelledby="discardTitleId"
      @close="answerDiscard(false)"
    >
      <div class="confirm">
        <span class="confirm__icon" aria-hidden="true"><TriangleAlert :size="22" /></span>
        <h2 :id="discardTitleId" class="confirm__title">Discard unsaved changes?</h2>
        <p class="confirm__text">
          Your changes to {{ title }} aren’t saved yet. If you leave now, they’re lost.
        </p>
        <footer class="confirm__footer">
          <BaseButton variant="secondary" @click="answerDiscard(false)">Keep editing</BaseButton>
          <BaseButton variant="danger-fill" @click="answerDiscard(true)"
            >Discard changes</BaseButton
          >
        </footer>
      </div>
    </BaseDialog>
  </div>
</template>

<style scoped>
.timesheets {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.page-header__title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.01em;
}

.page-header__subtitle {
  margin: 6px 0 0;
  font-size: 15px;
  color: var(--color-muted);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.toolbar__week {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.toolbar__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.nav-button {
  display: inline-flex;
  padding: var(--space-1);
  border: none;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--color-ink);
  cursor: pointer;
}

.nav-button:hover {
  background: var(--color-grey);
}

.nav-button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.toolbar__unsaved,
.toolbar__saved {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: 13px;
  font-weight: 500;
}

.toolbar__unsaved {
  color: var(--color-primary);
}

.toolbar__saved {
  color: var(--color-muted);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
}

.banner {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  font-size: 14px;
}

.banner svg {
  flex-shrink: 0;
  margin-top: 1px;
}

.banner--danger {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.banner--info {
  background: var(--color-info-soft);
  color: var(--color-info);
}

.banner__heading {
  margin: 0;
  font-weight: 600;
}

.banner__text {
  margin: 0;
  font-weight: 500;
}

.banner__heading + .banner__text,
.banner__text + .banner__text {
  margin-top: var(--space-1);
}

.state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  margin: 0;
  padding: var(--space-6);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
  color: var(--color-muted);
}

.state--error {
  color: var(--color-ink);
}

.footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.footer__note {
  margin: 0;
  font-size: 13px;
  color: var(--color-muted);
}

.footer__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

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

.confirm__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  align-self: stretch;
}
</style>
