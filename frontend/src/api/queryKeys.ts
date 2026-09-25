import type { AbsenceStatus } from './client'

// All TanStack Query keys in one place, so the code that writes a cache entry and the code that
// reads it can't drift apart (a typo would silently mean an extra request instead of a cache hit).
// Hierarchical: invalidating ['absences'] refreshes the balance and every requests range at once.
export const queryKeys = {
  me: ['me'] as const,
  absenceTypes: ['absence-types'] as const,
  absences: {
    all: ['absences'] as const,
    balance: (year: number) => ['absences', 'balance', year] as const,
    /** Every range of my requests: "Coming up" and each calendar month */
    allRequests: ['absences', 'requests'] as const,
    requests: (from: string, to: string) => ['absences', 'requests', from, to] as const,
  },
  // Requests I decide on as approver (FE-5.1). Separate from `absences`, which are my own.
  team: {
    all: ['team'] as const,
    absenceRequests: (status: AbsenceStatus) => ['team', 'absence-requests', status] as const,
  },
  publicHolidays: (year: number) => ['public-holidays', year] as const,
  // Invalidating ['notifications'] after marking something read refreshes the badge and the list
  notifications: {
    all: ['notifications'] as const,
    list: ['notifications', 'list'] as const,
    unreadCount: ['notifications', 'unread-count'] as const,
  },
}
