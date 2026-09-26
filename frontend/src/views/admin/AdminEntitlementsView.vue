<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { Lock, TriangleAlert } from 'lucide-vue-next'

import type { AbsenceTypeCode, AdminEntitlement } from '@/api/client'
import { fieldErrors, isForbidden, problemMessage } from '@/api/problems'
import { useAbsenceTypes } from '@/absences/queries'
import { cellError, changeFor, toCell, totalOf, type EntitlementCells } from '@/admin/entitlements'
import { useAdminEntitlements, useAdminUsers, useSaveEntitlements } from '@/admin/queries'
import { avatarColor } from '@/approvals/avatar'
import BaseButton from '@/components/BaseButton.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import SegmentedControl from '@/components/SegmentedControl.vue'
import { formatNumber, today, yearOf } from '@/format/dates'
import { initials } from '@/format/labels'

// FE-9.2, frame "13 Admin – Entitlements": one row per person for the chosen year and type, the
// days as number cells, one "Save changes" for all changed rows. The contract saves one
// entitlement per call (an upsert, decision 35), so the page sends a call per changed row.
// The frame's Used / Pending / Remaining columns need numbers the contract doesn't have for other
// people yet (AdminEntitlement has only the two stored values), so the page shows the Total.

const thisYear = yearOf(today())
const year = ref(thisYear)
const type = ref<AbsenceTypeCode>('VACATION')
const yearOptions = [thisYear - 1, thisYear, thisYear + 1, thisYear + 2].map((y) => ({
  value: y,
  label: String(y),
}))

const types = useAbsenceTypes()
// Only types with a balance have entitlements that mean something (Vacation, Training)
const typeOptions = computed(() =>
  (types.data.value ?? [])
    .filter((t) => t.deductsFromBalance)
    .sort((a, b) => Number(b.code === 'VACATION') - Number(a.code === 'VACATION'))
    .map((t) => ({ value: t.code, label: t.name })),
)
const typeName = computed(
  () => typeOptions.value.find((t) => t.value === type.value)?.label.toLowerCase() ?? 'days',
)

const users = useAdminUsers()
const entitlements = useAdminEntitlements(year)
const forbidden = computed(
  () => isForbidden(users.error.value) || isForbidden(entitlements.error.value),
)
const loading = computed(() => users.isPending.value || entitlements.isPending.value)
const failed = computed(() => users.isError.value || entitlements.isError.value)

function retry() {
  if (users.isError.value) void users.refetch()
  if (entitlements.isError.value) void entitlements.refetch()
}

/** What the admin typed, per user id; a row without an edit shows what's saved */
const edits = ref<Record<number, EntitlementCells>>({})
/** The last save's errors, per user id */
const rowErrors = ref<Record<number, { entitled?: string; carried?: string }>>({})
const saveBanner = ref<string | null>(null)

const saved = computed(() => {
  const byUser = new Map<number, AdminEntitlement>()
  for (const e of entitlements.data.value ?? []) {
    if (e.type === type.value) byUser.set(e.user.id, e)
  }
  return byUser
})

const rows = computed(() =>
  (users.data.value ?? []).map((u) => {
    const entitlement = saved.value.get(u.id)
    const cells = edits.value[u.id] ?? {
      entitled: toCell(entitlement?.entitledDays),
      carried: toCell(entitlement?.carriedOverDays),
    }
    const total = totalOf(cells)
    return {
      user: u,
      initials: initials(u.name),
      avatarColor: avatarColor(u.id),
      cells,
      total: total === undefined ? '–' : formatNumber(total),
      entitledError: cellError(cells.entitled) ?? rowErrors.value[u.id]?.entitled,
      carriedError: cellError(cells.carried) ?? rowErrors.value[u.id]?.carried,
      change: changeFor({ userId: u.id, year: year.value, type: type.value }, entitlement, cells),
    }
  }),
)

const changes = computed(() => rows.value.flatMap((r) => (r.change ? [r.change] : [])))
const dirty = computed(() => changes.value.length > 0)
const invalid = computed(() =>
  rows.value.some((r) => cellError(r.cells.entitled) || cellError(r.cells.carried)),
)

/** A copy of `record` without `key` */
function without<T>(record: Record<number, T>, key: number): Record<number, T> {
  return Object.fromEntries(Object.entries(record).filter(([k]) => Number(k) !== key))
}

function edit(row: (typeof rows.value)[number], cell: keyof EntitlementCells, value: string) {
  edits.value = { ...edits.value, [row.user.id]: { ...row.cells, [cell]: value } }
  // A new value starts over without the previous answer's error for this row
  rowErrors.value = without(rowErrors.value, row.user.id)
}

const save = useSaveEntitlements()

async function saveChanges() {
  const sent = changes.value
  saveBanner.value = null
  const results = await save.mutateAsync(sent)
  const names = new Map((users.data.value ?? []).map((u) => [u.id, u.name]))
  const errors: typeof rowErrors.value = {}
  const failedNames: string[] = []
  let message: string | null = null
  results.forEach((result, i) => {
    const change = sent[i]!
    if (result.status === 'fulfilled') {
      // Saved: the refetched grid shows it, so the edit can go
      edits.value = without(edits.value, change.userId)
      return
    }
    const fields = fieldErrors(result.reason)
    if (fields.entitledDays || fields.carriedOverDays) {
      errors[change.userId] = { entitled: fields.entitledDays, carried: fields.carriedOverDays }
    } else {
      failedNames.push(names.get(change.userId) ?? `user ${change.userId}`)
      message ??= problemMessage(result.reason)
    }
  })
  rowErrors.value = errors
  if (failedNames.length) {
    saveBanner.value = `Couldn’t save ${failedNames.join(', ')}. ${message ?? 'Something went wrong. Please try again.'}`
  }
}

const lastSaveOk = computed(
  () => save.isSuccess.value && !saveBanner.value && Object.keys(rowErrors.value).length === 0,
)

// --- Unsaved changes: switching the year or type, leaving the page, closing the tab ------------

/** Resolves the open "Discard changes?" dialog: true discards, false keeps editing */
const pendingDiscard = ref<((discard: boolean) => void) | null>(null)
/** Re-renders the year and type controls when a switch is called off, so they show the old value */
const controlsKey = ref(0)

function confirmDiscard(): boolean | Promise<boolean> {
  if (!dirty.value) {
    return true
  }
  return new Promise((resolve) => {
    pendingDiscard.value = resolve
  })
}

function answerDiscard(discard: boolean) {
  pendingDiscard.value?.(discard)
  pendingDiscard.value = null
}

async function switchTo(apply: () => void) {
  if (await confirmDiscard()) {
    edits.value = {}
    rowErrors.value = {}
    saveBanner.value = null
    save.reset()
    apply()
  } else {
    controlsKey.value++
  }
}

const selectedYear = computed({
  get: () => year.value,
  set: (value: number) => void switchTo(() => (year.value = value)),
})
const selectedType = computed({
  get: () => type.value,
  set: (value: AbsenceTypeCode) => void switchTo(() => (type.value = value)),
})

onBeforeRouteLeave(() => confirmDiscard())

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault()
  }
}
onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

const subtitle = computed(
  () => `Yearly ${typeName.value} days per person for ${year.value}, in steps of 0.5`,
)
const errorId = (userId: number, cell: string) => `entitlement-${userId}-${cell}-error`
</script>

<template>
  <section class="section" aria-labelledby="entitlements-title">
    <header class="section__header">
      <div>
        <h2 id="entitlements-title" class="section__title">Entitlements</h2>
        <p v-if="!forbidden" class="section__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="!forbidden" :key="controlsKey" class="section__actions">
        <SegmentedControl
          v-if="typeOptions.length > 1"
          v-model="selectedType"
          label="Absence type"
          hide-label
          :options="typeOptions"
        />
        <BaseSelect v-model="selectedYear" label="Year" :options="yearOptions" />
      </div>
    </header>

    <p v-if="loading" class="state" role="status">Loading entitlements…</p>
    <div v-else-if="forbidden" class="state state--forbidden" role="alert">
      <Lock :size="24" aria-hidden="true" class="state__icon" />
      <p class="state__title">You need an admin account</p>
      <p>Only admins can manage entitlements. Ask an admin if you need access.</p>
    </div>
    <div v-else-if="failed" class="state state--error" role="alert">
      <p>The entitlements couldn't be loaded.</p>
      <BaseButton variant="secondary" size="small" @click="retry">Try again</BaseButton>
    </div>
    <div v-else class="card">
      <table class="table">
        <caption class="visually-hidden">
          {{
            `Entitlements ${year}, ${typeName}`
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col">Person</th>
            <th scope="col" class="col-days">Entitled</th>
            <th scope="col" class="col-days">Carried over</th>
            <th scope="col" class="col-total">Total</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.user.id">
            <th scope="row">
              <span class="person">
                <span
                  class="person__avatar"
                  :style="{ background: row.avatarColor }"
                  aria-hidden="true"
                  >{{ row.initials }}</span
                >
                <span class="person__name">{{ row.user.name }}</span>
              </span>
            </th>
            <td class="days">
              <input
                type="number"
                min="0"
                max="366"
                step="0.5"
                inputmode="decimal"
                placeholder="0"
                class="days__input"
                :class="{ 'days__input--error': row.entitledError }"
                :value="row.cells.entitled"
                :aria-label="`Entitled days, ${row.user.name}`"
                :aria-invalid="row.entitledError ? 'true' : undefined"
                :aria-describedby="row.entitledError ? errorId(row.user.id, 'entitled') : undefined"
                @input="edit(row, 'entitled', ($event.target as HTMLInputElement).value)"
              />
              <p
                v-if="row.entitledError"
                :id="errorId(row.user.id, 'entitled')"
                class="days__error"
              >
                {{ row.entitledError }}
              </p>
            </td>
            <td class="days">
              <input
                type="number"
                min="0"
                max="366"
                step="0.5"
                inputmode="decimal"
                placeholder="0"
                class="days__input"
                :class="{ 'days__input--error': row.carriedError }"
                :value="row.cells.carried"
                :aria-label="`Carried over days, ${row.user.name}`"
                :aria-invalid="row.carriedError ? 'true' : undefined"
                :aria-describedby="row.carriedError ? errorId(row.user.id, 'carried') : undefined"
                @input="edit(row, 'carried', ($event.target as HTMLInputElement).value)"
              />
              <p v-if="row.carriedError" :id="errorId(row.user.id, 'carried')" class="days__error">
                {{ row.carriedError }}
              </p>
            </td>
            <td class="total">{{ row.total }}</td>
          </tr>
        </tbody>
      </table>

      <div v-if="saveBanner" class="banner" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ saveBanner }}
      </div>

      <footer class="footer">
        <p class="footer__note">
          Emptying both cells removes the entitlement: that person then has no {{ typeName }} days
          in {{ year }}.
        </p>
        <p v-if="dirty" class="footer__unsaved" role="status">
          <span class="dot" aria-hidden="true" />
          Unsaved changes
        </p>
        <p v-else-if="lastSaveOk" class="footer__saved" role="status">Saved</p>
        <BaseButton :disabled="!dirty || invalid || save.isPending.value" @click="saveChanges">
          {{ save.isPending.value ? 'Saving…' : 'Save changes' }}
        </BaseButton>
      </footer>
    </div>

    <ConfirmDialog
      v-if="pendingDiscard"
      heading="Discard unsaved changes?"
      cancel-label="Keep editing"
      confirm-label="Discard changes"
      danger
      @confirm="answerDiscard(true)"
      @close="answerDiscard(false)"
    >
      Your changes to the {{ typeName }} entitlements for {{ year }} aren’t saved yet. If you go on,
      they’re lost.
    </ConfirmDialog>
  </section>
</template>

<style scoped src="../../admin/section.css"></style>

<style scoped>
.col-days {
  width: 160px;
}

.col-total {
  width: 120px;
}

.table td.days {
  vertical-align: top;
}

.days__input {
  width: 96px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-input);
  background: var(--color-surface);
  color: var(--color-ink);
  font: inherit;
  font-size: 14px;
  text-align: right;
}

.days__input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.days__input--error,
.days__input--error:focus {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 1px var(--color-danger);
}

.days__error {
  max-width: 200px;
  margin: var(--space-1) 0 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-danger);
  white-space: normal;
}

.total {
  font-weight: 700;
}

.banner {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0 var(--space-4) var(--space-4);
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-weight: 500;
}

.footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  border-top: 1px solid var(--color-line);
}

.footer__note {
  flex: 1;
  min-width: 240px;
  margin: 0;
  font-size: 13px;
  color: var(--color-muted);
}

.footer__unsaved,
.footer__saved {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: 13px;
  font-weight: 500;
}

.footer__unsaved {
  color: var(--color-primary);
}

.footer__saved {
  color: var(--color-muted);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
}
</style>
