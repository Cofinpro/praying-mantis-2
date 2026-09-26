<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'

import AppHeader from '@/components/AppHeader.vue'

// The login page has its own full-screen layout; every other page gets the header (FE-1.2)
const route = useRoute()
</script>

<template>
  <RouterView v-if="route.meta.public" />
  <template v-else>
    <AppHeader />
    <main class="page">
      <RouterView />
    </main>
  </template>
</template>

<style scoped>
.page {
  padding: var(--space-9) var(--space-12);
}

/* design.md, "Mobile": 24px sides on tablets, 16px on phones */
@media (max-width: 1024px) {
  .page {
    padding: var(--space-7) var(--space-6);
  }
}

@media (max-width: 720px) {
  .page {
    padding: var(--space-5) var(--space-4) var(--space-10);
  }
}
</style>
