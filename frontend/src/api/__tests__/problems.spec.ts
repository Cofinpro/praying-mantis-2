import { describe, it, expect } from 'vitest'

import { ApiError } from '../client'
import { fieldErrors, problemMessage, showsStaleData } from '../problems'

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

  it('lets a caller reword a type for its point of view', () => {
    const overrides = { '/problems/insufficient-balance': 'It no longer fits.' }
    expect(problemMessage(conflict('/problems/insufficient-balance'), overrides)).toBe(
      'It no longer fits.',
    )
    // Types without an override keep the shared wording
    expect(problemMessage(conflict('/problems/absence-not-pending'), overrides)).toBe(
      'This request was already decided or cancelled.',
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
  it('shows a banner for a 400 without field errors (e.g. an unreadable body)', () => {
    // What Spring sends for JSON it can't read, checked against the real backend (FE-3.3)
    const unreadable = new ApiError({
      title: 'Bad Request',
      status: 400,
      detail: 'Failed to read request',
    } as ApiError['problem'])
    expect(problemMessage(unreadable)).toBe('Something went wrong. Please try again.')
    expect(fieldErrors(unreadable)).toEqual({})
  })

  it('treats a 404 or 409 as a sign that the data on screen is out of date', () => {
    expect(showsStaleData(conflict('/problems/absence-not-cancellable'))).toBe(true)
    expect(
      showsStaleData(new ApiError({ title: 'Not Found', status: 404 } as ApiError['problem'])),
    ).toBe(true)
    expect(showsStaleData(new ApiError({ type: 'about:blank', title: 'Oops', status: 500 }))).toBe(
      false,
    )
    expect(showsStaleData(new Error('offline'))).toBe(false)
  })
})
