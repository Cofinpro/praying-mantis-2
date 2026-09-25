import type { Client, ExportTemplate, TimesheetMonth } from '@/api/client'
import { addMonths, formatMonth, formatMonthName, isoWeek } from '@/format/dates'

// The logic of the export dialog (FE-8.1), kept out of the component so it's easy to test.

/** How far back the month select goes */
const MONTHS_BACK = 12

/**
 * The months the dialog offers, newest first: next month (to export ahead of a month's last days)
 * down to a year ago. Values are `YYYY-MM`, as the API takes them.
 */
export function monthOptions(today: string): { value: string; label: string }[] {
  const options = []
  for (let i = 1; i >= -MONTHS_BACK; i--) {
    const first = addMonths(today, i)
    options.push({ value: first.slice(0, 7), label: formatMonth(first) })
  }
  return options
}

/** The generic template first, as in the frame, then the clients' in the server's order (by name) */
export function orderTemplates(templates: ExportTemplate[]): ExportTemplate[] {
  return [...templates.filter((t) => !t.client), ...templates.filter((t) => t.client)]
}

/** Decision 33: the template of the user's client, else the generic one, else the first */
export function defaultTemplate(
  templates: ExportTemplate[],
  client: Client | undefined,
): ExportTemplate | undefined {
  return (
    templates.find((t) => client && t.client === client) ??
    templates.find((t) => !t.client) ??
    templates[0]
  )
}

/** "43", "43 and 44", "40, 41 and 42" */
function joinWeeks(weeks: number[]): string {
  if (weeks.length === 1) {
    return String(weeks[0])
  }
  return `${weeks.slice(0, -1).join(', ')} and ${weeks[weeks.length - 1]}`
}

/**
 * The warning under the templates, or null. The export includes every entry whatever its week's
 * status (decision 18), so it warns about the weeks that aren't approved yet. Only weeks with hours
 * in the month count: a week without any (a lazy draft, or the future) adds nothing to the file.
 */
export function monthWarning(summary: TimesheetMonth): string | null {
  const monthName = formatMonthName(`${summary.month}-01`)
  if (summary.totalHours === 0) {
    return `You have no hours in ${monthName} yet, so the file will have no entries.`
  }
  const weeks = summary.weeks
    .filter((w) => w.status !== 'APPROVED' && w.hoursInMonth > 0)
    .map((w) => isoWeek(w.weekStart))
  if (weeks.length === 0) {
    return null
  }
  if (weeks.length === 1) {
    return `Week ${weeks[0]} in ${monthName} is not approved yet. It will be included as it is.`
  }
  return `${weeks.length} weeks in ${monthName} are not approved yet (weeks ${joinWeeks(weeks)}). They will be included as they are.`
}
