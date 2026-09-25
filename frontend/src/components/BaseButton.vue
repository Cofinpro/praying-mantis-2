<script setup lang="ts">
// The pill button from the Figma foundations (design.md). `disabled` and listeners fall through
// to the <button>, so <BaseButton :disabled="x" @click="y"> works as expected.
const {
  variant = 'primary',
  size = 'default',
  block = false,
  type = 'button',
} = defineProps<{
  variant?: 'primary' | 'secondary' | 'dark' | 'ghost' | 'danger' | 'danger-fill'
  size?: 'default' | 'small'
  /** Full width of the parent */
  block?: boolean
  type?: 'button' | 'submit' | 'reset'
}>()
</script>

<template>
  <button
    :type="type"
    class="btn"
    :class="[`btn--${variant}`, { 'btn--small': size === 'small', 'btn--block': block }]"
  >
    <slot />
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-6);
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  cursor: pointer;
}

.btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn--small {
  padding: var(--space-2) var(--space-4);
  font-size: 13px;
}

.btn--block {
  width: 100%;
}

.btn--primary {
  background: var(--color-primary);
  color: var(--color-surface);
}

.btn--secondary {
  background: transparent;
  border-color: var(--color-ink);
  color: var(--color-ink);
}

.btn--dark {
  background: var(--color-ink);
  color: var(--color-surface);
}

.btn--ghost {
  background: transparent;
  color: var(--color-ink);
}

.btn--danger {
  background: transparent;
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.btn--danger-fill {
  background: var(--color-danger);
  color: var(--color-surface);
}
</style>
