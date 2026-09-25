import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'

import { api, ApiError } from '@/api/client'
import router from '@/router'
import { server } from '@/mocks/node'

describe('api client', () => {
  beforeEach(async () => {
    await router.push('/')
  })

  it('returns the response body on success', async () => {
    await expect(api.getHello()).resolves.toEqual({ message: 'Hello from the MSW mock' })
  })

  it('throws an ApiError carrying the Problem Details body', async () => {
    server.use(
      http.get('*/api/hello', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Conflict',
            status: 409,
            detail: 'Overlaps another absence',
          },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    const error = await api.getHello().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 409,
      message: 'Overlaps another absence',
      problem: { title: 'Conflict' },
    })
  })

  it('builds a Problem when the error has no body, and redirects to login on 401', async () => {
    await router.push('/?tab=absences')
    server.use(http.get('*/api/hello', () => new HttpResponse(null, { status: 401 })))

    const error = await api.getHello().catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 401, problem: { type: 'about:blank', status: 401 } })
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/?tab=absences')
  })
})
