import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import { queryClient } from './api/queryClient'
import router from './router'
import { installAuthGuard } from './router/authGuard'
import './assets/main.css'

const app = createApp(App)

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
    // The worker file is in public/, so it's served under the base path: /praying-mantis-2/ on
    // GitHub Pages (decision #30), / everywhere else. MSW's default is always /mockServiceWorker.js
    serviceWorker: { url: `${import.meta.env.BASE_URL}mockServiceWorker.js` },
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
  try {
    await router.isReady()
  } catch {
    // The guard cancels a navigation when GET /me fails for another reason than 401. Later that
    // just keeps the current page, but on first load there is none, and isReady() rejects.
    // Plain text until the "server unavailable" state has a Figma frame (decision #24).
    document.getElementById('app')!.textContent =
      'The server is unavailable. Please try again in a moment.'
    return
  }
  app.mount('#app')
}

start()
