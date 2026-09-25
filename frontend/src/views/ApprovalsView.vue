<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, TriangleAlert, X } from 'lucide-vue-next'

import type { TeamAbsenceRequest } from '@/api/client'
import { problemMessage } from '@/api/problems'
import BaseButton from '@/components/BaseButton.vue'
import RejectRequestDialog from '@/components/approvals/RejectRequestDialog.vue'
import { APPROVAL_MESSAGES } from '@/approvals/messages'
import { useDecideAbsenceRequest, usePendingTeamRequests } from '@/approvals/queries'
import { useAbsenceTypes } from '@/absences/queries'
import { typeColor, typeName } from '@/absences/types'
import { formatNumber, formatRange, formatRangeWithYear } from '@/format/dates'
import { initials } from '@/format/labels'

// FE-5.1, Figma frame "04 Approvals". Lists the pending requests the backend says I decide on
// (T-5.1). Someone who isn't a team lead gets an empty list from the backend (decision #11), so
// opening the URL directly just shows "Nothing to approve right now."
const { data: types } = useAbsenceTypes()
const pending = usePendingTeamRequests()
const approve = useDecideAbsenceRequest()

/** The request whose reject dialog is open */
const rejecting = ref<TeamAbsenceRequest | null>(null)

// Avatars get one of the frame's colours, fixed per person
const AVATAR_COLORS = [
  'var(--color-primary)',
  'var(--color-training)',
  'var(--color-info)',
  'var(--color-success-ink)',
]

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
      avatarColor: AVATAR_COLORS[requester.id % AVATAR_COLORS.length],
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

    <!-- Timesheets come with FE-7.1: shown as in the frame, but disabled until then -->
    <div class="tabs" role="tablist" aria-label="Approvals">
      <button
        id="tab-absences"
        type="button"
        role="tab"
        aria-selected="true"
        aria-controls="panel-absences"
        class="tab tab--active"
      >
        Absences
        <span v-if="pending.isSuccess.value" class="tab__count">{{ count }}</span>
      </button>
      <button
        type="button"
        role="tab"
        aria-selected="false"
        aria-disabled="true"
        tabindex="-1"
        class="tab"
        title="Coming soon"
      >
        Timesheets
      </button>
    </div>

    <section id="panel-absences" role="tabpanel" aria-labelledby="tab-absences" class="panel">
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

    <!-- Mounted only while open, so each rejection starts with an empty comment -->
    <RejectRequestDialog
      v-if="rejecting"
      :key="rejecting.request.id"
      :item="rejecting"
      :types="types"
      @close="rejecting = null"
    />
  </div>
</template>

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
}

.tab[aria-disabled='true'] {
  cursor: not-allowed;
}

.tab--active {
  color: var(--color-ink);
  font-weight: 600;
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
  background: var(--color-primary);
  color: var(--color-surface);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.card {
  overflow-x: auto;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.table th,
.table td {
  padding: 14px var(--space-4);
  text-align: left;
  white-space: nowrap;
}

.table thead th {
  padding-block: var(--space-3);
  background: var(--color-surface-alt);
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.table tbody tr {
  border-top: 1px solid var(--color-line);
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

.person {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-weight: 600;
}

.table tbody .person-cell {
  padding-block: var(--space-4);
}

.person__avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  color: var(--color-surface);
  font-size: 13px;
  font-weight: 600;
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

.muted {
  color: var(--color-muted);
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

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
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

.state p {
  margin: 0;
}

.state--error {
  color: var(--color-ink);
}

.banner {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-weight: 500;
}

.footnote {
  margin: 0;
  font-size: 13px;
  color: var(--color-muted);
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
