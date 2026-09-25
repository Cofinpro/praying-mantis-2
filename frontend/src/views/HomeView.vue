<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'

import { api } from '@/api/client'

// Sample call to GET /api/hello, to check that FE and BE (or the MSW mock) are wired up
const hello = useQuery({ queryKey: ['hello'], queryFn: api.getHello })
</script>

<template>
  <main>
    <h1>Praying Mantis</h1>
    <p>Timesheets and vacations in one place.</p>

    <p v-if="hello.isPending.value">Contacting the backend…</p>
    <p v-else-if="hello.isError.value" class="error">
      Backend unavailable: {{ hello.error.value?.message }}
    </p>
    <p v-else>Backend says: {{ hello.data.value?.message }}</p>
  </main>
</template>
