<script setup lang="ts">
import { computed, ref } from 'vue'
import { Lock, Pencil, Plus, Search } from 'lucide-vue-next'

import type { AdminUser } from '@/api/client'
import { isForbidden } from '@/api/problems'
import { useAdminUsers } from '@/admin/queries'
import { avatarColor } from '@/approvals/avatar'
import BaseButton from '@/components/BaseButton.vue'
import BaseToggle from '@/components/BaseToggle.vue'
import UserDialog from '@/components/admin/UserDialog.vue'
import { clientLabel, initials, levelLabel } from '@/format/labels'

// FE-9.1, frame "11 Admin – Users". The contract returns everyone at once (no paging), so the
// search filters on the client. The Admin column only shows the flag: it's changed in the edit
// dialog, where the last-admin rule (409) can be explained next to it.
const users = useAdminUsers()
const forbidden = computed(() => isForbidden(users.error.value))

const search = ref('')
const all = computed(() => users.data.value ?? [])
const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  return term
    ? all.value.filter(
        (u) => u.name.toLowerCase().includes(term) || u.email.toLowerCase().includes(term),
      )
    : all.value
})

const rows = computed(() =>
  filtered.value.map((u) => ({
    user: u,
    initials: initials(u.name),
    avatarColor: avatarColor(u.id),
    client: clientLabel(u.client),
    level: levelLabel(u.level),
    editLabel: `Edit ${u.name}`,
  })),
)

const subtitle = computed(() => {
  const count = all.value.length
  return `${count} ${count === 1 ? 'person' : 'people'} · the team lead is who approves their absences and timesheets`
})

/** The dialog: `undefined` closed, `null` adding, a user editing */
const editing = ref<AdminUser | null | undefined>(undefined)
</script>

<template>
  <section class="section" aria-labelledby="users-title">
    <header class="section__header">
      <div>
        <h2 id="users-title" class="section__title">Users</h2>
        <p v-if="users.isSuccess.value" class="section__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="!forbidden" class="section__actions">
        <label class="search">
          <Search :size="18" aria-hidden="true" class="search__icon" />
          <span class="visually-hidden">Search users</span>
          <input
            v-model="search"
            type="search"
            class="search__input"
            placeholder="Search by name or email"
          />
        </label>
        <BaseButton :disabled="!users.isSuccess.value" @click="editing = null">
          <Plus :size="18" aria-hidden="true" />
          Add user
        </BaseButton>
      </div>
    </header>

    <p v-if="users.isPending.value" class="state" role="status">Loading users…</p>
    <div v-else-if="forbidden" class="state state--forbidden" role="alert">
      <Lock :size="24" aria-hidden="true" class="state__icon" />
      <p class="state__title">You need an admin account</p>
      <p>Only admins can manage users. Ask an admin if you need access.</p>
    </div>
    <div v-else-if="users.isError.value" class="state state--error" role="alert">
      <p>The users couldn't be loaded.</p>
      <BaseButton variant="secondary" size="small" @click="users.refetch()">Try again</BaseButton>
    </div>
    <p v-else-if="rows.length === 0" class="state" role="status">
      No user matches “{{ search.trim() }}”.
    </p>
    <div v-else class="card card--stack">
      <table class="table table--stack">
        <caption class="visually-hidden">
          Users
        </caption>
        <thead>
          <tr>
            <th scope="col">Person</th>
            <th scope="col" class="col-client">Client</th>
            <th scope="col" class="col-level">Level</th>
            <th scope="col" class="col-lead">Team lead</th>
            <th scope="col" class="col-admin">Admin</th>
            <th scope="col" class="col-edit"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.user.id">
            <th scope="row" class="person-cell">
              <span class="person">
                <span
                  class="person__avatar"
                  :style="{ background: row.avatarColor }"
                  aria-hidden="true"
                  >{{ row.initials }}</span
                >
                <span class="person__text">
                  <span class="person__name">{{ row.user.name }}</span>
                  <span class="person__email">{{ row.user.email }}</span>
                </span>
              </span>
            </th>
            <td class="client" data-label="Client">{{ row.client }}</td>
            <td data-label="Level">{{ row.level }}</td>
            <td v-if="row.user.teamLead" data-label="Team lead">{{ row.user.teamLead.name }}</td>
            <td v-else class="muted" data-label="Team lead">
              <span aria-hidden="true">—</span><span class="visually-hidden">None</span>
            </td>
            <td data-label="Admin"><BaseToggle :model-value="row.user.isAdmin" readonly /></td>
            <td class="edit stack-corner">
              <button
                type="button"
                class="icon-button"
                :aria-label="row.editLabel"
                @click="editing = row.user"
              >
                <Pencil :size="18" aria-hidden="true" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <UserDialog
      v-if="editing !== undefined"
      :user="editing ?? undefined"
      :users="all"
      @close="editing = undefined"
    />
  </section>
</template>

<style scoped src="../../admin/section.css"></style>

<style scoped>
.search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
}

.search:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.search__icon {
  flex: none;
  color: var(--color-muted);
}

.search__input {
  width: 180px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-ink);
  font: inherit;
  font-size: 14px;
}

.search__input::placeholder {
  color: var(--color-muted);
}

.search__input:focus {
  outline: none;
}

.col-client {
  width: 100px;
}

.col-level {
  width: 130px;
}

.col-lead {
  width: 190px;
}

.col-admin {
  width: 90px;
}

.col-edit {
  width: 80px;
}

.person__email {
  font-size: 12px;
  color: var(--color-muted);
}

.client {
  font-weight: 500;
}

.table td.edit {
  text-align: right;
}
</style>
