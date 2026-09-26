import type { AbsenceTypeCode, AdminEntitlement, EntitlementInput } from '@/api/client'

// FE-9.2, frame "13 Admin – Entitlements": the grid edits two text cells per person. What to send
// is computed from the cells and the saved entitlement, so typing a value and deleting it again
// isn't a change, and a background refetch can't lose what was typed (only edits are stored).

/** What the admin typed in a row; an empty cell counts as 0 */
export interface EntitlementCells {
  entitled: string
  carried: string
}

export type EntitlementChange =
  | { kind: 'save'; userId: number; body: EntitlementInput }
  | { kind: 'delete'; userId: number; id: number }

/** A saved number as the cell shows it: 22 → "22", 2.5 → "2.5", none → "" */
export const toCell = (days: number | undefined) => (days === undefined ? '' : String(days))

/** The contract's rule for days: 0 to 366, in steps of 0.5 */
export function cellError(value: string): string | undefined {
  const text = value.trim()
  if (text === '') {
    return undefined
  }
  const days = Number(text)
  if (!Number.isFinite(days) || days < 0 || days > 366) {
    return 'Enter 0 to 366 days.'
  }
  return Number.isInteger(days * 2) ? undefined : 'Use steps of 0.5, e.g. 2.5.'
}

const days = (value: string) => (value.trim() === '' ? 0 : Number(value))

/** Entitled + carried over, as the Total column shows it; undefined while a cell is invalid */
export function totalOf(cells: EntitlementCells): number | undefined {
  if (cellError(cells.entitled) || cellError(cells.carried)) {
    return undefined
  }
  return days(cells.entitled) + days(cells.carried)
}

/**
 * The call a row needs, or null when it matches what's saved. Emptying both cells of a saved
 * entitlement deletes it (the person then has no days of that type, decision 29); PUT is an
 * upsert on (user, type, year), so a new row and a changed row are the same call (decision 35).
 */
export function changeFor(
  context: { userId: number; year: number; type: AbsenceTypeCode },
  saved: AdminEntitlement | undefined,
  cells: EntitlementCells,
): EntitlementChange | null {
  const { userId, year, type } = context
  if (cells.entitled.trim() === '' && cells.carried.trim() === '') {
    return saved ? { kind: 'delete', userId, id: saved.id } : null
  }
  const entitledDays = days(cells.entitled)
  const carriedOverDays = days(cells.carried)
  if (saved?.entitledDays === entitledDays && saved.carriedOverDays === carriedOverDays) {
    return null
  }
  return { kind: 'save', userId, body: { userId, type, year, entitledDays, carriedOverDays } }
}
