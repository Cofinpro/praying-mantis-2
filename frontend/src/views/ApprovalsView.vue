<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, TriangleAlert, X } from 'lucide-vue-next'

import type { TeamAbsenceRequest } from '@/api/client'
import { problemMessage } from '@/api/problems'
import BaseButton from '@/components/BaseButton.vue'
import RejectDialog from '@/components/approvals/RejectDialog.vue'
import TeamCalendar from '@/components/approvals/TeamCalendar.vue'
import TimesheetApprovals from '@/components/approvals/TimesheetApprovals.vue'
import { avatarColor } from '@/approvals/avatar'
import { APPROVAL_MESSAGES } from '@/approvals/messages'
import {
  useDecideAbsenceRequest,
  usePendingTeamRequests,
  useSubmittedTeamTimesheets,
} from '@/approvals/queries'
import { useAbsenceTypes } from '@/absences/queries'
import { typeColor, typeName } from '@/absences/types'
import { formatNumber, formatRange, formatRangeWithYear } from '@/format/dates'
import { initials } from '@/format/labels'

// FE-5.1 and FE-7.1, Figma frame "04 Approvals": what the backend says I decide on (T-5.1,
// T-7.1), in two tabs, plus FE-5.3's team calendar (frame "10 Approvals – Team calendar"). Someone who isn't a team lead gets empty lists from the backend (decision
// #11), so opening the URL directly just shows "Nothing to approve right now."

// --- Tabs -----------------------------------------------------------------------------------------
// The tab lives in the URL (`?tab=timesheets`, `?tab=team-calendar`), so the TIMESHEET_SUBMITTED notification can link
// straight to it, and a reload keeps it. Arrow keys, Home and End move between the tabs (the ARIA
// tabs pattern); only the selected tab is in the Tab order.
type Tab = 'absences' | 'timesheets' | 'team-calendar'
const TABS: Tab[] = ['absences', 'timesheets', 'team-calendar']

const route = useRoute()
const router = useRouter()
const tab = computed<Tab>(() => {
  const value = route.query.tab
  return TABS.find((t) => t === value) ?? 'absences'
})

function select(next: Tab) {
  if (next !== tab.value) {
    // replace, not push: switching tabs shouldn't fill the Back button's history
    void router.replace({ query: { ...route.query, tab: next === 'absences' ? undefined : next } })
  }
}

function onTabKeydown(event: KeyboardEvent) {
  const i = TABS.indexOf(tab.value)
  const next = {
    ArrowRight: TABS[(i + 1) % TABS.length],
    ArrowLeft: TABS[(i - 1 + TABS.length) % TABS.length],
    Home: TABS[0],
    End: TABS[TABS.length - 1],
  }[event.key]
  if (!next) {
    return
  }
  event.preventDefault()
  select(next)
  document.getElementById(`tab-${next}`)?.focus()
}

const timesheets = useSubmittedTeamTimesheets()
const timesheetCount = computed(() => timesheets.data.value?.length ?? 0)

// --- Absences tab ---------------------------------------------------------------------------------

const { data: types } = useAbsenceTypes()
const pending = usePendingTeamRequests()
const approve = useDecideAbsenceRequest()
// Its own instance, so a failed rejection shows in the dialog and not in the approve banner
const reject = useDecideAbsenceRequest()

/** The request whose reject dialog is open */
const rejecting = ref<TeamAbsenceRequest | null>(null)
const rejectTitle = computed(() => {
  if (!rejecting.value) {
    return ''
  }
  const firstName = rejecting.value.requester.name.trim().split(/\s+/)[0]
  return `Reject ${firstName}’s ${typeName(rejecting.value.request.type, types.value).toLowerCase()} request?`
})
const rejectRequest = (item: TeamAbsenceRequest) => (comment: string) =>
  reject.mutateAsync({ item, decision: 'reject', comment })

const rows = computed(() =>
  (pending.data.value ?? []).map((item) => {
    const { request, requester, remainingDays } = item
    const type = typeName(request.type, types.value)
    // "Approve Carla Mendes’s vacation, 16–18 Nov": the buttons need more than "Approve"
    const what = `${requester.name}’s ${type.toLowerCase()}, ${formatRange(request.startDate, request.endDate)}`
    // Days left now → after approving. Only types with a balance have it (VACATION); the backend
    // sends the others as null rather than leaving the field out, hence `== null`
    const hasBalance = remainingDays != null
    const after = hasBalance ? remainingDays - request.workingDays : 0
    return {
      item,
      id: request.id,
      name: requester.name,
      initials: initials(requester.name),
      avatarColor: avatarColor(requester.id),
      type,
      dotColor: typeColor(request.type).solid,
      dates: formatRangeWithYear(request.startDate, request.endDate),
      days: formatNumber(request.workingDays),
      balance: hasBalance ? `${formatNumber(remainingDays)} → ${formatNumber(after)}` : undefined,
      overdrawn: after < 0,
      reason: request.reason ?? '',
      approveLabel: `Approve ${what}`,
      rejectLabel: `Reject ${what}`,
    }
  }),
)

const count = computed(() => pending.data.value?.length ?? 0)

/** Who the last failed approval was for, to say so in the banner */
const failedFor = ref('')
const approveError = computed(() => {
  const message = problemMessage(approve.error.value, APPROVAL_MESSAGES)
  return message ? `Couldn’t approve ${failedFor.value}’s request. ${message}` : null
})

function onApprove(item: TeamAbsenceRequest) {
  failedFor.value = item.requester.name
  approve.mutate({ item, decision: 'approve' })
}
</script>

<template>
  <div class="approvals">
    <header class="page-header">
      <h1 class="page-header__title">Approvals</h1>
      <p class="page-header__subtitle">Requests from your team waiting for your decision</p>
    </header>

    <div class="tabs" role="tablist" aria-label="Approvals" @keydown="onTabKeydown">
      <button
        id="tab-absences"
        type="button"
        role="tab"
        :aria-selected="tab === 'absences'"
        aria-controls="panel-absences"
        :tabindex="tab === 'absences' ? 0 : -1"
        class="tab"
        :class="{ 'tab--active': tab === 'absences' }"
        @click="select('absences')"
      >
        Absences
        <span v-if="pending.isSuccess.value" class="tab__count">{{ count }}</span>
      </button>
      <button
        id="tab-timesheets"
        type="button"
        role="tab"
        :aria-selected="tab === 'timesheets'"
        aria-controls="panel-timesheets"
        :tabindex="tab === 'timesheets' ? 0 : -1"
        class="tab"
        :class="{ 'tab--active': tab === 'timesheets' }"
        @click="select('timesheets')"
      >
        Timesheets
        <span v-if="timesheets.isSuccess.value" class="tab__count">{{ timesheetCount }}</span>
      </button>
      <button
        id="tab-team-calendar"
        type="button"
        role="tab"
        :aria-selected="tab === 'team-calendar'"
        aria-controls="panel-team-calendar"
        :tabindex="tab === 'team-calendar' ? 0 : -1"
        class="tab"
        :class="{ 'tab--active': tab === 'team-calendar' }"
        @click="select('team-calendar')"
      >
        Team calendar
      </button>
    </div>

    <section
      id="panel-absences"
      role="tabpanel"
      aria-labelledby="tab-absences"
      class="panel"
      :hidden="tab !== 'absences'"
    >
      <div v-if="approveError" class="banner" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ approveError }}
      </div>

      <p v-if="pending.isPending.value" class="state" role="status">Loading requests…</p>
      <div v-else-if="pending.isError.value" class="state state--error" role="alert">
        <p>The requests couldn't be loaded.</p>
        <BaseButton variant="secondary" size="small" @click="pending.refetch()"
          >Try again</BaseButton
        >
      </div>
      <p v-else-if="rows.length === 0" class="state">Nothing to approve right now.</p>

      <div v-else class="card">
        <table class="table">
          <thead>
            <tr>
              <th scope="col" class="col-person">Person</th>
              <th scope="col" class="col-type">Type</th>
              <th scope="col" class="col-dates">Dates</th>
              <th scope="col" class="col-days">Days</th>
              <th scope="col" class="col-balance">Balance</th>
              <th scope="col">Reason</th>
              <th scope="col" class="col-actions"><span class="visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
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
              <td>
                <span class="type">
                  <span
                    class="type__dot"
                    :style="{ background: row.dotColor }"
                    aria-hidden="true"
                  />
                  {{ row.type }}
                </span>
              </td>
              <td>{{ row.dates }}</td>
              <td class="days">{{ row.days }}</td>
              <td class="muted" :class="{ overdrawn: row.overdrawn }">
                <template v-if="row.balance">
                  {{ row.balance }}
                  <span v-if="row.overdrawn" class="visually-hidden">(not enough days)</span>
                </template>
                <template v-else>
                  <span aria-hidden="true">–</span>
                  <span class="visually-hidden">No balance for this type</span>
                </template>
              </td>
              <td class="muted reason">{{ row.reason }}</td>
              <td>
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
          </tbody>
        </table>
      </div>

      <p class="footnote">
        The approver is the requester’s team lead. Admins can decide on any request.
      </p>
    </section>

    <section
      id="panel-timesheets"
      role="tabpanel"
      aria-labelledby="tab-timesheets"
      class="panel"
      :hidden="tab !== 'timesheets'"
    >
      <TimesheetApprovals />
    </section>

    <section
      id="panel-team-calendar"
      role="tabpanel"
      aria-labelledby="tab-team-calendar"
      class="panel"
      :hidden="tab !== 'team-calendar'"
    >
      <!-- Mounted only while its tab is open, so the other tabs don't fetch a month nobody sees -->
      <template v-if="tab === 'team-calendar'">
        <TeamCalendar />
        <p class="footnote">Pending requests are dashed. Hover a day to see who is away.</p>
      </template>
    </section>

    <!-- Mounted only while open, so each rejection starts with an empty comment -->
    <RejectDialog
      v-if="rejecting"
      :key="rejecting.request.id"
      :title="rejectTitle"
      confirm-label="Reject request"
      :reject="rejectRequest(rejecting)"
      @close="rejecting = null"
    />
  </div>
</template>

<style scoped src="../approvals/table.css"></style>

<style scoped>
.approvals {
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

.panel[hidden] {
  /* .panel's display: flex would otherwise beat the hidden attribute */
  display: none;
}

.tabs {
  display: flex;
  gap: var(--space-8);
  border-bottom: 1px solid var(--color-line);
}

.tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 0;
  border: none;
  background: none;
  color: var(--color-muted);
  font: inherit;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.tab--active {
  color: var(--color-ink);
  font-weight: 600;
}

.tab--active .tab__count {
  background: var(--color-primary);
  color: var(--color-surface);
}

.tab--active::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  background: var(--color-primary);
}

.tab:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.tab__count {
  padding: var(--space-1) 10px;
  border-radius: var(--radius-pill);
  background: var(--color-grey);
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}

.col-person {
  width: 240px;
}

.col-type {
  width: 130px;
}

.col-dates {
  width: 190px;
}

.col-days {
  width: 80px;
}

.col-balance {
  width: 130px;
}

.col-actions {
  width: 230px;
}

.type {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 500;
}

.type__dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
}

.days {
  font-weight: 600;
}

.overdrawn {
  color: var(--color-danger);
  font-weight: 500;
}

.reason {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
