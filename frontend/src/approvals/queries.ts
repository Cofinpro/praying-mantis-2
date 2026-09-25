import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/vue-query'

import { api, type TeamAbsenceRequest, type TeamTimesheet } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

// FE-5.1: the requests the logged-in user decides on (T-5.1). The page only shows pending ones.
const pendingKey = queryKeys.team.absenceRequests('PENDING')
// FE-7.1: the weeks waiting for their decision (T-7.1)
const submittedKey = queryKeys.team.timesheets('SUBMITTED')

export function usePendingTeamRequests() {
  return useQuery({
    queryKey: pendingKey,
    queryFn: () => api.getTeamAbsenceRequests('PENDING'),
  })
}

export function useSubmittedTeamTimesheets() {
  return useQuery({
    queryKey: submittedKey,
    queryFn: () => api.getTeamTimesheets('SUBMITTED'),
  })
}

export type Decision = 'approve' | 'reject'

export interface DecisionVariables<T> {
  item: T
  decision: Decision
  comment?: string
}

/**
 * Approve or reject an item of a team list, with an optimistic update: the row leaves the list as
 * soon as the user clicks, instead of after the round trip. If the call fails, only that row goes
 * back, at its old place. Afterwards the lists are refetched either way, so the server has the
 * last word. Shared by both tabs of the Approvals page.
 */
function useOptimisticDecision<T>(options: {
  listKey: QueryKey
  idOf: (item: T) => number
  decide: (variables: DecisionVariables<T>) => Promise<unknown>
  /** Other queries the decision changes, besides the team lists */
  alsoInvalidate: QueryKey[]
}) {
  const queryClient = useQueryClient()
  const { listKey, idOf } = options

  return useMutation({
    mutationFn: options.decide,

    async onMutate({ item }) {
      // A refetch still in flight would put the row back when it lands, so stop it first
      await queryClient.cancelQueries({ queryKey: listKey })
      const list = queryClient.getQueryData<T[]>(listKey) ?? []
      const index = list.findIndex((r) => idOf(r) === idOf(item))
      queryClient.setQueryData<T[]>(
        listKey,
        list.filter((r) => idOf(r) !== idOf(item)),
      )
      // Whatever onMutate returns reaches onError and onSettled as `context`
      return { index }
    },

    onError(_error, { item }, context) {
      // Put back only this row. Restoring a snapshot of the whole list would also bring back a
      // row that another decision, made meanwhile, removed.
      queryClient.setQueryData<T[]>(listKey, (list = []) => {
        if (list.some((r) => idOf(r) === idOf(item))) {
          return list
        }
        const copy = [...list]
        copy.splice(context?.index ?? copy.length, 0, item)
        return copy
      })
    },

    onSettled() {
      // Success or failure, refetch: after a 404 or 409 the list is out of date anyway (someone
      // else decided, or the requester cancelled). Queries nothing on this page uses are only
      // marked stale; they refetch when their page mounts.
      return Promise.all(
        [queryKeys.team.all, ...options.alsoInvalidate].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      )
    },
  })
}

export function useDecideAbsenceRequest() {
  return useOptimisticDecision<TeamAbsenceRequest>({
    listKey: pendingKey,
    idOf: (r) => r.request.id,
    decide: ({ item, decision, comment }) => {
      const body = comment ? { comment } : {}
      return decision === 'approve'
        ? api.approveAbsenceRequest(item.request.id, body)
        : api.rejectAbsenceRequest(item.request.id, body)
    },
    alsoInvalidate: [queryKeys.absences.all],
  })
}

export function useDecideTimesheet() {
  return useOptimisticDecision<TeamTimesheet>({
    listKey: submittedKey,
    // Listed weeks are stored ones, so they always have an id (decision 32)
    idOf: (t) => t.timesheet.id!,
    decide: ({ item, decision, comment }) => {
      const id = item.timesheet.id!
      const body = comment ? { comment } : {}
      return decision === 'approve' ? api.approveTimesheet(id, body) : api.rejectTimesheet(id, body)
    },
    // Nobody decides their own week (decision 16), so none of my own queries change
    alsoInvalidate: [],
  })
}
