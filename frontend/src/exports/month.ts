import type { Client, ExportTemplate, TimesheetMonth } from '@/api/client'
import { addMonths, formatMonth, formatMonthName, isoWeek } from '@/format/dates'
import { clientLabel } from '@/format/labels'
import { formatHours } from '@/timesheets/grid'

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

/**
 * The template to preselect. A client's sheet only holds that client's projects' hours, so once
 * the month's summary is there it's the sheet of the client with the most hours that month, or the
 * generic one when all of them are internal. Without hours (or before the summary loads) it's
 * decision 33: the user's client's sheet, else the generic one, else the first.
 */
export function defaultTemplate(
  templates: ExportTemplate[],
  client: Client | undefined,
  summary?: TimesheetMonth,
): ExportTemplate | undefined {
  const generic = templates.find((t) => !t.client)
  if (summary && summary.totalHours > 0) {
    // `clients` comes most hours first
    const worked = summary.clients.find((c) =>
      templates.some((t) => c.client && t.client === c.client),
    )
    const template = worked && templates.find((t) => t.client === worked.client)
    if (template) {
      return template
    }
    if (generic) {
      return generic
    }
  }
  return templates.find((t) => client && t.client === client) ?? generic ?? templates[0]
}

/** "DKB", "DKB and internal", "DBIS, Deka and internal" */
function joinClients(clients: (Client | undefined)[]): string {
  const names = clients.map((c) => (c ? clientLabel(c) : 'internal'))
  return names.length === 1
    ? names[0]!
    : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`
}

const hours = (n: number) => `${formatHours(n)} ${n === 1 ? 'hour' : 'hours'}`

/**
 * A warning when the chosen client sheet leaves hours out, or null. Only the generic sheet has
 * every hour; a client's sheet has only the hours on that client's projects (decision 33).
 */
export function coverageWarning(
  summary: TimesheetMonth,
  template: ExportTemplate | undefined,
): string | null {
  if (!template?.client || summary.totalHours === 0) {
    return null
  }
  const monthName = formatMonthName(`${summary.month}-01`)
  const label = clientLabel(template.client)
  const onSheet = summary.clients.find((c) => c.client === template.client)?.hours ?? 0
  if (onSheet >= summary.totalHours) {
    return null
  }
  const elsewhere = joinClients(
    summary.clients.filter((c) => c.client !== template.client).map((c) => c.client),
  )
  if (onSheet === 0) {
    return `None of your ${hours(summary.totalHours)} in ${monthName} are on ${label} projects, so this sheet will be empty. They are on ${elsewhere} projects.`
  }
  return `This sheet only has the ${hours(onSheet)} on ${label} projects. Your other ${hours(summary.totalHours - onSheet)} in ${monthName} are on ${elsewhere} projects.`
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
