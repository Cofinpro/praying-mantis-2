import createClient, { type Middleware } from 'openapi-fetch'

import router from '@/router'
import { queryClient } from './queryClient'
import { queryKeys } from './queryKeys'
import type { components, paths } from './generated/openapi'

// The only module that talks to the backend (decision #6). Types come from api/openapi.yaml via
// `pnpm gen:api` (decision #3), so a path, parameter or body that isn't in the contract won't compile.

export type Problem = components['schemas']['Problem']
export type Hello = components['schemas']['Hello']
export type LoginRequest = components['schemas']['LoginRequest']
export type CurrentUser = components['schemas']['CurrentUser']
export type Client = components['schemas']['Client']
export type Level = components['schemas']['Level']
export type AbsenceTypeCode = components['schemas']['AbsenceTypeCode']
export type AbsenceType = components['schemas']['AbsenceType']
export type AbsenceBalance = components['schemas']['AbsenceBalance']
export type AbsenceRequest = components['schemas']['AbsenceRequest']
export type AbsenceStatus = components['schemas']['AbsenceStatus']
export type DayPart = components['schemas']['DayPart']
export type PublicHoliday = components['schemas']['PublicHoliday']
export type NewAbsenceRequest = components['schemas']['NewAbsenceRequest']
// Not `Notification`: that name is taken by the browser's Notification API
export type AppNotification = components['schemas']['Notification']
export type NotificationType = components['schemas']['NotificationType']
export type NotificationPage = components['schemas']['NotificationPage']
export type UnreadCount = components['schemas']['UnreadCount']
export type NotificationsQuery = NonNullable<
  paths['/me/notifications']['get']['parameters']['query']
>
export type TeamAbsenceRequest = components['schemas']['TeamAbsenceRequest']
export type AbsenceDecision = components['schemas']['AbsenceDecision']
export type UserRef = components['schemas']['UserRef']

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

// On 401 the session is gone: forget the cached user, so the route guard asks the server again,
// then send the user to the login page and remember where they were (FE-1.1 sends them back).
//
// Hands off GET /me: the route guard (router/authGuard.ts) calls it during a navigation and handles
// its 401 itself. From here, a redirect would start a second navigation that cancels the guard's,
// and removing the query would cancel the guard's own in-flight fetch (CancelledError).
const redirectOnUnauthorized: Middleware = {
  // openapi-fetch awaits middleware, so the navigation has finished by the time the caller sees the error
  async onResponse({ response, schemaPath }) {
    // schemaPath is the path template from openapi.yaml, not the URL, so the base path can't fool it
    if (response.status !== 401 || schemaPath === '/me') {
      return
    }
    queryClient.removeQueries({ queryKey: queryKeys.me })
    const current = router.currentRoute.value
    if (current.name !== 'login') {
      await router.push({ name: 'login', query: { redirect: current.fullPath } })
    }
  },
}

// CSRF (openapi.yaml): the backend sets a readable XSRF-TOKEN cookie, and every unsafe request has
// to send it back in the X-XSRF-TOKEN header, or it gets a 403. A cross-site page can make the
// browser send our cookies, but it can't read them, so it can't copy the token into a header.
// Read the cookie on every request: it changes on login.
const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const sendCsrfToken: Middleware = {
  onRequest({ request }) {
    const token = readCookie('XSRF-TOKEN')
    if (token && UNSAFE_METHODS.has(request.method)) {
      request.headers.set('X-XSRF-TOKEN', token)
    }
    return request
  },
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const cookie = document.cookie.split('; ').find((c) => c.startsWith(prefix))
  return cookie === undefined ? undefined : decodeURIComponent(cookie.slice(prefix.length))
}

const client = createClient<paths>({
  // Absolute URL: the Request constructor rejects relative URLs outside a browser (Vitest)
  baseUrl: new URL('/api', window.location.origin).href,
  // Look up fetch on every call. openapi-fetch would otherwise keep the fetch that existed at import
  // time, and MSW in Vitest only patches globalThis.fetch later, in server.listen()
  fetch: (request) => globalThis.fetch(request),
})
client.use(sendCsrfToken, redirectOnUnauthorized)

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
  logout: () => unwrap(client.POST('/auth/logout')),
  getMe: () => unwrap(client.GET('/me')),
  getAbsenceTypes: () => unwrap(client.GET('/absence-types')),
  getMyAbsenceBalance: (year: number) =>
    unwrap(client.GET('/me/absence-balance', { params: { query: { year } } })),
  /** `from` and `to` are inclusive `YYYY-MM-DD` dates */
  getMyAbsenceRequests: (from: string, to: string) =>
    unwrap(client.GET('/me/absence-requests', { params: { query: { from, to } } })),
  createMyAbsenceRequest: (body: NewAbsenceRequest) =>
    unwrap(client.POST('/me/absence-requests', { body })),
  cancelMyAbsenceRequest: (id: number) =>
    unwrap(client.POST('/me/absence-requests/{id}/cancel', { params: { path: { id } } })),
  /** Requests I'm the approver of (T-5.1). The backend defaults `status` to PENDING. */
  getTeamAbsenceRequests: (status?: AbsenceStatus) =>
    unwrap(client.GET('/team/absence-requests', { params: { query: { status } } })),
  approveAbsenceRequest: (id: number, body: AbsenceDecision = {}) =>
    unwrap(client.POST('/team/absence-requests/{id}/approve', { params: { path: { id } }, body })),
  /** The comment is required to reject (decision 31) */
  rejectAbsenceRequest: (id: number, body: AbsenceDecision) =>
    unwrap(client.POST('/team/absence-requests/{id}/reject', { params: { path: { id } }, body })),
  getPublicHolidays: (year: number) =>
    unwrap(client.GET('/public-holidays', { params: { query: { year } } })),
  /** Newest first. `before` is the `id` of the last item you have, for the next page. */
  getMyNotifications: (query: NotificationsQuery = {}) =>
    unwrap(client.GET('/me/notifications', { params: { query } })),
  getMyUnreadNotificationCount: () => unwrap(client.GET('/me/notifications/unread-count')),
  markMyNotificationRead: (id: number) =>
    unwrap(client.POST('/me/notifications/{id}/read', { params: { path: { id } } })),
  markAllMyNotificationsRead: () => unwrap(client.POST('/me/notifications/read-all')),
}
