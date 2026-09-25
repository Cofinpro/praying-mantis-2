// All TanStack Query keys in one place, so the code that writes a cache entry and the code that
// reads it can't drift apart (a typo would silently mean an extra request instead of a cache hit).
// Hierarchical: invalidating ['absences'] refreshes the balance and every requests range at once.
export const queryKeys = {
  me: ['me'] as const,
  absenceTypes: ['absence-types'] as const,
  absences: {
    all: ['absences'] as const,
    balance: (year: number) => ['absences', 'balance', year] as const,
    requests: (from: string, to: string) => ['absences', 'requests', from, to] as const,
  },
  publicHolidays: (year: number) => ['public-holidays', year] as const,
}
