import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import router from './router'
import { installAuthGuard } from './router/authGuard'
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
app.use(VueQueryPlugin, { queryClient })
installAuthGuard(router, queryClient)

// With `pnpm dev:mock`, start MSW before mounting so the first requests are already mocked
// (decision #4). Vite replaces the env check at build time, so a production build drops this code.
async function enableMocking() {
  if (import.meta.env.VITE_API_MOCKS !== 'true') {
    return
  }
  const { worker } = await import('./mocks/browser')
  await worker.start({
    // /api calls without a handler go on to the real backend, with a console warning
    onUnhandledRequest(request, print) {
      if (new URL(request.url).pathname.startsWith('/api')) {
        print.warning()
      }
    },
  })
}

// Installing the router starts the first navigation, and the auth guard calls GET /me in it. So:
// mocks first, then the router, and mount only once that navigation has settled, so nothing renders
// for a page the guard is about to redirect away from.
async function start() {
  await enableMocking()
  app.use(router)
  await router.isReady()
  app.mount('#app')
}

start()
