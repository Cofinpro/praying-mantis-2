import { afterEach, describe, it, expect, vi } from 'vitest'

import {
  addDays,
  formatDate,
  formatDays,
  formatRange,
  formatRangeWithYear,
  today,
  weekday,
  weekdayName,
} from '../dates'

describe('dates', () => {
  afterEach(() => vi.useRealTimers())

  it('adds days across month, year and DST boundaries', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    // Europe leaves summer time on 25 Oct 2026; calendar days must not shift
    expect(addDays('2026-10-24', 2)).toBe('2026-10-26')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('numbers weekdays from Monday', () => {
    expect(weekday('2026-10-05')).toBe(0) // Monday
    expect(weekday('2026-10-11')).toBe(6) // Sunday
  })

  it('takes today from the local clock', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 1, 23, 30)) // 1 Oct, 23:30 local
    expect(today()).toBe('2026-10-01')
  })

  it('formats ranges and day counts', () => {
    expect(formatRange('2026-10-21', '2026-10-21')).toBe('21 Oct')
    expect(formatRange('2026-10-12', '2026-10-16')).toBe('12–16 Oct')
    expect(formatRange('2026-10-30', '2026-11-03')).toBe('30 Oct – 3 Nov')
    expect(formatDays(0.5)).toBe('0.5 day')
    expect(formatDays(1)).toBe('1 day')
    expect(formatDays(2.5)).toBe('2.5 days')
  })

  it('formats dates and ranges with the year', () => {
    expect(formatDate('2026-10-26')).toBe('26 Oct 2026')
    expect(formatRangeWithYear('2026-10-26', '2026-10-27')).toBe('26–27 Oct 2026')
    expect(formatRangeWithYear('2026-10-30', '2026-11-03')).toBe('30 Oct – 3 Nov 2026')
    expect(formatRangeWithYear('2026-12-30', '2027-01-02')).toBe('30 Dec 2026 – 2 Jan 2027')
    expect(weekdayName('2026-10-26')).toBe('Mon')
  })
})
