import { describe, it, expect } from 'vitest'

import { buildMonth, gridRange } from '../calendar'
import type { AbsenceRequest } from '@/api/client'

const req = (r: Partial<AbsenceRequest>): AbsenceRequest => ({
  id: 1,
  type: 'VACATION',
  startDate: '2026-10-12',
  endDate: '2026-10-16',
  startPart: 'FULL',
  endPart: 'FULL',
  workingDays: 5,
  status: 'APPROVED',
  createdAt: '2026-09-01T09:00:00Z',
  ...r,
})

const day = (weeks: ReturnType<typeof buildMonth>, date: string) =>
  weeks.flat().find((d) => d.date === date)!

describe('calendar', () => {
  it('spans whole weeks from Monday to Sunday', () => {
    expect(gridRange('2026-10-01')).toEqual({ from: '2026-09-28', to: '2026-11-01' }) // 5 weeks
    expect(gridRange('2026-08-01')).toEqual({ from: '2026-07-27', to: '2026-09-06' }) // 6 weeks
    expect(gridRange('2027-02-01')).toEqual({ from: '2027-02-01', to: '2027-02-28' }) // 4 weeks
  })

  it('marks days outside the month, weekends and holidays', () => {
    const weeks = buildMonth('2026-10-01', [], [{ date: '2026-10-05', name: 'Republic Day' }])

    expect(weeks).toHaveLength(5)
    expect(day(weeks, '2026-09-30').inMonth).toBe(false)
    expect(day(weeks, '2026-10-10').weekend).toBe(true)
    expect(day(weeks, '2026-10-05').holiday).toBe('Republic Day')
  })

  it('labels a request on its first day and again at the start of each week', () => {
    const weeks = buildMonth(
      '2026-10-01',
      [req({ startDate: '2026-10-15', endDate: '2026-10-20' })],
      [],
    )

    expect(day(weeks, '2026-10-15').entries[0]!.showLabel).toBe(true)
    expect(day(weeks, '2026-10-16').entries[0]!.showLabel).toBe(false)
    expect(day(weeks, '2026-10-19').entries[0]!.showLabel).toBe(true) // Monday, new row
  })

  it('puts no absence on weekends or holidays inside a request', () => {
    const weeks = buildMonth(
      '2026-10-01',
      [req({ startDate: '2026-10-02', endDate: '2026-10-06' })],
      [{ date: '2026-10-05', name: 'Republic Day' }],
    )

    expect(day(weeks, '2026-10-02').entries).toHaveLength(1)
    expect(day(weeks, '2026-10-03').entries).toHaveLength(0) // Saturday
    expect(day(weeks, '2026-10-05').entries).toHaveLength(0) // holiday
    expect(day(weeks, '2026-10-06').entries).toHaveLength(1)
  })

  it('knows half days at the start and the end', () => {
    const weeks = buildMonth(
      '2026-10-01',
      [
        req({
          startDate: '2026-10-13',
          endDate: '2026-10-15',
          startPart: 'AFTERNOON',
          endPart: 'MORNING',
        }),
      ],
      [],
    )

    expect(day(weeks, '2026-10-13').entries[0]!.half).toBe('AFTERNOON')
    expect(day(weeks, '2026-10-14').entries[0]!.half).toBeNull()
    expect(day(weeks, '2026-10-15').entries[0]!.half).toBe('MORNING')
  })
})
