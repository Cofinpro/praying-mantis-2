<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ExternalLink, LogOut, Menu, X } from 'lucide-vue-next'

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

// On phones the nav, the user and logout fold into a menu panel under the header (design.md,
// "Mobile"). It closes when a link takes you somewhere, and with Escape.
const menuOpen = ref(false)
const route = useRoute()
watch(
  () => route.fullPath,
  () => (menuOpen.value = false),
)
</script>

<template>
  <header class="header" :class="{ 'header--open': menuOpen }" @keydown.esc="menuOpen = false">
    <RouterLink to="/" class="header__logo">
      <img :src="logoUrl" alt="Cofinpro, home" />
    </RouterLink>

    <!-- One set of links for every width: a row on desktop, the menu panel on phones -->
    <div id="header-menu" class="header__menu">
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

      <div class="account">
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
          class="icon-button logout"
          aria-label="Log out"
          :disabled="loggingOut"
          @click="logout()"
        >
          <LogOut :size="20" aria-hidden="true" />
          <span class="logout__label" aria-hidden="true">Log out</span>
        </button>
      </div>
    </div>

    <div class="header__bell">
      <NotificationBell />
    </div>

    <button
      type="button"
      class="icon-button menu-toggle"
      :aria-label="menuOpen ? 'Close menu' : 'Open menu'"
      :aria-expanded="menuOpen"
      aria-controls="header-menu"
      @click="menuOpen = !menuOpen"
    >
      <X v-if="menuOpen" :size="24" aria-hidden="true" />
      <Menu v-else :size="24" aria-hidden="true" />
    </button>
  </header>
</template>

<style scoped>
.header {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: var(--space-10);
  height: 72px;
  padding: 0 var(--space-12);
  border-bottom: 1px solid var(--color-line);
  background: var(--color-surface);
}

/* Desktop: the menu's children join the header row; order puts the bell before the account */
.header__menu {
  display: contents;
}

.header__logo {
  display: flex;
  order: 1;
  flex-shrink: 0;
}

.header__logo img {
  height: 20px;
}

.nav {
  display: flex;
  order: 2;
  gap: var(--space-7);
  height: 100%;
}

.header__bell {
  order: 3;
  margin-left: auto;
}

.account {
  display: flex;
  order: 4;
  align-items: center;
  gap: var(--space-5);
}

/* .header in front, to beat .icon-button's display further down */
.header .menu-toggle {
  display: none;
  order: 5;
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
  white-space: nowrap;
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
  gap: var(--space-2);
  padding: 0;
  border: none;
  background: none;
  color: var(--color-muted);
  font: inherit;
  cursor: pointer;
}

.icon-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.logout__label {
  display: none;
}

.user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user__avatar {
  display: inline-flex;
  flex-shrink: 0;
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
  white-space: nowrap;
}

.user__role {
  font-size: 12px;
  color: var(--color-muted);
  white-space: nowrap;
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

/* Tablet: tighter spacing, and the avatar alone stands for the user */
@media (max-width: 1024px) {
  .header {
    gap: var(--space-6);
    padding: 0 var(--space-6);
  }

  .nav {
    gap: var(--space-5);
  }

  .user__text {
    display: none;
  }
}

/* Phone (design.md, "Mobile"): logo, bell and a menu button; the rest opens as a panel */
@media (max-width: 720px) {
  .header {
    position: sticky;
    top: 0;
    gap: var(--space-4);
    height: 60px;
    padding: 0 var(--space-4);
  }

  .header__logo img {
    height: 18px;
  }

  .header .menu-toggle {
    display: inline-flex;
    width: 40px;
    height: 40px;
    color: var(--color-ink);
  }

  .header__menu {
    position: absolute;
    top: 100%;
    right: 0;
    left: 0;
    display: none;
    flex-direction: column;
    gap: var(--space-4);
    max-height: calc(100dvh - 60px);
    overflow-y: auto;
    padding: var(--space-2) var(--space-4) var(--space-5);
    border-bottom: 1px solid var(--color-line);
    background: var(--color-surface);
    box-shadow: var(--shadow-float);
  }

  .header--open .header__menu {
    display: flex;
  }

  .nav {
    flex-direction: column;
    gap: 0;
    height: auto;
  }

  .nav__link {
    min-height: 48px;
    padding: 0 var(--space-3);
    border-radius: var(--radius-input);
    font-size: 16px;
  }

  /* The underline becomes a bar on the left, as in the admin sub-nav */
  .nav__link--active {
    background: var(--color-primary-soft);
  }

  .nav__link--active::after {
    top: var(--space-3);
    right: auto;
    bottom: var(--space-3);
    width: 3px;
    height: auto;
    border-radius: var(--radius-pill);
  }

  .account {
    flex-wrap: wrap;
    justify-content: space-between;
    padding-top: var(--space-4);
    border-top: 1px solid var(--color-line);
  }

  .user__text {
    display: flex;
  }

  .logout {
    min-height: 40px;
    padding: 0 var(--space-3);
    color: var(--color-ink);
    font-weight: 500;
  }

  .logout__label {
    display: inline;
  }
}
</style>
