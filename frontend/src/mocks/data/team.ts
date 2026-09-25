import type { TeamAbsenceRequest } from '@/api/client'

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
