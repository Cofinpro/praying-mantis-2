import createClient, { type Middleware } from 'openapi-fetch'

import router from '@/router'
import type { components, paths } from './generated/openapi'

// The only module that talks to the backend (decision #6). Types come from api/openapi.yaml via
// `pnpm gen:api` (decision #3), so a path, parameter or body that isn't in the contract won't compile.

export type Problem = components['schemas']['Problem']
export type Hello = components['schemas']['Hello']
export type LoginRequest = components['schemas']['LoginRequest']
export type CurrentUser = components['schemas']['CurrentUser']

/** Thrown for every non-2xx response. `problem` is the RFC 9457 body (decision #21). */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiError'
    this.status = problem.status
    this.problem = problem
  }
}

// On 401, send the user to the login page and remember where they were (FE-1.1 sends them back)
const redirectOnUnauthorized: Middleware = {
  // openapi-fetch awaits middleware, so the navigation has finished by the time the caller sees the error
  async onResponse({ response }) {
    const current = router.currentRoute.value
    if (response.status === 401 && current.name !== 'login') {
      await router.push({ name: 'login', query: { redirect: current.fullPath } })
    }
  },
}

const client = createClient<paths>({
  // Absolute URL: the Request constructor rejects relative URLs outside a browser (Vitest)
  baseUrl: new URL('/api', window.location.origin).href,
  // Look up fetch on every call. openapi-fetch would otherwise keep the fetch that existed at import
  // time, and MSW in Vitest only patches globalThis.fetch later, in server.listen()
  fetch: (request) => globalThis.fetch(request),
})
client.use(redirectOnUnauthorized)

// openapi-fetch returns { data, error, response } instead of throwing. TanStack Query needs a
// rejected promise to show an error, so turn every failed response into an ApiError.
async function unwrap<T>(
  request: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await request
  if (response.ok) {
    return data as T
  }
  throw new ApiError(toProblem(error, response))
}

function toProblem(body: unknown, response: Response): Problem {
  if (isProblem(body)) {
    return body
  }
  // Not every error has a Problem body yet (e.g. Spring Security's empty 401), so build one
  return {
    type: 'about:blank',
    title: response.statusText || 'Request failed',
    status: response.status,
  }
}

function isProblem(body: unknown): body is Problem {
  return typeof body === 'object' && body !== null && 'status' in body && 'title' in body
}

/** One function per operation in openapi.yaml. Use them as `queryFn` / `mutationFn`. */
export const api = {
  getHello: () => unwrap(client.GET('/hello')),
  // A wrong password is a 401 too, but the redirect middleware skips it: we're already on login
  login: (body: LoginRequest) => unwrap(client.POST('/auth/login', { body })),
}
