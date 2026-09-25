import { afterEach, describe, it, expect, vi } from 'vitest'

import {
  addDays,
  formatDate,
  formatDays,
  formatRange,
  formatRangeWithYear,
  formatTimeAgo,
  formatWeekdayDate,
  isIsoDate,
  isoWeek,
  today,
  weekday,
  weekdayName,
  weekStartOf,
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

  it('finds the Monday of a week', () => {
    expect(weekStartOf('2026-10-19')).toBe('2026-10-19') // Monday itself
    expect(weekStartOf('2026-10-25')).toBe('2026-10-19') // Sunday
    expect(weekStartOf('2027-01-01')).toBe('2026-12-28') // across the year
  })

  it('numbers weeks as ISO 8601 does', () => {
    expect(isoWeek('2026-10-19')).toBe(43) // the Figma frame's week
    expect(isoWeek('2026-10-25')).toBe(43)
    // 1 Jan 2026 is a Thursday, so its week is week 1 and starts in 2025
    expect(isoWeek('2025-12-29')).toBe(1)
    expect(isoWeek('2026-01-01')).toBe(1)
    // 2026 has 53 weeks: its last Thursday is 31 Dec
    expect(isoWeek('2026-12-31')).toBe(53)
    expect(isoWeek('2027-01-03')).toBe(53)
    expect(isoWeek('2027-01-04')).toBe(1)
  })

  it('names a day with its weekday', () => {
    expect(formatWeekdayDate('2026-10-19')).toBe('Monday 19 Oct')
  })

  it('accepts only real calendar days', () => {
    expect(isIsoDate('2026-10-19')).toBe(true)
    expect(isIsoDate('2026-02-30')).toBe(false)
    expect(isIsoDate('2026-1-5')).toBe(false)
    expect(isIsoDate(['2026-10-19'])).toBe(false)
    expect(isIsoDate(undefined)).toBe(false)
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
  it('says how long ago an instant was, in local calendar days', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 15, 10, 0)) // 15 Oct, 10:00 local
    const at = (...local: [number, number, number, number]) =>
      new Date(2026, ...local).toISOString()

    expect(formatTimeAgo(new Date(2026, 9, 15, 9, 59, 30).toISOString())).toBe('Just now')
    expect(formatTimeAgo(at(9, 15, 9, 59))).toBe('1 min ago')
    // Clocks a little apart: a moment in the future is still "just now"
    expect(formatTimeAgo(at(9, 15, 10, 1))).toBe('Just now')
    expect(formatTimeAgo(at(9, 15, 9, 58))).toBe('2 min ago')
    expect(formatTimeAgo(at(9, 15, 9, 0))).toBe('1 h ago')
    expect(formatTimeAgo(at(9, 15, 0, 5))).toBe('9 h ago')
    expect(formatTimeAgo(at(9, 14, 23, 0))).toBe('Yesterday')
    expect(formatTimeAgo(at(9, 12, 12, 0))).toBe('3 days ago')
    expect(formatTimeAgo(at(9, 9, 12, 0))).toBe('6 days ago')
    expect(formatTimeAgo(at(9, 8, 12, 0))).toBe('8 Oct 2026')
  })

  it('counts calendar days, not 24-hour periods, for "Yesterday"', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 15, 0, 10)) // 00:10
    expect(formatTimeAgo(new Date(2026, 9, 14, 23, 50).toISOString())).toBe('20 min ago')
    expect(formatTimeAgo(new Date(2026, 9, 14, 22, 0).toISOString())).toBe('Yesterday')
  })
})
