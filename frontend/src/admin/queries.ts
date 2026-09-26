import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  api,
  type AdminUser,
  type AdminUserUpdate,
  type NewAdminUser,
  type PasswordReset,
} from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import { useCurrentUser } from '@/auth/session'

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
