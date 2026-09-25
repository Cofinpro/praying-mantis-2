import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import LoginView from '../LoginView.vue'
import router from '@/router'
import { server } from '@/mocks/node'
import { MOCK_PASSWORD, mockUser } from '@/mocks/handlers'
import { queryPlugin, testQueryClient } from '@/test/query'

// Pages behind the nav are lazy-loaded (`import()`), which flushPromises() doesn't wait for,
// so navigation results are checked with vi.waitFor
async function mountLogin(path = '/login') {
  await router.push(path)
  const queryClient = testQueryClient()
  const wrapper = mount(LoginView, {
    // Attached to the document so focus can be checked; setup.ts unmounts it after each test
    attachTo: document.body,
    global: { plugins: [router, queryPlugin(queryClient)] },
  })
  return { wrapper, queryClient }
}

async function signIn(
  wrapper: Awaited<ReturnType<typeof mountLogin>>['wrapper'],
  password: string,
) {
  await wrapper.find('input[type="email"]').setValue('ana.silva@cofinpro.pt')
  await wrapper.find('input[type="password"]').setValue(password)
  await wrapper.find('form').trigger('submit')
}

describe('LoginView', () => {
  it('shows the sign-in form with labelled fields', async () => {
    const { wrapper } = await mountLogin()

    expect(wrapper.find('h1').text()).toBe('Sign in')
    for (const label of ['Email', 'Password']) {
      const labelEl = wrapper.findAll('label').find((l) => l.text() === label)!
      expect(wrapper.find(`#${labelEl.attributes('for')}`).exists()).toBe(true)
    }
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('logs in, caches the user and goes back to the page that was requested', async () => {
    const { wrapper, queryClient } = await mountLogin('/login?redirect=/timesheets%3Fweek%3D43')

    await signIn(wrapper, MOCK_PASSWORD)
    await flushPromises()

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/timesheets?week=43'))
    expect(queryClient.getQueryData(['me'])).toMatchObject({ name: mockUser.name })
  })

  it('goes home when there is no redirect', async () => {
    const { wrapper } = await mountLogin()

    await signIn(wrapper, MOCK_PASSWORD)
    await flushPromises()

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/absences'))
  })

  it.each(['//evil.example.com', '/%5Cevil.example.com'])(
    'ignores a redirect to another site (%s)',
    async (redirect) => {
      const { wrapper } = await mountLogin(`/login?redirect=${redirect}`)

      await signIn(wrapper, MOCK_PASSWORD)
      await flushPromises()

      await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/absences'))
    },
  )

  it('clears data cached by a previous session', async () => {
    const { wrapper, queryClient } = await mountLogin()
    queryClient.setQueryData(['absences'], ['from the previous user'])

    await signIn(wrapper, MOCK_PASSWORD)
    await flushPromises()

    expect(queryClient.getQueryData(['absences'])).toBeUndefined()
  })

  it('shows one inline error on 401, without saying which field was wrong', async () => {
    const { wrapper } = await mountLogin()

    await signIn(wrapper, 'wrong')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('Email or password is incorrect.')
    expect(router.currentRoute.value.name).toBe('login')
    expect(wrapper.find('[aria-invalid="true"]').exists()).toBe(false)
  })

  it('shows field errors from a 400 under the matching input and focuses it', async () => {
    server.use(
      http.post('*/api/auth/login', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Bad Request',
            status: 400,
            errors: [{ field: 'email', message: 'must be a well-formed email address' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const { wrapper } = await mountLogin()

    await signIn(wrapper, MOCK_PASSWORD)
    await flushPromises()

    const email = wrapper.find('input[type="email"]')
    expect(email.attributes('aria-invalid')).toBe('true')
    expect(wrapper.find(`#${email.attributes('aria-describedby')}`).text()).toBe(
      'must be a well-formed email address',
    )
    expect(document.activeElement).toBe(email.element)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows a generic error when the server fails', async () => {
    server.use(http.post('*/api/auth/login', () => new HttpResponse(null, { status: 503 })))
    const { wrapper } = await mountLogin()

    await signIn(wrapper, MOCK_PASSWORD)
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('Something went wrong. Please try again.')
  })

  it('disables the button while signing in', async () => {
    let respond!: () => void
    server.use(
      http.post('*/api/auth/login', async () => {
        await new Promise<void>((resolve) => (respond = resolve))
        return HttpResponse.json(mockUser)
      }),
    )
    const { wrapper } = await mountLogin()

    await signIn(wrapper, MOCK_PASSWORD)
    await flushPromises()

    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toBe('Signing in…')

    respond()
    await flushPromises()
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/absences'))
  })
})
