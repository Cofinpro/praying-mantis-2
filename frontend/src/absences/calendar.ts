import type { AbsenceRequest, PublicHoliday } from '@/api/client'
import { addDays, daysInMonth, weekday } from '@/format/dates'

// Pure helpers for the month calendar (FE-2.2), kept out of the component so they're easy to test

export interface CalendarEntry {
  request: AbsenceRequest
  /** Only half of this day is taken */
  half: 'MORNING' | 'AFTERNOON' | null
  /** Show the label here: the request's first day, or the first day of a new week row */
  showLabel: boolean
}

export interface CalendarDay {
  date: string
  inMonth: boolean
  weekend: boolean
  holiday: string | null
  entries: CalendarEntry[]
}

/** The Monday on or before the 1st, to the Sunday on or after the last day: 5 or 6 weeks */
export function gridRange(month: string): { from: string; to: string } {
  const from = addDays(month, -weekday(month))
  const weeks = Math.ceil((weekday(month) + daysInMonth(month)) / 7)
  return { from, to: addDays(from, weeks * 7 - 1) }
}

export function buildMonth(
  month: string,
  requests: AbsenceRequest[],
  holidays: PublicHoliday[],
): CalendarDay[][] {
  const { from, to } = gridRange(month)
  const holidayByDate = new Map(holidays.map((h) => [h.date, h.name]))
  const weeks: CalendarDay[][] = []
  let labelled = new Set<number>()

  for (let date = from; date <= to; date = addDays(date, 1)) {
    if (weekday(date) === 0) {
      weeks.push([])
      // Each week row repeats the label of a request that continues into it
      labelled = new Set()
    }
    const weekend = weekday(date) > 4
    const holiday = holidayByDate.get(date) ?? null
    // Weekends and holidays aren't absence days (decision #15), so they get no chips
    const entries =
      weekend || holiday
        ? []
        : requests
            .filter((r) => r.startDate <= date && r.endDate >= date)
            .map((request): CalendarEntry => {
              const showLabel = !labelled.has(request.id)
              labelled.add(request.id)
              return { request, half: halfOf(request, date), showLabel }
            })
    weeks[weeks.length - 1]!.push({
      date,
      inMonth: date.slice(0, 7) === month.slice(0, 7),
      weekend,
      holiday,
      entries,
    })
  }
  return weeks
}

/** A request can start in the afternoon and end in the morning; days in between are full */
function halfOf(r: AbsenceRequest, date: string): CalendarEntry['half'] {
  if (date === r.startDate && r.startPart !== 'FULL') {
    return r.startPart
  }
  if (date === r.endDate && r.endPart !== 'FULL') {
    return r.endPart
  }
  return null
}
