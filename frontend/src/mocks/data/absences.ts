import type { AbsenceBalance, AbsenceRequest, AbsenceType, PublicHoliday } from '@/api/client'

// Mock data shaped like the Figma frame "02 Absences" (October 2026)

export const absenceTypes: AbsenceType[] = [
  {
    code: 'PARENTAL',
    name: 'Parental leave',
    isPaid: true,
    deductsFromBalance: false,
    requiresApproval: true,
  },
  { code: 'SICK', name: 'Sick', isPaid: true, deductsFromBalance: false, requiresApproval: false },
  {
    code: 'TRAINING',
    name: 'Training',
    isPaid: true,
    deductsFromBalance: true,
    requiresApproval: true,
  },
  {
    code: 'UNPAID',
    name: 'Unpaid leave',
    isPaid: false,
    deductsFromBalance: false,
    requiresApproval: true,
  },
  {
    code: 'VACATION',
    name: 'Vacation',
    isPaid: true,
    deductsFromBalance: true,
    requiresApproval: true,
  },
]

export const balances: Record<number, AbsenceBalance[]> = {
  2025: [
    {
      type: 'VACATION',
      year: 2025,
      entitledDays: 25,
      carriedOverDays: 2,
      usedDays: 24,
      pendingDays: 0,
      remainingDays: 3,
    },
  ],
  2026: [
    {
      type: 'TRAINING',
      year: 2026,
      entitledDays: 5,
      carriedOverDays: 0,
      usedDays: 2,
      pendingDays: 0,
      remainingDays: 3,
    },
    {
      type: 'VACATION',
      year: 2026,
      entitledDays: 25,
      carriedOverDays: 3,
      usedDays: 9.5,
      pendingDays: 2,
      remainingDays: 18.5,
    },
  ],
}

const approver = { id: 1, name: 'Alex Admin' }
const created = '2026-09-01T09:00:00Z'
let nextId = 1
const request = (r: Omit<AbsenceRequest, 'id' | 'createdAt'>): AbsenceRequest => ({
  id: nextId++,
  createdAt: created,
  ...r,
})
const full = { startPart: 'FULL', endPart: 'FULL' } as const

export const absenceRequests: AbsenceRequest[] = [
  request({
    type: 'VACATION',
    startDate: '2026-03-30',
    endDate: '2026-04-02',
    ...full,
    workingDays: 4,
    status: 'APPROVED',
    approver,
  }),
  request({
    type: 'TRAINING',
    startDate: '2026-06-15',
    endDate: '2026-06-15',
    ...full,
    workingDays: 1,
    status: 'APPROVED',
    approver,
  }),
  request({
    type: 'VACATION',
    startDate: '2026-09-14',
    endDate: '2026-09-15',
    ...full,
    workingDays: 2,
    status: 'REJECTED',
    approver,
    decisionComment: 'Release week',
  }),
  request({
    type: 'SICK',
    startDate: '2026-10-08',
    endDate: '2026-10-08',
    ...full,
    workingDays: 1,
    status: 'APPROVED',
  }),
  request({
    type: 'VACATION',
    startDate: '2026-10-12',
    endDate: '2026-10-16',
    ...full,
    workingDays: 5,
    status: 'APPROVED',
    approver,
    reason: 'Autumn break',
  }),
  request({
    type: 'TRAINING',
    startDate: '2026-10-21',
    endDate: '2026-10-21',
    ...full,
    workingDays: 1,
    status: 'APPROVED',
    approver,
  }),
  request({
    type: 'VACATION',
    startDate: '2026-10-26',
    endDate: '2026-10-27',
    ...full,
    workingDays: 2,
    status: 'PENDING',
    approver,
    reason: 'Long weekend in Lisbon',
  }),
  request({
    type: 'VACATION',
    startDate: '2026-10-30',
    endDate: '2026-10-30',
    startPart: 'AFTERNOON',
    endPart: 'AFTERNOON',
    workingDays: 0.5,
    status: 'APPROVED',
    approver,
  }),
  request({
    type: 'VACATION',
    startDate: '2026-11-02',
    endDate: '2026-11-02',
    ...full,
    workingDays: 1,
    status: 'CANCELLED',
    approver,
  }),
]

// Portugal's national holidays (contract: GET /public-holidays); the admin page edits them (FE-9.4)
export const publicHolidays: Record<number, PublicHoliday[]> = {
  2026: [
    { date: '2026-01-01', name: 'New Year’s Day' },
    { date: '2026-04-03', name: 'Good Friday' },
    { date: '2026-04-25', name: 'Freedom Day' },
    { date: '2026-05-01', name: 'Labour Day' },
    { date: '2026-06-04', name: 'Corpus Christi' },
    { date: '2026-06-10', name: 'Portugal Day' },
    { date: '2026-08-15', name: 'Assumption' },
    { date: '2026-10-05', name: 'Republic Day' },
    { date: '2026-11-01', name: 'All Saints’ Day' },
    { date: '2026-12-01', name: 'Restoration of Independence' },
    { date: '2026-12-08', name: 'Immaculate Conception' },
    { date: '2026-12-25', name: 'Christmas Day' },
  ],
  2027: [
    { date: '2027-01-01', name: 'New Year’s Day' },
    { date: '2027-03-26', name: 'Good Friday' },
    { date: '2027-04-25', name: 'Freedom Day' },
    { date: '2027-05-01', name: 'Labour Day' },
    { date: '2027-05-27', name: 'Corpus Christi' },
    { date: '2027-06-10', name: 'Portugal Day' },
    { date: '2027-08-15', name: 'Assumption' },
    { date: '2027-10-05', name: 'Republic Day' },
    { date: '2027-11-01', name: 'All Saints’ Day' },
    { date: '2027-12-01', name: 'Restoration of Independence' },
    { date: '2027-12-08', name: 'Immaculate Conception' },
    { date: '2027-12-25', name: 'Christmas Day' },
  ],
}
