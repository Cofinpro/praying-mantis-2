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
      await queryClient.fetchQuery({
        queryKey: queryKeys.me,
        queryFn: api.getMe,
        // Other pages trust the cached user (login seeds it, client.ts drops it on a 401), so most
        // navigations make no request. The login page always asks the server: a cached user may
        // belong to a session that has just expired, and without asking we'd bounce them home.
        // That GET /me also gives a fresh tab its CSRF cookie before the first POST.
        staleTime: to.meta.public ? 0 : Infinity,
      })
    } catch (error) {
      const unauthorized = error instanceof ApiError && error.status === 401
      if (unauthorized) {
        // A failed fetch keeps the old data next to the error, so drop the user explicitly
        queryClient.removeQueries({ queryKey: queryKeys.me })
      }
      if (to.meta.public) {
        return true
      }
      if (unauthorized) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
      // Backend down or broken: cancel the navigation instead of pretending the user logged out
      throw error
    }
    // Already logged in: the login page has nothing to offer
    return to.name === 'login' ? { path: '/' } : true
  })
}
