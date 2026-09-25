import type { AbsenceType, Timesheet } from '@/api/client'
import { typeColor, typeName } from '@/absences/types'
import { weekday } from '@/format/dates'

// The chips in the week grid's header (FE-6.1): approved absences and public holidays, both from
// the week's GET (decision 32). Shared by my own week and the approver's read-only view (FE-7.1).

export interface DayMark {
  label: string
  /** Accessible and hover text, e.g. "Training (approved absence)" */
  title: string
  color: string
  background: string
}

export function dayMarks(
  sheet: Timesheet | undefined,
  days: string[],
  types: AbsenceType[] | undefined,
): Record<string, DayMark[]> {
  const result: Record<string, DayMark[]> = {}
  if (!sheet) {
    return result
  }
  for (const day of days) {
    const list: DayMark[] = []
    for (const absence of sheet.absences) {
      // Weekends aren't absence days (decision 15), even inside a Friday-to-Monday range
      if (
        absence.status !== 'APPROVED' ||
        weekday(day) >= 5 ||
        day < absence.startDate ||
        day > absence.endDate
      ) {
        continue
      }
      const name = typeName(absence.type, types)
      const half =
        (day === absence.startDate && absence.startPart !== 'FULL') ||
        (day === absence.endDate && absence.endPart !== 'FULL')
      const { solid, soft } = typeColor(absence.type)
      list.push({
        label: half ? `${name} ½` : name,
        title: `${name}${half ? ', half day' : ''} (approved absence)`,
        color: solid,
        background: soft,
      })
    }
    for (const holiday of sheet.holidays.filter((h) => h.date === day)) {
      list.push({
        label: holiday.name,
        title: `${holiday.name} (public holiday)`,
        color: 'var(--color-muted)',
        background: 'var(--color-grey)',
      })
    }
    result[day] = list
  }
  return result
}
