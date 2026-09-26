<script setup lang="ts">
// The on/off switch from the Figma frames (design.md, "11 Admin – Users" and "12 Admin – Edit
// user"): 38×22, primary track when on. A <button role="switch">, so screen readers announce it as
// a switch with its aria-checked state. Space and Enter flip it, like a click.
// `readonly` shows the state without being a control (the Admin column of the users table): a
// switch you can't flip would be announced as one, so it's hidden from screen readers and the
// state is read out as text instead.
const model = defineModel<boolean>({ required: true })
const { readonly = false } = defineProps<{
  /** The accessible name when no visible label names it */
  label?: string
  /** The id of a visible label */
  labelledby?: string
  /** The id of a visible description or error */
  describedby?: string
  disabled?: boolean
  readonly?: boolean
}>()

function toggle() {
  model.value = !model.value
}
</script>

<template>
  <span v-if="readonly" class="toggle-readonly">
    <span class="toggle" :class="{ 'toggle--on': model }" aria-hidden="true">
      <span class="toggle__knob" />
    </span>
    <span class="visually-hidden">{{ model ? 'Yes' : 'No' }}</span>
  </span>
  <button
    v-else
    type="button"
    role="switch"
    class="toggle"
    :class="{ 'toggle--on': model }"
    :aria-checked="model"
    :aria-label="label"
    :aria-labelledby="labelledby"
    :aria-describedby="describedby"
    :disabled="disabled"
    @click="toggle"
    @keydown.enter.prevent="toggle"
    @keydown.space.prevent="toggle"
  >
    <span class="toggle__knob" />
  </button>
</template>

<style scoped>
.toggle-readonly {
  display: inline-flex;
}

.toggle {
  position: relative;
  display: inline-flex;
  flex: none;
  width: 38px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-line);
  cursor: pointer;
  transition: background-color 0.15s;
}

.toggle--on {
  background: var(--color-primary);
}

.toggle__knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  transition: transform 0.15s;
}

.toggle--on .toggle__knob {
  transform: translateX(16px);
}

button.toggle:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

button.toggle:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

span.toggle {
  cursor: default;
}

@media (prefers-reduced-motion: reduce) {
  .toggle,
  .toggle__knob {
    transition: none;
  }
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
