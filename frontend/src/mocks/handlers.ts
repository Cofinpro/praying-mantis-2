import { http, HttpResponse } from 'msw'

import type { CurrentUser, Hello, LoginRequest, Problem } from '@/api/client'

// Mock backend that follows api/openapi.yaml (decision #4). Used by `pnpm dev:mock` and by Vitest.
// Paths are wildcards so they match both the dev origin and the jsdom origin in tests.

/** Any email logs in with this password in the mock. */
export const MOCK_PASSWORD = 'secret'

export const mockUser: CurrentUser = {
  id: 7,
  name: 'Ana Silva',
  email: 'ana.silva@cofinpro.pt',
  client: 'DKB',
  level: 'EXPERT',
  isAdmin: false,
  isTeamLead: true,
}

// A fake server session: login starts it, logout ends it, GET /me answers 401 without it.
// It lives in memory, so reloading the page in `pnpm dev:mock` logs you out.
let loggedInAs: CurrentUser | null = null

/** Log in without going through the form (tests) */
export function startMockSession(user: CurrentUser = mockUser) {
  loggedInAs = user
}

/** Called after every test by src/test/setup.ts */
export function resetMockSession() {
  loggedInAs = null
}

const unauthorized = (instance: string) =>
  HttpResponse.json<Problem>(
    { type: 'about:blank', title: 'Unauthorized', status: 401, instance },
    { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
  )

export const handlers = [
  http.get<never, never, Hello>('*/api/hello', () =>
    HttpResponse.json({ message: 'Hello from the MSW mock' }),
  ),

  http.post<never, LoginRequest, CurrentUser | Problem>('*/api/auth/login', async ({ request }) => {
    const { email, password } = await request.json()
    const blank = (['email', 'password'] as const).filter((field) => !{ email, password }[field])
    if (blank.length > 0) {
      return HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: 'Request has invalid fields',
          instance: '/api/auth/login',
          errors: blank.map((field) => ({ field, message: 'must not be blank' })),
        },
        { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
      )
    }
    if (password !== MOCK_PASSWORD) {
      // Same detail for an unknown email and a wrong password, as in the contract
      return HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Unauthorized',
          status: 401,
          detail: 'Invalid email or password',
          instance: '/api/auth/login',
        },
        { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
      )
    }
    loggedInAs = { ...mockUser, email }
    return HttpResponse.json(loggedInAs)
  }),

  // 204 also without a session, as in the contract
  http.post('*/api/auth/logout', () => {
    loggedInAs = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.get<never, never, CurrentUser | Problem>('*/api/me', () =>
    loggedInAs ? HttpResponse.json(loggedInAs) : unauthorized('/api/me'),
  ),
]
