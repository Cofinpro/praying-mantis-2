import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

/** The logged-in user. The route guard has loaded it before any page with the header renders. */
export function useCurrentUser() {
  return useQuery({ queryKey: queryKeys.me, queryFn: api.getMe })
}

/** Logs out, forgets everything cached for this user and goes to the login page. */
export function useLogout() {
  const queryClient = useQueryClient()
  const router = useRouter()
  return useMutation({
    mutationFn: api.logout,
    async onSuccess() {
      queryClient.clear()
      await router.push({ name: 'login' })
    },
  })
}
