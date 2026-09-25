<script setup lang="ts">
// The pill badges from the Figma foundations (design.md, "Status badges")
const { status, label } = defineProps<{
  status: 'draft' | 'submitted' | 'pending' | 'approved' | 'rejected'
  /** Defaults to the status name, e.g. "Pending" */
  label?: string
}>()
</script>

<template>
  <span class="badge" :class="`badge--${status}`">
    {{ label ?? status.charAt(0).toUpperCase() + status.slice(1) }}
  </span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  padding: var(--space-1) 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  line-height: 16px;
  white-space: nowrap;
}

.badge--draft {
  background: var(--color-grey);
  color: var(--color-muted);
}

.badge--submitted {
  background: var(--color-info-soft);
  color: var(--color-info);
}

.badge--pending {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.badge--approved {
  background: var(--color-success-soft);
  color: var(--color-success-ink);
}

.badge--rejected {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}
</style>
