import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import { saveFile } from './download'

/** The templates change only with a backend release (decision 33), so one fetch per session will do */
export function useExportTemplates() {
  return useQuery({
    queryKey: queryKeys.exportTemplates,
    queryFn: api.getExportTemplates,
    staleTime: Infinity,
  })
}

/** The month's weeks and their status, for the "not approved yet" warning. `month` is `YYYY-MM`. */
export function useMyTimesheetMonth(month: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.timesheets.month(toValue(month))),
    queryFn: () => api.getMyTimesheetMonth(toValue(month)),
  })
}

/**
 * Downloads the month as an `.xlsx`. A mutation rather than a query: it's a one-off action the user
 * starts, nothing to cache, and `isPending` / `error` are exactly what the button and banner need.
 */
export function useExportMonth() {
  return useMutation({
    mutationFn: ({ month, template }: { month: string; template: string }) =>
      api.exportMyTimesheetMonth(month, template),
    onSuccess: ({ blob, filename }) => saveFile(blob, filename),
  })
}
