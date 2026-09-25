import type { Project, ProjectRef, TimeEntryInput, Timesheet } from '@/api/client'
import { addDays } from '@/format/dates'
import { clientLabel } from '@/format/labels'

// The week grid of FE-6.1 as plain data, so the rules of decision 32 can be tested without a
// component: one row per project, one cell per day, hours kept as the text the user typed.

export interface Cell {
  /** What's in the input, e.g. "7,5" while typing; "" is an empty cell */
  hours: string
  description: string
}

export interface GridRow {
  project: ProjectRef
  /** Keyed by `YYYY-MM-DD` */
  cells: Record<string, Cell>
}

export const MAX_HOURS = 24

/** Monday to Sunday */
export const weekDays = (weekStart: string) =>
  Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))

const hoursFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2, useGrouping: false })

/** 7.25 → "7.25", 8 → "8" (formatNumber in format/dates rounds to one decimal) */
export const formatHours = (hours: number) => hoursFormat.format(hours)

export function emptyRow(project: ProjectRef, days: string[]): GridRow {
  return {
    project: { id: project.id, code: project.code, name: project.name },
    cells: Object.fromEntries(days.map((day) => [day, { hours: '', description: '' }])),
  }
}

/** The saved entries as rows, in the backend's order (by project code) */
export function toRows(timesheet: Timesheet): GridRow[] {
  const days = weekDays(timesheet.weekStart)
  const rows = new Map<number, GridRow>()
  for (const entry of timesheet.entries) {
    let row = rows.get(entry.project.id)
    if (!row) {
      row = emptyRow(entry.project, days)
      rows.set(entry.project.id, row)
    }
    row.cells[entry.workDate] = {
      hours: formatHours(entry.hours),
      description: entry.description ?? '',
    }
  }
  return [...rows.values()]
}

export type ParsedHours = { hours: number | null; error?: undefined } | { error: string }

/**
 * The input's text as hours, with the rules of decision 32: more than 0, at most 24, in quarter
 * hours. Empty or 0 means no entry. A comma works as the decimal point too ("7,5").
 */
export function parseHours(text: string): ParsedHours {
  const trimmed = text.trim().replace(',', '.')
  if (trimmed === '') {
    return { hours: null }
  }
  const hours = Number(trimmed)
  if (!/^\d*\.?\d*$/.test(trimmed) || Number.isNaN(hours)) {
    return { error: 'Enter hours as a number, e.g. 7.5' }
  }
  if (hours === 0) {
    return { hours: null }
  }
  if (hours > MAX_HOURS) {
    return { error: `At most ${MAX_HOURS} hours` }
  }
  if (!Number.isInteger(hours * 4)) {
    return { error: 'Use quarter hours, e.g. 7.25' }
  }
  return { hours }
}

/** The PUT body: every cell with valid hours. Cells with errors are left out. */
export function toEntries(rows: GridRow[]): TimeEntryInput[] {
  const entries: TimeEntryInput[] = []
  for (const row of rows) {
    for (const [workDate, cell] of Object.entries(row.cells)) {
      const parsed = parseHours(cell.hours)
      if (parsed.error === undefined && parsed.hours !== null) {
        const description = cell.description.trim()
        entries.push({
          projectId: row.project.id,
          workDate,
          hours: parsed.hours,
          ...(description ? { description } : {}),
        })
      }
    }
  }
  return entries
}

/** The same key for the same entries in any order, to tell whether the grid has unsaved changes */
export function entriesKey(entries: TimeEntryInput[]): string {
  return entries
    .map((e) => [e.projectId, e.workDate, e.hours, e.description ?? ''].join('|'))
    .sort()
    .join('\n')
}

export const cellKey = (projectId: number, day: string) => `${projectId}|${day}`

export interface GridErrors {
  /** Keyed by cellKey() */
  cells: Map<string, string>
  /** Days with more than 24 hours in total, keyed by date */
  days: Map<string, string>
}

export function validate(rows: GridRow[], days: string[]): GridErrors {
  const cells = new Map<string, string>()
  for (const row of rows) {
    for (const day of days) {
      const parsed = parseHours(row.cells[day]?.hours ?? '')
      if (parsed.error !== undefined) {
        cells.set(cellKey(row.project.id, day), parsed.error)
      }
    }
  }
  const dayErrors = new Map<string, string>()
  const totals = dayTotals(rows, days)
  days.forEach((day, i) => {
    if (totals[i]! > MAX_HOURS) {
      dayErrors.set(day, `More than ${MAX_HOURS} hours in one day`)
    }
  })
  return { cells, days: dayErrors }
}

const hoursOf = (cell: Cell | undefined) => {
  const parsed = parseHours(cell?.hours ?? '')
  return parsed.error === undefined ? (parsed.hours ?? 0) : 0
}

export const rowTotal = (row: GridRow, days: string[]) =>
  days.reduce((sum, day) => sum + hoursOf(row.cells[day]), 0)

export const dayTotals = (rows: GridRow[], days: string[]) =>
  days.map((day) => rows.reduce((sum, row) => sum + hoursOf(row.cells[day]), 0))

/** "DKB · DKB core banking", "Internal · Training"; just the name for a project not in the list */
export function projectSubtitle(project: ProjectRef, projects: Project[] | undefined): string {
  const known = projects?.find((p) => p.id === project.id)
  if (!known) {
    return project.name
  }
  const client = known.client ? clientLabel(known.client) : 'Internal'
  // The seed's INTERNAL project is named "Internal" too
  return known.name === client ? client : `${client} · ${known.name}`
}
