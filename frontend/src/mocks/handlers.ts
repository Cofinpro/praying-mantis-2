import { http, HttpResponse } from 'msw'

import type {
  AbsenceBalance,
  AbsenceDecision,
  AbsenceRequest,
  AbsenceStatus,
  AbsenceType,
  AppNotification,
  CurrentUser,
  Hello,
  LoginRequest,
  NewAbsenceRequest,
  NotificationPage,
  Problem,
  PublicHoliday,
  TeamAbsenceRequest,
  UnreadCount,
} from '@/api/client'
import { workingDays } from '@/absences/workingDays'
import { today } from '@/format/dates'
import { absenceRequests, absenceTypes, balances, publicHolidays } from './data/absences'
import { mockNotifications } from './data/notifications'
import { teamAbsenceRequests } from './data/team'
import { findMockUser } from './data/users'

// Mock backend that follows api/openapi.yaml (decision #4). Used by `pnpm dev:mock` and by Vitest.
// Paths are wildcards so they match both the dev origin and the jsdom origin in tests.

/** The dev seed's password, so the mock and the real backend take the same logins */
export const MOCK_PASSWORD = 'password'

/** Ana Silva, a team lead: the default user of startMockSession() in tests */
export const mockUser: CurrentUser = findMockUser('ana.silva@cofinpro.pt')!

// A fake server session: login starts it, logout ends it, GET /me answers 401 without it.
// It lives in memory, so reloading the page in `pnpm dev:mock` logs you out.
let loggedInAs: CurrentUser | null = null

/** Log in without going through the form (tests) */
export function startMockSession(user: CurrentUser = mockUser) {
  loggedInAs = user
}

// Requests live in memory too, so creating and cancelling show up in the calendar
let requests: AbsenceRequest[] = structuredClone(absenceRequests)
let nextRequestId = 1000
// The requests Ana decides on as team lead (T-5.1), so approving and rejecting stick
let teamRequests: TeamAbsenceRequest[] = structuredClone(teamAbsenceRequests)

// Notifications too, so marking them read changes the badge. Built lazily, so their "2 min ago" is
// relative to the (possibly faked) clock of the first request.
let notifications: AppNotification[] | null = null
const myNotifications = () => (notifications ??= mockNotifications())

/** Replace the logged-in user's notifications (tests) */
export function setMockNotifications(list: AppNotification[]) {
  notifications = structuredClone(list)
}

/** Called after every test by src/test/setup.ts */
export function resetMockSession() {
  loggedInAs = null
  requests = structuredClone(absenceRequests)
  notifications = null
  teamRequests = structuredClone(teamAbsenceRequests)
}

const problem = (status: number, body: Omit<Problem, 'status'>) =>
  HttpResponse.json<Problem>(
    { status, ...body },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )

const conflict = (type: string, detail: string) =>
  problem(409, { type, title: 'Conflict', detail, instance: '/api/me/absence-requests' })

const invalid = (field: string, message: string) =>
  problem(400, {
    type: 'about:blank',
    title: 'Bad Request',
    detail: 'Request has invalid fields',
    errors: [{ field, message }],
  })

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
    const user = findMockUser(email)
    if (!user || password !== MOCK_PASSWORD) {
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
    loggedInAs = user
    return HttpResponse.json(user)
  }),

  // 204 also without a session, as in the contract
  http.post('*/api/auth/logout', () => {
    loggedInAs = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.get<never, never, CurrentUser | Problem>('*/api/me', () =>
    loggedInAs ? HttpResponse.json(loggedInAs) : unauthorized('/api/me'),
  ),

  http.get<never, never, AbsenceType[] | Problem>('*/api/absence-types', () =>
    loggedInAs ? HttpResponse.json(absenceTypes) : unauthorized('/api/absence-types'),
  ),

  http.get<never, never, AbsenceBalance[] | Problem>('*/api/me/absence-balance', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/me/absence-balance')
    }
    return HttpResponse.json(balances[yearParam(request)] ?? [])
  }),

  http.get<never, never, AbsenceRequest[] | Problem>('*/api/me/absence-requests', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/me/absence-requests')
    }
    const params = new URL(request.url).searchParams
    const from = params.get('from') ?? ''
    const to = params.get('to') ?? ''
    // Overlaps [from, to]; ISO dates compare correctly as strings
    return HttpResponse.json(requests.filter((r) => r.startDate <= to && r.endDate >= from))
  }),

  // The rules of the contract (T-3.1): field errors, overlap, balance, auto-approved sick leave
  http.post<never, NewAbsenceRequest, AbsenceRequest | Problem>(
    '*/api/me/absence-requests',
    async ({ request }) => {
      if (!loggedInAs) {
        return unauthorized('/api/me/absence-requests')
      }
      const body = await request.json()
      if (body.endDate < body.startDate) {
        return invalid('endDate', 'must not be before startDate')
      }
      const holidays = new Set(
        [Number(body.startDate.slice(0, 4)), Number(body.endDate.slice(0, 4))]
          .flatMap((year) => publicHolidays[year] ?? [])
          .map((h) => h.date),
      )
      const days = workingDays(body, holidays)
      if (days === 0) {
        return invalid('endDate', 'the range has no working days')
      }
      const overlaps = requests.some(
        (r) =>
          (r.status === 'PENDING' || r.status === 'APPROVED') &&
          r.startDate <= body.endDate &&
          r.endDate >= body.startDate,
      )
      if (overlaps) {
        return conflict('/problems/absence-overlap', 'The request overlaps another absence')
      }
      const year = Number(body.startDate.slice(0, 4))
      const left = balances[year]?.find((b) => b.type === body.type)?.remainingDays
      if (body.type === 'VACATION' && (left ?? 0) < days) {
        return conflict(
          '/problems/insufficient-balance',
          `Only ${left ?? 0} vacation days left in ${year}, but the request needs ${days}`,
        )
      }
      const autoApproved = body.type === 'SICK'
      const created: AbsenceRequest = {
        ...body,
        id: nextRequestId++,
        workingDays: days,
        status: autoApproved ? 'APPROVED' : 'PENDING',
        ...(autoApproved ? {} : { approver: { id: 1, name: 'Alex Admin' } }),
        createdAt: new Date().toISOString(),
      }
      requests.push(created)
      return HttpResponse.json(created, { status: 201 })
    },
  ),

  http.post<{ id: string }, never, AbsenceRequest | Problem>(
    '*/api/me/absence-requests/:id/cancel',
    ({ params }) => {
      if (!loggedInAs) {
        return unauthorized(`/api/me/absence-requests/${params.id}/cancel`)
      }
      const found = requests.find((r) => r.id === Number(params.id))
      if (!found) {
        return problem(404, {
          type: 'about:blank',
          title: 'Not Found',
          detail: 'Absence request not found',
        })
      }
      const started = found.status === 'APPROVED' && found.startDate <= today()
      if (found.status === 'REJECTED' || found.status === 'CANCELLED' || started) {
        return conflict(
          '/problems/absence-not-cancellable',
          'The absence can no longer be cancelled',
        )
      }
      found.status = 'CANCELLED'
      return HttpResponse.json(found)
    },
  ),

  // Contract T-4.1: newest first, `limit` (default 20, max 100) and a `before` cursor on the id
  http.get<never, never, NotificationPage | Problem>('*/api/me/notifications', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/me/notifications')
    }
    const params = new URL(request.url).searchParams
    const limit = Number(params.get('limit') ?? 20)
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
      return invalid('limit', 'must be between 1 and 100')
    }
    const before = params.get('before')
    const matching = myNotifications()
      .filter((n) => params.get('unread') !== 'true' || n.readAt == null)
      .filter((n) => before === null || n.id < Number(before))
      .sort((a, b) => b.id - a.id)
    return HttpResponse.json({
      items: matching.slice(0, limit),
      hasMore: matching.length > limit,
    })
  }),

  http.get<never, never, UnreadCount | Problem>('*/api/me/notifications/unread-count', () =>
    loggedInAs
      ? HttpResponse.json({ count: myNotifications().filter((n) => n.readAt == null).length })
      : unauthorized('/api/me/notifications/unread-count'),
  ),

  // Idempotent: an already read notification keeps its first readAt
  http.post<{ id: string }, never, Problem>('*/api/me/notifications/:id/read', ({ params }) => {
    if (!loggedInAs) {
      return unauthorized(`/api/me/notifications/${params.id}/read`)
    }
    const found = myNotifications().find((n) => n.id === Number(params.id))
    if (!found) {
      return problem(404, {
        type: 'about:blank',
        title: 'Not Found',
        detail: 'Notification not found',
      })
    }
    found.readAt ??= new Date().toISOString()
    return new HttpResponse(null, { status: 204 })
  }),

  http.post<never, never, Problem>('*/api/me/notifications/read-all', () => {
    if (!loggedInAs) {
      return unauthorized('/api/me/notifications/read-all')
    }
    const now = new Date().toISOString()
    myNotifications().forEach((n) => (n.readAt ??= now))
    return new HttpResponse(null, { status: 204 })
  }),

  // T-5.1: only the requests whose approver is the caller, so anyone else gets an empty list
  http.get<never, never, TeamAbsenceRequest[] | Problem>(
    '*/api/team/absence-requests',
    ({ request }) => {
      if (!loggedInAs) {
        return unauthorized('/api/team/absence-requests')
      }
      const status = (new URL(request.url).searchParams.get('status') ?? 'PENDING') as AbsenceStatus
      const me = loggedInAs.id
      const mine = teamRequests.filter((r) => r.request.approver?.id === me)
      // Pending ones soonest start first; ISO dates compare correctly as strings
      return HttpResponse.json(
        mine
          .filter((r) => r.request.status === status)
          .sort((a, b) => a.request.startDate.localeCompare(b.request.startDate)),
      )
    },
  ),

  http.post<{ id: string; decision: string }, AbsenceDecision | null, AbsenceRequest | Problem>(
    '*/api/team/absence-requests/:id/:decision',
    async ({ params, request }) => {
      const instance = `/api/team/absence-requests/${params.id}/${params.decision}`
      if (!loggedInAs) {
        return unauthorized(instance)
      }
      if (params.decision !== 'approve' && params.decision !== 'reject') {
        return problem(404, { type: 'about:blank', title: 'Not Found', instance })
      }
      // The approve body is optional
      const comment = ((await request.json().catch(() => null)) ?? {}).comment?.trim()
      const found = teamRequests.find((r) => r.request.id === Number(params.id))
      if (!found) {
        return problem(404, {
          type: 'about:blank',
          title: 'Not Found',
          detail: 'Absence request not found',
          instance,
        })
      }
      if (found.request.status !== 'PENDING') {
        return problem(409, {
          type: '/problems/absence-not-pending',
          title: 'Conflict',
          detail: 'The absence request is no longer pending',
          instance,
        })
      }
      if (params.decision === 'reject' && !comment) {
        return invalid('comment', 'must not be blank')
      }
      if ((comment?.length ?? 0) > 500) {
        return invalid('comment', 'size must be between 0 and 500')
      }
      const { request: r, remainingDays } = found
      if (params.decision === 'approve' && remainingDays != null) {
        if (remainingDays < r.workingDays) {
          return problem(409, {
            type: '/problems/insufficient-balance',
            title: 'Conflict',
            detail: `Only ${remainingDays} vacation days left in ${r.startDate.slice(0, 4)}, but the request needs ${r.workingDays}`,
            instance,
          })
        }
        // The approved days now count as used for the requester's other requests of that type
        for (const other of teamRequests) {
          if (other.requester.id === found.requester.id && other.request.type === r.type) {
            other.remainingDays = (other.remainingDays ?? 0) - r.workingDays
          }
        }
      }
      r.status = params.decision === 'approve' ? 'APPROVED' : 'REJECTED'
      r.decidedAt = new Date().toISOString()
      if (comment) {
        r.decisionComment = comment
      }
      return HttpResponse.json(r)
    },
  ),

  http.get<never, never, PublicHoliday[] | Problem>('*/api/public-holidays', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/public-holidays')
    }
    return HttpResponse.json(publicHolidays[yearParam(request)] ?? [])
  }),
]

/** `?year=` or the current year, as the contract says */
function yearParam(request: Request): number {
  return Number(new URL(request.url).searchParams.get('year') ?? new Date().getFullYear())
}
