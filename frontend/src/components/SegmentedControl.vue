<script setup lang="ts" generic="T extends string">
import { useId } from 'vue'

// The segmented control from the Figma foundations (e.g. full day / morning / afternoon).
// Native radio buttons underneath: one tab stop, arrow keys move the choice, and screen readers
// announce "radio, 1 of 3". The fieldset's legend is the visible label.
const model = defineModel<T>({ required: true })
defineProps<{
  label: string
  options: { value: T; label: string; disabled?: boolean }[]
  error?: string
  /** Keep the legend for screen readers only, when the context names the choice (a toolbar) */
  hideLabel?: boolean
}>()

const name = useId()
</script>

<template>
  <fieldset class="segmented" :aria-invalid="error ? 'true' : undefined">
    <legend class="segmented__label" :class="{ 'visually-hidden': hideLabel }">{{ label }}</legend>
    <div class="segmented__track">
      <label
        v-for="option in options"
        :key="option.value"
        class="segmented__option"
        :class="{
          'segmented__option--on': model === option.value,
          'segmented__option--disabled': option.disabled,
        }"
      >
        <input
          v-model="model"
          type="radio"
          :name="name"
          :value="option.value"
          :disabled="option.disabled"
          class="segmented__radio"
        />
        {{ option.label }}
      </label>
    </div>
    <p v-if="error" class="segmented__error">{{ error }}</p>
  </fieldset>
</template>

<style scoped>
.segmented {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  border: none;
}

.segmented__label {
  margin-bottom: 6px;
  padding: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-muted);
}

.segmented__track {
  display: flex;
  gap: var(--space-1);
  padding: var(--space-1);
  border-radius: var(--radius-pill);
  background: var(--color-grey);
}

.segmented__option {
  position: relative;
  flex: 1;
  padding: var(--space-2) 14px;
  border-radius: var(--radius-pill);
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 500;
  text-align: center;
  white-space: nowrap;
  cursor: pointer;
}

.segmented__option--on {
  background: var(--color-surface);
  color: var(--color-ink);
  font-weight: 600;
}

.segmented__option--disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* Hidden but still focusable and announced */
.segmented__radio {
  position: absolute;
  inset: 0;
  margin: 0;
  opacity: 0;
  cursor: inherit;
}

.segmented__option:has(.segmented__radio:focus-visible) {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.segmented__error {
  margin: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-danger);
}
</style>
