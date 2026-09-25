import { describe, it, expect } from 'vitest'

import { ApiError } from '../client'
import { fieldErrors, problemMessage } from '../problems'

const conflict = (type: string, detail?: string) =>
  new ApiError({ type, title: 'Conflict', status: 409, detail })

describe('problems', () => {
  it('maps each 409 type to its own message', () => {
    expect(problemMessage(conflict('/problems/absence-overlap'))).toBe(
      'These days overlap another absence of yours that is pending or approved.',
    )
    expect(problemMessage(conflict('/problems/no-approver'))).toContain('ask an admin')
  })

  it('adds the numbers from the backend for the balance case', () => {
    const error = conflict(
      '/problems/insufficient-balance',
      'Only 3 vacation days left in 2026, but the request needs 5',
    )
    expect(problemMessage(error)).toBe(
      'You don’t have enough days left for this request. Only 3 vacation days left in 2026, but the request needs 5.',
    )
  })

  it('leaves 400s to the fields and falls back for anything else', () => {
    const invalid = new ApiError({
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      errors: [{ field: 'endDate', message: 'must not be before startDate' }],
    })
    expect(problemMessage(invalid)).toBeNull()
    expect(fieldErrors(invalid)).toEqual({ endDate: 'must not be before startDate' })
    expect(problemMessage(new ApiError({ type: 'about:blank', title: 'Oops', status: 500 }))).toBe(
      'Something went wrong. Please try again.',
    )
    expect(problemMessage(null)).toBeNull()
  })
})
