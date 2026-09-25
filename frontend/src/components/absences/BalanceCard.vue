<script setup lang="ts">
import { computed } from 'vue'

import type { AbsenceBalance } from '@/api/client'
import StatusBadge from '@/components/StatusBadge.vue'
import { typeColor } from '@/absences/types'
import { formatDays, formatNumber } from '@/format/dates'

// FE-2.1: one balance card from the Figma frame "02 Absences"
const { balance, name } = defineProps<{ balance: AbsenceBalance; name: string }>()

const color = computed(() => typeColor(balance.type).solid)
const available = computed(() => balance.entitledDays + balance.carriedOverDays)
const usedPercent = computed(() =>
  available.value > 0 ? Math.min(100, (balance.usedDays / available.value) * 100) : 0,
)

// Types that don't deduct from the balance have no remainingDays (contract): show used days
const big = computed(() => {
  const days = balance.remainingDays ?? balance.usedDays
  const unit = days === 1 ? 'day' : 'days'
  return {
    number: formatNumber(days),
    unit: balance.remainingDays === undefined ? `${unit} used` : `${unit} left`,
  }
})

const stats = computed(() =>
  [
    `${formatNumber(balance.entitledDays)} entitled`,
    balance.carriedOverDays > 0 ? `${formatNumber(balance.carriedOverDays)} carried over` : null,
    `${formatNumber(balance.usedDays)} used`,
  ]
    .filter(Boolean)
    .join(' · '),
)
</script>

<template>
  <article class="balance" :aria-label="`${name} balance`">
    <header class="balance__top">
      <h2 class="balance__name">
        <span class="balance__dot" :style="{ background: color }" aria-hidden="true" />
        {{ name }}
      </h2>
      <StatusBadge
        v-if="balance.pendingDays > 0"
        status="pending"
        :label="`${formatNumber(balance.pendingDays)} pending`"
      />
    </header>

    <!-- The space between number and unit is part of the text, for screen readers too -->
    <p class="balance__big">
      <span class="balance__number">{{ big.number }}</span> {{ big.unit }}
    </p>

    <div
      class="balance__bar"
      role="progressbar"
      :aria-valuenow="balance.usedDays"
      aria-valuemin="0"
      :aria-valuemax="available"
      :aria-label="`${formatDays(balance.usedDays)} of ${formatDays(available)} used`"
    >
      <span class="balance__used" :style="{ width: `${usedPercent}%`, background: color }" />
    </div>

    <p class="balance__stats">{{ stats }}</p>
  </article>
</template>

<style scoped>
.balance {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: var(--space-6);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.balance__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.balance__name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.balance__dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-pill);
}

.balance__big {
  margin: 0;
  font-size: 15px;
  color: var(--color-muted);
}

.balance__number {
  margin-right: 2px;
  color: var(--color-ink);
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.02em;
}

.balance__bar {
  height: 8px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--color-grey);
}

.balance__used {
  display: block;
  height: 100%;
  border-radius: var(--radius-pill);
}

.balance__stats {
  margin: 0;
  font-size: 13px;
  color: var(--color-muted);
}
</style>
