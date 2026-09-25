import { toValue, type MaybeRefOrGetter } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

/** How often the bell asks for the unread count (decision #17) */
export const UNREAD_POLL_MS = 30_000

/** How many notifications the dropdown shows; older ones would need a "See all" page */
export const DROPDOWN_LIMIT = 10

/**
 * The badge. Polls every 30 s and refetches when the tab regains focus (decision #17). The global
 * `staleTime` of 30 s would skip the focus refetch, so this query is always stale (learnings).
 */
export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: queryKeys.notifications.unreadCount,
    queryFn: api.getMyUnreadNotificationCount,
    select: (data) => data.count,
    staleTime: 0,
    refetchInterval: UNREAD_POLL_MS,
    refetchOnWindowFocus: true,
  })
}

/** The newest notifications, fetched each time the dropdown opens (`enabled` turns true) */
export function useRecentNotifications(enabled: MaybeRefOrGetter<boolean>) {
  return useQuery({
    queryKey: queryKeys.notifications.list,
    queryFn: () => api.getMyNotifications({ limit: DROPDOWN_LIMIT }),
    enabled: () => toValue(enabled),
    staleTime: 0,
  })
}

// Both mutations refresh the badge and the list afterwards, also when they fail: a 404 means the
// list on screen is out of date anyway.

export function useMarkNotificationRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.markMyNotificationRead,
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  })
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.markAllMyNotificationsRead,
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  })
}
