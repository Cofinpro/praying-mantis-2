<script setup lang="ts">
// Stand-in for pages whose story isn't built yet, so the nav has somewhere to go
defineProps<{ title: string; story: string }>()
</script>

<template>
  <header class="page-header">
    <h1 class="page-header__title">{{ title }}</h1>
    <p class="page-header__subtitle">This page comes with {{ story }}.</p>
  </header>
</template>

<style scoped>
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
</style>
