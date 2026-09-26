import type { AbsenceType, PublicHoliday, TeamAbsence, TeamMemberAbsences } from '@/api/client'
import { halfOf } from '@/absences/calendar'
import { typeName } from '@/absences/types'
import {
  addDays,
  addMonths,
  dayOfMonth,
  formatRange,
  formatWeekdayDate,
  weekday,
} from '@/format/dates'

// Pure helpers for the team calendar (FE-5.3, Figma frame "10 Approvals – Team calendar"): one row
// per person, one column per day of the month. Kept out of the component so they're easy to test.

const WEEKDAY_LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

export interface TeamDay {
  date: string
  /** "T" */
  letter: string
  /** 1 … 31 */
  day: number
  /** "Thursday 1 Oct", for screen readers */
  fullDate: string
  weekend: boolean
  holiday: string | null
  /** Who is away (a half day counts), in row order */
  away: string[]
  /** Two or more people away */
  conflict: boolean
}

export interface TeamBlock {
  absence: TeamAbsence
  /** Only this half of the day is taken */
  half: 'MORNING' | 'AFTERNOON' | null
  pending: boolean
  /** "Carla Mendes, vacation, pending, 16–18 Nov" */
  description: string
}

export interface TeamCell {
  date: string
  /** Weekend or public holiday: nobody takes those off (decision #15), so no blocks */
  off: boolean
  blocks: TeamBlock[]
}

export interface TeamRow {
  userId: number
  name: string
  cells: TeamCell[]
}

export interface TeamMonth {
  days: TeamDay[]
  rows: TeamRow[]
}

/** The first and last day of the month `month` (a `YYYY-MM-01`), both inclusive */
export function monthRange(month: string): { from: string; to: string } {
  return { from: month, to: addDays(addMonths(month, 1), -1) }
}

export function buildTeamMonth(
  month: string,
  members: TeamMemberAbsences[],
  holidays: PublicHoliday[],
  types: AbsenceType[] | undefined,
): TeamMonth {
  const { from, to } = monthRange(month)
  const holidayByDate = new Map(holidays.map((h) => [h.date, h.name]))
  const dates: string[] = []
  for (let date = from; date <= to; date = addDays(date, 1)) {
    dates.push(date)
  }

  const rows: TeamRow[] = members.map(({ user, absences }) => ({
    userId: user.id,
    name: user.name,
    cells: dates.map((date): TeamCell => {
      const off = weekday(date) > 4 || holidayByDate.has(date)
      const blocks = off
        ? []
        : absences
            .filter((a) => a.startDate <= date && a.endDate >= date)
            .map((absence): TeamBlock => {
              const half = halfOf(absence, date)
              const pending = absence.status === 'PENDING'
              const parts = [
                user.name,
                typeName(absence.type, types).toLowerCase(),
                pending ? 'pending' : 'approved',
                formatRange(absence.startDate, absence.endDate),
              ]
              if (half) {
                parts.push(`${half.toLowerCase()} only`)
              }
              return { absence, half, pending, description: parts.join(', ') }
            })
      return { date, off, blocks }
    }),
  }))

  const days = dates.map((date, i): TeamDay => {
    const away = rows.filter((row) => row.cells[i]!.blocks.length > 0).map((row) => row.name)
    return {
      date,
      letter: WEEKDAY_LETTERS[weekday(date)]!,
      day: dayOfMonth(date),
      fullDate: formatWeekdayDate(date),
      weekend: weekday(date) > 4,
      holiday: holidayByDate.get(date) ?? null,
      away,
      conflict: away.length >= 2,
    }
  })

  return { days, rows }
}
