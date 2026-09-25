import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { api, type TimeEntryInput } from '@/api/client'
import { showsStaleData } from '@/api/problems'
import { queryKeys } from '@/api/queryKeys'

/** The projects that take new hours. They change only when an admin edits one (FE-9.3). */
export function useActiveProjects() {
  return useQuery({
    queryKey: queryKeys.projects(true),
    queryFn: () => api.getProjects(true),
    staleTime: 5 * 60 * 1000,
  })
}

export function useMyTimesheet(weekStart: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.timesheets.week(toValue(weekStart))),
    queryFn: () => api.getMyTimesheet(toValue(weekStart)),
    // A refetch on focus would replace the week under the user's unsaved edits. The page keeps
    // those edits anyway (TimesheetsView), but there's nothing new to expect from another tab
    // often enough to justify it: the week only changes when this user or their approver acts.
    refetchOnWindowFocus: false,
  })
}

/**
 * Saves the whole week (PUT replaces every entry). The response is the saved week, so it goes
 * straight into the cache instead of a second GET. A 409 means the week was submitted meanwhile
 * (e.g. in another tab): refetch, so the page shows it read-only.
 */
export function useSaveTimesheet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ weekStart, entries }: { weekStart: string; entries: TimeEntryInput[] }) =>
      api.saveMyTimesheetEntries(weekStart, { entries }),
    onSuccess(saved) {
      queryClient.setQueryData(queryKeys.timesheets.week(saved.weekStart), saved)
    },
    onError(error, { weekStart }) {
      if (showsStaleData(error)) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.timesheets.week(weekStart) })
      }
    },
  })
}
