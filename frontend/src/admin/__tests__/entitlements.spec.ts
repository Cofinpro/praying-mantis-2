import { describe, expect, it } from 'vitest'

import type { AdminEntitlement } from '@/api/client'
import { cellError, changeFor, toCell, totalOf } from '../entitlements'

const saved: AdminEntitlement = {
  id: 7,
  user: { id: 2, name: 'Ana Silva' },
  type: 'VACATION',
  year: 2026,
  entitledDays: 22,
  carriedOverDays: 3,
}
const context = { userId: 2, year: 2026, type: 'VACATION' as const }

describe('entitlement cells', () => {
  it('shows saved days as typed text, and nothing for no entitlement', () => {
    expect(toCell(22)).toBe('22')
    expect(toCell(2.5)).toBe('2.5')
    expect(toCell(undefined)).toBe('')
  })

  it('accepts 0 to 366 in steps of 0.5, and an empty cell', () => {
    expect(cellError('')).toBeUndefined()
    expect(cellError(' 0 ')).toBeUndefined()
    expect(cellError('366')).toBeUndefined()
    expect(cellError('2.5')).toBeUndefined()
    expect(cellError('2.25')).toBe('Use steps of 0.5, e.g. 2.5.')
    expect(cellError('-1')).toBe('Enter 0 to 366 days.')
    expect(cellError('367')).toBe('Enter 0 to 366 days.')
    expect(cellError('abc')).toBe('Enter 0 to 366 days.')
  })

  it('adds entitled and carried over, empty counting as 0', () => {
    expect(totalOf({ entitled: '22', carried: '2.5' })).toBe(24.5)
    expect(totalOf({ entitled: '', carried: '' })).toBe(0)
    expect(totalOf({ entitled: '1.2', carried: '' })).toBeUndefined()
  })
})

describe('changeFor', () => {
  it('is null when the cells match what is saved, or both are empty without an entitlement', () => {
    expect(changeFor(context, saved, { entitled: '22', carried: '3' })).toBeNull()
    expect(changeFor(context, saved, { entitled: '22.0', carried: '3' })).toBeNull()
    expect(changeFor(context, undefined, { entitled: '', carried: ' ' })).toBeNull()
  })

  it('upserts a changed or new row, an empty cell as 0', () => {
    expect(changeFor(context, saved, { entitled: '24.5', carried: '3' })).toEqual({
      kind: 'save',
      userId: 2,
      body: { userId: 2, type: 'VACATION', year: 2026, entitledDays: 24.5, carriedOverDays: 3 },
    })
    expect(changeFor(context, undefined, { entitled: '25', carried: '' })).toEqual({
      kind: 'save',
      userId: 2,
      body: { userId: 2, type: 'VACATION', year: 2026, entitledDays: 25, carriedOverDays: 0 },
    })
  })

  it('deletes a saved entitlement whose cells were both emptied', () => {
    expect(changeFor(context, saved, { entitled: '', carried: '' })).toEqual({
      kind: 'delete',
      userId: 2,
      id: 7,
    })
  })
})
