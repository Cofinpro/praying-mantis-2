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
</style>
