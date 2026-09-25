<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchHello } from '@/api/hello'

const message = ref<string>()
const error = ref<string>()

onMounted(async () => {
  try {
    message.value = (await fetchHello()).message
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <p v-if="message" class="greeting">{{ message }}</p>
  <p v-else-if="error" class="error">Backend unavailable: {{ error }}</p>
  <p v-else>Loading…</p>
</template>
