<script setup lang="ts">
import { useId } from 'vue'

// Labelled text input from the Figma foundations (design.md), with the error state:
// 2px danger border and a danger helper text that screen readers announce with the field.
const model = defineModel<string>({ required: true })

const { type = 'text', error } = defineProps<{
  label: string
  type?: 'text' | 'email' | 'password'
  autocomplete?: string
  required?: boolean
  error?: string
}>()

// Unique per component instance and stable between server and client render (Vue 3.5)
const id = useId()
const errorId = `${id}-error`
</script>

<template>
  <div class="field">
    <label :for="id" class="field__label">{{ label }}</label>
    <input
      :id="id"
      v-model="model"
      :type="type"
      :autocomplete="autocomplete"
      :required="required"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="error ? errorId : undefined"
      class="field__input"
      :class="{ 'field__input--error': error }"
    />
    <p v-if="error" :id="errorId" class="field__error">{{ error }}</p>
  </div>
</template>

<style scoped>
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

.field__input {
  padding: var(--space-3) 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
  background: var(--color-surface);
  color: var(--color-ink);
  font: inherit;
  font-size: 14px;
}

/* Focus and error use a 2px border; the shadow adds the second pixel so the layout doesn't shift */
.field__input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.field__input--error,
.field__input--error:focus {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 1px var(--color-danger);
}

.field__error {
  margin: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-danger);
}
</style>
