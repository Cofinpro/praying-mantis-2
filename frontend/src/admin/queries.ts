import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  api,
  type AdminUser,
  type AdminUserUpdate,
  type NewAdminUser,
  type PasswordReset,
  type ProjectInput,
} from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import { useCurrentUser } from '@/auth/session'
import type { EntitlementChange } from './entitlements'

// FE-9.1: the users an admin manages (T-9.1). The backend checks the admin flag in the DB on every
// call (decisions #11, #35), so a non-admin gets a 403 here, not an empty list.

export function useAdminUsers() {
  return useQuery({
    queryKey: queryKeys.admin.users,
    queryFn: api.getAdminUsers,
    // A 403 won't turn into a 200 by asking again
    retry: false,
  })
}

/**
 * After a save: the list (names, team leads and "is team lead" can all change), and my own user
 * when I edited myself, since the header shows my name and the nav depends on my admin flag.
 */
function useAfterSave() {
  const queryClient = useQueryClient()
  const { data: me } = useCurrentUser()
  return async (saved: AdminUser) => {
    const refreshes = [queryClient.invalidateQueries({ queryKey: queryKeys.admin.users })]
    if (saved.id === me.value?.id) {
      refreshes.push(queryClient.invalidateQueries({ queryKey: queryKeys.me }))
    }
    await Promise.all(refreshes)
  }
}

export function useCreateAdminUser() {
  const afterSave = useAfterSave()
  return useMutation({
    mutationFn: (body: NewAdminUser) => api.createAdminUser(body),
    onSuccess: afterSave,
  })
}

export function useUpdateAdminUser() {
  const afterSave = useAfterSave()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: AdminUserUpdate }) =>
      api.updateAdminUser(id, body),
    onSuccess: afterSave,
  })
}

export function useSetAdminUserPassword() {
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: PasswordReset }) =>
      api.setAdminUserPassword(id, body),
  })
}

// FE-9.2: entitlements of a year, for every type; the page filters on the type
export function useAdminEntitlements(year: MaybeRefOrGetter<number>) {
  return useQuery({
    queryKey: computed(() => queryKeys.admin.entitlements.year(toValue(year))),
    queryFn: () => api.getAdminEntitlements(toValue(year)),
    retry: false,
  })
}

/**
 * Saves the changed rows of the grid, one call each (the contract has no batch endpoint), and
 * settles all of them: a failed row keeps its edit and shows its error, the others are saved.
 * Afterwards the grid and everyone's balances are stale, mine included when I changed my own row.
 */
export function useSaveEntitlements() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (changes: EntitlementChange[]) =>
      Promise.allSettled(
        changes.map((c) =>
          c.kind === 'save' ? api.saveAdminEntitlement(c.body) : api.deleteAdminEntitlement(c.id),
        ),
      ),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.admin.entitlements.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.absences.all }),
      ]),
  })
}

// FE-9.3: all projects, active and inactive
export function useAdminProjects() {
  return useQuery({
    queryKey: queryKeys.admin.projects,
    queryFn: api.getAdminProjects,
    retry: false,
  })
}

/**
 * After a project is saved: the admin list, the timesheet's project choices (active ones only)
 * and the weeks, whose entries show the project's code and name.
 */
function useAfterProjectSave() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.projects }),
      queryClient.invalidateQueries({ queryKey: queryKeys.allProjects }),
      queryClient.invalidateQueries({ queryKey: queryKeys.timesheets.all }),
    ])
}

export function useCreateAdminProject() {
  const afterSave = useAfterProjectSave()
  return useMutation({
    mutationFn: (body: ProjectInput) => api.createAdminProject(body),
    onSuccess: afterSave,
  })
}

export function useUpdateAdminProject() {
  const afterSave = useAfterProjectSave()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: ProjectInput }) =>
      api.updateAdminProject(id, body),
    onSuccess: afterSave,
  })
}
