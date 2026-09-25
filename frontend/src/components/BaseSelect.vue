<script setup lang="ts" generic="T extends string | number">
import { useId, type Component } from 'vue'
import { ChevronDown } from 'lucide-vue-next'

// Selects from the Figma frames, a native <select> underneath so keyboard and screen-reader
// behaviour come for free. Generic, so v-model keeps its type (a year stays a number).
// - "pill": the compact year selector; `label` is only its accessible name
// - "field": a form field like BaseInput, with the label shown above it
const model = defineModel<T>({ required: true })
const { variant = 'pill' } = defineProps<{
  options: { value: T; label: string }[]
  label: string
  variant?: 'pill' | 'field'
  error?: string
  /** A Lucide icon instead of the chevron, e.g. a calendar for a month */
  icon?: Component
}>()

const id = useId()
const errorId = `${id}-error`
</script>

<template>
  <div :class="variant === 'field' ? 'field' : 'pill'">
    <label v-if="variant === 'field'" :for="id" class="field__label">{{ label }}</label>
    <span class="select" :class="`select--${variant}`">
      <select
        :id="id"
        v-model="model"
        :aria-label="variant === 'pill' ? label : undefined"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="error ? errorId : undefined"
        class="select__native"
        :class="{ 'select__native--error': error }"
      >
        <option v-for="option in options" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <component :is="icon ?? ChevronDown" :size="16" class="select__icon" aria-hidden="true" />
    </span>
    <p v-if="error" :id="errorId" class="field__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.pill {
  display: inline-flex;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-muted);
}

.field__error {
  margin: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-danger);
}

.select {
  position: relative;
  display: flex;
  align-items: center;
}

.select__native {
  width: 100%;
  appearance: none;
  background: var(--color-surface);
  color: var(--color-ink);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}

.select--pill .select__native {
  padding: 10px 40px 10px 18px;
  border: 1px solid var(--color-ink);
  border-radius: var(--radius-pill);
  font-weight: 600;
}

.select--field .select__native {
  padding: var(--space-3) 40px var(--space-3) 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
}

.select--pill .select__native:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.select--field .select__native:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.select__native--error,
.select__native--error:focus {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 1px var(--color-danger);
}

.select__icon {
  position: absolute;
  right: 16px;
  color: var(--color-muted);
  pointer-events: none;
}

.select--pill .select__icon {
  color: var(--color-ink);
}
</style>
