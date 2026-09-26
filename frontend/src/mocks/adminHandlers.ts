import { http, HttpResponse } from 'msw'

import type {
  AdminUser,
  AdminUserUpdate,
  CurrentUser,
  NewAdminUser,
  PasswordReset,
  Problem,
} from '@/api/client'
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

/** Called from resetMockSession() after every test */
export function resetAdminMock() {
  users = seed()
  nextUserId = 100
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
  ]
}
