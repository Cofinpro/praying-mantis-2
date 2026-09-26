import { http, HttpResponse } from 'msw'

import type {
  AdminEntitlement,
  AdminUser,
  AdminUserUpdate,
  CurrentUser,
  EntitlementInput,
  NewAdminUser,
  PasswordReset,
  Problem,
  Project,
  ProjectInput,
} from '@/api/client'
import { projects } from './data/timesheets'
import { mockTeamLeads, mockUsers } from './data/users'

// The admin endpoints of api/openapi.yaml (T-9.1), in memory like the rest of the mock. Separate
// from handlers.ts, which owns the session: it passes in how to read and replace the logged-in user.

interface StoredUser {
  id: number
  name: string
  email: string
  client: AdminUser['client']
  level: AdminUser['level']
  isAdmin: boolean
  teamLeadId?: number
}

const seed = (): StoredUser[] =>
  mockUsers.map(({ id, name, email, client, level, isAdmin }) => ({
    id,
    name,
    email,
    client,
    level,
    isAdmin,
    ...(mockTeamLeads[id] ? { teamLeadId: mockTeamLeads[id] } : {}),
  }))

let users: StoredUser[] = seed()
let nextUserId = 100

type StoredEntitlement = Omit<AdminEntitlement, 'user'> & { userId: number }

// As the backend's dev seed (0003-absence-entitlements-dev-seed): 22 vacation days for everyone in
// the current year, Ana and Carla carry days over. Carla also has training days (her balance).
const seedEntitlements = (): StoredEntitlement[] => [
  ...mockUsers.map((u) => ({
    id: u.id,
    userId: u.id,
    type: 'VACATION' as const,
    year: 2026,
    entitledDays: 22,
    carriedOverDays: u.id === 2 ? 3 : u.id === 4 ? 2.5 : 0,
  })),
  { id: 50, userId: 4, type: 'TRAINING', year: 2026, entitledDays: 5, carriedOverDays: 0 },
]

let entitlements: StoredEntitlement[] = seedEntitlements()
let nextEntitlementId = 100

// Projects live in data/timesheets.ts, shared with GET /projects and the weeks, so an admin's edit
// shows up in the timesheet too. Changed in place, and put back after every test.
const projectSeed: Project[] = structuredClone(projects)
let nextProjectId = 100

/** Called from resetMockSession() after every test */
export function resetAdminMock() {
  users = seed()
  nextUserId = 100
  entitlements = seedEntitlements()
  nextEntitlementId = 100
  projects.splice(0, projects.length, ...structuredClone(projectSeed))
  nextProjectId = 100
}

const PROJECT_CODE = /^[A-Za-z0-9-]{2,30}$/

/** The contract's checks on a project (BE-9.3), with its field names */
function projectError(body: ProjectInput) {
  if (!PROJECT_CODE.test(body.code ?? '')) {
    return invalid('code', 'must match "^[A-Za-z0-9-]{2,30}$"')
  }
  if (!body.name?.trim() || body.name.length > 255) {
    return invalid('name', 'size must be between 1 and 255')
  }
  return null
}

/** The project as stored: the code upper-cased, the name trimmed, no `client` when internal */
function projectFrom(id: number, body: ProjectInput): Project {
  return {
    id,
    code: body.code.trim().toUpperCase(),
    name: body.name.trim(),
    ...(body.client ? { client: body.client } : {}),
    isBillable: body.isBillable,
    isActive: body.isActive,
  }
}

const codeTaken = (code: string) =>
  conflict('project-code-taken', `Another project already has the code ${code}`)

function entitlementView(e: StoredEntitlement): AdminEntitlement {
  const { userId, ...rest } = e
  const user = users.find((u) => u.id === userId)!
  return { ...rest, user: { id: user.id, name: user.name } }
}

/** 0 to 366 in steps of 0.5 (contract), checked by BE-9.2 with the same field names */
function daysError(field: string, days: unknown) {
  if (typeof days !== 'number' || days < 0 || days > 366) {
    return invalid(field, 'must be between 0 and 366')
  }
  return Number.isInteger(days * 2) ? null : invalid(field, 'must be in steps of 0.5')
}

/** Ordered by name, with the team lead and the derived "is team lead" (decision #9), as BE-9.1 */
function view(u: StoredUser): AdminUser {
  const lead = users.find((l) => l.id === u.teamLeadId)
  return {
    id: u.id,
    name: u.name,
    email: u.email,
    client: u.client,
    level: u.level,
    isAdmin: u.isAdmin,
    isTeamLead: users.some((m) => m.teamLeadId === u.id),
    ...(lead ? { teamLead: { id: lead.id, name: lead.name } } : {}),
  }
}

const problem = (status: number, body: Omit<Problem, 'status'>) =>
  HttpResponse.json<Problem>(
    { status, ...body },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )

const invalid = (field: string, message: string) =>
  problem(400, {
    type: 'about:blank',
    title: 'Bad Request',
    detail: 'Request has invalid fields',
    errors: [{ field, message }],
  })

const conflict = (type: string, detail: string) =>
  problem(409, { type: `/problems/${type}`, title: 'Conflict', detail })

const EMAIL = /^[^@\s]+@[^@\s]+$/

function validate(body: AdminUserUpdate, password?: string | null) {
  if (!body.name?.trim()) return invalid('name', 'must not be blank')
  if (!EMAIL.test(body.email?.trim() ?? ''))
    return invalid('email', 'must be a well-formed email address')
  if (password !== undefined && (!password || password.length < 8 || password.length > 72)) {
    return invalid('password', 'size must be between 8 and 72')
  }
  if (body.teamLeadId !== undefined && !users.some((u) => u.id === body.teamLeadId)) {
    return invalid('teamLeadId', 'no such user')
  }
  return null
}

export function createAdminHandlers(session: {
  current: () => CurrentUser | null
  replace: (user: CurrentUser) => void
}) {
  /** 401 without a session, 403 unless the stored user is an admin (checked every call, #11) */
  function guard(instance: string) {
    const me = session.current()
    if (!me) {
      return problem(401, { type: 'about:blank', title: 'Unauthorized', instance })
    }
    if (!users.find((u) => u.id === me.id)?.isAdmin) {
      return problem(403, {
        type: 'about:blank',
        title: 'Forbidden',
        detail: 'Only admins can do this',
        instance,
      })
    }
    return null
  }

  return [
    http.get<never, never, AdminUser[] | Problem>('*/api/admin/users', () => {
      const denied = guard('/api/admin/users')
      if (denied) return denied
      return HttpResponse.json([...users].sort((a, b) => a.name.localeCompare(b.name)).map(view))
    }),

    http.post<never, NewAdminUser, AdminUser | Problem>(
      '*/api/admin/users',
      async ({ request }) => {
        const denied = guard('/api/admin/users')
        if (denied) return denied
        const body = await request.json()
        const error = validate(body, body.password ?? null)
        if (error) return error
        const email = body.email.trim().toLowerCase()
        if (users.some((u) => u.email === email)) {
          return conflict('email-taken', 'Another user already has this email')
        }
        const created: StoredUser = {
          id: nextUserId++,
          name: body.name.trim(),
          email,
          client: body.client,
          level: body.level,
          isAdmin: body.isAdmin,
          ...(body.teamLeadId ? { teamLeadId: body.teamLeadId } : {}),
        }
        users.push(created)
        return HttpResponse.json(view(created), { status: 201 })
      },
    ),

    http.put<{ id: string }, AdminUserUpdate, AdminUser | Problem>(
      '*/api/admin/users/:id',
      async ({ params, request }) => {
        const denied = guard(`/api/admin/users/${params.id}`)
        if (denied) return denied
        const user = users.find((u) => u.id === Number(params.id))
        if (!user) {
          return problem(404, { type: 'about:blank', title: 'Not Found', detail: 'User not found' })
        }
        const body = await request.json()
        const error = validate(body)
        if (error) return error
        const email = body.email.trim().toLowerCase()
        if (users.some((u) => u.email === email && u.id !== user.id)) {
          return conflict('email-taken', 'Another user already has this email')
        }
        if (body.teamLeadId === user.id) {
          return invalid('teamLeadId', "a user can't be their own team lead")
        }
        // Walk up from the new lead: reaching the user means they already lead the new lead
        const seen = new Set<number>()
        for (
          let up = users.find((u) => u.id === body.teamLeadId);
          up && !seen.has(up.id);
          up = users.find((u) => u.id === up!.teamLeadId)
        ) {
          seen.add(up.id)
          if (up.id === user.id) {
            const lead = users.find((u) => u.id === body.teamLeadId)!
            return conflict(
              'team-lead-cycle',
              `${user.name} already leads ${lead.name}, directly or through others`,
            )
          }
        }
        if (user.isAdmin && !body.isAdmin && users.filter((u) => u.isAdmin).length === 1) {
          return conflict('last-admin', 'This is the only admin; make someone else an admin first')
        }
        Object.assign(user, {
          name: body.name.trim(),
          email,
          client: body.client,
          level: body.level,
          isAdmin: body.isAdmin,
          teamLeadId: body.teamLeadId,
        })
        const saved = view(user)
        const me = session.current()
        if (me?.id === user.id) {
          // GET /me shows the change, as the real backend reads the user from the DB
          const { name, email, client, level, isAdmin, isTeamLead } = saved
          session.replace({ ...me, name, email, client, level, isAdmin, isTeamLead })
        }
        return HttpResponse.json(saved)
      },
    ),

    http.post<{ id: string }, PasswordReset, Problem>(
      '*/api/admin/users/:id/password',
      async ({ params, request }) => {
        const denied = guard(`/api/admin/users/${params.id}/password`)
        if (denied) return denied
        if (!users.some((u) => u.id === Number(params.id))) {
          return problem(404, { type: 'about:blank', title: 'Not Found', detail: 'User not found' })
        }
        const { password } = await request.json()
        if (!password || password.length < 8 || password.length > 72) {
          return invalid('password', 'size must be between 8 and 72')
        }
        // The mock keeps one password for everyone (MOCK_PASSWORD), so there's nothing to store
        return new HttpResponse(null, { status: 204 })
      },
    ),

    // Entitlements (BE-9.2): by user name, then type
    http.get<never, never, AdminEntitlement[] | Problem>(
      '*/api/admin/entitlements',
      ({ request }) => {
        const denied = guard('/api/admin/entitlements')
        if (denied) return denied
        const year = Number(new URL(request.url).searchParams.get('year'))
        if (!Number.isInteger(year) || year < 2000 || year > 2100) {
          return invalid('year', 'must be between 2000 and 2100')
        }
        return HttpResponse.json(
          entitlements
            .filter((e) => e.year === year)
            .map(entitlementView)
            .sort((a, b) => a.user.name.localeCompare(b.user.name) || a.type.localeCompare(b.type)),
        )
      },
    ),

    // An upsert on (user, type, year), decision 35
    http.put<never, EntitlementInput, AdminEntitlement | Problem>(
      '*/api/admin/entitlements',
      async ({ request }) => {
        const denied = guard('/api/admin/entitlements')
        if (denied) return denied
        const body = await request.json()
        const error =
          daysError('entitledDays', body.entitledDays) ??
          daysError('carriedOverDays', body.carriedOverDays)
        if (error) return error
        if (!users.some((u) => u.id === body.userId)) return invalid('userId', 'no such user')
        let stored = entitlements.find(
          (e) => e.userId === body.userId && e.type === body.type && e.year === body.year,
        )
        if (!stored) {
          stored = { id: nextEntitlementId++, ...body }
          entitlements.push(stored)
        }
        Object.assign(stored, {
          entitledDays: body.entitledDays,
          carriedOverDays: body.carriedOverDays,
        })
        return HttpResponse.json(entitlementView(stored))
      },
    ),

    http.delete<{ id: string }, never, Problem>('*/api/admin/entitlements/:id', ({ params }) => {
      const denied = guard(`/api/admin/entitlements/${params.id}`)
      if (denied) return denied
      if (!entitlements.some((e) => e.id === Number(params.id))) {
        return problem(404, {
          type: 'about:blank',
          title: 'Not Found',
          detail: 'Entitlement not found',
        })
      }
      entitlements = entitlements.filter((e) => e.id !== Number(params.id))
      return new HttpResponse(null, { status: 204 })
    }),

    // Projects (BE-9.3): never deleted, only deactivated (decision 35)
    http.get<never, never, Project[] | Problem>('*/api/admin/projects', () => {
      const denied = guard('/api/admin/projects')
      if (denied) return denied
      return HttpResponse.json([...projects].sort((a, b) => a.code.localeCompare(b.code)))
    }),

    http.post<never, ProjectInput, Project | Problem>(
      '*/api/admin/projects',
      async ({ request }) => {
        const denied = guard('/api/admin/projects')
        if (denied) return denied
        const body = await request.json()
        const error = projectError(body)
        if (error) return error
        const created = projectFrom(nextProjectId++, body)
        if (projects.some((p) => p.code === created.code)) return codeTaken(created.code)
        projects.push(created)
        return HttpResponse.json(created, { status: 201 })
      },
    ),

    http.put<{ id: string }, ProjectInput, Project | Problem>(
      '*/api/admin/projects/:id',
      async ({ params, request }) => {
        const denied = guard(`/api/admin/projects/${params.id}`)
        if (denied) return denied
        const index = projects.findIndex((p) => p.id === Number(params.id))
        if (index < 0) {
          return problem(404, {
            type: 'about:blank',
            title: 'Not Found',
            detail: 'Project not found',
          })
        }
        const body = await request.json()
        const error = projectError(body)
        if (error) return error
        const updated = projectFrom(Number(params.id), body)
        if (projects.some((p) => p.code === updated.code && p.id !== updated.id)) {
          return codeTaken(updated.code)
        }
        projects[index] = updated
        return HttpResponse.json(updated)
      },
    ),
  ]
}
