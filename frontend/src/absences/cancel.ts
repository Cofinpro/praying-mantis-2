import type { AbsenceRequest } from '@/api/client'

/**
 * The rule from the contract (POST /me/absence-requests/{id}/cancel): a pending request can be
 * cancelled at any time, an approved one until the day it starts. Only used to show the button;
 * the backend enforces it and answers 409 otherwise.
 */
export function canCancel(request: AbsenceRequest, todayIso: string): boolean {
  return (
    request.status === 'PENDING' || (request.status === 'APPROVED' && request.startDate > todayIso)
  )
}
