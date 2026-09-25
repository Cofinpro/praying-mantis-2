import { describe, it, expect } from 'vitest'

import type { Project, Timesheet } from '@/api/client'
import {
  dayTotals,
  emptyRow,
  entriesKey,
  formatHours,
  parseHours,
  projectSubtitle,
  rowTotal,
  toEntries,
  toRows,
  validate,
  weekDays,
} from '../grid'

const week = weekDays('2026-10-19')
const core = { id: 1, code: 'DKB-CORE', name: 'DKB core banking' }
const training = { id: 8, code: 'TRAINING', name: 'Training' }

const sheet: Timesheet = {
  id: 4,
  weekStart: '2026-10-19',
  status: 'DRAFT',
  totalHours: 21.5,
  absences: [],
  holidays: [],
  entries: [
    { id: 1, project: core, workDate: '2026-10-19', hours: 6 },
    { id: 2, project: core, workDate: '2026-10-20', hours: 7.25, description: 'Sprint planning' },
    { id: 3, project: training, workDate: '2026-10-21', hours: 8 },
  ],
}

describe('timesheet grid', () => {
  it('lists the seven days of the week', () => {
    expect(week).toEqual([
      '2026-10-19',
      '2026-10-20',
      '2026-10-21',
      '2026-10-22',
      '2026-10-23',
      '2026-10-24',
      '2026-10-25',
    ])
  })

  it('parses hours with the rules of decision 32', () => {
    expect(parseHours('')).toEqual({ hours: null })
    expect(parseHours('  ')).toEqual({ hours: null })
    expect(parseHours('0')).toEqual({ hours: null })
    expect(parseHours('7.5')).toEqual({ hours: 7.5 })
    expect(parseHours('7,25')).toEqual({ hours: 7.25 })
    expect(parseHours('.75')).toEqual({ hours: 0.75 })
    expect(parseHours('24')).toEqual({ hours: 24 })
    expect(parseHours('24.25')).toEqual({ error: 'At most 24 hours' })
    expect(parseHours('7.1')).toEqual({ error: 'Use quarter hours, e.g. 7.25' })
    expect(parseHours('-2')).toEqual({ error: 'Enter hours as a number, e.g. 7.5' })
    expect(parseHours('abc')).toEqual({ error: 'Enter hours as a number, e.g. 7.5' })
    expect(parseHours('1e1')).toEqual({ error: 'Enter hours as a number, e.g. 7.5' })
  })

  it('formats hours with up to two decimals', () => {
    expect(formatHours(8)).toBe('8')
    expect(formatHours(7.25)).toBe('7.25')
    expect(formatHours(40.5)).toBe('40.5')
  })

  it('turns entries into rows and back', () => {
    const rows = toRows(sheet)

    expect(rows.map((r) => r.project.code)).toEqual(['DKB-CORE', 'TRAINING'])
    expect(rows[0]!.cells['2026-10-20']).toEqual({ hours: '7.25', description: 'Sprint planning' })
    expect(rows[0]!.cells['2026-10-21']).toEqual({ hours: '', description: '' })
    expect(toEntries(rows)).toEqual([
      { projectId: 1, workDate: '2026-10-19', hours: 6 },
      { projectId: 1, workDate: '2026-10-20', hours: 7.25, description: 'Sprint planning' },
      { projectId: 8, workDate: '2026-10-21', hours: 8 },
    ])
  })

  it('leaves out empty and invalid cells, and descriptions without hours', () => {
    const row = emptyRow(core, week)
    row.cells['2026-10-19'] = { hours: '4', description: '  ' }
    row.cells['2026-10-20'] = { hours: '4.1', description: 'typo' }
    row.cells['2026-10-21'] = { hours: '', description: 'no hours' }

    expect(toEntries([row])).toEqual([{ projectId: 1, workDate: '2026-10-19', hours: 4 }])
  })

  it('compares entries regardless of order', () => {
    const entries = toEntries(toRows(sheet))
    expect(entriesKey([...entries].reverse())).toBe(entriesKey(entries))
    expect(entriesKey(entries.slice(1))).not.toBe(entriesKey(entries))
  })

  it('adds up rows and days, skipping invalid cells', () => {
    const rows = toRows(sheet)
    rows[1]!.cells['2026-10-19']!.hours = 'x'

    expect(rowTotal(rows[0]!, week)).toBe(13.25)
    expect(dayTotals(rows, week)).toEqual([6, 7.25, 8, 0, 0, 0, 0])
  })

  it('flags invalid cells and days over 24 hours', () => {
    const rows = [emptyRow(core, week), emptyRow(training, week)]
    rows[0]!.cells['2026-10-19']!.hours = '16'
    rows[1]!.cells['2026-10-19']!.hours = '9'
    rows[1]!.cells['2026-10-20']!.hours = '3.3'

    const errors = validate(rows, week)

    expect([...errors.cells]).toEqual([['8|2026-10-20', 'Use quarter hours, e.g. 7.25']])
    expect([...errors.days]).toEqual([['2026-10-19', 'More than 24 hours in one day']])
  })

  it('labels a project with its client, or as internal', () => {
    const projects: Project[] = [
      { ...core, client: 'DKB', isBillable: true, isActive: true },
      { ...training, isBillable: false, isActive: true },
      { id: 7, code: 'INTERNAL', name: 'Internal', isBillable: false, isActive: true },
    ]
    expect(projectSubtitle(core, projects)).toBe('DKB · DKB core banking')
    expect(projectSubtitle(training, projects)).toBe('Internal · Training')
    expect(projectSubtitle(projects[2]!, projects)).toBe('Internal')
    // An inactive project isn't in the active list: just its name
    expect(projectSubtitle({ id: 9, code: 'DKB-LEGACY', name: 'Legacy' }, projects)).toBe('Legacy')
  })
})
