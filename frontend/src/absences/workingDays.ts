import type { DayPart } from '@/api/client'
import { addDays, weekday } from '@/format/dates'

// Client-side preview of a request's working days (FE-3.1). The backend computes the real value
// when the request is created (decision #15); this only has to agree with it for the preview.

export interface DayRange {
  startDate: string
  endDate: string
  startPart: DayPart
  endPart: DayPart
}

/** Weekdays that aren't public holidays; a half start or end day counts 0.5 */
export function workingDays(range: DayRange, holidays: ReadonlySet<string>): number {
  let days = 0
  for (let date = range.startDate; date <= range.endDate; date = addDays(date, 1)) {
    if (weekday(date) > 4 || holidays.has(date)) {
      continue
    }
    const half =
      (date === range.startDate && range.startPart !== 'FULL') ||
      (date === range.endDate && range.endPart !== 'FULL')
    days += half ? 0.5 : 1
  }
  return days
}

/**
 * Which parts are allowed (contract, `DayPart`): a single day has the same part at both ends;
 * a multi-day request starts FULL or AFTERNOON and ends FULL or MORNING, so there's no gap.
 */
export function allowedParts(singleDay: boolean, end: 'start' | 'end'): DayPart[] {
  if (singleDay) {
    return ['FULL', 'MORNING', 'AFTERNOON']
  }
  return end === 'start' ? ['FULL', 'AFTERNOON'] : ['FULL', 'MORNING']
}
