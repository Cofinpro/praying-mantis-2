<script setup lang="ts">
import { onMounted, useId, useTemplateRef } from 'vue'
import { X } from 'lucide-vue-next'

// The dialog from the Figma frames (design.md), on the native <dialog> element: showModal() makes
// the page behind it inert, traps focus inside, closes on Esc, and puts focus back where it was
// when it closes. Mount it with v-if when it should open; it emits `close` however it was closed.
const {
  title,
  labelledby,
  width = 'default',
} = defineProps<{
  /** The header with the close button. Leave it out for a confirmation, which has its own heading. */
  title?: string
  /** Without a title: the id of the heading inside the slot that names the dialog */
  labelledby?: string
  /** 580px (forms), 520px (details) or 460px (confirmations), as in the frames */
  width?: 'default' | 'medium' | 'narrow'
}>()
const emit = defineEmits<{ close: [] }>()

// A generated id, since a confirmation can open on top of another dialog
const titleId = useId()

const dialog = useTemplateRef<HTMLDialogElement>('dialog')

onMounted(() => dialog.value?.showModal())

/** Close from inside, e.g. after a successful submit */
function close() {
  dialog.value?.close()
}

defineExpose({ close })
</script>

<template>
  <dialog
    ref="dialog"
    class="dialog"
    :class="`dialog--${width}`"
    :aria-labelledby="title ? titleId : labelledby"
    @close="emit('close')"
  >
    <header v-if="title" class="dialog__header">
      <h2 :id="titleId" class="dialog__title">{{ title }}</h2>
      <button type="button" class="dialog__close" aria-label="Close" @click="close">
        <X :size="22" aria-hidden="true" />
      </button>
    </header>
    <slot />
  </dialog>
</template>

<style scoped>
.dialog {
  width: min(580px, calc(100vw - 2 * var(--space-4)));
  max-height: calc(100vh - 2 * var(--space-8));
  padding: var(--space-8);
  border: none;
  border-radius: var(--radius-dialog);
  background: var(--color-surface);
  color: var(--color-ink);
  box-shadow: var(--shadow-float);
}

.dialog--medium {
  width: min(520px, calc(100vw - 2 * var(--space-4)));
}

.dialog--narrow {
  width: min(460px, calc(100vw - 2 * var(--space-4)));
}

.dialog::backdrop {
  background: var(--color-overlay);
}

.dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}

.dialog__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.dialog__close {
  display: inline-flex;
  padding: var(--space-1);
  border: none;
  border-radius: var(--radius-pill);
  background: none;
  color: var(--color-muted);
  cursor: pointer;
}

.dialog__close:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
</style>
