import { describe, it, expect } from 'vitest'

import type { TimesheetMonth } from '@/api/client'
import { exportTemplates } from '@/mocks/data/exports'
import {
  coverageWarning,
  defaultTemplate,
  monthOptions,
  monthWarning,
  orderTemplates,
} from '../month'

const month = (weeks: TimesheetMonth['weeks']): TimesheetMonth => ({
  month: '2026-10',
  totalHours: weeks.reduce((sum, w) => sum + w.hoursInMonth, 0),
  weeks,
  clients: [],
})

/** A month with only its hours per client, most first as the API sends them */
const hoursOn = (clients: TimesheetMonth['clients']): TimesheetMonth => ({
  month: '2026-09',
  totalHours: clients.reduce((sum, c) => sum + c.hours, 0),
  weeks: [],
  clients,
})
const template = (code: string) => exportTemplates.find((t) => t.code === code)

describe('monthOptions', () => {
  it('goes from next month down to a year ago, across the year boundary', () => {
    const options = monthOptions('2026-09-25')

    expect(options).toHaveLength(14)
    expect(options[0]).toEqual({ value: '2026-10', label: 'October 2026' })
    expect(options[1]).toEqual({ value: '2026-09', label: 'September 2026' })
    expect(options[options.length - 1]).toEqual({ value: '2025-09', label: 'September 2025' })
    expect(monthOptions('2026-12-31')[0]!.value).toBe('2027-01')
  })
})

describe('orderTemplates and defaultTemplate', () => {
  it('puts the generic template first and keeps the rest in the server’s order', () => {
    expect(orderTemplates(exportTemplates).map((t) => t.code)).toEqual([
      'GENERIC',
      'DBIS',
      'DEKA',
      'DKB',
      'UNION',
      'VV',
    ])
  })

  it('picks the user’s client, else the generic one, else the first', () => {
    expect(defaultTemplate(exportTemplates, 'DEKA')?.code).toBe('DEKA')
    const withoutDeka = exportTemplates.filter((t) => t.code !== 'DEKA')
    expect(defaultTemplate(withoutDeka, 'DEKA')?.code).toBe('GENERIC')
    expect(defaultTemplate(withoutDeka, undefined)?.code).toBe('GENERIC')
    const clientsOnly = exportTemplates.filter((t) => t.client)
    expect(defaultTemplate(clientsOnly, 'DEKA' as const)?.code).toBe('DEKA')
    expect(defaultTemplate([], 'DKB')).toBeUndefined()
  })
})

describe('monthWarning', () => {
  it('names the weeks with hours that aren’t approved, as in the frame', () => {
    expect(
      monthWarning(
        month([
          { weekStart: '2026-09-28', status: 'APPROVED', hoursInMonth: 24 },
          { weekStart: '2026-10-05', status: 'APPROVED', hoursInMonth: 40 },
          { weekStart: '2026-10-12', status: 'APPROVED', hoursInMonth: 40 },
          { weekStart: '2026-10-19', status: 'SUBMITTED', hoursInMonth: 38 },
          { weekStart: '2026-10-26', status: 'DRAFT', hoursInMonth: 32 },
        ]),
      ),
    ).toBe(
      '2 weeks in October are not approved yet (weeks 43 and 44). They will be included as they are.',
    )
  })

  it('lists three weeks with commas, and one week in the singular', () => {
    const weeks = [
      { weekStart: '2026-10-05', status: 'REJECTED' as const, hoursInMonth: 8 },
      { weekStart: '2026-10-12', status: 'DRAFT' as const, hoursInMonth: 8 },
      { weekStart: '2026-10-19', status: 'SUBMITTED' as const, hoursInMonth: 8 },
    ]
    expect(monthWarning(month(weeks))).toContain('3 weeks in October')
    expect(monthWarning(month(weeks))).toContain('(weeks 41, 42 and 43)')
    expect(monthWarning(month(weeks.slice(0, 1)))).toBe(
      'Week 41 in October is not approved yet. It will be included as it is.',
    )
  })

  it('ignores weeks without hours in the month, and warns when there are no hours at all', () => {
    expect(
      monthWarning(
        month([
          { weekStart: '2026-09-28', status: 'DRAFT', hoursInMonth: 0 },
          { weekStart: '2026-10-05', status: 'APPROVED', hoursInMonth: 40 },
        ]),
      ),
    ).toBeNull()
    expect(
      monthWarning(month([{ weekStart: '2026-10-05', status: 'DRAFT', hoursInMonth: 0 }])),
    ).toBe('You have no hours in October yet, so the file will have no entries.')
  })
})

describe('defaultTemplate with the month’s hours', () => {
  it('picks the client the hours are for, not the user’s own', () => {
    // Ana works for DKB, but September went to DBIS
    const september = hoursOn([{ client: 'DBIS', hours: 40 }])
    expect(defaultTemplate(exportTemplates, 'DKB', september)?.code).toBe('DBIS')
  })

  it('picks the client with the most hours, skipping internal ones', () => {
    const month = hoursOn([
      { hours: 30 },
      { client: 'DEKA', hours: 8 },
      { client: 'DKB', hours: 2 },
    ])
    expect(defaultTemplate(exportTemplates, 'DKB', month)?.code).toBe('DEKA')
  })

  it('picks the generic sheet when every hour is internal', () => {
    expect(defaultTemplate(exportTemplates, 'DKB', hoursOn([{ hours: 16 }]))?.code).toBe('GENERIC')
  })

  it('falls back to the user’s client without hours', () => {
    expect(defaultTemplate(exportTemplates, 'DKB', hoursOn([]))?.code).toBe('DKB')
  })
})

describe('coverageWarning', () => {
  it('says a client sheet will be empty, and where the hours are', () => {
    expect(coverageWarning(hoursOn([{ client: 'DBIS', hours: 40 }]), template('DKB'))).toBe(
      'None of your 40 hours in September are on DKB projects, so this sheet will be empty. They are on DBIS projects.',
    )
  })

  it('says how many hours a client sheet leaves out', () => {
    const month = hoursOn([
      { client: 'DKB', hours: 32 },
      { client: 'DEKA', hours: 6 },
      { hours: 1.5 },
    ])
    expect(coverageWarning(month, template('DKB'))).toBe(
      'This sheet only has the 32 hours on DKB projects. Your other 7.5 hours in September are on Deka and internal projects.',
    )
  })

  it('stays quiet for the generic sheet, a complete client sheet, or no hours', () => {
    const month = hoursOn([{ client: 'DKB', hours: 32 }, { hours: 8 }])
    expect(coverageWarning(month, template('GENERIC'))).toBeNull()
    expect(coverageWarning(hoursOn([{ client: 'DKB', hours: 40 }]), template('DKB'))).toBeNull()
    expect(coverageWarning(hoursOn([]), template('DKB'))).toBeNull()
  })
})
