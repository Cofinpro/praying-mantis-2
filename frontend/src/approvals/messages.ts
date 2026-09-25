// The approver's wording for the 409s of approve and reject (T-5.1). problemMessage() falls back
// to the requester's wording in api/problems.ts for everything else.
export const APPROVAL_MESSAGES: Record<string, string> = {
  '/problems/insufficient-balance':
    'This request doesn’t fit the balance any more, so it can’t be approved. You can still reject it.',
}
