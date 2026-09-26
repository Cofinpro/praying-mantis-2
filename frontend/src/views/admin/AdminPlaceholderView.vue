<script setup lang="ts">
// Stand-in for the admin sections whose story isn't built yet (FE-9.2 to FE-9.4)
defineProps<{ title: string; story: string }>()
</script>

<template>
  <section class="section" aria-labelledby="section-title">
    <header class="section__header">
      <h2 id="section-title" class="section__title">{{ title }}</h2>
      <p class="section__subtitle">This section comes with {{ story }}.</p>
    </header>
  </section>
</template>

<style scoped>
.section__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.section__subtitle {
  margin: var(--space-1) 0 0;
  font-size: 13px;
  color: var(--color-muted);
}
</style>
