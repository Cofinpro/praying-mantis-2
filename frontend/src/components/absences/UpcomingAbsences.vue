<script setup lang="ts">
import { computed } from 'vue'

import type { AbsenceRequest, AbsenceType } from '@/api/client'
import StatusBadge from '@/components/StatusBadge.vue'
import { typeName } from '@/absences/types'
import { formatDays, formatRange } from '@/format/dates'

// FE-2.1: the "Coming up" card from the Figma frame "02 Absences"
const {
  requests,
  types,
  limit = 3,
} = defineProps<{
  /** Requests from today on, as returned by the API */
  requests: AbsenceRequest[]
  types: AbsenceType[] | undefined
  limit?: number
}>()

// Rejected and cancelled ones aren't coming up
const upcoming = computed(() =>
  requests.filter((r) => r.status === 'APPROVED' || r.status === 'PENDING').slice(0, limit),
)

const partLabel = (r: AbsenceRequest) =>
  r.startDate === r.endDate && r.startPart !== 'FULL' ? ` (${r.startPart.toLowerCase()})` : ''
</script>

<template>
  <section class="upcoming" aria-labelledby="upcoming-title">
    <h2 id="upcoming-title" class="upcoming__title">Coming up</h2>
    <ul v-if="upcoming.length" class="upcoming__list">
      <li v-for="r in upcoming" :key="r.id" class="upcoming__item">
        <div>
          <p class="upcoming__dates">{{ formatRange(r.startDate, r.endDate) }}{{ partLabel(r) }}</p>
          <p class="upcoming__type">
            {{ typeName(r.type, types) }} · {{ formatDays(r.workingDays) }}
          </p>
        </div>
        <StatusBadge :status="r.status === 'PENDING' ? 'pending' : 'approved'" />
      </li>
    </ul>
    <p v-else class="upcoming__empty">Nothing booked yet.</p>
  </section>
</template>

<style scoped>
.upcoming {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.upcoming__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.upcoming__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.upcoming__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.upcoming__dates {
  margin: 0;
  font-weight: 600;
}

.upcoming__type,
.upcoming__empty {
  margin: 0;
  font-size: 12px;
  color: var(--color-muted);
}
</style>
