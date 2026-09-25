import type { AbsenceStatus, TimesheetStatus } from './client'

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
  // What I decide on as approver: absence requests (FE-5.1) and weeks (FE-7.1). Separate from
  // `absences` and `timesheets`, which are my own; invalidating ['team'] refreshes both tabs.
  team: {
    all: ['team'] as const,
    absenceRequests: (status: AbsenceStatus) => ['team', 'absence-requests', status] as const,
    timesheets: (status: TimesheetStatus) => ['team', 'timesheets', status] as const,
  },
  publicHolidays: (year: number) => ['public-holidays', year] as const,
  // Invalidating ['notifications'] after marking something read refreshes the badge and the list
  notifications: {
    all: ['notifications'] as const,
    list: ['notifications', 'list'] as const,
    unreadCount: ['notifications', 'unread-count'] as const,
  },
  projects: (active: boolean) => ['projects', { active }] as const,
  // My own weeks (FE-6.1). The approver's view of other people's weeks is under `team`.
  timesheets: {
    all: ['timesheets'] as const,
    week: (weekStart: string) => ['timesheets', weekStart] as const,
    /** What an export of the month would contain (FE-8.1); under ['timesheets'], so it's mine too */
    month: (month: string) => ['timesheets', 'month', month] as const,
  },
  exportTemplates: ['export-templates'] as const,
}
