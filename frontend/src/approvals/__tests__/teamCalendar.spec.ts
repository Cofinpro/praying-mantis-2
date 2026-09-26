import { describe, expect, it } from 'vitest'

import type { TeamMemberAbsences } from '@/api/client'
import { buildTeamMonth, monthRange } from '@/approvals/teamCalendar'

const full = { startPart: 'FULL', endPart: 'FULL' } as const

const members: TeamMemberAbsences[] = [
  {
    user: { id: 2, name: 'Ana Silva' },
    absences: [
      // Runs over the weekend and into the next month
      {
        id: 1,
        type: 'VACATION',
        startDate: '2026-10-29',
        endDate: '2026-11-03',
        ...full,
        status: 'APPROVED',
      },
    ],
  },
  {
    user: { id: 4, name: 'Carla Mendes' },
    absences: [
      {
        id: 2,
        type: 'SICK',
        startDate: '2026-10-29',
        endDate: '2026-10-30',
        startPart: 'AFTERNOON',
        endPart: 'MORNING',
        status: 'PENDING',
      },
    ],
  },
]

describe('monthRange', () => {
  it('runs from the 1st to the last day of the month', () => {
    expect(monthRange('2026-02-01')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(monthRange('2026-12-01')).toEqual({ from: '2026-12-01', to: '2026-12-31' })
  })
})

describe('buildTeamMonth', () => {
  const month = buildTeamMonth(
    '2026-10-01',
    members,
    [{ date: '2026-10-05', name: 'Republic Day' }],
    undefined,
  )
  const cell = (row: number, date: string) => month.rows[row]!.cells.find((c) => c.date === date)!
  const day = (date: string) => month.days.find((d) => d.date === date)!

  it('has a column per day, with the weekday letter', () => {
    expect(month.days).toHaveLength(31)
    expect(month.days.slice(0, 5).map((d) => `${d.letter}${d.day}`)).toEqual([
      'T1',
      'F2',
      'S3',
      'S4',
      'M5',
    ])
    expect(day('2026-10-01').fullDate).toBe('Thursday 1 Oct')
  })

  it('marks weekends and holidays, and puts no blocks on them', () => {
    expect(day('2026-10-03').weekend).toBe(true)
    expect(day('2026-10-05').holiday).toBe('Republic Day')
    expect(cell(0, '2026-10-31')).toMatchObject({ off: true, blocks: [] })
    expect(cell(0, '2026-10-30').blocks).toHaveLength(1)
  })

  it('shows half days on their side and describes each block', () => {
    const start = cell(1, '2026-10-29').blocks[0]!
    expect(start).toMatchObject({ half: 'AFTERNOON', pending: true })
    expect(start.description).toBe('Carla Mendes, sick, pending, 29–30 Oct, afternoon only')
    expect(cell(1, '2026-10-30').blocks[0]!.half).toBe('MORNING')
    expect(cell(0, '2026-10-29').blocks[0]!.description).toBe(
      'Ana Silva, vacation, approved, 29 Oct – 3 Nov',
    )
  })

  it('counts who is away, a half day included, and flags 2+ as a conflict', () => {
    expect(day('2026-10-29')).toMatchObject({
      away: ['Ana Silva', 'Carla Mendes'],
      conflict: true,
    })
    expect(day('2026-10-28')).toMatchObject({ away: [], conflict: false })
    expect(day('2026-10-31').away).toEqual([])
  })
})
