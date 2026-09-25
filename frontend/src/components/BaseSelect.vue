<script setup lang="ts" generic="T extends string | number">
import { ChevronDown } from 'lucide-vue-next'

// The pill select from the Figma frames (e.g. the year selector). A native <select> underneath,
// so keyboard and screen-reader behaviour come for free. Generic, so v-model keeps its type.
const model = defineModel<T>({ required: true })
defineProps<{
  options: { value: T; label: string }[]
  /** Accessible name, since the pill has no visible label */
  label: string
}>()
</script>

<template>
  <span class="select">
    <select v-model="model" :aria-label="label" class="select__native">
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <ChevronDown :size="16" class="select__icon" aria-hidden="true" />
  </span>
</template>

<style scoped>
.select {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.select__native {
  appearance: none;
  padding: 10px 40px 10px 18px;
  border: 1px solid var(--color-ink);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-ink);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.select__native:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.select__icon {
  position: absolute;
  right: 16px;
  pointer-events: none;
}
</style>
