<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

import BaseButton from '@/components/BaseButton.vue'
import { useAbsenceTypes, usePublicHolidays } from '@/absences/queries'
import { typeColor, typeName } from '@/absences/types'
import { avatarColor } from '@/approvals/avatar'
import { useTeamAbsences } from '@/approvals/queries'
import { buildTeamMonth, monthRange, type TeamBlock, type TeamDay } from '@/approvals/teamCalendar'
import { addMonths, formatMonth, monthStart, today, yearOf } from '@/format/dates'
import { initials } from '@/format/labels'

// FE-5.3, Figma frame "10 Approvals – Team calendar": who on my team is away each day of a month,
// so I can spot overlaps before approving (T-5.3, decision 36). The backend sends my own row first,
// then the people I lead; conflict days (2+ away) are worked out here from those rows.

const thisMonth = monthStart(today())
const month = ref(thisMonth)
const range = computed(() => monthRange(month.value))

const absences = useTeamAbsences(
  () => range.value.from,
  () => range.value.to,
)
// Shading only: if the holidays can't be loaded, the calendar still works without them
const holidays = usePublicHolidays(() => yearOf(month.value))
const { data: types } = useAbsenceTypes()

const members = computed(() => absences.data.value ?? [])
const grid = computed(() =>
  buildTeamMonth(month.value, members.value, holidays.data.value ?? [], types.value),
)
const rows = computed(() =>
  grid.value.rows.map((row) => ({
    ...row,
    initials: initials(row.name),
    avatarColor: avatarColor(row.userId),
  })),
)
/** Someone who leads nobody gets only their own row (decision 36) */
const leadsNobody = computed(() => absences.isSuccess.value && members.value.length <= 1)

const legend = computed(() =>
  (['VACATION', 'TRAINING', 'SICK'] as const).map((code) => ({
    label: typeName(code, types.value),
    color: typeColor(code).solid,
  })),
)

function blockStyle({ absence, pending }: TeamBlock) {
  const color = typeColor(absence.type)
  // Pending: dashed border in the type's colour on its soft shade, as in the frame's legend
  return pending
    ? { background: color.soft, borderColor: color.solid }
    : { background: color.solid, borderColor: color.solid }
}

// Vue drops the whitespace between elements on separate lines, so the screen-reader texts are
// built here in one piece
function dayLabel(day: TeamDay): string {
  const parts = [day.fullDate]
  if (day.holiday) {
    parts.push(`public holiday: ${day.holiday}`)
  }
  if (day.conflict) {
    parts.push('several people away')
  }
  return parts.join(', ')
}

/** "Away: Ana Silva, Carla Mendes", shown on hover */
const whoIsAway = (day: TeamDay) => (day.away.length ? `Away: ${day.away.join(', ')}` : undefined)

function awayLabel(day: TeamDay): string {
  const n = day.away.length
  return `${n} ${n === 1 ? 'person' : 'people'} away: ${day.away.join(', ')}`
}
</script>

<template>
  <div class="card">
    <header class="toolbar">
      <div class="nav">
        <button
          type="button"
          class="icon-button"
          aria-label="Previous month"
          @click="month = addMonths(month, -1)"
        >
          <ChevronLeft :size="20" aria-hidden="true" />
        </button>
        <h2 class="nav__title" aria-live="polite">{{ formatMonth(month) }}</h2>
        <button
          type="button"
          class="icon-button"
          aria-label="Next month"
          @click="month = addMonths(month, 1)"
        >
          <ChevronRight :size="20" aria-hidden="true" />
        </button>
        <BaseButton variant="secondary" size="small" @click="month = thisMonth">Today</BaseButton>
      </div>

      <ul class="legend" aria-label="Legend">
        <li v-for="item in legend" :key="item.label">
          <span class="legend__dot" :style="{ background: item.color }" />{{ item.label }}
        </li>
        <li><span class="legend__pending" />Pending</li>
        <li><span class="badge badge--soft">2+</span>Several people away</li>
      </ul>
    </header>

    <p v-if="absences.isPending.value" class="message" role="status">Loading the team calendar…</p>
    <div v-else-if="absences.isError.value" class="message message--error" role="alert">
      <p>The team calendar couldn't be loaded.</p>
      <BaseButton variant="secondary" size="small" @click="absences.refetch()"
        >Try again</BaseButton
      >
    </div>

    <template v-else>
      <p v-if="leadsNobody" class="message">
        Nobody has you as their team lead, so the calendar only shows your own absences.
      </p>
      <!-- Scrolls sideways when the month doesn't fit; focusable so the keyboard can scroll it -->
      <div
        class="scroller"
        role="region"
        :aria-label="`Team calendar, ${formatMonth(month)}`"
        tabindex="0"
      >
        <table class="grid">
          <caption class="visually-hidden">
            {{
              `Who on your team is away in ${formatMonth(month)}`
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col" class="member member--head">Team member</th>
              <th
                v-for="day in grid.days"
                :key="day.date"
                scope="col"
                class="day"
                :class="{
                  'day--off': day.weekend || day.holiday,
                  'day--conflict': day.conflict,
                }"
                :abbr="day.fullDate"
                :title="whoIsAway(day)"
                :data-date="day.date"
              >
                <span class="day__letter" aria-hidden="true">{{ day.letter }}</span>
                <span class="day__number" aria-hidden="true">{{ day.day }}</span>
                <span class="visually-hidden">{{ dayLabel(day) }}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.userId">
              <th scope="row" class="member">
                <span class="person">
                  <span
                    class="avatar"
                    :style="{ background: row.avatarColor }"
                    aria-hidden="true"
                    >{{ row.initials }}</span
                  >
                  {{ row.name }}
                </span>
              </th>
              <td
                v-for="cell in row.cells"
                :key="cell.date"
                class="cell"
                :class="{ 'cell--off': cell.off }"
                :data-date="cell.date"
              >
                <span class="cell__blocks">
                  <span
                    v-for="block in cell.blocks"
                    :key="block.absence.id"
                    class="block"
                    :class="[
                      { 'block--pending': block.pending },
                      block.half && `block--${block.half.toLowerCase()}`,
                    ]"
                    :style="blockStyle(block)"
                    :title="block.description"
                  >
                    <span class="visually-hidden">{{ block.description }}</span>
                  </span>
                </span>
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="away">
              <th scope="row" class="member">Away</th>
              <td
                v-for="day in grid.days"
                :key="day.date"
                class="away__count"
                :title="whoIsAway(day)"
                :data-date="day.date"
              >
                <template v-if="day.away.length">
                  <span
                    class="count"
                    :class="{ 'badge badge--filled': day.conflict }"
                    aria-hidden="true"
                    >{{ day.away.length }}</span
                  >
                  <span class="visually-hidden">{{ awayLabel(day) }}</span>
                </template>
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.card {
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 18px var(--space-6);
  border-bottom: 1px solid var(--color-line);
}

.nav {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.nav__title {
  min-width: 150px;
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  text-align: center;
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
.scroller:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 18px;
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

.legend__pending {
  width: 14px;
  height: 10px;
  border: 1px dashed var(--color-primary);
  border-radius: 3px;
  background: var(--color-primary-soft);
}

.badge {
  padding: var(--space-1) 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}

.badge--soft {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.badge--filled {
  background: var(--color-primary);
  color: var(--color-surface);
}

.message {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  margin: 0;
  padding: var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--color-line);
  color: var(--color-muted);
  font-size: 14px;
}

.message p {
  margin: 0;
}

.message--error {
  color: var(--color-ink);
}

.scroller {
  overflow-x: auto;
}

.grid {
  /* 200px for the member column and at least 36px per day, as in the frame */
  width: 100%;
  min-width: calc(200px + 31 * 36px);
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 14px;
}

.grid th,
.grid td {
  padding: 0;
}

/* The member column stays put while the days scroll under it. `.grid` beats the padding reset. */
.grid .member {
  position: sticky;
  left: 0;
  z-index: 1;
  width: 200px;
  padding: 10px var(--space-6);
  background: var(--color-surface);
  text-align: left;
  white-space: nowrap;
}

thead th {
  background: var(--color-surface-alt);
  border-bottom: 1px solid var(--color-line);
}

.grid .member--head {
  padding-block: 10px;
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.grid .day {
  width: 36px;
  padding: var(--space-2) 0;
  text-align: center;
}

.day__letter,
.day__number {
  display: block;
  line-height: normal;
}

.day__letter {
  color: var(--color-muted);
  font-size: 10px;
  font-weight: 500;
}

.day__number {
  margin-top: 2px;
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 600;
}

.day--off {
  background: var(--color-grey);
}

.day--off .day__number {
  color: var(--color-muted);
}

.day--conflict {
  background: var(--color-primary-soft);
}

.day--conflict .day__number {
  color: var(--color-primary);
}

tbody tr {
  border-bottom: 1px solid var(--color-line);
}

.person {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  color: var(--color-surface);
  font-size: 10px;
  font-weight: 600;
}

.cell {
  height: 48px;
}

.cell--off {
  background: var(--color-surface-alt);
}

.cell__blocks {
  display: flex;
  gap: 2px;
  padding-inline: 3px;
}

.block {
  flex: 1;
  height: 24px;
  border: 1px solid transparent;
  border-radius: 4px;
}

.block--pending {
  border-style: dashed;
}

/* Half days take half the cell, on the side of the day they cover */
.block--morning,
.block--afternoon {
  flex: 0 0 calc(50% - 1px);
}

.block--afternoon {
  margin-left: auto;
}

.away th,
.away td {
  background: var(--color-surface-alt);
}

.grid .away th {
  padding-block: var(--space-3);
  font-size: 13px;
  font-weight: 600;
}

.away__count {
  height: 40px;
  text-align: center;
}

.count {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 600;
}

.count.badge {
  padding-inline: 7px;
  color: var(--color-surface);
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
