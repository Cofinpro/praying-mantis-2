<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, ChevronDown, TriangleAlert, X } from 'lucide-vue-next'

import type { TeamTimesheet } from '@/api/client'
import { problemMessage } from '@/api/problems'
import BaseButton from '@/components/BaseButton.vue'
import RejectDialog from '@/components/approvals/RejectDialog.vue'
import EntryDescriptionsDialog from '@/components/timesheets/EntryDescriptionsDialog.vue'
import TimesheetGrid from '@/components/timesheets/TimesheetGrid.vue'
import { avatarColor } from '@/approvals/avatar'
import { APPROVAL_MESSAGES } from '@/approvals/messages'
import { useDecideTimesheet, useSubmittedTeamTimesheets } from '@/approvals/queries'
import { useAbsenceTypes } from '@/absences/queries'
import { addDays, formatRange, isoWeek } from '@/format/dates'
import { initials } from '@/format/labels'
import { formatHours, toRows, weekDays, type GridRow } from '@/timesheets/grid'
import { dayMarks } from '@/timesheets/marks'
import { useActiveProjects } from '@/timesheets/queries'

// FE-7.1, the Timesheets tab of Figma frame "04 Approvals": the submitted weeks I'm the approver of
// (T-7.1), oldest first. Each row expands to the week grid of frame "05 Timesheets", read-only.
const submitted = useSubmittedTeamTimesheets()
const approve = useDecideTimesheet()
// Its own instance, so a failed rejection shows in the dialog and not in the approve banner
const reject = useDecideTimesheet()
const { data: projects } = useActiveProjects()
const { data: types } = useAbsenceTypes()

/** The ids of the expanded weeks */
const expanded = ref(new Set<number>())

function toggle(id: number) {
  const next = new Set(expanded.value)
  if (!next.delete(id)) {
    next.add(id)
  }
  expanded.value = next
}

const rows = computed(() =>
  (submitted.data.value ?? []).map((item) => {
    const { timesheet, user, projectHours } = item
    const id = timesheet.id!
    const days = weekDays(timesheet.weekStart)
    const range = formatRange(timesheet.weekStart, addDays(timesheet.weekStart, 6))
    const weekNumber = isoWeek(timesheet.weekStart)
    // "Diogo Pereira’s week 38, 14–20 Sep": the row buttons need more than "Approve"
    const what = `${user.name}’s week ${weekNumber}, ${range}`
    return {
      item,
      id,
      name: user.name,
      initials: initials(user.name),
      avatarColor: avatarColor(user.id),
      week: `Week ${weekNumber} · ${range}`,
      total: `${formatHours(timesheet.totalHours)} h`,
      // Built here: Vue drops whitespace between elements on separate lines (learnings.md)
      projects: projectHours.map((p) => `${p.project.code} ${formatHours(p.hours)}`).join(' · '),
      expanded: expanded.value.has(id),
      gridId: `timesheet-grid-${id}`,
      days,
      gridRows: toRows(timesheet),
      marks: dayMarks(timesheet, days, types.value),
      toggleLabel: `Hours of ${what}`,
      approveLabel: `Approve ${what}`,
      rejectLabel: `Reject ${what}`,
    }
  }),
)

/** Who the last failed approval was for, to say so in the banner */
const failedFor = ref('')
const approveError = computed(() => {
  const message = problemMessage(approve.error.value, APPROVAL_MESSAGES)
  return message ? `Couldn’t approve ${failedFor.value}. ${message}` : null
})

function onApprove(item: TeamTimesheet) {
  failedFor.value = `${item.user.name}’s week ${isoWeek(item.timesheet.weekStart)}`
  approve.mutate({ item, decision: 'approve' })
}

/** The week whose reject dialog is open */
const rejecting = ref<TeamTimesheet | null>(null)
const rejectTitle = computed(() => {
  if (!rejecting.value) {
    return ''
  }
  const firstName = rejecting.value.user.name.trim().split(/\s+/)[0]
  return `Reject ${firstName}’s week ${isoWeek(rejecting.value.timesheet.weekStart)}?`
})
const rejectWeek = (item: TeamTimesheet) => (comment: string) =>
  reject.mutateAsync({ item, decision: 'reject', comment })

/** The row whose descriptions are open: the week grid's message button */
const describing = ref<{ row: GridRow; days: string[] } | null>(null)

function onNotes(gridRows: GridRow[], days: string[], projectId: number) {
  const row = gridRows.find((r) => r.project.id === projectId)
  describing.value = row ? { row, days } : null
}
</script>

<template>
  <div class="panel">
    <div v-if="approveError" class="banner" role="alert">
      <TriangleAlert :size="18" aria-hidden="true" />
      {{ approveError }}
    </div>

    <p v-if="submitted.isPending.value" class="state" role="status">Loading timesheets…</p>
    <div v-else-if="submitted.isError.value" class="state state--error" role="alert">
      <p>The timesheets couldn't be loaded.</p>
      <BaseButton variant="secondary" size="small" @click="submitted.refetch()"
        >Try again</BaseButton
      >
    </div>
    <p v-else-if="rows.length === 0" class="state">No timesheets to approve right now.</p>

    <div v-else class="card card--stack">
      <table class="table table--stack">
        <thead>
          <tr>
            <th scope="col" class="col-toggle"><span class="visually-hidden">Details</span></th>
            <th scope="col" class="col-person">Person</th>
            <th scope="col" class="col-week">Week</th>
            <th scope="col" class="col-total">Total</th>
            <th scope="col">Projects</th>
            <th scope="col" class="col-actions"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody v-for="row in rows" :key="row.id">
          <tr>
            <td class="toggle-cell stack-corner">
              <button
                type="button"
                class="toggle"
                :aria-expanded="row.expanded"
                :aria-controls="row.gridId"
                :aria-label="row.toggleLabel"
                @click="toggle(row.id)"
              >
                <ChevronDown :size="18" aria-hidden="true" />
              </button>
            </td>
            <th scope="row" class="person-cell">
              <span class="person">
                <span
                  class="person__avatar"
                  :style="{ background: row.avatarColor }"
                  aria-hidden="true"
                  >{{ row.initials }}</span
                >
                {{ row.name }}
              </span>
            </th>
            <td data-label="Week">{{ row.week }}</td>
            <td class="total" data-label="Total">{{ row.total }}</td>
            <td class="muted projects" data-label="Projects">{{ row.projects }}</td>
            <td class="stack-actions">
              <div class="actions">
                <BaseButton
                  variant="secondary"
                  size="small"
                  :aria-label="row.rejectLabel"
                  @click="rejecting = row.item"
                >
                  <X :size="16" aria-hidden="true" />
                  Reject
                </BaseButton>
                <BaseButton
                  size="small"
                  :aria-label="row.approveLabel"
                  @click="onApprove(row.item)"
                >
                  <Check :size="16" aria-hidden="true" />
                  Approve
                </BaseButton>
              </div>
            </td>
          </tr>
          <tr v-if="row.expanded" class="details">
            <td :id="row.gridId" colspan="6" class="details__cell stack-full">
              <TimesheetGrid
                :rows="row.gridRows"
                :days="row.days"
                :projects="projects"
                :marks="row.marks"
                read-only
                @notes="onNotes(row.gridRows, row.days, $event)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="footnote">
      The approver is the person’s team lead. A rejected week goes back to them to correct.
    </p>

    <!-- Mounted only while open, so each rejection starts with an empty comment -->
    <RejectDialog
      v-if="rejecting"
      :key="rejecting.timesheet.id"
      :title="rejectTitle"
      confirm-label="Reject week"
      :reject="rejectWeek(rejecting)"
      @close="rejecting = null"
    />

    <EntryDescriptionsDialog
      v-if="describing"
      :row="describing.row"
      :days="describing.days"
      read-only
      @close="describing = null"
    />
  </div>
</template>

<style scoped src="../../approvals/table.css"></style>

<style scoped>
.col-toggle {
  width: 56px;
}

.col-person {
  width: 240px;
}

.col-week {
  width: 200px;
}

.col-total {
  width: 100px;
}

.col-actions {
  width: 230px;
}

.table .toggle-cell {
  padding-right: 0;
}

.toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--color-muted);
  cursor: pointer;
}

.toggle:hover {
  background: var(--color-surface-alt);
  color: var(--color-ink);
}

.toggle:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.toggle svg {
  transition: transform 0.15s ease;
}

.toggle[aria-expanded='true'] svg {
  transform: rotate(180deg);
}

.total {
  font-weight: 600;
}

.projects {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table .details__cell {
  padding: 0 var(--space-4) var(--space-4);
  background: var(--color-surface-alt);
  white-space: normal;
}

.table .details {
  border-top: none;
}
</style>
