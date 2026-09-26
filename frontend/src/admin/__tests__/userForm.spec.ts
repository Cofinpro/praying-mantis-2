import { describe, it, expect } from 'vitest'

import { ApiError } from '@/api/client'
import { userFormErrors } from '../userForm'

const conflict = (type: string) =>
  new ApiError({ type, title: 'Conflict', status: 409, detail: 'Backend wording' })

describe('userFormErrors', () => {
  it('falls back to "another user" when the owner of the email isn’t in the list', () => {
    const errors = userFormErrors(conflict('/problems/email-taken'), {
      users: [],
      email: 'new@cofinpro.pt',
    })
    expect(errors).toEqual({
      fields: { email: 'This email is already used by another user.' },
      banner: null,
    })
  })

  it('uses the generic cycle message when it can’t name both people', () => {
    const errors = userFormErrors(conflict('/problems/team-lead-cycle'), {
      users: [],
      email: '',
    })
    expect(errors.fields.teamLeadId).toBe(
      'Nobody can be their own team lead, directly or through others. Pick another team lead.',
    )
  })

  it('puts an unknown conflict or a server error in the banner', () => {
    expect(userFormErrors(conflict('/problems/something-new'), { users: [], email: '' })).toEqual({
      fields: {},
      banner: 'Something went wrong. Please try again.',
    })
    const serverError = new ApiError({ type: 'about:blank', title: 'Oops', status: 500 })
    expect(userFormErrors(serverError, { users: [], email: '' }).banner).toBe(
      'Something went wrong. Please try again.',
    )
  })
})
