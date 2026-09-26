<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

// The layout the admin pages share (design.md, "Admin pages share a layout"; frames 11 to 15):
// the page title, a 220px sub-nav, and the section on the right, which is a child route
// (FE-9.1 to FE-9.4). The header shows "Admin" to admins only; the backend enforces it (#11), so
// a non-admin who opens the URL gets a 403 from the section's request and sees that state.
const sections = [
  { name: 'admin-users', label: 'Users' },
  { name: 'admin-entitlements', label: 'Entitlements' },
  { name: 'admin-projects', label: 'Projects' },
  { name: 'admin-public-holidays', label: 'Public holidays' },
]
</script>

<template>
  <div class="admin">
    <header class="page-header">
      <h1 class="page-header__title">Admin</h1>
      <p class="page-header__subtitle">Users, entitlements, projects and public holidays</p>
    </header>

    <div class="admin__main">
      <nav class="subnav" aria-label="Admin sections">
        <RouterLink
          v-for="section in sections"
          :key="section.name"
          :to="{ name: section.name }"
          class="subnav__link"
          active-class="subnav__link--active"
        >
          {{ section.label }}
        </RouterLink>
      </nav>

      <div class="admin__section">
        <RouterView />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.page-header__title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.01em;
}

.page-header__subtitle {
  margin: 6px 0 0;
  font-size: 15px;
  color: var(--color-muted);
}

.admin__main {
  display: flex;
  align-items: flex-start;
  gap: var(--space-6);
}

.subnav {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--space-1);
  width: 220px;
}

.subnav__link {
  position: relative;
  padding: 11px var(--space-4);
  border: 1px solid transparent;
  border-radius: var(--radius-input);
  color: var(--color-muted);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
}

.subnav__link:hover {
  color: var(--color-ink);
}

.subnav__link:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.subnav__link--active {
  border-color: var(--color-line);
  background: var(--color-surface);
  color: var(--color-ink);
  font-weight: 600;
}

/* The 3px primary bar on the active item's left edge */
.subnav__link--active::before {
  position: absolute;
  top: 10px;
  left: -1px;
  width: 3px;
  height: 20px;
  background: var(--color-primary);
  content: '';
}

.admin__section {
  flex: 1;
  min-width: 0;
}

@media (max-width: 800px) {
  .admin__main {
    flex-direction: column;
    align-items: stretch;
  }

  /* design.md, "Mobile": the side nav becomes a row of pills that scrolls sideways */
  .subnav {
    flex-direction: row;
    overflow-x: auto;
    width: auto;
    margin: 0 calc(-1 * var(--space-4));
    padding: 0 var(--space-4) var(--space-1);
    scrollbar-width: none;
  }

  .subnav__link {
    flex: none;
    padding: var(--space-2) var(--space-4);
    border-color: var(--color-line);
    border-radius: var(--radius-pill);
    background: var(--color-surface);
    white-space: nowrap;
  }

  .subnav__link--active {
    border-color: var(--color-primary);
    background: var(--color-primary);
    color: var(--color-surface);
  }

  .subnav__link--active::before {
    display: none;
  }
}

@media (max-width: 720px) {
  .admin {
    gap: var(--space-5);
  }

  .page-header__title {
    font-size: 26px;
  }
}
</style>
