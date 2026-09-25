// The approver's wording for the 409s of approve and reject (T-5.1). problemMessage() falls back
// to the requester's wording in api/problems.ts for everything else.
export const APPROVAL_MESSAGES: Record<string, string> = {
  '/problems/insufficient-balance':
    'This request doesn’t fit the balance any more, so it can’t be approved. You can still reject it.',
  // FE-7.1: only SUBMITTED weeks can be decided (BE-7.2)
  '/problems/timesheet-not-submitted': 'This week was already approved or rejected.',
}
