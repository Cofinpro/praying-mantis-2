import type { AppNotification } from '@/api/client'

// Mock notifications for Ana Silva, a team lead (contract T-4.1), shaped like the Figma frame
// "07 Notifications dropdown". Times are relative to `now`, so "2 min ago" stays true in dev:mock.

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

export function mockNotifications(now: number = Date.now()): AppNotification[] {
  const at = (ago: number) => new Date(now - ago).toISOString()
  const read = (ago: number) => ({ readAt: at(ago) })
  return [
    {
      id: 106,
      type: 'ABSENCE_REQUESTED',
      message: 'Carla Mendes requested 5 days of vacation (2–6 Nov)',
      link: '/approvals',
      createdAt: at(2 * MINUTE),
    },
    {
      id: 105,
      type: 'ABSENCE_APPROVED',
      // Request 5 in data/absences.ts
      message: 'Your vacation 12–16 Oct was approved by Alex Admin',
      link: '/absences?request=5',
      createdAt: at(HOUR),
    },
    {
      id: 104,
      type: 'TIMESHEET_SUBMITTED',
      message: 'João Pereira submitted the timesheet for week 42',
      link: '/approvals?tab=timesheets',
      createdAt: at(DAY + 2 * HOUR),
    },
    {
      id: 103,
      type: 'TIMESHEET_REJECTED',
      message: 'Your timesheet for week 41 was rejected: “Missing hours on Friday”',
      link: '/timesheets?week=2026-10-05',
      createdAt: at(3 * DAY),
      ...read(3 * DAY - HOUR),
    },
    {
      id: 102,
      type: 'ABSENCE_CANCELLED',
      message: 'Carla Mendes cancelled her approved vacation (21–23 Oct)',
      link: '/approvals',
      createdAt: at(4 * DAY),
      ...read(4 * DAY - HOUR),
    },
    {
      id: 101,
      type: 'ABSENCE_REJECTED',
      // Request 3 in data/absences.ts
      message: 'Your vacation 14–15 Sep was rejected by Alex Admin: “Release week”',
      link: '/absences?request=3',
      createdAt: at(12 * DAY),
      ...read(12 * DAY - HOUR),
    },
  ]
}
