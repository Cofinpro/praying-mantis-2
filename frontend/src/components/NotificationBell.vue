<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useId, useTemplateRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { Bell } from 'lucide-vue-next'

import type { AppNotification, NotificationType } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import { formatTimeAgo } from '@/format/dates'
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useRecentNotifications,
  useUnreadNotificationCount,
} from '@/notifications/queries'
import { isInAppPath } from '@/router/inAppPath'

// FE-4.1, the bell in the header and the Figma frame "07 Notifications dropdown" (design.md).
// A disclosure: a button that shows and hides the panel below it. Not a menu, so Tab moves
// through the panel as usual; Esc, a click outside or tabbing out of it closes it.

const router = useRouter()
const queryClient = useQueryClient()
const open = ref(false)
const panelId = useId()
const titleId = useId()
const root = useTemplateRef('root')
const toggle = useTemplateRef('toggle')

const { data: unreadCount } = useUnreadNotificationCount()
const {
  data: page,
  isPending: loading,
  isError: failed,
  refetch,
} = useRecentNotifications(() => open.value)
const markRead = useMarkNotificationRead()
const markAllRead = useMarkAllNotificationsRead()

const count = computed(() => unreadCount.value ?? 0)
const badge = computed(() => (count.value > 9 ? '9+' : String(count.value)))
const label = computed(() =>
  count.value > 0 ? `Notifications, ${count.value} unread` : 'Notifications',
)
const items = computed(() => page.value?.items ?? [])
const anyUnread = computed(() => count.value > 0 || items.value.some(isUnread))

// The poll found something new while the panel is open: show it
watch(count, () => {
  if (open.value) {
    void refetch()
  }
})

// More unread than at the last poll: someone requested, decided or cancelled an absence, or
// submitted or decided a week. Refresh what the pages show, so both Approvals tabs (['team']
// covers them), the calendar and my week's status update without a reload (workflow step 7).
// Only active queries refetch; the rest are just marked stale. Not on the first load.
watch(unreadCount, (now, before) => {
  if (now !== undefined && before !== undefined && now > before) {
    void queryClient.invalidateQueries({ queryKey: queryKeys.team.all })
    void queryClient.invalidateQueries({ queryKey: queryKeys.absences.all })
    void queryClient.invalidateQueries({ queryKey: queryKeys.timesheets.all })
  }
})

// The icon colour per type; design.md lists the mapping
const TONES: Record<NotificationType, string> = {
  ABSENCE_REQUESTED: 'primary',
  ABSENCE_APPROVED: 'success',
  ABSENCE_REJECTED: 'danger',
  ABSENCE_CANCELLED: 'muted',
  TIMESHEET_SUBMITTED: 'info',
  TIMESHEET_APPROVED: 'success',
  TIMESHEET_REJECTED: 'danger',
}

// The backend sends `readAt: null` for unread ones, the contract allows leaving it out
function isUnread(n: AppNotification): boolean {
  return n.readAt == null
}

function close({ focusToggle = false } = {}) {
  open.value = false
  if (focusToggle) {
    toggle.value?.focus()
  }
}

function onToggle() {
  if (open.value) {
    close()
  } else {
    open.value = true
  }
}

function select(n: AppNotification) {
  if (isUnread(n)) {
    markRead.mutate(n.id)
  }
  close()
  // Only paths inside the app, never a full or protocol-relative URL (contract: `link`)
  if (isInAppPath(n.link)) {
    void router.push(n.link)
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    event.stopPropagation()
    close({ focusToggle: true })
  }
}

function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget
  if (open.value && next instanceof Node && !root.value?.contains(next)) {
    close()
  }
}

function onDocumentPointerDown(event: PointerEvent) {
  if (event.target instanceof Node && !root.value?.contains(event.target)) {
    close()
  }
}

// Listen for outside clicks only while the panel is open
watch(open, (isOpen) => {
  if (isOpen) {
    document.addEventListener('pointerdown', onDocumentPointerDown)
  } else {
    document.removeEventListener('pointerdown', onDocumentPointerDown)
  }
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
</script>

<template>
  <div ref="root" class="bell" @keydown="onKeydown" @focusout="onFocusOut">
    <button
      ref="toggle"
      type="button"
      class="bell__button"
      :aria-label="label"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="onToggle"
    >
      <Bell :size="20" aria-hidden="true" />
      <span v-if="count > 0" class="bell__badge" aria-hidden="true">{{ badge }}</span>
    </button>

    <section v-show="open" :id="panelId" class="panel" :aria-labelledby="titleId">
      <div class="panel__header">
        <h2 :id="titleId" class="panel__title">Notifications</h2>
        <button
          type="button"
          class="panel__mark-all"
          :disabled="!anyUnread || markAllRead.isPending.value"
          @click="markAllRead.mutate()"
        >
          Mark all read
        </button>
      </div>

      <p v-if="loading" class="panel__state">Loading notifications…</p>
      <div v-else-if="failed" class="panel__state panel__state--error" role="alert">
        <span>Couldn't load notifications.</span>
        <button type="button" class="panel__retry" @click="refetch()">Try again</button>
      </div>
      <p v-else-if="items.length === 0" class="panel__state">You're all caught up.</p>
      <ul v-else class="panel__list">
        <li v-for="n in items" :key="n.id">
          <button
            type="button"
            class="item"
            :class="{ 'item--unread': isUnread(n) }"
            @click="select(n)"
          >
            <span class="item__icon" :class="`item__icon--${TONES[n.type]}`" aria-hidden="true" />
            <span class="item__text">
              <span class="item__message">{{ n.message }}</span>
              <span class="item__time">
                <time :datetime="n.createdAt">{{ formatTimeAgo(n.createdAt) }}</time>
                <span v-if="isUnread(n)" class="visually-hidden">, unread</span>
              </span>
            </span>
            <span v-if="isUnread(n)" class="item__dot" aria-hidden="true" />
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.bell {
  position: relative;
}

.bell__button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-pill);
  background: none;
  color: var(--color-ink);
  cursor: pointer;
}

.bell__button:focus-visible,
.panel button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.item:focus-visible {
  outline-offset: -2px;
}

/* 18px primary circle on the top-right edge (frame "02 Absences") */
.bell__badge {
  position: absolute;
  top: -4px;
  right: -4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 var(--space-1);
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-surface);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.panel {
  position: absolute;
  top: calc(100% + var(--space-2));
  right: 0;
  z-index: 10;
  width: 420px;
  max-height: min(560px, calc(100vh - 96px));
  overflow-y: auto;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-surface);
  box-shadow: var(--shadow-float);
}

.panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
}

.panel__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.panel__mark-all,
.panel__retry {
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.panel__mark-all:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.panel__state {
  display: flex;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-6) var(--space-5);
  border-top: 1px solid var(--color-line);
  color: var(--color-muted);
  font-size: 14px;
}

.panel__state--error {
  color: var(--color-danger);
}

.panel__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.item {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  width: 100%;
  padding: 14px var(--space-5);
  border: none;
  border-top: 1px solid var(--color-line);
  background: var(--color-surface);
  color: var(--color-muted);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.item:hover {
  background: var(--color-surface-alt);
}

.item--unread {
  background: var(--color-surface-alt);
  color: var(--color-ink);
}

.item__icon {
  position: relative;
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-pill);
}

.item__icon::after {
  content: '';
  position: absolute;
  inset: 11px;
  border-radius: var(--radius-pill);
}

.item__icon--primary {
  background: var(--color-primary-soft);
}

.item__icon--primary::after {
  background: var(--color-primary);
}

.item__icon--success {
  background: var(--color-success-soft);
}

.item__icon--success::after {
  background: var(--color-success);
}

.item__icon--danger {
  background: var(--color-danger-soft);
}

.item__icon--danger::after {
  background: var(--color-danger);
}

.item__icon--info {
  background: var(--color-info-soft);
}

.item__icon--info::after {
  background: var(--color-info);
}

.item__icon--muted {
  background: var(--color-grey);
}

.item__icon--muted::after {
  background: var(--color-muted);
}

.item__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.item__message {
  font-size: 14px;
  line-height: 20px;
}

.item--unread .item__message {
  font-weight: 600;
}

.item__time {
  color: var(--color-muted);
  font-size: 12px;
}

.item__dot {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: var(--space-1);
  border-radius: var(--radius-pill);
  background: var(--color-primary);
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
