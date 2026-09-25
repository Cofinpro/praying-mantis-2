<script setup lang="ts">
import { computed, ref, useId, useTemplateRef } from 'vue'

import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import BaseInput from '@/components/BaseInput.vue'
import { formatWeekdayDate } from '@/format/dates'
import { formatHours, parseHours, type GridRow } from '@/timesheets/grid'

// FE-6.1: a description per entry (the contract's TimeEntry.description). Not in the Figma frame,
// see design.md "Not in the frames yet". One field per day that has hours in this row, since an
// entry only exists with hours. "Apply" puts them in the grid; the week's Save sends them.
const {
  row,
  days,
  readOnly = false,
} = defineProps<{
  row: GridRow
  days: string[]
  readOnly?: boolean
}>()
const emit = defineEmits<{ close: []; apply: [descriptions: Record<string, string>] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const formId = useId()

const entries = computed(() =>
  days.flatMap((day) => {
    const cell = row.cells[day]
    const parsed = parseHours(cell?.hours ?? '')
    if (parsed.error !== undefined || parsed.hours === null) {
      return []
    }
    const label = `${formatWeekdayDate(day)} · ${formatHours(parsed.hours)} h`
    return [{ day, label, saved: cell?.description ?? '' }]
  }),
)

// A copy, so Cancel leaves the grid as it was
const drafts = ref<Record<string, string>>(
  Object.fromEntries(entries.value.map((e) => [e.day, e.saved])),
)

function apply() {
  emit('apply', { ...drafts.value })
  dialog.value?.close()
}
</script>

<template>
  <BaseDialog
    ref="dialog"
    width="medium"
    :title="`Descriptions · ${row.project.code}`"
    @close="emit('close')"
  >
    <p v-if="entries.length === 0" class="hint">
      Enter hours for {{ row.project.code }} first: a description belongs to a day’s hours.
    </p>

    <template v-else-if="readOnly">
      <dl class="list">
        <template v-for="entry in entries" :key="entry.day">
          <dt>{{ entry.label }}</dt>
          <dd>{{ entry.saved || '–' }}</dd>
        </template>
      </dl>
    </template>

    <form v-else :id="formId" class="form" @submit.prevent="apply">
      <p class="hint">What did you work on? Descriptions are saved with the week.</p>
      <BaseInput
        v-for="entry in entries"
        :key="entry.day"
        v-model="drafts[entry.day]!"
        :label="entry.label"
        maxlength="500"
      />
    </form>

    <footer class="footer">
      <template v-if="readOnly || entries.length === 0">
        <BaseButton variant="secondary" @click="dialog?.close()">Close</BaseButton>
      </template>
      <template v-else>
        <BaseButton variant="secondary" @click="dialog?.close()">Cancel</BaseButton>
        <BaseButton type="submit" :form="formId">Apply</BaseButton>
      </template>
    </footer>
  </BaseDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.5;
}

.list {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-2) var(--space-4);
  margin: 0;
  font-size: 14px;
}

.list dt {
  color: var(--color-muted);
  font-weight: 500;
}

.list dd {
  margin: 0;
}

.footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-6);
}
</style>
