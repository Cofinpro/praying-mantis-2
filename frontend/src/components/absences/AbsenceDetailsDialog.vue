<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { Info, Trash2 } from 'lucide-vue-next'

import type { AbsenceRequest, AbsenceStatus, AbsenceType, DayPart } from '@/api/client'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import CancelAbsenceDialog from '@/components/absences/CancelAbsenceDialog.vue'
import { canCancel } from '@/absences/cancel'
import { typeColor, typeName } from '@/absences/types'
import {
  formatDate,
  formatInstantDate,
  formatNumber,
  formatRangeWithYear,
  today,
  weekdayName,
} from '@/format/dates'

// FE-3.2, Figma frame "08 Absences – Details". Opened from a calendar chip or "Coming up".
const { request, types } = defineProps<{
  request: AbsenceRequest
  types: AbsenceType[] | undefined
}>()
const emit = defineEmits<{ close: [] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const confirming = ref(false)

const BADGES: Record<
  AbsenceStatus,
  { status: 'pending' | 'approved' | 'rejected' | 'draft'; label?: string }
> = {
  PENDING: { status: 'pending' },
  APPROVED: { status: 'approved' },
  REJECTED: { status: 'rejected' },
  CANCELLED: { status: 'draft', label: 'Cancelled' },
}
const badge = computed(() => BADGES[request.status])

const PARTS: Record<DayPart, string> = {
  FULL: 'Full day',
  MORNING: 'Morning',
  AFTERNOON: 'Afternoon',
}

const range = computed(() => formatRangeWithYear(request.startDate, request.endDate))
const subtitle = computed(() => {
  const r = request
  const days =
    r.startDate === r.endDate
      ? weekdayName(r.startDate)
      : `${weekdayName(r.startDate)} – ${weekdayName(r.endDate)}`
  const unit = r.workingDays > 1 ? 'working days' : 'working day'
  return `${days} · ${formatNumber(r.workingDays)} ${unit}`
})

// Rows without a value are left out; the backend sends absent optional fields as null
const rows = computed(() =>
  [
    { label: 'Start', value: `${formatDate(request.startDate)} · ${PARTS[request.startPart]}` },
    { label: 'End', value: `${formatDate(request.endDate)} · ${PARTS[request.endPart]}` },
    { label: 'Approver', value: request.approver?.name },
    { label: 'Requested', value: formatInstantDate(request.createdAt) },
    { label: 'Reason', value: request.reason },
    { label: 'Comment', value: request.decisionComment },
  ].filter((row): row is { label: string; value: string } => !!row.value),
)

const cancellable = computed(() => canCancel(request, today()))
// The rule only matters while the request is still active
const active = computed(() => request.status === 'PENDING' || request.status === 'APPROVED')
</script>

<template>
  <BaseDialog ref="dialog" title="Absence details" width="medium" @close="emit('close')">
    <div class="details">
      <div class="details__type">
        <span class="details__dot" :style="{ background: typeColor(request.type).solid }" />
        {{ typeName(request.type, types) }}
        <StatusBadge :status="badge.status" :label="badge.label" />
      </div>

      <div>
        <p class="details__range">{{ range }}</p>
        <p class="details__subtitle">{{ subtitle }}</p>
      </div>

      <dl class="details__list">
        <div v-for="row in rows" :key="row.label" class="details__row">
          <dt>{{ row.label }}</dt>
          <dd>{{ row.value }}</dd>
        </div>
      </dl>

      <p v-if="active" class="details__note">
        <Info :size="16" aria-hidden="true" class="details__note-icon" />
        You can cancel a pending request at any time, and an approved one until the day it starts.
      </p>

      <footer class="details__footer">
        <BaseButton v-if="cancellable" variant="danger" @click="confirming = true">
          <Trash2 :size="16" aria-hidden="true" />
          Cancel request
        </BaseButton>
        <BaseButton variant="secondary" class="details__close" @click="dialog?.close()">
          Close
        </BaseButton>
      </footer>
    </div>
  </BaseDialog>

  <!-- A sibling, not a child, of the details <dialog>: showModal() puts it on top of it -->
  <CancelAbsenceDialog
    v-if="confirming"
    :request="request"
    :types="types"
    @close="confirming = false"
    @cancelled="dialog?.close()"
  />
</template>

<style scoped>
.details {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.details__type {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 15px;
  font-weight: 600;
}

.details__dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-pill);
}

.details__range {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.details__subtitle {
  margin: var(--space-1) 0 0;
  font-size: 13px;
  color: var(--color-muted);
}

.details__list {
  margin: 0;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
}

.details__row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  font-size: 13px;
}

.details__row + .details__row {
  border-top: 1px solid var(--color-line);
}

.details__row dt {
  color: var(--color-muted);
}

.details__row dd {
  margin: 0;
  font-weight: 600;
  text-align: right;
}

.details__note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-input);
  background: var(--color-bg);
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-muted);
}

.details__note-icon {
  flex: none;
  margin-top: 1px;
}

.details__footer {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.details__close {
  margin-left: auto;
}
</style>
