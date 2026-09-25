import { describe, it, expect } from 'vitest'

import type { TimesheetMonth } from '@/api/client'
import { exportTemplates } from '@/mocks/data/exports'
import { defaultTemplate, monthOptions, monthWarning, orderTemplates } from '../month'

const month = (weeks: TimesheetMonth['weeks']): TimesheetMonth => ({
  month: '2026-10',
  totalHours: weeks.reduce((sum, w) => sum + w.hoursInMonth, 0),
  weeks,
})

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
