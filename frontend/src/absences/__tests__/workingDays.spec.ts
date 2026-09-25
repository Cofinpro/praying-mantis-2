import { describe, it, expect } from 'vitest'

import { allowedParts, workingDays } from '../workingDays'

const range = (startDate: string, endDate: string, startPart = 'FULL', endPart = 'FULL') =>
  ({ startDate, endDate, startPart, endPart }) as Parameters<typeof workingDays>[0]
const noHolidays = new Set<string>()

describe('workingDays', () => {
  it('counts weekdays and skips weekends', () => {
    expect(workingDays(range('2026-10-12', '2026-10-16'), noHolidays)).toBe(5)
    expect(workingDays(range('2026-10-09', '2026-10-12'), noHolidays)).toBe(2) // Fri–Mon
    expect(workingDays(range('2026-10-10', '2026-10-11'), noHolidays)).toBe(0) // weekend only
  })

  it('skips public holidays', () => {
    expect(workingDays(range('2026-10-05', '2026-10-06'), new Set(['2026-10-05']))).toBe(1)
  })

  it('counts half start and end days as 0.5', () => {
    expect(workingDays(range('2026-10-13', '2026-10-13', 'MORNING', 'MORNING'), noHolidays)).toBe(
      0.5,
    )
    expect(workingDays(range('2026-10-13', '2026-10-15', 'AFTERNOON', 'MORNING'), noHolidays)).toBe(
      2,
    )
  })

  it('allows only parts without a gap on multi-day requests', () => {
    expect(allowedParts(true, 'start')).toEqual(['FULL', 'MORNING', 'AFTERNOON'])
    expect(allowedParts(false, 'start')).toEqual(['FULL', 'AFTERNOON'])
    expect(allowedParts(false, 'end')).toEqual(['FULL', 'MORNING'])
  })
})
