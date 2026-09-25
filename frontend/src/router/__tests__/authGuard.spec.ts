import { describe, it, expect, beforeEach } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { http, HttpResponse } from 'msw'
import type { QueryClient } from '@tanstack/vue-query'

import { routes } from '@/router'
import { installAuthGuard } from '@/router/authGuard'
import { queryKeys } from '@/api/queryKeys'
import { server } from '@/mocks/node'
import { mockUser, startMockSession } from '@/mocks/handlers'
import { testQueryClient } from '@/test/query'

describe('auth guard', () => {
  let router: Router
  let queryClient: QueryClient

  beforeEach(() => {
    // A router of its own per test, with the app's routes and the guard installed
    router = createRouter({ history: createMemoryHistory(), routes })
    queryClient = testQueryClient()
    installAuthGuard(router, queryClient)
  })

  it('sends a visitor without a session to login, remembering the page', async () => {
    await router.push('/timesheets?week=43')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/timesheets?week=43')
  })

  it('lets a logged-in user through and caches them', async () => {
    startMockSession()

    await router.push('/timesheets')

    expect(router.currentRoute.value.name).toBe('timesheets')
    expect(queryClient.getQueryData(queryKeys.me)).toMatchObject({ name: mockUser.name })
  })

  it('uses the cached user without calling GET /me', async () => {
    queryClient.setQueryData(queryKeys.me, mockUser)
    server.use(http.get('*/api/me', () => HttpResponse.error()))

    await router.push('/timesheets')

    expect(router.currentRoute.value.name).toBe('timesheets')
  })

  it('shows the login page to a visitor without a session', async () => {
    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('shows the login page when the session of the cached user has expired', async () => {
    // client.ts sends an expired session here; the stale cached user must not bounce it home
    queryClient.setQueryData(queryKeys.me, mockUser)

    await router.push('/login?redirect=/timesheets')

    expect(router.currentRoute.value.name).toBe('login')
    expect(queryClient.getQueryData(queryKeys.me)).toBeUndefined()
  })

  it('sends a logged-in user away from the login page', async () => {
    startMockSession()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('absences')
  })

  it('cancels the navigation when the backend fails, instead of logging the user out', async () => {
    server.use(http.get('*/api/me', () => new HttpResponse(null, { status: 503 })))

    await expect(router.push('/timesheets')).rejects.toMatchObject({ status: 503 })
    expect(router.currentRoute.value.name).not.toBe('login')
  })
})
