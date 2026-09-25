import type { Project, TimeEntry, Timesheet, TimesheetStatus } from '@/api/client'

// The same projects as the backend's dev seed (0004-SCRUM-56-projects.yaml), ids included
const project = (
  id: number,
  code: string,
  name: string,
  client: Project['client'],
  isActive = true,
): Project => ({
  id,
  code,
  name,
  ...(client ? { client } : {}),
  isBillable: client !== undefined,
  isActive,
})

export const projects: Project[] = [
  project(1, 'DKB-CORE', 'DKB core banking', 'DKB'),
  project(2, 'DKB-APP', 'DKB mobile app', 'DKB'),
  project(3, 'DEKA-RISK', 'Deka risk reporting', 'DEKA'),
  project(4, 'VV-MIGRATION', 'VV data migration', 'VV'),
  project(5, 'DBIS-PORTAL', 'DBIS client portal', 'DBIS'),
  project(6, 'UNION-FUNDS', 'Union Investment fund platform', 'UNION'),
  project(7, 'INTERNAL', 'Internal', undefined),
  project(8, 'TRAINING', 'Training', undefined),
  project(9, 'DKB-LEGACY', 'DKB legacy migration (finished)', 'DKB', false),
]

let nextEntryId = 1
/** "8" hours on DKB-CORE (id 1) on a date */
function entry(projectId: number, workDate: string, hours: number, description?: string) {
  const { id, code, name } = projects.find((p) => p.id === projectId)!
  const e: TimeEntry = { id: nextEntryId++, project: { id, code, name }, workDate, hours }
  return description ? { ...e, description } : e
}

/** A stored week. Absences and holidays are added by the handler, from the other mock data. */
export type StoredTimesheet = Omit<Timesheet, 'absences' | 'holidays' | 'totalHours'>

const alex = { id: 1, name: 'Alex Admin' }
const stored = (
  id: number,
  weekStart: string,
  status: TimesheetStatus,
  entries: TimeEntry[],
  extra: Partial<StoredTimesheet> = {},
): StoredTimesheet => ({ id, weekStart, status, entries, ...extra })

/**
 * Ana's weeks, keyed by Monday. Every other week of hers, and anyone else's, is an empty DRAFT
 * without id (decision 32). Today in the demo is late September 2026.
 */
export const timesheets: Record<string, StoredTimesheet> = {
  '2026-09-07': stored(
    1,
    '2026-09-07',
    'APPROVED',
    ['07', '08', '09', '10', '11'].map((d) => entry(1, `2026-09-${d}`, 8)),
    { approver: alex, submittedAt: '2026-09-11T16:00:00Z', decidedAt: '2026-09-14T09:00:00Z' },
  ),
  '2026-09-14': stored(
    2,
    '2026-09-14',
    'SUBMITTED',
    [
      ...['14', '15', '16', '17'].map((d) => entry(1, `2026-09-${d}`, 8)),
      entry(7, '2026-09-18', 4, 'Team retro'),
      entry(8, '2026-09-18', 4),
    ],
    { approver: alex, submittedAt: '2026-09-18T17:00:00Z' },
  ),
  // The TIMESHEET_REJECTED notification links here (mocks/data/notifications.ts)
  '2026-10-05': stored(
    3,
    '2026-10-05',
    'REJECTED',
    [
      entry(1, '2026-10-06', 8),
      entry(1, '2026-10-07', 8),
      entry(2, '2026-10-09', 8, 'Release support'),
    ],
    {
      approver: alex,
      submittedAt: '2026-10-09T16:30:00Z',
      decidedAt: '2026-10-12T09:00:00Z',
      decisionComment: 'Please book Thursday on DKB-CORE',
    },
  ),
  // The Figma frame's week: 19–25 Oct 2026, with Wednesday's approved training
  '2026-10-19': stored(4, '2026-10-19', 'DRAFT', [
    entry(1, '2026-10-19', 6),
    entry(1, '2026-10-20', 8, 'Sprint planning'),
    entry(1, '2026-10-22', 7),
    entry(1, '2026-10-23', 6),
    entry(2, '2026-10-19', 2),
    entry(2, '2026-10-22', 1),
    entry(8, '2026-10-21', 8, 'AWS course'),
  ]),
}

const ana = { id: 2, name: 'Ana Silva' }
const days = (from: number, to: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => String(from + i).padStart(2, '0'))

/**
 * Weeks Ana's team submitted for her to approve (T-7.1), with the user they belong to: the
 * Timesheets tab of the Approvals page. Carla's week 37 and Diogo's week 38.
 */
export const teamTimesheets: { userId: number; sheet: StoredTimesheet }[] = [
  {
    userId: 4,
    sheet: stored(
      11,
      '2026-09-07',
      'SUBMITTED',
      [
        ...days(7, 11).map((d) => entry(3, `2026-09-${d}`, 7.5)),
        entry(7, '2026-09-11', 1, 'Team retro'),
      ],
      { approver: ana, submittedAt: '2026-09-11T17:10:00Z' },
    ),
  },
  {
    userId: 5,
    sheet: stored(
      12,
      '2026-09-14',
      'SUBMITTED',
      [
        ...days(14, 17).map((d) => entry(1, `2026-09-${d}`, 8)),
        entry(8, '2026-09-18', 8, 'AWS course'),
      ],
      { approver: ana, submittedAt: '2026-09-18T16:45:00Z' },
    ),
  },
]
