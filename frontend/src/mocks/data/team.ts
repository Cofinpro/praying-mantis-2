import type { TeamAbsence, TeamAbsenceRequest } from '@/api/client'

// Pending requests from Ana Silva's team, with the dev seed's ids (data/users.ts, test-users.md), shaped like the Figma frame "04 Approvals"

const approver = { id: 2, name: 'Ana Silva' }
const full = { startPart: 'FULL', endPart: 'FULL' } as const

export const teamAbsenceRequests: TeamAbsenceRequest[] = [
  {
    request: {
      id: 501,
      type: 'TRAINING',
      startDate: '2026-11-10',
      endDate: '2026-11-10',
      ...full,
      workingDays: 1,
      status: 'PENDING',
      reason: 'AWS certification',
      approver,
      createdAt: '2026-09-20T10:12:00Z',
    },
    requester: { id: 5, name: 'Diogo Pereira' },
    // No remainingDays: the contract only sends it for VACATION
  },
  {
    request: {
      id: 502,
      type: 'VACATION',
      startDate: '2026-11-16',
      endDate: '2026-11-18',
      ...full,
      workingDays: 3,
      status: 'PENDING',
      reason: 'Visiting family in Madeira',
      approver,
      createdAt: '2026-09-22T08:40:00Z',
    },
    requester: { id: 4, name: 'Carla Mendes' },
    remainingDays: 19,
  },
  {
    // Doesn't fit any more: approving it answers 409 insufficient-balance (decision 29)
    request: {
      id: 503,
      type: 'VACATION',
      startDate: '2026-12-23',
      endDate: '2027-01-01',
      ...full,
      workingDays: 6,
      status: 'PENDING',
      reason: 'Christmas holidays',
      approver,
      createdAt: '2026-09-23T16:05:00Z',
    },
    requester: { id: 3, name: 'Bruno Costa' },
    remainingDays: 5,
  },
]

/**
 * Decided absences of the dev seed's people, for the team calendar (T-5.3). Their pending ones
 * come from `teamAbsenceRequests`, so approving or rejecting one shows up in the calendar too.
 * With Ana's own requests (data/absences.ts) they give October 2026 a conflict week (Ana and Carla
 * away 14–16 Oct, plus Diogo's training on the 15th), sick days and a morning off, and a conflict
 * across the month boundary at the end of September.
 */
export const teamCalendarAbsences: { userId: number; absence: TeamAbsence }[] = [
  {
    userId: 3,
    absence: {
      id: 601,
      type: 'VACATION',
      startDate: '2026-09-28',
      endDate: '2026-10-02',
      ...full,
      status: 'APPROVED',
    },
  },
  {
    userId: 5,
    absence: {
      id: 602,
      type: 'SICK',
      startDate: '2026-09-29',
      endDate: '2026-09-30',
      ...full,
      status: 'APPROVED',
    },
  },
  {
    userId: 4,
    absence: {
      id: 603,
      type: 'VACATION',
      startDate: '2026-10-14',
      endDate: '2026-10-16',
      ...full,
      status: 'APPROVED',
    },
  },
  {
    userId: 5,
    absence: {
      id: 604,
      type: 'TRAINING',
      startDate: '2026-10-15',
      endDate: '2026-10-15',
      ...full,
      status: 'APPROVED',
    },
  },
  {
    userId: 3,
    absence: {
      id: 605,
      type: 'VACATION',
      startDate: '2026-10-23',
      endDate: '2026-10-23',
      startPart: 'MORNING',
      endPart: 'MORNING',
      status: 'APPROVED',
    },
  },
  {
    userId: 5,
    absence: {
      id: 606,
      type: 'SICK',
      startDate: '2026-10-07',
      endDate: '2026-10-07',
      ...full,
      status: 'APPROVED',
    },
  },
  {
    userId: 6,
    absence: {
      id: 607,
      type: 'VACATION',
      startDate: '2026-10-19',
      endDate: '2026-10-23',
      ...full,
      status: 'APPROVED',
    },
  },
]
