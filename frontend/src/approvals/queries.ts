import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { api, type TeamAbsenceRequest } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

// FE-5.1: the requests the logged-in user decides on (T-5.1). The page only shows pending ones.
const pendingKey = queryKeys.team.absenceRequests('PENDING')

export function usePendingTeamRequests() {
  return useQuery({
    queryKey: pendingKey,
    queryFn: () => api.getTeamAbsenceRequests('PENDING'),
  })
}

export type Decision = 'approve' | 'reject'

export interface DecisionVariables {
  item: TeamAbsenceRequest
  decision: Decision
  comment?: string
}

/**
 * Approve or reject, with an optimistic update: the row leaves the list as soon as the user
 * clicks, instead of after the round trip. If the call fails, only that row goes back, at its
 * old place. Afterwards the list is refetched either way, so the server has the last word.
 */
export function useDecideAbsenceRequest() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ item, decision, comment }: DecisionVariables) => {
      const body = comment ? { comment } : {}
      return decision === 'approve'
        ? api.approveAbsenceRequest(item.request.id, body)
        : api.rejectAbsenceRequest(item.request.id, body)
    },

    async onMutate({ item }) {
      // A refetch still in flight would put the row back when it lands, so stop it first
      await queryClient.cancelQueries({ queryKey: pendingKey })
      const list = queryClient.getQueryData<TeamAbsenceRequest[]>(pendingKey) ?? []
      const index = list.findIndex((r) => r.request.id === item.request.id)
      queryClient.setQueryData<TeamAbsenceRequest[]>(
        pendingKey,
        list.filter((r) => r.request.id !== item.request.id),
      )
      // Whatever onMutate returns reaches onError and onSettled as `context`
      return { index }
    },

    onError(_error, { item }, context) {
      // Put back only this row. Restoring a snapshot of the whole list would also bring back a
      // row that another decision, made meanwhile, removed.
      queryClient.setQueryData<TeamAbsenceRequest[]>(pendingKey, (list = []) => {
        if (list.some((r) => r.request.id === item.request.id)) {
          return list
        }
        const copy = [...list]
        copy.splice(context?.index ?? copy.length, 0, item)
        return copy
      })
    },

    onSettled() {
      // Success or failure, refetch: after a 404 or 409 the list is out of date anyway (someone
      // else decided, or the requester cancelled). The absence queries are only marked stale
      // here, since nothing on this page uses them; they refetch when the Absences page mounts.
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.team.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.absences.all }),
      ])
    },
  })
}
