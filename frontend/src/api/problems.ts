import { ApiError } from './client'

// Turns an ApiError into what the user reads. 409s carry the business rule in the Problem's
// `type` (contract, decision #29), so the FE picks its own wording instead of parsing `detail`.

const CONFLICTS: Record<string, string> = {
  '/problems/absence-overlap':
    'These days overlap another absence of yours that is pending or approved.',
  '/problems/insufficient-balance': 'You don’t have enough days left for this request.',
  '/problems/no-approver': 'Nobody can approve this request yet. Please ask an admin.',
  '/problems/absence-not-cancellable': 'This absence can’t be cancelled any more.',
  '/problems/absence-not-pending': 'This request was already decided or cancelled.',
}

/**
 * A message for the form's banner, or null for a 400 with field errors (those go under the fields).
 * `overrides` rewords a `type` for another point of view, e.g. the approver's instead of the requester's.
 */
export function problemMessage(
  error: unknown,
  overrides: Record<string, string> = {},
): string | null {
  if (!(error instanceof ApiError)) {
    return error ? 'Something went wrong. Please try again.' : null
  }
  if (error.status === 400) {
    // Spring answers an unreadable body with a 400 without `errors`: nothing to show under a field
    return error.problem.errors?.length ? null : 'Something went wrong. Please try again.'
  }
  const known = overrides[error.problem.type] ?? CONFLICTS[error.problem.type]
  if (known) {
    // The backend's detail adds the numbers for the balance case ("Only 3 days left in 2026…")
    return error.problem.type === '/problems/insufficient-balance' && error.problem.detail
      ? `${known} ${error.problem.detail}.`
      : known
  }
  return 'Something went wrong. Please try again.'
}

/** Field messages from a 400 Problem, keyed by field name */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || error.status !== 400) {
    return {}
  }
  return Object.fromEntries((error.problem.errors ?? []).map((e) => [e.field, e.message]))
}

/**
 * A 404 or 409 usually means the data on screen is out of date: the request was cancelled in
 * another tab, or a new one overlaps. The caller then refetches, so the page shows the real state.
 */
export function showsStaleData(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.status === 409)
}
