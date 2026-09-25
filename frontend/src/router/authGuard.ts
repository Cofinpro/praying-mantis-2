import type { QueryClient } from '@tanstack/vue-query'
import type { Router } from 'vue-router'

import { api, ApiError } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

/**
 * Sends visitors without a session to the login page (FE-1.2). Installed from main.ts rather than in
 * router/index.ts, so the router and the API client (which imports the router) don't import each
 * other, and tests can install it on their own router.
 */
export function installAuthGuard(router: Router, queryClient: QueryClient) {
  router.beforeEach(async (to) => {
    try {
      // Uses the cached user when there is one (login seeds it), so most navigations make no
      // request. Without one, it calls GET /me, which also gives a fresh tab its CSRF cookie
      // before the first POST.
      await queryClient.ensureQueryData({ queryKey: queryKeys.me, queryFn: api.getMe })
    } catch (error) {
      if (to.meta.public) {
        return true
      }
      if (error instanceof ApiError && error.status === 401) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
      // Backend down or broken: cancel the navigation instead of pretending the user logged out
      throw error
    }
    // Already logged in: the login page has nothing to offer
    return to.name === 'login' ? { path: '/' } : true
  })
}
