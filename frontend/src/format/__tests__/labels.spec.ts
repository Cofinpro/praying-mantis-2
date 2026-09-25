import { describe, it, expect } from 'vitest'

import { clientLabel, initials, levelLabel } from '../labels'

describe('labels', () => {
  it.each([
    ['Ana Silva', 'AS'],
    ['Pedro Maria Custodinho', 'PC'],
    ['Ana', 'A'],
    ['  inês  rocha ', 'IR'],
  ])('initials(%j) is %s', (name, expected) => {
    expect(initials(name)).toBe(expected)
  })

  it('maps enum codes to display names', () => {
    expect(clientLabel('DEKA')).toBe('Deka')
    expect(levelLabel('SENIOR_ARCHITECT')).toBe('Senior architect')
  })
})
