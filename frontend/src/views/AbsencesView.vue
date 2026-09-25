<script setup lang="ts">
import { computed, ref } from 'vue'

import BaseButton from '@/components/BaseButton.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BalanceCard from '@/components/absences/BalanceCard.vue'
import UpcomingAbsences from '@/components/absences/UpcomingAbsences.vue'
import AbsenceCalendar from '@/components/absences/AbsenceCalendar.vue'
import { useAbsenceBalance, useAbsenceTypes, useMyAbsenceRequests } from '@/absences/queries'
import { byTypeOrder, typeName } from '@/absences/types'
import { addDays, today, yearOf } from '@/format/dates'

// The Absences page, Figma frame "02 Absences": balance cards (FE-2.1) and the calendar (FE-2.2).
// The "Request absence" button comes with FE-3.1.
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

const balances = computed(() => [...(balance.data.value ?? [])].sort(byTypeOrder))
</script>

<template>
  <div class="absences">
    <header class="page-header">
      <div>
        <h1 class="page-header__title">Absences</h1>
        <p class="page-header__subtitle">Your balance and booked time off</p>
      </div>
      <BaseSelect v-model="year" :options="years" label="Year" />
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

      <UpcomingAbsences v-if="upcoming.data.value" :requests="upcoming.data.value" :types="types" />
    </section>

    <AbsenceCalendar :types="types" />
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
