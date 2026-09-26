import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/client'
import { checkProject, projectFormErrors } from '../projectForm'

const apiError = (status: number, extra: object = {}) =>
  new ApiError({ type: 'about:blank', title: 'Error', status, ...extra })

describe('project form', () => {
  it('checks the code pattern and a non-blank name', () => {
    expect(checkProject('DKB-CORE', 'Core banking')).toEqual({})
    expect(checkProject(' dkb-core ', 'x')).toEqual({})
    expect(checkProject('D', ' ')).toEqual({
      code: 'Use 2 to 30 letters, digits or dashes, e.g. DKB-CORE.',
      name: 'Enter a name.',
    })
    expect(checkProject('DKB_CORE', 'x').code).toBeDefined()
    expect(checkProject('A'.repeat(31), 'x').code).toBeDefined()
  })

  it('names the taken code, upper-cased as stored', () => {
    const error = apiError(409, { type: '/problems/project-code-taken' })
    expect(projectFormErrors(error, ' dkb-core')).toEqual({
      fields: { code: 'Another project already has the code DKB-CORE.' },
      banner: null,
    })
  })

  it('rewords the generated pattern message, and keeps other 400 messages', () => {
    const error = apiError(400, {
      errors: [
        { field: 'code', message: 'must match "^[A-Za-z0-9-]{2,30}$"' },
        { field: 'name', message: 'size must be between 1 and 255' },
      ],
    })
    expect(projectFormErrors(error, 'x')).toEqual({
      fields: {
        code: 'Use 2 to 30 letters, digits or dashes, e.g. DKB-CORE.',
        name: 'size must be between 1 and 255',
      },
      banner: null,
    })
  })

  it('puts anything else in the banner', () => {
    expect(projectFormErrors(apiError(500), 'x')).toEqual({
      fields: {},
      banner: 'Something went wrong. Please try again.',
    })
    expect(projectFormErrors(null, 'x')).toEqual({ fields: {}, banner: null })
  })
})
