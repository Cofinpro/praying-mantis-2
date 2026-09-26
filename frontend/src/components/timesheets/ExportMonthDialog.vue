<script setup lang="ts">
import { computed, ref, useId, useTemplateRef, watch } from 'vue'
import { CalendarDays, Download, TriangleAlert } from 'lucide-vue-next'

import { ApiError } from '@/api/client'
import { fieldErrors, problemMessage } from '@/api/problems'
import { useCurrentUser } from '@/auth/session'
import BaseButton from '@/components/BaseButton.vue'
import BaseDialog from '@/components/BaseDialog.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import {
  coverageWarning,
  defaultTemplate,
  monthOptions,
  monthWarning,
  orderTemplates,
} from '@/exports/month'
import { useExportMonth, useExportTemplates, useMyTimesheetMonth } from '@/exports/queries'
import { today } from '@/format/dates'
import { clientLabel } from '@/format/labels'

// FE-8.1, Figma frame "06 Timesheets – Export dialog": pick a month and a template, download the
// month as an .xlsx in that template.
const emit = defineEmits<{ close: [] }>()

const dialog = useTemplateRef<InstanceType<typeof BaseDialog>>('dialog')
const radioName = useId()

const { data: me } = useCurrentUser()
const templatesQuery = useExportTemplates()
const exportMonth = useExportMonth()

// --- Month ----------------------------------------------------------------------------------------

const months = monthOptions(today())
/** `YYYY-MM`, the current month to start with */
const month = ref(today().slice(0, 7))
const summary = useMyTimesheetMonth(month)

// --- Template -------------------------------------------------------------------------------------

const templates = computed(() => orderTemplates(templatesQuery.data.value ?? []))
const selected = ref<string | null>(null)
/** Set once the user picks a card: from then on only a template that's gone gets replaced */
const picked = ref(false)

// Preselect once the templates are there, and again when the month's hours come in (decision
// 33): the sheet of the client the hours are for. Never overwrite the user's own choice.
watch(
  [templates, summary.data],
  ([list, data]) => {
    const gone = !list.some((t) => t.code === selected.value)
    if (!picked.value || gone) {
      selected.value = defaultTemplate(list, me.value?.client, data)?.code ?? null
    }
  },
  { immediate: true },
)

const selectedTemplate = computed(() => templates.value.find((t) => t.code === selected.value))

// --- Warnings -------------------------------------------------------------------------------------

/** What the file will leave out (hours on other clients' projects), then the unapproved weeks */
const warnings = computed(() => {
  if (summary.isError.value) {
    return ['Couldn’t check which weeks of this month are approved.']
  }
  const data = summary.data.value
  if (!data) {
    return []
  }
  return [coverageWarning(data, selectedTemplate.value), monthWarning(data)].filter(
    (w): w is string => w !== null,
  )
})

const isMine = (client: string | undefined | null) => !!client && client === me.value?.client

// --- Download -------------------------------------------------------------------------------------

function onDownload() {
  if (!selected.value) {
    return
  }
  exportMonth.mutate(
    { month: month.value, template: selected.value },
    { onSuccess: () => dialog.value?.close() },
  )
}

// A 400 names its field; both fields are ours, so they get our wording instead of the backend's
const error = computed(() => {
  const err = exportMonth.error.value
  const fields = fieldErrors(err)
  if (fields.template || (err instanceof ApiError && err.status === 404)) {
    return 'This template isn’t available any more. Pick another one.'
  }
  if (fields.month) {
    return 'This month can’t be exported. Pick another one.'
  }
  return problemMessage(err)
})

// An error is about what was sent; once the choice changes it may no longer apply
watch([month, selected], () => {
  if (exportMonth.isError.value) {
    exportMonth.reset()
  }
})
</script>

<template>
  <BaseDialog ref="dialog" title="Export timesheet" @close="emit('close')">
    <div class="export">
      <p class="export__intro">Download one month as an Excel file in your client’s format.</p>

      <BaseSelect
        v-model="month"
        variant="field"
        label="Month"
        :options="months"
        :icon="CalendarDays"
      />

      <fieldset class="templates">
        <legend class="templates__legend">Template</legend>
        <p v-if="templatesQuery.isPending.value" class="templates__state" role="status">
          Loading templates…
        </p>
        <div v-else-if="templatesQuery.isError.value" class="templates__state" role="alert">
          The templates couldn’t be loaded.
          <BaseButton variant="secondary" size="small" @click="templatesQuery.refetch()">
            Try again
          </BaseButton>
        </div>
        <div v-else class="templates__grid">
          <!-- Native radios: one tab stop and arrow keys between them come with the browser -->
          <label
            v-for="template in templates"
            :key="template.code"
            class="card"
            :class="{ 'card--selected': selected === template.code }"
          >
            <input
              v-model="selected"
              type="radio"
              class="card__radio"
              :name="radioName"
              :value="template.code"
              @change="picked = true"
            />
            <span class="card__text">
              <span class="card__title">
                {{ template.client ? clientLabel(template.client) : 'Generic' }}
                <span v-if="isMine(template.client)" class="card__badge">Your client</span>
              </span>
              <!-- Vue drops the line break: without a space, the radio's name runs the two together -->
              {{ ' ' }}<span class="card__description">{{ template.name }}</span>
            </span>
          </label>
        </div>
      </fieldset>

      <div v-if="warnings.length" class="export__warning" role="status">
        <TriangleAlert :size="20" aria-hidden="true" />
        <div class="export__warning-text">
          <p v-for="text in warnings" :key="text">{{ text }}</p>
        </div>
      </div>

      <div v-if="error" class="export__error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ error }}
      </div>

      <footer class="export__footer">
        <BaseButton variant="secondary" @click="dialog?.close()">Cancel</BaseButton>
        <BaseButton :disabled="!selected || exportMonth.isPending.value" @click="onDownload">
          <Download :size="18" aria-hidden="true" />
          {{ exportMonth.isPending.value ? 'Preparing…' : 'Download .xlsx' }}
        </BaseButton>
      </footer>
    </div>
  </BaseDialog>
</template>

<style scoped>
.export {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.export__intro {
  margin: 0;
  font-size: 14px;
  color: var(--color-muted);
}

.templates {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
  margin: 0;
  padding: 0;
  border: none;
}

.templates__legend {
  margin-bottom: var(--space-3);
  padding: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-muted);
}

.templates__state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  margin: 0;
  font-size: 14px;
  color: var(--color-muted);
}

.templates__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

@media (width <= 520px) {
  .templates__grid {
    grid-template-columns: 1fr;
  }
}

.card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
  background: var(--color-surface);
  cursor: pointer;
}

/* 2px primary border, drawn as border + 1px ring so the card doesn't grow when it's picked */
.card--selected {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
  background: var(--color-primary-soft);
}

.card__radio {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin: 0;
  border: 2px solid var(--color-line);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  appearance: none;
  cursor: pointer;
}

.card__radio:checked {
  border: 6px solid var(--color-primary);
}

.card__radio:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.card__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.card__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  font-size: 14px;
  font-weight: 600;
}

.card__badge {
  padding: var(--space-1) 10px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-surface);
  font-size: 12px;
  font-weight: 600;
}

.card__description {
  font-size: 12px;
  color: var(--color-muted);
}

.export__warning {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-input);
  background: var(--color-primary-soft);
  color: var(--color-ink);
}

.export__warning svg {
  flex-shrink: 0;
  color: var(--color-primary);
}

.export__warning-text {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.export__warning p {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
}

.export__error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: 14px;
  font-weight: 500;
}

.export__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* Phones (design.md, "Mobile"): full-width buttons, the main action on top */
@media (max-width: 720px) {
  .export__footer {
    flex-direction: column-reverse;
  }
}
</style>
