<script setup lang="ts">
import { computed, ref } from 'vue'

import BaseButton from '@/components/BaseButton.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BalanceCard from '@/components/absences/BalanceCard.vue'
import UpcomingAbsences from '@/components/absences/UpcomingAbsences.vue'
import AbsenceCalendar from '@/components/absences/AbsenceCalendar.vue'
import RequestAbsenceDialog from '@/components/absences/RequestAbsenceDialog.vue'
import AbsenceDetailsDialog from '@/components/absences/AbsenceDetailsDialog.vue'
import type { AbsenceRequest } from '@/api/client'
import { Plus } from 'lucide-vue-next'
import { useAbsenceBalance, useAbsenceTypes, useMyAbsenceRequests } from '@/absences/queries'
import { byTypeOrder, typeName } from '@/absences/types'
import { addDays, today, yearOf } from '@/format/dates'

// The Absences page, Figma frame "02 Absences": balance cards (FE-2.1), the calendar (FE-2.2) and
// the "Request absence" dialog (FE-3.1) and the details of an absence, to cancel it (FE-3.2)
const now = today()
const year = ref(yearOf(now))
const years = [year.value - 1, year.value, year.value + 1].map((y) => ({
  value: y,
  label: String(y),
}))

const { data: types } = useAbsenceTypes()
const balance = useAbsenceBalance(year)
// The next year of requests feeds "Coming up" (the contract allows up to 366 days per call)
const upcoming = useMyAbsenceRequests(now, addDays(now, 365))

const requesting = ref(false)
/** The absence whose details are open, from a calendar chip or "Coming up" */
const selected = ref<AbsenceRequest | null>(null)

const balances = computed(() => [...(balance.data.value ?? [])].sort(byTypeOrder))
</script>

<template>
  <div class="absences">
    <header class="page-header">
      <div>
        <h1 class="page-header__title">Absences</h1>
        <p class="page-header__subtitle">Your balance and booked time off</p>
      </div>
      <div class="page-header__actions">
        <BaseSelect v-model="year" :options="years" label="Year" />
        <BaseButton @click="requesting = true">
          <Plus :size="18" aria-hidden="true" />
          Request absence
        </BaseButton>
      </div>
    </header>

    <section class="cards" aria-label="Balance">
      <template v-if="balance.isPending.value">
        <p class="card-state" role="status">Loading your balance…</p>
      </template>
      <div v-else-if="balance.isError.value" class="card-state card-state--error" role="alert">
        <p>Your balance couldn't be loaded.</p>
        <BaseButton variant="secondary" size="small" @click="balance.refetch()"
          >Try again</BaseButton
        >
      </div>
      <p v-else-if="balances.length === 0" class="card-state">No balance for {{ year }} yet.</p>
      <BalanceCard
        v-for="b in balances"
        v-else
        :key="b.type"
        :balance="b"
        :name="typeName(b.type, types)"
      />

      <UpcomingAbsences
        v-if="upcoming.data.value"
        :requests="upcoming.data.value"
        :types="types"
        @select="selected = $event"
      />
    </section>

    <AbsenceCalendar :types="types" @select="selected = $event" />

    <!-- Mounted only while open, so each request starts with a fresh form -->
    <RequestAbsenceDialog v-if="requesting" @close="requesting = false" />
    <AbsenceDetailsDialog
      v-if="selected"
      :key="selected.id"
      :request="selected"
      :types="types"
      @close="selected = null"
    />
  </div>
</template>

<style scoped>
.absences {
  display: flex;
  flex-direction: column;
  gap: var(--space-7);
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
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

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--space-5);
  align-items: start;
}

.card-state {
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

.card-state p {
  margin: 0;
}

.card-state--error {
  color: var(--color-ink);
}
</style>
