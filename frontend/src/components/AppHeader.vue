<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ExternalLink, LogOut } from 'lucide-vue-next'

import logoUrl from '@/assets/cofinpro-logo.svg'
import { useCurrentUser, useLogout } from '@/auth/session'
import NotificationBell from '@/components/NotificationBell.vue'
import { clientLabel, initials, levelLabel } from '@/format/labels'

// FE-1.2, the header in Figma frame "02 Absences" (design.md)
const { data: user } = useCurrentUser()
const { mutate: logout, isPending: loggingOut, isError: logoutFailed } = useLogout()

// Hiding a link is only a convenience; the backend enforces who may do what (decision #11)
const nav = computed(() => [
  { name: 'absences', label: 'Absences' },
  { name: 'timesheets', label: 'Timesheets' },
  ...(user.value?.isTeamLead ? [{ name: 'approvals', label: 'Approvals' }] : []),
  ...(user.value?.isAdmin ? [{ name: 'admin', label: 'Admin' }] : []),
])

// Apps owned by other groups, opened in a new tab (CLAUDE.md: "a nav link at most"). A link
// without a configured URL is left out rather than pointing at this app.
const external = [
  { label: 'Trainings', href: import.meta.env.VITE_TRAININGS_URL },
  { label: 'Seats', href: import.meta.env.VITE_SEATS_URL },
].filter((link): link is { label: string; href: string } => !!link.href)
</script>

<template>
  <header class="header">
    <div class="header__left">
      <RouterLink to="/" class="header__logo">
        <img :src="logoUrl" alt="Cofinpro, home" />
      </RouterLink>
      <nav class="nav" aria-label="Main">
        <RouterLink
          v-for="item in nav"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav__link"
          active-class="nav__link--active"
        >
          {{ item.label }}
        </RouterLink>
        <a
          v-for="item in external"
          :key="item.label"
          :href="item.href"
          target="_blank"
          rel="noopener noreferrer"
          class="nav__link nav__link--external"
        >
          {{ item.label }}
          <ExternalLink :size="14" aria-hidden="true" />
          <span class="visually-hidden">(opens in a new tab)</span>
        </a>
      </nav>
    </div>

    <div class="header__right">
      <NotificationBell />

      <div v-if="user" class="user">
        <span class="user__avatar" aria-hidden="true">{{ initials(user.name) }}</span>
        <div class="user__text">
          <span class="user__name">{{ user.name }}</span>
          <span class="user__role"
            >{{ levelLabel(user.level) }} · {{ clientLabel(user.client) }}</span
          >
        </div>
      </div>

      <!-- No frame for this state yet (decision #24): a short, announced message -->
      <span v-if="logoutFailed" class="header__error" role="alert"
        >Couldn't log out. Try again.</span
      >
      <button
        type="button"
        class="icon-button"
        aria-label="Log out"
        :disabled="loggingOut"
        @click="logout()"
      >
        <LogOut :size="20" aria-hidden="true" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72px;
  padding: 0 var(--space-12);
  border-bottom: 1px solid var(--color-line);
  background: var(--color-surface);
}

.header__left,
.header__right {
  display: flex;
  align-items: center;
  height: 100%;
}

.header__left {
  gap: var(--space-10);
}

.header__right {
  gap: var(--space-5);
}

.header__logo {
  display: flex;
}

.header__logo img {
  height: 20px;
}

.nav {
  display: flex;
  gap: var(--space-7);
  height: 100%;
}

.nav__link {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: 15px;
  font-weight: 500;
  color: var(--color-muted);
  text-decoration: none;
}

.nav__link:hover {
  color: var(--color-ink);
}

.nav__link--active {
  font-weight: 600;
  color: var(--color-ink);
}

/* The 3px primary underline, flush with the header's bottom border */
.nav__link--active::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  background: var(--color-primary);
}

.nav__link:focus-visible,
.header__logo:focus-visible,
.icon-button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
}

.icon-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-surface);
  font-size: 13px;
  font-weight: 600;
}

.user__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.2;
}

.user__name {
  font-size: 14px;
  font-weight: 600;
}

.user__role {
  font-size: 12px;
  color: var(--color-muted);
}

.header__error {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-danger);
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
