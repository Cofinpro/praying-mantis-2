import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useQuery } from '@tanstack/vue-query'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

/** The types rarely change, so keep them for the whole session */
export function useAbsenceTypes() {
  return useQuery({
    queryKey: queryKeys.absenceTypes,
    queryFn: api.getAbsenceTypes,
    staleTime: Infinity,
  })
}

// Arguments are refs or getters, and the keys are computed from them: when the year or range
// changes, the key changes and TanStack Query fetches (and caches) the new one.

export function useAbsenceBalance(year: MaybeRefOrGetter<number>) {
  return useQuery({
    queryKey: computed(() => queryKeys.absences.balance(toValue(year))),
    queryFn: () => api.getMyAbsenceBalance(toValue(year)),
  })
}

export function useMyAbsenceRequests(from: MaybeRefOrGetter<string>, to: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.absences.requests(toValue(from), toValue(to))),
    queryFn: () => api.getMyAbsenceRequests(toValue(from), toValue(to)),
  })
}

export function usePublicHolidays(year: MaybeRefOrGetter<number>) {
  return useQuery({
    queryKey: computed(() => queryKeys.publicHolidays(toValue(year))),
    queryFn: () => api.getPublicHolidays(toValue(year)),
    // Holidays of a year practically never change
    staleTime: Infinity,
  })
}
