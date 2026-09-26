import { http, HttpResponse } from 'msw'

import type {
  AbsenceBalance,
  AbsenceDecision,
  AbsenceRequest,
  AbsenceStatus,
  AbsenceType,
  AppNotification,
  CurrentUser,
  ExportTemplate,
  Hello,
  LoginRequest,
  NewAbsenceRequest,
  NotificationPage,
  Problem,
  Project,
  ProjectHours,
  PublicHoliday,
  TeamAbsence,
  TeamAbsenceRequest,
  TeamMemberAbsences,
  TeamTimesheet,
  TimeEntry,
  Timesheet,
  TimesheetDecision,
  TimesheetEntries,
  TimesheetMonth,
  TimesheetStatus,
  UnreadCount,
} from '@/api/client'
import { workingDays } from '@/absences/workingDays'
import { addDays, daysInMonth, isIsoDate, today, weekStartOf, weekday } from '@/format/dates'
import { createAdminHandlers, resetAdminMock } from './adminHandlers'
import { absenceRequests, absenceTypes, balances, publicHolidays } from './data/absences'
import { exportTemplates, XLSX_TYPE } from './data/exports'
import { mockNotifications } from './data/notifications'
import { teamAbsenceRequests, teamCalendarAbsences } from './data/team'
import { projects, teamTimesheets, timesheets, type StoredTimesheet } from './data/timesheets'
import { findMockUser, mockTeamLeads, mockUsers } from './data/users'

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

// Timesheets per user and week (T-6.1), keyed `userId|weekStart`: Ana's own weeks and the ones
// her team submitted to her (T-7.1). Every other week is a lazy draft.
const ANA_ID = 2
const seedTimesheets = () =>
  new Map([
    ...Object.values(timesheets).map(
      (t) => [`${ANA_ID}|${t.weekStart}`, structuredClone(t)] as const,
    ),
    ...teamTimesheets.map(
      ({ userId, sheet }) => [`${userId}|${sheet.weekStart}`, structuredClone(sheet)] as const,
    ),
  ])
let myTimesheets: Map<string, StoredTimesheet> = seedTimesheets()
let nextTimesheetId = 100
let nextEntryId = 1000

/** Store a week for the logged-in user (tests) */
export function setMockTimesheet(userId: number, timesheet: StoredTimesheet) {
  myTimesheets.set(`${userId}|${timesheet.weekStart}`, structuredClone(timesheet))
}

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
  myTimesheets = seedTimesheets()
  resetAdminMock()
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
  ...createAdminHandlers({
    current: () => loggedInAs,
    replace: (user) => (loggedInAs = user),
  }),

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

  // T-5.3: my row first, then the people I lead by name; only pending and approved absences that
  // overlap the range, without reason or comments (decision 36)
  http.get<never, never, TeamMemberAbsences[] | Problem>('*/api/team/absences', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/team/absences')
    }
    const params = new URL(request.url).searchParams
    const from = params.get('from') ?? ''
    const to = params.get('to') ?? ''
    if (!isIsoDate(from)) {
      return invalid('from', 'must be a date')
    }
    if (!isIsoDate(to)) {
      return invalid('to', 'must be a date')
    }
    if (to < from) {
      return invalid('to', 'must not be before from')
    }
    if (addDays(from, 366) < to) {
      return invalid('to', 'the range must not be longer than 366 days')
    }
    const me = loggedInAs
    const inRange = (a: TeamAbsence) =>
      (a.status === 'PENDING' || a.status === 'APPROVED') && a.startDate <= to && a.endDate >= from
    const view = ({ id, type, startDate, endDate, startPart, endPart, status }: TeamAbsence) =>
      ({ id, type, startDate, endDate, startPart, endPart, status }) satisfies TeamAbsence
    const byStart = (a: TeamAbsence, b: TeamAbsence) => a.startDate.localeCompare(b.startDate)
    // The mock's "my requests" are the caller's own, whoever is logged in
    const mine = requests.filter(inRange).map(view).sort(byStart)
    const team = mockUsers
      .filter((u) => mockTeamLeads[u.id] === me.id)
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((u) => ({
        user: { id: u.id, name: u.name },
        absences: [
          ...teamCalendarAbsences.filter((t) => t.userId === u.id).map((t) => t.absence),
          ...teamRequests.filter((r) => r.requester.id === u.id).map((r) => r.request),
        ]
          .filter(inRange)
          .map(view)
          .sort(byStart),
      }))
    return HttpResponse.json([{ user: { id: me.id, name: me.name }, absences: mine }, ...team])
  }),

  // T-6.1: ordered by code; `active` defaults to true
  http.get<never, never, Project[] | Problem>('*/api/projects', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/projects')
    }
    const active = new URL(request.url).searchParams.get('active') !== 'false'
    return HttpResponse.json(
      projects.filter((p) => p.isActive === active).sort((a, b) => a.code.localeCompare(b.code)),
    )
  }),

  // Decision 32: a week never saved is an empty DRAFT without id, and GET stores nothing
  http.get<{ weekStart: string }, never, Timesheet | Problem>(
    '*/api/me/timesheets/:weekStart',
    ({ params }) => {
      if (!loggedInAs) {
        return unauthorized(`/api/me/timesheets/${params.weekStart}`)
      }
      if (!isMonday(params.weekStart)) {
        return invalid('weekStart', 'must be a Monday')
      }
      return HttpResponse.json(timesheetView(loggedInAs.id, params.weekStart))
    },
  ),

  http.put<{ weekStart: string }, TimesheetEntries, Timesheet | Problem>(
    '*/api/me/timesheets/:weekStart/entries',
    async ({ params, request }) => {
      const { weekStart } = params
      const instance = `/api/me/timesheets/${weekStart}/entries`
      if (!loggedInAs) {
        return unauthorized(instance)
      }
      if (!isMonday(weekStart)) {
        return invalid('weekStart', 'must be a Monday')
      }
      const key = `${loggedInAs.id}|${weekStart}`
      const existing = myTimesheets.get(key)
      if (existing && (existing.status === 'SUBMITTED' || existing.status === 'APPROVED')) {
        return problem(409, {
          type: '/problems/timesheet-not-editable',
          title: 'Conflict',
          detail: `This week is ${existing.status.toLowerCase()} and can't be changed`,
          instance,
        })
      }
      const { entries } = await request.json()
      const weekEnd = addDays(weekStart, 6)
      const onSheet = new Set(existing?.entries.map((e) => e.project.id))
      const cells = new Set<string>()
      const perDay = new Map<string, number>()
      const saved: TimeEntry[] = []
      for (const [i, e] of entries.entries()) {
        const at = `entries[${i}].`
        if (e.workDate < weekStart || e.workDate > weekEnd) {
          return invalid(`${at}workDate`, `must be inside the week of ${weekStart}`)
        }
        const found = projects.find((p) => p.id === e.projectId)
        if (!found) {
          return invalid(`${at}projectId`, 'no such project')
        }
        if (!found.isActive && !onSheet.has(found.id)) {
          return invalid(`${at}projectId`, `project ${found.code} is inactive`)
        }
        if (!(e.hours > 0 && e.hours <= 24)) {
          return invalid(`${at}hours`, 'must be more than 0 and at most 24')
        }
        if (!Number.isInteger(e.hours * 4)) {
          return invalid(`${at}hours`, 'must be in quarter hours')
        }
        if ((e.description?.length ?? 0) > 500) {
          return invalid(`${at}description`, 'size must be between 0 and 500')
        }
        if (cells.has(`${e.projectId}|${e.workDate}`)) {
          return invalid(`${at}workDate`, `a second entry for ${found.code} on ${e.workDate}`)
        }
        cells.add(`${e.projectId}|${e.workDate}`)
        perDay.set(e.workDate, (perDay.get(e.workDate) ?? 0) + e.hours)
        const { id, code, name } = found
        saved.push({
          id: nextEntryId++,
          project: { id, code, name },
          workDate: e.workDate,
          hours: e.hours,
          ...(e.description ? { description: e.description } : {}),
        })
      }
      const tooLong = [...perDay].find(([, hours]) => hours > 24)
      if (tooLong) {
        return invalid('entries', `more than 24 hours on ${tooLong[0]}`)
      }
      // Ordered by project code, then day, as the contract says
      saved.sort(
        (a, b) =>
          a.project.code.localeCompare(b.project.code) || a.workDate.localeCompare(b.workDate),
      )
      myTimesheets.set(key, {
        ...(existing ?? { id: nextTimesheetId++, weekStart, status: 'DRAFT' }),
        entries: saved,
      })
      return HttpResponse.json(timesheetView(loggedInAs.id, weekStart))
    },
  ),

  // BE-6.4: DRAFT or REJECTED → SUBMITTED, with the approver of decision 16
  http.post<{ weekStart: string }, never, Timesheet | Problem>(
    '*/api/me/timesheets/:weekStart/submit',
    ({ params }) => {
      const { weekStart } = params
      const instance = `/api/me/timesheets/${weekStart}/submit`
      if (!loggedInAs) {
        return unauthorized(instance)
      }
      if (!isMonday(weekStart)) {
        return invalid('weekStart', 'must be a Monday')
      }
      const key = `${loggedInAs.id}|${weekStart}`
      const existing = myTimesheets.get(key)
      if (existing && (existing.status === 'SUBMITTED' || existing.status === 'APPROVED')) {
        return problem(409, {
          type: '/problems/timesheet-not-editable',
          title: 'Conflict',
          detail: `This week is ${existing.status.toLowerCase()} and can't be changed`,
          instance,
        })
      }
      const approver = approverFor(loggedInAs.id)
      if (!approver) {
        return problem(409, {
          type: '/problems/no-approver',
          title: 'Conflict',
          detail:
            'Nobody can approve this timesheet: you have no team lead and there is no other admin',
          instance,
        })
      }
      // Submitting a lazy draft stores it; a new submit clears the last rejection
      const base: StoredTimesheet = existing
        ? { ...existing }
        : { id: nextTimesheetId++, weekStart, status: 'DRAFT', entries: [] }
      delete base.decisionComment
      delete base.decidedAt
      myTimesheets.set(key, {
        ...base,
        status: 'SUBMITTED',
        approver,
        submittedAt: new Date().toISOString(),
      })
      return HttpResponse.json(timesheetView(loggedInAs.id, weekStart))
    },
  ),

  // T-7.1: the weeks whose approver is the caller, so anyone else gets an empty list
  http.get<never, never, TeamTimesheet[] | Problem>('*/api/team/timesheets', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/team/timesheets')
    }
    const status = (new URL(request.url).searchParams.get('status') ??
      'SUBMITTED') as TimesheetStatus
    const me = loggedInAs.id
    const mine = [...myTimesheets.entries()]
      .filter(([, t]) => t.approver?.id === me && t.status === status)
      .map(([key, t]) => ({ userId: Number(key.split('|')[0]), t }))
      // Submitted ones oldest week first (they waited longest), the others newest first
      .sort((a, b) => {
        const order = a.t.weekStart.localeCompare(b.t.weekStart) || a.t.id! - b.t.id!
        return status === 'SUBMITTED' ? order : -order
      })
    return HttpResponse.json(mine.map(({ userId, t }) => teamTimesheetView(userId, t.weekStart)))
  }),

  // BE-7.2: the same order of checks as the backend: 400 (reject without comment), 404, 403, 409
  http.post<{ id: string; decision: string }, TimesheetDecision | null, Timesheet | Problem>(
    '*/api/team/timesheets/:id/:decision',
    async ({ params, request }) => {
      const instance = `/api/team/timesheets/${params.id}/${params.decision}`
      if (!loggedInAs) {
        return unauthorized(instance)
      }
      if (params.decision !== 'approve' && params.decision !== 'reject') {
        return problem(404, { type: 'about:blank', title: 'Not Found', instance })
      }
      // The approve body is optional
      const comment = ((await request.json().catch(() => null)) ?? {}).comment?.trim()
      if (params.decision === 'reject' && !comment) {
        return invalid('comment', 'must not be blank')
      }
      if ((comment?.length ?? 0) > 500) {
        return invalid('comment', 'size must be between 0 and 500')
      }
      const found = [...myTimesheets.entries()].find(([, t]) => t.id === Number(params.id))
      if (!found) {
        return problem(404, {
          type: 'about:blank',
          title: 'Not Found',
          detail: 'Timesheet not found',
          instance,
        })
      }
      const [key, sheet] = found
      const userId = Number(key.split('|')[0])
      const mayDecide = sheet.approver?.id === loggedInAs.id || loggedInAs.isAdmin
      if (userId === loggedInAs.id || !mayDecide) {
        return problem(403, {
          type: 'about:blank',
          title: 'Forbidden',
          detail: 'You may not decide this timesheet',
          instance,
        })
      }
      if (sheet.status !== 'SUBMITTED') {
        return problem(409, {
          type: '/problems/timesheet-not-submitted',
          title: 'Conflict',
          detail: `Only submitted timesheets can be decided; this one is ${sheet.status.toLowerCase()}`,
          instance,
        })
      }
      sheet.status = params.decision === 'approve' ? 'APPROVED' : 'REJECTED'
      sheet.decidedAt = new Date().toISOString()
      if (comment) {
        sheet.decisionComment = comment
      } else {
        delete sheet.decisionComment
      }
      return HttpResponse.json(timesheetView(userId, sheet.weekStart))
    },
  ),

  // BE-8.1: ordered by name
  http.get<never, never, ExportTemplate[] | Problem>('*/api/export-templates', () => {
    if (!loggedInAs) {
      return unauthorized('/api/export-templates')
    }
    return HttpResponse.json(exportTemplates)
  }),

  // BE-8.2: every week that touches the month; a week never saved is a DRAFT with 0 hours
  http.get<{ month: string }, never, TimesheetMonth | Problem>(
    '*/api/me/timesheet-months/:month',
    ({ params }) => {
      if (!loggedInAs) {
        return unauthorized(`/api/me/timesheet-months/${params.month}`)
      }
      if (!isMonth(params.month)) {
        return invalid('month', 'must match YYYY-MM')
      }
      return HttpResponse.json(timesheetMonth(loggedInAs.id, params.month))
    },
  ),

  // BE-8.2: the real backend builds the workbook with Apache POI. The mock sends a few placeholder
  // bytes with the contract's headers: enough to test the download, but Excel won't open it.
  http.get('*/api/me/timesheet-exports', ({ request }) => {
    if (!loggedInAs) {
      return unauthorized('/api/me/timesheet-exports')
    }
    const query = new URL(request.url).searchParams
    const month = query.get('month') ?? ''
    const template = query.get('template') ?? ''
    if (!isMonth(month)) {
      return invalid('month', 'must match YYYY-MM')
    }
    if (!exportTemplates.some((t) => t.code === template)) {
      return invalid('template', `unknown template ${template}`)
    }
    const login = loggedInAs.email.split('@')[0]
    return new HttpResponse(`Mock export of ${month} (${template})`, {
      headers: {
        'Content-Type': XLSX_TYPE,
        'Content-Disposition': `attachment; filename="timesheet-${month}-${login}-${template}.xlsx"`,
      },
    })
  }),

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

// The team leads of the dev seed (test-users.md). Without one, the admin approves (decision 16);
// the admin has nobody.
function approverFor(userId: number) {
  const ADMIN_ID = 1
  const leadId = mockTeamLeads[userId] ?? (userId === ADMIN_ID ? undefined : ADMIN_ID)
  const lead = mockUsers.find((u) => u.id === leadId)
  return lead ? { id: lead.id, name: lead.name } : undefined
}

const isMonday = (iso: string) => /^\d{4}-\d{2}-\d{2}$/.test(iso) && weekday(iso) === 0

const isMonth = (value: string) => /^\d{4}-(0[1-9]|1[0-2])$/.test(value)

/** What an export of the month would contain (BE-8.2) */
function timesheetMonth(userId: number, month: string): TimesheetMonth {
  const first = `${month}-01`
  const last = `${month}-${String(daysInMonth(first)).padStart(2, '0')}`
  const weeks = []
  for (let weekStart = weekStartOf(first); weekStart <= last; weekStart = addDays(weekStart, 7)) {
    const sheet = myTimesheets.get(`${userId}|${weekStart}`)
    const hoursInMonth = (sheet?.entries ?? [])
      .filter((e) => e.workDate >= first && e.workDate <= last)
      .reduce((sum, e) => sum + e.hours, 0)
    weeks.push({ weekStart, status: sheet?.status ?? ('DRAFT' as const), hoursInMonth })
  }
  return { month, totalHours: weeks.reduce((sum, w) => sum + w.hoursInMonth, 0), weeks }
}

/** The week as GET returns it: the stored one or an empty draft, plus absences and holidays */
function timesheetView(userId: number, weekStart: string): Timesheet {
  const weekEnd = addDays(weekStart, 6)
  const sheet = myTimesheets.get(`${userId}|${weekStart}`) ?? {
    weekStart,
    status: 'DRAFT' as const,
    entries: [],
  }
  const years = new Set([weekStart.slice(0, 4), weekEnd.slice(0, 4)].map(Number))
  return {
    ...structuredClone(sheet),
    totalHours: sheet.entries.reduce((sum, e) => sum + e.hours, 0),
    // The mock only has Ana's absences
    absences:
      userId === ANA_ID
        ? requests.filter(
            (r) => r.status === 'APPROVED' && r.startDate <= weekEnd && r.endDate >= weekStart,
          )
        : [],
    holidays: [...years]
      .flatMap((year) => publicHolidays[year] ?? [])
      .filter((h) => h.date >= weekStart && h.date <= weekEnd),
  }
}

/** A week as its approver sees it (T-7.1): the whole week plus hours per project, by code */
function teamTimesheetView(userId: number, weekStart: string): TeamTimesheet {
  const timesheet = timesheetView(userId, weekStart)
  const user = mockUsers.find((u) => u.id === userId)!
  const byProject = new Map<number, ProjectHours>()
  for (const e of timesheet.entries) {
    const hours = (byProject.get(e.project.id)?.hours ?? 0) + e.hours
    byProject.set(e.project.id, { project: e.project, hours })
  }
  return {
    timesheet,
    user: { id: user.id, name: user.name },
    projectHours: [...byProject.values()],
  }
}
