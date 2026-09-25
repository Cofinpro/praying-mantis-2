import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)

// One QueryClient for the whole app: it holds the cache of all server data (decision #6)
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Treat fetched data as fresh for 30 s, so navigating between pages doesn't refetch it
      staleTime: 30_000,
    },
  },
})

// Pinia is for client-only state shared across views. Server data goes through TanStack Query.
app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, { queryClient })

app.mount('#app')
